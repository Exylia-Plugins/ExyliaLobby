package net.exylia.exyliaLobby.module.spawn;

import net.exylia.exyliaLobby.config.LobbyConfig;
import net.exylia.lib.region.Regions;
import net.exylia.lib.region.SelectionResult;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import net.exylia.exyliaLobby.config.LobbyMessages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** The lobby areas editor: worlds and boxes listed, added from the admin's world or selection, removed on confirm. */
final class AreasEditor {

    private static final Pattern CORNER = Pattern.compile("\\s*(-?\\d+)[ ,]+(-?\\d+)[ ,]+(-?\\d+)\\s*");

    private final SpawnModule module;

    AreasEditor(SpawnModule module) {
        this.module = module;
    }

    void open(Player viewer) {
        module.ui().open(viewer, SpawnModule.AREAS, Map.of("back_name", module.areaParent(viewer)), module::areaRows);
    }

    List<UiEntry> rows() {
        SpawnMessages text = module.messages().get();
        LobbyConfig.Areas areas = module.context().config().get().areas();
        List<UiEntry> rows = new ArrayList<>();
        for (String name : areas.worlds()) {
            boolean loaded = Bukkit.getWorld(name) != null;
            rows.add(UiEntry.of("world:" + name)
                    .with("area_kind", "world")
                    .with("area_id", name)
                    .with("area_name", name)
                    .with("area_icon", Material.GRASS_BLOCK.name())
                    .withFormatted("area_kind_label", text.kindWorld())
                    .with("area_world", name)
                    .withFormatted("area_state_label", text.stateLabelWorld())
                    .withFormatted("area_state", loaded ? text.worldLoaded() : text.worldMissing())
                    .build());
        }
        for (Map.Entry<String, LobbyConfig.Box> entry : areas.boxes().entrySet()) {
            LobbyConfig.Box box = entry.getValue();
            rows.add(UiEntry.of("box:" + entry.getKey())
                    .with("area_kind", "box")
                    .with("area_id", entry.getKey())
                    .with("area_name", entry.getKey())
                    .with("area_icon", Material.FILLED_MAP.name())
                    .withFormatted("area_kind_label", text.kindBox())
                    .with("area_world", box.world())
                    .withFormatted("area_state_label", text.stateLabelBox())
                    .withFormatted("area_state", size(text, box))
                    .build());
        }
        return rows;
    }

    private static String size(SpawnMessages text, LobbyConfig.Box box) {
        var min = CORNER.matcher(box.min());
        var max = CORNER.matcher(box.max());
        if (!min.matches() || !max.matches()) return "{error}?";
        return Values.of("x", side(min, max, 1)).put("y", side(min, max, 2)).put("z", side(min, max, 3)).apply(text.boxSize());
    }

    private static int side(java.util.regex.Matcher min, java.util.regex.Matcher max, int group) {
        return Math.abs(Integer.parseInt(max.group(group)) - Integer.parseInt(min.group(group))) + 1;
    }

    // ======================== add ========================

    void addWorld(Player admin) {
        String world = admin.getWorld().getName();
        LobbyMessages.Areas text = module.context().messages().get().areas();
        module.updateAreas(areas -> {
            if (areas.worlds().contains(world)) return areas;
            List<String> worlds = new ArrayList<>(areas.worlds());
            worlds.add(world);
            return new LobbyConfig.Areas(worlds, areas.boxes(), areas.worldguardRegions());
        });
        module.send(admin, text.worldAdded(), Values.of("world", world));
        module.ui().update(admin, SpawnModule.AREAS, Map.of());
    }

    void addBox(Player admin) {
        admin.closeInventory();
        module.send(admin, module.messages().get().selectBox(), Values.of());
        Regions.of(module.context().plugin()).beginSelection(admin).result().whenComplete((result, error) ->
                module.context().tasks().runAtEntity(admin, () -> {
                    if (error != null || result == null) {
                        module.send(admin, module.context().messages().get().areas().noSelection(), Values.of());
                        open(admin);
                        return;
                    }
                    askName(admin, result);
                }));
    }

    private void askName(Player admin, SelectionResult result) {
        Map<String, LobbyConfig.Box> boxes = module.context().config().get().areas().boxes();
        int number = boxes.size() + 1;
        while (boxes.containsKey("box-" + number)) number++;
        module.inputs().answered(admin,
                module.inputs().slug(admin, module.messages().get().promptName())
                        .defaultValue("box-" + number)
                        .validate(name -> !module.context().config().get().areas().boxes().containsKey(name),
                                module.messages().get().nameTaken())
                        .open(),
                name -> {
                    save(name, result);
                    module.send(admin, module.context().messages().get().areas().boxSaved(), Values.of("area", name));
                    open(admin);
                },
                () -> open(admin));
    }

    private void save(String name, SelectionResult result) {
        var a = result.first();
        var b = result.second();
        String min = Math.min(a.x(), b.x()) + " " + Math.min(a.y(), b.y()) + " " + Math.min(a.z(), b.z());
        String max = Math.max(a.x(), b.x()) + " " + Math.max(a.y(), b.y()) + " " + Math.max(a.z(), b.z());
        LobbyConfig.Box box = new LobbyConfig.Box(result.world().fallbackName(), min, max);
        module.updateAreas(areas -> {
            Map<String, LobbyConfig.Box> boxes = new LinkedHashMap<>(areas.boxes());
            boxes.put(name, box);
            return new LobbyConfig.Areas(areas.worlds(), boxes, areas.worldguardRegions());
        });
    }

    // ======================== remove ========================

    void remove(Player admin, String kind, String id) {
        boolean world = kind.equals("world");
        String prompt = Values.of("area", id).apply(world ? module.messages().get().confirmWorld() : module.messages().get().confirmBox());
        admin.closeInventory();
        module.inputs().answered(admin, module.inputs().confirm(admin, prompt).dangerous().open(),
                yes -> {
                    if (yes) delete(admin, world, id);
                    open(admin);
                },
                () -> open(admin));
    }

    private void delete(Player admin, boolean world, String id) {
        LobbyMessages.Areas text = module.context().messages().get().areas();
        module.updateAreas(areas -> {
            if (world) {
                List<String> worlds = new ArrayList<>(areas.worlds());
                worlds.remove(id);
                return new LobbyConfig.Areas(worlds, areas.boxes(), areas.worldguardRegions());
            }
            Map<String, LobbyConfig.Box> boxes = new LinkedHashMap<>(areas.boxes());
            boxes.remove(id);
            return new LobbyConfig.Areas(areas.worlds(), boxes, areas.worldguardRegions());
        });
        module.send(admin, world ? text.worldRemoved() : text.boxRemoved(), Values.of(world ? "world" : "area", id));
    }
}

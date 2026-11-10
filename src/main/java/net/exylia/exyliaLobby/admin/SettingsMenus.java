package net.exylia.exyliaLobby.admin;

import net.exylia.exyliaLobby.ExyliaLobby;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.gadgets.GadgetsSettings;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigEdits;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.input.PluginInputs;
import net.exylia.lib.task.Tasks;
import net.exylia.lib.text.Lines;
import net.exylia.lib.text.Phrases;
import net.exylia.lib.ui.PluginMenus;
import net.exylia.lib.ui.UiKeys;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings hub and one page per module: the values an admin tunes while the server runs, drawn from the
 * module's own settings file. A page of a module that is off opens the Modules screen instead.
 *
 * <p>Gadget cooldowns live in a map of records, which {@link ConfigEdits} does not edit, so they are handled here.
 */
final class SettingsMenus {

    static final String HUB = "admin/settings";
    private static final String GADGETS = "gadgets";
    private static final Pattern COOLDOWN = Pattern.compile("gadgets\\.([a-z]+)\\.cooldown");

    /** Each page is the id of the module it edits and the suffix of its menu file; the keys are what the hub shows. */
    private static final Map<String, List<String>> PAGES = new LinkedHashMap<>();

    static {
        PAGES.put("spawn", List.of("on-join", "warmup"));
        PAGES.put("state", List.of("game-mode", "time"));
        PAGES.put("protection", List.of("block-break", "block-place"));
        PAGES.put("join", List.of("only-in-lobby", "welcome-delay"));
        PAGES.put("chat", List.of("locked-at-start", "slow-mode-seconds"));
        PAGES.put("onboarding", List.of("journey", "delay"));
        PAGES.put("movement", List.of("double-jump.enabled", "double-jump.cooldown"));
        PAGES.put("visibility", List.of("default-mode", "cooldown"));
        PAGES.put("pvp", List.of("equip-seconds", "tag-seconds"));
        PAGES.put(GADGETS, List.of("view-radius", "gadgets.grapple.cooldown"));
        PAGES.put("parkour", List.of("modes.endless", "modes.sprint-seconds"));
        PAGES.put("hud", List.of("sidebar.enabled", "boss-bars.enabled"));
    }

    private final ExyliaLobby lobby;
    private final PluginMenus menus;
    private final AdminHub hub;

    SettingsMenus(@NotNull ExyliaLobby lobby, @NotNull PluginMenus menus, @NotNull AdminHub hub) {
        this.lobby = lobby;
        this.menus = menus;
        this.hub = hub;
    }

    static List<String> menuIds() {
        List<String> ids = new ArrayList<>(List.of(HUB));
        PAGES.keySet().forEach(id -> ids.add(HUB + "_" + id));
        return ids;
    }

    static boolean hasPage(@NotNull String id) {
        return PAGES.containsKey(id);
    }

    private Optional<LobbyModule> module(String id) {
        return lobby.getRegistered().stream().filter(module -> module.getId().equals(id) && module.isEnabled()).findFirst();
    }

    private @Nullable ConfigFile<?> file(String id) {
        return module(id).map(LobbyModule::settingsFile).orElse(null);
    }

    private ConfigEdits edits(String id) {
        return ConfigEdits.of(lobby.getPlugin(), () -> file(id));
    }

    private static String name(String page, String key) {
        return page + "_" + key.replace('.', '_').replace('-', '_');
    }

    /** The placeholders of one page: the library's for every editable key, ours for the gadget cooldowns. */
    private Map<String, Object> values(String id) {
        Map<String, Object> context = new LinkedHashMap<>(edits(id).placeholders(id + "_"));
        context.put(id + "_state", (Supplier<String>) () -> module(id).isPresent()
                ? "{success}{icon:on} " + Phrases.tr("On") : "{error}{icon:off} " + Phrases.tr("Off"));
        if (id.equals(GADGETS) && file(id) != null) {
            for (String gadget : new GadgetsSettings().gadgets().keySet()) {
                String name = name(id, "gadgets." + gadget + ".cooldown");
                context.put(name, (Supplier<String>) () -> seconds(current(gadget)));
                context.put(name + "_default", (Supplier<String>) () -> seconds(defaults(gadget)));
                context.put(name + "_click", (Supplier<String>) () -> cooldownHint(gadget));
            }
        }
        return context;
    }

    void openHub(Player admin) {
        Map<String, Object> context = new LinkedHashMap<>();
        PAGES.forEach((id, shown) -> {
            for (String key : shown) {
                context.put(name(id, key), "{muted}—");
            }
            context.putAll(values(id));
        });
        menus.open(admin, HUB, context);
    }

    void openPage(Player admin, String id) {
        if (!PAGES.containsKey(id)) {
            openHub(admin);
            return;
        }
        if (file(id) == null) {
            hub.openModules(admin);
            return;
        }
        menus.open(admin, HUB + "_" + id, values(id));
    }

    void register(PluginActions actions, String permission) {
        actions.registerSync("settings_hub", permission, (action, args) -> {
            openHub(action.player());
            return ActionResult.success();
        });
        actions.registerSync("settings_page", permission, (action, args) -> {
            openPage(action.player(), args.string(0, ""));
            return ActionResult.success();
        });
        actions.registerSync("setting", permission, (action, args) -> {
            Player admin = action.player();
            String id = args.string(0, "");
            String key = args.string(1, "");
            if (!PAGES.containsKey(id)) {
                return ActionResult.success();
            }
            var click = action.get(UiKeys.CLICK).orElse(null);
            Matcher cooldown = COOLDOWN.matcher(key);
            if (id.equals(GADGETS) && cooldown.matches()) {
                cooldown(admin, cooldown.group(1), click != null && click.name().equals("SHIFT_RIGHT"));
            } else {
                edits(id).card(admin, key, click, () -> openPage(admin, id));
            }
            return ActionResult.success();
        });
    }

    // ======================== gadget cooldowns ========================

    @SuppressWarnings("unchecked")
    private @Nullable ConfigFile<GadgetsSettings> gadgets() {
        return (ConfigFile<GadgetsSettings>) file(GADGETS);
    }

    private double current(String gadget) {
        ConfigFile<GadgetsSettings> file = gadgets();
        GadgetsSettings.Gadget found = file == null ? null : file.get().gadgets().get(gadget);
        return found == null ? defaults(gadget) : found.cooldown();
    }

    private static double defaults(String gadget) {
        GadgetsSettings.Gadget found = new GadgetsSettings().gadgets().get(gadget);
        return found == null ? 0 : found.cooldown();
    }

    private static String seconds(double value) {
        return "{info}" + (value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value)) + "s";
    }

    private String cooldownHint(String gadget) {
        List<String> lines = new ArrayList<>();
        lines.add("{primary}{icon:click} " + Phrases.tr("Click to change"));
        lines.add("{letters_black}" + Phrases.tr("Type the new value."));
        if (current(gadget) != defaults(gadget)) {
            lines.add("{primary}{icon:right_click} " + Phrases.tr("Shift-right-click {letters_black}· {letters}reset"));
        }
        return Lines.join(lines);
    }

    private void cooldown(Player admin, String gadget, boolean reset) {
        ConfigFile<GadgetsSettings> file = gadgets();
        if (file == null) {
            return;
        }
        if (reset) {
            write(file, admin, gadget, defaults(gadget));
            return;
        }
        PluginInputs inputs = Inputs.of(lobby.getPlugin());
        inputs.answered(admin, inputs.decimal(admin, Phrases.tr("Type the new value."))
                        .range(BigDecimal.ZERO, BigDecimal.valueOf(3600))
                        .defaultValue(BigDecimal.valueOf(current(gadget))).open(),
                value -> write(file, admin, gadget, value.doubleValue()), () -> openPage(admin, GADGETS));
    }

    private void write(ConfigFile<GadgetsSettings> file, Player admin, String gadget, double seconds) {
        file.updateAsync(settings -> settings.withCooldown(gadget, seconds)).thenRun(
                () -> Tasks.of(lobby.getPlugin()).runAtEntity(admin, () -> openPage(admin, GADGETS)));
    }
}

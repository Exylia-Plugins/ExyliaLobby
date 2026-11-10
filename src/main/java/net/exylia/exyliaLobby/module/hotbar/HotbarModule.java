package net.exylia.exyliaLobby.module.hotbar;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.Actions;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.hotbar.HotbarItem;
import net.exylia.lib.hotbar.HotbarSet;
import net.exylia.lib.hotbar.Hotbars;
import net.exylia.lib.item.Item;
import net.exylia.lib.settings.Settings;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The lobby hotbar: a named set of buttons from the module's catalogue, drawn on entering the lobby and taken off on
 * leaving. Buttons are overlays (nothing real is written into the inventory).
 *
 * <p>Other modules use {@link #swordSlot()} and {@link #reserve(int)} (the lobby PvP sword), {@link #show},
 * {@link #hide} and {@link #refresh} (hand the hotbar over while a player is in a run or a fight).
 */
public final class HotbarModule extends LobbyModule {

    public static final String ID = "hotbar";
    static final String SET = "lobby";

    private ConfigFile<HotbarSettings> settings;
    private ConfigFile<HotbarMessages> messages;
    private Hotbars hotbars;
    private final Set<Integer> reserved = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> logged = new ConcurrentHashMap<>();
    private volatile String signature = "";
    private volatile String registeredSet = "";

    public HotbarModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.COMPASS;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(HotbarSettings.class);
        messages = messages(HotbarMessages.class);
    }

    @Override
    protected void start() {
        if (!Hotbars.isAvailable()) {
            once("packets", messages.get().noPackets());
            return;
        }
        if (practiceInCharge()) once("practice", messages.get().practiceInCharge());
        hotbars = Hotbars.of(plugin(), Actions.of(plugin()))
                .onCooldown((player, left) -> PluginMessages.of(plugin()).send(player, messages.get().cooling(),
                        Values.of("seconds", Math.max(1, (long) Math.ceil(left.toMillis() / 1000.0)))));
        settings.onReload(reloaded -> {
            if (isEnabled()) rebuild();
        });
    }

    @Override
    protected void stop() {
        if (hotbars != null) Hotbars.release(plugin());
        hotbars = null;
        // Reserved slots stay: the modules that reserved them may outlive this switch-off.
        signature = "";
    }

    private void once(String key, String line) {
        if (logged.put(key, true) == null) plugin().getLogger().info(line);
    }

    private boolean practiceInCharge() {
        return Bukkit.getPluginManager().getPlugin("ExyliaPracticeCore") != null && !settings.get().force();
    }

    // ======================== public hooks ========================

    /** The slot (0-8) the lobby PvP sword lives in; no button is drawn there. */
    public int swordSlot() {
        return settings.get().swordSlot();
    }

    /**
     * Keeps a slot free of buttons (the gadgets module reserves its slot); the returned handle frees it. Kept while
     * this module is off, so reserve through {@code context.registered} to survive it being switched on later.
     */
    public @NotNull Runnable reserve(int slot) {
        // Deferred: modules reserve while the plugin starts, before the actions the set names are all registered.
        if (reserved.add(slot)) context.tasks().run(this::rebuildIfRunning);
        return () -> {
            if (reserved.remove(slot)) context.tasks().run(this::rebuildIfRunning);
        };
    }

    /** Whether a slot is kept free: the configured sword slot or one reserved through {@link #reserve}. */
    public boolean reserved(int slot) {
        return slot == swordSlot() || reserved.contains(slot);
    }

    /** Draws the lobby hotbar on a player (back from a run or a fight). False when it cannot be drawn. */
    public boolean show(@NotNull Player player) {
        if (hotbars == null || practiceInCharge()) return false;
        ensureSet();
        return hotbars.show(player, SET);
    }

    /** Takes the lobby hotbar off a player (into a run or a fight). */
    public void hide(@NotNull Player player) {
        if (hotbars != null) hotbars.hide(player);
    }

    /** Redraws a player's buttons, after a permission or a state shown on a button changed. */
    public void refresh(@NotNull Player player) {
        if (hotbars != null) hotbars.refresh(player);
    }

    // ======================== lobby crossings ========================

    @Override
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        show(player);
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        hide(player);
    }

    // ======================== the set ========================

    /** Builds the set again when another module was switched since, so buttons of off modules never show. */
    private void ensureSet() {
        String now = context.modules().all().stream().filter(module -> module.isEnabled()).map(module -> module.getId())
                .sorted().reduce("", (a, b) -> a + "," + b);
        if (!now.equals(signature) || registeredSet.isEmpty()) rebuild();
    }

    private void rebuildIfRunning() {
        if (isEnabled() && hotbars != null) rebuild();
    }

    private synchronized void rebuild() {
        if (hotbars == null) return;
        String now = context.modules().all().stream().filter(module -> module.isEnabled()).map(module -> module.getId())
                .sorted().reduce("", (a, b) -> a + "," + b);
        signature = now;
        HotbarSettings current = settings.get();
        HotbarSettings.ItemSet chosen = current.sets().get(current.set());
        if (chosen == null && !current.sets().isEmpty()) chosen = current.sets().values().iterator().next();
        List<HotbarItem> items = new ArrayList<>();
        if (chosen != null) {
            for (Map.Entry<String, HotbarSettings.Button> entry : chosen.items().entrySet()) {
                HotbarSettings.Button button = entry.getValue();
                if (button.slot() < 0 || button.slot() > 8 || reserved(button.slot())) continue;
                if (!button.requires().isBlank() && !context.modules().isEnabled(button.requires())) continue;
                Item look = Item.of(button.material()).name(button.name()).lore(button.lore()).build();
                HotbarItem item = HotbarItem.of(entry.getKey(), button.slot(), look)
                        .withActions(button.actions())
                        .withCommands(button.commands())
                        .withCooldown(button.cooldown());
                if (!button.permission().isBlank()) item = item.withPermission(button.permission(), null);
                items.add(item);
            }
        }
        HotbarSet set = HotbarSet.of(SET, items);
        set = set.withInventory(set.lock(), current.hideRest(), set.pickup());
        try {
            hotbars.register(set);
            registeredSet = current.set();
        } catch (IllegalArgumentException invalid) {
            context.debug().error("The hotbar set '" + current.set() + "' has an action that does not exist: " + invalid.getMessage(), invalid);
        }
    }

    @Override
    protected void actions(PluginActions actions) {
        actions.registerSync("hotbar_settings", (action, args) -> {
            Settings.open(action.player());
            return ActionResult.success();
        });
    }
}

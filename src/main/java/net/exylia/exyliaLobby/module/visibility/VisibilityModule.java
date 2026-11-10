package net.exylia.exyliaLobby.module.visibility;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.hotbar.HotbarModule;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.clan.Clans;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.placeholder.Placeholders;
import net.exylia.lib.settings.Setting;
import net.exylia.lib.settings.Settings;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;


/**
 * Players choose to see everyone, friends and staff, or nobody while they are in the lobby. The choice is an
 * ExyliaLib setting (kept for the player, editable in {@code /settings}) and a hotbar button.
 *
 * <p>Other modules use {@link #mode(Player)} and {@link #cycle(Player)}.
 */
public final class VisibilityModule extends LobbyModule {

    public static final String ID = "visibility";
    static final String KEY = "mode";
    private static final String RULE = "visibility";
    private static final String COOLDOWN = "visibility_switch";

    private ConfigFile<VisibilitySettings> settings;
    private ConfigFile<VisibilityMessages> messages;

    public VisibilityModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.ENDER_EYE;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(VisibilitySettings.class);
        messages = messages(VisibilityMessages.class);
    }

    @Override
    protected void start() {
        register();
        settings.onReload(reloaded -> {
            if (isEnabled()) register();
        });
        context.visibility().add(RULE, this::canSee);
        Placeholders.group(plugin(), "lobby")
                .add("visibility_state", request -> request.hasViewer() && isEnabled() ? state(mode(request.viewer())) : "")
                .register();
    }

    @Override
    protected void stop() {
        context.visibility().remove(RULE);
        Settings.unregister(plugin(), KEY);
    }

    private void register() {
        VisibilityMessages text = messages.get();
        Settings.category(plugin(), "lobby", "COMPASS", text.category(), text.categoryText().toArray(String[]::new));
        Settings.register(plugin(), Setting.choice(KEY, settings.get().defaultMode().key(), "everyone", "friends", "nobody")
                .category("lobby")
                .icon("ENDER_EYE")
                .name(text.name())
                .description(text.description())
                .label("everyone", text.labelEveryone())
                .label("friends", text.labelFriends())
                .label("nobody", text.labelNobody())
                .available(context.visibility()::available, text.unavailable())
                .build());
    }

    // ======================== the rule ========================

    /** What a player chose to see. Memory only; safe from any thread. */
    public @NotNull VisibilitySettings.Mode mode(@NotNull Player player) {
        VisibilitySettings.Mode fallback = settings.get().defaultMode();
        String value = Settings.value(player, plugin(), KEY);
        return value == null ? fallback : VisibilitySettings.Mode.of(value, fallback);
    }

    private boolean canSee(Player viewer, Player target) {
        if (viewer.equals(target) || !context.areas().contains(viewer) || !context.areas().contains(target)) return true;
        VisibilitySettings config = settings.get();
        return switch (mode(viewer)) {
            case EVERYONE -> true;
            case NOBODY -> staff(target);
            case FRIENDS -> staff(target) || (config.clanFriends() && Clans.isSupported() && Clans.areInSameClan(viewer, target));
        };
    }

    private boolean staff(Player target) {
        String permission = settings.get().staffPermission();
        return !permission.isBlank() && target.hasPermission(permission);
    }

    private String state(VisibilitySettings.Mode mode) {
        VisibilityMessages text = messages.get();
        return switch (mode) {
            case EVERYONE -> text.stateEveryone();
            case FRIENDS -> text.stateFriends();
            case NOBODY -> text.stateNobody();
        };
    }

    // ======================== switching ========================

    /** Moves a player to the next mode (hotbar button), honouring the cooldown. Returns the new mode, or null when too soon. */
    public VisibilitySettings.Mode cycle(@NotNull Player player) {
        if (cooldowns().isActive(player, COOLDOWN)) {
            refuseCooling(player, COOLDOWN, messages.get().cooling(), Values.of());
            return null;
        }
        VisibilitySettings.Mode next = mode(player).next();
        if (!Settings.set(player, plugin(), KEY, next.key())) return null;
        cooldowns().start(player, COOLDOWN, settings.get().cooldown());
        changed(player, next);
        return next;
    }

    private void changed(Player player, VisibilitySettings.Mode next) {
        PluginMessages.of(plugin()).send(player, messages.get().changed(), Values.of("state", state(next)).putText("mode", next.key()));
        context.visibility().refreshAll();
        context.module(HotbarModule.ID, HotbarModule.class).ifPresent(hotbar -> hotbar.refresh(player));
    }

    @Override
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        context.visibility().refreshAll();
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        cooldowns().clear(player, COOLDOWN);
        context.visibility().refresh(player);
    }

    @Override
    protected void actions(PluginActions actions) {
        actions.registerSync("visibility_toggle", (action, args) -> {
            cycle(action.player());
            return ActionResult.success();
        });
    }
}

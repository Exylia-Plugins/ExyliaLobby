package net.exylia.exyliaLobby.module.state;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Game mode, health, hunger, potion effects, flight, time, weather and the XP bar inside the lobby.
 *
 * <p>Whatever {@code enter} changes is written to a snapshot first and {@code leave} puts exactly that back.
 */
public final class StateModule extends LobbyModule implements Listener {

    public static final String ID = "state";

    private ConfigFile<StateSettings> settings;
    private final Map<UUID, Saved> saved = new ConcurrentHashMap<>();
    /** What each player got back on leaving, for a late snapshot restore that would put the lobby's state back. */
    private final Map<UUID, Returned> returned = new ConcurrentHashMap<>();
    /** On the player, saved with their data: what they had before the lobby, so a crash does not lose it. */
    private NamespacedKey crashKey;

    private record Returned(GameMode mode, boolean flyTouched, boolean allowFlight, boolean flying,
                            List<PotionEffectType> applied) {
    }

    /** What one player had before the lobby changed it; a null or false field means it was not touched. */
    private static final class Saved {
        GameMode mode;
        double health;
        int food;
        float saturation;
        boolean flyTouched;
        boolean allowFlight;
        boolean flying;
        List<PotionEffect> effects = new ArrayList<>();
        List<PotionEffectType> applied = new ArrayList<>();
        boolean timeTouched;
        long timeOffset;
        boolean timeRelative;
        boolean weatherTouched;
        WeatherType weather;
        boolean xpTouched;
        int level;
        float exp;
        int total;
    }

    public StateModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.TOTEM_OF_UNDYING;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(StateSettings.class);
    }

    @Override
    protected void start() {
        crashKey = new NamespacedKey(plugin(), "lobby_state");
        listen(this);
    }

    /**
     * Puts back what this player had before the lobby (mode, flight) and takes the lobby's effects off again, once
     * something else restored a snapshot taken inside the lobby after they left.
     */
    public void reapply(@NotNull Player player) {
        Returned back = returned.get(player.getUniqueId());
        if (back == null || saved.containsKey(player.getUniqueId())) return;
        if (back.mode() != null && player.getGameMode() != back.mode()) player.setGameMode(back.mode());
        if (back.flyTouched()) {
            player.setAllowFlight(back.allowFlight());
            player.setFlying(back.allowFlight() && back.flying());
        }
        for (PotionEffectType type : back.applied()) {
            PotionEffect effect = player.getPotionEffect(type);
            if (effect != null && effect.isInfinite()) player.removePotionEffect(type);
        }
    }

    @Override
    protected void stop() {
        saved.clear();
        returned.clear();
    }

    @Override
    protected void timers() {
        if (!settings.get().xpBar()) return;
        timer(20L, 20L, () -> {
            for (UUID id : context.areas().players()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && saved.containsKey(id)) context.tasks().runAtEntity(player, () -> showXp(player));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !settings.get().fullHunger()) return;
        if (event.getFoodLevel() < player.getFoodLevel() && saved.containsKey(player.getUniqueId())) event.setCancelled(true);
    }

    @Override
    @SuppressWarnings("deprecation") // Player#getMaxHealth reads the same on every supported version
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        if (saved.containsKey(player.getUniqueId())) return;
        returned.remove(player.getUniqueId());
        StateSettings now = settings.get();
        Saved before = new Saved();
        saved.put(player.getUniqueId(), before);

        // Still marked from a lobby visit that never ended (a crash): what they have now is the lobby's.
        String[] crashed = crashed(player);
        GameMode current = player.getGameMode();
        boolean staff = current == GameMode.CREATIVE || current == GameMode.SPECTATOR;
        if (now.changeGameMode() && (!staff || now.overrideStaffModes())) {
            before.mode = crashed == null ? current : crashed[0].equals("-") ? null : GameMode.valueOf(crashed[0]);
            player.setGameMode(now.gameMode());
        }

        before.health = player.getHealth();
        if (now.fullHealth()) player.setHealth(player.getMaxHealth());
        before.food = player.getFoodLevel();
        before.saturation = player.getSaturation();
        if (now.fullHunger()) {
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }

        if (now.clearEffects()) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (!lobbyEffect(effect, now)) before.effects.add(effect);
                player.removePotionEffect(effect.getType());
            }
        }
        for (StateSettings.Effect entry : now.effects()) {
            PotionEffectType type = type(entry.effect());
            if (type == null) {
                context.debug().warn("State: unknown potion effect '" + entry.effect() + "'");
                continue;
            }
            PotionEffect previous = player.getPotionEffect(type);
            if (previous != null && !now.clearEffects() && !lobbyEffect(previous, now)) before.effects.add(previous);
            before.applied.add(type);
            player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, Math.max(0, entry.level() - 1),
                    false, entry.particles(), true));
        }

        boolean allowFlight = crashed != null ? Boolean.parseBoolean(crashed[1]) : player.getAllowFlight();
        boolean flying = crashed != null ? Boolean.parseBoolean(crashed[2]) : player.isFlying();
        if (now.fly() && !now.flyPermission().isBlank() && player.hasPermission(now.flyPermission())) {
            before.flyTouched = true;
            before.allowFlight = allowFlight;
            before.flying = flying;
            player.setAllowFlight(true);
        } else if (before.mode != null || crashed != null) {
            before.allowFlight = allowFlight;
            before.flying = flying;
            before.flyTouched = true;
        }
        if (crashed == null) {
            player.getPersistentDataContainer().set(crashKey, PersistentDataType.STRING,
                    (before.mode == null ? "-" : before.mode.name()) + ";" + allowFlight + ";" + flying);
        }

        if (now.time() >= 0) {
            before.timeTouched = true;
            before.timeOffset = player.getPlayerTimeOffset();
            before.timeRelative = player.isPlayerTimeRelative();
            player.setPlayerTime(now.time(), false);
        }
        if (now.weather() != StateSettings.Weather.OFF) {
            before.weatherTouched = true;
            before.weather = player.getPlayerWeather();
            player.setPlayerWeather(now.weather() == StateSettings.Weather.CLEAR ? WeatherType.CLEAR : WeatherType.DOWNFALL);
        }
        if (now.xpBar()) {
            before.xpTouched = true;
            before.level = player.getLevel();
            before.exp = player.getExp();
            before.total = player.getTotalExperience();
            showXp(player);
        }
    }

    @Override
    @SuppressWarnings("deprecation") // Player#getMaxHealth reads the same on every supported version
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        Saved before = saved.remove(player.getUniqueId());
        if (before == null) return;
        player.getPersistentDataContainer().remove(crashKey);

        for (PotionEffectType type : before.applied) player.removePotionEffect(type);
        player.addPotionEffects(before.effects);

        if (before.mode != null) player.setGameMode(before.mode);
        returned.put(player.getUniqueId(), new Returned(before.mode, before.flyTouched, before.allowFlight, before.flying,
                List.copyOf(before.applied)));
        if (before.flyTouched) {
            player.setAllowFlight(before.allowFlight);
            player.setFlying(before.allowFlight && before.flying);
        }
        player.setHealth(Math.max(0.5, Math.min(before.health, player.getMaxHealth())));
        player.setFoodLevel(before.food);
        player.setSaturation(before.saturation);

        if (before.timeTouched) {
            if (before.timeOffset == 0 && before.timeRelative) player.resetPlayerTime();
            else player.setPlayerTime(before.timeOffset, before.timeRelative);
        }
        if (before.weatherTouched) {
            if (before.weather == null) player.resetPlayerWeather();
            else player.setPlayerWeather(before.weather);
        }
        if (before.xpTouched) {
            player.setTotalExperience(before.total);
            player.setLevel(before.level);
            player.setExp(before.exp);
        }
    }

    private void showXp(Player player) {
        StateSettings now = settings.get();
        int level = 0;
        try {
            level = Math.max(0, Integer.parseInt(Text.of(now.xpLevel()).forPlayer(player).plain().trim()));
        } catch (NumberFormatException notANumber) {
            // an unresolved placeholder shows an empty bar rather than an error
        }
        player.setLevel(level);
        player.setExp((float) Math.max(0, Math.min(0.999, now.xpProgress())));
    }

    /** The mode, flight and flying a lobby visit that never ended recorded, or null when there is none. */
    private String[] crashed(Player player) {
        String value = player.getPersistentDataContainer().get(crashKey, PersistentDataType.STRING);
        if (value == null) return null;
        String[] parts = value.split(";");
        if (parts.length != 3) return null;
        try {
            if (!parts[0].equals("-")) GameMode.valueOf(parts[0]);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        return parts;
    }

    /** One of the configured lobby effects, as {@code enter} gives it: never the player's own. */
    private static boolean lobbyEffect(PotionEffect effect, StateSettings now) {
        if (!effect.isInfinite()) return false;
        for (StateSettings.Effect entry : now.effects()) {
            if (effect.getType().equals(type(entry.effect())) && effect.getAmplifier() == Math.max(0, entry.level() - 1)) {
                return true;
            }
        }
        return false;
    }

    private static PotionEffectType type(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.EFFECT.get(key);
    }
}

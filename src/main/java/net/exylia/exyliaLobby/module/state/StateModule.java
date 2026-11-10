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
        listen(this);
    }

    @Override
    protected void stop() {
        saved.clear();
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
        StateSettings now = settings.get();
        Saved before = new Saved();
        saved.put(player.getUniqueId(), before);

        GameMode current = player.getGameMode();
        boolean staff = current == GameMode.CREATIVE || current == GameMode.SPECTATOR;
        if (now.changeGameMode() && (!staff || now.overrideStaffModes())) {
            before.mode = current;
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
            before.effects.addAll(player.getActivePotionEffects());
            before.effects.forEach(effect -> player.removePotionEffect(effect.getType()));
        }
        for (StateSettings.Effect entry : now.effects()) {
            PotionEffectType type = type(entry.effect());
            if (type == null) {
                context.debug().warn("State: unknown potion effect '" + entry.effect() + "'");
                continue;
            }
            PotionEffect previous = player.getPotionEffect(type);
            if (previous != null && !now.clearEffects()) before.effects.add(previous);
            before.applied.add(type);
            player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, Math.max(0, entry.level() - 1),
                    false, entry.particles(), true));
        }

        if (now.fly() && !now.flyPermission().isBlank() && player.hasPermission(now.flyPermission())) {
            before.flyTouched = true;
            before.allowFlight = player.getAllowFlight();
            before.flying = player.isFlying();
            player.setAllowFlight(true);
        } else if (before.mode != null) {
            before.allowFlight = player.getAllowFlight();
            before.flying = player.isFlying();
            before.flyTouched = true;
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

        for (PotionEffectType type : before.applied) player.removePotionEffect(type);
        player.addPotionEffects(before.effects);

        if (before.mode != null) player.setGameMode(before.mode);
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

    private static PotionEffectType type(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.EFFECT.get(key);
    }
}

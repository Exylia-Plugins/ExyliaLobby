package net.exylia.exyliaLobby.module.parkour;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a run does not allow: flight, gliding, riptide, pearls, riding, game mode changes and teleports this
 * module did not make end it; launches and the blocked effects are refused while it lasts. Every switch is in
 * {@link ParkourSettings.AntiCheat}. All on the runner's own thread.
 */
final class RunGuard implements Listener {

    private final ParkourRuns runs;

    RunGuard(ParkourRuns runs) {
        this.runs = runs;
    }

    private ParkourSettings.AntiCheat rules() {
        return runs.settings().antiCheat();
    }

    private Run run(Object entity) {
        return entity instanceof Player player ? runs.runOf(player) : null;
    }

    /** A run starts: flight off, blocked effects taken away. */
    void protect(Run run) {
        Player player = run.player;
        if (rules().flight() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            run.flightBefore = player.getAllowFlight();
            player.setFlying(false);
            player.setAllowFlight(false);
        } else {
            run.flightBefore = player.getAllowFlight();
        }
        if (!rules().effects()) return;
        for (PotionEffectType type : blocked()) {
            PotionEffect effect = player.getPotionEffect(type);
            if (effect == null) continue;
            run.effectsBefore.add(effect);
            player.removePotionEffect(type);
        }
    }

    /** A run ended (it is already out of the registry): give back what {@link #protect} took. */
    void release(Run run) {
        Player player = run.player;
        if (!player.isOnline()) return;
        if (player.getAllowFlight() != run.flightBefore) player.setAllowFlight(run.flightBefore);
        for (PotionEffect effect : run.effectsBefore) player.addPotionEffect(effect);
        run.effectsBefore.clear();
    }

    private Set<PotionEffectType> blocked() {
        Set<PotionEffectType> types = new HashSet<>();
        for (String name : rules().blockedEffects()) {
            NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
            PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
            if (type != null) types.add(type);
        }
        return types;
    }

    // ======================== ends the run ========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        Run run = run(event.getPlayer());
        if (run == null || !event.isFlying() || !rules().flight()) return;
        event.setCancelled(true);
        runs.close(run, RunEnd.FLIGHT);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        Run run = run(event.getEntity());
        if (run == null || !event.isGliding() || !rules().elytra()) return;
        event.setCancelled(true);
        runs.close(run, RunEnd.ELYTRA);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRiptide(PlayerRiptideEvent event) {
        Run run = run(event.getPlayer());
        if (run != null && rules().riptide()) runs.close(run, RunEnd.RIPTIDE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        Run run = run(pearl.getShooter());
        if (run == null || !rules().pearls()) return;
        event.setCancelled(true);
        runs.close(run, RunEnd.PEARL);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChorus(PlayerItemConsumeEvent event) {
        Run run = run(event.getPlayer());
        if (run == null || event.getItem().getType() != Material.CHORUS_FRUIT || !rules().pearls()) return;
        event.setCancelled(true);
        runs.close(run, RunEnd.PEARL);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        Run run = run(event.getEntity());
        if (run == null || !rules().vehicles()) return;
        event.setCancelled(true);
        runs.close(run, RunEnd.VEHICLE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Run run = run(event.getPlayer());
        if (run != null && rules().gameMode()) runs.close(run, RunEnd.GAME_MODE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        Run run = run(event.getPlayer());
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (run == null || !rules().pearls()) return;
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        event.setCancelled(true);
        runs.close(run, RunEnd.PEARL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Run run = run(event.getPlayer());
        if (run == null || event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        if (expected(run.expected, event.getTo())) {
            run.expected = null;
            return;
        }
        if (rules().teleports()) runs.close(run, RunEnd.TELEPORT);
    }

    /** Whether a teleport lands where this module sent the runner (a safe landing may shift it a little). */
    static boolean expected(Location expected, Location to) {
        return expected != null && to != null && expected.getWorld() == to.getWorld()
                && expected.distanceSquared(to) <= 2.25;
    }

    // ======================== refused while running ========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(PlayerVelocityEvent event) {
        if (run(event.getPlayer()) != null && rules().launches()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (run(event.getEntity()) == null || !rules().effects() || event.getNewEffect() == null) return;
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        List<String> names = rules().blockedEffects();
        if (!names.isEmpty() && blocked().contains(event.getNewEffect().getType())) event.setCancelled(true);
    }
}

package net.exylia.exyliaLobby.module.protection;

import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.HashSet;
import java.util.Set;

/** The rules of {@link ProtectionSettings}, each one checked against the lobby areas and the build-mode bypass. */
final class ProtectionListener implements Listener {

    private static final Set<Material> CONTAINERS = containers();

    private final ProtectionModule module;

    ProtectionListener(ProtectionModule module) {
        this.module = module;
    }

    private boolean lobby(Location location) {
        return location != null && module.lobby().areas().contains(location);
    }

    private boolean lobby(Entity entity) {
        return entity instanceof Player player ? module.lobby().areas().contains(player) : lobby(entity.getLocation());
    }

    /** A player the rule applies to: inside the lobby and not building. */
    private boolean bound(Player player) {
        return module.lobby().areas().contains(player) && !module.bypass(player);
    }

    private boolean boundAt(Player player, Location location) {
        return lobby(location) && !module.bypass(player);
    }

    private static Player source(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    // ======================== blocks ========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (module.rules().blockBreak() && boundAt(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (module.rules().blockPlace() && boundAt(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (module.rules().blockPlace() && boundAt(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (module.rules().blockBreak() && boundAt(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (module.rules().blockBreak() && boundAt(event.getPlayer(), event.getHarvestedBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (module.rules().blockPlace() && player != null && boundAt(player, event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (module.rules().blockPlace() && player != null && boundAt(player, event.getEntity().getLocation())) event.setCancelled(true);
    }

    // ======================== using things ========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        if (!boundAt(player, block.getLocation())) return;
        ProtectionSettings rules = module.rules();
        Material type = block.getType();
        if (event.getAction() == Action.PHYSICAL) {
            if (type == Material.FARMLAND ? rules.cropTrample()
                    : rules.pressurePlates() && (Tag.PRESSURE_PLATES.isTagged(type) || type == Material.TRIPWIRE)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        boolean deny = (rules.doors() && (Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type)))
                || (rules.buttonsAndLevers() && (Tag.BUTTONS.isTagged(type) || type == Material.LEVER))
                || (rules.containers() && CONTAINERS.contains(type));
        if (deny) event.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!boundAt(event.getPlayer(), entity.getLocation())) return;
        if ((entity instanceof Hanging && module.rules().frames()) || (entity instanceof ArmorStand && module.rules().armorStands())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (module.rules().armorStands() && boundAt(event.getPlayer(), event.getRightClicked().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!module.rules().frames() || !lobby(event.getEntity().getLocation())) return;
        if (event instanceof HangingBreakByEntityEvent byEntity && byEntity.getRemover() instanceof Player player && module.bypass(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (module.rules().dropItems() && bound(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && module.rules().pickupItems() && bound(player)) event.setCancelled(true);
    }

    // ======================== damage, hunger, death ========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity ? source(byEntity.getDamager()) : null;
        if (!(event.getEntity() instanceof Player victim)) {
            if (attacker != null && module.rules().protectEntities() && boundAt(attacker, event.getEntity().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!module.lobby().areas().contains(victim)) return;
        if (attacker != null && attacker != victim && module.pvpAllowed(attacker, victim)) return;
        if (module.cancelledCauses().contains(event.getCause())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && module.rules().hunger()
                && event.getFoodLevel() < player.getFoodLevel() && module.lobby().areas().contains(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!module.lobby().areas().contains(player)) return;
        if (module.rules().hideDeathMessages()) event.deathMessage(null);
        if (module.rules().dropItems()) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (module.rules().hideAdvancementMessages() && module.lobby().areas().contains(event.getPlayer())) event.message(null);
    }

    // ======================== the world ========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (module.rules().mobSpawning() && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                && lobby(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (module.rules().explosions() && lobby(event.getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (module.rules().explosions() && lobby(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (module.rules().fireSpread() && lobby(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!lobby(event.getBlock().getLocation())) return;
        Player player = event.getPlayer();
        if (player != null) {
            if (module.rules().blockPlace() && !module.bypass(player)) event.setCancelled(true);
        } else if (module.rules().fireSpread()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent event) {
        if (module.rules().leafDecay() && lobby(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Material formed = event.getNewState().getType();
        if (module.rules().iceAndSnowForm() && frozen(formed) && lobby(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (module.rules().iceAndSnowMelt() && frozen(event.getBlock().getType()) && lobby(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!lobby(event.getBlock().getLocation())) return;
        if (event.getEntity() instanceof Player player) {
            if (module.rules().cropTrample() && !module.bypass(player)) event.setCancelled(true);
        } else if (module.rules().entityChangeBlock()) {
            event.setCancelled(true);
        }
    }

    private static boolean frozen(Material type) {
        return type == Material.SNOW || type == Material.ICE || type == Material.FROSTED_ICE || type == Material.SNOW_BLOCK
                || type == Material.POWDER_SNOW;
    }

    /** Blocks with an inventory or a screen, by name so a block missing on an older server is simply skipped. */
    private static Set<Material> containers() {
        Set<Material> set = new HashSet<>();
        for (String name : new String[]{"CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "BARREL", "FURNACE", "BLAST_FURNACE", "SMOKER",
                "HOPPER", "DROPPER", "DISPENSER", "CRAFTER", "CRAFTING_TABLE", "ENCHANTING_TABLE", "BREWING_STAND", "BEACON",
                "LOOM", "CARTOGRAPHY_TABLE", "SMITHING_TABLE", "STONECUTTER", "GRINDSTONE", "LECTERN", "JUKEBOX", "NOTE_BLOCK",
                "BELL", "COMPOSTER", "RESPAWN_ANCHOR", "DECORATED_POT", "CHISELED_BOOKSHELF", "COMMAND_BLOCK", "CAKE",
                "DRAGON_EGG", "REPEATER", "COMPARATOR", "DAYLIGHT_DETECTOR", "SWEET_BERRY_BUSH"}) {
            Material material = Material.matchMaterial(name);
            if (material != null) set.add(material);
        }
        for (Tag<Material> tag : java.util.List.of(Tag.SHULKER_BOXES, Tag.BEDS, Tag.ANVIL, Tag.ALL_SIGNS, Tag.CAULDRONS,
                Tag.FLOWER_POTS, Tag.CANDLES, Tag.CANDLE_CAKES)) {
            set.addAll(tag.getValues());
        }
        return set;
    }
}

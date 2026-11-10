package net.exylia.exyliaLobby.module.shared;

import net.exylia.lib.util.reward.OverflowPolicy;
import net.exylia.lib.util.reward.PendingRewards;
import net.exylia.lib.util.reward.PluginRewards;
import net.exylia.lib.util.reward.Rewards;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/** Puts a lobby item (the PvP sword, a gadget) in its slot without destroying the real item that was there. */
public final class Slots {

    private Slots() {
    }

    /**
     * Sets the item in the slot. Whatever the player had there moves to another free slot, or to the reward queue
     * (handed over on their next join) when the inventory is full. Player's own thread.
     */
    public static void place(@NotNull Plugin plugin, @NotNull Player player, int slot, @NotNull ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        ItemStack old = inventory.getItem(slot);
        inventory.setItem(slot, item);
        if (old == null || old.getType().isAir()) return;
        for (ItemStack rest : inventory.addItem(old).values()) queue(plugin).deliver(player.getUniqueId(), rest);
    }

    private static PluginRewards queue(Plugin plugin) {
        PluginRewards rewards = Rewards.of(plugin);
        if (rewards.pending() == null) {
            rewards.overflow(OverflowPolicy.QUEUE).pending(PendingRewards.database(plugin)).claimOnJoin();
        }
        return rewards;
    }
}

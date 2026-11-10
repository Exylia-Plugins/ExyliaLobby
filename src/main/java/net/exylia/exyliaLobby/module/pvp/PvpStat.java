package net.exylia.exyliaLobby.module.pvp;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.DataKind;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Indexed;
import net.exylia.lib.database.Owner;
import net.exylia.lib.database.Table;

import java.util.UUID;

/** One player's lobby PvP record. */
@Table(value = "lobby_pvp_stats", data = DataKind.PROGRESS)
public record PvpStat(
        @Id @Owner UUID player,
        @Column(length = 32) String name,
        @Indexed @Column int kills,
        @Column int deaths,
        @Column int streak,
        @Column("best_streak") int bestStreak) {

    public static PvpStat empty(UUID player, String name) {
        return new PvpStat(player, name, 0, 0, 0, 0);
    }

    public PvpStat withName(String newName) {
        return new PvpStat(player, newName, kills, deaths, streak, bestStreak);
    }

    public PvpStat killed(String newName) {
        int next = streak + 1;
        return new PvpStat(player, newName, kills + 1, deaths, next, Math.max(bestStreak, next));
    }

    public PvpStat died(String newName) {
        return new PvpStat(player, newName, kills, deaths + 1, 0, bestStreak);
    }
}

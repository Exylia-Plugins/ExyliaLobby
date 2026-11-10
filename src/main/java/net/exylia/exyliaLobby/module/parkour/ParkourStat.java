package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.DataKind;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Owner;
import net.exylia.lib.database.Indexed;
import net.exylia.lib.database.Table;

import java.util.UUID;

/**
 * How many times a player finished a course or an endless mode; the first finish is the one that pays a reward.
 *
 * @param id     {@code <player>|<course or mode>}
 * @param course the board name, without a period
 */
@Table(value = "lobby_parkour_stats", data = DataKind.PROGRESS)
public record ParkourStat(
        @Id(value = "id", length = 160) String id,
        @Owner @Indexed @Column("player") UUID player,
        @Column(value = "course", length = 64) String course,
        @Column("completions") int completions) {

    public static String id(UUID player, String course) {
        return player + "|" + course;
    }
}

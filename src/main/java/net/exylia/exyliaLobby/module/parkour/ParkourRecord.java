package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.DataKind;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Index;
import net.exylia.lib.database.Indexed;
import net.exylia.lib.database.Owner;
import net.exylia.lib.database.Table;

import java.util.List;
import java.util.UUID;

/**
 * A player's best on one board in one period: a course time in milliseconds (lowest first) or an endless score
 * (highest first).
 *
 * @param id     {@code <player>|<board key>}
 * @param board  {@link Board#key}: {@code tower|all}, {@code endless|w2026-11-09}
 * @param splits a course run's time at each checkpoint, in milliseconds; empty for endless modes
 * @param at     when it was set, epoch milliseconds
 */
@Table(value = "lobby_parkour_records", data = DataKind.PROGRESS)
@Index(columns = {"board", "value"}, name = "lobby_parkour_records_fastest")
@Index(columns = {"board", "value"}, descending = {"value"}, name = "lobby_parkour_records_highest")
public record ParkourRecord(
        @Id @Column(value = "id", length = 160) String id,
        @Owner @Indexed @Column("player") UUID player,
        @Column(value = "name", length = 16) String name,
        @Column(value = "board", length = 120) String board,
        @Column("value") long value,
        @Column(value = "splits", length = Column.UNBOUNDED) List<Long> splits,
        @Column("at") long at) {

    public static String id(UUID player, String boardKey) {
        return player + "|" + boardKey;
    }
}

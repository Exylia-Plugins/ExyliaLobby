package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.database.Databases;
import net.exylia.lib.database.Repository;
import net.exylia.lib.database.wipe.Wipes;
import net.exylia.lib.placeholder.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.exylia.lib.debug.Debug;
import net.exylia.lib.util.PeriodClock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Bests and leaderboards, kept in memory.
 *
 * <p>A player's rows are read when they reach the lobby and kept until they leave it; boards are read on a
 * timer and again right after a best changes on this server. Menus and placeholders read only memory.
 */
final class ParkourData {

    private final Repository<ParkourRecord> rows;
    private final Repository<ParkourStat> stats;
    private final Debug debug;
    private final Supplier<PeriodClock> clock;
    private final IntSupplier size;
    private final Map<UUID, CompletableFuture<Mine>> players = new ConcurrentHashMap<>();
    private final Map<String, List<ParkourRecord>> tops = new ConcurrentHashMap<>();

    /** One player's rows in memory: bests by board key, completions by board name. */
    private record Mine(Map<String, ParkourRecord> bests, Map<String, ParkourStat> stats) {
    }

    /**
     * What a submitted result changed.
     *
     * @param previous    the all-time best before it, null for the first one
     * @param best        the all-time best now
     * @param improved    whether it beat the all-time best
     * @param rank        the place on the all-time board now, 0 when outside the cached places
     * @param completions finishes of this board, this one included
     * @param first       whether this is the first finish ever (the reward flag)
     */
    record Outcome(@Nullable ParkourRecord previous, @NotNull ParkourRecord best, boolean improved, int rank,
                   int completions, boolean first) {
    }

    static ParkourData create(JavaPlugin plugin, Debug debug, Supplier<PeriodClock> clock, IntSupplier size) {
        var databases = Databases.of(plugin);
        return new ParkourData(databases.repository(ParkourRecord.class), databases.repository(ParkourStat.class),
                debug, clock, size);
    }

    ParkourData(Repository<ParkourRecord> rows, Repository<ParkourStat> stats, Debug debug, Supplier<PeriodClock> clock, IntSupplier size) {
        this.rows = rows;
        this.stats = stats;
        this.debug = debug;
        this.clock = clock;
        this.size = size;
    }

    // ======================== players ========================

    /**
     * Reads a player's rows once; later calls answer the same future. A failed read fails the future (so nothing
     * is written over rows that were never read) and is forgotten, so the next call reads again.
     */
    CompletableFuture<Mine> load(UUID player) {
        CompletableFuture<Mine> loaded = players.computeIfAbsent(player, id -> rows.where("player", id).find()
                .thenCombine(stats.where("player", id).find(), (found, counted) -> {
                    // ponytail: reads every period ever stored for the player; prune old period rows if this grows
                    Mine mine = new Mine(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
                    for (ParkourRecord row : found) mine.bests().put(row.board(), row);
                    for (ParkourStat row : counted) mine.stats().put(row.course(), row);
                    return mine;
                }));
        loaded.whenComplete((mine, failed) -> {
            if (failed == null) return;
            if (players.remove(player, loaded)) debug.error("Could not read the parkour records of " + player, failed);
        });
        return loaded;
    }

    void forget(UUID player) {
        players.remove(player);
    }

    /** The player's best on a board now; empty until their rows are read. */
    Optional<ParkourRecord> best(UUID player, Board board) {
        CompletableFuture<Mine> loaded = players.get(player);
        if (loaded == null || !loaded.isDone() || loaded.isCompletedExceptionally()) return Optional.empty();
        return Optional.ofNullable(loaded.join().bests().get(board.key(clock.get())));
    }

    /** The runs unit's entry point: {@link #submit} for an online player. */
    CompletableFuture<Outcome> record(Player player, String board, long value, List<Long> splits) {
        return submit(player.getUniqueId(), player.getName(), board, value, splits);
    }

    /** Finishes of a board by the player; 0 until their rows are read. */
    int completions(UUID player, String name) {
        CompletableFuture<Mine> loaded = players.get(player);
        if (loaded == null || !loaded.isDone() || loaded.isCompletedExceptionally()) return 0;
        ParkourStat stat = loaded.join().stats().get(name);
        return stat == null ? 0 : stat.completions();
    }

    /**
     * Counts a finish and stores the result on every board it beats.
     *
     * @return what changed; completes on a database thread, once the boards it touched are read again
     */
    CompletableFuture<Outcome> submit(UUID player, String name, String boardName, long value, List<Long> splits) {
        return load(player).thenCompose(mine -> {
            PeriodClock now = clock.get();
            boolean mode = Board.isMode(boardName);
            long at = System.currentTimeMillis();
            String allKey = new Board(boardName, Board.Period.ALL).key(now);
            ParkourRecord previous = mine.bests().get(allKey);
            List<Board> changed = new ArrayList<>();
            for (Board board : Standings.boardsOf(boardName)) {
                String key = board.key(now);
                ParkourRecord current = mine.bests().get(key);
                if (!Standings.beats(mode, value, current == null ? null : current.value())) continue;
                ParkourRecord row = new ParkourRecord(ParkourRecord.id(player, key), player, name, key, value,
                        List.copyOf(splits), at);
                mine.bests().put(key, row);
                rows.save(row).exceptionally(failed -> {
                    debug.error("Could not save a parkour record of " + name + " on " + key, failed);
                    return null;
                });
                changed.add(board);
            }
            ParkourStat before = mine.stats().get(boardName);
            ParkourStat stat = new ParkourStat(ParkourStat.id(player, boardName), player, boardName,
                    before == null ? 1 : before.completions() + 1);
            mine.stats().put(boardName, stat);
            stats.save(stat).exceptionally(failed -> {
                debug.error("Could not save the parkour finishes of " + name + " on " + boardName, failed);
                return null;
            });
            ParkourRecord best = mine.bests().get(allKey);
            return refresh(changed).thenApply(done -> new Outcome(previous, best, best != previous,
                    Standings.rankIn(top(new Board(boardName, Board.Period.ALL)), player), stat.completions(), before == null));
        });
    }

    /**
     * Marks a milestone of a board paid for the player, once per period of the board: milestones are claimed in
     * rising order within a run, so the row keeps the highest one paid.
     *
     * @return whether it was still unpaid; fails when the player's rows cannot be read
     */
    CompletableFuture<Boolean> claimMilestone(UUID player, Board board, int score) {
        return load(player).thenApply(mine -> {
            String key = "milestone|" + board.key(clock.get());
            boolean[] claimed = {false};
            ParkourStat row = mine.stats().compute(key, (k, paid) -> {
                if (paid != null && paid.completions() >= score) return paid;
                claimed[0] = true;
                return new ParkourStat(ParkourStat.id(player, k), player, k, score);
            });
            if (claimed[0]) stats.save(row).exceptionally(failed -> {
                debug.error("Could not save the parkour milestone " + key + " of " + player, failed);
                return null;
            });
            return claimed[0];
        });
    }

    /** Wipes the player's bests on a course, every period. */
    CompletableFuture<Void> reset(UUID player, String course) {
        return load(player).thenCompose(mine -> {
            PeriodClock now = clock.get();
            List<CompletableFuture<?>> deletes = new ArrayList<>();
            List<Board> boards = Standings.boardsOf(course);
            for (Board board : boards) {
                String key = board.key(now);
                if (mine.bests().remove(key) != null) deletes.add(rows.delete(ParkourRecord.id(player, key)));
            }
            return CompletableFuture.allOf(deletes.toArray(CompletableFuture[]::new)).thenRun(() -> refresh(boards));
        });
    }

    // ======================== boards ========================

    /** The cached board, best first; empty until read. */
    List<ParkourRecord> top(Board board) {
        return tops.getOrDefault(board.key(clock.get()), List.of());
    }

    /** The player's place on a cached board, from 1; 0 when they are not on it. */
    // ponytail: only the cached places are ranked; a player below them reads 0 until a count-based rank is needed
    int rank(UUID player, Board board) {
        return Standings.rankIn(top(board), player);
    }

    /** Reads boards again; completes when every one is in memory. */
    CompletableFuture<Void> refresh(Collection<Board> boards) {
        PeriodClock now = clock.get();
        Set<String> wanted = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<?>> reads = new ArrayList<>();
        for (Board board : boards) {
            String key = board.key(now);
            wanted.add(key);
            var query = rows.where("board", key);
            query = board.isMode() ? query.orderByDescending("value") : query.orderBy("value");
            reads.add(query.limit(Math.max(1, size.getAsInt())).find()
                    .thenAccept(found -> tops.put(key, List.copyOf(found)))
                    .exceptionally(failed -> {
                        debug.error("Could not read the parkour board " + key, failed);
                        return null;
                    }));
        }
        return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new));
    }

    /** Reads every board in use and forgets those of past periods. */
    CompletableFuture<Void> refreshAll(Collection<String> courses) {
        List<Board> boards = new ArrayList<>();
        for (String course : courses) boards.addAll(Standings.boardsOf(course));
        boards.addAll(Standings.boardsOf(Board.ENDLESS));
        boards.addAll(Standings.boardsOf(Board.SPRINT));
        boards.addAll(Standings.boardsOf(Board.DAILY));
        PeriodClock now = clock.get();
        Set<String> live = new java.util.HashSet<>();
        for (Board board : boards) live.add(board.key(now));
        tops.keySet().retainAll(live);
        return refresh(boards);
    }

    // ======================== wiring ========================

    /** Drops what a reset player left in memory, and registers the {@code %lobby_parkour_*%} placeholders. */
    void register(JavaPlugin plugin) {
        Wipes.of(plugin).onPlayerReset(this::forget);
        Placeholders.group(plugin, "lobby").verbatim()
                .add("parkour_best", r -> {
                    Player viewer = r.viewer();
                    Board board = Board.parse(r.arg(0, ""));
                    if (viewer == null) return "";
                    return best(viewer.getUniqueId(), board).map(row -> show(board, row.value())).orElse("-");
                })
                .add("parkour_endless_best", r -> {
                    Player viewer = r.viewer();
                    Board board = new Board(Board.ENDLESS, Board.Period.ALL);
                    if (viewer == null) return "";
                    return best(viewer.getUniqueId(), board).map(row -> show(board, row.value())).orElse("0");
                })
                .add("parkour_top", r -> top(r.arg(0, "")))
                .register();
    }

    static String show(Board board, long value) {
        return board.isMode() ? Long.toString(value) : Times.format(value);
    }

    /** {@code <board>_<n>_name} or {@code <board>_<n>_value}; the board may hold dashes and underscores. */
    private String top(String text) {
        boolean name = text.endsWith("_name");
        if (!name && !text.endsWith("_value")) return "";
        String rest = text.substring(0, text.lastIndexOf('_'));
        int cut = rest.lastIndexOf('_');
        if (cut < 1) return "";
        int place;
        try {
            place = Integer.parseInt(rest.substring(cut + 1));
        } catch (NumberFormatException bad) {
            return "";
        }
        Board board = Board.parse(rest.substring(0, cut));
        List<ParkourRecord> top = top(board);
        if (place < 1 || place > top.size()) return name ? "-" : "";
        ParkourRecord row = top.get(place - 1);
        return name ? row.name() : show(board, row.value());
    }
}

package net.exylia.exyliaLobby.module.shared;

import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.lib.hologram.Holograms;
import net.exylia.lib.task.TaskHandle;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The text boards (a parkour top, a PvP ranking) a module draws with ExyliaLib's holograms. Lines are read again every
 * two seconds and sent only when they changed. A module owns one instance and calls {@link #clear} from {@code stop()}.
 */
public final class Boards {

    private final LobbyContext context;
    private final Map<String, Supplier<List<String>>> boards = new ConcurrentHashMap<>();
    private final Map<String, List<String>> shown = new ConcurrentHashMap<>();
    private TaskHandle refresher;

    public Boards(@NotNull LobbyContext context) {
        this.context = context;
    }

    /** Draws a board; placing the same id again replaces it. */
    public synchronized void place(@NotNull String id, @NotNull Location at, @NotNull Supplier<List<String>> lines) {
        if (!Holograms.isSupported()) return;
        List<String> now = lines.get();
        boards.put(id, lines);
        shown.put(id, now);
        Holograms.show(context.plugin(), id, at.clone(), HologramLook.text(now, 48, 0));
        if (refresher == null) refresher = context.tasks().runTimer(40L, 40L, this::refresh);
    }

    public synchronized void remove(@NotNull String id) {
        shown.remove(id);
        if (boards.remove(id) != null) Holograms.remove(context.plugin(), id);
    }

    public synchronized void clear() {
        for (String id : boards.keySet()) Holograms.remove(context.plugin(), id);
        boards.clear();
        shown.clear();
        if (refresher != null) refresher.cancel();
        refresher = null;
    }

    private void refresh() {
        boards.forEach((id, lines) -> {
            List<String> now = lines.get();
            if (now.equals(shown.get(id))) return;
            shown.put(id, now);
            Holograms.get(context.plugin(), id).ifPresent(hologram -> hologram.lines(now));
        });
    }
}

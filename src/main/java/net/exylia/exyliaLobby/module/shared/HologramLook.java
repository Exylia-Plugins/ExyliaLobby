package net.exylia.exyliaLobby.module.shared;

import net.exylia.lib.hologram.HologramConfig;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** The one way the hub modules build the config of a text hologram from plain lines. */
public final class HologramLook {

    private HologramLook() {
    }

    /** A text hologram with these lines, visible within {@code viewDistance} blocks, shifted up by {@code offsetY}. */
    public static @NotNull HologramConfig text(@NotNull List<String> lines, double viewDistance, double offsetY) {
        HologramConfig base = new HologramConfig();
        return new HologramConfig(true, HologramConfig.Kind.TEXT, lines, base.item(), base.block(), false, viewDistance,
                0, offsetY, 0, base.properties(), base.config());
    }
}

package net.exylia.exyliaLobby.module.holograms;

import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code modules/holograms/config.yml}: text boards placed in game from /lobbyadmin → Holograms. */
public record HologramsSettings(
        @Comment("The holograms, by name. Lines take the palette tokens, hologram tags and any placeholder, for example %lobby_hud_here%.")
        Map<String, Hologram> holograms) {

    public HologramsSettings() {
        this(new LinkedHashMap<>());
    }

    /**
     * One hologram.
     *
     * @param lines        the text, one entry per line
     * @param viewDistance how close a player has to be to see it
     */
    public record Hologram(String world, double x, double y, double z, List<String> lines, double viewDistance) {
        public Hologram() {
            this("world", 0, 64, 0, List.of("{primary}&lWELCOME", "{letters}Players here{letters_black}: {info}%lobby_hud_here%"), 48);
        }
    }
}

package net.exylia.exyliaLobby.module.games;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code modules/games/config.yml}. */
public record GamesSettings(
        @Comment("Labels of the player command that opens the games screen. Empty removes the command.")
        List<String> command) {

    public GamesSettings() {
        this(List.of("games"));
    }
}

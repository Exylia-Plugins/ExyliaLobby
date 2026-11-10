package net.exylia.exyliaLobby.module.games;

import net.exylia.lib.config.Comment;

/** {@code lang/<language>/modules/games/messages.yml}. */
public record GamesMessages(
        @Comment("Said when no plugin on this server offers a game yet.")
        String empty) {

    public GamesMessages() {
        this("ENTITY_VILLAGER_NO %prefix% {error}There are no games on this server yet.");
    }
}

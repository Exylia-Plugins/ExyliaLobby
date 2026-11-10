package net.exylia.exyliaLobby.module.hud;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/hud/messages.yml}. */
public record HudMessages(
        @Comment("The announcements channel as /settings lists it.")
        String channelName,
        List<String> channelDescription) {

    public HudMessages() {
        this("Lobby announcements", List.of("Tips and news the lobby", "shows now and then."));
    }
}

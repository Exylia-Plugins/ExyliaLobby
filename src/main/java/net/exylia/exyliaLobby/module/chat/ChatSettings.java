package net.exylia.exyliaLobby.module.chat;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Menu;

/** {@code modules/chat/config.yml}: chat control inside the lobby. No formatting: keep your chat plugin for that. */
public record ChatSettings(
        @Comment("Permission that ignores the lock and slow mode, and keeps a player's chat when it is cleared.")
        String bypassPermission,

        @Comment("Start with the chat locked. /lobbyadmin chat lock switches it live; a restart goes back to this.")
        boolean lockedAtStart,

        @Comment("Seconds between two messages of one player. 0 is off. /lobbyadmin chat slow <seconds> changes it live.")
        @Menu.Unit("s") @Menu.Range(min = 0, max = 3600)
        int slowModeSeconds,

        @Comment("How many blank lines /lobbyadmin chat clear sends.")
        @Menu.Range(min = 1, max = 500, step = 10)
        int clearLines) {

    public ChatSettings() {
        this("exylialobby.chat.bypass", false, 0, 100);
    }
}

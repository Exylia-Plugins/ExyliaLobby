package net.exylia.exyliaLobby.module.hotbar;

import net.exylia.lib.config.Comment;

/** {@code lang/<language>/modules/hotbar/messages.yml}. */
public record HotbarMessages(
        @Comment("A button pressed again too soon. %seconds% is the time left.")
        String cooling,
        @Comment("Logged once when ExyliaPracticeCore keeps its own lobby hotbar.")
        String practiceInCharge,
        @Comment("Logged once when PacketEvents is missing, so no hotbar can be drawn.")
        String noPackets) {

    public HotbarMessages() {
        this("[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {warning}Wait {highlight}%seconds%s{warning} before using that again.",
                "ExyliaPracticeCore is installed: its lobby hotbar stays in charge. Set 'force: true' in modules/hotbar/config.yml to show this one.",
                "PacketEvents is not installed: the lobby hotbar needs it and is not shown.");
    }
}

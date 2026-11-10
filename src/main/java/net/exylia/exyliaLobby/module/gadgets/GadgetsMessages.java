package net.exylia.exyliaLobby.module.gadgets;

import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code lang/<language>/modules/gadgets/messages.yml}. */
public record GadgetsMessages(
        @Comment("Chat lines. %gadget% is the gadget's name, %time% the cooldown left (0.4s).")
        String cooling,
        String noPermission,
        String equipped,
        String putAway,
        String unavailable,
        String notNow,
        @Comment("The preference in the player settings screen.")
        String settingCategory,
        List<String> settingCategoryText,
        String settingName,
        List<String> settingDescription,
        @Comment("Card words of the gadgets menu.")
        String kind,
        String kindEquipped,
        String kindLocked,
        String labelCooldown,
        String labelNeeds,
        String lockedNote,
        String clickEquip,
        String clickEquipNext,
        String clickPut,
        String clickPutNext,
        @Comment("Every gadget: its name, a description (up to three lines), the click it uses and what that does.")
        Map<String, Card> cards) {

    /** The words of one gadget. */
    public record Card(String name, List<String> text, String click, String use) {
        public Card() {
            this("Gadget", List.of(), "Right-click", "use it");
        }
    }

    public GadgetsMessages() {
        this("[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {warning}%gadget%{warning} is cooling down: {highlight}%time%{warning}.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}You do not have %gadget%{error} yet.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {highlight}%gadget%{letters} is in your hotbar.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}You put your gadget away.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}That gadget cannot be used on this server.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {warning}You cannot use a gadget right now.",
                "Gadgets",
                List.of("Toys to play with in the lobby."),
                "Gadgets affect me",
                List.of("Whether other players' gadgets", "can push you or paint your floor."),
                "Gadget",
                "Gadget · Equipped",
                "Gadget · Locked",
                "Cooldown",
                "Needs",
                "Ask staff how to unlock it.",
                "Click to equip",
                "It goes in your hotbar.",
                "Click to put away",
                "Your hotbar slot is freed.",
                defaultCards());
    }

    private static Map<String, Card> defaultCards() {
        Map<String, Card> map = new LinkedHashMap<>();
        map.put("grapple", new Card("Grappling Hook", List.of("Cast it at a wall or the floor", "and it pulls you there."), "Right-click", "cast and reel in"));
        map.put("ender", new Card("Ender Ride", List.of("Ride your own pearl through", "the air and land safely."), "Right-click", "throw and ride"));
        map.put("paint", new Card("Paint Blaster", List.of("Shots colour the ground for", "a few seconds. Nothing breaks."), "Right-click", "shoot paint"));
        map.put("knockback", new Card("Knockback Stick", List.of("Push players who allow it.", "It never hurts."), "Hit a player", "push them away"));
        map.put("leap", new Card("Leap Feather", List.of("Boost yourself where you look,", "landing softly."), "Right-click", "leap forward"));
        map.put("snowball", new Card("Snowball Cannon", List.of("A burst of snowballs that", "nudge players who allow it."), "Right-click", "fire a burst"));
        map.put("trampoline", new Card("Trampoline", List.of("A bouncy pad under your feet", "for a few seconds."), "Right-click", "place the pad"));
        return map;
    }
}

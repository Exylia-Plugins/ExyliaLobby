package net.exylia.exyliaLobby.module.hotbar;

import net.exylia.lib.config.Comment;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code modules/hotbar/config.yml}: which hotbar set players wear in the lobby and what every button looks like and does. */
public record HotbarSettings(
        @Comment("The set worn inside the lobby: a key of 'sets' below.")
        String set,

        @Comment("Show this hotbar even when ExyliaPracticeCore is installed. Without it, PracticeCore's own lobby")
        @Comment("hotbar stays in charge and this module draws nothing.")
        boolean force,

        @Comment("Blank every slot the set does not draw. Keep it off when the lobby PvP module hands out a real sword.")
        boolean hideRest,

        @Comment("The slot (0-8) reserved for the lobby PvP sword: no button is drawn there.")
        int swordSlot,

        @Comment("The sets. Each button is drawn, not held: nothing is written into the real inventory.")
        @Comment("Combat gear must be real items, never a button.")
        Map<String, ItemSet> sets) {

    public HotbarSettings() {
        this("lobby", false, false, 4, defaults());
    }

    /** One named set of buttons, by button id. */
    public record ItemSet(Map<String, Button> items) {
        public ItemSet() {
            this(Map.of());
        }
    }

    /**
     * One button.
     *
     * @param slot        inventory slot, 0-8 for the hotbar
     * @param material    anything an item accepts: a material, {@code basehead-...}, {@code nexo:id}
     * @param requires    a module id (such as {@code parkour}): the button is drawn only while that module is on
     * @param permission  a permission: the button is drawn only for holders. Empty for everyone
     * @param cooldown    time between two presses. 0 for buttons whose action keeps its own cooldown
     * @param actions     click-prefixed action lines ({@code right: exylialobby:selector_open}); no prefix is every click
     * @param commands    click-prefixed command lines ({@code right: player: parkour})
     */
    public record Button(
            int slot,
            String material,
            String name,
            List<String> lore,
            String requires,
            String permission,
            Duration cooldown,
            List<String> actions,
            List<String> commands) {

        public Button() {
            this(0, "STONE", "", List.of(), "", "", Duration.ofMillis(250), List.of(), List.of());
        }
    }

    private static Map<String, ItemSet> defaults() {
        Map<String, Button> items = new LinkedHashMap<>();
        items.put("selector", button(0, "COMPASS", "info", "Servers", "Server selector", "Every server of the network,",
                "with its live player count.", "Click to open", "Pick a server to join.", "selector",
                List.of("exylialobby:selector_open"), List.of()));
        items.put("games", button(1, "NETHER_STAR", "success", "Games", "Games", "Every game of the network,",
                "ready to queue for.", "Click to open", "Pick a game to play.", "games",
                List.of("exylialobby:games_open"), List.of()));
        items.put("parkour", button(2, "FEATHER", "highlight", "Parkour", "Parkour", "Courses, your best times",
                "and the top of each one.", "Click to open", "Pick a course to run.", "parkour",
                List.of(), List.of("player: parkour")));
        items.put("visibility", button(6, "LIME_DYE", "info", "Players: %lobby_visibility_state%", "Visibility",
                "Choose who you see in the", "lobby: everyone, friends or nobody.", "Click to switch", "Takes effect at once.",
                "visibility", List.of("exylialobby:visibility_toggle"), List.of()));
        items.put("gadgets", button(7, "ENDER_CHEST", "accent", "Gadgets", "Gadgets", "Cosmetic toys to play with",
                "while you wait.", "Click to open", "Pick a gadget to use.", "gadgets",
                List.of(), List.of("player: gadgets")));
        items.put("profile", button(8, "COMPARATOR", "primary", "Settings", "Your settings", "Your preferences, such as",
                "the chat and announcements.", "Click to open", "Changes are saved for you.", "",
                List.of("exylialobby:hotbar_settings"), List.of()));
        return Map.of("lobby", new ItemSet(items));
    }

    private static Button button(int slot, String material, String tone, String name, String kind, String line1, String line2,
                                 String click, String next, String requires, List<String> actions, List<String> commands) {
        String light = switch (tone) {
            case "info" -> "{info}:{info_light}";
            case "success" -> "{success}:{success_light}";
            case "highlight" -> "{highlight}:{warning_light}";
            case "accent" -> "{accent}:{secondary_light}";
            default -> "{primary}:{secondary_light}";
        };
        String colour = "{" + tone + "}";
        // The visibility switch keeps its own cooldown; the other buttons only guard against a double click.
        return new Button(slot, material,
                "{letters}Lobby {letters_black}▸ <gradient:" + light + ">" + name + "</gradient>",
                List.of("{letters_black}" + kind, "", colour + "✎ Information", " {letters}" + line1, " {letters}" + line2, "",
                        colour + "{icon:click} " + click, "{letters_black}" + next),
                requires, "", requires.equals("visibility") ? Duration.ZERO : Duration.ofMillis(250), actions, commands);
    }
}

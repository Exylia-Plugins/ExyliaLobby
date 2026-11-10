package net.exylia.exyliaLobby.module.gadgets;

import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code modules/gadgets/config.yml}: the toys players can equip in the lobby. */
public record GadgetsSettings(
        @Comment("Labels of the player command that opens the gadgets menu. Empty removes the command.")
        List<String> command,

        @Comment("The hotbar slot (0-8) an equipped gadget is placed in. The hotbar module keeps it free of buttons.")
        int slot,

        @Comment("Players within this many blocks see paint and trampolines.")
        int viewRadius,

        @Comment("The blocks the paint blaster colours with, one picked per shot.")
        List<String> paintColors,

        @Comment("The gadgets, by id: grapple, ender, paint, knockback, leap, snowball, trampoline.")
        Map<String, Gadget> gadgets) {

    public GadgetsSettings() {
        this(List.of("gadgets"), 5, 32, List.of("RED_CONCRETE", "ORANGE_CONCRETE", "YELLOW_CONCRETE", "LIME_CONCRETE",
                "LIGHT_BLUE_CONCRETE", "PURPLE_CONCRETE", "MAGENTA_CONCRETE"), defaults());
    }

    /**
     * One gadget.
     *
     * @param enabled  off hides it from the menu and refuses it
     * @param permission needed to equip and use it. Empty is everyone
     * @param cooldown seconds between two uses, decimals allowed (0.5 is half a second)
     * @param material the item it is carried as (a material)
     * @param power    its strength: pull speed, launch speed, push strength or bounce height, as the gadget uses it
     * @param seconds  how long what it makes lasts: the paint, the trampoline pad. Unused by the others
     */
    public record Gadget(boolean enabled, String permission, double cooldown, String material, double power, int seconds) {
        public Gadget() {
            this(true, "", 1.0, "STICK", 1.0, 0);
        }
    }

    private static Map<String, Gadget> defaults() {
        Map<String, Gadget> map = new LinkedHashMap<>();
        map.put("grapple", new Gadget(true, "exylialobby.gadget.grapple", 0.8, "FISHING_ROD", 1.0, 0));
        map.put("ender", new Gadget(true, "exylialobby.gadget.ender", 2, "ENDER_PEARL", 1.4, 0));
        map.put("paint", new Gadget(true, "exylialobby.gadget.paint", 0.3, "BRUSH", 1.8, 6));
        map.put("knockback", new Gadget(true, "exylialobby.gadget.knockback", 0.5, "STICK", 1.1, 0));
        map.put("leap", new Gadget(true, "exylialobby.gadget.leap", 1, "FEATHER", 1.3, 0));
        map.put("snowball", new Gadget(true, "exylialobby.gadget.snowball", 0.5, "SNOWBALL", 0.5, 0));
        map.put("trampoline", new Gadget(true, "exylialobby.gadget.trampoline", 6, "SLIME_BALL", 1.0, 8));
        return map;
    }
}

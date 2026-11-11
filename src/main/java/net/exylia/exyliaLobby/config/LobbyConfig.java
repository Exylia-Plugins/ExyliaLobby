package net.exylia.exyliaLobby.config;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Languages;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;

/**
 * {@code config.yml}: which modules run, the staff command and the lobby areas.
 *
 * <p>Each module keeps its own settings in {@code modules/<module>/config.yml} and its text in
 * {@code lang/<language>/modules/<module>/messages.yml}.
 */
public record LobbyConfig(
        @Comment("Language of menus and messages. 'default' follows ExyliaLib's config.yml;")
        @Comment("en, es, pt, fr, ru or tr set this plugin alone. Each one is a folder under lang/ you can edit.")
        String language,

        @Comment("Which modules run. A module that is off registers nothing and costs nothing.")
        @Comment("Switch them live from /lobbyadmin → Modules; this file follows.")
        Modules modules,

        @Comment("Where the lobby rules apply. Leaving these places gives players back what they had.")
        Areas areas,

        @Comment("Labels of the staff command. The first one is the main label.")
        List<String> adminCommand,

        @Comment("Log what each module does, for finding a problem.")
        boolean debug) {

    public LobbyConfig() {
        this(Languages.DEFAULT, new Modules(), new Areas(), List.of("lobbyadmin", "hubadmin"), false);
    }

    public record Areas(
            @Comment("Whole worlds that are lobby. On a dedicated hub, list its world here.")
            List<String> worlds,

            @Comment("Boxes inside other worlds that are lobby, such as the spawn of a survival server.")
            @Comment("Add them in game: /lobbyadmin, Areas. Format: name: {world, min: 'x y z', max: 'x y z'}.")
            java.util.Map<String, Box> boxes,

            @Comment("WorldGuard regions that are lobby, by id, when WorldGuard is installed.")
            List<String> worldguardRegions) {

        public Areas() {
            this(List.of("world"), java.util.Map.of(), List.of());
        }
    }

    public record Box(String world, String min, String max) {
        public Box() {
            this("world", "0 0 0", "0 0 0");
        }
    }

    public record Modules(
            @Comment("Spawn point, /spawn, spawn on join, void return and respawn at spawn.")
            boolean spawn,
            @Comment("Game mode, health, hunger, potion effects, time and weather inside the lobby.")
            boolean state,
            @Comment("No building, breaking, damage, drops, hunger, mobs, fire or explosions inside the lobby.")
            boolean protection,
            @Comment("Join, leave and first-join messages, welcome title and commands on join and quit.")
            boolean join,
            @Comment("Items in the hotbar that open menus, send to servers or run commands.")
            boolean hotbar,
            @Comment("Double jump and launch pads.")
            boolean movement,
            @Comment("Buttons, pressure plates, blocks and areas that run actions when used or walked into.")
            boolean triggers,
            @Comment("Players choose to see everyone, only friends and staff, or nobody.")
            boolean visibility,
            @Comment("Server and lobby selector and portals.")
            boolean selector,
            @Comment("Opens the screen of every game the Exylia plugins offer.")
            boolean games,
            @Comment("Tab header and footer and announcements (sidebars and boss bars: ExyliaScreens).")
            boolean hud,
            @Comment("Lobby PvP: hold the sword to fight other players who opted in.")
            boolean pvp,
            @Comment("Parkour courses, endless parkour and their leaderboards.")
            boolean parkour,
            @Comment("Harmless toys: grappling hook, ender ride, paint blaster and more.")
            boolean gadgets,
            @Comment("Chat lock, slow mode and clear chat. No chat formatting: keep your chat plugin for that.")
            boolean chat,
            @Comment("Hands players on their first join to ExyliaJourney, when it is installed.")
            boolean onboarding) {

        public Modules() {
            this(true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, false);
        }

        /** The switch of a module by its id ({@code first-join} style), empty for an unknown id. */
        public Optional<Boolean> switchOf(String id) {
            for (RecordComponent component : Modules.class.getRecordComponents()) {
                if (!component.getName().equals(id)) continue;
                try {
                    return Optional.of((Boolean) component.getAccessor().invoke(this));
                } catch (ReflectiveOperationException impossible) {
                    throw new IllegalStateException(impossible);
                }
            }
            return Optional.empty();
        }

        /** A copy with one switch changed; unknown ids leave it as it is. */
        public Modules with(String id, boolean on) {
            RecordComponent[] components = Modules.class.getRecordComponents();
            Object[] values = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            try {
                for (int i = 0; i < components.length; i++) {
                    types[i] = boolean.class;
                    values[i] = components[i].getName().equals(id) ? on : components[i].getAccessor().invoke(this);
                }
                return Modules.class.getDeclaredConstructor(types).newInstance(values);
            } catch (ReflectiveOperationException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }
}

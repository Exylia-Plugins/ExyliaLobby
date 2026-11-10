package net.exylia.exyliaLobby.config;

import net.exylia.lib.config.Comment;

/**
 * {@code lang/<language>/messages.yml}: what the core says. Each module has its own messages file.
 *
 * <p>Colours are palette tokens, so the server recolours every Exylia plugin from {@code colors.yml}.
 */
public record LobbyMessages(
        @Comment("Prefixed onto this plugin's chat lines as %prefix%")
        String prefix,

        @Comment("A command or button of a module that is switched off. %module% is its name.")
        String moduleOff,

        Modules modules,

        Areas areas) {

    public LobbyMessages() {
        this("{primary}&lLOBBY {letters_black}•<reset>",
                "ENTITY_VILLAGER_NO %prefix% {letters}The {highlight}%module%{letters} module is switched off.",
                new Modules(), new Areas());
    }

    public record Modules(
            @Comment("The state shown on a module card")
            String on,
            String off,
            @Comment("The module waits for another one that is off. %required% names it.")
            String blocked,
            @Comment("Sent after a module was switched from the Modules screen")
            String switchedOn,
            String switchedOff,
            @Comment("The module could not start; the console says why")
            String failed,
            @Comment("Turning this one off also turns these off. %modules% lists them.")
            String dependants) {

        public Modules() {
            this("{success}{icon:on} On", "{error}{icon:off} Off", "{warning}{icon:warning} Needs %required%",
                    "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Module {highlight}%module%{letters} is now {success}on{letters}.",
                    "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Module {highlight}%module%{letters} is now {error}off{letters}.",
                    "ENTITY_VILLAGER_NO %prefix% {error}Module {highlight}%module%{error} could not start. See the console.",
                    "{letters_black}Also turns off: %modules%");
        }
    }

    public record Areas(
            @Comment("Shown when an admin saves a lobby box from their selection")
            String boxSaved,
            String boxRemoved,
            String noSelection,
            String worldAdded,
            String worldRemoved) {

        public Areas() {
            this("ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Lobby area {highlight}%area%{letters} saved.",
                    "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Lobby area {highlight}%area%{letters} removed.",
                    "ENTITY_VILLAGER_NO %prefix% {error}Select two corners first.",
                    "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}World {highlight}%world%{letters} is now lobby.",
                    "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}World {highlight}%world%{letters} is no longer lobby.");
        }
    }
}

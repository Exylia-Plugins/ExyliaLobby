package net.exylia.exyliaLobby.module.state;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Menu;
import org.bukkit.GameMode;

import java.util.List;

/** {@code modules/state/config.yml}: what a player is inside the lobby. Everything is given back when they leave. */
public record StateSettings(
        @Comment("Set the game mode of players entering the lobby.")
        boolean changeGameMode,

        @Comment("The game mode inside the lobby: SURVIVAL, ADVENTURE, CREATIVE or SPECTATOR.")
        GameMode gameMode,

        @Comment("Also change players who are in creative or spectator, such as staff. Off leaves them alone.")
        boolean overrideStaffModes,

        @Comment("Heal players to full health when they enter.")
        boolean fullHealth,

        @Comment("Fill the hunger bar when they enter and never let it drop inside the lobby.")
        boolean fullHunger,

        @Comment("Remove every potion effect when they enter. They get them back on leaving.")
        boolean clearEffects,

        @Comment("Potion effects held while in the lobby, never running out. Remove the effect on leaving.")
        @Comment("Example: {effect: speed, level: 2, particles: false}. Names are Minecraft's: speed, jump_boost, night_vision...")
        List<Effect> effects,

        @Comment("Let players who have the permission below fly (double-jump is the movement module's).")
        boolean fly,

        @Comment("The permission that allows flying in the lobby.")
        String flyPermission,

        @Comment("Fixed time of day shown to players in the lobby, in ticks (6000 is noon, 18000 midnight). -1 keeps the world's.")
        @Menu.Range(min = 0, max = 24000, step = 500, none = -1)
        int time,

        @Comment("Weather shown to players in the lobby: OFF keeps the world's, CLEAR or DOWNFALL.")
        Weather weather,

        @Comment("Show a number on the experience bar. Their own experience is given back on leaving.")
        boolean xpBar,

        @Comment("What the bar's level shows: a number or a placeholder such as %player_ping%.")
        String xpLevel,

        @Comment("How full the bar is, from 0 to 1.")
        double xpProgress) {

    public StateSettings() {
        this(true, GameMode.ADVENTURE, false, true, true, false, List.of(), true, "exylialobby.fly", 6000, Weather.CLEAR,
                false, "0", 0.0);
    }

    /** Weather forced on each player. */
    public enum Weather {
        OFF, CLEAR, DOWNFALL
    }

    /** One potion effect. */
    public record Effect(String effect, int level, boolean particles) {
        public Effect() {
            this("speed", 1, false);
        }
    }
}

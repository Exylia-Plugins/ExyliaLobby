package net.exylia.exyliaLobby.module.join;

import net.exylia.lib.config.Comment;
import net.exylia.lib.effect.EffectConfig;

import java.util.List;

/** {@code lang/<language>/modules/join/messages.yml}. */
public record JoinMessages(
        @Comment("The line everyone sees when a player joins. %player% is their name. Empty shows nothing.")
        String join,

        @Comment("The line everyone sees when a player leaves.")
        String leave,

        @Comment("Replaces the join line the first time a player ever joins. Empty uses the normal one.")
        String firstJoin,

        @Comment("Shown to the player themselves a moment after they join: a title and a short sound.")
        EffectConfig welcome,

        @Comment("Chat lines sent to the player a moment after they join, one per line. %player% is their name.")
        List<String> motd) {

    public JoinMessages() {
        this("{letters_black}[{success}+{letters_black}] {letters}%player%",
                "{letters_black}[{error}-{letters_black}] {letters}%player%",
                "{letters_black}[{success}+{letters_black}] {highlight}%player%{letters} joined for the first time!",
                new EffectConfig(new EffectConfig.Title("{primary}&lWELCOME", "{letters}%player%", 0.3, 2.5, 0.6, "auto"),
                        new EffectConfig.ActionBar(), new EffectConfig.BossBar(),
                        new EffectConfig.Sound("ENTITY_EXPERIENCE_ORB_PICKUP", 0.5, 1.4, "MASTER"),
                        new EffectConfig.Particle(), new EffectConfig.Firework()),
                List.of());
    }
}

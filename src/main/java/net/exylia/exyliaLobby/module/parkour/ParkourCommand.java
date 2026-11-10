package net.exylia.exyliaLobby.module.parkour;

import org.bukkit.entity.Player;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /parkour [mode|course]}, {@code leave} and {@code top [board]}, under the labels the settings name. */
@CommandPermission(ParkourModule.PERMISSION)
final class ParkourCommand implements OrphanCommand {

    private final ParkourModule module;

    ParkourCommand(ParkourModule module) {
        this.module = module;
    }

    @CommandPlaceholder
    public void parkour(Player player, @Optional @Single String what) {
        if (what == null) module.chatCourses(player);
        else module.play(player, what);
    }

    @Subcommand("leave")
    public void leave(Player player) {
        module.leaveRun(player);
    }

    @Subcommand("top")
    public void top(Player player) {
        module.top(player, Board.ENDLESS);
    }

    @Subcommand("top")
    public void top(Player player, @Single String board) {
        module.top(player, board);
    }
}

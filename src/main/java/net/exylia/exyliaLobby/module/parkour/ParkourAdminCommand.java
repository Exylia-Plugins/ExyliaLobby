package net.exylia.exyliaLobby.module.parkour;

import net.exylia.exyliaLobby.Permissions;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /lobbyadmin parkour ...}: the staff tools that place courses, boards and endless areas. */
@CommandPermission(Permissions.ADMIN)
final class ParkourAdminCommand implements OrphanCommand {

    private final ParkourModule module;

    ParkourAdminCommand(ParkourModule module) {
        this.module = module;
    }

    @Subcommand("parkour create")
    public void create(Player admin, @Single String id) {
        module.create(admin, id, null);
    }

    @Subcommand("parkour setstart")
    public void setStart(Player admin, @Single String course) {
        module.setStart(admin, course, null);
    }

    @Subcommand("parkour addcheckpoint")
    public void addCheckpoint(Player admin, @Single String course) {
        module.addCheckpoint(admin, course, null);
    }

    @Subcommand("parkour setfinish")
    public void setFinish(Player admin, @Single String course) {
        module.setFinish(admin, course, null);
    }

    @Subcommand("parkour addkill")
    public void addKill(Player admin, @Single String course) {
        module.addKill(admin, course, null);
    }

    @Subcommand("parkour medals")
    public void medals(Player admin, @Single String course, double gold, double silver, double bronze) {
        module.setMedals(admin, course, gold, silver, bronze);
    }

    @Subcommand("parkour delete")
    public void delete(Player admin, @Single String course) {
        module.delete(admin, course);
    }

    @Subcommand("parkour board")
    public void board(Player admin, @Single String board) {
        module.board(admin, board, null);
    }

    @Subcommand("parkour endlessarea")
    public void endlessArea(Player admin, @Single String name) {
        module.endlessArea(admin, name, null);
    }
}

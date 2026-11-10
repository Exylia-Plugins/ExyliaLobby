package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/parkour/messages.yml}. */
public record ParkourMessages(
        @Comment("/parkour with nothing after it. %courses% lists the courses, %command% is the command label.")
        String usage,
        String unknownCourse,
        String courseClosed,
        String modeOff,
        @Comment("An endless mode with no area set up")
        String noArea,
        String notRunning,
        @Comment("Left a run with /parkour leave or the leave item")
        String left,

        @Comment("Course runs. %course% is its name, %time% the time so far, %checkpoint%/%total% the progress.")
        String startTitle,
        String startSubtitle,
        String checkpointTitle,
        @Comment("%delta% is one of the delta lines below: green ahead of your best, red behind.")
        String checkpointSubtitle,
        String deltaAhead,
        String deltaBehind,
        String bossBar,
        @Comment("The finish was reached without every checkpoint")
        String missedCheckpoint,
        String timeUp,
        @Comment("A finish under the course's minimum time")
        String tooFast,
        @Comment("Chat lines after a finish. %medal% is a medal line below or nothing, %rank% the place on the all-time board.")
        List<String> finished,
        String newRecord,
        String noRecord,
        String medalGold,
        String medalSilver,
        String medalBronze,
        @Comment("No place on a board yet")
        String unranked,

        @Comment("Endless modes. %mode% is a mode name below, %score% the jumps, %left% a sprint's time left.")
        String endlessName,
        String sprintName,
        String dailyName,
        String endlessBossBar,
        String sprintBossBar,
        @Comment("Shown when the path changes theme. %theme% is the theme's name.")
        String themeTitle,
        String milestoneTitle,
        String milestoneSubtitle,
        @Comment("Chat lines when an endless run ends. %retry% is the retry button below.")
        List<String> endlessEnded,
        String retry,

        @Comment("A run ended by something not allowed. %reason% is one of the reasons below.")
        String cheated,
        Reasons reasons,

        @Comment("Your times on a course were wiped")
        String timeReset,

        @Comment("Staff commands under /lobbyadmin parkour. %course% is the id.")
        String courseCreated,
        String courseExists,
        String startSet,
        String checkpointAdded,
        String finishSet,
        String courseDeleted,
        String wrongWorld,

        @Comment("The label over the next checkpoint's beam. %checkpoint%/%total% the checkpoint, %distance% metres away.")
        String beaconLabel,
        @Comment("The items a runner holds: back to the last checkpoint, start again, leave.")
        RunItems items) {

    public ParkourMessages() {
        this("%prefix% {letters}Courses: %courses%{letters}. Play one with {highlight}/%command% <course>{letters}, or {highlight}/%command% endless{letters}.",
                "ENTITY_VILLAGER_NO %prefix% {error}There is no course called {highlight}%course%{error}.",
                "ENTITY_VILLAGER_NO %prefix% {error}{highlight}%course%{error} is closed right now.",
                "ENTITY_VILLAGER_NO %prefix% {error}That mode is switched off.",
                "ENTITY_VILLAGER_NO %prefix% {error}Endless parkour has no area yet. Ask an admin.",
                "ENTITY_VILLAGER_NO %prefix% {error}You are not running a parkour.",
                "%prefix% {letters}You left the parkour.",

                "{primary}&l%course%",
                "{letters}Go!",
                "{info}%checkpoint%{letters_black}/{info}%total%",
                "{letters}%time% %delta%",
                "{success}%delta%",
                "{error}%delta%",
                "{primary}%course% {letters_black}» {info}%time%",
                "ENTITY_VILLAGER_NO %prefix% {warning}You missed a checkpoint. Reach every one before the finish.",
                "ENTITY_VILLAGER_NO %prefix% {error}Out of time on {highlight}%course%{error}.",
                "ENTITY_VILLAGER_NO %prefix% {error}That finish was faster than this course allows, so it was not counted.",
                List.of("",
                        "  {primary}&l%course% {letters_black}» {success}Finished",
                        "  {letters_black}· {letters}Time{letters_black}: {info}%time% %record%",
                        "  {letters_black}· {letters}Best{letters_black}: {highlight}%best% {letters_black}(#%rank%) %medal%",
                        ""),
                "{success}New record!",
                "",
                "{highlight}Gold medal",
                "{letters}Silver medal",
                "{warning}Bronze medal",
                "—",

                "Endless",
                "Sprint",
                "Daily",
                "{primary}%mode% {letters_black}» {info}%score% {letters}jumps {letters_black}· {info}%time%",
                "{primary}%mode% {letters_black}» {info}%score% {letters}jumps {letters_black}· {warning}%left%",
                "%theme%",
                "{highlight}&l%score%",
                "{letters}jumps in a row",
                List.of("",
                        "  {primary}&l%mode% {letters_black}» {letters}Run over",
                        "  {letters_black}· {letters}Score{letters_black}: {info}%score% {letters_black}in {info}%time% %record%",
                        "  {letters_black}· {letters}Best{letters_black}: {highlight}%best% {letters_black}(#%rank%)",
                        "  %retry%",
                        ""),
                "<click:run_command:'/%command% %mode_id%'><hover:show_text:'{letters}Start a new run'>{success}&l[↻ RETRY]</hover></click>",

                "ENTITY_VILLAGER_NO %prefix% {error}Run ended: %reason%.",
                new Reasons(),

                "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Your times on {highlight}%course%{letters} were reset.",

                "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Course {highlight}%course%{letters} created with its start at your feet. Add checkpoints and a finish.",
                "ENTITY_VILLAGER_NO %prefix% {error}A course called {highlight}%course%{error} already exists.",
                "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Start of {highlight}%course%{letters} set at your feet.",
                "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Checkpoint {highlight}%checkpoint%{letters} of {highlight}%course%{letters} added at your feet.",
                "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Finish of {highlight}%course%{letters} set at your feet.",
                "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Course {highlight}%course%{letters} deleted.",
                "ENTITY_VILLAGER_NO %prefix% {error}{highlight}%course%{error} is in world {highlight}%world%{error}. Stand there to edit it.",

                "{letters}Checkpoint {info}%checkpoint%{letters_black}/{info}%total% {letters_black}· {info}%distance% m",
                new RunItems());
    }

    /** A run item's look: its material, name and lore. */
    public record RunItem(String icon, String name, List<String> lore) {
    }

    public record RunItems(RunItem checkpoint, RunItem restart, RunItem leave) {
        public RunItems() {
            this(new RunItem("LIGHT_WEIGHTED_PRESSURE_PLATE",
                            "{letters}Parkour {letters_black}▸ <gradient:{primary}:{secondary_light}>Checkpoint</gradient>",
                            List.of("{letters_black}Run item", "", "{primary}{icon:click} Click to go back",
                                    "{letters_black}To the last checkpoint you reached.")),
                    new RunItem("CLOCK",
                            "{letters}Parkour {letters_black}▸ <gradient:{primary}:{secondary_light}>Restart</gradient>",
                            List.of("{letters_black}Run item", "", "{primary}{icon:click} Click to restart",
                                    "{letters_black}A new run from the start.")),
                    new RunItem("BARRIER",
                            "{letters}Parkour {letters_black}▸ <gradient:{error}:{error_light}>Leave</gradient>",
                            List.of("{letters_black}Run item", "", "{error}{icon:click} Click to leave",
                                    "{letters_black}Ends the run; nothing is saved.")));
        }
    }

    /** Why a run was ended, shown in {@link #cheated}. */
    public record Reasons(String flight, String elytra, String riptide, String pearl, String vehicle, String gameMode,
                          String teleport) {
        public Reasons() {
            this("flying", "gliding", "riptide", "ender pearl", "riding", "game mode changed", "teleported away");
        }
    }
}

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
        @Comment("/parkour <course or mode> away from the lobby, or while another activity (a fight) holds you")
        String notInLobby,
        String busy,

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
        @Comment("The database could not be read or written, so a finish or score was not saved")
        String notSaved,

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
        @Comment("The setup wizards: what they name on screen and what they ask.")
        Wizard wizard,
        @Comment("The items a runner holds: back to the last checkpoint, start again, leave.")
        RunItems items,
        @Comment("Chat leaderboards and staff tools.")
        Tools tools,
        @Comment("Menu cards: stat lines, subtitles, prompts and states. %tone% and %light% are the card's palette colours.")
        Screens screens,
        @Comment("The staff screens of /lobbyadmin: course, area and theme lists.")
        Staff staff) {

    public ParkourMessages() {
        this("%prefix% {letters}Courses: %courses%{letters}. Play one with {highlight}/%command% <course>{letters}, or {highlight}/%command% endless{letters}.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}There is no course called {highlight}%course%{error}.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}{highlight}%course%{error} is closed right now.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}That mode is switched off.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Endless parkour has no area yet. Ask an admin.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}You are not running a parkour.",
                "%prefix% {letters}You left the parkour.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Parkour can only be started in the lobby.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Finish what you are doing before starting a parkour.",

                "{primary}&l%course%",
                "{letters}Go!",
                "{info}%checkpoint%{letters_black}/{info}%total%",
                "{letters}%time% %delta%",
                "{success}%delta%",
                "{error}%delta%",
                "{primary}%course% {letters_black}» {info}%time%",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {warning}You missed a checkpoint. Reach every one before the finish.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Out of time on {highlight}%course%{error}.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}That finish was faster than this course allows, so it was not counted.",
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
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Your result could not be saved right now. Try again in a moment.",

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

                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Run ended: %reason%.",
                new Reasons(),

                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Your times on {highlight}%course%{letters} were reset.",

                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Course {highlight}%course%{letters} created.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}A course called {highlight}%course%{error} already exists.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Start of {highlight}%course%{letters} set.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Checkpoint {highlight}%checkpoint%{letters} of {highlight}%course%{letters} added.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Finish of {highlight}%course%{letters} set.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Course {highlight}%course%{letters} deleted.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}{highlight}%course%{error} is in world {highlight}%world%{error}. Set its places there.",

                "{letters}Checkpoint {info}%checkpoint%{letters_black}/{info}%total% {letters_black}· {info}%distance% m",
                new Wizard(),
                new RunItems(),
                new Tools(),
                new Screens(),
                new Staff());
    }

    /** One add button of a staff list: card name, description lines, click line and the line saying what happens next. */
    public record Button(String title, List<String> text, String click, String next) {
        public Button() {
            this("", List.of(), "", "");
        }
    }

    /** Text of the staff lists (courses, endless areas, themes); the editor and hub keep theirs in their menu files. */
    public record Staff(
            String back,
            String titleCourses,
            String kindCourse,
            String titleAreas,
            String kindArea,
            String titleThemes,
            String kindTheme,
            @Comment("Card subtitles of the rows.")
            String subtitleCourseReady,
            String subtitleCourseSetup,
            String subtitleArea,
            String subtitleTheme,
            @Comment("Stat labels of the rows.")
            String labelDifficulty,
            String labelCheckpoints,
            String labelState,
            String labelWorld,
            String labelStart,
            String labelFloor,
            @Comment("Click lines of the rows, one per line.")
            List<String> clicksCourse,
            List<String> clicksArea,
            List<String> clicksTheme,
            @Comment("The add buttons of each list (slots 45 and 46).")
            Button createCourse,
            Button settingsCourses,
            Button addArea,
            Button settingsAreas,
            Button editThemes,
            Button enableThemes) {
        public Staff() {
            this("Parkour", "Courses", "Course", "Endless areas", "Area", "Themes", "Theme",
                    "Course · ready", "Course · setup needed", "Endless area", "Endless theme",
                    "Difficulty", "Checkpoints", "State", "World", "Start", "Floor",
                    List.of("{primary}{icon:click} Left-click {letters_black}· {letters}edit",
                            "{primary}{icon:right_click} Right-click {letters_black}· {letters}turn on or off",
                            "{error}{icon:right_click} Shift-right-click {letters_black}· {letters}delete",
                            "{letters_black}You will be asked to confirm."),
                    List.of("{error}{icon:right_click} Right-click {letters_black}· {letters}delete",
                            "{letters_black}You will be asked to confirm."),
                    List.of("{primary}{icon:click} Click {letters_black}· {letters}turn on or off",
                            "{letters_black}Endless paths use it while on."),
                    new Button("Create", List.of(" {letters}Name it, then set its start,", " {letters}checkpoints and finish."),
                            "Click to start", "Opens its editor when done."),
                    new Button("Settings", List.of(" {letters}Modes, anti-cheat, boards", " {letters}and the rest."),
                            "Click to open", "Every parkour setting."),
                    new Button("Add", List.of(" {letters}Name it, select its box,", " {letters}then stand where runs start."),
                            "Click to start", "Runs head the way you face."),
                    new Button("Settings", List.of(" {letters}Path tuning, milestones", " {letters}and the endless modes."),
                            "Click to open", "Every parkour setting."),
                    new Button("Edit blocks", List.of(" {letters}The blocks each theme", " {letters}draws its path with."),
                            "Click to open", "Opens the themes file."),
                    new Button("Enable all", List.of(" {letters}Turns every theme on."),
                            "Click to enable", "Endless paths use them all."));
        }
    }

    /** A run item's look: its material, name and lore. */
    public record RunItem(String icon, String name, List<String> lore) {
        public RunItem() {
            this("STONE", "", List.of());
        }
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
                          String teleport, String unconfirmed) {
        public Reasons() {
            this("flying", "gliding", "riptide", "ender pearl", "riding", "game mode changed", "teleported away",
                    "a jump could not be confirmed");
        }
    }

    /**
     * The setup wizards. Names (start to areaStart) are plain text, no colours; the title and prompts take colours.
     *
     * @param course   the title of the new course flow; %course% is its id
     * @param stand    a stand-and-sneak-click step; %step% names it
     * @param more     whether to add another checkpoint; %checkpoint% is its number
     */
    public record Wizard(String course, String stand, String more, String start, String checkpoint, String finish,
                         String kill, String board, String area, String areaStart) {
        public Wizard() {
            this("{primary}&lNEW COURSE {muted}%course%",
                    "{warning}➥ {highlight}%step%{letters}: stand there and {highlight}sneak + click{letters}.",
                    "Add checkpoint %checkpoint%? Answer no to set the finish.",
                    "Course start",
                    "Checkpoint",
                    "Course finish",
                    "Kill block",
                    "Top board",
                    "Endless area",
                    "Endless start");
        }
    }

    /** Chat boards, hologram boards and the rest of the staff tools. */
    public record Tools(
            @Comment("%board% is the board id.")
            String topTitle,
            @Comment("%place%, %name% and %value% of one row.")
            String topLine,
            String topEmpty,
            String unknownBoard,
            @Comment("Hologram board lines: a title, then one row per place (up to 10).")
            String boardTitle,
            String boardLine,
            String boardEmpty,
            String boardPlaced,
            String killAdded,
            String medalsSet,
            @Comment("Asks before deleting a course. %course% is the id.")
            String confirmDelete,
            @Comment("Asks for the area's name.")
            String promptArea,
            String areaSaved) {
        public Tools() {
            this("%prefix% {primary}&l%board% {letters_black}» {letters}Top",
                    "  {highlight}#%place% {letters_black}· {letters}%name% {letters_black}· {info}%value%",
                    "%prefix% {letters}Nobody is on that board yet.",
                    "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}There is no board called {highlight}%board%{error}.",
                    "{primary}&l%board%",
                    "{highlight}#%place% {letters}%name% {letters_black}· {info}%value%",
                    "{letters_black}Nobody yet",
                    "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Board {highlight}%board%{letters} placed.",
                    "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}{highlight}%block%{letters} now sends runners of {highlight}%course%{letters} back.",
                    "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Medals of {highlight}%course%{letters} set.",
                    "Delete %course% and its settings? Saved times stay in the database.",
                    "Name of the area",
                    "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Endless area {highlight}%area%{letters} saved.");
        }
    }

    /** Text the parkour menus build their cards from. */
    public record Screens(
            String on,
            String off,
            @Comment("A value that does not exist yet: no best time, no rank.")
            String none,
            @Comment("Card subtitles. A finished course is yours, so it takes the success tone.")
            String subtitleCourse,
            String subtitleCourseDone,
            String subtitleEndless,
            String subtitleSprint,
            String subtitleDaily,
            String subtitleTop,
            @Comment("Card names: %subject% is the course or mode; the gradient wraps plain names in the card's tone.")
            String nameCourse,
            String nameMode,
            String nameRow,
            String gradient,
            String labelRank,
            String backList,
            String labelToday,
            @Comment("The section labels of a card.")
            String labelInfo,
            String labelStatus,
            @Comment("What a course or mode is, in a line or two. <nl> splits lines. %seconds% is a sprint's length.")
            String aboutCourse,
            String aboutEndless,
            String aboutSprint,
            String aboutDaily,
            @Comment("Stat lines: %value% is the value.")
            String statDifficulty,
            String statBest,
            String statScore,
            String statMedal,
            String statRank,
            String statFinishes,
            String statCheckpoints,
            @Comment("The click lines of a list card (a course, then a mode).")
            String clickCourse,
            String clickMode,
            @Comment("Your splits on a course's detail screen: one line per checkpoint, then the finish against the gold medal.")
            String splitsNone,
            String splitLine,
            String splitMore,
            String splitFinish,
            String splitGold,
            @Comment("Names of the periods on a leaderboard.")
            Periods periods,
            @Comment("Asks before wiping your times. %course% is its name.")
            String confirmReset,
            @Comment("Staff screens.")
            String promptCourse,
            String promptDifficulty,
            String promptGold,
            String promptSilver,
            String promptBronze,
            String promptArea,
            String confirmDeleteArea,
            String areaDeleted,
            String themeToggled,
            String difficultySet,
            String enabledSet,
            String notReady,
            String ready,
            String placed,
            String notPlaced,
            String noCourses,
            String noAreas) {

        public Screens() {
            this("{success}{icon:on} On", "{error}{icon:off} Off", "{muted}None yet",
                    "{letters_black}Course", "{success}Course · finished", "{letters_black}Endless mode",
                    "{letters_black}Sprint mode", "{letters_black}Daily mode", "{letters_black}Leaderboard · %board%",
                    "{letters}Course {letters_black}▸ %subject%", "{letters}Mode {letters_black}▸ %subject%",
                    "{letters}#%place% {letters_black}▸ %subject%", "<gradient:%tone%:%light%>%subject%</gradient>",
                    "Rank", "Courses", "Today's rank",
                    "%tone%✎ Information", "%tone%✦ Status",
                    " {letters}Start, reach every checkpoint,<nl> {letters}then {highlight}finish {letters}as fast as you can.",
                    " {letters}Jump as far as you can.<nl> {letters}Your score is the {highlight}jumps{letters}.",
                    " {letters}Make as many jumps as you can<nl> {letters}in {highlight}%seconds% seconds{letters}.",
                    " {letters}The same path for everyone today.<nl> {letters}One board, {highlight}reset daily{letters}.",
                    "  {letters_black}· {warning}{icon:star} {letters}Difficulty{letters_black}: {warning}%value%",
                    "  {letters_black}· {info}{icon:clock} {letters}Best{letters_black}: {info}%value%",
                    "  {letters_black}· {info}{icon:star} {letters}Best{letters_black}: {info}%value%",
                    "  {letters_black}· {highlight}{icon:gold} {letters}Medal{letters_black}: %value%",
                    "  {letters_black}· {highlight}{icon:star} {letters}%label%{letters_black}: {highlight}%value%",
                    "  {letters_black}· {success}{icon:book} {letters}Finishes{letters_black}: {success}%value%",
                    "  {letters_black}· {info}{icon:info} {letters}Checkpoints{letters_black}: {info}%value%",
                    "%tone%{icon:click} Left-click {letters_black}· {letters}play<nl>%tone%{icon:right_click} Right-click {letters_black}· {letters}details<nl>{letters_black}Play sends you to the start.",
                    "%tone%{icon:click} Left-click {letters_black}· {letters}play<nl>%tone%{icon:right_click} Right-click {letters_black}· {letters}leaderboard<nl>{letters_black}Play sends you to its area.",
                    " {letters_black}Finish the course to record splits.",
                    "  {letters_black}· {info}{icon:clock} {letters}Checkpoint %number%{letters_black}: {info}%time%",
                    "  {letters_black}· {letters_black}and %more% more",
                    "  {letters_black}· {highlight}{icon:clock} {letters}Finish{letters_black}: {info}%time% %medal%",
                    "  {letters_black}· {highlight}{icon:gold} {letters}Gold{letters_black}: {warning}%time% {letters_black}(%delta%{letters_black})",
                    new Periods(),
                    "Wipe your times on %course%? Your places on its boards are removed.",
                    "Name of the course", "Difficulty (Easy, Medium, Hard...)", "Gold medal time in seconds (0 for none)",
                    "Silver medal time in seconds (0 for none)", "Bronze medal time in seconds (0 for none)", "Name of the area",
                    "Delete the endless area %area%?",
                    "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Endless area {highlight}%area%{letters} deleted.",
                    "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Theme {highlight}%theme%{letters}: %state%{letters}.",
                    "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Difficulty of {highlight}%course%{letters} set to {highlight}%difficulty%{letters}.",
                    "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Course {highlight}%course%{letters}: %state%{letters}.",
                    "{error}Needs a start and a finish", "{success}Ready to run", "{success}Placed", "{error}Not placed",
                    "{muted}No more courses", "{muted}No more areas");
        }
    }

    public record Periods(String all, String monthly, String weekly, String daily) {
        public Periods() {
            this("All time", "Monthly", "Weekly", "Daily");
        }

        String of(Board.Period period) {
            return switch (period) {
                case ALL -> all;
                case MONTHLY -> monthly;
                case WEEKLY -> weekly;
                case DAILY -> daily;
            };
        }
    }
}

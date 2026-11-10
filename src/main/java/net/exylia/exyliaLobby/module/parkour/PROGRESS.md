# Parkour module progress (delete before the last commit)

Worktree: /root/Java/Exylia/ExyliaLobby-worktrees/parkour (detached, commits only, no push).

- [x] endless/ pure generator (Cell, Role, Bounds, Jump, Move, SegmentType, Tuning, JumpPhysics, PathGenerator) + tests
- [x] timing/periods pure helpers (Times, Board) + tests
- [x] config records: ParkourSettings, ParkourCourses (catalogue, Spot, Medals), ParkourThemes, ParkourMessages + tests
- [x] data (ParkourData: tables, caches, tops, wipes, placeholders; module calls create, register, load/forget, refreshAll on a timer)
- [x] runs 1/3: ParkourRuns registry + CourseRun (CourseTrack detection, splits, boss bar, beacon, run items via Hotbars, rewards, medals) + RunEnd
- [x] runs 2/3: anti-cheat (RunGuard: ends on flight/elytra/riptide/pearl/mount/gamemode/foreign teleport; refuses velocity and blocked effects; minimum time refused + Logs `parkour-too-fast`)
- [x] runs 3/3: endless, sprint, daily (EndlessRun + EndlessMode, endless/Window; fake blocks kept for the runner only, themes, milestones, best marker, kick-for-flying cancelled)
- [ ] module wiring, placeholders, commands, actions, visibility
- [ ] player menus (list, course, top) + admin menus

## Runs: what the wiring step needs

- Build once in `start()`: `runs = new ParkourRuns(context, settings, courses, themes, messages, data, clockSupplier)`, then `listen(runs)` and `listen(runs.guard())`.
  It registers its own actions (`parkour_run_checkpoint|restart|leave`, namespace of `Actions.of(plugin)`), the run-item hotbar set
  and the reward queue (QUEUE + `PendingRewards.database`) if nobody set one. `stop()`: `runs.close()`. Reload: `runs.reload()` (redraws run items).
- `runs.isRunning(player)` for `Busy#busy` and the visibility rule; `runs.stop(player, RunEnd.LOBBY)` in `leave(...)` (QUIT is handled by its own listener).
- `/parkour <course>`: `runs.startCourse(player, typed)` (sends its own errors). `/parkour leave`: `stop(player, RunEnd.LEFT)` (send `notRunning` when `!isRunning`).
- Spawn void hook: `context.module("spawn", SpawnModule.class).ifPresent(s -> rescueHandle = s.rescue(100, runs::rescue))`; run the handle in `stop()`.
- `/parkour endless|sprint|daily`: `EndlessMode.parse(arg).ifPresent(m -> runs.startEndless(player, m))` (sends modeOff/noArea itself).
  The retry button runs `/<first label> <mode id>`, so the player command must accept the mode ids before course ids.
- Stepping onto a course's start plate begins a run by itself (skipped when `Busy.any`).

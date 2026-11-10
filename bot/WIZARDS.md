# Progress: 1.1.0 (delete before release)

Work happens in /root/Java/Exylia/ExyliaLobby (main). Commits are local, not pushed. The coordinator is adding a
build-mode change (Areas.pause, AreaCause.BUILD, ProtectionModule toggleBuild) on origin/main: rebase onto it before
pushing. ExyliaLib docs: read them from origin/main (the local ExyliaLib checkout is 250 commits behind). A clean
origin/main worktree exists at /root/Java/Exylia/ExyliaLib-worktrees/lobby-localized (nothing changed in it yet;
use it for lib work or remove it with `git worktree remove`).

## Done (committed, `./gradlew build` passes)

Wizards, en only:
- `LobbyModule#setup(admin, back)` wraps `Wizards.of(plugin()).setup(...)`; `back == null` for commands.
- Spawn: "Set spawn" card -> `spawn()`, reopens the Spawn screen. Areas editor "Add box" -> `area()` then name.
- PvP: zones -> `area()`; the "Around you" add button is gone, replaced by "Respawn" (`spawn()`); board -> `block()`
  (hologram at block + 0.5/2/0.5). `/lobbyadmin pvp setspawn|setboard` start the same wizards.
- Movement: "Launch pad" -> `block()`; "Aimed pad" -> `spawn()` (feet block, or the one under it, + yaw, fixed);
  shift-left on a pad: fixed -> free, free -> `spawn()` to aim it.
- Holograms: "Free spot" -> `spawn()` (+1.6), "Over a block" -> `block()` (+0.5/2/0.5); right-click move -> `spawn()`.
- Selector: NPC add = server choice then `spawn()`; NPC move -> `spawn()`; portal = `area()` then server.
- Parkour: `create(admin, id, back)` is one `define`d wizard built per start (captures id and current messages):
  stand start -> confirm "Add checkpoint n?" nested `when` up to 20 -> stand finish -> summary; progress bar off
  (its upper bound would read 2/43). Afterwards opens the course editor if saved, else `back`.
  Single edits from the editor and the commands: start/checkpoint/finish -> `spawn()`, kill -> `block()` (adds the
  clicked block's type), board -> `block()` (+0.5/3.2/0.5), endless area -> define wizard: region + stand (heading
  from yaw). Commands `/lobbyadmin parkour create|setstart|addcheckpoint|setfinish|addkill|board|endlessarea` pass
  `back = null`.
- Messages records changed (keys renamed/added/removed; translations must follow):
  SpawnMessages (+wizardSpawn, wizardBox; -selectBox; spawnSet text), PvpMessages (addHere* -> addRespawn*,
  -selectZone, +wizardZone/wizardRespawn/wizardBoard, texts), MovementMessages (-noTarget, addHere* -> addPad*,
  addLook* -> addAimed*, +wizardPad/wizardAimed/wizardAim, clicks text), HologramsMessages (-noTarget,
  addHere* -> addSpot*, addLook* -> addBlock*, +wizardSpot/wizardBlock), SelectorMessages (-selectPortal,
  +wizardNpc/wizardPortal, texts), ParkourMessages (+Wizard record after beaconLabel; Tools -killNoBlock,
  -selectArea, -noSelection; Staff createCourse/addArea buttons; chat texts).
- en admin menus edited and menu-version raised to 2: admin/spawn/detail.yml, admin/spawn/areas.yml,
  admin/parkour/editor.yml.

## Done after the wizards (cooldowns pass, en only)

- Grappling hook (GadgetsModule#fished): cast anywhere (checked on PlayerFishEvent FISHING, never on the reel-in
  click); reel-in pulls when the hook touches a block (the spot it struck, or its block and the 6 around it), open
  air just reels in, entities are never hooked; its cooldown starts on a pull only.
- Cooldowns are decimal: gadget `cooldown` is a double (grapple 0.8, ender 2, paint 0.3, knockback 0.5, leap 1,
  snowball 0.5, trampoline 6); visibility 0.5s; double jump 0.4s; pads 0.5s; pvp equip/unequip 3s/3s (doubles);
  hotbar buttons 250ms, the visibility button 0 (its module keeps the cooldown); a hotbar refusal under 300ms is silent.
- Item 3 done: every cooldown goes through `LobbyModule#cooldowns()` (= `Cooldowns.forPlugin`), refusals through
  `refuseCooling`/`refusalDue` (once a second per key), times through `LobbyModule.withTime` (`%time%` PRECISE,
  `%seconds%` AUTO alias). Gadget ride/safe/bounce, visibility, selector connect, chat slow mode, pvp combat tag and
  repeat-kill window converted.
- Message defaults changed (text only, no keys added): GadgetsMessages.cooling, VisibilityMessages.cooling,
  ChatMessages.slowDenied, HotbarMessages.cooling, PvpMessages.tagged, SpawnMessages.teleporting now say `%time%`
  instead of `%seconds%s`. Translations must follow (keep `%seconds%` working; `%time%` carries its unit).
- Item 4 done: `module/shared/Countdown` (Effects boss bar counting down in decimals, redrawn every 2 ticks, a tick
  each whole second with rising pitch, finish and cancel sounds, all to the player only). PvP equip/unequip use it
  (stepping back on/off the sword mid-countdown plays the cancel sound; deaths, zones and leaving are silent), plus a
  start title and a "kit on" title. /spawn warmup shows it too (the teleport keeps its own warmup; the cancelled
  chat line carries the refusal sound). Parkour has no start countdown to convert: a course clock already waits
  while the runner stands on the start, endless runs start on arrival.
  - Settings added: PvpSettings.countdownSounds, SpawnSettings.warmupSounds (`Countdown.Sounds` tick/finish/cancel).
  - Message keys added: PvpMessages equipTitle, equipSubtitle, kitOnTitle, kitOnSubtitle (after unequipBar);
    SpawnMessages warmupBar (after cancelled). Changed text: PvpMessages equipBar/unequipBar say `%time%` (old
    files with `%seconds%s` still read right: the countdown maps it to `%time%`).

## Remaining

1. Wizards: translate every key above in lang/{es,fr,pt,ru,tr}/modules/<id>/messages.yml (rename keys there too) and
   the three menus (raise their menu-version to 2). Find them with `git diff 327cf8e -- src/main/java src/main/resources/lang/en`.
   Update bot tests that set places by command (bot/lobby-test.js ~167-171, bot/lobby-dump.js ~74-81 and the
   add-button loop ~83): commands now start wizards, so the bot must sneak + click (stand) after each command, and
   answer the checkpoint confirm and the summary for `create`. Not done.
2. Translated player text in *Settings (HotbarSettings buttons ~78, HudSettings lines ~75, selector server cards,
   holograms defaults, join messages, gadget names, pvp kit names, parkour themes/course text): no lib mechanism
   yet. Findings: `Configs.define(...).translated()` moves the file under lang/<code>/ (would orphan existing
   modules/<id>/config.yml into lang/custom and hides it from ConfigMenus); `ConfigFileImpl#defaults()` reads
   packaged `lang/<code>/<name>.yml` defaults only for translated files; `Phrases` is library-only. Proposed lib
   change (in a lib worktree): an opt-in builder flag (e.g. `.localizedDefaults()`) so a file stays where it is but
   its defaults come from the packaged `lang/<code>/<name>.yml`; existing files then see the change through
   followDefaults / `/exylialib updates` (check that DefaultsMerge handles map entries). Then ship
   lang/<code>/modules/<id>/config.yml with only the text keys for es/fr/pt/ru/tr.
3. (DONE, see above) Cooldowns: every hand-rolled cooldown to `Cooldowns.forPlugin(plugin)`: VisibilityModule ~125-131 (`last` map),
   SelectorModule:79/187, ChatModule:34/86, GadgetsModule:81-83, pvp, parkour repeat rewards, spawn, hotbar
   (HotbarModule ~71 onCooldown uses Math.ceil). One cooldown per action: hotbar buttons whose module has its own
   cooldown get hotbar cooldown 0 (or the module owns it). Times via `TimeFormats`: messages take `%time%`
   (`TimeFormats.Style.PRECISE`: "3.3s", "1m 30s"), keep `%seconds%` as an alias (AUTO number). Throttle refusals:
   `cooldowns.tryStart(player, "refusal:" + key, Duration.ofSeconds(1))` before sending (no lib change needed).
4. (DONE, see above) Countdowns (PvP equip/unequip boss bar, parkour start, spawn warmup, any other): `Effects.of(plugin()).bossBar`
   like HudModule:168 / EndlessRun:116, decimals through TimeFormats, progress updated every few ticks, a quiet tick
   each whole second to the player (note_block.hat / ui.button.click, pitch rising), a finish sound
   (experience_orb.pickup / item.armor.equip_iron), a refusal sound on cancel, a start title/subtitle and a short
   "kit on" title. Texts in PvpMessages (+5 translations), sounds configurable in PvpSettings.
5. Audit: hand boss bars -> Effects (PvpModule ~426-448 with its own Countdown, ProtectionModule ~104-118,
   ParkourRuns ~326-327); ParkourRuns ~606 showTitle -> `Effects.title`; PvpModule ~759 String.format K/D ->
   `Numbers.decimals(value, 2)`; command/AdminCommand.java:46,53 raw sendMessage -> messages with %prefix% (+5
   translations). parkour/Times.java: leave as is, note that TimeFormats has no millis style.
6. Then: `./gradlew build`, bot test `cd bot && node lobby-test.js paper` (only if no test server runs:
   `for p in $(pgrep -x java); do readlink /proc/$p/cwd; done | grep test-servers`; stop it after), fetch/rebase,
   version 1.1.0 in build.gradle, commit `chore: release 1.1.0`, `git tag -a v1.1.0 -m "ExyliaLobby 1.1.0"`, push
   main and tag, delete this file before the release commit.

## Gotchas

- `AdminScreens.open` needs exactly two add buttons.
- Wizard `afterwards` (3rd arg of `start`) runs on every ending, finish included; `Setup`'s back runs only on cancel.
- `Setup` names must be plain text (no `{}`, `<>`, `&`): the wizard names come from messages, keep them plain in
  translations.
- Parkour `edit(admin, id, where, ...)` checks the course world against the wizard's location; `where == null`
  skips it (medals).

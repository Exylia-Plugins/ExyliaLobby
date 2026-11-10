# Building ExyliaLobby: contracts for every contributor

Read `/root/Java/CLAUDE.md` (style, menus, item cards, ExyliaLib first, Folia, teleports, respawns) and this file before writing code. The plan: https://claude.ai/artifact/NviyVLwncS2ixzBS7nVx5e

## Architecture

- `ExyliaLobby` boots: `config.yml` (`LobbyConfig`), `messages.yml`, `modules.yml` (card names), `Areas`, `LobbyVisibility`, `AdminHub`, then every module listed in `module/Modules.all` (order = start order; leave runs in reverse).
- Every feature is a `LobbyModule` (extends ExyliaLib `PluginModule`) in its own package `module/<id>/`. Its switch is a boolean in `LobbyConfig.Modules` (component name = module id). Module ids: `spawn state protection join hotbar movement visibility selector games hud pvp parkour gadgets holograms chat onboarding`.
- A module that is off registers nothing. Never reach into another module's classes except through `context.module(id, Type.class)` (empty when off) and degrade gracefully.

## LobbyModule contract

- `settings(Record.class)` → `modules/<id>/config.yml`; `messages(Record.class)` → `lang/<code>/modules/<id>/messages.yml` (translated). Every value an owner may tune is a record component with `@Comment`s, so ConfigMenus edits it in game. No settings screen of our own.
- `listen(listener)` instead of registering listeners yourself (they are removed when the module stops).
- `enter(player, cause)` / `leave(player, cause)` are called on the player's own thread on every lobby crossing, and for everyone already inside when the module is switched on (`MODULE`) or off (`DISABLE`). Whatever `enter` changes on a player, `leave` gives back exactly. Do not listen for join/quit to apply lobby state; use these.
- `context.areas().contains(player)` / `contains(location)`: is it lobby.
- `context.visibility().add(id, rule)` / `remove(id)` / `refresh(target)`: the plugin's only visibility rule. Never call `Packets.of(plugin).visibility().rule` yourself.
- Commands: return Lamp handlers from `commands()` (not `commandHandlers()`, which the base class seals). They are registered for every module at start, running or not, and refused while the module is off, so a handler must not touch what `start()` builds until it runs; player labels come from the module's settings (`List<String> command`), staff subcommands go under `/lobbyadmin` via `adminCommand(handler)`. Lamp rules from `/root/Java/CLAUDE.md` (`@Single`, no-arg overloads).
- Admin screen: override `hasAdmin()` / `openAdmin(viewer, back)`. Its YAML goes to `src/main/resources/lang/en/menus/admin/<id>/...`; register each id with `context.hub().menu("admin/<id>/<name>")` in `start()` so it loads and reloads with the core. Player menus go under `lang/en/menus/<id>/` and are installed with `Menus.of(plugin).refreshVersionedDirectory(...)` / `Languages.refresh` (server-owned, never overwritten).
- Actions: register in `actions(PluginActions)` with ids prefixed by the module id (`parkour_join`), permission-gated for staff (`registerSync(id, permission, handler)`).
- Placeholders: `Placeholders.group(plugin, "lobby")` keys prefixed by the module id (`%lobby_parkour_best_<course>%`).
- Data: ExyliaLib Databases; every `@Table(value = "lobby_<module>_<what>", data = DataKind.X)` with `@Owner` on the player column; indexes for every top. Player preferences go to ExyliaLib Settings (`net.exylia.lib.settings`), not our own tables. Never query the database from a menu lambda; keep caches warm.
- Places set in game (spawn, pads, checkpoints, NPCs, holograms, zones) are stored in the module's YAML (a `Map<String, Record>` in its settings or its own catalogue file), written by the in-game tools.

## ExyliaLib pieces made for this plugin (1.559.0+)

- Live module switches: the core already handles them (`PluginModules.setEnabled`, actions gated, commands refused). Modules need nothing special beyond `enter`/`leave` and `stop()`.
- `net.exylia.lib.hotbar.Hotbars` (docs/hotbars.md): overlay hotbar sets with actions, cooldowns, permissions, locked slots. Overlay items are not real: combat gear must be real items.
- `net.exylia.lib.movement.DoubleJump`, `JumpPads` (docs/movement.md).
- `Proxy.servers()` / `Proxy.server(name)` → `NetworkServer(name, players, online)` (docs/proxy.md), refreshed every 3 s.
- `ConfigMenus.of(plugin)...file(name).open(viewer)` opens one settings file.

## Fleet rules that bite here

- Move players only with `Teleports.of(plugin).to(player, loc).warmup(0).start()`; never `Player#teleport`, never from a join listener directly (the lib defers join moves).
- Respawn location via `Respawns.of(plugin)`; no `PlayerRespawnEvent` listeners.
- Folia: no main-thread assumptions; entity work on `tasks.runAtEntity`, location work on `runAtLocation`.
- Overflowing items go to the reward queue; no fireworks; short quiet sounds played to the player.
- Menus follow the layouts and item-card rules in `/root/Java/CLAUDE.md` (titles `{primary}&lSECTION {letters_black}» {highlight}<subject>`, back buttons, empty fillers, glow for "yours/active").
- Every player-facing string lives in a messages record or a menu YAML, English only (translations come later under `lang/<code>/`).
- API newer than 1.21.1 only through `net.exylia.lib.platform`.

## Ownership while building

Each contributor owns only its packages and resources. Shared files you may touch, minimally: `module/Modules.java` (one line), `config/LobbyConfig.java` (never), `plugin.yml` (permissions block for your module), `config/ModuleNames.java` (never). Do not commit; the lead integrates. Run `./gradlew build` before reporting.

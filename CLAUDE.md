# ExyliaLobby

A complete, modular lobby: spawn, protection, hotbar, movement, server selector, HUD, lobby PvP, parkour and gadgets. Free and open source (GPL-3.0), public at `Exylia-Plugins/ExyliaLobby`. Contracts for contributors: `CONTRIBUTING-AGENTS.md`.

## Releasing

- Every finished change is released, without asking and without `/exylia-release`: bump `version` in `build.gradle` (patch for fixes, minor for features, major for breaking commands or config), commit `chore: release <version>`, tag that commit `v<version>`, and push the branch and the tag.
- The release workflow runs only on `v*` tags and expects the tag to match the `build.gradle` version; it publishes the GitHub release and the fixed-name `ExyliaLobby.jar` that servers auto-update from. A push to `main` without a tag only builds.
- Until 1.0.0 is tagged, pushes only build.

## ExyliaLib

- Always the newest: `build.gradle` resolves the latest GitHub release (or a newer one in mavenLocal) at build time, and that version becomes plugin.yml's `exylia-lib`.
- `ExyliaLobbyPlugin` is the Bukkit entry point and touches no ExyliaLib type; without ExyliaLib, `bootstrap/ExyliaLibMissing` logs where to download it. With it, it hands over to `ExyliaLobby`.
- Updates of this plugin come from ExyliaLib's `PluginUpdater` (`exylia-updates` in plugin.yml).

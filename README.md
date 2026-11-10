# ExyliaLobby

A complete lobby for Paper 1.21.1+ and Folia 1.21.4+, free and open source.

Every part is a module you switch on or off live from `/lobbyadmin`, and every setting, item, pad, course and server is edited in game.

- **Spawn and protection**: spawn on join, void return, no building, damage, hunger, drops, mobs or fire inside the lobby.
- **Hotbar**: items that open menus, send to servers or run commands, and can never leak into a real inventory.
- **Movement**: double jump and launch pads.
- **Server selector**: live player counts, lobby selector, server NPCs and portals.
- **HUD**: sidebar, tab list, rotating boss bars and announcements.
- **Lobby PvP**: hold the sword to fight players who opted in; your hotbar comes back exactly.
- **Parkour**: courses with checkpoints, splits and a ghost of your best run, endless parkour that never repeats itself, and leaderboards shared across lobbies.
- **Gadgets**: grappling hook, ender ride, paint blaster and more harmless toys.

Requires [ExyliaLib](https://modrinth.com/plugin/exylialib). PacketEvents is recommended (hotbar overlay, player visibility, endless parkour).

## Building

Java 21: `./gradlew build`. See `CONTRIBUTING.md`.

## License

GNU General Public License v3.0.

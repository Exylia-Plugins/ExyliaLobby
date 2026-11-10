# Hub modules progress (delete before last commit)
Worktree on detached head from main 7659334. Commit per module, never push.
Shared: module/shared/AdminScreens + menus/admin/list.yml (two add buttons + rows; actions screen_row/add/back route by module id).
Done: 1 hotbar, 2 movement, 3 visibility, 4 selector (hooks: SelectorModule.openServers, openLobbies, join; actions selector_open, selector_lobbies, selector_join), 5 games (action games_open, GamesModule.open)
Todo order: 6 hud, 7 holograms
Notes: modules need softdepends in plugin.yml only for ExyliaPracticeCore (added). Actions ids: selector_open, games_open, visibility_toggle (hotbar defaults use them), %lobby_visibility_state% placeholder.

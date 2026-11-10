# Hub modules progress (delete before last commit)
Worktree on detached head from main 7659334. Commit per module, never push.
Shared: module/shared/AdminScreens + menus/admin/list.yml (two add buttons + rows; actions screen_row/add/back route by module id).
Done: 1 hotbar, 2 movement
Todo order: 2 movement, 3 visibility, 4 selector, 5 games, 6 hud, 7 holograms
Notes: modules need softdepends in plugin.yml only for ExyliaPracticeCore (added). Actions ids: selector_open, games_open, visibility_toggle (hotbar defaults use them), %lobby_visibility_state% placeholder.

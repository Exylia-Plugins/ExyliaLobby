# Polish progress (delete in the last commit)

Done: dump covers player screens, parkour details, staff lists with data (pad, hologram, zone, npc, portal, courses, areas, themes), gadget equip.
- admin list stats use distinct icons; editor/hub/spawn stats too
- parkour staff list texts moved from Java to ParkourMessages.Staff
- gadgets: no double blank line, summary card only offers click when equipped
- pvp top: rank shows a dash when unranked, streak split in two lines
- admin hub: dropped the players-in-lobby stat

Left: re-run dump (lobby-dump.js paper) and read it, then lobby-test.js paper; games screen needs a game provider; ConfigMenus screens belong to the lib; period filter selected tone; gadget locked card shows permission node.

// Dumps every ExyliaLobby screen as rendered (title, slot, name, lore) and flags raw tokens, unresolved placeholders and long lines.
// Usage: (cd bot && node lobby-dump.js <paper|folia> > /tmp/dump.txt)   Owns the server like lobby-test.js and always stops it.
const C = require('./lobby-common')
const { sleep, cmd, connect, openScreen, screen, textOf, fs, path } = C
const NAME = 'ExyBot'
const seen = new Set()
const issues = []
let bot

const strip = s => s.replace(/§./g, '')
function dump(label, s) {
  if (!s) { console.log(`\n### ${label}: NO WINDOW`); issues.push(`${label}: did not open`); return }
  console.log(`\n### ${label} | title: ${s.title}`)
  const groups = new Map()   // identical cards (frame, empty slots) print once with their slots
  for (const it of s.items) {
    const k = [it.type, it.name, ...it.lore].join('\n')
    if (!groups.has(k)) groups.set(k, [])
    groups.get(k).push(it)
  }
  for (const its of groups.values()) {
    const it = its[0], lines = [it.name, ...it.lore]
    if (/glass_pane/.test(it.type) && !it.lore.length) { console.log(`[${its.map(i => i.slot).join(',')}] frame`); continue }
    console.log(`[${its.map(i => i.slot).join(',')}] ${it.type} | ${it.name}`)
    for (const l of it.lore) console.log(`      ${l}`)
    for (const l of lines) {
      if (/%[a-z_]+%|\{[a-z_]+\}|<[a-z_#:]+|&[0-9a-fl-or]|\{icon|§/i.test(l)) issues.push(`${label} [${it.slot}] raw/unresolved: ${l}`)
      if (strip(l).length > 40) issues.push(`${label} [${it.slot}] long (${strip(l).length}): ${l}`)
    }
  }
  if (/%[a-z_]+%|\{[a-z_]+\}|<[a-z_#:]+/i.test(s.title)) issues.push(`${label}: raw title ${s.title}`)
}
const click = async (slot, button = 0) => { bot.clickWindow(slot, button, 0).catch(() => { }); await sleep(1200) }

/** Opens a root screen, dumps it, then clicks each card once and dumps what opens (one level), reopening the root in between. */
async function crawl(label, command, { skip = () => false } = {}) {
  const root = await openScreen(bot, command)
  dump(label, root)
  if (!root) return
  for (const it of root.items) {
    if (skip(it)) continue
    if (/^(glass|.*_pane)$/.test(it.type.replace('minecraft:', '')) && !it.lore.length) continue
    const before = bot.lastTitle, w0 = bot.currentWindow
    const again = await openScreen(bot, command, 800)
    if (!again) continue
    const opened = new Promise(r => bot.once('windowOpen', r))
    const chat0 = bot.chatLog.length
    await click(it.slot)
    const w = await Promise.race([opened, sleep(1500).then(() => null)])
    if (w) {
      await sleep(1000)
      const sub = screen(bot)
      const key = sub.title + '|' + sub.items.length
      if (!seen.has(key)) { seen.add(key); dump(`${label} > [${it.slot}] ${it.name}`, sub) }
    } else {
      const said = bot.chatLog.slice(chat0).join(' | ')
      console.log(`\n--- ${label} > [${it.slot}] ${it.name}: no window${said ? '; chat: ' + said : ''}`)
    }
  }
}

async function main() {
  C.boot(false); C.srv('stop')
  const pk = path.join(C.DIR, 'plugins/ExyliaLobby/modules/parkour/config.yml')
  fs.writeFileSync(pk, fs.readFileSync(pk, 'utf8').replace(/^  areas: \{\}/m, "  areas:\n    sky: {world: world, min: '40 -40 -30', max: '100 0 30', start: '50 -20 0', heading: 0}"))
  const sel = path.join(C.DIR, 'plugins/ExyliaLobby/modules/selector/config.yml')
  fs.writeFileSync(sel, fs.readFileSync(sel, 'utf8').replace(/^lobbies: \[\]/m, 'lobbies:\n- survival\n- minigames')
    .replace(/^npcs: \{\}/m, "npcs:\n  guide: {server: survival, skin: '', world: world, x: 3.5, y: -60, z: 3.5, yaw: 0, pitch: 0, hologram: [], hologram-height: 2.4}")
    .replace(/^portals: \{\}/m, "portals:\n  gate: {server: minigames, world: world, x1: 10, y1: -60, z1: 10, x2: 11, y2: -58, z2: 11}"))
  console.log(C.srv('start').trim())
  bot = await connect(NAME); await sleep(4000)
  cmd(`op ${NAME}`); await sleep(800)
  cmd('fill -15 -61 -15 15 -61 15 stone'); await sleep(800)
  cmd(`tp ${NAME} 0.5 -60 0.5`); await sleep(800)
  for (const c of ['create c1', 'setstart c1', 'addcheckpoint c1', 'setfinish c1']) {
    if (c.startsWith('addcheck')) cmd(`tp ${NAME} 4.5 -60 0.5`); if (c.startsWith('setfinish')) cmd(`tp ${NAME} 8.5 -60 0.5`)
    await sleep(600); bot.chat(`/lobbyadmin parkour ${c}`); await sleep(800)
  }
  cmd(`tp ${NAME} 0.5 -60 0.5`); await sleep(800)
  bot.chat('/parkour c1'); await sleep(1500); bot.chat('/parkour leave'); await sleep(600)

  bot.chat('/lobbyadmin parkour create c2'); await sleep(900)
  // data for the admin lists: one pad, one hologram, one zone, made with the add buttons
  for (const [mod, slot] of [[/movement/, 45], [/holograms/, 45], [/pvp/, 46]]) {
    await openScreen(bot, '/lobbyadmin modules', 700)
    const it = bot.currentWindow.slots.find(x => x && mod.test(textOf(x.customName)) && x.slot < 36)
    await click(it.slot, 1); await sleep(800)
    await click(slot); await sleep(800)
    console.log('created via', mod, bot.chatLog.slice(-1)[0] || '')
  }
  // player screens
  for (const [label, c] of [['servers', '/servers'], ['lobbies', '/lobbies'], ['gadgets', '/gadgets'], ['parkour', '/parkour'], ['parkour top', '/parkour top c1'], ['pvp top', '/pvptop'], ['games', '/games']]) {
    dump(label, await openScreen(bot, c))
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
    console.log('chat:', bot.chatLog.slice(-1)[0] || '')
  }
  // player detail screens and states: a course's details, the endless board, an equipped gadget
  for (const [label, c, slot, button] of [['course details', '/parkour', 10, 1], ['endless board', '/parkour', 11, 1], ['gadget equipped', '/gadgets', 10, 0]]) {
    await openScreen(bot, c, 700); const o = new Promise(r => bot.once('windowOpen', r))
    await click(slot, button); await Promise.race([o, sleep(1500)]); await sleep(900)
    dump(label, bot.currentWindow ? screen(bot) : null)
    if (label === 'gadget equipped') dump('gadgets (equipped)', await openScreen(bot, '/gadgets'))
  }
  // staff parkour screens by name: hub, courses, editor, areas, themes
  const follow = async (label, steps) => {
    await openScreen(bot, '/lobbyadmin modules', 700)
    for (const [pick, button] of steps) {
      const w = bot.currentWindow; if (!w) break
      const it = w.slots.slice(0, w.inventoryStart).find(x => x && (typeof pick === 'number' ? x.slot === pick : pick.test(textOf(x.customName))))
      if (!it) { console.log(`\n--- ${label}: no item ${pick}`); return }
      const o = new Promise(r => bot.once('windowOpen', r))
      await click(it.slot, button); await Promise.race([o, sleep(1500)]); await sleep(900)
    }
    dump(label, bot.currentWindow ? screen(bot) : null)
  }
  const par = [[/parkour/, 1]]
  await follow('parkour hub', par)
  await follow('parkour courses', [...par, [/courses/, 0]])
  await follow('parkour editor c1', [...par, [/courses/, 0], [/c1/, 0]])
  await follow('parkour editor c2 (not ready)', [...par, [/courses/, 0], [/c2/, 0]])
  await follow('parkour areas', [...par, [/areas/, 0]])
  await follow('parkour themes', [...par, [/themes/, 0]])
  // admin: hub and everything a card opens (the modules screen only gets dumped: its cards toggle modules)
  await crawl('admin hub', '/lobbyadmin', { skip: it => /toggle|switch/i.test(it.name) })
  // the modules screen: each module card opens its own admin screen on right-click (left toggles); open each once
  const list = await openScreen(bot, '/lobbyadmin modules')
  for (const it of (list ? list.items : []).filter(i => i.lore.length && !/pane/.test(i.type))) {
    await openScreen(bot, '/lobbyadmin modules', 700)
    const opened = new Promise(r => bot.once('windowOpen', r))
    await click(it.slot, 1)
    if (await Promise.race([opened, sleep(1500).then(() => null)])) { await sleep(900); const sub = screen(bot); const key = sub.title + '|' + sub.items.length; if (!seen.has(key)) { seen.add(key); dump(`module ${it.name} (right-click)`, sub) } }
    else console.log(`\n--- module ${it.name}: right-click opens no window`)
  }
}
main().catch(e => console.log('ERROR', e.stack)).finally(async () => {
  console.log('\n==== issues ====\n' + (issues.join('\n') || 'none'))
  console.log('\n==== server errors ====\n' + (C.serverLog().split('\n').filter(l => /ERROR|Exception/.test(l)).slice(0, 10).join('\n') || 'none'))
  try { bot?.quit() } catch { }
  await sleep(800); try { console.log(C.srv('stop').trim()) } catch { }
  process.exit(0)
})

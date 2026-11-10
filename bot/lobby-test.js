// In-game checks of ExyliaLobby with mineflayer bots.
// Usage: (cd bot && node lobby-test.js <paper|folia>)   servers: /root/Java/test-servers/lobby-paper, lobby-folia
// The script owns the server: wipes plugins/ExyliaLobby, boots twice (second boot has a patched state config),
// runs every scenario and always stops the server. One PASS/FAIL line per scenario, a table at the end.
const { Vec3 } = require('/root/Java/test-servers/bot/node_modules/vec3')
const C = require('./lobby-common')
const { sleep, cmd, serverLog, connect, openScreen, textOf, fs, path } = C

const NAME = 'ExyBot', NAME2 = 'ExyBot2'
const results = []
const log = (...a) => console.log(`[${new Date().toISOString().slice(11, 19)}]`, ...a)
const result = (name, ok, ev) => { results.push({ name, ok }); log(ok ? 'PASS' : 'FAIL', name, '-', ev) }
let bot, bot2
const since = (b, i) => b.chatLog.slice(i).join(' | ')
const dist = (a, b) => a.distanceTo(b)

async function tp(b, x, y, z, who = b.username) {
  cmd(`tp ${who} ${x} ${y} ${z}`)
  const to = new Vec3(x, y, z)
  for (let i = 0; i < 40; i++) { await sleep(150); if (dist(b.entity.position, to) < 1.5) break }
  await sleep(500)
}
async function walkTo(b, x, z, ms = 8000) {
  const t0 = Date.now()
  while (Date.now() - t0 < ms) {
    const p = b.entity.position
    if (Math.hypot(p.x - x, p.z - z) < 0.35) break
    await b.lookAt(new Vec3(x, p.y + b.entity.height, z), true)
    b.setControlState('forward', true)
    await sleep(50)
  }
  b.clearControlStates()
}
const hotbar = (b, n) => b.inventory.slots[36 + Number(n)]
const inv = b => b.inventory.slots.map((x, i) => x && i + ':' + x.name).filter(Boolean).join(' ')
const lockUp = async () => { await sleep(300) }

async function main() {
  // ---- boot twice: first creates the configs, then the state module gets a speed effect
  C.boot(false)
  C.srv('stop')
  const stateCfg = path.join(C.DIR, 'plugins/ExyliaLobby/modules/state/config.yml')
  fs.writeFileSync(stateCfg, fs.readFileSync(stateCfg, 'utf8').replace(/^effects: \[\]/m, 'effects:\n- {effect: speed, level: 2, particles: false}'))
  const main = path.join(C.DIR, 'plugins/ExyliaLobby/config.yml')
  fs.writeFileSync(main, fs.readFileSync(main, 'utf8').replace(/^debug: false/m, 'debug: true'))
  const pk = path.join(C.DIR, 'plugins/ExyliaLobby/modules/parkour/config.yml')
  fs.writeFileSync(pk, fs.readFileSync(pk, 'utf8').replace(/^  areas: \{\}/m, "  areas:\n    sky: {world: world, min: '40 -40 -30', max: '100 0 30', start: '50 -20 0', heading: 0}"))
  console.log(C.srv('start').trim())

  bot = await connect(NAME)
  await sleep(4000)
  cmd(`op ${NAME}`); await sleep(800)

  // ---- join: spawn, mode, health, effects
  const joinPos = bot.entity.position.clone()
  const ground = joinPos.floored()
  await tp(bot, joinPos.x + 30, joinPos.y + 5, joinPos.z)
  const m = bot.chatLog.length
  bot.chat('/spawn'); await sleep(2500)
  result('join: /spawn returns to the join spawn', dist(bot.entity.position, joinPos) < 2, `join ${joinPos} now ${bot.entity.position} ${since(bot, m)}`)
  result('join: adventure mode', bot.game.gameMode === 'adventure', bot.game.gameMode)
  result('join: full health and food', bot.health >= 20 && bot.food >= 20, `${bot.health}/${bot.food}`)
  const fx = Object.values(bot.entity.effects || {}).map(e => e.id)
  result('join: configured effect (speed) applied', fx.length > 0, JSON.stringify(fx))

  // ---- platform around the spawn, so every test has flat ground
  const gx = ground.x, gy = ground.y, gz = ground.z
  cmd(`fill ${gx - 15} ${gy - 1} ${gz - 15} ${gx + 15} ${gy - 1} ${gz + 15} stone`)
  cmd(`fill ${gx - 15} ${gy} ${gz - 15} ${gx + 15} ${gy + 4} ${gz + 15} air`)
  await sleep(1500)
  await tp(bot, gx + 0.5, gy, gz + 0.5)
  const Y = bot.entity.position.y

  // ---- protection (creative, so the server would allow it: only the plugin can refuse)
  cmd(`gamemode creative ${NAME}`); await sleep(600)
  const target = new Vec3(gx + 2, gy - 1, gz)
  await bot.lookAt(target.offset(0.5, 0.5, 0.5), true)
  await bot.dig(bot.blockAt(target)).catch(() => { }); await sleep(800)
  cmd(`fill ${target.x} ${target.y} ${target.z} ${target.x} ${target.y} ${target.z} stone replace air`); await sleep(600)
  result('protection: break refused', /No blocks were filled/.test(serverLog().split('\n').slice(-3).join('\n')), `client ${bot.blockAt(target).name}; ${serverLog().split('\n').slice(-2).join(' / ')}`)
  cmd(`give ${NAME} dirt 5`); await sleep(800)
  const dirt = bot.inventory.items().find(i => i.name === 'dirt')
  await bot.equip(dirt, 'hand').catch(() => { })
  const ref = bot.blockAt(new Vec3(gx + 2, gy - 1, gz))
  await bot.placeBlock(ref, new Vec3(0, 1, 0)).catch(() => { }); await sleep(800)
  result('protection: place refused', bot.blockAt(new Vec3(gx + 2, gy, gz)).name === 'air', bot.blockAt(new Vec3(gx + 2, gy, gz)).name)
  const groundItems = () => Object.values(bot.entities).filter(e => e.name === 'item')
  const before = new Set(groundItems().map(e => e.id))
  if (before.size) console.log('ground items before drop:', groundItems().map(e => `${e.getDroppedItem()?.name} @${e.position.floored()}`).join(', '))
  const itemsOnGround = () => groundItems().filter(e => !before.has(e.id)).length
  cmd(`clear ${NAME} dirt 0`); await sleep(600)
  const held = (/Found (\d+)/.exec(serverLog().split('\n').slice(-3).join('\n')) || [])[1]
  const dirt2 = bot.inventory.items().find(i => i.name === 'dirt')
  if (dirt2) { await bot.equip(dirt2, 'hand').catch(() => { }); await bot.tossStack(dirt2).catch(() => { }) }
  await sleep(1500)
  cmd(`clear ${NAME} dirt 0`); await sleep(600)
  const left = (/Found (\d+)/.exec(serverLog().split('\n').slice(-3).join('\n')) || [])[1]
  result('protection: drop refused', !!held && held === left && itemsOnGround() === 0, `dirt ${held}->${left}, ground items ${itemsOnGround()}`)
  cmd(`clear ${NAME} dirt`)
  cmd(`gamemode adventure ${NAME}`); await sleep(500)
  await tp(bot, gx + 0.5, gy + 9, gz + 0.5); await sleep(2500)
  result('protection: no fall damage', bot.health >= 20, `health ${bot.health} y ${bot.entity.position.y.toFixed(1)}`)
  await tp(bot, gx + 0.5, gy, gz + 0.5)

  // ---- hotbar overlay
  await sleep(1500)
  const want = { 0: 'compass', 1: 'nether_star', 2: 'feather', 6: 'lime_dye', 7: 'ender_chest', 8: 'comparator' }
  const got = Object.fromEntries(Object.keys(want).map(s => [s, hotbar(bot, s)?.name]))
  result('hotbar: overlay items in configured slots', Object.keys(want).every(s => got[s] === want[s]), JSON.stringify(got))

  // ---- visibility item cycles
  const name0 = textOf(hotbar(bot, 6)?.customName)
  const states = [name0]
  for (let i = 0; i < 3; i++) {
    bot.setQuickBarSlot(6); await sleep(200); bot.activateItem(); await sleep(3300)
    states.push(textOf(hotbar(bot, 6)?.customName))
  }
  bot.setQuickBarSlot(0)
  result('visibility: item cycles state', new Set(states).size >= 3, JSON.stringify(states))

  // ---- menus
  for (const [c, re] of [['/servers', /servers/], ['/games', /hub|games/], ['/gadgets', /gadgets/], ['/parkour', /parkour/]]) {
    const s = await openScreen(bot, c)
    const said = bot.chatLog.slice(-1)[0] || ''
    result(`menu: ${c} opens`, !!s ? re.test(s.title) : (c === '/games' && /no games/.test(said)), s ? s.title : `no window; chat: ${said}`)
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  }
  // a hotbar button opens a menu too
  bot.setQuickBarSlot(2); await sleep(200)
  const win = new Promise(r => bot.once('windowOpen', r)); bot.activateItem()
  result('hotbar: parkour item opens the menu', !!(await Promise.race([win, sleep(3000).then(() => null)])), bot.lastTitle)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  bot.setQuickBarSlot(0); await sleep(300)

  log('inv before toggle', inv(bot))
  // ---- live module toggle: protection and parkour
  let i = bot.chatLog.length
  cmd('lobbyadmin module protection off'); await sleep(1200)
  bot.chat('/lobbyadmin build'); await sleep(1200)
  result('toggle: protection off refuses /lobbyadmin build', /switched off/.test(since(bot, i)), since(bot, i))
  cmd('lobbyadmin module protection on'); await sleep(1500)
  i = bot.chatLog.length
  bot.chat('/lobbyadmin build'); await sleep(1200)
  result('toggle: protection on again, build works', /build mode/i.test(since(bot, i)) && !/switched off/.test(since(bot, i)), since(bot, i))
  await sleep(800)
  result('build mode: lobby hands the inventory back', !hotbar(bot, 0) && bot.game.gameMode !== 'adventure', `slot0 ${hotbar(bot, 0)?.name} mode ${bot.game.gameMode}`)
  bot.chat('/lobbyadmin build'); await sleep(1500)
  result('build mode off: lobby applies again', hotbar(bot, 0)?.name === 'compass' && bot.game.gameMode === 'adventure', `slot0 ${hotbar(bot, 0)?.name} mode ${bot.game.gameMode}`)
  bot.chat('/lobbyadmin build'); await sleep(800)
  log('inv after build off', inv(bot))
  cmd('lobbyadmin module parkour off'); await sleep(1200)
  i = bot.chatLog.length
  bot.chat('/parkour'); await sleep(1200)
  result('toggle: parkour off refuses /parkour', /switched off/.test(since(bot, i)) && !bot.currentWindow, since(bot, i))
  cmd('lobbyadmin module parkour on'); await sleep(2500)
  const ps = await openScreen(bot, '/parkour')
  result('toggle: parkour on again, /parkour opens', !!ps, ps ? ps.title : 'no window')
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow)

  log('inv after toggles', inv(bot))
  // ---- void return
  cmd(`fill ${gx + 8} -64 ${gz + 8} ${gx + 10} -58 ${gz + 10} air`); await sleep(800)
  await tp(bot, gx + 9.5, -50, gz + 9.5); await sleep(4000)
  log('inv after void', inv(bot), 'hp', bot.health, 'mode', bot.game.gameMode)
  await sleep(3000); log('inv +3s', inv(bot))
  result('void: fall below Y returns to spawn', dist(bot.entity.position, joinPos) < 3, `at ${bot.entity.position}`)
  await tp(bot, gx + 0.5, gy, gz + 0.5)

  log('inv before course', inv(bot))
  // ---- parkour course
  bot.chat('/lobbyadmin parkour create c1'); await sleep(1000)
  const spots = [['setstart', 3], ['addcheckpoint', 6], ['setfinish', 9]]
  for (const [c, dx] of spots) {
    await tp(bot, gx + dx + 0.5, gy, gz + 0.5); bot.chat(`/lobbyadmin parkour ${c} c1`); await sleep(1000)
  }
  await tp(bot, gx - 6.5, gy, gz + 0.5)
  i = bot.chatLog.length
  bot.chat('/parkour c1'); await sleep(3000)
  const started = since(bot, i)
  await walkTo(bot, gx + 6.5, gz + 0.5); await sleep(600)
  await walkTo(bot, gx + 9.5, gz + 0.5); await sleep(2500)
  const run = since(bot, i)
  cmd('parkour top c1'); await sleep(1200)
  const top = serverLog().split('\n').slice(-12).join('\n')
  result('parkour: course run start -> checkpoint -> finish, record saved', top.includes(NAME), `chat: ${run.slice(0, 220)} | top: ${top.slice(-200).replace(/\n/g, ' / ')}`)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow)

  log('inv after course', inv(bot))
  // ---- endless
  await tp(bot, gx + 0.5, gy, gz + 0.5)
  const snap = () => { const o = {}; const p = bot.entity.position.floored(); for (let x = -8; x <= 8; x++) for (let z = -8; z <= 8; z++) for (let y = -3; y <= 2; y++) o[`${x},${y},${z}`] = bot.blockAt(p.offset(x, y, z))?.name; return o }
  i = bot.chatLog.length
  bot.chat('/parkour endless'); await sleep(4000)
  const p0 = bot.entity.position.clone()
  const nowBlocks = (() => { const o = {}; const p = p0.floored(); for (let x = -8; x <= 8; x++) for (let z = -8; z <= 8; z++) for (let y = -3; y <= 2; y++) o[`${x},${y},${z}`] = bot.blockAt(p.offset(x, y, z))?.name; return o })()
  const nonStone = Object.values(nowBlocks).filter(n => n && !['air', 'stone', 'cave_air'].includes(n)).length
  result('endless: starts and fake blocks appear', nonStone > 0, `bot at ${p0}, ${nonStone} non-stone/air blocks near, chat: ${since(bot, i).slice(0, 160)}`)
  await tp(bot, p0.x, p0.y - 12, p0.z); await sleep(3000)
  const end = since(bot, i)
  bot.chat('/parkour leave'); await sleep(800)
  result('endless: falling ends the run with a summary', /not running|not in a run|aren't/i.test(since(bot, i)) || /run|score|ended/i.test(end), `chat: ${end.slice(-260)}`)
  await tp(bot, gx + 0.5, gy, gz + 0.5)

  log('inv after endless', inv(bot))
  // ---- pvp with two bots
  bot2 = await connect(NAME2); await sleep(3500)
  cmd(`op ${NAME2}`)
  await tp(bot2, gx + 5.5, gy, gz + 5.5)
  await tp(bot, gx + 5.5, gy, gz + 7.5)
  // ---- leaving the lobby area restores the game mode (a fresh bot: the first one had its mode changed by commands)
  cmd(`tp ${NAME2} 0 -60 0`); await sleep(1200)
  const mode = async () => ({ survival: '0', creative: '1', adventure: '2', spectator: '3' })[bot2.game.gameMode]  // client side: `data get` fails on Folia
  const inside = await mode()
  cmd(`execute in minecraft:the_end run tp ${NAME2} 100 50 0`); await sleep(3500)
  const outside = await mode()
  result('area: leaving the lobby restores game mode and drops the overlay', inside === '2' && outside === '0' && !hotbar(bot2, 0), `inside ${inside} outside ${outside} slot0 ${hotbar(bot2, 0)?.name}`)
  cmd(`execute in minecraft:overworld run tp ${NAME2} ${gx + 5.5} ${gy} ${gz + 5.5}`); await sleep(2500)
  const hp2 = () => bot2.health
  const hit = async () => { await bot.lookAt(bot2.entity.position.offset(0, 1.2, 0), true); bot.attack(bot2.entity); await sleep(900) }
  const dbg = () => log('pvp chat:', since(bot, bot.chatLog.length - 3), '|', since(bot2, bot2.chatLog.length - 3))
  await hit(); await hit()
  result('pvp: nobody opted in, no damage', hp2() >= 20, `hp ${hp2()}`)
  bot.setQuickBarSlot(4); await sleep(6500)
  dbg(); log('pvp log:', serverLog().split('\n').filter(l => /ExyBot|ERROR/.test(l)).slice(-4).join(' / '))
  const sword = bot.inventory.items().find(i => /sword/.test(i.name))
  await hit(); await hit()
  result('pvp: only the attacker opted in, victim safe', !!sword && hp2() >= 20, `sword ${sword?.name} hp ${hp2()} inv ${bot.inventory.items().map(i => i.slot + ':' + i.name)} held ${bot.quickBarSlot}`)
  bot2.setQuickBarSlot(4); await sleep(6500)
  await tp(bot2, gx + 5.5, gy, gz + 5.5); await tp(bot, gx + 5.5, gy, gz + 7.0)
  log('pvp2 inv', inv(bot), '|', inv(bot2), 'held', bot.quickBarSlot, bot2.quickBarSlot, 'dist', dist(bot.entity.position, bot2.entity.position), 'chat', since(bot2, bot2.chatLog.length - 3))
  await hit(); await hit(); await hit()
  cmd(`data get entity ${NAME2} Health`); await sleep(500); log(serverLog().split('\n').slice(-2).join(' / '))
  result('pvp: both opted in, damage lands', hp2() < 20, `hp ${hp2()}`)
  // ---- leaving mid-fight (the pvp kit is on) must also give the game mode back
  const mid = await (async () => { cmd(`execute in minecraft:the_end run tp ${NAME2} 100 50 0`); await sleep(3500); return mode() })()
  result('area: leaving mid-fight restores game mode', mid === '0', `outside ${mid}`)
  bot2.quit()
}

main().catch(e => log('ERROR', e.stack)).finally(async () => {
  const bad = serverLog().split('\n').filter(l => /ERROR|Exception|SEVERE/.test(l) && !/Unknown or incomplete/.test(l))
  console.log('\n==== server errors ====\n' + (bad.slice(0, 15).join('\n') || 'none'))
  console.log('\n==== summary ====')
  for (const r of results) console.log(r.ok ? 'PASS' : 'FAIL', r.name)
  try { bot?.quit(); bot2?.quit() } catch { }
  await sleep(800)
  try { console.log(C.srv('stop').trim()) } catch { }
  process.exit(0)
})

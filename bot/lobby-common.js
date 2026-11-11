// Shared helpers of lobby-test.js and lobby-dump.js: server control, a mineflayer bot, chat and window capture.
const { createRequire } = require('module')
const req = createRequire('/root/Java/test-servers/bot/package.json')
const mineflayer = req('mineflayer')
const nbt = req('prismarine-nbt')
const { execFileSync } = require('child_process')
const path = require('path')
const fs = require('fs')

const TS = '/root/Java/test-servers'
const SRV = 'lobby-' + (process.argv[2] || 'paper')
const DIR = path.join(TS, SRV)
const PORT = Number(/server-port=(\d+)/.exec(fs.readFileSync(path.join(DIR, 'server.properties'), 'utf8'))[1])
const sleep = ms => new Promise(r => setTimeout(r, ms))
const srv = (...a) => execFileSync(path.join(TS, 'srv.sh'), [SRV, ...a], { encoding: 'utf8' })
const cmd = c => srv('send', c)
const SMALL = 'ᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ', ASCII = 'abcdefghijklmnopqrstuvwxyz'
const fold = s => [...s].map(c => { const i = SMALL.indexOf(c); return i < 0 ? c : ASCII[i] }).join('')
const flat = n => n == null ? '' : typeof n === 'string' ? n : Array.isArray(n) ? n.map(flat).join('')
  : (n.text ?? n[''] ?? (n.translate || '')) + (n.extra ? n.extra.map(flat).join('') : '')
function rawText(x) {
  if (x == null) return ''
  if (typeof x === 'string') { try { return flat(JSON.parse(x)) } catch { return x } }
  if (x.type && x.value !== undefined) return flat(nbt.simplify(x))
  return flat(x)
}
const textOf = x => fold(rawText(x))
const serverLog = () => fold(fs.readFileSync(path.join(DIR, 'console.out'), 'utf8').replace(/\x1b\[[0-9;]*m/g, ''))

/** A bot with chat, titles and window-title capture. */
function connect(name, onSpawn) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, version: '1.21.4', auth: 'offline' })
    bot.chatLog = []; bot.titles = []; bot.lastTitle = ''
    bot._client.on('system_chat', p => {
      if (p.isActionBar) return
      try { bot.chatLog.push(fold(flat(typeof p.content === 'string' ? JSON.parse(p.content) : nbt.simplify(p.content)))) } catch { bot.chatLog.push(String(p.content)) }
    })
    bot._client.on('set_title_text', p => bot.titles.push(textOf(p.text)))
    bot._client.on('open_window', p => { bot.lastTitle = textOf(p.windowTitle ?? p.title) })
    bot.on('kicked', r => console.log('kicked', name, JSON.stringify(r)))
    bot.on('error', e => console.log('error', name, e.message))
    bot.once('spawn', () => resolve(bot))
    setTimeout(() => reject(new Error('no spawn in 30 s')), 30000)
  })
}
const describe = it => it && { slot: it.slot, type: it.name, name: textOf(it.customName) || it.displayName, lore: (it.customLore || []).map(textOf) }
const screen = bot => {
  const w = bot.currentWindow
  return w ? { title: bot.lastTitle, items: w.slots.slice(0, w.inventoryStart).filter(Boolean).map(describe) } : null
}
async function openScreen(bot, chatCmd, wait = 1500) {
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400) }
  const opened = new Promise(r => bot.once('windowOpen', r))
  bot.chat(chatCmd)
  const w = await Promise.race([opened, sleep(5000).then(() => null)])
  if (!w) return null
  await sleep(wait)
  return screen(bot)
}
const shift = (bot, on) => bot._client.write('entity_action', { entityId: bot.entity.id, actionId: on ? 0 : 1, jumpBoost: 0 })
// Wizard gestures (ExyliaLib wizards). Clicks are made from an empty hotbar slot, so no lobby button answers them.
/** A "stand there" step (spawn): sneak and right-click the floor under the bot. */
async function standClick(bot, slot = 3) {
  bot.setQuickBarSlot(slot); await sleep(200)
  shift(bot, true); await sleep(250)
  await bot.activateBlock(bot.blockAt(bot.entity.position.offset(0, -0.5, 0))).catch(() => { }); await sleep(400)
  shift(bot, false); await sleep(800)
}
/** A "click a block" step. */
async function blockClick(bot, block, slot = 3) {
  bot.setQuickBarSlot(slot); await sleep(200)
  await bot.activateBlock(block).catch(() => { }); await sleep(1000)
}
/** An area step: left-click one corner, right-click the other, then sneak + left-click to accept the box. */
async function selectArea(bot, a, b) {
  await sleep(800)
  const wand = [...Array(9).keys()].find(i => /axe|wand/.test(bot.inventory.slots[36 + i]?.name || ''))
  if (wand !== undefined) bot.setQuickBarSlot(wand)
  await sleep(300)
  // adventure mode: Paper turns the swing at a block into a left click on it
  await bot.lookAt(a.offset(0.5, 0.5, 0.5), true); bot.swingArm(); await sleep(700)
  await bot.activateBlock(bot.blockAt(b)).catch(() => { }); await sleep(700)
  shift(bot, true); await sleep(250); bot.swingArm(); await sleep(400); shift(bot, false); await sleep(1000)
}
/** Answers a confirm window (yes or no); returns the clicked button's name, or null when none was found. */
async function answer(bot, yes, wait = 5000) {
  for (let i = 0; i < wait / 100 && !bot.currentWindow; i++) await sleep(100)
  await sleep(500)
  const s = screen(bot)
  if (!s) { console.log('answer: no window'); return null }
  const re = yes ? /confirm|yes|accept|✔/i : /deny|^no\b|cancel|decline|✘/i
  const it = s.items.find(i => re.test(i.name))
  if (!it) { console.log('answer: no button in', s.title, '|', s.items.map(i => i.slot + ':' + i.name).join(' | ')); return null }
  await bot.clickWindow(it.slot, 0, 0).catch(() => { }); await sleep(1200)
  return it.name
}
/** Starts the server fresh (plugin data wiped unless keep). */
function boot(keep) {
  try { srv('stop') } catch { }
  if (!keep) {
    fs.rmSync(path.join(DIR, 'plugins', 'ExyliaLobby'), { recursive: true, force: true })
    // bots must join fresh, not where the last run left them (the end is not lobby)
    for (const w of ['world', 'world_nether', 'world_the_end']) fs.rmSync(path.join(DIR, w, 'playerdata'), { recursive: true, force: true })
  }
  console.log(srv('start').trim())
}
module.exports = { TS, SRV, DIR, sleep, srv, cmd, fold, flat, textOf, serverLog, connect, describe, screen, openScreen, boot, standClick, blockClick, selectArea, answer, fs, path }

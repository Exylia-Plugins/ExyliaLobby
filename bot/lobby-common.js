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
module.exports = { TS, SRV, DIR, sleep, srv, cmd, fold, flat, textOf, serverLog, connect, describe, screen, openScreen, boot, fs, path }

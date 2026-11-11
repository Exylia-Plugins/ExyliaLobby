// Triggers module: a block trigger runs on a click, an area trigger on entering and leaving.
// Usage: node triggers-test.js [paper|folia]  (server running, module on)
const C = require('./lobby-common')
const fs = require('fs'), path = require('path')
;(async () => {
  const before = C.serverLog().length
  const bot = await C.connect('TrigBot')
  await C.sleep(2500)
  const p = bot.entity.position.floored()
  const below = p.offset(2, 0, 0)
  C.cmd(`setblock ${below.x} ${below.y} ${below.z} stone_button[face=floor]`); await C.sleep(800)
  const file = path.join(C.DIR, 'plugins/ExyliaLobby/modules/triggers/config.yml')
  const ax = p.x + 10
  fs.writeFileSync(file, `list:
  block-one:
    world: world
    min: '${below.x} ${below.y} ${below.z}'
    max: '${below.x} ${below.y} ${below.z}'
    area: false
    permission: ''
    cooldown: 1s
    actions:
    - 'right: console: say TRIGGER-BLOCK %player_name% %trigger%'
    leave-actions: []
  area-one:
    world: world
    min: '${ax} ${p.y - 2} ${p.z - 2}'
    max: '${ax + 4} ${p.y + 3} ${p.z + 2}'
    area: true
    permission: ''
    cooldown: 0s
    actions:
    - 'console: say TRIGGER-ENTER %player_name%'
    leave-actions:
    - 'console: say TRIGGER-LEAVE %player_name%'
config-version: 1
`)
  C.cmd('lobbyadmin reload'); await C.sleep(2000)
  console.log('target', bot.blockAt(below)?.name)
  bot.setQuickBarSlot(3); await C.sleep(300) // an empty slot: a lobby hotbar button in hand turns the click into its own press
  await bot.lookAt(below.offset(0.5, 0.2, 0.5), true)
  await bot.activateBlock(bot.blockAt(below)); await C.sleep(400)
  await bot.activateBlock(bot.blockAt(below)); await C.sleep(1200) // inside the 1s cooldown: one run only
  C.cmd(`tp TrigBot ${ax + 2}.5 ${p.y} ${p.z}.5`); await C.sleep(1500)
  C.cmd(`tp TrigBot ${p.x}.5 ${p.y} ${p.z}.5`); await C.sleep(1500)
  const log = C.serverLog().slice(before)
  const count = s => (log.match(new RegExp(s, 'g')) || []).length
  const result = { block: count('TRIGGER-BLOCK TrigBot block-one'), enter: count('TRIGGER-ENTER TrigBot'), leave: count('TRIGGER-LEAVE TrigBot') }
  console.log(JSON.stringify(result))
  console.log(result.block === 1 && result.enter === 1 && result.leave === 1 ? 'PASS' : 'FAIL')
  bot.quit(); process.exit(0)
})().catch(e => { console.error(e); process.exit(1) })

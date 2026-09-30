// 我的地盘（my）+ 待办（todo）界面检查
//
// 覆盖：/zentao/my
//   ① 概览卡片：今天的待办数 = 页面默认列表的行数（同为「今天 + 未完成」口径）
//   ② 待办 Tab：范围切换（今天/明天）、每行的类型/优先级/指派
//   ③ 新建待办弹窗：字段齐全 + 「待办是个人清单」的说明
//   ④ 完成待办：点「完成」后状态标签变成已完成（走 UI 状态流转）
//   ⑤ 我的动态 Tab：时间线有内容
// 数据准备与清理走后端接口，保证可重复执行。
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { login } from './_login.mjs'
const _pw = await import(pathToFileURL(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim() + '/playwright/index.js').href)
const chromium = _pw.chromium || _pw.default?.chromium
const BASE = process.env.ZENTAO_UI_BASE || 'http://localhost'
const API_BASE = process.env.ZENTAO_API_BASE || 'http://localhost:48080/admin-api'

const loginResp = await fetch(`${API_BASE}/system/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'tenant-id': '1' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
}).then((r) => r.json())
const tok = loginResp.data.accessToken
const api = async (m, u, b) => (await fetch(API_BASE + u, {
  method: m,
  headers: { Authorization: 'Bearer ' + tok, 'tenant-id': '1', 'Content-Type': 'application/json' },
  body: b ? JSON.stringify(b) : undefined
})).json()

const TS = Date.now()
const NAME = 'UI待办-' + TS
const created = await api('POST', '/zentao/todo/create', { name: NAME, pri: 1 })
if (created.code !== 0) throw new Error('准备待办失败：' + JSON.stringify(created))
const TODO = created.data
console.log(`准备: 待办=${TODO}（${NAME}，今天、未完成）`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
const p = await c.newPage()

// 断言失败时脚本会直接抛异常退出 —— 如果不在退出前关掉浏览器，
// 每个失败的 Playwright 进程都会留下一个 headless Chromium 挂在那里吃 CPU/内存。
const __cleanup = () => { try { b.close() } catch { /* 已经关掉了 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })
p.on('pageerror', (e) => console.log('  [pageerror]', String(e.stack || e).split('\n').slice(0, 3).join(' | ')))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))

await login(p, BASE)
console.log('登录成功')

const waitList = (frag, ms = 60000) =>
  new Promise((resolve) => {
    const handler = (r) => {
      if (r.url().includes(frag)) { p.off('response', handler); resolve(true) }
    }
    p.on('response', handler)
    setTimeout(() => { p.off('response', handler); resolve(false) }, ms)
  })

const arrived = waitList('/zentao/todo/my-list')
await p.goto(BASE + '/zentao/my', { waitUntil: 'domcontentloaded' })
await arrived
await p.waitForTimeout(2500)

const read = async () =>
  p.evaluate(() => {
    // 概览卡片限定在 el-row 里 —— ContentWrap 本身也是一个 el-card，会把整页文字都算进来
    const cards = Array.from(document.querySelectorAll('.el-row .el-card')).map((el) =>
      el.innerText.replace(/\s+/g, ' ').trim()
    )
    const pane = Array.from(document.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
    const rows = pane ? Array.from(pane.querySelectorAll('.el-table__body tbody tr')) : []
    return {
      cards,
      headers: pane ? Array.from(pane.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean) : [],
      rows: rows.map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 100))
    }
  })

const initial = await read()
console.log('① 页面加载 :', JSON.stringify({ cards: initial.cards, rows: initial.rows.length }))
for (const h of ['日期', '待办', '类型', '优先级', '状态', '指派给']) {
  if (!initial.headers.includes(h)) throw new Error('待办表头缺少「' + h + '」：' + JSON.stringify(initial.headers))
}
if (!initial.rows.some((r) => r.includes(NAME))) throw new Error('新建的待办没出现在「今天」列表里')
const todayCard = initial.cards.find((t) => t.includes('今天的待办')) || ''
const cardCount = Number((todayCard.match(/^(\d+)/) || [])[1] || 0)
if (cardCount !== initial.rows.length) {
  throw new Error(`概览卡片(${cardCount})与列表行数(${initial.rows.length})不一致`)
}
if (!initial.rows.some((r) => r.includes('私有'))) {
  console.log('   提示：今天没有私有待办（演示数据里那条私有待办可能被移到了别的日期）')
}

// 我的动态：切 Tab
await p.locator('.el-tabs__item').filter({ hasText: '我的动态' }).first().click()
await p.waitForTimeout(2500)
const timeline = await p.evaluate(() => document.querySelectorAll('.el-timeline-item').length)
console.log('② 我的动态 :', timeline, '条')
if (timeline < 1) throw new Error('我的动态时间线是空的')

// ③ 新建弹窗
await p.locator('.el-tabs__item').filter({ hasText: '待办' }).first().click()
await p.waitForTimeout(1500)
await p.locator('button:has-text("新建待办")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '新建待办' }).first().waitFor({ state: 'visible', timeout: 20000 })
const dialog = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  return {
    labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.trim()),
    alert: Array.from(d.querySelectorAll('.el-alert__title, .el-alert__description'))
      .map((e) => e.innerText.trim()).join(' | ').slice(0, 80)
  }
})
console.log('③ 新建弹窗 :', JSON.stringify(dialog))
for (const l of ['待办名称', '日期', '类型', '优先级', '指派给', '私有']) {
  if (!dialog.labels.includes(l)) throw new Error('弹窗缺少字段「' + l + '」：' + JSON.stringify(dialog.labels))
}
if (!dialog.alert.includes('个人清单') || !dialog.alert.includes('我今天要干')) {
  throw new Error('没有说明「待办是个人清单」：' + dialog.alert)
}
await p.locator('.el-dialog__headerbtn:visible').first().click()
await p.waitForTimeout(1000)

// ④ 完成待办（走 UI 状态流转）
const row = p.locator('.el-table__body tbody tr').filter({ hasText: NAME }).first()
await row.locator('button:has-text("完成")').first().click()
await p.waitForTimeout(3000)
const after = await read()
// 默认筛选是「未完成」：完成之后它应当从列表里消失
if (after.rows.some((r) => r.includes(NAME))) {
  throw new Error('点「完成」后它仍留在「未完成」列表里：' + JSON.stringify(after.rows))
}
const stat = (await api('GET', '/zentao/todo/get?id=' + TODO)).data
if (stat.status !== 'done' || !stat.finishedBy) throw new Error('接口里的状态/完成人不对：' + JSON.stringify(stat))
console.log('④ 完成之后 :', '已从未完成列表消失，接口状态=' + stat.status + '，完成人=' + stat.finishedBy)

// 再把状态筛成「已完成」，它应该带着「已完成」标签出现
await p.locator('.el-form-item:has-text("状态") .el-select').first().click()
await p.waitForTimeout(800)
await p.locator('.el-select-dropdown__item:visible').filter({ hasText: '已完成' }).first().click()
await p.waitForTimeout(2500)
const doneList = await read()
const doneRow = doneList.rows.find((r) => r.includes(NAME)) || ''
console.log('   已完成筛选 :', JSON.stringify(doneRow))
if (!doneRow.includes('已完成')) throw new Error('按「已完成」筛选后看不到它：' + doneRow)

// ⑤ 范围切到「明天」：本次待办不应出现
await p.locator('.el-form-item:has-text("范围") .el-select').first().click()
await p.waitForTimeout(800)
await p.locator('.el-select-dropdown__item:visible').filter({ hasText: '明天' }).first().click()
await p.waitForTimeout(2500)
const tomorrow = await read()
console.log('⑤ 明天范围 :', JSON.stringify(tomorrow.rows))
if (tomorrow.rows.some((r) => r.includes(NAME))) throw new Error('切到「明天」后仍能看到今天的待办')

await p.screenshot({ path: '/tmp/zentao-my.png', fullPage: true })
console.log('截图       : /tmp/zentao-my.png')

// 收尾
await api('DELETE', '/zentao/todo/delete?id=' + TODO)
console.log('已清理测试数据')
await b.close()

// 执行燃尽图界面检查
//
// 覆盖 /zentao/execution 的「燃尽图」抽屉：
//   ① 点行上的「燃尽图」→ 抽屉打开、ECharts 画布渲染、图例有「实际/理想」
//   ② 切「原计划工时」曲线 → 提示文案里的字段名跟着变
//   ③ 点「重新计算」→ 提示写入条数 + 快照表格有今天那一行
//   ④ 切「含周末」→ 点数变多
//
// 数据准备与清理走后端接口，保证可重复执行。
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { login } from './_login.mjs'
const _pw = await import(pathToFileURL(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim() + '/playwright/index.js').href)
const chromium = _pw.chromium || _pw.default?.chromium
const BASE = process.env.ZENTAO_UI_BASE || 'http://localhost'
const API_BASE = process.env.ZENTAO_API_BASE || 'http://localhost:48080/admin-api'

let PASS = 0; let FAIL = 0
const ok = (name, cond, extra = '') => {
  if (cond) { PASS++; console.log(`  ✅ ${name}${extra ? '  ' + extra : ''}`) }
  else { FAIL++; console.log(`  ❌ ${name}${extra ? '  ' + extra : ''}`) }
}

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

// 用**本地时区**拼日期：toISOString() 是 UTC，凌晨 0~8 点（UTC+8）会算成「昨天」，
// 而页面上的日期来自服务端本地时间 —— 两边一错位，断言就假红（2026-09-18 00:5x 那次）。
const day = (offset) => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

let EID, TID, PID = 1
const cleanup = async () => {
  if (TID) await api('DELETE', '/zentao/task/delete?id=' + TID).catch(() => {})
  // zt_burn 没有 deleted 列（快照是事实数据），执行删掉后快照会留在库里 ——
  // 它只按 execution 编号读取，编号不复用，所以对演示数据没有影响；
  // 需要彻底清干净时用 deploy/test-burn-module.sh（那边走 SQL 物理删）
  if (EID) await api('DELETE', '/zentao/execution/delete?id=' + EID).catch(() => {})
}

// 准备：一个跨越今天的迭代 + 一个任务
const NAME = 'BURN-UI迭代-' + Date.now()
const exec = await api('POST', '/zentao/execution/create', {
  name: NAME, project: PID, type: 'sprint', pri: 3, begin: day(-6), end: day(6), PM: 'admin'
})
if (exec.code !== 0) throw new Error('准备迭代失败：' + JSON.stringify(exec))
EID = exec.data
const task = await api('POST', '/zentao/task/create', {
  project: PID, execution: EID, name: 'BURN-UI任务', type: 'devel', pri: 3, estimate: 12, left: 8
})
if (task.code !== 0) throw new Error('准备任务失败：' + JSON.stringify(task))
TID = task.data
await api('POST', '/zentao/execution/compute-burn?id=' + EID)
console.log(`准备: 迭代=${EID}（${day(-6)} ~ ${day(6)}）任务=${task.data}`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
const p = await c.newPage()
const pageErrors = []
p.on('pageerror', (e) => pageErrors.push(String(e.stack || e).split('\n')[0]))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', async (err) => { __cleanup(); await cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', async (err) => { __cleanup(); await cleanup(); console.error(err); process.exit(1) })

await login(p, BASE)
console.log('登录成功')

await p.goto(BASE + '/zentao/execution', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(6000)

// ---------------- ① 打开抽屉 ----------------
const row = p.locator('.el-table__body tbody tr').filter({ hasText: NAME }).first()
ok('新迭代出现在列表里', (await row.count()) > 0, NAME)
await row.locator('button:has-text("燃尽图")').first().click()
await p.locator('.el-drawer__title').filter({ hasText: '燃尽图' }).first().waitFor({ state: 'visible', timeout: 20000 })
await p.waitForTimeout(3000)
const drawer = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null)
  if (!d) return null
  return {
    text: d.innerText.replace(/\s+/g, ' ').trim(),
    canvas: d.querySelectorAll('canvas').length,
    svg: d.querySelectorAll('svg').length,
    rows: Array.from(d.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim())
  }
})
console.log('① 抽屉 :', JSON.stringify({ canvas: drawer?.canvas, svg: drawer?.svg, rows: drawer?.rows.length }))
ok('抽屉打开', !!drawer)
ok('ECharts 画布渲染出来了', (drawer?.canvas || 0) > 0 || (drawer?.svg || 0) > 0, `canvas=${drawer?.canvas} svg=${drawer?.svg}`)
ok('提示区有区间与点数', /区间 \d{4}-\d{2}-\d{2} ~ \d{4}-\d{2}-\d{2}，共 \d+ 个点/.test(drawer?.text || ''),
  (drawer?.text || '').slice(0, 90))
ok('说明写了「画的是每天的快照」', (drawer?.text || '').includes('快照'), '')
ok('快照表里有今天那一行', (drawer?.rows || []).some((r) => r.includes(day(0))), JSON.stringify((drawer?.rows || []).slice(0, 2)))
ok('今天的快照是 原计划 12 / 剩余 8', (drawer?.rows || []).some((r) => r.includes('12') && r.includes('8')),
  JSON.stringify((drawer?.rows || [])[0] || ''))

// ---------------- ② 切换曲线字段 ----------------
await p.locator('.el-drawer:visible .el-form-item:has-text("曲线") .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '原计划工时' }).first().click()
await p.waitForTimeout(3000)
const afterSwitch = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null)
  return d ? d.innerText.replace(/\s+/g, ' ').trim() : ''
})
ok('切到「原计划工时」后提示里的理想线起点仍是 12', afterSwitch.includes('理想线从 12'), afterSwitch.slice(0, 120))

// ---------------- ③ 重新计算 ----------------
await p.locator('.el-drawer:visible button:has-text("重新计算")').first().click()
await p.waitForTimeout(3000)
const afterCompute = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null)
  const msg = Array.from(document.querySelectorAll('.el-message')).map((m) => m.innerText.trim()).join(' | ')
  return { msg, rows: Array.from(d.querySelectorAll('.el-table__body tbody tr')).length }
})
console.log('③ 重新计算 :', JSON.stringify(afterCompute))
ok('弹出「已写入 N 条快照」提示', /已写入 \d+ 条快照/.test(afterCompute.msg), afterCompute.msg)
ok('重新计算后快照仍是 1 行（当天覆盖）', afterCompute.rows === 1, `行数=${afterCompute.rows}`)

// ---------------- ④ 含周末 ----------------
await p.locator('.el-drawer:visible .el-form-item:has-text("日期") .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '含周末' }).first().click()
await p.waitForTimeout(3000)
const weekendText = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null)
  return d ? d.innerText.replace(/\s+/g, ' ').trim() : ''
})
const countOf = (t) => Number((t.match(/共 (\d+) 个点/) || [])[1] || 0)
// 「含周末」之前的那次文案就是 noweekend 的点数（afterSwitch 是切曲线字段后抓的）
const noweekendCount = countOf(afterSwitch)
ok('含周末后点数变多', countOf(weekendText) > noweekendCount, `${noweekendCount} → ${countOf(weekendText)}`)

ok('页面没有 JS 报错', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '))

await p.screenshot({ path: '/tmp/zentao-burn.png', fullPage: true })
console.log('截图       : /tmp/zentao-burn.png')

await cleanup()
console.log('已清理测试执行')
await b.close()

console.log('======================================================')
console.log(`  burn 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

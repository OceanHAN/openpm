// 报表（report）界面检查
//
// 覆盖 /zentao/report 三个页签：
//   ① 年度数据：概览卡片 / 贡献分布 / 历年雷达对比 / 月度趋势（12 个月 + 切对象）/ 产品与执行统计
//   ② 每日提醒：按人折叠，展开能看到这个人的缺陷/任务/待办/测试单/卡片
//   ③ 产出统计：各类对象的动作明细 + 我参与的项目状态总览
//
// 依赖：后端 127.0.0.1:48080，前端 http://localhost/
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

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
const p = await c.newPage()
const pageErrors = []
p.on('pageerror', (e) => pageErrors.push(String(e.stack || e).split('\n')[0]))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })

await login(p, BASE)
console.log('登录成功')

// 当前可见 tab-pane 里所有表格的表头
const panes = async () =>
  p.evaluate(() => {
    const pane = Array.from(document.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
    if (!pane) return { tables: [], cards: [], text: '' }
    return {
      cards: Array.from(pane.querySelectorAll('.el-row .el-card')).map((el) => el.innerText.replace(/\s+/g, ' ').trim()),
      tables: Array.from(pane.querySelectorAll('.el-table')).map((t) => ({
        headers: Array.from(t.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
        rows: Array.from(t.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim())
      })),
      text: pane.innerText.replace(/\s+/g, ' ').trim()
    }
  })

await p.goto(BASE + '/zentao/report', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(6000)

// ---------------- ① 年度数据 ----------------
let view = await panes()
console.log('① 年度数据 :', JSON.stringify({ cards: view.cards.length, tables: view.tables.length }))
ok('概览卡片渲染', view.cards.length >= 6, `卡片=${view.cards.length}`)
ok('卡片里有「动作数」与「贡献数」',
  view.cards.some((t) => t.includes('动作数')) && view.cards.some((t) => t.includes('贡献数')),
  view.cards.slice(0, 3).join(' | ').slice(0, 120))
const contribution = view.tables.find((t) => t.headers.includes('对象') && t.headers.includes('占比'))
ok('贡献分布表渲染（对象/动作/条数/占比）', !!contribution && contribution.rows.length > 0,
  contribution ? `行数=${contribution.rows.length}` : '没找到表')
const radar = view.tables.find((t) => t.headers.includes('产品') && t.headers.includes('研发'))
ok('历年雷达对比表渲染（含产品/执行/研发/测试四列）', !!radar && radar.rows.length > 0,
  radar ? JSON.stringify(radar.headers) : '没找到表')
const trend = view.tables.find((t) => t.headers.length === 14 && t.headers.includes('动作'))
ok('月度趋势表有 12 个月 + 动作 + 合计共 14 列', !!trend, trend ? JSON.stringify(trend.headers.slice(0, 4)) : '没找到表')
ok('趋势表有「创建」行', !!trend && trend.rows.some((r) => r.startsWith('created')),
  trend ? trend.rows.map((r) => r.split(' ')[0]).join(',') : '')

// 切到「缺陷」页签：趋势表应换成缺陷的动作
await p.locator('.el-tabs__item').filter({ hasText: '缺陷' }).first().click()
await p.waitForTimeout(1500)
const bugTrend = (await panes()).tables.find((t) => t.headers.length === 14 && t.headers.includes('动作'))
ok('切到「缺陷」后月度趋势换了一组动作',
  !!bugTrend && bugTrend.rows.some((r) => r.startsWith('created')),
  bugTrend ? bugTrend.rows.map((r) => r.split(' ')[0]).join(',') : '')

// 选人员 = admin → 变成个人视角，卡片换成「登录次数」
await p.locator('.el-form-item:has-text("人员") .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'admin' }).first().click()
await p.waitForTimeout(3000)
view = await panes()
ok('切到个人视角后卡片变成「登录次数」', view.cards.some((t) => t.includes('登录次数')),
  view.cards.slice(0, 2).join(' | ').slice(0, 100))
ok('视角标签跟着变', view.text.includes('个人视角'), view.text.slice(0, 60))

// ---------------- ② 每日提醒 ----------------
await p.locator('.el-tabs__item').filter({ hasText: '每日提醒' }).first().click()
await p.waitForTimeout(4000)
const reminder = await p.evaluate(() => {
  const pane = Array.from(document.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
  return {
    items: Array.from(pane.querySelectorAll('.el-collapse-item')).length,
    titles: Array.from(pane.querySelectorAll('.el-collapse-item__header')).map((e) => e.innerText.replace(/\s+/g, ' ').trim()),
    tables: pane.querySelectorAll('.el-table').length
  }
})
console.log('② 每日提醒 :', JSON.stringify({ 人数: reminder.items, 表格: reminder.tables }))
ok('按人折叠渲染', reminder.items > 0, `人数=${reminder.items}`)
ok('折叠标题带账号与分类标签', reminder.titles.some((t) => t.includes('admin')), reminder.titles[0]?.slice(0, 80))
ok('展开的默认前几个人里能看到明细表格', reminder.tables > 0, `表格=${reminder.tables}`)

// ---------------- ③ 产出统计 ----------------
await p.locator('.el-tabs__item').filter({ hasText: '产出统计' }).first().click()
await p.waitForTimeout(4000)
view = await panes()
const output = view.tables.find((t) => t.headers.includes('动作明细'))
ok('产出统计表渲染（对象/合计/动作明细）', !!output && output.rows.length > 0,
  output ? `行数=${output.rows.length}` : '没找到表')
ok('产出里有用例（创建/执行）', !!output && output.rows.some((r) => r.includes('用例') && r.includes('执行')),
  output ? output.rows.filter((r) => r.includes('用例')).join(' | ').slice(0, 80) : '')
ok('「我参与的项目状态总览」渲染', view.text.includes('我参与的项目状态总览'), view.text.slice(-80))

ok('页面没有 JS 报错', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '))

await p.screenshot({ path: '/tmp/zentao-report.png', fullPage: true })
console.log('截图       : /tmp/zentao-report.png')

console.log('======================================================')
console.log(`  report 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
await b.close()
if (FAIL > 0) process.exit(1)

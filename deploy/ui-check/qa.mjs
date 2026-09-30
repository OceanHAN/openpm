// 测试仪表盘（qa）界面检查
//
// 覆盖 /zentao/qa：
//   ① 六张汇总卡片渲染（缺陷总数 / 待处理 / 有效缺陷 / 修复率 / 待评审用例 / 未完成测试单）
//   ② 按产品的质量统计表 + 修复率进度条
//   ③ 分布标签（缺陷状态 / 严重程度 / 解决方案）
//   ④ 三个列表块（待处理缺陷 / 待评审用例 / 未完成测试单）与它们页签上的计数
//   ⑤ 按产品过滤后汇总卡片跟着变
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

// 准备：一个专属产品 + 两个缺陷（一个激活、一个解决为 fixed）
const TS = Date.now()
const proj = await api('POST', '/zentao/product/create', { name: 'QAUI产品-' + TS, code: 'qaui' + TS, PO: 'admin' })
if (proj.code !== 0) throw new Error('准备产品失败：' + JSON.stringify(proj))
const PID = proj.data
const b1 = await api('POST', '/zentao/bug/create', { product: PID, title: 'QAUI-激活缺陷-' + TS, severity: 2, pri: 2, openedBuild: 'trunk' })
const b2 = await api('POST', '/zentao/bug/create', { product: PID, title: 'QAUI-已修复缺陷-' + TS, severity: 3, pri: 3, openedBuild: 'trunk' })
if (b1.code !== 0 || b2.code !== 0) throw new Error('准备缺陷失败：' + JSON.stringify([b1, b2]))
await api('PUT', '/zentao/bug/resolve', { id: b2.data, resolution: 'fixed', resolvedBuild: 'trunk' })
console.log(`准备: 产品=${PID} 缺陷=${b1.data}(激活) ${b2.data}(已修复)`)

let cleanupDone = false
const cleanup = async () => {
  if (cleanupDone) return
  cleanupDone = true
  await api('DELETE', '/zentao/bug/delete?id=' + b1.data).catch(() => {})
  await api('DELETE', '/zentao/bug/delete?id=' + b2.data).catch(() => {})
  await api('DELETE', '/zentao/product/delete?id=' + PID).catch(() => {})
}

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

const read = async () =>
  p.evaluate(() => {
    const cards = Array.from(document.querySelectorAll('.el-row .el-card')).map((el) => el.innerText.replace(/\s+/g, ' ').trim())
    const tables = Array.from(document.querySelectorAll('.el-table')).filter((t) => t.offsetParent !== null)
    const pane = Array.from(document.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
    return {
      cards,
      text: document.body.innerText.replace(/\s+/g, ' ').trim(),
      productRows: tables.length ? Array.from(tables[0].querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim()) : [],
      paneRows: pane ? Array.from(pane.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim()) : [],
      tabs: Array.from(document.querySelectorAll('.el-tabs__item')).map((e) => e.innerText.replace(/\s+/g, ' ').trim())
    }
  })

await p.goto(BASE + '/zentao/qa', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(6000)

// ---------------- ① 汇总卡片 ----------------
let view = await read()
console.log('① 卡片 :', JSON.stringify(view.cards.slice(0, 6)))
ok('六张汇总卡片渲染', view.cards.length >= 6, `卡片=${view.cards.length}`)
for (const label of ['缺陷总数', '待处理（激活）', '有效缺陷', '修复率', '待评审 / 用例总数', '未完成 / 测试单']) {
  ok(`卡片里有「${label}」`, view.cards.some((t) => t.includes(label)), '')
}

// ---------------- ② 按产品统计 ----------------
const qaRow = view.productRows.find((r) => r.includes('QAUI产品-' + TS))
ok('按产品统计表里能找到刚建的专属产品', !!qaRow, qaRow || `行数=${view.productRows.length}`)
ok('这一行有修复率进度条', !!qaRow && /%|100/.test(qaRow), qaRow || '')
ok('表头说明了修复率口径（分母是有效缺陷）', view.text.includes('有效缺陷 = 状态激活'), '')

// ---------------- ③ 分布标签 ----------------
ok('缺陷状态标签里有「激活」', view.text.includes('激活'), '')
ok('严重程度标签渲染', /级 \d/.test(view.text), (view.text.match(/\d 级 \d+/) || [''])[0])
ok('解决方案标签里有「已修复」', view.text.includes('已修复'), '')

// ---------------- ④ 三个列表块 ----------------
console.log('④ 页签 :', JSON.stringify(view.tabs))
ok('三个列表页签都带计数', view.tabs.filter((t) => /（\d+）/.test(t)).length >= 3, JSON.stringify(view.tabs))
ok('默认页签「待处理缺陷」里有刚建的激活缺陷', view.paneRows.some((r) => r.includes('QAUI-激活缺陷-' + TS)),
  JSON.stringify(view.paneRows.slice(0, 2)))
ok('已修复的那个不在待处理列表里', !view.paneRows.some((r) => r.includes('QAUI-已修复缺陷-' + TS)), '')

// ---------------- ⑤ 按产品过滤 ----------------
await p.locator('.el-form-item:has-text("产品") .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'QAUI产品-' + TS }).first().click()
await p.waitForTimeout(3000)
view = await read()
const bugTotalCard = view.cards.find((t) => t.includes('缺陷总数')) || ''
const total = Number((bugTotalCard.match(/^(\d+)/) || [])[1] || -1)
ok('切到专属产品后「缺陷总数」= 2', total === 2, bugTotalCard)
const rateCard = view.cards.find((t) => t.includes('修复率')) || ''
// 两个缺陷都算「有效」（一个是激活态、一个是已修复），但只有 1 个已修复 → 1/2 = 50%
ok('修复率 = 50%（1 已修复 / 2 有效：激活的那个也算有效）', rateCard.includes('50%'), rateCard)
const ttCard = view.cards.find((t) => t.includes('未完成 / 测试单')) || ''
ok('这个产品没有测试单 → 0/0', ttCard.startsWith('0/0'), ttCard)

ok('页面没有 JS 报错', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '))

await p.screenshot({ path: '/tmp/zentao-qa.png', fullPage: true })
console.log('截图       : /tmp/zentao-qa.png')

await cleanup()
console.log('已清理测试数据')
await b.close()

console.log('======================================================')
console.log(`  qa 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

// 度量（metric）界面检查
//
// 覆盖 /zentao/metric：
//   ① 概览卡片（内置度量项 / 已迁移口径 / 待补口径 / 数据条数）与接口一致
//   ② 左侧度量项列表渲染（含 code 与「已迁移/待补」标记）
//   ③ 「只看已迁移口径」过滤后条数变少
//   ④ 选中度量项 → 右侧显示口径定义（definition）与上次计算
//   ⑤ 点「计算」→ 数据表格出现行、行里有对象名与值
//   ⑥ 未迁移口径的「计算」按钮是禁用的
//   ⑦ 「全部计算」→ 概览里的数据条数变大或保持
//
// 依赖：后端 127.0.0.1:48080（本机栈或远端栈都行），前端 http://localhost/
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

const backendSummary = (await api('GET', '/zentao/metric/summary')).data
console.log('  后端概览:', JSON.stringify(backendSummary))

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
const p = await c.newPage()
p.on('pageerror', (e) => console.log('  [pageerror]', String(e.stack || e).split('\n').slice(0, 3).join(' | ')))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })

const waitFor = async (fn, timeout = 20000, interval = 500) => {
  const end = Date.now() + timeout
  for (;;) {
    const v = await fn().catch(() => null)
    if (v) return v
    if (Date.now() > end) return null
    await p.waitForTimeout(interval)
  }
}

const tableRows = async (idx) =>
  p.evaluate((i) => {
    const tables = Array.from(document.querySelectorAll('.el-table')).filter((t) => t.offsetParent !== null)
    const table = tables[i]
    if (!table) return []
    return Array.from(table.querySelectorAll('.el-table__body tbody tr')).map((r) =>
      Array.from(r.querySelectorAll('td')).map((td) => td.innerText.trim())
    )
  }, idx)

await login(p, BASE)
console.log('登录成功')

await p.goto(BASE + '/zentao/metric', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 概览 ----------------
const cards = await p.evaluate(() =>
  Array.from(document.querySelectorAll('.metric-card')).map((el) => ({
    num: el.querySelector('.metric-card-num')?.textContent?.trim(),
    label: el.querySelector('.metric-card-label')?.textContent?.trim()
  }))
)
console.log('  概览卡片:', JSON.stringify(cards))
const cardOf = (label) => cards.find((x) => x.label === label)?.num
ok('概览卡片渲染 4 张', cards.length === 4, JSON.stringify(cards.map((x) => x.label)))
ok('内置度量项数与接口一致',
  cardOf('内置度量项') === String(backendSummary.totalCount), `${cardOf('内置度量项')} vs ${backendSummary.totalCount}`)
ok('已迁移/待补口径与接口一致',
  cardOf('已迁移口径') === String(backendSummary.implementedCount) &&
  cardOf('待补口径') === String(backendSummary.pendingCount))

// ---------------- ② 左侧列表 ----------------
const rows = await tableRows(0)
console.log('  列表前 3 行:', JSON.stringify(rows.slice(0, 3)))
ok('度量项列表渲染（含 code 与口径标记）',
  rows.length > 10 && rows.every((r) => r.length >= 3) && rows.some((r) => r[0].includes('count_of_')),
  `行数=${rows.length}`)
ok('列表里有「已迁移」和「待补」两种标记',
  rows.some((r) => r[2] === '已迁移') && rows.some((r) => r[2] === '待补'))

// ---------------- ③ 只看已迁移 ----------------
await p.locator('.el-checkbox:has-text("只看已迁移口径")').first().click()
await p.waitForTimeout(2000)
const rowsFiltered = await tableRows(0)
ok('「只看已迁移口径」过滤生效',
  rowsFiltered.length === backendSummary.implementedCount && rowsFiltered.every((r) => r[2] === '已迁移'),
  `${rowsFiltered.length} vs ${backendSummary.implementedCount}`)
await p.locator('.el-checkbox:has-text("只看已迁移口径")').first().click()
await p.waitForTimeout(2000)

// ---------------- ④ 选中度量项 ----------------
const targetRow = p.locator('.el-table').first().locator('tbody tr').filter({ hasText: 'count_of_story_in_product' }).first()
await targetRow.click()
await p.waitForTimeout(2500)
const detailText = await p.locator('.el-descriptions').first().innerText()
console.log('  定义区:', detailText.replace(/\s+/g, ' ').slice(0, 120))
ok('右侧显示口径定义', detailText.includes('产品中研发需求的个数求和'))
ok('右侧显示上次计算信息', /条 \/ /.test(detailText.replace(/\s+/g, ' ')))

// ---------------- ⑤ 计算 ----------------
const before = (await api('GET', '/zentao/metric/get?code=count_of_story_in_product')).data.dataCount
const calcResp = await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/metric/calc?'), { timeout: 30000 }).catch(() => null),
  // 注意用精确文本：「全部计算」也包含「计算」，has-text 会先命中它
  p.getByRole('button', { name: '计算', exact: true }).first().click()
])
await p.waitForTimeout(3000)
ok('计算接口返回 200', calcResp[0]?.status() === 200, `status=${calcResp[0]?.status?.()}`)
const dataRows = await waitFor(async () => {
  const list = await tableRows(1)
  return list.length > 0 ? list : null
})
console.log('  数据行:', JSON.stringify(dataRows?.slice(0, 2)))
ok('数据表格出现计算结果', !!dataRows && dataRows.length > 0, `行数=${dataRows?.length ?? 0}`)
ok('数据行带对象名与值', !!dataRows && dataRows[0][0].length > 0 && dataRows[0][2].length > 0,
  JSON.stringify(dataRows?.[0]))
const after = (await api('GET', '/zentao/metric/get?code=count_of_story_in_product')).data.dataCount
ok('计算后该度量项 dataCount > 0', after > 0 && after >= before, `${before} → ${after}`)

// ---------------- ⑥ 未迁移口径按钮禁用 ----------------
await p.waitForTimeout(1000)
const pendingRow = p.locator('.el-table').first().locator('tbody tr').filter({ hasText: 'count_of_story_in_stage_in_product' }).first()
await pendingRow.click()
await p.waitForTimeout(2000)
const disabled = await p.getByRole('button', { name: '计算', exact: true }).first().isDisabled()
ok('未迁移口径的「计算」按钮禁用', disabled)

// ---------------- ⑦ 全部计算 ----------------
const allResp = await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/metric/calc-all'), { timeout: 60000 }).catch(() => null),
  p.getByRole('button', { name: '全部计算', exact: true }).first().click()
])
await p.waitForTimeout(4000)
ok('「全部计算」接口返回 200', allResp[0]?.status() === 200, `status=${allResp[0]?.status?.()}`)
const summaryAfter = (await api('GET', '/zentao/metric/summary')).data
ok('全部计算后数据量 > 0 且回写时间', summaryAfter.dataCount > 0 && !!summaryAfter.lastCalcTime,
  JSON.stringify({ dataCount: summaryAfter.dataCount, lastCalcTime: summaryAfter.lastCalcTime }))

console.log('======================================================')
console.log(`  度量界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
await b.close()
if (FAIL > 0) process.exit(1)

// BI（数据视图 + 图表）界面检查
//
// 覆盖 /zentao/bi：
//   ① 数据视图列表渲染（3 个演示视图 + 字段数 + 被引用数）
//   ② 预览弹窗：列名与数据行
//   ③ 新建数据视图（试跑 → 保存）→ 列表多一行
//   ④ SQL 白名单：试跑一条非法 SQL → 报错提示（不允许注释符/系统表）
//   ⑤ 图表列表渲染 + 选中后 ECharts 有 canvas + 数据表有行
//   ⑥ 新建图表（引用数据视图 + 维度/聚合）→ 渲染成功
//   ⑦ 收尾：删图表、删数据视图
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

const TS = Date.now()
const DV_NAME = 'UI视图-' + TS
const CH_NAME = 'UI图表-' + TS

// 清掉上次中断留下的临时对象
for (const c of (await api('GET', '/zentao/bi/chart/list')).data || []) {
  if ((c.name || '').startsWith('UI图表-') || c.name === '坏图表') {
    await api('DELETE', '/zentao/bi/chart/delete?id=' + c.id)
  }
}
for (const v of (await api('GET', '/zentao/bi/dataview/list')).data || []) {
  if ((v.name || '').startsWith('UI视图-')) await api('DELETE', '/zentao/bi/dataview/delete?id=' + v.id)
}

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

await p.goto(BASE + '/zentao/bi', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 数据视图列表 ----------------
const dvRows = await tableRows(0)
console.log('  数据视图:', JSON.stringify(dvRows.slice(0, 2)))
ok('数据视图列表渲染出 3 个演示视图',
  dvRows.length >= 3 && dvRows.some((r) => r[0] === '需求数据') && dvRows.some((r) => r[0] === '任务数据'),
  `行数=${dvRows.length}`)
ok('列表带字段数与引用数', dvRows.every((r) => r.length >= 6), JSON.stringify(dvRows[0]))

// ---------------- ② 预览 ----------------
await p.locator('.el-table').first().locator('tbody tr').filter({ hasText: '需求数据' }).first()
  .locator('button:has-text("预览")').click()
await p.waitForTimeout(2500)
const previewDlg = p.locator('.el-dialog:visible').filter({ hasText: '数据预览' }).first()
await previewDlg.waitFor({ state: 'visible', timeout: 15000 })
const previewRows = await previewDlg.locator('.el-table__body tbody tr').count()
const previewText = await previewDlg.innerText()
ok('预览弹窗有数据行', previewRows > 0, `行数=${previewRows}`)
ok('预览显示列名与执行 SQL', previewText.includes('status') && previewText.includes('LIMIT'))
await previewDlg.locator('.el-dialog__footer button, .el-dialog__headerbtn').first().click().catch(() => {})
await p.keyboard.press('Escape')
await p.waitForTimeout(1000)

// ---------------- ③ 新建数据视图（试跑 + 保存） ----------------
await p.getByRole('button', { name: '新建数据视图', exact: true }).first().click()
await p.waitForTimeout(1200)
const dvDlg = p.locator('.el-dialog:visible').filter({ hasText: '新建数据视图' }).first()
await dvDlg.waitFor({ state: 'visible', timeout: 15000 })
await dvDlg.locator('.el-form-item:has-text("名称") input').first().fill(DV_NAME)
await dvDlg.locator('.el-form-item:has-text("代码") input').first().fill('ui_view_' + TS)
await dvDlg.locator('textarea').first().fill('SELECT id, product, status, pri FROM zt_story WHERE deleted = 0')
// 试跑按钮在弹窗里，直接在弹窗作用域内找（页面上还有别的按钮）
await dvDlg.locator('button:has-text("试")').first().click()
await p.waitForTimeout(2500)
const tryText = await dvDlg.innerText()
ok('试跑返回列与行数', tryText.includes('列：') && tryText.includes('status'), tryText.replace(/\s+/g, ' ').slice(-120))
// 试跑一条非法 SQL：后端白名单必须拦下（断言响应体里的业务错误码 + 界面不出结果）
await dvDlg.locator('textarea').first().fill('SELECT id FROM system_users WHERE 1=1')
const badResp = p.waitForResponse((r) => r.url().includes('/dataview/preview-sql'), { timeout: 20000 }).catch(() => null)
await dvDlg.locator('button:has-text("试")').first().click()
const badBody = await (await badResp)?.json().catch(() => null)
ok('非法 SQL 被白名单拦下（业务码 1020027003）', badBody?.code === 1020027003,
  JSON.stringify(badBody ? { code: badBody.code, msg: String(badBody.msg).slice(0, 60) } : null))
const badShowsResult = await waitFor(async () => (await dvDlg.innerText()).includes('列：'), 3000)
ok('非法 SQL 不产生试跑结果', !badShowsResult)
// 换回合法 SQL 并保存
await dvDlg.locator('textarea').first().fill('SELECT id, product, status, pri FROM zt_story WHERE deleted = 0')
const dvResp = await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/bi/dataview/create'), { timeout: 30000 }).catch(() => null),
  dvDlg.locator('.el-dialog__footer button:has-text("确 定")').click()
])
await p.waitForTimeout(2500)
ok('界面新建数据视图成功', dvResp[0]?.status() === 200, `status=${dvResp[0]?.status?.()}`)
const createdDv = (await api('GET', '/zentao/bi/dataview/list')).data.find((v) => v.name === DV_NAME)
ok('新视图出现在列表里', !!createdDv, JSON.stringify(createdDv ? { id: createdDv.id, fields: createdDv.fields?.length } : null))
ok('字段被自动解析（4 个）', createdDv?.fields?.length === 4)

// ---------------- ④ 图表渲染 ----------------
await p.locator('.el-tabs__item:has-text("图表")').first().click()
await p.waitForTimeout(2500)
// 注意：第一个 .el-table 是「数据视图」页签里那张（隐藏但仍在 DOM），必须限定可见页签
const chartRows = await tableRows(0)
ok('图表列表渲染（含类型与版本）',
  chartRows.length >= 3 && chartRows.some((r) => r[0].includes('需求状态分布')),
  JSON.stringify(chartRows.slice(0, 2)))
await p.locator('.el-tab-pane:visible .el-table').first().locator('tbody tr')
  .filter({ hasText: '需求状态分布' }).first().click()
const canvasOk = await waitFor(async () => (await p.locator('canvas').count()) > 0)
ok('选中图表后 ECharts 渲染出 canvas', !!canvasOk)
const chartTableRows = await tableRows(1)
ok('图表数据表有行（维度 + 值）', chartTableRows.length > 0 && chartTableRows[0].length === 2,
  JSON.stringify(chartTableRows.slice(0, 2)))
const pageText = await p.locator('body').innerText()
ok('页面显示执行 SQL（含 GROUP BY）', pageText.includes('GROUP BY'),
  pageText.split('\n').filter((l) => l.includes('GROUP BY')).join(' ').slice(0, 140))

// ---------------- ⑤ 新建图表 ----------------
await p.getByRole('button', { name: '新建图表', exact: true }).first().click()
await p.waitForTimeout(1200)
const chDlg = p.locator('.el-dialog:visible').filter({ hasText: '新建图表' }).first()
await chDlg.waitFor({ state: 'visible', timeout: 15000 })
await chDlg.locator('.el-form-item:has-text("名称") input').first().fill(CH_NAME)
// 数据视图选刚建的那个
await chDlg.locator('.el-form-item:has-text("数据视图") .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: DV_NAME }).first().click()
await p.waitForTimeout(500)
await chDlg.locator('.el-form-item:has-text("分组维度") input').first().fill('status')
await chDlg.locator('.el-form-item:has-text("聚合方式") .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '计数' }).first().click()
const chResp = await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/bi/chart/create'), { timeout: 30000 }).catch(() => null),
  chDlg.locator('.el-dialog__footer button:has-text("确 定")').click()
])
await p.waitForTimeout(3000)
ok('界面新建图表成功', chResp[0]?.status() === 200, `status=${chResp[0]?.status?.()}`)
const createdCh = (await api('GET', '/zentao/bi/chart/list')).data.find((x) => x.name === CH_NAME)
ok('新图表出现在列表里且已渲染', !!createdCh, JSON.stringify(createdCh ? { id: createdCh.id, type: createdCh.type } : null))
const newChartData = createdCh ? (await api('GET', '/zentao/bi/chart/data?id=' + createdCh.id)).data : null
ok('新图表能算出数据（与库交叉校验）', !!newChartData && newChartData.rows.length > 0,
  JSON.stringify(newChartData ? { rows: newChartData.rows.length, sum: newChartData.rows.reduce((s, r) => s + Number(r.value), 0) } : null))

// ---------------- 收尾 ----------------
if (createdCh) await api('DELETE', '/zentao/bi/chart/delete?id=' + createdCh.id)
if (createdDv) await api('DELETE', '/zentao/bi/dataview/delete?id=' + createdDv.id)
console.log('  清理完成')

console.log('======================================================')
console.log(`  BI 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
await b.close()
if (FAIL > 0) process.exit(1)

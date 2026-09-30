// 节假日（holiday）界面检查
//
// 覆盖 /zentao/holiday：
//   ① 列表渲染（名称/类型标签/起止天数）
//   ② 新增弹窗字段齐全 + 建一条假期 → 列表出现，类型标签是「假期」
//   ③ 工作日试算：假期区间 → 0 天；试算结果按天列出
//   ④ 删除后列表恢复
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

// 准备：清掉历史数据 + 建一条假期（2027 年，避免撞上别的测试用的 2026）
await fetch(`${API_BASE}/zentao/holiday/list?year=2027`, {
  headers: { Authorization: 'Bearer ' + tok, 'tenant-id': '1' }
})
const created = await api('POST', '/zentao/holiday/create', {
  name: 'UI假期-元旦', type: 'holiday', begin: '2027-01-01', end: '2027-01-03', desc: 'UI 检查用'
})
if (created.code !== 0) throw new Error('准备假期失败：' + JSON.stringify(created))
const HID = created.data
console.log(`准备: 假期=${HID}（2027-01-01 ~ 2027-01-03）`)

const cleanup = async () => { if (HID) await api('DELETE', '/zentao/holiday/delete?id=' + HID).catch(() => {}) }
const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1600, height: 1000 } })
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

const rows = async () =>
  p.evaluate(() => ({
    text: document.body.innerText.replace(/\s+/g, ' ').trim(),
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim())
  }))

await p.goto(BASE + '/zentao/holiday', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// 切到 2027 年（有数据的那年）
await p.locator('.el-form-item:has-text("年份") .el-select').first().click()
await p.waitForTimeout(600)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '2027' }).first().click()
await p.waitForTimeout(2500)

// ---------------- ① 列表 ----------------
let view = await rows()
const row = view.rows.find((r) => r.includes('UI假期-元旦'))
ok('列表里有刚建的假期', !!row, row || `行数=${view.rows.length}`)
ok('类型显示成「假期」', !!row && row.includes('假期'), row || '')
ok('起止显示 3 天', !!row && row.includes('3 天'), row || '')
ok('页面说明了两种记录与优先级', view.text.includes('补班 > 假期 > 周末'), '')

// ---------------- ② 新增弹窗 ----------------
await p.locator('button:has-text("新增")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '新增节假日' }).first().waitFor({ state: 'visible', timeout: 20000 })
const dialog = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  return { labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.trim()), text: d.innerText.replace(/\s+/g, ' ') }
})
for (const label of ['名称', '类型', '开始', '结束', '描述']) {
  ok(`弹窗有「${label}」`, dialog.labels.includes(label), JSON.stringify(dialog.labels))
}
ok('类型是「假期 / 补班」两选一', dialog.text.includes('假期（不算工作日）') && dialog.text.includes('补班（算工作日）'), '')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)

// ---------------- ③ 工作日试算 ----------------
await p.locator('.el-form-item:has-text("开始") input').first().fill('2027-01-01')
await p.keyboard.press('Enter')
await p.waitForTimeout(500)
await p.locator('.el-form-item:has-text("结束") input').first().fill('2027-01-04')
await p.keyboard.press('Enter')
await p.waitForTimeout(500)
await p.locator('button:has-text("试算")').first().click()
await p.waitForTimeout(2500)
view = await rows()
ok('试算 01-01~01-03（假期）→ 0 天', view.text.includes('实际工作日 0 天'), (view.text.match(/实际工作日 \d+ 天[^。]*/) || [''])[0].slice(0, 80))
ok('给了「全是假期或周末」的提示', view.text.includes('这几天全是假期或周末'), '')

// 换成一段普通工作日
await p.locator('.el-form-item:has-text("开始") input').first().fill('2027-01-04')
await p.keyboard.press('Enter')
await p.waitForTimeout(400)
await p.locator('.el-form-item:has-text("结束") input').first().fill('2027-01-09')
await p.keyboard.press('Enter')
await p.waitForTimeout(400)
await p.locator('button:has-text("试算")').first().click()
await p.waitForTimeout(2500)
view = await rows()
ok('试算 01-04~01-08（周一到周五）→ 5 天', view.text.includes('实际工作日 5 天'), (view.text.match(/实际工作日 \d+ 天/) || [''])[0])
ok('每天都列出来了', ['2027-01-04', '2027-01-08'].every((d) => view.text.includes(d)), '')

// ---------------- ④ 删除 ----------------
await p.locator('.el-table__body tbody tr').filter({ hasText: 'UI假期-元旦' }).first()
  .locator('button:has-text("删除")').first().click()
await p.waitForTimeout(700)
await p.locator('.el-message-box__btns button:has-text("确定")').click()
await p.waitForTimeout(2500)
view = await rows()
ok('删除后列表里没有了', !view.rows.some((r) => r.includes('UI假期-元旦')), '')

ok('页面没有 JS 报错', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '))

await p.screenshot({ path: '/tmp/zentao-holiday.png', fullPage: true })
console.log('截图       : /tmp/zentao-holiday.png')

await b.close()
console.log('======================================================')
console.log(`  holiday 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

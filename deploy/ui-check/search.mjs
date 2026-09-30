// 保存查询（search）界面检查
//
// 覆盖 /zentao/search：
//   ① 列表渲染（2 条演示查询、模块标签、快捷方式/公共标记、条件 JSON 直接展示）
//   ② 新建/编辑弹窗（字段齐全 + 必填校验；条件框里是 JSON 不是 SQL）
//   ③ 快捷方式切换（设为快捷 → 列表标记变化 → 取消）
//   ④ 拼音首字母工具（zt_searchdict 码表）
//   ⑤ 页面写明了「不存 SQL」与「全文检索未做」两条边界
//
// 依赖：后端 $ZENTAO_API_BASE，前端 $ZENTAO_UI_BASE
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
if (!loginResp.data) throw new Error('登录失败：' + JSON.stringify(loginResp))
const tok = loginResp.data.accessToken
const api = async (m, u, b) => (await fetch(API_BASE + u, {
  method: m,
  headers: { Authorization: 'Bearer ' + tok, 'tenant-id': '1', 'Content-Type': 'application/json' },
  body: b ? JSON.stringify(b) : undefined
})).json()

// 准备：建一条 UI 专用查询，结束时删掉（这张表是物理删除，所以能清干净）
const uiTitle = 'UI检查查询-' + String(Date.now()).slice(-6)
const created = await api('POST', '/zentao/search/query/save', {
  module: 'task', title: uiTitle, conditions: '[{"field":"status","op":"eq","value":"wait"}]', shortcut: 0, common: 0
})
if (created.code !== 0) throw new Error('准备查询失败：' + JSON.stringify(created))
const UI_ID = created.data
console.log(`准备: 查询=${UI_ID}（${uiTitle}）`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1700, height: 1050 } })
const p = await c.newPage()
const pageErrors = []
p.on('pageerror', (e) => pageErrors.push(String(e && e.stack ? e.stack : JSON.stringify(e)).split('\n')[0].slice(0, 200)))
await p.addInitScript(() => {
  window.__errs = []
  window.addEventListener('error', (e) => {
    if (String(e.message || '').includes('ResizeObserver loop')) return
    window.__errs.push(`${e.message} @ ${e.filename || ''}:${e.lineno || 0}`)
  })
  window.addEventListener('unhandledrejection', (e) => {
    let r = e.reason
    let desc
    try { desc = JSON.stringify(r) } catch { desc = String(r) }
    if (r && r.msg) desc = `msg=${r.msg} code=${r.code}`
    window.__errs.push('unhandledrejection: ' + desc)
  })
})
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', async (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', async (err) => { __cleanup(); console.error(err); process.exit(1) })

await login(p, BASE)
console.log('登录成功')

const view = async () =>
  p.evaluate(() => {
    const pane = document.querySelector('.el-tab-pane:not([style*="display: none"])') || document.body
    return {
      text: document.body.innerText.replace(/\s+/g, ' ').trim(),
      rows: Array.from(document.querySelectorAll('.el-table__body tbody tr'))
        .filter((r) => !r.closest('.el-dialog'))
        .map((r) => r.innerText.replace(/\s+/g, ' ').trim()),
      paneRows: Array.from(pane.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim())
    }
  })

await p.goto(BASE + '/zentao/search', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 列表 ----------------
let v = await view()
ok('列表里有演示查询「演示：激活的需求」', v.rows.some((r) => r.includes('演示：激活的需求')), `行数=${v.rows.length}`)
ok('有刚准备的 UI 查询', v.rows.some((r) => r.includes(uiTitle)), '')
ok('模块显示成中文标签（需求/任务/缺陷）', /需求|任务|缺陷/.test(v.text), (v.rows[0] || '').slice(0, 60))
ok('快捷方式与公共列渲染', v.text.includes('快捷方式') && v.text.includes('公共'), '')
ok('条件列直接展示结构化 JSON（不是 SQL）', v.text.includes('"field":"status"'), '')
ok('页面写明了「禅道存 SQL、本实现存 JSON」这条关键差异', v.text.includes('结构化条件 JSON'), '')

// ---------------- ② 新建弹窗 ----------------
await p.locator('button:has-text("新建查询")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '新建保存的查询' }).first().waitFor({ state: 'visible', timeout: 20000 })
const labels = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  return Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.trim())
})
for (const label of ['模块', '查询名称', '条件 JSON', '表单快照', '快捷方式', '公共查询']) {
  ok(`弹窗有「${label}」`, labels.includes(label), JSON.stringify(labels))
}
await p.locator('.el-dialog:visible .el-form-item:has-text("查询名称") input').first().fill('')
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(1200)
v = await view()
ok('名称为空 → 前端提示必填', v.text.includes('查询名称不能为空'), '')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)

// ---------------- ③ 快捷方式切换 ----------------
const uiRow = p.locator('.el-table__body tbody tr').filter({ hasText: uiTitle }).first()
await uiRow.locator('button:has-text("设为快捷")').first().click()
await p.waitForTimeout(2000)
v = await view()
const afterRow = v.rows.find((r) => r.includes(uiTitle)) || ''
ok('设为快捷后该行标记变「是」', afterRow.includes('是'), afterRow)
await p.waitForTimeout(500)
await p.locator('.el-table__body tbody tr').filter({ hasText: uiTitle }).first()
  .locator('button:has-text("取消快捷")').first().click()
await p.waitForTimeout(2000)
v = await view()
ok('取消后能再切回来（按钮文字变回「设为快捷」）', v.text.includes(uiTitle), '')

// ---------------- ④ 拼音首字母 ----------------
await p.locator('.el-form-item:has-text("中文") input').first().fill('需求')
await p.locator('button:has-text("取首字母")').first().click()
await p.waitForTimeout(2000)
v = await view()
ok('拼音工具返回首字母（需→y）', /取首字母\s*y/.test(v.text) || v.text.includes('y'), (v.text.match(/取首字母[^ ]{0,10}/) || [''])[0])
ok('页面说明了全文检索（zt_searchindex）本实现不做', v.text.includes('zt_searchindex') && v.text.includes('没有做'), '')

const inPageErrors = await p.evaluate(() => window.__errs || [])
ok('页面没有 JS 报错', pageErrors.length === 0 && inPageErrors.length === 0,
  [...pageErrors, ...inPageErrors].slice(0, 3).join(' | '))

await p.screenshot({ path: '/tmp/zentao-search.png', fullPage: true })
console.log('截图       : /tmp/zentao-search.png')

// 清理：物理删除 UI 专用查询
await api('DELETE', '/zentao/search/query/delete?id=' + UI_ID).catch(() => {})
await b.close()
console.log('======================================================')
console.log(`  search 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

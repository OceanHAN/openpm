import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
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
const prod = await api('POST', '/zentao/product/create', { name: 'UIRelProd-' + TS, code: 'UR' + TS, type: 'normal', PO: 'admin' })
const pid = prod.data
const proj = await api('POST', '/zentao/project/create', { name: 'UIRelProj-' + TS, model: 'scrum', pri: 3, PM: 'admin', team: 'admin', multiple: 1 })
const exec = await api('POST', '/zentao/execution/create', { project: proj.data, name: 'UIRelExec-' + TS, type: 'sprint', pri: 3 })
const story = await api('POST', '/zentao/story/create', { product: pid, title: 'UI发布需求-' + TS, pri: 3, category: 'feature' })
const bug = await api('POST', '/zentao/bug/create', { product: pid, title: 'UI发布缺陷-' + TS, steps: '步骤', severity: 3, pri: 3, type: 'codeerror' })
const build = await api('POST', '/zentao/build/create', { product: pid, project: proj.data, execution: exec.data, name: 'UI-rel-build-' + TS, date: '2026-01-10', builder: 'admin' })
const rel = await api('POST', '/zentao/release/create', { product: pid, name: 'UI-V1.0-' + TS, date: '2026-02-28', builds: [build.data] })
console.log('准备: 产品=' + pid + ' 需求=' + story.data + ' 缺陷=' + bug.data + ' 构建=' + build.data + ' 发布=' + rel.data + ' code=' + rel.code)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1680, height: 1050 } })
const p = await c.newPage()

// 断言失败时脚本会直接抛异常退出 —— 如果不在退出前关掉浏览器，
// 每个失败的 Playwright 进程都会留下一个 headless Chromium 挂在那里吃 CPU/内存
// （实测跑错几次就攒了 30 多个，机器负载升高后连登录都超时，形成连锁失败）。
const __cleanup = () => { try { b.close() } catch { /* 已经关掉了 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })
p.on('pageerror', (e) => console.log('  [pageerror]', String(e.stack || e).split('\n').slice(0, 3).join(' | ')))
await p.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(1500)
await p.fill('input[placeholder="请输入用户名"]', 'admin')
await p.fill('input[placeholder="请输入密码"]', 'admin123')
await p.locator('button:has-text("登录")').first().click()
await p.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 25000 })
console.log('登录成功')

await p.goto(BASE + '/zentao/release', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)
await p.click('.el-form-item:has-text("所属产品") .el-select')
await p.waitForTimeout(500)
await p.locator('.el-form-item:has-text("所属产品") .el-select input').first().fill('UIRelProd').catch(() => {})
await p.waitForTimeout(900)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'UIRelProd' }).first().click()
await p.waitForTimeout(3000)
const info = await p.evaluate(() => {
  const txt = document.body.innerText
  return {
    url: location.pathname,
    has404: txt.includes('404') || txt.includes('找不到'),
    headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 130)),
    total: (txt.match(/共 (\d+) 条/) || [])[1] || ''
  }
})
console.log('页面URL  :', info.url, '| 404:', info.has404, '| 共:', info.total)
console.log('表头     :', info.headers.join(' | '))
info.rows.forEach((r) => console.log('   行:', r))

// 新建弹窗
await p.click('button:has-text("新建发布")')
await p.waitForTimeout(1500)
const dlg = await p.evaluate(() => {
  const d = document.querySelector('.el-dialog')
  if (!d || d.offsetParent === null) return null
  return {
    title: (d.querySelector('.el-dialog__title') || {}).innerText,
    labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.replace(/\s+/g, ''))
  }
})
console.log('新建弹窗 :', JSON.stringify(dlg))
await p.keyboard.press('Escape')
await p.waitForTimeout(500)
await p.click('.el-dialog__footer button:has-text("取 消")').catch(() => {})
await p.waitForTimeout(800)

// 发布清单抽屉（三个 Tab）
const idx = info.rows.findIndex((r) => r.includes('UI-V1.0-' + TS))
const rowNo = idx >= 0 ? idx + 1 : 1
await p.click(`.el-table__body tbody tr:nth-child(${rowNo}) button:has-text("更多")`)
await p.waitForTimeout(700)
await p.locator('.el-dropdown-menu__item:visible').filter({ hasText: '发布清单' }).first().click()
await p.waitForTimeout(3000)
const drawer = await p.evaluate(() => {
  const d = document.querySelector('.el-drawer')
  if (!d || d.offsetParent === null) return null
  return {
    title: (d.querySelector('.el-drawer__title') || {}).innerText,
    tabs: Array.from(d.querySelectorAll('.el-tabs__item')).map((e) => e.innerText.replace(/\s+/g, ' ')),
    text: d.innerText.replace(/\s+/g, ' ').slice(0, 240)
  }
})
console.log('发布清单 :', JSON.stringify(drawer))
await p.screenshot({ path: '/tmp/zentao-release.png', fullPage: true })
console.log('截图     : /tmp/zentao-release.png')

// 清理
await api('DELETE', '/zentao/release/delete?id=' + rel.data)
await api('DELETE', '/zentao/build/delete?id=' + build.data)
await api('DELETE', '/zentao/story/delete?id=' + story.data)
await api('DELETE', '/zentao/bug/delete?id=' + bug.data)
await api('DELETE', '/zentao/execution/delete?id=' + exec.data)
await api('DELETE', '/zentao/project/delete?id=' + proj.data)
await api('DELETE', '/zentao/product/delete?id=' + pid)
console.log('已清理测试数据')
await b.close()

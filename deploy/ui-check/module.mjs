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
const prod = await api('POST', '/zentao/product/create', { name: 'UIModProd-' + TS, code: 'UIM' + TS, type: 'normal', PO: 'admin' })
const pid = prod.data
const m1 = await api('POST', '/zentao/module/create', { root: pid, type: 'story', name: 'UI用户中心-' + TS })
const m2 = await api('POST', '/zentao/module/create', { root: pid, type: 'story', name: 'UI订单中心-' + TS })
const m3 = await api('POST', '/zentao/module/create', { root: pid, type: 'story', name: 'UI登录注册-' + TS, parent: m1.data })
console.log('准备: 产品=' + pid + ' 模块=' + m1.data + ',' + m2.data + ',' + m3.data + ' code=' + m1.code)

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
p.on('pageerror', (e) => console.log('  [pageerror]', String(e).slice(0, 200)))
await p.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(1500)
await p.fill('input[placeholder="请输入用户名"]', 'admin')
await p.fill('input[placeholder="请输入密码"]', 'admin123')
await p.locator('button:has-text("登录")').first().click()
await p.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 25000 })
console.log('登录成功')

await p.goto(BASE + '/zentao/module', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(4000)
// 选产品
await p.click('.el-form-item:has-text("所属产品") .el-select')
await p.waitForTimeout(600)
await p.fill('.el-form-item:has-text("所属产品") .el-select input', 'UIModProd')
await p.waitForTimeout(1200)
await p.keyboard.press('ArrowDown')
await p.waitForTimeout(200)
await p.keyboard.press('Enter')
await p.waitForTimeout(2500)

const info = await p.evaluate((name) => {
  const txt = document.body.innerText
  return {
    url: location.pathname,
    has404: txt.includes('404') || txt.includes('找不到'),
    headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 80)),
    hasM1: txt.includes(name)
  }
}, 'UI用户中心-' + TS)
console.log('页面URL :', info.url, '| 404:', info.has404)
console.log('表头    :', info.headers.join(' | '))
console.log('含一级模块:', info.hasM1)
console.log('行(含展开的子模块):')
info.rows.forEach((r) => console.log('   ', r))

// 新建子模块弹窗：选类型 story + 产品后，上级模块应有树可选
await p.click('button:has-text("新建一级模块")')
await p.waitForTimeout(1500)
const dlg1 = await p.evaluate(() => {
  const d = document.querySelector('.el-dialog')
  if (!d || d.offsetParent === null) return null
  return {
    title: (d.querySelector('.el-dialog__title') || {}).innerText,
    labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.replace(/\s+/g, ''))
  }
})
console.log('新建弹窗:', JSON.stringify(dlg1))
await p.screenshot({ path: '/tmp/zentao-module.png', fullPage: true })
console.log('截图    : /tmp/zentao-module.png')

// 行内「编辑」应能打开并回填
await p.click('.el-dialog__footer button:has-text("取 消")')
await p.waitForTimeout(800)
const idx = info.rows.findIndex((r) => r.includes('UI订单中心'))
if (idx >= 0) {
  await p.click(`.el-table__body tbody tr:nth-child(${idx + 1}) button:has-text("编辑")`)
  await p.waitForTimeout(2500)
  const edit = await p.evaluate(() => {
    const d = document.querySelector('.el-dialog')
    if (!d || d.offsetParent === null) return null
    const inputs = Array.from(d.querySelectorAll('input')).map((i) => i.value).filter(Boolean)
    return { title: (d.querySelector('.el-dialog__title') || {}).innerText, values: inputs.slice(0, 6) }
  })
  console.log('编辑弹窗:', JSON.stringify(edit))
}

// 清理
await api('DELETE', '/zentao/module/delete?id=' + m1.data)
for (const id of [m2.data, m3.data]) await api('DELETE', '/zentao/module/delete?id=' + id)
await api('DELETE', '/zentao/product/delete?id=' + pid)
console.log('已清理测试数据')
await b.close()

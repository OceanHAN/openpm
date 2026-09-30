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

// 准备：一个 platform 产品 + 两条分支
const TS = Date.now()
const prod = await api('POST', '/zentao/product/create', { name: 'UIBrProj-' + TS, code: 'UIB' + TS, type: 'platform', PO: 'admin' })
const pid = prod.data
const b1 = await api('POST', '/zentao/branch/create', { product: pid, name: 'UI政务版-' + TS, desc: 'UI 测试分支' })
const b2 = await api('POST', '/zentao/branch/create', { product: pid, name: 'UI企业版-' + TS })
console.log('准备: 产品=' + pid + ' 分支=' + b1.data + ',' + b2.data + ' (code=' + prod.code + '/' + b1.code + ')')

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

await p.goto(BASE + '/zentao/branch', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(4000)
// 选产品以触发主干行
await p.click('.el-form-item:has-text("所属产品") .el-select')
await p.waitForTimeout(600)
await p.fill('.el-form-item:has-text("所属产品") .el-select input', 'UIBrProj')
await p.waitForTimeout(1200)
await p.keyboard.press('ArrowDown')
await p.waitForTimeout(200)
await p.keyboard.press('Enter')
await p.waitForTimeout(2500)

const info = await p.evaluate(() => {
  const txt = document.body.innerText
  return {
    url: location.pathname,
    has404: txt.includes('404') || txt.includes('找不到'),
    headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 90)),
    total: (txt.match(/共 (\d+) 条/) || [])[1] || ''
  }
})
console.log('页面URL  :', info.url, '| 404:', info.has404, '| 共:', info.total)
console.log('表头     :', info.headers.join(' | '))
console.log('行       :')
info.rows.forEach((r) => console.log('   ', r))

// 设为默认（第一行是主干，取第二条业务分支）
const idx = info.rows.findIndex((r) => r.includes('UI政务版'))
if (idx >= 0) {
  await p.click(`.el-table__body tbody tr:nth-child(${idx + 1}) button:has-text("设为默认")`)
  await p.waitForTimeout(700)
  await p.click('.el-message-box__btns button:has-text("确定")')
  await p.waitForTimeout(2500)
  const after = await p.evaluate(() => Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 90)))
  console.log('设为默认后:')
  after.forEach((r) => console.log('   ', r))
}

// 新增弹窗
await p.click('button:has-text("新建分支/平台")')
await p.waitForTimeout(1500)
const dlg = await p.evaluate(() => {
  const d = document.querySelector('.el-dialog')
  if (!d || d.offsetParent === null) return null
  return {
    title: (d.querySelector('.el-dialog__title') || {}).innerText,
    labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.replace(/\s+/g, '')),
    tips: (d.querySelector('.el-alert__title') || {}).innerText || ''
  }
})
console.log('弹窗     :', JSON.stringify(dlg))

// 选一个 platform 产品后，文案应该从「分支名称」变成「平台名称」
await p.click('.el-dialog .el-form-item:has-text("所属产品") .el-select')
await p.waitForTimeout(600)
await p.fill('.el-dialog .el-form-item:has-text("所属产品") .el-select input', 'UIBrProj')
await p.waitForTimeout(1200)
await p.keyboard.press('ArrowDown')
await p.waitForTimeout(200)
await p.keyboard.press('Enter')
await p.waitForTimeout(1000)
const labels2 = await p.evaluate(() =>
  Array.from(document.querySelectorAll('.el-dialog .el-form-item__label')).map((e) => e.innerText.replace(/\s+/g, ''))
)
console.log('选产品后标签:', JSON.stringify(labels2))
await p.screenshot({ path: '/tmp/zentao-branch.png', fullPage: true })
console.log('截图     : /tmp/zentao-branch.png')

// 清理
await api('DELETE', '/zentao/branch/delete-list?ids=' + b1.data + ',' + b2.data)
const left = await api('GET', '/zentao/branch/list-by-product?product=' + pid)
for (const r of left.data || []) if (r.id) await api('DELETE', '/zentao/branch/delete?id=' + r.id)
await api('DELETE', '/zentao/product/delete?id=' + pid)
console.log('已清理测试数据')
await b.close()

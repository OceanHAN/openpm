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
const prod = await api('POST', '/zentao/product/create', { name: 'UIFilterProd-' + TS, code: 'UIF' + TS, type: 'platform', PO: 'admin' })
const pid = prod.data
const br = await api('POST', '/zentao/branch/create', { product: pid, name: 'UI分支-' + TS })
const mp = await api('POST', '/zentao/module/create', { root: pid, type: 'story', name: 'UI父模块-' + TS })
const mc = await api('POST', '/zentao/module/create', { root: pid, type: 'story', name: 'UI子模块-' + TS, parent: mp.data })
const s1 = await api('POST', '/zentao/story/create', { product: pid, title: 'UI主干需求-' + TS, pri: 3, category: 'feature', module: mc.data })
const s2 = await api('POST', '/zentao/story/create', { product: pid, branch: br.data, title: 'UI分支需求-' + TS, pri: 3, category: 'feature' })
console.log('准备: 产品=' + pid + ' 分支=' + br.data + ' 父模块=' + mp.data + ' 子模块=' + mc.data + ' 需求=' + s1.data + ',' + s2.data)

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

const read = async () =>
  p.evaluate(() => ({
    headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: document.querySelectorAll('.el-table__body tbody tr').length,
    total: (document.body.innerText.match(/共 (\d+) 条/) || [])[1] || '',
    row0: (document.querySelector('.el-table__body tbody tr') || {}).innerText?.replace(/\s+/g, ' ').slice(0, 80) || ''
  }))

// 选择 el-select 选项：点开 → 输入过滤词 → 点中可见下拉项
// （不用键盘方向键：Element Plus 的 navigateOptions 连续 ArrowDown 时会栈溢出）
const pickSelect = async (label, text) => {
  await p.click(`.el-form-item:has-text("${label}") .el-select`)
  await p.waitForTimeout(500)
  await p
    .locator(`.el-form-item:has-text("${label}") .el-select input`)
    .first()
    .fill(text)
    .catch(() => {})
  await p.waitForTimeout(900)
  const item = p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: text })
  await item.first().waitFor({ state: 'visible', timeout: 20000 })
  await item.first().click()
  await p.waitForTimeout(2500)
}

await p.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(1500)
await p.fill('input[placeholder="请输入用户名"]', 'admin')
await p.fill('input[placeholder="请输入密码"]', 'admin123')
await p.locator('button:has-text("登录")').first().click()
await p.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 25000 })
console.log('登录成功')

await p.goto(BASE + '/zentao/story', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(4000)

await pickSelect('所属产品', 'UIFilterProd')
console.log('① 选产品      :', JSON.stringify(await read()))

await pickSelect('分支/平台', 'UI分支')
console.log('② 选分支      :', JSON.stringify(await read()))

await pickSelect('分支/平台', '主干')
console.log('③ 回到主干    :', JSON.stringify(await read()))

await p.click('.el-form-item:has-text("所属模块") .el-select')
await p.waitForTimeout(1200)
// el-tree-select 的节点文本在 .el-tree-node__content 里（不是 el-tree-node__label）
const opts = await p.evaluate(() =>
  Array.from(document.querySelectorAll('.el-tree-node__content')).map((e) => e.innerText.trim())
)
console.log('   模块树选项 :', JSON.stringify(opts))
const parent = p.locator('.el-tree-node__content').filter({ hasText: 'UI父模块' }).first()
await parent.waitFor({ state: 'visible', timeout: 15000 })
await parent.click()
await p.waitForTimeout(2500)
console.log('④ 按父模块过滤:', JSON.stringify(await read()))

await p.screenshot({ path: '/tmp/zentao-story-filter.png', fullPage: true })
console.log('截图         : /tmp/zentao-story-filter.png')

for (const id of [s1.data, s2.data]) await api('DELETE', '/zentao/story/delete?id=' + id)
await api('DELETE', '/zentao/module/delete?id=' + mp.data)
await api('DELETE', '/zentao/branch/delete?id=' + br.data)
await api('DELETE', '/zentao/product/delete?id=' + pid)
console.log('已清理测试数据')
await b.close()

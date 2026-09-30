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

// 准备：一个普通产品 + 计划 + 挂在该计划下的需求
const TS = Date.now()
const prod = await api('POST', '/zentao/product/create', { name: 'UIPlanProd-' + TS, code: 'UIP' + TS, type: 'normal', PO: 'admin' })
const pid = prod.data
const plan = await api('POST', '/zentao/plan/create', { product: pid, title: 'UI计划-' + TS, begin: '2026-01-01', end: '2026-06-30' })
const story = await api('POST', '/zentao/story/create', { product: pid, title: 'UI计划需求-' + TS, pri: 3, category: 'feature', plan: String(plan.data) })
console.log('准备: 产品=' + pid + ' 计划=' + plan.data + ' 需求=' + story.data + ' code=' + plan.code + '/' + story.code)

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

// ---- 计划列表页 ----
await p.goto(BASE + '/zentao/plan', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(6000)
const info = await p.evaluate(() => {
  const txt = document.body.innerText
  return {
    url: location.pathname,
    has404: txt.includes('404') || txt.includes('找不到'),
    headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 110)),
    total: (txt.match(/共 (\d+) 条/) || [])[1] || ''
  }
})
console.log('页面URL  :', info.url, '| 404:', info.has404, '| 共:', info.total)
console.log('表头     :', info.headers.join(' | '))
console.log('行:')
info.rows.forEach((r) => console.log('   ', r))

// ---- 新建计划弹窗 ----
await p.click('button:has-text("新建计划")')
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
console.log('新建弹窗 :', JSON.stringify(dlg))

// 切到「待定」后日期选择器应该消失
await p.click('.el-dialog .el-switch')
await p.waitForTimeout(800)
const afterFuture = await p.evaluate(() => {
  const d = document.querySelector('.el-dialog')
  return Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.replace(/\s+/g, ''))
})
console.log('切待定后 :', JSON.stringify(afterFuture))
await p.keyboard.press('Escape')
await p.waitForTimeout(600)
await p.click('.el-dialog__footer button:has-text("取 消")').catch(() => {})
await p.waitForTimeout(800)

// ---- 关联需求抽屉 ----
const idx = info.rows.findIndex((r) => r.includes('UI计划-' + TS))
const rowNo = idx >= 0 ? idx + 1 : 1
await p.click(`.el-table__body tbody tr:nth-child(${rowNo}) button:has-text("更多")`)
await p.waitForTimeout(700)
await p.locator('.el-dropdown-menu__item:visible').filter({ hasText: '关联需求' }).first().click()
await p.waitForTimeout(3000)
const drawer = await p.evaluate(() => {
  const d = document.querySelector('.el-drawer')
  if (!d || d.offsetParent === null) return null
  return {
    title: (d.querySelector('.el-drawer__title') || {}).innerText,
    panels: Array.from(d.querySelectorAll('.font-bold')).map((e) => e.innerText.replace(/\s+/g, ' ')),
    tables: d.querySelectorAll('.el-table').length,
    text: d.innerText.replace(/\s+/g, ' ').slice(0, 200)
  }
})
console.log('关联抽屉 :', JSON.stringify(drawer))
await p.screenshot({ path: '/tmp/zentao-plan.png', fullPage: true })
console.log('截图     : /tmp/zentao-plan.png')
await p.keyboard.press('Escape')
await p.waitForTimeout(800)

// ---- 需求列表的计划列与过滤 ----
await p.goto(BASE + '/zentao/story?product=' + pid, { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(4000)
await p.click('.el-form-item:has-text("所属产品") .el-select')
await p.waitForTimeout(500)
await p.locator('.el-form-item:has-text("所属产品") .el-select input').first().fill('UIPlanProd').catch(() => {})
await p.waitForTimeout(900)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'UIPlanProd' }).first().click()
await p.waitForTimeout(3000)
const storyInfo = await p.evaluate(() => ({
  headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
  rows: Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 110)),
  hasPlanFilter: !!Array.from(document.querySelectorAll('.el-form-item__label')).find((e) => e.innerText.includes('所属计划'))
}))
console.log('需求页表头:', storyInfo.headers.join(' | '))
console.log('有计划的过滤:', storyInfo.hasPlanFilter)
storyInfo.rows.forEach((r) => console.log('   行:', r))

// 清理
await api('DELETE', '/zentao/story/delete?id=' + story.data)
await api('DELETE', '/zentao/plan/delete?id=' + plan.data)
await api('DELETE', '/zentao/product/delete?id=' + pid)
console.log('已清理测试数据')
await b.close()

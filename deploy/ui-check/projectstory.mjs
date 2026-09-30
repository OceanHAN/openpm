import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
const _pw = await import(pathToFileURL(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim() + '/playwright/index.js').href)
const chromium = _pw.chromium || _pw.default?.chromium
const BASE = process.env.ZENTAO_UI_BASE || 'http://localhost'
const API_BASE = process.env.ZENTAO_API_BASE || 'http://localhost:48080/admin-api'

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

await p.goto(BASE + '/zentao/projectstory', { waitUntil: 'domcontentloaded' })
await p.waitForLoadState('networkidle').catch(() => {})
await p.waitForTimeout(2500)

const info = await p.evaluate(() => {
  const txt = document.body.innerText
  const cards = Array.from(document.querySelectorAll('.el-card')).map((e) =>
    (e.querySelector('.el-card__header') || {}).innerText?.trim() || ''
  )
  const tables = Array.from(document.querySelectorAll('.el-table')).map((t) => ({
    headers: Array.from(t.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: Array.from(t.querySelectorAll('.el-table__body tbody tr')).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 110))
  }))
  return {
    url: location.pathname,
    has404: txt.includes('404') || txt.includes('找不到'),
    cards,
    tables,
    hasVersionTag: txt.includes('版本已变更')
  }
})
console.log('页面URL   :', info.url, '| 404:', info.has404)
console.log('区块      :', JSON.stringify(info.cards))
info.tables.forEach((t, i) => {
  console.log(`表${i + 1} 表头 :`, t.headers.join(' | '))
  t.rows.forEach((r) => console.log('   行:', r))
})
console.log('含版本变更标记:', info.hasVersionTag)

// 打开「纳入需求」抽屉
await p.click('button:has-text("纳入需求")')
await p.waitForTimeout(2500)
const drawer = await p.evaluate(() => {
  const d = document.querySelector('.el-drawer')
  if (!d || d.offsetParent === null) return null
  return {
    title: (d.querySelector('.el-drawer__title') || {}).innerText,
    panels: Array.from(d.querySelectorAll('.font-bold')).map((e) => e.innerText.replace(/\s+/g, ' ')),
    text: d.innerText.replace(/\s+/g, ' ').slice(0, 220)
  }
})
console.log('纳入抽屉  :', JSON.stringify(drawer))
await p.screenshot({ path: '/tmp/zentao-projectstory.png', fullPage: true })
console.log('截图      : /tmp/zentao-projectstory.png')
await b.close()

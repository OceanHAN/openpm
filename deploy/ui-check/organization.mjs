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

await p.goto(BASE + '/zentao/organization', { waitUntil: 'domcontentloaded' })
await p.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {})
await p.waitForTimeout(3000)

const read = async () =>
  p.evaluate(() => ({
    url: location.pathname,
    has404: document.body.innerText.includes('404') || document.body.innerText.includes('找不到'),
    tabs: Array.from(document.querySelectorAll('.el-tabs__item')).map((e) => e.innerText.replace(/\s+/g, ' ')),
    headers: Array.from(document.querySelectorAll('.el-table')).map((t) =>
      Array.from(t.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean)
    ),
    firstRows: Array.from(document.querySelectorAll('.el-table')).slice(0, 2).map((t) =>
      Array.from(t.querySelectorAll('.el-table__body tbody tr')).slice(0, 2).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 120))
    ),
    alerts: Array.from(document.querySelectorAll('.el-alert__title')).map((e) => e.innerText.trim())
  }))

const info = await read()
console.log('页面URL   :', info.url, '| 404:', info.has404)
console.log('提示条    :', JSON.stringify(info.alerts))
console.log('Tab       :', JSON.stringify(info.tabs))
console.log('用户表头  :', info.headers[0]?.join(' | '))
info.firstRows[0]?.forEach((r) => console.log('   用户行:', r))

// 切到权限包
await p.click('.el-tabs__item:has-text("权限包")')
await p.waitForTimeout(2000)
const roleInfo = await p.evaluate(() => {
  const t = Array.from(document.querySelectorAll('.el-table')).find((x) =>
    x.innerText.includes('权限包') || x.querySelector('.el-table__header')
  )
  const tables = Array.from(document.querySelectorAll('.el-table'))
  const roleTable = tables.find((x) => x.innerText.includes('禅道权限条数')) || tables[0]
  return {
    headers: Array.from(roleTable.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: Array.from(roleTable.querySelectorAll('.el-table__body tbody tr')).slice(0, 3).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 150))
  }
})
console.log('权限包表头:', roleInfo.headers.join(' | '))
roleInfo.rows.forEach((r) => console.log('   权限包行:', r))

// 切到部门树
await p.click('.el-tabs__item:has-text("部门树")')
await p.waitForTimeout(1800)
const deptInfo = await p.evaluate(() => {
  const t = Array.from(document.querySelectorAll('.el-table')).find((x) => x.innerText.includes('path（禅道写法）')) ||
    Array.from(document.querySelectorAll('.el-table'))[0]
  return {
    headers: Array.from(t.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: Array.from(t.querySelectorAll('.el-table__body tbody tr')).slice(0, 3).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 120))
  }
})
console.log('部门表头  :', deptInfo.headers.join(' | '))
deptInfo.rows.forEach((r) => console.log('   部门行:', r))

// 切到映射对照
await p.click('.el-tabs__item:has-text("迁移映射对照")')
await p.waitForTimeout(1500)
const mapInfo = await p.evaluate(() => {
  const t = Array.from(document.querySelectorAll('.el-table')).find((x) => x.innerText.includes('迁移策略'))
  return t
    ? {
        headers: Array.from(t.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
        rows: Array.from(t.querySelectorAll('.el-table__body tbody tr')).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 130))
      }
    : null
})
console.log('映射表头  :', mapInfo?.headers.join(' | '))
mapInfo?.rows.forEach((r) => console.log('   映射行:', r))

await p.screenshot({ path: '/tmp/zentao-organization.png', fullPage: true })
console.log('截图      : /tmp/zentao-organization.png')
await b.close()

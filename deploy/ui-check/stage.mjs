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

await p.goto(BASE + '/zentao/stage', { waitUntil: 'domcontentloaded' })
await p.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {})
await p.waitForTimeout(2500)

const info = await p.evaluate(() => {
  const txt = document.body.innerText
  return {
    url: location.pathname,
    has404: txt.includes('404') || txt.includes('找不到'),
    headers: Array.from(document.querySelectorAll('.el-table')).map((t) =>
      Array.from(t.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean)
    ),
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((e) =>
      e.innerText.replace(/\s+/g, ' ').slice(0, 110)
    ),
    totalTag: (txt.match(/占比合计\s*([\d.]+)%/) || [])[1] || ''
  }
})
console.log('页面URL   :', info.url, '| 404:', info.has404, '| 占比合计:', info.totalTag + '%')
info.headers.forEach((h, i) => console.log(`表${i + 1} 表头 :`, h.join(' | ')))
info.rows.forEach((r) => console.log('   行:', r))

// 新建弹窗
await p.click('button:has-text("新建阶段")')
await p.waitForTimeout(1500)
const dlg = await p.evaluate(() => {
  const d = document.querySelector('.el-dialog')
  if (!d || d.offsetParent === null) return null
  return {
    title: (d.querySelector('.el-dialog__title') || {}).innerText,
    labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.replace(/\s+/g, '')),
    hint: (d.innerText.match(/当前该流程已占用[^）]*%/) || [''])[0]
  }
})
console.log('新建弹窗  :', JSON.stringify(dlg))
await p.screenshot({ path: '/tmp/zentao-stage.png', fullPage: true })
console.log('截图      : /tmp/zentao-stage.png')
await b.close()

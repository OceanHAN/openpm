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

// 准备：产品 + 项目 + 执行 + 两条构建 + 一条集成构建 + 一个未解决缺陷
const TS = Date.now()
const prod = await api('POST', '/zentao/product/create', { name: 'UIBuildProd-' + TS, code: 'UB' + TS, type: 'normal', PO: 'admin' })
const pid = prod.data
const proj = await api('POST', '/zentao/project/create', { name: 'UIBuildProj-' + TS, model: 'scrum', pri: 3, PM: 'admin', team: 'admin', multiple: 1 })
const exec = await api('POST', '/zentao/execution/create', { project: proj.data, name: 'UIBuildExec-' + TS, type: 'sprint', pri: 3 })
const b1 = await api('POST', '/zentao/build/create', { product: pid, project: proj.data, execution: exec.data, name: 'UI-beta1-' + TS, date: '2026-01-10', builder: 'admin' })
const b2 = await api('POST', '/zentao/build/create', { product: pid, project: proj.data, execution: exec.data, name: 'UI-beta2-' + TS, date: '2026-01-20', builder: 'admin' })
const bi = await api('POST', '/zentao/build/create', { product: pid, integrated: true, builds: [b1.data, b2.data], name: 'UI-集成-' + TS, date: '2026-01-31', builder: 'admin' })
const bug = await api('POST', '/zentao/bug/create', { product: pid, title: 'UI构建缺陷-' + TS, steps: '步骤', severity: 3, pri: 3, type: 'codeerror' })
console.log('准备: 产品=' + pid + ' 执行=' + exec.data + ' 构建=' + b1.data + ',' + b2.data + ' 集成=' + bi.data + ' 缺陷=' + bug.data)

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

// ---- 构建列表页 ----
await p.goto(BASE + '/zentao/build', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(6000)
await p.click('.el-form-item:has-text("所属产品") .el-select')
await p.waitForTimeout(500)
await p.locator('.el-form-item:has-text("所属产品") .el-select input').first().fill('UIBuildProd').catch(() => {})
await p.waitForTimeout(900)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'UIBuildProd' }).first().click()
await p.waitForTimeout(3000)
const info = await p.evaluate(() => {
  const txt = document.body.innerText
  return {
    url: location.pathname,
    has404: txt.includes('404') || txt.includes('找不到'),
    ctx404: (() => {
      const i = txt.indexOf('404')
      return i >= 0 ? txt.slice(Math.max(0, i - 50), i + 50).replace(/\s+/g, ' ') : ''
    })(),
    headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 130)),
    total: (txt.match(/共 (\d+) 条/) || [])[1] || ''
  }
})
console.log('页面URL  :', info.url, '| 404:', info.has404, info.ctx404 ? '上下文=[' + info.ctx404 + ']' : '', '| 共:', info.total)
console.log('表头     :', info.headers.join(' | '))
info.rows.forEach((r) => console.log('   行:', r))

// ---- 新建弹窗（集成构建开关） ----
await p.click('button:has-text("新建构建")')
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
await p.click('.el-dialog .el-switch')
await p.waitForTimeout(900)
const afterIntegrated = await p.evaluate(() => {
  const d = document.querySelector('.el-dialog')
  return Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.replace(/\s+/g, ''))
})
console.log('切集成后 :', JSON.stringify(afterIntegrated))
await p.keyboard.press('Escape')
await p.waitForTimeout(500)
await p.click('.el-dialog__footer button:has-text("取 消")').catch(() => {})
await p.waitForTimeout(800)

// ---- 关联抽屉：Bug Tab（关联后自动解决） ----
const idx = info.rows.findIndex((r) => r.includes('UI-beta1'))
const rowNo = idx >= 0 ? idx + 1 : 1
await p.click(`.el-table__body tbody tr:nth-child(${rowNo}) button:has-text("关联")`)
await p.waitForTimeout(3000)
const drawer = await p.evaluate(() => {
  const d = document.querySelector('.el-drawer')
  if (!d || d.offsetParent === null) return null
  return {
    title: (d.querySelector('.el-drawer__title') || {}).innerText,
    tabs: Array.from(d.querySelectorAll('.el-tabs__item')).map((e) => e.innerText.replace(/\s+/g, ' ')),
    text: d.innerText.replace(/\s+/g, ' ').slice(0, 220)
  }
})
console.log('关联抽屉 :', JSON.stringify(drawer))
await p.screenshot({ path: '/tmp/zentao-build.png', fullPage: true })
console.log('截图     : /tmp/zentao-build.png')
await p.keyboard.press('Escape')
await p.waitForTimeout(800)

// ---- Bug 解决弹窗的「解决版本」下拉 ----
await p.goto(BASE + '/zentao/bug', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(4000)
await p.click('.el-form-item:has-text("所属产品") .el-select')
await p.waitForTimeout(500)
await p.locator('.el-form-item:has-text("所属产品") .el-select input').first().fill('UIBuildProd').catch(() => {})
await p.waitForTimeout(900)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'UIBuildProd' }).first().click()
await p.waitForTimeout(3000)
const bugRow = await p.evaluate(() => {
  const tr = Array.from(document.querySelectorAll('.el-table__body tbody tr')).find((x) => x.innerText.includes('UI构建缺陷'))
  return tr ? tr.innerText.replace(/\s+/g, ' ').slice(0, 100) : '(未找到)'
})
console.log('缺陷行   :', bugRow)
const rIdx = await p.evaluate(() => {
  const trs = Array.from(document.querySelectorAll('.el-table__body tbody tr'))
  return trs.findIndex((x) => x.innerText.includes('UI构建缺陷'))
})
if (rIdx >= 0) {
  await p.click(`.el-table__body tbody tr:nth-child(${rIdx + 1}) button:has-text("解决")`)
  await p.waitForTimeout(2500)
  const resolveDlg = await p.evaluate(() => {
    const d = document.querySelector('.el-dialog')
    if (!d || d.offsetParent === null) return null
    const labels = Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.replace(/\s+/g, ''))
    return { labels, text: d.innerText.replace(/\s+/g, ' ').slice(0, 160) }
  })
  console.log('解决弹窗 :', JSON.stringify(resolveDlg))
  // 打开「解决版本」下拉，应该能选到构建
  await p.click('.el-dialog .el-form-item:has-text("解决版本") .el-select')
  await p.waitForTimeout(1200)
  const opts = await p.evaluate(() =>
    Array.from(document.querySelectorAll('.el-select-dropdown:not([style*="display: none"]) .el-select-dropdown__item'))
      .map((e) => e.innerText.trim())
      .filter((t) => t.includes('UI-beta'))
  )
  console.log('解决版本选项:', JSON.stringify(opts))
}

// 清理
await api('DELETE', '/zentao/bug/delete?id=' + bug.data)
for (const id of [bi.data, b1.data, b2.data]) await api('DELETE', '/zentao/build/delete?id=' + id)
await api('DELETE', '/zentao/execution/delete?id=' + exec.data)
await api('DELETE', '/zentao/project/delete?id=' + proj.data)
await api('DELETE', '/zentao/product/delete?id=' + pid)
console.log('已清理测试数据')
await b.close()

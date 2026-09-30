import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
const _pw = await import(pathToFileURL(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim() + '/playwright/index.js').href)
const chromium = _pw.chromium || _pw.default?.chromium
const BASE = process.env.ZENTAO_UI_BASE || 'http://localhost'
const API_BASE = process.env.ZENTAO_API_BASE || 'http://localhost:48080/admin-api'
const stamp = 'UI执行-' + Date.now()

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
p.on('console', (m) => { if (m.type() === 'error') console.log('  [console.error]', m.text().slice(0, 160)) })
p.on('pageerror', (e) => console.log('  [pageerror]', String(e).slice(0, 200)))

// ---- 登录（env.local 已关闭验证码）----
await p.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(1500)
await p.fill('input[placeholder="请输入用户名"]', 'admin')
await p.fill('input[placeholder="请输入密码"]', 'admin123')
await p.locator('button:has-text("登录")').first().click()
await p.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 25000 })
console.log('登录成功 →', p.url())

// ---- 准备数据：项目 + 执行 ----
// 直接用后端登录接口拿 token，不依赖前端存储实现
const loginResp = await fetch(`${API_BASE}/system/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'tenant-id': '1' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
}).then((r) => r.json())
const tok = loginResp.data.accessToken
console.log('token:', tok.slice(0, 12) + '...')
const api = async (method, url, body) => {
  const r = await fetch(API_BASE + url, {
    method,
    headers: { Authorization: 'Bearer ' + tok, 'tenant-id': '1', 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  })
  return r.json()
}
const proj = await api('POST', '/zentao/project/create', { name: 'UIProj-' + Date.now(), model: 'scrum', pri: 3, estimate: 100, PM: 'admin', team: 'admin' })
const pid = proj.data
const exec = await api('POST', '/zentao/execution/create', { project: pid, name: stamp, type: 'sprint', pri: 3, estimate: 80, PM: 'admin', team: 'admin', begin: '2026-03-01', end: '2026-03-31' })
console.log('准备数据: 项目=' + pid + ' 执行=' + exec.data + ' (code=' + exec.code + ')')

// ---- 打开执行管理页 ----
await p.goto(BASE + '/zentao/execution', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(7000)
const info = await p.evaluate(() => {
  const txt = document.body.innerText
  return {
    url: location.pathname,
    has404: txt.includes('404') || txt.includes('找不到'),
    alert: (document.querySelector('.el-alert__title') || {}).innerText || '',
    headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
    rows: document.querySelectorAll('.el-table__body tbody tr').length,
    full: txt,
    text: txt.replace(/\s+/g, ' ').slice(0, 400)
  }
})
console.log('页面URL   :', info.url)
console.log('404       :', info.has404)
console.log('提示条    :', info.alert)
console.log('表头      :', info.headers.join(' | '))
console.log('表格行数  :', info.rows)
console.log('列表含新行:', info.full.includes(stamp))

// ---- UI 新增执行 ----
await p.click('button:has-text("新增执行")')
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
console.log('弹窗      :', JSON.stringify(dlg))

const uiName = 'UI新建执行-' + Date.now()
await p.fill('.el-dialog input[placeholder="如 第 1 迭代"]', uiName)
// 所属项目下拉
await p.click('.el-dialog .el-form-item:has-text("所属项目") .el-select')
await p.waitForTimeout(800)
await p.fill('.el-dialog .el-form-item:has-text("所属项目") .el-select input', 'UIProj')
await p.waitForTimeout(1500)
// 下拉项用键盘选中，避免页面上残留的多个 dropdown 干扰
await p.keyboard.press('ArrowDown')
await p.waitForTimeout(300)
await p.keyboard.press('Enter')
await p.waitForTimeout(500)
await p.click('.el-dialog__footer button:has-text("确 定")')
await p.waitForTimeout(2500)
const after = await p.evaluate(() => ({
  msgs: Array.from(document.querySelectorAll('.el-message')).map((e) => e.innerText.trim()),
  dlgOpen: (() => { const d = document.querySelector('.el-dialog'); return !!(d && d.offsetParent !== null) })(),
  has: document.body.innerText.includes(window.__n || '')
}))
console.log('提交提示  :', JSON.stringify(after.msgs), '弹窗仍开=', after.dlgOpen)
await p.waitForTimeout(500)
const listed = await p.evaluate((n) => document.body.innerText.includes(n), uiName)
console.log('列表含UI新建行:', listed)

// ---- 状态机按钮：开始 ----
const rowIdx = await p.evaluate((n) => {
  const trs = Array.from(document.querySelectorAll('.el-table__body tbody tr'))
  return trs.findIndex((tr) => tr.innerText.includes(n))
}, uiName)
if (rowIdx >= 0) {
  await p.click(`.el-table__body tbody tr:nth-child(${rowIdx + 1}) button:has-text("开始")`)
  await p.waitForTimeout(800)
  await p.click('.el-message-box__btns button:has-text("确定")')
  await p.waitForTimeout(2500)
  const st = await p.evaluate((n) => {
    const tr = Array.from(document.querySelectorAll('.el-table__body tbody tr')).find((x) => x.innerText.includes(n))
    return tr ? tr.innerText.replace(/\s+/g, ' ') : '(未找到)'
  }, uiName)
  console.log('开始后该行:', st.slice(0, 160))
}

// ---- 任务页联动：从执行页跳任务 ----
await p.goto(BASE + '/zentao/task?execution=' + exec.data, { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(3500)
const taskInfo = await p.evaluate(() => ({
  url: location.pathname + location.search,
  headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
  execFilter: (() => {
    const it = Array.from(document.querySelectorAll('.el-form-item')).find((e) => e.innerText.includes('所属执行'))
    return it ? (it.querySelector('input') || {}).value || '' : '(无)'
  })()
}))
console.log('任务页    :', JSON.stringify(taskInfo))

// ---- 截图 ----
await p.goto(BASE + '/zentao/execution', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(3500)
await p.screenshot({ path: '/tmp/zentao-execution.png', fullPage: true })
console.log('截图      : /tmp/zentao-execution.png')

// ---- 清理 ----
await api('DELETE', '/zentao/execution/delete-list?ids=' + exec.data)
const page2 = await api('GET', '/zentao/execution/page?pageNo=1&pageSize=100&project=' + pid)
for (const r of page2.data.list || []) await api('DELETE', '/zentao/execution/delete?id=' + r.id)
await api('DELETE', '/zentao/project/delete?id=' + pid)
console.log('已清理测试数据')
await b.close()

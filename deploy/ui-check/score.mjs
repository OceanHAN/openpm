// 积分（score）界面检查
//
// 覆盖 /zentao/score：
//   ① 概览卡片（总积分 / 昨日新增 / 流水条数 / 功能开关）+ 口径说明
//   ② 积分记录 Tab（模块与动作中文名、分值、分页）
//   ③ 积分规则 Tab（38 条、抽查 user.login 的 3/24/1、tutorial.finish 的 100 分、execution.close 的扩展说明）
//   ④ 计分试算 Tab（点一次计分：要么计上，要么明确提示「命中次数/时间窗」——两种都算通过）
//
// 说明：**界面里没有删除入口**（禅道也没有），所以这里不建数据；试算用已存在的账号 admin。
//
// 依赖：后端 $ZENTAO_API_BASE（默认 http://localhost:48080/admin-api），前端 $ZENTAO_UI_BASE（默认 http://localhost）
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { login } from './_login.mjs'
const _pw = await import(pathToFileURL(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim() + '/playwright/index.js').href)
const chromium = _pw.chromium || _pw.default?.chromium
const BASE = process.env.ZENTAO_UI_BASE || 'http://localhost'
const API_BASE = process.env.ZENTAO_API_BASE || 'http://localhost:48080/admin-api'

let PASS = 0; let FAIL = 0
const ok = (name, cond, extra = '') => {
  if (cond) { PASS++; console.log(`  ✅ ${name}${extra ? '  ' + extra : ''}`) }
  else { FAIL++; console.log(`  ❌ ${name}${extra ? '  ' + extra : ''}`) }
}

const loginResp = await fetch(`${API_BASE}/system/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'tenant-id': '1' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
}).then((r) => r.json())
if (!loginResp.data) throw new Error('登录失败：' + JSON.stringify(loginResp))
const tok = loginResp.data.accessToken
const api = async (m, u, b) => (await fetch(API_BASE + u, {
  method: m,
  headers: { Authorization: 'Bearer ' + tok, 'tenant-id': '1', 'Content-Type': 'application/json' },
  body: b ? JSON.stringify(b) : undefined
})).json()

const totalBefore = await api('GET', '/zentao/score/total?account=admin')
console.log(`准备: admin 当前积分 = ${totalBefore.data?.total}，流水 ${totalBefore.data?.count} 条`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1700, height: 1050 } })
const p = await c.newPage()
const pageErrors = []
p.on('pageerror', (e) => pageErrors.push(String(e && e.stack ? e.stack : JSON.stringify(e)).split('\n')[0].slice(0, 200)))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))
// 页面内自己挂监听：Playwright 的 pageerror 对某些对象只能给出 Array(1): Object（坑位 #53）
await p.addInitScript(() => {
  window.__errs = []
  window.addEventListener('error', (e) => {
    if (String(e.message || '').includes('ResizeObserver loop')) return
    window.__errs.push(`${e.message} @ ${e.filename || ''}:${e.lineno || 0}`)
  })
  window.addEventListener('unhandledrejection', (e) => {
    let r = e.reason
    let desc
    try { desc = JSON.stringify(r) } catch { desc = String(r) }
    if (r && r.msg) desc = `msg=${r.msg} code=${r.code}`
    window.__errs.push('unhandledrejection: ' + desc)
  })
})
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', async (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', async (err) => { __cleanup(); console.error(err); process.exit(1) })

await login(p, BASE)
console.log('登录成功')

// 只取**当前可见页签**里的表格行：Element Plus 的 el-tab-pane 切换后旧页签仍在 DOM 里，
// 不限定范围的话「积分记录」的行会和「积分规则」的 38 行混在一起（本轮踩到）
const view = async () =>
  p.evaluate(() => {
    const pane = document.querySelector('.el-tab-pane:not([style*="display: none"])') || document.body
    return {
      text: document.body.innerText.replace(/\s+/g, ' ').trim(),
      rows: Array.from(pane.querySelectorAll('.el-table__body tbody tr'))
        .filter((r) => !r.closest('.el-dialog'))
        .map((r) => r.innerText.replace(/\s+/g, ' ').trim())
    }
  })
const switchTab = async (label) => {
  await p.locator(`.el-tabs__item:has-text("${label}")`).first().click()
  await p.waitForTimeout(1500)
}

await p.goto(BASE + '/zentao/score', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 概览 ----------------
let v = await view()
ok('概览显示当前积分', v.text.includes('当前积分'), '')
ok('概览显示功能开关', v.text.includes('功能开关') && v.text.includes('已开启'), '')
ok('页面写明了总分口径（SUM(zt_score.score)，禅道冗余在 zt_user.score）', v.text.includes('SUM(zt_score.score)'), '')

// ---------------- ② 积分记录 ----------------
ok('积分记录表格有数据', v.rows.length >= 1, `行数=${v.rows.length}`)
ok('列表带模块与动作中文名（不是裸 task/finish）', /任务|用户|缺陷|需求|执行/.test(v.text), (v.rows[0] || '').slice(0, 60))

// ---------------- ③ 积分规则 ----------------
await switchTab('积分规则')
await p.waitForTimeout(2000)
v = await view()
ok('规则表条数 = 38', v.rows.length === 38, `行数=${v.rows.length}`)
const loginRow = v.rows.find((r) => r.includes('user.login')) || ''
ok('user.login 规则：3 次 / 24 小时 / 1 分', loginRow.includes('3') && loginRow.includes('24') && loginRow.includes('登录'), loginRow)
const tutorialRow = v.rows.find((r) => r.includes('tutorial.finish')) || ''
ok('tutorial.finish 规则：1 次 / 100 分', tutorialRow.includes('100'), tutorialRow)
const execRow = v.rows.find((r) => r.includes('execution.close')) || ''
ok('execution.close 带扩展加成说明（项目经理/成员）', execRow.includes('项目经理') && execRow.includes('成员'), execRow.slice(0, 80))
ok('规则页写明了「次数/时间窗」的实现口径（hour=0 数全量、hour>0 数当天）',
  v.text.includes('hour=0') && v.text.includes('当天'), '')

// ---------------- ④ 计分试算 ----------------
await switchTab('计分试算')
await p.waitForTimeout(1200)
await p.locator('.el-tab-pane:visible .el-form-item:has-text("模块") input').first().fill('user')
await p.locator('.el-tab-pane:visible .el-form-item:has-text("动作") input').first().fill('login')
await p.locator('.el-tab-pane:visible .el-form-item:has-text("账号") input').first().fill('admin')
await p.locator('button:has-text("计分")').first().click()
await p.waitForTimeout(2500)
v = await view()
const scored = v.text.includes('计分成功')
const skipped = v.text.includes('没有产生流水')
ok('点「计分」后有明确结果（计上了 或 命中次数/时间窗）', scored || skipped,
  scored ? '计分成功' : (skipped ? '命中上限（静默跳过）' : v.text.slice(-120)))
ok('结果提示里解释了「静默跳过」的语义', !skipped || v.text.includes('静默跳过'), '')

// 规则不存在 → 明确报错
await p.locator('.el-tab-pane:visible .el-form-item:has-text("模块") input').first().fill('nosuch')
await p.locator('.el-tab-pane:visible .el-form-item:has-text("动作") input').first().fill('nope')
await p.locator('button:has-text("计分")').first().click()
await p.waitForTimeout(2500)
v = await view()
ok('未知规则 → 明确报错（接口口径，禅道内部调用才是静默）', v.text.includes('计分失败') && v.text.includes('积分规则不存在'), '')

const inPageErrors = await p.evaluate(() => window.__errs || [])
ok('页面没有 JS 报错', pageErrors.length === 0 && inPageErrors.length === 0,
  [...pageErrors, ...inPageErrors].slice(0, 3).join(' | '))

await p.screenshot({ path: '/tmp/zentao-score.png', fullPage: true })
console.log('截图       : /tmp/zentao-score.png')

await b.close()
console.log('======================================================')
console.log(`  score 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

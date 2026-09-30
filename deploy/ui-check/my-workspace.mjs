// 我的地盘（my）第二组界面检查：我参与的项目 / 执行 / 团队 / 测试单 / 用例 / 文档 / 日历
//
// 覆盖 /zentao/my 的 7 个新页签：
//   ① 我参与的项目：我以 PM 身份参与的项目能查到（member 过滤由后端注入，前端不传账号）
//   ② 我参与的执行：靠团队成员命中我的执行能查到
//   ③ 我的团队：一行 = 我在某个项目/执行里的角色，带对象名与可用工时
//   ④ 我的测试单：我创建的测试单能查到
//   ⑤ 我的用例：口径是「我创建的 或 我评审过的」（表格里同时显示创建人与评审人）
//   ⑥ 我的文档：我创建的正文能查到（章节默认不在里面）
//   ⑦ 我的日历：今天的待办在日历上落到今天那一格
//
// 依赖：后端 127.0.0.1:48080，前端 http://localhost/
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
const tok = loginResp.data.accessToken
const api = async (m, u, b) => (await fetch(API_BASE + u, {
  method: m,
  headers: { Authorization: 'Bearer ' + tok, 'tenant-id': '1', 'Content-Type': 'application/json' },
  body: b ? JSON.stringify(b) : undefined
})).json()

const TS = Date.now()
const PNAME = 'UI我参与项目-' + TS
const ENAME = 'UI我参与执行-' + TS
const TNAME = 'UI测试单-' + TS
const CNAME = 'UI我的用例-' + TS
const DNAME = 'UI我的文档-' + TS
const TODO = 'UI日历待办-' + TS
// 本地时区拼日期（不能用 toISOString()：那是 UTC，凌晨 0~8 点会算成「昨天」，
// 而服务端页面上的日期是本地的，两边错位会让「今天那一格」这类断言假红）
const TODAY = (() => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
})()

// ---------------- 准备数据 ----------------
// 这些 id 先声明成 let：cleanupData 是闭包，准备阶段中途失败时它们可能还没赋值
let PID, EID, MID, TID, CID, DID, TODOID
const proj = await api('POST', '/zentao/project/create', { project: 0, name: PNAME, model: 'scrum', pri: 3, PM: 'admin' })
if (proj.code !== 0) throw new Error('准备项目失败：' + JSON.stringify(proj))
PID = proj.data
// 执行创建不收 team 字段（成员来自负责人字段），要把自己算进团队得单独 add-member
const exec = await api('POST', '/zentao/execution/create', { name: ENAME, project: PID, type: 'sprint', pri: 3, PM: 'tester' })
if (exec.code !== 0) throw new Error('准备执行失败：' + JSON.stringify(exec))
EID = exec.data
const addMe = await api('POST', '/zentao/team/add-member', { root: EID, type: 'execution', account: 'admin', role: '研发', days: 5, hours: 7 })
if (addMe.code !== 0) throw new Error('准备执行成员失败：' + JSON.stringify(addMe))
MID = addMe.data
const tt = await api('POST', '/zentao/testtask/create', { product: 1, name: TNAME, type: 'feature', begin: TODAY, end: '2026-12-31' })
if (tt.code !== 0) throw new Error('准备测试单失败：' + JSON.stringify(tt))
TID = tt.data
// 用例默认状态是 normal（视为已评审过），要测「我评审过的」必须显式建成 wait
const cs = await api('POST', '/zentao/testcase/create', { product: 1, title: CNAME, type: 'feature', status: 'wait' })
if (cs.code !== 0) throw new Error('准备用例失败：' + JSON.stringify(cs))
CID = cs.data
// 用例：评审一下，这样「我创建的 + 我评审过的」两条路径同时命中（顺便验证去重）
const rv = await api('PUT', '/zentao/testcase/review', { id: CID, result: 'normal', comment: '通过' })
if (rv.code !== 0) throw new Error('准备评审失败：' + JSON.stringify(rv))
const libs = await api('GET', '/zentao/doc/lib/list?type=product&objectID=1')
const LIB = (libs?.data || [])[0]?.id
const dc = await api('POST', '/zentao/doc/create', { lib: LIB, title: DNAME, type: 'markdown', content: '正文' })
if (dc.code !== 0) throw new Error('准备文档失败：' + JSON.stringify(dc))
DID = dc.data
const td = await api('POST', '/zentao/todo/create', { name: TODO })
if (td.code !== 0) throw new Error('准备待办失败：' + JSON.stringify(td))
TODOID = td.data
console.log(`准备: 项目=${PID} 执行=${EID} 测试单=${TID} 用例=${CID} 文档=${DID} 待办=${TODOID}`)

// 中间步骤抛异常时也要把准备的数据删掉：上一版的清理写在脚本末尾，
// 结果「评审用例」那步报错退出，就留下了一个 deleted=0 的测试单，
// 把 test-testtask-module.sh 里「产品 1 下共 3 个测试单」的断言顶成了 4（真实的连带故障）
const cleanupData = async () => {
  const del = (url) => api('DELETE', url).catch(() => {})
  if (MID) await del('/zentao/team/remove-member?id=' + MID)
  if (TODOID) await del('/zentao/todo/delete?id=' + TODOID)
  if (DID) await del('/zentao/doc/delete?id=' + DID)
  if (CID) await del('/zentao/testcase/delete?id=' + CID)
  if (TID) await del('/zentao/testtask/delete?id=' + TID)
  if (EID) await del('/zentao/execution/delete?id=' + EID)
  if (PID) await del('/zentao/project/delete?id=' + PID)
}

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
const p = await c.newPage()
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => {
  __cleanup()
  cleanupData().finally(() => { console.error(err); process.exit(1) })
})
process.on('unhandledRejection', (err) => {
  __cleanup()
  cleanupData().finally(() => { console.error(err); process.exit(1) })
})
const pageErrors = []
p.on('pageerror', (e) => pageErrors.push(String(e.stack || e).split('\n')[0]))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))

await login(p, BASE)
console.log('登录成功')

// 当前可见 tab-pane 的表头与行文本
const readPane = async () =>
  p.evaluate(() => {
    const pane = Array.from(document.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
    if (!pane) return { headers: [], rows: [], text: '' }
    return {
      headers: Array.from(pane.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
      rows: Array.from(pane.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim()),
      text: pane.innerText.replace(/\s+/g, ' ').trim()
    }
  })

const gotoTab = async (label, apiFrag) => {
  const arrived = new Promise((resolve) => {
    const handler = (r) => { if (r.url().includes(apiFrag)) { p.off('response', handler); resolve(true) } }
    p.on('response', handler)
    setTimeout(() => { p.off('response', handler); resolve(false) }, 20000)
  })
  await p.locator('.el-tabs__item').filter({ hasText: label }).first().click()
  await arrived
  await p.waitForTimeout(2000)
}

await p.goto(BASE + '/zentao/my', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 我参与的项目 ----------------
await gotoTab('我参与的项目', '/zentao/my/project-page')
let pane = await readPane()
console.log('① 我参与的项目 :', JSON.stringify({ headers: pane.headers, rows: pane.rows.length }))
ok('表头齐全', ['编号', '项目名称', '状态', '负责人'].every((h) => pane.headers.includes(h)), JSON.stringify(pane.headers))
ok('我以 PM 身份参与的项目出现在列表里', pane.rows.some((r) => r.includes(PNAME)), PNAME)

// ---------------- ② 我参与的执行 ----------------
await gotoTab('我参与的执行', '/zentao/my/execution-page')
pane = await readPane()
console.log('② 我参与的执行 :', JSON.stringify({ headers: pane.headers, rows: pane.rows.length }))
ok('表头齐全', ['编号', '执行名称', '类型'].every((h) => pane.headers.includes(h)), JSON.stringify(pane.headers))
ok('团队成员命中我的执行出现在列表里', pane.rows.some((r) => r.includes(ENAME)), ENAME)

// ---------------- ③ 我的团队 ----------------
await gotoTab('我的团队', '/zentao/my/team-list')
pane = await readPane()
console.log('③ 我的团队 :', JSON.stringify({ headers: pane.headers, rows: pane.rows.length }))
ok('表头齐全', ['项目/执行', '我的角色', '可用工作日', '工时上限'].every((h) => pane.headers.includes(h)), JSON.stringify(pane.headers))
ok('团队列表有数据', pane.rows.length > 0, `行数=${pane.rows.length}`)
ok('新执行那一行补上了对象名', pane.text.includes(ENAME), ENAME)
ok('说明了团队行的口径', pane.text.includes('项目/执行 × 成员'), pane.text.slice(0, 60))

// ---------------- ④ 我的测试单 ----------------
await gotoTab('我的测试单', '/zentao/my/testtask-page')
pane = await readPane()
console.log('④ 我的测试单 :', JSON.stringify({ headers: pane.headers, rows: pane.rows.length }))
ok('表头齐全', ['编号', '测试单名称', '状态', '负责人'].every((h) => pane.headers.includes(h)), JSON.stringify(pane.headers))
ok('我创建的测试单出现在列表里', pane.rows.some((r) => r.includes(TNAME)), TNAME)

// ---------------- ⑤ 我的用例 ----------------
await gotoTab('我的用例', '/zentao/my/case-page')
pane = await readPane()
console.log('⑤ 我的用例 :', JSON.stringify({ headers: pane.headers, rows: pane.rows.length }))
ok('表头齐全', ['编号', '用例标题', '创建人', '评审人'].every((h) => pane.headers.includes(h)), JSON.stringify(pane.headers))
const caseRows = pane.rows.filter((r) => r.includes(CNAME))
ok('我创建并评审过的用例出现且只出现一次', caseRows.length === 1, `出现 ${caseRows.length} 次`)
ok('行里能同时看到创建人与评审人', (caseRows[0] || '').split('admin').length >= 3, caseRows[0] || '')
ok('说明了 OR + 去重的口径', pane.text.includes('我创建的') && pane.text.includes('我评审过的'), pane.text.slice(0, 60))

// ---------------- ⑥ 我的文档 ----------------
await gotoTab('我的文档', '/zentao/my/doc-page')
pane = await readPane()
console.log('⑥ 我的文档 :', JSON.stringify({ headers: pane.headers, rows: pane.rows.length }))
ok('表头齐全', ['编号', '文档标题', '创建人', '最后修改'].every((h) => pane.headers.includes(h)), JSON.stringify(pane.headers))
ok('我创建的文档出现在列表里', pane.rows.some((r) => r.includes(DNAME)), DNAME)
const docRows = pane.rows.filter((r) => r.includes(DNAME)).join(' ')
ok('文档行的最后修改人是我', docRows.includes('admin'), docRows)

// ---------------- ⑦ 我的日历 ----------------
await gotoTab('我的日历', '/zentao/my/calendar')
const cal = await p.evaluate(() => {
  const pane = Array.from(document.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
  if (!pane) return { days: [], text: '' }
  const days = Array.from(pane.querySelectorAll('.mb-12px')).map((el) => el.innerText.replace(/\s+/g, ' ').trim())
  return { days, text: pane.innerText.replace(/\s+/g, ' ').trim() }
})
console.log('⑦ 我的日历 :', JSON.stringify({ 天数: cal.days.length, 首日: cal.days[0]?.slice(0, 60) }))
ok('日历按天分组渲染', cal.days.length > 0, `天数=${cal.days.length}`)
ok('今天那一格有今天新建的待办', cal.days.some((d) => d.includes(TODAY) && d.includes(TODO)),
  cal.days.find((d) => d.includes(TODAY))?.slice(0, 80) || '没有今天')
ok('日历说明只收待办/任务/测试单三类', cal.text.includes('待办') && cal.text.includes('测试单'), '')

ok('页面没有 JS 报错', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '))

await p.screenshot({ path: '/tmp/zentao-my-workspace.png', fullPage: true })
console.log('截图       : /tmp/zentao-my-workspace.png')

// ---------------- 收尾 ----------------
await cleanupData()
console.log('已清理测试数据')

console.log('======================================================')
console.log(`  my 第二组界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
await b.close()
if (FAIL > 0) process.exit(1)

// 用例库（caselib）界面检查
//
// 覆盖 /zentao/caselib：
//   ① 左侧用例库列表渲染（演示库「公共用例库」+ 用例数角标）
//   ② 点库 → 右侧库内用例列表切换（演示库 2 条）
//   ③ 新建用例库（走弹窗）→ 列表多一行、自动选中
//   ④ 库内新建用例（带步骤）→ 用例列表多一条、步骤落库
//   ⑤ 从产品导入：勾选产品用例 → 导入 → 库里出现带「来自 #xx」标记的用例
//   ⑥ 删除保护：库内还有用例时删除被拒绝
//
// 依赖：后端 127.0.0.1:48080（本机栈或远端栈都行），前端 http://localhost/
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
const LIB_NAME = 'UI用例库-' + TS

// 清掉上一次中断留下的临时库（保证脚本可重复执行）；
// 顺带清掉演示库里被历史检查「导入」进来的用例 —— 演示库应当只有 95401/95402 两条手工用例
for (const item of (await api('GET', '/zentao/caselib/case-page?libId=95301&pageNo=1&pageSize=100')).data.list) {
  if (item.fromCaseID > 0) await api('DELETE', '/zentao/testcase/delete?id=' + item.id)
}

for (const lib of (await api('GET', '/zentao/caselib/list')).data || []) {
  if (!(lib.name || '').startsWith('UI用例库-')) continue
  for (const item of (await api('GET', `/zentao/caselib/case-page?libId=${lib.id}&pageNo=1&pageSize=100`)).data.list) {
    await api('DELETE', '/zentao/testcase/delete?id=' + item.id)
  }
  await api('DELETE', '/zentao/caselib/delete?id=' + lib.id)
}

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
const p = await c.newPage()
p.on('pageerror', (e) => console.log('  [pageerror]', String(e.stack || e).split('\n').slice(0, 3).join(' | ')))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })

await login(p, BASE)
console.log('登录成功')

await p.goto(BASE + '/zentao/caselib', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(4500)

// 页面里有两个 el-table：左边库列表、右边（此时还没选库时不渲染）用例列表
// 轮询等待（界面刷新/后端写入都有延迟，固定 sleep 会把「慢」误报成「坏」）
const waitFor = async (fn, timeout = 20000, interval = 500) => {
  const end = Date.now() + timeout
  for (;;) {
    const v = await fn().catch(() => null)
    if (v) return v
    if (Date.now() > end) return null
    await p.waitForTimeout(interval)
  }
}

const tableRows = async (idx) =>
  p.evaluate((i) => {
    const tables = Array.from(document.querySelectorAll('.el-table')).filter((t) => t.offsetParent !== null)
    const table = tables[i]
    if (!table) return []
    return Array.from(table.querySelectorAll('.el-table__body tbody tr')).map((r) =>
      Array.from(r.querySelectorAll('td')).map((td) => td.innerText.trim())
    )
  }, idx)

// ---------------- ① 库列表 ----------------
const libRows = await tableRows(0)
console.log('  库列表:', JSON.stringify(libRows.map((r) => [r[0], r[1]])))
ok('用例库列表渲染出演示库', libRows.some((r) => r[0] === '公共用例库'))
ok('演示库带用例数 2', libRows.some((r) => r[0] === '公共用例库' && r[1] === '2'))

// ---------------- ② 默认选中第一个库 → 右侧用例列表 ----------------
await p.waitForTimeout(1500)
let caseRows = await tableRows(1)
console.log('  库内用例:', JSON.stringify(caseRows.map((r) => [r[0], r[1]])))
ok('右侧展示该库的用例', caseRows.length === 2, `行数=${caseRows.length}`)
ok('库内用例带标题与状态', caseRows.some((r) => r[1].includes('库用例：账号密码正确登录')))

// ---------------- ③ 新建用例库 ----------------
await p.getByRole('button', { name: '新建用例库', exact: true }).first().click()
await p.waitForTimeout(1200)
const libDlg = p.locator('.el-dialog:visible').filter({ hasText: '新建用例库' }).first()
await libDlg.waitFor({ state: 'visible', timeout: 15000 })
await libDlg.locator('input[placeholder="用例库名称（全局唯一）"]').fill(LIB_NAME)
await libDlg.locator('textarea').first().fill('界面检查建的库')
await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/caselib/create'), { timeout: 30000 }).catch(() => null),
  libDlg.locator('.el-dialog__footer button:has-text("确 定")').click()
])
await p.waitForTimeout(2500)
const createdLib = (await api('GET', '/zentao/caselib/list')).data.find((l) => l.name === LIB_NAME)
ok('界面新建用例库成功', !!createdLib, JSON.stringify(createdLib))
await p.waitForTimeout(500)
const libRows2 = await tableRows(0)
ok('新库出现在左侧列表', libRows2.some((r) => r[0] === LIB_NAME))

// 选中新库（点行）→ 右侧应当是空的
const newLibRow = p.locator('.el-table').first().locator('tbody tr').filter({ hasText: LIB_NAME }).first()
await newLibRow.click()
await p.waitForTimeout(2000)
ok('切到新库后右侧为空（新库没有用例）', (await tableRows(1)).length === 0)

// ---------------- ④ 库内新建用例（带步骤） ----------------
await p.getByRole('button', { name: '新建用例', exact: true }).first().click()
await p.waitForTimeout(1200)
const caseDlg = p.locator('.el-dialog:visible').filter({ hasText: '新建库内用例' }).first()
await caseDlg.waitFor({ state: 'visible', timeout: 15000 })
await caseDlg.locator('input[placeholder="用例标题"]').fill('UI库内用例-' + TS)
await caseDlg.locator('input[placeholder="操作步骤"]').first().fill('打开页面')
await caseDlg.locator('input[placeholder="预期结果"]').first().fill('页面正常')
await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/caselib/create-case'), { timeout: 30000 }).catch(() => null),
  caseDlg.locator('.el-dialog__footer button:has-text("确 定")').click()
])
await p.waitForTimeout(2500)
const createdCase = (await api('GET', `/zentao/caselib/case-page?libId=${createdLib.id}&pageNo=1&pageSize=10`)).data.list[0]
ok('界面建出库内用例', !!createdCase && createdCase.lib === createdLib.id,
  JSON.stringify(createdCase ? { id: createdCase.id, lib: createdCase.lib, steps: createdCase.steps?.length } : createdCase))
ok('库内用例的步骤落库（1 步）', createdCase?.steps?.length === 1 ||
  (await api('GET', '/zentao/caselib/case-get?id=' + createdCase?.id)).data.steps.length === 1)
const uiShowsNewCase = await waitFor(async () => {
  const rows = await tableRows(1)
  return rows.some((r) => r[1].includes('UI库内用例-' + TS))
})
ok('右侧列表出现刚建的用例', !!uiShowsNewCase)

// ---------------- ⑤ 从产品导入 ----------------
await p.getByRole('button', { name: '从产品导入', exact: true }).first().click()
await p.waitForTimeout(2500)
const impDlg = p.locator('.el-dialog:visible').filter({ hasText: '从产品导入用例' }).first()
await impDlg.waitFor({ state: 'visible', timeout: 15000 })
const impRows = await impDlg.locator('.el-table__body tbody tr').count()
console.log('  可导入产品用例行数:', impRows)
ok('导入弹窗列出了产品用例候选', impRows > 0)
// 勾第一条
await impDlg.locator('.el-table__body tbody tr').first().locator('.el-checkbox').click()
await p.waitForTimeout(500)
const impResp = await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/caselib/import-to-lib'), { timeout: 30000 }).catch(() => null),
  impDlg.locator('button:has-text("导入选中的")').click()
])
const impStatus = await impResp[0]?.status?.()
const imported = await waitFor(async () => {
  const libCases = (await api('GET', `/zentao/caselib/case-page?libId=${createdLib.id}&pageNo=1&pageSize=10`)).data.list
  return libCases.find((x) => x.fromCaseID > 0)
})
ok('导入接口返回 200 且库里出现来源标记', impStatus === 200 && !!imported,
  `status=${impStatus} ` + JSON.stringify(imported ? { id: imported.id, from: imported.fromCaseID, v: imported.fromCaseVersion } : null))
const uiShowsImport = await waitFor(async () => {
  const rows = await tableRows(1)
  return rows.some((r) => r[1].includes('来自 #'))
})
ok('界面显示「来自 #xx」来源标记', !!uiShowsImport)

// ---------------- ⑥ 删除保护 ----------------
await p.waitForTimeout(500)
const delBtn = p.locator('.el-table').first().locator('tbody tr').filter({ hasText: LIB_NAME }).locator('button:has-text("删除")')
await delBtn.click()
await p.waitForTimeout(800)
await p.locator('.el-message-box__btns button:has-text("确定")').click()
await p.waitForTimeout(2000)
const stillThere = (await api('GET', '/zentao/caselib/list')).data.some((l) => l.name === LIB_NAME)
const errText = await p.locator('.el-message').last().innerText().catch(() => '')
ok('库内有用例时删除被拒绝（库还在）', stillThere, errText.replace(/\s+/g, ' ').slice(0, 60))

// ---------------- 收尾：清库内用例 → 删库 ----------------
for (const item of (await api('GET', `/zentao/caselib/case-page?libId=${createdLib.id}&pageNo=1&pageSize=100`)).data.list) {
  await api('DELETE', '/zentao/testcase/delete?id=' + item.id)
}
const delResp = await api('DELETE', '/zentao/caselib/delete?id=' + createdLib.id)
console.log('  清理：', JSON.stringify(delResp))

console.log('======================================================')
console.log(`  用例库界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
await b.close()
if (FAIL > 0) process.exit(1)

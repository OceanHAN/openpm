// 团队（zt_team）界面检查
//
// 覆盖：/zentao/project 与 /zentao/execution 的「团队」抽屉
//   ① 抽屉渲染：成员行（账号/姓名/角色/加入日期/天数/每天小时/可用工时/受限）
//   ② 可用工时 = 天数 × 每天小时数；合计与列表一致
//   ③ 添加成员（走弹窗）→ 列表多一行、项目上的团队人数跟着变
//   ④ 改一行（角色/天数/小时）→ 立刻保存并重算可用工时
//   ⑤ 移除成员 → 行消失（物理删除，移除后还能再加回来）
//
// 依赖：后端在 127.0.0.1:48080（本机栈或远端栈都行），前端在 http://localhost/
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { login } from './_login.mjs'
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

// 清掉上一次中断留下的临时成员，保证脚本可重复执行
const leftovers = (await api('GET', '/zentao/team/list?root=1&type=project')).data || []
for (const m of leftovers.filter((x) => (x.account || '').startsWith('dev3-'))) {
  await api('DELETE', '/zentao/team/remove-member?id=' + m.id)
}

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
const p = await c.newPage()

// 断言失败时脚本会直接抛异常退出 —— 如果不在退出前关掉浏览器，
// 每个失败的 Playwright 进程都会留下一个 headless Chromium 挂在那里吃 CPU/内存。
const __cleanup = () => { try { b.close() } catch { /* 已经关掉了 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })
p.on('pageerror', (e) => console.log('  [pageerror]', String(e.stack || e).split('\n').slice(0, 3).join(' | ')))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))

await login(p, BASE)
console.log('登录成功')

const waitList = (frag, ms = 60000) =>
  new Promise((resolve) => {
    const handler = (r) => {
      if (r.url().includes(frag)) { p.off('response', handler); resolve(true) }
    }
    p.on('response', handler)
    setTimeout(() => { p.off('response', handler); resolve(false) }, ms)
  })

const readDrawer = async () =>
  p.evaluate(() => {
    const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null)
    if (!d) return { title: '', rows: [], tip: '' }
    const rows = Array.from(d.querySelectorAll('.el-table__body tbody tr')).map((r) =>
      r.innerText.replace(/\s+/g, ' ').trim().slice(0, 120)
    )
    // 「共 N 人，可用工时合计 X 小时」这句在表格外面，只能从整个抽屉的文本里抓
    const text = (d.innerText || '').replace(/\s+/g, ' ')
    return {
      title: (d.querySelector('.el-drawer__title') || {}).innerText?.trim() || '',
      rows,
      tip: (d.querySelector('.el-alert__title') || {}).innerText?.trim() || '',
      summaryTotal: Number((text.match(/可用工时合计\s*([\d.]+)/) || [])[1] || 0),
      summaryMembers: Number((text.match(/共\s*(\d+)\s*人/) || [])[1] || 0)
    }
  })

// ==================== 项目页的团队抽屉 ====================
const arrived = waitList('/zentao/project/page?')
await p.goto(BASE + '/zentao/project', { waitUntil: 'domcontentloaded' })
await arrived
await p.waitForTimeout(2500)

const row = p.locator('.el-table__body tbody tr').filter({ hasText: '禅道迁移一期' }).first()
if ((await row.count()) === 0) throw new Error('项目列表里找不到演示项目「禅道迁移一期」')

const openTeam = async () => {
  const title = p.locator('.el-drawer__title').filter({ hasText: '团队' }).first()
  await row.locator('button:has-text("团队")').first().click()
  try {
    await title.waitFor({ state: 'visible', timeout: 8000 })
  } catch {
    await row.locator('button:has-text("团队")').first().click()
    await title.waitFor({ state: 'visible', timeout: 20000 })
  }
  await p.waitForTimeout(1500)
}
await openTeam()

const initial = await readDrawer()
console.log('① 团队抽屉 :', JSON.stringify(initial))
if (!initial.title.includes('禅道迁移一期')) throw new Error('抽屉标题不对：' + initial.title)
if (!initial.tip.includes('可用工时')) throw new Error('没有说明可用工时的口径：' + initial.tip)
if (initial.rows.length !== 3) throw new Error('演示团队应有 3 人，实际 ' + initial.rows.length)
for (const frag of ['admin', '项目经理', '140', 'tester', '测试', '40']) {
  if (!initial.rows.some((r) => r.includes(frag))) {
    throw new Error('成员行里缺少「' + frag + '」：' + JSON.stringify(initial.rows))
  }
}

// ② 可用工时 = 天数 × 小时（UI 里的数字与接口一致）
const apiList = (await api('GET', '/zentao/team/list?root=1&type=project')).data
const apiTotal = Number((await api('GET', '/zentao/team/total-hours?root=1&type=project')).data)
const uiTotal = initial.summaryTotal
console.log('② 可用工时 :', 'api=' + apiTotal, 'ui=' + uiTotal, 'members=' + JSON.stringify(apiList.map((m) => m.totalHours)))
if (apiTotal !== 285) throw new Error('演示团队可用工时合计应为 285，实际 ' + apiTotal)
if (uiTotal !== apiTotal) throw new Error(`UI 合计(${uiTotal})与接口(${apiTotal})不一致`)

// ③ 添加成员（走弹窗）
await p.locator('.el-drawer:visible button:has-text("添加成员")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '添加成员' }).first().waitFor({ state: 'visible', timeout: 20000 })
// 账号是 el-select + allow-create：填完必须回车才会把输入变成选项值，否则表单里还是空的
const accountInput = p.locator('.el-dialog .el-form-item:has-text("账号") input').first()
await accountInput.fill('dev3-' + TS)
await accountInput.press('Enter')
await p.waitForTimeout(500)
await p.locator('.el-dialog .el-form-item:has-text("可用天数") input').first().fill('2')
await p.locator('.el-dialog .el-form-item:has-text("每天小时") input').first().fill('5')
await p.locator('.el-dialog button:has-text("添加")').first().click()
await p.waitForTimeout(3000)
const afterAdd = await readDrawer()
console.log('③ 添加之后 :', JSON.stringify(afterAdd.rows))
if (afterAdd.rows.length !== 4) throw new Error('添加后应有 4 人，实际 ' + afterAdd.rows.length)
const added = (await api('GET', '/zentao/team/list?root=1&type=project')).data.find((m) => m.account === 'dev3-' + TS)
if (!added) throw new Error('接口里没有新成员 dev3-' + TS)
if (Number(added.totalHours) !== 10) throw new Error('新成员可用工时应为 2×5=10，实际 ' + added.totalHours)
const projAfterAdd = (await api('GET', '/zentao/project/get?id=1')).data
if (Number(projAfterAdd.teamCount) !== 4) throw new Error('项目上的团队人数没跟着变：' + projAfterAdd.teamCount)

// ④ 改一行：天数改成 4 → 可用工时变 20
const editRow = p.locator('.el-drawer:visible .el-table__body tbody tr').filter({ hasText: 'dev3-' + TS }).first()
// 注意：行内第 1 个 input 是「角色」用的 el-select（只读），可用天数在第 5 列
const daysInput = editRow.locator('td').nth(4).locator('input').first()
await daysInput.fill('4')
await daysInput.press('Enter')
await p.waitForTimeout(3000)
const afterEdit = (await api('GET', '/zentao/team/list?root=1&type=project')).data.find((m) => m.account === 'dev3-' + TS)
console.log('④ 改天数之后 :', 'days=' + afterEdit.days, 'hours=' + afterEdit.hours, 'totalHours=' + afterEdit.totalHours)
if (Number(afterEdit.days) !== 4) {
  throw new Error('界面上把天数改成 4 没生效（等了 3 秒仍是 ' + afterEdit.days + '）')
}
if (Number(afterEdit.totalHours) !== Number(afterEdit.days) * Number(afterEdit.hours)) {
  throw new Error('可用工时 ≠ 天数 × 小时数：' + JSON.stringify(afterEdit))
}

// ⑤ 移除成员（物理删除）
const delRow = p.locator('.el-drawer:visible .el-table__body tbody tr').filter({ hasText: 'dev3-' + TS }).first()
await delRow.locator('button:has-text("移除")').first().click()
await p.waitForTimeout(1200)
await p.locator('.el-message-box button:has-text("确定")').first().click()
await p.waitForTimeout(2500)
const afterRemove = await readDrawer()
console.log('⑤ 移除之后 :', afterRemove.rows.length, '行')
if (afterRemove.rows.some((r) => r.includes('dev3-' + TS))) throw new Error('移除后仍能看到该成员')
// 物理删除的证明：还能再加回来（逻辑删除会撞 UNIQUE(root,type,account)）
const readd = await api('POST', '/zentao/team/add-member', { root: 1, type: 'project', account: 'dev3-' + TS, role: '研发', days: 1, hours: 7 })
if (readd.code !== 0) throw new Error('移除后再加同一个人失败（说明不是物理删除）：' + JSON.stringify(readd))
await api('DELETE', '/zentao/team/remove-member?id=' + readd.data)

await p.screenshot({ path: '/tmp/zentao-team.png', fullPage: true })
console.log('截图       : /tmp/zentao-team.png')

// ==================== 执行页的团队抽屉 ====================
await p.locator('.el-drawer__close-btn:visible').first().click()
await p.waitForTimeout(1000)
const execArrived = waitList('/zentao/execution/page?')
await p.goto(BASE + '/zentao/execution', { waitUntil: 'domcontentloaded' })
await execArrived
await p.waitForTimeout(2000)
const execRow = p.locator('.el-table__body tbody tr').filter({ hasText: 'V1.0 迭代' }).first()
if ((await execRow.count()) === 0) throw new Error('执行列表里找不到演示执行「V1.0 迭代」')
await execRow.locator('button:has-text("团队")').first().click()
await p.locator('.el-drawer__title').filter({ hasText: 'V1.0 迭代' }).first().waitFor({ state: 'visible', timeout: 20000 })
await p.waitForTimeout(1500)
const execTeam = await readDrawer()
console.log('⑥ 执行团队 :', JSON.stringify(execTeam.rows))
if (!execTeam.rows.some((r) => r.includes('项目经理')) || !execTeam.rows.some((r) => r.includes('研发'))) {
  throw new Error('执行团队的角色不对（应来自 ownerFields）：' + JSON.stringify(execTeam.rows))
}

console.log('已确认：项目与执行的团队抽屉都能正常打开')
await b.close()

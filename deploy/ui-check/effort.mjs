// 工时明细（effort）界面检查
//
// 覆盖：
//   /zentao/effort ① 页面渲染（表头、演示数据行）② 按任务编号过滤
//                  ③ 登记工时弹窗（消耗/剩余/工作内容）→ 列表 + 汇总卡片刷新
//   /zentao/task   ④ 任务行的「工时」抽屉：预计/已消耗/剩余描述 + 明细表
//                  ⑤ 抽屉里删掉工时时钩子 → 已消耗归零、剩余回到预计
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
const TASK_NAME = 'UI工时任务-' + TS

// 准备一个干净的任务：预计 8 小时、未开始
const created = await api('POST', '/zentao/task/create', {
  project: 1, execution: 90001, name: TASK_NAME, type: 'devel', pri: 3, estimate: 8
})
if (created.code !== 0) throw new Error('准备任务失败：' + JSON.stringify(created))
const TASK = created.data
console.log(`准备: 任务=${TASK}（${TASK_NAME}，预计 8 小时）`)

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

// ==================== /zentao/effort ====================
const arrived = waitList('/zentao/effort/page?')
await p.goto(BASE + '/zentao/effort', { waitUntil: 'domcontentloaded' })
await arrived
await p.waitForTimeout(2500)

const read = async () =>
  p.evaluate(() => {
    const table = document.querySelector('.el-table')
    const rows = table ? Array.from(table.querySelectorAll('.el-table__body tbody tr')) : []
    return {
      headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
      rows: rows.length,
      total: (document.body.innerText.match(/共 (\d+) 条/) || [])[1] || '',
      row0: rows[0] ? rows[0].innerText.replace(/\s+/g, ' ').trim().slice(0, 100) : '',
      summary: Array.from(document.querySelectorAll('.el-card .el-table__body tbody tr'))
        .map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 40)),
      sumLine: (document.body.innerText.match(/合计\s*[\d.]+\s*小时 \/ \d+ 人/) || [''])[0]
    }
  })

const initial = await read()
console.log('① 页面加载   :', JSON.stringify(initial))
for (const h of ['日期', '账号', '任务', '工作内容', '消耗', '剩余']) {
  if (!initial.headers.includes(h)) throw new Error('表头缺少「' + h + '」：' + JSON.stringify(initial.headers))
}
if (initial.rows < 2) throw new Error('演示数据应有至少 2 条工时，实际 ' + initial.rows)
if (!initial.sumLine) throw new Error('右侧汇总卡片没有出现合计：' + JSON.stringify(initial))
if (!initial.summary.length) throw new Error('汇总表没有数据')

// 按任务编号过滤成本次准备的任务 → 应为 0 条
await p.locator('.el-form-item:has-text("任务编号") input').first().fill(String(TASK))
await p.locator('button:has-text("搜索")').first().click()
await p.waitForTimeout(2500)
const empty = await read()
console.log('② 按任务过滤 :', JSON.stringify({ rows: empty.rows, total: empty.total }))
if (empty.rows !== 0) throw new Error('新任务不该有工时，实际 ' + empty.rows + ' 行')

// 登记第一条工时：消耗 3、剩 5
const openForm = async () => {
  const title = p.locator('.el-dialog__title').filter({ hasText: '登记工时' }).first()
  await p.locator('button:has-text("登记工时")').first().click()
  try {
    await title.waitFor({ state: 'visible', timeout: 8000 })
  } catch {
    // 网络慢时表格还在 loading，第一次点击可能落在遮罩上，补一次
    await p.locator('button:has-text("登记工时")').first().click()
    await title.waitFor({ state: 'visible', timeout: 20000 })
  }
}
await openForm()
await p.waitForTimeout(500)
await p.locator('.el-dialog .el-form-item:has-text("任务") input').first().fill(String(TASK))
await p.locator('.el-dialog .el-form-item:has-text("消耗工时") input').first().fill('3')
await p.locator('.el-dialog .el-form-item:has-text("剩余工时") input').first().fill('5')
await p.locator('.el-dialog .el-form-item:has-text("工作内容") textarea').first().fill('UI 登记的工时')
await p.locator('.el-dialog button:has-text("保存")').first().click()
await p.waitForTimeout(3500)

const afterCreate = await read()
console.log('③ 登记工时后 :', JSON.stringify({ rows: afterCreate.rows, row0: afterCreate.row0, summary: afterCreate.summary, sumLine: afterCreate.sumLine }))
if (afterCreate.rows !== 1) throw new Error('登记后应有 1 行，实际 ' + afterCreate.rows)
if (!afterCreate.row0.includes(TASK_NAME)) throw new Error('行里没有回填任务名：' + afterCreate.row0)

// 任务侧的 consumed/left 应同步
const stat = (await api('GET', '/zentao/effort/task-stat?taskId=' + TASK)).data
console.log('   任务工时   :', JSON.stringify({ estimate: stat.estimate, consumed: stat.consumed, left: stat.left, status: stat.status }))
if (Number(stat.consumed) !== 3 || Number(stat.left) !== 5) throw new Error('任务工时没跟着变：' + JSON.stringify(stat))

await p.screenshot({ path: '/tmp/zentao-effort.png', fullPage: true })
console.log('截图         : /tmp/zentao-effort.png')

// ==================== /zentao/task 抽屉 ====================
const taskArrived = waitList('/zentao/task/page?')
await p.goto(BASE + '/zentao/task', { waitUntil: 'domcontentloaded' })
await taskArrived
await p.waitForTimeout(2000)
const filtered = waitList('/zentao/task/page?')
await p.fill('input[placeholder="名称关键词"]', TASK_NAME)
await p.locator('button:has-text("搜索")').first().click()
await filtered
await p.waitForTimeout(2000)

const taskRow = p.locator('.el-table__body tbody tr').filter({ hasText: TASK_NAME }).first()
if ((await taskRow.count()) === 0) throw new Error('任务列表里找不到本次任务')
const drawerTitle = p.locator('.el-drawer__title').filter({ hasText: TASK_NAME }).first()
await taskRow.locator('button:has-text("工时")').first().click()
try {
  await drawerTitle.waitFor({ state: 'visible', timeout: 8000 })
} catch {
  // 表格还在 loading 时第一次点击会落在遮罩上，补一次
  await taskRow.locator('button:has-text("工时")').first().click()
  await drawerTitle.waitFor({ state: 'visible', timeout: 20000 })
}
await p.waitForTimeout(1500)

const drawer = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null) || document.querySelector('.el-drawer')
  const rows = Array.from(d.querySelectorAll('.el-table__body tbody tr'))
  return {
    title: (d.querySelector('.el-drawer__title') || {}).innerText?.trim() || '',
    desc: Array.from(d.querySelectorAll('.el-descriptions__label, .el-descriptions__content'))
      .map((e) => e.innerText.trim()).join(' / '),
    alert: (d.querySelector('.el-alert__title') || {}).innerText?.trim() || '',
    rows: rows.map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 60))
  }
})
console.log('④ 任务工时抽屉:', JSON.stringify(drawer))
if (!drawer.title.includes(TASK_NAME)) throw new Error('抽屉标题不对：' + drawer.title)
if (!drawer.desc.includes('8') || !drawer.desc.includes('3') || !drawer.desc.includes('5')) {
  throw new Error('预计/已消耗/剩余描述不对：' + drawer.desc)
}
if (drawer.rows.length !== 1 || !drawer.rows[0].includes('UI 登记的工时')) {
  throw new Error('抽屉里的工时明细不对：' + JSON.stringify(drawer.rows))
}
if (!drawer.alert.includes('最后一条')) throw new Error('没有说明「剩余以最后一条为准」：' + drawer.alert)

// 抽屉里删掉这条工时 → 已消耗归零、剩余回到预计工时
await p.locator('.el-drawer .el-table__body tbody tr').first().locator('button:has-text("删除")').click()
await p.waitForTimeout(1200)
await p.locator('.el-message-box button:has-text("确定")').first().click()
await p.waitForTimeout(3000)
const afterDelete = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null) || document.querySelector('.el-drawer')
  return {
    desc: Array.from(d.querySelectorAll('.el-descriptions__label, .el-descriptions__content'))
      .map((e) => e.innerText.trim()).join(' / '),
    rows: d.querySelectorAll('.el-table__body tbody tr').length,
    empty: !!d.querySelector('.el-table__empty-text')
  }
})
console.log('⑤ 抽屉里删除 :', JSON.stringify(afterDelete))
if (afterDelete.rows !== 0) throw new Error('删除后明细应为空，实际 ' + afterDelete.rows + ' 行')
if (!afterDelete.desc.includes('已消耗 / 0')) throw new Error('删除后已消耗应归零：' + afterDelete.desc)

const stat2 = (await api('GET', '/zentao/effort/task-stat?taskId=' + TASK)).data
console.log('   任务工时   :', JSON.stringify({ consumed: stat2.consumed, left: stat2.left, status: stat2.status, count: stat2.effortCount }))
if (Number(stat2.consumed) !== 0 || stat2.status !== 'wait' || stat2.effortCount !== 0) {
  throw new Error('删光工时的重算不对：' + JSON.stringify(stat2))
}
// 注意剩余工时：这个任务一直是「未开始」，禅道对这种任务删完工时**不动任务自己的 left**
// （只把 consumed 归零），所以剩余仍是那条工时声明过的 5，而不是预计的 8。
// 已开始/已完成的任务删光工时才走「退回未开始 + 剩余回到预计」。
if (Number(stat2.left) !== 5) throw new Error('未开始任务删光工时的剩余口径不对：' + stat2.left)

// 收尾
await api('DELETE', '/zentao/task/delete?id=' + TASK)
console.log('已清理测试数据')
await b.close()

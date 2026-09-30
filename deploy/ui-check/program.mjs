// 项目集（program）界面检查
//
// 覆盖：/zentao/program
//   ① 页面渲染：项目集树（9001 + 子项目集 9002）、层级/path、统计列
//   ② 详情抽屉：项目 Tab（2 个项目）与产品 Tab（1 个产品）
//   ③ 新建弹窗：字段齐全 + 「三个常量指向同一张表」的说明
//   ④ 新建的项目集出现在树里（走 API 建、页面上认）
// 另外顺带检查 /zentao/project 新增的「所属项目集」列与筛选项。
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
const NEW_NAME = 'UI项目集-' + TS

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

// ==================== /zentao/program ====================
const arrived = waitList('/zentao/program/list')
await p.goto(BASE + '/zentao/program', { waitUntil: 'domcontentloaded' })
await arrived
await p.waitForTimeout(2500)

const read = async () =>
  p.evaluate(() => {
    const table = document.querySelector('.el-table')
    const rows = table ? Array.from(table.querySelectorAll('.el-table__body tbody tr')) : []
    return {
      headers: Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim()).filter(Boolean),
      rows: rows.map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 110)),
      alert: (document.querySelector('.el-alert__description') || {}).innerText?.trim().slice(0, 120) || ''
    }
  })

const initial = await read()
console.log('① 页面加载 :', JSON.stringify(initial))
for (const h of ['项目集', '层级', 'path', '下级项目集', '项目', '产品']) {
  if (!initial.headers.includes(h)) throw new Error('表头缺少「' + h + '」：' + JSON.stringify(initial.headers))
}
if (initial.rows.length < 2) throw new Error('项目集树应至少有 2 行（9001 + 子项目集 9002），实际 ' + initial.rows.length)
if (!initial.rows.some((r) => r.includes('禅道迁移项目集') && r.includes(',9001,'))) {
  throw new Error('没有渲染出演示项目集 9001 及其 path：' + JSON.stringify(initial.rows))
}
if (!initial.alert.includes('TABLE_PROGRAM')) throw new Error('说明文案没提到三个常量共表：' + initial.alert)

// 详情抽屉：项目 / 产品
const openDetail = async () => {
  const row = p.locator('.el-table__body tbody tr').filter({ hasText: '禅道迁移项目集' }).first()
  const title = p.locator('.el-drawer__title').filter({ hasText: '禅道迁移项目集' }).first()
  await row.locator('button:has-text("详情")').first().click()
  try {
    await title.waitFor({ state: 'visible', timeout: 8000 })
  } catch {
    // 表格还在 loading 时第一次点击会落在遮罩上，补一次
    await row.locator('button:has-text("详情")').first().click()
    await title.waitFor({ state: 'visible', timeout: 20000 })
  }
  await p.waitForTimeout(1500)
}
await openDetail()
const detail = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null) || document.querySelector('.el-drawer')
  const pane = Array.from(d.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
  const rows = pane ? Array.from(pane.querySelectorAll('.el-table__body tbody tr')) : []
  return {
    title: (d.querySelector('.el-drawer__title') || {}).innerText?.trim() || '',
    desc: Array.from(d.querySelectorAll('.el-descriptions__label, .el-descriptions__content')).map((e) => e.innerText.trim()).join(' / '),
    tabs: Array.from(d.querySelectorAll('.el-tabs__item')).map((e) => e.innerText.trim()),
    rows: rows.map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 70))
  }
})
console.log('② 详情抽屉 :', JSON.stringify(detail))
if (!detail.title.includes('禅道迁移项目集')) throw new Error('抽屉标题不对：' + detail.title)
if (!detail.desc.includes(',9001,')) throw new Error('抽屉里没有 path：' + detail.desc)
if (detail.tabs.join('|') !== '项目|产品') throw new Error('两个 Tab 不对：' + detail.tabs.join('|'))
if (detail.rows.length !== 2) throw new Error('项目集下应有 2 个项目，实际 ' + detail.rows.length)

await p.locator('.el-drawer .el-tabs__item').filter({ hasText: '产品' }).first().click()
await p.waitForTimeout(1500)
const productRows = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null) || document.querySelector('.el-drawer')
  const pane = Array.from(d.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
  return pane ? Array.from(pane.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 60)) : []
})
console.log('   产品 Tab  :', JSON.stringify(productRows))
if (productRows.length !== 1) throw new Error('项目集下应有 1 个产品，实际 ' + productRows.length)

await p.screenshot({ path: '/tmp/zentao-program.png', fullPage: true })
console.log('截图       : /tmp/zentao-program.png')
// 页面上同时存在多个 drawer 的关闭按钮（隐藏的也在 DOM 里），必须取可见的那个
await p.locator('.el-drawer__close-btn:visible').first().click()
await p.waitForTimeout(1200)

// 新建弹窗：字段齐全
const openCreateDialog = async () => {
  const title = p.locator('.el-dialog__title').filter({ hasText: '新建项目集' }).first()
  await p.locator('button:has-text("新建项目集")').first().click()
  try {
    await title.waitFor({ state: 'visible', timeout: 8000 })
  } catch {
    await p.locator('button:has-text("新建项目集")').first().click()
    await title.waitFor({ state: 'visible', timeout: 20000 })
  }
  await p.waitForTimeout(800)
}
await openCreateDialog()
const dialog = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  if (!d) return { title: '', labels: [], alert: '' }
  return {
    title: (d.querySelector('.el-dialog__title') || {}).innerText?.trim() || '',
    labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.trim()),
    alert: (d.querySelector('.el-alert__description') || {}).innerText?.trim().slice(0, 80) || ''
  }
})
console.log('③ 新建弹窗 :', JSON.stringify(dialog))
if (!dialog.title.includes('新建项目集')) throw new Error('弹窗标题不对：' + dialog.title)
for (const l of ['上级项目集', '名称', '负责人', '计划起止', '预算']) {
  if (!dialog.labels.includes(l)) throw new Error('弹窗缺少字段「' + l + '」：' + JSON.stringify(dialog.labels))
}
await p.locator('.el-dialog__headerbtn:visible').first().click()
await p.waitForTimeout(1000)

// ==================== 新建的项目集要出现在树里 ====================
const created = await api('POST', '/zentao/program/create', {
  parent: 9001, name: NEW_NAME, code: 'UI-' + TS, PM: 'admin',
  begin: '2026-02-01', end: '2026-08-31'
})
if (created.code !== 0) throw new Error('准备项目集失败：' + JSON.stringify(created))
const NEW_ID = created.data
const reload = waitList('/zentao/program/list')
await p.goto(BASE + '/zentao/program', { waitUntil: 'domcontentloaded' })
await reload
await p.waitForTimeout(2500)
const after = await read()
console.log('④ 新建后   :', JSON.stringify(after.rows))
if (!after.rows.some((r) => r.includes(NEW_NAME))) throw new Error('新建的项目集没有出现在树里')
if (!after.rows.some((r) => r.includes(NEW_NAME) && r.includes(',9001,' + NEW_ID + ','))) {
  throw new Error('新建项目集的 path 不对（应为 ,9001,' + NEW_ID + ',）')
}

// ==================== 项目页的「所属项目集」列 ====================
const projArrived = waitList('/zentao/project/page?')
await p.goto(BASE + '/zentao/project', { waitUntil: 'domcontentloaded' })
await projArrived
await p.waitForTimeout(2000)
const projPage = await p.evaluate(() => {
  const headers = Array.from(document.querySelectorAll('.el-table__header th')).map((e) => e.innerText.trim())
  const rows = Array.from(document.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 90))
  return { headers, rows }
})
console.log('⑤ 项目页列 :', JSON.stringify(projPage.headers))
if (!projPage.headers.includes('所属项目集')) throw new Error('项目列表没有「所属项目集」列：' + JSON.stringify(projPage.headers))
if (!projPage.rows.some((r) => r.includes('禅道迁移项目集'))) {
  throw new Error('项目行里没有回填所属项目集名称：' + JSON.stringify(projPage.rows))
}

// 收尾
await api('DELETE', '/zentao/program/delete?id=' + NEW_ID)
console.log('已清理测试数据')
await b.close()

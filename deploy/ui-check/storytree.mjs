// 父子需求（需求分解）界面检查
//
// 覆盖：需求列表的「父 / 父需求已变更」标记、详情抽屉的「子需求」Tab、分解弹窗
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
// 准备：一个父需求 + 两条子需求（用接口造，界面只负责展示与分解）
const parent = await api('POST', '/zentao/story/create', {
  product: 1, title: 'UI父需求-' + TS, type: 'story', category: 'feature', pri: 1
})
if (parent.code !== 0) throw new Error('准备父需求失败：' + JSON.stringify(parent))
const child = await api('POST', '/zentao/story/create', {
  product: 1, title: 'UI子需求-' + TS, type: 'story', category: 'feature', pri: 2, parent: parent.data
})
if (child.code !== 0) throw new Error('准备子需求失败：' + JSON.stringify(child))
console.log(`准备: 父=${parent.data}（已带 1 个子需求 ${child.data}）`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1680, height: 1050 } })
const p = await c.newPage()
p.on('pageerror', (e) => console.log('  [pageerror]', String(e.stack || e).split('\n').slice(0, 3).join(' | ')))
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })

await login(p, BASE)
console.log('登录成功')

await p.goto(BASE + '/zentao/story', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// 用搜索框把演示数据收窄到本次造的父需求
await p.fill('input[placeholder="请输入标题关键词"]', 'UI父需求-' + TS)
await p.locator('button:has-text("搜索")').first().click()
await p.waitForTimeout(2500)

const listInfo = await p.evaluate(() => {
  const rows = Array.from(document.querySelectorAll('.el-table__body tbody tr'))
  return {
    rows: rows.length,
    row0: rows[0] ? rows[0].innerText.replace(/\s+/g, ' ').trim().slice(0, 80) : '',
    tags: Array.from(document.querySelectorAll('.el-table__body .el-tag')).map((e) => e.innerText.trim())
  }
})
console.log('① 列表行与标记:', JSON.stringify(listInfo))
if (listInfo.rows !== 1) throw new Error('预期筛出 1 行，实际 ' + listInfo.rows)
if (!listInfo.tags.includes('父')) throw new Error('列表里没有「父」标记：' + JSON.stringify(listInfo.tags))

// 打开详情抽屉 → 子需求 Tab
await p.locator('.el-table__body tbody tr').first().locator('.el-link').first().click()
await p.waitForTimeout(3000)
const tabs = await p.evaluate(() =>
  Array.from(document.querySelectorAll('.el-drawer .el-tabs__item')).map((e) => e.innerText.trim())
)
console.log('② 抽屉页签:', JSON.stringify(tabs))
if (!tabs.some((t) => t.includes('子需求'))) throw new Error('抽屉里没有「子需求」Tab')

await p.locator('.el-drawer .el-tabs__item').filter({ hasText: '子需求' }).first().click()
await p.waitForTimeout(2500)
const readChild = async () =>
  p.evaluate(() => {
    const drawer = document.querySelector('.el-drawer')
    const pane = Array.from(drawer.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
    const rows = pane ? Array.from(pane.querySelectorAll('.el-table__body tbody tr')) : []
    return {
      rows: rows.map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 60)),
      hasAlert: !!pane?.querySelector('.el-alert')
    }
  })
const before = await readChild()
console.log('③ 子需求 Tab（分解前）:', JSON.stringify(before))
if (before.rows.length !== 1) throw new Error('预期 1 条子需求，实际 ' + before.rows.length)

// 分解弹窗：一次拆两条
// 注意：页面上同时存在多个 el-dialog（新建需求表单也是 dialog，只是没显示），
// `.el-dialog textarea` 会抓到**隐藏的那个**，文字填进去等于没填（前端会提示标题为空）。
// 所以必须限定到「可见的、标题是分解子需求的那个弹窗」。
const decomposeCalls = []
p.on('response', (r) => { if (r.url().includes('/batch-create-child')) decomposeCalls.push(r.status()) })
await p.locator('.el-drawer button:has-text("分解子需求")').first().click()
await p.waitForTimeout(1500)
const dlg = p.locator('.el-dialog:visible').filter({ hasText: '分解子需求' }).first()
await dlg.waitFor({ state: 'visible', timeout: 15000 })
const titles = `UI拆分A-${TS}\nUI拆分B-${TS}`
await dlg.locator('textarea').first().fill(titles)
const filled = await dlg.locator('textarea').first().inputValue()
if (filled !== titles) throw new Error('分解弹窗里的标题没填进去：' + JSON.stringify(filled))
// 点确定的同时等接口，别用固定 sleep（弹窗有动画，点早了按钮还没生效）
const [resp] = await Promise.all([
  p.waitForResponse((r) => r.url().includes('batch-create-child'), { timeout: 30000 }).catch(() => null),
  dlg.locator('.el-dialog__footer button:has-text("确定")').first().click()
])
if (!resp) {
  const toast = await p.evaluate(() => document.querySelector('.el-message')?.innerText || '(无提示)')
  throw new Error('点击确定后没有发出 batch-create-child 请求，页面提示：' + toast)
}
console.log('   分解接口返回:', resp.status())
// 等列表真的变成 3 行再断言：固定 sleep 在这台机器上不稳
// （远端 Redis/MySQL 往返一慢，刷新就会晚于 sleep）
await p.waitForFunction(
  () => {
    const drawer = document.querySelector('.el-drawer')
    const pane = Array.from(drawer.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
    const rows = pane ? pane.querySelectorAll('.el-table__body tbody tr') : []
    return rows.length === 3
  },
  { timeout: 30000 }
).catch(() => {})
const after = await readChild()
console.log('④ 子需求 Tab（分解后）:', JSON.stringify(after))
if (after.rows.length !== 3) throw new Error('分解后预期 3 条子需求，实际 ' + after.rows.length)
await p.screenshot({ path: '/tmp/zentao-storytree.png', fullPage: true })
console.log('截图: /tmp/zentao-storytree.png')

// ⑤ 需求转任务：详情抽屉里的「任务」Tab
await p.locator('.el-drawer .el-tabs__item').filter({ hasText: '任务 (' }).first().click()
await p.waitForTimeout(2000)
const taskTab = await p.evaluate(() => {
  const drawer = document.querySelector('.el-drawer')
  const pane = Array.from(drawer.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
  return {
    rows: pane ? pane.querySelectorAll('.el-table__body tbody tr').length : -1,
    hasAlert: !!pane?.querySelector('.el-alert'),
    hasButton: !!pane && Array.from(pane.querySelectorAll('button')).some((b) => b.innerText.includes('需求转任务'))
  }
})
console.log('⑤ 任务 Tab:', JSON.stringify(taskTab))
if (!taskTab.hasButton) throw new Error('任务 Tab 里没有「需求转任务」按钮')

const taskResp = []
p.on('response', (r) => { if (r.url().includes('/task/batch-create-from-story')) taskResp.push(r.status()) })
await p.locator('.el-drawer button:has-text("需求转任务")').first().click()
await p.waitForTimeout(1500)
const taskDlg = p.locator('.el-dialog:visible').filter({ hasText: '需求转任务' }).first()
await taskDlg.waitFor({ state: 'visible', timeout: 15000 })
await taskDlg.locator('textarea').first().fill(`UI任务A-${TS}\nUI任务B-${TS}`)
await Promise.all([
  p.waitForResponse((r) => r.url().includes('/task/batch-create-from-story'), { timeout: 30000 }).catch(() => null),
  taskDlg.locator('.el-dialog__footer button:has-text("确定")').first().click()
])
console.log('   转任务接口返回:', JSON.stringify(taskResp))
if (!taskResp.length) throw new Error('没有调用 batch-create-from-story')
await p.waitForFunction(
  () => {
    const drawer = document.querySelector('.el-drawer')
    const pane = Array.from(drawer.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
    return pane && pane.querySelectorAll('.el-table__body tbody tr').length === 2
  },
  { timeout: 30000 }
).catch(() => {})

// 收尾：删掉任务、子需求再删父需求
const children = await api('GET', '/zentao/story/child-list?parentId=' + parent.data)
for (const item of children.data) await api('DELETE', '/zentao/story/delete?id=' + item.id)
await api('DELETE', '/zentao/story/delete?id=' + parent.data)
console.log('已清理测试数据')
await b.close()

// 干系人抽屉界面检查（项目集页 / 项目页）
//
// 覆盖：
//   ① 项目集页「干系人」按钮能打开抽屉，列表渲染出 关键标记 / 内部外部 / 来源
//   ② 走弹窗添加一个**外部干系人**（from=outside → type=outside）
//   ③ 移除它，列表复原
// 数据准备与清理走后端接口，脚本可重复执行。
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { login } from './_login.mjs'
const _pw = await import(pathToFileURL(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim() + '/playwright/index.js').href)
const chromium = _pw.chromium || _pw.default?.chromium
const BASE = process.env.ZENTAO_UI_BASE || 'http://localhost'
const API_BASE = process.env.ZENTAO_API_BASE || 'http://localhost:48080/admin-api'

const loginResp = await fetch(`${API_BASE}/system/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json', 'tenant-id': '1' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
}).then((r) => r.json())
const tok = loginResp.data.accessToken
const api = async (m, u, b) => (await fetch(API_BASE + u, {
  method: m, headers: { Authorization: 'Bearer ' + tok, 'tenant-id': '1', 'Content-Type': 'application/json' },
  body: b ? JSON.stringify(b) : undefined
})).json()

const TS = Date.now()
const NAME = `UI外部干系人-${TS}`
// 清掉上次中断留下的
const old = (await api('GET', '/zentao/stakeholder/list?objectType=program&objectID=9001')).data || []
for (const s of old.filter((x) => (x.user || '').startsWith('UI外部干系人-'))) {
  await api('DELETE', '/zentao/stakeholder/delete?id=' + s.id)
}

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
const p = await c.newPage()
const __cleanup = () => { try { b.close() } catch { /* 已经关掉了 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })
p.on('pageerror', (e) => console.log('  [pageerror]', String(e.stack || e).split('\n').slice(0, 3).join(' | ')))

await login(p, BASE)
console.log('登录成功')

const waitList = (frag, ms = 60000) =>
  new Promise((resolve) => {
    const h = (r) => { if (r.url().includes(frag)) { p.off('response', h); resolve(true) } }
    p.on('response', h); setTimeout(() => { p.off('response', h); resolve(false) }, ms)
  })

const readDrawer = async () =>
  p.evaluate(() => {
    const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null)
    if (!d) return { title: '', rows: [], tip: '' }
    return {
      title: (d.querySelector('.el-drawer__title') || {}).innerText?.trim() || '',
      tip: (d.querySelector('.el-alert__title') || {}).innerText?.trim() || '',
      rows: Array.from(d.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 90))
    }
  })

const arrived = waitList('/zentao/program/list')
await p.goto(BASE + '/zentao/program', { waitUntil: 'domcontentloaded' })
await arrived
await p.waitForTimeout(2500)

const row = p.locator('.el-table__body tbody tr').filter({ hasText: '禅道迁移项目集' }).first()
if ((await row.count()) === 0) throw new Error('找不到演示项目集')
const title = p.locator('.el-drawer__title').filter({ hasText: '禅道迁移项目集' }).first()
await row.locator('button:has-text("干系人")').first().click()
try {
  await title.waitFor({ state: 'visible', timeout: 8000 })
} catch {
  await row.locator('button:has-text("干系人")').first().click()
  await title.waitFor({ state: 'visible', timeout: 20000 })
}
await p.waitForTimeout(1500)

const initial = await readDrawer()
console.log('① 干系人抽屉 :', JSON.stringify(initial))
if (!initial.title.includes('禅道迁移项目集')) throw new Error('抽屉标题不对：' + initial.title)
if (!initial.tip.includes('不是团队成员')) throw new Error('没有说明「干系人不是团队成员」：' + initial.tip)
if (initial.rows.length !== 3) throw new Error('演示干系人应有 3 个，实际 ' + initial.rows.length)
if (!initial.rows.some((r) => r.includes('关键') && r.includes('外部') && r.includes('张三'))) {
  throw new Error('没有渲染出「张三（甲方）外部 + 关键」：' + JSON.stringify(initial.rows))
}

// ② 添加外部干系人
await p.locator('.el-drawer:visible button:has-text("添加干系人")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '添加干系人' }).first().waitFor({ state: 'visible', timeout: 20000 })
// 来源改成「外部人员」→ 变成文本输入
await p.locator('.el-dialog .el-form-item:has-text("来源") .el-select').first().click()
await p.waitForTimeout(600)
await p.locator('.el-select-dropdown__item:visible').filter({ hasText: '外部人员' }).first().click()
await p.waitForTimeout(600)
await p.locator('.el-dialog .el-form-item:has-text("干系人") input').first().fill(NAME)
await p.locator('.el-dialog button:has-text("添加")').first().click()
await p.waitForTimeout(3000)
const afterAdd = await readDrawer()
console.log('② 添加之后   :', JSON.stringify(afterAdd.rows))
if (afterAdd.rows.length !== 4) throw new Error('添加后应有 4 个，实际 ' + afterAdd.rows.length)
const added = ((await api('GET', '/zentao/stakeholder/list?objectType=program&objectID=9001')).data || [])
  .find((x) => x.user === NAME)
if (!added) throw new Error('接口里没有新干系人 ' + NAME)
if (added.type !== 'outside') throw new Error('外部来源的 type 应为 outside，实际 ' + added.type)

// ③ 移除
const delRow = p.locator('.el-drawer:visible .el-table__body tbody tr').filter({ hasText: NAME }).first()
await delRow.locator('button:has-text("移除")').first().click()
await p.waitForTimeout(1000)
await p.locator('.el-message-box button:has-text("确定")').first().click()
await p.waitForTimeout(2500)
const afterRemove = await readDrawer()
console.log('③ 移除之后   :', afterRemove.rows.length, '行')
if (afterRemove.rows.some((r) => r.includes(NAME))) throw new Error('移除后仍在列表里')

await p.screenshot({ path: '/tmp/zentao-stakeholder.png', fullPage: true })
console.log('截图         : /tmp/zentao-stakeholder.png')
console.log('已确认：干系人抽屉的渲染 / 添加（外部）/ 移除都正常')
await b.close()

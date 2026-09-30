// 操作日志（回收站 / 动态 / 备注）界面检查
//
// 覆盖 /zentao/action：
//   ① 回收站列表渲染（对象类型中文名 / 对象名 / 删除人 / 能否还原）
//   ② 还原：先删一条需求 → 回收站里出现 → 点还原 → 库里 deleted=0 且需求能查到
//   ③ 隐藏：隐藏后回收站里不再出现该条（对象仍是删除状态）
//   ④ 动态 tab：渲染「谁 + 动作 + 字段变化」的渲染文本
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
const TITLE = 'UI回收站需求-' + TS

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
const p = await c.newPage()
p.on('pageerror', (e) => console.log('  [pageerror]', String(e.stack || e).split('\n').slice(0, 3).join(' | ')))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })

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

await login(p, BASE)
console.log('登录成功')

// 准备：建一条需求并删掉（删除会记一条 deleted 动作，于是它会出现在回收站）
const story = await api('POST', '/zentao/story/create', { product: 1, title: TITLE, type: 'story', pri: 3 })
if (story.code !== 0) throw new Error('准备需求失败：' + JSON.stringify(story))
const storyId = story.data
await api('DELETE', '/zentao/story/delete?id=' + storyId)
console.log(`准备: 需求 ${storyId} 已删除`)

await p.goto(BASE + '/zentao/action', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 回收站列表 ----------------
await p.fill('input[placeholder="账号"]', 'admin')
await p.locator('button:has-text("查询")').first().click()
await p.waitForTimeout(2500)
const rows = await tableRows(0)
console.log('  回收站前 2 行:', JSON.stringify(rows.slice(0, 2)))
ok('回收站列表渲染', rows.length > 0, `行数=${rows.length}`)
ok('列表带对象类型与删除人', rows[0].length >= 5, JSON.stringify(rows[0]))
const targetRow = p.locator('.el-table').first().locator('tbody tr').filter({ hasText: TITLE }).first()
ok('刚删的需求出现在回收站里', (await targetRow.count()) > 0, TITLE)

// ---------------- ② 还原 ----------------
const undeleteResp = await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/action/undelete'), { timeout: 30000 }).catch(() => null),
  (async () => {
    await targetRow.locator('button:has-text("还原")').first().click()
    await p.waitForTimeout(600)
    await p.locator('.el-message-box__btns button:has-text("确定")').click()
  })()
])
await p.waitForTimeout(2500)
ok('还原接口返回 200', undeleteResp[0]?.status() === 200, `status=${undeleteResp[0]?.status?.()}`)
const restored = await api('GET', '/zentao/story/get?id=' + storyId)
ok('还原后需求又能查到（deleted=0）', restored.code === 0 && restored.data?.title === TITLE,
  JSON.stringify(restored.data ? { id: restored.data.id, title: restored.data.title } : restored))
// 禅道语义：还原不会删掉那条「删除」动作（回收站里仍在，但不能再次还原）——
// 与 ZenTao getTrashes 一致：只要 extra=canUndelete 就一直列出来，由 checkActionCanUndelete 决定按钮状态
await p.locator('button:has-text("查询")').first().click()
await p.waitForTimeout(2000)
const rowAfter = p.locator('.el-table').first().locator('tbody tr').filter({ hasText: TITLE }).first()
const undeleteDisabled = await rowAfter.locator('button:has-text("还原")').first().isDisabled().catch(() => null)
ok('还原后那条记录仍在回收站，但「还原」按钮禁用', undeleteDisabled === true, `disabled=${undeleteDisabled}`)

// ---------------- ③ 隐藏 ----------------
await api('DELETE', '/zentao/story/delete?id=' + storyId)
await p.locator('button:has-text("查询")').first().click()
await p.waitForTimeout(2000)
const row2 = p.locator('.el-table').first().locator('tbody tr').filter({ hasText: TITLE }).first()
ok('再次删除后又出现在回收站', (await row2.count()) > 0)
await row2.locator('button:has-text("隐藏")').first().click()
await p.waitForTimeout(600)
await p.locator('.el-message-box__btns button:has-text("确定")').click()
await p.waitForTimeout(2500)
// 隐藏：按「编号」精确定位那条记录（同一对象可能有多条删除记录），断言它从列表里消失
const hiddenIds = await waitFor(async () => {
  await p.locator('button:has-text("查询")').first().click()
  await p.waitForTimeout(1500)
  const list = await tableRows(0)
  const ids = list.filter((r) => JSON.stringify(r).includes(TITLE)).map((r) => r[0])
  return ids.length === 1 ? ids : null
})
ok('隐藏后只剩一条删除记录（被隐藏的那条消失了）', !!hiddenIds, JSON.stringify(hiddenIds))
const stillDeleted = await api('GET', '/zentao/story/get?id=' + storyId)
ok('隐藏不改变对象的删除状态', stillDeleted.code !== 0, JSON.stringify({ code: stillDeleted.code, msg: stillDeleted.msg }))

// ---------------- ④ 动态 ----------------
await p.locator('.el-tabs__item:has-text("动态")').first().click()
await p.waitForTimeout(3000)
const rowsHtml = await p.evaluate(() =>
  Array.from(document.querySelectorAll('.action-row')).map((el) => el.innerText.replace(/\s+/g, ' ').trim())
)
console.log('  动态前 2 条:', JSON.stringify(rowsHtml.slice(0, 2)))
ok('动态列表渲染', rowsHtml.length > 0, `条数=${rowsHtml.length}`)
ok('动态里是渲染好的「谁 + 动作」文本', rowsHtml.some((t) => /admin\s*(创建|删除|编辑)/.test(t)),
  rowsHtml[0]?.slice(0, 60))
// 切到「全部」周期应当能查到编辑过的动态（带字段变化标签）
// 注意限定到「可见的页签」：回收站页签里那个 el-select 还在 DOM 里（隐藏）
await p.locator('.el-tab-pane:visible .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '全部' }).first().click()
await p.waitForTimeout(2500)
const allRows = await p.evaluate(() =>
  Array.from(document.querySelectorAll('.action-row')).map((el) => el.innerText.replace(/\s+/g, ' ').trim())
)
ok('「全部」周期动态更多或相同', allRows.length >= rowsHtml.length, `${rowsHtml.length} → ${allRows.length}`)
const hasHistoryTag = await p.locator('.action-row-histories .el-tag').count()
ok('字段变化渲染成标签（编辑类动态）', hasHistoryTag > 0, `标签数=${hasHistoryTag}`)

// ---------------- 收尾 ----------------
await api('POST', '/zentao/action/hide-all')
console.log('  清理完成（测试删除的需求保持删除状态，并从回收站隐藏）')

console.log('======================================================')
console.log(`  action 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
await b.close()
if (FAIL > 0) process.exit(1)

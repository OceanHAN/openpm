// 看板（kanban）界面检查
//
// 覆盖 /zentao/kanban：
//   ① 空间 → 看板下拉联动，默认选中演示看板
//   ② 看板网格渲染：列头（含「卡片数 / 在制品上限」）、泳道行、卡片位置
//   ③ 新建看板 → 自动出现「默认区域 + 默认泳道 + 未开始/进行中/已完成/已关闭」
//   ④ 单元格里「+ 卡片」建卡片 → 卡片出现在该格
//   ⑤ 卡片详情里「移动」到另一列 → 网格里位置跟着变
//   ⑥ 卡片详情里「完成」→ 卡片带已完成样式（status=done）
//   ⑦ 跑完清理：删卡片、删看板
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
const KB_NAME = 'UI看板-' + TS

// 清掉上次中断留下的临时看板
for (const k of (await api('GET', '/zentao/kanban/list?space=96001')).data || []) {
  if ((k.name || '').startsWith('UI看板-')) await api('DELETE', '/zentao/kanban/delete?id=' + k.id)
}

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1900, height: 1200 } })
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

await login(p, BASE)
console.log('登录成功')

await p.goto(BASE + '/zentao/kanban', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 演示看板渲染 ----------------
const headText = await p.evaluate(() => {
  const heads = Array.from(document.querySelectorAll('.kb-col-head')).map((el) => el.innerText.replace(/\s+/g, ' ').trim())
  const lanes = Array.from(document.querySelectorAll('.kb-lane-cell')).map((el) => el.innerText.replace(/\s+/g, ' ').trim())
  const cards = Array.from(document.querySelectorAll('.kb-card')).map((el) => el.innerText.replace(/\s+/g, ' ').trim())
  return { heads, lanes, cards }
})
console.log('  列头:', JSON.stringify(headText.heads))
console.log('  泳道:', JSON.stringify(headText.lanes))
ok('列头渲染（4 列 + 卡片数/上限）',
  headText.heads.length === 4 && headText.heads[0].includes('未开始') && headText.heads[1].includes('进行中'),
  headText.heads.join(' | '))
ok('进行中列显示 WIP 上限 3', headText.heads[1].includes('3'), headText.heads[1])
ok('泳道渲染（默认泳道 / 需求泳道）',
  headText.lanes.some((t) => t.includes('默认泳道')) && headText.lanes.some((t) => t.includes('需求泳道')))
ok('卡片渲染（2 张演示卡片）',
  headText.cards.some((t) => t.includes('打通登录链路')) && headText.cards.some((t) => t.includes('需求池分层视图')),
  JSON.stringify(headText.cards))

// ---------------- ② 新建看板 → 默认布局 ----------------
await p.getByRole('button', { name: '新建看板', exact: true }).first().click()
await p.waitForTimeout(1200)
const kbDlg = p.locator('.el-dialog:visible').filter({ hasText: '新建看板' }).first()
await kbDlg.waitFor({ state: 'visible', timeout: 15000 })
await kbDlg.locator('input[placeholder="看板名称"]').fill(KB_NAME)
const kbResp = await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/kanban/create'), { timeout: 30000 }).catch(() => null),
  kbDlg.locator('.el-dialog__footer button:has-text("确 定")').click()
])
await p.waitForTimeout(3000)
const created = (await api('GET', '/zentao/kanban/list?space=96001')).data.find((k) => k.name === KB_NAME)
ok('界面新建看板成功', !!created && kbResp[0]?.status() === 200, JSON.stringify(created ? { id: created.id } : created))

const newBoard = await waitFor(async () => {
  const d = await p.evaluate(() => {
    const heads = Array.from(document.querySelectorAll('.kb-col-head')).map((el) => el.innerText.replace(/\s+/g, ' ').trim())
    const lanes = Array.from(document.querySelectorAll('.kb-lane-cell')).map((el) => el.innerText.replace(/\s+/g, ' ').trim())
    return { heads, lanes }
  })
  return d.heads.length === 4 ? d : null
})
ok('新看板自动带默认布局（4 列 + 默认泳道）',
  !!newBoard && newBoard.heads.every((h) => ['未开始', '进行中', '已完成', '已关闭'].some((n) => h.includes(n))) &&
  newBoard.lanes.some((l) => l.includes('默认泳道')),
  newBoard ? newBoard.heads.join(' | ') : 'null')
ok('新看板的列默认不限（∞）', !!newBoard && newBoard.heads.every((h) => h.includes('∞') || h.includes('/ 0')), newBoard?.heads.join(' | '))

// ---------------- ③ 单元格「+ 卡片」建卡片 ----------------
// 点第二个单元格（进行中列）的「+ 卡片」
const plusButtons = p.locator('.kb-cell button:has-text("+ 卡片")')
const plusCount = await plusButtons.count()
ok('每个单元格都有「+ 卡片」入口', plusCount >= 4, `按钮数=${plusCount}`)
await plusButtons.nth(1).click()
await p.waitForTimeout(1200)
const cardDlg = p.locator('.el-dialog:visible').filter({ hasText: '新建卡片' }).first()
await cardDlg.waitFor({ state: 'visible', timeout: 15000 })
await cardDlg.locator('input[placeholder="卡片标题"]').fill('UI卡片-' + TS)
await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/kanban/card/create'), { timeout: 30000 }).catch(() => null),
  cardDlg.locator('.el-dialog__footer button:has-text("确 定")').click()
])
await p.waitForTimeout(3000)
const uiCard = (await api('GET', `/zentao/kanban/card/page?kanban=${created.id}&pageNo=1&pageSize=10`)).data.list
  .find((x) => x.name === 'UI卡片-' + TS)
ok('界面建出卡片', !!uiCard, JSON.stringify(uiCard ? { id: uiCard.id, status: uiCard.status } : null))
const shown = await waitFor(async () => {
  const cards = await p.evaluate(() =>
    Array.from(document.querySelectorAll('.kb-card')).map((el) => el.innerText.replace(/\s+/g, ' ').trim()))
  return cards.some((t) => t.includes('UI卡片-' + TS))
})
ok('卡片出现在网格里', !!shown)

// ---------------- ④ 卡片详情：移动 ----------------
await p.locator('.kb-card').filter({ hasText: 'UI卡片-' + TS }).first().click()
await p.waitForTimeout(1500)
const detailDlg = p.locator('.el-dialog:visible').filter({ hasText: '卡片详情' }).first()
await detailDlg.waitFor({ state: 'visible', timeout: 15000 })
ok('卡片详情打开', await detailDlg.isVisible())
// 移动：把「列」改成已完成（第 3 个可选列）
await detailDlg.locator('.el-form-item:has-text("列") .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '已完成' }).first().click()
const moveResp = await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/kanban/card/move'), { timeout: 30000 }).catch(() => null),
  detailDlg.locator('button:has-text("移动")').first().click()
])
await p.waitForTimeout(2500)
ok('移动接口返回 200', moveResp[0]?.status() === 200, `status=${moveResp[0]?.status?.()}`)

// 校验：后端数据里卡片在「已完成」列
const moved = await waitFor(async () => {
  const d = (await api('GET', `/zentao/kanban/data?kanbanId=${created.id}`)).data
  for (const lane of d.regions[0].lanes) {
    for (const cell of lane.cells) {
      if (cell.cards.some((x) => x.id === uiCard.id) && cell.columnName === '已完成') return cell
    }
  }
  return null
})
ok('卡片移到「已完成」列（视图数据）', !!moved, moved ? `column=${moved.columnName}` : 'null')

// ---------------- ⑤ 完成卡片 ----------------
await p.locator('.kb-card').filter({ hasText: 'UI卡片-' + TS }).first().click()
await p.waitForTimeout(1500)
const dlg2 = p.locator('.el-dialog:visible').filter({ hasText: '卡片详情' }).first()
await dlg2.waitFor({ state: 'visible', timeout: 15000 })
if (await dlg2.locator('button:has-text("完成")').count()) {
  await Promise.all([
    p.waitForResponse((r) => r.url().includes('/card/finish'), { timeout: 30000 }).catch(() => null),
    dlg2.locator('button:has-text("完成")').first().click()
  ])
  await p.waitForTimeout(2500)
}
const done = (await api('GET', '/zentao/kanban/card/get?id=' + uiCard.id)).data
ok('完成卡片后 status=done / progress=100', done.status === 'done' && Number(done.progress) === 100,
  JSON.stringify({ status: done.status, progress: done.progress }))
const doneStyle = await waitFor(async () => {
  const n = await p.locator('.kb-card.kb-card-done').count()
  return n > 0
})
ok('网格里卡片带「已完成」样式', !!doneStyle)

// ---------------- 收尾 ----------------
await api('DELETE', '/zentao/kanban/card/delete?id=' + uiCard.id)
const delResp = await api('DELETE', '/zentao/kanban/delete?id=' + created.id)
console.log('  清理:', JSON.stringify(delResp))

console.log('======================================================')
console.log(`  看板界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
await b.close()
if (FAIL > 0) process.exit(1)

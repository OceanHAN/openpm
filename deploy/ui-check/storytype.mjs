// 需求分层（业务需求 ER / 用户需求 UR / 研发需求 SR）界面检查
//
// 覆盖：/zentao/story 的
//   ① 类型页签：全部 / 业务需求 / 用户需求 / 研发需求，角标数量来自 type-summary
//   ② 点「业务需求」页签 → 列表只剩 type=epic 的需求
//   ③ 「分层视图」→ 树表渲染出 业务需求 → 用户需求 → 研发需求 三层父子链路
//   ④ 「新增需求」弹窗里的「需求类型」下拉能建出业务需求，列表类型列显示「业务需求」
//
// 依赖：后端 127.0.0.1:48080（本机栈或远端栈都行），前端 http://localhost/
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { login } from './_login.mjs'
const _pw = await import(pathToFileURL(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim() + '/playwright/index.js').href)
const chromium = _pw.chromium || _pw.default?.chromium
const BASE = process.env.ZENTAO_UI_BASE || 'http://localhost'
const API_BASE = process.env.ZENTAO_API_BASE || 'http://localhost:48080/admin-api'
const PRODUCT_NAME = '禅道研发管理平台'

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
// 演示数据里已经有一条三层链路：99301 业务需求 → 99302 用户需求 → 99303 研发需求
const summary = (await api('GET', '/zentao/story/type-summary?product=1')).data
console.log(`准备: 产品 1 的需求数量 ${JSON.stringify(summary)}`)

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

await p.goto(BASE + '/zentao/story', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(4000)

// ---------------- 1. 选产品，页签与角标 ----------------
await p.locator('.el-form-item:has-text("所属产品") .el-select').first().click()
await p.waitForTimeout(800)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: PRODUCT_NAME }).first().click()
await p.waitForTimeout(3000)

const tabText = await p.locator('.el-radio-group').first().innerText()
console.log('  页签文案:', tabText.replace(/\s+/g, ' ').trim())
ok('四个类型页签都在', ['全部', '业务需求', '用户需求', '研发需求'].every((t) => tabText.includes(t)))
ok('业务需求页签带角标数量', /业务需求\s*\(\d+\)/.test(tabText.replace(/\s+/g, '')), tabText.replace(/\s+/g, ' '))
ok('角标数字与接口一致',
  tabText.replace(/\s+/g, '').includes(`业务需求(${summary.epic})`) &&
  tabText.replace(/\s+/g, '').includes(`用户需求(${summary.requirement})`) &&
  tabText.replace(/\s+/g, '').includes(`研发需求(${summary.story})`))

// ---------------- 2. 切到「业务需求」页签：列表只剩 epic ----------------
await p.locator('.el-radio-button__inner').filter({ hasText: '业务需求' }).first().click()
await p.waitForTimeout(2500)
const epicRows = await p.evaluate(() => {
  const tables = Array.from(document.querySelectorAll('.el-table'))
  const table = tables.find((t) => t.offsetParent !== null)
  const rows = Array.from(table.querySelectorAll('.el-table__body tbody tr'))
  // 类型列是第 3 列（选择框 / 编号 / 标题 / 类型）
  return rows.map((r) => Array.from(r.querySelectorAll('td')).map((td) => td.innerText.trim()))
})
ok('业务需求页签下有数据', epicRows.length > 0, `行数=${epicRows.length}`)
ok('每行的类型都是业务需求', epicRows.length > 0 && epicRows.every((r) => r[3] === '业务需求'),
  `类型列=${[...new Set(epicRows.map((r) => r[3]))].join('/')}`)
ok('演示数据 99301 在业务需求列表里', epicRows.some((r) => r[1] === '99301'))

// ---------------- 3. 分层视图：三层链路 ----------------
await p.locator('button:has-text("分层视图")').first().click()
await p.waitForTimeout(3000)
const tree = await p.evaluate(() => {
  const table = Array.from(document.querySelectorAll('.el-table')).find((t) => t.offsetParent !== null)
  const rows = Array.from(table.querySelectorAll('.el-table__body tbody tr'))
  return rows.map((r) => ({
    cls: r.className,
    cells: Array.from(r.querySelectorAll('td')).map((td) => td.innerText.trim())
  }))
})
console.log('  分层视图行:', JSON.stringify(tree.map((r) => [r.cells[0], r.cells[1]])))
const node99301 = tree.find((r) => r.cells[0].startsWith('业务需求：需求池支撑多层级管理'))
const node99302 = tree.find((r) => r.cells[0].startsWith('用户需求：按层级浏览需求'))
const node99303 = tree.find((r) => r.cells[0].startsWith('研发需求：实现需求分层树接口'))
ok('分层视图渲染出业务需求 99301', !!node99301)
ok('分层视图渲染出用户需求 99302', !!node99302)
ok('分层视图渲染出研发需求 99303', !!node99303)
ok('三层类型列正确',
  node99301?.cells[1] === '业务需求' && node99302?.cells[1] === '用户需求' && node99303?.cells[1] === '研发需求')
ok('层级列是 1/2/3',
  node99301?.cells[2] === '1' && node99302?.cells[2] === '2' && node99303?.cells[2] === '3',
  `${node99301?.cells[2]}/${node99302?.cells[2]}/${node99303?.cells[2]}`)
// 子需求是缩进渲染的（el-table 的 tree 缩进加在第一个单元格的 placeholder 上）
const indent = await p.evaluate(() => {
  const rows = Array.from(document.querySelectorAll('.el-table__body tbody tr'))
  const get = (frag) => {
    const tr = rows.find((r) => r.innerText.includes(frag))
    const span = tr?.querySelector('.el-table__indent')
    return span ? span.style.paddingLeft || '0px' : '0px'
  }
  return { l1: get('业务需求：需求池'), l2: get('用户需求：按层级'), l3: get('研发需求：实现需求分层树') }
})
ok('三层有缩进层级（父需求在最左）',
  indent.l1 === '0px' && indent.l2 !== '0px' && indent.l3 !== '0px' && parseFloat(indent.l3) > parseFloat(indent.l2),
  JSON.stringify(indent))
// 顺序：父需求排在自己的子需求上面（同一层的兄弟按编号升序），演示链路就该连续三行
ok('父子顺序正确（业务需求 → 用户需求 → 研发需求 连续三行）',
  tree[0]?.cells[0].startsWith('业务需求：需求池支撑多层级管理') &&
  tree[1]?.cells[0].startsWith('用户需求：按层级浏览需求') &&
  tree[2]?.cells[0].startsWith('研发需求：实现需求分层树接口'),
  tree.slice(0, 3).map((r) => r.cells[0].slice(0, 12)).join(' → '))

// ---------------- 4. 新增需求弹窗里的「需求类型」 ----------------
await p.locator('button:has-text("返回列表")').first().click()
await p.waitForTimeout(2000)
await p.locator('button:has-text("新增需求")').first().click()
await p.waitForTimeout(1500)
const dlg = p.locator('.el-dialog:visible').filter({ hasText: '新增需求' }).first()
await dlg.waitFor({ state: 'visible', timeout: 15000 })
const typeItem = dlg.locator('.el-form-item:has-text("需求类型")').first()
ok('新增弹窗有「需求类型」下拉', (await typeItem.count()) > 0)
const typeText = await typeItem.innerText()
ok('需求类型下拉默认研发需求', typeText.includes('研发需求'), typeText.replace(/\s+/g, ' '))

// 选产品 + 类型=业务需求 + 标题，提交
await dlg.locator('.el-form-item:has-text("所属产品") .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: PRODUCT_NAME }).first().click()
await p.waitForTimeout(600)
await typeItem.locator('.el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '业务需求' }).first().click()
await dlg.locator('input[placeholder="请输入需求标题"]').fill('UI业务需求-' + TS)
await Promise.all([
  p.waitForResponse((r) => r.url().includes('/zentao/story/create'), { timeout: 30000 }).catch(() => null),
  dlg.locator('.el-dialog__footer button:has-text("确 定")').first().click()
])
await p.waitForTimeout(2500)

const created = await api('GET', '/zentao/story/page?pageNo=1&pageSize=10&product=1&type=epic&title=' + encodeURIComponent('UI业务需求-' + TS))
const createdRow = (created.data?.list || [])[0]
ok('界面建出了业务需求（type=epic）', !!createdRow && createdRow.type === 'epic',
  JSON.stringify(createdRow ? { id: createdRow.id, type: createdRow.type, grade: createdRow.grade } : created))
ok('业务需求是一级需求（grade=1）', createdRow?.grade === 1)
ok('业务需求顶层的 path/root 自成一体',
  createdRow?.path === ',' + createdRow?.id + ',' && createdRow?.root === createdRow?.id,
  `path=${createdRow?.path} root=${createdRow?.root}`)

// ---------------- 5. 列表类型列显示中文类型名 ----------------
await p.locator('.el-radio-button__inner').filter({ hasText: '业务需求' }).first().click()
await p.waitForTimeout(2000)
await p.fill('input[placeholder="请输入标题关键词"]', 'UI业务需求-' + TS)
await p.locator('button:has-text("搜索")').first().click()
await p.waitForTimeout(2500)
const uiRow = await p.evaluate(() => {
  const table = Array.from(document.querySelectorAll('.el-table')).find((t) => t.offsetParent !== null)
  const tr = table.querySelector('.el-table__body tbody tr')
  return tr ? Array.from(tr.querySelectorAll('td')).map((td) => td.innerText.trim()) : []
})
ok('列表里类型列显示「业务需求」', uiRow[3] === '业务需求', uiRow.join(' | '))

// ---------------- 收尾：删掉界面造的业务需求 ----------------
if (createdRow?.id) {
  await api('DELETE', '/zentao/story/delete?id=' + createdRow.id)
  console.log(`已清理测试数据（业务需求 ${createdRow.id}）`)
}

console.log('======================================================')
console.log(`  需求分层界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
await b.close()
if (FAIL > 0) process.exit(1)

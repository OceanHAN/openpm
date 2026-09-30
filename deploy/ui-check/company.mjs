// 公司信息（company，禅道界面上的「组织视图」）界面检查
//
// 覆盖 /zentao/company 的五个 Tab：
//   ① 公司信息（getFirst：id 最小的一条）+ 编辑弹窗（改名/电话 + http:// 归一 + 必填校验）
//   ② 外部公司（id != 1；text/value/keys 三件套）
//   ③ 超管口径对照（zt_company.admins ↔ super_admin）
//   ④ 组织成员（复用 organization 模块的 user-list）
//   ⑤ 组织动态（复用 action 模块的 dynamic）
//   ⑥ 没有「删除公司」入口（禅道没有这个 action）
//
// 说明：**故意不在界面里新建公司** —— 本模块（与禅道一致）没有删除接口，
// 建出来的数据没法从界面清理；所以这里只验「新建弹窗的字段与必填校验」，不真的落库。
//
// 依赖：后端 $ZENTAO_API_BASE（默认 http://localhost:48080/admin-api），前端 $ZENTAO_UI_BASE（默认 http://localhost）
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

// 准备：把本公司电话恢复到演示值（上一轮可能改过），保证断言稳定
const first = await api('GET', '/zentao/company/get-first')
if (first.code !== 0) throw new Error('准备失败：' + JSON.stringify(first))
await api('PUT', '/zentao/company/update', {
  id: first.data.id, name: first.data.name, phone: '0532-88886666', guest: 0
})
console.log(`准备: 本公司 id=${first.data.id} ${first.data.name}`)
const originalPhone = '0532-88886666'

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1700, height: 1050 } })
const p = await c.newPage()
const pageErrors = []
// 有些 pageerror 是数组/对象（Vue 的 warn 会被包成数组），String() 出来只有 "Array(1): Object"，
// 所以这里兜一层 JSON，方便定位到底是哪条
p.on('pageerror', (e) => pageErrors.push(String(e && e.stack ? e.stack : JSON.stringify(e)).split('\n')[0].slice(0, 200)))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', async (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', async (err) => { __cleanup(); console.error(err); process.exit(1) })

// Playwright 的 pageerror 对某些错误只能给出 {log:[],name:'Array(1)'}（序列化不了），
// 所以在页面里自己挂一个监听，把 message/来源/行号记下来 —— 排查时才有线索
await p.addInitScript(() => {
  window.__errs = []
  window.addEventListener('error', (e) => {
    // 「ResizeObserver loop completed with undelivered notifications」是浏览器的良性告警
    // （Element Plus 的表格/页签在同一帧里改了布局就会触发），不是页面错误，过滤掉
    if (String(e.message || '').includes('ResizeObserver loop')) return
    window.__errs.push(`${e.message} @ ${e.filename || ''}:${e.lineno || 0}`)
  })
  window.addEventListener('unhandledrejection', (e) => {
    let r = e.reason
    let desc
    try { desc = JSON.stringify(r) } catch { desc = String(r) }
    if (r && r.msg) desc = `msg=${r.msg} code=${r.code}`
    else if (r && r.message) desc = `message=${r.message}`
    window.__errs.push('unhandledrejection: ' + desc)
  })
})

await login(p, BASE)
console.log('登录成功')

const view = async () =>
  p.evaluate(() => ({
    text: document.body.innerText.replace(/\s+/g, ' ').trim(),
    // 只取主区域的行：弹窗里的表格/描述也命中 el-table__body，不排除会误判
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr'))
      .filter((r) => !r.closest('.el-dialog'))
      .map((r) => r.innerText.replace(/\s+/g, ' ').trim())
  }))
const switchTab = async (label) => {
  await p.locator(`.el-tabs__item:has-text("${label}")`).first().click()
  await p.waitForTimeout(1500)
}

await p.goto(BASE + '/zentao/company', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 公司信息 ----------------
let v = await view()
ok('页面显示本公司名称', v.text.includes(first.data.name), '')
ok('显示 admins 逗号串（禅道判超管的依据）', v.text.includes(',admin,'), '')
ok('显示联系电话', v.text.includes(originalPhone), '')
ok('页面说明了「禅道没有删除公司」', v.text.includes('禅道没有「删除公司」这个 action'), '')
ok('没有删除按钮（只有编辑）', !v.text.includes('删除公司') || v.text.includes('也没有删除按钮'), '')

// 编辑：改电话 → 保存 → 描述列表更新
await p.locator('button:has-text("编辑公司信息")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '编辑公司信息' }).first().waitFor({ state: 'visible', timeout: 20000 })
const dlgLabels = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  return Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.trim())
})
for (const label of ['公司名称', '联系电话', '传真', '通讯地址', '邮政编码', '官网', '内网', '匿名登录']) {
  ok(`编辑弹窗有「${label}」`, dlgLabels.includes(label), JSON.stringify(dlgLabels))
}
const newPhone = '0532-00001111'
await p.locator('.el-dialog:visible .el-form-item:has-text("联系电话") input').first().fill(newPhone)
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(2500)
v = await view()
ok('保存后公司信息里电话已更新', v.text.includes(newPhone), (v.text.match(/0532-\d+/) || [''])[0])
// 还原演示值，别把库改脏
await api('PUT', '/zentao/company/update', { id: first.data.id, name: first.data.name, phone: originalPhone, guest: 0 })

// 必填校验：清空名称 → 确定 → 提示
await p.locator('button:has-text("编辑公司信息")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '编辑公司信息' }).first().waitFor({ state: 'visible', timeout: 20000 })
await p.locator('.el-dialog:visible .el-form-item:has-text("公司名称") input').first().fill('')
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(1200)
v = await view()
ok('公司名称为空 → 前端提示必填', v.text.includes('公司名称不能为空'), '')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)

// ---------------- ② 外部公司 ----------------
await switchTab('外部公司')
v = await view()
ok('外部公司列表有「甲方信息科技有限公司」', v.rows.some((r) => r.includes('甲方信息科技有限公司')), `行数=${v.rows.length}`)
ok('外部公司列表有「乙方软件服务有限公司」', v.rows.some((r) => r.includes('乙方软件服务有限公司')), '')
ok('本公司（id=1）不在外部公司列表里', !v.rows.some((r) => r.includes(first.data.name)), '')
ok('页面说明了 id != 1 的判据', v.text.includes('getOutsideCompanies() 的判据就是 id != 1'), '')
// 新建弹窗：只验字段与必填，不真的建（没有删除接口，建了清不掉）
await p.locator('button:has-text("新建公司")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '新建公司' }).first().waitFor({ state: 'visible', timeout: 20000 })
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(1000)
v = await view()
ok('新建公司名称为空 → 前端提示必填', v.text.includes('公司名称不能为空'), '')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)

// ---------------- ③ 超管口径对照 ----------------
await switchTab('超管口径对照')
v = await view()
ok('禅道口径里有 admin', v.text.includes('禅道口径') && v.text.includes('admin'), '')
ok('yudao 口径写的是 super_admin 角色', v.text.includes('super_admin'), '')
ok('给出了差异（已对齐/只在禅道侧/只在 yudao 侧）',
  v.text.includes('已对齐') && v.text.includes('只在禅道侧') && v.text.includes('只在 yudao 侧'), '')
ok('带口径说明（超管硬编码放行、不查权限表）', v.text.includes('硬编码放行'), '')

// ---------------- ④ 组织成员（复用 organization）----------------
await switchTab('组织成员')
await p.waitForTimeout(1500)
v = await view()
ok('组织成员表格渲染出用户', v.rows.length >= 1 && v.rows.some((r) => r.includes('admin')), `行数=${v.rows.length}`)
ok('Tab 标题写明了复用 organization 模块', v.text.includes('复用 organization'), '')

// ---------------- ⑤ 组织动态（复用 action）----------------
await switchTab('组织动态')
await p.waitForTimeout(2000)
const timelineCount = await p.locator('.el-timeline-item').count()
v = await view()
ok('组织动态渲染（有动态或明确提示没有）', timelineCount > 0 || v.text.includes('这个周期没有动态'), `时间线条数=${timelineCount}`)
ok('动态里带渲染后的描述（动作渲染）', timelineCount === 0 || /admin|芋道/.test(v.text), '')
ok('说明了 action 模块还不支持「上周/上月」', v.text.includes('上周/上月'), '')

const inPageErrors = await p.evaluate(() => window.__errs || [])
ok('页面没有 JS 报错', pageErrors.length === 0 && inPageErrors.length === 0,
  [...pageErrors, ...inPageErrors].slice(0, 3).join(' | '))

await p.screenshot({ path: '/tmp/zentao-company.png', fullPage: true })
console.log('截图       : /tmp/zentao-company.png')

await b.close()
console.log('======================================================')
console.log(`  company 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

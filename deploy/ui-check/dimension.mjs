// 维度（dimension，BI 的 1.5 级导航）界面检查
//
// 覆盖 /zentao/dimension 的三个页签：
//   ① 维度列表：3 张维度卡片 + 「当前维度」提示 + 切换当前维度（切换 = 写末次维度记录）
//   ② 可见性口径：biModel::getViewableObject 的三句判据逐行复算（表格）
//   ③ 1.5 级导航下拉：ajaxGetDropMenu 的 data/link/labelMap + 两处参数例外（pivot-design → browse）
//   ④ 页面明确写着「开源版只有只读维度，没有维度管理界面」——所以没有新建/编辑/删除按钮
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

// 准备：末次维度归位到 1，保证断言从确定状态出发
const reset = await api('GET', '/zentao/dimension/get-dimension?dimensionID=1&tab=bi')
if (reset.code !== 0) throw new Error('准备失败：' + JSON.stringify(reset))
console.log(`准备: 当前维度归位到 ${reset.data.dimensionID}`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1700, height: 1050 } })
const p = await c.newPage()
const pageErrors = []
// 有些 pageerror 是数组/对象（Vue 的 warn 会被包成数组），String() 出来只有 "Array(1): Object"，
// 所以这里兜一层 JSON，方便定位到底是哪条（坑位 #53）
p.on('pageerror', (e) => pageErrors.push(String(e && e.stack ? e.stack : JSON.stringify(e)).split('\n')[0].slice(0, 200)))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', async (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', async (err) => { __cleanup(); console.error(err); process.exit(1) })

// Playwright 的 pageerror 对某些错误序列化不出来，所以在页面里自己挂监听，把 message/来源/行号记下来
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

// 只统计**当前可见页签**里的表格行：el-tabs 会把渲染过的 pane 留在 DOM 里（display:none），
// 不过滤就会把隐藏页签的行一起数进来（坑位 #40 同源：选择器要么限定层级、要么取 :visible）
const view = async () =>
  p.evaluate(() => {
    const visible = (el) => el.offsetParent !== null
    return {
      text: document.body.innerText.replace(/\s+/g, ' ').trim(),
      rows: Array.from(document.querySelectorAll('.el-table__body tbody tr'))
        .filter((r) => visible(r) && !r.closest('.el-dialog'))
        .map((r) => r.innerText.replace(/\s+/g, ' ').trim())
    }
  })
const switchTab = async (label) => {
  await p.locator(`.el-tabs__item:has-text("${label}")`).first().click()
  await p.waitForTimeout(1200)
}

await p.goto(BASE + '/zentao/dimension', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 维度列表 ----------------
let v = await view()
ok('页面标题写明「维度（禅道 module/dimension）：BI 的 1.5 级导航」',
  v.text.includes('BI 的 1.5 级导航'), '')
ok('说明了「开源版只有只读维度，没有维度管理界面」',
  v.text.includes('开源版只有只读维度，没有维度管理界面'), '')
ok('三个预置维度都渲染成卡片（宏观/效能/质量）',
  v.text.includes('宏观管理维度') && v.text.includes('效能管理维度') && v.text.includes('质量管理维度'), '')
ok('卡片上带维度代号 code', v.text.includes('macro') && v.text.includes('quality'), '')
ok('显示「当前维度」与命中来源（四级兜底链）',
  v.text.includes('当前维度：') && v.text.includes('命中来源：'), '')
ok('说明里写清了可见性判据在 bi 模块（不在 dimension 模块）',
  v.text.includes("biModel::getViewableObject('dimension')"), '')
ok('卡片上有「切换到此维度」按钮（v-hasPermi 生效）',
  await p.locator('button:has-text("切换到此维度")').count() >= 2, '')
// 不看文案（说明里本来就会写「不提供新建 / 编辑 / 删除」），直接数按钮
ok('没有新建/编辑/删除维度的按钮（开源版没有维度 CRUD）',
  (await p.locator('button:has-text("新建维度")').count()) === 0 &&
    (await p.locator('button:has-text("编辑维度")').count()) === 0 &&
    (await p.locator('button:has-text("删除维度")').count()) === 0, '')

// 切换当前维度 → 当前维度提示跟着变（后端写末次维度记录）
// 选择器要限定层级：ContentWrap 本身就是个 el-card，直接 .el-card:has-text(...) 会命中整个内容区，
// 再点它里面的「切换到此维度」就会点到第一张卡片（坑位 #40）
await p.locator('.el-row .el-col .el-card:has-text("效能管理维度") button:has-text("切换到此维度")').first().click()
await p.waitForTimeout(2500)
v = await view()
ok('切换后当前维度变成「效能管理维度」', v.text.includes('当前维度：效能管理维度'), '')
// 还原成 1，别把「末次维度」留脏
await api('GET', '/zentao/dimension/get-dimension?dimensionID=1&tab=bi')
await p.reload({ waitUntil: 'domcontentloaded' })
await p.waitForTimeout(3500)
v = await view()
ok('还原后当前维度回到「宏观管理维度」', v.text.includes('当前维度：宏观管理维度'), '')

// ---------------- ② 可见性口径 ----------------
await switchTab('可见性口径')
v = await view()
ok('可见性表格逐行渲染（3 条演示维度都在）', v.rows.length >= 3, `行数=${v.rows.length}`)
ok('行里带 acl 与命中的判据', v.rows.some((r) => r.includes('open') && r.includes('admin')), v.rows[0] || '')
ok('显示被检查账号与超管标记', v.text.includes('被检查账号') && v.text.includes('直通全部维度'), '')
ok('判据原文里带 FIND_IN_SET（照抄 bi 模块的 SQL 口径）', v.text.includes('FIND_IN_SET'), '')

// ---------------- ③ 1.5 级导航下拉 ----------------
await switchTab('1.5 级导航下拉')
v = await view()
ok('默认演示「例外①」：pivot + design 被改写成 browse',
  v.text.includes('/pivot/browse?dimensionID={id}'), '')
ok('下拉项表格渲染（3 条演示维度都在）', v.rows.length >= 3, `行数=${v.rows.length}`)
ok('下拉项带 keys（拼音首字母列；码表缺字时退化成维度名）',
  v.rows.some((r) => r.includes('宏观管理维度')), v.rows[0] || '')
ok('labelMap.dimension = 维度 与 searchHint = 搜索 都显示出来了',
  v.text.includes('搜索') && v.text.includes('维度'), '')
// 换成例外②：tree + browsegroup
await p.locator('.el-form-item:has-text("module") .el-select').first().click()
await p.waitForTimeout(600)
await p.locator('.el-select-dropdown__item:has-text("tree")').first().click()
await p.waitForTimeout(600)
await p.locator('.el-form-item:has-text("method") input').first().fill('browsegroup')
await p.locator('button:has-text("刷新")').first().click()
await p.waitForTimeout(2000)
v = await view()
ok('例外②：tree + browsegroup 追加 groupID=0&type={viewType}',
  v.text.includes('groupID=0'), '')

const inPageErrors = await p.evaluate(() => window.__errs || [])
ok('页面没有 JS 报错（已过滤 ResizeObserver 良性告警）',
  pageErrors.length === 0 && inPageErrors.length === 0,
  [...pageErrors, ...inPageErrors].slice(0, 3).join(' | '))

await p.screenshot({ path: '/tmp/zentao-dimension.png', fullPage: true })
console.log('截图       : /tmp/zentao-dimension.png')

await b.close()
console.log('======================================================')
console.log(`  dimension 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

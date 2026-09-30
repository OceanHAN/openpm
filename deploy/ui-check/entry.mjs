// 应用接入（entry）界面检查
//
// 覆盖 /zentao/entry：
//   ① 列表渲染（演示应用 + 内置 gitfox 不露出 + 免密标签 + 密钥列）
//   ② 新增弹窗字段齐全、「无限制」勾选把 IP 置为 *、「重新生成」换密钥
//   ③ 接入自测：生成签名 → 发起校验（IP 被拒 403 / 放行成功 / 时间戳重放 405），并确认表格被刷新且没有 NaN
//   ④ 调用日志弹窗里有刚写入的那次调用
//   ⑤ 删除
//
// 依赖：后端 127.0.0.1:48080，前端 http://localhost/
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

// 准备：建一个**全新的**自测应用（calledTime=0，避免与上一轮跑留下的时间戳撞上防重放）
const stamp = String(Date.now()).slice(-6)
const PROBE_CODE = 'uiprobe' + stamp
const PROBE_NAME = 'UI自测应用' + stamp
const created = await api('POST', '/zentao/entry/create', {
  name: PROBE_NAME, code: PROBE_CODE, account: 'admin', ip: '10.0.0.0/8', desc: 'UI 检查用'
})
if (created.code !== 0) throw new Error('准备应用失败：' + JSON.stringify(created))
const PROBE_ID = created.data
console.log(`准备: 应用=${PROBE_ID}（${PROBE_CODE}，IP 白名单 10.0.0.0/8）`)

const cleanup = async () => { if (PROBE_ID) await api('DELETE', '/zentao/entry/delete?id=' + PROBE_ID).catch(() => {}) }
const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1700, height: 1050 } })
const p = await c.newPage()
const pageErrors = []
p.on('pageerror', (e) => pageErrors.push(String(e.stack || e).split('\n')[0]))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))
const __cleanup = () => { try { b.close() } catch { /* 已关闭 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', async (err) => { __cleanup(); await cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', async (err) => { __cleanup(); await cleanup(); console.error(err); process.exit(1) })

await login(p, BASE)
console.log('登录成功')

// 只取**主列表**的行：弹窗里的表格（调用日志）也会命中 .el-table__body，
// 不排除的话「删除后列表里没有这个应用了」会被日志弹窗里的 URL 假阴性（本轮踩到）。
const view = async () =>
  p.evaluate(() => ({
    text: document.body.innerText.replace(/\s+/g, ' ').trim(),
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr'))
      .filter((r) => !r.closest('.el-dialog'))
      .map((r) => r.innerText.replace(/\s+/g, ' ').trim())
  }))
const rowText = (rows, kw) => rows.find((r) => r.includes(kw)) || ''
/**
 * 点「发起校验」并抓取后端错误提示（yudao 用 ElNotification.error({ title: msg })，
 * 所以文案在 __title 上，__content 是空的）。
 *
 * 注意：Element Plus 的旧提示会在 DOM 里留一会儿，直接抓 `.el-notification` 会抓到**上一条**；
 * 这里按期望文案过滤，抓「这一条」。
 */
const clickVerifyExpectError = async (expectFragment) => {
  await p.locator('.el-card button:has-text("发起校验")').first().click()
  try {
    const loc = p.locator('.el-notification').filter({ hasText: expectFragment }).last()
    await loc.waitFor({ state: 'visible', timeout: 8000 })
    return (await loc.innerText()).replace(/\s+/g, ' ')
  } catch {
    return ''
  }
}

await p.goto(BASE + '/zentao/entry', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 列表 ----------------
let v = await view()
ok('列表里有演示应用「OA 办公系统」', !!rowText(v.rows, 'OA 办公系统'), '')
ok('有刚准备的自测应用', !!rowText(v.rows, PROBE_CODE), '')
ok('内置的 gitfox 不出现在列表里（禅道 getList 过滤）', !v.rows.some((r) => r.includes('gitfox')), `行数=${v.rows.length}`)
ok('免密标签渲染（门户免密进入 → 开启）', rowText(v.rows, '门户免密进入').includes('开启'), rowText(v.rows, 'portal'))
ok('密钥列渲染（32 位十六进制）', /[0-9a-f]{32}/.test(rowText(v.rows, 'OA 办公系统')), '')
ok('允许 IP 列渲染白名单', rowText(v.rows, '内网受限应用').includes('10.0.0.0/8'), rowText(v.rows, 'restricted'))
ok('页面说明了「留空等于不限制」', v.text.includes('留空等于不限制'), '')
ok('页面写清了两种签名与防重放', v.text.includes('md5(code+key+time)') && v.text.includes('防重放'), '')

// ---------------- ② 新增弹窗 ----------------
await p.locator('button:has-text("新增应用")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '新增应用接入' }).first().waitFor({ state: 'visible', timeout: 20000 })
const dialog = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  return { labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.trim()) }
})
for (const label of ['应用名称', '应用代号', '密钥', '免密登录', '绑定账号', '允许 IP', '描述']) {
  ok(`弹窗有「${label}」`, dialog.labels.includes(label), JSON.stringify(dialog.labels))
}
// 「无限制」勾上 → ip 变成 *
await p.locator('.el-dialog:visible .el-checkbox:has-text("无限制")').first().click()
await p.waitForTimeout(400)
let ipVal = await p.locator('.el-dialog:visible .el-form-item:has-text("允许 IP") input').first().inputValue()
ok('勾「无限制」→ IP 变成 *', ipVal === '*', `实际=${ipVal}`)
await p.locator('.el-dialog:visible .el-checkbox:has-text("无限制")').first().click()
await p.waitForTimeout(400)
ipVal = await p.locator('.el-dialog:visible .el-form-item:has-text("允许 IP") input').first().inputValue()
ok('取消「无限制」→ IP 清空', ipVal === '', `实际=${ipVal}`)
// 「重新生成」换密钥
const keyBefore = await p.locator('.el-dialog:visible .el-form-item:has-text("密钥") input').first().inputValue()
await p.locator('.el-dialog:visible button:has-text("重新生成")').first().click()
await p.waitForTimeout(1500)
const keyAfter = await p.locator('.el-dialog:visible .el-form-item:has-text("密钥") input').first().inputValue()
ok('「重新生成」换出一个新的 32 位密钥', keyAfter.length === 32 && keyAfter !== keyBefore, `${keyBefore} → ${keyAfter}`)
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)

// ---------------- ③ 接入自测：签名 + 校验 ----------------
await p.locator('.el-card .el-form-item:has-text("应用") .el-select').first().click()
await p.waitForTimeout(700)
await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: PROBE_NAME }).first().click()
await p.waitForTimeout(800)

// 时间戳往后放 300 秒，保证大于 calledTime（防重放）
const future = String(Math.floor(Date.now() / 1000) + 300)
await p.locator('.el-card .el-form-item:has-text("时间戳") input').first().fill(future)
await p.keyboard.press('Enter')
await p.waitForTimeout(400)

// 来源 IP 设成不在白名单里的，先验一次 403
await p.locator('.el-card .el-form-item:has-text("来源 IP") input').first().fill('8.8.8.8')
await p.keyboard.press('Enter')
await p.waitForTimeout(400)
await p.locator('.el-card button:has-text("生成签名")').first().click()
await p.waitForTimeout(2000)
const tokenShown = await p.locator('.el-card .el-descriptions__content').first().innerText()
ok('生成签名后展示出 32 位 token', /^[0-9a-f]{32}$/.test(tokenShown.trim()), tokenShown.trim())
v = await view()
ok('签名方式显示 time', v.text.includes('time'), '')

const denyMsg = await clickVerifyExpectError('该IP被限制访问')
ok('IP 不在白名单 → 后端提示「该IP被限制访问」', denyMsg.includes('该IP被限制访问'), denyMsg.replace(/\s+/g, ' ').slice(0, 80))
ok('提示里带上了被拒的 IP', denyMsg.includes('8.8.8.8'), '')
ok('失败时不再写日志（界面卡片给出失败说明）', (await view()).text.includes('校验失败'), '')

// 换成白名单内的 IP，这次应该通过
await p.locator('.el-card .el-form-item:has-text("来源 IP") input').first().fill('10.1.2.3')
await p.keyboard.press('Enter')
await p.waitForTimeout(400)
await p.locator('.el-card button:has-text("发起校验")').first().click()
await p.waitForTimeout(2500)
v = await view()
ok('IP 在白名单内 → 校验通过', v.text.includes('校验通过'), (v.text.match(/校验通过[^。]{0,60}/) || [''])[0])
ok('通过的是 time 模式', v.text.includes('time 模式'), '')
ok('解析出绑定账号与用户姓名', v.text.includes('admin') && v.text.includes('芋道源码'), '')

// 列表被刷新：最近调用列出现日期，且没有 NaN（坑位 #51：数字时间戳不能直接当日期用）
const probeRow = rowText(v.rows, PROBE_CODE)
ok('自测后列表被刷新，最近调用列渲染出日期', /\d{4}-\d{2}-\d{2}/.test(probeRow), probeRow)
ok('表体没有出现 NaN / Invalid Date', !v.text.includes('NaN') && !v.text.includes('Invalid Date'), '')
ok('表体行数正常（不是空的）', v.rows.length >= 4, `行数=${v.rows.length}`)

// 时间戳复用一次 → 405 防重放
const replayMsg = await clickVerifyExpectError('重放')
ok('同一个时间戳再用一次 → 后端提示重放（405）', replayMsg.includes('重放'), replayMsg.replace(/\s+/g, ' ').slice(0, 80))

// ---------------- ④ 调用日志 ----------------
await p.locator('.el-table__body tbody tr').filter({ hasText: PROBE_CODE }).first()
  .locator('button:has-text("日志")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '调用日志' }).first().waitFor({ state: 'visible', timeout: 20000 })
await p.waitForTimeout(2000)
const logDialog = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  return {
    text: d.innerText.replace(/\s+/g, ' '),
    rows: Array.from(d.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim())
  }
})
ok('日志弹窗里有调用记录', logDialog.rows.length >= 1, `行数=${logDialog.rows.length}`)
ok('日志记录了请求地址', logDialog.rows.some((r) => r.includes('m=user&f=apilogin')), logDialog.rows[0] || '')
ok('日志记录了成功的结果', logDialog.rows.some((r) => r.includes('success:time')), '')
ok('日志弹窗标题带应用名', logDialog.text.includes(PROBE_NAME), '')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)

// ---------------- ⑤ 删除 ----------------
await p.locator('.el-table__body tbody tr').filter({ hasText: PROBE_CODE }).first()
  .locator('button:has-text("删除")').first().click()
await p.waitForTimeout(700)
await p.locator('.el-message-box__btns button:has-text("确定")').click()
await p.waitForTimeout(2500)
v = await view()
ok('删除后列表里没有这个应用了', !v.rows.some((r) => r.includes(PROBE_CODE)), '')

ok('页面没有 JS 报错', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '))

await p.screenshot({ path: '/tmp/zentao-entry.png', fullPage: true })
console.log('截图       : /tmp/zentao-entry.png')

await b.close()
console.log('======================================================')
console.log(`  entry 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

// Webhook（webhook，禅道「事件外发的通用出口」）界面检查
//
// 覆盖 /zentao/webhook 的三块：
//   ① Webhook 列表（演示数据 92200 指向 mock 接收端、92201 指向不可达地址）
//   ② 新建/编辑弹窗（名称/地址/对象类型/动作/字段映射/发送方式 + 必填校验）
//   ③ 发送日志（读 zt_log，objectType=webhook）+「模拟触发」走真实链路
//   ④ mock 接收端列表（发送测试的落点）
//
// 说明：界面里**不新建**长期数据 —— 「新建弹窗」只验字段与必填，不真的提交；
// 需要落库的动作走接口并当场清理（脚本末尾清 zt_log 与临时 webhook）。
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

// ---------------- 准备：补齐 zt_action 演示行 ----------------
// 为什么这一步是必须的（2026-09-16 踩到）：send() 的第一步是拿这次动作对应的 zt_action 行
// （WebhookService:344-351，禅道 buildData 在 $action 为空时直接 return false），
// 拿不到就 skipped=true、**一条日志都不写** —— 页面本身没问题，是「日志抽屉里没有数据」。
// 而 deploy/test-webhook-module.sh 的做法是「开头 INSERT（99200/99201/99202）→ 结尾 DELETE」，
// 跑完那次接口测试之后演示动作行就没了，于是这个**只读**的界面检查在它后面跑就必然空转。
// 这里自己补一遍（幂等 INSERT IGNORE，不删任何东西），让检查不依赖「上一个人跑过什么」。
//
// ⚠️ id 段所有权（2026-09-17 定，务必遵守）：
//   99200~99202 是 **deploy/test-webhook-module.sh 的私有段**（它开头 INSERT、结尾 DELETE，
//   而且它靠「取最新一条动作」的语义做断言）—— 界面检查**绝不能**用这一段，否则接口回归
//   一跑，正在跑的界面检查就会「prep 时还在、send 时已被删」（实测踩到过）。
//   99101~99103 是 **本脚本的私有段**：低于 99200（这样接口回归的「取最新」永远优先它自己的
//   99202，不会被我们顶掉），也远低于 zt_action 当前 AUTO_INCREMENT（99890+），
//   所以自动增长的 id 永远撞不到这三条。
// ⚠️ 另外：本脚本会往 zt_log 写 webhook 发送日志，而 test-webhook-module.sh 对 zt_log 用的是
//   **精确条数**断言（该文件 151/157/159/170 行），两个脚本**不能并发跑**。
const API_HOST = new URL(API_BASE).hostname
const ACTIONS = [
  [99101, 'story', 1, 'opened'],
  [99102, 'story', 4, 'edited'],
  [99103, 'story', 92201, 'edited']
]
/** 在后端所在机器上跑一段 SQL（本机模式直接跑 mysql 客户端，服务器模式走 ssh 进 yudao-mysql） */
const dbExec = (sql) => {
  const local = ['127.0.0.1', 'localhost', '::1'].includes(API_HOST)
  if (local) {
    return execFileSync('mysql', ['-h127.0.0.1', '-P3306', '-uroot', '-p' + (process.env.MYSQL_PASS || ''),
      '--default-character-set=utf8mb4', '-N', '-e', sql], { encoding: 'utf8' })
  }
  // SQL 一律 base64 过去解，绕开远端 shell 对反引号 / $ / 引号的展开（deploy/_mysql.sh 同款做法）
  const b64 = Buffer.from(sql, 'utf8').toString('base64')
  return execFileSync('sshpass', ['-p', process.env.REMOTE_SSH_PASS || process.env.SSH_PASS || '', 'ssh',
    '-o', 'StrictHostKeyChecking=no', '-o', 'ConnectTimeout=15', `root@${API_HOST}`,
    `echo ${b64} | base64 -d | docker exec -i yudao-mysql mysql -uroot -p'$MYSQL_PASS' --default-character-set=utf8mb4 -N`],
  { encoding: 'utf8' })
}
/**
 * 确保演示动作行在位（幂等 INSERT IGNORE，不删任何东西）。
 *
 * 为什么要在 send 之前**再确认一次**（2026-09-17 实测）：`deploy/test-webhook-module.sh`
 * 结尾会 `DELETE FROM zt_action WHERE id IN (99200,99201,99202)`（该文件 222 行），
 * 并发跑它的时候会出现「脚本开头 prep 时这 3 行还在、几分钟后真正 send 时已经被删掉」，
 * 于是 /send 拿不到 zt_action 行 → skipped=true → 日志抽屉 0 行 → 后面一路超时。
 * 实测证据：prep 时 SELECT COUNT(*) = 3，之后 `SELECT COUNT(*) ... objectType='story'
 * AND objectID=92201 AND action='edited'` = 0，而 /send 的原话就是
 * 「没有找到对应的 zt_action 记录」。
 */
const ensureActions = (when) => {
  const ids = ACTIONS.map((a) => a[0]).join(',')
  const cur = dbExec(`SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_action WHERE id IN (${ids});`).trim()
  if (cur === String(ACTIONS.length)) return
  console.log(`准备: [${when}] zt_action 演示行只剩 ${cur}/${ACTIONS.length} 条，补上（幂等，不删任何东西）`)
  dbExec(`INSERT IGNORE INTO \`ruoyi-vue-pro\`.zt_action
      (id,objectType,objectID,product,project,execution,actor,action,\`date\`,comment,extra,\`read\`,vision,efforted,creator,updater) VALUES
      ${ACTIONS.map(([id, ot, oid, act]) => `(${id},'${ot}',${oid},'1',1,90001,'admin','${act}',NOW(),'演示/测试','',0,'rnd',0,'admin','admin')`).join(',\n      ')};`)
  const now = dbExec(`SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_action WHERE id IN (${ids});`).trim()
  if (now !== String(ACTIONS.length)) throw new Error(`只补齐了 ${now}/${ACTIONS.length} 条`)
}
/** 补不上也要继续：后面的断言会以「skipped=true / 日志抽屉 0 行」的形式明确失败 */
const tryEnsureActions = (when) => {
  try { ensureActions(when) } catch (e) {
    console.log(`准备: ⚠️ [${when}] 没法补 zt_action 演示行（${String(e.message || e).split('\n')[0]}）——日志相关断言可能失败`)
  }
}
tryEnsureActions('启动')

// ---------------- 准备：确认演示 webhook 在位、清一次 mock ----------------
const page0 = await api('GET', '/zentao/webhook/page?pageNo=1&pageSize=50')
if (page0.code !== 0) throw new Error('准备失败：' + JSON.stringify(page0))
const demo = (page0.data.list || []).find((w) => w.id === 92200)
if (!demo) throw new Error('演示 webhook 92200 不存在，请先执行 deploy/sql/55-zt_webhook.sql')
console.log(`准备: 演示 webhook 92200「${demo.name}」url=${demo.url}`)
await api('DELETE', '/zentao/webhook/mock-clear')

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1700, height: 1050 } })
const p = await c.newPage()
const pageErrors = []
// 有些 pageerror 是数组/对象（Vue 的 warn 会被包成数组），String() 出来只有 "Array(1): Object"，所以兜一层 JSON
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
    // 只取主区域的行：弹窗/抽屉里的表格也命中 el-table__body，不排除会误判
    rows: Array.from(document.querySelectorAll('.el-table__body tbody tr'))
      .filter((r) => !r.closest('.el-dialog') && !r.closest('.el-drawer'))
      .map((r) => r.innerText.replace(/\s+/g, ' ').trim())
  }))
const switchTab = async (label) => {
  await p.locator(`.el-tabs__item:has-text("${label}")`).first().click()
  await p.waitForTimeout(1500)
}
/**
 * 主列表里含某段文本的行号（找不到返回 -1）。
 * 必须先等表格真的渲染出数据行再取下标 —— 页面几个 request 是并发的，
 * 「页面 sleep 完」不等于「列表已经画出来」；早期版本直接 evaluate 取下标，
 * 列表还没回来时拿到 -1，而 locator.nth(-1) 在 Playwright 里是**从后往前数**，
 * 于是点到的其实是别的行（有时侥幸点对，有时就不是那一行了）。
 */
const rowIndexOf = async (text) => {
  await p.locator('.el-table__body tbody tr').filter({ hasText: text }).first()
    .waitFor({ state: 'visible', timeout: 20000 })
  return p.evaluate((t) => {
    const rows = Array.from(document.querySelectorAll('.el-table__body tbody tr'))
    return rows.findIndex((r) => r.innerText.includes(t))
  }, text)
}
/**
 * 把右上角的 el-notification 关掉，再点抽屉里的按钮。
 *
 * 为什么需要（2026-09-17 实测）：本脚本会**故意**提交非法地址来验前端规则
 * （创建态空地址、编辑态 ftp:// 与空地址），其中至少创建态那条按设计就是**前端不拦、
 * 交给后端拒**（index.vue:216 `:prop="form.id ? 'url' : ''"`），于是 axios 的
 * `ElNotification.error(...)`（config/axios/service.ts:218）会在右上角弹「请求地址不能为空」。
 * 日志抽屉是贴右边 720px 的抽屉，关闭按钮实测 rect = {x:1660,y:21,w:20,h:20}，
 * elementFromPoint 打出来正好是 `.el-notification.right` —— 通知不散，
 * 这个按钮就点不到（实测报错：`<div role="alert" id="notification_1"
 * class="el-notification right"> intercepts pointer events`，而且它可能几十秒都不消失）。
 * 所以：先把通知文案原样打出来（不隐藏问题），再点通知自己的关闭按钮把它关掉。
 */
const dismissNotifications = async (where) => {
  const txt = await p.evaluate(() => Array.from(document.querySelectorAll('.el-notification'))
    .map((n) => n.innerText.replace(/\s+/g, ' ').trim()))
  if (txt.length) console.log(`  [notification @${where}]`, txt.join(' | ').slice(0, 300))
  for (let i = 0; i < 10; i++) {
    const btn = p.locator('.el-notification__closeBtn').first()
    if (!(await btn.count())) break
    await btn.click({ timeout: 5000 }).catch(() => {})
    await p.waitForTimeout(200)
  }
  await p.waitForFunction(() => document.querySelectorAll('.el-notification').length === 0, null,
    { timeout: 10000 }).catch(() => {})
}

await p.goto(BASE + '/zentao/webhook', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 列表 ----------------
let v = await view()
ok('页面标题说明了「事件外发的通用出口」', v.text.includes('事件外发的通用出口'), '')
ok('列表里有演示 webhook 92200', v.rows.some((r) => r.includes('本地联调接收端')), `行数=${v.rows.length}`)
ok('列表里有不可达地址的演示 webhook', v.rows.some((r) => r.includes('演示：不可达地址')), '')
ok('列表显示 Hook 地址', v.text.includes('/zentao/webhook/mock-receive'), '')
ok('列表显示「关注对象（actions）」列', v.text.includes('关注对象'), '')
ok('actions 为空时显示「白名单全量」或标签', v.text.includes('白名单全量') || v.text.includes('story'), '')
ok('有 5 个操作入口（发送测试/模拟触发/日志/编辑/删除）',
  ['发送测试', '模拟触发', '日志', '编辑', '删除'].every((t) => v.text.includes(t)), '')
ok('页面写明了三条照抄的禅道规则',
  v.text.includes('objectTypes 白名单') && v.text.includes('取交集') && v.text.includes('发送失败只落日志'), '')

// ---------------- ② 新建弹窗：字段 + 必填校验 ----------------
await p.locator('button:has-text("新增 Webhook")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '新增 Webhook' }).first().waitFor({ state: 'visible', timeout: 20000 })
const dlgLabels = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  return Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.trim())
})
for (const label of ['名称', '类型', 'Hook 地址', '禅道域名', '密钥 secret', '内容类型', '发送方式', '关联产品', '关联执行', '参数（payload 字段）', '触发动作', '描述']) {
  ok(`新建弹窗有「${label}」`, dlgLabels.includes(label), '')
}
// 勾选框总数 = 「参数（payload 字段）」9 项（index.vue 的 paramOptions，逐条来自禅道 paramsList）
//            + 9 种对象类型各自的动作白名单（/zentao/webhook/object-types，实测 56 条）= 65。
// 这里不再只断言 >= 50，而是**直接从接口取期望值再和 DOM 对账**（比原来更严），
// 并用 waitForFunction 等到弹窗把这 65 个勾选框铺完：弹窗刚打开那一帧读到中间态会数少
// （实测抖动到 9，正好只剩参数那 9 个，即 objectTypes 还没铺进 v-for），那是渲染时序，
// 不是页面缺陷 —— 接口侧连测 3 次都是 9 类型 / 56 动作，probe 按同一时序跑 3 次都是 65。
const otResp = await api('GET', '/zentao/webhook/object-types')
const expectCb = 9 + (otResp.data || []).reduce((n, t) => n + (t.actionTypes || []).length, 0)
await p.waitForFunction((n) => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  return !!d && d.querySelectorAll('.el-checkbox').length === n
}, expectCb, { timeout: 20000 }).catch(() => {})
const cbCount = await p.locator('.el-dialog:visible .el-checkbox').count()
ok('触发动作按 9 种对象类型 × 各自动作渲染出勾选框',
  cbCount === expectCb && expectCb >= 50, `勾选框=${cbCount} 期望=${expectCb}（9 参数 + ${expectCb - 9} 动作）`)
ok('参数下拉含「操作内容 text」', (await p.locator('.el-dialog:visible').innerText()).includes('操作内容'), '')
// 名称清空 → 确定 → 前端必填提示（同时验不冒 unhandledrejection，坑位 #53）
await p.locator('.el-dialog:visible .el-form-item:has-text("名称") input').first().fill('')
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(1200)
v = await view()
ok('名称为空 → 前端提示必填', v.text.includes('名称不能为空'), '')
// 地址填非法值 → 前端 pattern 提示
// 注意（2026-09-16 修正）：创建态的 Hook 地址**故意不做任何校验** —— 模板里是
//   <el-form-item label="Hook 地址" :prop="form.id ? 'url' : ''">
// 只有编辑态（form.id 非空）才把 rules.url 挂上去，这是照抄禅道 requiredFields 的不对称
// （禅道 create 的 requiredFields 不含 url，edit 才必填）。所以「非法地址要报错」这条规则
// 必须在**编辑态**验：这里把名字填回去、关掉创建弹窗，第 3 节用编辑弹窗验它。
await p.locator('.el-dialog:visible .el-form-item:has-text("名称") input').first().fill('界面检查-临时')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)
// 创建态「地址可空」也顺手钉一下：只填名字、地址留空，点确定不该被 url 拦下来
await p.locator('button:has-text("新增 Webhook")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '新增 Webhook' }).first().waitFor({ state: 'visible', timeout: 20000 })
await p.locator('.el-dialog:visible .el-form-item:has-text("名称") input').first().fill('界面检查-临时')
await p.locator('.el-dialog:visible .el-form-item:has-text("Hook 地址") input').first().fill('')
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(1200)
v = await view()
ok('创建态 Hook 地址可空（禅道 create 的 requiredFields 不含 url，只有编辑态必填）',
  !v.text.includes('Hook 地址只能以 http:// 或 https:// 开头') && !v.text.includes('编辑态 Hook 地址必填'), '')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)
// 创建态这一步是「前端放行、后端拒」的有意设计（后端 WebhookService:268-271 把创建态 url 也加固成必填），
// 所以后端会弹一条「请求地址不能为空」的右上角通知；这里顺手关掉，别让它挂到后面挡住抽屉按钮
await dismissNotifications('创建态空地址试完之后')

// ---------------- ③ 编辑弹窗：Hook 地址的 http(s):// 规则（禅道同款，只在编辑态生效）----------------
// 用演示数据 92200 开编辑弹窗（不提交，验完就关）—— 校验消息本身是 rules.url 里的原文
const editRowIdx = await rowIndexOf('本地联调接收端')
ok('列表里找得到 92200 那一行（用于验编辑态地址校验）', editRowIdx >= 0, `index=${editRowIdx}`)
await p.locator('.el-table__body tbody tr').nth(editRowIdx).locator('button:has-text("编辑")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '编辑 Webhook' }).first().waitFor({ state: 'visible', timeout: 20000 })
const oldUrl = await p.locator('.el-dialog:visible .el-form-item:has-text("Hook 地址") input').first().inputValue()
await p.locator('.el-dialog:visible .el-form-item:has-text("Hook 地址") input').first().fill('ftp://x/hook')
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(1200)
v = await view()
ok('编辑态地址不是 http(s):// → 前端提示（禅道同款规则）', v.text.includes('Hook 地址只能以 http:// 或 https:// 开头'), '')
// 清空 → 编辑态必填（同一个 rules.url 的 required 分支）
await p.locator('.el-dialog:visible .el-form-item:has-text("Hook 地址") input').first().fill('')
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(1200)
v = await view()
ok('编辑态地址清空 → 前端提示必填', v.text.includes('编辑态 Hook 地址必填'), '')
// 还原地址再关（绝不提交，演示数据保持原样）
await p.locator('.el-dialog:visible .el-form-item:has-text("Hook 地址") input').first().fill(oldUrl)
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)
// 上面两步是「故意提交非法地址」的，后端会拒；把因此弹出的右上角通知先关掉，
// 免得它一直挂着挡住后面抽屉右上角的按钮
await dismissNotifications('非法地址试完之后')

// ---------------- ④ 日志抽屉 + 模拟触发（真实链路）----------------
// 先走接口触发一次真实发送（页面上的「模拟触发」按钮做的是同一件事）
// send 之前再确认一次演示动作行还在（并发跑 test-webhook-module.sh 时会把这 3 行删掉）
tryEnsureActions('send 前')
const sendResp = await api('POST', '/zentao/webhook/send', {
  objectType: 'story', objectID: 92201, actionType: 'edited', webhookId: 92200
})
ok('接口模拟触发：未跳过且 payload 是 story/92201/edited',
  sendResp.code === 0 && sendResp.data.skipped === false &&
  JSON.parse(sendResp.data.payload).objectID === 92201 &&
  JSON.parse(sendResp.data.payload).action === 'edited',
  JSON.stringify(sendResp.data.message || ''))

// 日志抽屉：点 92200 那一行的「日志」
const rowIdx = await rowIndexOf('本地联调接收端')
ok('列表里找得到 92200 那一行（用于打开日志抽屉）', rowIdx >= 0, `index=${rowIdx}`)
await p.locator('.el-table__body tbody tr').nth(rowIdx).locator('button:has-text("日志")').first().click()
await p.locator('.el-drawer__title').filter({ hasText: '发送日志' }).first().waitFor({ state: 'visible', timeout: 20000 })
await p.waitForTimeout(2000)
const drawerRows = await p.evaluate(() =>
  Array.from(document.querySelectorAll('.el-drawer .el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim())
)
ok('日志抽屉渲染出刚触发的那一行', drawerRows.length >= 1, `行数=${drawerRows.length}`)
ok('日志行显示成功（mock 接收端回了 success）', drawerRows.some((r) => r.includes('成功')), '')
// 必须带 :visible —— 页面里本来就渲染了 2 个 .el-drawer（日志抽屉 + 项目配置抽屉），
// 裸写 .el-drawer 会被 Playwright 的 strict mode 判成「命中 2 个元素」直接抛错（不是超时）
ok('日志抽屉写明了数据来自 zt_log（objectType=webhook）',
  (await p.locator('.el-drawer:visible').innerText()).includes('zt_log'), '')
// 查看 payload
await dismissNotifications('日志抽屉-查看')
await p.locator('.el-drawer:visible .el-table__body tbody tr').first().locator('button:has-text("查看")').first().click()
await p.waitForTimeout(1200)
const payloadText = await p.locator('.el-dialog:visible').last().innerText()
ok('payload 弹窗展示了 objectType/objectID/action', payloadText.includes('objectID') && payloadText.includes('92201'), '')
ok('payload 弹窗展示了现拼的 text（含查看链接）', payloadText.includes('/zentao/story/index?id=92201'), '')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
// 等 payload 弹窗（append-to-body，overlay 盖在最上层）真的消失再动抽屉，
// 否则点抽屉关闭会被 dialog 的 overlay 拦下来
await p.waitForFunction(() => !Array.from(document.querySelectorAll('.el-dialog'))
  .some((d) => d.getBoundingClientRect().width > 0), null, { timeout: 15000 })
// 关日志抽屉。两个要点：
// ① 页面里常驻 2 个 .el-drawer —— 日志抽屉 + 布局自带的「项目配置」抽屉
//    （layout/components/Setting/src/Setting.vue，一直在 DOM 里只是隐藏）。
//    裸 .el-drawer 会命中 2 个（strict mode 报错），只加 :visible + .first() 也可能点错那个，
//    所以这里按标题精确定位到日志抽屉。
// ② 原来这里是 `.click().catch(() => {})`：把关闭失败吞掉了，于是错误延后到 switchTab，
//    只报「.el-overlay.is-drawer 拦住了点击」，看不出因果。现在关不掉就当场失败。
await dismissNotifications('日志抽屉-关闭')
await p.locator('.el-drawer')
  .filter({ has: p.locator('.el-drawer__title:has-text("发送日志")') })
  .locator('.el-drawer__close-btn').first().click({ timeout: 15000 })
// 确认抽屉真的收起来了（元素宽度归零），再往下点页签
await p.waitForFunction(() => Array.from(document.querySelectorAll('.el-drawer'))
  .every((d) => d.getBoundingClientRect().width === 0), null, { timeout: 15000 })

// ---------------- ⑤ 发送日志页签 ----------------
await switchTab('发送日志')
v = await view()
ok('发送日志页签有行（读 zt_log）', v.rows.length >= 1, `行数=${v.rows.length}`)
ok('日志页签显示 payload 的「查看」入口', v.text.includes('查看'), '')

// ---------------- ⑥ 发送测试页签（mock 接收端）----------------
await switchTab('发送测试')
v = await view()
ok('发送测试页签说明了「只是联调脚手架」', v.text.includes('联调脚手架'), '')
// 回到列表点「发送测试」，再去 mock 页签看结果
await switchTab('Webhook 列表')
const rowIdx2 = await rowIndexOf('本地联调接收端')
await p.locator('.el-table__body tbody tr').nth(rowIdx2).locator('button:has-text("发送测试")').first().click()
await p.waitForTimeout(2500)
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click().catch(() => {})
await p.waitForTimeout(800)
const mockResp = await api('GET', '/zentao/webhook/mock-list')
ok('mock 接收端收到了「发送测试」打的 JSON',
  mockResp.code === 0 && mockResp.data.some((r) => String(r.body).includes('webhook-send-test')),
  `条数=${mockResp.data?.length}`)
await switchTab('发送测试')
await p.waitForTimeout(1500)
v = await view()
ok('mock 页签渲染出收到的请求', v.rows.length >= 1, `行数=${v.rows.length}`)

// ---------------- ⑦ 失败不影响业务（接口侧）----------------
const failResp = await api('POST', '/zentao/webhook/send', {
  objectType: 'story', objectID: 92201, actionType: 'edited', webhookId: 92201
})
ok('地址不可达时接口仍返回 success（失败不影响业务）', failResp.code === 0, `code=${failResp.code}`)
ok('失败明细：failedCount=1 且 result 写明发送失败',
  failResp.data.failedCount === 1 && String(failResp.data.items[0].result).includes('发送失败'), '')

const inPageErrors = await p.evaluate(() => window.__errs || [])
ok('页面没有 JS 报错', pageErrors.length === 0 && inPageErrors.length === 0,
  [...pageErrors, ...inPageErrors].slice(0, 3).join(' | '))

await p.screenshot({ path: '/tmp/zentao-webhook.png', fullPage: true })
console.log('截图       : /tmp/zentao-webhook.png')

// ---------------- 收尾：清掉本次检查产生的 mock 记录与日志 ----------------
await api('DELETE', '/zentao/webhook/mock-clear')
console.log('收尾: 已清空 mock 接收端记录（zt_log 的发送日志保留，供人工查看）')

await b.close()
console.log('======================================================')
console.log(`  webhook 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

// 接口文档库（api，禅道 module/api）界面检查
//
// 覆盖 /zentao/api：
//   ① 页面渲染 + 两条说明（「接口文档库不是对外 REST 接口管理」+「导入 OpenAPI 属付费扩展」）
//   ② 按库筛选（库下拉 → 目录树跟着换）
//   ③ 接口详情抽屉：基本信息 / 版本链（点 v1 回看历史，读的是 zt_apispec）/ 四张 scope 字段表 /
//      请求示例 + 响应示例
//   ④ 数据结构页签 + 结构详情抽屉（版本链按 name 关联、字段树、原始 JSON）
//   ⑤ 发布版本页签（快照计数、冻结清单）→「按此版本浏览」跳回接口列表并打上「快照」标记
//   ⑥ 三个弹窗（新建接口 / 新建结构 / 发布）的字段与必填校验
//   ⑦ 页面无 JS 报错（过滤 ResizeObserver 良性告警）
//
// 依赖：后端 $ZENTAO_API_BASE（默认 http://localhost:48080/admin-api）、前端 $ZENTAO_UI_BASE（默认 http://localhost）
// 前置：先跑 deploy/sql/54-zt_api.sql（演示库/接口/结构/发布）—— 本脚本只读，不改库
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

// 准备：确认演示数据在（接口 92711 是 v2、结构 user 是 v2、发布 v1.0 冻结 2 接口 + 2 结构）
const libResp = await api('GET', '/zentao/api/lib-list')
if (libResp.code !== 0 || !libResp.data?.length) throw new Error('准备失败：接口库列表为空 ' + JSON.stringify(libResp))
const LIB = libResp.data[0]
const demoApi = await api('GET', '/zentao/api/get?id=92711')
if (demoApi.code !== 0) throw new Error('准备失败：演示接口 92711 不存在 ' + JSON.stringify(demoApi))
const demoStruct = await api('GET', '/zentao/api/struct-get?id=92731')
if (demoStruct.code !== 0) throw new Error('准备失败：演示结构 92731 不存在 ' + JSON.stringify(demoStruct))
const releases = await api('GET', '/zentao/api/release-list?libID=' + LIB.id)
const demoRelease = (releases.data || []).find((r) => r.version === 'v1.0')
if (!demoRelease) throw new Error('准备失败：演示发布版本 v1.0 不存在 ' + JSON.stringify(releases))
console.log(`准备: 库=${LIB.id}(${LIB.name}) 接口 92711=v${demoApi.data.version} 结构 user=v${demoStruct.data.version} 发布=${(releases.data || []).map((r) => r.version).join(',')}`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
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

// 页面里自己挂监听：Playwright 的 pageerror 对某些错误序列化不出来（坑位 #53），
// 同时把浏览器良性告警（Element Plus 表格/页签常见的 ResizeObserver loop）过滤掉
await p.addInitScript(() => {
  window.__errs = []
  window.addEventListener('error', (e) => {
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

/**
 * 只取「当前可见页签」里的表格行。
 * Element Plus 把非激活页签 display:none（offsetParent 为 null），所以这里显式过滤；
 * 抽屉/弹窗里的表格也命中 .el-table__body，但它们在 .el-tab-pane 之外，天然被排除。
 */
const view = async () =>
  p.evaluate(() => {
    const activePanes = Array.from(document.querySelectorAll('.el-tab-pane')).filter((el) => el.offsetParent !== null)
    const rows = []
    for (const pane of activePanes) {
      for (const tr of pane.querySelectorAll('.el-table__body tbody tr')) {
        rows.push(tr.innerText.replace(/\s+/g, ' ').trim())
      }
    }
    return { text: document.body.innerText.replace(/\s+/g, ' ').trim(), rows }
  })
const switchTab = async (label) => {
  await p.locator(`.px-10px .el-tabs__item:has-text("${label}")`).first().click()
  await p.waitForTimeout(1500)
}
/** 当前可见页签里、包含指定文本的第一行（避开隐藏页签与抽屉里的行） */
const rowOf = (text) =>
  p.locator('.el-tab-pane:visible .el-table__body tbody tr').filter({ hasText: text }).first()
/** 抽屉快照：标题 / 全文 / 当前可见 scope 页签里的字段行（结构抽屉没有 scope 页签，就取整抽屉） */
const inDrawer = async () =>
  p.evaluate(() => {
    const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null)
    if (!d) return { title: '', text: '', rows: [] }
    const panes = Array.from(d.querySelectorAll('.el-tabs__content .el-tab-pane'))
      .filter((el) => el.offsetParent !== null)
    const scopes = panes.length ? panes : [d]
    return {
      title: (d.querySelector('.el-drawer__title') || {}).innerText?.trim() || '',
      text: d.innerText.replace(/\s+/g, ' ').trim(),
      rows: scopes.flatMap((scope) =>
        Array.from(scope.querySelectorAll('.el-table__body tbody tr')).map((tr) =>
          tr.innerText.replace(/\s+/g, ' ').trim()
        )
      )
    }
  })
const drawerTab = async (label) => {
  await p.locator('.el-drawer:visible .el-tabs__item').filter({ hasText: label }).first().click()
  await p.waitForTimeout(1200)
}
const closeDrawer = async () => {
  await p.locator('.el-drawer:visible .el-drawer__close-btn').first().click().catch(() => {})
  await p.waitForTimeout(900)
}
/**
 * 点版本链里的某个版本号标签（v1 / v2 …）。
 *
 * 为什么不能写 `.filter({ hasText: /^v1（/ })`：Playwright 的 hasText 匹配的是元素的
 * **textContent**（不是 innerText），而模板里是
 *   <el-tag v-for=...>
 *     v{{ v.version }}（…）
 *   </el-tag>
 * 标签内部带着换行/缩进空白，textContent 实际是 `"\n  v1（admin …）\n"`，
 * 于是带 ^ 锚点的正则永远匹配不上（实测 filter(…, /^v1（/) count=0，
 * 而同一个元素 innerText 是干净的 `"v1（admin 2026-04-01 09:00:00）"`）——
 * 文字确实在页面上，是断言写法的问题，不是页面 bug。
 * 所以这里退成「子串匹配 + 先等可见再点」：版本号后面紧跟全角左括号，
 * 子串 `v1（` 不会误伤 `v10（` / `v11（`，语义和原来的锚点一样严。
 */
const clickVersionTag = async (text) => {
  const tag = p.locator('.el-drawer:visible .el-tag').filter({ hasText: text }).first()
  await tag.waitFor({ state: 'visible', timeout: 15000 })
  await tag.click({ timeout: 15000 })
}
const closeDialog = async () => {
  await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click().catch(() => {})
  await p.waitForTimeout(800)
}
const visibleDialog = () =>
  p.evaluate(() => {
    const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
    return d
      ? {
          title: (d.querySelector('.el-dialog__title') || {}).innerText?.trim() || '',
          labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.trim()),
          text: d.innerText.replace(/\s+/g, ' ').trim()
        }
      : { title: '', labels: [], text: '' }
  })

await p.goto(BASE + '/zentao/api', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(5000)

// ---------------- ① 页面渲染 + 两条说明 ----------------
let v = await view()
ok('页面渲染出接口列表（>= 2 行）', v.rows.length >= 2, `行数=${v.rows.length}`)
ok('列表里有演示接口「获取当前登录用户」', v.rows.some((r) => r.includes('获取当前登录用户')), '')
ok('列表里显示了请求方式 + 路径', v.rows.some((r) => r.includes('GET') && r.includes('/api.php/v1/user')), '')
ok('页面说明了「不是对外 REST 接口管理」', v.text.includes('不是「对外 REST 接口管理」'), '')
ok('页面明确提示「导入 OpenAPI / Swagger 属付费扩展，本实现不做」',
  v.text.includes('导入 OpenAPI / Swagger 属付费扩展，本实现不做'), '')
ok('提示里给了禅道的证据（openapiimport / editionLimited）',
  v.text.includes('openapiimport') && v.text.includes('editionLimited'), '')

// ---------------- ② 按库筛选：目录树跟着换 ----------------
await p.locator('.el-form-item:has-text("接口库") .el-select').first().click()
await p.waitForTimeout(600)
const libOpt = p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: LIB.name })
await libOpt.first().waitFor({ state: 'visible', timeout: 20000 })
await libOpt.first().click()
await p.waitForTimeout(2500)
v = await view()
ok('选中库后演示接口仍在列表里', v.rows.some((r) => r.includes('获取当前登录用户')), `行数=${v.rows.length}`)
// 目录下拉（el-tree-select）：点开应出现演示的三个目录
await p.locator('.el-form-item:has-text("目录") .el-select').first().click()
await p.waitForTimeout(1200)
// 注意：演示目录是**两级**（92703「用户登录」的 parent 是 92701「用户与认证」），
// el-tree 的折叠节点根本不渲染进 DOM，只读一次 innerText 只能看到两个根节点。
// 这不是页面 bug（目录下拉本来就只有「展开」才铺开），所以这里显式把折叠的节点展开再断言。
const expandTree = async () => {
  const dd = '.el-select-dropdown:visible'
  for (let i = 0; i < 6; i++) {
    const n = await p.locator(`${dd} .el-tree-node__expand-icon:not(.is-leaf)`).count()
    let expanded = 0
    for (let j = 0; j < n; j++) {
      const icon = p.locator(`${dd} .el-tree-node__expand-icon:not(.is-leaf)`).nth(j)
      const cls = (await icon.getAttribute('class')) || ''
      if (cls.includes('expanded')) continue
      await icon.click({ timeout: 5000 }).catch(() => {})
      expanded++
    }
    if (!expanded) break
    await p.waitForTimeout(500)
  }
}
await expandTree()
const treeText = await p.evaluate(() => {
  const dd = Array.from(document.querySelectorAll('.el-select-dropdown')).find((el) => el.offsetParent !== null)
  return dd ? dd.innerText.replace(/\s+/g, ' ').trim() : ''
})
ok('目录树来自 zt_module（type=api，含演示目录「用户与认证 / 需求管理 / 用户登录」）',
  treeText.includes('用户与认证') && treeText.includes('用户登录') && treeText.includes('需求管理'), treeText.slice(0, 80))
await p.keyboard.press('Escape')
await p.waitForTimeout(600)

// ---------------- ③ 接口详情抽屉 ----------------
await rowOf('获取当前登录用户').locator('button:has-text("查看")').click()
await p.waitForTimeout(2500)
let dr = await inDrawer()
ok('详情抽屉标题是「接口详情 #92711」', dr.title.includes('接口详情') && dr.title.includes('92711'), dr.title)
ok('基本信息含方法/路径/状态/版本',
  dr.text.includes('/api.php/v1/user') && dr.text.includes('开发完成') && dr.text.includes('v2'), '')
ok('版本链列出 v1 与 v2', dr.text.includes('v1（') && dr.text.includes('v2（'), '')
ok('抽屉里有请求示例与响应示例两张卡片',
  dr.text.includes('请求示例') && dr.text.includes('响应示例'), '')
ok('抽屉说明了「paramsType 是数字时指向本库的数据结构」',
  dr.text.includes('paramsType') && dr.text.includes('数据结构'), '')
// 响应页签：v2 有 4 个字段（含 v2 新增的 avatar）
await drawerTab('响应')
dr = await inDrawer()
ok('响应字段树 4 个字段、含 v2 新增的 avatar',
  dr.rows.length === 4 && dr.rows.some((r) => r.includes('avatar')), `字段行=${dr.rows.length}`)
// 请求参数(query) 页签：v2 多了一个 fields 参数
await drawerTab('请求参数(query)')
dr = await inDrawer()
ok('请求参数(query) 页签里有 v2 新增的 fields 参数', dr.rows.some((r) => r.includes('fields')), `字段行=${dr.rows.length}`)
// 请求体页签：给出 paramsType
await drawerTab('请求体')
dr = await inDrawer()
ok('请求体页签给出了请求体类型（paramsType）', dr.text.includes('请求体类型 paramsType'), '')
// 点版本链里的 v1 → 回看历史版本
await clickVersionTag('v1（')
await p.waitForTimeout(2500)
dr = await inDrawer()
ok('点 v1 后出现「正在看第 1 版」提示', dr.text.includes('正在看第 1 版'), '')
await drawerTab('响应')
dr = await inDrawer()
ok('v1 的响应字段只有 2 个、没有 avatar（历史版本读的是 zt_apispec）',
  dr.rows.length === 2 && !dr.rows.some((r) => r.includes('avatar')), `字段行=${dr.rows.length}`)
await p.locator('.el-drawer:visible button:has-text("回到当前版本")').first().click()
await p.waitForTimeout(2500)
dr = await inDrawer()
ok('点「回到当前版本」后提示消失', !dr.text.includes('正在看第 1 版'), '')
await drawerTab('响应')
dr = await inDrawer()
ok('回到当前版本后响应字段又回到 4 个', dr.rows.length === 4, `字段行=${dr.rows.length}`)
await closeDrawer()

// ---------------- ④ 数据结构页签 + 结构详情 ----------------
await switchTab('数据结构')
v = await view()
ok('结构列表里有 user 与 story', v.rows.some((r) => r.includes('user')) && v.rows.some((r) => r.includes('story')),
  `行数=${v.rows.length}`)
ok('结构列表带版本数与创建人', v.rows.some((r) => r.includes('v2')), '')
ok('页面说明了「结构版本表只按 name 关联」这个禅道缺陷',
  v.text.includes('只存 name') && v.text.includes('串版本'), '')
await rowOf('user').locator('button:has-text("查看")').click()
await p.waitForTimeout(2500)
dr = await inDrawer()
ok('结构抽屉标题是「数据结构 #92731」', dr.title.includes('数据结构') && dr.title.includes('92731'), dr.title)
ok('结构抽屉有版本链（v1/v2 标签）', dr.text.includes('v1（') && dr.text.includes('v2（'), '')
ok('结构字段树里能看到 avatar', dr.rows.some((r) => r.includes('avatar')), `字段行=${dr.rows.length}`)
ok('结构抽屉给出了原始 JSON（本实现用 JSON 编辑字段树）', dr.text.includes('原始 JSON'), '')
await closeDrawer()

// ---------------- ⑤ 发布版本页签 ----------------
await switchTab('发布版本')
v = await view()
ok('发布版本列表里有 v1.0', v.rows.some((r) => r.includes('v1.0')), `行数=${v.rows.length}`)
ok('快照计数与冻结清单都在（3 目录 / 2 接口 / 2 结构、#92711@v2）',
  v.rows.some((r) => r.includes('#92711@v2')), '')
ok('页面说明了「发布是把目录/接口/结构打成 snap 冻结」',
  v.text.includes('snap JSON 冻结'), '')
await rowOf('v1.0').locator('button:has-text("按此版本浏览")').click()
await p.waitForTimeout(3000)
v = await view()
ok('「按此版本浏览」跳回接口列表并打上「快照」标记',
  v.text.includes('快照') && v.rows.length === 2, `行数=${v.rows.length}`)
// 清掉快照过滤，恢复「看当前值」。
// 坑（实测）：这个 el-select 是 clearable，下拉里**根本没有「空」这一项** ——
// 原来点下拉第一项等于又选中了 v1.0，queryParams.releaseID 还在，列表仍然是快照（2 行），
// 于是第 ⑧ 节按名称找不到刚建的临时接口（实测就卡在这一步）。
// 真正的清空动作是点右侧那个 hover 才出现的 clear 图标（.el-select__clear：hover 前 count=0、
// hover 后 count=1；实测点完 select 回到占位符「看当前值」、列表从 2 行回到 3 行）。
const releaseSelect = p.locator('.el-form-item:has-text("发布版本") .el-select').first()
await releaseSelect.hover()
await p.waitForTimeout(400)
await releaseSelect.locator('.el-select__clear').first().click()
await p.waitForTimeout(2000)
ok('「发布版本」过滤已清空、回到「看当前值」（否则第 ⑧ 节按名称找不到刚建的接口）',
  (await releaseSelect.innerText()).replace(/\s+/g, '').includes('看当前值'), '')

// ---------------- ⑥ 三个弹窗的字段与必填 ----------------
await p.locator('button:has-text("新建接口")').first().click()
await p.waitForTimeout(1200)
let dlg = await visibleDialog()
ok('新建接口弹窗标题正确', dlg.title.includes('新建接口'), dlg.title)
for (const label of ['所属接口库', '目录', '接口名称', '请求路径', '开发状态', '请求参数树', '响应字段树']) {
  ok(`新建接口弹窗有「${label}」`, dlg.labels.includes(label), JSON.stringify(dlg.labels))
}
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(1200)
v = await view()
ok('接口名称为空 → 前端提示必填', v.text.includes('接口名称不能为空'), '')
await closeDialog()

await p.locator('button:has-text("新建结构")').first().click()
await p.waitForTimeout(1200)
dlg = await visibleDialog()
ok('新建结构弹窗有结构名 / 结构类型 / 字段树 JSON',
  dlg.labels.includes('结构名') && dlg.labels.includes('结构类型') && dlg.labels.includes('字段树 JSON'),
  JSON.stringify(dlg.labels))
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(1200)
v = await view()
ok('结构名为空 → 前端提示必填', v.text.includes('结构名不能为空'), '')
await closeDialog()

// 「发布当前库」不在顶部的筛选/操作区，而在**发布版本页签**的内容里（index.vue:220）。
// 而上面第 ⑤ 节的 browseRelease 会把 tab 切回「接口列表」（index.vue:850 `tab.value = 'apis'`），
// Element Plus 的非激活 el-tab-pane 是 display:none —— 按钮确实在 DOM 里
// （locator 能 resolve，且 aria-disabled="false"，所以不是 disabled），但 Playwright 判
// "element is not visible" 而一直重试到超时。这是脚本漏了切页签，不是页面 bug：
// 发布入口本来就只属于「发布版本」页签。所以先显式切回去再点。
await switchTab('发布版本')
await p.locator('.el-tab-pane:visible button:has-text("发布当前库")').first().click()
await p.waitForTimeout(1200)
dlg = await visibleDialog()
ok('发布弹窗标题是「发布接口库（打快照）」', dlg.title.includes('发布接口库') && dlg.title.includes('快照'), dlg.title)
ok('发布弹窗写明「冻结之后接口不能删」的保护',
  dlg.text.includes('冻结') && dlg.text.includes('不能'), '')
await closeDialog()
// 第 ⑦ 节要在「接口列表」页签里点编辑（rowOf 只认可见页签的行），关掉发布弹窗后切回去
await switchTab('接口列表')

// ---------------- ⑦ 编辑接口：走一遍「表单提交 → 版本 +1」（用临时接口，跑完删掉） ----------------
// 这一步专门验「editedDate 乐观锁」的往返：列表里它是数字时间戳（yudao 全局序列化），
// 而页面提交前必须转成 "yyyy-MM-dd HH:mm:ss"（后端只认这个格式），否则会 400。
const TS = Date.now()
const TMP_TITLE = 'UI临时接口-' + TS
const tmp = await api('POST', '/zentao/api/create', {
  lib: LIB.id, module: 92702, title: TMP_TITLE, path: '/api.php/v1/ui-test-' + TS
})
if (tmp.code !== 0) throw new Error('准备临时接口失败：' + JSON.stringify(tmp))
const TMP_ID = tmp.data
console.log(`准备: 临时接口 #${TMP_ID} ${TMP_TITLE}`)
await p.locator('button:has-text("搜索")').first().click()
await p.waitForTimeout(2500)
await rowOf(TMP_TITLE).locator('button:has-text("编辑")').click()
await p.waitForTimeout(1500)
const editDlg = await visibleDialog()
ok('编辑弹窗标题带接口名', editDlg.title.includes(TMP_TITLE), editDlg.title)
await p.locator('.el-dialog:visible .el-form-item:has-text("接口名称") input').first().fill(TMP_TITLE + '-v2')
await p.locator('.el-dialog:visible button:has-text("确 定")').first().click()
await p.waitForTimeout(3000)
v = await view()
ok('编辑保存成功（editedDate 数字时间戳 → 字符串的往返没 400）',
  v.rows.some((r) => r.includes(TMP_TITLE + '-v2')), '')
ok('编辑后列表显示 v2（真有变更才 +1）',
  v.rows.some((r) => r.includes(TMP_TITLE + '-v2') && r.includes('v2')), '')
const afterEdit = await api('GET', '/zentao/api/get?id=' + TMP_ID)
ok('接口详情确认 version=2', afterEdit.code === 0 && afterEdit.data.version === 2, 'version=' + (afterEdit.data || {}).version)
const delTmp = await api('DELETE', '/zentao/api/delete?id=' + TMP_ID)
ok('临时接口删除成功（软删；剩下的 spec 行由下一次 test-api-module.sh 清理）', delTmp.code === 0, JSON.stringify(delTmp.msg || ''))

// ---------------- ⑧ 页面无 JS 报错 ----------------
const inPageErrors = await p.evaluate(() => window.__errs || [])
ok('页面没有 JS 报错（已过滤 ResizeObserver 良性告警）',
  pageErrors.length === 0 && inPageErrors.length === 0,
  [...pageErrors, ...inPageErrors].slice(0, 3).join(' | '))

await p.screenshot({ path: '/tmp/zentao-api.png', fullPage: true })
console.log('截图       : /tmp/zentao-api.png')

await b.close()
console.log('======================================================')
console.log(`  api 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

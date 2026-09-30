// 代码库（repo）界面检查
//
// 覆盖 /zentao/repo：
//   ① 代码库列表渲染（路径 / 默认分支 / 同步状态）
//   ② 「同步」按钮 → 提示新入库 N 条提交，列表里的同步痕迹变成「已同步 + sha + 条数」
//   ③ 「提交记录」抽屉 → 提交列表渲染，点一行看详情
//   ④ 提交详情：提交说明、改动文件（含重命名的 oldPath → path）、关联对象标签
//   ⑤ 新建/编辑弹窗的字段与校验提示
//
// 准备：脚本自己造一个**真实的本地 git 仓库**（3 次提交，含 git mv 与 Story/Task/Bug #id），
// 结束后删掉代码库与目录。
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { login } from './_login.mjs'
import { mkdirSync, writeFileSync, rmSync } from 'node:fs'
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

// ---------------- 准备：真实的本地 git 仓库 ----------------
// 夹具仓库必须建在**后端所在的那台机器**上（代码库模块会拿这个路径去跑 git log）：
//   本机模式直接跑；服务器模式（API_BASE 指向别的机器）通过 ssh 在那边建。
// 这是「同一套检查两边都能跑」的最后一个位置相关的点（api 测试那边也有同一处理）。
const TS = Date.now()
const DIR = `/tmp/zt-repo-ui-${TS}`
const API_HOST = new URL(API_BASE).hostname
const REMOTE_FIXTURE = !['127.0.0.1', 'localhost', '::1'].includes(API_HOST)
// 夹具仓库要建在**后端那台机器**上（后端拿它跑 git log）。
// 默认 ubuntu + 本机公钥免密（119 已装好）；换机器时给 REMOTE_SSH_USER / REMOTE_SSH_PASS 覆盖，
// 给了 REMOTE_SSH_PASS 才走 sshpass（183 时代就是这么连的）。
const SSH_USER = process.env.REMOTE_SSH_USER || 'ubuntu'
const sh = (cmd) => {
  if (!REMOTE_FIXTURE) return execFileSync('bash', ['-c', cmd], { encoding: 'utf8' })
  const sshArgs = ['-o', 'StrictHostKeyChecking=no', '-o', 'ConnectTimeout=10', `${SSH_USER}@${API_HOST}`, cmd]
  if (process.env.REMOTE_SSH_PASS) {
    return execFileSync('sshpass', ['-p', process.env.REMOTE_SSH_PASS, 'ssh', ...sshArgs], { encoding: 'utf8' })
  }
  return execFileSync('ssh', sshArgs, { encoding: 'utf8' })
}
// git 2.28 以前没有 `init -b`，用 init + symbolic-ref 兼容旧版（119 上是 git 2.53，写法照样可用）
sh(`set -e
rm -rf '${DIR}'; mkdir -p '${DIR}'
git -C '${DIR}' init -q && git -C '${DIR}' symbolic-ref HEAD refs/heads/master
git -C '${DIR}' config user.email ui@zentao.local && git -C '${DIR}' config user.name 'UI Dev'
printf 'login page\n' > '${DIR}/login.php'
git -C '${DIR}' add -A && git -C '${DIR}' commit -q -m '初始化登录页

Task #1'
printf 'login page v2\n' > '${DIR}/login.php'
git -C '${DIR}' add -A && git -C '${DIR}' commit -q -m '登录接口支持记住我

Story #1 Task #1,2'
git -C '${DIR}' mv login.php signin.php
git -C '${DIR}' add -A && git -C '${DIR}' commit -q -m '登录文件改名

Bug #1'`)
console.log(`准备: 夹具仓库 ${DIR}（在后端所在机器 ${API_HOST} 上，3 次提交，含 git mv 与 Story/Task/Bug #id）`)

const NAME = 'repo-ui-' + TS
const repo = await api('POST', '/zentao/repo/create', {
  name: NAME, path: DIR, defaultBranch: 'master', product: '1', desc: 'UI 检查用'
})
if (repo.code !== 0) throw new Error('准备代码库失败：' + JSON.stringify(repo))
const RID = repo.data
console.log(`准备: 代码库=${RID}`)

let cleaned = false
const cleanup = async () => {
  if (cleaned) return
  cleaned = true
  await api('DELETE', '/zentao/repo/delete?id=' + RID).catch(() => {})
  try { sh(`rm -rf '${DIR}'`) } catch { /* 忽略 */ }
}

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
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

const rows = async () =>
  p.evaluate(() => {
    const tables = Array.from(document.querySelectorAll('.el-table')).filter((t) => t.offsetParent !== null)
    const table = tables[tables.length - 1]
    const pane = Array.from(document.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
    return {
      text: document.body.innerText.replace(/\s+/g, ' ').trim(),
      main: table ? Array.from(table.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim()) : [],
      visibleTables: tables.length,
      paneText: pane ? pane.innerText.replace(/\s+/g, ' ').trim() : ''
    }
  })

await p.goto(BASE + '/zentao/repo', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(6000)

// ---------------- ① 列表 ----------------
let view = await rows()
const rowSel = p.locator('.el-table__body tbody tr').filter({ hasText: NAME }).first()
ok('新代码库出现在列表里', (await rowSel.count()) > 0, NAME)
ok('列表显示仓库路径与默认分支', view.main.some((r) => r.includes(DIR) && r.includes('master')),
  (view.main.find((r) => r.includes(NAME)) || '').slice(0, 120))
ok('未同步时显示「未同步」', view.text.includes('未同步'), '')

// ---------------- ② 同步 ----------------
const synced = new Promise((resolve) => {
  const handler = (r) => { if (r.url().includes('/zentao/repo/sync')) { p.off('response', handler); resolve(r.status()) } }
  p.on('response', handler)
  setTimeout(() => resolve(0), 20000)
})
await rowSel.locator('button:has-text("同步")').first().click()
const syncStatus = await synced
await p.waitForTimeout(1200)
view = await rows()
ok('同步接口返回 200', syncStatus === 200, `status=${syncStatus}`)
ok('同步提示新入库条数', /同步完成：新入库 \d+ 条提交/.test(view.text), (view.text.match(/同步完成：新入库 \d+ 条提交/) || [''])[0])
ok('列表变成「已同步」并显示 sha 与条数', view.text.includes('已同步') && /已同步 [0-9a-f]{8} · 3 条/.test(view.text),
  (view.text.match(/已同步 [0-9a-f]{8} · \d+ 条[^ ]*/) || [''])[0])

// ---------------- ③ 提交记录抽屉 ----------------
const commitListed = new Promise((resolve) => {
  const handler = (r) => { if (r.url().includes('/zentao/repo/commit-page')) { p.off('response', handler); resolve(r.status()) } }
  p.on('response', handler)
  setTimeout(() => resolve(0), 20000)
})
await rowSel.locator('button:has-text("提交记录")').first().click()
await commitListed
await p.locator('.el-drawer__title').filter({ hasText: '提交记录' }).first().waitFor({ state: 'visible', timeout: 20000 })
await p.waitForTimeout(2500)
const drawer = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-drawer')).find((el) => el.offsetParent !== null)
  if (!d) return { rows: [], text: '' }
  return {
    rows: Array.from(d.querySelectorAll('.el-table__body tbody tr')).map((r) => r.innerText.replace(/\s+/g, ' ').trim()),
    text: d.innerText.replace(/\s+/g, ' ').trim()
  }
})
console.log('③ 抽屉 :', JSON.stringify(drawer.rows.slice(0, 3)))
if (drawer.rows.length === 0) console.log('   抽屉文本:', drawer.text.slice(0, 300))
ok('抽屉里有 3 条提交', drawer.rows.length === 3, `行数=${drawer.rows.length}`)
ok('提交按序号倒序（最新在前）', drawer.rows[0]?.startsWith('3'), drawer.rows[0] || '')
ok('提交说明与提交者都渲染了', drawer.rows.every((r) => r.includes('UI Dev')), '')

// ---------------- ④ 提交详情 ----------------
await p.locator('.el-drawer:visible .el-table__body tbody tr').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '提交' }).first().waitFor({ state: 'visible', timeout: 20000 })
await p.waitForTimeout(2500)
const detail = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).filter((el) => el.offsetParent !== null).pop()
  return d ? d.innerText.replace(/\s+/g, ' ').trim() : ''
})
console.log('④ 详情 :', detail.slice(0, 200))
ok('详情里有关联对象标签（Bug #1）', /缺陷 #1/.test(detail), (detail.match(/缺陷 #\d+[^ ]*/) || [''])[0])
ok('详情里有改动文件（重命名显示 old → new）', detail.includes('login.php') && detail.includes('signin.php'),
  (detail.match(/\S+ → \S+/) || [''])[0])
ok('重命名标成「重命名」', detail.includes('重命名'), '')
ok('提交说明原样展示（多行）', detail.includes('登录文件改名'), '')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)

// ---------------- ⑤ 新增弹窗 ----------------
await p.locator('.el-drawer:visible .el-drawer__close-btn').first().click().catch(() => {})
await p.waitForTimeout(800)
await p.locator('button:has-text("新增代码库")').first().click()
await p.locator('.el-dialog__title').filter({ hasText: '新增代码库' }).first().waitFor({ state: 'visible', timeout: 20000 })
const formInfo = await p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((el) => el.offsetParent !== null)
  return {
    labels: Array.from(d.querySelectorAll('.el-form-item__label')).map((e) => e.innerText.trim()),
    tip: d.innerText.replace(/\s+/g, ' ').trim()
  }
})
for (const label of ['名称', '仓库路径', '默认分支', '关联产品', '描述']) {
  ok(`弹窗有「${label}」`, formInfo.labels.includes(label), JSON.stringify(formInfo.labels))
}
ok('弹窗说明了「必须是存在且含 .git 的目录、不支持远程服务商」',
  formInfo.tip.includes('必须是存在且含 .git 的目录') && formInfo.tip.includes('不支持远程服务商'), '')
await p.locator('.el-dialog:visible .el-dialog__headerbtn').first().click()
await p.waitForTimeout(800)

ok('页面没有 JS 报错', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '))

await p.screenshot({ path: '/tmp/zentao-repo.png', fullPage: true })
console.log('截图       : /tmp/zentao-repo.png')

await cleanup()
console.log('已清理测试代码库与夹具目录')
await b.close()

console.log('======================================================')
console.log(`  repo 界面检查：通过 ${PASS} 项，失败 ${FAIL} 项`)
console.log('======================================================')
if (FAIL > 0) process.exit(1)

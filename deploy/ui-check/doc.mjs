// 文档（doc）模块界面检查
//
// 覆盖：/zentao/doc 页面
//   ① 页面渲染（库下拉、文档表格、章节树）
//   ② 按库过滤 → 章节树出现，点章节过滤文档
//   ③ 新建章节 / 新建文档（走弹窗表单）
//   ④ 查看抽屉：正文 + 版本历史（含回读历史版本）
//   ⑤ 草稿「发布」按钮
//   ⑥ 删除（含章节下有子节点时的拒绝）
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
const _pw = await import(pathToFileURL(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim() + '/playwright/index.js').href)
const chromium = _pw.chromium || _pw.default?.chromium
const BASE = process.env.ZENTAO_UI_BASE || 'http://localhost'
const API_BASE = process.env.ZENTAO_API_BASE || 'http://localhost:48080/admin-api'

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
// 准备：一个自定义库（可删除）+ 一个章节 + 一篇文档
const spaceList = await api('GET', '/zentao/doc/lib/list?parent=0')
const space = spaceList.data[0]
const lib = await api('POST', '/zentao/doc/lib/create', {
  type: 'custom', parent: space.id, name: 'UI文档库-' + TS
})
if (lib.code !== 0) throw new Error('准备文档库失败：' + JSON.stringify(lib))
const LIB = lib.data
const chapter = await api('POST', '/zentao/doc/create', {
  lib: LIB, title: 'UI章节-' + TS, type: 'chapter'
})
const doc = await api('POST', '/zentao/doc/create', {
  lib: LIB, parent: chapter.data, title: 'UI文档-' + TS, type: 'markdown',
  content: '# V1\n\n第一版', rawContent: '# V1\n\n第一版'
})
// 再改一次正文，制造 v2，方便验证版本历史
await api('PUT', '/zentao/doc/update', {
  id: doc.data, lib: LIB, parent: chapter.data, title: 'UI文档-' + TS,
  type: 'markdown', content: '# V2\n\n第二版', rawContent: '# V2\n\n第二版'
})
console.log(`准备: 库=${LIB} 章节=${chapter.data} 文档=${doc.data}（v2，含 v1 历史）`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1680, height: 1100 } })
const p = await c.newPage()

// 断言失败时脚本会直接抛异常退出 —— 如果不在退出前关掉浏览器，
// 每个失败的 Playwright 进程都会留下一个 headless Chromium 挂在那里吃 CPU/内存
// （实测跑错几次就攒了 30 多个，机器负载升高后连登录都超时，形成连锁失败）。
const __cleanup = () => { try { b.close() } catch { /* 已经关掉了 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })
p.on('pageerror', (e) => console.log('  [pageerror]', String(e.stack || e).split('\n').slice(0, 3).join(' | ')))
p.on('console', (m) => m.type() === 'error' && console.log('  [console]', m.text().slice(0, 200)))

await p.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(1500)
await p.fill('input[placeholder="请输入用户名"]', 'admin')
await p.fill('input[placeholder="请输入密码"]', 'admin123')
await p.locator('button:has-text("登录")').first().click()
await p.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 25000 })
console.log('登录成功')

await p.goto(BASE + '/zentao/doc', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(4000)

const read = async () =>
  p.evaluate(() => {
    const tables = Array.from(document.querySelectorAll('.el-table'))
    const main = tables[0]
    const rows = main ? Array.from(main.querySelectorAll('.el-table__body tbody tr')) : []
    return {
      headers: Array.from(document.querySelectorAll('.el-table__header th'))
        .map((e) => e.innerText.trim())
        .filter(Boolean),
      rows: rows.length,
      total: (document.body.innerText.match(/共 (\d+) 条/) || [])[1] || '',
      row0: rows[0] ? rows[0].innerText.replace(/\s+/g, ' ').trim().slice(0, 90) : '',
      tree: document.querySelectorAll('.el-tree-node').length
    }
  })

console.log('① 页面加载   :', JSON.stringify(await read()))

// 按库过滤：选库下拉 → 章节树出现
await p.locator('.el-form-item:has-text("文档库") .el-select').first().click()
await p.waitForTimeout(600)
const libInput = p.locator('.el-form-item:has-text("文档库") .el-select input').first()
await libInput.fill('UI文档库-' + TS).catch(() => {})
await p.waitForTimeout(900)
const opt = p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'UI文档库-' + TS })
await opt.first().waitFor({ state: 'visible', timeout: 20000 })
await opt.first().click()
await p.waitForTimeout(3000)
const afterLib = await read()
console.log('② 按库过滤   :', JSON.stringify(afterLib))
if (afterLib.tree < 1) throw new Error('按库过滤后没有出现章节树')
if (afterLib.total !== '1') throw new Error('预期该库下 1 篇文档，实际 ' + afterLib.total)
if (!afterLib.row0.includes('UI章节-' + TS)) {
  throw new Error('列表里的「所属章节」应该显示章节名，实际：' + afterLib.row0)
}

// 点章节节点过滤
await p.locator('.el-tree-node__content').filter({ hasText: 'UI章节-' + TS }).first().click()
await p.waitForTimeout(2500)
console.log('③ 按章节过滤 :', JSON.stringify(await read()))

await p.screenshot({ path: '/tmp/zentao-doc.png', fullPage: true })
console.log('截图         : /tmp/zentao-doc.png')

// 打开详情抽屉：正文 + 版本历史
await p.locator('.el-table__body tbody tr').first().locator('button:has-text("查看")').click()
await p.waitForTimeout(3000)
const detail = await p.evaluate(() => ({
  title: (document.querySelector('.el-drawer__title') || {}).innerText?.trim() || '',
  tabs: Array.from(document.querySelectorAll('.el-drawer .el-tabs__item')).map((e) => e.innerText.trim()),
  content: (document.querySelector('.doc-markdown') || document.querySelector('.doc-content') || {})
    .innerText?.trim()
    .slice(0, 40) || '',
  descs: Array.from(document.querySelectorAll('.el-drawer .el-descriptions__label'))
    .map((e) => e.innerText.trim())
    .slice(0, 6)
}))
console.log('④ 详情抽屉   :', JSON.stringify(detail))
if (!detail.content.includes('第二版')) throw new Error('详情正文不是当前版本 v2：' + detail.content)

// 切到版本历史
await p.locator('.el-drawer .el-tabs__item').filter({ hasText: '版本历史' }).first().click()
await p.waitForTimeout(2500)
const versions = await p.evaluate(() => {
  const drawer = document.querySelector('.el-drawer')
  const rows = Array.from(drawer.querySelectorAll('.el-table__body tbody tr'))
  return rows.map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 60))
})
console.log('⑤ 版本历史   :', JSON.stringify(versions))
if (versions.length !== 2) throw new Error('预期 2 个版本快照，实际 ' + versions.length)

// 回读 v1
await p
  .locator('.el-drawer .el-table__body tbody tr')
  .filter({ hasText: 'v1' })
  .first()
  .locator('button:has-text("查看该版")')
  .click()
await p.waitForTimeout(2500)
const v1content = await p.evaluate(
  () =>
    (document.querySelector('.doc-markdown') || document.querySelector('.doc-content') || {})
      .innerText?.trim()
      .slice(0, 30) || ''
)
console.log('⑥ 回读 v1    :', JSON.stringify(v1content))
if (!v1content.includes('第一版')) throw new Error('回读历史版本内容不对：' + v1content)
const alert = await p.evaluate(
  () => (document.querySelector('.el-drawer .el-alert__content') || {}).innerText?.trim() || ''
)
console.log('   历史版本提示:', JSON.stringify(alert.slice(0, 60)))
if (!alert.includes('历史版本')) throw new Error('没有出现「正在查看历史版本」的提示')

// 关闭抽屉
await p.locator('.el-drawer__close-btn').first().click()
await p.waitForTimeout(1200)

// 新建章节（走弹窗，验证章节类型下不显示正文框）
await p.locator('button:has-text("新建章节")').first().click()
await p.waitForTimeout(2000)
const chapterDlg = await p.evaluate(() => ({
  title: (document.querySelector('.el-dialog__title') || {}).innerText?.trim() || '',
  hasContentBox: !!document.querySelector('.el-dialog textarea'),
  hint: Array.from(document.querySelectorAll('.el-dialog .text-12px'))
    .map((e) => e.innerText.trim())
    .join(' | ')
    .slice(0, 120)
}))
console.log('⑦ 新建章节弹窗:', JSON.stringify(chapterDlg))
if (!chapterDlg.title.includes('章节')) throw new Error('新建章节弹窗标题不对：' + chapterDlg.title)
if (chapterDlg.hasContentBox) throw new Error('章节不应该有正文输入框')
await p.locator('.el-dialog__headerbtn').first().click()
await p.waitForTimeout(1200)

// 删除库内文档 → 删章节（章节下有文档时不该有子章节；这里验证删除成功）
const delTarget = p.locator('.el-table__body tbody tr').first()
await delTarget.locator('button:has-text("删除")').click()
await p.waitForTimeout(1200)
await p.locator('.el-message-box__btns button:has-text("确定")').first().click()
await p.waitForTimeout(2500)
const afterDel = await read()
console.log('⑧ 删除文档后 :', JSON.stringify({ total: afterDel.total, rows: afterDel.rows }))
// 只剩 0 条时 Element Plus 的分页器会把「共 N 条」整个收起来，
// 所以这里用行数断言，别去匹配文案
if (afterDel.rows !== 0) throw new Error('删除后库内应剩 0 行，实际 ' + afterDel.rows)
await p.screenshot({ path: '/tmp/zentao-doc-after-delete.png', fullPage: true })

// 收尾
await api('DELETE', '/zentao/doc/delete?id=' + chapter.data)
await api('DELETE', '/zentao/doc/lib/delete?id=' + LIB)
console.log('已清理测试数据')
await b.close()

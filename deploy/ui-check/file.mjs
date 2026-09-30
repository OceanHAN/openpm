// 附件（file）模块界面检查
//
// 覆盖：需求详情抽屉里的「附件」Tab（公共组件 AttachmentPanel.vue）
//   ① 已有附件时列表能渲染（文件名/大小/上传人/下载数）
//   ② 通过界面真正上传一个文件（走 el-upload 的 http-request）
//   ③ 下载按钮能拿到后端返回的真实地址并打开新标签
//   ④ 删除附件后列表清空
// 附件是跨模块的公共能力，这里用「需求详情」作为宿主页面验证。
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { writeFileSync } from 'node:fs'
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
const prod = await api('POST', '/zentao/product/create', { name: 'UIFileProd-' + TS, code: 'UIF' + TS, type: 'normal', PO: 'admin' })
const pid = prod.data
const story = await api('POST', '/zentao/story/create', { product: pid, title: 'UI附件需求-' + TS, pri: 3, category: 'feature' })
const sid = story.data

// 先通过接口挂一个附件，验证「已有附件能渲染」
const form = new FormData()
form.append('file', new Blob(['attachment-from-api-' + TS], { type: 'text/plain' }), 'api-' + TS + '.txt')
const up = await fetch(`${API_BASE}/zentao/file/upload?objectType=story&objectID=${sid}`, {
  method: 'POST',
  headers: { Authorization: 'Bearer ' + tok, 'tenant-id': '1' },
  body: form
}).then((r) => r.json())
if (up.code !== 0) throw new Error('准备附件失败：' + JSON.stringify(up))
console.log('准备: 产品=' + pid + ' 需求=' + sid + ' 预置附件=' + up.data.id + ' url=' + up.data.url)

// 界面上传用的本地文件
const localFile = `/tmp/zentao-ui-upload-${TS}.md`
writeFileSync(localFile, `# UI 上传测试 ${TS}\n`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1680, height: 1050 }, acceptDownloads: true })
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
// 下载接口的返回地址：比读 window.open 出来的新标签 URL 稳得多
// （新标签常常还停在 about:blank，或浏览器把文件直接下载掉压根不开标签页）
const dlUrls = []
p.on('response', async (res) => {
  if (!res.url().includes('/zentao/file/download')) return
  try {
    const body = await res.json()
    if (body.code === 0 && body.data) dlUrls.push(body.data)
  } catch {
    /* 忽略非 JSON 响应 */
  }
})

// 读附件表格
const read = async () =>
  p.evaluate(() => {
    const panel = document.querySelector('.attachment-panel')
    if (!panel) return { found: false }
    const rows = Array.from(panel.querySelectorAll('.el-table__body tbody tr'))
    return {
      found: true,
      summary: (panel.querySelector('.text-14px') || {}).innerText?.trim() || '',
      tabLabel: (document.querySelector('.el-tabs__item.is-active') || {}).innerText?.trim() || '',
      rows: rows.length,
      row0: rows[0] ? rows[0].innerText.replace(/\s+/g, ' ').trim() : '',
      // 单元格按列取，避免用「行文本末尾的数字」这种脆弱写法（末列是操作按钮）
      cells0: rows[0]
        ? Array.from(rows[0].querySelectorAll('td')).map((td) => td.innerText.replace(/\s+/g, ' ').trim())
        : []
    }
  })

await p.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(1500)
await p.fill('input[placeholder="请输入用户名"]', 'admin')
await p.fill('input[placeholder="请输入密码"]', 'admin123')
await p.locator('button:has-text("登录")').first().click()
await p.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 25000 })
console.log('登录成功')

await p.goto(BASE + '/zentao/story', { waitUntil: 'domcontentloaded' })
await p.waitForTimeout(4000)

// 只筛出本次建的需求：标题就是唯一关键词
await p.fill('input[placeholder="请输入标题关键词"]', 'UI附件需求-' + TS)
await p.locator('button:has-text("搜索")').first().click()
await p.waitForTimeout(2500)
const listRows = await p.evaluate(() => document.querySelectorAll('.el-table__body tbody tr').length)
console.log('① 列表筛出  :', listRows, '行')
if (listRows !== 1) throw new Error('预期筛出 1 行需求，实际 ' + listRows)

await p.locator('.el-table__body tbody tr').first().locator('button:has-text("详情")').click()
await p.waitForTimeout(2500)
const tabNames = await p.evaluate(() =>
  Array.from(document.querySelectorAll('.el-tabs__item')).map((e) => e.innerText.trim())
)
console.log('② 抽屉页签  :', JSON.stringify(tabNames))

await p.locator('.el-tabs__item').filter({ hasText: '附件' }).first().click()
await p.waitForTimeout(2500)
const before = await read()
console.log('③ 已有附件  :', JSON.stringify(before))
if (!before.found) throw new Error('没有找到附件面板（AttachmentPanel 未渲染）')
if (before.rows !== 1) throw new Error('预期 1 个附件，实际 ' + before.rows)

// ==================== 界面上传 ====================
await p.setInputFiles('.attachment-panel input[type=file]', localFile)
await p.waitForTimeout(4000)
const after = await read()
console.log('④ 上传后    :', JSON.stringify(after))
if (after.rows !== 2) throw new Error('上传后预期 2 个附件，实际 ' + after.rows)
const uploadedName = `zentao-ui-upload-${TS}.md`
if (!after.cells0[0]?.includes(uploadedName.slice(0, 12))) {
  throw new Error('最新一行不是刚上传的文件：' + JSON.stringify(after.cells0))
}
console.log('   最新一行   :', after.cells0[0])

// ==================== 下载 ====================
const popupPromise = c.waitForEvent('page', { timeout: 15000 }).catch(() => null)
await p
  .locator('.attachment-panel .el-table__body tbody tr')
  .first()
  .locator('button:has-text("下载")')
  .click()
const popup = await popupPromise
if (popup) {
  await popup.waitForLoadState('domcontentloaded').catch(() => {})
  await popup.close()
}
await p.waitForTimeout(1800)
if (dlUrls.length === 0) throw new Error('界面上点「下载」没有调用 /zentao/file/download')
const dlUrl = dlUrls[dlUrls.length - 1]
console.log('⑤ 下载地址   :', dlUrl.slice(0, 110), popup ? '(已打开新标签)' : '(未开新标签)')
if (!dlUrl.includes('/infra/file/')) throw new Error('下载地址不是文件服务地址：' + dlUrl)
// 真的把字节取回来，确认下载地址可用
const dlBody = await fetch(dlUrl).then((r) => r.text())
if (!dlBody.includes('UI 上传测试 ' + TS)) throw new Error('下载内容与上传内容不一致：' + dlBody.slice(0, 80))
console.log('   下载内容   :', JSON.stringify(dlBody.trim()))
const afterDownload = await read()
console.log('⑥ 下载后计数 :', JSON.stringify(afterDownload.cells0))
// 列顺序：文件名 / 大小 / 上传人 / 上传时间 / 下载 / 操作
const downloads = afterDownload.cells0[4]
if (downloads !== '1') throw new Error('下载后计数预期 1，实际 ' + downloads)

await p.screenshot({ path: '/tmp/zentao-file.png', fullPage: true })
console.log('截图         : /tmp/zentao-file.png')

// ==================== 删除 ====================
const delRow = p.locator('.attachment-panel .el-table__body tbody tr').first()
await delRow.locator('button:has-text("删除")').click()
await p.waitForTimeout(1200)
await p.locator('.el-message-box__btns button:has-text("确定")').first().click()
await p.waitForTimeout(3000)
const afterDelete = await read()
console.log('⑦ 删除后    :', JSON.stringify(afterDelete))
if (afterDelete.rows !== 1) throw new Error('删除后预期 1 个附件，实际 ' + afterDelete.rows)

// 收尾：清掉库里的附件与业务数据
await api('DELETE', `/zentao/file/delete-by-object?objectType=story&objectID=${sid}`)
await api('DELETE', '/zentao/story/delete?id=' + sid)
await api('DELETE', '/zentao/product/delete?id=' + pid)
console.log('已清理测试数据')
await b.close()

// 测试用例（testcase）模块界面检查
//
// 覆盖：/zentao/testcase
//   ① 页面渲染（表头、行数）
//   ② 过滤：状态、待确认开关（needConfirm）
//   ③ 详情抽屉：步骤表的层级编号（1. / 1.1 / 1.2）
//   ④ 版本历史：v2 与 v1 的步骤数不同，回看 v1 步骤数跟着变
//   ⑤ 新建弹窗：步骤编辑器（添加步骤组 → 组内步骤）
//   ⑥ 需求变更提示与「确认需求」按钮
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { login } from './_login.mjs'
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
// 准备：一条带「步骤组」的用例（2 个组 + 3 个步骤），制造 v1→v2 便于验证版本历史
const story = await api('GET', '/zentao/story/page?product=1&pageSize=1')
const storyId = story.data.list[0].id
const created = await api('POST', '/zentao/testcase/create', {
  product: 1, module: 93301, story: storyId, title: 'UI用例-' + TS, type: 'feature',
  stage: 'smoke,feature', pri: 1,
  steps: [{ type: 'step', desc: '打开页面', expect: '正常' }]
})
if (created.code !== 0) throw new Error('准备用例失败：' + JSON.stringify(created))
const CASE = created.data
await api('PUT', '/zentao/testcase/update', {
  id: CASE, product: 1, module: 93301, story: storyId, title: 'UI用例-' + TS, type: 'feature',
  stage: 'smoke,feature', pri: 1,
  steps: [
    { type: 'group', desc: '第一步：准备' },
    { type: 'step', desc: '打开页面', expect: '正常', parent: 0 },
    { type: 'step', desc: '输入账号', expect: '回显', parent: 0 },
    { type: 'group', desc: '第二步：提交' },
    { type: 'step', desc: '点击登录', expect: '进首页', parent: 3 }
  ]
})
console.log(`准备: 用例=${CASE}（v2，5 个步骤：2 组 + 3 步）`)

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1760, height: 1100 } })
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

await login(p, BASE)
console.log('登录成功')

// 等这个页面自己的列表请求回来再断言。
// 不用固定 sleep：新加的页面第一次访问时 Vite 还要现场转换模块，
// 冷启动那次可能慢到几秒以上，固定等待会偶发「表头是空的」。
const listArrived = new Promise((resolve) => {
  const handler = (r) => {
    if (r.url().includes('/zentao/testcase/page?')) {
      p.off('response', handler)
      resolve(true)
    }
  }
  p.on('response', handler)
  setTimeout(() => resolve(false), 30000)
})
await p.goto(BASE + '/zentao/testcase', { waitUntil: 'domcontentloaded' })
await listArrived
await p.waitForTimeout(1500)

const read = async () =>
  p.evaluate(() => {
    const table = document.querySelector('.el-table')
    const rows = table ? Array.from(table.querySelectorAll('.el-table__body tbody tr')) : []
    return {
      headers: Array.from(document.querySelectorAll('.el-table__header th'))
        .map((e) => e.innerText.trim())
        .filter(Boolean),
      rows: rows.length,
      total: (document.body.innerText.match(/共 (\d+) 条/) || [])[1] || '',
      row0: rows[0] ? rows[0].innerText.replace(/\s+/g, ' ').trim().slice(0, 90) : ''
    }
  })

const initial = await read()
console.log('① 页面加载   :', JSON.stringify(initial))
if (!initial.headers.includes('关联需求') || !initial.headers.includes('环节')) {
  throw new Error('表头不符合预期：' + JSON.stringify(initial.headers))
}

// 按标题过滤出本次的用例
await p.fill('input[placeholder="请输入标题关键词"]', 'UI用例-' + TS)
await p.locator('button:has-text("搜索")').first().click()
await p.waitForTimeout(2500)
const filtered = await read()
console.log('② 按标题过滤 :', JSON.stringify({ rows: filtered.rows, total: filtered.total, row0: filtered.row0 }))
if (filtered.rows !== 1) throw new Error('预期筛出 1 行，实际 ' + filtered.rows)

// 详情抽屉
await p.locator('.el-table__body tbody tr').first().locator('button:has-text("查看")').click()
await p.waitForTimeout(3000)
const detail = await p.evaluate(() => {
  const drawer = document.querySelector('.el-drawer')
  // 抽屉里同时存在「步骤」和「版本历史」两张表（el-tabs 两个 pane 都留在 DOM 里），
  // 所以只能取**当前可见**的那个 pane，否则行数会串在一起
  const pane = Array.from(drawer.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
  const table = pane ? pane.querySelector('.el-table') : null
  const rows = table ? Array.from(table.querySelectorAll('.el-table__body tbody tr')) : []
  return {
    title: (drawer.querySelector('.el-drawer__title') || {}).innerText?.trim() || '',
    tabs: Array.from(drawer.querySelectorAll('.el-tabs__item')).map((e) => e.innerText.trim()),
    descriptions: Array.from(drawer.querySelectorAll('.el-descriptions__label, .el-descriptions__content'))
      .map((e) => e.innerText.trim())
      .slice(0, 8),
    steps: rows.map((r) => r.innerText.replace(/\s+/g, ' ').trim().slice(0, 60))
  }
})
console.log('③ 详情-步骤  :', JSON.stringify(detail.steps))
if (detail.steps.length !== 5) throw new Error('预期 5 个步骤，实际 ' + detail.steps.length)
const names = detail.steps.map((s) => s.split(' ')[0])
console.log('   层级编号   :', JSON.stringify(names))
if (names.join('|') !== '1|1.1|1.2|2|2.1') throw new Error('层级编号不对：' + names.join('|'))

// 切到版本历史 → 回看 v1
await p.locator('.el-drawer .el-tabs__item').filter({ hasText: '版本历史' }).first().click()
await p.waitForTimeout(2000)
const versions = await p.evaluate(() => {
  const drawer = document.querySelector('.el-drawer')
  const pane = Array.from(drawer.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
  const table = pane ? pane.querySelector('.el-table') : null
  return table
    ? Array.from(table.querySelectorAll('.el-table__body tbody tr')).map((r) =>
        r.innerText.replace(/\s+/g, ' ').trim().slice(0, 60)
      )
    : []
})
console.log('④ 版本历史   :', JSON.stringify(versions))
if (versions.length !== 2) throw new Error('预期 2 个版本，实际 ' + versions.length)

await p
  .locator('.el-drawer .el-table__body tbody tr')
  .filter({ hasText: 'v1 ' })
  .first()
  .locator('button:has-text("查看该版")')
  .click()
await p.waitForTimeout(2500)
const v1 = await p.evaluate(() => {
  const drawer = document.querySelector('.el-drawer')
  const pane = Array.from(drawer.querySelectorAll('.el-tab-pane')).find((el) => el.offsetParent !== null)
  const table = pane ? pane.querySelector('.el-table') : null
  return {
    alert: (drawer.querySelector('.el-alert__content') || {}).innerText?.trim().slice(0, 60) || '',
    steps: table
      ? Array.from(table.querySelectorAll('.el-table__body tbody tr')).map((r) =>
          r.innerText.replace(/\s+/g, ' ').trim().slice(0, 40)
        )
      : []
  }
})
console.log('⑤ 回看 v1    :', JSON.stringify(v1))
if (!v1.alert.includes('历史版本')) throw new Error('没有出现历史版本提示')
if (v1.steps.length !== 1) throw new Error('v1 应只有 1 个步骤，实际 ' + v1.steps.length)

await p.locator('.el-drawer__close-btn').first().click()
await p.waitForTimeout(1200)

// 新建弹窗：步骤编辑器
await p.locator('button:has-text("新建用例")').first().click()
await p.waitForTimeout(2000)
const form = await p.evaluate(() => {
  const dlg = document.querySelector('.el-dialog')
  return {
    title: (dlg.querySelector('.el-dialog__title') || {}).innerText?.trim() || '',
    hint: Array.from(dlg.querySelectorAll('.el-alert__title, .el-alert__description'))
      .map((e) => e.innerText.trim())
      .join(' | ')
      .slice(0, 120),
    stepRows: dlg.querySelectorAll('.step-row').length
  }
})
console.log('⑥ 新建弹窗   :', JSON.stringify(form))
if (!form.title.includes('新建用例')) throw new Error('弹窗标题不对：' + form.title)
if (form.stepRows !== 1) throw new Error('默认应有一行步骤，实际 ' + form.stepRows)

await p.locator('.el-dialog button:has-text("添加步骤组")').first().click()
await p.waitForTimeout(800)
const afterGroup = await p.evaluate(() => {
  const dlg = document.querySelector('.el-dialog')
  return {
    stepRows: dlg.querySelectorAll('.step-row').length,
    tags: Array.from(dlg.querySelectorAll('.step-index')).map((e) => e.innerText.trim())
  }
})
console.log('   加步骤组后 :', JSON.stringify(afterGroup))
if (afterGroup.stepRows !== 2 || afterGroup.tags.join('|') !== '1|组') {
  throw new Error('步骤编辑器行为不对：' + JSON.stringify(afterGroup))
}

await p.screenshot({ path: '/tmp/zentao-testcase.png', fullPage: true })
console.log('截图         : /tmp/zentao-testcase.png')
await p.locator('.el-dialog__headerbtn').first().click()
await p.waitForTimeout(1200)

// 需求变更：待确认开关
await p.fill('input[placeholder="请输入标题关键词"]', '')
await p.locator('button:has-text("重置")').first().click()
await p.waitForTimeout(2000)
await p.locator('.el-form-item:has-text("待确认") .el-switch').first().click()
await p.waitForTimeout(2500)
const confirmList = await read()
console.log('⑦ 待确认过滤 :', JSON.stringify({ rows: confirmList.rows, row0: confirmList.row0 }))
if (confirmList.rows < 1 || !confirmList.row0.includes('需求已变更')) {
  throw new Error('待确认列表没有出现「需求已变更」标记：' + JSON.stringify(confirmList))
}

// 收尾
await api('DELETE', '/zentao/testcase/delete?id=' + CASE)
console.log('已清理测试数据')
await b.close()

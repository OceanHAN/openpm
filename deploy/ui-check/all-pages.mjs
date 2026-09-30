import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'
import { login } from './_login.mjs'
const _pw = await import(pathToFileURL(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim() + '/playwright/index.js').href)
const chromium = _pw.chromium || _pw.default?.chromium
const BASE = process.env.ZENTAO_UI_BASE || 'http://localhost'
const API_BASE = process.env.ZENTAO_API_BASE || 'http://localhost:48080/admin-api'

const b = await chromium.launch({ headless: true })
const c = await b.newContext({ viewport: { width: 1680, height: 1050 } })
const p = await c.newPage()

// 断言失败时脚本会直接抛异常退出 —— 如果不在退出前关掉浏览器，
// 每个失败的 Playwright 进程都会留下一个 headless Chromium 挂在那里吃 CPU/内存
// （实测跑错几次就攒了 30 多个，机器负载升高后连登录都超时，形成连锁失败）。
const __cleanup = () => { try { b.close() } catch { /* 已经关掉了 */ } }
process.on('exit', __cleanup)
process.on('uncaughtException', (err) => { __cleanup(); console.error(err); process.exit(1) })
process.on('unhandledRejection', (err) => { __cleanup(); console.error(err); process.exit(1) })
const errors = []
p.on('pageerror', (e) => errors.push(String(e).split('\n')[0].slice(0, 160)))

await login(p, BASE)
console.log('登录成功\n')

const pages = [
  '/zentao/my',
  '/zentao/program',
  '/zentao/product',
  '/zentao/branch',
  '/zentao/story',
  '/zentao/project',
  '/zentao/execution',
  '/zentao/task',
  '/zentao/bug',
  '/zentao/module',
  '/zentao/plan',
  '/zentao/build',
  '/zentao/projectstory',
  '/zentao/stage',
  '/zentao/organization',
  '/zentao/release',
  '/zentao/doc',
  '/zentao/testcase',
  '/zentao/testtask',
  '/zentao/testreport',
  '/zentao/effort'
]

let ok = 0
for (const path of pages) {
  errors.length = 0
  // 等这个页面自己的列表请求回来再断言。三种偷懒做法都试过、都会偶发误报 0 行：
  //   固定 sleep —— 服务慢一点就不够
  //   networkidle —— Vite 的 HMR websocket 常驻，永远等不到（要加短超时）
  //   判断「暂无数据」—— Element Plus 在数据到达前就渲染空态，会立刻返回
  // 所以直接盯网络：页面发出的第一个 /zentao 列表请求（排除 /get、simple-list 等）返回即认为渲染完成。
  const listArrived = new Promise((resolve) => {
    const handler = (r) => {
      const url = r.url()
      if (!url.includes('/zentao/')) return
      // 只认「列表类」请求：/page、/list、/list-by-*、/tree、/project-stages、/product-list、/story-list
      // （页面还会并发拉 simple-list / total-percent 等，先到的不代表表格已就绪）
      const isList =
        url.includes('/page?') ||
        url.includes('/list?') ||
        url.includes('/list-by-') ||
        url.includes('/tree?') ||
        url.includes('/project-stages') ||
        url.includes('/product-list') ||
        url.includes('/story-list?') ||
        url.includes('/user-list')
      if (!isList) return
      p.off('response', handler)
      resolve()
    }
    p.on('response', handler)
    // 上限给足：本机到远端 MySQL/Redis 的网络抖动时（实测 RTT 从 ~1ms 劣化到 150ms+），
    // 单个列表接口要 2-5s、页面首屏要 20s 以上。原来写 15s，结果 16 个页面全部误报
    // 「表头 0 列」—— 不是页面坏了，是等得不够久。
    setTimeout(() => {
      p.off('response', handler)
      resolve()
    }, 60000)
  })
  await p.goto(BASE + path, { waitUntil: 'domcontentloaded' })
  await listArrived
  await p
    .waitForFunction(() => document.querySelectorAll('.el-loading-mask').length === 0, { timeout: 30000 })
    .catch(() => {})
  await p.waitForTimeout(1500)

  const info = await p.evaluate(() => {
    const txt = document.body.innerText
    return {
      notFound: txt.includes('404') || txt.includes('找不到'),
      headers: document.querySelectorAll('.el-table__header th').length,
      rows: document.querySelectorAll('.el-table__body tbody tr').length,
      title: (document.body.innerText.match(/禅道|产品|分支|模块|需求|项目|执行|任务|缺陷/) || [''])[0]
    }
  })
  const good = !info.notFound && info.headers > 0 && errors.length === 0
  if (good) ok++
  console.log(
    `${good ? '✅' : '❌'} ${path.padEnd(28)} 表头 ${String(info.headers).padStart(2)} 列, ${String(info.rows).padStart(2)} 行` +
      (errors.length ? `  pageerror: ${errors[0]}` : '')
  )
}
console.log(`\n页面可用: ${ok}/${pages.length}`)
await b.close()

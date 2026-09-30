// 共用的登录助手。
//
// 为什么不能只「填表单 → 点登录 → waitForURL」：
//   Vite 开发服务器在冷启动（或刚新增/改动页面文件触发整页 reload）时，
//   SPA 的首屏挂载会慢好几秒，此时点击「登录」可能落在还没挂载完的按钮上被吞掉，
//   于是 waitForURL 干等到超时 —— 表现为「偶发登录失败」，重跑一次又好了。
// 这里改成「点一次 → 等 URL；超时就再点一次，最多 3 轮」，把这类抖动挡掉。
//
// 用法：
//   import { login } from './_login.mjs'
//   await login(page, BASE)
export async function login(page, BASE = 'http://localhost') {
  // 后端/前端挪到服务器上之后（2026-09-14），Vite 冷启动要现编译整页模块，
  // 首次访问 /login 可能 30s+，Playwright 默认 30s 的导航超时会直接抛
  // 「TimeoutError: navigating to "/login"」。这里统一放宽（导航 90s / 动作 30s）。
  page.setDefaultNavigationTimeout(90000)
  page.setDefaultTimeout(30000)
  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
  // 等用户名输入框出现：比固定 sleep 可靠
  await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 30000 })
  await page.waitForTimeout(800)

  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.fill('input[placeholder="请输入用户名"]', 'admin')
    await page.fill('input[placeholder="请输入密码"]', 'admin123')
    // 点之前先等按钮可用：登录接口慢的时候（本机到远端 Redis 有 100ms+ 抖动）按钮会长时间
    // 处于 loading/disabled，直接 click 会一直重试到超时，报出来的错还看不出真正原因
    const loginBtn = page.locator('button:has-text("登录")').first()
    await loginBtn.waitFor({ state: 'visible', timeout: 30000 })
    await page.waitForFunction(
      () => {
        const btn = Array.from(document.querySelectorAll('button')).find((b) => b.innerText.includes('登录'))
        return btn && !btn.disabled
      },
      { timeout: 30000 }
    ).catch(() => {})
    await loginBtn.click({ timeout: 15000 }).catch(() => {})
    try {
      // 登录后会串行调用 get-permission-info（菜单+权限聚合），
      // 在远端 Redis 抖动时这一步本身就要 20s+，所以超时给足
      await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 90000 })
      return
    } catch (e) {
      if (attempt === 3) throw e
      console.log(`  （登录第 ${attempt} 次没跳转，重试一次）`)
      await page.waitForTimeout(2000)
    }
  }
}

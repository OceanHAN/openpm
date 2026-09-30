#!/bin/bash
# ---------------------------------------------------------------------------
# 全量验证（**服务器模式**）：后端与前端都跑在 192.168.0.119 上，本机只发请求 / 开浏览器
#
# 为什么有这条路径：本机内存不够（JVM 2G + OrbStack 里的 MySQL/Redis），
# 2026-09-14 把整套栈挪到 192.168.0.183；2026-09-28 又整体迁到 192.168.0.119（183 上的痕迹已清理）：
#   MySQL  3307  容器 yudao-mysql      （/data/yudao/docker-compose.yml）
#   Redis  6380  容器 yudao-redis
#   后端   48080 systemd yudao-server  （/data/yudao/server，JDK 25 + yudao-server.jar）
#   前端   8081  systemd yudao-ui      （/data/yudao/frontend，vite dev server）
#
# 本脚本做的事：
#   1/5 把 deploy/sql/35~56 同步到服务器并灌进远端库（幂等）
#   2/5 重启服务器上的两个 systemd 服务并等就绪
#   3/5 全量接口回归（自动扫描 deploy/test-*.sh；脚本支持 ZENTAO_API_BASE 覆盖）
#   4/5 浏览器检查（all-pages + 各专项；脚本支持 ZENTAO_UI_BASE / ZENTAO_API_BASE）
#   5/5 打印汇总
#
# 用法：
#   bash deploy/resume-verification-remote.sh
#
# 可覆盖的环境变量：
#   REMOTE_HOST=192.168.0.119  REMOTE_USER=ubuntu  SSH_PASS=...（留空=走密钥免密）  REMOTE_API_PORT=48080  REMOTE_UI_PORT=8081
# ---------------------------------------------------------------------------
set -uo pipefail
# 取口令：用 BASH_SOURCE 定位脚本目录（$0 在被 source 时是外层命令名，不准）
_zt_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$_zt_dir/_secrets.sh" 2>/dev/null || true

REMOTE_HOST="${REMOTE_HOST:-192.168.0.119}"
REMOTE_USER="${REMOTE_USER:-ubuntu}"
# 119 上已经装好本机公钥：默认走密钥免密，SSH_PASS 为空；需要密码时再传 SSH_PASS=...
SSH_PASS="${SSH_PASS:-}"
REMOTE_API_PORT="${REMOTE_API_PORT:-48080}"
REMOTE_UI_PORT="${REMOTE_UI_PORT:-8081}"
SQL_LIST=(35-zt_team.sql 36-zt_demo_seed.sql 37-zt_stakeholder.sql 38-zt_workestimation.sql 39-zt_caselib.sql \
          40-zt_kanban.sql 41-zt_metric.sql 42-zt_bi.sql 43-zt_action_menu.sql 44-zt_report.sql 45-zt_burn.sql \
          46-zt_qa_menu.sql 47-zt_repo.sql 48-zt_holiday.sql 49-zt_entry.sql 50-zt_company.sql 51-zt_score.sql 52-zt_search.sql 53-zt_dimension.sql 54-zt_api.sql 55-zt_webhook.sql 56-hide-unused-menus.sql)

API_BASE="http://${REMOTE_HOST}:${REMOTE_API_PORT}/admin-api"
UI_BASE="http://${REMOTE_HOST}:${REMOTE_UI_PORT}"
export ZENTAO_API_BASE="$API_BASE"
export ZENTAO_UI_BASE="$UI_BASE"
# 直连查库：本机 mysql 客户端直连服务器 3307（实测 0.7s/次），比每个断言都开一次 ssh 快得多。
# 万一本机没有 mysql 客户端，把它改成 MYSQL_TARGET=remote 走 ssh（慢但一定能用）。
export MYSQL_TARGET="${MYSQL_TARGET:-direct}"
export REMOTE_HOST

if [ -n "$SSH_PASS" ]; then
  SSH=(sshpass -p "$SSH_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "${REMOTE_USER}@${REMOTE_HOST}")
  SCP=(sshpass -p "$SSH_PASS" scp -o StrictHostKeyChecking=no)
else
  SSH=(ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "${REMOTE_USER}@${REMOTE_HOST}")
  SCP=(scp -o StrictHostKeyChecking=no)
fi
Q() { "${SSH[@]}" "$1" 2>/dev/null | grep -viE "post-quantum|store now|may need to be upgraded|^\*\*"; }
say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m!! %s\033[0m\n' "$*"; exit 1; }

say "0/5 探测 ${REMOTE_HOST}"
ping -c 1 -t 3 "$REMOTE_HOST" >/dev/null 2>&1 || die "远端不可达（检查网线/VPN）"
Q "echo ok" | grep -q ok || die "ssh 连不上 ${REMOTE_USER}@${REMOTE_HOST}"
printf '   API : %s\n   UI  : %s\n' "$API_BASE" "$UI_BASE"

say "1/5 同步 SQL 并灌进远端库"
"${SCP[@]}" deploy/sql/3[5-9]-*.sql deploy/sql/4[0-9]-*.sql deploy/sql/5[0-9]-*.sql "${REMOTE_USER}@${REMOTE_HOST}:/data/yudao/sql/" >/dev/null 2>&1 \
  || die "SQL 文件传不上去"
# 22 个文件放在一条 ssh 里循环灌完：虽然 119 建连接很快，但一次连接灌完仍然最省事，
# 也让「哪个文件失败」在同一段输出里连续可读。
FILES="${SQL_LIST[*]}"
"${SSH[@]}" "cd /data/yudao/sql && for f in $FILES; do printf '  → %s ' \"\$f\"; docker exec -i yudao-mysql mysql -uroot -p"$MYSQL_PASS" --default-character-set=utf8mb4 ruoyi-vue-pro < \"\$f\" >/dev/null 2>&1 && echo ok || echo FAIL; done" 2>/dev/null \
  | grep -viE "post-quantum|store now|may need|^\*\*"
TABLES=$("${SSH[@]}" "docker exec -i yudao-mysql mysql -uroot -p"$MYSQL_PASS" -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'zt\\\\_%';\"" 2>/dev/null | grep -viE "post-quantum|store now|may need|^\*\*|Warning" | tail -1 | tr -d ' ')
[ "$TABLES" = "67" ] || die "远端 zt_ 表数是 ${TABLES}，期望 67"
echo "   zt_ 表数 = ${TABLES}"

say "2/5 重启服务器上的后端与前端"
Q "sudo systemctl restart yudao-server; sudo systemctl restart yudao-ui; echo restarted" >/dev/null
for i in $(seq 1 40); do
  sleep 5
  Q "grep -q 'Started YudaoServerApplication' /data/yudao/server/logs/stdout.log && echo READY" | grep -q READY && { echo "   后端已就绪（第 $((i*5)) 秒）"; break; }
  [ "$i" = "40" ] && die "后端 200 秒没起来：ssh ${REMOTE_HOST} 'tail -50 /data/yudao/server/logs/stdout.log'"
done
for i in $(seq 1 24); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$UI_BASE/login")
  [ "$code" = "200" ] && { echo "   前端已就绪（第 $((i*5)) 秒）"; break; }
  sleep 5
  [ "$i" = "24" ] && die "前端 120 秒没起来：ssh ${REMOTE_HOST} 'tail -30 /data/yudao/frontend/logs/vite.log'"
done
# 预热：Vite 冷启动要现编译模块，首次访问会把模块编译时间算进首个断言里（会把「慢」误报成「坏」）
for p in /login /zentao/entry /zentao/company /zentao/my; do curl -s -o /dev/null --max-time 60 "$UI_BASE$p"; done
sleep 3

say "3/5 全量接口回归（远程后端）"
# 本机到 192.168.0.0/24 之间仍可能偶发抖动（历史上有过 4 次断连），脚本第一条就是登录，
# 一次断连会让整个脚本 30 个断言全红。所以失败时**重跑一次**，
# 只有两次都失败才算真失败（重跑前会打印提示，不会把真 bug 洗成绿）。
run_api() {
  local _f="$1" _out _bad
  for _attempt in 1 2; do
    _out=$(bash "$_f" 2>&1)
    _bad=$(printf '%s' "$_out" | grep -cE "❌")
    if [ "$_bad" = "0" ]; then printf '%s' "$_out"; return 0; fi
    [ "$_attempt" = "1" ] && echo "  （$(basename "$_f") 第 1 次有失败项，重跑一次）"
    sleep 5
  done
  printf '%s' "$_out"
  return 1
}

TOTAL=0; PASSED=0; SUM=0
for f in deploy/test-*.sh; do
  out=$(run_api "$f")
  ok=$(echo "$out" | grep -cE "✅")
  bad=$(echo "$out" | grep -cE "❌")
  printf '  %-42s ✅ %s / ❌ %s\n' "$(basename "$f")" "$ok" "$bad"
  [ "$bad" != "0" ] && echo "$out" | grep -E "❌" | head -5
  TOTAL=$((TOTAL+1)); SUM=$((SUM+ok))
  [ "$bad" = "0" ] && PASSED=$((PASSED+1))
done
echo "  ---- 脚本通过 ${PASSED} / ${TOTAL}，断言 ${SUM}"

say "4/5 浏览器检查（远程前端）"
# 浏览器检查同理：冷启动编译整页模块 + 代理抖动，也可能一次不过；同样重跑一次
run_ui() {
  local _m="$1" _out
  for _attempt in 1 2; do
    _out=$(node "deploy/ui-check/${_m}.mjs" 2>&1); _rc=$?
    # 通过判定两种写法都认：
    #   ① 新式脚本末尾会打「…界面检查：通过 N 项，失败 0 项」
    #   ② 老式脚本（team.mjs 等）没有收尾行，靠「退出码 0 且没有 ❌」判定
    if printf '%s' "$_out" | grep -qE "失败 0 项" \
       || { [ "$_rc" = "0" ] && ! printf '%s' "$_out" | grep -q "❌"; }; then
      printf '%s\n' "$_out" | tail -4; return 0
    fi
    [ "$_attempt" = "1" ] && echo "  （ui-check/${_m}.mjs 第 1 次未通过，重跑一次）"
    sleep 8
  done
  printf '%s\n' "$_out" | tail -20
  return 1
}

# 页面巡检最容易撞两件事：Vite 冷启动（首次访问要现编译模块，实测有超 90s 直接
# page.goto 超时的情况）和代理抖动。它没有 run_ui 那层包装，所以这里单独补一次重试。
_all_ok=0; _all_out=""
for _attempt in 1 2; do
  _all_out=$(node deploy/ui-check/all-pages.mjs 2>&1) && _all_ok=1
  if [ "$_all_ok" = "1" ]; then printf '%s\n' "$_all_out" | tail -3; break; fi
  [ "$_attempt" = "1" ] && echo "  （all-pages 第 1 次未通过，重跑一次）"
  sleep 8
done
[ "$_all_ok" != "1" ] && printf '%s\n' "$_all_out" | tail -20
for m in team storytype caselib kanban metric bi action my-workspace report burn qa repo holiday entry company score search dimension api webhook; do
  run_ui "$m"
done

say "5/5 结束"
echo "  接口：脚本 ${PASSED}/${TOTAL}，断言 ${SUM}"
echo "  页面：见上面的 ui-check 输出（每个脚本最后一行是「通过 N 项，失败 N 项」）"

#!/bin/bash
# ---------------------------------------------------------------------------
# 补验证脚本：把「上一轮改了代码但没跑成」的验证一次性补齐
#
# 背景：第 20 轮（团队模块 zt_team）代码写完时，远端 MySQL/Redis（192.168.0.183）
# 变得不可达（本机换网段 + VPN 抢了默认路由），接口断言与浏览器检查都没跑。
# 这个脚本按正确顺序把那部分验证补上，任何一步失败就停下并提示。
#
# 用法：
#   bash deploy/resume-verification.sh              # 用远端栈（默认，先探测连通性）
#   LOCAL=1 bash deploy/resume-verification.sh      # 用本机栈（deploy/local-stack）
# ---------------------------------------------------------------------------
set -u
# 取口令：用 BASH_SOURCE 定位脚本目录（$0 在被 source 时是外层命令名，不准）
_zt_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$_zt_dir/_secrets.sh" 2>/dev/null || true
cd "$(dirname "$0")/.." || exit 1
LOCAL="${LOCAL:-0}"
BASE=http://127.0.0.1:48080/admin-api

say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m✗ %s\033[0m\n' "$*"; exit 1; }

# ---------------------------------------------------------------------------
# 0. 决定连哪套数据库
# ---------------------------------------------------------------------------
if [ "$LOCAL" = "1" ]; then
  say "本机栈模式：先确认 deploy/local-stack 起来了"
  timeout 20 docker ps --format '{{.Names}}' 2>/dev/null | grep -q yudao-local-mysql \
    || die "本机栈没起（cd deploy/local-stack && docker compose up -d）；若 docker 命令本身卡住，说明容器引擎（OrbStack）挂了"
  MYSQL_HOST=127.0.0.1
  MYSQL_PORT=3307
  REDIS_HOST=127.0.0.1
  # 容器里的 redis 挂掉时可以先用本机 redis 顶上：
  #   redis-server --port 6381 --requirepass "$REDIS_PASS" --appendonly no --save '' &
  #   REDIS_PORT=6381 LOCAL=1 bash deploy/resume-verification.sh
  REDIS_PORT="${REDIS_PORT:-6380}"
  SQL_FILES=(deploy/sql/35-zt_team.sql deploy/sql/36-zt_demo_seed.sql deploy/sql/37-zt_stakeholder.sql deploy/sql/38-zt_workestimation.sql deploy/sql/39-zt_caselib.sql deploy/sql/40-zt_kanban.sql deploy/sql/41-zt_metric.sql deploy/sql/42-zt_bi.sql deploy/sql/43-zt_action_menu.sql deploy/sql/44-zt_report.sql deploy/sql/45-zt_burn.sql deploy/sql/46-zt_qa_menu.sql deploy/sql/47-zt_repo.sql deploy/sql/48-zt_holiday.sql deploy/sql/49-zt_entry.sql deploy/sql/50-zt_company.sql deploy/sql/51-zt_score.sql deploy/sql/52-zt_search.sql deploy/sql/53-zt_dimension.sql deploy/sql/54-zt_api.sql deploy/sql/55-zt_webhook.sql deploy/sql/56-hide-unused-menus.sql)
  MYSQL_CMD=(docker exec -i yudao-local-mysql mysql -uroot -p"$MYSQL_PASS" --default-character-set=utf8mb4 ruoyi-vue-pro)
  export MYSQL_TARGET=local   # 让 test-testcase / test-testtask 里直连数据库的断言也用本机栈
  OVERRIDES=(--spring.datasource.dynamic.datasource.master.url="jdbc:mysql://127.0.0.1:3307/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&nullCatalogMeansCurrent=true"
             --spring.datasource.dynamic.datasource.slave.url="jdbc:mysql://127.0.0.1:3307/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&nullCatalogMeansCurrent=true"
             --spring.data.redis.host=127.0.0.1 --spring.data.redis.port="$REDIS_PORT")
else
  say "远端栈模式：先探测 192.168.0.119"
  ping -c 1 -t 3 192.168.0.119 >/dev/null 2>&1 \
    || die "远端不可达。要么把本机接回 192.168.0.x 网段，要么用 LOCAL=1 走本机栈"
  MYSQL_HOST=192.168.0.119
  MYSQL_PORT=3307
  REDIS_HOST=192.168.0.119
  REDIS_PORT=6380
  SQL_FILES=(deploy/sql/35-zt_team.sql deploy/sql/36-zt_demo_seed.sql deploy/sql/37-zt_stakeholder.sql deploy/sql/38-zt_workestimation.sql deploy/sql/39-zt_caselib.sql deploy/sql/40-zt_kanban.sql deploy/sql/41-zt_metric.sql deploy/sql/42-zt_bi.sql deploy/sql/43-zt_action_menu.sql deploy/sql/44-zt_report.sql deploy/sql/45-zt_burn.sql deploy/sql/46-zt_qa_menu.sql deploy/sql/47-zt_repo.sql deploy/sql/48-zt_holiday.sql deploy/sql/49-zt_entry.sql deploy/sql/50-zt_company.sql deploy/sql/51-zt_score.sql deploy/sql/52-zt_search.sql deploy/sql/53-zt_dimension.sql deploy/sql/54-zt_api.sql deploy/sql/55-zt_webhook.sql deploy/sql/56-hide-unused-menus.sql)
  # 119 上装了本机公钥，走密钥免密（183 时代用的是 sshpass + 密码）
  MYSQL_CMD=(ssh -o StrictHostKeyChecking=no ubuntu@192.168.0.119 \
             "docker exec -i yudao-mysql mysql -uroot -p'$MYSQL_PASS' --default-character-set=utf8mb4 ruoyi-vue-pro")
  OVERRIDES=()
fi
printf '   MySQL  : %s:%s\n   Redis  : %s:%s\n' "$MYSQL_HOST" "$MYSQL_PORT" "$REDIS_HOST" "$REDIS_PORT"

# ---------------------------------------------------------------------------
# 1. 灌 SQL（团队表 + 执行层级口径纠正）
# ---------------------------------------------------------------------------
say "1/5 应用 SQL（团队表 + 演示数据补种）"
for f in "${SQL_FILES[@]}"; do
  echo "  → $f"
  "${MYSQL_CMD[@]}" < "$f" 2>&1 | grep -viE "warning|post-quantum|store now|may need" || true
done
"${MYSQL_CMD[@]}" <<'SQL' 2>/dev/null | grep -viE "warning" || true
SELECT COUNT(*) AS team_members FROM `zt_team`;
SELECT id, project, parent, path, grade, type FROM `zt_project`
 WHERE type IN ('sprint','stage','kanban') AND deleted = 0 ORDER BY id;
SQL

# ---------------------------------------------------------------------------
# 2. 启动后端（带上地址覆盖），等它 ready
# ---------------------------------------------------------------------------
say "2/5 启动后端"
PGREP=$(pgrep -f "yudao-server.jar" | head -1 || true)
if [ -n "$PGREP" ]; then
  echo "  已有后端在跑（pid ${PGREP}），先停掉"
  pkill -f "yudao-server.jar" || true
  sleep 3
fi
export JAVA_HOME="/Users/co/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
cd ruoyi-vue-pro || exit 1
export DB_PASSWORD="${DB_PASSWORD:-${MYSQL_PASS:-}}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-${REDIS_PASS:-}}"
nohup caffeinate -is java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local "${OVERRIDES[@]}" \
  > /tmp/yudao-server.log 2>&1 &
cd ..
for i in $(seq 1 30); do
  sleep 5
  if grep -q "Started YudaoServerApplication" /tmp/yudao-server.log 2>/dev/null; then
    echo "  后端已就绪（第 $((i*5)) 秒）"
    break
  fi
  [ "$i" = "30" ] && die "后端 150 秒没起来，看 /tmp/yudao-server.log"
done

# ---------------------------------------------------------------------------
# 3. 团队模块断言
# ---------------------------------------------------------------------------
say "3/5 团队模块回归"
bash deploy/test-team-module.sh || die "团队模块断言失败（见上面输出）"

# ---------------------------------------------------------------------------
# 4. 全量回归（执行模块的 path/parent/grade 这一轮改过，必须全跑一遍）
# ---------------------------------------------------------------------------
say "4/5 全量回归（自动扫描 deploy/test-*.sh）"
TOTAL=0; PASSED=0; SUM=0
for f in deploy/test-*.sh; do
  out=$(bash "$f" 2>&1)
  ok=$(echo "$out" | grep -cE "✅")
  bad=$(echo "$out" | grep -cE "❌")
  printf '  %-42s ✅ %s / ❌ %s\n' "$(basename "$f")" "$ok" "$bad"
  [ "$bad" != "0" ] && echo "$out" | grep -E "❌" | head -5
  TOTAL=$((TOTAL+1)); SUM=$((SUM+ok))
  [ "$bad" = "0" ] && PASSED=$((PASSED+1))
done
echo "  ---- 脚本通过 ${PASSED} / ${TOTAL}，断言 ${SUM}"

# ---------------------------------------------------------------------------
# 5. 浏览器检查（先全站页面扫一遍，再三个专项抽屉/视图）
# ---------------------------------------------------------------------------
say "5/5 浏览器检查"
# 页面巡检最容易撞 Vite 冷启动（首次访问现编译模块，实测有 page.goto 超 90s 的情况）与代理抖动，补一次重试
_all_ok=0; _all_out=""
for _attempt in 1 2; do
  _all_out=$(node deploy/ui-check/all-pages.mjs 2>&1) && _all_ok=1
  if [ "$_all_ok" = "1" ]; then printf '%s\n' "$_all_out" | tail -3; break; fi
  [ "$_attempt" = "1" ] && echo "  （all-pages 第 1 次未通过，重跑一次）"
  sleep 8
done
[ "$_all_ok" != "1" ] && printf '%s\n' "$_all_out" | tail -20
node deploy/ui-check/team.mjs | tail -5
# 需求分层：类型页签角标 / 分层视图三层缩进 / 弹窗建业务需求
node deploy/ui-check/storytype.mjs | tail -5
# 用例库：库列表 / 库内用例 / 建库建用例 / 从产品导入 / 删除保护
node deploy/ui-check/caselib.mjs | tail -5
# 看板：网格渲染 / 默认布局 / 建卡片 / 移动 / 完成
node deploy/ui-check/kanban.mjs | tail -5
# 度量：概览卡片 / 度量项列表 / 口径定义 / 计算 / 数据表
node deploy/ui-check/metric.mjs | tail -5
# BI：数据视图 / SQL 白名单 / 图表渲染
node deploy/ui-check/bi.mjs | tail -5
# 回收站：列表 / 还原 / 隐藏 / 动态
node deploy/ui-check/action.mjs | tail -5
# 我的地盘第二组：我参与的项目/执行/团队/测试单/用例/文档/日历
node deploy/ui-check/my-workspace.mjs | tail -5
# 报表：年度数据 / 每日提醒 / 产出统计
node deploy/ui-check/report.mjs | tail -5
# 执行燃尽图：快照 / 三条线 / 重新计算 / 周末切换
node deploy/ui-check/burn.mjs | tail -5
# 测试仪表盘：质量统计口径 / 三个列表块 / 按产品过滤
node deploy/ui-check/qa.mjs | tail -5
# 代码库：同步真实 git 仓库 / 提交详情 / 对象关联
node deploy/ui-check/repo.mjs | tail -5
# 节假日：假期/补班列表 / 工作日试算 / 燃尽图口径
node deploy/ui-check/holiday.mjs | tail -5
# 应用接入：应用列表 / 签名助手 / 校验链（403 与 405）/ 调用日志
node deploy/ui-check/entry.mjs | tail -5
# 公司信息：本公司/编辑弹窗/http:// 归一/外部公司/超管口径对照/复用组织与动态
node deploy/ui-check/company.mjs | tail -5
# 积分：概览/记录/38 条规则/计分试算（含未知规则报错）
node deploy/ui-check/score.mjs | tail -5
# 保存查询：列表/弹窗/快捷方式切换/拼音码表/两条边界说明
node deploy/ui-check/search.mjs | tail -5
# 维度：可见性过滤/末次维度兜底链/1.5 级导航下拉
node deploy/ui-check/dimension.mjs | tail -5
# 接口文档库：接口列表/详情/结构/发布版本
node deploy/ui-check/api.mjs | tail -5
# Webhook：列表/新建弹窗/mock 端到端/发送日志
node deploy/ui-check/webhook.mjs | tail -5

say "补验证结束：把上面 4/5 的断言总数与 5/5 的页面数写回 README 2.4"

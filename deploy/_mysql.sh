# 取口令：用 BASH_SOURCE 定位脚本目录（$0 在被 source 时是外层命令名，不准）
_zt_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$_zt_dir/_secrets.sh" 2>/dev/null || true
# ---------------------------------------------------------------------------
# 直接查库的小工具（给需要断言数据库状态的测试脚本用）
#
# 背景：有些断言不能只看接口，得直接查库（比如「run 与执行历史真的被物理删了吗」）。
# 这类脚本原来把远端 ssh 命令写死在里面，结果**远端不可达时整段断言直接失效**，
# 本机栈（deploy/local-stack）也跑不了。
#
# 目标选择：MYSQL_TARGET=local|remote|auto（默认 auto）
#   auto：先探本机栈容器，探不到再走远端 ssh
#
# 用法：
#   source "$(dirname "$0")/_mysql.sh"
#   mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_testrun WHERE task=94101;"
#   mysql_exec  "UPDATE \`ruoyi-vue-pro\`.zt_case SET storyVersion=1 WHERE id=93103;"
#
# 踩过的坑（重要）：库名 ruoyi-vue-pro 带横线，SQL 里必须加反引号。而远端那条路是
#   ssh "docker exec ... mysql -e \"$SQL\""
# 反引号出现在**远端 shell 的源码里**，会被远端当成命令替换先执行掉，SQL 静默变成
#   SELECT ... FROM .zt_project
# 报语法错、断言全部拿到空串（看起来像「数据不存在」）。所以远端改成 base64 传 SQL，
# 绕开一切引号 / 反引号 / 变量展开规则。
# ---------------------------------------------------------------------------
MYSQL_TARGET="${MYSQL_TARGET:-auto}"
REMOTE_HOST="${REMOTE_HOST:-192.168.0.119}"      # 2026-09-28 从 192.168.0.183 迁过来
REMOTE_USER="${REMOTE_USER:-ubuntu}"
# 直连模式（direct）：用本机 mysql 客户端直连服务器的 3307（应用账号 yudao）
MYSQL_DIRECT_USER="${MYSQL_DIRECT_USER:-yudao}"
MYSQL_DIRECT_PASS="${MYSQL_DIRECT_PASS:-${MYSQL_PASS:-}}"
MYSQL_DIRECT_PORT="${MYSQL_DIRECT_PORT:-3307}"

case "${MYSQL_TARGET}" in
  direct|auto)
    if [ -z "${MYSQL_PASS:-}" ]; then
      echo "!! 没有口令：请先 source deploy/_secrets.sh（模板见 deploy/_secrets.example.sh）" >&2
      exit 1
    fi ;;
esac

_mysql_pick() {
  [ "$MYSQL_TARGET" != "auto" ] && { echo "$MYSQL_TARGET"; return; }
  if timeout 20 docker exec yudao-local-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT 1" >/dev/null 2>&1; then
    echo local; return
  fi
  echo remote
}

# 调用方常写成 \`table\`（那是给远端 ssh 那一层做转义的），这里统一还原成真正的反引号
_normalize_sql() { printf '%s' "$1" | sed 's/\\`/`/g'; }

# 底层：$1=SQL，$2=额外的 mysql 参数（如 -N）
# SQL 出错时把 mysql 的报错打到 stderr（stdout 保持纯结果，调用方的 $(...) 不受污染）。
# 为什么要这么做：以前 stderr 直接丢进 /dev/null，一条 ONLY_FULL_GROUP_BY 报错被吞成「查不到数据」，
# 结果在 CI 上表现为「断言莫名失败」，排查花了很久。
_mysql_report_err() {
  local _rc="$1" _errfile="$2"
  if [ "$_rc" != "0" ] && [ -s "$_errfile" ]; then
    grep -v 'Using a password' "$_errfile" | sed 's/^/[mysql] /' >&2
  fi
  rm -f "$_errfile"
}

_mysql_raw() {
  local _sql _extra _b64
  _sql="$(_normalize_sql "$1")"
  _extra="$2"
  case "$(_mysql_pick)" in
    local)
      _err=$(mktemp)
      timeout 60 docker exec -i yudao-local-mysql mysql -uroot -p"$MYSQL_PASS" \
        --default-character-set=utf8mb4 $_extra -e "$_sql" 2>"$_err" | grep -v WARNING
      _rc=${PIPESTATUS[0]}
      _mysql_report_err "$_rc" "$_err" ;;
    direct)
      # 直连服务器 3307（应用账号 yudao）：比走 ssh 快，而且没有「远端 shell 展开反引号」的坑。
      _err=$(mktemp)
      timeout 60 mysql -h"$REMOTE_HOST" -P"$MYSQL_DIRECT_PORT" \
        -u"$MYSQL_DIRECT_USER" -p"$MYSQL_DIRECT_PASS" \
        --default-character-set=utf8mb4 $_extra -e "$_sql" 2>"$_err" | grep -v WARNING
      _rc=${PIPESTATUS[0]}
      _mysql_report_err "$_rc" "$_err" ;;
    *)
      _b64=$(printf '%s' "$_sql" | base64 | tr -d '\n')
      _err=$(mktemp)
      timeout 90 ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 \
        "${REMOTE_USER}@${REMOTE_HOST}" \
        "echo $_b64 | base64 -d | docker exec -i yudao-mysql mysql -uroot -p'$MYSQL_PASS' --default-character-set=utf8mb4 $_extra" \
        2>"$_err" | grep -v WARNING
      _rc=${PIPESTATUS[0]}
      _mysql_report_err "$_rc" "$_err" ;;
  esac
}

# 执行 SQL（取结果，-N 去掉表头，去空白）
mysql_query() { _mysql_raw "$1" "-N" | tr -d ' '; }

# 执行 SQL（不关心结果，用于还原演示数据）
mysql_exec() { _mysql_raw "$1" ""; }

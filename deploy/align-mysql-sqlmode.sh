#!/bin/bash
# ---------------------------------------------------------------------------
# 把 MySQL 的 sql_mode 对齐到 **MySQL 8 默认值**（含 ONLY_FULL_GROUP_BY）
#
# 为什么要做：
#   旧容器是带着 --sql-mode=STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION 起的（少了
#   ONLY_FULL_GROUP_BY）。于是 `SELECT DISTINCT ... ORDER BY <不在 SELECT 列表里的列>`
#   这种写法在本地一直能过，CI（MySQL 8 默认 sql_mode）却直接 ERROR 3065 ——
#   2026-09-30 就是这么红的：本地绿、CI 红，排查花了很久。
#   把两边拉齐之后，「本地跑过 = CI 能过」才成立。
#
# 数据安全性：MySQL 数据在 bind mount（<COMPOSE_DIR>/data/mysql）里，重建容器不动数据；
#   脚本会在重建前后比对库表数 / 关键表行数，不一致就报警。
#
# 用法（在部署机上）：
#   bash align-mysql-sqlmode.sh              # 只检查（dry-run）
#   bash align-mysql-sqlmode.sh --yes        # 执行：改 compose → 重建 mysql → 校验 → 重启后端
#
# 环境变量：
#   COMPOSE_DIR  部署目录（默认 /data/yudao）
#   CONTAINER    MySQL 容器名（默认 yudao-mysql）
#   DB           库名（默认 ruoyi-vue-pro）
#   APP_UNIT     后端 systemd 单元（默认 yudao-server；为空则跳过重启）
# ---------------------------------------------------------------------------
set -uo pipefail
COMPOSE_DIR="${COMPOSE_DIR:-/data/yudao}"
CONTAINER="${CONTAINER:-yudao-mysql}"
DB="${DB:-ruoyi-vue-pro}"
APP_UNIT="${APP_UNIT:-yudao-server}"
APPLY="${1:-}"

# ---------- 口令 ----------
PASS="${MYSQL_PASS:-}"
if [ -z "$PASS" ] && [ -r "$COMPOSE_DIR/server/.secrets" ]; then
  # shellcheck disable=SC1091
  . "$COMPOSE_DIR/server/.secrets"; PASS="${DB_PASSWORD:-}"
fi
for f in "$COMPOSE_DIR/deploy/.env" "$COMPOSE_DIR/.env"; do
  if [ -z "$PASS" ] && [ -r "$f" ]; then . "$f"; PASS="${MYSQL_PASS:-}"; fi
done
if [ -z "$PASS" ]; then
  echo "!! 没拿到 MySQL 口令。找过：$COMPOSE_DIR/server/.secrets(DB_PASSWORD)、$COMPOSE_DIR/{deploy/,}.env(MYSQL_PASS)" >&2
  exit 1
fi

q() { docker exec -i "$CONTAINER" mysql -uroot -p"$PASS" -N -B -e "$1" 2>/dev/null; }

# ---------- 前置检查 ----------
if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "!! 容器 $CONTAINER 没在跑（先 docker compose up -d）" >&2
  exit 1
fi

echo "== 变更前 =="
BEFORE_MODE=$(q "SELECT @@sql_mode;")
TABLES_BEFORE=$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB';")
ROWS_BEFORE=$(q "SELECT (SELECT COUNT(*) FROM \`$DB\`.zt_project) + (SELECT COUNT(*) FROM \`$DB\`.zt_story) + (SELECT COUNT(*) FROM \`$DB\`.zt_task) + (SELECT COUNT(*) FROM \`$DB\`.zt_bug);")
printf '  sql_mode : %s\n' "${BEFORE_MODE:-?}"
printf '  表数     : %s\n' "${TABLES_BEFORE:-?}"
printf '  zt_project+story+task+bug 行数合计 : %s\n' "${ROWS_BEFORE:-?}"
case "${BEFORE_MODE:-}" in
  *ONLY_FULL_GROUP_BY*) echo "  ✅ 已含 ONLY_FULL_GROUP_BY，与 CI 一致，无需变更"; exit 0 ;;
  *) echo "  ⚠️  缺 ONLY_FULL_GROUP_BY（宽松模式，会把本地问题藏到 CI）" ;;
esac

# ---------- 找到 compose 文件 ----------
COMPOSE_FILE=""
for f in "$COMPOSE_DIR/docker-compose.yml" "$COMPOSE_DIR/deploy/docker-compose.yml" "$COMPOSE_DIR/deploy/local-stack/docker-compose.yml"; do
  [ -r "$f" ] && COMPOSE_FILE="$f" && break
done
[ -n "$COMPOSE_FILE" ] || { echo "!! 找不到 docker-compose.yml" >&2; exit 1; }
HAS_SQLMODE=$(grep -c -- '--sql-mode' "$COMPOSE_FILE" || true)
echo "  compose  : $COMPOSE_FILE（--sql-mode 行数：$HAS_SQLMODE）"

if [ "$APPLY" != "--yes" ]; then
  cat <<EOF

（dry-run）将要执行：
  1) 备份并去掉 $COMPOSE_FILE 里的 --sql-mode 行 → 用 MySQL 8 默认值
  2) docker compose up -d mysql（重建容器；数据在 $COMPOSE_DIR/data/mysql，不受影响）
  3) 校验：sql_mode 含 ONLY_FULL_GROUP_BY、表数与关键行数不变
  4) systemctl restart $APP_UNIT（若存在）并做一次登录冒烟
加 --yes 才真正执行。
EOF
  exit 0
fi

# ---------- 执行 ----------
BAK="$COMPOSE_FILE.bak.$(date +%Y%m%d%H%M%S)"
cp -a "$COMPOSE_FILE" "$BAK"
echo "  已备份 → $BAK"
python3 - "$COMPOSE_FILE" <<'PY'
import sys
p = sys.argv[1]
lines = open(p, encoding='utf-8').read().split('\n')
out, removed = [], 0
for line in lines:
    if '--sql-mode' in line and line.lstrip().startswith('- '):
        out.append('      # 不设 --sql-mode：用 MySQL 8 默认值（含 ONLY_FULL_GROUP_BY），与 CI 保持一致')
        removed += 1
        continue
    out.append(line)
open(p, 'w', encoding='utf-8').write('\n'.join(out))
print(f'  已去掉 {removed} 行 --sql-mode')
PY
python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1])); print('  compose YAML 校验通过')" "$COMPOSE_FILE" || { echo "!! compose 语法坏了，回滚"; cp -a "$BAK" "$COMPOSE_FILE"; exit 1; }

cd "$(dirname "$COMPOSE_FILE")" || exit 1
if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi
$DC up -d mysql || { echo "!! 重建失败"; exit 1; }

echo "  等待 MySQL 就绪…"
for i in $(seq 1 60); do
  docker exec "$CONTAINER" mysqladmin ping -uroot -p"$PASS" --silent >/dev/null 2>&1 && break
  sleep 3
done

echo "== 变更后 =="
AFTER_MODE=$(q "SELECT @@sql_mode;")
TABLES_AFTER=$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB';")
ROWS_AFTER=$(q "SELECT (SELECT COUNT(*) FROM \`$DB\`.zt_project) + (SELECT COUNT(*) FROM \`$DB\`.zt_story) + (SELECT COUNT(*) FROM \`$DB\`.zt_task) + (SELECT COUNT(*) FROM \`$DB\`.zt_bug);")
printf '  sql_mode : %s\n' "${AFTER_MODE:-?}"
printf '  表数     : %s（变更前 %s）\n' "${TABLES_AFTER:-?}" "${TABLES_BEFORE:-?}"
printf '  关键行数 : %s（变更前 %s）\n' "${ROWS_AFTER:-?}" "${ROWS_BEFORE:-?}"

RC=0
case "${AFTER_MODE:-}" in *ONLY_FULL_GROUP_BY*) echo "  ✅ sql_mode 已是 MySQL 8 默认语义" ;; *) echo "  ❌ 仍然缺 ONLY_FULL_GROUP_BY：检查 compose 是否还有别的 sql-mode 设置（例如 /etc/mysql/conf.d）"; RC=1 ;; esac
[ "${TABLES_AFTER}" = "${TABLES_BEFORE}" ] && [ "${ROWS_AFTER}" = "${ROWS_BEFORE}" ] || { echo "  ❌ 数据量与变更前不一致，请先看日志再决定是否回滚（备份：$BAK）"; RC=1; }

if [ -n "$APP_UNIT" ] && systemctl list-unit-files 2>/dev/null | grep -q "^${APP_UNIT}.service"; then
  echo "  重启 $APP_UNIT …"
  sudo systemctl restart "$APP_UNIT"
  for i in $(seq 1 36); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -X POST \
      http://127.0.0.1:48080/admin-api/system/auth/login \
      -H 'Content-Type: application/json' -H 'tenant-id: 1' \
      -d '{"username":"admin","password":"admin123"}' || true)
    [ "$code" = "200" ] && { echo "  ✅ 后端登录冒烟通过"; break; }
    sleep 5
  done
  [ "$code" = "200" ] || { echo "  ❌ 后端没起来，看 journalctl -u $APP_UNIT -n 50"; RC=1; }
fi

echo
[ "$RC" = "0" ] && echo "完成。建议紧接着跑一轮接口回归：ZENTAO_API_BASE=http://<host>:48080/admin-api bash deploy/resume-verification-remote.sh" || echo "有检查项没过（见上）"
exit $RC

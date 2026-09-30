#!/bin/bash
# ---------------------------------------------------------------------------
# 保留字预检：找出所有 zt_* 表里「撞上 MySQL 保留字」的列。
#
# 为什么需要这个脚本（IMPLEMENTATION-NOTES.md 第 10、11、20 条坑）：
#   - MyBatis-Plus 默认按「驼峰转下划线」生成列名，只有加了 @TableField("`xxx`") 才会带反引号
#   - 保留字有**两份不同的清单**：
#       1) MySQL 自己的（system / release / desc / order / groups / from …）
#       2) MyBatis-Plus 用的 JSqlParser（output / begin / end …）
#     本脚本查的是第 1 份（真实 MySQL，最权威）；第 2 份只能在应用层暴露，
#     所以「新增一张禅道表」后除了跑本脚本，还要真的调一次列表接口。
#
# 【为什么不「拼 SELECT 试跑」了】
#   早期版本把每张表的列名不加反引号拼成 SELECT 丢给 MySQL，靠报错定位。
#   问题是 **MySQL 一条语句只报第一个语法错**，报完整条就失败，
#   所以每张表永远只能看到「第一个」保留字 —— zt_doc 上就吃了这个亏：
#   第一遍报的是 order（已经加了反引号），真正漏掉的 groups 在它后面，被掩盖了。
#   现在改成直接查 information_schema.KEYWORDS（MySQL 8.0.13+ 自带的关键字表，
#   reserved=1 即保留字），一条 SQL 拿全量，既快又不会漏。
#
# 用法：
#   bash deploy/check-reserved-columns.sh                    # 目标自动选（先探远端，探不到用本机栈）
#   MYSQL_TARGET=local  bash deploy/check-reserved-columns.sh
#   MYSQL_TARGET=remote bash deploy/check-reserved-columns.sh
# ---------------------------------------------------------------------------
set -u
# 取口令：用 BASH_SOURCE 定位脚本目录（$0 在被 source 时是外层命令名，不准）
_zt_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$_zt_dir/_secrets.sh" 2>/dev/null || true
DB=${DB:-ruoyi-vue-pro}

# 目标选择复用 _mysql.sh（远端探不到时自动落到 deploy/local-stack 的本机容器）
source "$(dirname "$0")/_mysql.sh"

# 执行整段 SQL 文件（--force：出错继续，用于端到端扫全列）
run_sql_file() {
  case "$(_mysql_pick)" in
    local)
      timeout 120 docker exec -i yudao-local-mysql mysql -uroot -p"$MYSQL_PASS" \
        --default-character-set=utf8mb4 --force -D "$DB" < "$1" > "$2" 2>&1 || true ;;
    *)
      sshpass -p "$SSH_PASS" ssh -o StrictHostKeyChecking=no "root@${REMOTE_HOST}" \
        "docker exec -i yudao-mysql mysql -uroot -p'$MYSQL_PASS' --default-character-set=utf8mb4 --force -D $DB" \
        < "$1" > "$2" 2>&1 || true ;;
  esac
}

# 全量列清单：既是第 2 步扫全列的输入，也是「连不上库就报错」的判据
mysql_query "SELECT table_name, column_name FROM information_schema.columns
              WHERE table_schema='$DB' AND table_name LIKE 'zt%'
              ORDER BY table_name, ordinal_position;" > /tmp/zt_cols.tsv

if [ ! -s /tmp/zt_cols.tsv ]; then
  echo "✗ 取不到任何 zt_* 表的列信息：远端不可达、本机栈也没起来（或库名写错了：${DB}）" >&2
  exit 1
fi

echo "--- 1. MySQL 保留字列（information_schema.KEYWORDS）---"
mysql_query "SELECT c.table_name, c.column_name
               FROM information_schema.columns c
               JOIN information_schema.keywords k ON k.word = UPPER(c.column_name)
              WHERE c.table_schema = '$DB' AND c.table_name LIKE 'zt%' AND k.reserved = 1
              ORDER BY c.table_name, c.ordinal_position;" > /tmp/zt_reserved.tsv

if [ -s /tmp/zt_reserved.tsv ]; then
  python3 - <<'PYEOF'
from collections import OrderedDict

bad = OrderedDict()
for line in open('/tmp/zt_reserved.tsv', encoding='utf-8'):
    if not line.strip():
        continue
    parts = line.rstrip('\n').split('\t')
    if len(parts) != 2:
        continue
    table, col = parts
    bad.setdefault(table, []).append(col)

for table, cols in bad.items():
    print(f"  {table:20s} 保留字列：{', '.join(cols)}")
    print(f"  {'':20s}   → 这些列在 DO 里必须有 @TableField(\"`{cols[0]}`\")")
print(f"  （共 {len(bad)} 张表命中）")
PYEOF
else
  echo "  （无）"
fi

echo
echo "--- 2. 真实驱动验证：每张表 SELECT 全列（不带反引号）---"
# 列清单在第 0 步已经取到 /tmp/zt_cols.tsv（取不到就直接退出了），这里只负责拼 sweep SQL
python3 - <<'PYEOF'
from collections import OrderedDict
tables = OrderedDict()
for line in open('/tmp/zt_cols.tsv', encoding='utf-8'):
    if not line.strip():
        continue
    parts = line.rstrip('\n').split('\t')
    if len(parts) != 2:
        continue
    t, c = parts
    tables.setdefault(t, []).append(c)
with open('/tmp/zt_sweep.sql', 'w', encoding='utf-8') as f:
    for t, cols in tables.items():
        f.write("SELECT " + ", ".join(cols) + f" FROM `{t}` LIMIT 1;\n")
print(f"  待验证表数：{len(tables)}")
PYEOF

# 第 1 步查的是「名字是否在保留字表里」，这一步是端到端确认：
# 把列名原样拼进 SELECT 跑一遍，能跑通说明这张表在纯 SQL 层面没问题。
# 仍然不能替代应用层验证（JSqlParser 是另一份清单）。
run_sql_file /tmp/zt_sweep.sql /tmp/zt_sweep.out

python3 - <<'PYEOF'
import re
from collections import OrderedDict

tables = []
for line in open('/tmp/zt_sweep.sql', encoding='utf-8'):
    m = re.match(r'SELECT .*? FROM `(.+?)` LIMIT 1;', line)
    if m:
        tables.append(m.group(1))

failed = OrderedDict()
for line in open('/tmp/zt_sweep.out', encoding='utf-8', errors='ignore'):
    m = re.search(r'at line (\d+)', line)
    if not m:
        continue
    idx = int(m.group(1)) - 1
    table = tables[idx] if 0 <= idx < len(tables) else '?'
    near = re.search(r"near '([^']*)'", line)
    frag = near.group(1).split(',')[0].strip().lstrip('`') if near else '?'
    failed.setdefault(table, frag)

if failed:
    for table, frag in failed.items():
        print(f"  {table:20s} 执行失败，报错片段：{frag}")
else:
    print(f"  （{len(tables)} 张表全部可执行）")
PYEOF

echo "--- 预检结束 ---"

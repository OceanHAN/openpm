#!/bin/bash
# ---------------------------------------------------------------------------
# 驼峰列名预检：找出「字段名是驼峰、但漏了 @TableField」的 DO 字段。
#
# 为什么需要它（README 第 1 条坑，每新增一张禅道表都会遇到）：
#   禅道的列名是驼峰（objectID / addedBy / rawContent / baseUrl …），
#   而 MyBatis-Plus 默认把 Java 字段名按「驼峰转下划线」拼列名，
#   于是 baseUrl 会被拼成 base_url —— 报错是
#     Unknown column 'base_url' in 'field list'
#   必须在字段上加 @TableField("baseUrl")。
#
#   这个坑的特征是「不跑一次接口就发现不了」：编译通过、启动通过，
#   只有真的查这张表才炸。所以做成静态检查，和 check-reserved-columns.sh 配对使用：
#     - check-camel-columns.sh      → 驼峰列名漏 @TableField（本脚本）
#     - check-reserved-columns.sh   → 保留字列名漏反引号
#
# 用法：
#   bash deploy/check-camel-columns.sh                    # 目标自动选（先探远端，探不到用本机栈）
#   MYSQL_TARGET=local  bash deploy/check-camel-columns.sh
#   MYSQL_TARGET=remote bash deploy/check-camel-columns.sh
# ---------------------------------------------------------------------------
set -u
DB=${DB:-ruoyi-vue-pro}
MODULE_DIR=${MODULE_DIR:-$(cd "$(dirname "$0")/.." && pwd)/ruoyi-vue-pro/yudao-module-zentao/src/main/java/cn/iocoder/yudao/module/zentao/dal/dataobject}

# 目标选择复用 _mysql.sh（远端探不到时自动落到 deploy/local-stack 的本机容器）
source "$(dirname "$0")/_mysql.sh"

mysql_query "SELECT table_name, column_name FROM information_schema.columns
              WHERE table_schema='$DB' AND table_name LIKE 'zt%'
              ORDER BY table_name, ordinal_position;" > /tmp/zt_columns.tsv

# ★ 取不到列信息必须报错退出：早期版本写死远端 ssh，远端不可达时这里是个空文件，
#   脚本照样打印「OK：0 张表」，把「连不上库」伪装成「检查通过」（参考 README 第四节坑位 #42 的教训）
if [ ! -s /tmp/zt_columns.tsv ]; then
  echo "✗ 取不到任何 zt_* 表的列信息：远端不可达、本机栈也没起来（或库名写错了：${DB}）" >&2
  exit 1
fi

python3 - "$MODULE_DIR" <<'PYEOF'
import os
import re
import sys
from collections import OrderedDict

module_dir = sys.argv[1]

db_columns = OrderedDict()
for line in open('/tmp/zt_columns.tsv', encoding='utf-8'):
    if not line.strip():
        continue
    parts = line.rstrip('\n').split('\t')
    if len(parts) != 2:
        continue
    table, col = parts
    db_columns.setdefault(table, []).append(col)


def camel_to_snake(name):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


problems = []
checked_tables = 0
for root, _, files in os.walk(module_dir):
    for fname in files:
        if not fname.endswith('DO.java'):
            continue
        path = os.path.join(root, fname)
        text = open(path, encoding='utf-8').read()
        m = re.search(r'@TableName\("(\w+)"\)', text)
        if not m:
            continue
        table = m.group(1)
        if table not in db_columns:
            continue
        checked_tables += 1
        cols = db_columns[table]
        # 把字段声明连同它前面 6 行（注解区）一起切出来，判断有没有 @TableField
        for fm in re.finditer(r'((?:^[ \t]*@\w+(?:\([^)]*\))?[ \t]*\n)*)[ \t]*private\s+[\w<>,.\s]+?\s+(\w+)\s*;',
                              text, re.MULTILINE):
            annotations, field = fm.group(1), fm.group(2)
            if field in ('serialVersionUID',):
                continue
            snake = camel_to_snake(field)
            # 显式写了 @TableField 的字段跳过：列名由注解决定，
            # 反引号/保留字那一类问题交给 check-reserved-columns.sh。
            # （@TableField(exist=false) 的瞬态字段也走这一支，属于有意为之。）
            if '@TableField' in annotations:
                continue
            if field in cols and snake not in cols:
                # 字段名本身就是列名（驼峰列），而 MP 会拼成下划线 → 必须补注解
                problems.append((path, table, field, snake, '驼峰列名漏 @TableField'))
            elif field not in cols and snake not in cols:
                problems.append((path, table, field, snake, 'DO 里的字段在表里没有对应列（可能是改名后忘了同步）'))

if problems:
    print("--- 发现可疑字段 ---")
    for path, table, field, snake, reason in problems:
        rel = os.path.relpath(path, os.path.dirname(module_dir))
        print(f"  {table:16s} {field:16s} (MP 会拼成 {snake:20s}) {reason}")
        print(f"  {'':16s}   {rel}")
    print(f"--- 共 {len(problems)} 处，需要人工确认 ---")
else:
    print(f"--- OK：{checked_tables} 张表对应的 DO 字段与列名全部对得上 ---")
PYEOF

#!/bin/bash
# ---------------------------------------------------------------------------
# 租户忽略表预检：`yudao.tenant.ignore-tables` 是否覆盖了所有 zt_ 表
#
# 为什么需要这个检查（第 42 轮踩到）：yudao 的租户插件会往**每一条** SQL 里塞
# `tenant_id = ?`，只有在 ignore-tables 里登记过的表才会跳过。漏登记的表不会在启动时
# 报错，而是在**第一次查询时**抛 `Unknown column 'tenant_id' in 'where clause'`（500 系统异常）——
# 表现就是「接口 500、日志里是个 SQL 语法错」，很容易误以为是 DO/字段写错。
#
# 用法：bash deploy/check-tenant-ignore.sh
# 退出码：0 = 全覆盖；1 = 有漏（会列出缺哪几张表）
# ---------------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

YAML=ruoyi-vue-pro/yudao-server/src/main/resources/application.yaml
[ -f "$YAML" ] || { echo "找不到 $YAML"; exit 1; }

python3 - "$YAML" <<'PY'
# -*- coding: utf-8 -*-
import io, re, sys, glob
yaml_path = sys.argv[1]

tables = set()
for f in glob.glob('deploy/sql/*.sql'):
    txt = io.open(f, encoding='utf-8', errors='ignore').read()
    tables |= set(re.findall(r'CREATE TABLE IF NOT EXISTS `(zt_[a-z0-9_]+)`', txt))

yaml = io.open(yaml_path, encoding='utf-8').read()
ignore = set(re.findall(r'^\s+- (zt_[a-z0-9_]+)\s*$', yaml, re.M))

missing = sorted(tables - ignore)
extra = sorted(ignore - tables)
print(f'SQL 建出来的 zt_ 表：{len(tables)} 张；ignore-tables 登记：{len(ignore)} 张')
if extra:
    print(f'⚠️  yaml 里登记了但 SQL 没建（无害，但可能是删表后忘了清）：{", ".join(extra)}')
if missing:
    print(f'❌ 漏登记（这些表一查就报 Unknown column \'tenant_id\'）：{", ".join(missing)}')
    sys.exit(1)
print('✅ 全覆盖：所有 zt_ 表都在 ignore-tables 里')
PY

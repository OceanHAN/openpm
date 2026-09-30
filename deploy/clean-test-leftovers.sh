#!/bin/bash
# ---------------------------------------------------------------------------
# 清掉历次「校验脚本 / 界面检查」被中断后留下的测试残留（2026-09-29 新增）
#
# 为什么会有残留：deploy/test-*.sh 与 deploy/ui-check/*.mjs 每个脚本最后都会清理自己造的
# 夹具，但如果脚本跑到一半被中断（断网、被 kill、超时），清理那一段就没机会执行。
# 本项目在 183→119 迁移期间被中断过好几次，于是 zt_project / zt_product / zt_story /
# zt_case / zt_bug 里积了一批「名字带毫秒时间戳」的夹具，会污染列表和统计
# （在 /zentao/program 页面上最明显：52 条「测试项目集-…」）。
#
# 判据（刻意收得很紧，只认「测试前缀 + 时间戳后缀」，不碰任何正常命名）：
#   名称/标题以 `-<10 位或 13 位数字>` 结尾（所有夹具的 `名字-<epoch秒/毫秒>` 约定）
#   例：UIProj-1789273599875 / 测试项目集-1789324879 / 任务-1789275446 / 普通计划-1789277407
#   真实数据（禅道迁移项目集 / 禅道研发管理平台 / 禅道迁移一期 / V1.0迭代 …）都不带这种后缀
#
# 用法（默认只统计不删）：
#   bash deploy/clean-test-leftovers.sh          # dry-run：打印每条规则命中多少行
#   bash deploy/clean-test-leftovers.sh --yes    # 真正删除（单个事务）
#
# 数据源：走 deploy/_mysql.sh，默认 REMOTE_HOST=192.168.0.119（可用 MYSQL_TARGET=local 切本机栈）
# ---------------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
source deploy/_mysql.sh >/dev/null 2>&1
export MYSQL_TARGET="${MYSQL_TARGET:-direct}"
APPLY="${1:-}"

q() { mysql_query "$1"; }

EPOCH='-1[0-9]{9}([0-9]{3})?$'   # 夹具命名约定：名字以 -<epoch 秒(10 位)/毫秒(13 位)> 结尾
TS="$EPOCH"                        # 兼容旧变量名
RULES=(
  "zt_project|name" "zt_product|name" "zt_story|title" "zt_case|title" "zt_bug|title"
  "zt_task|name" "zt_doc|title" "zt_doclib|name" "zt_testtask|name" "zt_repo|name"
  "zt_productplan|title" "zt_branch|name" "zt_module|name" "zt_build|name"
  "zt_release|name" "zt_todo|name" "zt_effort|work" "zt_kanban|name" "zt_kanbancard|name"
  "zt_dataview|name" "zt_chart|name"
)

if [ "$MYSQL_TARGET" = "local" ]; then echo "== 目标库：本机栈"; else echo "== 目标库：远程 ${REMOTE_HOST:-?}:${MYSQL_DIRECT_PORT:-3307}"; fi
echo
printf '%-18s %-8s %8s\n' 表 列 命中
TOTAL=0
for r in "${RULES[@]}"; do
  IFS='|' read -r tbl col <<<"$r"
  n=$(q "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.$tbl WHERE $col REGEXP '$EPOCH';")
  printf '%-18s %-8s %8s\n' "$tbl" "$col" "${n:-?}"
  TOTAL=$((TOTAL + ${n:-0}))
done
echo
echo "合计命中：${TOTAL} 行（另会清掉它们关联的 zt_action / zt_storyspec / zt_casestep / zt_projectproduct / zt_module / zt_projectstory / zt_team / zt_burn / zt_effort 行）"

if [ "$APPLY" != "--yes" ]; then
  echo
  echo "（dry-run 结束；加 --yes 才真正删除）"
  exit 0
fi

echo
echo "== 执行删除（单事务） =="
mysql_exec "
START TRANSACTION;
CREATE TEMPORARY TABLE t_proj   AS SELECT id FROM \`ruoyi-vue-pro\`.zt_project     WHERE name  REGEXP '$EPOCH';
CREATE TEMPORARY TABLE t_prod   AS SELECT id FROM \`ruoyi-vue-pro\`.zt_product     WHERE name  REGEXP '$EPOCH';
CREATE TEMPORARY TABLE t_story  AS SELECT id FROM \`ruoyi-vue-pro\`.zt_story       WHERE title REGEXP '$EPOCH';
CREATE TEMPORARY TABLE t_case   AS SELECT id FROM \`ruoyi-vue-pro\`.zt_case        WHERE title REGEXP '$EPOCH';
CREATE TEMPORARY TABLE t_bug    AS SELECT id FROM \`ruoyi-vue-pro\`.zt_bug         WHERE title REGEXP '$EPOCH';
CREATE TEMPORARY TABLE t_doclib AS SELECT id FROM \`ruoyi-vue-pro\`.zt_doclib      WHERE name  REGEXP '$EPOCH';
CREATE TEMPORARY TABLE t_kanban AS SELECT id FROM \`ruoyi-vue-pro\`.zt_kanban      WHERE name  REGEXP '$EPOCH';
CREATE TEMPORARY TABLE t_plan   AS SELECT id FROM \`ruoyi-vue-pro\`.zt_productplan WHERE title REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_action WHERE objectID IN (SELECT id FROM t_proj);
DELETE FROM \`ruoyi-vue-pro\`.zt_action WHERE objectID IN (SELECT id FROM t_prod);
DELETE FROM \`ruoyi-vue-pro\`.zt_action WHERE objectID IN (SELECT id FROM t_story);
DELETE FROM \`ruoyi-vue-pro\`.zt_action WHERE objectID IN (SELECT id FROM t_case);
DELETE FROM \`ruoyi-vue-pro\`.zt_action WHERE objectID IN (SELECT id FROM t_bug);
DELETE FROM \`ruoyi-vue-pro\`.zt_doc        WHERE lib    IN (SELECT id FROM t_doclib);
DELETE FROM \`ruoyi-vue-pro\`.zt_kanbancard WHERE kanban IN (SELECT id FROM t_kanban);
UPDATE      \`ruoyi-vue-pro\`.zt_story SET plan=0 WHERE plan IN (SELECT id FROM t_plan);
DELETE FROM \`ruoyi-vue-pro\`.zt_story       WHERE product IN (SELECT id FROM t_prod);
DELETE FROM \`ruoyi-vue-pro\`.zt_case        WHERE product IN (SELECT id FROM t_prod);
DELETE FROM \`ruoyi-vue-pro\`.zt_bug         WHERE product IN (SELECT id FROM t_prod);
DELETE FROM \`ruoyi-vue-pro\`.zt_build       WHERE product IN (SELECT id FROM t_prod);
DELETE FROM \`ruoyi-vue-pro\`.zt_release     WHERE product IN (SELECT id FROM t_prod);
DELETE FROM \`ruoyi-vue-pro\`.zt_module      WHERE root    IN (SELECT id FROM t_prod);
DELETE FROM \`ruoyi-vue-pro\`.zt_projectproduct WHERE product IN (SELECT id FROM t_prod);
DELETE FROM \`ruoyi-vue-pro\`.zt_projectproduct WHERE project IN (SELECT id FROM t_proj);
DELETE FROM \`ruoyi-vue-pro\`.zt_module      WHERE root    IN (SELECT id FROM t_proj);
DELETE FROM \`ruoyi-vue-pro\`.zt_projectstory WHERE project IN (SELECT id FROM t_proj);
DELETE FROM \`ruoyi-vue-pro\`.zt_team        WHERE root    IN (SELECT id FROM t_proj);
DELETE FROM \`ruoyi-vue-pro\`.zt_burn        WHERE execution IN (SELECT id FROM t_proj);
DELETE FROM \`ruoyi-vue-pro\`.zt_effort      WHERE project   IN (SELECT id FROM t_proj);
DELETE FROM \`ruoyi-vue-pro\`.zt_effort      WHERE execution IN (SELECT id FROM t_proj);
DELETE FROM \`ruoyi-vue-pro\`.zt_storyspec   WHERE story IN (SELECT id FROM t_story);
DELETE FROM \`ruoyi-vue-pro\`.zt_casestep    WHERE \`case\` IN (SELECT id FROM t_case);
DELETE FROM \`ruoyi-vue-pro\`.zt_case  WHERE id IN (SELECT id FROM t_case);
DELETE FROM \`ruoyi-vue-pro\`.zt_bug   WHERE id IN (SELECT id FROM t_bug);
DELETE FROM \`ruoyi-vue-pro\`.zt_story WHERE id IN (SELECT id FROM t_story);
DELETE FROM \`ruoyi-vue-pro\`.zt_task     WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_effort   WHERE work  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_todo     WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_branch   WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_build    WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_release  WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_testtask WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_repo     WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_dataview WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_chart    WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_module   WHERE name  REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_doc      WHERE title REGEXP '$EPOCH';
DELETE FROM \`ruoyi-vue-pro\`.zt_doclib   WHERE id IN (SELECT id FROM t_doclib);
DELETE FROM \`ruoyi-vue-pro\`.zt_kanban   WHERE id IN (SELECT id FROM t_kanban);
DELETE FROM \`ruoyi-vue-pro\`.zt_productplan WHERE id IN (SELECT id FROM t_plan);
DELETE FROM \`ruoyi-vue-pro\`.zt_project  WHERE id IN (SELECT id FROM t_proj);
DELETE FROM \`ruoyi-vue-pro\`.zt_product  WHERE id IN (SELECT id FROM t_prod);
COMMIT;"
echo "  删除完成"

echo
echo "== 复核 =="
printf '  残留（应为 0）: %s\n' "$(q "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_project WHERE name REGEXP '^(UI|测试|BURN|项目集).*-[0-9]{10,13}\$' OR name='测试';")"
printf '  真实项目集: %s\n' "$(q "SELECT GROUP_CONCAT(name SEPARATOR ' / ') FROM \`ruoyi-vue-pro\`.zt_project WHERE type='program';")"
printf '  真实产品: %s\n' "$(q "SELECT GROUP_CONCAT(name SEPARATOR ' / ') FROM \`ruoyi-vue-pro\`.zt_product WHERE deleted='\0';")"
printf '  项目 9001 下项目数: %s\n' "$(q "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_project WHERE parent=9001 AND type='project';")"

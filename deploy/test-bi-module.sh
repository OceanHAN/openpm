#!/bin/bash
# BI（数据视图 + 图表）接口测试
#
# 禅道语义（module/bi + module/pivot + module/chart）：
#   1. 链路：数据视图（只读 SQL）→ 图表（维度分组 + 指标聚合）
#   2. 禅道底层用 DuckDB + Parquet，本实现走禅道自己也支持的 **SQL 模式**：
#      数据视图存一条只读 SELECT，直接在 MySQL 上执行
#   3. 因此**安全边界必须自己守住**：单语句 / 必须 SELECT / 关键字黑名单 /
#      只允许 zt_* 表 / 字段名白名单 / 聚合函数取自枚举 / 结果包一层 LIMIT
#   4. 数据视图被图表引用时不能删（本实现加的保护）
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

source "$(dirname "$0")/_mysql.sh"

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')
# SQL 试跑用 JSON body：用 python 生成 JSON，省得在 shell 里和引号搏斗
trysql_json() { python3 -c "import json,sys;print(json.dumps({'sql':sys.argv[1],'limit':2}))" "$1"; }

check() {
  local name="$1" want="$2" body="$3" frag="$4"
  local code msg
  code=$(echo "$body" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null)
  msg=$(echo "$body" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("msg",""))' 2>/dev/null)
  if [ "$code" = "$want" ] && { [ -z "$frag" ] || [[ "$msg" == *"$frag"* ]]; }; then
    PASS=$((PASS+1)); printf '  ✅ %-52s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-52s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }
# 试跑一段 SQL（POST + 表单参数，避免中文/特殊字符进 URL）
trysql() { trysql_json "$1" | curl -s -X POST "${H[@]}" "$BASE/zentao/bi/dataview/preview-sql" --data-binary @-; }

echo "===== BI（数据视图 + 图表）模块测试 ====="
TS=$(date +%s)

# 清掉上次中断留下的测试图表与视图（脚本要可重复执行）
for id in $(curl -s "${H[@]}" "$BASE/zentao/bi/chart/list" \
  | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data') or []
print(' '.join(str(x['id']) for x in d if (x['name'] or '').startswith('接口测试图表-') or x['name'] == '坏图表'))"); do
  curl -s -X DELETE "${H[@]}" "$BASE/zentao/bi/chart/delete?id=$id" >/dev/null
done
for id in $(curl -s "${H[@]}" "$BASE/zentao/bi/dataview/list" \
  | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data') or []
print(' '.join(str(x['id']) for x in d if (x['name'] or '').startswith('接口测试视图-')))"); do
  curl -s -X DELETE "${H[@]}" "$BASE/zentao/bi/dataview/delete?id=$id" >/dev/null
done

echo "--- 1. 演示数据视图 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/bi/dataview/list")
line=$(echo "$R" | field "len(d), sorted(x['code'] for x in d)")
[ "${line}" = "3 ['bug_data', 'story_data', 'task_data']" ] && { PASS=$((PASS+1)); echo "  ✅ 3 个演示数据视图（需求/任务/缺陷）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 数据视图列表 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/bi/dataview/get?id=97001")
line=$(echo "$R" | field "d['code'], d['mode'], d['chartCount'], len(d['fields']), d['langs']['id']")
[ "${line}" = "story_data sql 1 5 需求数" ] && { PASS=$((PASS+1)); echo "  ✅ 数据视图详情：字段（维度/指标）+ 中文名 + 被 1 个图表引用"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 数据视图详情 实际=${line}"; }
line=$(echo "$R" | field "[ (f['field'], f['type']) for f in d['fields'] ][:2]")
[ "${line}" = "[('id', 'metric'), ('status', 'dimension')]" ] && { PASS=$((PASS+1)); echo "  ✅ 字段区分「指标 / 维度」（种子里的 fields 原样保留）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 字段类型 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/bi/dataview/preview?id=97001&limit=2")
line=$(echo "$R" | field "d['total'], len(d['columns']) >= 5, 'status' in d['columns'], 'LIMIT 2' in d['executedSql']")
[ "${line}" = "2 True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 预览：最多取 N 行，SQL 被包了一层 LIMIT"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 预览 实际=${line}"; }

echo "--- 2. SQL 白名单（SqlGuard） ---"
R=$(trysql "SELECT id, status FROM zt_story WHERE deleted = 0")
line=$(echo "$R" | field "d['total'] >= 1, 'id' in d['columns']")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 合法 SQL 可以试跑（返回列与数据）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 试跑合法 SQL 实际=${line}"; }

R=$(trysql "SELECT id FROM zt_story; DROP TABLE zt_story")
check "多语句 → 拒绝" 1020027002 "$R" "只能写一条语句"
R=$(trysql "DELETE FROM zt_story WHERE 1=1")
check "非查询 → 拒绝" 1020027002 "$R" "只允许 SELECT"
R=$(trysql "SELECT id FROM zt_story WHERE id = 1 -- 注释")
check "注释符 → 拒绝" 1020027002 "$R" "注释符"
R=$(trysql "SELECT id FROM system_users WHERE 1=1")
check "查系统表 → 拒绝" 1020027003 "$R" "只允许查询禅道自己的表"
R=$(trysql "SELECT id FROM \`ruoyi-vue-pro\`.zt_story")
check "带库名前缀 → 拒绝" 1020027003 "$R" "不能带库名前缀"
R=$(trysql "SELECT id FROM information_schema.tables")
check "查 information_schema → 拒绝" 1020027002 "$R" "系统库"
R=$(trysql "SELECT COUNT(*) AS total FROM zt_bug WHERE severity = 1")
line=$(echo "$R" | field "d['columns'], d['rows'][0]['total'] >= 0")
[ "${line}" = "['total'] True" ] && { PASS=$((PASS+1)); echo "  ✅ 聚合字段按别名解析（COUNT(*) AS total）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 聚合别名 实际=${line}"; }

echo "--- 3. 数据视图 CRUD ---"
DV=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bi/dataview/create" -H 'Content-Type: application/json' \
  -d "{\"name\":\"接口测试视图-$TS\",\"code\":\"it_view_$TS\",\"sql\":\"SELECT id, project, status, consumed FROM zt_task WHERE deleted = 0\"}" | d)
[ -n "$DV" ] && { PASS=$((PASS+1)); echo "  ✅ 新建数据视图（自动解析字段）：编号 $DV"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建数据视图 失败：$DV"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/bi/dataview/get?id=$DV")
line=$(echo "$R" | field "[f['field'] for f in d['fields']]")
[ "${line}" = "['id', 'project', 'status', 'consumed']" ] && { PASS=$((PASS+1)); echo "  ✅ 未传 fields 时按 SQL 自动解析出 4 个字段"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 自动解析字段 实际=${line}"; }

R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bi/dataview/create" -H 'Content-Type: application/json' \
  -d "{\"name\":\"重复代码\",\"code\":\"it_view_$TS\",\"sql\":\"SELECT id FROM zt_task\"}")
check "代码重复 → 拒绝" 1020027001 "$R" "代码已存在"

R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/bi/dataview/update" -H 'Content-Type: application/json' \
  -d "{\"id\":$DV,\"name\":\"接口测试视图-$TS-改\",\"code\":\"it_view_$TS\",\"sql\":\"SELECT id, status FROM zt_task WHERE deleted = 0\"}")
check "修改数据视图" 0 "$R" ""
line=$(curl -s "${H[@]}" "$BASE/zentao/bi/dataview/get?id=$DV" | field "d['name'], len(d['fields'])")
[ "${line}" = "接口测试视图-$TS-改 2" ] && { PASS=$((PASS+1)); echo "  ✅ 改完字段跟着重新解析（4 → 2）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修改后字段 实际=${line}"; }

R=$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/bi/dataview/delete?id=97001")
check "被图表引用的数据视图不能删" 1020027005 "$R" "引用"

echo "--- 4. 图表定义与数据 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/bi/dict")
line=$(echo "$R" | field "len(d['chartTypeList']), len(d['aggList']), len(d['dataViewList']) >= 3")
[ "${line}" = "8 5 True" ] && { PASS=$((PASS+1)); echo "  ✅ 字典：8 种图表类型 / 5 种聚合 / 可选数据视图"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 字典 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/bi/chart/list")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
codes={x['code'] for x in d}
types={x['type'] for x in d}
print({'story_status_pie','task_status_hour_bar','bug_severity_pie'} <= codes, 'pie' in types, 'cluBarX' in types)")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 3 个演示图表都在（饼图 + 簇状柱形图）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 图表列表 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/bi/chart/get?id=97101")
line=$(echo "$R" | field "d['typeName'], d['echartsType'], d['viewCode'], d['settings']['agg'], d['settings']['dimensionField']")
[ "${line}" = "饼图 pie story_data count status" ] && { PASS=$((PASS+1)); echo "  ✅ 图表定义：类型名/渲染类型/数据视图/聚合/维度 都对"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 图表定义 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/bi/chart/data?id=97101")
line=$(echo "$R" | field "d['agg'], d['dimensionField'], len(d['rows']) >= 1, 'GROUP BY' in d['executedSql']")
[ "${line}" = "count status True True" ] && { PASS=$((PASS+1)); echo "  ✅ 图表数据：按状态分组计数"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 图表数据 实际=${line}"; }
# 与直查数据库交叉校验：各状态条数之和 = 未删除的需求数
DB_STORY=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_story WHERE deleted = 0;")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(sum(int(float(r['value'])) for r in d['rows']))")
[ "${line}" = "${DB_STORY}" ] && { PASS=$((PASS+1)); echo "  ✅ 各扇区之和 = 需求总数（${DB_STORY}），与库一致"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 分组求和 实际=${line} 期望=${DB_STORY}"; }

# 求和型：任务已消耗工时
R=$(curl -s "${H[@]}" "$BASE/zentao/bi/chart/data?id=97102")
DB_HOUR=$(mysql_query "SELECT COALESCE(ROUND(SUM(consumed),2),0) FROM \`ruoyi-vue-pro\`.zt_task WHERE deleted = 0;")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['agg'], '%.2f' % sum(float(r['value']) for r in d['rows']))")
[ "${line}" = "sum ${DB_HOUR}" ] && { PASS=$((PASS+1)); echo "  ✅ 聚合方式 sum：分组求和与库一致（${DB_HOUR} 小时）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ sum 聚合 实际=${line} 期望=sum ${DB_HOUR}"; }

# 排序：按值降序
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
vals=[float(r['value']) for r in d['rows']]
print(vals == sorted(vals, reverse=True))")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 默认按值降序"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 排序 实际=${line}"; }

echo "--- 5. 图表 CRUD 与过滤器 ---"
CH=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bi/chart/create" -H 'Content-Type: application/json' \
  -d "{\"name\":\"接口测试图表-$TS\",\"code\":\"it_chart_$TS\",\"type\":\"cluBarX\",\"viewCode\":\"task_data\",\"settings\":{\"dimensionField\":\"status\",\"metricField\":\"consumed\",\"agg\":\"sum\",\"limit\":5},\"filters\":[{\"field\":\"project\",\"operator\":\"gt\",\"value\":0}]}" | d)
[ -n "$CH" ] && { PASS=$((PASS+1)); echo "  ✅ 新建图表（引用数据视图 task_data + 过滤器）：编号 $CH"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建图表 失败：$CH"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/bi/chart/data?id=$CH")
line=$(echo "$R" | field "len(d['rows']) >= 1, 'WHERE' in d['executedSql'], 'project' in d['executedSql']")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 过滤器生效（WHERE project > 0 拼进 SQL）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 过滤器 实际=${line}"; }

R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/bi/chart/update" -H 'Content-Type: application/json' \
  -d "{\"id\":$CH,\"name\":\"接口测试图表-$TS-改\",\"type\":\"pie\",\"viewCode\":\"task_data\",\"settings\":{\"dimensionField\":\"status\",\"metricField\":\"id\",\"agg\":\"count\"}}")
check "修改图表" 0 "$R" ""
line=$(curl -s "${H[@]}" "$BASE/zentao/bi/chart/get?id=$CH" | field "d['version'], d['type'], d['settings']['agg']")
[ "${line}" = "2 pie count" ] && { PASS=$((PASS+1)); echo "  ✅ 图表是版本化对象：改一次 version+1（1 → 2）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 图表版本 实际=${line}"; }

R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bi/chart/create" -H 'Content-Type: application/json' \
  -d "{\"name\":\"坏图表\",\"sql\":\"SELECT id FROM zt_task\",\"settings\":{\"dimensionField\":\"status\",\"agg\":\"median\"}}")
check "聚合方式不在白名单 → 拒绝" 1020027012 "$R" "聚合方式不合法"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bi/chart/create" -H 'Content-Type: application/json' \
  -d "{\"name\":\"坏图表\",\"sql\":\"SELECT id FROM zt_task\",\"settings\":{\"dimensionField\":\"status\",\"agg\":\"sum\"}}")
check "sum 聚合缺指标字段 → 拒绝" 1020027013 "$R" "必须指定指标字段"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bi/chart/create" -H 'Content-Type: application/json' \
  -d "{\"name\":\"坏图表\",\"type\":\"bogus\",\"sql\":\"SELECT id FROM zt_task\",\"settings\":{\"dimensionField\":\"status\",\"agg\":\"count\"}}")
check "图表类型非法 → 拒绝" 1020027011 "$R" "图表类型不合法"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bi/chart/create" -H 'Content-Type: application/json' \
  -d "{\"name\":\"坏图表\",\"sql\":\"SELECT id FROM zt_task\",\"settings\":{\"dimensionField\":\"status; DROP TABLE zt_task\",\"agg\":\"count\"}}")
check "维度字段带注入片段 → 拒绝" 1020027014 "$R" "字段名不合法"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bi/chart/create" -H 'Content-Type: application/json' \
  -d "{\"name\":\"坏图表\",\"sql\":\"SELECT id FROM zt_task WHERE 1=1 -- x\",\"settings\":{\"dimensionField\":\"status\",\"agg\":\"count\"}}")
check "数据源 SQL 带注释符 → 拒绝" 1020027002 "$R" "注释符"

echo "--- 6. 清理 ---"
check "删除图表" 0 "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/bi/chart/delete?id=$CH")" ""
check "删掉图表后数据视图可以删" 0 "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/bi/dataview/delete?id=$DV")" ""
R=$(curl -s "${H[@]}" "$BASE/zentao/bi/dataview/list")
line=$(echo "$R" | field "len(d)")
[ "${line}" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 演示数据完好（仍 3 个数据视图）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 清理后 实际=${line}"; }

echo "======================================================"
echo "  BI 模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

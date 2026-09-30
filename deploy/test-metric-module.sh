#!/bin/bash
# 度量（metric）接口测试
#
# 禅道语义（module/metric）：
#   1. 三块职责：定义在 zt_metric（目的/范围/对象/单位/时间维度/口径说明）、
#      口径在代码里（禅道 module/metric/calc 下 414 个 calc 类，本实现是 Java 注册表）、
#      数据在 zt_metriclib（一行 = 维度组合 + 时间粒度 + value）
#   2. 记录的主键逻辑是「度量项 + 维度 + 时间」：重算前按周期清旧数据
#      （year → 清该年；month → 清该年该月；week → 清该年该周；day → 清该年该月该日）
#   3. nodate（快照）型不写时间列，靠 date 记「算的那一天」，查询只看 date >= 今天
#   4. 未迁移的口径必须**报错**，不能算出一个错的值
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

source "$(dirname "$0")/_mysql.sh"

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')

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
calc() { curl -s -X POST "${H[@]}" "$BASE/zentao/metric/calc?code=$1&calcType=${2:-inference}"; }
data() { curl -s "${H[@]}" "$BASE/zentao/metric/data?code=$1&scope=$2&pageNo=1&pageSize=100"; }

echo "===== 度量（metric）模块测试 ====="

echo "--- 1. 度量项定义（口径来自禅道 calc 类，代码原样） ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/metric/summary")
line=$(echo "$R" | field "d['totalCount'], d['implementedCount'], d['pendingCount']")
[ "${line}" = "18 15 3" ] && { PASS=$((PASS+1)); echo "  ✅ 概览：18 个度量项（15 个已迁移口径 + 3 个未迁移）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 概览 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/get?code=count_of_story_in_product")
line=$(echo "$R" | field "d['purpose'], d['purposeName'], d['scope'], d['object'], d['unit'], d['dateType'], d['implemented'], d['builtin']")
[ "${line}" = "scale 规模估算 product story 个 nodate True True" ] && { PASS=$((PASS+1)); echo "  ✅ 度量项定义：目的/范围/对象/单位/时间维度/已迁移/内置 都对"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 度量项定义 实际=${line}"; }
line=$(echo "$R" | field "len(d['definition']) > 10 and '研发需求' in d['name']")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 口径说明（definition）来自禅道 calc 类的「定义」段"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 口径说明 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/get?code=count_of_story_in_stage_in_product")
line=$(echo "$R" | field "d['implemented'], d['scope'], d['object']")
[ "${line}" = "False stage story" ] && { PASS=$((PASS+1)); echo "  ✅ 未迁移口径标记 implemented=false"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未迁移口径 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/page?pageNo=1&pageSize=100&onlyImplemented=true")
line=$(echo "$R" | field "d['total']")
[ "${line}" = "15" ] && { PASS=$((PASS+1)); echo "  ✅ 「只看已迁移口径」过滤出 15 条"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ onlyImplemented 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/page?pageNo=1&pageSize=100&scope=product")
line=$(echo "$R" | field "d['total'] > 5, all(x['scope']=='product' for x in d['list'])")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 按范围过滤（product 范围全部命中）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按范围过滤 实际=${line}"; }

# 中文查询参数必须百分号编码：Tomcat 在进 Spring 之前就会拒掉 URL 里的原始非 ASCII 字节（坑位 #36）
R=$(curl -s "${H[@]}" "$BASE/zentao/metric/page?pageNo=1&pageSize=100&keyword=%E9%9C%80%E6%B1%82")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
text=lambda x: (x or '')
print(d['total'] > 0, all('需求' in (text(x.get('name'))+text(x.get('alias'))+text(x.get('code'))) for x in d['list']))")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 关键词过滤（名称/别名/代码）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 关键词过滤 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/dict")
line=$(echo "$R" | field "sorted(d.keys())")
[ "${line}" = "['calcTypeList', 'dateTypeList', 'objectList', 'purposeList', 'scopeList', 'unitList']" ] && { PASS=$((PASS+1)); echo "  ✅ 字典包含 目的/范围/对象/单位/时间维度/计算方式"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 字典 实际=${line}"; }
line=$(echo "$R" | field "len(d['purposeList']) >= 6, len(d['scopeList']) == 6, len(d['dateTypeList']) == 5")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 字典项数与禅道配置一致（目的 6 / 范围 6 / 时间维度 5）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 字典项数 实际=${line}"; }

echo "--- 2. 计算（口径 → 清旧数据 → 写 metriclib → 回写） ---"
R=$(calc count_of_story_in_product)
line=$(echo "$R" | field "d['cycle'], d['calcType'], d['recordCount'] >= 1")
[ "${line}" = "nodate inference True" ] && { PASS=$((PASS+1)); echo "  ✅ 计算快照型度量项：cycle=nodate / 人工触发"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 计算快照型 实际=${line}"; }

# 与库里的真实数据交叉校验（不是看接口自说自话）
DB_STORY=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_story WHERE deleted = 0 AND product > 0 AND type = 'story';")
R=$(data count_of_story_in_product product)
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
total=sum(int(float(r['value'])) for r in d['rows'])
p1=[r for r in d['rows'] if r['scopeObjectId']==1]
print(total, len(p1) > 0, p1[0]['scopeObjectName'] if p1 else '')")
[ "${line% *}" = "${DB_STORY} True" ] && { PASS=$((PASS+1)); echo "  ✅ 计算值与直查数据库一致（研发需求总数 = ${DB_STORY}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 与库交叉校验 实际=${line} 期望=${DB_STORY}"; }

line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
r=[x for x in d['rows'] if x['scopeObjectId']==1][0]
print(r['period'].endswith('（快照）'), len(r['date']) > 10, r['calcType'], r['calculatedBy'])")
[ "${line}" = "True True inference admin" ] && { PASS=$((PASS+1)); echo "  ✅ 快照行带计算时间、周期、计算方式与计算人"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 快照行字段 实际=${line}"; }

# 幂等：再算一次，快照数据不翻倍
BEFORE=$(data count_of_story_in_product product | field "d['total']")
calc count_of_story_in_product >/dev/null
AFTER=$(data count_of_story_in_product product | field "d['total']")
[ "${BEFORE}" = "${AFTER}" ] && { PASS=$((PASS+1)); echo "  ✅ 重复计算不翻倍（nodate 清「今天的快照」：${BEFORE} = ${AFTER}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 幂等性 实际=${BEFORE} → ${AFTER}"; }

R=$(calc count_of_story_in_product cron)
line=$(echo "$R" | field "d['calcType']")
DBCALC=$(data count_of_story_in_product product | field "d['rows'][0]['calcType'], d['rows'][0]['calculatedBy']")
[ "${line}" = "cron" ] && [ "${DBCALC}" = "cron system" ] && { PASS=$((PASS+1)); echo "  ✅ calcType=cron 时 calculatedBy=system（定时任务的口径）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ cron 计算 实际=${line} / ${DBCALC}"; }

R=$(calc count_of_annual_created_story_in_product)
line=$(echo "$R" | field "d['cycle'], d['recordCount'] >= 1")
[ "${line}" = "year True" ] && { PASS=$((PASS+1)); echo "  ✅ 年度口径：cycle=year（记录里只填 year 列）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 年度口径 实际=${line}"; }
R=$(data count_of_annual_created_story_in_product product)
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
r=[x for x in d['rows'] if x['scopeObjectId']==1][0]
print(r['year'], r['period'], len(r['week']), len(r['month']))")
[ "${line}" = "2026 2026 年 0 0" ] && { PASS=$((PASS+1)); echo "  ✅ 年度数据只带 year，不带 month/week"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 年度数据行 实际=${line}"; }

R=$(calc consume_of_all_in_project)
line=$(echo "$R" | field "d['cycle'], d['recordCount'] >= 1")
[ "${line}" = "nodate True" ] && { PASS=$((PASS+1)); echo "  ✅ 工时型口径（项目内所有消耗工时）计算成功"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 工时口径 实际=${line}"; }
DB_EFFORT=$(mysql_query "SELECT ROUND(SUM(consumed),2) FROM \`ruoyi-vue-pro\`.zt_effort WHERE deleted = 0 AND project > 0;")
R=$(data consume_of_all_in_project project)
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('%.2f' % sum(float(r['value']) for r in d['rows']))")
[ "${line}" = "${DB_EFFORT}" ] && { PASS=$((PASS+1)); echo "  ✅ 工时合计与库一致（${DB_EFFORT} 小时）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 工时合计 实际=${line} 期望=${DB_EFFORT}"; }

R=$(calc count_of_created_bug_in_user)
DB_BUG=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_bug WHERE deleted = 0;")
R2=$(data count_of_created_bug_in_user user)
line=$(echo "$R2" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
total=sum(int(float(r['value'])) for r in d['rows'])
named=all(r['scopeObjectName'] for r in d['rows'])
print(total, named)")
[ "${line}" = "${DB_BUG} True" ] && { PASS=$((PASS+1)); echo "  ✅ 人员维度口径：合计=${DB_BUG}（与库一致）、每行都解析出人名"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 人员维度 实际=${line} 期望=${DB_BUG} True"; }

R=$(calc count_of_story_in_stage_in_product)
check "未迁移口径 → 拒绝（不算错的值）" 1020026002 "$R" "口径尚未迁移"
R=$(calc no_such_metric_code)
check "不存在的度量项 → 拒绝" 1020026000 "$R" "度量项不存在"

R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/metric/calc-all?calcType=inference")
line=$(echo "$R" | field "len(d), sum(1 for x in d if x['recordCount'] >= 0)")
[ "${line}" = "15 15" ] && { PASS=$((PASS+1)); echo "  ✅ 批量计算：15 个已迁移口径全部成功"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 批量计算 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/get?code=count_of_bug_in_product")
line=$(echo "$R" | field "d['lastCalcRows'] > 0, d['lastCalcTime'] is not None, d['dataCount'] > 0")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 计算后回写 lastCalcRows / lastCalcTime，且 dataCount 可查"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 回写 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/summary")
line=$(echo "$R" | field "d['dataCount'] > 0, d['lastCalcTime'] is not None")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 概览里的数据量与上次计算时间跟着更新"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 概览更新 实际=${line}"; }

echo "--- 3. 数据查询 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/metric/data?code=count_of_story_in_product&scope=product&pageNo=1&pageSize=1")
line=$(echo "$R" | field "len(d['rows']), d['total'] >= 1")
[ "${line}" = "1 True" ] && { PASS=$((PASS+1)); echo "  ✅ 度量数据支持分页"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 分页 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/data?code=count_of_story_in_product&pageNo=1&pageSize=10")
line=$(echo "$R" | field "d['metric']['code'], len(d['rows']) > 0")
[ "${line}" = "count_of_story_in_product True" ] && { PASS=$((PASS+1)); echo "  ✅ 不传 scope 时用度量项自己的范围"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 默认范围 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/data?code=count_of_annual_created_story_in_product&scope=product&dateBegin=2000-01-01&dateEnd=1999-12-31&pageNo=1&pageSize=10")
line=$(echo "$R" | field "d['total']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 按时间维度过滤（反区间 → 0 条）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 时间过滤 实际=${line}"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/metric/data?code=count_of_annual_created_story_in_product&scope=product&dateBegin=2020-01-01&dateEnd=2030-12-31&pageNo=1&pageSize=10")
line=$(echo "$R" | field "d['total'] >= 1")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 时间区间覆盖数据时可查到"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 时间区间 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/metric/data?code=count_of_bug_in_product&scope=product&pageNo=1&pageSize=10")
line=$(echo "$R" | field "all(r['scopeObjectName'] for r in d['rows']), all(r['metricCode']=='count_of_bug_in_product' for r in d['rows'])")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 行里带对象名（产品名）与度量项代码"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 行字段 实际=${line}"; }

echo "======================================================"
echo "  度量模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

#!/bin/bash
# 测试仪表盘（qa）模块测试：质量统计口径 / 三个列表块 / 产品与区间过滤
#
# 禅道语义（module/qa，153 行、1 个 action）：
#   1. qa.index **什么都不算** —— 它只是把 `block` 的看板拼出来（dashboard=qa）
#   2. 看板里那块「质量统计」的口径在 module/bi/config/metrics.php 的度量定义里：
#        count_of_daily_created_bug_in_product   区间内新增缺陷
#        count_of_daily_resolved_bug_in_product  区间内解决缺陷
#        count_of_daily_closed_bug_in_product    区间内关闭缺陷
#        count_of_effective_bug_in_product       有效缺陷 = 解决方案为已修复/延期处理/不予解决 **或状态为激活**
#        count_of_fixed_bug_in_product           已修复数（resolution=fixed）
#        rate_of_fixed_bug_in_product            修复率 = 已修复 ÷ 有效缺陷
#   3. 另外三块是列表：待处理缺陷（active）、待评审用例（wait）、未完成测试单（wait/doing）
#
# 本脚本重点是**修复率的分母**：它不是缺陷总数，而是「有效缺陷」—— 这是最容易写错的一条。
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

source "$(dirname "$0")/_mysql.sh"

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')
HF=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1')

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
TS=$(date +%s); TODAY=$(date +%F)

echo "===== 测试仪表盘（质量统计 / 待处理缺陷 / 待评审用例 / 未完成测试单）测试 ====="

echo "--- 1. 汇总与库交叉核对 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard")
line=$(echo "$R" | field "d['days'], d['begin']")
[ "${line}" = "7 $(date -v-6d +%F 2>/dev/null || date -d '-6 days' +%F)" ] && { PASS=$((PASS+1)); echo "  ✅ 默认区间是最近 7 天（${line}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 默认区间 实际=${line}"; }
DB_TOTAL=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_bug WHERE deleted+0=0;")
DB_ACTIVE=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_bug WHERE deleted+0=0 AND status='active';")
line=$(echo "$R" | field "d['summary']['bugTotal'], d['summary']['bugActive']")
[ "${line}" = "${DB_TOTAL} ${DB_ACTIVE}" ] && { PASS=$((PASS+1)); echo "  ✅ 缺陷总数/激活数与库一致（${DB_TOTAL} / ${DB_ACTIVE}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 缺陷汇总 接口=${line} 库=${DB_TOTAL} ${DB_ACTIVE}"; }
DB_EFFECTIVE=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_bug WHERE deleted+0=0 AND (status='active' OR resolution IN ('fixed','postponed','willnotfix'));")
line=$(echo "$R" | field "d['summary']['bugEffective']")
[ "${line}" = "${DB_EFFECTIVE}" ] && { PASS=$((PASS+1)); echo "  ✅ 有效缺陷 = 激活 或 已修复/延期/不予解决（${DB_EFFECTIVE}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 有效缺陷 接口=${line} 库=${DB_EFFECTIVE}"; }
DB_CASE=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_case WHERE deleted+0=0 AND lib=0;")
DB_CASE_WAIT=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_case WHERE deleted+0=0 AND lib=0 AND status='wait';")
line=$(echo "$R" | field "d['summary']['caseTotal'], d['summary']['caseWait']")
[ "${line}" = "${DB_CASE} ${DB_CASE_WAIT}" ] && { PASS=$((PASS+1)); echo "  ✅ 用例总数/待评审数与库一致（只算产品用例，${DB_CASE} / ${DB_CASE_WAIT}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用例汇总 接口=${line} 库=${DB_CASE} ${DB_CASE_WAIT}"; }
DB_TT=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_testtask WHERE deleted+0=0;")
DB_TT_OPEN=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_testtask WHERE deleted+0=0 AND status IN ('wait','doing');")
line=$(echo "$R" | field "d['summary']['testTaskTotal'], d['summary']['testTaskUnclosed']")
[ "${line}" = "${DB_TT} ${DB_TT_OPEN}" ] && { PASS=$((PASS+1)); echo "  ✅ 测试单总数/未完成数与库一致（${DB_TT} / ${DB_TT_OPEN}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 测试单汇总 接口=${line} 库=${DB_TT} ${DB_TT_OPEN}"; }
line=$(echo "$R" | field "len(d['productQuality'])")
DB_PRODUCTS=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_product WHERE deleted+0=0;")
[ "${line}" = "${DB_PRODUCTS}" ] && { PASS=$((PASS+1)); echo "  ✅ 按产品统计覆盖全部产品（${DB_PRODUCTS} 个）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 产品行数 接口=${line} 库=${DB_PRODUCTS}"; }

echo "--- 2. 修复率：造两个缺陷，一个有效（fixed）、一个无效（bydesign）---"
# 准备：新建一个专属产品，避免污染演示产品的统计
PID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/product/create" \
  -d "{\"name\":\"QA产品-$TS\",\"code\":\"qa$TS\",\"PO\":\"admin\"}" | d)
[ -n "$PID" ] && { PASS=$((PASS+1)); echo "  ✅ 准备：建一个专属产品 $PID"; } || { FAIL=$((FAIL+1)); echo "  ❌ 建产品失败"; }
B1=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bug/create" \
  -d "{\"product\":$PID,\"title\":\"QA-有效缺陷-$TS\",\"severity\":2,\"pri\":2,\"openedBuild\":\"trunk\"}" | d)
B2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bug/create" \
  -d "{\"product\":$PID,\"title\":\"QA-无效缺陷-$TS\",\"severity\":2,\"pri\":2,\"openedBuild\":\"trunk\"}" | d)
[ -n "$B1" ] && [ -n "$B2" ] && { PASS=$((PASS+1)); echo "  ✅ 准备：建两个缺陷 $B1 $B2（都还是激活态）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建缺陷失败：$B1 $B2"; }
R=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard?product=$PID")
line=$(echo "$R" | field "d['summary']['bugTotal'], d['summary']['bugEffective'], d['summary']['bugFixed'], d['summary']['bugFixRate']")
[ "${line}" = "2 2 0 0.0" ] && { PASS=$((PASS+1)); echo "  ✅ 两个激活缺陷 → 有效 2、已修复 0、修复率 0（分母是有效缺陷不是总数）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修复率(初始) 实际=${line}"; }
# 注意（坑位 #46）：JSON 里带转义引号时不能塞进 "$(curl ...)"，macOS 的 bash 3.2 会按空格拆词
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/bug/resolve" -d "{\"id\":$B1,\"resolution\":\"fixed\",\"resolvedBuild\":\"trunk\",\"comment\":\"已修复\"}")
check "把 B1 解决为 fixed" 0 "$R"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/bug/resolve" -d "{\"id\":$B2,\"resolution\":\"bydesign\",\"resolvedBuild\":\"trunk\",\"comment\":\"重复\"}")
check "把 B2 解决为 bydesign（设计如此，不算有效缺陷）" 0 "$R"
R=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard?product=$PID")
line=$(echo "$R" | field "d['summary']['bugTotal'], d['summary']['bugEffective'], d['summary']['bugFixed'], d['summary']['bugFixRate']")
[ "${line}" = "2 1 1 100.0" ] && { PASS=$((PASS+1)); echo "  ✅ 一个 fixed + 一个 bydesign → 有效 1、已修复 1、修复率 100%（bydesign 不算有效）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修复率(解决后) 实际=${line}"; }
line=$(echo "$R" | field "[p for p in d['productQuality'] if p['product']==$PID][0]['effective'], [p for p in d['productQuality'] if p['product']==$PID][0]['fixed']")
[ "${line}" = "1 1" ] && { PASS=$((PASS+1)); echo "  ✅ 按产品的同一行也是 有效 1 / 已修复 1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按产品统计 实际=${line}"; }
line=$(echo "$R" | field "d['summary']['bugOpenedInRange'], d['summary']['bugResolvedInRange']")
[ "${line}" = "2 2" ] && { PASS=$((PASS+1)); echo "  ✅ 区间内新增 2、解决 2（都发生在最近 7 天内）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 区间统计 实际=${line}"; }
line=$(echo "$R" | field "[x['value'] for x in d['bugStatus'] if x['name']=='resolved'][0], [x['value'] for x in d['bugResolution'] if x['name']=='fixed'][0]")
[ "${line}" = "2 1" ] && { PASS=$((PASS+1)); echo "  ✅ 缺陷状态分布与解决方案分布同步（resolved 2 / fixed 1）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 分布 实际=${line}"; }

echo "--- 3. 列表块与过滤 ---"
line=$(echo "$R" | field "len(d['pendingBugs'])")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 刚解决完两个缺陷 → 这个产品的「待处理缺陷」为空"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 待处理缺陷 实际=${line}（期望 0，B1/B2 都已解决）"; }
B3=$(curl -s -X POST "${H[@]}" "$BASE/zentao/bug/create" \
  -d "{\"product\":$PID,\"title\":\"QA-待处理缺陷-$TS\",\"severity\":1,\"pri\":1,\"openedBuild\":\"trunk\"}" | d)
line=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard?product=$PID" | field "len(d['pendingBugs']), d['pendingBugs'][0]['id']")
[ "${line}" = "1 $B3" ] && { PASS=$((PASS+1)); echo "  ✅ 新建一个激活缺陷 → 立刻出现在「待处理缺陷」里（$B3）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 待处理缺陷 实际=${line}"; }
C=$(curl -s -X POST "${H[@]}" "$BASE/zentao/testcase/create" \
  -d "{\"product\":$PID,\"title\":\"QA-待评审用例-$TS\",\"type\":\"feature\",\"status\":\"wait\"}" | d)
TT=$(curl -s -X POST "${H[@]}" "$BASE/zentao/testtask/create" \
  -d "{\"product\":$PID,\"name\":\"QA-测试单-$TS\",\"type\":\"feature\",\"begin\":\"$TODAY\",\"end\":\"2026-12-31\"}" | d)
line=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard?product=$PID" | field "len(d['reviewCases']), d['reviewCases'][0]['id'], len(d['unclosedTestTasks']), d['unclosedTestTasks'][0]['id']")
[ "${line}" = "1 $C 1 $TT" ] && { PASS=$((PASS+1)); echo "  ✅ 待评审用例（$C）与未完成测试单（$TT）都进了对应的列表块"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 列表块 实际=${line}"; }
# 不传 product 时这些数据也在（全局视角）
line=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard" | field "sum(1 for b in d['pendingBugs'] if b['id']==$B3), sum(1 for c in d['reviewCases'] if c['id']==$C)")
[ "${line}" = "1 1" ] && { PASS=$((PASS+1)); echo "  ✅ 全局视角也能看到它们"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 全局视角 实际=${line}"; }
# 区间收窄到 1 天：今天建的东西还在；把区间改成未来不可能（用 days=1 与 days=30 对比新增数）
line=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard?product=$PID&days=1" | field "d['days'], d['summary']['bugOpenedInRange']")
[ "${line}" = "1 3" ] && { PASS=$((PASS+1)); echo "  ✅ days=1 时区间内新增仍是 3（都是今天建的）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ days=1 实际=${line}"; }
# 分布按产品过滤
line=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard?product=$PID" | field "sum(x['value'] for x in d['bugStatus'])")
[ "${line}" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 缺陷状态分布按产品过滤（只有这个产品的 3 个）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按产品过滤 实际=${line}"; }

echo "--- 4. 边界与鉴权 ---"
line=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard?days=0" | field "d['days']")
[ "${line}" = "7" ] && { PASS=$((PASS+1)); echo "  ✅ days=0 / 负数 → 回落到默认 7 天"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ days=0 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard?product=99999999" | field "d['summary']['bugTotal'], len(d['productQuality'])")
[ "${line}" = "0 $(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_product WHERE deleted+0=0;")" ] && { PASS=$((PASS+1)); echo "  ✅ 不存在的产品：汇总为 0，产品列表仍返回全部（不报错）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 不存在产品 实际=${line}"; }
line=$(curl -s "$BASE/zentao/qa/dashboard" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
[ "${line}" = "401" ] && { PASS=$((PASS+1)); echo "  ✅ 不带令牌被拦（code=401）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未登录 实际 code=${line}"; }

echo "--- 5. 清理 ---"
for b in $B1 $B2 $B3; do curl -s -X DELETE "${H[@]}" "$BASE/zentao/bug/delete?id=$b" >/dev/null; done
curl -s -X DELETE "${H[@]}" "$BASE/zentao/testcase/delete?id=$C" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/testtask/delete?id=$TT" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/product/delete?id=$PID" >/dev/null
line=$(curl -s "${HF[@]}" "$BASE/zentao/qa/dashboard?product=$PID" | field "d['summary']['bugTotal'], d['summary']['testTaskTotal']")
[ "${line}" = "0 0" ] && { PASS=$((PASS+1)); echo "  ✅ 清理：删掉产品后它的缺陷与测试单都不再统计（${line}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 清理后 实际=${line}"; }

echo "======================================================"
echo "  qa 模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

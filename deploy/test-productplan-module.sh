#!/bin/bash
# 产品计划（productplan）模块接口测试
#
# 禅道语义：计划是产品维度的排期单元；需求通过 zt_story.plan（逗号列表）挂到计划上。
#   三个特色：分支是多值、待定用日期哨兵 2030-01-01、parent=-1 表示有子计划。
BASE=${ZENTAO_API_BASE:-http://localhost:48080/admin-api}
PASS=0; FAIL=0

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')

check() {
  local name="$1" want="$2" body="$3" frag="$4"
  local code msg
  code=$(echo "$body" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null)
  msg=$(echo "$body" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("msg",""))' 2>/dev/null)
  if [ "$code" = "$want" ] && { [ -z "$frag" ] || [[ "$msg" == *"$frag"* ]]; }; then
    PASS=$((PASS+1)); printf '  ✅ %-44s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-44s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
field() { python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('$1'))"; }

echo "===== 产品计划模块测试 ====="
TS=$(date +%s)

echo "--- 0. 准备：普通产品 + 多平台产品 + 分支 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"PlanProdN-$TS\",\"code\":\"PN$TS\",\"type\":\"normal\",\"PO\":\"admin\"}")
check "普通产品" 0 "$R"
PRODN=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"PlanProdP-$TS\",\"code\":\"PP$TS\",\"type\":\"platform\",\"PO\":\"admin\"}")
check "多平台产品" 0 "$R"
PRODP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PRODP,\"name\":\"平台A-$TS\"}")
check "分支 A" 0 "$R"
BRA=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PRODP,\"name\":\"平台B-$TS\"}")
check "分支 B" 0 "$R"
BRB=$(echo "$R" | d)
echo "    普通产品=$PRODN 平台产品=$PRODP 分支=$BRA,$BRB"

echo "--- 1. 创建校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODN}")
check "缺少名称→参数校验" 400 "$R" "计划名称不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"title\":\"没有产品\"}")
check "缺少产品→参数校验" 400 "$R" "所属产品不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODP,\"title\":\"没选分支-$TS\",\"begin\":\"2026-01-01\",\"end\":\"2026-02-01\"}")
check "多平台产品不选分支→拒绝" 1020009001 "$R" "必须选择平台"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODN,\"title\":\"缺日期-$TS\"}")
check "非待定但缺日期→拒绝" 1020009002 "$R" "不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODN,\"title\":\"日期倒挂-$TS\",\"begin\":\"2026-03-01\",\"end\":\"2026-01-01\"}")
check "结束早于开始→拒绝" 1020009003 "$R" "不能早于"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODN,\"title\":\"普通产品传分支-$TS\",\"branches\":[$BRA],\"begin\":\"2026-01-01\",\"end\":\"2026-02-01\"}")
check "普通产品传真实分支→拒绝" 1020009010 "$R" "不属于该产品"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODP,\"title\":\"分支不属于产品-$TS\",\"branches\":[99999999],\"begin\":\"2026-01-01\",\"end\":\"2026-02-01\"}")
check "分支不属于该产品→拒绝" 1020009010 "$R" "不属于该产品"

echo "--- 2. 正常创建与待定计划 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODN,\"title\":\"普通计划-$TS\",\"begin\":\"2026-01-01\",\"end\":\"2026-06-30\",\"desc\":\"首个版本\"}")
check "普通产品建计划" 0 "$R"
PLAN1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["branch"], d["status"], d["future"], d["createdBy"], d["statusName"])')
[ "$line" = "0 wait False admin 未开始" ] && { PASS=$((PASS+1)); echo "  ✅ 建后默认值 branch/status/future/createdBy = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 建后默认值 期望='0 wait False admin 未开始' 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODN,\"title\":\"待定计划-$TS\",\"future\":true}")
check "待定计划（future=true）" 0 "$R"
PLAN_F=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN_F")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["begin"], d["end"], d["future"])')
[ "$line" = "2030-01-01 2030-01-01 True" ] && { PASS=$((PASS+1)); echo "  ✅ 待定计划日期哨兵 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 待定计划 期望='2030-01-01 2030-01-01 True' 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODP,\"title\":\"平台计划-$TS\",\"branches\":[$BRA,$BRB],\"begin\":\"2026-01-01\",\"end\":\"2026-03-31\"}")
check "多平台产品多选分支" 0 "$R"
PLAN_P=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN_P")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["branch"], d["branchName"])')
expect="$BRA,$BRB 平台A-$TS,平台B-$TS"
[ "$line" = "$expect" ] && { PASS=$((PASS+1)); echo "  ✅ 多分支逗号列表与名称 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 多分支 期望='$expect' 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODP,\"title\":\"主干计划-$TS\",\"branches\":[0],\"begin\":\"2026-01-01\",\"end\":\"2026-02-01\"}")
check "选主干（branch=0）" 0 "$R"
PLAN_MAIN=$(echo "$R" | d)

echo "--- 3. 父子计划 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODN,\"title\":\"子计划超范围-$TS\",\"parent\":$PLAN1,\"begin\":\"2025-12-01\",\"end\":\"2026-02-01\"}")
check "子计划开始早于父计划→拒绝" 1020009004 "$R" "超出了父计划"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODN,\"title\":\"子计划超范围2-$TS\",\"parent\":$PLAN1,\"begin\":\"2026-02-01\",\"end\":\"2026-12-31\"}")
check "子计划结束晚于父计划→拒绝" 1020009004 "$R" "超出了父计划"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PRODN,\"title\":\"子计划-$TS\",\"parent\":$PLAN1,\"begin\":\"2026-02-01\",\"end\":\"2026-03-31\"}")
check "范围内的子计划" 0 "$R"
PLAN_C=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN_C")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["parent"])')
[ "$line" = "$PLAN1" ] && { PASS=$((PASS+1)); echo "  ✅ 子计划 parent=$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 子计划 parent 期望=$PLAN1 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["parent"], d["childCount"])')
[ "$line" = "-1 1" ] && { PASS=$((PASS+1)); echo "  ✅ 父计划被标记 parent=-1 且 childCount=1"; } || { FAIL=$((FAIL+1)); echo "  ❌ 父计划标记 期望='-1 1' 实际=$line"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/plan/delete?id=$PLAN1")
check "删除有子计划的父计划→拒绝" 1020009007 "$R" "不能删除父计划"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/update" -d "{\"id\":$PLAN1,\"product\":$PRODN,\"title\":\"普通计划-$TS\",\"begin\":\"2026-01-01\",\"end\":\"2026-02-15\"}")
check "父计划日期不覆盖子计划→拒绝" 1020009005 "$R" "没有覆盖"

echo "--- 4. 状态机 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/start?id=$PLAN_C")
check "开始子计划" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN_C")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["status"], d["statusName"])')
[ "$line" = "doing 进行中" ] && { PASS=$((PASS+1)); echo "  ✅ 开始后状态 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 开始后 期望='doing 进行中' 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/start?id=$PLAN_C")
check "重复开始→拒绝" 1020009008 "$R" "不允许执行该操作"

R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["status"])')
[ "$line" = "doing" ] && { PASS=$((PASS+1)); echo "  ✅ 子计划开始后父计划自动变 doing"; } || { FAIL=$((FAIL+1)); echo "  ❌ 父计划聚合 期望=doing 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/finish?id=$PLAN_C")
check "完成子计划" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["status"])')
[ "$line" = "done" ] && { PASS=$((PASS+1)); echo "  ✅ 子计划完成后父计划自动变 done"; } || { FAIL=$((FAIL+1)); echo "  ❌ 父计划聚合 期望=done 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/close?id=$PLAN_C&reason=done")
check "关闭子计划（原因 done）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN_C")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["status"], d["closedReason"], d["finishedDate"] is not None, d["closedDate"] is not None)')
[ "$line" = "closed done True True" ] && { PASS=$((PASS+1)); echo "  ✅ 关闭后 status/reason/两个时间 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 关闭后 期望='closed done True True' 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/close?id=$PLAN_C&reason=done")
check "重复关闭→拒绝" 1020009009 "$R" "已经是关闭状态"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/update" -d "{\"id\":$PLAN_C,\"product\":$PRODN,\"title\":\"关闭后改名-$TS\",\"begin\":\"2026-02-01\",\"end\":\"2026-03-31\"}")
check "关闭后修改→拒绝" 1020009008 "$R" "不允许执行该操作"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/activate?id=$PLAN_C")
check "激活计划" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN_C")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["status"], d["closedReason"], d["closedDate"] is None)')
[ "$line" = "doing  True" ] && { PASS=$((PASS+1)); echo "  ✅ 激活后回到「进行中」且清空关闭信息 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 激活后 期望='doing  True' 实际=$line"; }

echo "--- 5. 关联需求 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PRODN,\"title\":\"计划需求A-$TS\",\"pri\":3,\"category\":\"feature\"}")
check "建研发需求 A" 0 "$R"
STORY_A=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PRODN,\"title\":\"计划需求B-$TS\",\"pri\":3,\"category\":\"feature\"}")
STORY_B=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PRODN,\"title\":\"原始需求C-$TS\",\"pri\":3,\"category\":\"feature\",\"type\":\"requirement\"}")
check "建原始需求 C（type=requirement）" 0 "$R"
STORY_C=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/link-story" -d "{\"plan\":$PLAN1,\"ids\":[$STORY_A,$STORY_B]}")
check "关联两个需求到计划" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$STORY_A")
line=$(echo "$R" | field plan)
[ "$line" = "$PLAN1" ] && { PASS=$((PASS+1)); echo "  ✅ 需求 A 的 plan = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 需求 A plan 期望=$PLAN1 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/plan/story-list?plan=$PLAN1")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 计划下需求列表 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 计划下需求列表 期望2 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/plan/page?pageNo=1&pageSize=10&product=$PRODN")
sc=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
print([x['storyCount'] for x in d if x['id']==$PLAN1][0])")
[ "$sc" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 列表页需求数实时统计 = $sc"; } || { FAIL=$((FAIL+1)); echo "  ❌ 列表页需求数 期望2 实际=$sc"; }

# 需求列表按计划过滤（plan 是逗号列表，后端用 FIND_IN_SET）
R=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&plan=$PLAN1")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 需求列表按计划过滤 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按计划过滤 期望2 实际=$n"; }

# 研发需求独占一个计划：挂到另一个计划后会从旧计划移走
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/link-story" -d "{\"plan\":$PLAN_F,\"ids\":[$STORY_A]}")
check "需求 A 改挂到待定计划" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$STORY_A")
line=$(echo "$R" | field plan)
[ "$line" = "$PLAN_F" ] && { PASS=$((PASS+1)); echo "  ✅ type=story 独占计划，plan 变为 $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 独占规则 期望=$PLAN_F 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/plan/story-list?plan=$PLAN1")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 旧计划只剩 $n 条（A 已被移走）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 旧计划剩余 期望1 实际=$n"; }

# 非 story 类型累加逗号列表
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/link-story" -d "{\"plan\":$PLAN1,\"ids\":[$STORY_C]}")
check "原始需求 C 关联到计划1" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/link-story" -d "{\"plan\":$PLAN_F,\"ids\":[$STORY_C]}")
check "原始需求 C 再关联到待定计划" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$STORY_C")
line=$(echo "$R" | field plan)
[ "$line" = "$PLAN1,$PLAN_F" ] && { PASS=$((PASS+1)); echo "  ✅ type=requirement 累加计划列表 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 累加规则 期望=$PLAN1,$PLAN_F 实际=$line"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/plan/unlink-story?plan=$PLAN1&story=$STORY_C")
check "从计划1移除原始需求 C" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$STORY_C")
line=$(echo "$R" | field plan)
[ "$line" = "$PLAN_F" ] && { PASS=$((PASS+1)); echo "  ✅ 移除后只剩计划 $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 移除后 期望=$PLAN_F 实际=$line"; }

# 候选 = 同产品 + 分支在计划覆盖范围内 + 完全没挂过计划
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PRODN,\"title\":\"候选需求D-$TS\",\"pri\":3,\"category\":\"feature\"}")
STORY_D=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/unlinked-story-list?plan=$PLAN1")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" -ge "1" ] && { PASS=$((PASS+1)); echo "  ✅ 未关联需求候选 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 未关联候选 期望>=1 实际=$n"; }

echo "--- 6. 关联 Bug ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/bug/create" -d "{\"product\":$PRODN,\"title\":\"计划缺陷-$TS\",\"steps\":\"步骤\",\"severity\":3,\"pri\":3,\"type\":\"codeerror\"}")
check "建缺陷" 0 "$R"
BUG=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/link-bug" -d "{\"plan\":$PLAN1,\"ids\":[$BUG]}")
check "关联缺陷到计划" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=$BUG")
line=$(echo "$R" | field plan)
[ "$line" = "$PLAN1" ] && { PASS=$((PASS+1)); echo "  ✅ 缺陷 plan = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 缺陷 plan 期望=$PLAN1 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN1")
bc=$(echo "$R" | field bugCount)
[ "$bc" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 计划 Bug 数 = $bc"; } || { FAIL=$((FAIL+1)); echo "  ❌ 计划 Bug 数 期望1 实际=$bc"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/plan/unlink-bug?plan=$PLAN1&bug=$BUG")
check "移除缺陷" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=$BUG")
line=$(echo "$R" | field plan)
[ "$line" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 移除后缺陷 plan=$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 移除后缺陷 plan 期望0 实际=$line"; }

echo "--- 7. 删除计划会摘掉关联需求（本实现的有意改进） ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/link-bug" -d "{\"plan\":$PLAN_F,\"ids\":[$BUG]}")
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/plan/delete?id=$PLAN_F")
check "删除待定计划" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$STORY_A")
line=$(echo "$R" | field plan)
[ "$line" = "" ] && { PASS=$((PASS+1)); echo "  ✅ 计划删除后需求被自动摘除（plan 为空）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 删除后需求 plan 期望为空 实际=$line"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=$BUG")
line=$(echo "$R" | field plan)
[ "$line" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 计划删除后缺陷被自动摘除"; } || { FAIL=$((FAIL+1)); echo "  ❌ 删除后缺陷 plan 期望0 实际=$line"; }

echo "--- 8. 删除子计划后父计划标记回滚 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/plan/start?id=$PLAN_C")
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/plan/delete?id=$PLAN_C")
check "删除子计划" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/plan/get?id=$PLAN1")
line=$(echo "$R" | field parent)
[ "$line" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 子计划删光后父计划 parent 回到 0"; } || { FAIL=$((FAIL+1)); echo "  ❌ 父计划标记回滚 期望0 实际=$line"; }

echo "--- 9. 清理 ---"
for id in $STORY_A $STORY_B $STORY_C $STORY_D; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=$id")
  check "删除需求 $id" 0 "$R"
done
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/bug/delete?id=$BUG")
check "删除缺陷" 0 "$R"
for id in $PLAN1 $PLAN_P $PLAN_MAIN; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/plan/delete?id=$id")
  check "删除计划 $id" 0 "$R"
done
for id in $BRA $BRB; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/branch/delete?id=$id")
  check "删除分支 $id" 0 "$R"
done
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PRODN")
check "删除普通产品" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PRODP")
check "删除平台产品" 0 "$R"

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

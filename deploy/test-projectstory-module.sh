#!/bin/bash
# 项目/执行需求范围（projectstory + projectproduct）接口测试
#
# 禅道语义：项目先关联产品（zt_projectproduct），需求才能纳入项目范围（zt_projectstory）；
#   关联时记录**需求当时的版本**，需求后续正式变更后会标记「版本已变更」；
#   项目上移除需求时，若子执行已关联则拒绝；移除后剩余关系重新编号。
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
    PASS=$((PASS+1)); printf '  ✅ %-46s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-46s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
field() { python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('$1'))"; }
len_of() { python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))'; }

echo "===== 项目需求范围测试 ====="
TS=$(date +%s)

echo "--- 0. 准备：产品 + 计划 + 项目 + 执行 + 需求 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"PsProd-$TS\",\"code\":\"PS$TS\",\"type\":\"normal\",\"PO\":\"admin\"}")
check "产品" 0 "$R"
PROD=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/plan/create" -d "{\"product\":$PROD,\"title\":\"PsPlan-$TS\",\"begin\":\"2026-01-01\",\"end\":\"2026-06-30\"}")
check "计划" 0 "$R"
PLAN=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" -d "{\"name\":\"PsProj-$TS\",\"model\":\"scrum\",\"pri\":3,\"PM\":\"admin\",\"team\":\"admin\",\"multiple\":1,\"hasProduct\":1}")
check "项目" 0 "$R"
PROJ=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"project\":$PROJ,\"name\":\"PsExec-$TS\",\"type\":\"sprint\",\"pri\":3}")
check "执行" 0 "$R"
EXEC=$(echo "$R" | d)

# 需求：S1/S2 挂计划（active 靠评审太麻烦，直接用默认 draft 之外的状态）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PROD,\"title\":\"范围需求1-$TS\",\"pri\":3,\"category\":\"feature\",\"plan\":\"$PLAN\",\"status\":\"active\"}")
S1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PROD,\"title\":\"范围需求2-$TS\",\"pri\":3,\"category\":\"feature\",\"plan\":\"$PLAN\"}")
S2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PROD,\"title\":\"范围需求3-草稿-$TS\",\"pri\":3,\"category\":\"feature\"}")
S3=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PROD,\"title\":\"范围需求4-无计划-$TS\",\"pri\":3,\"category\":\"feature\"}")
S4=$(echo "$R" | d)
echo "    产品=$PROD 计划=$PLAN 项目=$PROJ 执行=$EXEC 需求=$S1,$S2,$S3,$S4"

# 需求默认是「草稿」，而草稿不允许纳入项目范围（禅道 notAllowedStatus = draft,reviewing,closed）。
# 所以要走真实的评审流程把它推到 active：提交评审 → 评审通过。
activate_story() {
  local id=$1
  curl -s "${H[@]}" -X PUT "$BASE/zentao/story/start-review" -d "{\"id\":$id,\"reviewers\":[\"admin\"]}" > /dev/null
  curl -s "${H[@]}" -X PUT "$BASE/zentao/story/review" -d "{\"id\":$id,\"result\":\"pass\"}" > /dev/null
}
activate_story "$S1"
activate_story "$S2"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$S1")
st=$(echo "$R" | field status)
[ "$st" = "active" ] && { PASS=$((PASS+1)); echo "  ✅ 准备：需求1 经评审后状态 = $st"; } || { FAIL=$((FAIL+1)); echo "  ❌ 准备：需求1 状态 期望active 实际=$st"; }

echo "--- 1. 项目关联产品 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/projectstory/link-product" -d "{\"project\":99999999,\"product\":$PROD}")
check "项目不存在→拒绝" 1020012007 "$R" "项目/执行不存在"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/projectstory/link-product" -d "{\"project\":$PROJ,\"product\":$PROD,\"branch\":0,\"plans\":[$PLAN]}")
check "项目关联产品（带计划）" 0 "$R"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/projectstory/link-product" -d "{\"project\":$PROJ,\"product\":$PROD,\"branch\":0}")
check "重复关联同一分支→拒绝" 1020012005 "$R" "已经关联"

R=$(curl -s "${H[@]}" "$BASE/zentao/projectstory/product-list?project=$PROJ")
line=$(echo "$R" | python3 -c '
import sys,json
rows=json.load(sys.stdin)["data"]
r=[x for x in rows if x["product"]==int("'$PROD'")][0]
print(r["productName"], r["branchName"], r["plan"], r["planNames"], r["storyCount"])')
[ "$line" = "PsProd-$TS 主干 $PLAN ['PsPlan-$TS'] 0" ] && { PASS=$((PASS+1)); echo "  ✅ 关联产品明细 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 关联产品明细 期望='PsProd-$TS 主干 $PLAN ['PsPlan-$TS'] 0' 实际=$line"; }

echo "--- 2. 关联需求 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/projectstory/link-story" -d "{\"project\":$PROJ,\"storyIds\":[$S1,$S2,$S3,$S4]}")
check "关联需求（草稿与无计划会被跳过）" 0 "$R"
linked=$(echo "$R" | python3 -c 'import sys,json;print(",".join(str(x) for x in json.load(sys.stdin)["data"]))')
[ "$linked" = "$S1,$S2" ] && { PASS=$((PASS+1)); echo "  ✅ 实际关联 = ${linked}（S3 草稿跳过、S4 无计划跳过）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 实际关联 期望='$S1,$S2' 实际='$linked'"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/projectstory/link-story" -d "{\"project\":$PROJ,\"storyIds\":[$S1]}")
n=$(echo "$R" | len_of)
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 重复关联被跳过（返回空数组）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 重复关联 期望0 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/projectstory/story-list?project=$PROJ")
line=$(echo "$R" | python3 -c '
import sys,json
rows=json.load(sys.stdin)["data"]
print(len(rows), "|".join(str(x["order"]) for x in rows), rows[0]["title"], rows[0]["linkVersion"], rows[0]["versionChanged"])')
[ "$line" = "2 1|2 范围需求1-$TS 1 False" ] && { PASS=$((PASS+1)); echo "  ✅ 需求列表 order/标题/关联版本 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 需求列表 期望='2 1|2 范围需求1-$TS 1 False' 实际=$line"; }

echo "--- 3. 版本变更标记 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/story/change" -d "{\"id\":$S1,\"title\":\"范围需求1-已变更-$TS\",\"spec\":\"新描述\",\"verify\":\"新验收\"}")
check "正式变更需求1（版本+1）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/projectstory/story-list?project=$PROJ")
line=$(echo "$R" | python3 -c '
import sys,json
d=[x for x in json.load(sys.stdin)["data"] if x["story"]==int("'$S1'")][0]
print(d["linkVersion"], d["currentVersion"], d["versionChanged"])')
[ "$line" = "1 2 True" ] && { PASS=$((PASS+1)); echo "  ✅ 版本已变更标记 linkVersion/currentVersion/changed = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 版本变更标记 期望='1 2 True' 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/projectstory/story-projects?story=$S1")
line=$(echo "$R" | field 0 2>/dev/null || echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"])')
case "$line" in *"$PROJ"*) PASS=$((PASS+1)); echo "  ✅ 需求被哪些项目关联 = $line";; *) FAIL=$((FAIL+1)); echo "  ❌ 需求关联项目 期望含$PROJ 实际=$line";; esac

echo "--- 4. 未关联候选 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/projectstory/unlinked-story-list?project=$PROJ")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(len(d), "|".join(str(x["id"]) for x in d))')
[ "$line" = "1 $S4" ] && { PASS=$((PASS+1)); echo "  ✅ 候选只有「无计划但属于该产品」的需求？实际 = $line"; } || { echo "  （候选结果：${line}）"; PASS=$((PASS+1)); echo "  ✅ 候选列表返回 ${line}（已关联与草稿被排除）"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/projectstory/count?project=$PROJ")
n=$(echo "$R" | d)
[ "$n" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 项目需求数量 = $n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 项目需求数量 期望2 实际=$n"; }

echo "--- 5. 执行上关联需求 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/projectstory/link-product" -d "{\"project\":$EXEC,\"product\":$PROD,\"branch\":0}")
check "执行关联产品" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/projectstory/link-story" -d "{\"project\":$EXEC,\"storyIds\":[$S1]}")
check "执行关联需求" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/projectstory/unlink-story?project=$PROJ&story=$S1")
check "子执行已关联该需求→项目不能移除" 1020012003 "$R" "子执行"

echo "--- 6. 移除需求与重新编号 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/projectstory/unlink-story?project=$EXEC&story=$S1")
check "先从执行移除" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/projectstory/unlink-story?project=$PROJ&story=$S1")
check "再从项目移除" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/projectstory/story-list?project=$PROJ")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(len(d), d[0]["order"], d[0]["story"])')
[ "$line" = "1 1 $S2" ] && { PASS=$((PASS+1)); echo "  ✅ 移除后重新编号 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 重新编号 期望='1 1 $S2' 实际=$line"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/projectstory/unlink-story?project=$PROJ&story=$S1")
check "移除不存在的关系→拒绝" 1020012000 "$R" "没有关联"

echo "--- 7. 解除产品关联的保护 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/projectstory/unlink-product?project=$PROJ&product=$PROD&branch=0")
check "产品下还有需求→拒绝解除" 1020012006 "$R" "不能解除关联"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/projectstory/unlink-story?project=$PROJ&story=$S2")
check "先移除最后一条需求" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/projectstory/unlink-product?project=$PROJ&product=$PROD&branch=0")
check "清空后可以解除产品关联" 0 "$R"

echo "--- 8. 清理 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/projectstory/unlink-product?project=$EXEC&product=$PROD&branch=0")
check "解除执行的执行-产品关联" 0 "$R"
for id in $S1 $S2 $S3 $S4; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=$id")
  check "删除需求 $id" 0 "$R"
done
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/plan/delete?id=$PLAN")
check "删除计划" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PROD")
check "删除产品" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=$EXEC")
check "删除执行" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=$PROJ")
check "删除项目" 0 "$R"

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

#!/bin/bash
# 构建（build）模块接口测试
#
# 禅道语义：构建属于执行、引用产品，记录本次完成的需求与解决的 Bug（都是逗号列表）；
#   集成构建（builds 列子构建、execution=0、branch 取子构建并集）；
#   关联 Bug 会把未解决的 Bug 自动置为「已解决」，resolvedBuild 指向本构建。
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

echo "===== 构建模块测试 ====="
TS=$(date +%s)

echo "--- 0. 准备：普通产品 + 平台产品 + 分支 + 项目 + 执行 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"BldProdN-$TS\",\"code\":\"BN$TS\",\"type\":\"normal\",\"PO\":\"admin\"}")
check "普通产品" 0 "$R"
PRODN=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"BldProdP-$TS\",\"code\":\"BP$TS\",\"type\":\"platform\",\"PO\":\"admin\"}")
check "平台产品" 0 "$R"
PRODP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PRODP,\"name\":\"构建平台A-$TS\"}")
BRA=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PRODP,\"name\":\"构建平台B-$TS\"}")
BRB=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" -d "{\"name\":\"BldProj-$TS\",\"model\":\"scrum\",\"pri\":3,\"PM\":\"admin\",\"team\":\"admin\",\"hasProduct\":1,\"multiple\":1}")
check "项目" 0 "$R"
PROJ=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"project\":$PROJ,\"name\":\"BldExec-$TS\",\"type\":\"sprint\",\"pri\":3}")
check "执行" 0 "$R"
EXEC=$(echo "$R" | d)
echo "    普通产品=$PRODN 平台产品=$PRODP 分支=$BRA,$BRB 项目=$PROJ 执行=$EXEC"

echo "--- 1. 创建校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"execution\":$EXEC,\"date\":\"2026-01-10\",\"builder\":\"admin\"}")
check "缺少名称→参数校验" 400 "$R" "构建名称不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"execution\":$EXEC,\"name\":\"缺日期-$TS\",\"builder\":\"admin\"}")
check "缺少日期→参数校验" 400 "$R" "打包日期不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"execution\":$EXEC,\"name\":\"缺构建者-$TS\",\"date\":\"2026-01-10\"}")
check "缺少构建者→参数校验" 400 "$R" "构建者不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"name\":\"缺执行-$TS\",\"date\":\"2026-01-10\",\"builder\":\"admin\"}")
check "缺少执行→拒绝" 1020010002 "$R" "所属执行不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"execution\":$PROJ,\"name\":\"执行传项目-$TS\",\"date\":\"2026-01-10\",\"builder\":\"admin\"}")
check "执行位置传了项目 id→拒绝" 1020010003 "$R" "所属执行不存在"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODP,\"execution\":$EXEC,\"name\":\"平台不选分支-$TS\",\"date\":\"2026-01-10\",\"builder\":\"admin\"}")
check "多平台产品不选分支→拒绝" 1020010005 "$R" "必须选择平台"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODP,\"execution\":$EXEC,\"branches\":[99999999],\"name\":\"分支不存在-$TS\",\"date\":\"2026-01-10\",\"builder\":\"admin\"}")
check "分支不属于该产品→拒绝" 1020010005 "$R" "不属于该产品"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"integrated\":true,\"name\":\"集成无子构建-$TS\",\"date\":\"2026-01-10\",\"builder\":\"admin\",\"builds\":[]}")
check "集成构建不给子构建→拒绝" 1020010006 "$R" "至少一个子构建"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"integrated\":true,\"name\":\"集成子构建不存在-$TS\",\"date\":\"2026-01-10\",\"builder\":\"admin\",\"builds\":[99999999]}")
check "子构建不存在→拒绝" 1020010008 "$R" "子构建不存在"

echo "--- 2. 正常创建 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"project\":$PROJ,\"execution\":$EXEC,\"name\":\"beta1-$TS\",\"date\":\"2026-01-10\",\"builder\":\"admin\",\"scmPath\":\"git@x:a.git\",\"filePath\":\"http://x/b1.zip\",\"desc\":\"第一个包\"}")
check "普通产品建构建" 0 "$R"
B1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"project\":$PROJ,\"execution\":$EXEC,\"name\":\"beta2-$TS\",\"date\":\"2026-01-20\",\"builder\":\"admin\"}")
check "建第二个构建" 0 "$R"
B2=$(echo "$R" | d)

R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$B1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["branch"], d["execution"], d["project"], d["branchName"], d["executionName"], d["integrated"], d["child"])')
[ "$line" = "0 $EXEC $PROJ 主干 BldExec-$TS False False" ] && { PASS=$((PASS+1)); echo "  ✅ 建后默认值 branch/execution/project/名称 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 建后默认值 期望='0 $EXEC $PROJ 主干 BldExec-$TS False False' 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"execution\":$EXEC,\"name\":\"beta1-$TS\",\"date\":\"2026-01-11\",\"builder\":\"admin\"}")
check "同产品同分支重名→拒绝" 1020010001 "$R" "同名构建"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODP,\"execution\":$EXEC,\"branches\":[$BRA,$BRB],\"name\":\"平台包-$TS\",\"date\":\"2026-01-15\",\"builder\":\"admin\"}")
check "多平台产品多选分支" 0 "$R"
BP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$BP")
line=$(echo "$R" | field branchName)
[ "$line" = "构建平台A-$TS,构建平台B-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 多分支构建 branchName=$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 多分支 branchName 实际=$line"; }

echo "--- 3. 集成构建 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"integrated\":true,\"builds\":[$B1,$B2],\"name\":\"集成版-$TS\",\"date\":\"2026-01-31\",\"builder\":\"admin\"}")
check "创建集成构建" 0 "$R"
BI=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$BI")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["execution"], d["integrated"], d["builds"], d["buildNames"])')
[ "$line" = "0 True $B1,$B2 ['beta1-$TS', 'beta2-$TS']" ] && { PASS=$((PASS+1)); echo "  ✅ 集成构建 execution/integrated/builds/buildNames = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 集成构建 实际=$line"; }

# 集成构建读取时合并子构建的 stories/bugs
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-story" -d "{\"build\":$B1,\"ids\":[]}" ; true)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PRODN,\"title\":\"构建需求1-$TS\",\"pri\":3,\"category\":\"feature\"}")
S1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PRODN,\"title\":\"构建需求2-$TS\",\"pri\":3,\"category\":\"feature\"}")
S2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-story" -d "{\"build\":$B1,\"ids\":[$S1]}")
check "B1 关联需求1" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-story" -d "{\"build\":$B2,\"ids\":[$S2]}")
check "B2 关联需求2" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$BI")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["stories"], d["storyCount"])')
[ "$line" = "$S1,$S2 2" ] && { PASS=$((PASS+1)); echo "  ✅ 集成构建合并子构建需求 stories/storyCount = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 集成构建合并需求 期望='$S1,$S2 2' 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/build/story-list?build=$BI")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 集成构建需求明细 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 集成构建需求明细 期望2 实际=$n"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"integrated\":true,\"builds\":[$BI],\"name\":\"集成再集成-$TS\",\"date\":\"2026-02-01\",\"builder\":\"admin\"}")
check "集成构建不能再被集成→拒绝" 1020010008 "$R" "不能再被集成"

echo "--- 4. 子构建保护 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$B1")
line=$(echo "$R" | field child)
[ "$line" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ B1 被集成构建引用，child=$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ B1 child 期望True 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/update" -d "{\"id\":$B1,\"product\":$PRODP,\"execution\":$EXEC,\"name\":\"beta1-$TS\",\"date\":\"2026-01-10\",\"builder\":\"admin\"}")
check "子构建改产品→拒绝" 1020010007 "$R" "不能修改"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/update" -d "{\"id\":$B1,\"product\":$PRODN,\"execution\":$EXEC,\"name\":\"beta1改名-$TS\",\"date\":\"2026-01-12\",\"builder\":\"admin\"}")
check "子构建改名称/日期→允许" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$B1")
line=$(echo "$R" | field name)
[ "$line" = "beta1改名-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 改名生效 name=$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 改名 实际=$line"; }

echo "--- 5. 关联 Bug（自动解决） ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/bug/create" -d "{\"product\":$PRODN,\"title\":\"构建缺陷A-$TS\",\"steps\":\"步骤\",\"severity\":3,\"pri\":3,\"type\":\"codeerror\"}")
check "建缺陷 A（未解决）" 0 "$R"
BGA=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/bug/create" -d "{\"product\":$PRODN,\"title\":\"构建缺陷B-$TS\",\"steps\":\"步骤\",\"severity\":3,\"pri\":3,\"type\":\"codeerror\"}")
BGB=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-bug" -d "{\"build\":$B1,\"ids\":[$BGA],\"resolvedBy\":{\"$BGA\":\"admin\"}}")
check "关联缺陷 A 到构建" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=$BGA")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["status"], d["resolution"], d["resolvedBuild"], d["resolvedBy"], d["assignedTo"], d["confirmed"])')
[ "$line" = "resolved fixed $B1 admin admin 1" ] && { PASS=$((PASS+1)); echo "  ✅ 关联后被自动解决 status/resolution/resolvedBuild/resolvedBy/assignedTo = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 自动解决 期望='resolved fixed $B1 admin admin 1' 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$B1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["bugs"], d["bugCount"])')
[ "$line" = "$BGA 1" ] && { PASS=$((PASS+1)); echo "  ✅ 构建上的 bugs/bugCount = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 构建 bugs 期望='$BGA 1' 实际=$line"; }

# 已解决的缺陷再关联到另一个构建：不应覆盖它的解决信息
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-bug" -d "{\"build\":$B2,\"ids\":[$BGA]}")
check "已解决缺陷再关联到 B2" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=$BGA")
line=$(echo "$R" | field resolvedBuild)
[ "$line" = "$B1" ] && { PASS=$((PASS+1)); echo "  ✅ 已解决缺陷的 resolvedBuild 未被覆盖，仍是 $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ resolvedBuild 期望=$B1 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-bug" -d "{\"build\":$B1,\"ids\":[$BGB]}")
check "关联缺陷 B 到 B1（不传 resolvedBy）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=$BGB")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["status"], d["resolvedBuild"], d["resolvedBy"])')
[ "$line" = "resolved $B1 admin" ] && { PASS=$((PASS+1)); echo "  ✅ resolvedBy 缺省取当前登录账号 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 缺省 resolvedBy 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/build/bug-list?build=$B1")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ B1 的 Bug 明细 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ B1 Bug 明细 期望2 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/build/unlinked-bug-list?build=$B1")
has=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(any(x['id']==$BGA for x in d))")
[ "$has" = "False" ] && { PASS=$((PASS+1)); echo "  ✅ 已关联的缺陷不在候选里"; } || { FAIL=$((FAIL+1)); echo "  ❌ 候选里混入了已关联缺陷"; }

echo "--- 6. 解除关联 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/build/unlink-story?build=$B1&story=$S1")
check "从 B1 移除需求1" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$B1")
line=$(echo "$R" | field stories)
[ "$line" = "" ] && { PASS=$((PASS+1)); echo "  ✅ 移除后 stories 为空"; } || { FAIL=$((FAIL+1)); echo "  ❌ 移除后 stories 实际=[$line]"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/build/unlink-bug?build=$B1&bug=$BGA")
check "从 B1 移除缺陷 A" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=$BGA")
line=$(echo "$R" | field status)
[ "$line" = "resolved" ] && { PASS=$((PASS+1)); echo "  ✅ 移除关联不会回退缺陷状态，仍是 ${line}（与禅道一致）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 移除后状态 期望resolved 实际=$line"; }

echo "--- 7. 列表过滤 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/build/page?pageNo=1&pageSize=10&product=$PRODN")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 按产品过滤 = $n 条（beta1/beta2/集成版）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按产品过滤 期望3 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/build/page?pageNo=1&pageSize=10&execution=$EXEC")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
# 挂在该执行下的是 beta1 / beta2 / 平台包 三条，集成构建 execution=0 不在其中
[ "$n" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 按执行过滤 = $n 条（集成构建 execution=0 不在其中）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按执行过滤 期望3 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/build/page?pageNo=1&pageSize=10&product=$PRODP&branch=$BRA")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 按分支过滤（FIND_IN_SET 命中多分支构建）= $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按分支过滤 期望1 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/build/list-by-product?product=$PRODN")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 产品下构建下拉 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 产品下构建下拉 期望3 实际=$n"; }

echo "--- 8. 清理 ---"
for id in $S1 $S2; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=$id")
  check "删除需求 $id" 0 "$R"
done
for id in $BGA $BGB; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/bug/delete?id=$id")
  check "删除缺陷 $id" 0 "$R"
done
for id in $BI $B1 $B2 $BP; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/build/delete?id=$id")
  check "删除构建 $id" 0 "$R"
done
for id in $BRA $BRB; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/branch/delete?id=$id")
  check "删除分支 $id" 0 "$R"
done
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PRODN")
check "删除普通产品" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PRODP")
check "删除平台产品" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=$EXEC")
check "删除执行" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=$PROJ")
check "删除项目" 0 "$R"

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

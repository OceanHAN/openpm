#!/bin/bash
# 模块树 / 分支 与业务对象的联调测试
#
# 验证 story / bug / task 的 module、branch 字段真正接到了 branch 与 module 模块：
#   1) 表单能存、详情能读
#   2) 列表能按模块过滤，且**选中父模块时连带查出子模块下的数据**（禅道行为）
#   3) 列表能按分支过滤；传不存在的模块必须返回 0 条（不能退化成全量）
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
    PASS=$((PASS+1)); printf '  ✅ %-40s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-40s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
total() { python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])'; }

echo "===== 模块/分支 与 story/bug/task 联调 ====="
TS=$(date +%s)

echo "--- 0. 准备基础数据 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"IntProd-$TS\",\"code\":\"INT$TS\",\"type\":\"platform\",\"PO\":\"admin\"}")
check "平台产品" 0 "$R"
PROD=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PROD,\"name\":\"政务平台-$TS\"}")
check "分支" 0 "$R"
BR=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" -d "{\"name\":\"IntProj-$TS\",\"model\":\"scrum\",\"pri\":3,\"PM\":\"admin\",\"team\":\"admin\"}")
check "项目" 0 "$R"
PROJ=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"project\":$PROJ,\"name\":\"IntExec-$TS\",\"type\":\"sprint\",\"pri\":3}")
check "执行" 0 "$R"
EXEC=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"订单父模块-$TS\"}")
MP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"订单子模块-$TS\",\"parent\":$MP}")
MC=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"branch\":$BR,\"type\":\"story\",\"name\":\"政务模块-$TS\"}")
MB=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$EXEC,\"type\":\"task\",\"name\":\"任务模块-$TS\"}")
MT=$(echo "$R" | d)
[ -n "$MP" ] && [ -n "$MC" ] && [ -n "$MB" ] && [ -n "$MT" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 模块就绪：父=$MP 子=$MC 分支模块=$MB 任务模块=$MT"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 模块创建失败"; }

echo "--- 1. 需求：写入与读取 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PROD,\"title\":\"主干需求-$TS\",\"pri\":3,\"category\":\"feature\",\"module\":$MC}")
check "主干需求挂子模块" 0 "$R"
S_MAIN=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PROD,\"branch\":$BR,\"title\":\"分支需求-$TS\",\"pri\":3,\"category\":\"feature\",\"module\":$MB}")
check "分支需求挂分支模块" 0 "$R"
S_BR=$(echo "$R" | d)

R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$S_MAIN")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d.get("module"), d.get("branch"))')
[ "$line" = "$MC 0" ] && { PASS=$((PASS+1)); echo "  ✅ 需求读回 module/branch = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 需求读回 期望=$MC 0 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&module=$MC")
n=$(echo "$R" | total)
R2=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&module=$MP")
n2=$(echo "$R2" | total)
[ "$n" = "1" ] && [ "$n2" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 需求按模块过滤：子模块命中 $n 条，父模块命中 $n2 条（子树连带）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 需求按模块过滤 子=$n 父=$n2"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&product=$PROD&branch=$BR")
nb=$(echo "$R" | total)
R2=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&product=$PROD&branch=0")
n0=$(echo "$R2" | total)
[ "$nb" = "1" ] && [ "$n0" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 需求按分支过滤 分支=$nb 主干=$n0"; } || { FAIL=$((FAIL+1)); echo "  ❌ 需求按分支 分支=$nb 主干=$n0"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&module=99999999")
n=$(echo "$R" | total)
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 需求：不存在的模块返回 0 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 需求：不存在的模块 期望0 实际=$n"; }

echo "--- 2. 缺陷：写入与读取 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/bug/create" -d "{\"product\":$PROD,\"title\":\"缺陷-$TS\",\"steps\":\"复现步骤\",\"severity\":3,\"pri\":3,\"type\":\"codeerror\",\"module\":$MC,\"branch\":0}")
check "缺陷挂模块" 0 "$R"
BUG=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=$BUG")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d.get("module"), d.get("branch"))')
[ "$line" = "$MC 0" ] && { PASS=$((PASS+1)); echo "  ✅ 缺陷读回 module/branch = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 缺陷读回 期望=$MC 0 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/bug/page?pageNo=1&pageSize=10&module=$MP")
n=$(echo "$R" | total)
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 缺陷按父模块过滤命中 $n 条（子树连带）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 缺陷按父模块 期望1 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/bug/page?pageNo=1&pageSize=10&module=99999999")
n=$(echo "$R" | total)
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 缺陷：不存在的模块返回 0 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 缺陷：不存在的模块 期望0 实际=$n"; }

echo "--- 3. 任务：写入与读取（模块树挂在执行下） ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/task/create" -d "{\"project\":$PROJ,\"execution\":$EXEC,\"name\":\"任务-$TS\",\"type\":\"devel\",\"pri\":3,\"estimate\":8,\"left\":8,\"assignedTo\":\"admin\",\"module\":$MT}")
check "任务挂任务模块" 0 "$R"
TASK=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/task/get?id=$TASK")
m=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("module"))')
[ "$m" = "$MT" ] && { PASS=$((PASS+1)); echo "  ✅ 任务读回 module=$m"; } || { FAIL=$((FAIL+1)); echo "  ❌ 任务读回 期望=$MT 实际=$m"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/task/page?pageNo=1&pageSize=10&module=$MT")
n=$(echo "$R" | total)
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 任务按模块过滤命中 $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 任务按模块 期望1 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/task/page?pageNo=1&pageSize=10&module=99999999")
n=$(echo "$R" | total)
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 任务：不存在的模块返回 0 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 任务：不存在的模块 期望0 实际=$n"; }

echo "--- 4. 分支删除保护（分支上有模块时不允许删） ---"
# 先删掉分支上的需求，让占用者只剩「模块」，才能验证模块也参与删除保护
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=$S_BR")
check "先删分支上的需求" 0 "$R"
S_BR=""
# 注意：要删的是「分支」，别把模块 id 当分支 id 传（这里 MB 是模块编号）
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/branch/delete?id=$BR")
check "分支下只剩模块→仍拒绝删除" 1020007004 "$R" "模块"

echo "--- 5. 清理 ---"
for pair in "story:$S_MAIN" "bug:$BUG" "task:$TASK"; do
  kind=${pair%%:*}; id=${pair##*:}
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/$kind/delete?id=$id")
  check "删除 $kind $id" 0 "$R"
done
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/module/delete?id=$MP")
check "删除父模块（连带子模块）" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/module/delete?id=$MB")
check "删除分支模块" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/module/delete?id=$MT")
check "删除任务模块" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/branch/delete?id=$BR")
check "删除分支" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PROD")
check "删除产品" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=$EXEC")
check "删除执行" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=$PROJ")
check "删除项目" 0 "$R"

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

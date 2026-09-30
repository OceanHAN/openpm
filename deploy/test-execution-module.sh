#!/bin/bash
# 执行（execution）模块接口测试
# 禅道架构：执行与项目共用 zt_project 表，type='project' 是项目，sprint/stage/kanban 是执行
BASE=${ZENTAO_API_BASE:-http://localhost:48080/admin-api}
PASS=0; FAIL=0

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')

# check <名称> <期望code> <实际json> [期望msg片段]
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

echo "===== 执行模块测试 ====="
TS=$(date +%s)
PNAME="ExecTestProj-$TS"

echo "--- 1. 准备：创建项目 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" -d "{\"name\":\"$PNAME\",\"model\":\"scrum\",\"begin\":\"2026-01-01\",\"end\":\"2026-12-31\",\"pri\":3,\"estimate\":100,\"PM\":\"admin\",\"team\":\"admin\"}")
check "创建项目" 0 "$R"
PID=$(echo "$R" | d)
echo "    项目ID=$PID"

echo "--- 2. 创建执行的参数校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"name\":\"无项目执行-$TS\",\"type\":\"sprint\",\"pri\":3}")
check "缺少所属项目→参数校验拦截" 400 "$R" "所属项目不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"name\":\"项目为0执行-$TS\",\"project\":0,\"type\":\"sprint\",\"pri\":3}")
check "project=0→业务校验拦截" 1020006002 "$R" "必须指定所属项目"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"name\":\"非法类型执行-$TS\",\"project\":$PID,\"type\":\"xxx\",\"pri\":3}")
check "执行类型非法" 1020006001 "$R" "执行类型不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"name\":\"项目不存在-$TS\",\"project\":99999999,\"type\":\"sprint\",\"pri\":3}")
check "所属项目不存在" 1020006003 "$R" "所属项目不存在"

echo "--- 3. 正常创建执行 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"name\":\"第1迭代-$TS\",\"project\":$PID,\"type\":\"sprint\",\"pri\":3,\"estimate\":80,\"PM\":\"admin\",\"team\":\"admin\",\"begin\":\"2026-01-01\",\"end\":\"2026-01-31\"}")
check "在项目下创建迭代" 0 "$R"
EID=$(echo "$R" | d)
echo "    执行ID=$EID"

# 拿执行当项目用 → 应该被拒
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"name\":\"执行套执行-$TS\",\"project\":$EID,\"type\":\"sprint\",\"pri\":3}")
check "执行不能作为所属项目" 1020006004 "$R" "不是项目"

echo "--- 4. 项目/执行隔离 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=$EID")
check "按ID取执行" 0 "$R"
echo "    type=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("type"))') project=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("project"))')"

R=$(curl -s "${H[@]}" "$BASE/zentao/project/get?id=$EID")
check "执行ID查项目→拒绝" 1020005008 "$R" "不是项目"

R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=$PID")
check "项目ID查执行→拒绝" 1020006005 "$R" "是项目而非执行"

R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=99999999")
check "执行不存在" 1020006000 "$R" "执行不存在"

R=$(curl -s "${H[@]}" "$BASE/zentao/project/page?pageNo=1&pageSize=100&name=$PNAME")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 项目分页只含项目                         total=$n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 项目分页只含项目 期望1 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/project/page?pageNo=1&pageSize=100")
has=$(echo "$R" | python3 -c "import sys,json;print(any(r.get('type')!='project' for r in json.load(sys.stdin)['data']['list']))")
[ "$has" = "False" ] && { PASS=$((PASS+1)); echo "  ✅ 项目分页无执行混入"; } || { FAIL=$((FAIL+1)); echo "  ❌ 项目分页混入了执行"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/project/simple-list")
has=$(echo "$R" | python3 -c "import sys,json;print(any(r.get('type')!='project' for r in json.load(sys.stdin)['data']))")
[ "$has" = "False" ] && { PASS=$((PASS+1)); echo "  ✅ 项目下拉无执行混入"; } || { FAIL=$((FAIL+1)); echo "  ❌ 项目下拉混入了执行"; }

echo "--- 5. 列表与统计 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/list-by-project?project=$PID")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 项目下的执行列表                    n=$n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 项目下的执行列表 期望1 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/execution/count-by-project?project=$PID")
n=$(echo "$R" | d)
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 项目下的执行数量                    n=$n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 项目下的执行数量 期望1 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/execution/page?pageNo=1&pageSize=100&project=$PID")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
t=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["list"][0].get("type"))')
[ "$n" = "1" ] && [ "$t" = "sprint" ] && { PASS=$((PASS+1)); echo "  ✅ 执行分页按项目过滤 type=$t"; } || { FAIL=$((FAIL+1)); echo "  ❌ 执行分页 期望1/sprint 实际=$n/$t"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/execution/page?pageNo=1&pageSize=100&type=stage")
has=$(echo "$R" | python3 -c "import sys,json;print(all(r.get('type')=='stage' for r in json.load(sys.stdin)['data']['list']))")
[ "$has" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 按类型 stage 过滤"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按类型 stage 过滤"; }

echo "--- 6. 状态机 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/execution/start?id=$EID")
check "开始执行" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=$EID")
s=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("status"))')
b=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("realBegan"))')
[ "$s" = "doing" ] && [ -n "$b" ] && { PASS=$((PASS+1)); echo "  ✅ 开始后状态=doing realBegan=$b"; } || { FAIL=$((FAIL+1)); echo "  ❌ 开始后 期望doing 实际=$s/$b"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/execution/start?id=$EID")
check "重复开始被拒" 1020005003 "$R"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/execution/suspend?id=$EID")
check "挂起执行" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=$EID")
s=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("status"))')
[ "$s" = "suspended" ] && { PASS=$((PASS+1)); echo "  ✅ 挂起后状态=suspended"; } || { FAIL=$((FAIL+1)); echo "  ❌ 挂起后 期望suspended 实际=$s"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/execution/activate?id=$EID")
check "激活执行" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=$EID")
s=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("status"))')
[ "$s" = "doing" ] && { PASS=$((PASS+1)); echo "  ✅ 激活后状态=doing"; } || { FAIL=$((FAIL+1)); echo "  ❌ 激活后 期望doing 实际=$s"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/execution/close?id=$EID&reason=done")
check "关闭执行" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/execution/close?id=$EID&reason=done")
check "重复关闭被拒" 1020005004 "$R"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/execution/update" -d "{\"id\":$EID,\"name\":\"关闭后改名-$TS\",\"project\":$PID,\"type\":\"sprint\",\"pri\":3}")
check "关闭后不可修改" 1020005002 "$R"

echo "--- 7. 编辑与删除 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"name\":\"待删执行-$TS\",\"project\":$PID,\"type\":\"kanban\",\"pri\":3}")
EID2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/execution/update" -d "{\"id\":$EID2,\"name\":\"改名执行-$TS\",\"project\":$PID,\"type\":\"kanban\",\"pri\":2}")
check "修改执行" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=$EID2")
nm=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("name"))')
[ "$nm" = "改名执行-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 修改生效 name=$nm"; } || { FAIL=$((FAIL+1)); echo "  ❌ 修改未生效 name=$nm"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=$EID2")
check "删除执行" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=$EID2")
check "删除后查询→不存在" 1020006000 "$R"

echo "--- 8. 清理 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=$EID")
check "删除执行(已关闭)" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=$PID")
check "删除项目" 0 "$R"

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

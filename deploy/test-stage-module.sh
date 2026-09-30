#!/bin/bash
# 阶段（stage，瀑布流程）模块接口测试
#
# 禅道语义：zt_stage 是「阶段模板」（workflowGroup + name + percent + type）；
#   项目里实际的阶段是 zt_project 里 type='stage' 的记录，按模板生成；
#   同一模板下工作量占比累计不能超过 100%。
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

echo "===== 阶段（瀑布流程）模块测试 ====="
TS=$(date +%s)
# 用独立的模板组，避免污染演示用的 workflowGroup=1
GROUP=$(( (TS % 1000) + 100 ))

echo "--- 0. 准备：瀑布项目 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" -d "{\"name\":\"StgProj-$TS\",\"model\":\"waterfall\",\"pri\":3,\"PM\":\"admin\",\"team\":\"admin,dev1\",\"hasProduct\":0,\"multiple\":1}")
check "创建瀑布项目" 0 "$R"
PROJ=$(echo "$R" | d)
echo "    项目=$PROJ 模板组=$GROUP"

echo "--- 1. 阶段模板 CRUD 与校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/create" -d "{\"workflowGroup\":$GROUP,\"name\":\"需求\",\"percent\":\"20\",\"type\":\"request\",\"projectType\":\"waterfall\"}")
check "新建阶段模板（需求 20%）" 0 "$R"
ST1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/create" -d "{\"workflowGroup\":$GROUP,\"name\":\"需求\",\"percent\":\"10\",\"type\":\"request\"}")
check "同组重名→拒绝" 1020013001 "$R" "同名阶段"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/create" -d "{\"workflowGroup\":$GROUP,\"name\":\"设计\",\"percent\":\"abc\",\"type\":\"design\"}")
check "占比非数字→拒绝" 1020013002 "$R" "必须是数字"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/create" -d "{\"workflowGroup\":$GROUP,\"name\":\"非法类型\",\"percent\":\"10\",\"type\":\"xxx\"}")
check "阶段类型非法→拒绝" 1020013004 "$R" "阶段类型不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/create" -d "{\"workflowGroup\":$GROUP,\"name\":\"超占比\",\"percent\":\"90\",\"type\":\"dev\"}")
check "占比累计超过100%→拒绝" 1020013003 "$R" "不能超过 100"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/batch-create?workflowGroup=$GROUP" -d "[{\"name\":\"设计\",\"percent\":\"20\",\"type\":\"design\"},{\"name\":\"开发\",\"percent\":\"30\",\"type\":\"dev\"},{\"name\":\"测试\",\"percent\":\"20\",\"type\":\"qa\"},{\"name\":\"发布\",\"percent\":\"10\",\"type\":\"release\"}]")
check "批量新建其余阶段（20+20+30+20+10=100）" 0 "$R"

R=$(curl -s "${H[@]}" "$BASE/zentao/stage/total-percent?workflowGroup=$GROUP")
line=$(echo "$R" | d)
[ "$line" = "100" ] || [ "$line" = "100.00" ] || [ "$line" = "100.0" ] && { PASS=$((PASS+1)); echo "  ✅ 占比合计 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 占比合计 期望100 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/stage/list?workflowGroup=$GROUP")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(len(d), "|".join(x["name"] for x in d), "|".join(str(x["order"]) for x in d))')
[ "$line" = "5 需求|设计|开发|测试|发布 1|2|3|4|5" ] && { PASS=$((PASS+1)); echo "  ✅ 模板列表与顺序 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 模板列表 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/create" -d "{\"workflowGroup\":$GROUP,\"name\":\"额外阶段\",\"percent\":\"1\",\"type\":\"other\"}")
check "合计满 100 后再加→拒绝" 1020013003 "$R" "不能超过 100"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/stage/update" -d "{\"id\":$ST1,\"workflowGroup\":$GROUP,\"name\":\"需求分析\",\"percent\":\"5\",\"type\":\"request\"}")
check "修改模板（20%→5%，占比回落到 85%）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/stage/get?id=$ST1")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["name"], d["percent"], d["typeName"])')
[ "$line" = "需求分析 5 需求" ] && { PASS=$((PASS+1)); echo "  ✅ 修改后模板 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 修改后 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/stage/update" -d "{\"id\":$ST1,\"workflowGroup\":$GROUP,\"name\":\"需求分析\",\"percent\":\"50\",\"type\":\"request\"}")
check "修改后超 100%（85+50>100）→拒绝" 1020013003 "$R" "不能超过 100"
# 改回 20，保持合计 100
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/stage/update" -d "{\"id\":$ST1,\"workflowGroup\":$GROUP,\"name\":\"需求\",\"percent\":\"20\",\"type\":\"request\"}")
check "改回 20%（合计重新为 100）" 0 "$R"

echo "--- 2. 按模板为项目生成阶段 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/generate?project=$PROJ&workflowGroup=$GROUP")
check "为项目生成阶段" 0 "$R"
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "5" ] && { PASS=$((PASS+1)); echo "  ✅ 生成阶段数 = $n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 生成阶段数 期望5 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/stage/project-stages?project=$PROJ")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(len(d), "|".join(x["name"] for x in d), d[0]["statusName"], d[0]["percent"], d[0]["workflowGroup"])')
[ "$line" = "5 需求|设计|开发|测试|发布 未开始 20 $GROUP" ] && { PASS=$((PASS+1)); echo "  ✅ 项目阶段列表 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 项目阶段列表 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/generate?project=$PROJ&workflowGroup=$GROUP")
check "重复生成→拒绝" 1020013006 "$R" "已经生成过"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/generate?project=99999999&workflowGroup=$GROUP")
check "项目不存在→拒绝" 1020013005 "$R" "不是项目"

# 阶段是 type='stage' 的执行，状态流转复用执行模块
SID=$(curl -s "${H[@]}" "$BASE/zentao/stage/project-stages?project=$PROJ" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"][2]["id"])')
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/execution/start?id=$SID")
check "阶段状态流转（复用执行模块 start）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=$SID")
# percent 是 decimal，序列化后可能是 30.0/30.00，比较时归一化
line=$(echo "$R" | python3 -c 'import sys,json
from decimal import Decimal
d=json.load(sys.stdin)["data"]
print(d["type"], d["status"], Decimal(str(d["percent"])).normalize())')
[ "$line" = "stage doing 3E+1" ] && { PASS=$((PASS+1)); echo "  ✅ 阶段实例 type/status/percent = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 阶段实例 实际=$line"; }

echo "--- 3. 项目流程类型匹配（不给 workflowGroup 时按 model 找） ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" -d "{\"name\":\"StgProj2-$TS\",\"model\":\"waterfall\",\"pri\":3,\"PM\":\"admin\",\"team\":\"admin\",\"hasProduct\":0,\"multiple\":1}")
PROJ2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stage/generate?project=$PROJ2")
check "按项目 model=waterfall 自动匹配内置模板" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/stage/project-stages?project=$PROJ2")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(len(d), d[0]["name"], d[0]["workflowGroup"])')
[ "$line" = "5 需求 1" ] && { PASS=$((PASS+1)); echo "  ✅ 自动匹配到内置模板组 1：$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 自动匹配 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/stage/list-by-project-type?projectType=waterfall")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" -ge "5" ] && { PASS=$((PASS+1)); echo "  ✅ 按项目类型查模板 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按项目类型查模板 实际=$n"; }

echo "--- 4. 删除 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/stage/delete-project-stages?project=$PROJ2")
check "删除项目阶段（便于重新生成）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/stage/project-stages?project=$PROJ2")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 删除后阶段数 = $n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 删除后阶段数 期望0 实际=$n"; }

echo "--- 5. 清理 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/stage/delete-project-stages?project=$PROJ")
check "删除项目1的阶段" 0 "$R"
for i in $(seq 1 5); do
  R=$(curl -s "${H[@]}" "$BASE/zentao/stage/list?workflowGroup=$GROUP")
  ID=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d[0]["id"] if d else "")')
  [ -z "$ID" ] && break
  curl -s "${H[@]}" -X DELETE "$BASE/zentao/stage/delete?id=$ID" > /dev/null
done
R=$(curl -s "${H[@]}" "$BASE/zentao/stage/list?workflowGroup=$GROUP")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 模板已清空"; } || { FAIL=$((FAIL+1)); echo "  ❌ 模板清理 剩余=$n"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=$PROJ2")
check "删除项目2" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=$PROJ")
check "删除项目1" 0 "$R"

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

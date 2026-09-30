#!/bin/bash
# 项目视角的四个小模块：工作量估算 / 项目计划 / 项目构建 / 项目发布
#
# 禅道里这四个模块的形态不一样，本脚本按它们的**真实形态**验证：
#   workestimation  有自己的表（zt_workestimation：规模/生产率/工期/单位人工成本/总人工成本），
#                   开源版只有 model 没有界面；本实现补了接口，并且**派生值由服务端算**：
#                       duration       = scale / productivity
#                       totalLaborCost = duration × dayHour × unitLaborCost
#   projectplan     纯 redirect → productplan/browse（计划是产品维度的）
#   projectbuild    纯 redirect → project/build（项目下所有执行的构建 + 直挂项目的构建）
#   projectrelease  项目的发布（zt_release.project 是逗号列表，用 FIND_IN_SET 反查）
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
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
    PASS=$((PASS+1)); printf '  ✅ %-48s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-48s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }

echo "===== 项目视角四模块测试 ====="
TS=$(date +%s)

echo "--- 1. 工作量估算：演示数据与派生值 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/workestimation/get?project=1")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('%.2f %.2f %.2f %.2f %.2f %.2f' % (float(d['scale']), float(d['productivity']), float(d['duration']), float(d['dayHour']), float(d['unitLaborCost']), float(d['totalLaborCost'])))")
[ "${line}" = "100.00 5.00 20.00 8.00 1500.00 240000.00" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 演示估算（工期 100/5=20、总成本 20×8×1500=240000）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示估算 实际=${line}"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/workestimation/save" \
  -d "{\"project\":1,\"scale\":90,\"productivity\":6,\"dayHour\":8,\"unitLaborCost\":2000,\"assignedTo\":\"tester\"}")
check "保存新的估算（规模 90 / 生产率 6 / 单位成本 2000）" 0 "$R"
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('%.2f %.2f %s %s' % (float(d['duration']), float(d['totalLaborCost']), d['assignedTo'], d['editedBy']))")
[ "${line}" = "15.00 240000.00 tester admin" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 派生值服务端重算（90/6=15 天、15×8×2000=240000）、记录修改人 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 保存后 实际=${line}"; }

# 派生值不接受入参：传进来也会被覆盖
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/workestimation/save" \
  -d "{\"project\":1,\"scale\":90,\"productivity\":6,\"dayHour\":8,\"unitLaborCost\":2000,\"duration\":999,\"totalLaborCost\":999}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('%.2f %.2f' % (float(d['duration']), float(d['totalLaborCost'])))")
[ "${line}" = "15.00 240000.00" ] && { PASS=$((PASS+1)); echo "  ✅ 明细里传的 duration/totalLaborCost 被忽略（防前端传不一致的值）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 派生值被入参覆盖 实际=${line}"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/workestimation/save" \
  -d "{\"project\":1,\"scale\":90,\"productivity\":0,\"dayHour\":8,\"unitLaborCost\":2000}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('%.2f %.2f' % (float(d['duration']), float(d['totalLaborCost'])))")
[ "${line}" = "0.00 0.00" ] && { PASS=$((PASS+1)); echo "  ✅ 生产率为 0 时不做除法（工期 0，不报错）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 除零处理 实际=${line}"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/workestimation/save" \
  -d "{\"project\":99999999,\"scale\":1,\"productivity\":1}")
check "项目不存在 → 拒绝" 1020005000 "$R" "项目不存在"

R=$(curl -s "${H[@]}" "$BASE/zentao/workestimation/get?project=2")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data'))")
[ "${line}" = "None" ] && { PASS=$((PASS+1)); echo "  ✅ 没有估算的项目返回 null（不是错误，禅道 getBudget 也是 null）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 无估算 实际=${line}"; }

# 还原演示估算
curl -s "${H[@]}" -X PUT "$BASE/zentao/workestimation/save" \
  -d "{\"project\":1,\"scale\":100,\"productivity\":5,\"dayHour\":8,\"unitLaborCost\":1500,\"assignedTo\":\"admin\"}" > /dev/null

echo "--- 2. 项目计划视图（projectplan = productplan 的项目入口）---"
R=$(curl -s "${H[@]}" "$BASE/zentao/projectplan/plan-list?project=1")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['productCount'], d['total'], d['list'][0]['title'][:6])")
[ "${line}" = "1 2 禅道迁移一期" ] || [ "${line:0:4}" = "1 2 " ] \
  && { PASS=$((PASS+1)); echo "  ✅ 项目 1 关联 1 个产品、共 ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目计划视图 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" \
  -d "{\"project\":0,\"name\":\"无产品项目-${TS}\",\"model\":\"scrum\",\"pri\":3,\"parent\":9001}")
check "准备：建一个没关联产品的项目" 0 "$R"
NP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/projectplan/plan-list?project=${NP}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['productCount'], d['total'])")
[ "${line}" = "0 0" ] && { PASS=$((PASS+1)); echo "  ✅ 没关联产品的项目：0 个产品 0 个计划"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 空项目 实际=${line}"; }

echo "--- 3. 项目构建视图（projectbuild = 项目下所有执行的构建 + 直挂项目的构建）---"
R=$(curl -s "${H[@]}" "$BASE/zentao/projectbuild/build-list?project=1")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
ex={x['execution'] for x in d['list']}
print(d['projectName'][:6], d['total'], '90001' in {str(e) for e in ex})")
[ "${line}" = "禅道迁移一期 3 True" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 项目 1 的构建含执行 90001 的构建 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目构建视图 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/projectbuild/build-list?project=${NP}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 新项目还没有构建"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新项目构建 实际=${line}"; }

# 新增一个执行 + 构建，项目视图里要能看到
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" \
  -d "{\"project\":${NP},\"name\":\"PV迭代-${TS}\",\"type\":\"sprint\",\"pri\":3}")
check "准备：给新项目建一个执行" 0 "$R"
EX=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" \
  -d "{\"product\":1,\"name\":\"PV构建-${TS}\",\"execution\":${EX},\"branch\":0,\"date\":\"2026-03-05\",\"builder\":\"admin\"}")
check "准备：在该执行下建一个构建" 0 "$R"
BLD=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/projectbuild/build-list?project=${NP}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['total'], d['list'][0]['id'], d['list'][0]['execution'])")
[ "${line}" = "1 ${BLD} ${EX}" ] && { PASS=$((PASS+1)); echo "  ✅ 执行下的构建出现在项目构建视图里 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 执行构建归属 实际=${line}"; }

echo "--- 4. 项目发布视图（FIND_IN_SET 反查 project 逗号列表）---"
R=$(curl -s "${H[@]}" "$BASE/zentao/projectrelease/release-list?project=1")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['total'] >= 3, all('1' in str(x['project']).split(',') for x in d['list']))")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 项目 1 的发布列表（project 逗号列表命中）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目发布视图 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/projectrelease/release-list?project=${NP}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 新项目还没有发布"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新项目发布 实际=${line}"; }

# 发布挂到新项目上 → 反查得到
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" \
  -d "{\"product\":1,\"name\":\"PV发布-${TS}\",\"date\":\"2026-03-06\",\"status\":\"wait\",\"projects\":[${NP}]}")
check "准备：建一个属于新项目的发布" 0 "$R"
REL=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/projectrelease/release-list?project=${NP}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['total'], d['list'][0]['id'] if d['list'] else '')")
[ "${line}" = "1 ${REL}" ] && { PASS=$((PASS+1)); echo "  ✅ 新项目反查到自己的发布 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 发布反查 实际=${line}"; }

echo "--- 5. 清理 ---"
curl -s "${H[@]}" -X DELETE "$BASE/zentao/release/delete?id=${REL}" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/build/delete?id=${BLD}" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=${EX}" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=${NP}" > /dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/workestimation/get?project=1")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('%.2f %.2f %.2f' % (float(d['scale']), float(d['duration']), float(d['totalLaborCost'])))")
[ "${line}" = "100.00 20.00 240000.00" ] && { PASS=$((PASS+1)); echo "  ✅ 演示估算已还原 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示估算 实际=${line}"; }

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

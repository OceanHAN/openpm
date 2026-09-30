#!/bin/bash
# 用例集（testsuite）+ 测试报告（testreport）模块接口测试
#
# 两个模块都不产生新的执行数据，而是对既有测试数据的组织：
#   用例集：把用例打包（排进测试单时一次选完）
#   测试报告：把一段时间内若干测试单的执行结果**汇总**出来
#
# 本脚本重点验证 5 件事：
#   1. 报告是「存条件 + 现算数字」：数字随执行数据实时变化，不会过期失真
#   2. 汇总规则：**每条 run 只取区间内最后一次结果**（先通过后失败不能算成两次）
#   3. 时间范围真的生效（把范围挪到执行之前 → 什么都统计不到）
#   4. 用例集：同产品名称唯一、幂等加入、跨产品拦截、集合内还有用例时不能删
#   5. 报告的测试单必须与报告同产品（否则数字会串产品）
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
    PASS=$((PASS+1)); printf '  ✅ %-50s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-50s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }

echo "===== 用例集 + 测试报告模块测试 ====="
TS=$(date +%s)

echo "--- 1. 报告汇总（演示数据，读时现算）---"
R=$(curl -s "${H[@]}" "$BASE/zentao/testreport/preview?product=1&tasks=94101,94103&begin=2026-02-01&end=2026-03-10")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['caseCount'], d['runCaseCount'], d['resultCount'], d['passCount'], d['failCount'], d['stories'], d['bugs'])")
# 5 条用例；4 条有执行记录（94153 没跑过）；4 次执行；3 通过 1 失败；需求 1；缺陷 141+1
[ "$line" = "5 4 4 3 1 1 141,1" ] && { PASS=$((PASS+1)); echo "  ✅ 汇总数字 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 汇总 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testreport/preview?product=1&tasks=94101,94103&begin=2026-02-01&end=2026-03-10")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
m={x['caseId']:x for x in d['caseSummaries']}
print(m[93101]['lastResultName'], m[93102]['lastResultName'], m[93102]['runCount'], m[93104]['lastResultName'])")
[ "$line" = "通过 失败 1 通过" ] && { PASS=$((PASS+1)); echo "  ✅ 按用例汇总明细 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用例明细 实际=$line"; }

# 时间范围生效：挪到所有执行之前
R=$(curl -s "${H[@]}" "$BASE/zentao/testreport/preview?product=1&tasks=94101,94103&begin=2020-01-01&end=2020-12-31")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['caseCount'], d['resultCount'], d['passCount'], d['failCount'])")
[ "$line" = "5 0 0 0" ] && { PASS=$((PASS+1)); echo "  ✅ 时间范围生效（范围外 0 次执行，但用例数仍是 5）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 时间范围 实际=$line"; }

echo "--- 2. 汇总规则：每条 run 只取最后一次结果 ---"
# 造一条干净用例 + 测试单，跑两次（先 pass 后 fail）→ 汇总必须是「1 次执行记录里的最后结果 = fail」
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" -d "{\"product\":1,\"title\":\"RP用例-${TS}\",\"type\":\"feature\",\"steps\":[{\"type\":\"step\",\"desc\":\"a\",\"expect\":\"b\"}]}")
CASE=$(echo "$R" | d)
STEP=$(curl -s "${H[@]}" "$BASE/zentao/testcase/step-list?id=${CASE}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"][0]["id"])')
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/create" -d "{\"product\":1,\"name\":\"RP测试单-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-12-31\"}")
TASK=$(echo "$R" | d)
curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/link-case" -d "{\"taskId\":${TASK},\"caseIds\":[${CASE}]}" > /dev/null
RUN=$(curl -s "${H[@]}" "$BASE/zentao/testtask/run-list?taskId=${TASK}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"][0]["id"])')
curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/run-case" -d "{\"runId\":${RUN},\"stepResults\":[{\"id\":${STEP},\"result\":\"pass\"}]}" > /dev/null
curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/run-case" -d "{\"runId\":${RUN},\"stepResults\":[{\"id\":${STEP},\"result\":\"fail\"}]}" > /dev/null
echo "    用例=${CASE} 测试单=${TASK} run=${RUN}（先 pass 后 fail）"

TODAY=$(date +%F)
R=$(curl -s "${H[@]}" "$BASE/zentao/testreport/preview?product=1&tasks=${TASK}&begin=2026-01-01&end=${TODAY}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['caseCount'], d['runCaseCount'], d['resultCount'], d['passCount'], d['failCount'])")
# 关键：resultCount=2（跑了两次）但 runCaseCount=1、failCount=1（只取最后一次）
[ "$line" = "1 1 2 0 1" ] && { PASS=$((PASS+1)); echo "  ✅ 跑 2 次只算 1 条结果，且取最后一次（fail）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 汇总规则 实际=${line}（期望 1 1 2 0 1）"; }

echo "--- 3. 报告 CRUD 与留档 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/create" -d "{\"product\":1,\"title\":\"RP报告-${TS}\",\"tasks\":\"${TASK}\",\"begin\":\"2026-01-01\",\"end\":\"${TODAY}\",\"owner\":\"admin\",\"report\":\"结论：有一个失败\"}")
check "新建报告" 0 "$R"
REPORT=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/testreport/get?id=${REPORT}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['title'].startswith('RP报告-'), d['taskNames'].startswith('RP测试单-'), d['owner'], d['caseCount'], d['failCount'], d['resultCount'])")
[ "$line" = "True True admin 1 1 2" ] && { PASS=$((PASS+1)); echo "  ✅ 报告详情（含现算汇总）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 报告详情 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/create" -d "{\"product\":1,\"title\":\"日期非法-${TS}\",\"tasks\":\"${TASK}\",\"begin\":\"2026-05-01\",\"end\":\"2026-01-01\",\"owner\":\"admin\"}")
check "结束早于开始→拒绝" 1020018012 "$R" "不能早于开始"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/create" -d "{\"product\":1,\"title\":\"无测试单-${TS}\",\"tasks\":\"\",\"begin\":\"2026-01-01\",\"end\":\"${TODAY}\",\"owner\":\"admin\"}")
code=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "$code" != "0" ] && { PASS=$((PASS+1)); echo "  ✅ 不给测试单被拦下 code=${code}"; } || { FAIL=$((FAIL+1)); echo "  ❌ 无测试单 code=${code}"; }

# 跨产品测试单：94102 属于产品 1，用产品 3 的报告去汇总它
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/create" -d "{\"product\":3,\"title\":\"跨产品-${TS}\",\"tasks\":\"94102\",\"begin\":\"2026-01-01\",\"end\":\"${TODAY}\",\"owner\":\"admin\"}")
check "汇总别的产品的测试单→拒绝" 1020018014 "$R" "不属于该报告的产品"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testreport/update" -d "{\"id\":${REPORT},\"product\":1,\"title\":\"RP报告-${TS}-改名\",\"tasks\":\"${TASK}\",\"begin\":\"2026-01-01\",\"end\":\"${TODAY}\",\"owner\":\"admin\",\"report\":\"改过的结论\"}")
check "修改报告" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testreport/get?id=${REPORT}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['title'].endswith('-改名'), d['report'])")
[ "$line" = "True 改过的结论" ] && { PASS=$((PASS+1)); echo "  ✅ 修改后 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 修改后 实际=$line"; }

echo "--- 4. 用例集 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/testreport/suite/page?product=1&pageSize=50")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
m={x['id']:x for x in d['list']}
print(m[95101]['name'], m[95101]['caseCount'], m[95102]['caseCount'])")
[ "$line" = "冒烟用例集 2 3" ] && { PASS=$((PASS+1)); echo "  ✅ 演示用例集与用例数 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用例集 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/suite/create" -d "{\"product\":1,\"name\":\"冒烟用例集\"}")
check "同产品重名→拒绝" 1020018001 "$R" "已存在同名用例集"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/suite/create" -d "{\"product\":1,\"name\":\"RP集合-${TS}\",\"desc\":\"临时\"}")
check "新建用例集" 0 "$R"
SUITE=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/suite/link-case" -d "{\"suiteId\":${SUITE},\"caseIds\":[${CASE}]}")
[ "$(echo "$R" | d)" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 用例加进集合"; } || { FAIL=$((FAIL+1)); echo "  ❌ 加用例=$(echo "$R" | d)"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/suite/link-case" -d "{\"suiteId\":${SUITE},\"caseIds\":[${CASE}]}")
[ "$(echo "$R" | d)" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 重复加入是幂等的"; } || { FAIL=$((FAIL+1)); echo "  ❌ 重复加入=$(echo "$R" | d)"; }
n=$(curl -s "${H[@]}" "$BASE/zentao/testreport/suite/get?id=${SUITE}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["caseCount"])')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 重复加入后集合里仍是 1 条（不会重复插行）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 集合条数=${n}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/suite/create" -d "{\"product\":3,\"name\":\"别的产品集合-${TS}\"}")
OTHER=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testreport/suite/link-case" -d "{\"suiteId\":${OTHER},\"caseIds\":[${CASE}]}")
check "跨产品用例→拒绝" 1020018002 "$R" "不属于该用例集的产品"

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/testreport/suite/delete?id=${SUITE}")
check "集合里还有用例→拒绝删除" 1020018004 "$R" "还有 1 条用例"

R=$(curl -s "${H[@]}" -X GET "$BASE/zentao/testreport/suite/unlinked-case-list?suiteId=${SUITE}")
line=$(echo "$R" | python3 -c "
import sys,json
ids={x['id'] for x in json.load(sys.stdin)['data']}
print('ok' if ${CASE} not in ids and 93102 in ids else sorted(ids))")
[ "$line" = "ok" ] && { PASS=$((PASS+1)); echo "  ✅ 可加入的用例排除了已加入的"; } || { FAIL=$((FAIL+1)); echo "  ❌ 可加入列表 实际=$line"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/testreport/suite/unlink-case?suiteId=${SUITE}&caseId=${CASE}")
check "移出用例" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/testreport/suite/delete?id=${SUITE}")
check "清空后删除集合" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testreport/suite/get?id=${SUITE}")
check "删除后再查→不存在" 1020018000 "$R" "用例集不存在"

echo "--- 5. 清理 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/testreport/delete?id=${REPORT}")
check "删除报告" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testreport/get?id=${REPORT}")
check "删除后再查→不存在" 1020018010 "$R" "测试报告不存在"

# 演示数据不能被误删
n=$(curl -s "${H[@]}" "$BASE/zentao/testreport/suite/page?product=1&pageSize=50" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]["list"]))')
[ "$n" -ge "2" ] && { PASS=$((PASS+1)); echo "  ✅ 演示用例集未被误删（${n} 个）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 演示集合=${n}"; }

# 收尾：删掉本次造的用例/测试单（测试单删除会连带清理 run 与结果）
curl -s "${H[@]}" -X DELETE "$BASE/zentao/testtask/delete?id=${TASK}" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/testcase/delete?id=${CASE}" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/testreport/suite/delete?id=${OTHER}" > /dev/null

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

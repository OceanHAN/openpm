#!/bin/bash
# 质量链收口：缺陷解决 → 构建 / 发布清单的**自动回写**
#
# 禅道语义（module/bug/model.php:2075 resolve()）：
#   缺陷解决时如果填了「解决版本」（存的是**构建编号**），会做两件事：
#     ① 把该 Bug 并进这个构建的 Bug 清单（zt_build.bugs）
#     ② 再找到**包含这个构建**的发布（FIND_IN_SET(build) 或 shadow = 构建），
#        把 Bug 并进发布的 Bug 清单（zt_release.bugs），并同步 zt_releaserelated 关系行
#   这就是「质量链」的自动那一半：用例失败 → 建缺陷 → 解决 → 进构建 → 进发布。
#   之前只有手工关联（build/link-bug）能动这两份清单，缺陷自己解决是"断链"的。
#
# 本脚本覆盖：正向回写、没匹配发布的构建、影子构建、非数字解决版本、去重与幂等、
# 以及反向（构建关联 Bug 自动解决）也走同一条链。
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

# 某构建/发布清单里有没有这个 Bug（逗号列表，两端可能带逗号）
has_bug() {  # $1=json body $2=字段 $3=bugId
  echo "$1" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
raw = d.get('$2') or ''
ids = [x for x in str(raw).replace(' ', '').split(',') if x]
print('$3' in ids)"
}

echo "===== 质量链（缺陷 → 构建 → 发布）测试 ====="
TS=$(date +%s)

echo "--- 0. 准备：产品 / 构建 / 发布 / 缺陷 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"QC产品-${TS}\",\"code\":\"QC${TS}\",\"type\":\"normal\",\"PO\":\"admin\"}")
check "产品" 0 "$R"
PROD=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" \
  -d "{\"product\":${PROD},\"name\":\"QC构建A-${TS}\",\"execution\":90001,\"branch\":0,\"date\":\"2026-03-01\",\"builder\":\"admin\"}")
check "构建 A" 0 "$R"
BUILD_A=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" \
  -d "{\"product\":${PROD},\"name\":\"QC发布A-${TS}\",\"builds\":[${BUILD_A}],\"date\":\"2026-03-02\",\"status\":\"wait\"}")
check "发布 A（包含构建 A）" 0 "$R"
REL_A=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" \
  -d "{\"product\":${PROD},\"name\":\"QC构建B-${TS}\",\"execution\":90001,\"branch\":0,\"date\":\"2026-03-03\",\"builder\":\"admin\"}")
check "构建 B（不属于任何发布）" 0 "$R"
BUILD_B=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" \
  -d "{\"product\":${PROD},\"name\":\"QC发布C-${TS}（无构建）\",\"date\":\"2026-03-04\",\"status\":\"wait\"}")
check "发布 C（不带构建 → 自动生成影子构建）" 0 "$R"
REL_C=$(echo "$R" | d)
SHADOW=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=${REL_C}" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['shadow'])")
[ "${SHADOW}" != "0" ] && [ -n "${SHADOW}" ] && { PASS=$((PASS+1)); echo "  ✅ 发布 C 的影子构建编号 = ${SHADOW}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 影子构建 实际=${SHADOW}"; }

mkbug() {  # 建一个缺陷，回显 id
  curl -s "${H[@]}" -X POST "$BASE/zentao/bug/create" \
    -d "{\"product\":${PROD},\"title\":\"$1\",\"steps\":\"步骤\",\"severity\":3,\"pri\":3,\"type\":\"codeerror\"}" | d
}
B1=$(mkbug "QC缺陷1-${TS}")
B2=$(mkbug "QC缺陷2-${TS}")
B3=$(mkbug "QC缺陷3-${TS}")
B4=$(mkbug "QC缺陷4-${TS}")

echo "--- 1. 缺陷解决 → 自动并进构建 A 与发布 A ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/bug/resolve" \
  -d "{\"id\":${B1},\"resolution\":\"fixed\",\"resolvedBuild\":\"${BUILD_A}\"}")
check "缺陷 1 以「已解决/fixed」解决，解决版本=构建 A" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=${B1}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['status'], d['resolution'], d['resolvedBuild'])")
[ "${line}" = "resolved fixed ${BUILD_A}" ] && { PASS=$((PASS+1)); echo "  ✅ 缺陷状态/解决版本 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 缺陷状态 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=${BUILD_A}")
line=$(has_bug "$R" bugs "${B1}")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 构建 A 的 Bug 清单自动加上了缺陷 1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 构建 A 清单 实际=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["bugs"])')"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=${REL_A}")
line=$(has_bug "$R" bugs "${B1}")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 发布 A 的 Bug 清单自动加上了缺陷 1（构建→发布）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 发布 A 清单 实际=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["bugs"])')"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/release/bug-list?release=${REL_A}&type=bug")
line=$(echo "$R" | python3 -c "import sys,json;print(len([x for x in json.load(sys.stdin)['data'] if x['id']==${B1}]))")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 发布 A 的「解决的 Bug」清单里能查到它"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 发布 A bug-list 实际=${line}"; }

# 关系行（zt_releaserelated）也随之同步：它不该再出现在「未关联」候选里
R=$(curl -s "${H[@]}" "$BASE/zentao/release/unlinked-bug-list?release=${REL_A}&type=bug")
line=$(echo "$R" | python3 -c "import sys,json;print(len([x for x in json.load(sys.stdin)['data'] if x['id']==${B1}]))")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 缺陷 1 不再出现在发布 A 的「未关联」候选里（关系行也同步了）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未关联候选 实际=${line}"; }

echo "--- 2. 构建不在任何发布里 → 只回写构建 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/bug/resolve" \
  -d "{\"id\":${B2},\"resolution\":\"fixed\",\"resolvedBuild\":\"${BUILD_B}\"}")
check "缺陷 2 解决版本=构建 B" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=${BUILD_B}")
line=$(has_bug "$R" bugs "${B2}")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 构建 B 的清单加上了缺陷 2"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 构建 B 清单 实际=${line}"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=${REL_A}")
line=$(has_bug "$R" bugs "${B2}")
[ "${line}" = "False" ] && { PASS=$((PASS+1)); echo "  ✅ 发布 A 没有被误加（构建 B 不属于它）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 发布 A 被误加 实际=${line}"; }

echo "--- 3. 影子构建：发布 C 没有构建，解决版本填它的影子构建 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/bug/resolve" \
  -d "{\"id\":${B3},\"resolution\":\"fixed\",\"resolvedBuild\":\"${SHADOW}\"}")
check "缺陷 3 解决版本=发布 C 的影子构建" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=${REL_C}")
line=$(has_bug "$R" bugs "${B3}")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 发布 C 拿到了缺陷 3（shadow 路径）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 发布 C 实际=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["bugs"])')"; }

echo "--- 4. 解决版本不是构建编号 → 不动清单 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/bug/resolve" \
  -d "{\"id\":${B4},\"resolution\":\"fixed\",\"resolvedBuild\":\"v1.1\"}")
check "缺陷 4 解决版本填了版本名（非构建编号）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=${BUILD_A}")
line=$(has_bug "$R" bugs "${B4}")
[ "${line}" = "False" ] && { PASS=$((PASS+1)); echo "  ✅ 构建 A 没被加上（不是编号，直接跳过，不报错）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 构建 A 被污染 实际=${line}"; }

echo "--- 5. 去重与幂等 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/link-bug" -d "{\"release\":${REL_A},\"ids\":[${B1}],\"type\":\"bug\"}")
check "再手工关联一次同一个 Bug" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=${REL_A}")
line=$(echo "$R" | python3 -c "
import sys,json
raw=(json.load(sys.stdin)['data'].get('bugs') or '').replace(' ','')
print(raw.split(',').count('${B1}'))")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 发布 A 的清单里缺陷 1 只出现一次（去重）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 重复 实际=${line}"; }

echo "--- 6. 反向：构建关联未解决的 Bug → 自动解决 + 同样回写发布 ---"
B5=$(mkbug "QC缺陷5-${TS}")
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-bug" -d "{\"build\":${BUILD_A},\"ids\":[${B5}]}")
check "把缺陷 5 关联到构建 A" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=${B5}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['status'], d['resolution'], d['resolvedBuild'])")
[ "${line}" = "resolved fixed ${BUILD_A}" ] && { PASS=$((PASS+1)); echo "  ✅ 关联即解决（禅道 updateLinkedBug）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 关联即解决 实际=${line}"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=${REL_A}")
line=$(has_bug "$R" bugs "${B5}")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 发布 A 也自动拿到了缺陷 5（与 resolve 走同一条链）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 发布 A 实际=${line}"; }

echo "--- 7. 清理 ---"
for b in ${B1} ${B2} ${B3} ${B4} ${B5}; do
  curl -s "${H[@]}" -X DELETE "$BASE/zentao/bug/delete?id=${b}" > /dev/null
done
for r in ${REL_A} ${REL_C}; do
  curl -s "${H[@]}" -X DELETE "$BASE/zentao/release/delete?id=${r}" > /dev/null
done
for b in ${BUILD_A} ${BUILD_B}; do
  curl -s "${H[@]}" -X DELETE "$BASE/zentao/build/delete?id=${b}" > /dev/null
done
curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=${PROD}" > /dev/null
n=$(curl -s "${H[@]}" "$BASE/zentao/build/page?pageNo=1&pageSize=200&product=${PROD}" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "${n}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 测试数据已清理"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 残留构建=${n}"; }

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

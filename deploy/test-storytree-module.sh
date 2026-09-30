#!/bin/bash
# 父子需求（需求分解）接口测试
#
# 禅道语义：需求可以分解成若干子需求，父需求只做汇总：
#   1. path/root/grade 组成需求树（与模块树同一套规则）
#   2. parentVersion 冻结**分解时父需求的版本**
#   3. isParent 标记 + 父需求 estimate = **所有子需求工时之和**
#   4. 状态级联：子需求全关 → 父自动关闭；父已关闭但子又被激活 → 父自动激活
#   5. 本实现额外做了「父需求还有子需求时不能删」的保护
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

echo "===== 父子需求（分解）模块测试 ====="
TS=$(date +%s)

echo "--- 1. 演示数据的父需求聚合 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=4")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['isParent'], d['childCount'], d['estimate'], d['path'], d['grade'])")
[ "${line}" = "True 2 10.0 ,4, 1" ] && { PASS=$((PASS+1)); echo "  ✅ 父需求 4：已分解 / 2 个子需求 / 工时 10（=4+6）/ 一级"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 父需求聚合 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/story/child-list?parentId=4")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), '|'.join(str(x['id']) for x in d), '|'.join(str(x['grade']) for x in d), d[0]['parentTitle'][:6])")
[ "${line}" = "2 92201|92202 2|2 支持需求批量" ] && { PASS=$((PASS+1)); echo "  ✅ 子需求列表 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 子需求列表 实际=${line}"; }

echo "--- 2. 分解已有需求 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":1,\"title\":\"父需求-${TS}\",\"type\":\"story\",\"category\":\"feature\",\"pri\":1,\"estimate\":0}")
check "准备：新建一个空的需求" 0 "$R"
PARENT=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":1,\"title\":\"待分解-${TS}\",\"type\":\"story\",\"category\":\"feature\",\"pri\":3,\"estimate\":3}")
check "准备：新建一条待分解的需求" 0 "$R"
CHILD=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/subdivide?parentId=${PARENT}" -d "[${CHILD}]")
[ "$(echo "$R" | d)" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 把已有需求挂到父需求下"; } || { FAIL=$((FAIL+1)); echo "  ❌ subdivide=$(echo "$R" | d)"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=${CHILD}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['parent'], d['root'], d['path'], d['grade'], d['parentVersion'], d['parentChanged'])")
[ "${line}" = "${PARENT} ${PARENT} ,${PARENT},${CHILD}, 2 1 False" ] && { PASS=$((PASS+1)); echo "  ✅ 子需求的树字段 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 树字段 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=${PARENT}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['isParent'], d['childCount'], d['estimate'])")
[ "${line}" = "True 1 3.0" ] && { PASS=$((PASS+1)); echo "  ✅ 父需求聚合（工时 = 子需求之和 3）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 父需求聚合 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/subdivide?parentId=${PARENT}" -d "[${CHILD}]")
check "重复分解→拒绝" 1020000011 "$R" "已经有父需求"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/subdivide?parentId=${CHILD}" -d "[${PARENT}]")
check "把父挂到子下→拒绝" 1020000009 "$R" "子需求"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/subdivide?parentId=${PARENT}" -d "[${PARENT}]")
check "挂到自己→拒绝" 1020000009 "$R" "子需求"

echo "--- 3. 批量分解成子需求（只给标题，其余继承父需求）---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/batch-create-child?parentId=${PARENT}" -d "[\"子需求A-${TS}\",\"子需求B-${TS}\"]")
check "把需求拆成 2 条子需求" 0 "$R"
N=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "${N}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 返回 2 个子需求编号"; } || { FAIL=$((FAIL+1)); echo "  ❌ 子需求数=${N}"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/story/child-list?parentId=${PARENT}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), all(x['product']==1 for x in d), '|'.join(str(x['grade']) for x in d))")
[ "${line}" = "3 True 2|2|2" ] && { PASS=$((PASS+1)); echo "  ✅ 继承父需求的产品，层级都是 2 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 子需求继承 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=${PARENT}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['childCount'], d['estimate'])")
[ "${line}" = "3 3.0" ] && { PASS=$((PASS+1)); echo "  ✅ 父需求子需求数 3（新子需求没填工时，合计仍 3）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 父需求 实际=${line}"; }

echo "--- 4. 状态级联（子动父跟着动）---"
# 先把三条子需求都关闭 → 父需求应自动关闭
for cid in $(curl -s "${H[@]}" "$BASE/zentao/story/child-list?parentId=${PARENT}" | python3 -c 'import sys,json;print(" ".join(str(x["id"]) for x in json.load(sys.stdin)["data"]))'); do
  curl -s "${H[@]}" -X PUT "$BASE/zentao/story/close" -d "{\"id\":${cid},\"closedReason\":\"done\"}" > /dev/null
done
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=${PARENT}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['status'])")
[ "${line}" = "closed" ] && { PASS=$((PASS+1)); echo "  ✅ 子需求全部关闭 → 父需求自动关闭"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 父需求状态 实际=${line}"; }

# 再激活一条子需求 → 父需求应自动激活
FIRST=$(curl -s "${H[@]}" "$BASE/zentao/story/child-list?parentId=${PARENT}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"][0]["id"])')
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/story/activate?id=${FIRST}")
check "激活一条子需求" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=${PARENT}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['status'])")
[ "${line}" = "active" ] && { PASS=$((PASS+1)); echo "  ✅ 父需求已关闭但子需求被激活 → 父需求自动激活"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 父需求状态 实际=${line}"; }

echo "--- 5. 父需求版本冻结 ---"
# 父需求正式变更 → 子需求的 parentChanged 应变成 true
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/story/change" -d "{\"id\":${PARENT},\"title\":\"父需求-${TS}-改过了\",\"spec\":\"新描述\",\"verify\":\"\"}")
check "父需求正式变更（版本 +1）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/child-list?parentId=${PARENT}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d[0]['parentVersion'], d[0]['parentChanged'])")
[ "${line}" = "1 True" ] && { PASS=$((PASS+1)); echo "  ✅ 父需求升版后子需求提示「父需求已变更」= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 父需求变更提示 实际=${line}"; }

echo "--- 6. 父需求删除保护与清理 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=${PARENT}")
check "父需求还有子需求→拒绝删除" 1020000010 "$R" "已分解出"

# 清理：先删子需求再删父需求
for cid in $(curl -s "${H[@]}" "$BASE/zentao/story/child-list?parentId=${PARENT}" | python3 -c 'import sys,json;print(" ".join(str(x["id"]) for x in json.load(sys.stdin)["data"]))'); do
  curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=${cid}" > /dev/null
done
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=${PARENT}")
check "子需求清空后删父需求" 0 "$R"

# 演示数据不能被误删
R=$(curl -s "${H[@]}" "$BASE/zentao/story/child-list?parentId=4")
line=$(echo "$R" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")
[ "${line}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 演示的父子需求未被误删（2 条）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 演示子需求=${line}"; }

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

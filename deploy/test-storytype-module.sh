#!/bin/bash
# 需求分层（业务需求 ER / 用户需求 UR / 研发需求 SR）接口测试
#
# 禅道语义：
#   1. 三层需求共用 zt_story 一张表，靠 type 区分：epic / requirement / story
#      （module/epic、module/requirement 只是 story 的薄壳，model.php 都只有 15 行）
#   2. 父子类型规则：**父需求的层级不能低于子需求**，归纳自禅道
#      getEpicParents（父只能 epic）/ getRequirementParents（epic+requirement）/
#      getStoryParents（epic+requirement）
#   3. 分解（batchCreateChild）出来的子需求类型按父层级推导：
#      业务需求 → 用户需求 → 研发需求 → 研发需求
#   4. 三层共用同一套 path/root/grade、版本快照、聚合（父 estimate = 子之和）机制
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
    PASS=$((PASS+1)); printf '  ✅ %-52s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-52s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
# 从 stdin 的响应里取字段，参数是 python 表达式（d 为 data）
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }
# 原始响应体，用于校验失败场景
mkraw() { # product parent type title [estimate]
  local est="${5:-0}"
  curl -s -X POST "${H[@]}" "$BASE/zentao/story/create" \
    -d "{\"product\":$1,\"parent\":$2,\"type\":\"$3\",\"title\":\"$4\",\"pri\":3,\"estimate\":$est}"
}
# 建需求，回显编号（失败时为空）
mkstory() { # product parent type title [estimate]
  mkraw "$1" "$2" "$3" "$4" | d
}

echo "===== 需求分层（业务需求/用户需求/研发需求）模块测试 ====="
TS=$(date +%s)

echo "--- 1. 类型字典（禅道 \$lang->story->typeList） ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/type-list")
line=$(echo "$R" | field "len(d), '|'.join(t['type'] for t in d)")
[ "${line}" = "3 epic|requirement|story" ] && { PASS=$((PASS+1)); echo "  ✅ 类型字典共 3 项：epic/requirement/story"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 类型字典 实际=${line}"; }

line=$(echo "$R" | python3 -c "
import sys,json
m={t['type']:t for t in json.load(sys.stdin)['data']}
e=m['epic']; print(e['name'], e['level'], e['parentTypes'], e['childType'])")
[ "${line}" = "业务需求 1 epic requirement" ] && { PASS=$((PASS+1)); echo "  ✅ 业务需求：层级 1 / 父只能业务需求 / 分解出用户需求"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 业务需求字典 实际=${line}"; }

line=$(echo "$R" | python3 -c "
import sys,json
m={t['type']:t for t in json.load(sys.stdin)['data']}
u=m['requirement']; print(u['name'], u['level'], u['parentTypes'], u['childType'])")
[ "${line}" = "用户需求 2 epic,requirement story" ] && { PASS=$((PASS+1)); echo "  ✅ 用户需求：层级 2 / 父可为业务需求+用户需求 / 分解出研发需求"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用户需求字典 实际=${line}"; }

line=$(echo "$R" | python3 -c "
import sys,json
m={t['type']:t for t in json.load(sys.stdin)['data']}
s=m['story']; print(s['name'], s['level'], s['parentTypes'], s['childType'])")
[ "${line}" = "研发需求 3 epic,requirement,story story" ] && { PASS=$((PASS+1)); echo "  ✅ 研发需求：层级 3 / 父可为三层任意 / 分解出来还是研发需求"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 研发需求字典 实际=${line}"; }

echo "--- 2. 演示数据的分层链（业务需求 99301 → 用户需求 99302 → 研发需求 99303） ---"
line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=99301" | field "d['type'], d['grade'], d['path'], d['isParent']")
[ "${line}" = "epic 1 ,99301, True" ] && { PASS=$((PASS+1)); echo "  ✅ 业务需求 99301：type=epic / 一级 / 已分解"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 业务需求 99301 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=99303" | field "d['type'], d['grade'], d['path'], d['parent'], d['parentVersion']")
[ "${line}" = "story 3 ,99301,99302,99303, 99302 1" ] && { PASS=$((PASS+1)); echo "  ✅ 研发需求 99303：三级 / path 串起三层链 / 冻结父版本"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 研发需求 99303 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/story/type-tree?product=1" | python3 -c "
import sys,json
roots={r['id']:r for r in json.load(sys.stdin)['data']}
r=roots[99301]
print(r['type'], r['typeName'], r['childCount'], r['children'][0]['id'], r['children'][0]['typeName'],
      r['children'][0]['children'][0]['id'], r['children'][0]['children'][0]['typeName'])")
[ "${line}" = "epic 业务需求 1 99302 用户需求 99303 研发需求" ] && { PASS=$((PASS+1)); echo "  ✅ 分层树：业务需求 → 用户需求 → 研发需求 三层嵌套"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 分层树 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/story/type-tree?product=1&rootId=99302" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), d[0]['id'], d[0]['children'][0]['id'])")
[ "${line}" = "1 99302 99303" ] && { PASS=$((PASS+1)); echo "  ✅ 分层树支持只看子树（rootId=99302）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 子树查询 实际=${line}"; }

# 顺序：每一层都按 (层级, 编号) 升序 —— 父需求排在自己的子需求上面，同级按编号小到大
line=$(curl -s "${H[@]}" "$BASE/zentao/story/type-tree?product=1" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
LEVEL={'epic':1,'requirement':2,'story':3}
bad=[]
def walk(nodes):
    keys=[(LEVEL.get(n['type'],3), n['id']) for n in nodes]
    if keys != sorted(keys): bad.append([k for k in keys])
    for n in nodes: walk(n['children'])
walk(d)
print(len(d) > 0 and not bad, bad[:2])")
[ "${line}" = "True []" ] && { PASS=$((PASS+1)); echo "  ✅ 分层树每一层都按「层级 + 编号」升序（父在子上面）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 分层树顺序 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/story/type-summary?product=1" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('epic' in d and 'requirement' in d and 'story' in d, d['epic'] >= 1, d['requirement'] >= 1, d['story'] >= 1)")
[ "${line}" = "True True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 类型数量统计：三个类型都有数据（页签角标）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 类型统计 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/story/type-summary?product=99999999" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['epic'], d['requirement'], d['story'])")
[ "${line}" = "0 0 0" ] && { PASS=$((PASS+1)); echo "  ✅ 没有需求的产品：三个类型都返回 0（不是 undefined）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 空产品统计 实际=${line}"; }

echo "--- 3. 新建三层需求（含层级规则校验） ---"
E=$(mkstory 1 0 epic "ER-$TS")
line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$E" | field "d['type'], d['grade'], d['path'], d['isParent'], d['status'], d['version']")
[ "${line}" = "epic 1 ,$E, False draft 1" ] && { PASS=$((PASS+1)); echo "  ✅ 新建业务需求：自己就是一棵树（path=,$E, / grade=1）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建业务需求 实际=${line}"; }

U=$(mkstory 1 "$E" requirement "UR-$TS")
line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$U" | field "d['type'], d['grade'], d['path'], d['parent'], d['parentVersion']")
[ "${line}" = "requirement 2 ,$E,$U, $E 1" ] && { PASS=$((PASS+1)); echo "  ✅ 业务需求下建用户需求：二级 / 冻结父版本 1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 业务需求下建用户需求 实际=${line}"; }

S=$(mkraw 1 "$U" story "SR-$TS" 3.5 | d)
line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$S" | field "d['type'], d['grade'], d['path']")
[ "${line}" = "story 3 ,$E,$U,$S," ] && { PASS=$((PASS+1)); echo "  ✅ 用户需求下建研发需求：三级 / path 串起三层"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用户需求下建研发需求 实际=${line}"; }

S2=$(mkstory 1 "$E" story "SR2-$TS")
line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$S2" | field "d['type'], d['grade']")
[ "${line}" = "story 2" ] && { PASS=$((PASS+1)); echo "  ✅ 跨层合法：业务需求下直接建研发需求（父层级 1 ≤ 子层级 3）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 跨层建研发需求 实际=${line}"; }

check "用户需求下建业务需求 → 拒绝" 1020000015 \
  "$(mkraw 1 "$U" epic "BAD-ER-$TS")" "不能挂在"
check "研发需求下建用户需求 → 拒绝" 1020000015 \
  "$(mkraw 1 "$S" requirement "BAD-UR-$TS")" "不能挂在"
check "研发需求下建业务需求 → 拒绝" 1020000015 \
  "$(mkraw 1 "$S" epic "BAD-ER2-$TS")" "不能挂在"
check "非法需求类型 → 拒绝" 1020000014 \
  "$(mkraw 1 0 bogus "BAD-TYPE-$TS")" "需求类型不合法"
check "业务需求挂到研发需求下（subdivide）→ 拒绝" 1020000015 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/story/subdivide?parentId=$S" -d "[$E]")" "不能挂在"

echo "--- 4. 分解出来的子需求类型按父层级推导 ---"
CID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/story/batch-create-child?parentId=$U" -d "[\"CH-UR-$TS\"]" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0] if d else '')")
line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$CID" | field "d['type'], d['grade'], d['title']")
[ "${line}" = "story 3 CH-UR-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 用户需求分解出的子需求是研发需求（不是沿用父类型）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用户需求分解 实际=${line}"; }

CID2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/story/batch-create-child?parentId=$E" -d "[\"CH-ER-$TS\"]" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0] if d else '')")
line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$CID2" | field "d['type'], d['grade']")
[ "${line}" = "requirement 2" ] && { PASS=$((PASS+1)); echo "  ✅ 业务需求分解出的子需求是用户需求"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 业务需求分解 实际=${line}"; }

CID3=$(curl -s -X POST "${H[@]}" "$BASE/zentao/story/batch-create-child?parentId=$S" -d "[\"CH-SR-$TS\"]" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0] if d else '')")
line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$CID3" | field "d['type'], d['grade']")
[ "${line}" = "story 4" ] && { PASS=$((PASS+1)); echo "  ✅ 研发需求分解出来的还是研发需求（四级子需求）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 研发需求分解 实际=${line}"; }

# 父需求聚合：U 的直接子需求是 S（estimate=3.5）+ CID（继承不到 estimate，算 0）
line=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$U" | field "d['isParent'], d['childCount'], '%.2f' % d['estimate']")
[ "${line}" = "True 2 3.50" ] && { PASS=$((PASS+1)); echo "  ✅ 分解后父需求聚合：isParent=true / 2 个子需求 / 工时=子之和 3.50"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 分解后父需求聚合 实际=${line}"; }

echo "--- 5. 分页按分层类型过滤 ---"
line=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=100&product=1&type=epic" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['list']
print(len(rows) > 0, all(r['type']=='epic' for r in rows), any(r['id']==99301 for r in rows))")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ type=epic：只返回业务需求，含演示数据 99301"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ type=epic 过滤 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=200&product=1&types=epic,requirement" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['list']
print(all(r['type'] in ('epic','requirement') for r in rows), any(r['id']==99303 for r in rows))")
[ "${line}" = "True False" ] && { PASS=$((PASS+1)); echo "  ✅ types=epic,requirement：研发需求 99303 被排除"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ types 过滤 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=200&product=1" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['list']
print(any(r['id']==99301 for r in rows), any(r['id']==99302 for r in rows), any(r['id']==99303 for r in rows))")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 不带 type：三层需求都在列表里"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 不带 type 实际=${line}"; }

echo "--- 6. 删除保护与自清理（先叶子后父） ---"
check "有子需求的用户需求不能删" 1020000010 \
  "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$U")" "已分解出"
check "叶子研发需求可以删" 0 \
  "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$S2")" ""
for id in "$CID3" "$S" "$CID" "$CID2"; do
  check "删叶子需求 $id" 0 "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$id")" ""
done
check "子需求都删完后用户需求可以删" 0 \
  "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$U")" ""
check "最后业务需求可以删（三代全部清理干净）" 0 \
  "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$E")" ""

# 兜底清理：万一上面某步失败，别把测试数据留在库里污染后续用例
for id in "$CID3" "$S" "$CID" "$CID2" "$S2" "$U" "$E"; do
  [ -n "$id" ] && curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$id" >/dev/null
done

echo "======================================================"
echo "  需求分层模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

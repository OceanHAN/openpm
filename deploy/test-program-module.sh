#!/bin/bash
# 项目集（program）接口测试
#
# 禅道语义：config/zentaopms.php 里三个常量指向同一张表
#     define('TABLE_PROGRAM',   '`zt_project`');
#     define('TABLE_PROJECT',   '`zt_project`');
#     define('TABLE_EXECUTION', '`zt_project`');
# 也就是项目集、项目、执行是同一张表的三种 type。本脚本重点验证：
#   ① 三个角色互不串味（项目集列表里没有项目/执行，项目列表里没有项目集）
#   ② path 是**逗号格式且包含自己**、grade 从 1 开始（禅道 setTreePath）
#   ③ 项目靠 parent 指向所属项目集；执行靠 project 指向所属项目
#   ④ 移动项目集要重算整棵子树的 path/grade（禅道 processNode）
#   ⑤ 产品靠 zt_product.program 归属项目集
#
# 两个手测注意点（都踩过，见 README 第四节 35/36）：
#   * 中文查询参数必须 --data-urlencode：Tomcat 直接拒收未编码的非 ASCII 请求行（400 HTML）
#   * 删项目集有保护：先清产品/项目/子项目集，再删项目集
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

# 项目集/项目 的 path/grade/parent 三元组
shape() {
  curl -s "${H[@]}" "$BASE/zentao/$1/get?id=$2" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('%s %s %s' % (d.get('path'), d.get('grade'), d.get('parent')))"
}

echo "===== 项目集模块测试 ====="
TS=$(date +%s)

echo "--- 1. 演示数据：项目集 9001 / 9002 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/program/get?id=9001")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['name'], d['path'], d['grade'], d['status'], d['childCount'], d['projectCount'], d['productCount'])")
[ "${line}" = "禅道迁移项目集 ,9001, 1 doing 1 2 1" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 9001 = ${line}（1 个子项目集 / 2 个项目 / 1 个产品）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 9001 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/program/list-by-parent?parent=0")
line=$(echo "$R" | python3 -c "import sys,json;print(' '.join(str(x['id']) for x in json.load(sys.stdin)['data']))")
[ "${line}" = "9001" ] && { PASS=$((PASS+1)); echo "  ✅ 顶级项目集只有 9001（9002 挂在它下面）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 顶级项目集 实际=${line}"; }

line=$(shape program 9002)
[ "${line}" = ",9001,9002, 2 9001" ] && { PASS=$((PASS+1)); echo "  ✅ 子项目集 9002：path/grade/parent = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 9002 层级 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/program/project-list?programId=9001")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), ' '.join('%s:%s:%s' % (x['id'], x['path'], x['grade']) for x in d))")
[ "${line}" = "2 1:,9001,1,:2 2:,9001,2,:2" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 项目集下的项目：${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目集下的项目 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/program/product-list?programId=9001")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(len(d), d[0]['id'], d[0]['program'])")
[ "${line}" = "1 1 9001" ] && { PASS=$((PASS+1)); echo "  ✅ 项目集下的产品（zt_product.program）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目集下的产品 实际=${line}"; }

echo "--- 2. 三个角色互不串味 ---"
R=$(curl -s -G "${H[@]}" --data-urlencode "name=数据治理项目集" "$BASE/zentao/project/page?pageNo=1&pageSize=50")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 项目列表里查不到项目集（total=0）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目列表混入项目集 total=${line}"; }

R=$(curl -s -G "${H[@]}" --data-urlencode "name=禅道迁移项目集" "$BASE/zentao/execution/page?pageNo=1&pageSize=50")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 执行列表里查不到项目集（total=0）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 执行列表混入项目集 total=${line}"; }

R=$(curl -s -G "${H[@]}" --data-urlencode "name=禅道迁移一期" "$BASE/zentao/program/page?pageNo=1&pageSize=50")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 项目集列表里查不到项目（total=0）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目集列表混入项目 total=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/program/page?pageNo=1&pageSize=50&status=doing")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['total'], all(x['status']=='doing' for x in d['list']))")
[ "${line}" = "1 True" ] && { PASS=$((PASS+1)); echo "  ✅ 按状态过滤项目集 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 状态过滤 实际=${line}"; }

# 拿错 id：项目/执行 id 查项目集、项目集 id 查项目
R=$(curl -s "${H[@]}" "$BASE/zentao/program/get?id=1")
check "用项目 id 查项目集 → 拒绝" 1020020000 "$R" "项目集不存在"
R=$(curl -s "${H[@]}" "$BASE/zentao/program/get?id=90001")
check "用执行 id 查项目集 → 拒绝" 1020020000 "$R" "项目集不存在"
R=$(curl -s "${H[@]}" "$BASE/zentao/project/get?id=9001")
check "用项目集 id 查项目 → 拒绝" 1020005008 "$R" "不是项目"
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=9001")
code=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "${code}" != "0" ] && { PASS=$((PASS+1)); echo "  ✅ 用项目集 id 查执行被拒绝 code=${code}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 执行接口接受了项目集 id"; }

echo "--- 3. 新建项目集：path/grade/唯一性/日期 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/program/create" \
  -d "{\"name\":\"测试项目集-${TS}\",\"code\":\"TP-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-06-30\",\"PM\":\"admin\"}")
check "新建顶级项目集" 0 "$R"
P=$(echo "$R" | d)
line=$(shape program "${P}")
[ "${line}" = ",${P}, 1 0" ] && { PASS=$((PASS+1)); echo "  ✅ 顶级项目集 path=',id,'、grade=1 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 顶级项目集 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/program/create" \
  -d "{\"parent\":${P},\"name\":\"测试子项目集-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-06-30\"}")
check "新建子项目集" 0 "$R"
SUB=$(echo "$R" | d)
line=$(shape program "${SUB}")
[ "${line}" = ",${P},${SUB}, 2 ${P}" ] && { PASS=$((PASS+1)); echo "  ✅ 子项目集 path=父.path+id+','、grade=2 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 子项目集 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/program/create" \
  -d "{\"parent\":${P},\"name\":\"测试子项目集-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-06-30\"}")
check "同一级下重名 → 拒绝" 1020020001 "$R" "同级下已存在同名项目集"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/program/create" \
  -d "{\"name\":\"测试子项目集-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-06-30\"}")
check "换个上级后同名 → 允许（禅道只在本级查重）" 0 "$R"
P2=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/program/create" \
  -d "{\"name\":\"日期错-${TS}\",\"begin\":\"2026-06-30\",\"end\":\"2026-01-01\"}")
check "结束早于开始 → 拒绝" 1020020008 "$R" "不能早于"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/program/create" -d "{\"name\":\"缺日期-${TS}\"}")
check "缺计划日期 → 拒绝" 400 "$R"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/program/create" -d "{\"begin\":\"2026-01-01\",\"end\":\"2026-02-01\"}")
check "缺名称 → 拒绝" 400 "$R"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/program/create" \
  -d "{\"parent\":1,\"name\":\"挂项目下-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-02-01\"}")
check "上级传项目 id → 拒绝" 1020020009 "$R" "不是项目集"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/program/create" \
  -d "{\"parent\":90001,\"name\":\"挂执行下-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-02-01\"}")
check "上级传执行 id → 拒绝" 1020020009 "$R" "不是项目集"

echo "--- 4. 项目挂到项目集 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" \
  -d "{\"parent\":${P},\"name\":\"项目集下的项目-${TS}\",\"model\":\"scrum\",\"pri\":3,\"begin\":\"2026-02-01\",\"end\":\"2026-04-30\"}")
check "在项目集下建项目" 0 "$R"
PROJ=$(echo "$R" | d)
line=$(shape project "${PROJ}")
[ "${line}" = ",${P},${PROJ}, 2 ${P}" ] && { PASS=$((PASS+1)); echo "  ✅ 项目 path=项目集.path+id+','、grade=2 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目层级 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" \
  -d "{\"parent\":1,\"name\":\"挂项目下的项目-${TS}\",\"model\":\"scrum\",\"pri\":3}")
check "项目的所属项目集传项目 id → 拒绝" 1020005009 "$R" "不是项目集"

R=$(curl -s "${H[@]}" "$BASE/zentao/project/page?pageNo=1&pageSize=50&parent=${P}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], d['list'][0]['id'])")
[ "${line}" = "1 ${PROJ}" ] && { PASS=$((PASS+1)); echo "  ✅ 项目列表按所属项目集过滤 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按项目集过滤 实际=${line}"; }

echo "--- 5. 移动项目集：整棵子树的 path/grade 一起重算 ---"
# P 下面有：子项目集 SUB、项目 PROJ。把 P 挂到 9001 下 → 子树 path 前缀整体换成 ,9001,、grade 各 +1
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/update" \
  -d "{\"id\":${P},\"parent\":9001,\"name\":\"测试项目集-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-06-30\"}")
check "把项目集挂到 9001 下" 0 "$R"
line=$(shape program "${P}"); [ "${line}" = ",9001,${P}, 2 9001" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 自己：${line}"; } || { FAIL=$((FAIL+1)); echo "  ❌ 自己 实际=${line}"; }
line=$(shape program "${SUB}"); [ "${line}" = ",9001,${P},${SUB}, 3 ${P}" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 子项目集：path 前缀换成新父、grade +1 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 子项目集 实际=${line}"; }
line=$(shape project "${PROJ}"); [ "${line}" = ",9001,${P},${PROJ}, 3 ${P}" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 项目也一起挪 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目 实际=${line}"; }

# 再挪回顶级 → 前缀去掉 ,9001,、grade 各 -1
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/update" \
  -d "{\"id\":${P},\"parent\":0,\"name\":\"测试项目集-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-06-30\"}")
check "把项目集挪回顶级" 0 "$R"
line=$(shape program "${P}"); [ "${line}" = ",${P}, 1 0" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 自己：${line}"; } || { FAIL=$((FAIL+1)); echo "  ❌ 自己 实际=${line}"; }
line=$(shape program "${SUB}"); [ "${line}" = ",${P},${SUB}, 2 ${P}" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 子项目集回到 2 级 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 子项目集 实际=${line}"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/update" \
  -d "{\"id\":${P},\"parent\":${P},\"name\":\"测试项目集-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-06-30\"}")
check "把自己挂到自己下面 → 拒绝" 1020020009 "$R"

echo "--- 6. 产品归属项目集 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" \
  -d "{\"program\":${P},\"name\":\"项目集下的产品-${TS}\"}")
check "建产品并归属项目集" 0 "$R"
PROD=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/product/page?pageNo=1&pageSize=50&program=${P}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], d['list'][0]['program'])")
[ "${line}" = "1 ${P}" ] && { PASS=$((PASS+1)); echo "  ✅ 产品按项目集过滤 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 产品过滤 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" \
  -d "{\"program\":99999999,\"name\":\"坏项目集产品-${TS}\"}")
check "项目集不存在 → 拒绝" 1020020011 "$R" "项目集不存在"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" \
  -d "{\"line\":99999999,\"name\":\"坏产品线产品-${TS}\"}")
check "产品线不存在 → 拒绝" 1020020012 "$R" "产品线不存在"

echo "--- 7. 状态流转 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/start?id=${P2}")
check "开始项目集（wait → doing）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/program/get?id=${P2}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['status'], bool(d['realBegan']))")
[ "${line}" = "doing True" ] && { PASS=$((PASS+1)); echo "  ✅ 状态与实际开始日期 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 开始项目集 实际=${line}"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/start?id=${P2}")
check "重复开始 → 拒绝" 1020020003 "$R"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/suspend?id=${P2}")
check "挂起项目集" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/activate?id=${P2}")
check "激活项目集" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/close?id=${P2}&reason=done")
check "关闭项目集" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/close?id=${P2}&reason=done")
check "重复关闭 → 拒绝" 1020020004 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/program/update" \
  -d "{\"id\":${P2},\"name\":\"改已关闭-${TS}\",\"begin\":\"2026-01-01\",\"end\":\"2026-02-01\"}")
check "已关闭的项目集不能改 → 拒绝" 1020020002 "$R" "已关闭"

echo "--- 8. 删除保护 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/program/delete?id=9001")
check "项目集下有子项目集/项目/产品 → 拒绝" 1020020005 "$R" "子项目集"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/program/delete?id=${SUB}")
check "空的子项目集可以删" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/program/delete?id=${P}")
check "项目集下有项目 → 拒绝" 1020020006 "$R" "个项目"
curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=${PROJ}" > /dev/null
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/program/delete?id=${P}")
check "项目和子项目集都清了、但还有产品 → 拒绝" 1020020007 "$R" "个产品"

echo "--- 9. 清理并确认演示数据完好 ---"
# 顺序很重要：项目集只有在「没有下级项目集/项目/产品」时才删得掉
curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=${PROD}" > /dev/null
for id in $(curl -s "${H[@]}" "$BASE/zentao/program/list" | python3 -c "
import sys,json
for x in json.load(sys.stdin)['data']:
    if x['id'] not in (9001, 9002):
        print(x['id'])"); do
  curl -s "${H[@]}" -X DELETE "$BASE/zentao/program/delete?id=${id}" > /dev/null
done

R=$(curl -s "${H[@]}" "$BASE/zentao/program/get?id=9001")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['name'], d['path'], d['grade'], d['childCount'], d['projectCount'], d['productCount'])")
[ "${line}" = "禅道迁移项目集 ,9001, 1 1 2 1" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 演示数据完好：${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示数据被破坏：${line}"; }

line=$(shape program 9002); [ "${line}" = ",9001,9002, 2 9001" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 9002 仍是 9001 的子项目集"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 9002 实际=${line}"; }

line=$(shape project 1); [ "${line}" = ",9001,1, 2 9001" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 演示项目 1 仍挂在项目集 9001 下"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示项目 1 实际=${line}"; }

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

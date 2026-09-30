#!/bin/bash
# 用例库（caselib）接口测试
#
# 禅道语义（module/caselib/*）：
#   1. 用例库**没有自己的表**：它是 zt_testsuite 里 (product=0, type='library') 的行
#      （module/caselib/model.php:142），用例集是同一张表里 (product>0, type in public/private)
#   2. 库内用例是 zt_case 里 (product=0, lib=<库编号>) 的行，库的模块树是
#      zt_module 里 (root=<库>, type='caselib') 那棵树
#   3. 因此两边查询都必须带区分条件 —— 库不能出现在用例集列表里，库用例不能出现在产品用例列表里
#   4. 产品用例 → 用例库（testcase/importToLib）：复制用例与步骤，fromCaseID / fromCaseVersion
#      记下来源与来源版本；来源升版后只在库里提示「源用例已更新」，不自动同步
#   5. 用例库名称全局唯一（禅道 caselib/create 的 check('name','unique',"deleted='0'")）
source "$(dirname "$0")/_mysql.sh"

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
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }
# 建产品用例（product=1），回显编号；params: <title> <module>
mkcase() {
  curl -s -X POST "${H[@]}" "$BASE/zentao/testcase/create" \
    -d "{\"product\":1,\"title\":\"$1\",\"type\":\"feature\",\"pri\":3,\"module\":${2:-0},\"steps\":[{\"desc\":\"打开页面\",\"expect\":\"显示正常\"},{\"desc\":\"点击提交\",\"expect\":\"提交成功\"}]}" | d
}

echo "===== 用例库（caselib）模块测试 ====="
TS=$(date +%s)

# 兜底清理：上一次 UI 检查（ui-check/caselib.mjs）若被中断（例如断网），会留下一个
# 「UI用例库-<ts>」，它会让下面「库列表里只有 1 个 library」的断言变红。UI 检查自己也会清，
# 这里只是保证接口回归在任何残留情况下都能跑起来。
STALE=$(curl -s "${H[@]}" "$BASE/zentao/caselib/list" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data') or []
print(' '.join(str(x['id']) for x in d if str(x.get('name','')).startswith('UI用例库-')))" 2>/dev/null)
for _id in $STALE; do curl -s -X DELETE "${H[@]}" "$BASE/zentao/caselib/delete?id=$_id" >/dev/null; done
[ -n "$STALE" ] && echo "  （已清理残留的 UI 用例库：$STALE）"

echo "--- 1. 演示用例库 + 共用表区分 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/caselib/list")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
m={x['id']:x for x in d}
lib=m.get(95301)
print(len([x for x in d if x['type']=='library']), lib['name'], lib['type'], lib['caseCount'] if lib else 'NA')")
[ "${line}" = "1 公共用例库 library 2" ] && { PASS=$((PASS+1)); echo "  ✅ 演示用例库 95301：type=library / 库内 2 条用例"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示用例库 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/get?id=95301" | field "d['name'], d['caseCount'], d['type']")
[ "${line}" = "公共用例库 2 library" ] && { PASS=$((PASS+1)); echo "  ✅ 用例库详情：名称 + 用例数 + type=library"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用例库详情 实际=${line}"; }

# 用例库绝不能出现在用例集列表里（共用 zt_testsuite，必须带 type 过滤）
line=$(curl -s "${H[@]}" "$BASE/zentao/testreport/suite/page?pageNo=1&pageSize=100" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['list']
print(any(r['id']==95301 for r in rows), all(r['type']!='library' for r in rows))")
[ "${line}" = "False True" ] && { PASS=$((PASS+1)); echo "  ✅ 用例集列表里没有用例库（type='library' 被排除）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用例集列表混入库 实际=${line}"; }

check "拿用例集编号当用例库用 → 拒绝" 1020024003 \
  "$(curl -s "${H[@]}" "$BASE/zentao/caselib/get?id=95101")" "不是用例库"
check "拿用例库编号调用例集接口 → 拒绝" 1020018000 \
  "$(curl -s "${H[@]}" "$BASE/zentao/testreport/suite/get?id=95301")" "用例集不存在"

echo "--- 2. 用例库 CRUD ---"
LIB=$(curl -s -X POST "${H[@]}" "$BASE/zentao/caselib/create" \
  -d "{\"name\":\"接口测试库-$TS\",\"desc\":\"自动化建的库\",\"order\":5}" | d)
[ -n "$LIB" ] && { PASS=$((PASS+1)); echo "  ✅ 新建用例库成功（编号 $LIB）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建用例库失败"; }
check "重名 → 拒绝" 1020024001 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/caselib/create" -d "{\"name\":\"接口测试库-$TS\"}")" "名称已存在"
check "与既有用例库重名 → 拒绝" 1020024001 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/caselib/create" -d '{"name":"公共用例库"}')" "名称已存在"
check "空名称 → 拒绝（校验）" 400 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/caselib/create" -d '{"name":""}')" ""

line=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/caselib/update" \
  -d "{\"id\":$LIB,\"name\":\"接口测试库-$TS-改\",\"desc\":\"改过了\",\"order\":9}" >/dev/null; \
  curl -s "${H[@]}" "$BASE/zentao/caselib/get?id=$LIB" | field "d['name'], d['desc'], d['order']")
[ "${line}" = "接口测试库-$TS-改 改过了 9" ] && { PASS=$((PASS+1)); echo "  ✅ 修改用例库：名称/描述/排序都生效"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修改用例库 实际=${line}"; }

echo "--- 3. 库内用例 ---"
line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/case-page?libId=95301&pageNo=1&pageSize=10" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
rows=d['list']
print(d['total'], [r['id'] for r in rows], all(r['lib']==95301 and r['product']==0 for r in rows))")
[ "${line}" = "2 [95402, 95401] True" ] && { PASS=$((PASS+1)); echo "  ✅ 库内用例列表：2 条 / 都带 lib=95301、product=0"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 库内用例列表 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/case-get?id=95401" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['lib'], d['product'], len(d['steps']), d['status'], d['version'])")
[ "${line}" = "95301 0 2 normal 1" ] && { PASS=$((PASS+1)); echo "  ✅ 库内用例详情：带 2 步、version=1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 库内用例详情 实际=${line}"; }

# 产品用例列表不能混入库用例（共用 zt_case，必须带 lib 过滤）
line=$(curl -s "${H[@]}" "$BASE/zentao/testcase/page?pageNo=1&pageSize=200&product=1" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['list']
print(any(r['id'] in (95401,95402) for r in rows), all((r.get('lib') or 0)==0 for r in rows))")
[ "${line}" = "False True" ] && { PASS=$((PASS+1)); echo "  ✅ 产品用例列表里没有库用例（lib=0 过滤）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 产品用例列表混入库用例 实际=${line}"; }

# 不带 product 的用例列表也要排除库用例（否则「全部」会把库里的用例都倒出来）
line=$(curl -s "${H[@]}" "$BASE/zentao/testcase/page?pageNo=1&pageSize=200" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['list']
print(all((r.get('lib') or 0)==0 for r in rows))")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 不带 product 的用例列表同样排除库用例"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 不带 product 的列表混入库用例 实际=${line}"; }

LC=$(curl -s -X POST "${H[@]}" "$BASE/zentao/caselib/create-case?libId=95301" \
  -d "{\"title\":\"库内新建-$TS\",\"type\":\"feature\",\"pri\":2,\"module\":95501,\"story\":1,\"steps\":[{\"desc\":\"A\",\"expect\":\"a\"},{\"desc\":\"B\",\"expect\":\"b\"},{\"desc\":\"C\",\"expect\":\"c\"}]}" | d)
line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/case-get?id=$LC" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['product'], d['lib'], d['module'], d['story'], d['branch'], d['status'], d['version'], len(d['steps']))")
# story 被强制清 0（库用例不关联需求）、branch 归 0、步骤 3 条
[ "${line}" = "0 95301 95501 0 0 normal 1 3" ] && { PASS=$((PASS+1)); echo "  ✅ 库内建用例：product=0 / lib=95301 / 需求被清 0 / 3 步"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 库内建用例 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/get?id=95301" | field "d['caseCount']")
[ "${line}" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 库内用例数变成 3"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 库内用例数 实际=${line}"; }

# 库内用例同样走 testcase 的「步骤变了才升版本」规则
curl -s -X PUT "${H[@]}" "$BASE/zentao/testcase/update" \
  -d "{\"id\":$LC,\"product\":0,\"title\":\"库内新建-$TS\",\"type\":\"feature\",\"pri\":2,\"module\":95501,\"steps\":[{\"desc\":\"A\",\"expect\":\"a\"},{\"desc\":\"B2\",\"expect\":\"b\"},{\"desc\":\"C\",\"expect\":\"c\"}]}" >/dev/null
line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/case-get?id=$LC" | field "d['version'], d['status']")
[ "${line}" = "2 wait" ] && { PASS=$((PASS+1)); echo "  ✅ 库内用例改步骤 → version+1 且打回「待评审」（复用 testcase 规则）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 库内用例升版 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/case-page?libId=95301&pageNo=1&pageSize=10&status=wait" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['list']
print(sorted(r['id'] for r in rows), all(r['status']=='wait' for r in rows))")
[ "${line}" = "[95402, $LC] True" ] && { PASS=$((PASS+1)); echo "  ✅ 库内用例按状态过滤（待评审 2 条）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 库内用例状态过滤 实际=${line}"; }

echo "--- 4. 产品用例 → 用例库（importToLib） ---"
SRC=$(mkcase "导入源用例-$TS" 93301)
SRC2=$(mkcase "导入源用例2-$TS" 93301)
line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/can-import-case-page?libId=95301&product=1&title=$TS&pageNo=1&pageSize=10" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['list']
print([r['id'] for r in rows])")
[ "${line}" = "[$SRC2, $SRC]" ] && { PASS=$((PASS+1)); echo "  ✅ 可导入清单：我的 2 条产品用例都在"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 可导入清单 实际=${line}"; }

# 导入接口返回的是**编号列表**（一次可以导入多条）
IMP=$(curl -s -X POST "${H[@]}" "$BASE/zentao/caselib/import-to-lib?libId=95301" -d "[$SRC]" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0] if d else '')")
line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/case-get?id=$IMP" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['product'], d['lib'], d['fromCaseID'], d['fromCaseVersion'], d['sourceChanged'], d['version'], len(d['steps']), d['title'])")
[ "${line}" = "0 95301 $SRC 1 False 1 2 导入源用例-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 导入成功：复制用例 + 2 步，记下来源与来源版本 1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 导入结果 实际=${line}"; }

CASE_MODULE=$(curl -s "${H[@]}" "$BASE/zentao/caselib/case-get?id=$IMP" | field "d['module']")
# 模块树的 from 字段没有出到接口上，所以直接查库
# （禅道用 zt_module.from 记住「这个库模块是从哪个产品模块导过来的」，用于幂等复用）
MOD_FROM=$(mysql_query "SELECT \`from\` FROM \`ruoyi-vue-pro\`.zt_module WHERE id = $CASE_MODULE;")
MOD_NAME=$(mysql_query "SELECT name FROM \`ruoyi-vue-pro\`.zt_module WHERE id = $CASE_MODULE;")
# path 的规则是「逗号包起来且包含自己」，id 是自增的不写死，用 (path = ,id,) 判断
MOD_TREE=$(mysql_query "SELECT CONCAT(root, ':', type, ':', (path = CONCAT(',', id, ',')), ':', grade, ':', parent) FROM \`ruoyi-vue-pro\`.zt_module WHERE id = $CASE_MODULE;")
MC1=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_module WHERE root = 95301 AND type = 'caselib' AND deleted = 0;")
[ "$CASE_MODULE" != "0" ] && [ "$MOD_FROM" = "93301" ] && [ "$MOD_NAME" = "登录模块" ] \
  && [ "$MOD_TREE" = "95301:caselib:1:1:0" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 来源模块被复制进库的模块树（root=95301 / type=caselib / from=93301 / grade=1）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 模块映射 实际 module=$CASE_MODULE from=$MOD_FROM name=$MOD_NAME tree=$MOD_TREE"; }

# 再导入一条同模块的用例：模块应当复用（同 from 不重复建）
IMP2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/caselib/import-to-lib?libId=95301" -d "[$SRC2]" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0] if d else '')")
MC2=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_module WHERE root = 95301 AND type = 'caselib' AND deleted = 0;")
[ "$MC2" = "$MC1" ] && { PASS=$((PASS+1)); echo "  ✅ 同来源模块第二次导入时复用（模块数 $MC1 不增加）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 模块复用 实际 模块数=$MC2 之前=$MC1"; }

check "重复导入同一条用例 → 拒绝" 1020024006 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/caselib/import-to-lib?libId=95301" -d "[$SRC]")" "已经导入过"
check "把库内用例再导入库 → 拒绝" 1020024005 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/caselib/import-to-lib?libId=95301" -d "[$IMP]")" "不能再导入"

# 两条都已经导入过了 → 「可导入清单」应当空了（判重就是靠 lib + fromCaseID）
line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/can-import-case-page?libId=95301&product=1&title=$TS&pageNo=1&pageSize=10" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['list']
print([r['id'] for r in rows])")
[ "${line}" = "[]" ] && { PASS=$((PASS+1)); echo "  ✅ 已导入的用例从「可导入清单」里消失（两条都导过 → 空）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 可导入清单判重 实际=${line}"; }

# 来源用例升版 → 库里那条要提示「源用例已更新」，但不自动同步
curl -s -X PUT "${H[@]}" "$BASE/zentao/testcase/update" \
  -d "{\"id\":$SRC,\"product\":1,\"title\":\"导入源用例-$TS\",\"type\":\"feature\",\"pri\":3,\"module\":93301,\"steps\":[{\"desc\":\"打开页面\",\"expect\":\"显示正常\"},{\"desc\":\"点击提交\",\"expect\":\"提交成功\"},{\"desc\":\"校验结果\",\"expect\":\"结果正确\"}]}" >/dev/null
line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/case-get?id=$IMP" | field "d['sourceChanged'], d['fromCaseVersion'], d['version'], len(d['steps'])")
[ "${line}" = "True 1 1 2" ] && { PASS=$((PASS+1)); echo "  ✅ 来源升版 → 库里那条标记「源用例已更新」，自身版本/步骤不变（不自动同步）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 源用例已更新标记 实际=${line}"; }

check "库内还有用例时不能删库" 1020024002 \
  "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/caselib/delete?id=95301")" "还有"
check "空库可以删（新建的那个）" 0 \
  "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/caselib/delete?id=$LIB")" ""

echo "--- 5. 清理并确认演示数据完好 ---"
for id in "$IMP" "$IMP2" "$LC" "$SRC" "$SRC2"; do
  [ -n "$id" ] && curl -s -X DELETE "${H[@]}" "$BASE/zentao/testcase/delete?id=$id" >/dev/null
done
line=$(curl -s "${H[@]}" "$BASE/zentao/caselib/get?id=95301" | field "d['caseCount']")
[ "${line}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 清理完成：演示库回到 2 条用例，测试库已删"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 清理后用例数 实际=${line}"; }

for id in "$IMP" "$IMP2" "$LC" "$SRC" "$SRC2"; do
  [ -n "$id" ] && curl -s -X DELETE "${H[@]}" "$BASE/zentao/testcase/delete?id=$id" >/dev/null
done
curl -s -X DELETE "${H[@]}" "$BASE/zentao/caselib/delete?id=$LIB" >/dev/null

echo "======================================================"
echo "  用例库模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

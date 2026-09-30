#!/bin/bash
# 分支/平台（branch）模块接口测试
#
# 禅道语义：zt_branch 是产品维度的分支表；id=0 是虚拟主干（不落库）；
# 产品类型 normal 不支持分支，branch 叫「分支」，platform 叫「平台」。
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
    PASS=$((PASS+1)); printf '  ✅ %-44s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-44s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }

echo "===== 分支/平台模块测试 ====="

echo "--- 0. 准备：一个平台型产品 + 一个普通产品 ---"
TS=$(date +%s)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"BrPlatform-$TS\",\"code\":\"BP$TS\",\"type\":\"platform\",\"PO\":\"admin\"}")
check "创建 platform 类型产品" 0 "$R"
PROD=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"BrNormal-$TS\",\"code\":\"BN$TS\",\"type\":\"normal\",\"PO\":\"admin\"}")
check "创建 normal 类型产品" 0 "$R"
PRODN=$(echo "$R" | d)
echo "    platform 产品=$PROD  normal 产品=$PRODN"

echo "--- 1. 创建校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PRODN,\"name\":\"不该建\"}")
check "normal 产品建分支→拒绝" 1020007003 "$R" "未启用分支"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":99999999,\"name\":\"无主分支\"}")
check "产品不存在" 1020007002 "$R" "所属产品不存在"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"name\":\"没产品\"}")
check "缺少产品→参数校验" 400 "$R" "所属产品不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PROD,\"name\":\"\"}")
check "名称为空→参数校验" 400 "$R" "名称不能为空"

echo "--- 2. 正常创建 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PROD,\"name\":\"政务版-$TS\",\"desc\":\"政务平台\"}")
check "创建分支" 0 "$R"
B1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PROD,\"name\":\"企业版-$TS\"}")
B2=$(echo "$R" | d)
echo "    分支1=$B1 分支2=$B2"

R=$(curl -s "${H[@]}" "$BASE/zentao/branch/get?id=$B1")
o=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("order"))')
s=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("status"))')
lbl=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("branchLabel"))')
pn=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("productName"))')
[ "$s" = "active" ] && [ "$lbl" = "平台" ] && [ "$pn" = "BrPlatform-$TS" ] && [ "$o" = "1" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 建后默认值 order=$o status=$s label=$lbl productName=$pn"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建后默认值 order=$o status=$s label=$lbl productName=$pn"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PROD,\"name\":\"政务版-$TS\"}")
check "同名分支→拒绝" 1020007001 "$R" "名称已存在"

echo "--- 3. 虚拟主干（id=0） ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/branch/get?id=0")
nm=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("name"))')
mb=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("mainBranch"))')
[ "$nm" = "主干" ] && [ "$mb" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 主干 get：name=$nm mainBranch=$mb"; } || { FAIL=$((FAIL+1)); echo "  ❌ 主干 get：name=$nm mainBranch=$mb"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/branch/close?id=0")
check "关闭主干→拒绝" 1020007005 "$R" "主干"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/branch/delete?id=0")
check "删除主干→拒绝" 1020007005 "$R" "主干"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/branch/update" -d '{"id":0,"product":1,"name":"改主干"}')
check "修改主干→拒绝" 1020007005 "$R" "主干"

echo "--- 4. 列表与统计 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/branch/page?pageNo=1&pageSize=10&product=$PROD")
t=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
first=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"]["list"][0];print(d.get("id"),d.get("name"),d.get("mainBranch"))')
[ "$t" = "3" ] && [ "$first" = "0 主干 True" ] && { PASS=$((PASS+1)); echo "  ✅ 分页含主干 total=$t 首行=[$first]"; } || { FAIL=$((FAIL+1)); echo "  ❌ 分页含主干 total=$t 首行=[$first]"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/branch/page?pageNo=2&pageSize=10&product=$PROD")
t=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]["list"]))')
[ "$t" = "3" ] && [ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 第二页不重复补主干 total=$t n=$n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 第二页 total=$t n=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/branch/page?pageNo=1&pageSize=10&product=$PROD&status=closed")
t=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$t" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ status=closed 时不补主干 total=$t"; } || { FAIL=$((FAIL+1)); echo "  ❌ status=closed total=$t"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/branch/list-by-product?product=$PROD")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
R2=$(curl -s "${H[@]}" "$BASE/zentao/branch/list-by-product?product=$PROD&status=closed")
n2=$(echo "$R2" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "3" ] && [ "$n2" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ list-by-product 全部=$n / closed=$n2"; } || { FAIL=$((FAIL+1)); echo "  ❌ list-by-product 全部=$n / closed=$n2"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/branch/count-by-product?product=$PROD")
c=$(echo "$R" | d)
[ "$c" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 分支数量（不含主干）=$c"; } || { FAIL=$((FAIL+1)); echo "  ❌ 分支数量 期望2 实际=$c"; }

echo "--- 5. 状态机与默认分支 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/branch/close?id=$B2")
check "关闭分支" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/branch/get?id=$B2")
s=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("status"))')
cd=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("closedDate"))')
[ "$s" = "closed" ] && [ -n "$cd" ] && [ "$cd" != "None" ] && { PASS=$((PASS+1)); echo "  ✅ 关闭后 status=$s closedDate=$cd"; } || { FAIL=$((FAIL+1)); echo "  ❌ 关闭后 status=$s closedDate=$cd"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/branch/close?id=$B2")
check "重复关闭→拒绝" 1020007006 "$R" "已经是关闭状态"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/branch/activate?id=$B2")
check "激活分支" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/branch/get?id=$B2")
s=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("status"))')
[ "$s" = "active" ] && { PASS=$((PASS+1)); echo "  ✅ 激活后 status=$s"; } || { FAIL=$((FAIL+1)); echo "  ❌ 激活后 status=$s"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/branch/set-default?product=$PROD&branchId=$B1")
check "设为默认分支" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/branch/list-by-product?product=$PROD")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(" ".join(str(x["id"]) + ":" + str(x.get("defaultFlag")) for x in d))')
[ "$line" = "0:0 $B1:1 $B2:0" ] && { PASS=$((PASS+1)); echo "  ✅ 默认标记唯一：$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 默认标记 实际=[$line]"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/branch/set-default?product=$PROD&branchId=0")
check "默认还原到主干" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/branch/list-by-product?product=$PROD")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(" ".join(str(x["id"]) + ":" + str(x.get("defaultFlag")) for x in d))')
[ "$line" = "0:0 $B1:0 $B2:0" ] && { PASS=$((PASS+1)); echo "  ✅ 主干默认后全部清零：$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 主干默认 实际=[$line]"; }

echo "--- 6. 修改 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/branch/update" -d "{\"id\":$B1,\"product\":$PROD,\"name\":\"政务版改名-$TS\",\"desc\":\"改过了\"}")
check "修改分支" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/branch/get?id=$B1")
nm=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("name"))')
[ "$nm" = "政务版改名-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 修改生效 name=$nm"; } || { FAIL=$((FAIL+1)); echo "  ❌ 修改未生效 name=$nm"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/branch/update" -d "{\"id\":$B1,\"product\":$PROD,\"name\":\"企业版-$TS\"}")
check "改名撞其他分支→拒绝" 1020007001 "$R" "名称已存在"

echo "--- 7. 删除保护 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PROD,\"title\":\"挂分支需求-$TS\",\"pri\":3,\"category\":\"feature\",\"branch\":$B1}")
check "建一条挂在该分支上的需求" 0 "$R"
STORY=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/branch/delete?id=$B1")
check "分支下有需求→拒绝删除" 1020007004 "$R" "需求"

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=$STORY")
check "删除需求" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/branch/delete?id=$B1")
check "清空后可以删除" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/branch/get?id=$B1")
check "删除后再查→不存在" 1020007000 "$R" "不存在"

echo "--- 8. 清理 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/branch/delete?id=$B2")
check "删除分支2" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PROD")
check "删除平台产品" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PRODN")
check "删除普通产品" 0 "$R"

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

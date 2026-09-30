#!/bin/bash
# 模块树（module）模块接口测试
#
# 禅道语义：zt_module 是通用树表，(root, type, branch) 定位一棵树；
#   path 逗号格式（,5,6,）、grade 一级为 1、order = 同级 max + 10；
#   删除模块连带子孙，并把挂在其上的需求/任务/缺陷改挂到父模块。
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
    PASS=$((PASS+1)); printf '  ✅ %-42s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-42s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }

echo "===== 模块树模块测试 ====="
TS=$(date +%s)

echo "--- 0. 准备：平台产品 + 执行 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"MdProd-$TS\",\"code\":\"MD$TS\",\"type\":\"platform\",\"PO\":\"admin\"}")
check "创建产品" 0 "$R"
PROD=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" -d "{\"name\":\"MdProj-$TS\",\"model\":\"scrum\",\"pri\":3,\"PM\":\"admin\",\"team\":\"admin\"}")
check "创建项目" 0 "$R"
PROJ=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"project\":$PROJ,\"name\":\"MdExec-$TS\",\"type\":\"sprint\",\"pri\":3}")
check "创建执行" 0 "$R"
EXEC=$(echo "$R" | d)
echo "    产品=$PROD 项目=$PROJ 执行=$EXEC"

echo "--- 1. 创建校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"xxxx\",\"name\":\"非法类型\"}")
check "树类型非法" 1020008002 "$R" "模块树类型不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"type\":\"story\",\"name\":\"没有root\"}")
check "缺少 root→参数校验" 400 "$R" "所属根对象不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"   \"}")
check "名称为空白→参数校验" 400 "$R" "模块名称不能为空"

echo "--- 2. 创建与 path/grade 自动维护 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"订单-$TS\"}")
check "创建一级模块" 0 "$R"
M1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/module/get?id=$M1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["path"], d["grade"], d["order"])')
[ "$line" = ",$M1, 1 10" ] && { PASS=$((PASS+1)); echo "  ✅ 一级模块 path/grade/order = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 一级模块 期望=,$M1, 1 10 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"订单-$TS\"}")
check "同级重名→拒绝" 1020008001 "$R" "同名模块"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"一级第二个-$TS\"}")
M2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/module/get?id=$M2")
o2=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["order"])')
[ "$o2" = "20" ] && { PASS=$((PASS+1)); echo "  ✅ 第二个一级模块 order=${o2}（max+10）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 第二个一级模块 order 期望20 实际=$o2"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"退款-$TS\",\"parent\":$M1,\"owner\":\"admin\"}")
M3=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/module/get?id=$M3")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["path"], d["grade"])')
[ "$line" = ",$M1,$M3, 2" ] && { PASS=$((PASS+1)); echo "  ✅ 子模块 path/grade = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 子模块 期望=,$M1,$M3, 2 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"bug\",\"name\":\"跨树-$TS\",\"parent\":$M1}")
check "parent 与当前模块不在同一棵树→拒绝" 1020008004 "$R" "不在同一棵树"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"分支模块-$TS\",\"branch\":1}")
check "分支模块创建" 0 "$R"
M4=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"跨分支-$TS\",\"branch\":2,\"parent\":$M4}")
check "parent 与当前模块不在同一分支→拒绝" 1020008004 "$R" "不在同一分支"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"主干模块-$TS\",\"branch\":0,\"parent\":$M4}")
check "主干模块挂到分支模块下→拒绝" 1020008004 "$R" "不在同一分支"

echo "--- 3. 树与列表 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/module/tree?root=$PROD&type=story")
tree=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]

def find(nodes, name):
    for n in nodes:
        if n["name"] == name: return n
        hit = find(n.get("children") or [], name)
        if hit: return hit
    return None

n1 = find(d, "订单-'$TS'")
print(len(d), n1["childCount"], n1["children"][0]["name"] if n1["children"] else "-")')
[ "$tree" = "3 1 退款-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 树接口：一级数量/子模块数/子模块名 = $tree"; } || { FAIL=$((FAIL+1)); echo "  ❌ 树接口 期望=3 1 退款-$TS 实际=$tree"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/module/list?root=$PROD&type=story&branch=0")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 主干列表（不含分支模块）= $n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 主干列表 期望3 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/module/list?root=$PROD&type=story&branch=1")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 分支 1 列表 = $n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 分支 1 列表 期望1 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/module/list?root=$PROD&type=story")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "4" ] && { PASS=$((PASS+1)); echo "  ✅ 不限分支列表 = $n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 不限分支列表 期望4 实际=$n"; }

echo "--- 4. 移动模块 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/module/update" -d "{\"id\":$M3,\"root\":$PROD,\"type\":\"story\",\"name\":\"退款改名-$TS\",\"parent\":$M2}")
check "改名 + 移动到另一个父模块" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/module/get?id=$M3")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["name"], d["path"], d["grade"])')
[ "$line" = "退款改名-$TS ,$M2,$M3, 2" ] && { PASS=$((PASS+1)); echo "  ✅ 移动后 path/grade 重算 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 移动后 期望=退款改名-$TS ,$M2,$M3, 2 实际=$line"; }

# 在 M2 下再建一个子模块，用「把 M2 移到自己的子模块下」来测成环拦截
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"发货-$TS\",\"parent\":$M2}")
check "在 M2 下建子模块" 0 "$R"
M5=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/module/update" -d "{\"id\":$M2,\"root\":$PROD,\"type\":\"story\",\"name\":\"一级第二个-$TS\",\"parent\":$M5}")
check "移到自己子孙下→拒绝" 1020008005 "$R" "自己或自己的子模块"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/module/update" -d "{\"id\":$M1,\"root\":$PROD,\"type\":\"story\",\"name\":\"订单-$TS\",\"parent\":$M1}")
check "移到自己下→拒绝" 1020008005 "$R" "自己或自己的子模块"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/module/update" -d "{\"id\":$M1,\"root\":$PROD,\"type\":\"bug\",\"name\":\"订单-$TS\"}")
check "改树类型→拒绝" 1020008004 "$R" "不允许修改"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/module/update" -d "{\"id\":$M1,\"root\":$PROD,\"type\":\"story\",\"name\":\"一级第二个-$TS\"}")
check "改名撞同级→拒绝" 1020008001 "$R" "同名模块"

echo "--- 5. 排序 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/module/update-order" -d "{\"items\":[{\"id\":$M2,\"order\":5},{\"id\":$M1,\"order\":50}]}")
check "批量排序" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/module/get?id=$M1")
o1=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["order"])')
R=$(curl -s "${H[@]}" "$BASE/zentao/module/get?id=$M2")
o2b=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["order"])')
[ "$o1" = "50" ] && [ "$o2b" = "5" ] && { PASS=$((PASS+1)); echo "  ✅ 排序值已写入 M1=$o1 M2=$o2b"; } || { FAIL=$((FAIL+1)); echo "  ❌ 排序值 M1=$o1(期望50) M2=$o2b(期望5)"; }

# 树接口里一级模块按 order 升序：M2(5) 应在 M1(50) 之前
R=$(curl -s "${H[@]}" "$BASE/zentao/module/tree?root=$PROD&type=story&branch=0")
order=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(" ".join(str(x["id"]) for x in d))')
[ "$order" = "$M2 $M1" ] && { PASS=$((PASS+1)); echo "  ✅ 树接口一级顺序（按 order，仅主干）= $order"; } || { FAIL=$((FAIL+1)); echo "  ❌ 树接口一级顺序 期望=$M2 $M1 实际=$order"; }

echo "--- 6. 删除模块：改挂业务数据 ---"
# 需求挂到 M3（M2 的子模块）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PROD,\"title\":\"挂模块需求-$TS\",\"pri\":3,\"category\":\"feature\",\"module\":$M3}")
check "建需求并挂到子模块" 0 "$R"
STORY=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/module/delete?id=$M2")
check "删除含子模块的模块" 0 "$R"

R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$STORY")
mod=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("module"))')
[ "$mod" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 需求被改挂到父模块 module=$mod"; } || { FAIL=$((FAIL+1)); echo "  ❌ 需求改挂 期望0 实际=$mod"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/module/get?id=$M3")
check "子模块已删除" 1020008000 "$R" "模块不存在"
R=$(curl -s "${H[@]}" "$BASE/zentao/module/get?id=$M2")
check "父模块已删除" 1020008000 "$R" "模块不存在"

R=$(curl -s "${H[@]}" "$BASE/zentao/module/list?root=$PROD&type=story&branch=0")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 删除后主干模块数 = ${n}（只剩 M1）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 删除后 期望1 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/action/list?objectType=module&objectID=$M2")
hit=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print("改挂" if any("改挂" in (a.get("comment") or "") for a in d) else "无")')
[ "$hit" = "改挂" ] && { PASS=$((PASS+1)); echo "  ✅ 删除动作日志记录了数据改挂"; } || { FAIL=$((FAIL+1)); echo "  ❌ 删除动作日志未记录改挂"; }

echo "--- 7. 任务树与产品线 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$EXEC,\"type\":\"task\",\"name\":\"任务模块-$TS\"}")
check "任务树（root=执行）" 0 "$R"
MT=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/module/get?id=$MT")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["type"], d["root"], d["path"])')
[ "$line" = "task $EXEC ,$MT," ] && { PASS=$((PASS+1)); echo "  ✅ 任务模块 type/root/path = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 任务模块 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":0,\"type\":\"line\",\"name\":\"金融产品线-$TS\"}")
check "产品线（type=line, root=0）" 0 "$R"
ML=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/module/tree?root=0&type=line")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" -ge "1" ] && { PASS=$((PASS+1)); echo "  ✅ 产品线树可查，一级数量=$n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 产品线树 数量=$n"; }

echo "--- 8. 列表按模块过滤（含子树） ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"父模块-$TS\"}")
MP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/module/create" -d "{\"root\":$PROD,\"type\":\"story\",\"name\":\"子模块-$TS\",\"parent\":$MP}")
MC=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PROD,\"title\":\"子树过滤需求-$TS\",\"pri\":3,\"category\":\"feature\",\"module\":$MC}")
STORY2=$(echo "$R" | d)
check "准备：父/子模块 + 挂子模块的需求" 0 "$R"

R=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&module=$MC")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 按子模块过滤命中 $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按子模块过滤 期望1 实际=$n"; }

# 关键：按「父模块」过滤时应连带查出子模块下的需求（禅道行为）
R=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&module=$MP")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 按父模块过滤连带查出子树数据 $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按父模块过滤 期望1 实际=$n"; }

# 不存在的模块 → 展开为空列表，不应报错也不应查出全部
R=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&module=99999999")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 不存在的模块 → 0 条（不会退化成全量）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 不存在的模块 期望0 实际=$n"; }

# 分支过滤
R=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&product=$PROD&branch=0")
n0=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
R=$(curl -s "${H[@]}" "$BASE/zentao/story/page?pageNo=1&pageSize=10&product=$PROD&branch=9")
n9=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n0" = "2" ] && [ "$n9" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 分支过滤 branch=0 命中 $n0 条, branch=9 命中 $n9 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 分支过滤 期望2/0 实际=$n0/$n9"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=$STORY2")
check "删除子树过滤需求" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/module/delete?id=$MP")
check "删除父模块（连带子模块）" 0 "$R"

echo "--- 9. 清理 ---"
for id in $M1 $M4 $MT $ML; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/module/delete?id=$id")
  check "删除模块 $id" 0 "$R"
done
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=$STORY")
check "删除需求" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PROD")
check "删除产品" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=$EXEC")
check "删除执行" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=$PROJ")
check "删除项目" 0 "$R"

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

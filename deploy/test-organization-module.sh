#!/bin/bash
# 组织与权限（视图）接口测试
#
# 这一块**不迁移禅道表**：zt_user/zt_dept/zt_group/zt_grouppriv 映射到 yudao 的
# system_users/system_dept/system_role/system_role_menu。测试重点是把 yudao 的权限
# 正确翻译回禅道的 (模块, 方法) 视角，尤其是「超级管理员是硬编码放行」这个坑。
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

echo "===== 组织与权限（视图）测试 ====="

echo "--- 1. 用户列表（zt_user 视角） ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/organization/user-list")
check "用户列表" 0 "$R"
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
u=[x for x in d if x["account"]=="admin"][0]
print(u["account"], u["realname"], u["deptName"], u["roleNames"], u["status"])')
[[ "$line" == admin*"超级管理员"* ]] && { PASS=$((PASS+1)); echo "  ✅ admin 的用户视图 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ admin 用户视图 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/organization/user-list?keyword=admin")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" -ge "1" ] && { PASS=$((PASS+1)); echo "  ✅ 按账号模糊查询 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 关键字查询 实际=$n"; }

echo "--- 2. 超管权限展开（yudao 的 super_admin 不写权限表） ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/organization/user-permissions?userId=1")
# 不硬编码模块数/权限数：每加一个模块都要改断言的话，测试就变成「改常量」而非「验证行为」。
# 这里验证真正的不变量：story 的模块名与方法是禅道的 (模块, 方法) 视角，且条数与明细自洽。
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
story=[x for x in d if x["module"]=="story"][0]
assert story["moduleName"] == "需求", story
assert story["methods"] == ["create","delete","query","update"], story
assert all(x["count"] == len(x["methods"]) for x in d), "count 与 methods 不一致"
assert len({x["module"] for x in d}) == len(d), "模块有重复"
# 不写死「15 个模块 / 56 条权限」：每加一个禅道模块都要改常量的话，
# 这个测试就退化成「改数字」而不是「验行为」。这里只断言不变量 + 关键模块存在。
required = {"story", "task", "bug", "product", "project", "execution",
            "branch", "module", "plan", "build", "release",
            "projectstory", "stage", "organization", "file", "doc"}
missing = required - {x["module"] for x in d}
assert not missing, "超管展开里缺少模块：%s" % missing
print(story["moduleName"], ",".join(story["methods"]), len(d), sum(x["count"] for x in d), "ok")')
case "$line" in
  "需求 create,delete,query,update "*) PASS=$((PASS+1)); echo "  ✅ 超管权限按模块聚合（自洽且模块齐全）= $line";;
  *) FAIL=$((FAIL+1)); echo "  ❌ 超管权限 实际=$line";;
esac

# 附件是公共能力，权限点也必须出现在超管展开里（否则非超管角色根本勾不到）
R=$(curl -s "${H[@]}" "$BASE/zentao/organization/user-permissions?userId=1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
f=[x for x in d if x["module"]=="file"]
doc=[x for x in d if x["module"]=="doc"]
print("yes" if f and f[0]["methods"]==["create","delete","query","update"]
      and doc and doc[0]["methods"]==["create","delete","query","update"] else "no: %s %s" % (f, doc))')
[ "$line" = "yes" ] && { PASS=$((PASS+1)); echo "  ✅ 超管展开含附件+文档权限 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 超管展开缺附件/文档权限 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/organization/user-list?keyword=admin")
cnt=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
u=[x for x in d if x["account"]=="admin"][0]
mods=u["modules"]
# modules 是模块名列表（去重），permissionCount 是权限串总数。
# 这里只验「两者互相自洽」和「该有的模块都在」，不写死任何数字。
assert {"file", "doc", "story", "task", "bug"} <= set(mods), mods
assert u["permissionCount"] >= len(mods), (u["permissionCount"], len(mods))
print(u["permissionCount"], len(mods), "consistent")')
[[ "$cnt" == *"consistent"* ]] && { PASS=$((PASS+1)); echo "  ✅ 列表里的权限条数/模块数 = $cnt"; } || { FAIL=$((FAIL+1)); echo "  ❌ 列表权限条数 实际=$cnt"; }

echo "--- 3. 非超管用户（只有 common 角色） ---"
source "$(dirname "$0")/_mysql.sh"
UID2=$(mysql_query "SELECT u.id FROM \`ruoyi-vue-pro\`.system_users u WHERE u.deleted=0 AND u.username='test' AND NOT EXISTS (SELECT 1 FROM \`ruoyi-vue-pro\`.system_user_role ur JOIN \`ruoyi-vue-pro\`.system_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND ur.deleted=0 AND r.code='super_admin') LIMIT 1;")
if [ -n "$UID2" ]; then
  R=$(curl -s "${H[@]}" "$BASE/zentao/organization/user-permissions?userId=$UID2")
  n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
  [ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 非超管用户（id=${UID2}）没有禅道权限 = $n 个模块"; } || { FAIL=$((FAIL+1)); echo "  ❌ 非超管用户权限 期望0 实际=$n"; }
  R=$(curl -s "${H[@]}" "$BASE/zentao/organization/user-list?keyword=test")
  line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
u=[x for x in d if x['id']==$UID2][0]
print(u['permissionCount'], len(u['modules']))")
  [ "$line" = "0 0" ] && { PASS=$((PASS+1)); echo "  ✅ 列表里该用户权限条数/模块数 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 列表里非超管 期望='0 0' 实际=$line"; }
else
  echo "  （跳过：没有找到非超管用户）"
fi

echo "--- 4. 权限包（角色 → zt_group 视角） ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/organization/role-list?withPermissions=true")
check "权限包列表（带权限明细）" 0 "$R"
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
sa=[x for x in d if x["code"]=="super_admin"][0]
cm=[x for x in d if x["code"]=="common"][0]
# zentaoPermissionCount 必须等于展开后的明细之和（超管是按「全部禅道权限」展开的，不查 system_role_menu）
assert sa["zentaoPermissionCount"] == sum(x["count"] for x in sa["permissions"]), sa["zentaoPermissionCount"]
assert sa["zentaoPermissionCount"] > 0 and len(sa["permissions"]) > 0 and cm["zentaoPermissionCount"] == 0
print(sa["zentaoPermissionCount"], len(sa["permissions"]), cm["zentaoPermissionCount"], "consistent")')
[[ "$line" == *" 0 consistent" ]] && { PASS=$((PASS+1)); echo "  ✅ 超管权限包展开 / common 无禅道权限 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 权限包 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/organization/role-permissions?roleId=1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
b=[x for x in d if x["module"]=="bug"][0]
assert b["moduleName"] == "缺陷", b
assert b["methods"] == ["create","delete","query","update"], b
print(b["moduleName"], ",".join(b["methods"]))')
[ "$line" = "缺陷 create,delete,query,update" ] && { PASS=$((PASS+1)); echo "  ✅ 单角色权限明细 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 角色权限明细 期望='缺陷 create,delete,query,update' 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/organization/role-permissions?roleId=2")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 普通角色没有禅道权限 = $n"; } || { FAIL=$((FAIL+1)); echo "  ❌ 普通角色权限 期望0 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/organization/role-list?withPermissions=false")
has=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(all(x["permissions"] is None for x in d))')
[ "$has" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ withPermissions=false 时不返回权限明细"; } || { FAIL=$((FAIL+1)); echo "  ❌ withPermissions=false 仍返回了明细"; }

echo "--- 5. 部门树（zt_dept 视角） ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/organization/dept-tree")
check "部门树" 0 "$R"
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
def walk(nodes):
    for n in nodes:
        yield n
        yield from walk(n.get("children") or [])
flat=list(walk(d))
leaf=[x for x in flat if x["grade"]==3][0]
print("ok" if len(d) >= 1 else "none", leaf["grade"], leaf["path"].startswith(","), leaf["path"].endswith(","), leaf["userCount"] is not None)')
# 根部门数量取决于演示数据，这里只断言「有根」「叶子层级 3」「path 是禅道的逗号格式」「带人数」
[ "$line" = "ok 3 True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 部门树 层级/path 逗号格式/人数 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 部门树 期望='ok 3 True True True' 实际=$line"; }

echo "--- 6. 映射对照表 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/organization/mapping")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
tables=[x["zentaoTable"] for x in d]
group=[x for x in d if x["zentaoTable"].startswith("zt_group")][0]
print(len(d), "|".join(tables[:3]), len(group["fieldMapping"]), len(group["notes"]))')
[ "$line" = "4 zt_user|zt_dept|zt_group + zt_grouppriv 4 4" ] && { PASS=$((PASS+1)); echo "  ✅ 映射表 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 映射表 实际=$line"; }

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

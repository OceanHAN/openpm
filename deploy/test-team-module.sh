#!/bin/bash
# 项目/执行团队（zt_team）接口测试
#
# 禅道语义：
#   ① zt_team 一张表存项目与执行的成员，靠 type 区分（project / execution）；
#   ② **团队人数来自本表**（module/project/tao.php#fetchMemberCountByIdList），
#      不是 zt_project.team 那个逗号串；
#   ③ 可用工时 = days × hours（禅道 project/model.php:556 的 totalHours），hours 默认 7.0；
#   ④ 全量保存是「先删后插」，但**老成员的加入日期要保留**（project/tao.php#insertMember）；
#   ⑤ 成员是**物理增删**：本表没有 deleted 列，逻辑删除会撞 UNIQUE(root,type,account)；
#   ⑥ 执行的层级口径（execution::setTreePath）：parent=所属项目、path=,项目,执行,、grade=1。
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

echo "===== 项目/执行团队模块测试 ====="
TS=$(date +%s)

echo "--- 1. 演示数据：项目 1 的团队 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=1&type=project")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
# BigDecimal 的尾零在 JSON 里不稳定（140.00 / 140.0 都见过），统一按两位小数比
print(len(d), ' '.join('%s:%s:%.2f' % (x['account'], x['role'], float(x['totalHours'] or 0)) for x in d))")
[ "${line}" = "3 admin:项目经理:140.00 tester:测试:40.00 dev1:研发:105.00" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 成员与可用工时（days×hours）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示团队 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=1&type=project")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(all(x['realname'] for x in d), d[0]['limited'], d[2]['limited'])")
[ "${line}" = "True no yes" ] && { PASS=$((PASS+1)); echo "  ✅ 姓名回填 + 受限标记（dev1 limited=yes）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 姓名/受限 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/team/total-hours?root=1&type=project")
line=$(echo "$R" | python3 -c "import sys,json;print('%.2f' % float(json.load(sys.stdin)['data'] or 0))")
[ "${line}" = "285.00" ] && { PASS=$((PASS+1)); echo "  ✅ 团队可用工时合计 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 合计 实际=${line}"; }

echo "--- 2. 团队人数以成员表为准（而不是 zt_project.team 字符串）---"
R=$(curl -s "${H[@]}" "$BASE/zentao/project/get?id=1")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['teamCount'], d['team'])")
[ "${line}" = "3 admin,tester,dev1" ] && { PASS=$((PASS+1)); echo "  ✅ 项目 1 的 teamCount/team = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目 1 团队 实际=${line}"; }

# 加一个成员 → 项目上的 teamCount 跟着变（说明它来自成员表）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/team/add-member" \
  -d "{\"root\":1,\"type\":\"project\",\"account\":\"dev2\",\"role\":\"研发\",\"days\":5,\"hours\":8}")
check "添加成员 dev2（5 天 × 8 小时）" 0 "$R"
M1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/project/get?id=1")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['teamCount'], d['team'])")
[ "${line}" = "4 admin,tester,dev1,dev2" ] && { PASS=$((PASS+1)); echo "  ✅ 加人后 teamCount/team 同步 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 同步 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=1&type=project")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
m=[x for x in d if x['account']=='dev2'][0]
print(m['join'], '%.2f' % float(m['totalHours'] or 0))")
today=$(date +%F)
[ "${line}" = "${today} 40.00" ] && { PASS=$((PASS+1)); echo "  ✅ 新成员的加入日期默认今天、可用工时 5×8=40 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新成员 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/team/add-member" \
  -d "{\"root\":1,\"type\":\"project\",\"account\":\"dev2\",\"role\":\"研发\"}")
check "重复添加同一个人 → 拒绝" 1020022001 "$R" "已在团队里"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/team/add-member" \
  -d "{\"root\":1,\"type\":\"team\",\"account\":\"dev3\"}")
check "type 非法 → 拒绝" 1020022002 "$R" "类型不合法"

echo "--- 3. 修改与移除成员 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/team/update-member" \
  -d "{\"id\":${M1},\"root\":1,\"type\":\"project\",\"account\":\"dev2\",\"role\":\"测试\",\"days\":6,\"hours\":4,\"limited\":\"yes\"}")
check "改角色/天数/小时数/受限" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=1&type=project")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
m=[x for x in d if x['account']=='dev2'][0]
print(m['role'], m['days'], '%.2f' % float(m['hours'] or 0), m['limited'], '%.2f' % float(m['totalHours'] or 0))")
[ "${line}" = "测试 6 4.00 yes 24.00" ] && { PASS=$((PASS+1)); echo "  ✅ 修改生效 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修改 实际=${line}"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/team/remove-member?id=${M1}")
check "移除成员" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/team/remove-member?id=99999999")
check "移除不存在的成员 → 拒绝" 1020022000 "$R" "团队成员不存在"

# 移除后还能再加回来（物理删除的直接证据：逻辑删除会撞唯一键）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/team/add-member" \
  -d "{\"root\":1,\"type\":\"project\",\"account\":\"dev2\",\"role\":\"研发\",\"days\":1,\"hours\":1}")
check "移除后再加同一个人 → 允许（物理删除）" 0 "$R"
M2=$(echo "$R" | d)
curl -s "${H[@]}" -X DELETE "$BASE/zentao/team/remove-member?id=${M2}" > /dev/null

echo "--- 4. 全量保存：先删后插，但加入日期保留 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=1&type=project")
join_admin=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print([x['join'] for x in d if x['account']=='admin'][0])")
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/team/update-members" \
  -d "{\"root\":1,\"type\":\"project\",\"members\":[{\"account\":\"admin\",\"role\":\"项目经理\",\"days\":20,\"hours\":7},{\"account\":\"tmp1-${TS}\",\"role\":\"研发\",\"days\":2,\"hours\":5}]}")
check "全量保存（admin 留下、tester/dev1 被移除、tmp1 新增）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=1&type=project")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), ' '.join(x['account'] for x in d), [x['join'] for x in d if x['account']=='admin'][0])")
[ "${line}" = "2 admin tmp1-${TS} ${join_admin}" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 成员换成 2 人，admin 的加入日期仍是 ${join_admin}（没被刷成今天）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 全量保存 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/project/get?id=1")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['teamCount'])")
[ "${line}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 项目上的 teamCount 跟着变成 2"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ teamCount 实际=${line}"; }

# 还原演示团队
curl -s "${H[@]}" -X PUT "$BASE/zentao/team/update-members" \
  -d "{\"root\":1,\"type\":\"project\",\"members\":[{\"account\":\"admin\",\"role\":\"项目经理\",\"days\":20,\"hours\":7},{\"account\":\"tester\",\"role\":\"测试\",\"days\":10,\"hours\":4},{\"account\":\"dev1\",\"role\":\"研发\",\"days\":15,\"hours\":7,\"limited\":\"yes\"}]}" > /dev/null

echo "--- 5. 项目表单里的「团队成员」会落到成员表 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" \
  -d "{\"project\":0,\"name\":\"团队测试项目-${TS}\",\"model\":\"scrum\",\"pri\":3,\"parent\":9001,\"team\":\"admin,dev1,tester\"}")
check "建项目时带 3 个团队成员" 0 "$R"
TP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=${TP}&type=project")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), ' '.join(x['account'] for x in d), all(x['hours']==7.0 or x['hours']==7 for x in d))")
[ "${line}" = "3 admin dev1 tester True" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 成员落表、默认每天 7 小时 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建项目带团队 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/project/get?id=${TP}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['teamCount'])")
[ "${line}" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 新项目的 teamCount = 3"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ teamCount 实际=${line}"; }

# 改项目团队成员（去掉 tester、加上 dev2）→ 成员表同步
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/project/update" \
  -d "{\"id\":${TP},\"project\":0,\"name\":\"团队测试项目-${TS}\",\"model\":\"scrum\",\"pri\":3,\"parent\":9001,\"team\":\"admin,dev1,dev2\"}")
check "改项目的团队成员" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=${TP}&type=project")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), ' '.join(x['account'] for x in d))")
[ "${line}" = "3 admin dev1 dev2" ] && { PASS=$((PASS+1)); echo "  ✅ 成员表同步成 ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 改团队 实际=${line}"; }

echo "--- 6. 执行团队与执行层级口径 ---"
# 执行建好之后：parent=所属项目、path=,项目,执行,、grade=1（禅道 execution::setTreePath）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" \
  -d "{\"project\":${TP},\"name\":\"团队测试迭代-${TS}\",\"type\":\"sprint\",\"pri\":3,\"begin\":\"2026-02-01\",\"end\":\"2026-03-01\",\"PM\":\"admin\",\"QD\":\"tester\"}")
check "新建执行（带 PM/QD）" 0 "$R"
EX=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=${EX}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['project'], d['parent'], d['path'], d['grade'])")
[ "${line}" = "${TP} ${TP} ,${TP},${EX}, 1" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 执行层级：project=parent=项目、path=,项目,执行,、grade=1 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 执行层级 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=${EX}&type=execution")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), ' '.join('%s:%s' % (x['account'], x['role']) for x in d))")
[ "${line}" = "2 admin:项目经理 tester:测试负责人" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 执行的负责人自动进团队成员（角色来自 ownerFields）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 执行团队 实际=${line}"; }

# 演示执行 90001 的层级（上一轮把执行 path 写空是错的，这一轮纠正）
R=$(curl -s "${H[@]}" "$BASE/zentao/execution/get?id=90001")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['project'], d['parent'], d['path'], d['grade'])")
[ "${line}" = "1 1 ,1,90001, 1" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 演示执行 90001 的层级已纠偏 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示执行层级 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/execution/page?pageNo=1&pageSize=50&project=${TP}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], d['list'][0]['id'])")
[ "${line}" = "1 ${EX}" ] && { PASS=$((PASS+1)); echo "  ✅ 按项目过滤执行仍正常 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 执行过滤 实际=${line}"; }

echo "--- 7. 删除项目会连带删掉成员行 ---"
# 先删执行：项目下有执行时会被删除保护拦住（这是有意的设计，见 README「已知限制」）
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=${TP}")
check "项目下还有执行 → 拒绝删除" 1020005005 "$R" "执行"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=${EX}")
check "先删掉执行" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=${TP}")
check "删除项目" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=${TP}&type=project")
line=$(echo "$R" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 项目的成员行一起被删掉（禅道 project/model.php:2029）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 成员残留 实际=${line}"; }

echo "--- 8. 清理并确认演示数据完好 ---"
curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=${EX}" > /dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=1&type=project")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), ' '.join('%s:%.2f' % (x['account'], float(x['totalHours'] or 0)) for x in d))")
[ "${line}" = "3 admin:140.00 tester:40.00 dev1:105.00" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 演示团队还原 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示团队 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/team/list?root=90001&type=execution")
line=$(echo "$R" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")
[ "${line}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 演示执行 90001 的团队仍是 2 人"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示执行团队 实际=${line}"; }

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

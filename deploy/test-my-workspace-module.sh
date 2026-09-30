#!/bin/bash
# 我的地盘（my）模块测试：第二组「我参与的对象」
#   my 是查询层，不产生数据；这一组回答的是「我在哪些项目/执行/团队/测试单/用例/文档里」，
#   以及「我的日历」上这个月有什么。
#
# 禅道语义（module/my）：
#   1. 我参与的项目/执行 = 四个负责人字段（PM/PO/QD/RD）命中我 **或** 团队成员里有我
#      （zt_project.team 是逗号列表，只能 FIND_IN_SET）
#   2. 我的团队 = zt_team 里 account=我的行，一行 = 我在某个项目/执行里的角色 + 可用工时
#      （可用工时 = days × hours，与 team 模块 getTotalHours 同口径）
#   3. 我的测试单 = owner 或 createdBy 命中我（测试单没有成员表）
#   4. 我的用例 = 我创建的「或」我评审过的 —— 注意是 OR，而用例表里这两个字段是 AND，
#      所以后端拆成两次查询再按 id 去重合并
#   5. 我的文档 = 创建人 / 指派给 / 最后修改人任一中（章节是目录节点，默认剔除）
#   6. 我的日历 = 待办(date) + 任务(estStarted) + 测试单(begin) 按天归集到一个月
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

source "$(dirname "$0")/_mysql.sh"

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')
HF=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1')

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

echo "===== 我的地盘（我参与的项目 / 执行 / 团队 / 测试单 / 用例 / 文档 / 日历）测试 ====="
TS=$(date +%s)
TODAY=$(date +%Y-%m-%d)
MONTH=$(date +%Y-%m)

echo "--- 1. 我参与的项目：负责人字段命中 或 团队成员命中 ---"
P1=$(curl -s -X POST "${H[@]}" "$BASE/zentao/project/create" \
  -d "{\"project\":0,\"name\":\"MYP-项目A-$TS\",\"model\":\"scrum\",\"pri\":3,\"PM\":\"admin\"}" | d)
P2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/project/create" \
  -d "{\"project\":0,\"name\":\"MYP-项目B-$TS\",\"model\":\"scrum\",\"pri\":3,\"PM\":\"tester\",\"team\":\"admin\"}" | d)
P3=$(curl -s -X POST "${H[@]}" "$BASE/zentao/project/create" \
  -d "{\"project\":0,\"name\":\"MYP-项目C-$TS\",\"model\":\"scrum\",\"pri\":3,\"PM\":\"tester\",\"team\":\"tester2\"}" | d)
[ -n "$P1" ] && [ -n "$P2" ] && [ -n "$P3" ] && { PASS=$((PASS+1)); echo "  ✅ 准备：建三个项目（PM=我 / 团队有我 / 与我无关）$P1 $P2 $P3"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建项目失败：$P1 $P2 $P3"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/my/project-page?pageNo=1&pageSize=100&name=MYP-%E9%A1%B9%E7%9B%AE")
line=$(echo "$R" | field "d['total']")
[ "${line}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ PM 命中 + 团队成员命中各算一个，与我无关的不算（total=2）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我参与的项目数 实际=${line}（期望 2）"; }
line=$(echo "$R" | field "sorted([x['id'] for x in d['list']]) == sorted([$P1,$P2])")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 命中的正是 A（PM=我）与 B（团队里有我）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 命中集合 实际=${line}"; }
# P2 的命中完全靠 FIND_IN_SET（PM 是 tester）
line=$(mysql_query "SELECT team FROM \`ruoyi-vue-pro\`.zt_project WHERE id=$P2;")
[ "${line}" = "admin" ] && { PASS=$((PASS+1)); echo "  ✅ B 的命中来源确实是 team 逗号列表（FIND_IN_SET）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ B 的 team 字段 实际=${line}"; }
line=$(echo "$R" | field "'project' if all(x['type']=='project' for x in d['list']) else 'mixed'")
[ "${line}" = "project" ] && { PASS=$((PASS+1)); echo "  ✅ 项目列表不混入执行（type 全部是 project）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 返回里混了执行 实际=${line}"; }
# 与我无关的项目单独查也查不到
line=$(curl -s "${H[@]}" "$BASE/zentao/my/project-page?pageNo=1&pageSize=100&name=MYP-%E9%A1%B9%E7%9B%AEC" | field "d['total']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 与我无关的项目：用同样的名字筛也查不出来"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 无关项目 实际=${line}（期望 0）"; }
# 名字里的引号走预编译参数，不该报 SQL 错
line=$(curl -s "${H[@]}" "$BASE/zentao/my/project-page?pageNo=1&pageSize=10&name=%27" | field "d['total']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 查询条件里的引号被当普通字符（预编译参数，无 SQL 注入）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 引号查询 实际=${line}"; }

echo "--- 2. 我参与的执行 ---"
# 注意：执行创建不收 team 字段（禅道语义：成员来自负责人字段），团队要另外用 team/add-member 加
E1=$(curl -s -X POST "${H[@]}" "$BASE/zentao/execution/create" \
  -d "{\"name\":\"MYP-执行A-$TS\",\"project\":$P1,\"type\":\"sprint\",\"pri\":3,\"PM\":\"tester\",\"begin\":\"2026-01-01\",\"end\":\"2026-01-31\"}" | d)
E2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/execution/create" \
  -d "{\"name\":\"MYP-执行B-$TS\",\"project\":$P1,\"type\":\"sprint\",\"pri\":3,\"RD\":\"dev1\"}" | d)
[ -n "$E1" ] && [ -n "$E2" ] && { PASS=$((PASS+1)); printf '  ✅ 准备：建两个执行（稍后把我加进 A 的团队）%s %s\n' "$E1" "$E2"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建执行失败：$E1 $E2"; }
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/team/add-member" \
  -d "{\"root\":$E1,\"type\":\"execution\",\"account\":\"admin\",\"role\":\"测试负责人\",\"days\":5,\"hours\":7.5,\"limited\":\"yes\"}")
check "准备：把我加进执行 A 的团队（5 天 × 7.5 小时）" 0 "$R"
MID=$(echo "$R" | d)

R=$(curl -s "${H[@]}" "$BASE/zentao/my/execution-page?pageNo=1&pageSize=100&name=MYP-%E6%89%A7%E8%A1%8C")
line=$(echo "$R" | field "d['total'], sorted([x['id'] for x in d['list']]) == [$E1]")
[ "${line}" = "1 True" ] && { PASS=$((PASS+1)); echo "  ✅ 我参与的执行：团队成员命中 → 出现；团队里没我 → 不出现"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我参与的执行 实际=${line}"; }
# 命中来源必须真的是成员表（执行 A 的 PM 是 tester，团队里加了我之后 zt_project.team 同步成 tester,admin）
# 用 CONCAT 拼一列：mysql_query 会 tr -d ' '，多列输出之间的制表符不好断言
line=$(mysql_query "SELECT CONCAT(PM, '|', team) FROM \`ruoyi-vue-pro\`.zt_project WHERE id=$E1;")
[ "${line}" = "tester|tester,admin" ] && { PASS=$((PASS+1)); echo "  ✅ 团队字符串与成员表同步（PM=tester，team=tester,admin）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 团队字符串 实际=${line}"; }
# QD=admin 的执行也算（负责人字段这条路径）
E3=$(curl -s -X POST "${H[@]}" "$BASE/zentao/execution/create" \
  -d "{\"name\":\"MYP-执行C-$TS\",\"project\":$P1,\"type\":\"stage\",\"pri\":3,\"QD\":\"admin\",\"PM\":\"tester\"}" | d)
line=$(curl -s "${H[@]}" "$BASE/zentao/my/execution-page?pageNo=1&pageSize=100&name=MYP-%E6%89%A7%E8%A1%8C" | field "d['total'], sorted([x['id'] for x in d['list']]) == sorted([$E1,$E3])")
[ "${line}" = "2 True" ] && { PASS=$((PASS+1)); echo "  ✅ 执行列表也认负责人字段（QD=我 且 团队里没我，照样命中）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 负责人字段路径 实际=${line}"; }
line=$(curl -s "${H[@]}" "$BASE/zentao/my/execution-page?pageNo=1&pageSize=100&type=sprint&name=MYP-%E6%89%A7%E8%A1%8C" | field "d['total']")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 执行列表可按 type 再过滤（sprint 只剩 1 个）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ type 过滤 实际=${line}"; }

echo "--- 3. 我的团队 ---"
# 成员行在上一步已经用 add-member 建好（5 天 × 7.5 小时），这里验证接口回读的工时口径
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/team/update-member" \
  -d "{\"id\":$MID,\"root\":$E1,\"type\":\"execution\",\"account\":\"admin\",\"role\":\"测试负责人\",\"days\":5,\"hours\":7.5,\"limited\":\"yes\"}")
check "准备：把团队成员行改成 5 天 × 7.5 小时" 0 "$R"

R=$(curl -s "${H[@]}" "$BASE/zentao/my/team-list")
line=$(echo "$R" | field "len(d) > 0, all(x['account']=='admin' for x in d), all(x['type'] in ('project','execution') for x in d)")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 我的团队只返回我自己（account 全是 admin）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的团队 实际=${line}"; }
line=$(echo "$R" | field "[(x['rootName'],x['rootStatus'],str(x['totalHours']),x['limited'],x['role']) for x in d if x['id']==$MID][0]")
[ "${line}" = "('MYP-执行A-$TS', 'wait', '37.5', 'yes', '测试负责人')" ] && { PASS=$((PASS+1)); echo "  ✅ 团队成员行补上了对象名/状态，可用工时 5×7.5=37.50"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 团队成员行 实际=${line}"; }
line=$(echo "$R" | field "all(x['realname'] for x in d)")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 每行都带姓名（批量取，不是逐个 RPC）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 姓名 实际=${line}"; }
n=$(echo "$R" | field "len(d)")
line=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_team WHERE account='admin';")
[ "${line}" = "${n}" ] && { PASS=$((PASS+1)); echo "  ✅ 团队行数与库里 account=admin 的行数一致（${n}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 团队行数 接口=${n} 库=${line}"; }

echo "--- 4. 我的测试单 ---"
T1=$(curl -s -X POST "${H[@]}" "$BASE/zentao/testtask/create" \
  -d "{\"product\":1,\"name\":\"MYP-测试单A-$TS\",\"type\":\"feature\",\"begin\":\"2026-04-01\",\"end\":\"2026-04-10\"}" | d)
T2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/testtask/create" \
  -d "{\"product\":1,\"name\":\"MYP-测试单B-$TS\",\"type\":\"feature\",\"begin\":\"2026-04-01\",\"end\":\"2026-04-10\"}" | d)
[ -n "$T1" ] && [ -n "$T2" ] && { PASS=$((PASS+1)); echo "  ✅ 准备：建两个测试单 $T1 $T2"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建测试单失败"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/my/testtask-page?pageNo=1&pageSize=100&name=MYP-%E6%B5%8B%E8%AF%95%E5%8D%95")
line=$(echo "$R" | field "d['total'], sorted([x['id'] for x in d['list']]) == sorted([$T1,$T2])")
[ "${line}" = "2 True" ] && { PASS=$((PASS+1)); echo "  ✅ 我创建的测试单（owner 默认是我）都在列表里"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的测试单 实际=${line}"; }
# 把 A 的 owner 换成 tester、createdBy 保持 admin：仍应命中（createdBy 这条路径）
mysql_exec "UPDATE \`ruoyi-vue-pro\`.zt_testtask SET owner='tester' WHERE id=$T1;" >/dev/null 2>&1
R=$(curl -s "${H[@]}" "$BASE/zentao/my/testtask-page?pageNo=1&pageSize=100&name=MYP-%E6%B5%8B%E8%AF%95%E5%8D%95")
line=$(echo "$R" | field "d['total'], [x['owner'] for x in d['list'] if x['id']==$T1][0]")
[ "${line}" = "2 tester" ] && { PASS=$((PASS+1)); echo "  ✅ 指派给别人但由我创建的测试单，仍然算「我的」（owner=tester 也在）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ createdBy 路径 实际=${line}"; }
# 把 B 的 owner 与 createdBy 都换成 tester：应该消失
mysql_exec "UPDATE \`ruoyi-vue-pro\`.zt_testtask SET owner='tester', createdBy='tester' WHERE id=$T2;" >/dev/null 2>&1
line=$(curl -s "${H[@]}" "$BASE/zentao/my/testtask-page?pageNo=1&pageSize=100&name=MYP-%E6%B5%8B%E8%AF%95%E5%8D%95" | field "d['total'], sorted([x['id'] for x in d['list']]) == [$T1]")
[ "${line}" = "1 True" ] && { PASS=$((PASS+1)); echo "  ✅ 与我完全无关的测试单被过滤掉"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 无关测试单 实际=${line}"; }

echo "--- 5. 我的用例（我创建的 或 我评审过的）---"
# 用例默认状态是 normal（= 已评审过），要测评审就必须显式建成 wait
C1=$(curl -s -X POST "${H[@]}" "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"MYP-用例A-$TS\",\"type\":\"feature\",\"status\":\"wait\"}" | d)
C2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"MYP-用例B-$TS\",\"type\":\"feature\",\"status\":\"wait\"}" | d)
C3=$(curl -s -X POST "${H[@]}" "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"MYP-用例C-$TS\",\"type\":\"feature\",\"status\":\"wait\"}" | d)
# 注意（坑 #46）：JSON 里带转义引号时不能塞进 "$(curl ...)"，macOS 的 bash 3.2 会按空格拆词，
# body 被拆成多个参数，Jackson 收到的是坏 JSON（报 no String-argument constructor）
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/testcase/review" -d "{\"id\":$C1,\"result\":\"normal\",\"comment\":\"通过\"}")
check "准备：评审用例 A（我创建 + 我评审 → 用来验证去重）" 0 "$R"
# B：把创建人改成 tester，再由我评审 → 只有「评审过」这条路径能命中
mysql_exec "UPDATE \`ruoyi-vue-pro\`.zt_case SET openedBy='tester' WHERE id=$C2;" >/dev/null 2>&1
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/testcase/review" -d "{\"id\":$C2,\"result\":\"normal\",\"comment\":\"通过\"}")
check "准备：评审用例 B（别人创建、我评审）" 0 "$R"
# C：创建人也改成 tester、不评审 → 两条路径都不该命中
mysql_exec "UPDATE \`ruoyi-vue-pro\`.zt_case SET openedBy='tester' WHERE id=$C3;" >/dev/null 2>&1

R=$(curl -s "${H[@]}" "$BASE/zentao/my/case-page?pageNo=1&pageSize=100&title=MYP-%E7%94%A8%E4%BE%8B")
line=$(echo "$R" | field "d['total'], sorted([x['id'] for x in d['list']]) == sorted([$C1,$C2])")
[ "${line}" = "2 True" ] && { PASS=$((PASS+1)); echo "  ✅ 我创建的（B 不是）+ 我评审过的（C 没评审）合并后正好 2 条"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的用例合并 实际=${line}"; }
line=$(echo "$R" | field "[len([x for x in d['list'] if x['id']==$C1])][0]")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 既是我创建又是我评审的用例只出现一次（按 id 去重）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 去重 实际出现次数=${line}"; }
line=$(echo "$R" | field "[x['openedBy'] for x in d['list'] if x['id']==$C2][0]")
[ "${line}" = "tester" ] && { PASS=$((PASS+1)); echo "  ✅ 别人创建、我评审的用例也进得来（OR 关系，不是 AND）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 评审路径 实际创建人=${line}"; }
DBU=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_case WHERE deleted+0=0 AND title LIKE 'MYP-用例%$TS' AND (openedBy='admin' OR FIND_IN_SET('admin',reviewedBy));")
line=$(echo "$R" | field "d['total']")
[ "${line}" = "${DBU}" ] && { PASS=$((PASS+1)); echo "  ✅ 合并结果与库里 OR 口径的条数一致（${DBU}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 口径一致性 接口=${line} 库=${DBU}"; }

echo "--- 6. 我的文档 ---"
LIB=$(mysql_query "SELECT id FROM \`ruoyi-vue-pro\`.zt_doclib WHERE deleted+0=0 ORDER BY id LIMIT 1;")
D1=$(curl -s -X POST "${H[@]}" "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"title\":\"MYP-文档A-$TS\",\"type\":\"markdown\",\"content\":\"正文\"}" | d)
D2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"title\":\"MYP-章节B-$TS\",\"type\":\"chapter\"}" | d)
[ -n "$D1" ] && [ -n "$D2" ] && { PASS=$((PASS+1)); printf '  ✅ 准备：建一个正文文档 + 一个章节（lib=%s）\n' "$LIB"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建文档失败：$D1 $D2"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/my/doc-page?pageNo=1&pageSize=100&title=MYP-")
line=$(echo "$R" | field "d['total'], sorted([x['id'] for x in d['list']]) == [$D1]")
[ "${line}" = "1 True" ] && { PASS=$((PASS+1)); echo "  ✅ 我的文档含我创建的正文，章节（目录节点）默认剔除"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的文档 实际=${line}"; }
# 创建人改成 tester，最后修改人保持我 → 仍应命中
mysql_exec "UPDATE \`ruoyi-vue-pro\`.zt_doc SET addedBy='tester', editedBy='admin' WHERE id=$D1;" >/dev/null 2>&1
line=$(curl -s "${H[@]}" "$BASE/zentao/my/doc-page?pageNo=1&pageSize=100&title=MYP-%E6%96%87%E6%A1%A3A" | field "d['total']")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 别人创建、最后由我修改的文档也算「我的」"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 最后修改人路径 实际=${line}"; }
# 创建人/指派给/修改人全部换成别人 → 消失
mysql_exec "UPDATE \`ruoyi-vue-pro\`.zt_doc SET addedBy='tester', editedBy='tester' WHERE id=$D1;" >/dev/null 2>&1
line=$(curl -s "${H[@]}" "$BASE/zentao/my/doc-page?pageNo=1&pageSize=100&title=MYP-%E6%96%87%E6%A1%A3A" | field "d['total']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 三处都不含我的文档被过滤掉"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 无关文档 实际=${line}"; }

echo "--- 7. 我的日历 ---"
TD=$(curl -s -X POST "${H[@]}" "$BASE/zentao/todo/create" -d "{\"name\":\"MYP-日历待办-$TS\"}" | d)
[ -n "$TD" ] && { PASS=$((PASS+1)); printf '  ✅ 准备：建一条今天的待办（%s，日期默认 %s）\n' "$TD" "$TODAY"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建待办失败"; }
R=$(curl -s "${HF[@]}" "$BASE/zentao/my/calendar")
line=$(echo "$R" | field "[len([i for i in day['items'] if i['type']=='todo' and i['id']==$TD]) for day in d if day['date']=='$TODAY'][0] if any(day['date']=='$TODAY' for day in d) else 'no-day'")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 不传 month 时取当月，今天的待办落在 $TODAY 这一天"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 日历里的待办 实际=${line}"; }
line=$(echo "$R" | field "all(x['count']==len(x['items']) for x in d), [x['date'] for x in d]==sorted(x['date'] for x in d)")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 每天带 count，且日期按升序返回（TreeMap 保证）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 日历结构与排序 实际=${line}"; }
line=$(echo "$R" | field "all(set(i['type'] for i in x['items']) <= {'todo','task','testtask'} for x in d)")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 日历只收待办/任务/测试单三类（和禅道 my 的日历一致）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 日历类型 实际=${line}"; }
# 指定一个肯定没有数据的月份 → 空
line=$(curl -s "${HF[@]}" "$BASE/zentao/my/calendar?month=2030-01" | field "len(d)")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 指定 2030-01：没有任何数据时返回空数组（不是 null）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 空月份 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/my/calendar?month=$MONTH" | field "len(d) > 0")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); printf '  ✅ 显式传当月（%s）也有数据\n' "$MONTH"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 显式当月 实际=${line}"; }
# 测试单落在 begin 那天（注意日历只收 owner 是我的测试单，A 此时已被改成 owner=tester）
TC=$(curl -s -X POST "${H[@]}" "$BASE/zentao/testtask/create" \
  -d "{\"product\":1,\"name\":\"MYP-日历测试单-$TS\",\"type\":\"feature\",\"begin\":\"$TODAY\",\"end\":\"2026-12-31\"}" | d)
[ -n "$TC" ] && { PASS=$((PASS+1)); echo "  ✅ 准备：建一个以今天为开始的测试单（owner 是我）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建日历用测试单失败"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/my/calendar?month=$MONTH" | field "[len([i for i in day['items'] if i['type']=='testtask' and i['id']==$TC]) for day in d if day['date']=='$TODAY'][0] if any(day['date']=='$TODAY' for day in d) else 'no-day'")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 我负责的测试单按 begin 落到日历上"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 日历里的测试单 实际=${line}"; }
check "月份格式非法 → 参数/业务异常" 500 "$(curl -s "${HF[@]}" "$BASE/zentao/my/calendar?month=abc")" ""

echo "--- 8. 鉴权与既有能力回归 ---"
# 框架的鉴权失败是 HTTP 200 + body.code=401（全局异常处理器统一包了一层），所以断言 body 里的 code
line=$(curl -s "$BASE/zentao/my/project-page?pageNo=1&pageSize=10" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
[ "${line}" = "401" ] && { PASS=$((PASS+1)); echo "  ✅ 不带令牌访问「我参与的项目」被拦（code=401）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未登录 实际 code=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/my/overview" | field "d['account']")
[ "${line}" = "admin" ] && { PASS=$((PASS+1)); echo "  ✅ 原有的概览接口不受影响（account=admin）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 概览 实际=${line}"; }
line=$(curl -s "${H[@]}" "$BASE/zentao/my/task-page?pageNo=1&pageSize=5" | field "d['total'] >= 0")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 原有的「我的任务」分页不受影响"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的任务 实际=${line}"; }

echo "--- 9. 清理 ---"
# zt_team 没有 deleted 列（成员是物理增删），删项目/执行不会带走成员行 —— 必须手动清，
# 否则每跑一次测试就留下十几条孤儿团队成员，污染演示数据的团队人数统计
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_team WHERE root IN ($P1,$P2,$P3,$E1,$E2,$E3);" >/dev/null 2>&1
curl -s -X DELETE "${H[@]}" "$BASE/zentao/todo/delete?id=$TD" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/doc/delete?id=$D1" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/doc/delete?id=$D2" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/testcase/delete?id=$C1" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/testcase/delete?id=$C2" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/testcase/delete?id=$C3" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/testtask/delete?id=$TC" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/testtask/delete?id=$T1" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/testtask/delete?id=$T2" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/execution/delete?id=$E1" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/execution/delete?id=$E2" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/execution/delete?id=$E3" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/project/delete?id=$P1" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/project/delete?id=$P2" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/project/delete?id=$P3" >/dev/null
line=$(curl -s "${H[@]}" "$BASE/zentao/my/project-page?pageNo=1&pageSize=100&name=MYP-%E9%A1%B9%E7%9B%AE" | field "d['total']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 清理：删掉的项目不再出现在「我参与的项目」里（${line}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 清理后 实际=${line}"; }

echo "======================================================"
echo "  my 模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

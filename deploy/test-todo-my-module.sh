#!/bin/bash
# 待办（todo）与「我的地盘」（my）接口测试
#
# 禅道语义：
#   ① zt_todo 是**个人**清单，和项目里的任务（zt_task）不是一回事：
#      type='custom' 可以不挂任何对象，也可以指向 task/bug/story/testtask 当快捷入口；
#   ② 「我的待办」的条件是 assignedTo = 我 OR finishedBy = 我 OR closedBy = 我
#      （module/todo/tao.php#getListBy），不是按 account 查；
#   ③ 关闭待办会把指派人置成伪用户 'closed'，激活时再从 finishedBy 还原；
#   ④ zd 我的地盘是查询层：任务/需求/缺陷都按「指派给我」统计（module/my/model.php#getOverview）。
#
# 手测注意点：中文查询参数必须 --data-urlencode（Tomcat 拒收未编码的非 ASCII 请求行）
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

# 下面重建 todo 演示数据要用 mysql_exec 直连库
source "$(dirname "$0")/_mysql.sh"

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
n() { python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data') or []))"; }

# 我的待办某个范围的条数
# 概览里的 今天/未完成/已过期 都是「未完成」口径，所以列表也要带 status=undone
cnt() { curl -s "${H[@]}" "$BASE/zentao/todo/my-list?browseType=$1&status=undone" | n; }

# 幂等重建 todo 演示数据（7 条，形状照抄 deploy/sql/34-zt_todo.sql：
# 今日 3 条 / 明天 1 条 / 过期 1 条 / 已完成 1 条 / 已关闭 1 条）。
# 为什么必须重建：演示数据的日期是相对 CURDATE() 算的，而 import2Today 会把**所有**未完成的
# 过期待办挪到今天。隔天再跑时 97101/97102/97103/97107 都已过期、会被一起挪走，而脚本结尾只还原
# 了 97104，计数就漂成「今日 4 / 明天 0」（本脚本原来的可重复性缺口）。所以开跑前先按 seed 重建，
# 结尾再重建一次把现场还回去。
demo_todo_reset() {
  mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_todo WHERE id BETWEEN 97101 AND 97107;
INSERT INTO \`ruoyi-vue-pro\`.zt_todo (\`id\`, \`account\`, \`date\`, \`begin\`, \`end\`, \`type\`, \`objectID\`, \`pri\`, \`name\`, \`desc\`, \`status\`, \`private\`, \`assignedTo\`, \`assignedBy\`, \`assignedDate\`, \`finishedBy\`, \`finishedDate\`, \`closedBy\`, \`closedDate\`, \`vision\`, \`creator\`, \`updater\`) VALUES
(97101, 'admin', CURDATE(), '0900', '1000', 'custom', 0, 1, '评审需求变更', '禅道迁移一期 v2 的需求变更需要过一遍评审', 'wait', 0, 'admin', 'admin', NOW(), '', NULL, '', NULL, 'rnd', 'admin', 'admin'),
(97102, 'admin', CURDATE(), '1400', '1500', 'custom', 0, 2, '写工时明细的迁移说明', NULL, 'doing', 0, 'admin', 'admin', NOW(), '', NULL, '', NULL, 'rnd', 'admin', 'admin'),
(97103, 'admin', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '0930', '1100', 'task', 1, 3, '看一下登录接口的实现', '关联任务 1', 'wait', 0, 'admin', 'tester', NOW(), '', NULL, '', NULL, 'rnd', 'tester', 'tester'),
(97104, 'admin', DATE_SUB(CURDATE(), INTERVAL 2 DAY), '1000', '1200', 'custom', 0, 2, '补上遗漏的回归脚本', '已经过期但还没做，用来验证「今日待办」的过期提示', 'wait', 0, 'admin', 'admin', NOW(), '', NULL, '', NULL, 'rnd', 'admin', 'admin'),
(97105, 'admin', DATE_SUB(CURDATE(), INTERVAL 1 DAY), '1500', '1600', 'bug', 1, 4, '确认缺陷 1 是否已修复', NULL, 'done', 0, 'admin', 'admin', NOW(), 'admin', NOW(), '', NULL, 'rnd', 'admin', 'admin'),
(97106, 'admin', DATE_SUB(CURDATE(), INTERVAL 3 DAY), '0800', '0900', 'custom', 0, 4, '整理上周的会议纪要', NULL, 'closed', 0, 'closed', 'admin', NOW(), '', NULL, 'admin', NOW(), 'rnd', 'admin', 'admin'),
(97107, 'admin', CURDATE(), '1600', '1700', 'custom', 0, 1, '私事：取快递', '私有待办只有自己列表里能看到', 'wait', 1, 'admin', 'admin', NOW(), '', NULL, '', NULL, 'rnd', 'admin', 'admin');"
}

echo "===== 待办 + 我的地盘模块测试 ====="
TS=$(date +%s)
today=$(date +%F)

# 开跑前先把演示数据恢复成固定形状，后面的计数断言才有确定的基线
demo_todo_reset

# import2Today 会把「未完成的过期待办」挪到今天；已完成的待办不该被挪，
# 先记下它原始的日期，第 5 节拿来做对照（演示数据的还原统一交给 demo_todo_reset）
ORIG_DONE_DATE=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=97105" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['date'])")

echo "--- 1. 演示数据：今日/未完成/已过期/未来 ---"
line="$(cnt today) $(cnt undone) $(cnt before) $(cnt tomorrow)"
[ "${line}" = "3 5 1 1" ] && { PASS=$((PASS+1)); echo "  ✅ 今日 3 条、未完成 5 条、已过期 1 条、明天 1 条 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示数据 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=97101")
line=$(echo "$R" | python3 -c "
import sys,json
x=json.load(sys.stdin)['data']
print(x['account'], x['assignedTo'], x['status'], x['typeName'], x['pri'], x['overdue'])")
[ "${line}" = "admin admin wait 自定义 1 False" ] && { PASS=$((PASS+1)); echo "  ✅ 待办 97101 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 97101 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=97104")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(x['status'], x['overdue'])")
[ "${line}" = "wait True" ] && { PASS=$((PASS+1)); echo "  ✅ 已过期未完成的待办 overdue=true = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 过期标记 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=97105")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(x['status'], x['overdue'], x['finishedBy'])")
[ "${line}" = "done False admin" ] && { PASS=$((PASS+1)); echo "  ✅ 已完成待办不算过期 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 已完成 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/list-by-date?date=$(date +%F)" >/dev/null 2>&1; echo '')
R=$(curl -s -G "${H[@]}" --data-urlencode "name=评审" "$BASE/zentao/todo/page?pageNo=1&pageSize=20")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], d['list'][0]['id'])")
[ "${line}" = "1 97101" ] && { PASS=$((PASS+1)); echo "  ✅ 管理视角按名称过滤 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 名称过滤 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/page?pageNo=1&pageSize=20&status=undone")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(all(x['status'] not in ('done','closed') for x in d['list']))")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 未完成过滤不含 done/closed"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未完成过滤 实际=${line}"; }

echo "--- 2. 新建待办：默认值 / 类型 / 校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/todo/create" -d "{\"name\":\"测试待办-${TS}\"}")
check "只给名称就能建（日期默认今天）" 0 "$R"
T1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=${T1}")
line=$(echo "$R" | python3 -c "
import sys,json
x=json.load(sys.stdin)['data']
print(x['account'], x['assignedTo'], x['status'], x['pri'], x['date'], x['type'])")
[ "${line}" = "admin admin wait 3 ${today} custom" ] && { PASS=$((PASS+1)); echo "  ✅ 默认值 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 默认值 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/todo/create" \
  -d "{\"name\":\"关联任务待办-${TS}\",\"type\":\"task\",\"objectID\":1,\"date\":\"2026-10-01\"}")
check "建关联任务（type=task + objectID）的待办" 0 "$R"
T2=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/todo/create" -d "{\"name\":\"缺对象-${TS}\",\"type\":\"task\"}")
check "type=task 但没有 objectID → 拒绝" 1020021005 "$R" "必须关联一个对象"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/todo/create" -d "{\"name\":\"类型错-${TS}\",\"type\":\"xxx\"}")
check "类型不合法 → 拒绝" 1020021004 "$R" "待办类型不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/todo/create" -d "{\"date\":\"2026-10-01\"}")
check "缺名称 → 拒绝" 400 "$R"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/todo/batch-create?names=%E6%89%B9%E9%87%8F1-${TS},%E6%89%B9%E9%87%8F2-${TS}" \
  -d "{\"name\":\"占位\",\"date\":\"2026-10-02\",\"pri\":2}")
check "批量创建 2 条待办" 0 "$R"
BATCH=$(echo "$R" | d)
n2=$(echo "$BATCH" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')
[ "${n2}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 批量创建返回 2 个编号"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 批量创建 实际=${n2}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=$(echo "$BATCH" | python3 -c 'import sys,json;print(json.load(sys.stdin)[0])')")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(x['name'], x['date'], x['pri'])")
[ "${line}" = "批量1-${TS} 2026-10-02 2" ] && { PASS=$((PASS+1)); echo "  ✅ 批量创建的名称/日期/优先级 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 批量创建 实际=${line}"; }

echo "--- 3. 状态流转：开始/完成/关闭/激活 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/start?id=${T1}")
check "开始待办（wait → doing）" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/start?id=${T1}")
check "重复开始 → 拒绝" 1020021001 "$R"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/finish?id=${T1}")
check "完成待办" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=${T1}")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(x['status'], x['finishedBy'], bool(x['finishedDate']))")
[ "${line}" = "done admin True" ] && { PASS=$((PASS+1)); echo "  ✅ 完成人/完成时间都写了 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 完成待办 实际=${line}"; }
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/finish?id=${T1}")
check "重复完成 → 拒绝" 1020021002 "$R"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/close?id=${T1}")
check "关闭待办" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=${T1}")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(x['status'], x['assignedTo'], x['closedBy'])")
[ "${line}" = "closed closed admin" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 关闭后指派人被置成伪用户 closed = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 关闭待办 实际=${line}"; }
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/close?id=${T1}")
check "重复关闭 → 拒绝" 1020021003 "$R"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/activate?id=${T1}")
check "激活待办（closed → wait）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=${T1}")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(x['status'], x['assignedTo'])")
[ "${line}" = "wait admin" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 激活后指派人从 finishedBy 还原 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 激活待办 实际=${line}"; }
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/activate?id=${T1}")
check "未开始的待办再激活 → 拒绝" 1020021001 "$R"

echo "--- 4. 指派与「我指派给别人的」 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/assign" -d "{\"id\":${T1},\"assignedTo\":\"tester\"}")
check "把待办指派给 tester" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=${T1}")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(x['assignedTo'], x['assignedBy'])")
[ "${line}" = "tester admin" ] && { PASS=$((PASS+1)); echo "  ✅ 指派人/指派人记录 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 指派 实际=${line}"; }

found=$(curl -s "${H[@]}" "$BASE/zentao/todo/my-list?browseType=all&assignedToOther=true" | python3 -c "
import sys,json
ids=[x['id'] for x in json.load(sys.stdin)['data']]
print(${T1} in ids)")
[ "${found}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 出现在「我指派给别人的」列表里"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 指派列表 实际=${found}"; }

found=$(curl -s "${H[@]}" "$BASE/zentao/todo/my-list?browseType=all&account=tester" | python3 -c "
import sys,json
ids=[x['id'] for x in json.load(sys.stdin)['data']]
print(${T1} in ids)")
[ "${found}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 也出现在 tester 的「我的待办」里（assignedTo 命中）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ tester 列表 实际=${found}"; }

# 私有待办：我建的、指派给别人的 → 在我的「指派给别人」列表里只显示「这是私有待办」
# 私有待办的遮挡：别人（tester）建一条私有待办指派给我（admin），
# 我能在列表里看到它（assignedTo 命中），但名称被遮住
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/todo/create" \
  -d "{\"name\":\"私有待办-${TS}\",\"account\":\"tester\",\"assignedTo\":\"admin\",\"privateFlag\":1}")
check "tester 建一条私有待办指派给 admin" 0 "$R"
TP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/my-list?browseType=all")
line=$(echo "$R" | python3 -c "
import sys,json
hit=[x for x in json.load(sys.stdin)['data'] if x['id']==${TP}]
print(hit[0]['name'] if hit else '')")
[ "${line}" = "这是私有待办" ] && { PASS=$((PASS+1)); echo "  ✅ 别人建的私有待办在我这里显示「这是私有待办」= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 私有待办 实际=${line}"; }
# 对照：归属人自己（admin）建一条私有待办指派出去，在自己「指派给别人」的列表里看到的是**真实名称**
# （禅道 getList 的判据是「看的人是不是这条待办的 account」，归属人当然不是别人）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/todo/create" \
  -d "{\"name\":\"我自己的私有待办-${TS}\",\"assignedTo\":\"tester\",\"privateFlag\":1}")
check "admin 建一条私有待办指派给 tester" 0 "$R"
TP2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/my-list?browseType=all&assignedToOther=true")
line=$(echo "$R" | python3 -c "
import sys,json
hit=[x for x in json.load(sys.stdin)['data'] if x['id']==${TP2}]
print(hit[0]['name'] if hit else '')")
[ "${line}" = "我自己的私有待办-${TS}" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 归属人自己看到的是真实名称 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 归属人视角 实际=${line}"; }

echo "--- 5. 挪到今天（import2Today）---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/todo/create" \
  -d "{\"name\":\"过期待办-${TS}\",\"date\":\"2020-01-01\"}")
OLD=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/import-to-today")
check "把没完成的过期待办挪到今天" 0 "$R"
moved=$(echo "$R" | d)
[ "${moved}" -ge 1 ] && { PASS=$((PASS+1)); echo "  ✅ 挪动了 ${moved} 条"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 挪动条数 实际=${moved}"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=${OLD}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['date'])")
[ "${line}" = "${today}" ] && { PASS=$((PASS+1)); echo "  ✅ 过期待办的日期变成今天 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 日期 实际=${line}"; }
# 已完成的待办不该被挪动
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=97105")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(x['date'], x['status'])")
[ "${line}" = "${ORIG_DONE_DATE} done" ] && { PASS=$((PASS+1)); echo "  ✅ 已完成的待办没被挪 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 已完成待办被挪了 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=97104")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['date'])")
[ "${line}" = "${today}" ] && { PASS=$((PASS+1)); echo "  ✅ 演示数据里那条过期待办也被挪到今天 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 过期待办 实际=${line}"; }

echo "--- 6. 我的地盘：概览与各 Tab ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/my/overview")
line=$(echo "$R" | python3 -c "
import sys,json
x=json.load(sys.stdin)['data']
print(x['account'], x['todoToday'], x['todoUndone'], x['todoOverdue'])")
today_n=$(cnt today); undone_n=$(cnt undone); overdue_n=$(cnt before)
[ "${line}" = "admin ${today_n} ${undone_n} ${overdue_n}" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 概览与「我的待办」三个范围自洽 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 概览/列表不自洽 实际=${line} 列表=${today_n}/${undone_n}/${overdue_n}"; }

# 我的任务：新建两个任务，一个指派给我、一个指派给 tester
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/task/create" \
  -d "{\"project\":1,\"execution\":90001,\"name\":\"我的任务-${TS}\",\"type\":\"devel\",\"pri\":3,\"assignedTo\":\"admin\"}")
check "准备：指派给 admin 的任务" 0 "$R"
MT=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/task/create" \
  -d "{\"project\":1,\"execution\":90001,\"name\":\"别人的任务-${TS}\",\"type\":\"devel\",\"pri\":3,\"assignedTo\":\"tester\"}")
check "准备：指派给 tester 的任务" 0 "$R"
OT=$(echo "$R" | d)

R=$(curl -s "${H[@]}" "$BASE/zentao/my/task-page?pageNo=1&pageSize=50&name=%E6%88%91%E7%9A%84%E4%BB%BB%E5%8A%A1-${TS}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], d['list'][0]['id'], d['list'][0]['assignedTo'])")
[ "${line}" = "1 ${MT} admin" ] && { PASS=$((PASS+1)); echo "  ✅ 我的任务里只有指派给我的那条 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的任务 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/my/task-page?pageNo=1&pageSize=50&name=%E5%88%AB%E4%BA%BA%E7%9A%84%E4%BB%BB%E5%8A%A1-${TS}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 指派给别人的任务不在我的列表里（assignedTo 被强制为当前账号）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 接口没有强制账号 实际=${line}"; }

# 我的缺陷 / 我的需求
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/bug/create" \
  -d "{\"product\":1,\"title\":\"我的缺陷-${TS}\",\"openedBuild\":\"trunk\",\"pri\":3,\"severity\":3,\"type\":\"codeerror\",\"assignedTo\":\"admin\"}")
check "准备：指派给 admin 的缺陷" 0 "$R"
MB=$(echo "$R" | d)
R=$(curl -s -G "${H[@]}" --data-urlencode "title=我的缺陷-${TS}" "$BASE/zentao/my/bug-page?pageNo=1&pageSize=50")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], d['list'][0]['assignedTo'])")
[ "${line}" = "1 admin" ] && { PASS=$((PASS+1)); echo "  ✅ 我的缺陷 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的缺陷 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" \
  -d "{\"product\":1,\"title\":\"我的需求-${TS}\",\"type\":\"story\",\"category\":\"feature\",\"pri\":2,\"assignedTo\":\"admin\"}")
check "准备：指派给 admin 的需求" 0 "$R"
MS=$(echo "$R" | d)
R=$(curl -s -G "${H[@]}" --data-urlencode "title=我的需求-${TS}" "$BASE/zentao/my/story-page?pageNo=1&pageSize=50")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], d['list'][0]['assignedTo'])")
[ "${line}" = "1 admin" ] && { PASS=$((PASS+1)); echo "  ✅ 我的需求 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的需求 实际=${line}"; }

# 我的工时：登记一条今天的工时
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${MT},\"date\":\"${today}\",\"consumed\":2.5,\"left\":0,\"work\":\"我的工时-${TS}\"}")
check "准备：登记 2.5 小时工时" 0 "$R"
ME=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/my/effort-page?pageNo=1&pageSize=50&taskId=${MT}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], d['list'][0]['account'], d['list'][0]['consumed'])")
[ "${line}" = "1 admin 2.5" ] && { PASS=$((PASS+1)); echo "  ✅ 我的工时 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的工时 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/my/overview")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(float(x['effortThisMonth']))")
[ "${line}" = "2.5" ] && { PASS=$((PASS+1)); echo "  ✅ 概览里的本月工时 = ${line}（正好是刚登记的 2.5）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 本月工时 实际=${line}"; }

# 我的动态
R=$(curl -s "${H[@]}" "$BASE/zentao/my/action-list?limit=50")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
kinds={x['objectType'] for x in d}
print(len(d) > 0, 'todo' in kinds, all(x['actor']=='admin' for x in d))")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 我的动态里有待办记录且都是我自己 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 我的动态 实际=${line}"; }

echo "--- 7. 修改与删除 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/todo/update" \
  -d "{\"id\":${T2},\"name\":\"改过的待办-${TS}\",\"type\":\"task\",\"objectID\":1,\"date\":\"2026-10-05\",\"pri\":1}")
check "修改待办" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/todo/get?id=${T2}")
line=$(echo "$R" | python3 -c "import sys,json;x=json.load(sys.stdin)['data'];print(x['name'], x['date'], x['pri'])")
[ "${line}" = "改过的待办-${TS} 2026-10-05 1" ] && { PASS=$((PASS+1)); echo "  ✅ 修改生效 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修改 实际=${line}"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/todo/delete?id=88888888")
check "删除不存在的待办 → 拒绝" 1020021000 "$R" "待办不存在"

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/type-list")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(len(d), d[0]['value'], d[1]['label'])")
[ "${line}" = "6 custom 周期" ] && { PASS=$((PASS+1)); echo "  ✅ 类型枚举 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 类型枚举 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/status-list")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(len(d), d[-1]['value'])")
[ "${line}" = "5 undone" ] && { PASS=$((PASS+1)); echo "  ✅ 状态枚举含聚合状态 undone = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 状态枚举 实际=${line}"; }

echo "--- 8. 清理并确认演示数据完好 ---"
for id in ${T1} ${T2} ${TP} ${TP2} ${OLD} $(echo "$BATCH" | python3 -c 'import sys,json;print(" ".join(str(x) for x in json.load(sys.stdin)))'); do
  curl -s "${H[@]}" -X DELETE "$BASE/zentao/todo/delete?id=${id}" > /dev/null
done
curl -s "${H[@]}" -X DELETE "$BASE/zentao/effort/delete?id=${ME}" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/task/delete?id=${MT}" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/task/delete?id=${OT}" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/bug/delete?id=${MB}" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=${MS}" > /dev/null
# 还原被 import2Today 挪走的演示待办：按 seed 重建 7 条，回到开跑前的固定形状
demo_todo_reset

R=$(curl -s "${H[@]}" "$BASE/zentao/todo/page?pageNo=1&pageSize=50")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "${line}" = "7" ] && { PASS=$((PASS+1)); echo "  ✅ 演示待办仍是 7 条"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 残留待办 实际=${line}"; }

line="$(cnt today) $(cnt undone) $(cnt before) $(cnt tomorrow)"
[ "${line}" = "3 5 1 1" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 清理并还原后，待办四个范围的计数回到演示状态 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 清理后计数 实际=${line}"; }

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

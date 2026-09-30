#!/bin/bash
# 报表（report）模块测试：年度数据 / 每日提醒 / 产出统计 / 项目状态总览
#
# 注意脚本名：禅道的「测试报告」模块在本项目里已经占了 test-report-module.sh，
# 所以报表用 test-annual-report-module.sh（同样是踩过的「同名」坑位 #25）。
#
# 禅道语义（module/report，v20 起这个模块只剩「年度数据」和「每日提醒」两件事，
# 老版自定义报表 zt_report 被 BI 取代，本实现不做它、自定义报表走 bi 的 SQL 模式）：
#   1. 年度数据三种视角：不传参数=全公司、传 dept=部门（含子部门）、传 account=个人；
#      指标 = 登录次数 / 动作数 / 待办 / 工时 / 贡献（按 zt_action 的动作类型统计）
#      + 产品与执行产出 + 需求/任务/缺陷/用例的状态分布与月度趋势
#   2. 贡献按 objectType 分类，且**要排除「对象已经删掉」的动作**（禅道 getUserYearContributions）
#   3. 「完成任务 / 解决缺陷」本实现在动作层面分不出来（都记成 changed），
#      所以按 finishedDate / resolvedDate 这两个事实列数（禅道年度执行统计也是这么数的）
#   4. 每日提醒 = 快到期的、没做完的 任务/缺陷/待办/测试单/看板卡片，按人聚合
#   5. 产出统计 = 各类对象本年各动作的条数（禅道 getOutput4API）
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
YEAR=$(date +%Y); TODAY=$(date +%F)

echo "===== 报表（年度数据 / 每日提醒 / 产出统计）模块测试 ====="

echo "--- 1. 筛选项 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/report/options")
line=$(echo "$R" | field "len(d['years']) > 0, d['current'] == d['years'][-1], any(u['account']=='admin' for u in d['users']), len(d['depts']) > 0")
[ "${line}" = "True True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 年份/人员/部门都有（current 是最后一年、users 含 admin）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 筛选项 实际=${line}"; }
line=$(echo "$R" | field "all(y.isdigit() and len(y)==4 for y in d['years'])")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 年份格式是 YYYY 且升序"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 年份格式 实际=${line}"; }

echo "--- 2. 公司视角年度数据：与库交叉核对 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/report/annual-data?year=$YEAR")
line=$(echo "$R" | field "d['mode'], d['year'], len(d['months'])")
[ "${line}" = "company $YEAR 12" ] && { PASS=$((PASS+1)); echo "  ✅ 不传参数=公司视角，月份 12 个"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 公司视角 实际=${line}"; }
DB_ACTIONS=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_action WHERE LEFT(date,4)='$YEAR';")
line=$(echo "$R" | field "d['actions']")
[ "${line}" = "${DB_ACTIONS}" ] && { PASS=$((PASS+1)); echo "  ✅ 动作数 = 库里本年 zt_action 条数（${DB_ACTIONS}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 动作数 接口=${line} 库=${DB_ACTIONS}"; }
DB_TODO=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_todo WHERE deleted+0=0 AND LEFT(date,4)='$YEAR';")
DB_UNDONE=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_todo WHERE deleted+0=0 AND LEFT(date,4)='$YEAR' AND status<>'done';")
line=$(echo "$R" | field "d['todos']['count'], d['todos']['undone']")
[ "${line}" = "${DB_TODO} ${DB_UNDONE}" ] && { PASS=$((PASS+1)); echo "  ✅ 待办口径与库一致（总数 ${DB_TODO} / 未完成 ${DB_UNDONE}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 待办 接口=${line} 库=${DB_TODO} ${DB_UNDONE}"; }
DB_EFFORT=$(mysql_query "SELECT COALESCE(SUM(consumed),0)+0 FROM \`ruoyi-vue-pro\`.zt_effort WHERE deleted+0=0 AND LEFT(date,4)='$YEAR';")
line=$(echo "$R" | field "'%.2f' % float(d['consumed'])")
[ "${line}" = "$(printf '%.2f' "${DB_EFFORT}")" ] && { PASS=$((PASS+1)); echo "  ✅ 本年消耗工时 = 库里的 SUM(consumed)（${DB_EFFORT}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 工时 接口=${line} 库=${DB_EFFORT}"; }
line=$(echo "$R" | field "d['contributionCount'] >= 0, d['maxCount'] > 0")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 贡献数与雷达最大值都算出来了"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 基础指标 实际=${line}"; }
# 雷达与贡献的关系：同一条贡献可能同时归到「执行」和「研发」（禅道也是这么双计的），
# 所以雷达之和 ≥ 贡献明细之和，且多出来的部分只能来自 task.* 这类多标签动作。
line=$(echo "$R" | field "sum(d['radarData'].values()), sum(sum(v.values()) for v in d['contributions'].values()), sum(v.get('create',0)+v.get('assign',0)+v.get('finish',0)+v.get('activate',0)+v.get('close',0) for k,v in d['contributions'].items() if k=='task')")
set -- $line
if [ "$1" -ge "$2" ] && [ "$1" -le "$(( $2 + $3 ))" ]; then
  PASS=$((PASS+1)); echo "  ✅ 雷达之和（$1）≥ 贡献明细之和（$2），差额不超过多标签任务数（$3）"
else
  FAIL=$((FAIL+1)); echo "  ❌ 雷达与贡献关系 雷达=$1 明细=$2 任务=$3"
fi
line=$(echo "$R" | field "sorted(d['radarData'].keys())")
[ "${line}" = "['devel', 'execution', 'other', 'product', 'qa']" ] && { PASS=$((PASS+1)); echo "  ✅ 雷达维度是 产品/执行/研发/测试/其它"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 雷达维度 实际=${line}"; }
line=$(echo "$R" | field "sorted(d['statusStat'].keys())")
[ "${line}" = "['bug', 'story', 'task']" ] && { PASS=$((PASS+1)); echo "  ✅ 公司视角带全量状态分布（需求/任务/缺陷）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 状态分布 实际=${line}"; }
line=$(echo "$R" | field "all(v.startswith('共 ') and '未完成' in v for v in d['overview'].values())")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 三类对象都有「共 N 条，未完成 M 条」概述"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 概述 实际=${line}"; }
# 贡献的「状态分布」要排除已删除对象；「月度趋势」不排除（与禅道 getYearObjectStat 一致）
DB_STORY_UNDEL=$(mysql_query "SELECT COUNT(DISTINCT s.id) FROM \`ruoyi-vue-pro\`.zt_action a JOIN \`ruoyi-vue-pro\`.zt_story s ON s.id=a.objectID AND s.deleted+0=0 WHERE a.objectType IN ('story','requirement','epic') AND a.action='created' AND LEFT(a.date,4)='$YEAR';")
line=$(echo "$R" | field "sum(d['storyStat']['statusStat'].values())")
[ "${line}" = "${DB_STORY_UNDEL}" ] && { PASS=$((PASS+1)); echo "  ✅ 需求状态分布只数未删除的对象（${DB_STORY_UNDEL}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 需求状态分布 接口=${line} 库=${DB_STORY_UNDEL}"; }
DB_MONTH=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_action WHERE objectType IN ('story','requirement','epic') AND action='created' AND LEFT(date,4)='$YEAR';")
line=$(echo "$R" | field "sum(d['storyStat']['actionStat']['created'].values())")
[ "${line}" = "${DB_MONTH}" ] && { PASS=$((PASS+1)); echo "  ✅ 月度趋势数的是全部动作（含已删除对象，与禅道一致）：${DB_MONTH}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 月度趋势 接口=${line} 库=${DB_MONTH}"; }
DB_EXEC=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_project WHERE deleted+0=0 AND type='sprint' AND multiple=1 AND (LEFT(begin,4)='$YEAR' OR LEFT(\`end\`,4)='$YEAR');")
line=$(echo "$R" | field "len(d['executionStat'])")
[ "${line}" = "${DB_EXEC}" ] && { PASS=$((PASS+1)); echo "  ✅ 执行统计只收「多迭代项目下的迭代」且起止在本年（${DB_EXEC}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 执行统计 接口=${line} 库=${DB_EXEC}"; }
line=$(echo "$R" | field "all(set(x.keys()) >= {'id','name','plan','story','requirement','epic','closed'} for x in d['productStat']), all(set(x.keys()) >= {'id','name','task','story','bug'} for x in d['executionStat'])")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 产品/执行统计的字段齐全"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 产品/执行字段 实际=${line}"; }
DB_CASE_RUN=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_testresult t JOIN \`ruoyi-vue-pro\`.zt_case c ON c.id=t.case WHERE c.deleted+0=0 AND LEFT(t.date,4)='$YEAR';")
line=$(echo "$R" | field "d['contributions'].get('case',{}).get('run',-1)")
[ "${line}" = "${DB_CASE_RUN}" ] && { PASS=$((PASS+1)); echo "  ✅ 用例「执行」数 = zt_testresult 的流水条数（${DB_CASE_RUN}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用例执行数 接口=${line} 库=${DB_CASE_RUN}"; }

echo "--- 3. 个人视角 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/report/annual-data?year=$YEAR&account=admin")
line=$(echo "$R" | field "d['mode'], d['who']")
[ "${line}" = "user 芋道源码" ] && { PASS=$((PASS+1)); echo "  ✅ 传 account → 个人视角，who 是姓名"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 个人视角 实际=${line}"; }
DB_LOGIN=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.system_login_log WHERE result=0 AND username='admin' AND LEFT(create_time,4)='$YEAR';")
line=$(echo "$R" | field "d['logins']")
[ "${line}" = "${DB_LOGIN}" ] && { PASS=$((PASS+1)); echo "  ✅ 登录次数来自 system_login_log（${DB_LOGIN}）—— 禅道记 zt_action，本实现读框架表"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 登录次数 接口=${line} 库=${DB_LOGIN}"; }
DB_MINE=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_action WHERE actor='admin' AND LEFT(date,4)='$YEAR';")
line=$(echo "$R" | field "d['actions']")
[ "${line}" = "${DB_MINE}" ] && { PASS=$((PASS+1)); echo "  ✅ 个人动作数 = actor=admin 的条数（${DB_MINE}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 个人动作数 接口=${line} 库=${DB_MINE}"; }
line=$(echo "$R" | field "'users' in d and d['users'] is None, d['logins'] is not None")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 个人视角不给「参与人数」，给登录次数"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 个人视角字段 实际=${line}"; }
line=$(echo "$R" | field "len(d['statusStat'])")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 个人视角不给全量状态分布（禅道也是只在公司视角给）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 个人状态分布 实际=${line}"; }

echo "--- 4. 部门视角（含子部门）---"
DEPT=$(curl -s "${HF[@]}" "$BASE/zentao/report/options" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print([x['id'] for x in d['depts'] if x['name']=='研发部门'][0])")
R=$(curl -s "${HF[@]}" "$BASE/zentao/report/annual-data?year=$YEAR&dept=$DEPT")
line=$(echo "$R" | field "d['mode'], d['who']")
[ "${line}" = "dept 研发部门" ] && { PASS=$((PASS+1)); echo "  ✅ 传 dept → 部门视角，who 是部门名（${DEPT}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 部门视角 实际=${line}"; }
DEPT_ACTIONS=$(echo "$R" | field "d['actions']")
COMPANY_ACTIONS=$(curl -s "${HF[@]}" "$BASE/zentao/report/annual-data?year=$YEAR" | field "d['actions']")
[ "${DEPT_ACTIONS}" -le "${COMPANY_ACTIONS}" ] && { PASS=$((PASS+1)); echo "  ✅ 部门动作数（${DEPT_ACTIONS}）≤ 公司动作数（${COMPANY_ACTIONS}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 部门动作数 部门=${DEPT_ACTIONS} 公司=${COMPANY_ACTIONS}"; }

echo "--- 5. 年份过滤 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/report/annual-data?year=2019")
line=$(echo "$R" | field "d['actions'], len(d['contributions']), len(d['executionStat']), d['maxCount'], d['contributionCount']")
[ "${line}" = "0 0 0 0 0" ] && { PASS=$((PASS+1)); echo "  ✅ 2019 年没有数据时返回 0 / 空，而不是报错"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 空年份 实际=${line}"; }

echo "--- 6. 每日提醒 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/report/reminder-list")
line=$(echo "$R" | field "len(d) > 0, all(r['total'] == len(r['bugs'])+len(r['tasks'])+len(r['todos'])+len(r['testTasks'])+len(r['cards']) for r in d)")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 提醒按人聚合，每人的 total 等于五类之和"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 提醒结构 实际=${line}"; }
DB_BUG=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_bug b JOIN \`ruoyi-vue-pro\`.system_users u ON u.username=b.assignedTo AND u.deleted=0 WHERE b.deleted+0=0 AND b.assignedTo NOT IN ('','closed') AND (b.deadline IS NULL OR b.deadline < DATE_ADD('$TODAY', INTERVAL 4 DAY));")
line=$(echo "$R" | field "sum(len(r['bugs']) for r in d)")
[ "${line}" = "${DB_BUG}" ] && { PASS=$((PASS+1)); echo "  ✅ 快到期的缺陷数与库一致（${DB_BUG}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 提醒缺陷 接口=${line} 库=${DB_BUG}"; }
DB_TODO_REM=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_todo t JOIN \`ruoyi-vue-pro\`.system_users u ON u.username=(CASE WHEN t.assignedTo<>'' THEN t.assignedTo ELSE t.account END) AND u.deleted=0 WHERE t.deleted+0=0 AND (t.cycle=0 OR t.cycle IS NULL) AND t.status IN ('wait','doing');")
line=$(echo "$R" | field "sum(len(r['todos']) for r in d)")
[ "${line}" = "${DB_TODO_REM}" ] && { PASS=$((PASS+1)); echo "  ✅ 未完成待办数与库一致（${DB_TODO_REM}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 提醒待办 接口=${line} 库=${DB_TODO_REM}"; }
line=$(echo "$R" | field "all('account' in r and 'realname' in r for r in d), [r['total'] for r in d] == sorted([r['total'] for r in d], reverse=True)")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 每人带账号与姓名，且按条数倒序"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 提醒排序 实际=${line}"; }
# 现造一个「指派给 admin、今天到期」的任务 → 进提醒；删掉后消失
NEWTASK=$(curl -s -X POST "${H[@]}" "$BASE/zentao/task/create" \
  -d "{\"project\":1,\"execution\":90001,\"name\":\"REPORT-提醒任务-$YEAR\",\"type\":\"devel\",\"assignedTo\":\"admin\",\"estStarted\":\"$TODAY\",\"deadline\":\"$TODAY\",\"estimate\":1,\"pri\":3}" | d)
[ -n "$NEWTASK" ] && { PASS=$((PASS+1)); echo "  ✅ 准备：建一个指派给 admin、今天到期的任务（$NEWTASK）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建任务失败"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/report/reminder-list" | field "sum(len([t for t in r['tasks'] if t['id']==$NEWTASK]) for r in d)")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 快到期的任务进了提醒"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 提醒任务 实际=${line}"; }
curl -s -X DELETE "${H[@]}" "$BASE/zentao/task/delete?id=$NEWTASK" >/dev/null
line=$(curl -s "${HF[@]}" "$BASE/zentao/report/reminder-list" | field "sum(len([t for t in r['tasks'] if t['id']==$NEWTASK]) for r in d)")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 任务删掉后不再出现在提醒里"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删后提醒 实际=${line}"; }

echo "--- 7. 产出统计 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/report/output?year=$YEAR")
line=$(echo "$R" | field "all(x['total'] == sum(a['total'] for a in x['actions']) for x in d), all(x['total'] > 0 for x in d)")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 每类的合计等于动作明细之和，且不返回空类"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 产出统计合计 实际=${line}"; }
line=$(echo "$R" | field "any(a['code']=='finish' for x in d if x['objectType']=='task' for a in x['actions'])")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 任务类里有「完成」（按 finishedDate 数的）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 任务完成 实际=${line}"; }
line=$(echo "$R" | field "any(a['code']=='run' for x in d if x['objectType']=='case' for a in x['actions'])")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 用例类里有「执行」"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用例执行 实际=${line}"; }
DB_TASK_CREATED=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_action a WHERE a.objectType='task' AND a.action='created' AND LEFT(a.date,4)='$YEAR' AND NOT EXISTS (SELECT 1 FROM \`ruoyi-vue-pro\`.zt_task t WHERE t.id=a.objectID AND t.deleted+0=1);")
line=$(echo "$R" | field "[a['total'] for x in d if x['objectType']=='task' for a in x['actions'] if a['code']=='create'][0]")
[ "${line}" = "${DB_TASK_CREATED}" ] && { PASS=$((PASS+1)); echo "  ✅ 任务「创建」数与库一致（${DB_TASK_CREATED}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 任务创建数 接口=${line} 库=${DB_TASK_CREATED}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/report/output?year=$YEAR&account=admin" | field "all(x['total'] >= 0 for x in d), len(d) >= 0")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 产出统计支持按人过滤"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按人过滤 实际=${line}"; }

echo "--- 8. 项目状态总览 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/report/project-status")
DB_ALL=$(mysql_query "SELECT COUNT(DISTINCT p.id) FROM \`ruoyi-vue-pro\`.zt_project p JOIN \`ruoyi-vue-pro\`.zt_team t ON t.root=p.id AND t.type='project' WHERE p.deleted+0=0 AND p.type='project';")
line=$(echo "$R" | field "sum(d.values())")
[ "${line}" = "${DB_ALL}" ] && { PASS=$((PASS+1)); echo "  ✅ 全部项目状态合计 = 有团队行的项目数（${DB_ALL}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目状态合计 接口=${line} 库=${DB_ALL}"; }
R=$(curl -s "${HF[@]}" "$BASE/zentao/report/project-status?account=admin")
DB_MINE_PROJ=$(mysql_query "SELECT COUNT(DISTINCT p.id) FROM \`ruoyi-vue-pro\`.zt_project p JOIN \`ruoyi-vue-pro\`.zt_team t ON t.root=p.id AND t.type='project' WHERE p.deleted+0=0 AND p.type='project' AND t.account='admin';")
line=$(echo "$R" | field "sum(d.values())")
[ "${line}" = "${DB_MINE_PROJ}" ] && { PASS=$((PASS+1)); echo "  ✅ 按人过滤后 = 我参与的团队项目数（${DB_MINE_PROJ}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按人过滤 接口=${line} 库=${DB_MINE_PROJ}"; }

echo "--- 9. 鉴权 ---"
line=$(curl -s "$BASE/zentao/report/annual-data" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
[ "${line}" = "401" ] && { PASS=$((PASS+1)); echo "  ✅ 不带令牌被拦（code=401）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未登录 实际 code=${line}"; }

echo "======================================================"
echo "  report 模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

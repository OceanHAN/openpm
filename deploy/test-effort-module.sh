#!/bin/bash
# 工时明细（effort）接口测试
#
# 禅道语义：一条工时记录 = 谁、哪天、为哪个任务花了多久、**这之后还剩多久**。
#   task.consumed = 所有工时之和
#   task.left     = **最后一条**工时里声明的值（不是 预计 - 已消耗）
#   剩余归零 → 任务自动 done；已完成的任务又冒出剩余 → 退回 doing
#   工时被删光 → 任务退回 wait，剩余回到 estimate，完成/取消/关闭痕迹清空
#
# 这些规则对应禅道 module/task/tao.php#getTaskAfterDeleteWorkhour（禅道是增量维护，
# 本实现改成「每次改动后从流水重算」，结果一致但不会漏掉某个特判）。
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
    PASS=$((PASS+1)); printf '  ✅ %-46s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-46s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }

# 任务当前的 预计/已消耗/剩余/状态
stat() {
  curl -s "${H[@]}" "$BASE/zentao/effort/task-stat?taskId=$1" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('%.2f %.2f %.2f %s' % (float(d['estimate'] or 0), float(d['consumed'] or 0), float(d['left'] or 0), d['status']))"
}

echo "===== 工时明细模块测试 ====="
TS=$(date +%s)

echo "--- 1. 演示数据：consumed = 工时之和，left = 最后一条 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/effort/task-stat?taskId=1")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
efforts=d['efforts']
s=sum(float(e['consumed'] or 0) for e in efforts)
dates=[e['date'] for e in efforts]
print(len(efforts), '%.2f' % s, '%.2f' % float(d['consumed'] or 0),
      '%.2f' % float(efforts[-1]['left'] or 0), '%.2f' % float(d['left'] or 0),
      dates == sorted(dates))")
set -- ${line}
[ "$1" -ge 2 ] && [ "$2" = "$3" ] && [ "$4" = "$5" ] && [ "$6" = "True" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 演示任务 1：${line}（条数、SUM=consumed、最后一条left=task.left、日期正序）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示任务 1 自洽性 实际=${line}"; }

echo "--- 2. 新建任务，逐条登记工时 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/task/create" \
  -d "{\"project\":1,\"execution\":90001,\"name\":\"工时任务-${TS}\",\"type\":\"devel\",\"pri\":3,\"estimate\":8}")
check "准备：新建预计 8 小时的任务" 0 "$R"
TASK=$(echo "$R" | d)
line=$(stat "${TASK}")
[ "${line}" = "8.00 0.00 8.00 wait" ] && { PASS=$((PASS+1)); echo "  ✅ 新任务 estimate=8、left 默认等于 estimate = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新任务初始 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK},\"date\":\"2026-06-01\",\"consumed\":3,\"left\":5,\"work\":\"搭骨架\",\"begin\":\"0900\",\"end\":\"1200\"}")
check "登记第 1 条工时（消耗 3，剩 5）" 0 "$R"
E1=$(echo "$R" | d)
line=$(stat "${TASK}")
[ "${line}" = "8.00 3.00 5.00 wait" ] && { PASS=$((PASS+1)); echo "  ✅ 消耗 3、剩 5、状态仍是 wait = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 第 1 条后 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK},\"date\":\"2026-06-02\",\"consumed\":2,\"left\":0,\"work\":\"收尾\"}")
check "登记第 2 条工时（消耗 2，剩 0）" 0 "$R"
E2=$(echo "$R" | d)
line=$(stat "${TASK}")
[ "${line}" = "8.00 5.00 0.00 done" ] && { PASS=$((PASS+1)); echo "  ✅ 剩余归零 → 任务自动 done = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 剩余归零 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK},\"date\":\"2026-06-03\",\"consumed\":1,\"left\":3,\"work\":\"发现还要补联调\"}")
check "登记第 3 条工时（消耗 1，剩 3）" 0 "$R"
E3=$(echo "$R" | d)
line=$(stat "${TASK}")
[ "${line}" = "8.00 6.00 3.00 doing" ] && { PASS=$((PASS+1)); echo "  ✅ 已完成又冒出剩余 → 退回 doing = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 退回进行中 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK},\"date\":\"2026-06-04\",\"consumed\":2,\"work\":\"不填剩余，按当前剩余推算\"}")
check "登记第 4 条工时（不填剩余）" 0 "$R"
E4=$(echo "$R" | d)
line=$(stat "${TASK}")
[ "${line}" = "8.00 8.00 1.00 doing" ] && { PASS=$((PASS+1)); echo "  ✅ 不填剩余时按「当前剩余 3 - 本次 2」推算 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 剩余推算 实际=${line}"; }

echo "--- 3. 改一条工时，任务工时总和跟着变 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/effort/update" \
  -d "{\"id\":${E1},\"taskId\":${TASK},\"date\":\"2026-06-01\",\"consumed\":1,\"left\":5,\"work\":\"搭骨架（改小了）\"}")
check "把第 1 条的消耗 3 改成 1" 0 "$R"
line=$(stat "${TASK}")
[ "${line}" = "8.00 6.00 1.00 doing" ] && { PASS=$((PASS+1)); echo "  ✅ 消耗变成 1+2+1+2=6，剩余仍是最后一条的 1 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修改后 实际=${line}"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/effort/update" \
  -d "{\"id\":${E4},\"taskId\":${TASK},\"date\":\"2026-06-04\",\"consumed\":2,\"left\":0,\"work\":\"不填剩余\"}")
check "把最后一条的剩余改成 0" 0 "$R"
line=$(stat "${TASK}")
[ "${line}" = "8.00 6.00 0.00 done" ] && { PASS=$((PASS+1)); echo "  ✅ 最后一条剩余归零 → done = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 最后一条归零 实际=${line}"; }

# 复原成剩 1，继续后面的删除测试
curl -s "${H[@]}" -X PUT "$BASE/zentao/effort/update" \
  -d "{\"id\":${E4},\"taskId\":${TASK},\"date\":\"2026-06-04\",\"consumed\":2,\"left\":1,\"work\":\"不填剩余\"}" > /dev/null

echo "--- 4. 删除工时：删非最后一条、删最后一条、删光 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/effort/delete?id=${E2}")
check "删掉中间那条（6/2，消耗 2）" 0 "$R"
line=$(stat "${TASK}")
[ "${line}" = "8.00 4.00 1.00 doing" ] && { PASS=$((PASS+1)); echo "  ✅ 消耗 6-2=4，剩余还是最后一条的 1 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删中间那条 实际=${line}"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/effort/delete?id=${E4}")
check "删掉最后一条（6/4，剩 1）" 0 "$R"
line=$(stat "${TASK}")
[ "${line}" = "8.00 2.00 3.00 doing" ] && { PASS=$((PASS+1)); echo "  ✅ 剩余回到新的最后一条（6/3 声明的 3）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删最后一条 实际=${line}"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/effort/delete?id=${E3}")
check "删掉 6/3 那条" 0 "$R"
line=$(stat "${TASK}")
[ "${line}" = "8.00 1.00 5.00 doing" ] && { PASS=$((PASS+1)); echo "  ✅ 只剩第 1 条（消耗 1、剩 5）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 只剩一条 实际=${line}"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/effort/delete?id=${E1}")
check "删光最后一条工时" 0 "$R"
line=$(stat "${TASK}")
[ "${line}" = "8.00 0.00 8.00 wait" ] && { PASS=$((PASS+1)); echo "  ✅ 工时删光 → 退回 wait、剩余回到 estimate、消耗归零 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删光 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/effort/task-stat?taskId=${TASK}")
n=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['effortCount'])")
R=$(curl -s "${H[@]}" "$BASE/zentao/task/get?id=${TASK}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['status'], repr(d['finishedBy'] or ''), d['finishedDate'] or 'null', repr(d['closedReason'] or ''))")
[ "${n}" = "0" ] && [ "${line}" = "wait '' null ''" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 工时条数归零，完成人/完成时间/关闭原因一并清空 = [${line}]"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 痕迹清理 条数=${n} 实际=[${line}]"; }

echo "--- 4b. 未开始的任务删光工时：剩余保持任务字段原值 ---"
# 禅道口径：只有「非未开始」的任务才会被退回未开始并把剩余改回预计工时；
# 未开始的任务删完工时只把已消耗归零，任务自己的 left 不动（可能是在表单里手填的）。
curl -s "${H[@]}" -X PUT "$BASE/zentao/task/update" \
  -d "{\"id\":${TASK},\"project\":1,\"execution\":90001,\"name\":\"工时任务-${TS}\",\"type\":\"devel\",\"pri\":3,\"estimate\":8,\"left\":8}" > /dev/null
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK},\"date\":\"2026-06-09\",\"consumed\":3,\"left\":5,\"work\":\"未开始时报的工时\"}")
E5=$(echo "$R" | d)
check "未开始的任务登记工时（消耗 3、剩 5）" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/effort/delete?id=${E5}")
check "再删掉它" 0 "$R"
line=$(stat "${TASK}")
[ "${line}" = "8.00 0.00 5.00 wait" ] && { PASS=$((PASS+1)); echo "  ✅ 未开始的任务：已消耗归零、剩余保持任务字段原值 5 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未开始任务删光工时 实际=${line}"; }

echo "--- 5. 状态联动：done 的任务删完工时不留「假完成」---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/task/start?id=${TASK}")
check "准备：把任务开始（wait → doing）" 0 "$R"
curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK},\"date\":\"2026-06-05\",\"consumed\":8,\"left\":0,\"work\":\"一把做完\"}" > /dev/null
line=$(stat "${TASK}")
[ "${line}" = "8.00 8.00 0.00 done" ] && { PASS=$((PASS+1)); echo "  ✅ 一次报满 8 小时、剩余 0 → done = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 报满 实际=${line}"; }

echo "--- 6. 参数与权限校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK},\"date\":\"2026-06-06\",\"consumed\":-1,\"work\":\"负数\"}")
check "消耗工时为负 → 拒绝" 400 "$R"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK},\"date\":\"2026-06-06\",\"consumed\":1}")
check "不填工作内容 → 拒绝" 400 "$R" "工作内容不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"date\":\"2026-06-06\",\"consumed\":1,\"work\":\"没任务\"}")
check "不填任务编号 → 拒绝" 400 "$R"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":99999999,\"date\":\"2026-06-06\",\"consumed\":1,\"left\":0,\"work\":\"不存在的任务\"}")
check "任务不存在 → 拒绝" 1020002000 "$R" "任务不存在"

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/effort/delete?id=88888888")
check "删除不存在的工时 → 拒绝" 1020019000 "$R" "工时记录不存在"

# 工时不能改挂到别的任务上
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/task/create" \
  -d "{\"project\":1,\"execution\":90001,\"name\":\"另一个任务-${TS}\",\"type\":\"devel\",\"pri\":3,\"estimate\":4}")
TASK2=$(echo "$R" | d)
RE=$(curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK},\"date\":\"2026-06-07\",\"consumed\":1,\"left\":0,\"work\":\"待在原任务\"}")
REID=$(echo "$RE" | d)
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/effort/update" \
  -d "{\"id\":${REID},\"taskId\":${TASK2},\"date\":\"2026-06-07\",\"consumed\":1,\"left\":0,\"work\":\"改挂\"}")
check "把工时改挂到另一个任务 → 拒绝" 1020019001 "$R" "不能改挂"

R=$(curl -s "${H[@]}" -X GET "$BASE/zentao/effort/get?id=${REID}")
check "按编号查单条工时" 0 "$R"
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['objectID'], d['account'], d['work'], d['objectType'])")
[ "${line}" = "${TASK} admin 待在原任务 task" ] && { PASS=$((PASS+1)); echo "  ✅ 单条工时归属正确 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 单条工时 实际=${line}"; }

echo "--- 7. 列表 / 分页 / 汇总 ---"
# 换一个干净的任务来验证查询口径，免得受前面增删改的影响
curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK2},\"date\":\"2026-07-01\",\"consumed\":2,\"left\":0,\"work\":\"甲干的活\"}" > /dev/null
curl -s "${H[@]}" -X POST "$BASE/zentao/effort/create" \
  -d "{\"taskId\":${TASK2},\"date\":\"2026-07-02\",\"consumed\":1.5,\"left\":0,\"account\":\"tester\",\"work\":\"乙干的活\"}" > /dev/null

R=$(curl -s "${H[@]}" "$BASE/zentao/effort/list?taskId=${TASK2}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), d[0]['date'], d[-1]['date'], all(x['taskName'] for x in d))")
[ "${line}" = "2 2026-07-01 2026-07-02 True" ] && { PASS=$((PASS+1)); echo "  ✅ 任务工时列表按日期正序且回填任务名 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 工时列表 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/effort/page?pageNo=1&pageSize=50&taskId=${TASK2}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], len(d['list']))")
[ "${line}" = "2 2" ] && { PASS=$((PASS+1)); echo "  ✅ 按任务分页 total=2 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 分页 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/effort/page?pageNo=1&pageSize=50&account=tester&taskId=${TASK2}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'])")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 按账号过滤 total=1 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 账号过滤 实际=${line}"; }

# 两点注意：curl 会把 [0] 当通配符（要 -g）；而 Tomcat 直接拒收未编码的 [ ]（400），
# 所以要用 %5B0%5D —— 浏览器/axios 发出的就是这种编码形式
R=$(curl -s "${H[@]}" "$BASE/zentao/effort/page?pageNo=1&pageSize=50&taskId=${TASK2}&date%5B0%5D=2026-07-02&date%5B1%5D=2026-07-03")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['total'], d['list'][0]['date'])")
[ "${line}" = "1 2026-07-02" ] && { PASS=$((PASS+1)); echo "  ✅ 按日期区间过滤 total=1 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 日期区间 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/effort/summary?taskId=${TASK2}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), d[0]['account'], '%.2f' % float(d[0]['consumed']), d[0]['taskCount'], d[0]['effortCount'],
      d[1]['account'], '%.2f' % float(d[1]['consumed']))")
[ "${line}" = "2 admin 2.00 1 1 tester 1.50" ] && { PASS=$((PASS+1)); echo "  ✅ 按账号汇总：降序、任务数/条数/合计都对 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 汇总 实际=${line}"; }

echo "--- 8. 操作日志里留下了工时记录 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/action/list?objectType=task&objectID=${TASK}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
hits=[a for a in d if a['action']=='recordworkhour']
print(len(hits), (hits[0]['comment'] or '')[:12] if hits else '')")
set -- ${line}
[ "$1" -ge 4 ] && [[ "$2" == 消耗* || "$2" == 修改工时* || "$2" == 删除工时* ]] \
  && { PASS=$((PASS+1)); echo "  ✅ 任务的动态里有 ${1} 条 recordworkhour 记录（最新：${2}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 操作日志 实际=${line}"; }

echo "--- 9. 任务被删了、工时还在：悬空引用要能扛住 ---"
# 先把任务删掉，工时留着 —— 这是链路里最常见的脏数据（删任务时没级联清工时）
curl -s "${H[@]}" -X DELETE "$BASE/zentao/task/delete?id=${TASK2}" > /dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/effort/page?pageNo=1&pageSize=50&taskId=${TASK2}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(d['code'], d['data']['total'], all(x['taskName'] is None for x in d['data']['list']))")
[ "${line}" = "0 2 True" ] && { PASS=$((PASS+1)); echo "  ✅ 任务已删除时工时列表照常返回，taskName 为空而不是 500 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 悬空引用 实际=${line}"; }

echo "--- 10. 清理 ---"
for t in ${TASK} ${TASK2}; do
  for id in $(curl -s "${H[@]}" "$BASE/zentao/effort/list?taskId=${t}" | python3 -c 'import sys,json;print(" ".join(str(x["id"]) for x in json.load(sys.stdin)["data"]))'); do
    curl -s "${H[@]}" -X DELETE "$BASE/zentao/effort/delete?id=${id}" > /dev/null
  done
done
curl -s "${H[@]}" -X DELETE "$BASE/zentao/task/delete?id=${TASK}" > /dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/effort/list?taskId=${TASK}")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
R=$(curl -s "${H[@]}" "$BASE/zentao/effort/list?taskId=${TASK2}")
m=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "${n}" = "0" ] && [ "${m}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 测试任务的工时已清空"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 残留工时 TASK=${n} TASK2=${m}"; }

# 全库不应该留下「任务没了、工时还在」的孤儿
orphan=$(curl -s "${H[@]}" "$BASE/zentao/effort/page?pageNo=1&pageSize=200&taskId=1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "${orphan}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 演示任务 1 的 2 条工时完好"; } || { FAIL=$((FAIL+1)); echo "  ❌ 演示工时=${orphan}"; }

# 演示数据不能被测试脚本破坏
line=$(stat 1)
[ "${line}" = "8.00 7.00 1.00 closed" ] && { PASS=$((PASS+1)); echo "  ✅ 演示任务 1 仍是 8/7/1 closed"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示任务被改动 实际=${line}"; }

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

#!/bin/bash
# 测试单（testtask）模块接口测试
#
# 禅道语义：测试单 zt_testtask + 排进来的用例 zt_testrun（UNIQUE(task,case)）
#           + 每次执行的 zt_testresult 历史。
# 本脚本重点验证 6 件事：
#   1. 执行一次会**写三处**：结果历史、用例的最近执行结果、run 的状态与结果
#   2. 用例级结果由步骤结果算出：默认 pass，非 pass/n-a 以它为准，**fail 优先**
#   3. 重新关联已排过的用例**不会抹掉**已有执行结果（本实现有意偏离禅道的 REPLACE）
#   4. 状态机：wait→doing→done，doing⇄blocked，关闭校验完成时间
#   5. 排进来的用例必须与测试单同产品
#   6. 移除用例 / 删除测试单时，run 与结果历史一起物理清理
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
# 在服务端跑一段 python，直接查库校验（比接口更能证明「真的写进去了」）
# 直接查库的辅助函数（本机栈 / 远端自动选择，见 _mysql.sh）
source "$(dirname "$0")/_mysql.sh"
sql() { mysql_query "$1"; }

echo "===== 测试单（testtask）模块测试 ====="
TS=$(date +%s)

echo "--- 1. 列表与统计（演示数据）---"
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/page?product=1&pageSize=20")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
m={x['id']:x for x in d['list']}
t=m[94101]
print(d['total'], t['statusName'], t['caseCount'], t['passCount'], t['failCount'], t['unexecutedCount'], t['buildName'])")
[ "$line" = "3 进行中 3 1 1 1 V1.0-beta1" ] && { PASS=$((PASS+1)); echo "  ✅ 测试单 94101 统计 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 统计 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/page?product=1&type=interface")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['total'], d['list'][0]['name'])")
[ "$line" = "1 迭代 1 功能测试" ] && { PASS=$((PASS+1)); echo "  ✅ type 逗号列表过滤（FIND_IN_SET）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ type 过滤 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/page?product=1&status=done")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 按状态过滤（已关闭 1 条）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 状态过滤=${n}"; }

echo "--- 2. 执行列表（run）---"
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/run-list?taskId=94101")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), '|'.join(str(x['caseId']) for x in d), '|'.join(x['lastRunResultName'] for x in d))")
[ "$line" = "3 93101|93102|93103 通过|失败|未执行" ] && { PASS=$((PASS+1)); echo "  ✅ run 列表 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ run 列表 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/run-list?taskId=94101")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
x=[i for i in d if i['caseId']==93101][0]
print(x['caseVersion'], x['latestCaseVersion'], x['caseChanged'], x['stepCount'])")
[ "$line" = "2 2 False 6" ] && { PASS=$((PASS+1)); echo "  ✅ 排进来的用例版本/步骤数 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用例版本 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/linkable-list?taskId=94101")
# 只断言「已排的不在、演示里没排的都在」，不写死条数 ——
# 跑测残留会往产品 1 里加用例，写死条数就会假失败
line=$(echo "$R" | python3 -c "
import sys,json
ids={x['caseId'] for x in json.load(sys.stdin)['data']}
print('ok' if {93104,93105} <= ids and not ({93101,93102,93103} & ids) else sorted(ids))")
[ "$line" = "ok" ] && { PASS=$((PASS+1)); echo "  ✅ 可排入的用例（排除已排的 93101-93103）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ linkable 实际=$line"; }

echo "--- 3. 准备一条干净的用例与测试单 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" -d "{\"product\":1,\"title\":\"TT用例-${TS}\",\"type\":\"feature\",\"steps\":[{\"type\":\"step\",\"desc\":\"第一步\",\"expect\":\"OK\"},{\"type\":\"step\",\"desc\":\"第二步\",\"expect\":\"OK\"},{\"type\":\"step\",\"desc\":\"第三步\",\"expect\":\"OK\"}]}")
check "准备：新建 3 步用例" 0 "$R"
CASE=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/step-list?id=${CASE}")
read -r S1 S2 S3 < <(echo "$R" | python3 -c 'import sys,json;print(" ".join(str(x["id"]) for x in json.load(sys.stdin)["data"]))')
echo "    用例=${CASE} 步骤=${S1},${S2},${S3}"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/create" -d "{\"product\":1,\"name\":\"TT测试单-${TS}\",\"type\":\"feature\",\"begin\":\"2026-04-01\",\"end\":\"2026-04-10\"}")
check "新建测试单" 0 "$R"
TASK=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/create" -d "{\"product\":1,\"name\":\"日期非法-${TS}\",\"begin\":\"2026-04-10\",\"end\":\"2026-04-01\"}")
check "结束早于开始→拒绝" 1020017001 "$R" "不能早于开始"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/link-case" -d "{\"taskId\":${TASK},\"caseIds\":[${CASE}]}")
[ "$(echo "$R" | d)" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 用例排进测试单"; } || { FAIL=$((FAIL+1)); echo "  ❌ link-case=$(echo "$R" | d)"; }
RUN=$(curl -s "${H[@]}" "$BASE/zentao/testtask/run-list?taskId=${TASK}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"][0]["id"])')
echo "    run=${RUN}"

echo "--- 4. 执行结果聚合规则（核心）---"
# 4.1 全 pass → pass
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/run-case" -d "{\"runId\":${RUN},\"stepResults\":[{\"id\":${S1},\"result\":\"pass\"},{\"id\":${S2},\"result\":\"pass\"},{\"id\":${S3},\"result\":\"pass\"}]}")
[ "$(echo "$R" | d)" = "pass" ] && { PASS=$((PASS+1)); echo "  ✅ 全 pass → pass"; } || { FAIL=$((FAIL+1)); echo "  ❌ 全 pass=$(echo "$R" | d)"; }

# 4.2 pass + n/a → pass（n/a 不影响）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/run-case" -d "{\"runId\":${RUN},\"stepResults\":[{\"id\":${S1},\"result\":\"pass\"},{\"id\":${S2},\"result\":\"n/a\"},{\"id\":${S3},\"result\":\"pass\"}]}")
[ "$(echo "$R" | d)" = "pass" ] && { PASS=$((PASS+1)); echo "  ✅ pass + n/a → pass（n/a 不影响）"; } || { FAIL=$((FAIL+1)); echo "  ❌ n/a=$(echo "$R" | d)"; }

# 4.3 blocked 在前、fail 在后 → fail（fail 优先，不是按顺序取第一个）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/run-case" -d "{\"runId\":${RUN},\"stepResults\":[{\"id\":${S1},\"result\":\"blocked\"},{\"id\":${S2},\"result\":\"fail\"},{\"id\":${S3},\"result\":\"pass\"}]}")
[ "$(echo "$R" | d)" = "fail" ] && { PASS=$((PASS+1)); echo "  ✅ blocked 在前 + fail 在后 → fail（fail 优先）"; } || { FAIL=$((FAIL+1)); echo "  ❌ fail优先=$(echo "$R" | d)"; }

# 4.4 只有 blocked → blocked（且用例状态变成「被阻塞」）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/run-case" -d "{\"runId\":${RUN},\"stepResults\":[{\"id\":${S1},\"result\":\"pass\"},{\"id\":${S2},\"result\":\"blocked\"},{\"id\":${S3},\"result\":\"pass\"}]}")
[ "$(echo "$R" | d)" = "blocked" ] && { PASS=$((PASS+1)); echo "  ✅ 只有 blocked → blocked"; } || { FAIL=$((FAIL+1)); echo "  ❌ blocked=$(echo "$R" | d)"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=${CASE}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['lastRunResult'], d['lastRunner'], d['statusName'])")
[ "$line" = "blocked admin 被阻塞" ] && { PASS=$((PASS+1)); echo "  ✅ 结果回写到用例（含状态→被阻塞）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 回写用例 实际=$line"; }

# 4.5 步骤不属于该用例 → 拒绝
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/run-case" -d "{\"runId\":${RUN},\"stepResults\":[{\"id\":99999999,\"result\":\"pass\"}]}")
check "步骤不属于该用例→拒绝" 1020017015 "$R" "不属于该用例"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/run-case" -d "{\"runId\":${RUN},\"stepResults\":[{\"id\":${S1},\"result\":\"xxx\"}]}")
check "结果值非法→拒绝" 1020017014 "$R" "执行结果不合法"

echo "--- 5. 一次执行写三处（历史 / 用例 / run）---"
n=$(sql "SELECT COUNT(*) FROM \\\`ruoyi-vue-pro\\\`.zt_testresult WHERE run=${RUN};")
[ "$n" = "4" ] && { PASS=$((PASS+1)); echo "  ✅ 执行 4 次 → 4 条结果历史"; } || { FAIL=$((FAIL+1)); echo "  ❌ 结果历史=${n}（期望 4）"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/run-list?taskId=${TASK}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'][0];print(d['lastRunResult'], d['status'], d['lastRunner'])")
[ "$line" = "blocked blocked admin" ] && { PASS=$((PASS+1)); echo "  ✅ run 上也写了结果与状态 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ run 回写 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/result-list?runId=${RUN}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), '|'.join(x['caseResult'] for x in d))")
[ "$line" = "4 blocked|fail|pass|pass" ] && { PASS=$((PASS+1)); echo "  ✅ 结果历史（最新在前）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 结果历史 实际=$line"; }

echo "--- 6. 重新关联不抹掉执行结果（有意偏离禅道）---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/link-case" -d "{\"taskId\":${TASK},\"caseIds\":[${CASE}]}")
[ "$(echo "$R" | d)" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 重复关联返回 1（幂等）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 重复关联=$(echo "$R" | d)"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/run-list?taskId=${TASK}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(len(d), d[0]['id'], d[0]['lastRunResult'])")
[ "$line" = "1 ${RUN} blocked" ] && { PASS=$((PASS+1)); echo "  ✅ 重复关联后 run 还是同一条、结果还在 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 重复关联后 实际=$line"; }
n=$(sql "SELECT COUNT(*) FROM \\\`ruoyi-vue-pro\\\`.zt_testresult WHERE run=${RUN};")
[ "$n" = "4" ] && { PASS=$((PASS+1)); echo "  ✅ 结果历史也没被清（禅道 REPLACE 会清空）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 历史=${n}"; }

echo "--- 7. 跨产品用例不能排进来 ---"
# 必须造一条**真属于别的产品**的用例：演示数据里的 93101 属于产品 1，
# 拿它当「别的产品」等于自测（而且会把 93101 误排进本测试单，污染后面的断言）
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" -d "{\"product\":3,\"title\":\"别的产品用例-${TS}\",\"type\":\"feature\",\"steps\":[{\"type\":\"step\",\"desc\":\"x\",\"expect\":\"y\"}]}")
check "准备：产品 3 的用例" 0 "$R"
FOREIGN=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/link-case" -d "{\"taskId\":${TASK},\"caseIds\":[${FOREIGN}]}")
check "别的产品的用例→拒绝" 1020017012 "$R" "不属于该测试单的产品"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/link-case" -d "{\"taskId\":${TASK},\"caseIds\":[]}")
code=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "$code" = "400" ] && { PASS=$((PASS+1)); echo "  ✅ 空用例列表被参数校验拦下 code=${code}"; } || { FAIL=$((FAIL+1)); echo "  ❌ 空列表 code=${code}"; }

echo "--- 8. 用例变更提示 ---"
curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/update" -d "{\"id\":${CASE},\"product\":1,\"title\":\"TT用例-${TS}\",\"type\":\"feature\",\"steps\":[{\"type\":\"step\",\"desc\":\"改了步骤\",\"expect\":\"OK\"}]}" > /dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/run-list?taskId=${TASK}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'][0];print(d['caseVersion'], d['latestCaseVersion'], d['caseChanged'])")
[ "$line" = "1 2 True" ] && { PASS=$((PASS+1)); echo "  ✅ 排进来后用例升版 → caseChanged=True = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ caseChanged 实际=$line"; }

echo "--- 9. 状态机 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testtask/start?id=${TASK}")
check "开始测试单" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/get?id=${TASK}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['statusName'], d['realBegan'])")
[ "$line" = "进行中 $(date +%F)" ] && { PASS=$((PASS+1)); echo "  ✅ wait → doing，写入实际开始日期 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 开始后 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testtask/start?id=${TASK}")
check "重复开始→拒绝" 1020017002 "$R" "不允许执行该操作"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testtask/block?id=${TASK}" -d "{\"comment\":\"环境未就绪\"}")
check "阻塞测试单" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/get?id=${TASK}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['statusName'])")
[ "$line" = "被阻塞" ] && { PASS=$((PASS+1)); echo "  ✅ doing → blocked"; } || { FAIL=$((FAIL+1)); echo "  ❌ 阻塞后=${line}"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testtask/close?id=${TASK}" -d "{}")
check "关闭不填完成时间→拒绝" 1020017003 "$R" "必须填写实际完成时间"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testtask/close?id=${TASK}" -d "{\"realFinishedDate\":\"2026-03-01 10:00:00\"}")
check "完成时间早于计划开始→拒绝" 1020017004 "$R" "不能早于计划开始日期"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testtask/close?id=${TASK}" -d "{\"realFinishedDate\":\"2099-01-01 10:00:00\"}")
check "完成时间晚于明天→拒绝" 1020017005 "$R" "不能晚于明天"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testtask/close?id=${TASK}" -d "{\"realFinishedDate\":\"$(date +%F) 18:00:00\",\"report\":\"测完了\"}")
check "正常关闭" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/get?id=${TASK}")
line=$(echo "$R" | python3 -c "
import sys, json, datetime
d = json.load(sys.stdin)['data']
v = d['realFinishedDate']
# yudao 的 TimestampLocalDateTimeSerializer 把 LocalDateTime 序列化成毫秒时间戳，
# 前端 formatDate 两种都能吃，所以这里两种形态都接受
day = datetime.datetime.fromtimestamp(v / 1000).strftime('%Y-%m-%d') if isinstance(v, (int, float)) else str(v)[:10]
print(d['statusName'], d['report'], day)")
[ "$line" = "已关闭 测完了 $(date +%F)" ] && { PASS=$((PASS+1)); echo "  ✅ doing → done + 测试总结 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 关闭后 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testtask/activate?id=${TASK}" -d "{\"comment\":\"还要再测\"}")
check "激活（已关闭 → 进行中）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/get?id=${TASK}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['statusName'], d['realFinishedDate'], d['report'])")
[ "$line" = "进行中 None None" ] && { PASS=$((PASS+1)); echo "  ✅ 激活后清空完成时间与测试总结（第 19 条坑的写法）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 激活后 实际=$line"; }

echo "--- 10. 执行失败 → 建缺陷（测试链的最后一环）---"
# 用本脚本自己造的 run（${RUN} / 用例 ${CASE}）：不碰演示数据的用例，
# 因为验证「版本冻结」需要让用例升版，动演示数据会把它改坏
# 注意 stepId 要用**当前版本**的步骤 id：第 8 节改过步骤，v1 的步骤 id 已经不属于 v2 了
NOWSTEP=$(curl -s "${H[@]}" "$BASE/zentao/testcase/step-list?id=${CASE}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"][0]["id"])')
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/create-bug" -d "{\"runId\":${RUN},\"stepId\":${NOWSTEP},\"title\":\"TT缺陷-${TS}\",\"severity\":2,\"pri\":1,\"type\":\"code\"}")
check "从用例执行结果建缺陷" 0 "$R"
BUG=$(echo "$R" | d)

# 溯源：归属来自测试单（产品）与用例（分支/模块/需求）；来源用例+版本+测试单写进缺陷
# 注意 caseVersion 记的是**实际执行的那一版**（用例当前版本 = 第 8 节改过步骤后的 v2）
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=${BUG}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['product'], d['branch'], d['module'], d['story'], d['caseId'], d['caseVersion'], d['testtask'], d['severity'], d['pri'])")
[ "$line" = "1 0 0 0 ${CASE} 2 ${TASK} 2 1" ] && { PASS=$((PASS+1)); echo "  ✅ 溯源三件套 + 归属正确 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 缺陷归属 实际=$line（期望 1 0 0 0 ${CASE} 2 ${TASK} 2 1）"; }

# 复现步骤：不填就用「用例步骤」自动生成，并标出指定的失败步骤
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=${BUG}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
steps=d['steps'] or ''
print('yes' if ('来源用例 #${CASE}' in steps and '← 这一步失败' in steps and '预期：' in steps) else repr(steps[:70]))")
[ "$line" = "yes" ] && { PASS=$((PASS+1)); echo "  ✅ 复现步骤自动生成，并标出失败的步骤"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 复现步骤 实际=$line"; }

# 手动传 steps 时不覆盖
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/create-bug" -d "{\"runId\":${RUN},\"title\":\"TT缺陷2-${TS}\",\"steps\":\"我自己写的复现步骤\"}")
BUG2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=${BUG2}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['steps'])")
[ "$line" = "我自己写的复现步骤" ] && { PASS=$((PASS+1)); echo "  ✅ 手动传复现步骤时不被自动内容覆盖"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 手动步骤 实际=$line"; }

# 用例 / 测试单都能反查缺陷
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/bug-list-by-case?caseId=${CASE}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('yes' if {${BUG},${BUG2}} <= {x['id'] for x in d} else [x['id'] for x in d])")
[ "$line" = "yes" ] && { PASS=$((PASS+1)); echo "  ✅ 按用例反查缺陷"; } || { FAIL=$((FAIL+1)); echo "  ❌ 反查用例 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/bug-list-by-task?taskId=${TASK}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('yes' if {${BUG},${BUG2}} <= {x['id'] for x in d} else [x['id'] for x in d])")
[ "$line" = "yes" ] && { PASS=$((PASS+1)); echo "  ✅ 按测试单反查缺陷"; } || { FAIL=$((FAIL+1)); echo "  ❌ 反查测试单 实际=$line"; }

# 版本冻结：用例再升一版，缺陷仍指向当初执行的那一版
curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/update" -d "{\"id\":${CASE},\"product\":1,\"title\":\"TT用例-${TS}\",\"type\":\"feature\",\"steps\":[{\"type\":\"step\",\"desc\":\"又改了\",\"expect\":\"x\"}]}" > /dev/null
now=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=${CASE}" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['version'])")
kept=$(curl -s "${H[@]}" "$BASE/zentao/bug/get?id=${BUG}" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['caseVersion'])")
[ "$kept" = "2" ] && [ "$now" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 用例升到 v${now} 后缺陷仍指向 v${kept}（冻结值）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 冻结值 实际=缺陷v${kept} 用例v${now}"; }

# 校验分支
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/create-bug" -d "{\"runId\":99999999,\"title\":\"x-${TS}\"}")
check "run 不存在→拒绝" 1020017010 "$R" "测试单里没有这条用例"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testtask/create-bug" -d "{\"runId\":${RUN},\"title\":\"\"}")
code=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "$code" = "400" ] && { PASS=$((PASS+1)); echo "  ✅ 缺标题被参数校验拦下 code=${code}"; } || { FAIL=$((FAIL+1)); echo "  ❌ 缺标题 code=${code}"; }

echo "--- 11. 移除用例 / 删除测试单的物理清理 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/testtask/unlink-case?runId=${RUN}")
check "从测试单移除用例" 0 "$R"
n=$(sql "SELECT (SELECT COUNT(*) FROM \\\`ruoyi-vue-pro\\\`.zt_testrun WHERE id=${RUN}) + (SELECT COUNT(*) FROM \\\`ruoyi-vue-pro\\\`.zt_testresult WHERE run=${RUN});")
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ run 与它的执行历史一起物理清理"; } || { FAIL=$((FAIL+1)); echo "  ❌ 残留=${n}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/run-list?taskId=${TASK}")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 移除后 run 列表为空"; } || { FAIL=$((FAIL+1)); echo "  ❌ run 列表=${n}"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/testtask/delete?id=${TASK}")
check "删除测试单" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testtask/get?id=${TASK}")
check "删除后再查→不存在" 1020017000 "$R" "测试单不存在"

# 演示测试单的 run/结果不能被误删
n=$(sql "SELECT (SELECT COUNT(*) FROM \\\`ruoyi-vue-pro\\\`.zt_testrun WHERE task=94101) + (SELECT COUNT(*) FROM \\\`ruoyi-vue-pro\\\`.zt_testrun WHERE task=94103);")
[ "$n" = "5" ] && { PASS=$((PASS+1)); echo "  ✅ 演示测试单的用例未被误删（5 条 run）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 演示 run=${n}"; }

# 清理：本次造的缺陷删掉；用例 93102 的步骤被本脚本改过（为了验证版本冻结），也删掉重建
curl -s "${H[@]}" -X DELETE "$BASE/zentao/bug/delete?id=${BUG}" > /dev/null
# 清理：把本次造的缺陷也删掉
for b in ${BUG:-} ${BUG2:-}; do [ -n "$b" ] && curl -s "${H[@]}" -X DELETE "$BASE/zentao/bug/delete?id=$b" > /dev/null; done
# 清理：把本次造的用例也删掉（演示数据不动）
curl -s "${H[@]}" -X DELETE "$BASE/zentao/testcase/delete?id=${CASE}" > /dev/null
[ -n "${FOREIGN:-}" ] && curl -s "${H[@]}" -X DELETE "$BASE/zentao/testcase/delete?id=${FOREIGN}" > /dev/null

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

#!/bin/bash
# 执行燃尽图（burn）测试：快照计算 / 曲线补齐 / 理想线 / 延期段 / 采样与周末
#
# 禅道语义（module/execution 的 burn + computeBurn + buildBurnData）：
#   1. 燃尽图不是实时算的：computeBurn 把当天所有任务的 estimate/left/consumed/需求规模
#      汇总成一行写进 zt_burn（唯一键 execution+date，当天用 REPLACE 覆盖）；
#      历史快照不再改，所以任务后来被改被删，已经画出来的曲线不会跟着变
#   2. 某天没跑 computeBurn 就没有那一行，画图时要用**前一个有值的日期**补上（禅道 createSingleJSON）
#   3. 剩余 = Σ任务left − Σ已关闭任务的left；原计划 = Σ任务estimate − Σ已完成/关闭任务的estimate
#   4. 需求规模 = 本执行关联的、未关闭且阶段未完成的非父需求的 estimate 合计
#   5. 三条线：burnLine 实际（计划结束日之后为 null）/ baseLine 理想（首值线性降到计划结束日为 0）
#      / delayLine 延期段（只有真的过了计划结束日还没结束才给；与 burnLine 在计划结束日交汇）
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

echo "===== 执行燃尽图（快照 / 补齐 / 理想线 / 延期段）测试 ====="
TODAY=$(date +%F)
BEGIN=$(date -v-20d +%F 2>/dev/null || date -d '-20 days' +%F)
BEGIN10=$(date -v-10d +%F 2>/dev/null || date -d '-10 days' +%F)
END=$(date -v+10d +%F 2>/dev/null || date -d '+10 days' +%F)
YESTERDAY=$(date -v-1d +%F 2>/dev/null || date -d '-1 day' +%F)

# 燃尽图默认是 noweekend：labels 会**跳过周末与节假日**（HolidayService），所以「按日历日 index()」
# 在 BEGIN / BEGIN10 落到周末时根本取不到（2026-09-18 那次 BEGIN=周六 → 「首日值」断言拿到空）。
# 统一改成「labels 里第一个 >= 该日期的位置」，语义不变：快照落在周末时，值会被后一个工作日承接。
idx10='next(i for i,l in enumerate(d["labels"]) if l >= "'$BEGIN10'")'

echo "--- 1. 准备：一个跨越今天的迭代 + 三个任务 ---"
PID=1
EID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/execution/create" \
  -d "{\"name\":\"BURN-迭代-$TODAY\",\"project\":$PID,\"type\":\"sprint\",\"pri\":3,\"begin\":\"$BEGIN\",\"end\":\"$END\",\"PM\":\"admin\"}" | d)
[ -n "$EID" ] && { PASS=$((PASS+1)); echo "  ✅ 建迭代 $EID（$BEGIN ~ $END，跨今天）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建迭代失败"; }
T1=$(curl -s -X POST "${H[@]}" "$BASE/zentao/task/create" \
  -d "{\"project\":$PID,\"execution\":$EID,\"name\":\"BURN-任务1\",\"type\":\"devel\",\"pri\":3,\"estimate\":10,\"left\":10}" | d)
T2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/task/create" \
  -d "{\"project\":$PID,\"execution\":$EID,\"name\":\"BURN-任务2\",\"type\":\"devel\",\"pri\":3,\"estimate\":20,\"left\":15}" | d)
T3=$(curl -s -X POST "${H[@]}" "$BASE/zentao/task/create" \
  -d "{\"project\":$PID,\"execution\":$EID,\"name\":\"BURN-任务3-已取消\",\"type\":\"devel\",\"pri\":3,\"estimate\":99,\"left\":99}" | d)
[ -n "$T1" ] && [ -n "$T2" ] && [ -n "$T3" ] && { PASS=$((PASS+1)); echo "  ✅ 建三个任务（第三个待会儿取消，用来验证「取消的不算」）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建任务失败：$T1 $T2 $T3"; }
curl -s -X PUT "${H[@]}" "$BASE/zentao/task/cancel?id=$T3" >/dev/null
DB_CANCEL=$(mysql_query "SELECT status FROM \`ruoyi-vue-pro\`.zt_task WHERE id=$T3;")
[ "${DB_CANCEL}" = "cancel" ] && { PASS=$((PASS+1)); echo "  ✅ 任务3 已取消（status=cancel）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 取消任务 实际=${DB_CANCEL}"; }

echo "--- 2. computeBurn：把今天的汇总写成快照 ---"
R=$(curl -s -X POST "${HF[@]}" "$BASE/zentao/execution/compute-burn?id=$EID")
check "计算燃尽图" 0 "$R"
line=$(echo "$R" | d)
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 正好写了 1 条快照（一个执行一条）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 快照条数 实际=${line}"; }
DB_ROW=$(mysql_query "SELECT CONCAT(estimate,'|',\`left\`,'|',consumed,'|',storyPoint) FROM \`ruoyi-vue-pro\`.zt_burn WHERE execution=$EID AND \`date\`='$TODAY';")
# 口径：left=Σleft(未取消)−Σleft(已关闭)=25−0=25；estimate=Σestimate−Σdone/closed=30−0=30；consumed=0
[ "${DB_ROW}" = "30.00|25.00|0.00|0.00" ] && { PASS=$((PASS+1)); echo "  ✅ 快照口径正确：原计划 30 / 剩余 25 / 已消耗 0（已取消任务的 99 没算进来）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 快照内容 实际=${DB_ROW}"; }
# 再算一次不应产生第二行（REPLACE 覆盖）
curl -s -X POST "${HF[@]}" "$BASE/zentao/execution/compute-burn?id=$EID" >/dev/null
DB_CNT=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_burn WHERE execution=$EID;")
[ "${DB_CNT}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 重复计算只覆盖当天那一行（唯一键 execution+date）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 快照行数 实际=${DB_CNT}"; }

echo "--- 3. 插一条历史快照，验证「缺失日期用前一个值补齐」---"
mysql_exec "INSERT INTO \`ruoyi-vue-pro\`.zt_burn (execution, product, task, \`date\`, estimate, \`left\`, consumed, storyPoint) VALUES ($EID, 0, 0, '$BEGIN10', 60.00, 50.00, 5.00, 0.00) ON DUPLICATE KEY UPDATE \`left\`=50.00, estimate=60.00, consumed=5.00;" >/dev/null 2>&1
R=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID")
line=$(echo "$R" | field "len(d['labels']), len(d['burnLine']), len(d['baseLine'])")
[ "${line}" = "$(echo "$R" | field "len(d['labels'])") $(echo "$R" | field "len(d['labels'])") $(echo "$R" | field "len(d['labels'])")" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 三条线与 labels 等长（本实现用 null 对齐，禅道会截断数组）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 数组长度 实际=${line}"; }
# 昨天不一定是工作日（现在是周一 → 昨天是周日会被跳过），所以按 labels 里的真实位置断言
line=$(echo "$R" | field "$idx10, d['labels'].index('$TODAY'), set(d['burnLine'][$idx10:d['labels'].index('$TODAY')]), d['burnLine'][d['labels'].index('$TODAY')]")
[ "${line}" = "$(echo "$R" | field "$idx10") $(echo "$R" | field "d['labels'].index('$TODAY')") {50.0} 25.0" ] && { PASS=$((PASS+1)); echo "  ✅ 历史快照 50 一直补齐到昨天，今天换成真实值 25"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 补齐结果 实际=${line}"; }
line=$(echo "$R" | field "d['labels'][0], d['burnLine'][0]")
[ "$(echo "$line" | awk '{print $2}')" = "0.0" ] && { PASS=$((PASS+1)); echo "  ✅ 第一条快照之前（首个工作日 ${line%% *}）的值是 0（禅道 preValue 初值也是 0）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 首日值 实际=${line}"; }
line=$(echo "$R" | field "len([v for l,v in zip(d['labels'],d['burnLine']) if l > '$TODAY']) > 0, all(v is None for l,v in zip(d['labels'],d['burnLine']) if l > '$TODAY')")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ labels 延伸到了计划结束日，但今天之后的值全是 null（没数据）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未来点 实际=${line}"; }

echo "--- 4. 理想线 baseLine ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID")
line=$(echo "$R" | field "d['firstValue'], d['baseLine'][0], d['baseLine'][-1]")
[ "${line}" = "50.0 50.0 0.0" ] && { PASS=$((PASS+1)); echo "  ✅ 理想线从首值 50 线性降到计划结束日为 0"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 理想线 实际=${line}"; }
line=$(echo "$R" | field "all(d['baseLine'][i] >= d['baseLine'][i+1] for i in range(len(d['baseLine'])-1))")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 理想线单调不增"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 理想线单调性 实际=${line}"; }
line=$(echo "$R" | field "d['baseLine'][-1] == 0.0, d['baseLine'][len(d['baseLine'])//2] > 0")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 末点为 0、中点仍大于 0（不是一条平线）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 理想线形状 实际=${line}"; }

echo "--- 5. burnBy 取值字段 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID&burnBy=estimate")
line=$(echo "$R" | field "d['burnBy'], [v for l,v in zip(d['labels'],d['burnLine']) if l=='$TODAY'][0]")
[ "${line}" = "estimate 30.0" ] && { PASS=$((PASS+1)); echo "  ✅ burnBy=estimate 时曲线取原计划（今天 30）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ burnBy=estimate 实际=${line}"; }
R=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID&burnBy=consumed")
line=$(echo "$R" | field "d['burnBy'], d['burnLine'][$idx10]")
[ "${line}" = "consumed 5.0" ] && { PASS=$((PASS+1)); echo "  ✅ burnBy=consumed 取已消耗（历史快照那天 5）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ burnBy=consumed 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID&burnBy=xxx" | field "d['burnBy']")
[ "${line}" = "left" ] && { PASS=$((PASS+1)); echo "  ✅ burnBy 非法时回落到默认的 left"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 非法 burnBy 实际=${line}"; }

echo "--- 6. 周末与采样 ---"
# 注意：点数为 31 时禅道（和本实现）会开始按 interval=总数/31 自动采样，
# 拿来比周末会失真 —— 所以另建一个只有 7 天的执行来比
EID5=$(curl -s -X POST "${H[@]}" "$BASE/zentao/execution/create" \
  -d "{\"name\":\"BURN-短迭代-$TODAY\",\"project\":$PID,\"type\":\"sprint\",\"pri\":3,\"begin\":\"$(date -v-5d +%F 2>/dev/null || date -d '-5 days' +%F)\",\"end\":\"$(date -v+1d +%F 2>/dev/null || date -d '+1 day' +%F)\",\"PM\":\"admin\"}" | d)
line=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID5&type=noweekend" | field "len(d['labels'])")
linew=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID5&type=weekend" | field "len(d['labels'])")
[ "${linew}" = "7" ] && [ "${line}" = "5" ] && { PASS=$((PASS+1)); echo "  ✅ 7 天的区间：weekend 7 个点、noweekend 5 个点（正好差一个周末）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 周末过滤 实际 noweekend=${line} weekend=${linew}（期望 5 / 7）"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID&type=weekend" | field "any(__import__('datetime').date.fromisoformat(l).weekday()>=5 for l in d['labels'])")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ weekend 模式下 labels 里真的有周六/周日"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ weekend 含周末 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID&type=noweekend" | field "all(__import__('datetime').date.fromisoformat(l).weekday()<5 for l in d['labels'])")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ noweekend 模式下 labels 里一个周末都没有"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ noweekend 排除周末 实际=${line}"; }
# 采样：把区间拉长到 100 天，interval 不传时自动按「总数/31」采样
BEGIN2=$(date -v-60d +%F 2>/dev/null || date -d '-60 days' +%F)
END2=$(date -v+60d +%F 2>/dev/null || date -d '+60 days' +%F)
EID2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/execution/create" \
  -d "{\"name\":\"BURN-长迭代-$TODAY\",\"project\":$PID,\"type\":\"sprint\",\"pri\":3,\"begin\":\"$BEGIN2\",\"end\":\"$END2\",\"PM\":\"admin\"}" | d)
R=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID2&type=weekend")
# 121 个自然日 → interval = 121/31 = 3 → 每 4 天留一个点，约 31 个
line=$(echo "$R" | field "d['interval'], len(d['labels']) <= 40, len(d['labels']) >= 25")
[ "${line}" = "3 True True" ] && { PASS=$((PASS+1)); echo "  ✅ 长区间自动采样：interval=3，点数压到 25~40 之间"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 自动采样 实际=${line}"; }
line=$(echo "$R" | field "'$END2' in d['labels']")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 采样后计划结束日仍被保留（否则理想线的 0 点会丢）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 保留结束日 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID2&type=weekend&interval=0" | field "d['interval']")
[ "${line}" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ interval=0 也走自动采样"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ interval=0 实际=${line}"; }

echo "--- 7. 延期段与边界 ---"
# EID 的计划结束日在未来 → 不算延期；EID3 计划结束日在过去且状态仍进行中 → 延期
EID3=$(curl -s -X POST "${H[@]}" "$BASE/zentao/execution/create" \
  -d "{\"name\":\"BURN-延期迭代-$TODAY\",\"project\":$PID,\"type\":\"sprint\",\"pri\":3,\"begin\":\"$(date -v-40d +%F 2>/dev/null || date -d '-40 days' +%F)\",\"end\":\"$(date -v-5d +%F 2>/dev/null || date -d '-5 days' +%F)\",\"PM\":\"admin\"}" | d)
curl -s -X PUT "${H[@]}" "$BASE/zentao/execution/start?id=$EID3" >/dev/null
# 先落一条今天的快照，否则延期段没有任何点，断言「延期线有值」会假失败
curl -s -X POST "${HF[@]}" "$BASE/zentao/execution/compute-burn?id=$EID3" >/dev/null
R=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID3")
line=$(echo "$R" | field "d['type'], d['delayLine'] is not None, len(d['delayLine'])")
[ "${line}" = "noweekend,withdelay True $(echo "$R" | field "len(d['labels'])")" ] && { PASS=$((PASS+1)); echo "  ✅ 过了计划结束日还没结束 → 自动带 withdelay 并给出延期线"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 延期段 实际=${line}"; }
line=$(echo "$R" | field "[v for l,v in zip(d['labels'],d['burnLine']) if l > d['end']] == [None]*len([v for l,v in zip(d['labels'],d['burnLine']) if l > d['end']]), any(v is not None for l,v in zip(d['labels'],d['delayLine']) if l > d['end'])")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 计划结束日之后：实际线是 null、延期线有值（两条线在结束日交汇）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 两线分段 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID" | field "d['delayLine'] is None")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 没延期（结束日在未来）时不给延期线"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未延期仍给延期线 实际=${line}"; }
check "对项目（不是执行）取燃尽图 → 拒绝" 1020006007 "$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$PID")" "燃尽图"
check "执行不存在 → 拒绝" 1020006000 "$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=99999999")" "执行不存在"
# 没有起止日期的执行
EID4=$(curl -s -X POST "${H[@]}" "$BASE/zentao/execution/create" \
  -d "{\"name\":\"BURN-无日期-$TODAY\",\"project\":$PID,\"type\":\"sprint\",\"pri\":3,\"PM\":\"admin\"}" | d)
check "执行没有起止日期 → 拒绝" 1020006006 "$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID4")" "起止日期"

echo "--- 8. 状态与鉴权 ---"
# 已关闭的执行不参与 computeBurn（禅道 status notin done,closed,suspended）
curl -s -X PUT "${H[@]}" "$BASE/zentao/execution/close?id=$EID4" >/dev/null
line=$(curl -s -X POST "${HF[@]}" "$BASE/zentao/execution/compute-burn?id=$EID4" | d)
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 已关闭的执行不参与计算（返回 0 条）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 已关闭执行 实际=${line}"; }
line=$(curl -s "$BASE/zentao/execution/burn-data?id=$EID" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
[ "${line}" = "401" ] && { PASS=$((PASS+1)); echo "  ✅ 不带令牌被拦（code=401）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未登录 实际 code=${line}"; }

echo "--- 9. 清理 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_burn WHERE execution IN ($EID,$EID2,$EID3,$EID4,$EID5);" >/dev/null 2>&1
for t in $T1 $T2 $T3; do curl -s -X DELETE "${H[@]}" "$BASE/zentao/task/delete?id=$t" >/dev/null; done
for e in $EID $EID2 $EID3 $EID4 $EID5; do curl -s -X DELETE "${H[@]}" "$BASE/zentao/execution/delete?id=$e" >/dev/null; done
line=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_burn WHERE execution IN ($EID,$EID2,$EID3,$EID4,$EID5);")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 清理：快照与任务/执行都删掉了（本表没有 deleted，物理删除）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 清理 实际剩余=${line}"; }

echo "======================================================"
echo "  burn 模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

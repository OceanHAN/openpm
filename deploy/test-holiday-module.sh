#!/bin/bash
# 节假日（holiday）模块测试：假期 / 补班 / 实际工作日口径 + **燃尽图的工作日口径**
#
# 禅道语义（module/holiday，688 行、6 个 action）：
#   1. 一张表两种记录：type='holiday' 假期（不算工作日）、type='working' 补班（算工作日，即调休）
#   2. getActualWorkingDays 的优先级：补班 > 假期 > 周末（weekend=2 指周六周日）> 其它算工作日
#   3. 它是全项目「工作日」的唯一出口：燃尽图/甘特排期/工期计算/日历都调它
#   4. 一个照抄的怪癖：getActualWorkingDays(begin, end) 是**左闭右开**（begin==end 才返回那一天）
#
# 本脚本的重点是第 5 节：**燃尽图的横轴要跳过假期、并且要算上补班的周六** ——
# 这是第 36 轮做燃尽图时只按「跳周末」实现的口径补全。
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
wd() { curl -s "${HF[@]}" "$BASE/zentao/holiday/working-days?begin=$1&end=$2" | field "$3"; }

echo "===== 节假日（假期 / 补班 / 实际工作日 / 燃尽图口径）测试 ====="

echo "--- 1. 准备：清掉已有节假日，保证断言不受历史数据影响 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_holiday;" >/dev/null 2>&1
line=$(curl -s "${HF[@]}" "$BASE/zentao/holiday/list" | field "len(d)")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 初始没有节假日"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 初始列表 实际=${line}"; }

echo "--- 2. 假期与补班的 CRUD ---"
HID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/holiday/create" \
  -d '{"name":"国庆节","type":"holiday","begin":"2026-10-01","end":"2026-10-07","desc":"法定假期"}' | d)
WID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/holiday/create" \
  -d '{"name":"国庆调休","type":"working","begin":"2026-10-10","end":"2026-10-10"}' | d)
[ -n "$HID" ] && [ -n "$WID" ] && { PASS=$((PASS+1)); echo "  ✅ 建一条假期（$HID）+ 一条补班（$WID）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建节假日失败：$HID $WID"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/holiday/get?id=$HID" | field "d['name'], d['type'], d['year'], d['begin'], d['end']")
[ "${line}" = "国庆节 holiday 2026 2026-10-01 2026-10-07" ] && { PASS=$((PASS+1)); echo "  ✅ year 由 begin 自动推出（2026），类型与起止都对"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 详情 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/holiday/list?year=2026" | field "len(d)")
[ "${line}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 按年筛选出 2 条"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按年筛选 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/holiday/list?type=working" | field "len(d), d[0]['name']")
[ "${line}" = "1 国庆调休" ] && { PASS=$((PASS+1)); echo "  ✅ 按类型筛选（补班）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按类型筛选 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/holiday/years" | field "'2026' in d, '2027' in d, len(d) >= 2")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 可选年份：数据里的 2026 + 今年/明年（倒序）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 年份列表 实际=${line}"; }
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/holiday/update" \
  -d "{\"id\":$HID,\"name\":\"国庆节（改名）\",\"type\":\"holiday\",\"begin\":\"2026-10-01\",\"end\":\"2026-10-07\"}")
check "修改名称" 0 "$R"
line=$(curl -s "${HF[@]}" "$BASE/zentao/holiday/get?id=$HID" | field "d['name']")
[ "${line}" = "国庆节（改名）" ] && { PASS=$((PASS+1)); echo "  ✅ 修改生效"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修改 实际=${line}"; }

echo "--- 3. 实际工作日口径（补班 > 假期 > 周末）---"
line=$(wd 2026-10-01 2026-10-08 "d['count']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 10-01~10-07 全是假期 → 0 个工作日"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 假期区间 实际=${line}"; }
line=$(wd 2026-10-08 2026-10-08 "d['count'], d['days'][0]")
[ "${line}" = "1 2026-10-08" ] && { PASS=$((PASS+1)); echo "  ✅ 单日 10-08（周四，假期已结束）→ 1 天"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 单日 实际=${line}"; }
line=$(wd 2026-10-09 2026-10-12 "d['count'], d['days']")
[ "${line}" = "2 ['2026-10-09', '2026-10-10']" ] && { PASS=$((PASS+1)); echo "  ✅ 10-09(周五) + 10-10(周六但补班) = 2 天，10-11(周日) 跳过"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 补班 实际=${line}"; }
line=$(wd 2026-10-12 2026-10-19 "d['count'], d['days']")
[ "${line}" = "5 ['2026-10-12', '2026-10-13', '2026-10-14', '2026-10-15', '2026-10-16']" ] && { PASS=$((PASS+1)); echo "  ✅ 没有节假日的普通一周 → 5 天"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 普通一周 实际=${line}"; }
line=$(wd 2026-10-01 2026-10-01 "d['count']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 左闭右开：begin==end 且那天是假期 → 0"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 单日假期 实际=${line}"; }
line=$(wd 2026-10-14 2026-10-14 "d['count']")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 左闭右开：begin==end 且那天是工作日 → 1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 单日工作日 实际=${line}"; }
line=$(wd 2026-10-19 2026-10-19 "d['count']")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 左闭右开：begin==end 且那天是周一 → 1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 单日周一 实际=${line}"; }
line=$(wd 2026-10-17 2026-10-17 "d['count']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 左闭右开：begin==end 且那天是周六 → 0"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 单日周六 实际=${line}"; }

echo "--- 4. 燃尽图的横轴：跳过假期、算上补班（第 36 轮只跳周末）---"
EID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/execution/create" \
  -d '{"name":"HOLIDAY-口径验证","project":1,"type":"sprint","pri":3,"begin":"2026-09-21","end":"2026-10-09","PM":"admin"}' | d)
[ -n "$EID" ] && { PASS=$((PASS+1)); echo "  ✅ 建一个跨国庆的迭代（2026-09-21 ~ 2026-10-09，$EID）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建迭代失败"; }
R=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID&type=noweekend")
line=$(echo "$R" | field "d['interval'], len(d['labels'])")
[ "${line}" = "0 10" ] && { PASS=$((PASS+1)); echo "  ✅ 横轴 10 个点（区间内 15 个工作日 − 落在假期里的 5 个），未触发采样"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 点数 实际=${line}（期望 0 10）"; }
line=$(echo "$R" | field "[x for x in d['labels'] if x.startswith('2026-10-0')]")
[ "${line}" = "['2026-10-08', '2026-10-09']" ] && { PASS=$((PASS+1)); echo "  ✅ 10-01~10-07 假期被跳过；10-08、10-09 这两个工作日仍在横轴上"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 假期跳过 实际=${line}"; }
line=$(echo "$R" | field "'2026-09-26' in d['labels'], '2026-09-27' in d['labels']")
[ "${line}" = "False False" ] && { PASS=$((PASS+1)); echo "  ✅ 09-26(周六) 不在（那天没设补班）、09-27(周日) 也不在"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 周末 实际=${line}"; }
# 给 09-26 设一条补班 → 它应该出现在横轴上
WID2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/holiday/create" \
  -d '{"name":"9月调休","type":"working","begin":"2026-09-26","end":"2026-09-26"}' | d)
line=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID&type=noweekend" | field "'2026-09-26' in d['labels'], len(d['labels'])")
[ "${line}" = "True 11" ] && { PASS=$((PASS+1)); echo "  ✅ 把 09-26(周六) 设成补班后，它被算进横轴（10 → 11 个点）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 补班进横轴 实际=${line}"; }
# 反过来：把一个普通工作日设成假期 → 它应该从横轴上消失
HID2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/holiday/create" \
  -d '{"name":"临时放假","type":"holiday","begin":"2026-09-23","end":"2026-09-23"}' | d)
line=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID&type=noweekend" | field "'2026-09-23' in d['labels'], len(d['labels'])")
[ "${line}" = "False 10" ] && { PASS=$((PASS+1)); echo "  ✅ 把一个普通工作日（09-23）设成假期后，它从横轴消失（11 → 10）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 假期进横轴 实际=${line}"; }
# type=weekend 是自然日，一天不落（不受节假日影响）
line=$(curl -s "${HF[@]}" "$BASE/zentao/execution/burn-data?id=$EID&type=weekend&interval=0" | field "len(d['labels'])")
[ "${line}" = "19" ] && { PASS=$((PASS+1)); echo "  ✅ type=weekend 仍是自然日 19 天（不受节假日影响）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ weekend 口径 实际=${line}"; }
curl -s -X DELETE "${H[@]}" "$BASE/zentao/execution/delete?id=$EID" >/dev/null
echo "  （已删除验证用迭代）"

echo "--- 5. 删除与鉴权 ---"
check "删除补班记录" 0 "$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/holiday/delete?id=$WID2")"
line=$(wd 2026-10-09 2026-10-12 "d['count']")
[ "${line}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 删掉 09-26 的补班不影响别的日期"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删除后 实际=${line}"; }
check "删除假期" 0 "$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/holiday/delete?id=$HID")"
check "删除临时假期" 0 "$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/holiday/delete?id=$HID2")"
check "删除补班" 0 "$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/holiday/delete?id=$WID")"
line=$(wd 2026-10-01 2026-10-08 "d['count']")
[ "${line}" = "5" ] && { PASS=$((PASS+1)); echo "  ✅ 假期删完后 10-01~10-07 又变回 5 个工作日"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删假期后 实际=${line}"; }
line=$(curl -s "$BASE/zentao/holiday/list" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
[ "${line}" = "401" ] && { PASS=$((PASS+1)); echo "  ✅ 不带令牌被拦（code=401）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未登录 实际 code=${line}"; }

echo "--- 6. 清理（保证后面跑的 burn 测试不受影响）---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_holiday;" >/dev/null 2>&1
line=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_holiday;")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 节假日表清空（燃尽图的绝对点数断言才不会受影响）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 清理 实际残留=${line}"; }

echo "======================================================"
echo "  holiday 模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

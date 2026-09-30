#!/bin/bash
# 积分（score）模块测试：规则表 / 次数与时间窗 / 四条特例 / 总分与昨日 / 与 entry 的联调
#
# 禅道语义（module/score：control 42 行 + model 374 行；规则在 config.php）：
#   1. 规则 = (module, method, times, hour, score) + 扩展加成（严重程度/优先级/密码强度/执行关闭）
#   2. 计分入口 create(module, method, param, account, time)：未知规则**静默跳过**（内部调用）
#   3. 四条特例：缺陷「确认」的分给**提单人**；需求关闭额外给创建者 2 分；
#      任务完成 = 1 + 优先级加成 + round(预计/10)；执行关闭 PM 20 / 成员 5（按期或提前再加 10 / 5）
#   4. 次数与时间窗：hour=0 数全量历史，hour>0 数**当天**；命中上限静默跳过；0 分不落库
#   5. 总分：禅道冗余在 zt_user.score 上，本实现 = SUM(zt_score.score)（不迁 zt_user）
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
    PASS=$((PASS+1)); printf '  ✅ %-54s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-54s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
eq() {
  local name="$1" want="$2" got="$3"
  if [ "$want" = "$got" ]; then
    PASS=$((PASS+1)); printf '  ✅ %-54s = %s\n' "$name" "$got"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-54s 期望[%s] 实际[%s]\n' "$name" "$want" "$got"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }
# 计分（JSON 里没有转义引号，可以直接内联；有的话按坑位 #46 改两步走）
score() { curl -s -X POST "${H[@]}" "$BASE/zentao/score/create" -d "$1"; }
acct_count() { mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_score WHERE account='$1' AND module='$2' AND method='$3';"; }

echo "===== 积分（规则 / 次数窗口 / 四条特例 / 总分 / 与 entry 联调）测试 ====="

echo "--- 1. 准备：清掉测试账号与 admin 的历史流水 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_score;"
eq "初始流水为 0" "0" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_score;")"

echo "--- 2. 规则表（38 条，与禅道 config.php 一一对应）---"
RULES=$(curl -s "${HF[@]}" "$BASE/zentao/score/rule")
eq "规则条数" "38" "$(echo "$RULES" | field "len(d)")"
eq "user.login = 3 次 / 24 小时 / 1 分" "3 24 1 登录" \
   "$(echo "$RULES" | field "' '.join(str(x) for x in [ (r['times'], r['hour'], r['score'], r['methodName']) for r in d if r['module']=='user' and r['method']=='login'][0])")"
# hour=0 时接口按禅道口径显示「不限制」（$lang->score->noLimit）
eq "tutorial.finish = 1 次 / 100 分（新手教程）" "1 不限制 100" \
   "$(echo "$RULES" | field "' '.join(str(x) for x in [ (r['times'], r['hour'], r['score']) for r in d if r['module']=='tutorial'][0])")"
eq "execution.close 带扩展说明（项目经理 / 成员）" "1" \
   "$(echo "$RULES" | field "1 if any('项目经理' in (r['desc'] or '') and '成员' in (r['desc'] or '') for r in d if r['module']=='execution') else 0")"
eq "task.finish 带优先级加成说明" "1" \
   "$(echo "$RULES" | field "1 if any('p1' in (r['desc'] or '') for r in d if r['module']=='task' and r['method']=='finish') else 0")"
eq "bug.resolve 带严重程度加成说明" "1" \
   "$(echo "$RULES" | field "1 if any('s1' in (r['desc'] or '') for r in d if r['module']=='bug' and r['method']=='resolve') else 0")"

echo "--- 3. 次数与时间窗：user.login 一天最多 3 次 ---"
eq "第 1 次登录 +1（0 → 1）" "1 1" "$(score '{"module":"user","method":"login","account":"admin"}' | field "d['score'], d['after']")"
score '{"module":"user","method":"login","account":"admin"}' >/dev/null
score '{"module":"user","method":"login","account":"admin"}' >/dev/null
eq "3 次之后有水 3 条" "3" "$(acct_count admin user login)"
R=$(score '{"module":"user","method":"login","account":"admin"}')
eq "第 4 次被时间窗挡住（不落库、接口返回 null）" "" "$(echo "$R" | d)"
eq "流水仍然是 3 条" "3" "$(acct_count admin user login)"
eq "总分 = SUM(流水) = 3" "3" "$(curl -s "${HF[@]}" "$BASE/zentao/score/total?account=admin" | field "d['total']")"

echo "--- 4. 四条特例 ---"
# 4.1 任务完成 = 1 + 优先级加成 + round(预计/10)
TASK=$(curl -s "${HF[@]}" "$BASE/zentao/task/get?id=1")
TPRI=$(echo "$TASK" | field "d['pri']")
TEST=$(echo "$TASK" | field "d['estimate']")
EXPECT=$(python3 -c "
pri=$TPRI; est=float($TEST or 0)
bonus={1:2,2:1,3:0}.get(pri,0)
print(1+bonus+(round(est/10.0) if est>0 else 0))
")
R=$(score '{"module":"task","method":"finish","param":1,"account":"admin"}')
eq "task.finish 分值 = 1 + 优先级加成 + round(预计/10)（pri=$TPRI 预计=${TEST}）" "$EXPECT" "$(echo "$R" | field "d['score']")"
eq "描述里带对象编号" "完成任务ID:1" "$(echo "$R" | field "d['desc']")"
# 4.2 缺陷解决 + 严重程度加成
BUG=$(curl -s "${HF[@]}" "$BASE/zentao/bug/get?id=1")
BSEV=$(echo "$BUG" | field "d['severity']")
BOPEN=$(echo "$BUG" | field "d['openedBy']")
EXPBUG=$(python3 -c "print(1+{1:3,2:2,3:1}.get($BSEV,0))")
R=$(score '{"module":"bug","method":"resolve","param":1,"account":"admin"}')
eq "bug.resolve = 1 + 严重程度加成（severity=$BSEV）" "$EXPBUG" "$(echo "$R" | field "d['score']")"
eq "解决缺陷算在解决人头上" "admin" "$(echo "$R" | field "d['account']")"
# 4.3 缺陷确认 → 分给提单人
R=$(score '{"module":"bug","method":"confirm","param":1,"account":"admin"}')
eq "bug.confirm 的分给**提单人**（不是确认人）" "$BOPEN" "$(echo "$R" | field "d['account']")"
eq "bug.confirm 同样带严重程度加成" "$EXPBUG" "$(echo "$R" | field "d['score']")"
# 4.4 需求关闭 → 关闭人 + 创建者各一条
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_score WHERE module='story';"
# 演示需求 1 的 openedBy 存的是「昵称」而不是账号；禅道 saveScore 对取不到的账号静默跳过，
# 那样这条断言什么都验不到。先把 openedBy 改成真实账号，让断言只依赖行为、不依赖演示数据长什么样。
mysql_exec "UPDATE \`ruoyi-vue-pro\`.\`zt_story\` SET openedBy='admin' WHERE id=1;"
STORY=$(curl -s "${HF[@]}" "$BASE/zentao/story/get?id=1")
SOPEN=$(echo "$STORY" | field "d['openedBy']")
R=$(score '{"module":"story","method":"close","param":1,"account":"admin"}')
# 禅道的短路：story.close 只给创建者 2 分，关闭人那 1 分是死规则（见 ScoreService case "story" 的注释）
eq "需求关闭只有 1 条流水（给创建者的，禅道短路行为）" "1" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_score WHERE module='story';")"
eq "拿分的是创建者，2 分（ruleExtended createID）" "$SOPEN|2" \
   "$(mysql_query "SELECT CONCAT(account,'|',score) FROM \`ruoyi-vue-pro\`.zt_score WHERE module='story' ORDER BY id DESC LIMIT 1;")"
eq "关闭人那条（config 里写的 1 分）没有产生流水 —— 死规则" "0" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_score WHERE module='story' AND account='admin' AND score=1;")"
eq "接口返回的就是创建者那条" "$SOPEN" "$(echo "$R" | field "d['account']")"

echo "--- 5. 执行关闭：PM 20 + 每个成员 5（按期或提前再加分）---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_score WHERE module='execution';"
EXEC=$(curl -s "${HF[@]}" "$BASE/zentao/execution/get?id=90001")
EPM=$(echo "$EXEC" | field "d['PM']")
ETEAM=$(curl -s "${HF[@]}" "$BASE/zentao/team/member-list?root=90001&type=execution" | field "len(d)")
R=$(score '{"module":"execution","method":"close","param":90001,"account":"admin"}')
eq "返回了最后一条流水（禅道也是返回最后一个对象）" "1" "$(echo "$R" | field "1 if d['id'] else 0")"
eq "PM 拿到 20 分（截至今天未延期就是 20）" "1" \
   "$(mysql_query "SELECT 1 FROM \`ruoyi-vue-pro\`.zt_score WHERE module='execution' AND account='$EPM' AND score IN (20,30) LIMIT 1;")"
eq "PM 不会重复拿成员那份（PM 只有一条执行关闭流水）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_score WHERE module='execution' AND account='$EPM';")"

echo "--- 6. 静默跳过与报错口径 ---"
check "未知规则 → 明确报错（接口口径）" 1020032000 "$(score '{"module":"nosuch","method":"nope"}')" "积分规则不存在"
R=$(score '{"module":"task","method":"finish","account":"admin"}')
eq "task.finish 不带 param → 不落库（禅道取不到任务就 return true）" "" "$(echo "$R" | d)"
check "缺 module → 参数校验" 400 "$(score '{"method":"login"}')" "模块不能为空"

echo "--- 7. 总分 / 昨日 / 提示语 ---"
TOTAL=$(curl -s "${HF[@]}" "$BASE/zentao/score/total?account=admin")
eq "总分 = 该账号流水之和" "$(mysql_query "SELECT COALESCE(SUM(score),0) FROM \`ruoyi-vue-pro\`.zt_score WHERE account='admin';")" "$(echo "$TOTAL" | field "d['total']")"
eq "流水条数一致" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_score WHERE account='admin';")" "$(echo "$TOTAL" | field "d['count']")"
eq "功能开关（zentao.score.enabled）默认开" "True" "$(echo "$TOTAL" | field "d['enabled']")"
eq "昨日为 0 时不给提示语（禅道 getNotice 的口径）" "" "$(echo "$TOTAL" | field "d['tip'] or ''")"
mysql_exec "INSERT INTO \`ruoyi-vue-pro\`.zt_score (account,module,method,\`desc\`,\`before\`,\`score\`,\`after\`,\`time\`) VALUES ('admin','user','login','登录',0,7,7,NOW() - INTERVAL 1 DAY);"
TOTAL=$(curl -s "${HF[@]}" "$BASE/zentao/score/total?account=admin")
eq "昨天有分 → 给提示语（昨天增加了积分：7，总积分：N）" "1" \
   "$(echo "$TOTAL" | field "1 if d['yesterday']==7 and '昨天增加了积分：7' in (d['tip'] or '') else 0")"

echo "--- 8. 分页与过滤 ---"
PAGE=$(curl -s -G "${HF[@]}" "$BASE/zentao/score/page" -d "account=admin" -d "pageNo=1" -d "pageSize=5")
eq "按账号过滤的条数与库一致" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_score WHERE account='admin';")" "$(echo "$PAGE" | field "d['total']")"
eq "排序是 time desc（最新的在前）" "1" \
   "$(echo "$PAGE" | field "1 if d['list'][0]['time'] >= d['list'][-1]['time'] else 0")"
eq "列表带模块/动作中文名" "1" \
   "$(echo "$PAGE" | field "1 if d['list'][0]['moduleName'] and d['list'][0]['methodName'] else 0")"

echo "--- 9. 与 entry 联调：应用接入校验通过 → 计一次登录分（禅道 common::checkEntry 的行为）---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_score WHERE account='admin' AND module='user' AND method='login';"
mysql_exec "UPDATE \`ruoyi-vue-pro\`.zt_entry SET calledTime=0 WHERE code='oa';"
TS=$(python3 -c 'import time;print(int(time.time())+100)')
TK=$(curl -s "${HF[@]}" "$BASE/zentao/entry/sign?code=oa&time=$TS" | field "d['token']")
curl -s -X POST "$BASE/zentao/entry/verify" -H 'tenant-id: 1' -H 'Content-Type: application/json' \
  -d "{\"code\":\"oa\",\"token\":\"$TK\",\"time\":\"$TS\",\"clientIp\":\"127.0.0.1\",\"module\":\"user\",\"method\":\"apilogin\"}" >/dev/null
eq "entry 校验通过后 admin 多了一条 user.login 流水" "1" "$(acct_count admin user login)"
eq "这条流水的描述是「登录」" "登录" \
   "$(mysql_query "SELECT \`desc\` FROM \`ruoyi-vue-pro\`.zt_score WHERE account='admin' AND module='user' AND method='login' ORDER BY id DESC LIMIT 1;")"

echo "--- 10. 清理：把演示数据恢复成 SQL 51 的 4 条 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_score;
INSERT INTO \`ruoyi-vue-pro\`.zt_score (account,module,method,\`desc\`,\`before\`,\`score\`,\`after\`,\`time\`) VALUES
('admin','user','login','登录',0,1,1,NOW() - INTERVAL 2 DAY),
('admin','task','finish','完成任务ID:1',1,3,4,NOW() - INTERVAL 1 DAY),
('admin','bug','resolve','解决BugID:1',4,2,6,NOW() - INTERVAL 1 DAY),
('admin','story','close','需求关闭ID:1',6,1,7,NOW() - INTERVAL 3 HOUR);"
eq "演示数据恢复成 4 条（与 SQL 51 一致，可重复执行）" "4" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_score;")"
eq "库里没有测试账号残留" "0" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_score WHERE account <> 'admin';")"

echo
echo "===== 结果：通过 $PASS 项，失败 $FAIL 项 ====="
[ "$FAIL" = "0" ] || exit 1

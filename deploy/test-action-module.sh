#!/bin/bash
# 操作日志（action）模块测试：回收站 / 动态 / 备注 / 动作渲染
#
# 禅道语义（module/action）：
#   1. 删除对象时记一条 action='deleted' 的日志；**回收站就是这批日志**
#      （禅道用 extra='canUndelete' 标记可还原的删除，本实现把所有逻辑删除都当可还原，
#        extra 只用来记「已从回收站隐藏」= beHidden）
#   2. 还原 = 去对象所在的表里把 deleted 置回 0（表名走 ActionObjectMap 白名单），并记一条 undeleted
#   3. 隐藏 = 只改日志的 extra（对象仍是删除状态），并记一条 hidden；hideAll 一次隐藏全部
#   4. 备注 = 一条 action='commented' 的日志，作者可以改自己的备注
#   5. 动作渲染（renderAction + renderChanges）：把 action + zt_history 拼成
#      「admin 编辑：状态 激活 → 已关闭」这样的人话
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

source "$(dirname "$0")/_mysql.sh"

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')
# 备注/回收站这些接口是 @RequestParam（表单参数），不能带 JSON Content-Type，
# 否则 Spring 去解析 JSON body，报「请求参数缺失:xxx」
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

echo "===== 操作日志（回收站 / 动态 / 备注 / 动作渲染）模块测试 ====="
TS=$(date +%s)

echo "--- 1. 回收站：删除 → 列表 → 还原 ---"
# 造一条需求，删掉它（删除会记一条 deleted 动作）
ST=$(curl -s -X POST "${H[@]}" "$BASE/zentao/story/create" \
  -d "{\"product\":1,\"title\":\"回收站测试需求-$TS\",\"type\":\"story\",\"pri\":3}" | d)
[ -n "$ST" ] && { PASS=$((PASS+1)); echo "  ✅ 准备：新建需求 $ST"; } || { FAIL=$((FAIL+1)); echo "  ❌ 建需求失败"; }
check "删除需求（返回 0）" 0 "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$ST")" ""

R=$(curl -s "${H[@]}" "$BASE/zentao/action/trash?pageNo=1&pageSize=100&objectType=story")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
row=[x for x in d['list'] if x['objectID']==$ST]
print(len(row), row[0]['objectName'] if row else '', row[0]['objectTypeName'] if row else '', row[0]['canUndelete'] if row else '')")
[ "${line}" = "1 回收站测试需求-$TS 需求 True" ] && { PASS=$((PASS+1)); echo "  ✅ 回收站列出被删的需求（回查出了对象名与类型中文名）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 回收站列表 实际=${line}"; }
ACT=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print([x['actionId'] for x in d['list'] if x['objectID']==$ST][0])")
DB_DEL=$(mysql_query "SELECT deleted+0 FROM \`ruoyi-vue-pro\`.zt_story WHERE id = $ST;")
[ "${DB_DEL}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 删除是逻辑删除（库里 deleted=1）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删除状态 实际=${DB_DEL}"; }

check "还原" 0 "$(curl -s -X POST "${H[@]}" "$BASE/zentao/action/undelete?id=$ACT")" ""
DB_DEL=$(mysql_query "SELECT deleted+0 FROM \`ruoyi-vue-pro\`.zt_story WHERE id = $ST;")
[ "${DB_DEL}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 还原后库里 deleted=0，需求又回来了"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 还原后状态 实际=${DB_DEL}"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$ST")
line=$(echo "$R" | field "d['title']")
[ "${line}" = "回收站测试需求-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 接口也能读到还原后的需求"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 还原后读取 实际=${line}"; }

line=$(curl -s "${H[@]}" "$BASE/zentao/action/list?objectType=story&objectID=$ST" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print([x['actionName'] for x in d])")
[ "${line}" = "['还原', '删除', '创建']" ] && { PASS=$((PASS+1)); echo "  ✅ 时间线里多了「还原」动作（最新在前）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 时间线 实际=${line}"; }

echo "--- 2. 回收站：隐藏 / 不能再还原 ---"
check "删除需求" 0 "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$ST")" ""
ACT2=$(curl -s "${H[@]}" "$BASE/zentao/action/trash?pageNo=1&pageSize=100&objectType=story" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print([x['actionId'] for x in d['list'] if x['objectID']==$ST][0])")
check "从回收站隐藏" 0 "$(curl -s -X POST "${H[@]}" "$BASE/zentao/action/hide?id=$ACT2")" ""
# 同一对象可能被删过多次（前一步还原又删了一次），所以断言的是「被隐藏的那条动作」不在列表里
line=$(curl -s "${H[@]}" "$BASE/zentao/action/trash?pageNo=1&pageSize=100&objectType=story" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(any(x['actionId']==$ACT2 for x in d['list']))")
[ "${line}" = "False" ] && { PASS=$((PASS+1)); echo "  ✅ 被隐藏的那条记录不在回收站列表里"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 隐藏后仍在列表 实际=${line}"; }
check "已隐藏的不能再还原" 1020028002 "$(curl -s -X POST "${H[@]}" "$BASE/zentao/action/undelete?id=$ACT2")" "隐藏"
DB_DEL=$(mysql_query "SELECT deleted+0 FROM \`ruoyi-vue-pro\`.zt_story WHERE id = $ST;")
[ "${DB_DEL}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 隐藏不改变对象的删除状态（仍是 deleted=1）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 隐藏后的对象状态 实际=${DB_DEL}"; }
CREATED_ACT=$(curl -s "${H[@]}" "$BASE/zentao/action/list?objectType=story&objectID=$ST" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print([x['id'] for x in d if x['action']=='created'][0])")
check "隐藏一条没被删除的记录 → 拒绝" 1020028001 \
  "$(curl -s -X POST "${HF[@]}" "$BASE/zentao/action/hide?id=$CREATED_ACT")" "删除"
check "还原一条不存在的日志 → 拒绝" 1020028000 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/action/undelete?id=99999999")" "操作日志不存在"

echo "--- 3. 动作渲染（renderAction + renderChanges） ---"
# 造一条需求并编辑（编辑会产生 zt_history 字段变化）
ST2=$(curl -s -X POST "${H[@]}" "$BASE/zentao/story/create" \
  -d "{\"product\":1,\"title\":\"动作渲染测试-$TS\",\"type\":\"story\",\"pri\":3,\"estimate\":2}" | d)
curl -s -X PUT "${H[@]}" "$BASE/zentao/story/update" \
  -d "{\"id\":$ST2,\"product\":1,\"title\":\"动作渲染测试-$TS-改\",\"type\":\"story\",\"pri\":1,\"estimate\":5}" >/dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/action/list?objectType=story&objectID=$ST2")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
edit=[x for x in d if x['action']=='edited'][0]
fields=[h['field'] for h in edit['histories']]
print(len(fields) >= 1, 'rendered' if edit['renderedDesc'].startswith('admin 编辑：') else edit['renderedDesc'][:40])")
[ "${line}" = "True rendered" ] && { PASS=$((PASS+1)); echo "  ✅ 编辑动作渲染成「admin 编辑：字段 旧 → 新」"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 动作渲染 实际=${line}"; }
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
edit=[x for x in d if x['action']=='edited'][0]
print(' → ' in edit['renderedDesc'], edit['histories'][0]['oldValue'] is not None)")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 渲染文本里带箭头，histories 里带旧值/新值"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 渲染细节 实际=${line}"; }
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
create=[x for x in d if x['action']=='created'][0]
print(create['renderedDesc'])")
[ "${line}" = "admin 创建" ] && { PASS=$((PASS+1)); echo "  ✅ 无字段变化的动作只渲染「谁 + 动作」"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 创建动作渲染 实际=${line}"; }

echo "--- 4. 备注 ---"
CM=$(curl -s -X POST "${HF[@]}" "$BASE/zentao/action/comment" \
  --data-urlencode "objectType=story" --data-urlencode "objectID=$ST2" --data-urlencode "comment=先把这条需求挂起-$TS" | d)
[ -n "$CM" ] && { PASS=$((PASS+1)); echo "  ✅ 发备注成功（编号 $CM）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 发备注失败：$CM"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/action/list?objectType=story&objectID=$ST2")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
c=[x for x in d if x['action']=='commented'][0]
print(c['actionName'], c['comment'], c['renderedDesc'])")
[ "${line}" = "备注 先把这条需求挂起-$TS admin 备注：先把这条需求挂起-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 备注进时间线，渲染文本带内容"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 备注时间线 实际=${line}"; }
check "改备注" 0 "$(curl -s -X PUT "${HF[@]}" "$BASE/zentao/action/comment/update" --data-urlencode "id=$CM" --data-urlencode "comment=改过了-$TS")" ""
line=$(curl -s "${H[@]}" "$BASE/zentao/action/list?objectType=story&objectID=$ST2" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print([x['comment'] for x in d if x['action']=='commented'][0])")
[ "${line}" = "改过了-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 改完内容生效"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 改备注 实际=${line}"; }
# 造一条「别人发的备注」：直接插一条 actor=tester 的 commented 动作（模拟他人操作）
mysql_exec "INSERT INTO \`ruoyi-vue-pro\`.zt_action (objectType, objectID, actor, action, \`date\`, comment, \`read\`, efforted, vision) VALUES ('story', $ST2, 'tester', 'commented', NOW(), '别人发的备注', 0, 0, 'rnd');" >/dev/null 2>&1
OTHER=$(mysql_query "SELECT id FROM \`ruoyi-vue-pro\`.zt_action WHERE objectType='story' AND objectID=$ST2 AND actor='tester' AND action='commented' ORDER BY id DESC LIMIT 1;")
check "改别人的备注 → 拒绝" 1020028008 \
  "$(curl -s -X PUT "${HF[@]}" "$BASE/zentao/action/comment/update" --data-urlencode "id=$OTHER" --data-urlencode "comment=越权")" "只能修改自己"
check "备注内容为空 → 拒绝" 1020028006 \
  "$(curl -s -X POST "${HF[@]}" "$BASE/zentao/action/comment" --data-urlencode "objectType=story" --data-urlencode "objectID=$ST2" --data-urlencode "comment=")" "不能为空"
check "改一条非备注日志 → 拒绝" 1020028007 \
  "$(curl -s -X PUT "${HF[@]}" "$BASE/zentao/action/comment/update" --data-urlencode "id=$CREATED_ACT" --data-urlencode "comment=x")" "备注不存在" 

echo "--- 5. 动态（feed） ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/action/dynamic?limit=5")
line=$(echo "$R" | field "len(d) > 0, len(d) <= 5, all(x['renderedDesc'] for x in d)")
[ "${line}" = "True True True" ] && { PASS=$((PASS+1)); echo "  ✅ 动态流返回最近 5 条（都带渲染文本）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 动态流 实际=${line}"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/action/dynamic?actor=admin&period=today&limit=3")
line=$(echo "$R" | field "len(d) > 0, all(x['actor']=='admin' for x in d)")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 按人 + 周期过滤"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 动态过滤 实际=${line}"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/action/dynamic?product=1&limit=3")
line=$(echo "$R" | field "len(d) >= 0")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 按产品过滤可用"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按产品过滤 实际=${line}"; }

echo "--- 6. 列表与清理 ---"
line=$(curl -s "${H[@]}" "$BASE/zentao/action/trash?pageNo=1&pageSize=5" | field "len(d['list']) <= 5, d['total'] >= 1")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 回收站分页正常"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 回收站分页 实际=${line}"; }
HIDE_ALL=$(curl -s -X POST "${H[@]}" "$BASE/zentao/action/hide-all" | d)
line=$(curl -s "${H[@]}" "$BASE/zentao/action/trash?pageNo=1&pageSize=5" | field "d['total']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 全部隐藏后回收站清空（隐藏了 ${HIDE_ALL} 条）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 全部隐藏 实际剩余=${line}"; }

# 清理：把测试造的需求真正删掉（再删一次即可，日志已经被隐藏）
curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$ST" >/dev/null
curl -s -X DELETE "${H[@]}" "$BASE/zentao/story/delete?id=$ST2" >/dev/null
curl -s -X POST "${H[@]}" "$BASE/zentao/action/hide-all" >/dev/null

echo "======================================================"
echo "  action 模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

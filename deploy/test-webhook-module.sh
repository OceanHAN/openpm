#!/bin/bash
# Webhook（webhook，禅道「事件外发的通用出口」）模块测试
#
# 禅道语义（module/webhook：control 347 行 + model 914 行 + zen 92 + tao 135，10 个 action）：
#   业务动作（新增/编辑/关闭需求、任务、缺陷……）发生后，遍历所有启用的 zt_webhook，
#   用 buildData() 把这次 zt_action 的字段按 params 拼成 JSON，HTTP POST 出去，
#   结果写进通用日志表 zt_log（objectType='webhook'）。
#   照抄的规则（config/webhook.php + model.php）：
#     ① objectTypes 白名单（9 种对象类型 + 各自允许的动作），buildData 第一道闸是动作标签表；
#     ② products 取交集、executions 包含才发；空 = 不限（不是「谁都不发」）；
#     ③ 群机器人类强制 application/json；钉钉群加签 timestamp(ms)+"\n"+secret 做 HMAC-SHA256；
#     ④ 失败不影响主流程：send() 一律返回 true，失败只 saveLog 落 zt_log；
#     ⑤ 编辑态 url 必填、创建态可空（config/formdata.php 的不对称）。
#
# 测试手段：演示数据里 92200 指向本项目自带的 mock 接收端点
#   /admin-api/zentao/webhook/mock-receive（把收到的 body 存进服务端内存），
#   92201 指向必然连不上的 127.0.0.1:1 —— 端到端断言「payload 与日志」和「失败被记录」。
#
# 【坑位 #46】本脚本**不写**「嵌套引号 + $( ) + JSON」：
#   一律 $R=$(curl ...) 先取响应，再 check "..." N "$R" "关键字" 两步走；
#   JSON 用 --data-binary 时也单独成变量。变量紧跟中文一律写 ${VAR}（坑位 #28）。
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
    PASS=$((PASS+1)); printf '  ✅ %-56s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-56s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
eq() {
  local name="$1" want="$2" got="$3"
  if [ "$want" = "$got" ]; then
    PASS=$((PASS+1)); printf '  ✅ %-56s = %s\n' "$name" "$got"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-56s 期望[%s] 实际[%s]\n' "$name" "$want" "$got"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
rcode() { python3 -c "import sys,json;print(json.load(sys.stdin).get('code'))" 2>/dev/null; }
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }
# 断言「msg 里含某个关键字」（用 check 的 frag 参数即可，这里保留 helper 以备后用）

echo "===== Webhook（事件外发：白名单/字段映射/发送/日志/失败不影响业务）测试 ====="

echo "--- 1. 准备：重建演示 webhook、演示动作行与 mock 记录 ---"
# ⚠️ zt_action id 段所有权（2026-09-17 定）：99200~99202 是**本脚本的私有段**
#   （下面 INSERT、结尾 222 行 DELETE，都只碰这三条；并且本脚本多处依赖「取最新一条动作」的语义）。
#   deploy/ui-check/webhook.mjs 用的是它自己的 99101~99103，两边不再互相踩。
#   注意：两边**仍然不能并发跑** —— 本脚本对 zt_log 用的是精确条数断言（151/157/159/170 行），
#   而界面检查会往 zt_log 写 webhook 发送日志，同时跑必然互相打断。
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_webhook WHERE id IN (92200,92201);
INSERT INTO \`ruoyi-vue-pro\`.zt_webhook (id,type,name,url,domain,secret,contentType,sendType,products,executions,params,actions,\`desc\`,createdBy,createdDate,editedBy,editedDate,creator,updater,deleted) VALUES
(92200,'default','本地联调接收端','http://127.0.0.1:48080/admin-api/zentao/webhook/mock-receive','','','application/json','sync','1','','objectType,objectID,product,action,actor,date,comment,text','{\"story\":[\"opened\",\"edited\",\"changed\",\"closed\",\"activated\"],\"task\":[\"opened\",\"edited\",\"closed\"]}','演示用',  'admin',NOW(),'admin',NOW(),'admin','admin',0),
(92201,'default','演示：不可达地址','http://127.0.0.1:1/zentao/unreachable','','','application/json','sync','1','','objectType,objectID,text','{\"story\":[\"opened\",\"edited\"]}','演示用','admin',NOW(),'admin',NOW(),'admin','admin',0);
DELETE FROM \`ruoyi-vue-pro\`.zt_action WHERE id IN (99200,99201,99202);
INSERT INTO \`ruoyi-vue-pro\`.zt_action (id,objectType,objectID,product,project,execution,actor,action,\`date\`,comment,extra,\`read\`,vision,efforted,creator,updater) VALUES
(99200,'story',1,'1',1,90001,'admin','opened',NOW(),'演示','',0,'rnd',0,'admin','admin'),
(99201,'story',4,'1',1,90001,'admin','edited',NOW(),'演示','',0,'rnd',0,'admin','admin'),
(99202,'story',92201,'1',1,90001,'admin','edited',NOW(),'演示/测试','',0,'rnd',0,'admin','admin');
DELETE FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='webhook';"
curl -s -X DELETE "${HF[@]}" "$BASE/zentao/webhook/mock-clear" >/dev/null
eq "库里有 2 条演示 webhook" "2" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_webhook WHERE id IN (92200,92201) AND deleted=0;")"

echo "--- 2. 分页 / 详情 / 错误码 ---"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/webhook/page" -d "pageNo=1" -d "pageSize=10")
eq "分页返回 2 条" "2" "$(echo "$R" | field "d['total']")"
# 中文参数必须 URL 编码（直接拼在 URL 里 Tomcat 会 400）
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/webhook/page" --data-urlencode "name=联调" -d "pageNo=1" -d "pageSize=10")
eq "按名称模糊分页（联调 → 1 条）" "1" "$(echo "$R" | field "d['total']")"
R=$(curl -s "${HF[@]}" "$BASE/zentao/webhook/get?id=92200")
eq "详情：类型/发送方式/参数三件套" "default sync objectType,objectID,product,action,actor,date,comment,text" \
   "$(echo "$R" | field "d['type'], d['sendType'], d['params']")"
check "取不存在的 webhook → 404 语义" 1020036000 \
  "$(curl -s "${HF[@]}" "$BASE/zentao/webhook/get?id=999999")" "Webhook 不存在"

echo "--- 3. 创建/修改的校验（照抄禅道 requiredFields 与 url 规则）---"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/create" -d '{"name":"","url":"http://x.example.com/hook"}')
check "名称为空 → 参数校验" 400 "$R" "名称不能为空"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/create" -d '{"name":"测试-坏地址","url":"ftp://x.example.com/hook"}')
check "url 不以 http(s):// 开头 → 拒绝（禅道 preg_match）" 1020036002 "$R" "请求地址不能为空"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/create" -d '{"name":"测试-坏地址2","type":"default","url":"","requireProduct":false}')
check "通用类型 url 为空 → 拒绝（发不出去）" 1020036002 "$R" "请求地址不能为空"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/create" -d '{"name":"测试-要产品","type":"default","url":"http://x.example.com/hook","requireProduct":true}')
check "requireProduct=true 且 products 为空 → 必须选择产品" 1020036003 "$R" "必须选择产品"

# 清掉第 3 节建出来的临时 webhook：它们的 products/actions 都是空的（= 禅道语义上的「不限」），
# 会污染下一节的 available-list 断言（第一次跑就踩到了）
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_webhook WHERE id > 92201;"

echo "--- 4. 对象类型白名单（禅道 config/webhook.php objectTypes）---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/webhook/object-types")
eq "白名单 9 种对象类型" "9" "$(echo "$R" | field "len(d)")"
eq "任务允许 12 个动作（禅道原样）" "12" \
   "$(echo "$R" | field "len([x for x in d if x['type']=='task'][0]['actionTypes'])")"
eq "缺陷动作里含 bugconfirmed（禅道特有）" "1" \
   "$(echo "$R" | field "1 if 'bugconfirmed' in [x for x in d if x['type']=='bug'][0]['actionTypes'] else 0")"
eq "待办只允许 opened/edited" "opened,edited" \
   "$(echo "$R" | field "','.join([x for x in d if x['type']=='todo'][0]['actionTypes'])")"
eq "needAssignTypes 含 story/task/bug/todo" "4" \
   "$(echo "$R" | field "len([x for x in d if x['needAssign']])")"
eq "白名单不含 release（webhook 只认这 9 种）" "0" \
   "$(echo "$R" | field "len([x for x in d if x['type']=='release'])")"

echo "--- 5. 按对象取可用 webhook（available-list）---"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/webhook/available-list" -d "objectType=story" -d "actionType=opened" -d "product=1")
eq "story+opened+产品1 → 两条都能收" "2" "$(echo "$R" | field "len(d)")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/webhook/available-list" -d "objectType=story" -d "actionType=opened" -d "product=2")
eq "产品 2 不在 products 交集里 → 0 条" "0" "$(echo "$R" | field "len(d)")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/webhook/available-list" -d "objectType=product" -d "actionType=opened" -d "product=1")
eq "对象类型不在白名单（product）→ 0 条" "0" "$(echo "$R" | field "len(d)")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/webhook/available-list" -d "objectType=product" -d "product=1")
eq "白名单外的对象类型，就算不限定动作也是 0 条" "0" "$(echo "$R" | field "len(d)")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/webhook/available-list" -d "objectType=story")
eq "story 不限定动作 → 两条 webhook 的 actions 都含 story" "2" "$(echo "$R" | field "len(d)")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/webhook/available-list" -d "objectType=story" -d "actionType=activated")
eq "story+activated → 只有 92200（92201 只配 opened/edited）" "92200" \
   "$(echo "$R" | field "','.join(str(x['id']) for x in d)")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/webhook/available-list" -d "objectType=task" -d "actionType=opened" -d "product=1")
eq "task+opened → 只有 92200（92201 只配了 story）" "92200" \
   "$(echo "$R" | field "','.join(str(x['id']) for x in d)")"

echo "--- 6. 发送：payload 字段映射 + 落 zt_log（mock 接收端端到端）---"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/send" \
  -d '{"objectType":"story","objectID":92201,"actionType":"edited","webhookId":92200}')
eq "发送未跳过（对象/动作/产品都命中）" "False" "$(echo "$R" | field "d['skipped']")"
eq "命中的是 92200 一个" "92200" "$(echo "$R" | field "','.join(str(x) for x in d['matchedWebhookIds'])")"
eq "payload 的 key 就是 params 里的字段（照抄 getDataByType 的 else 分支）" "1" \
   "$(echo "$R" | field "1 if set(['objectType','objectID','product','action','actor','date','comment','text']).issubset(set(json.loads(d['payload']).keys())) else 0")"
eq "payload.objectType = story" "story" "$(echo "$R" | field "json.loads(d['payload'])['objectType']")"
eq "payload.objectID = 92201" "92201" "$(echo "$R" | field "json.loads(d['payload'])['objectID']")"
eq "payload.action = edited" "edited" "$(echo "$R" | field "json.loads(d['payload'])['action']")"
eq "payload.actor = admin" "admin" "$(echo "$R" | field "json.loads(d['payload'])['actor']")"
eq "payload.text = 动作标签 + 对象类型名 + [#id::名称](链接)" "1" \
   "$(echo "$R" | field "1 if json.loads(d['payload'])['text'].startswith('编辑了需求 [#92201::批量导入-解析 Excel](') else 0")"
eq "text 里的链接用 webhook.domain（空则回落站内前端地址）" "1" \
   "$(echo "$R" | field "1 if '/zentao/story/index?id=92201' in json.loads(d['payload'])['text'] else 0")"
eq "发送结果 = mock 接收端回的 success" "success" "$(echo "$R" | field "d['items'][0]['result']")"
eq "item.success = True" "True" "$(echo "$R" | field "d['items'][0]['success']")"
eq "item.logId 非空（已写 zt_log）" "1" "$(echo "$R" | field "1 if d['items'][0]['logId'] else 0")"

# mock 接收端确实收到了这次 payload（端到端闭环）
R=$(curl -s "${HF[@]}" "$BASE/zentao/webhook/mock-list")
eq "mock 接收端收到 1 条" "1" "$(echo "$R" | field "len(d)")"
eq "mock 收到的 body 与接口返回的 payload 完全一致" "1" \
   "$(echo "$R" | field "1 if json.loads(d[0]['body'])['objectID']==92201 and json.loads(d[0]['body'])['action']=='edited' else 0")"

# zt_log 里的那一行
eq "zt_log 落了一行（objectType=webhook）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='webhook' AND objectID=92200;")"
eq "日志行存了 payload 与结果" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='webhook' AND objectID=92200 AND data LIKE '%92201%' AND result='success';")"

echo "--- 7. 发送：地址不可达 → 失败被记录、接口仍返回成功 ---"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/send" \
  -d '{"objectType":"story","objectID":92201,"actionType":"edited","webhookId":92201}')
check "接口仍然返回成功（失败不影响业务，禅道 send() 返回 true）" 0 "$R" ""
eq "failedCount = 1" "1" "$(echo "$R" | field "d['failedCount']")"
eq "item.success = False" "False" "$(echo "$R" | field "d['items'][0]['success']")"
eq "失败原因写进了 result" "1" "$(echo "$R" | field "1 if '发送失败' in d['items'][0]['result'] else 0")"
eq "失败也写了 zt_log（禅道 saveLog 的语义）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='webhook' AND objectID=92201 AND result LIKE '发送失败%';")"
eq "两次发送共 2 行日志" "2" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='webhook';")"

echo "--- 8. 发送：静默跳过的两种情形（禅道 buildData 的 return false）---"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/send" \
  -d '{"objectType":"task","objectID":92201,"actionType":"opened","webhookId":92200}')
eq "对象行不存在（task 92201）→ 跳过、不报错" "True" "$(echo "$R" | field "d['skipped']")"
eq "跳过时不产生日志" "2" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='webhook';")"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/send" \
  -d '{"objectType":"story","objectID":92201,"actionType":"deleted","webhookId":92200}')
eq "动作不在白名单（deleted）→ 跳过" "True" "$(echo "$R" | field "d['skipped']")"

echo "--- 9. 发送：显式 actionID + 抽取动作行里的 objectType ---"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/send" \
  -d '{"objectType":"story","objectID":92201,"actionType":"edited","actionID":99200,"webhookId":92200}')
eq "传 99200（story 1 的 opened）时按动作行取数" "99200" "$(echo "$R" | field "str(d['actionID'])")"
eq "payload.objectID 变成动作行的 1（不是请求里的 92201）" "1" \
   "$(echo "$R" | field "json.loads(d['payload'])['objectID']")"
eq "payload.action 变成动作行的 opened" "opened" "$(echo "$R" | field "json.loads(d['payload'])['action']")"

echo "--- 10. 创建/编辑/删除（params 强制补 text + 编辑态 url 必填）---"
NEW=$(curl -s -X POST "${H[@]}" "$BASE/zentao/webhook/create" \
  -d '{"name":"测试-临时 Webhook","type":"default","url":"http://127.0.0.1:48080/admin-api/zentao/webhook/mock-receive","products":"1","params":"objectType,objectID","sendType":"sync","desc":"临时"}')
NEWID=$(echo "$NEW" | d)
if [ -n "$NEWID" ]; then PASS=$((PASS+1)); printf '  ✅ %-56s id=%s\n' "新建临时 webhook" "$NEWID"; \
  else FAIL=$((FAIL+1)); echo "  ❌ 新建临时 webhook 失败：$NEW"; fi
eq "create 无条件追加 ,text（禅道原样）" "objectType,objectID,text" \
   "$(mysql_query "SELECT params FROM \`ruoyi-vue-pro\`.zt_webhook WHERE id=${NEWID};")"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/webhook/update" -d "{\"id\":${NEWID},\"name\":\"测试-临时 Webhook\",\"type\":\"default\",\"url\":\"\"}")
check "编辑态 url 为空 → 拒绝（formdata.php form->edit url required=true）" 1020036002 "$R" "请求地址不能为空"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/webhook/update" \
  -d "{\"id\":${NEWID},\"name\":\"测试-临时 Webhook\",\"type\":\"default\",\"url\":\"http://127.0.0.1:48080/admin-api/zentao/webhook/mock-receive\",\"params\":\"objectType\",\"products\":\"1\"}")
eq "编辑成功" "0" "$(echo "$R" | rcode)"
eq "update 里 params 已含 text 就不再重复追加" "objectType,text" \
   "$(mysql_query "SELECT params FROM \`ruoyi-vue-pro\`.zt_webhook WHERE id=${NEWID};")"
R=$(curl -s "${HF[@]}" "$BASE/zentao/webhook/log-page?objectID=92200&pageNo=1&pageSize=10")
eq "日志分页按 webhook 过滤（92200 两行）" "2" "$(echo "$R" | field "d['total']")"
R=$(curl -s "${HF[@]}" "$BASE/zentao/webhook/log-page?objectID=92201&pageNo=1&pageSize=10")
eq "日志分页按 webhook 过滤（92201 一行、且是失败）" "1" "$(echo "$R" | field "d['total']")"

echo "--- 11. 删除（逻辑删）---"
R=$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/webhook/delete?id=${NEWID}")
eq "删除成功" "0" "$(echo "$R" | rcode)"
eq "删除是逻辑删（deleted=1）" "1" \
   "$(mysql_query "SELECT deleted+0 FROM \`ruoyi-vue-pro\`.zt_webhook WHERE id=${NEWID};")"
check "删不存在的 webhook → 404 语义" 1020036000 \
  "$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/webhook/delete?id=999999")" "Webhook 不存在"

echo "--- 12. 清理 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_webhook WHERE id NOT IN (92200,92201);
DELETE FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='webhook';
DELETE FROM \`ruoyi-vue-pro\`.zt_action WHERE id IN (99200,99201,99202);"
curl -s -X DELETE "${HF[@]}" "$BASE/zentao/webhook/mock-clear" >/dev/null
eq "清理后只剩 2 条演示 webhook" "2" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_webhook WHERE deleted=0;")"
eq "清理后 webhook 日志已清空" "0" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='webhook';")"

echo
echo "===== 结果：通过 $PASS 项，失败 $FAIL 项 ====="
[ "$FAIL" = "0" ] || exit 1

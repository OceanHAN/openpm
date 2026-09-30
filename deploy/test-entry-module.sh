#!/bin/bash
# 应用接入（entry）模块测试：校验链 / 两种签名 / 防重放 / IP 白名单 / 调用日志
#
# 禅道语义（module/entry + common::checkEntry + common::checkEntryToken + common::checkIP）：
#   1. 校验链顺序与错误码（module/entry/config.php 的 errcode 原样）：
#        缺 code / 缺 token / 没配 key / token 不对          → 401
#        IP 不在白名单 / 非免密又没绑账号                    → 403
#        应用不存在                                          → 404
#        时间戳不大于 calledTime（重放）                     → 405
#        账号在用户表里不存在                                → 406
#        时间戳格式不对（截断后非 10 位或首位 >= '4'）       → 407
#   2. 两种签名：time 模式 md5(code+key+time) + 防重放回写 calledTime；query 模式 md5(md5(query)+key)
#   3. 一个**照抄的怪癖**：time 只校验「截断后 10 位、首位 < '4'」，但摘要用的是**原始**字符串 ——
#      所以 13 位毫秒时间戳也能通过（第 3.9 节专门验它）
#   4. IP 白名单的六种形态：* / 精确 / 逗号列表 / a-b 区间 / 192.168.1.* / CIDR（第 4 节逐条验）
#   5. 调用日志落在通用表 zt_log（objectType='entry'），**只有校验通过才写**（第 6 节验）
#
# 有意偏离（README 3.43）：校验通过不建立登录会话（yudao 的认证归 OAuth2），只做校验 + 记账 + 返回账号。
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
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }

# 组装 JSON body（避免手写转义引号，坑位 #46）
jbody() {
  python3 - "$@" <<'PY'
import json,sys
d={}
for kv in sys.argv[1:]:
    k,_,v=kv.partition('=')
    d[k]=v
print(json.dumps(d))
PY
}
# 免登录的校验接口
verify() { curl -s -X POST "$BASE/zentao/entry/verify" -H 'tenant-id: 1' -H 'Content-Type: application/json' -d "$1"; }
# 管理端的签名助手
sign() { curl -s "${HF[@]}" "$BASE/zentao/entry/sign?$1" | field "d['token']"; }
# query 里带 & 必须 URL 编码，否则服务端只收到第一段（curl 的坑，不是接口的坑）
sign_query() { curl -s -G "${HF[@]}" "$BASE/zentao/entry/sign" --data-urlencode "code=$1" --data-urlencode "query=$2" | field "d['token']"; }
# 时间戳：从「现在 + 100 秒」起，每次调用在**文件里**自增 —— 因为断言里的
# next_ts 常写在 $( ... ) 子 shell 里，用 shell 变量自增不会传回父 shell，
# 结果每次都拿到同一个时间戳，全被防重放判成 405（本轮踩过的坑）。
TS_FILE=/tmp/.entry-test-ts
TS=$(( $(date +%s) + 100 ))
next_ts() {
  local n
  n=$(cat "$TS_FILE" 2>/dev/null || echo 0)
  n=$((n + 1)); echo "$n" > "$TS_FILE"
  printf '%s' $((TS + n))
}
log_count() { mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='entry' AND objectID=$1;"; }

KEY_OA='c0f0e1d2a3b4c5d6e7f8091a2b3c4d5e'

echo "===== 应用接入（校验链 / 两种签名 / 防重放 / IP 白名单 / 调用日志）测试 ====="

echo "--- 1. 准备：重建禅道那 5 条演示应用 + 清空调用日志 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_entry;
DELETE FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='entry';
INSERT INTO \`ruoyi-vue-pro\`.zt_entry (name,account,code,\`key\`,freePasswd,ip,createdBy,createdDate,calledTime) VALUES
('OA 办公系统','admin','oa','$KEY_OA',0,'*','admin',NOW(),0),
('门户免密进入','admin','portal','a1b2c3d4e5f60718293a4b5c6d7e8f90',1,'127.0.0.1,192.168.1.*','admin',NOW(),0),
('内网受限应用','admin','restricted','ffeeddccbbaa99887766554433221100',0,'10.0.0.0/8','admin',NOW(),0),
('gitfox','admin','gitfox','0000000000000000000000000000gitf',0,'*','admin',NOW(),0),
('绑定了不存在的账号','ghost','ghost','1234567890abcdef1234567890abcdef',0,'*','admin',NOW(),0);"
DB_TOTAL=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_entry WHERE deleted=0;")
eq "库里有 5 条演示应用" "5" "$DB_TOTAL"
eq "列表里只有 4 条（gitfox 是内置的，禅道 getList 过滤掉）" "4" \
   "$(curl -s "${HF[@]}" "$BASE/zentao/entry/page?pageNo=1&pageSize=20" | field "d['total']")"
eq "simple-list 同样不露出 gitfox" "0" \
   "$(curl -s "${HF[@]}" "$BASE/zentao/entry/simple-list" | field "[e['code'] for e in d].count('gitfox')")"
eq "但 gitfox 这条记录确实还在库里" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_entry WHERE code='gitfox';")"

echo "--- 2. CRUD 与字段校验 ---"
EID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/entry/create" \
  -d '{"name":"测试应用","code":"oa2","account":"admin","ip":"*","freePasswd":0}' | d)
[ -n "$EID" ] && { PASS=$((PASS+1)); printf '  ✅ 新建应用 oa2，密钥自动生成（id=%s）\n' "$EID"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建失败：$EID"; }
eq "自动生成的密钥是 32 位" "32" "$(curl -s "${HF[@]}" "$BASE/zentao/entry/get?id=$EID" | field "len(d['key'])")"
eq "代号、名称、绑定账号都落库" "oa2|测试应用|admin" \
   "$(mysql_query "SELECT CONCAT(code,'|',name,'|',account) FROM \`ruoyi-vue-pro\`.zt_entry WHERE id=$EID;")"
eq "random-key 也返回 32 位（对应界面的「重新生成密钥」）" "32" \
   "$(curl -s "${HF[@]}" "$BASE/zentao/entry/random-key" | field "len(d)")"

check "代号带横线 → 拒绝（禅道 check('code','code')：字母或数字）" 400 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/entry/create" -d '{"name":"坏代号","code":"oa-3","account":"admin"}')" \
  "字母或数字"
check "代号重复 → 拒绝" 1020030001 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/entry/create" -d '{"name":"重复代号","code":"oa","account":"admin"}')" \
  "已经有"
check "非免密不绑账号 → 拒绝" 1020030008 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/entry/create" -d '{"name":"没账号","code":"noacct","freePasswd":0}')" \
  "未绑定用户"
FID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/entry/create" \
  -d '{"name":"免密不绑账号","code":"freeonly","freePasswd":1,"ip":"*"}' | d)
[ -n "$FID" ] && { PASS=$((PASS+1)); echo "  ✅ 免密应用可以不绑账号（禅道 requiredFields 收缩为 name,code,key）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 免密应用创建失败"; }
check "修改成不存在的 id → 拒绝" 1020030000 \
  "$(curl -s -X PUT "${H[@]}" "$BASE/zentao/entry/update" -d '{"id":99999999,"name":"不存在","code":"nothere","account":"admin"}')" \
  "应用不存在"
curl -s -X PUT "${H[@]}" "$BASE/zentao/entry/update" \
  -d "{\"id\":$EID,\"name\":\"测试应用改名\",\"code\":\"oa2\",\"account\":\"admin\",\"ip\":\"192.168.9.9\"}" >/dev/null
eq "改名成功（顺带把 IP 改成精确地址，给第 4 节用）" "测试应用改名" \
   "$(mysql_query "SELECT name FROM \`ruoyi-vue-pro\`.zt_entry WHERE id=$EID;")"

echo "--- 3. time 模式签名 + 防重放 ---"
T1=$(next_ts); TK1=$(sign "code=oa&time=$T1")
eq "签名助手给出 32 位 token" "32" "${#TK1}"
eq "签名等于 md5(code+key+time)（用 python 独立复算）" "$TK1" \
   "$(python3 -c "import hashlib;print(hashlib.md5(('oa'+'$KEY_OA'+'$T1').encode()).hexdigest())")"
R=$(verify "$(jbody code=oa token=$TK1 time=$T1 module=user method=apilogin)")
check "校验通过（time 模式）" 0 "$R" ""
eq "命中的是 time 模式，且解析出绑定账号与用户" "time admin 芋道源码" \
   "$(echo "$R" | field "d['tokenMode'], d['account'], d['userNickname']")"
eq "calledTime 被回写成这次的时间戳（防重放的基础）" "$T1" \
   "$(mysql_query "SELECT calledTime FROM \`ruoyi-vue-pro\`.zt_entry WHERE code='oa';")"
check "同一个时间戳再用一次 → 405 重放" 1020030010 \
  "$(verify "$(jbody code=oa token=$TK1 time=$T1 module=user method=apilogin)")" "重放"
T0=$((T1 - 10)); TK0=$(sign "code=oa&time=$T0")
check "时间戳比 calledTime 小 → 405 重放" 1020030010 \
  "$(verify "$(jbody code=oa token=$TK0 time=$T0 module=user method=apilogin)")" "重放"
check "token 改成错的 → 401 无效签名" 1020030007 \
  "$(verify "$(jbody code=oa token=deadbeefdeadbeefdeadbeefdeadbeef time=$(next_ts) module=user method=apilogin)")" \
  "无效的token参数"
check "代号不存在 → 404 语义（EMPTY_ENTRY）" 1020030000 \
  "$(verify "$(jbody code=nosuch token=deadbeef time=$(next_ts))")" "应用不存在"
check "缺 code 参数 → 参数校验" 400 "$(verify '{"token":"x"}')" "缺少 code 参数"
check "缺 token 参数 → 参数校验" 400 "$(verify '{"code":"oa"}')" "缺少 token 参数"
check "时间戳只有 8 位 → 407" 1020030011 \
  "$(verify "$(jbody code=oa token=deadbeef time=17000000)")" "错误的时间戳"
check "时间戳首位是 4（超出百年窗口）→ 407" 1020030011 \
  "$(verify "$(jbody code=oa token=deadbeef time=4700000000)")" "错误的时间戳"

echo "--- 3.9 照抄的怪癖：13 位毫秒时间戳能通过（校验用截断值、摘要用原值） ---"
T2=$(next_ts); MS="${T2}500"; TKM=$(sign "code=oa&time=$MS")
check "毫秒时间戳 + 原样拼接的摘要 → 校验通过" 0 \
  "$(verify "$(jbody code=oa token=$TKM time=$MS module=user method=apilogin)")" ""
eq "写回的 calledTime 是截断后的 10 位秒值" "$T2" \
   "$(mysql_query "SELECT calledTime FROM \`ruoyi-vue-pro\`.zt_entry WHERE code='oa';")"

echo "--- 4. IP 白名单的六种形态（全部通过校验接口验证） ---"
try_ip() { # $1=code $2=clientIp $3=wantCode $4=说明
  local ts tk r
  ts=$(next_ts); tk=$(sign "code=$1&time=$ts")
  r=$(verify "$(jbody code=$1 token=$tk time=$ts clientIp=$2 module=user method=apilogin)")
  check "$4（clientIp=$2）" "$3" "$r" ""
}
try_ip oa 203.0.113.9 0        "① '*' 通配：任意 IP 放行"
try_ip restricted 10.1.2.3 0   "② CIDR 10.0.0.0/8：命中"
try_ip restricted 8.8.8.8 1020030006 "② CIDR 10.0.0.0/8：不在段内被拒（403）"
try_ip portal 192.168.1.77 0   "③ 192.168.1.* 通配（逗号列表里的第二条）"
try_ip portal 127.0.0.1 0      "③ 逗号列表里的精确 IP"
try_ip portal 192.168.2.77 1020030006 "③ 两个规则都不命中 → 403"
try_ip oa2 192.168.9.9 0       "④ 精确 IP 命中"
try_ip oa2 192.168.9.10 1020030006 "④ 精确 IP 不等于 → 403"
RID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/entry/create" \
  -d '{"name":"区间应用","code":"rangeapp","account":"admin","ip":"192.168.1.10-192.168.1.20"}' | d)
try_ip rangeapp 192.168.1.15 0  "⑤ a-b 区间命中"
try_ip rangeapp 192.168.1.25 1020030006 "⑤ a-b 区间之外 → 403"
WID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/entry/create" \
  -d '{"name":"两位通配","code":"wild2","account":"admin","ip":"192.168.*"}' | d)
try_ip wild2 192.168.9.9 0      "⑥ 192.168.* 两位通配（按点的个数补 0/255）"
try_ip wild2 192.169.9.9 1020030006 "⑥ 192.169.* 不在段内 → 403"
check "非 '*' 白名单下，非法来源 IP 直接拒绝（比禅道严：禅道 ip2long 失败会退化成 0）" 1020030006 \
  "$(ts=$(next_ts); tk=$(sign "code=restricted&time=$ts"); verify "$(jbody code=restricted token=$tk time=$ts clientIp=not-an-ip module=user method=apilogin)")" ""
check "'*' 白名单下不校验来源 IP 是否合法（照抄禅道：'*' 最先短路放行）" 0 \
  "$(ts=$(next_ts); tk=$(sign "code=oa&time=$ts"); verify "$(jbody code=oa token=$tk time=$ts clientIp=not-an-ip module=user method=apilogin)")" ""

echo "--- 5. 账号：非免密没绑账号 / 绑了不存在的账号 / 免密可指定账号 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_entry WHERE code='unbound';
INSERT INTO \`ruoyi-vue-pro\`.zt_entry (name,account,code,\`key\`,freePasswd,ip,createdBy,createdDate,calledTime)
VALUES ('未绑账号','','unbound','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',0,'*','admin',NOW(),0);"
check "非免密 + 账号为空 → 403 未绑定账号" 1020030008 \
  "$(ts=$(next_ts); tk=$(sign "code=unbound&time=$ts"); verify "$(jbody code=unbound token=$tk time=$ts module=user method=apilogin)")" \
  "未绑定用户"
check "绑定的账号在用户表里不存在 → 406" 1020030009 \
  "$(ts=$(next_ts); tk=$(sign "code=ghost&time=$ts"); verify "$(jbody code=ghost token=$tk time=$ts module=user method=apilogin)")" \
  "用户不存在"
# 显式带 clientIp=127.0.0.1：portal 的白名单是 127.0.0.1,192.168.1.*，
# 不指定的话会拿调用方的真实 IP —— 后端跑在服务器上时就是 192.168.0.x，会被 403（本轮真踩到）
R=$(ts=$(next_ts); tk=$(sign "code=portal&time=$ts"); verify "$(jbody code=portal token=$tk time=$ts clientIp=127.0.0.1 account=admin module=user method=apilogin)")
check "免密应用 + account 参数（m=user&f=apilogin）→ 以指定账号进入" 0 "$R" ""
eq "返回的账号就是 account 参数指定的" "admin" "$(echo "$R" | field "d['account']")"
check "免密应用指定一个不存在的账号 → 406" 1020030009 \
  "$(ts=$(next_ts); tk=$(sign "code=portal&time=$ts"); verify "$(jbody code=portal token=$tk time=$ts clientIp=127.0.0.1 account=nosuchuser module=user method=apilogin)")" \
  "用户不存在"
R=$(ts=$(next_ts); tk=$(sign "code=portal&time=$ts"); verify "$(jbody code=portal token=$tk time=$ts clientIp=127.0.0.1 account=nosuchuser module=other method=x)")
check "非 apilogin 时 account 参数被忽略（仍用绑定的 admin）" 0 "$R" ""
eq "账号回落为绑定账号" "admin" "$(echo "$R" | field "d['account']")"

echo "--- 6. query 模式签名（不带时间戳） ---"
Q='m=user&f=apilogin&account=admin'
TQ=$(sign_query oa "$Q")
eq "签名等于 md5(md5(query)+key)（独立复算）" "$TQ" \
   "$(python3 -c "import hashlib;print(hashlib.md5((hashlib.md5('$Q'.encode()).hexdigest()+'$KEY_OA').encode()).hexdigest())")"
R=$(verify "$(jbody code=oa token=$TQ query=$Q module=user method=apilogin)")
check "不带时间戳的 query 模式校验通过" 0 "$R" ""
eq "命中的是 query 模式" "query" "$(echo "$R" | field "d['tokenMode']")"
check "query 串改一个字符 → 401" 1020030007 \
  "$(verify "$(jbody code=oa token=$TQ query=m=user\&f=apilogin\&account=root)")" "无效的token参数"
check "time 模式签名失败后会 fallthrough 到 query 模式（禅道行为）：给个错 time 仍能用 query 通过" 0 \
  "$(TQ2=$(sign_query oa "$Q"); verify "$(jbody code=oa token=$TQ2 time=$(next_ts) query=$Q module=user method=apilogin)")" ""

echo "--- 7. 调用日志（zt_log）：只有校验通过才写 ---"
OA_ID=$(mysql_query "SELECT id FROM \`ruoyi-vue-pro\`.zt_entry WHERE code='oa';")
BEFORE=$(log_count "$OA_ID")
curl -s -X POST "$BASE/zentao/entry/verify" -H 'tenant-id: 1' -H 'Content-Type: application/json' \
  -d '{"code":"oa","token":"wrong","time":"1700000000"}' >/dev/null
eq "失败的校验不写日志" "$BEFORE" "$(log_count "$OA_ID")"
T7=$(next_ts); TK7=$(sign "code=oa&time=$T7")
curl -s -X POST "$BASE/zentao/entry/verify" -H 'tenant-id: 1' -H 'Content-Type: application/json' \
  -d "$(jbody code=oa token=$TK7 time=$T7 url=/index.php?m=user\&f=apilogin module=user method=apilogin)" >/dev/null
eq "成功的校验写 1 条日志" "$((BEFORE + 1))" "$(log_count "$OA_ID")"
eq "日志里记的是 objectType=entry + 请求地址" "entry|/index.php?m=user&f=apilogin" \
   "$(mysql_query "SELECT CONCAT(objectType,'|',url) FROM \`ruoyi-vue-pro\`.zt_log WHERE objectID=$OA_ID ORDER BY id DESC LIMIT 1;")"
eq "日志分页接口按应用过滤（oa 的日志数 = 库里的条数）" "$(log_count "$OA_ID")" \
   "$(curl -s "${HF[@]}" "$BASE/zentao/entry/log-page?pageNo=1&pageSize=50&objectType=entry&objectID=$OA_ID" | field "d['total']")"
eq "日志带出 URL 与结果" "1" \
   "$(curl -s "${HF[@]}" "$BASE/zentao/entry/log-page?pageNo=1&pageSize=5&objectType=entry&objectID=$OA_ID" | field "1 if d['list'][0]['url'] and d['list'][0]['date'] else 0")"
eq "按 objectID 过滤不会串到别的应用" "0" \
   "$(curl -s "${HF[@]}" "$BASE/zentao/entry/log-page?pageNo=1&pageSize=5&objectType=entry&objectID=99999999" | field "d['total']")"

echo "--- 8. 删除 ---"
PID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/entry/create" -d '{"name":"待删应用","code":"todelete","account":"admin"}' | d)
curl -s -X DELETE "${HF[@]}" "$BASE/zentao/entry/delete?id=$PID" >/dev/null
check "删除后按 id 取 → 404 语义" 1020030000 \
  "$(curl -s "${HF[@]}" "$BASE/zentao/entry/get?id=$PID")" "应用不存在"
eq "逻辑删除（deleted=1，行还在）" "1" \
   "$(mysql_query "SELECT deleted FROM \`ruoyi-vue-pro\`.zt_entry WHERE id=$PID;")"
eq "被删的应用列表里查不到" "0" \
   "$(curl -s "${HF[@]}" "$BASE/zentao/entry/page?pageNo=1&pageSize=50&code=todelete" | field "d['total']")"
check "代号可以复用吗？—— 删除后仍算重复（禅道 unique 校验不过滤 deleted）" 1020030001 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/entry/create" -d '{"name":"复用代号","code":"todelete","account":"admin"}')" \
  "已经有"

echo "--- 9. 清理：只留下 SQL 49 定义的 5 条演示应用与 2 条演示日志 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_entry WHERE code NOT IN ('oa','portal','restricted','gitfox','ghost');
DELETE FROM \`ruoyi-vue-pro\`.zt_log WHERE objectType='entry';
UPDATE \`ruoyi-vue-pro\`.zt_entry SET calledTime = 0;
INSERT INTO \`ruoyi-vue-pro\`.zt_log (objectType, objectID, \`date\`, url, result)
SELECT 'entry', id, NOW() - INTERVAL 30 MINUTE, '/index.php?m=user&f=apilogin&account=admin', 'success:time' FROM \`ruoyi-vue-pro\`.zt_entry WHERE code = 'oa';
INSERT INTO \`ruoyi-vue-pro\`.zt_log (objectType, objectID, \`date\`, url, result)
SELECT 'entry', id, NOW() - INTERVAL 5 MINUTE, '/index.php?m=user&f=apilogin&account=admin', 'success:query' FROM \`ruoyi-vue-pro\`.zt_entry WHERE code = 'portal';"
eq "清理后只剩演示应用" "5" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_entry;")"
eq "calledTime 复位（下一轮/界面自测不会被防重放挡住）" "0" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_entry WHERE calledTime <> 0;")"

echo
echo "===== 结果：通过 $PASS 项，失败 $FAIL 项 ====="
[ "$FAIL" = "0" ] || exit 1

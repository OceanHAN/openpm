#!/bin/bash
# 接口文档库（api，禅道 module/api）模块测试
#
# 禅道语义（module/api：control 1141 行 + model 1097 行 + zen 181 行，32 个 action）：
#   1. **它不是「对外 REST 接口管理」，是接口文档库**：
#      库（zt_doclib 里 type='api'）→ 目录（zt_module type='api'）→ 接口（zt_api）
#      → 可复用结构（zt_apistruct）→ 发布版本（zt_api_lib_release）
#   2. **没有 zt_apilib**：接口库就是 zt_doclib 的 type='api' 记录（model.php:636 往 TABLE_DOCLIB 插）
#   3. **两条版本链**：
#      接口 zt_api(头部) + zt_apispec(doc,version)；结构 zt_apistruct + zt_apistruct_spec(name,version)
#      接口「真有变更才 version+1」并把当前版本的 spec 先删后插（model.php:139/162）；
#      结构则是无条件 +1（control.php:544）
#   4. **发布是快照**：publishLib 把 modules/apis/structs 打成 snap JSON（model.php:39-70），
#      apis/structs 只存 id+version，内容仍在 spec 表里 → 读发布时按版本回查（model.php:312/362）
#   5. **两条唯一性**：title 在 (lib,module)、path 在 (lib,module,method)，
#      且 unique 检查**不过滤已删除行**（与 zt_company.name 同一个坑）
#   6. **乐观锁**：编辑接口时带的 editedDate 与库里不一致 → 拒绝（model.php:130-135）
#   7. **删除保护**（禅道没有，本实现有意加固）：接口被发布版本冻结 / 被结构引用时拒绝删除；
#      正规解除路径是删掉发布版本（禅道 deleteRelease 是物理删除）
#   8. 不做的：OpenAPI/Swagger 导入导出（禅道付费扩展 openapiimport，开源版 control.php:594 直接
#      editionLimited）；getModel/sql/debug（config/config.php:157-158 默认关闭的调试后门）
#
# 断言写法纪律（坑位 #46/#28）：**绝不写 `check "..." "$(curl ... -d "{\"a\":1}")"`** ——
# macOS bash 3.2 会把嵌套的 \" 当成字符串结束，body 被截断。一律「先取响应到变量、再断言」；
# 需要嵌套 JSON 的请求体一律用 python3 生成（json.dumps），彻底绕开 shell 转义。
# 中文参数一律 --data-urlencode；变量紧跟中文时写成 ${VAR}（坑位 #28）。
# 查库断言注意：mysql_query 会去掉所有空格，**多列必须 CONCAT 分隔符**，否则会粘成一个数。
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

source "$(dirname "$0")/_mysql.sh"

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')
HF=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1')

DB='`ruoyi-vue-pro`'
LIB=92751          # 演示接口库（zt_doclib，type='api'）
DEMO_API=92711     # 演示接口（已被演示发布版本 92761 冻结）
DEMO_API2=92712    # 演示接口 2
DEMO_STRUCT=92731  # 演示结构 user
DEMO_STRUCT2=92732 # 演示结构 story
DEMO_RELEASE=92761 # 演示发布版本 v1.0

check() {
  local name="$1" want="$2" body="$3" frag="$4"
  local code msg
  code=$(echo "$body" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null)
  msg=$(echo "$body" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("msg",""))' 2>/dev/null)
  if [ "$code" = "$want" ] && { [ -z "$frag" ] || [[ "$msg" == *"$frag"* ]]; }; then
    PASS=$((PASS+1)); printf '  ✅ %-58s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-58s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
eq() {
  local name="$1" want="$2" got="$3"
  if [ "$want" = "$got" ]; then
    PASS=$((PASS+1)); printf '  ✅ %-58s = %s\n' "$name" "$got"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-58s 期望[%s] 实际[%s]\n' "$name" "$want" "$got"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
rcode() { python3 -c "import sys,json;print(json.load(sys.stdin).get('code'))" 2>/dev/null; }
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }
# 用 python3 造请求体：需要嵌套 JSON（params 里的转义引号）时只能这么写，别用 shell 转义
body() { python3 -c "import json,sys;print(json.dumps(json.loads(sys.argv[1]),ensure_ascii=False))" "$1"; }

echo "===== 接口文档库（库/目录/接口/结构/发布快照/删除保护）测试 ====="

echo "--- 1. 准备：把演示数据恢复成 54-zt_api.sql 的形状 ---"
# 物理清掉测试产物（先删发布版本与结构，再删接口 —— 否则会撞上删除保护）
mysql_exec "DELETE FROM ${DB}.zt_api_lib_release WHERE lib = ${LIB} AND id <> ${DEMO_RELEASE};
DELETE FROM ${DB}.zt_apispec WHERE doc NOT IN (${DEMO_API}, ${DEMO_API2});
DELETE FROM ${DB}.zt_api WHERE lib = ${LIB} AND id NOT IN (${DEMO_API}, ${DEMO_API2});
DELETE FROM ${DB}.zt_apistruct_spec WHERE name NOT IN ('user','story');
DELETE FROM ${DB}.zt_apistruct WHERE lib = ${LIB} AND id NOT IN (${DEMO_STRUCT}, ${DEMO_STRUCT2});"
eq "库里有 2 个演示接口" "2" "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_api WHERE lib=${LIB} AND deleted=0;")"
eq "接口 92711 是 v2（版本链）" "2" "$(mysql_query "SELECT version FROM ${DB}.zt_api WHERE id=${DEMO_API};")"
eq "接口 92711 有 2 条 spec（v1/v2）" "2" "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_apispec WHERE doc=${DEMO_API};")"
eq "结构 user 有 2 条 spec（按 name 关联）" "2" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_apistruct_spec WHERE name='user';")"

echo "--- 2. 接口库：库不在本模块的表里（zt_doclib type='api'）---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/api/lib-list")
eq "接口库 1 个（演示库）" "1" "$(echo "$R" | field "len(d)")"
eq "库名/类型/接口数/结构数" "禅道接口库 api 2 2" \
   "$(echo "$R" | field "d[0]['name'], d[0]['type'], d[0]['apiCount'], d[0]['structCount']")"
eq "没有 zt_apilib 这张表（禅道从来没有，库就是 zt_doclib 的 api 型记录）" "0" \
   "$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name='zt_apilib';")"

echo "--- 3. 接口分页：按库/目录/名称/方式/状态过滤 ---"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/page" -d "lib=${LIB}" -d "pageNo=1" -d "pageSize=10")
eq "按库分页 2 条" "2" "$(echo "$R" | field "d['total']")"
eq "第一条带库名/目录名/状态文案" "禅道接口库 用户登录 开发完成" \
   "$(echo "$R" | field "d['list'][0]['libName'], d['list'][0]['moduleName'], d['list'][0]['statusName']")"
eq "列表带版本数（共 N 版）" "2" "$(echo "$R" | field "d['list'][0]['versionCount']")"
# 中文参数必须 URL 编码，直接拼进 URL 会被 Tomcat 拒（坑位 #36）
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/page" --data-urlencode "title=登录" -d "lib=${LIB}" -d "pageNo=1" -d "pageSize=10")
eq "按中文名称模糊查" "1" "$(echo "$R" | field "d['total']")"
# 目录 92701（用户与认证）要连带查出子目录 92703 下的接口 —— 禅道把目录展开成「自己+子孙」
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/page" -d "lib=${LIB}" -d "module=92701" -d "pageNo=1" -d "pageSize=10")
eq "父目录连带子目录（选中 92701 查到挂在 92703 的接口）" "1" "$(echo "$R" | field "d['total']")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/page" -d "lib=${LIB}" -d "method=POST" -d "pageNo=1" -d "pageSize=10")
eq "按请求方式过滤（POST 无数据）" "0" "$(echo "$R" | field "d['total']")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/page" -d "lib=${LIB}" -d "status=doing" -d "pageNo=1" -d "pageSize=10")
eq "按开发状态过滤（doing=开发中）" "1" "$(echo "$R" | field "d['total']")"
# 不存在的目录必须返回 0 条，不能因为「展开为空」退化成全量（坑位 #16）
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/page" -d "lib=${LIB}" -d "module=999999" -d "pageNo=1" -d "pageSize=10")
eq "不存在的目录 → 0 条（空展开不能变全量）" "0" "$(echo "$R" | field "d['total']")"

echo "--- 4. 接口详情：当前值 / 历史版本 / 发布快照回溯 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/api/get?id=${DEMO_API}")
eq "当前值是 v2、viewingVersion=0（读主表）" "2 0" "$(echo "$R" | field "d['version'], d['viewingVersion']")"
eq "v2 的响应字段树里有 v2 新增的 avatar" "1" \
   "$(echo "$R" | field "1 if 'avatar' in (d['response'] or '') else 0")"
R=$(curl -s "${HF[@]}" "$BASE/zentao/api/get?id=${DEMO_API}&version=1")
eq "指定 version=1 → 返回 v1" "1 1" "$(echo "$R" | field "d['version'], d['viewingVersion']")"
eq "v1 里没有 avatar（历史版本内容真的不一样）" "0" \
   "$(echo "$R" | field "1 if 'avatar' in (d['response'] or '') else 0")"
eq "版本链两格（新版本在前，带写者）" "2 admin" \
   "$(echo "$R" | field "len(d['versionList']), d['versionList'][0]['addedBy']")"
R=$(curl -s "${HF[@]}" "$BASE/zentao/api/get?id=${DEMO_API}&releaseID=${DEMO_RELEASE}")
eq "按发布版本回溯 → 取 snap 里冻结的 v2、带发布版本号" "2 v1.0 ${DEMO_RELEASE}" \
   "$(echo "$R" | field "d['viewingVersion'], d['releaseVersion'], d['releaseID']")"
check "取不存在的接口 → 接口不存在" 1020035000 \
  "$(curl -s "${HF[@]}" "$BASE/zentao/api/get?id=999999")" "接口不存在"
check "取不存在的版本 → 明确提示缺行" 1020035005 \
  "$(curl -s "${HF[@]}" "$BASE/zentao/api/get?id=${DEMO_API}&version=99")" "没有第 99 版"

echo "--- 5. 新建接口：三条默认值 + 两条唯一性 ---"
B=$(body "{\"lib\":${LIB},\"module\":92702,\"title\":\"测试接口-列表\",\"path\":\"/api.php/v1/test-list\"}")
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/create" -d "$B")
NEW_ID=$(echo "$R" | d)
eq "新建接口拿到编号（大于演示段 92712）" "1" "$(python3 -c "print(1 if int('${NEW_ID}' or 0) > 92712 else 0)")"
eq "落库补了禅道默认值 protocol/method/status/version" "HTTP|GET|done|1" \
   "$(mysql_query "SELECT CONCAT(protocol,'|',method,'|',status,'|',version) FROM ${DB}.zt_api WHERE id=${NEW_ID};")"
eq "同时写入 v1 的 spec（版本链第一格）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_apispec WHERE doc=${NEW_ID} AND version=1;")"
R=$(curl -s "${HF[@]}" "$BASE/zentao/api/get?id=${NEW_ID}")
eq "params 用禅道的四键默认形状" "1" \
   "$(echo "$R" | field "1 if all(k in (d['params'] or '') for k in ['header','params','paramsType','query']) else 0")"
B=$(body "{\"lib\":${LIB},\"module\":92702,\"title\":\"测试接口-列表\",\"path\":\"/api.php/v1/other\"}")
check "同 (lib,module) 下重名 → 拒绝" 1020035009 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/create" -d "$B")" "接口名称不能重复"
B=$(body "{\"lib\":${LIB},\"module\":92702,\"title\":\"测试接口-另一个\",\"path\":\"/api.php/v1/test-list\"}")
check "同 (lib,module,method) 下同路径 → 拒绝" 1020035010 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/create" -d "$B")" "已被接口"
B=$(body "{\"lib\":${LIB},\"module\":92702,\"title\":\"\",\"path\":\"/api.php/v1/x\"}")
check "接口名称为空 → 预置业务码 API_TITLE_REQUIRED" 1020035002 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/create" -d "$B")" "接口名称不能为空"
B=$(body '{"lib":92151,"module":0,"title":"挂错库","path":"/api.php/v1/wrong"}')
check "库不是接口库（传产品文档库 92151）→ 接口库不存在" 1020035001 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/create" -d "$B")" "接口库不存在"

echo "--- 6. 修改接口：真有变更才 +1；没变更只原地重写当前版本；乐观锁 ---"
B=$(body "{\"id\":${NEW_ID},\"module\":92702,\"title\":\"测试接口-列表\",\"path\":\"/api.php/v1/test-list\",\"protocol\":\"HTTP\",\"method\":\"GET\",\"status\":\"done\"}")
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/api/update" -d "$B")
eq "无字段变更的保存：请求成功" "0" "$(echo "$R" | rcode)"
eq "无变更 → version 仍是 1（禅道 createChanges 口径）" "1" \
   "$(mysql_query "SELECT version FROM ${DB}.zt_api WHERE id=${NEW_ID};")"
eq "无变更 → spec 仍只有 1 条（原地重写，不追加）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_apispec WHERE doc=${NEW_ID};")"
# 真有变更：改标题 + 换请求体 → version+1，spec 变 2 条，(doc,2) 是新内容
B=$(body "{\"id\":${NEW_ID},\"title\":\"测试接口-列表v2\",\"path\":\"/api.php/v1/test-list\",\"params\":\"{\\\"header\\\":[],\\\"params\\\":[{\\\"field\\\":\\\"page\\\",\\\"paramsType\\\":\\\"int\\\"}],\\\"paramsType\\\":\\\"json\\\",\\\"query\\\":[]}\"}")
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/api/update" -d "$B")
eq "有变更的保存：请求成功" "0" "$(echo "$R" | rcode)"
eq "有变更 → version=2" "2" "$(mysql_query "SELECT version FROM ${DB}.zt_api WHERE id=${NEW_ID};")"
eq "有变更 → spec 2 条（v1 保留、v2 新增）" "2" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_apispec WHERE doc=${NEW_ID};")"
eq "v2 的 spec 存的是新请求体（paramsType=json）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_apispec WHERE doc=${NEW_ID} AND version=2 AND params LIKE '%\"paramsType\":\"json\"%';")"
eq "v1 的老 spec 还在（历史只追加）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_apispec WHERE doc=${NEW_ID} AND version=1 AND params LIKE '%formData%';")"
B=$(body "{\"id\":${NEW_ID},\"title\":\"抢改\",\"path\":\"/api.php/v1/test-list\",\"editedDate\":\"2000-01-01 00:00:00\"}")
check "带过期的 editedDate → 拒绝（别人已经改过）" 1020035006 \
  "$(curl -s -X PUT "${H[@]}" "$BASE/zentao/api/update" -d "$B")" "已被其他人修改"
B=$(body "{\"id\":${NEW_ID},\"title\":\"测试接口-列表v3\",\"path\":\"/api.php/v1/test-list\",\"responseType\":\"text/xml\"}")
# 先人为把 responseType 改成别的值，确认「传了也改不动」不是因为本来就是空
mysql_exec "UPDATE ${DB}.zt_api SET responseType='text/xml-old' WHERE id=${NEW_ID};"
curl -s -X PUT "${H[@]}" "$BASE/zentao/api/update" -d "$B" >/dev/null
eq "responseType 传了也不改（禅道 edit 表单里没有它）" "text/xml-old" \
   "$(mysql_query "SELECT responseType FROM ${DB}.zt_api WHERE id=${NEW_ID};")"

echo "--- 7. 数据结构：分页 / 详情（按 name+version 回溯）/ 创建 ---"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/struct-page" -d "lib=${LIB}" -d "pageNo=1" -d "pageSize=10")
eq "结构分页 2 条（user/story）" "2" "$(echo "$R" | field "d['total']")"
eq "id 倒序第一条是 story、版本数 1、创建人姓名非空" "story 1 1" \
   "$(echo "$R" | field "d['list'][0]['name'], d['list'][0]['versionCount'], 1 if d['list'][0]['addedName'] else 0")"
R=$(curl -s "${HF[@]}" "$BASE/zentao/api/struct-get?id=${DEMO_STRUCT}")
eq "结构详情：user / v2 / 4 个字段" "user 2 4" \
   "$(echo "$R" | field "d['name'], d['version'], len(__import__('json').loads(d['attribute']))")"
R=$(curl -s "${HF[@]}" "$BASE/zentao/api/struct-get?id=${DEMO_STRUCT}&version=1")
eq "按 name+version 取历史版本（v1 只有 3 个字段）" "1 3" \
   "$(echo "$R" | field "d['version'], len(__import__('json').loads(d['attribute']))")"
check "结构不存在 → API_STRUCT_NOT_EXISTS" 1020035003 \
  "$(curl -s "${HF[@]}" "$BASE/zentao/api/struct-get?id=999999")" "接口结构不存在"
B=$(body "{\"lib\":${LIB},\"name\":\"testStruct\",\"type\":\"json\",\"desc\":\"测试结构\",\"attribute\":\"[{\\\"field\\\":\\\"code\\\",\\\"paramsType\\\":\\\"string\\\",\\\"required\\\":true,\\\"desc\\\":\\\"编码\\\",\\\"children\\\":[]}]\"}")
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/struct-create" -d "$B")
NEW_STRUCT=$(echo "$R" | d)
eq "新建结构拿到编号（大于演示段 92731）" "1" "$(python3 -c "print(1 if int('${NEW_STRUCT}' or 0) > 92732 else 0)")"
eq "结构同时写入 v1 的 spec（按 name）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_apistruct_spec WHERE name='testStruct' AND version=1;")"
B=$(body "{\"lib\":${LIB},\"name\":\"\"}")
check "结构名为空 → 参数校验" 400 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/struct-create" -d "$B")" "结构名不能为空"

echo "--- 8. 发布版本：列表 / 打快照 / 版本号唯一 / 按发布浏览 ---"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/release-list" -d "libID=${LIB}")
eq "演示发布版本 1 个" "1" "$(echo "$R" | field "len(d)")"
eq "快照计数：3 个目录 / 2 个接口 / 2 个结构" "3 2 2" \
   "$(echo "$R" | field "d[0]['moduleCount'], d[0]['apiCount'], d[0]['structCount']")"
eq "snap.apis 只存 id+version（不是内容副本）" "[{'id': 92711, 'version': 2}, {'id': 92712, 'version': 1}]" \
   "$(echo "$R" | field "d[0]['snapApis']")"
B=$(body "{\"lib\":${LIB},\"version\":\"v1.1\",\"desc\":\"测试发布\"}")
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/release-create" -d "$B")
NEW_RELEASE=$(echo "$R" | d)
eq "发布成功拿到编号（大于演示段 92761）" "1" "$(python3 -c "print(1 if int('${NEW_RELEASE}' or 0) > 92761 else 0)")"
eq "快照写进 zt_api_lib_release.snap（含 modules 与 structs）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_api_lib_release WHERE id=${NEW_RELEASE} AND snap LIKE '%\"modules\"%' AND snap LIKE '%\"structs\"%';")"
eq "新发布冻结了 3 个接口（含第 5 步新建的）" "3" \
   "$(mysql_query "SELECT JSON_LENGTH(snap, '\$.apis') FROM ${DB}.zt_api_lib_release WHERE id=${NEW_RELEASE};")"
B=$(body "{\"lib\":${LIB},\"version\":\"v1.1\"}")
check "同库重复版本号 → 拒绝" 1020035011 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/release-create" -d "$B")" "已经存在版本"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/page" -d "releaseID=${NEW_RELEASE}" -d "pageNo=1" -d "pageSize=10")
eq "按新发布浏览：3 个接口（走快照）" "3" "$(echo "$R" | field "d['total']")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/page" -d "releaseID=${NEW_RELEASE}" -d "lib=92151" -d "pageNo=1" -d "pageSize=10")
eq "发布只属于一个库：换库查 → 0 条" "0" "$(echo "$R" | field "d['total']")"

echo "--- 9. 删除保护：被发布冻结 → 解除 → 又被结构引用 → 解除后删除 ---"
R=$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/api/delete?id=${NEW_ID}")
check "接口已被发布版本冻结 → 拒绝删除" 1020035007 "$R" "冻结"
eq "拒绝之后接口还在（没被误删）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_api WHERE id=${NEW_ID} AND deleted=0;")"
R=$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/api/release-delete?id=${NEW_RELEASE}")
eq "删除发布版本成功（解除冻结的正规路径）" "0" "$(echo "$R" | rcode)"
eq "发布版本是真删（物理删除，禅道 deleteRelease 原样）" "0" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_api_lib_release WHERE id=${NEW_RELEASE};")"
# 造一条「结构引用接口」的数据：字段类型直接写接口编号（正常只会是内置类型或结构编号）
B=$(body "{\"lib\":${LIB},\"name\":\"refToApi\",\"type\":\"json\",\"attribute\":\"[{\\\"field\\\":\\\"x\\\",\\\"paramsType\\\":\\\"${NEW_ID}\\\",\\\"children\\\":[]}]\"}")
curl -s -X POST "${H[@]}" "$BASE/zentao/api/struct-create" -d "$B" >/dev/null
R=$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/api/delete?id=${NEW_ID}")
check "接口被数据结构引用 → 拒绝删除" 1020035008 "$R" "被数据结构"
mysql_exec "DELETE FROM ${DB}.zt_apistruct WHERE name='refToApi'; DELETE FROM ${DB}.zt_apistruct_spec WHERE name='refToApi';"
R=$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/api/delete?id=${NEW_ID}")
eq "解除引用后删除成功" "0" "$(echo "$R" | rcode)"
eq "删除是软删 zt_api（deleted=1），spec 历史仍保留" "1|3" \
   "$(mysql_query "SELECT CONCAT((SELECT deleted+0 FROM ${DB}.zt_api WHERE id=${NEW_ID}),'|',(SELECT COUNT(*) FROM ${DB}.zt_apispec WHERE doc=${NEW_ID}));")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/api/page" --data-urlencode "title=测试接口-列表v3" -d "lib=${LIB}" -d "pageNo=1" -d "pageSize=10")
eq "被删除的接口在列表里不再出现" "0" "$(echo "$R" | field "d['total']")"
B=$(body "{\"lib\":${LIB},\"module\":92702,\"title\":\"测试接口-列表v3\",\"path\":\"/api.php/v1/test-list\"}")
check "被删接口的名字仍然占位（unique 不过滤已删除）" 1020035009 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/create" -d "$B")" "接口名称不能重复"

echo "--- 10. 无 OpenAPI 导入导出（禅道付费扩展：开源版直接 editionLimited）---"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/api/import-open-api" -d '{}')
IMP_CODE=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null)
IMP_MSG=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("msg",""))' 2>/dev/null)
if [ "$IMP_CODE" = "404" ]; then
  PASS=$((PASS+1)); echo "  ✅ 没有 OpenAPI 导入接口（禅道把它放在付费扩展 openapiimport 里）：$IMP_MSG"
else
  FAIL=$((FAIL+1)); echo "  ❌ 竟然有导入接口：code=$IMP_CODE msg=$IMP_MSG"
fi

echo "--- 11. 清理：恢复演示数据 ---"
mysql_exec "DELETE FROM ${DB}.zt_api_lib_release WHERE lib=${LIB} AND id <> ${DEMO_RELEASE};
DELETE FROM ${DB}.zt_apispec WHERE doc NOT IN (${DEMO_API}, ${DEMO_API2});
DELETE FROM ${DB}.zt_api WHERE lib=${LIB} AND id NOT IN (${DEMO_API}, ${DEMO_API2});
DELETE FROM ${DB}.zt_apistruct WHERE lib=${LIB} AND id NOT IN (${DEMO_STRUCT}, ${DEMO_STRUCT2});
DELETE FROM ${DB}.zt_apistruct_spec WHERE name NOT IN ('user','story');"
eq "清理后接口 2 条" "2" "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_api WHERE lib=${LIB};")"
eq "清理后结构 2 条（user + story）" "2" "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_apistruct WHERE lib=${LIB};")"
eq "清理后发布版本 1 条" "1" "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_api_lib_release WHERE lib=${LIB};")"
eq "演示接口 92711 仍是 v2、2 条 spec（没被测试改脏）" "2|2" \
   "$(mysql_query "SELECT CONCAT((SELECT version FROM ${DB}.zt_api WHERE id=${DEMO_API}),'|',(SELECT COUNT(*) FROM ${DB}.zt_apispec WHERE doc=${DEMO_API}));")"

echo
echo "===== 结果：通过 $PASS 项，失败 $FAIL 项 ====="
[ "$FAIL" = "0" ] || exit 1

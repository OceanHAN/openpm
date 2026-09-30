#!/bin/bash
# 公司信息（company，禅道界面上的「组织视图」）模块测试
#
# 禅道语义（module/company：5 个 action，control 211 + model 132 + zen 177 行）：
#   1. zt_company 是「公司信息」：name/phone/fax/address/zipcode/website/backyard/guest/admins
#   2. admins 是**逗号串**（安装时写 ",admin,"），禅道判超管就是 strpos(admins, ",account,")；
#      yudao 侧超管是 super_admin 角色（硬编码放行、不查权限表）—— 本模块给「口径对照」接口
#   3. getFirst() 取 id 最小的一条当本公司；getOutsideCompanies() 就是 id != 1
#   4. update 两条规则：name 必填 + unique（**不过滤已删除**）；website/backyard 正好等于
#      "http://" 时清空（表单预填值）
#   5. browse（组织成员）与 dynamic（组织动态）不重复实现，分别复用 organization / action 模块
#   6. 有意没有 delete：禅道 module/company 没有删除 action（本模块也一样）
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
rcode() { python3 -c "import sys,json;print(json.load(sys.stdin).get('code'))" 2>/dev/null; }
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }

echo "===== 公司信息（组织视图：公司信息 / 外部公司 / 超管口径 / 复用模块）测试 ====="

echo "--- 1. 准备：重建演示公司（id=1 本公司 + 两家外部公司）---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_company WHERE id IN (1,2,3) OR name LIKE '测试公司%';
INSERT INTO \`ruoyi-vue-pro\`.zt_company (id,name,phone,fax,address,zipcode,website,backyard,guest,admins) VALUES
(1,'示例科技有限公司','0532-88886666','0532-88886667','山东省青岛市崂山区示例路 1 号','266100','https://www.example.com','http://192.168.0.10',0,',admin,'),
(2,'甲方信息科技有限公司','010-66668888','','北京市海淀区中关村示例大厦 8 层','100080','','',0,''),
(3,'乙方软件服务有限公司','021-55556666','','上海市浦东新区示例园区 3 号楼','200120','','',0,'');"
eq "库里有 3 家公司" "3" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_company WHERE deleted=0;")"

echo "--- 2. 本公司（getFirst：id 最小的一条）与列表 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/company/get-first")
eq "本公司是 id=1、名字对、admins 是 ,admin," "1 示例科技有限公司 ,admin," \
   "$(echo "$R" | field "d['id'], d['name'], d['admins']")"
eq "公司列表 3 条" "3" "$(curl -s "${HF[@]}" "$BASE/zentao/company/list" | field "len(d)")"
# 中文参数必须 URL 编码，直接拼在 URL 里 Tomcat 会 400（项目里踩过的坑）
PAGE=$(curl -s -G "${HF[@]}" "$BASE/zentao/company/page" --data-urlencode "name=示例科技" -d "pageNo=1" -d "pageSize=10")
eq "按名称模糊分页（示例）" "1" "$(echo "$PAGE" | field "d['total']")"
check "取不存在的公司 → 404 语义" 1020031000 "$(curl -s "${HF[@]}" "$BASE/zentao/company/get?id=999999")" "公司不存在"

echo "--- 3. 外部公司下拉（禅道 ajaxGetOutsideCompany：id != 1）---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/company/outside-list")
eq "外部公司 2 家（不含 id=1 的本公司）" "2" "$(echo "$R" | field "len(d)")"
eq "返回 text/value/keys 三件套（禅道原样结构）" "甲方信息科技有限公司 2 甲方信息科技有限公司" \
   "$(echo "$R" | field "d[0]['text'], d[0]['value'], d[0]['keys']")"
eq "下拉里没有本公司（id=1）" "0" "$(echo "$R" | field "[x['value'] for x in d].count(1)")"

echo "--- 4. 新建公司：唯一性 + http:// 归一 ---"
CID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/company/create" \
  -d '{"name":"测试公司甲","phone":"123","website":"http://","backyard":"http://"}' | d)
[ -n "$CID" ] && { PASS=$((PASS+1)); printf '  ✅ 新建公司（id=%s）\n' "$CID"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建公司失败：$CID"; }
eq "只填 http:// 的官网/内网被清空（禅道 setIF 行为）" "1" \
   "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_company WHERE id=$CID AND website='' AND backyard='';")"
check "公司重名 → 拒绝" 1020031001 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/company/create" -d '{"name":"测试公司甲"}')" "已经有"
check "名称为空 → 参数校验" 400 \
  "$(curl -s -X POST "${H[@]}" "$BASE/zentao/company/create" -d '{"name":""}')" "公司名称不能为空"

echo "--- 5. 修改公司信息 ---"
check "改不存在的公司 → 404 语义" 1020031000 \
  "$(curl -s -X PUT "${H[@]}" "$BASE/zentao/company/update" -d '{"id":999999,"name":"不存在"}')" "公司不存在"
# 注意：JSON 里带转义引号时**不能**写成 check "..." "$(curl ... -d "{\"a\":1}")" ——
# macOS bash 3.2 会把嵌套的 \" 当成字符串结束，body 被截断，服务端报 JSON parse error（坑位 #46）。
# 一律改成「先取响应到变量，再断言」两步走。
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/company/update" -d "{\"id\":$CID,\"name\":\"示例科技有限公司\"}")
check "改成已有的名字 → 拒绝（unique）" 1020031001 "$R" "已经有"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/company/update" -d '{"id":1,"name":"示例科技有限公司","phone":"0532-99998888"}')
eq "本公司改自己的名字（unique 排除自己 → 允许）" "0" "$(echo "$R" | rcode)"
eq "电话已更新" "0532-99998888" "$(mysql_query "SELECT phone FROM \`ruoyi-vue-pro\`.zt_company WHERE id=1;")"
curl -s -X PUT "${H[@]}" "$BASE/zentao/company/update" -d "{\"id\":$CID,\"name\":\"测试公司甲\",\"website\":\"http://\",\"backyard\":\"https://intra.example.com\"}" >/dev/null
eq "编辑时 website 只填 http:// → 清空、backyard 保留" "|https://intra.example.com" \
   "$(mysql_query "SELECT CONCAT(website,'|',backyard) FROM \`ruoyi-vue-pro\`.zt_company WHERE id=$CID;")"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/company/update" -d "{\"id\":1,\"name\":\"示例科技有限公司\",\"admins\":\",hacker,\"}")
check "admins 不在编辑表单里：传了也不改（禅道 form->edit 没有这一项）" 0 "$R" ""
eq "admins 仍是 ,admin,（写入被忽略）" ",admin," \
   "$(mysql_query "SELECT admins FROM \`ruoyi-vue-pro\`.zt_company WHERE id=1;")"
eq "guest 可以改（字段照存）" "1" \
   "$(curl -s -X PUT "${H[@]}" "$BASE/zentao/company/update" -d '{"id":1,"name":"示例科技有限公司","guest":1}' >/dev/null; mysql_query "SELECT guest FROM \`ruoyi-vue-pro\`.zt_company WHERE id=1;")"
curl -s -X PUT "${H[@]}" "$BASE/zentao/company/update" -d '{"id":1,"name":"示例科技有限公司","guest":0}' >/dev/null

echo "--- 6. 超管口径对照（zt_company.admins ↔ super_admin 角色）---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/company/admins")
eq "禅道口径里是 admin" "['admin']" "$(echo "$R" | field "d['zentaoAdmins']")"
eq "yudao 口径里也有 admin（已对齐）" "1" "$(echo "$R" | field "1 if 'admin' in d['matched'] else 0")"
# 差异集合必须自洽：matched + onlyInZentao == zentaoAdmins（yudao 侧超管有几个是环境数据决定的，不做硬编码断言）
eq "差异集合自洽（matched ∪ onlyInZentao = zentaoAdmins）" "1" \
   "$(echo "$R" | field "1 if set(d['matched'])|set(d['onlyInZentao'])==set(d['zentaoAdmins']) else 0")"
echo "     （yudao 侧超管 $(echo "$R" | field "len(d['yudaoSuperAdmins'])") 个，只在 yudao 侧 $(echo "$R" | field "len(d['onlyInYudao'])") 个）"
eq "接口带口径说明（不是只给数据）" "1" "$(echo "$R" | field "1 if d['note'] and 'super_admin' in d['note'] else 0")"

echo "--- 7. 复用模块：组织成员 / 组织动态 ---"
eq "组织成员列表（复用 organization 的 user-list）" "1" \
   "$(curl -s "${HF[@]}" "$BASE/zentao/organization/user-list" | field "1 if len(d)>0 and d[0]['account'] else 0")"
eq "组织动态（复用 action 的 dynamic，全公司不按对象过滤）" "1" \
   "$(curl -s "${HF[@]}" "$BASE/zentao/action/dynamic?period=all&limit=5" | field "1 if len(d)>0 else 0")"

echo "--- 8. 没有删除接口（与禅道一致）---"
# yudao 对「路径不存在」是 NoResourceFoundException 处理器兜的：HTTP 200 但 body 里 code=404
R=$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/company/delete?id=$CID")
DCODE=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null)
DMSG=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("msg",""))' 2>/dev/null)
if [ "$DCODE" = "404" ] && [[ "$DMSG" == *"company/delete"* ]]; then
  PASS=$((PASS+1)); echo "  ✅ 没有 DELETE /zentao/company/delete（禅道没有删除 action，本实现也没有）：$DMSG"
else
  FAIL=$((FAIL+1)); echo "  ❌ 竟然有删除接口：code=$DCODE msg=$DMSG"
fi

echo "--- 9. 清理 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_company WHERE id NOT IN (1,2,3);"
eq "清理后只剩演示的 3 家" "3" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_company;")"

echo
echo "===== 结果：通过 $PASS 项，失败 $FAIL 项 ====="
[ "$FAIL" = "0" ] || exit 1

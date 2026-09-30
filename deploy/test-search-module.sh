#!/bin/bash
# 保存查询 / 搜索（search）模块测试
#
# 禅道语义（module/search：11 个 action + model 1,211 行）：
#   1. zt_userquery = 「保存搜索条件」：列表页搜索后能存下来、一键复用、设快捷方式/公共查询
#   2. zt_searchdict = 拼音首字母码表（key=汉字码点，value=一位字母），实现「按拼音搜中文」
#   3. zt_searchindex = 跨对象全文检索（InnoDB FULLTEXT）—— **本实现不做**，在这里断言语义边界
#
# ⚠️ 关键差异：禅道把条件序列化成**一段 SQL**存进 zt_userquery.sql、列表页直接拼 WHERE；
#    本实现存**结构化条件 JSON**（field/op/value），由各模块的强类型查询 VO 翻译执行。
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
eq() {
  local name="$1" want="$2" got="$3"
  if [ "$want" = "$got" ]; then
    PASS=$((PASS+1)); printf '  ✅ %-52s = %s\n' "$name" "$got"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-52s 期望[%s] 实际[%s]\n' "$name" "$want" "$got"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }
rcode() { python3 -c "import sys,json;print(json.load(sys.stdin).get('code'))" 2>/dev/null; }

echo "===== 保存查询 / 搜索（保存查询 + 拼音码表 + 越权保护）测试 ====="

echo "--- 1. 准备：恢复 SQL 52 的演示数据 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_userquery;
INSERT INTO \`ruoyi-vue-pro\`.zt_userquery (account,module,title,form,\`sql\`,shortcut,common) VALUES
('admin','story','演示：激活的需求','{\"fields\":[{\"field\":\"status\"}]}','[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"active\"}]',1,0),
('admin','bug','演示：未解决的 Bug','{\"fields\":[{\"field\":\"status\"}]}','[{\"field\":\"status\",\"op\":\"ne\",\"value\":\"resolved\"}]',0,1);"
eq "库里有 2 条演示查询" "2" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_userquery;")"

echo "--- 2. 列表 / 详情 / 按模块取 ---"
eq "分页总数" "2" "$(curl -s -G "${HF[@]}" "$BASE/zentao/search/query/page" -d "account=admin" -d "pageNo=1" -d "pageSize=10" | field "d['total']")"
QID=$(mysql_query "SELECT id FROM \`ruoyi-vue-pro\`.zt_userquery WHERE title='演示：激活的需求';")
eq "详情带出条件 JSON（不是 SQL）" '[{"field":"status","op":"eq","value":"active"}]' \
   "$(curl -s "${HF[@]}" "$BASE/zentao/search/query/get?id=$QID" | field "d['conditions']")"
eq "story 模块下能取到（我的 + 公共）" "1" \
   "$(curl -s -G "${HF[@]}" "$BASE/zentao/search/query/list" --data-urlencode "module=story" -d "account=admin" | field "len(d)")"
eq "bug 模块的公共查询对别人也可见" "1" \
   "$(curl -s -G "${HF[@]}" "$BASE/zentao/search/query/list" --data-urlencode "module=bug" -d "account=yudao" | field "len(d)")"
eq "快捷方式只有 1 条（story 那条）" "1" \
   "$(curl -s -G "${HF[@]}" "$BASE/zentao/search/query/shortcut-list" --data-urlencode "module=story" -d "account=admin" | field "len(d)")"

echo "--- 3. 新建 / 修改 / 删除 ---"
NEW=$(curl -s -X POST "${H[@]}" "$BASE/zentao/search/query/save" \
  -d '{"module":"task","title":"测试查询-高优先级","conditions":"[{\"field\":\"pri\",\"op\":\"le\",\"value\":1}]","shortcut":0,"common":0}' | d)
[ -n "$NEW" ] && { PASS=$((PASS+1)); printf '  ✅ 新建查询（id=%s）\n' "$NEW"; } || { FAIL=$((FAIL+1)); echo "  ❌ 新建失败：$NEW"; }
eq "条件 JSON 原样落库（没有被拼成 SQL）" '[{"field":"pri","op":"le","value":1}]' \
   "$(mysql_query "SELECT \`sql\` FROM \`ruoyi-vue-pro\`.zt_userquery WHERE id=$NEW;")"
check "名称为空 → 参数校验" 400 "$(curl -s -X POST "${H[@]}" "$BASE/zentao/search/query/save" -d '{"module":"task","title":""}')" "查询名称不能为空"
check "模块为空 → 参数校验" 400 "$(curl -s -X POST "${H[@]}" "$BASE/zentao/search/query/save" -d '{"module":"","title":"x"}')" "模块不能为空"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/search/query/shortcut?id=$NEW&shortcut=1")
check "设为快捷方式" 0 "$R" ""
eq "库里 shortcut=1" "1" "$(mysql_query "SELECT shortcut FROM \`ruoyi-vue-pro\`.zt_userquery WHERE id=$NEW;")"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/search/query/shortcut?id=$NEW&shortcut=0")
eq "取消快捷方式后 shortcut=0" "0" "$(mysql_query "SELECT shortcut FROM \`ruoyi-vue-pro\`.zt_userquery WHERE id=$NEW;")"
R=$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/search/query/delete?id=$NEW")
check "删除自己的查询" 0 "$R" ""
eq "删除是**物理删除**（这张表没有 deleted 列）" "0" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_userquery WHERE id=$NEW;")"
check "删除不存在的 → 404 语义" 1020033000 "$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/search/query/delete?id=$NEW")" "保存的查询不存在"

echo "--- 4. 越权保护：不能改/删别人的查询 ---"
OTHER=$(mysql_query "SELECT id FROM \`ruoyi-vue-pro\`.zt_userquery WHERE account='yudao' LIMIT 1;")
if [ -z "$OTHER" ]; then
  mysql_exec "INSERT INTO \`ruoyi-vue-pro\`.zt_userquery (account,module,title,\`sql\`,shortcut,common) VALUES ('yudao','story','别人的查询','[]',0,0);"
  OTHER=$(mysql_query "SELECT id FROM \`ruoyi-vue-pro\`.zt_userquery WHERE account='yudao' LIMIT 1;")
fi
check "删别人的查询 → 拒绝" 1020033001 "$(curl -s -X DELETE "${HF[@]}" "$BASE/zentao/search/query/delete?id=$OTHER")" "只能操作自己的保存查询"
check "改别人的快捷方式 → 拒绝" 1020033001 "$(curl -s -X PUT "${HF[@]}" "$BASE/zentao/search/query/shortcut?id=$OTHER&shortcut=0")" "只能操作自己的保存查询"

echo "--- 5. 拼音首字母码表（zt_searchdict）---"
eq "码表条数（演示 2 条）" "2" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_searchdict;")"
R=$(curl -s -G "${HF[@]}" "$BASE/zentao/search/dict/pinyin" --data-urlencode "text=需求")
eq "命中码表的字取到首字母（需→x、求→q → xq）" "xq" "$(echo "$R" | d)"
eq "未命中码表的字原样保留（禅道也是这个降级）" "abc" \
   "$(curl -s -G "${HF[@]}" "$BASE/zentao/search/dict/pinyin" --data-urlencode "text=abc" | d)"

echo "--- 6. 语义边界：全文检索（zt_searchindex）本实现不做 ---"
eq "库里**没有** zt_searchindex 表（有意不做，不是漏了）" "0" \
   "$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name='zt_searchindex';")"

echo "--- 7. 清理：恢复演示数据 ---"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_userquery;
INSERT INTO \`ruoyi-vue-pro\`.zt_userquery (account,module,title,form,\`sql\`,shortcut,common) VALUES
('admin','story','演示：激活的需求','{\"fields\":[{\"field\":\"status\"}]}','[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"active\"}]',1,0),
('admin','bug','演示：未解决的 Bug','{\"fields\":[{\"field\":\"status\"}]}','[{\"field\":\"status\",\"op\":\"ne\",\"value\":\"resolved\"}]',0,1);"
eq "清理后只剩演示数据" "2" "$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_userquery;")"

echo
echo "===== 结果：通过 $PASS 项，失败 $FAIL 项 ====="
[ "$FAIL" = "0" ] || exit 1

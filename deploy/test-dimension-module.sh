#!/bin/bash
# 维度（dimension，BI 的 1.5 级导航）模块测试
#
# 禅道语义（module/dimension：control 72 行 + model 118 行 + zen 14 行，2 个 action）：
#   1. 一张 zt_dimension 把大屏/透视表/图表归到「宏观/效能/质量」三个维度下；开源版预置 3 行
#      （db/zentao.sql:14579），且**不含维度 CRUD / 管理界面**（唯一的写入口是
#      upgrade/model.php:6971 直接 INSERT）——所以本实现只有查询 + 切换，没有写接口。
#   2. 可见性判定不在 dimension 模块里，而在 biModel::getViewableObject('dimension')
#      （module/bi/model.php:41-65）：acl='open' 或 createdBy=自己 或 whitelist 命中自己；超管直通全部。
#   3. 「末次维度」四级兜底链（dimensionModel::saveState，model.php:83-117）：
#      配置 → 会话 → 可见性校验（不在可见集合里就取可见的第一条）→ getFirst()。
#      拿到后双写 session（按 app->tab 分桶）+ setting 项 {account}common.dimension.lastDimension。
#   4. 1.5 级导航下拉的两处参数例外（control.php:39-46）：
#      module=pivot & method=design → method 改写成 browse；
#      tab=bi & module=tree & method=browsegroup → 参数追加 groupID=0&type={viewType}。
#
# 本实现的四处有意偏离（README 有记录，测试按新语义断言）：
#   a. 没有维度 CRUD（开源版就没有）→ 第 8 节专门验「POST /create 不存在」；
#   b. 末次维度写 Redis（键 zentao:dimension:last:{tab}:{account}）而不是 session+setting 表；
#   c. 下拉链接形状是 /{module}/{method}?{params}（禅道是 index.php?m=x&f=y&...）；
#   d. /get 多一层可见性校验（禅道 getByID 不校验，因为它从不暴露成 HTTP 接口）。
#
# 断言写法两条纪律：
#   - JSqlParser/MySQL 的坑与本模块无关，但**中文参数一律 --data-urlencode**（本模块入参都是数字/ASCII，
#     中文只出现在响应里；仍然统一用 -G --data-urlencode，避免任何「裸中文进 URL」的风险）。
#   - JSON 里带转义引号时不要写进 "$( ... )"（macOS bash 3.2 会截断 body，坑位 #46）：
#     一律「先取响应到变量、再断言」。
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

source "$(dirname "$0")/_mysql.sh"

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')
HF=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1')
DB='`ruoyi-vue-pro`'

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
# 取 visibility 诊断里某个维度的「命中判据 + 是否可见」（python 的元组会带上括号和引号，所以单独算）
dim_row() { python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
x=[y for y in d['dimensions'] if y['id']==$1][0]
print(x['reason'], x['visible'])"; }

# 查询参数统一走 -G --data-urlencode：中文参数必须 URL 编码（本模块入参都是数字/ASCII，
# 但统一写法就不会有「裸中文进 URL」这类漏网）
get() { curl -s -G "${HF[@]}" "$BASE$1" "${@:2}"; }

echo "===== 维度（BI 1.5 级导航：只读维度 + 切换语义 + 可见性口径）测试 ====="

echo "--- 0. 准备：清掉可能残留的测试数据，恢复禅道预置的 3 行 ---"
mysql_exec "DELETE FROM ${DB}.zt_dimension WHERE id >= 94201;
DELETE FROM ${DB}.zt_searchdict WHERE \`key\` IN (23439,35266,31649,29702,32500,24230);
UPDATE ${DB}.zt_dimension SET \`desc\`='', acl='open', whitelist=NULL, createdBy='system',
  createdDate='2023-04-27 20:22:16', editedBy='', editedDate=NULL, deleted=0 WHERE id IN (1,2,3);"
# 末次维度记录（Redis）先归位到 1，保证后面的断言从确定状态出发
get "/zentao/dimension/get-dimension" --data-urlencode "dimensionID=1" --data-urlencode "tab=bi" >/dev/null

echo "--- 1. 表结构与预置数据（对齐 db/zentao.sql:651 + :14579）---"
eq "预置维度 3 行" "3" "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_dimension WHERE deleted=0;")"
eq "代号顺序 macro,efficiency,quality" "macro,efficiency,quality" \
   "$(mysql_query "SELECT GROUP_CONCAT(code ORDER BY id) FROM ${DB}.zt_dimension WHERE deleted=0;")"
eq "菜单 90190 页面 + 90191 权限行都在" "2" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.system_menu WHERE id IN (90190,90191) AND deleted=0;")"

echo "--- 2. 可见维度列表 / 按 id 取（禅道 getList / getByID）---"
R=$(get "/zentao/dimension/list")
eq "可见维度 3 条" "3" "$(echo "$R" | field "len(d)")"
eq "第一条是宏观管理维度 macro" "1 宏观管理维度 macro" "$(echo "$R" | field "d[0]['id'], d[0]['name'], d[0]['code']")"
eq "第三条是质量管理维度 quality" "quality" "$(echo "$R" | field "d[2]['code']")"
R=$(get "/zentao/dimension/get" --data-urlencode "id=1")
eq "get?id=1 返回宏观管理维度" "1 宏观管理维度 macro" "$(echo "$R" | field "d['id'], d['name'], d['code']")"
check "取不存在的维度 → 维度不存在" 1020034000 "$(get "/zentao/dimension/get" --data-urlencode "id=999999")" "维度不存在"

echo "--- 3. 可见性口径（biModel::getViewableObject：acl / createdBy / whitelist / 超管直通）---"
# 造两条私有维度：94201 由 ghost 创建（命中 createdBy），94202 白名单里有 ghost3（命中 whitelist）
mysql_exec "INSERT INTO ${DB}.zt_dimension (id,name,code,\`desc\`,acl,whitelist,createdBy,createdDate)
VALUES (94201,'测试私有维度甲','test-private-a','','private','','ghost','2026-01-01 00:00:00'),
       (94202,'测试私有维度乙','test-private-b','','private','ghost3','ghost2','2026-01-01 00:00:00')
ON DUPLICATE KEY UPDATE acl='private', whitelist=VALUES(whitelist), createdBy=VALUES(createdBy), deleted=0;"
R=$(get "/zentao/dimension/list")
eq "超管直通：admin 看到 5 条（3 条演示 + 2 条私有）" "5" "$(echo "$R" | field "len(d)")"
R=$(get "/zentao/dimension/visibility" --data-urlencode "account=admin")
eq "admin 是超管" "True" "$(echo "$R" | field "d['superAdmin']")"
eq "超管的判据一律是 admin（短路，不看 acl）" "5" \
   "$(echo "$R" | field "len([x for x in d['dimensions'] if x['reason']=='admin' and x['visible']])")"
R=$(get "/zentao/dimension/visibility" --data-urlencode "account=ghost")
eq "ghost 不是超管" "False" "$(echo "$R" | field "d['superAdmin']")"
eq "94201 对 ghost 可见（createdBy 命中）" "creator True" "$(echo "$R" | dim_row 94201)"
eq "94202 对 ghost 不可见（不是创建者、不在白名单）" "none False" "$(echo "$R" | dim_row 94202)"
R=$(get "/zentao/dimension/visibility" --data-urlencode "account=ghost3")
eq "94202 对 ghost3 可见（whitelist 命中）" "whitelist True" "$(echo "$R" | dim_row 94202)"
R=$(get "/zentao/dimension/visibility" --data-urlencode "account=ghost4")
eq "viewableIds 与逐行 visible 自洽" "True" \
   "$(echo "$R" | field "d['viewableIds']==[x['id'] for x in d['dimensions'] if x['visible']]")"

echo "--- 4. 当前维度：末次维度四级兜底链（dimensionModel::getDimension）---"
R=$(get "/zentao/dimension/get-dimension" --data-urlencode "dimensionID=2" --data-urlencode "tab=bi")
eq "传 dimensionID=2 → 2（source=explicit）" "2 explicit" "$(echo "$R" | field "d['dimensionID'], d['source']")"
R=$(get "/zentao/dimension/get-dimension" --data-urlencode "tab=bi")
eq "不传参数 → 2（走末次访问记录，source=last）" "2 last" "$(echo "$R" | field "d['dimensionID'], d['source']")"
R=$(get "/zentao/dimension/get-dimension" --data-urlencode "dimensionID=999999" --data-urlencode "tab=bi")
eq "传不存在的 id → 回退到可见的第一条（source=fallback）" "1 fallback" \
   "$(echo "$R" | field "d['dimensionID'], d['source']")"
R=$(get "/zentao/dimension/get-dimension" --data-urlencode "dimensionID=3" --data-urlencode "tab=chart")
eq "chart 标签页切到 3" "3 explicit" "$(echo "$R" | field "d['dimensionID'], d['source']")"
R=$(get "/zentao/dimension/get-dimension" --data-urlencode "tab=bi")
eq "标签页分桶：bi 仍是 1" "1" "$(echo "$R" | field "d['dimensionID']")"
eq "切换维度不会改维度数据（editedDate 全空）" "0" \
   "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_dimension WHERE id IN (1,2,3) AND (editedDate IS NOT NULL OR editedBy<>'');")"

echo "--- 5. 1.5 级导航下拉（ajaxGetDropMenu 的 data/link/labelMap + 两处参数例外）---"
R=$(get "/zentao/dimension/drop-menu" --data-urlencode "dimensionID=1" --data-urlencode "module=pivot" \
        --data-urlencode "method=design" --data-urlencode "viewType=pivot" --data-urlencode "tab=bi")
eq "例外①：pivot + design → method 改写为 browse" "pivot browse" "$(echo "$R" | field "d['module'], d['method']")"
eq "例外①的 params 保持 dimensionID={id}" "dimensionID={id}" "$(echo "$R" | field "d['params']")"
eq "link.dimension = /pivot/browse?dimensionID={id}" "/pivot/browse?dimensionID={id}" \
   "$(echo "$R" | field "d['link']['dimension']")"
eq "下拉项 5 条（含两条私有，因为 admin 是超管）+ 三件套原样" "5 1 宏观管理维度 宏观管理维度" \
   "$(echo "$R" | field "len(d['data']), d['data'][0]['id'], d['data'][0]['text'], d['data'][0]['keys']")"
eq "searchHint/labelMap/expandName/itemType 四个键原样" "搜索 维度 closed dimension" \
   "$(echo "$R" | field "d['searchHint'], d['labelMap']['dimension'], d['expandName'], d['itemType']")"
R=$(get "/zentao/dimension/drop-menu" --data-urlencode "dimensionID=1" --data-urlencode "module=tree" \
        --data-urlencode "method=browsegroup" --data-urlencode "viewType=pivot" --data-urlencode "tab=bi")
eq "例外②：tab=bi + tree/browsegroup → 追加 groupID/type" "dimensionID={id}&groupID=0&type=pivot" \
   "$(echo "$R" | field "d['params']")"
eq "例外②的 link 同步带上参数" "/tree/browsegroup?dimensionID={id}&groupID=0&type=pivot" \
   "$(echo "$R" | field "d['link']['dimension']")"
R=$(get "/zentao/dimension/drop-menu" --data-urlencode "dimensionID=1" --data-urlencode "module=tree" \
        --data-urlencode "method=browsegroup" --data-urlencode "viewType=pivot" --data-urlencode "tab=other")
eq "tab 守卫：不是 bi 就不追加（禅道 \$this->app->tab == 'bi'）" "dimensionID={id}" \
   "$(echo "$R" | field "d['params']")"
check "缺 module/method → 参数校验 400" 400 \
  "$(get "/zentao/dimension/drop-menu" --data-urlencode "dimensionID=1")" ""

echo "--- 6. 下拉项拼音 keys（走 search 模块的 zt_searchdict 码表）---"
# 码表是「缺字就原样保留」的降级语义：先验降级，再临时补 6 个字验真的出拼音，补完立刻删掉
# （zt_searchdict 的演示数据只有 3 条，test-search-module.sh 有断言钉着这个数字）
eq "码表没这些字时 keys 退化成维度名（禅道 convert2Pinyin 的降级行为）" "质量管理维度" \
   "$(get "/zentao/dimension/drop-menu" --data-urlencode "module=pivot" --data-urlencode "method=browse" \
        --data-urlencode "tab=bi" | field "d['data'][2]['keys']")"
mysql_exec "INSERT IGNORE INTO ${DB}.zt_searchdict (\`key\`,\`value\`) VALUES
(23439,'h'),(35266,'g'),(31649,'g'),(29702,'l'),(32500,'w'),(24230,'d');"
eq "补上码表后 keys = hgglwd（宏h观g管g理l维w度d）" "hgglwd" \
   "$(get "/zentao/dimension/drop-menu" --data-urlencode "module=pivot" --data-urlencode "method=browse" \
        --data-urlencode "tab=bi" | field "d['data'][0]['keys']")"
mysql_exec "DELETE FROM ${DB}.zt_searchdict WHERE \`key\` IN (23439,35266,31649,29702,32500,24230);"
eq "删掉临时码表后 searchdict 回到演示的 2 条" "2" "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_searchdict;")"

echo "--- 7. 没有维度 CRUD（开源版只有只读维度，本实现也不提供写接口）---"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/dimension/create" -d '{"name":"不该存在的接口","code":"x"}')
DCODE=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null)
DMSG=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("msg",""))' 2>/dev/null)
if [ "$DCODE" = "404" ] && [[ "$DMSG" == *"dimension/create"* ]]; then
  PASS=$((PASS+1)); echo "  ✅ 没有 POST /zentao/dimension/create（开源版没有维度 CRUD）：$DMSG"
else
  FAIL=$((FAIL+1)); echo "  ❌ 竟然有维度写接口：code=$DCODE msg=$DMSG"
fi

echo "--- 8. 清理：删掉临时维度、还原演示数据 ---"
get "/zentao/dimension/get-dimension" --data-urlencode "dimensionID=1" --data-urlencode "tab=bi" >/dev/null
get "/zentao/dimension/get-dimension" --data-urlencode "dimensionID=1" --data-urlencode "tab=chart" >/dev/null
mysql_exec "DELETE FROM ${DB}.zt_dimension WHERE id >= 94201;"
eq "清理后只剩演示的 3 条维度" "3" "$(mysql_query "SELECT COUNT(*) FROM ${DB}.zt_dimension WHERE deleted=0;")"
R=$(get "/zentao/dimension/list")
eq "清理后可见维度回到 3 条" "3" "$(echo "$R" | field "len(d)")"

echo
echo "===== 结果：通过 $PASS 项，失败 $FAIL 项 ====="
[ "$FAIL" = "0" ] || exit 1

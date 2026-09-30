#!/bin/bash
# 看板（kanban）接口测试
#
# 禅道语义（module/kanban）：
#   1. 七层聚合：空间 → 看板 → 区域 → 分组 → 泳道 / 列 → 卡片，
#      卡片的位置存在 zt_kanbancell.cards（泳道 × 列的逗号列表）
#   2. 建看板/建区域会**自动建默认布局**：1 个分组 + 「默认泳道」+
#      四个默认列（未开始/进行中/已完成/已关闭，WIP 不限）+ 所有格子
#   3. 列的在制品上限（WIP）：-1 不限或正整数；**子列之和不能超过父列限额**，
#      父列有限额时子列不能不限（createColumn / checkChildColumn）
#   4. 卡片移动：按**源泳道类型**把卡片从该区域所有格子里摘掉，再追加到目标格子，
#      card.group 跟着目标泳道走（model.php:moveCard）
#   5. WIP 超限**后端不阻止**（禅道只在界面标红）→ data 里用 overWip 提示
#   6. 完成卡片 = progress 100 + done；激活卡片 = doing + 进度 0~99
#   7. 列与卡片是**物理删除**，泳道/区域/看板/空间是逻辑删除
#
# 【写断言的方式】所有请求都**先把响应取到变量、再交给 check**。
# 不要写成 check "..." N "$(curl ... -d "{\"a\":1}")" —— macOS 的 bash 3.2 会把
# 嵌套引号拆开，服务端收到半截字符串（实测报 no String-argument constructor ... ('column')）。
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

source "$(dirname "$0")/_mysql.sh"

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')

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
data() { curl -s "${H[@]}" "$BASE/zentao/kanban/data?kanbanId=$1"; }

echo "===== 看板（kanban）模块测试 ====="
TS=$(date +%s)

# 清掉上次中断留下的测试看板（脚本要可重复执行；中断时它会让「演示空间只有 1 个看板」的断言失败）
for id in $(curl -s "${H[@]}" "$BASE/zentao/kanban/list?space=96001" \
  | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data') or []
print(' '.join(str(x['id']) for x in d if x['name'].startswith('接口测试看板-')))"); do
  curl -s -X DELETE "${H[@]}" "$BASE/zentao/kanban/delete?id=$id" >/dev/null
done

echo "--- 1. 演示数据与默认布局 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/kanban/space/list")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
m={x['id']:x for x in d}
s=m.get(96001)
print(s['name'], s['type'], s['typeName'], s['kanbanCount'], s['status'])")
[ "${line}" = "禅道研发空间 cooperation 协作空间 1 active" ] && { PASS=$((PASS+1)); echo "  ✅ 演示空间 96001：协作空间 / 1 个看板"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示空间 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/kanban/get?id=96101")
line=$(echo "$R" | field "d['name'], d['regionCount'], d['cardCount'], d['acl'], d['showWIP'], d['colWidth']")
[ "${line}" = "禅道迁移看板 1 2 extend 1 264" ] && { PASS=$((PASS+1)); echo "  ✅ 演示看板 96101：1 区域 / 2 卡片 / 继承空间权限"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示看板 实际=${line}"; }

R=$(data 96101)
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
r=d['regions'][0]
print(len(d['regions']), r['name'], len(r['lanes']), len(r['columns']),
      [c['name'] for c in r['columns']], [c['limit'] for c in r['columns']])")
[ "${line}" = "1 默认区域 2 4 ['未开始', '进行中', '已完成', '已关闭'] [-1, 3, -1, -1]" ] && { PASS=$((PASS+1)); echo "  ✅ 视图数据：1 区域 / 2 泳道 / 4 列（进行中 WIP=3，其余不限）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 视图数据 实际=${line}"; }

line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['regions'][0]
lanes={l['name']: l for l in d['lanes']}
def cards_of(lane, col):
    for c in lanes[lane]['cells']:
        if c['columnName']==col: return [x['name'] for x in c['cards']], c['overWip']
    return [], None
print(cards_of('默认泳道','进行中'), cards_of('需求泳道','未开始'), cards_of('默认泳道','未开始'))")
[ "${line}" = "(['打通登录链路'], False) (['需求池分层视图'], False) ([], False)" ] && { PASS=$((PASS+1)); echo "  ✅ 卡片位置：96501 在默认泳道的进行中、96502 在需求泳道的未开始"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 卡片位置 实际=${line}"; }

echo "--- 2. 新建看板 → 默认布局 ---"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/create" \
  -d "{\"space\":96001,\"name\":\"接口测试看板-$TS\",\"owner\":\"admin\",\"team\":\"admin,tester\",\"acl\":\"extend\"}")
KB=$(echo "$R" | d)
[ -n "$KB" ] && { PASS=$((PASS+1)); echo "  ✅ 新建看板成功（编号 $KB）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建看板失败：$(echo "$R" | head -c 200)"; }

R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/create" -d "{\"space\":96001,\"name\":\"接口测试看板-$TS\"}")
check "同空间重名 → 拒绝" 1020025011 "$R" "已存在同名看板"

R=$(data "$KB")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
r=d['regions'][0]
print(len(d['regions']), r['name'], [l['name'] for l in r['lanes']], [l['type'] for l in r['lanes']],
      [c['name'] for c in r['columns']], [c['limit'] for c in r['columns']],
      len(r['lanes'][0]['cells']))")
[ "${line}" = "1 默认区域 ['默认泳道'] ['common'] ['未开始', '进行中', '已完成', '已关闭'] [-1, -1, -1, -1] 4" ] && { PASS=$((PASS+1)); echo "  ✅ 默认布局：默认区域 + 默认泳道 + 4 个默认列（都不限）+ 4 个格子"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 默认布局 实际=${line}"; }

RS=$(curl -s "${H[@]}" "$BASE/zentao/kanban/region/list?kanban=$KB")
RG=$(echo "$RS" | field "d[0]['id'], d[0]['groupId'], d[0]['laneCount'], d[0]['columnCount']")
GRP=$(echo "$RG" | cut -d' ' -f2)
echo "  区域摘要: $RG"
R=$(curl -s "${H[@]}" "$BASE/zentao/kanban/column/list?group=$GRP")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), [c['name'] for c in d], all(c['groupId']==$GRP for c in d))")
[ "${line}" = "4 ['未开始', '进行中', '已完成', '已关闭'] True" ] && { PASS=$((PASS+1)); echo "  ✅ 列通过 group 归属（4 列，都挂在同一个分组下）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 列列表 实际=${line}"; }

echo "--- 3. 列与在制品上限（WIP）规则 ---"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/column/create" -d "{\"group\":$GRP,\"name\":\"坏列\",\"limit\":0}")
check "WIP=0 → 拒绝" 1020025041 "$R" "在制品上限"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/column/create" -d "{\"group\":$GRP,\"name\":\"坏列\",\"limit\":-2}")
check "WIP=-2 → 拒绝" 1020025041 "$R" "在制品上限"

R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/column/create" -d "{\"group\":$GRP,\"name\":\"评审中-$TS\",\"limit\":5,\"order\":2}")
COL5=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/kanban/column/list?group=$GRP")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
row=[c for c in d if c['id']==$COL5][0]
print(len(d), row['name'], row['limit'], row['order'])")
[ "${line}" = "5 评审中-$TS 5 2" ] && { PASS=$((PASS+1)); echo "  ✅ 建列 limit=5 成功，并插到 order=2（后面的列整体后移）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建列 实际=${line}"; }

R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/column/create" -d "{\"group\":$GRP,\"name\":\"子列A-$TS\",\"limit\":3,\"parent\":$COL5}")
CH1=$(echo "$R" | d)
[ -n "$CH1" ] && { PASS=$((PASS+1)); echo "  ✅ 建子列（parent=$COL5，limit=3 ≤ 父列 5）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建子列失败：$(echo "$R" | head -c 160)"; }
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/column/create" -d "{\"group\":$GRP,\"name\":\"子列B-$TS\",\"limit\":3,\"parent\":$COL5}")
check "子列之和超过父列限额 → 拒绝" 1020025042 "$R" "不能超过父列"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/column/create" -d "{\"group\":$GRP,\"name\":\"子列C-$TS\",\"limit\":-1,\"parent\":$COL5}")
check "父列有限额时子列不限 → 拒绝" 1020025042 "$R" "不能超过父列"
R=$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/kanban/column/delete?id=$COL5")
check "已拆分的父列不能删" 1020025043 "$R" "子列"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/kanban/column/archive?id=$CH1")
check "归档列" 0 "$R" ""
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/kanban/column/restore?id=$CH1")
check "还原列" 0 "$R" ""

echo "--- 4. 泳道 ---"
RID=$(echo "$RG" | cut -d' ' -f1)
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/lane/create" \
  -d "{\"region\":$RID,\"name\":\"需求泳道-$TS\",\"type\":\"story\",\"color\":\"#476BDA\"}")
LN2=$(echo "$R" | d)
COLN=$(curl -s "${H[@]}" "$BASE/zentao/kanban/column/list?group=$GRP" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")
R=$(data "$KB")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['regions'][0]
lane=[l for l in d['lanes'] if l['id']==$LN2][0]
print(lane['name'], lane['type'], lane['color'], len(lane['cells'])==$COLN, len(lane['cells']))")
[ "${line}" = "需求泳道-$TS story #476BDA True 6" ] && { PASS=$((PASS+1)); echo "  ✅ 新建泳道并补齐该泳道在该分组**所有列**（含子列）下的格子"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建泳道 实际=${line}"; }
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/lane/create" -d "{\"region\":$RID,\"name\":\"坏泳道\",\"type\":\"bogus\"}")
check "泳道类型非法 → 拒绝" 1020025031 "$R" "泳道类型不合法"

echo "--- 5. 卡片 ---"
LN1=$(data "$KB" | field "d['regions'][0]['lanes'][0]['id']")
COL_DOING=$(data "$KB" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['regions'][0]
print([c['columnId'] for c in d['lanes'][0]['cells'] if c['columnName']=='进行中'][0])")
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/card/create" \
  -d "{\"kanban\":$KB,\"lane\":$LN1,\"column\":$COL_DOING,\"name\":\"接口测试卡片-$TS\",\"pri\":2,\"assignedTo\":\"dev1\",\"estimate\":3.5}")
CARD=$(echo "$R" | d)
[ -n "$CARD" ] && { PASS=$((PASS+1)); echo "  ✅ 新建卡片成功（编号 $CARD）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建卡片失败：$(echo "$R" | head -c 200)"; }
R=$(data "$KB")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['regions'][0]['lanes'][0]
cell=[c for c in d['cells'] if c['columnName']=='进行中'][0]
print(cell['cardCount'], [x['name'] for x in cell['cards']], cell['overWip'], cell['limit'])")
[ "${line}" = "1 ['接口测试卡片-$TS'] False -1" ] && { PASS=$((PASS+1)); echo "  ✅ 卡片落在「默认泳道 × 进行中」的格子里"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 卡片位置 实际=${line}"; }

R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/card/create" -d "{\"kanban\":$KB,\"lane\":$LN1,\"column\":$COL_DOING,\"name\":\"坏卡片\",\"estimate\":-1}")
check "预计工时为负 → 拒绝" 1020025051 "$R" "不能为负数"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/card/create" -d "{\"kanban\":$KB,\"lane\":$LN1,\"column\":$COL_DOING,\"name\":\"坏卡片\",\"begin\":\"2026-03-10\",\"end\":\"2026-03-01\"}")
check "截止早于开始 → 拒绝" 1020025052 "$R" "不能早于"

COL_DONE=$(data "$KB" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['regions'][0]
print([c['columnId'] for c in d['lanes'][0]['cells'] if c['columnName']=='已完成'][0])")
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/card/move?cardId=$CARD&fromColumnId=$COL_DOING&toColumnId=$COL_DONE&fromLaneId=$LN1&toLaneId=$LN1")
check "移动卡片（列 进行中→已完成）" 0 "$R" ""
R=$(data "$KB")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['regions'][0]['lanes'][0]
def n(col):
    c=[x for x in d['cells'] if x['columnName']==col][0]
    return [y['id'] for y in c['cards']]
print(n('进行中'), n('已完成'))")
[ "${line}" = "[] [$CARD]" ] && { PASS=$((PASS+1)); echo "  ✅ 移动后：源格子空、目标格子有卡片（cards 逗号列表跟着变）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 移动结果 实际=${line}"; }

# 换泳道：源泳道 common、目标泳道 story —— 摘除要按**源泳道类型**扫，否则卡片会同时留在两个格子里
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/kanban/card/move?cardId=$CARD&fromColumnId=$COL_DONE&toColumnId=$COL_DONE&fromLaneId=$LN1&toLaneId=$LN2")
check "移动卡片（泳道 默认→需求）" 0 "$R" ""
R=$(data "$KB")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['regions'][0]
lanes={l['id']: l for l in d['lanes']}
def ids(lane):
    return [y['id'] for c in lanes[lane]['cells'] if c['columnName']=='已完成' for y in c['cards']]
print(ids($LN1), ids($LN2))")
[ "${line}" = "[] [$CARD]" ] && { PASS=$((PASS+1)); echo "  ✅ 换泳道后卡片只在新泳道的格子里（源泳道格子清空）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 换泳道结果 实际=${line}"; }

R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/kanban/card/finish?id=$CARD")
check "完成卡片" 0 "$R" ""
R=$(curl -s "${H[@]}" "$BASE/zentao/kanban/card/get?id=$CARD")
line=$(echo "$R" | field "d['status'], '%.0f' % d['progress'], d['statusName']")
[ "${line}" = "done 100 已完成" ] && { PASS=$((PASS+1)); echo "  ✅ 完成卡片：progress=100 + status=done"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 完成卡片 实际=${line}"; }
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/kanban/card/activate?id=$CARD&progress=100")
check "激活卡片进度 100 → 拒绝" 1020025053 "$R" "进度必须"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/kanban/card/activate?id=$CARD&progress=-5")
check "激活卡片进度 -5 → 拒绝" 1020025053 "$R" "进度必须"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/kanban/card/activate?id=$CARD&progress=30")
check "激活卡片进度 30" 0 "$R" ""
R=$(curl -s "${H[@]}" "$BASE/zentao/kanban/card/get?id=$CARD")
line=$(echo "$R" | field "d['status'], '%.0f' % d['progress']")
[ "${line}" = "doing 30" ] && { PASS=$((PASS+1)); echo "  ✅ 激活卡片：status=doing + progress=30"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 激活卡片 实际=${line}"; }

R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/kanban/card/archive?id=$CARD")
check "归档卡片" 0 "$R" ""
R=$(curl -s "${H[@]}" "$BASE/zentao/kanban/card/get?id=$CARD")
line=$(echo "$R" | field "d['archived']")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 归档卡片 archived=true"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 归档卡片 实际=${line}"; }
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/kanban/card/restore?id=$CARD")
check "还原卡片" 0 "$R" ""
R=$(curl -s "${H[@]}" "$BASE/zentao/kanban/card/page?kanban=$KB&pageNo=1&pageSize=10")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['total'], d['list'][0]['id']==$CARD)")
[ "${line}" = "1 True" ] && { PASS=$((PASS+1)); echo "  ✅ 卡片分页按看板过滤（1 张）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 卡片分页 实际=${line}"; }

echo "--- 6. 物理删除与级联 ---"
R=$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/kanban/card/delete?id=$CARD")
check "删除卡片（物理删除）" 0 "$R" ""
R=$(data "$KB")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['regions'][0]
print(sum(c['cardCount'] for l in d['lanes'] for c in l['cells']))")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 删除后视图里没有卡片"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删除后视图 实际=${line}"; }
line=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_kanbancell WHERE kanban = $KB AND cards LIKE '%,$CARD,%';")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 格子里也没留下悬空卡片编号（本实现顺手摘掉，禅道会留）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 格子悬空编号 实际=${line}"; }

R=$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/kanban/space/delete?id=96001")
check "空间下还有看板时不能删空间" 1020025002 "$R" "还有"
R=$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/kanban/delete?id=$KB")
check "删除看板" 0 "$R" ""
R=$(curl -s "${H[@]}" "$BASE/zentao/kanban/get?id=$KB")
check "删掉后看板不存在" 1020025010 "$R" "看板不存在"
line=$(mysql_query "SELECT CONCAT(
  (SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_kanbanregion WHERE kanban = $KB AND deleted = 0), ':',
  (SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_kanbanlane WHERE region IN (SELECT id FROM \`ruoyi-vue-pro\`.zt_kanbanregion WHERE kanban = $KB) AND deleted = 0), ':',
  (SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_kanbancell WHERE kanban = $KB));")
[ "${line}" = "0:0:0" ] && { PASS=$((PASS+1)); echo "  ✅ 删看板级联软删区域/泳道/列/卡片，格子物理清理干净"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 级联清理 实际=${line}"; }

R=$(data 96101)
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d['regions']), sum(len(l['cells']) for l in d['regions'][0]['lanes']),
      sum(len(c['cards']) for l in d['regions'][0]['lanes'] for c in l['cells']))")
[ "${line}" = "1 8 2" ] && { PASS=$((PASS+1)); echo "  ✅ 演示看板 96101 完好（1 区域 / 8 格子 / 2 卡片）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示看板被影响 实际=${line}"; }

# 兜底清理
curl -s -X DELETE "${H[@]}" "$BASE/zentao/kanban/delete?id=$KB" >/dev/null

echo "======================================================"
echo "  看板模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

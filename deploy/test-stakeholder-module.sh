#!/bin/bash
# 干系人（stakeholder）接口测试
#
# 禅道语义：zt_stakeholder 记的是「某个项目集/项目相关的人」，**不是团队成员**：
#   团队成员（zt_team）  = 要干活的人（有角色、有可用工时）
#   干系人（zt_stakeholder）= 需要知情/被影响的人（甲方、领导、外部顾问……）
# 三条关键规则：
#   ① type 由 from 推导：from=outside → outside，其余 → inside（module/stakeholder/model.php#create）
#   ② 同一个人不能重复加到同一个对象下（禅道 check：user unique(objectID=? AND deleted=0)）
#   ③ 删除是按「对象 + 账号」的（禅道 delete(userID)）
BASE=${ZENTAO_API_BASE:-http://127.0.0.1:48080/admin-api}
PASS=0; FAIL=0

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')

check() {
  local name="$1" want="$2" body="$3" frag="$4"
  local code msg
  code=$(echo "$body" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null)
  msg=$(echo "$body" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("msg",""))' 2>/dev/null)
  if [ "$code" = "$want" ] && { [ -z "$frag" ] || [[ "$msg" == *"$frag"* ]]; }; then
    PASS=$((PASS+1)); printf '  ✅ %-48s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-48s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
n() { python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data') or []))"; }

echo "===== 干系人模块测试 ====="
TS=$(date +%s)

echo "--- 1. 演示数据：项目集 9001 / 项目 1 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/list?objectType=program&objectID=9001")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), ' '.join('%s:%s:%s:%s' % (x['user'], x['typeName'], x['key'], x['fromName']) for x in d))")
[ "${line}" = "3 admin:内部:1:团队成员 张三（甲方）:外部:1:外部人员 tester:内部:0:公司同事" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 项目集干系人（关键排前面）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示数据 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/list?objectType=project&objectID=1")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), ' '.join(x['user'] for x in d))")
[ "${line}" = "2 admin tester" ] && { PASS=$((PASS+1)); echo "  ✅ 项目干系人 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 项目干系人 实际=${line}"; }

# 干系人 ≠ 团队成员：加干系人不该动团队人数
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stakeholder/create" \
  -d "{\"objectType\":\"project\",\"objectID\":1,\"user\":\"dev2-${TS}\",\"from\":\"company\",\"key\":0}")
check "给项目 1 加一个公司同事干系人" 0 "$R"
S1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/project/get?id=1")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['teamCount'])")
[ "${line}" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 团队人数仍是 3（干系人不进 zt_team）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 团队人数被影响 实际=${line}"; }

echo "--- 2. 新增：type 由 from 推导 / 唯一性 / 校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stakeholder/create" \
  -d "{\"objectType\":\"program\",\"objectID\":9001,\"user\":\"甲方李四-${TS}\",\"from\":\"outside\",\"key\":1}")
check "加一个外部干系人（from=outside）" 0 "$R"
S2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/get?id=${S2}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['type'], d['typeName'], d['key'], d['fromName'], d['realname'])")
[ "${line}" = "outside 外部 1 外部人员 甲方李四-${TS}" ] \
  && { PASS=$((PASS+1)); echo "  ✅ type 由 from 推导 + 外部人员直接用存下来的名字 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 外部干系人 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stakeholder/create" \
  -d "{\"objectType\":\"program\",\"objectID\":9001,\"user\":\"admin\",\"from\":\"team\"}")
check "重复添加同一个人 → 拒绝" 1020023001 "$R" "已经在这个对象下"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stakeholder/create" \
  -d "{\"objectType\":\"program\",\"objectID\":9001,\"user\":\"someone-${TS}\",\"from\":\"xxx\"}")
check "来源不合法 → 拒绝" 1020023003 "$R" "来源不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stakeholder/create" \
  -d "{\"objectType\":\"product\",\"objectID\":1,\"user\":\"someone-${TS}\",\"from\":\"team\"}")
check "挂在产品下 → 拒绝（只能挂项目集/项目）" 1020023002 "$R" "干系人只能挂在项目集/项目下"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stakeholder/create" \
  -d "{\"objectType\":\"program\",\"objectID\":9001,\"from\":\"team\"}")
check "不填干系人 → 拒绝" 400 "$R"

echo "--- 3. 批量添加（已存在的跳过）---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/stakeholder/batch-create?objectType=program&objectID=9001&from=company" \
  -d "[\"admin\",\"batch1-${TS}\",\"batch2-${TS}\",\"tester\"]")
check "批量添加 4 个（其中 admin/tester 已存在）" 0 "$R"
added=$(echo "$R" | n)
[ "${added}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 实际只新增 2 个（已有的静默跳过，与禅道一致）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新增数量 实际=${added}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/list?objectType=program&objectID=9001")
line=$(echo "$R" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")
[ "${line}" = "6" ] && { PASS=$((PASS+1)); echo "  ✅ 项目集干系人共 6 个（3 演示 + 外部 1 + 批量 2）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 总数 实际=${line}"; }

echo "--- 4. 修改：关键标记与来源联动 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/stakeholder/update" \
  -d "{\"id\":${S1},\"objectType\":\"project\",\"objectID\":1,\"user\":\"dev2-${TS}\",\"from\":\"outside\",\"key\":1}")
check "把公司同事改成外部 + 标关键" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/get?id=${S1}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['type'], d['key'], d['fromName'])")
[ "${line}" = "outside 1 外部人员" ] && { PASS=$((PASS+1)); echo "  ✅ 改来源后 type 跟着变、关键标记生效 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修改 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/list?objectType=project&objectID=1")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
keys=[x['key'] for x in d]
# 关键干系人必须都排在非关键之前（同为关键时按加入顺序）
print(''.join(str(k) for k in keys), d[-1]['user'])")
[ "${line}" = "110 tester" ] && { PASS=$((PASS+1)); echo "  ✅ 关键干系人排在前面、非关键在后 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 排序 实际=${line}"; }

echo "--- 5. 删除：按编号 / 按对象+账号 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/stakeholder/delete?id=${S1}")
check "按编号移除" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/stakeholder/delete-by-user?objectType=program&objectID=9001&user=%E7%94%B2%E6%96%B9%E6%9D%8E%E5%9B%9B-${TS}")
check "按「对象 + 账号」移除（禅道 delete(userID) 口径）" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/stakeholder/delete-by-user?objectType=program&objectID=9001&user=nobody-${TS}")
check "移除不存在的干系人 → 拒绝" 1020023000 "$R" "干系人不存在"

echo "--- 6. 我作为干系人参与的对象 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/list-by-user?objectType=program&user=admin")
line=$(echo "$R" | python3 -c "import sys,json;print(' '.join(str(x) for x in json.load(sys.stdin)['data']))")
[ "${line}" = "9001" ] && { PASS=$((PASS+1)); echo "  ✅ admin 作为干系人参与的项目集 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ list-by-user 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/type-list")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(len(d), d[0]['value'], d[1]['label'])")
[ "${line}" = "2 inside 外部" ] && { PASS=$((PASS+1)); echo "  ✅ 类型枚举 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 类型枚举 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/from-list")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(len(d), d[0]['value'], d[2]['label'])")
[ "${line}" = "3 team 外部人员" ] && { PASS=$((PASS+1)); echo "  ✅ 来源枚举 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 来源枚举 实际=${line}"; }

echo "--- 7. 清理并确认演示数据完好 ---"
for id in $(curl -s "${H[@]}" "$BASE/zentao/stakeholder/list?objectType=program&objectID=9001" | python3 -c "
import sys,json
for x in json.load(sys.stdin)['data']:
    if x['user'].startswith('batch') or x['user'].startswith('甲方') or x['user'].startswith('someone'):
        print(x['id'])"); do
  curl -s "${H[@]}" -X DELETE "$BASE/zentao/stakeholder/delete?id=${id}" > /dev/null
done
R=$(curl -s "${H[@]}" "$BASE/zentao/stakeholder/list?objectType=program&objectID=9001")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), ' '.join(x['user'] for x in d))")
[ "${line}" = "3 admin 张三（甲方） tester" ] && { PASS=$((PASS+1)); echo "  ✅ 演示干系人还原 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 清理后 实际=${line}"; }

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

#!/bin/bash
# 测试用例（testcase）模块接口测试
#
# 禅道语义：
#   头部 zt_case + 版本快照 zt_casespec + 步骤 zt_casestep 三张表；
#   **只有「步骤」变了才 version+1，并把状态打回 wait（待评审）**；
#   storyVersion 冻结关联时的需求版本，需求升版后进入「待确认」。
#
# 本脚本重点验证 6 件事：
#   1. 版本规则：改标题不升版本、改步骤才升版本且状态打回待评审
#   2. 步骤层级：parent 是「本次提交数组里的下标」，层级编号 1./1.1/1.1.1 由后端算出
#   3. 步骤校验：步骤组不能有预期结果、父必须是前面的组、最多三级
#   4. 需求联动：关联需求必须同产品；需求升版后 needConfirm，确认后追平
#   5. 评审：只有 wait 能评审，结果只能是 normal/blocked/investigate
#   6. 过滤：模块含子树、stage 是逗号列表用 FIND_IN_SET
BASE=${ZENTAO_API_BASE:-http://localhost:48080/admin-api}
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
    PASS=$((PASS+1)); printf '  ✅ %-50s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-50s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }

echo "===== 测试用例（testcase）模块测试 ====="
TS=$(date +%s)

echo "--- 1. 列表与过滤（演示数据）---"
# 只看演示数据那 5 条（93101-93105）：脚本中途被打断会留下残留，按 id 断言才稳
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/page?product=1&pageSize=100")
line=$(echo "$R" | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
demo = {x['id']: x for x in d['list'] if 93101 <= x['id'] <= 93105}
print(len(demo), '|'.join('%d:%s' % (i, demo[i]['typeName']) for i in sorted(demo)))")
[ "$line" = "5 93101:功能测试|93102:功能测试|93103:功能测试|93104:接口测试|93105:性能测试" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 演示用例 5 条及其类型 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 用例列表 实际=$line"; }

# 模块含子树：93301（登录模块）下有 93303（密码校验）
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/page?product=1&module=93301&pageSize=20")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["total"], "|".join(str(x["module"]) for x in sorted(d["list"], key=lambda y: y["id"])))')
[ "$line" = "3 93301|93303|93301" ] && { PASS=$((PASS+1)); echo "  ✅ 按父模块过滤（含子树）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 模块子树 实际=$line"; }

# stage 是逗号列表：FIND_IN_SET
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/page?product=1&stage=smoke&pageSize=20")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["total"], d["list"][0]["title"][:12])')
[ "$line" = "1 正常登录-用户名密码正确" ] && { PASS=$((PASS+1)); echo "  ✅ stage 逗号列表过滤（FIND_IN_SET）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ stage 过滤 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/page?product=1&status=wait&pageSize=100")
# 不写死条数：跑测中断会留下待评审的用例，写死就会假失败。
# 只验「演示的那条待评审用例在里面，且返回的行状态都真的是 wait」
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
ids={x['id'] for x in d['list']}
ok = 93103 in ids and all(x['status']=='wait' for x in d['list'])
print('ok' if ok else sorted(ids))")
[ "$line" = "ok" ] && { PASS=$((PASS+1)); echo "  ✅ 按状态过滤（演示的 93103 在内，且都是待评审）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 状态过滤 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/page?product=1&needConfirm=true")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["total"], d["list"][0]["storyVersion"], d["list"][0]["latestStoryVersion"], d["list"][0]["needConfirm"])')
[ "$line" = "1 1 2 True" ] && { PASS=$((PASS+1)); echo "  ✅ 待确认列表（storyVersion=1 而需求已是 v2）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 待确认 实际=$line"; }

# needConfirm=true 的结果集，必须正好等于「全量列表里 needConfirm 为 true 的那些行」
line=$(python3 - <<PYEOF
import json, urllib.request
def get(url):
    req = urllib.request.Request(url, headers={"Authorization": "Bearer $TOKEN", "tenant-id": "1"})
    return json.load(urllib.request.urlopen(req))["data"]
all_cases = get("$BASE/zentao/testcase/page?product=1&pageSize=100")["list"]
flagged = get("$BASE/zentao/testcase/page?product=1&needConfirm=true&pageSize=100")["list"]
expected = {x["id"] for x in all_cases if x["needConfirm"]}
actual = {x["id"] for x in flagged}
print("ok" if expected == actual and expected else "mismatch %s vs %s" % (sorted(expected), sorted(actual)))
PYEOF
)
[ "$line" = "ok" ] && { PASS=$((PASS+1)); echo "  ✅ needConfirm 过滤与列表里的标记一致"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ needConfirm 过滤 实际=$line"; }

echo "--- 2. 版本叠加与步骤层级编号 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=93101")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["version"], d["title"], len(d["steps"]))')
[ "$line" = "2 正常登录-用户名密码正确（补充验证码） 6" ] && { PASS=$((PASS+1)); echo "  ✅ 当前版本 v2（标题取快照）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 当前版本 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=93101&version=1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["title"], len(d["steps"]), "|".join(s["name"] for s in d["steps"]))')
[ "$line" = "正常登录-用户名密码正确 3 1|2|3" ] && { PASS=$((PASS+1)); echo "  ✅ 历史版本 v1 标题/步骤独立 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ v1 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/step-list?id=93101&version=2")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print("|".join(s["name"] for s in d), "|".join(str(s["grade"]) for s in d))')
[ "$line" = "1|1.1|1.2|2|2.1|2.2 1|2|2|1|2|2" ] && { PASS=$((PASS+1)); echo "  ✅ 步骤组层级编号 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 层级编号 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/spec-list?id=93101")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print("|".join(str(x["version"]) for x in d), "|".join(str(x["stepCount"]) for x in d), "|".join(str(x["current"]) for x in d))')
[ "$line" = "2|1 6|3 True|False" ] && { PASS=$((PASS+1)); echo "  ✅ 版本历史（含各版本步骤数）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 版本历史 实际=$line"; }

echo "--- 3. 新建校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"\",\"type\":\"feature\"}")
code=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "$code" = "400" ] && { PASS=$((PASS+1)); echo "  ✅ 缺标题被参数校验拦下 code=$code"; } || { FAIL=$((FAIL+1)); echo "  ❌ 缺标题 code=$code"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"类型非法-$TS\",\"type\":\"xxx\"}")
check "类型非法→拒绝" 1020016002 "$R" "用例类型不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"环节非法-$TS\",\"type\":\"feature\",\"stage\":\"xxx\"}")
check "测试环节非法→拒绝" 1020016003 "$R" "测试环节不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"状态非法-$TS\",\"type\":\"feature\",\"status\":\"xxx\"}")
check "状态非法→拒绝" 1020016004 "$R" "用例状态不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"需求不存在-$TS\",\"type\":\"feature\",\"story\":99999999}")
# 复用需求模块的错误码（StoryService 抛的），比再定义一遍更直白
check "关联不存在的需求→拒绝" 1020000000 "$R" "需求不存在"

# 需求 2 属于产品 1，需求 5 也属于产品 1；用产品 2 的需求试试跨产品
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=6")
p6=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["product"])')
if [ "$p6" != "1" ]; then
  R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" \
    -d "{\"product\":1,\"title\":\"跨产品需求-$TS\",\"type\":\"feature\",\"story\":6}")
  check "关联其它产品的需求→拒绝" 1020016007 "$R" "不属于该用例的产品"
else
  echo "  （跳过：需求 6 与用例同产品）"
fi

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"步骤组带预期-$TS\",\"type\":\"feature\",\"steps\":[{\"type\":\"group\",\"desc\":\"组\",\"expect\":\"不该有\"}]}")
check "步骤组带预期结果→拒绝" 1020016008 "$R" "步骤组不能有预期结果"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" \
  -d "{\"product\":1,\"title\":\"父不存在-$TS\",\"type\":\"feature\",\"steps\":[{\"type\":\"step\",\"desc\":\"第一步\",\"parent\":5}]}")
check "父指向不存在的位置→拒绝" 1020016009 "$R" "上级步骤组不在本次提交里"

echo "--- 4. 版本规则（核心）---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/testcase/create" -d "{\"product\":1,\"module\":93301,\"story\":1,\"title\":\"版本规则-$TS\",\"type\":\"feature\",\"stage\":\"smoke\",\"pri\":2,\"steps\":[{\"type\":\"step\",\"desc\":\"打开页面\",\"expect\":\"正常\"},{\"type\":\"step\",\"desc\":\"点击按钮\",\"expect\":\"有反应\"}]}")
check "新建用例（2 步）" 0 "$R"
CASE=$(echo "$R" | d)

R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=$CASE")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["version"], d["statusName"], d["storyVersion"], d["latestStoryVersion"], d["needConfirm"], len(d["steps"]))')
# 需求 1 当前是 v2，所以新建时冻结的就是 2；此时不该有「待确认」
[ "$line" = "1 正常 2 2 False 2" ] && { PASS=$((PASS+1)); echo "  ✅ 新建后 v1 / 非待评审 / storyVersion 冻结=需求当前版本 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建后 实际=$line"; }

# 只改标题与优先级 → 版本不动
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/update" -d "{\"id\":$CASE,\"product\":1,\"module\":93301,\"story\":1,\"title\":\"版本规则-${TS}（只改标题）\",\"type\":\"feature\",\"stage\":\"smoke\",\"pri\":1,\"steps\":[{\"type\":\"step\",\"desc\":\"打开页面\",\"expect\":\"正常\"},{\"type\":\"step\",\"desc\":\"点击按钮\",\"expect\":\"有反应\"}]}")
check "只改标题/优先级" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=$CASE")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["version"], d["pri"], d["title"], d["statusName"])')
[ "$line" = "1 1 版本规则-${TS}（只改标题） 正常" ] && { PASS=$((PASS+1)); echo "  ✅ 标题/优先级改了但版本仍是 v1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 只改标题 实际=$line"; }

# 改步骤 → v2 + 状态打回 wait
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/update" -d "{\"id\":$CASE,\"product\":1,\"module\":93301,\"story\":1,\"title\":\"版本规则-${TS}（只改标题）\",\"type\":\"feature\",\"stage\":\"smoke\",\"pri\":1,\"steps\":[{\"type\":\"group\",\"desc\":\"准备\"},{\"type\":\"step\",\"desc\":\"打开页面\",\"expect\":\"正常\",\"parent\":0},{\"type\":\"step\",\"desc\":\"点击按钮\",\"expect\":\"有反应\",\"parent\":0}]}")
check "改步骤（升版本）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=$CASE")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["version"], d["statusName"], "|".join(s["name"] for s in d["steps"]))')
[ "$line" = "2 待评审 1|1.1|1.2" ] && { PASS=$((PASS+1)); echo "  ✅ 步骤变了 → v2 且打回待评审 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 改步骤 实际=$line"; }

# 步骤没变（原样再提交一次）→ 版本不动
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/update" -d "{\"id\":$CASE,\"product\":1,\"module\":93301,\"story\":1,\"title\":\"版本规则-${TS}（只改标题）\",\"type\":\"feature\",\"stage\":\"smoke\",\"pri\":1,\"steps\":[{\"type\":\"group\",\"desc\":\"准备\"},{\"type\":\"step\",\"desc\":\"打开页面\",\"expect\":\"正常\",\"parent\":0},{\"type\":\"step\",\"desc\":\"点击按钮\",\"expect\":\"有反应\",\"parent\":0}]}")
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=$CASE")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["version"], len(d["steps"]))')
[ "$line" = "2 3" ] && { PASS=$((PASS+1)); echo "  ✅ 步骤内容没变（原样再提交）→ 版本不动"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 原样再提交 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/spec-list?id=$CASE")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 只有 2 条版本快照（没有因为重复提交而多出快照）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 快照数=$n"; }

echo "--- 5. 评审 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/review" -d "{\"id\":$CASE,\"result\":\"pass\",\"comment\":\"乱填\"}")
check "评审结果非法→拒绝" 1020016012 "$R" "评审结果不合法"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/review" -d "{\"id\":$CASE,\"result\":\"normal\",\"comment\":\"步骤完整\"}")
check "评审通过（wait → normal）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=$CASE")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["statusName"], d["reviewedBy"], d["reviewedDate"])')
[ "$line" = "正常 admin $(date +%F)" ] && { PASS=$((PASS+1)); echo "  ✅ 评审记录写入 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 评审记录 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/review" -d "{\"id\":$CASE,\"result\":\"normal\"}")
check "重复评审→拒绝" 1020016013 "$R" "只有「待评审」"

# 评审成 blocked
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/update" -d "{\"id\":$CASE,\"product\":1,\"module\":93301,\"story\":1,\"title\":\"版本规则-${TS}（只改标题）\",\"type\":\"feature\",\"stage\":\"smoke\",\"pri\":1,\"steps\":[{\"type\":\"step\",\"desc\":\"新步骤\",\"expect\":\"x\"}]}")
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/review" -d "{\"id\":$CASE,\"result\":\"blocked\",\"comment\":\"依赖环境\"}")
check "评审为被阻塞" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=$CASE")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["statusName"], d["version"])')
[ "$line" = "被阻塞 3" ] && { PASS=$((PASS+1)); echo "  ✅ 再次改步骤 → v3 且可重新评审为被阻塞 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 被阻塞 实际=$line"; }

echo "--- 6. 需求变更确认 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/page?product=1&needConfirm=true")
n1=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/confirm-story-change?id=93103")
check "确认需求变更" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=93103")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["storyVersion"], d["latestStoryVersion"], d["needConfirm"])')
[ "$line" = "2 2 False" ] && { PASS=$((PASS+1)); echo "  ✅ 确认后 storyVersion 追平 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 确认后 实际=$line"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/page?product=1&needConfirm=true")
n2=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n2" = "$((n1-1))" ] && { PASS=$((PASS+1)); echo "  ✅ 待确认列表从 $n1 降到 $n2"; } || { FAIL=$((FAIL+1)); echo "  ❌ 待确认 $n1 → $n2"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/confirm-story-change?id=93103")
check "已是新版本→拒绝确认" 1020016014 "$R" "已是最新"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/testcase/confirm-story-change?id=93104")
check "无关联需求→拒绝确认" 1020016014 "$R" "已是最新"

# 把 93103 的 storyVersion 还原，保持演示数据可重复验证
# （原来这里写死了远端 ssh，本机栈跑的时候还原不了，第二次跑就会失败）
source "$(dirname "$0")/_mysql.sh"
mysql_exec "UPDATE \`ruoyi-vue-pro\`.zt_case SET storyVersion=1 WHERE id=93103;"

echo "--- 7. 需求关联查询与删除清理 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/list-by-story?story=1")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" -ge "3" ] && { PASS=$((PASS+1)); echo "  ✅ 需求 1 关联的用例 $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ list-by-story=$n"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/testcase/delete?id=$CASE")
check "删除用例" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/get?id=$CASE")
check "删除后再查→不存在" 1020016000 "$R" "测试用例不存在"
R=$(curl -s "${H[@]}" "$BASE/zentao/testcase/spec-list?id=$CASE")
check "删除后版本记录也不可查" 1020016000 "$R" "测试用例不存在"

# 步骤与快照必须被物理清掉，否则会占住 (case, version) 唯一键
# 走共享 helper：本机栈/远端自动选择（原来写死远端 ssh，本机栈跑就被跳过）
source "$(dirname "$0")/_mysql.sh"
n=$(mysql_query "SELECT (SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_casestep WHERE \`case\`=$CASE) + (SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_casespec WHERE \`case\`=$CASE);")
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 步骤与版本快照被物理清理（残留 0 行）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 残留 $n 行"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/testcase/delete?id=99999999")
check "删不存在的用例→拒绝" 1020016000 "$R" "测试用例不存在"

echo
echo "===== 结果：通过 $PASS / 失败 $FAIL ====="
[ "$FAIL" = "0" ] || exit 1

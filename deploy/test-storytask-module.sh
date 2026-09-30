#!/bin/bash
# 需求转任务（任务分解）接口测试
#
# 禅道语义：任务可以挂在需求下（zt_task.story），并记下**建任务时需求的版本**：
#   需求后来正式变更，任务不会跟着变，而是提示「需求已变更」由人确认 ——
#   已经按老需求做完的工作不该被无声改写。
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
    PASS=$((PASS+1)); printf '  ✅ %-46s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-46s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }

echo "===== 需求转任务模块测试 ====="
TS=$(date +%s)

echo "--- 1. 演示数据：任务上的需求版本冻结 ---"
# 演示任务 1 挂需求 1，但 storyVersion 回填成了需求当前版本（迁移脚本做的）→ 不应提示变更
R=$(curl -s "${H[@]}" "$BASE/zentao/task/get?id=1")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['story'], d['storyVersion'], d['latestStoryVersion'], d['storyChanged'], (d['storyTitle'] or '')[:6])")
[ "${line}" = "1 2 2 False 支持需求批量" ] && { PASS=$((PASS+1)); echo "  ✅ 演示任务的需求信息 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示任务 实际=${line}"; }

echo "--- 2. 需求转任务 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":1,\"title\":\"转任务需求-${TS}\",\"type\":\"story\",\"category\":\"feature\",\"pri\":2}")
check "准备：新建需求" 0 "$R"
STORY=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/task/batch-create-from-story?storyId=${STORY}&execution=90001&project=1" -d "[\"登录接口开发-${TS}\",\"登录接口联调-${TS}\"]")
check "把需求分解成 2 个任务" 0 "$R"
N=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "${N}" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 返回 2 个任务编号"; } || { FAIL=$((FAIL+1)); echo "  ❌ 任务数=${N}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/task/list-by-story?story=${STORY}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d), sorted({x['execution'] for x in d}), sorted({x['storyVersion'] for x in d}), sorted({x['pri'] for x in d}))")
[ "${line}" = "2 [90001] [1] [2]" ] && { PASS=$((PASS+1)); echo "  ✅ 任务挂在执行 90001、需求版本冻结为 1、优先级从需求继承 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 分解结果 实际=${line}"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/task/get?id=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"][0]["id"])')")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['storyVersion'], d['latestStoryVersion'], d['storyChanged'], (d['storyTitle'] or '')[:5])")
[ "${line}" = "1 1 False 转任务需求" ] && { PASS=$((PASS+1)); echo "  ✅ 任务详情带需求标题、未变更 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 任务详情 实际=${line}"; }

echo "--- 3. 需求变更后任务提示「已变更」---"
# 「需求已变更」只在需求**激活**时才有意义：草稿需求还没人在做，谈不上「已排期的工作被改了」。
# 草稿需求要变成激活态必须走评审（禅道的 activate 只用于「已关闭 → 激活」），
# 所以这里走一遍评审：提交评审 → 评审人全票通过 → 需求变 active
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/story/start-review" -d "{\"id\":${STORY},\"reviewers\":[\"admin\"]}")
check "提交需求评审" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/story/review" -d "{\"id\":${STORY},\"result\":\"pass\",\"comment\":\"通过\"}")
check "评审通过（draft → active）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=${STORY}")
line=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['status'])")
[ "${line}" = "active" ] && { PASS=$((PASS+1)); echo "  ✅ 评审全票通过后需求变 active"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 需求状态 实际=${line}"; }

# 需求正式变更 → 版本 +1；任务冻结在 v1，此时应提示「需求已变更」
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/story/change" -d "{\"id\":${STORY},\"title\":\"转任务需求-${TS}-改\",\"spec\":\"新描述\",\"verify\":\"\"}")
check "需求正式变更（版本 +1）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/task/list-by-story?story=${STORY}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(sorted({x['storyVersion'] for x in d}), sorted({x['latestStoryVersion'] for x in d}), sorted({x['storyChanged'] for x in d}))")
[ "${line}" = "[1] [2] [True]" ] && { PASS=$((PASS+1)); echo "  ✅ 需求升到 v2，任务仍冻结在 v1 且提示已变更 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 变更提示 实际=${line}"; }

echo "--- 4. 直接建任务时也会冻结需求版本 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/task/create" -d "{\"project\":1,\"execution\":90001,\"name\":\"手工任务-${TS}\",\"type\":\"devel\",\"pri\":3,\"story\":${STORY}}")
check "直接建任务并挂需求" 0 "$R"
TASK=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/task/get?id=${TASK}")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['story'], d['storyVersion'], d['latestStoryVersion'])")
[ "${line}" = "${STORY} 2 2" ] && { PASS=$((PASS+1)); echo "  ✅ 新建时冻结的是需求**当前**版本（v2）= ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新建冻结 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/task/create" -d "{\"project\":1,\"execution\":90001,\"name\":\"无需求任务-${TS}\",\"type\":\"devel\",\"pri\":3}")
check "建不挂需求的任务" 0 "$R"
NOSTORY=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/task/get?id=${NOSTORY}")
line=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['story'], d['storyVersion'], d['storyChanged'])")
[ "${line}" = "0 0 False" ] && { PASS=$((PASS+1)); echo "  ✅ 不挂需求时 storyVersion=0、无变更提示 = ${line}"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 无需求任务 实际=${line}"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/task/create" -d "{\"project\":1,\"execution\":90001,\"name\":\"坏需求-${TS}\",\"type\":\"devel\",\"pri\":3,\"story\":99999999}")
check "挂不存在的需求→拒绝" 1020000000 "$R" "需求不存在"

R=$(curl -s "${H[@]}" -X GET "$BASE/zentao/task/list-by-story?story=99999999")
code=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "${code}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 查不存在的需求下的任务返回空列表而不是报错"; } || { FAIL=$((FAIL+1)); echo "  ❌ code=${code}"; }

echo "--- 5. 清理 ---"
for t in ${TASK} ${NOSTORY}; do curl -s "${H[@]}" -X DELETE "$BASE/zentao/task/delete?id=${t}" > /dev/null; done
for t in $(curl -s "${H[@]}" "$BASE/zentao/task/list-by-story?story=${STORY}" | python3 -c 'import sys,json;print(" ".join(str(x["id"]) for x in json.load(sys.stdin)["data"]))'); do
  curl -s "${H[@]}" -X DELETE "$BASE/zentao/task/delete?id=${t}" > /dev/null
done
curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=${STORY}" > /dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/task/list-by-story?story=${STORY}")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "${n}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 清理完成（需求下 0 个任务）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 残留=${n}"; }

# 演示任务 1 不能被误删
R=$(curl -s "${H[@]}" "$BASE/zentao/task/get?id=1")
[ "$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')" = "1" ] \
  && { PASS=$((PASS+1)); echo "  ✅ 演示任务未被误删"; } || { FAIL=$((FAIL+1)); echo "  ❌ 演示任务缺失"; }

echo
echo "===== 结果：通过 ${PASS} / 失败 ${FAIL} ====="
[ "${FAIL}" = "0" ] || exit 1

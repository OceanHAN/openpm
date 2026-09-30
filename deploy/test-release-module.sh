#!/bin/bash
# 发布（release）模块接口测试
#
# 禅道语义：发布引用构建、维护三份清单（完成的需求/解决的 Bug/遗留的 Bug）；
#   发布名全局唯一；创建时自动生成「影子构建」；选了构建会同步需求与 Bug。
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
    PASS=$((PASS+1)); printf '  ✅ %-46s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-46s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
field() { python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('$1'))"; }

echo "===== 发布模块测试 ====="
TS=$(date +%s)

echo "--- 0. 准备：普通产品 + 平台产品 + 分支 + 项目 + 执行 + 两条构建 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"RelProdN-$TS\",\"code\":\"RN$TS\",\"type\":\"normal\",\"PO\":\"admin\"}")
check "普通产品" 0 "$R"
PRODN=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/product/create" -d "{\"name\":\"RelProdP-$TS\",\"code\":\"RP$TS\",\"type\":\"platform\",\"PO\":\"admin\"}")
check "平台产品" 0 "$R"
PRODP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/branch/create" -d "{\"product\":$PRODP,\"name\":\"发布平台A-$TS\"}")
BRA=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/project/create" -d "{\"name\":\"RelProj-$TS\",\"model\":\"scrum\",\"pri\":3,\"PM\":\"admin\",\"team\":\"admin\",\"multiple\":1}")
check "项目" 0 "$R"
PROJ=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/execution/create" -d "{\"project\":$PROJ,\"name\":\"RelExec-$TS\",\"type\":\"sprint\",\"pri\":3}")
check "执行" 0 "$R"
EXEC=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PRODN,\"title\":\"发布需求1-$TS\",\"pri\":3,\"category\":\"feature\"}")
S1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/story/create" -d "{\"product\":$PRODN,\"title\":\"发布需求2-$TS\",\"pri\":3,\"category\":\"feature\"}")
S2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/bug/create" -d "{\"product\":$PRODN,\"title\":\"发布缺陷1-$TS\",\"steps\":\"步骤\",\"severity\":3,\"pri\":3,\"type\":\"codeerror\"}")
BG1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/bug/create" -d "{\"product\":$PRODN,\"title\":\"发布缺陷2-$TS\",\"steps\":\"步骤\",\"severity\":3,\"pri\":3,\"type\":\"codeerror\"}")
BG2=$(echo "$R" | d)

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"project\":$PROJ,\"execution\":$EXEC,\"name\":\"rel-beta1-$TS\",\"date\":\"2026-01-10\",\"builder\":\"admin\"}")
B1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"project\":$PROJ,\"execution\":$EXEC,\"name\":\"rel-beta2-$TS\",\"date\":\"2026-01-20\",\"builder\":\"admin\"}")
B2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-story" -d "{\"build\":$B1,\"ids\":[$S1]}")
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-story" -d "{\"build\":$B2,\"ids\":[$S2]}")
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/build/link-bug" -d "{\"build\":$B2,\"ids\":[$BG1]}")
check "准备：构建关联需求与Bug" 0 "$R"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/build/create" -d "{\"product\":$PRODN,\"integrated\":true,\"builds\":[$B1,$B2],\"name\":\"rel-集成-$TS\",\"date\":\"2026-01-31\",\"builder\":\"admin\"}")
BI=$(echo "$R" | d)
echo "    普通产品=$PRODN 平台产品=$PRODP 项目=$PROJ 执行=$EXEC 构建=$B1,$B2 集成=$BI 需求=$S1,$S2 Bug=$BG1,$BG2"

echo "--- 1. 创建校验 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODN,\"date\":\"2026-02-28\"}")
check "缺少版本号→参数校验" 400 "$R" "发布版本号不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"name\":\"无产品-$TS\",\"date\":\"2026-02-28\"}")
check "缺少产品→参数校验" 400 "$R" "所属产品不能为空"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODN,\"name\":\"缺日期-$TS\"}")
check "wait 状态缺计划日期→拒绝" 1020011002 "$R" "必填"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODN,\"name\":\"normal缺实际日期-$TS\",\"status\":\"normal\"}")
check "normal 状态缺实际发布日期→拒绝" 1020011003 "$R" "必须填写实际发布日期"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODN,\"name\":\"未来日期-$TS\",\"date\":\"2026-02-28\",\"releasedDate\":\"2099-01-01 10:00:00\"}")
check "实际发布日期在未来→拒绝" 1020011004 "$R" "不能晚于今天"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODP,\"name\":\"平台不选分支-$TS\",\"date\":\"2026-02-28\"}")
check "多平台产品不选分支→拒绝" 1020011009 "$R" "必须选择平台"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODN,\"name\":\"构建不存在-$TS\",\"date\":\"2026-02-28\",\"builds\":[99999999]}")
check "构建不存在→拒绝" 1020011007 "$R" "构建不存在"

echo "--- 2. 正常创建 + 影子构建 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODN,\"name\":\"rel-V1.0-$TS\",\"date\":\"2026-02-28\",\"desc\":\"首个版本\"}")
check "创建发布（wait）" 0 "$R"
REL1=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["status"], d["statusName"], d["branch"], d["releasedDate"], d["shadow"] is not None and d["shadow"]>0, d["createdBy"])')
[ "$line" = "wait 未开始 ,0, None True admin" ] && { PASS=$((PASS+1)); echo "  ✅ 建后默认值 status/branch/releasedDate/有影子构建/createdBy = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 建后默认值 期望='wait 未开始 ,0, None True admin' 实际=$line"; }

SHADOW=$(echo "$R" | field shadow)
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$SHADOW")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['name'], d['product'], d['branch'])")
[ "$line" = "rel-V1.0-$TS $PRODN 0" ] && { PASS=$((PASS+1)); echo "  ✅ 影子构建 name/product/branch = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 影子构建 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODP,\"name\":\"rel-V1.0-$TS\",\"date\":\"2026-02-28\"}")
check "发布名全局唯一→拒绝" 1020011001 "$R" "全局唯一"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODP,\"branches\":[$BRA],\"name\":\"rel-P1-$TS\",\"date\":\"2026-02-28\"}")
check "平台产品带分支建发布" 0 "$R"
RELP=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$RELP")
line=$(echo "$R" | field branchName)
[ "$line" = "发布平台A-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 平台发布 branchName=$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 平台发布 branchName 实际=$line"; }

echo "--- 3. 从构建同步需求/Bug ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODN,\"name\":\"rel-V2.0-$TS\",\"date\":\"2026-03-31\",\"builds\":[$B1,$B2]}")
check "创建发布并关联两个构建" 0 "$R"
REL2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL2")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["stories"], d["storyCount"], d["bugCount"], d["build"], d["project"])')
[ "$line" = "$S1,$S2 2 1 ,$B1,$B2, ,$PROJ," ] && { PASS=$((PASS+1)); echo "  ✅ 从构建同步 stories/bugs 与前缀逗号格式 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 从构建同步 期望='$S1,$S2 2 1 ,$B1,$B2, ,$PROJ,' 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODN,\"name\":\"rel-V3.0-$TS\",\"date\":\"2026-04-30\",\"builds\":[$BI]}")
check "关联集成构建（应含子构建数据）" 0 "$R"
REL3=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL3")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["stories"], d["storyCount"], d["bugCount"])')
[ "$line" = "$S1,$S2 2 1" ] && { PASS=$((PASS+1)); echo "  ✅ 集成构建的父子数据都并进来了 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 集成构建同步 期望='$S1,$S2 2 1' 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/release/create" -d "{\"product\":$PRODN,\"name\":\"rel-V4.0-$TS\",\"date\":\"2026-05-31\",\"builds\":[$B1],\"syncFromBuilds\":false}")
check "关闭同步（syncFromBuilds=false）" 0 "$R"
REL4=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL4")
line=$(echo "$R" | field storyCount)
[ "$line" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 关闭同步后需求数为 $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 关闭同步 期望0 实际=$line"; }

echo "--- 4. 关联需求 / Bug / 遗留Bug ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/link-story" -d "{\"release\":$REL1,\"ids\":[$S1,$S2]}")
check "关联两个需求" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/story-list?release=$REL1")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 发布下需求列表 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 发布下需求列表 期望2 实际=$n"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/link-bug" -d "{\"release\":$REL1,\"type\":\"bug\",\"ids\":[$BG1]}")
check "关联已解决 Bug（type=bug）" 0 "$R"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/link-bug" -d "{\"release\":$REL1,\"type\":\"leftBug\",\"ids\":[$BG2]}")
check "关联遗留 Bug（type=leftBug）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["bugs"], d["bugCount"], d["leftBugs"], d["leftBugCount"])')
[ "$line" = "$BG1 1 $BG2 1" ] && { PASS=$((PASS+1)); echo "  ✅ 解决的 Bug 与遗留的 Bug 分开统计 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ Bug 清单 期望='$BG1 1 $BG2 1' 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/release/bug-list?release=$REL1&type=leftBug")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 遗留 Bug 列表 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 遗留 Bug 列表 期望1 实际=$n"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/release/unlink-story?release=$REL1&story=$S1")
check "移除需求1" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL1")
line=$(echo "$R" | field stories)
[ "$line" = "$S2" ] && { PASS=$((PASS+1)); echo "  ✅ 移除后 stories=$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 移除后 stories 实际=$line"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/release/unlink-bug?release=$REL1&type=leftBug&bug=$BG2")
check "移除遗留 Bug" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL1")
line=$(echo "$R" | field leftBugs)
[ "$line" = "" ] && { PASS=$((PASS+1)); echo "  ✅ 移除后 leftBugs 为空"; } || { FAIL=$((FAIL+1)); echo "  ❌ 移除后 leftBugs 实际=[$line]"; }

echo "--- 5. 发布与状态流转 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/publish?id=$REL1")
check "发布（publish）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["status"], d["statusName"], d["releasedDate"] is not None)')
[ "$line" = "normal 已发布 True" ] && { PASS=$((PASS+1)); echo "  ✅ 发布后状态与实际发布日期 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 发布后 期望='normal 已发布 True' 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/story/get?id=$S2")
line=$(echo "$R" | field stage)
[ "$line" = "released" ] && { PASS=$((PASS+1)); echo "  ✅ 发布后需求阶段推进为 $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 需求阶段 期望released 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/change-status?id=$REL2&status=terminate")
check "改为停止维护" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL2")
line=$(echo "$R" | field statusName)
[ "$line" = "停止维护" ] && { PASS=$((PASS+1)); echo "  ✅ 状态变更成功：$line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 状态变更 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/change-status?id=$REL2&status=xxxx")
check "非法状态→拒绝" 1020011006 "$R" "不允许执行该操作"

echo "--- 6. 影子构建同步 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/update" -d "{\"id\":$REL1,\"product\":$PRODN,\"name\":\"rel-V1.0-改名-$TS\",\"date\":\"2026-03-05\",\"builds\":[$B1],\"status\":\"normal\",\"releasedDate\":\"2026-03-06 10:00:00\"}")
check "改发布名称/日期/构建" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$SHADOW")
line=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d['name'], d['date'], d['builds'])")
[ "$line" = "rel-V1.0-改名-$TS 2026-03-05 ,$B1," ] && { PASS=$((PASS+1)); echo "  ✅ 影子构建同步 name/date/builds = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 影子构建同步 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/update" -d "{\"id\":$REL1,\"product\":$PRODN,\"name\":\"rel-V1.0-改名-$TS\",\"status\":\"wait\",\"date\":\"2026-03-05\"}")
check "改回 wait（清空实际发布日期）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/get?id=$REL1")
line=$(echo "$R" | field releasedDate)
[ "$line" = "None" ] && { PASS=$((PASS+1)); echo "  ✅ wait 状态清空 releasedDate"; } || { FAIL=$((FAIL+1)); echo "  ❌ releasedDate 期望None 实际=$line"; }

echo "--- 7. 删除保护与清理连带 ---"
# REL1 把 REL4 包含进来 → 此时 REL4 是子发布，不能被单独删除
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/update" -d "{\"id\":$REL1,\"product\":$PRODN,\"name\":\"rel-V1.0-改名-$TS\",\"status\":\"wait\",\"date\":\"2026-03-05\",\"releases\":[$REL4]}")
check "REL1 包含 REL4（把 REL4 设为子发布）" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/release/delete?id=$REL4")
check "被包含的子发布不能删除" 1020011005 "$R" "不能删除"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/release/update" -d "{\"id\":$REL1,\"product\":$PRODN,\"name\":\"rel-V1.0-改名-$TS\",\"status\":\"wait\",\"date\":\"2026-03-05\",\"releases\":[]}")
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/release/delete?id=$REL1")
check "解除包含后父发布可以删除" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/build/get?id=$SHADOW")
check "影子构建被连带删除" 1020010000 "$R" "构建不存在"

echo "--- 8. 列表过滤 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/release/page?pageNo=1&pageSize=10&product=$PRODN")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 按产品过滤 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按产品过滤 期望3 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/release/page?pageNo=1&pageSize=10&product=$PRODN&status=wait")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 按状态 wait 过滤 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按状态过滤 期望2 实际=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/release/page?pageNo=1&pageSize=10&product=$PRODP&branch=$BRA")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 按分支过滤 = $n 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 按分支过滤 期望1 实际=$n"; }

echo "--- 9. 清理 ---"
for id in $REL2 $REL3 $REL4 $RELP; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/release/delete?id=$id")
  check "删除发布 $id" 0 "$R"
done
for id in $BI $B1 $B2; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/build/delete?id=$id")
  check "删除构建 $id" 0 "$R"
done
for id in $S1 $S2; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/story/delete?id=$id")
  check "删除需求 $id" 0 "$R"
done
for id in $BG1 $BG2; do
  R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/bug/delete?id=$id")
  check "删除缺陷 $id" 0 "$R"
done
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/branch/delete?id=$BRA")
check "删除分支" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PRODN")
check "删除普通产品" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/product/delete?id=$PRODP")
check "删除平台产品" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/execution/delete?id=$EXEC")
check "删除执行" 0 "$R"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/project/delete?id=$PROJ")
check "删除项目" 0 "$R"

echo ""
echo "===== 结果: 通过 $PASS / 失败 $FAIL ====="

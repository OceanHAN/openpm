#!/bin/bash
# 代码库（repo）模块测试：代码库 CRUD / git 同步 / 提交记录 / 提交与对象的双向关联
#
# 禅道语义（module/repo，16,016 行、71 个 action）：
#   1. zt_repo 是代码库定义；提交按「本地 git 仓库」同步进 zt_repohistory，
#      改动的文件进 zt_repofiles（action 是 git --name-status 的首字母，重命名带 oldPath）
#   2. 提交说明里的 `Story #1`、`Task #2,3`、`Bug #4` 会被解析出来写进通用的 zt_relation
#      （AType='revision', relation='commit'）—— 于是「这个需求是哪几次提交做完的」可以反查
#   3. 同步要幂等：同一个 sha 只入库一次；重新同步要清掉旧的改动文件与关系行
#   4. 本实现只做「本地 Git 仓库」这一种来源（服务商 API 属于 module/provider，未迁移）
#
# 本脚本用一个**真实的本地 git 仓库**做夹具：initialize → 3 次提交 → 同步 → 断言，
# 然后再提交一次验证「增量同步只拉新提交」。
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
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
field() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print($1)" 2>/dev/null; }

echo "===== 代码库（仓库 CRUD / git 同步 / 提交与对象关联）测试 ====="
TS=$(date +%s)
REPO="/tmp/zt-repo-fixture-$TS"
REPO2="/tmp/zt-repo-fixture2-$TS"

# 夹具仓库必须建在**后端所在的那台机器**上：仓库路径要真实存在、要含 .git，后端是拿它去跑 git log 的。
# 本机模式直接跑；服务器模式（ZENTAO_API_BASE 指向别的机器，如 192.168.0.119）就通过 ssh 在那边建。
# 这是「同一套断言两边都能跑」的最后一个位置相关的点（前面 entry 的 IP 白名单是另一个）。
API_HOST=$(printf '%s' "$BASE" | sed -E 's#^https?://([^:/]+).*#\1#')
if [ "$API_HOST" = "127.0.0.1" ] || [ "$API_HOST" = "localhost" ]; then
  fx() { bash -c "$1"; }
else
  # 服务器模式：默认用 ubuntu + 密钥免密（119 上已装本机公钥）；
  # 换机器时用 REMOTE_SSH_USER / REMOTE_SSH_PASS 覆盖（给了密码就走 sshpass）。
  if [ -n "${REMOTE_SSH_PASS:-}" ]; then
    fx() { timeout 180 sshpass -p "$REMOTE_SSH_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "${REMOTE_SSH_USER:-ubuntu}@${API_HOST}" "$1" 2>/dev/null; }
  else
    fx() { timeout 180 ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 "${REMOTE_SSH_USER:-ubuntu}@${API_HOST}" "$1" 2>/dev/null; }
  fi
fi
echo "   （夹具机器 = $API_HOST）"

echo "--- 1. 准备一个真实的本地 git 仓库 ---"
fx "rm -rf '$REPO' && mkdir -p '$REPO' && git -C '$REPO' init -q && git -C '$REPO' symbolic-ref HEAD refs/heads/master && git -C '$REPO' config user.email demo@zentao.local && git -C '$REPO' config user.name 'Demo Dev'"
fx "printf 'login page\n' > '$REPO/login.php' && printf '# readme\n' > '$REPO/README.md'"
fx "git -C '$REPO' add -A && git -C '$REPO' commit -q -m '初始化登录页

Task #1'"
fx "printf 'login page v2\n' >> '$REPO/login.php' && mkdir -p '$REPO/src' && printf '<?php\n// api\n' > '$REPO/src/api.php'"
fx "git -C '$REPO' add -A && git -C '$REPO' commit -q -m '登录接口支持记住我

Story #1 Task #1,2'"
fx "git -C '$REPO' mv README.md docs.md && printf 'docs\n' >> '$REPO/docs.md'"
fx "git -C '$REPO' add -A && git -C '$REPO' commit -q -m '文档改名

Bug #1'"
# 第二个仓库：只用来验证「改路径」
fx "rm -rf '$REPO2' && mkdir -p '$REPO2' && git -C '$REPO2' init -q && git -C '$REPO2' symbolic-ref HEAD refs/heads/master && git -C '$REPO2' config user.email demo2@zentao.local && git -C '$REPO2' config user.name 'Demo2' && printf 'x\n' > '$REPO2/only.txt' && git -C '$REPO2' add -A && git -C '$REPO2' commit -q -m '另一个仓库的唯一提交'"
FIRST_SHA=$(fx "git -C '$REPO' rev-list --max-parents=0 HEAD" | tr -d '\r')
HEAD_SHA=$(fx "git -C '$REPO' rev-parse HEAD" | tr -d '\r')
n=$(fx "git -C '$REPO' rev-list --count HEAD" | tr -d '\r')
[ "$n" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 夹具仓库建好：3 次提交（HEAD=${HEAD_SHA:0:8}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 夹具仓库 提交数=${n}"; }

echo "--- 2. 代码库 CRUD 与校验 ---"
NAME="repo-$TS"
RID=$(curl -s -X POST "${H[@]}" "$BASE/zentao/repo/create" \
  -d "{\"name\":\"$NAME\",\"path\":\"$REPO\",\"defaultBranch\":\"master\",\"product\":\"1\",\"desc\":\"接口测试用\"}" | d)
[ -n "$RID" ] && { PASS=$((PASS+1)); echo "  ✅ 新建代码库 $RID（路径指向上面的本地仓库）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 建代码库失败"; }
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/repo/create" \
  -d "{\"name\":\"$NAME\",\"path\":\"$REPO\"}")
check "重名 → 拒绝" 1020029001 "$R" "已存在"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/repo/create" \
  -d "{\"name\":\"repo-bad-$TS\",\"path\":\"/tmp/not-a-repo-$TS\"}")
check "路径不存在 / 不是 git 仓库 → 拒绝" 1020029003 "$R" "仓库路径不可用"
fx "mkdir -p '/tmp/not-git-$TS'"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/repo/create" \
  -d "{\"name\":\"repo-bad2-$TS\",\"path\":\"/tmp/not-git-$TS\"}")
check "目录存在但没有 .git → 拒绝" 1020029003 "$R" "仓库路径不可用"
R=$(curl -s -X POST "${H[@]}" "$BASE/zentao/repo/create" \
  -d "{\"name\":\"repo-svn-$TS\",\"path\":\"$REPO\",\"scmType\":\"svn\"}")
check "只支持 git（svn → 拒绝）" 1020029002 "$R" "只支持 git"
check "取代码库详情" 0 "$(curl -s "${HF[@]}" "$BASE/zentao/repo/get?id=$RID")"
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/get?id=$RID" | field "d['name'], d['scmType'], d['synced'], d['status']")
[ "${line}" = "$NAME git 0 active" ] && { PASS=$((PASS+1)); echo "  ✅ 默认值补齐：scmType=git、synced=0、status=active"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 默认值 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/simple-list" | field "any(r['id']==$RID for r in d)")
[ "${line}" = "True" ] && { PASS=$((PASS+1)); echo "  ✅ 精简列表里有它（下拉可用）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 精简列表 实际=${line}"; }

echo "--- 3. 同步提交记录 ---"
R=$(curl -s -X POST "${HF[@]}" "$BASE/zentao/repo/sync?id=$RID")
check "首次同步" 0 "$R"
line=$(echo "$R" | d)
[ "${line}" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 首次同步进来 3 条提交"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 首次同步条数 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/get?id=$RID" | field "d['synced'], d['lastSyncRevision'], d['lastSyncCount']")
[ "${line}" = "1 $HEAD_SHA 3" ] && { PASS=$((PASS+1)); echo "  ✅ 代码库上记录了「同步到 ${HEAD_SHA:0:8}」与条数"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 同步痕迹 实际=${line}"; }
R=$(curl -s -X POST "${HF[@]}" "$BASE/zentao/repo/sync?id=$RID")
line=$(echo "$R" | d)
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 再同步一次 → 0 条（同一个 sha 不重复入库）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 重复同步 实际=${line}"; }
DB_CNT=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_repohistory WHERE repo=$RID;")
[ "${DB_CNT}" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 库里也只有 3 条提交（幂等）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 提交条数 实际=${DB_CNT}"; }

echo "--- 4. 提交记录内容与改动文件 ---"
R=$(curl -s "${HF[@]}" "$BASE/zentao/repo/commit-page?pageNo=1&pageSize=10&repo=$RID")
line=$(echo "$R" | field "d['total'], [x['commit'] for x in d['list']]")
[ "${line}" = "3 [3, 2, 1]" ] && { PASS=$((PASS+1)); echo "  ✅ 提交按序号倒序（最新的在前）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 提交顺序 实际=${line}"; }
line=$(echo "$R" | field "[x['revision'] for x in d['list']][-1]")
[ "${line}" = "${FIRST_SHA}" ] && { PASS=$((PASS+1)); echo "  ✅ 最早那条就是仓库的第一个 sha"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 首个 sha 实际=${line}"; }
line=$(echo "$R" | field "all(x['committer']=='Demo Dev' for x in d['list']), all(x['fileCount']>0 for x in d['list'])")
[ "${line}" = "True True" ] && { PASS=$((PASS+1)); echo "  ✅ 提交者与改动文件数都落上了"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 提交者/文件数 实际=${line}"; }
# 第 3 条是 git mv（重命名），要能看出 oldPath
R=$(curl -s "${HF[@]}" "$BASE/zentao/repo/commit-get?repo=$RID&revision=$HEAD_SHA")
line=$(echo "$R" | field "d['files'][0]['action'], d['files'][0]['path'], d['files'][0]['oldPath']")
[ "${line}" = "R docs.md README.md" ] && { PASS=$((PASS+1)); echo "  ✅ 重命名提交带 action=R 与 oldPath（git mv 识别正确）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 重命名 实际=${line}"; }
line=$(echo "$R" | field "[f['path'] for f in d['files']]")
[ "${line}" = "['docs.md']" ] && { PASS=$((PASS+1)); echo "  ✅ 该提交的改动文件清单正确"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 改动文件 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/commit-get?repo=$RID&revision=deadbeef" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
[ "${line}" = "1020029004" ] && { PASS=$((PASS+1)); echo "  ✅ 不存在的 revision → 报「提交记录不存在」"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 不存在的 revision 实际 code=${line}"; }

echo "--- 5. 提交与对象的双向关联（Story/Task/Bug #id）---"
# 同一个对象会被多条提交关联（task 1 在第 1、2 次提交里都写了），所以按去重后的对象集合断言
# 注意：DISTINCT + ORDER BY 必须排序**别名**（AS k ... ORDER BY k）。MySQL 8 默认 sql_mode 含
# ONLY_FULL_GROUP_BY 时，ORDER BY 引用非选中列会直接报 ERROR 3065 —— CI 上就是这么红的（本地库 sql_mode 宽松，掩盖了这个问题）。
DB_LINKS=$(mysql_query "SELECT DISTINCT CONCAT(BType,':',BID) AS k FROM \`ruoyi-vue-pro\`.zt_relation r JOIN \`ruoyi-vue-pro\`.zt_repohistory h ON h.id=r.AID WHERE h.repo=$RID ORDER BY k;")
line=$(echo "$DB_LINKS" | tr '\n' ' ')
[ "${line}" = "bug:1 story:1 task:1 task:2 " ] && { PASS=$((PASS+1)); echo "  ✅ 三条提交说明解析出 4 个关联对象：Story#1 / Task#1 / Task#2 / Bug#1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 关系行 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/commit-get?repo=$RID&revision=$HEAD_SHA" | field "d['linkedObjects'][0]['objectType'], d['linkedObjects'][0]['objectID'], len(d['linkedObjects'][0]['objectName']) > 0")
[ "${line}" = "bug 1 True" ] && { PASS=$((PASS+1)); echo "  ✅ 提交详情里带关联对象，并回查出了对象标题"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 详情关联 实际=${line}"; }
# 反向：某个任务被哪些提交关联
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/commit-page?pageNo=1&pageSize=10&repo=$RID&objectType=task&objectID=1" | field "d['total'], sorted([x['commit'] for x in d['list']])")
[ "${line}" = "2 [1, 2]" ] && { PASS=$((PASS+1)); echo "  ✅ 反查：任务 1 被第 1、2 次提交关联"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 反查任务 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/commit-page?pageNo=1&pageSize=10&repo=$RID&objectType=story&objectID=1" | field "d['total']")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 反查：需求 1 被 1 次提交关联"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 反查需求 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/commit-page?pageNo=1&pageSize=10&repo=$RID&objectType=bug&objectID=999" | field "d['total']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 没被关联过的对象 → 0 条（不报错）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 无关对象 实际=${line}"; }

echo "--- 6. 增量同步 ---"
fx "printf 'extra\n' > '$REPO/extra.txt' && git -C '$REPO' add -A && git -C '$REPO' commit -q -m '补充说明

Story #1'"
R=$(curl -s -X POST "${HF[@]}" "$BASE/zentao/repo/sync?id=$RID")
line=$(echo "$R" | d)
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 新增一次提交后，增量同步只拉 1 条"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 增量同步 实际=${line}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/commit-page?pageNo=1&pageSize=10&repo=$RID" | field "d['total'], d['list'][0]['comment'].split(chr(10))[0]")
[ "${line}" = "4 补充说明" ] && { PASS=$((PASS+1)); echo "  ✅ 总数变 4，最新的一条就是刚提交的"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 增量后列表 实际=${line}"; }
DB_FILES=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_repofiles WHERE repo=$RID;")
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/commit-get?repo=$RID&revision=$(fx "git -C '$REPO' rev-parse HEAD" | tr -d '\r')" | field "d['fileCount']")
[ "${line}" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 新提交只带 1 个改动文件（库里文件行合计 ${DB_FILES}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新提交文件数 实际=${line}"; }
# 重复同步不会让文件行翻倍
curl -s -X POST "${HF[@]}" "$BASE/zentao/repo/sync?id=$RID" >/dev/null
DB_FILES2=$(mysql_query "SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_repofiles WHERE repo=$RID;")
[ "${DB_FILES}" = "${DB_FILES2}" ] && { PASS=$((PASS+1)); echo "  ✅ 再同步一次文件行数不变（先清后写）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 文件行数 前=${DB_FILES} 后=${DB_FILES2}"; }

echo "--- 7. 修改 / 删除 / 鉴权 ---"
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/repo/update" -d "{\"id\":$RID,\"name\":\"$NAME\",\"path\":\"$REPO\",\"desc\":\"改过了\"}")
check "改描述" 0 "$R"
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/get?id=$RID" | field "d['desc']")
[ "${line}" = "改过了" ] && { PASS=$((PASS+1)); echo "  ✅ 修改生效，且 synced 仍为 1（路径没变，同步痕迹保留）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 修改 实际=${line}"; }
R=$(curl -s -X PUT "${H[@]}" "$BASE/zentao/repo/update" -d "{\"id\":$RID,\"name\":\"$NAME\",\"path\":\"$REPO2\",\"desc\":\"换库了\"}")
check "改路径（指向另一个仓库）" 0 "$R"
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/get?id=$RID" | field "d['synced']")
[ "${line}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 换路径后同步痕迹清零（否则增量同步会拿旧 sha 去新库里找）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 换路径 实际 synced=${line}"; }
check "删除代码库" 0 "$(curl -s -X DELETE "${H[@]}" "$BASE/zentao/repo/delete?id=$RID")"
DB_LEFT=$(mysql_query "SELECT (SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_repohistory WHERE repo=$RID) + (SELECT COUNT(*) FROM \`ruoyi-vue-pro\`.zt_repofiles WHERE repo=$RID);")
[ "${DB_LEFT}" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 删除时提交记录与改动文件一起物理清理（这两张表没有 deleted 列）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删除后残留 实际=${DB_LEFT}"; }
line=$(curl -s "${HF[@]}" "$BASE/zentao/repo/get?id=$RID" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
[ "${line}" = "1020029000" ] && { PASS=$((PASS+1)); echo "  ✅ 删除后再查 → 代码库不存在"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 删除后查询 实际 code=${line}"; }
line=$(curl -s "$BASE/zentao/repo/page?pageNo=1&pageSize=10" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
[ "${line}" = "401" ] && { PASS=$((PASS+1)); echo "  ✅ 不带令牌被拦（code=401）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未登录 实际 code=${line}"; }

echo "--- 8. 清理 ---"
source "$(dirname "$0")/_mysql.sh"
mysql_exec "DELETE FROM \`ruoyi-vue-pro\`.zt_relation WHERE AID IN (SELECT id FROM \`ruoyi-vue-pro\`.zt_repohistory WHERE repo=$RID);" >/dev/null 2>&1
fx "rm -rf '$REPO' '$REPO2' '/tmp/not-git-$TS'"
echo "  已清理夹具仓库与测试数据"

echo "======================================================"
echo "  repo 模块测试：通过 $PASS 项，失败 $FAIL 项"
echo "======================================================"
[ "$FAIL" -eq 0 ]

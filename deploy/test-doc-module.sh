#!/bin/bash
# 文档（doc）模块接口测试
#
# 禅道语义：zt_doclib（库）→ zt_doc（文档/章节）→ zt_doccontent（版本内容）三层。
# 本脚本重点验证 5 件事：
#   1. 文档库：跟随对象的主库（main=1，不能删）、自定义库必须挂空间、主库删除保护
#   2. 章节树：type=chapter 是树节点（没有正文），path 前缀决定子树
#   3. 版本链：正文变了才 version+1；只改基础信息版本不动；v1 内容仍可回读
#   4. 草稿位：draft 反复保存改的是 version=0 那一行，发布时升成 v1
#   5. 校验分支：链接格式、正文必填、章节不能挂在文档下、不能移到自己子孙下
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
    PASS=$((PASS+1)); printf '  ✅ %-48s code=%s %s\n' "$name" "$code" "$msg"
  else
    FAIL=$((FAIL+1)); printf '  ❌ %-48s 期望code=%s 实际code=%s msg=%s\n' "$name" "$want" "$code" "$msg"
  fi
}
d() { python3 -c "import sys,json;d=json.load(sys.stdin).get('data');print(d if d is not None else '')"; }
# 取某个字段（支持点号路径）
f() { python3 -c "
import sys,json
d=json.load(sys.stdin).get('data')
for k in '$1'.split('.'):
    d = d[int(k)] if k.isdigit() else d[k]
print(d)"; }

echo "===== 文档（doc）模块测试 ====="
TS=$(date +%s)

echo "--- 1. 文档库 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/lib/list?type=product&objectID=1")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(len(d), d[0]["name"], d[0]["typeName"], d[0]["main"], d[0]["docCount"])')
[ "$line" = "1 产品文档库 产品文档库 True 3" ] && { PASS=$((PASS+1)); echo "  ✅ 产品 1 的库列表 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 库列表 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/doc/lib/type-list")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "4" ] && { PASS=$((PASS+1)); echo "  ✅ 库类型 4 种（product/project/execution/custom）"; } || { FAIL=$((FAIL+1)); echo "  ❌ 库类型=$n"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/lib/create" -d "{\"type\":\"xxx\",\"name\":\"L-$TS\"}")
check "非法库类型→拒绝" 1020015002 "$R" "文档库类型不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/lib/create" -d "{\"type\":\"product\",\"name\":\"L-$TS\"}")
check "product 库不给产品→拒绝" 1020015003 "$R" "必须指定所属对象"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/lib/create" -d "{\"type\":\"custom\",\"name\":\"L-$TS\"}")
check "custom 库不给空间→拒绝" 1020015004 "$R" "必须挂在某个团队空间"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/lib/create" -d "{\"type\":\"product\",\"product\":1,\"name\":\"产品文档库\"}")
check "同空间重名→拒绝" 1020015001 "$R" "已存在同名文档库"

# 执行库也是「跟随对象走」的主库（main=1）。这里直接读演示数据里的 92154，
# 不再新建 —— 主库删不掉，新建一个就永久留一条垃圾数据。
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/lib/get?id=92154")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["type"],d["typeName"],d["main"],d["execution"])')
[ "$line" = "execution 执行文档库 True 90001" ] && { PASS=$((PASS+1)); echo "  ✅ 执行库是主库（main=True）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 执行库 实际=$line"; }
# 后续测试要用一个「可删除」的库：自定义库必须挂在团队空间 92155 下，且 main=0
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/lib/create" -d "{\"type\":\"custom\",\"parent\":92155,\"name\":\"测试规范库-$TS\"}")
check "新建自定义库（可删除，main=0）" 0 "$R"
LIB=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/lib/get?id=$LIB")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["type"],d["main"],d["parent"])')
[ "$line" = "custom False 92155" ] && { PASS=$((PASS+1)); echo "  ✅ 自定义库 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 自定义库 实际=$line"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/lib/delete?id=92151")
check "删除产品主库→拒绝" 1020015005 "$R" "不允许删除"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/lib/delete?id=92154")
check "删除执行主库→拒绝" 1020015005 "$R" "不允许删除"

echo "--- 2. 章节树 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/chapter-tree?lib=92151")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(len(d), "|".join(x["title"] for x in d), "|".join(str(x["docCount"]) for x in d))')
[ "$line" = "2 需求文档|设计文档 1|1" ] && { PASS=$((PASS+1)); echo "  ✅ 章节树 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 章节树 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/doc/page?lib=92151&excludeChapter=true&pageSize=20")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print(d["total"], all(not x["chapter"] for x in d["list"]), any(x["title"]=="需求文档" for x in d["list"]))')
[ "$line" = "3 True False" ] && { PASS=$((PASS+1)); echo "  ✅ 文档列表排除章节（章节不在列表里）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 文档列表 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/doc/page?lib=92151&parent=92101&excludeChapter=true&pageSize=20")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["total"], d["list"][0]["title"])')
[ "$line" = "1 产品需求说明书" ] && { PASS=$((PASS+1)); echo "  ✅ 按章节过滤文档 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 按章节过滤 实际=$line"; }

echo "--- 3. 新建章节与文档 ---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"title\":\"测试章节-$TS\",\"type\":\"chapter\"}")
check "新建章节（无正文）" 0 "$R"
CH=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$CH")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["chapter"],d["path"],d["grade"],d["typeName"])')
[ "$line" = "True ,$CH, 1 章节" ] && { PASS=$((PASS+1)); echo "  ✅ 章节 path/grade = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 章节 实际=$line"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"parent\":$CH,\"title\":\"子章节-$TS\",\"type\":\"chapter\"}")
check "新建子章节" 0 "$R"
CH2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$CH2")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["path"],d["grade"])')
[ "$line" = ",$CH,$CH2, 2" ] && { PASS=$((PASS+1)); echo "  ✅ 子章节 path/grade = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 子章节 实际=$line"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/delete?id=$CH")
check "删除有子节点的章节→拒绝" 1020015020 "$R" "还有 1 个子节点"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"parent\":$CH,\"title\":\"子章节-$TS\",\"type\":\"chapter\"}")
check "同章节下重名→拒绝" 1020015012 "$R" "已存在同名文档"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"parent\":92103,\"title\":\"挂文档下-$TS\",\"type\":\"html\",\"content\":\"x\"}")
check "上级不是章节→拒绝" 1020015017 "$R" "不是章节"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"parent\":92101,\"title\":\"跨库章节-$TS\",\"type\":\"chapter\"}")
check "上级章节不在同库→拒绝" 1020015019 "$R" "不在同一个文档库"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"title\":\"无正文-$TS\",\"type\":\"html\"}")
check "html 不给正文→拒绝" 1020015014 "$R" "必须填写正文"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"title\":\"坏链接-$TS\",\"type\":\"url\",\"content\":\"不是链接\"}")
check "url 类型链接不合法→拒绝" 1020015015 "$R" "链接地址不合法"

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"title\":\"附件型-$TS\",\"type\":\"attachment\"}")
check "attachment 不给附件→拒绝" 1020015025 "$R" "必须上传附件"

echo "--- 4. 版本链（正文变了才升版本）---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"parent\":$CH2,\"title\":\"版本链文档-$TS\",\"type\":\"markdown\",\"content\":\"# v1\",\"rawContent\":\"# v1\"}")
check "新建 markdown 文档" 0 "$R"
DOC=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$DOC")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["version"],d["path"],d["grade"],d["content"])')
[ "$line" = "1 ,$CH,$CH2,$DOC, 3 # v1" ] && { PASS=$((PASS+1)); echo "  ✅ 新文档 v1 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 新文档 实际=$line"; }

# 只改关键词（基础信息）→ 版本不动
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/update" \
  -d "{\"id\":$DOC,\"lib\":$LIB,\"parent\":$CH2,\"title\":\"版本链文档-$TS\",\"type\":\"markdown\",\"content\":\"# v1\",\"rawContent\":\"# v1\",\"keywords\":\"改了关键词\"}")
check "只改基础信息" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$DOC")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["version"],d["keywords"])')
[ "$line" = "1 改了关键词" ] && { PASS=$((PASS+1)); echo "  ✅ 基础信息改了但版本仍是 v1"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 版本/关键词 实际=$line"; }

# 改正文 → 版本 +1
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/update" \
  -d "{\"id\":$DOC,\"lib\":$LIB,\"parent\":$CH2,\"title\":\"版本链文档-$TS\",\"type\":\"markdown\",\"content\":\"# v2\",\"rawContent\":\"# v2\"}")
check "改正文（升版本）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$DOC")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["version"],d["content"])')
[ "$line" = "2 # v2" ] && { PASS=$((PASS+1)); echo "  ✅ 正文变了 → v2"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 升版本 实际=$line"; }

# v1 仍可回读（版本链的意义）
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$DOC&version=1")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["content"])')
[ "$line" = "# v1" ] && { PASS=$((PASS+1)); echo "  ✅ 回读历史版本 v1 内容不变"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 回读 v1 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/doc/content-list?id=$DOC")
line=$(echo "$R" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"]
print("|".join(str(x["version"]) for x in d), "|".join(str(x["current"]) for x in d))')
[ "$line" = "2|1 True|False" ] && { PASS=$((PASS+1)); echo "  ✅ 版本历史 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 版本历史 实际=$line"; }

# 再改一次正文 → v3，v2 快照保留
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/update" \
  -d "{\"id\":$DOC,\"lib\":$LIB,\"parent\":$CH2,\"title\":\"版本链文档-$TS\",\"type\":\"markdown\",\"content\":\"# v3\",\"rawContent\":\"# v3\"}")
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/content-list?id=$DOC")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(len(d),"|".join(str(x["version"]) for x in d))')
[ "$line" = "3 3|2|1" ] && { PASS=$((PASS+1)); echo "  ✅ 三次正文编辑 → 三个快照 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 快照数 实际=$line"; }

echo "--- 5. 草稿位（version=0）---"
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"title\":\"草稿文档-$TS\",\"type\":\"markdown\",\"status\":\"draft\",\"content\":\"草稿内容1\"}")
check "新建草稿" 0 "$R"
DRAFT=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$DRAFT")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["statusName"],d["version"],d["content"])')
[ "$line" = "草稿 0 草稿内容1" ] && { PASS=$((PASS+1)); echo "  ✅ 草稿状态 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 草稿 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/update" \
  -d "{\"id\":$DRAFT,\"lib\":$LIB,\"title\":\"草稿文档-$TS\",\"type\":\"markdown\",\"status\":\"draft\",\"content\":\"草稿内容2\"}")
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/content-list?id=$DRAFT")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(len(d),d[0]["version"],d[0]["content"],d[0]["draft"])')
[ "$line" = "1 0 草稿内容2 True" ] && { PASS=$((PASS+1)); echo "  ✅ 反复保存草稿只改 version=0 那一行 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 草稿反复保存 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/publish?id=$DRAFT")
check "发布草稿" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$DRAFT")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["statusName"],d["version"],d["content"])')
[ "$line" = "已发布 1 草稿内容2" ] && { PASS=$((PASS+1)); echo "  ✅ 发布后 草稿行升成 v1 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 发布后 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/publish?id=$DRAFT")
check "重复发布→拒绝" 1020015023 "$R" "不是草稿状态"

# 发布后编辑正文 → v2
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/update" \
  -d "{\"id\":$DRAFT,\"lib\":$LIB,\"title\":\"草稿文档-$TS\",\"type\":\"markdown\",\"content\":\"正式v2\"}")
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/content-list?id=$DRAFT")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(len(d),"|".join(str(x["version"]) for x in d))')
[ "$line" = "2 2|1" ] && { PASS=$((PASS+1)); echo "  ✅ 发布后再编辑 → v2（草稿位没留下脏行）= $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 发布后编辑 实际=$line"; }

echo "--- 6. 浏览计数 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/view?id=$DOC")
n1=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["views"])')
curl -s "${H[@]}" "$BASE/zentao/doc/view?id=$DOC" > /dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/view?id=$DOC")
n3=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["views"])')
[ "$n3" = "$((n1+2))" ] && { PASS=$((PASS+1)); echo "  ✅ 浏览三次 views 递增（$n1 → ${n3}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ views 期望 $((n1+2)) 实际=$n3"; }

# 注意：$DRAFT 在第 5 节已经被发布了，所以这里必须新造一个草稿才能验证「草稿不计数」
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/doc/create" \
  -d "{\"lib\":$LIB,\"title\":\"计数用草稿-$TS\",\"type\":\"markdown\",\"status\":\"draft\",\"content\":\"x\"}")
DRAFT2=$(echo "$R" | d)
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/view?id=$DRAFT2")
v=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["views"])')
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/view?id=$DRAFT2")
v2=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["views"])')
[ "$v" = "$v2" ] && [ "$v" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 草稿浏览不计入 views（$v = ${v2}）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 草稿 views 变化了：$v → $v2"; }
curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/delete?id=$DRAFT2" > /dev/null

echo "--- 7. 移动（子树 path/grade 一起重算）---"
# 移到根：自己变一级，挂在它下面的文档跟着上浮
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/move" \
  -d "{\"id\":$CH2,\"lib\":$LIB,\"parent\":0}")
check "把子章节移到库根" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$CH2")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["path"],d["grade"],d["parent"])')
[ "$line" = ",$CH2, 1 0" ] && { PASS=$((PASS+1)); echo "  ✅ 移动后自己的 path/grade = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 移动后 实际=$line"; }

# 子树（挂在 CH2 下的文档）path/grade 也要跟着变
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$DOC")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["path"],d["grade"])')
[ "$line" = ",$CH2,$DOC, 2" ] && { PASS=$((PASS+1)); echo "  ✅ 子树的 path/grade 一起重算 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 子树 path 实际=$line"; }

# 再移回去，验证反向也正确
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/move" \
  -d "{\"id\":$CH2,\"lib\":$LIB,\"parent\":$CH}")
check "再移回原章节下" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=$DOC")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["path"],d["grade"])')
[ "$line" = ",$CH,$CH2,$DOC, 3" ] && { PASS=$((PASS+1)); echo "  ✅ 反向移动后子树回位 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 反向移动 实际=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/move" \
  -d "{\"id\":$CH,\"lib\":$LIB,\"parent\":$CH2}")
check "移到自己的子孙下→拒绝" 1020015018 "$R" "自己的子节点"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/move" \
  -d "{\"id\":$DOC,\"lib\":$LIB,\"parent\":92103}")
check "移动到别的库的非章节下→拒绝" 1020015017 "$R" "不是章节"

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/doc/move" \
  -d "{\"id\":$DOC,\"lib\":$LIB,\"parent\":92102}")
check "跨库移动到其它库的章节→拒绝（父章节必须同库）" 1020015019 "$R" "不在同一个文档库"

echo "--- 8. 库删除保护与清理 ---"
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/lib/delete?id=$LIB")
check "库内还有文档→拒绝删除（本实现的有意改进）" 1020015006 "$R" "还有"

# 这里必须用一个**确定不存在**的 id。
# 原来图省事写成「把库 id 交给文档接口」，结果 zt_doclib 与 zt_doc 的自增各自独立，
# 演示数据段又靠得太近 —— 某一次跑测时库 id 正好等于演示文档 id（91013），
# 这条「应该报不存在」的断言就把演示文档真的删掉了，后续断言全部崩。
# 教训：测试里别用「另一个表的 id」当反例，那不是一个安全的不存在值。
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/delete?id=99999999")
check "删不存在的文档→不存在" 1020015010 "$R" "文档不存在"

# 清理：先删文档，再删章节，最后删库
for id in $DOC $DRAFT; do curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/delete?id=$id" > /dev/null; done
curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/delete?id=$CH2" > /dev/null
curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/delete?id=$CH" > /dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/page?lib=$LIB&pageSize=50")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["total"])')
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 清理后库内 0 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 库内还剩 $n 条"; }

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/doc/lib/delete?id=$LIB")
check "清空后删库成功" 0 "$R"

R=$(curl -s "${H[@]}" "$BASE/zentao/doc/lib/get?id=$LIB")
check "删库后再查→不存在" 1020015000 "$R" "文档库不存在"

# 被删文档的版本快照应该一起物理清理（否则会占住 (doc,version) 唯一键）
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/content-list?id=$DOC")
check "删文档后版本记录也不可查" 1020015010 "$R" "文档不存在"

echo "--- 9. 演示数据完整性（只读断言，防止误删演示数据）---"
R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=92103")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["title"],d["typeName"],d["version"],d["parentTitle"])')
[ "$line" = "产品需求说明书 富文本 2 需求文档" ] && { PASS=$((PASS+1)); echo "  ✅ 演示文档 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示文档 实际=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/doc/get?id=92105")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["title"],d["statusName"],d["version"])')
[ "$line" = "Git 提交规范 草稿 0" ] && { PASS=$((PASS+1)); echo "  ✅ 演示草稿 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 演示草稿 实际=$line"; }

echo
echo "===== 结果：通过 $PASS / 失败 $FAIL ====="
[ "$FAIL" = "0" ] || exit 1

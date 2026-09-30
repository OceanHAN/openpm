#!/bin/bash
# 附件（file）模块接口测试
#
# 禅道语义：zt_file 靠 (objectType, objectID) 挂在业务对象上，是所有模块的公共能力。
# 本脚本重点验证 4 件事：
#   1. 上传 → 元数据落 zt_file、字节落到 yudao 文件服务（url 可直接下载）
#   2. 下载累加 downloads（禅道 file->download 的行为）
#   3. gid 两阶段绑定（先上传、后 bind-by-gid）
#   4. 大小限制、空文件、不存在的附件等错误分支
BASE=${ZENTAO_API_BASE:-http://localhost:48080/admin-api}
PASS=0; FAIL=0

TOKEN=$(curl -s -X POST "$BASE/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
H=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' -H 'Content-Type: application/json')
# 上传接口必须走 multipart：-H 'Content-Type: application/json' 会盖掉 -F 自动生成的
# multipart/form-data 头，Spring 会报 "Current request is not a multipart request"。
HU=(-H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1')

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

echo "===== 附件（file）模块测试 ====="
# 清理上次跑测留下的附件：本脚本用的是演示数据里的固定对象（story#1 等），
# 不先清干净就会越跑越多，断言「列表 = N 条」必然失败。
cleanup() {
  for spec in "story:1" "story:2" "story:3" "bug:7"; do
    curl -s "${H[@]}" -X DELETE "$BASE/zentao/file/delete-by-object?objectType=${spec%%:*}&objectID=${spec##*:}" > /dev/null
  done
}
cleanup
TS=$(date +%s)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
printf 'hello-zentao-%s\n' "$TS" > "$WORK/spec-$TS.txt"
# 造一个 64MB 文件用于测大小上限（只写稀疏文件，不实际占盘）
mkdir -p "$WORK"
python3 - "$WORK/big.bin" <<'PY'
import sys
with open(sys.argv[1], 'wb') as f:
    f.seek(51 * 1024 * 1024)   # 51MB，超过 50MB 上限
    f.write(b'x')
PY
# 一个 1x1 PNG，用于验证 image 标记
python3 - "$WORK/pic.png" <<'PY'
import base64, sys
png = base64.b64decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==')
open(sys.argv[1], 'wb').write(png)
PY

echo "--- 1. 绑定到对象上传（story #1）---"
R=$(curl -s "${HU[@]}" -F "file=@$WORK/spec-$TS.txt" "$BASE/zentao/file/upload?objectType=story&objectID=1")
check "上传附件到 story#1" 0 "$R"
FID=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
FSIZE=$(wc -c < "$WORK/spec-$TS.txt" | tr -d ' ')
echo "$R" > "$WORK/upload.json"
WANT=$(python3 -c "
import sys
size = int(sys.argv[1]); ts = sys.argv[2]
if size < 1024:
    text = str(size) + ' B'
elif size < 1024 * 1024:
    text = f'{size / 1024:.1f} KB'
else:
    text = f'{size / 1024 / 1024:.1f} MB'
print(f'spec-{ts}.txt txt {size} {text} story 1 False')
" "$FSIZE" "$TS")
GOT=$(python3 -c "
import json, sys
d = json.load(open(sys.argv[1]))['data']
print(d['title'], d['extension'], d['size'], d['sizeText'], d['objectType'], d['objectID'], d['image'])
" "$WORK/upload.json")
[ "$GOT" = "$WANT" ] && { PASS=$((PASS+1)); echo "  ✅ 附件元数据 = $GOT"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 附件元数据 期望=[$WANT] 实际=[$GOT]"; }

URL=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["url"])')
BODY=$(curl -s "$URL")
[ "$BODY" = "hello-zentao-$TS" ] && { PASS=$((PASS+1)); echo "  ✅ 存储地址可匿名访问，内容一致"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 存储地址内容不符：$URL → $BODY"; }

echo "--- 2. 列表 / 计数 / 详情 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/file/list?objectType=story&objectID=1")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 列表返回 1 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ 列表条数=$n"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/file/count?objectType=story&objectID=1")
[ "$(echo "$R" | d)" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 计数 = 1"; } || { FAIL=$((FAIL+1)); echo "  ❌ 计数=$(echo "$R" | d)"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/file/get?id=$FID")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["id"], d["downloads"])')
[ "$line" = "$FID 0" ] && { PASS=$((PASS+1)); echo "  ✅ 详情 downloads 初始为 0"; } || { FAIL=$((FAIL+1)); echo "  ❌ 详情=$line"; }

echo "--- 3. 下载累加计数 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/file/download?id=$FID")
GOT_URL=$(echo "$R" | d)
[ -n "$URL" ] && [ "$GOT_URL" = "$URL" ] && { PASS=$((PASS+1)); echo "  ✅ download 返回存储地址"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ download 期望=[$URL] 实际=[$GOT_URL]"; }
curl -s "${H[@]}" "$BASE/zentao/file/download?id=$FID" > /dev/null
curl -s "${H[@]}" "$BASE/zentao/file/download?id=$FID" > /dev/null
R=$(curl -s "${H[@]}" "$BASE/zentao/file/get?id=$FID")
n=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["downloads"])')
[ "$n" = "3" ] && { PASS=$((PASS+1)); echo "  ✅ 下载 3 次后计数 = 3"; } || { FAIL=$((FAIL+1)); echo "  ❌ 计数=$n 期望3"; }

echo "--- 4. 重命名 ---"
R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/file/rename" -d "{\"id\":$FID,\"title\":\"需求说明书-$TS.md\"}")
check "重命名（扩展名跟着变）" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/file/get?id=$FID")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["title"], d["extension"])')
[ "$line" = "需求说明书-$TS.md md" ] && { PASS=$((PASS+1)); echo "  ✅ 重命名后 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 重命名后=$line"; }

R=$(curl -s "${H[@]}" -X PUT "$BASE/zentao/file/rename" -d "{\"id\":$FID,\"title\":\"   \"}")
code=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "$code" != "0" ] && { PASS=$((PASS+1)); echo "  ✅ 空文件名被参数校验拦下 code=$code"; } || { FAIL=$((FAIL+1)); echo "  ❌ 空文件名竟然通过"; }

echo "--- 5. gid 两阶段绑定 ---"
GID="gid-$TS"
R=$(curl -s "${HU[@]}" -F "file=@$WORK/pic.png" "$BASE/zentao/file/upload?gid=$GID")
check "带 gid 上传（暂不绑对象）" 0 "$R"
GID1=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["objectType"] or "-", d["objectID"], d["gid"], d["image"])')
[ "$line" = "- 0 $GID True" ] && { PASS=$((PASS+1)); echo "  ✅ 未绑定状态 = $line"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ 未绑定状态=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/file/list-by-gid?gid=$GID")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ gid 列表 1 条"; } || { FAIL=$((FAIL+1)); echo "  ❌ gid 列表=$n"; }

# 只有 1x1 图，顺便验证 image 标记
R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/file/bind-by-gid" \
  -d "{\"gid\":\"$GID\",\"objectType\":\"bug\",\"objectID\":7}")
[ "$(echo "$R" | d)" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ 绑定返回 1"; } || { FAIL=$((FAIL+1)); echo "  ❌ 绑定返回=$(echo "$R" | d)"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/file/get?id=$GID1")
line=$(echo "$R" | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["objectType"], d["objectID"], d["gid"] or "-")')
[ "$line" = "bug 7 -" ] && { PASS=$((PASS+1)); echo "  ✅ 绑定后归属 = $line"; } || { FAIL=$((FAIL+1)); echo "  ❌ 绑定后归属=$line"; }

R=$(curl -s "${H[@]}" "$BASE/zentao/file/list-by-gid?gid=$GID")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 绑定后 gid 列表清空"; } || { FAIL=$((FAIL+1)); echo "  ❌ 绑定后 gid 列表=$n"; }

R=$(curl -s "${H[@]}" -X POST "$BASE/zentao/file/bind-by-gid" \
  -d "{\"gid\":\"not-exist-$TS\",\"objectType\":\"story\",\"objectID\":1}")
check "绑定不存在的 gid→拒绝" 1020014004 "$R" "临时分组"

echo "--- 6. 错误分支 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/file/get?id=99999999")
check "查询不存在的附件" 1020014000 "$R" "附件不存在"

R=$(curl -s "${H[@]}" "$BASE/zentao/file/download?id=99999999")
check "下载不存在的附件" 1020014000 "$R" "附件不存在"

R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/file/delete?id=99999999")
check "删除不存在的附件" 1020014000 "$R" "附件不存在"

R=$(curl -s "${HU[@]}" -F "file=@$WORK/big.bin" "$BASE/zentao/file/upload?objectType=story&objectID=1")
check "超过 50MB→拒绝" 1020014002 "$R" "上限"

: > "$WORK/empty.txt"
R=$(curl -s "${HU[@]}" -F "file=@$WORK/empty.txt" "$BASE/zentao/file/upload?objectType=story&objectID=1")
check "空文件→拒绝" 1020014001 "$R" "为空"

echo "--- 7. 删除与对象级清理 ---"
R=$(curl -s "${HU[@]}" -F "file=@$WORK/pic.png" "$BASE/zentao/file/upload?objectType=story&objectID=2")
F2=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/file/delete?id=$F2")
check "删除单个附件" 0 "$R"
R=$(curl -s "${H[@]}" "$BASE/zentao/file/get?id=$F2")
check "删除后再查→不存在" 1020014000 "$R" "附件不存在"
R=$(curl -s "${H[@]}" "$BASE/zentao/file/count?objectType=story&objectID=2")
[ "$(echo "$R" | d)" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 删除后计数 = 0"; } || { FAIL=$((FAIL+1)); echo "  ❌ 删除后计数=$(echo "$R" | d)"; }

# 给 story#3 传 2 个，再按对象批量删
curl -s "${HU[@]}" -F "file=@$WORK/pic.png" "$BASE/zentao/file/upload?objectType=story&objectID=3" > /dev/null
curl -s "${HU[@]}" -F "file=@$WORK/spec-$TS.txt" "$BASE/zentao/file/upload?objectType=story&objectID=3" > /dev/null
R=$(curl -s "${H[@]}" -X DELETE "$BASE/zentao/file/delete-by-object?objectType=story&objectID=3")
[ "$(echo "$R" | d)" = "2" ] && { PASS=$((PASS+1)); echo "  ✅ 按对象批量删除返回 2"; } || { FAIL=$((FAIL+1)); echo "  ❌ 批量删除=$(echo "$R" | d)"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/file/count?objectType=story&objectID=3")
[ "$(echo "$R" | d)" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 批量删除后计数 = 0"; } || { FAIL=$((FAIL+1)); echo "  ❌ 批量删除后计数=$(echo "$R" | d)"; }

echo "--- 8. 隔离性：不同对象互不串 ---"
R=$(curl -s "${H[@]}" "$BASE/zentao/file/list?objectType=story&objectID=1")
n1=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
R=$(curl -s "${H[@]}" "$BASE/zentao/file/list?objectType=bug&objectID=7")
n2=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n1" = "1" ] && [ "$n2" = "1" ] && { PASS=$((PASS+1)); echo "  ✅ story#1=1 条、bug#7=1 条（互不串）"; } \
  || { FAIL=$((FAIL+1)); echo "  ❌ story#1=$n1 bug#7=$n2"; }
R=$(curl -s "${H[@]}" "$BASE/zentao/file/list?objectType=story&objectID=7")
n=$(echo "$R" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
[ "$n" = "0" ] && { PASS=$((PASS+1)); echo "  ✅ 类型不同不串（story#7=0）"; } || { FAIL=$((FAIL+1)); echo "  ❌ story#7=$n"; }

cleanup
echo
echo "===== 结果：通过 $PASS / 失败 $FAIL ====="
[ "$FAIL" = "0" ] || exit 1

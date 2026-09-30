#!/bin/bash
# ---------------------------------------------------------------------------
# 183 退场清理：把「本项目（禅道迁移 yudao）」在 192.168.0.183 上的痕迹全部删掉
#
# 背景：2026-09-28 整套环境迁到 192.168.0.119（Ubuntu 26.04）：数据库 mysqldump 整库恢复、
# 镜像 docker save|ssh|docker load 内网直搬、源码在 119 重新编译、前端重新 pnpm install、
# 两个 systemd 单元重建。迁完并在 119 上跑通全量回归之后，才执行本脚本。
#
# ⚠️ 183 是**共用机器**，上面还跑着别的项目，脚本绝不碰它们：
#     tyarchive-*（数字档案馆：mysql/redis/es/rocketmq/nginx）
#     dangan-hot / dangan-hot-ocr、panwatch、dg-*（数据治理）、ruoyi-archives-*
#    另外 mysql:8.0 与 redis:7-alpine 两个**镜像**是别人也在用的，只删容器不删镜像。
#
# 用法：
#   bash deploy/decommission-183.sh          # 只打印将要删除的东西（dry-run，默认）
#   bash deploy/decommission-183.sh --yes    # 真正执行
#
# 刻意保留（见文末说明）：/usr/local/bin/node 与 /opt/git
# ---------------------------------------------------------------------------
set -uo pipefail

HOST="${HOST:-192.168.0.183}"
USER_="${USER_:-root}"
# 取口令：用 BASH_SOURCE 定位脚本目录（$0 在被 source 时是外层命令名，不准）
_zt_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$_zt_dir/_secrets.sh" 2>/dev/null || true
PASS="${PASS:-${SSH_PASS:-}}"
APPLY="${1:-}"

SSH=(sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "${USER_}@${HOST}")
Q() { "${SSH[@]}" "$1" 2>/dev/null | grep -viE "post-quantum|store now|may need to be upgraded|^\*\*"; }

ITEMS=(
  "systemd 单元  /etc/systemd/system/yudao-server.service"
  "systemd 单元  /etc/systemd/system/yudao-ui.service"
  "容器          yudao-mysql（含 ./data/mysql 绑定目录）"
  "容器          yudao-redis（含 ./data/redis 绑定目录）"
  "目录          /opt/yudao-stack（server + frontend + sql + data）"
  "目录          /opt/yudao-build（源码 + build.sh + maven 缓存配置）"
  "目录          /opt/jdk25（本项目手装的 JDK 25）"
  "目录          /opt/maven（本项目手装的 Maven）"
  "目录          /opt/src（git 2.43 的源码与构建脚本）"
  "保留          /opt/git + /usr/local/bin/git（git 2.43；删了会让全机回落到 CentOS 7 的 1.8.3）"
  "保留          /usr/local/bin/node（7 月 30 就装好了，早于本项目，别人的）"
)

echo "== 目标：${USER_}@${HOST}"
echo "== 将要删除 / 保留："
printf '   %s\n' "${ITEMS[@]}"
echo
echo "== 当前状态确认（删之前先看一眼）"
Q 'systemctl is-active yudao-server yudao-ui 2>/dev/null; docker ps -a --format "{{.Names}}" | grep -E "^yudao-" ; ls -d /opt/yudao-stack /opt/yudao-build /opt/jdk25 /opt/maven /opt/src 2>/dev/null'

if [ "$APPLY" != "--yes" ]; then
  echo
  echo "（dry-run 结束；加 --yes 才真正执行）"
  exit 0
fi

echo
echo "== 1) 停并删除两个 systemd 单元"
# 实测教训（2026-09-29）：只 stop+rm 单元文件是不够的 —— 单元可能变成 failed 但进程还活着，
# 而 vite 会把 /opt/yudao-stack/frontend/.vite 缓存目录重新建出来，看起来像「删不掉」。
# 所以必须显式 kill 残留进程，并且**复核进程数 = 0**。
Q 'systemctl disable --now yudao-server yudao-ui 2>&1 | tail -2; sleep 2; systemctl stop yudao-server yudao-ui 2>&1 | tail -2; echo "  服务已停"'
# 注意：pkill -f 的模式不能在本命令行里原样出现，否则会**自杀**（实测把远端执行命令的 shell 一起杀了，
# ssh 直接 channel closed）。所以用 "vite[.]js" / "yudao[-]server[.]jar" 这种「正则不匹配自己」的写法。
Q 'pkill -f "yudao[-]server[.]jar" && echo "  已杀 java(yudao-server.jar)" || echo "  无 java 残留"; pkill -f "vite[.]js --mode" && echo "  已杀 vite" || echo "  无 vite 残留"; sleep 3; echo "  残留进程数: $(ps -ef | grep -E "yudao[-]server[.]jar|vite[.]js --mode" | grep -vc grep)"'
Q 'rm -f /etc/systemd/system/yudao-server.service /etc/systemd/system/yudao-ui.service; systemctl daemon-reload; systemctl reset-failed 2>/dev/null; echo "  systemd 清理完成"; ls /etc/systemd/system/ | grep -i yudao || echo "  已无 yudao 单元文件"'

echo "== 2) 停并删除两个容器（镜像保留，别人也在用）"
Q 'docker stop yudao-mysql yudao-redis 2>/dev/null; docker rm yudao-mysql yudao-redis 2>/dev/null; echo "  容器清理完成"; docker ps -a --format "{{.Names}}" | grep -E "^yudao-" || echo "  已无 yudao 容器"'

echo "== 3) 删除本项目独占的目录"
Q 'rm -rf /opt/yudao-stack /opt/yudao-build /opt/jdk25 /opt/maven /opt/src; echo "  目录清理完成"; ls -d /opt/yudao-stack /opt/yudao-build /opt/jdk25 /opt/maven /opt/src 2>/dev/null || echo "  已全部不存在"'

echo "== 4) 复核：本项目痕迹应为 0，别的项目必须还在"
Q 'echo "  yudao 容器数: $(docker ps -a --format "{{.Names}}" | grep -c "^yudao-" || true)"; echo "  yudao 单元数: $(ls /etc/systemd/system/ | grep -ci yudao || true)"; echo "  yudao 进程数: $(ps -ef | grep -E "yudao[-]server[.]jar|vite[.]js --mode" | grep -vc grep || true)"; echo "  监听端口数(3307/6380/8081/48080): $(ss -lntp 2>/dev/null | grep -cE ":(3307|6380|8081|48080)" || true)"; echo "  /opt 下剩余: $(ls /opt | tr "\n" " ")"; echo "  别的项目容器: $(docker ps --format "{{.Names}}" | grep -vE "^yudao-" | tr "\n" " ")"'


echo
echo "== 完成。注意：183 上 3307/6380/8081/48080 这几个端口现在应当没有任何监听"

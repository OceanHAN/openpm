#!/bin/bash
# ---------------------------------------------------------------------------
# 把 192.168.0.119 上的项目整体挪到 /data/yudao（用户要求：代码放 /data 下）
#
# 迁移前布局（2026-09-28 从 183 搬过来时沿用 183 的 /opt 布局）：
#   /opt/yudao-stack/{server,frontend,sql,data,docker-compose.yml}
#   /opt/yudao-build/{ruoyi-vue-pro,build.sh,maven-settings.xml}
# 迁移后布局：
#   /data/yudao/
#     ├── build/{ruoyi-vue-pro,build.sh,maven-settings.xml}   后端源码 + 构建脚本
#     ├── frontend/                                           前端源码（含 node_modules）
#     ├── server/{yudao-server.jar,start.sh,logs}              后端运行时
#     ├── sql/                                                 建库 SQL 备份
#     ├── data/{mysql,redis}                                   MySQL/Redis 数据（bind mount）
#     └── docker-compose.yml
#
# 为什么连运行时和数据一起搬：compose 里的 bind mount 是相对路径（./data/mysql），
# 只搬源码会在 /opt 与 /data 之间留下两套路径，systemd 单元、验证脚本、文档各指一边，必炸。
#
# ✅ 2026-09-28 已在 192.168.0.119 上执行完毕（`--yes`）：项目现在在 /data/yudao，
#    /opt/yudao-stack 与 /opt/yudao-build 已删除；容器用新 bind mount 重建后 healthy，
#    后端 25 秒就绪、UI/API 各 200、zt_ 表数仍是 67。脚本保留在这里作为「同一台机再搬一次」
#    （或搬到别处）的可复现记录 —— 幂等：已是新布局时会直接退出。
#
# 用法（在 119 上，或从 Mac 上 ssh 过去执行）：
#   bash move-to-data-119.sh            # dry-run
#   bash move-to-data-119.sh --yes      # 真正执行
# ---------------------------------------------------------------------------
set -uo pipefail
# 取口令：用 BASH_SOURCE 定位脚本目录（$0 在被 source 时是外层命令名，不准）
_zt_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$_zt_dir/_secrets.sh" 2>/dev/null || true

OLD_STACK=/opt/yudao-stack
OLD_BUILD=/opt/yudao-build
NEW=/data/yudao
APPLY="${1:-}"

step() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }

if [ -d "$NEW/build/ruoyi-vue-pro" ] && [ ! -d "$OLD_STACK" ]; then
  echo "已经在 $NEW（无需迁移）"; exit 0
fi

step "0) 现状"
docker ps --format '{{.Names}}\t{{.Status}}' | grep -E '^yudao-' || true
systemctl is-active yudao-server yudao-ui 2>/dev/null || true
ls -d "$OLD_STACK" "$OLD_BUILD" 2>/dev/null || true
du -sh "$OLD_STACK"/* "$OLD_BUILD"/* 2>/dev/null | tail -8

if [ "$APPLY" != "--yes" ]; then
  echo; echo "（dry-run 结束；加 --yes 才真正执行）"; exit 0
fi

step "1) 停服务与容器"
sudo systemctl stop yudao-server yudao-ui
( cd "$OLD_STACK" && docker compose down )
# 确认容器已经停了（停不干净就 mv 数据目录会伤库）
docker ps -a --format '{{.Names}}' | grep -E '^yudao-' && { echo "!! 容器还在，放弃"; exit 1; }

step "2) 建新目录并搬（同分区 mv，秒级）"
sudo mkdir -p "$NEW"
sudo mv "$OLD_STACK/server"   "$NEW/server"
sudo mv "$OLD_STACK/frontend" "$NEW/frontend"
sudo mv "$OLD_STACK/sql"      "$NEW/sql"
sudo mv "$OLD_STACK/data"     "$NEW/data"
sudo mv "$OLD_STACK/docker-compose.yml" "$NEW/"
[ -f "$OLD_STACK/README.md" ] && sudo mv "$OLD_STACK/README.md" "$NEW/"
sudo mkdir -p "$NEW/build"
sudo mv "$OLD_BUILD/ruoyi-vue-pro" "$NEW/build/"
sudo mv "$OLD_BUILD/build.sh" "$OLD_BUILD/maven-settings.xml" "$NEW/build/" 2>/dev/null || true
sudo chown -R ubuntu:ubuntu "$NEW"
ls -la "$NEW"; du -sh "$NEW"/* 2>/dev/null

step "3) 改脚本里的旧路径（start.sh / build.sh）"
sudo sed -i "s#/opt/yudao-stack#/data/yudao#g; s#/opt/yudao-build#/data/yudao/build#g" \
  "$NEW/server/start.sh" "$NEW/frontend/start.sh" "$NEW/build/build.sh"
grep -n 'data/yudao' "$NEW/server/start.sh" "$NEW/frontend/start.sh" "$NEW/build/build.sh" | head -8

step "4) 重写两个 systemd 单元"
sudo tee /etc/systemd/system/yudao-server.service >/dev/null <<'EOF'
[Unit]
Description=yudao-server (ZenTao migration backend, 192.168.0.119:/data/yudao)
After=docker.service network-online.target
Wants=network-online.target

[Service]
User=ubuntu
WorkingDirectory=/data/yudao/server
ExecStart=/data/yudao/server/start.sh
Restart=always
RestartSec=10
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
sudo tee /etc/systemd/system/yudao-ui.service >/dev/null <<'EOF'
[Unit]
Description=yudao admin UI (vite dev server, 192.168.0.119:8081, /data/yudao/frontend)
After=network-online.target
Wants=network-online.target

[Service]
User=ubuntu
WorkingDirectory=/data/yudao/frontend
ExecStart=/data/yudao/frontend/start.sh
Restart=always
RestartSec=10
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload

step "5) 起容器与两个服务"
( cd "$NEW" && docker compose up -d )
for i in $(seq 1 30); do
  m=$(docker inspect -f '{{.State.Health.Status}}' yudao-mysql 2>/dev/null || echo none)
  r=$(docker inspect -f '{{.State.Health.Status}}' yudao-redis 2>/dev/null || echo none)
  [ "$m" = healthy ] && [ "$r" = healthy ] && break; sleep 5
done
echo "mysql=$m redis=$r"
sudo truncate -s 0 "$NEW/server/logs/stdout.log" 2>/dev/null || true
sudo systemctl start yudao-server yudao-ui
for i in $(seq 1 40); do
  grep -q 'Started YudaoServerApplication' "$NEW/server/logs/stdout.log" 2>/dev/null && { echo "后端就绪（第 $((i*5)) 秒）"; break; }
  sleep 5
done
systemctl is-active yudao-server yudao-ui
curl -s -o /dev/null -w 'UI http=%{http_code}\n' --max-time 20 http://127.0.0.1:8081/
curl -s -o /dev/null -w 'API http=%{http_code}\n' --max-time 20 http://127.0.0.1:48080/admin-api/system/auth/login
docker exec yudao-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT CONCAT('zt_ 表数=', COUNT(*)) FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'zt\\\\_%';" 2>/dev/null

step "6) 删掉 /opt 下的旧目录"
sudo rm -rf "$OLD_STACK" "$OLD_BUILD"
ls -d "$OLD_STACK" "$OLD_BUILD" 2>/dev/null || echo "  /opt 下已无 yudao 目录"
echo
echo "完成：项目现在在 $NEW"

#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# yudao 后端构建脚本（SIGKILL 看门狗版）
#
# 背景与教训：
#   1. 本机到 Maven 仓库的连接会偶发挂起，而 Maven 对停滞连接没有读超时，
#      一挂就是几十分钟甚至永久。
#   2. Maven 注册了 JVM 关闭钩子，收到 SIGTERM 会尝试优雅关闭；而它正阻塞在
#      网络读取上时，关闭钩子同样会挂住 —— 所以普通 `timeout`（默认 SIGTERM）
#      和基于 SIGTERM 的看门狗都杀不掉它，必须用 SIGKILL(-9)。
#   3. 每轮都会复用 ~/.m2 里已下载的依赖，因此进度是累积的，多轮能收敛。
#
# 用法：bash build-backend.sh
# ---------------------------------------------------------------------------
set -u

PROJECT_DIR="/Library/allproject/02code/yudao/ruoyi-vue-pro"
MVN_SETTINGS="/Library/allproject/02code/yudao/maven-settings.xml"
export JAVA_HOME="/Users/co/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home"

MAX_ROUNDS=20
ROUND_TIMEOUT=420        # 单轮最长 7 分钟，到点无条件 SIGKILL
STALL_LIMIT=150          # 日志停滞 150 秒即判定卡死
CHECK_INTERVAL=15
LOG_DIR="/tmp/yudao-build"

mkdir -p "$LOG_DIR"

hard_kill() {
  # 只杀本项目；用户的 ygdSys 等其他 Java 进程不受影响
  pkill -9 -f "ruoyi-vue-pro" 2>/dev/null
  sleep 2
}

for i in $(seq 1 "$MAX_ROUNDS"); do
  LOG="$LOG_DIR/kill-$i.log"
  echo "########## 第 $i 轮 ##########"

  find "$HOME/.m2/repository" -name "*.lastUpdated" -delete 2>/dev/null

  cd "$PROJECT_DIR" || exit 1
  mvn -B -s "$MVN_SETTINGS" \
      -Dmaven.legacyLocalRepo=true \
      -Dmaven.artifact.threads=2 \
      package -DskipTests > "$LOG" 2>&1 &
  MPID=$!

  # 【关键】阻止系统睡眠。本机靠电池供电，macOS 每隔十几分钟就进入 Sleep，
  # 一睡眠网络连接全部冻结、Maven 表现为"卡死"，sleep 计时器也会被拉长。
  # -dimsu = 显示器/空闲/磁盘/系统/用户活跃 全部保持唤醒，-w 跟随构建进程退出。
  caffeinate -dimsu -w "$MPID" &
  CAFF=$!

  # 兜底：到点无条件硬杀（防止看门狗本身出问题）
  ( sleep "$ROUND_TIMEOUT"; kill -9 "$MPID" 2>/dev/null ) &
  KILLER=$!

  LAST=-1
  STALL=0
  while kill -0 "$MPID" 2>/dev/null; do
    sleep "$CHECK_INTERVAL"
    SIZE=$(wc -c < "$LOG" 2>/dev/null | tr -d ' ')
    SIZE=${SIZE:-0}
    if [ "$SIZE" = "$LAST" ]; then
      STALL=$((STALL + CHECK_INTERVAL))
    else
      STALL=0
    fi
    LAST=$SIZE
    if [ "$STALL" -ge "$STALL_LIMIT" ]; then
      echo "  ⚠️  日志停滞 ${STALL}s，SIGKILL 终止本轮"
      kill -9 "$MPID" 2>/dev/null
      break
    fi
  done

  kill "$KILLER" 2>/dev/null
  kill "$CAFF" 2>/dev/null
  wait "$MPID" 2>/dev/null
  hard_kill

  MODULES=$(grep -cE '^\[INFO\] Building yudao' "$LOG" 2>/dev/null | tr -d ' ')
  MODULES=${MODULES:-0}

  if grep -q "BUILD SUCCESS" "$LOG" 2>/dev/null; then
    echo "  ✅ 第 $i 轮构建成功"
    grep -E "BUILD SUCCESS|Total time" "$LOG" | head -2
    echo
    echo "=== 产物 ==="
    ls -lh "$PROJECT_DIR"/yudao-server/target/*.jar 2>/dev/null
    exit 0
  fi

  echo "  轮次 $i 结束：构建到 $MODULES / 21 个模块"
done

echo "❌ $MAX_ROUNDS 轮后仍未成功"
tail -15 "$LOG"
exit 1

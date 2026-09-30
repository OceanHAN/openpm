#!/bin/bash
# ---------------------------------------------------------------------------
# 在 192.168.0.119 上编译 yudao-server（禅道迁移项目）—— /data/yudao/build/build.sh
#
# 用法：
#   bash /data/yudao/build/build.sh            # 只编译
#   bash /data/yudao/build/build.sh --deploy   # 编译 + 换 jar + 重启 yudao-server
#
# 本机（Mac）同步源码 —— **必须带 -m**（否则见下面的坑）：
#   cd /Library/allproject/02code/yudao
#   tar czmf - --exclude='.git' --exclude='target' -C ruoyi-vue-pro . \
#     | ssh ubuntu@192.168.0.119 'tar xzmf - -C /data/yudao/build/ruoyi-vue-pro'
#   ssh ubuntu@192.168.0.119 'bash /data/yudao/build/build.sh --deploy'
#
# ⚠️ 坑位 #58：`tar` 默认保留源文件 mtime。Mac 上的文件如果是「本地时间 10:38」、
#    服务器上的 class 是「UTC 02:44」，去掉时区后源码反而更旧 —— Maven 的增量编译
#    会直接跳过，`package` 把旧 class 打进 jar，部署脚本照样换 jar、重启、一切「成功」。
#    所以：同步必须 `-m`（--touch）；下面还会在编译前自动检查一次并报警。
# ---------------------------------------------------------------------------
set -e
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
cd /data/yudao/build/ruoyi-vue-pro

SRC_NEW=$(find yudao-module-zentao/src -type f -newer yudao-module-zentao/target/classes 2>/dev/null | head -1)
if [ -z "$SRC_NEW" ] && [ -d yudao-module-zentao/target/classes ]; then
  echo "!! 注意：yudao-module-zentao/src 下没有任何文件比 target/classes 新。"
  echo "!! 如果你刚同步过源码，说明 tar 把 Mac 的 mtime 一起带过来了 —— Maven 会跳过编译（坑位 #58）。"
  echo "!! 用 tar -m/-‑touch 重新同步（tar czmf … | ssh … 'tar xzmf …'）后再跑本脚本。"
fi

echo "== JDK: $(java -version 2>&1 | head -1)"
echo "== Maven: $(mvn -v 2>&1 | head -1)"
JAR_BEFORE=$(stat -c %Y yudao-server/target/yudao-server.jar 2>/dev/null || echo 0)
echo "== 开始编译（$(date '+%F %T')）"
mvn -B -s /data/yudao/build/maven-settings.xml -Dmaven.legacyLocalRepo=true \
    -pl yudao-server -am package -DskipTests
JAR_AFTER=$(stat -c %Y yudao-server/target/yudao-server.jar 2>/dev/null || echo 0)
echo "== 编译完成（$(date '+%F %T')）"
ls -lh yudao-server/target/yudao-server.jar
if [ "$JAR_BEFORE" = "$JAR_AFTER" ]; then
  echo "!! jar 时间戳没变 —— 大概率什么都没编（mtime 坑位 #58 / 或者确实没有改动）"
fi

if [ "$1" = "--deploy" ]; then
  cp yudao-server/target/yudao-server.jar /data/yudao/server/yudao-server.jar
  sudo systemctl restart yudao-server
  echo "== 已替换服务器上的 jar 并重启 yudao-server"
fi

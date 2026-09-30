#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 切换到 JDK 25 —— 芋道 ruoyi-vue-pro (master-jdk25 分支) 要求
#
# 用法：
#   source use-jdk25.sh      # 当前 shell 生效
#
# 说明：
#   本机的 ~/.zshrc 把 JAVA_HOME 硬编码到了 Corretto 8，会让 mvn 也跑在 Java 8 上。
#   本项目需要 JDK 25（Spring Boot 4.1.1）。这里只改当前 shell，不动全局配置，
#   避免影响你其他 Java 8 的项目。
# ---------------------------------------------------------------------------

_JH="$(/usr/libexec/java_home -v 25 2>/dev/null)"

if [ -z "$_JH" ]; then
  echo "❌ 未找到 JDK 25，请先安装（brew install --cask temurin@25）" >&2
  return 1 2>/dev/null || exit 1
fi

export JAVA_HOME="$_JH"
export PATH="$JAVA_HOME/bin:$PATH"
unset _JH

echo "✅ JAVA_HOME = $JAVA_HOME"
java -version
echo
echo "Maven: $(mvn -v 2>/dev/null | head -1)"

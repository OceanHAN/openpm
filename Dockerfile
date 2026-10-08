# ---------------------------------------------------------------------------
# openpm 镜像（多目标）：
#   docker build --target backend  -t ghcr.io/<owner>/openpm-server:v0.1.0 .
#   docker build --target frontend -t ghcr.io/<owner>/openpm-ui:v0.1.0     .
# 也可以直接用仓库根目录的 docker-compose.app.yml 一起起（含 MySQL/Redis）。
#
# 说明：前端必须用 VITE_BASE_URL 指定后端地址；容器里默认走同源（空串），
#      由 deploy/docker/nginx.conf 把 /admin-api 反代到后端服务。
# ---------------------------------------------------------------------------

# ---------- 1) 后端构建 ----------
FROM eclipse-temurin:25-jdk AS backend-build
ARG MAVEN_VERSION=3.9.9
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
 && curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    | tar -xz -C /opt \
 && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY ruoyi-vue-pro/ ./
RUN mvn -B -pl yudao-server -am package -DskipTests

# ---------- 2) 后端运行时 ----------
FROM eclipse-temurin:25-jre AS backend
LABEL org.opencontainers.image.title="openpm server" \
      org.opencontainers.image.description="研发协作平台后端（参考禅道 + 自己的设计）" \
      org.opencontainers.image.licenses="AGPL-3.0"
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 10001 openpm
COPY --from=backend-build /src/yudao-server/target/yudao-server.jar /app/app.jar
USER openpm
EXPOSE 48080
# 直接读环境变量：DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD、REDIS_HOST/REDIS_PORT/REDIS_PASSWORD
ENTRYPOINT ["sh","-c","exec java -Duser.timezone=Asia/Shanghai -jar /app/app.jar \
  --spring.profiles.active=local \
  --spring.datasource.dynamic.datasource.master.url=\"jdbc:mysql://${DB_HOST:-mysql}:${DB_PORT:-3306}/${DB_NAME:-ruoyi-vue-pro}?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true\" \
  --spring.datasource.dynamic.datasource.slave.url=\"jdbc:mysql://${DB_HOST:-mysql}:${DB_PORT:-3306}/${DB_NAME:-ruoyi-vue-pro}?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true\" \
  --spring.datasource.dynamic.datasource.master.username=${DB_USER:-root} \
  --spring.datasource.dynamic.datasource.master.password=${DB_PASSWORD:-} \
  --spring.datasource.dynamic.datasource.slave.username=${DB_USER:-root} \
  --spring.datasource.dynamic.datasource.slave.password=${DB_PASSWORD:-} \
  --spring.data.redis.host=${REDIS_HOST:-redis} --spring.data.redis.port=${REDIS_PORT:-6379} --spring.data.redis.password=${REDIS_PASSWORD:-}"]

# ---------- 3) 前端构建 ----------
FROM node:22-slim AS frontend-build
ARG VITE_BASE_URL=""
RUN corepack enable
WORKDIR /src
COPY yudao-ui-admin-vue3/package.json yudao-ui-admin-vue3/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY yudao-ui-admin-vue3/ ./
# 容器里默认同源（由 nginx 反代 /admin-api），需要绝对地址时改 --build-arg VITE_BASE_URL=http://your-host:48080
RUN printf 'NODE_ENV=production\nVITE_DEV=false\nVITE_BASE_URL=%s\nVITE_UPLOAD_TYPE=server\nVITE_API_URL=/admin-api\n' "$VITE_BASE_URL" > .env.prod.local \
 && node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build --mode prod

# ---------- 4) 前端运行时（nginx 静态 + 反代） ----------
FROM nginx:1.27-alpine AS frontend
LABEL org.opencontainers.image.title="openpm ui" \
      org.opencontainers.image.description="研发协作平台前端（Vue3 + Element Plus）" \
      org.opencontainers.image.licenses="AGPL-3.0"
COPY --from=frontend-build /src/dist /usr/share/nginx/html
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

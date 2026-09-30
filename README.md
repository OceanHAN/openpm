<div align="center">

# openpm

**把禅道（ZenTao）的业务规则，用 Java + Vue3 重新实现的开源研发协作平台**

产品 · 项目 · 执行 · 需求 · 任务 · 缺陷 · 测试 · 文档 · 工时 · 看板 · 度量 · BI · 报表

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3-42b883.svg)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1.svg)](https://www.mysql.com/)
[![Tests](https://img.shields.io/badge/API%20tests-43%20suites%20%2F%201704%20assertions-success.svg)](#测试与验证)

</div>

---

## 这是什么

**openpm** 是一套用 Java 重写的研发项目管理与协作系统，业务规则对标 [禅道（ZenTao）](https://github.com/easysoft/zentaopms)，
技术底座是 [yudao / ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro)，数据库表结构沿用禅道的 `zt_*` 命名与字段语义。

一句话：**同一套研发管理流程，换成 Java + MySQL + Vue3 的技术栈，规则尽量 1:1。**
每条业务规则都能追溯到禅道源码里的出处，有意偏离的地方在 [实现说明](docs/IMPLEMENTATION-NOTES.md) 里逐条写明。

### 它不是什么

- **不是禅道官方项目**，与青岛易软天创无关联；仓库里**不含禅道的任何源码**（不 fork、不搬运）。
- **不是「PHP 翻译成 Java」的逐行移植**：业务规则照抄，实现方式按 Java 技术栈重写（MyBatis-Plus、Spring 事务、Jackson 3…）。
- **不是开箱即用的 SaaS**：定位是自建部署的研发管理平台，需要自己准备 MySQL 8 / Redis 7 与构建环境。

> 「禅道 / ZenTao」是其权利人的商标，本项目仅在说明兼容性时引用该名称。

## 功能

| 领域 | 能力 |
|---|---|
| 主干链 | 产品、产品计划、项目集、项目、执行（迭代 / 阶段）、需求（含业务/用户/研发分层与版本链）、任务（含多人任务）、缺陷、构建、发布 |
| 质量链 | 测试用例、用例库、测试单、测试报告、测试仪表盘、缺陷解决 → 构建/发布清单自动回写 |
| 研发协作 | 文档库、附件、工时明细、团队、干系人、看板、待办与「我的地盘」、操作日志与回收站 |
| 度量与分析 | 度量框架（定义 + 快照 + 复用口径）、数据视图（受控 SQL）、图表、报表（年度/产出统计）、执行燃尽图 |
| 系统与集成 | 组织/权限（复用框架 RBAC 并给出禅道视角）、代码库（本地 Git 同步与提交关联）、节假日与工作日口径、应用接入（第三方免登录签名）、公司信息、积分、保存查询、度量维度、接口文档库、Webhook |

界面为中文，登录后左侧菜单「禅道」下即全部功能。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 25 · Spring Boot 4.1 · MyBatis-Plus · MySQL 8 · Redis 7（`ruoyi-vue-pro/yudao-module-zentao`） |
| 前端 | Vue 3 · TypeScript · Element Plus · Vite（`yudao-ui-admin-vue3`） |
| 部署 | Docker Compose（MySQL / Redis）+ systemd（后端 jar / 前端 vite dev server） |

规模：后端禅道模块 **537 个 Java 文件 / 约 5.7 万行**，前端禅道页面与接口层 **130 个文件 / 约 2.4 万行**，数据库 **67 张 `zt_*` 表**。

## 快速开始

### 前置条件

- JDK 25、Maven 3.9+
- Node 20+ 与 pnpm
- Docker（跑 MySQL 8 与 Redis 7）

### 1. 起依赖（MySQL / Redis）

```bash
git clone https://github.com/OceanHAN/openpm.git && cd openpm
cp deploy/.env.example deploy/.env          # 填 MYSQL_PASS / REDIS_PASS
cd deploy && docker compose up -d && cd ..
```

### 2. 建库（导入禅道表结构与应用数据）

```bash
set -a; . deploy/.env; set +a
docker exec -i yudao-mysql mysql -uroot -p"$MYSQL_PASS" --default-character-set=utf8mb4 \
  < deploy/sql/01-ruoyi-vue-pro.sql
# 其余增量脚本（02~56）按序号依次导入，均为幂等；完整清单与说明见 deploy/README.md
```

### 3. 起后端

```bash
cd ruoyi-vue-pro
mvn -pl yudao-server -am package -DskipTests
java -jar yudao-server/target/yudao-server.jar \
  --spring.profiles.active=local \
  --spring.datasource.dynamic.datasource.master.url="jdbc:mysql://127.0.0.1:3307/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai" \
  --spring.datasource.dynamic.datasource.master.username=yudao \
  --spring.datasource.dynamic.datasource.master.password="$MYSQL_PASS" \
  --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6380 --spring.data.redis.password="$REDIS_PASS"
```

### 4. 起前端

```bash
cd yudao-ui-admin-vue3
pnpm install
cp .env.local.example .env.local 2>/dev/null || true   # 把 VITE_BASE_URL 指向后端
pnpm dev
```

浏览器打开 <http://localhost:8081>，默认账号 `admin / admin123`。

> ⚠️ `admin123` 只是演示默认值，任何对外暴露的部署都必须先改掉它，并把 3307/6380 收到防火墙后面。

## 测试与验证

这个仓库的验证不是「点几下」，而是可复现的脚本：

```bash
# 43 个接口回归脚本（1704 项断言），针对任意已部署的后端
ZENTAO_API_BASE=http://<host>:48080/admin-api bash deploy/test-story-module.sh

# 21 个页面巡检 + 20 个浏览器专项（Playwright）
ZENTAO_UI_BASE=http://<host>:8081 node deploy/ui-check/all-pages.mjs

# 一把跑完整套（SQL 同步 → 重启服务 → 接口回归 → 浏览器检查）
bash deploy/resume-verification-remote.sh
```

| 项 | 规模 |
|---|---|
| 接口回归 | **43 个脚本 / 1704 项断言** |
| 页面巡检 | **21 个页面** |
| 浏览器专项 | **20 个**（团队、看板、度量、BI、燃尽图、代码库、Webhook…） |
| 静态预检 | MySQL 保留字、驼峰列名、租户忽略表 |

## 项目状态

禅道开源版共 99 个模块，本项目的覆盖情况（证据见 [模块可行性审计](docs/MODULE-FEASIBILITY-AUDIT.md)）：

| 状态 | 模块数 | 说明 |
|---|---|---|
| ✅ 已实现 | **48** | 主干链、交付链、质量链、度量/BI 等 |
| 🚧 部分实现 | 4 | `metric`（框架 + 15 个口径）、`bi`（SQL 模式；DuckDB/透视表未做）、`common`、`block` |
| ⛔ 不迁移 | 47 | 23 个开源版没有实现可搬 + 6 个外部服务依赖 + 18 个「框架已有等价能力」（如审批流 → 工作流引擎、站内信/邮件 → 系统通知） |

**路线图**：`metric` 剩余口径与 `bi` 透视表 → 更多列表页的批量操作与导入导出 → 通知/消息链路 → 生产部署形态（静态前端 + 反向代理 + 备份）。

## 文档

| 文档 | 内容 |
|---|---|
| [实现说明（长文）](docs/IMPLEMENTATION-NOTES.md) | 逐模块口径对照、架构决策、**58 条踩坑记录**、部署细节 |
| [迁移清单](docs/MIGRATION-INVENTORY.md) | 99 个模块的逐条状态与理由 |
| [模块可行性审计](docs/MODULE-FEASIBILITY-AUDIT.md) | 哪些能迁、哪些不必迁、哪些没有实现可搬（含复现脚本） |
| [只做映射的模块](docs/MAP-ONLY-MAPPINGS.md) | 10 个「框架已有等价能力」模块的替代关系 |
| [剩余模块取证](docs/REMAINING-MODULE-VERDICTS.md) | 24 个模块的逐条证据 |
| [deploy/README.md](deploy/README.md) | 部署与验证脚本怎么用 |

## 贡献

欢迎 issue 与 PR。动手前请先读 [CONTRIBUTING.md](CONTRIBUTING.md)（分支/提交约定、怎么跑测试、新增模块的检查清单）。

## 许可证

[AGPL-3.0](LICENSE)。

本项目不含禅道源码，但**表结构与业务规则对标禅道**；禅道采用 ZPL 1.2 / AGPL 双授权，因此本项目选择 AGPL-3.0 以保持兼容。
如果你的使用场景与该选择冲突，请先开 issue 讨论。

## 致谢

- [禅道 ZenTao](https://github.com/easysoft/zentaopms) —— 业务规则的来源与规格
- [yudao / ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) —— 技术底座
- [Element Plus](https://element-plus.org/) · [Vue](https://vuejs.org/) · [MyBatis-Plus](https://baomidou.com/) · [Playwright](https://playwright.dev/)

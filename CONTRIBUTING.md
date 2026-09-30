# 贡献指南

感谢你愿意参与。这个仓库的目标是**把一套研发管理流程做扎实**，因此对代码和验证的要求比一般项目更严一点。

## 开发环境

```bash
# 依赖：JDK 25、Maven 3.9+、Node 20+、pnpm、Docker
cp deploy/.env.example deploy/.env                 # 填 MySQL/Redis 口令
cd deploy && docker compose up -d && cd ..         # MySQL 3307 / Redis 6380
# 建库见 README「快速开始」
```

口令一律走环境变量与 `deploy/_secrets.sh`（模板 `deploy/_secrets.example.sh`，真实文件已被 gitignore）——**不要把任何口令写进代码或文档**，GitHub 的 push protection 也会拦。

## 提交前必跑

```bash
# 1) 编译（后端）
cd ruoyi-vue-pro && mvn -pl yudao-server -am package -DskipTests

# 2) 新增表后的三个静态预检（都会连库，缺一不可）
bash deploy/check-reserved-columns.sh      # MySQL 保留字列要加反引号
bash deploy/check-camel-columns.sh         # 驼峰列名漏 @TableField
bash deploy/check-tenant-ignore.sh         # 新 zt_* 表必须登记进 tenant.ignore-tables

# 3) 至少跑与改动相关的接口回归脚本
ZENTAO_API_BASE=http://<host>:48080/admin-api bash deploy/test-<模块>-module.sh
```

**新增模块**请按仓库既有约定成套交付：真实的表 + 接口 + 前端页面 + 接口回归脚本（`deploy/test-<模块>-module.sh`）+ 浏览器检查（`deploy/ui-check/<模块>.mjs`），并把它们登记进 `deploy/resume-verification-remote.sh`。

## 代码约定

- 新增类名统一带 `Zentao` 前缀（如 `ZentaoStoryService`），避免与框架模块的 bean 名/类型别名冲突；注入字段也带前缀。
- 数据库表沿用禅道命名 `zt_*`；**不要改表名与字段语义**，它们是与禅道源码对照的锚点。
- 业务规则要能说清出处（禅道哪个文件、哪段逻辑）；有意偏离必须在 PR 描述与 `docs/IMPLEMENTATION-NOTES.md` 里写明。
- 只做只读聚合或查询时，注意分页/排序/日期口径，尽量给「数量级」断言而不是只看接口 200。

## 分支与提交

- 分支：`feat/<模块>`、`fix/<问题>`、`docs/<主题>`。
- 提交信息：一行摘要 + 必要的正文，说清「做了什么、为什么、怎么验证的」；涉及禅道规则时引用出处。
- PR：描述里附上跑过的命令与结果（哪个脚本、通过多少项）。

## Issue

- Bug 请附：操作路径、期望与实际、后端日志（`logs/stdout.log`）、涉事表数据。
- 功能请求请说明对应的禅道行为（如有），便于判断是「迁移」还是「新功能」。

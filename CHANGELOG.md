# 变更记录

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与[语义化版本](https://semver.org/lang/zh-CN/)。
尚未发布正式版本，下面是按时间倒序的里程碑。

> 定位：以禅道的研发管理思路为参考，按自己的判断做取舍与改进（**不是禅道复刻**）。

## [未发布]

## [v0.1.0] - 2026-10-08

首个公开版本（tag `v0.1.0`）。

### 新增

- Docker 镜像：`Dockerfile` 多目标（`backend` = 后端 jar，`frontend` = nginx + 构建后的静态资源），
  由 [.github/workflows/docker.yml](.github/workflows/docker.yml) 构建并推送到 GHCR；
  `deploy/docker-compose.app.yml` 可一键起前后端 + MySQL + Redis
- README 增加「用镜像一键起」与 release 徽章

### 修复

- 补上上游 `yudao-ui-admin-vue3` checkout 里缺失的 `src/views/oa/utils/constants.ts`
  （3 个考勤组件 import 它），前端**生产构建 `vite build` 从报错变为可用**（28.8s 构建成功）
- `deploy/ui-check/webhook.mjs` 的远端 SQL 通道不再写死 `sshpass root@`，改为公钥 + 远端读口令
- `test-program-module.sh` 两处断言不再假设「库里只有演示数据」


### 新增

- 完整实现禅道主干链：产品 / 产品计划 / 项目集 / 项目 / 执行（迭代与阶段）/ 需求（分层 + 版本链）/ 任务（含多人任务）/ 缺陷 / 构建 / 发布
- 质量链：测试用例、用例库、测试单、测试报告、测试仪表盘，缺陷解决 → 构建/发布清单自动回写
- 研发协作：文档库、附件、工时明细、团队、干系人、看板、待办与「我的地盘」、操作日志与回收站
- 度量与分析：度量框架（定义 + 快照 + 15 个内置口径）、数据视图（受控 SQL）、图表、报表、执行燃尽图
- 系统与集成：组织与权限视图、代码库（本地 Git 同步与提交关联）、节假日与工作日口径、应用接入（签名校验）、公司信息、积分、保存查询、度量维度、接口文档库、Webhook
- 验证资产：43 个接口回归脚本（1704 项断言）、21 页面巡检、20 个浏览器专项、3 个静态预检脚本
- 文档：迁移清单、模块可行性审计（121 个候选的 A/B/C 判定）、只做映射的模块、剩余模块取证、实现说明（含 58 条坑点）

### 变更

- 首页 README 重写为面向开源访客的结构；原实现长文移至 `docs/IMPLEMENTATION-NOTES.md`
- 口令一律从环境变量与未跟踪的 `deploy/_secrets.sh` 读取，仓库内不再保存任何明文口令

### 安全

- 移除仓库中残留的示例云厂商 Key（腾讯 / 阿里云 / 七牛 / 火山 / 华为）与应用/数据库口令

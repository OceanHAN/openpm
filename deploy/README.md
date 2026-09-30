# yudao 容器栈（192.168.0.119，2026-09-28 从 192.168.0.183 迁来）

为 `ruoyi-vue-pro` 项目提供的独立 MySQL + Redis，与机器上已有的
`tyarchive-stack`、数据治理(dg) 两个栈完全隔离。

## 连接信息

| 服务 | 地址 | 账号 | 密码 |
|---|---|---|---|
| MySQL（业务用） | `192.168.0.119:3307` | `yudao` | `<口令见 deploy/_secrets.sh>` |
| MySQL（管理用） | `192.168.0.119:3307` | `root` | `<口令见 deploy/_secrets.sh>` |
| Redis | `192.168.0.119:6380` | — | `<口令见 deploy/_secrets.sh>` |

- 数据库名：`ruoyi-vue-pro`
- MySQL 字符集：`utf8mb4` / `utf8mb4_unicode_ci`
- MySQL 时区：`+08:00`，`lower_case_table_names=1`
- Redis：`appendonly yes`，`maxmemory 512mb`，`maxmemory-policy noeviction`

> ⚠️ 这是开发/测试环境的密码，上生产前必须更换，并且 Redis 不应直接暴露公网。

## 目录结构

```
/data/yudao/
├── docker-compose.yml
├── sql/                      # 容器首次初始化时自动执行
│   ├── 01-ruoyi-vue-pro.sql  # 48 张业务表
│   ├── 02-quartz.sql         # 11 张定时任务表
│   └── 03-app-user.sql       # 创建 yudao 账号并授权
└── data/                     # 持久化数据（勿删）
    ├── mysql/
    └── redis/
```

仓库里的 `deploy/sql/01~56*.sql` 可以**从空库完整建出环境**（01~03 是 yudao 基础库，
04~35 是禅道的表/菜单/部分演示数据，**36 是演示数据补种** —— 项目/需求/任务/缺陷那几条
当年只存在于远端库里，从空库初始化时会缺，详见 36 的注释与docs/IMPLEMENTATION-NOTES.md 坑位 #42；
**39 是用例库**：它不给 `zt_case` 建新表，而是补 `lib`/`fromCaseID`/`fromCaseVersion` 三列 +
用例库菜单 + 演示库，ALTER 用 `information_schema` 判过、可重复执行；**40 是看板**：
八张表（空间/看板/区域/分组/泳道/列/卡片/格子）+ 菜单 + 一条演示板；**41 是度量**：
`zt_metric` + `zt_metriclib` + 18 个内置度量项定义；**42 是 BI**：`zt_dataview` + `zt_chart`；**43 是回收站/动态的菜单权限**（不建表，
只补 `zentao:action:*` 三行权限，否则前端按钮会被 v-hasPermi 隐藏）；
**44 是报表的演示动作**（报表按 `zt_action` 统计，演示数据要补配套动作，坑位 #48）、
**45 是燃尽图** `zt_burn`、**46 是测试仪表盘的菜单权限**、**47 是代码库** `zt_repo`/`zt_repohistory`/`zt_repofiles`/`zt_relation`、
**48 是节假日** `zt_holiday`、**49 是应用接入** `zt_entry` + 通用日志表 `zt_log`、
**50 是公司信息** `zt_company`、**51 是积分** `zt_score`（规则/日志/总分）、
**52 是保存查询** `zt_userquery` + `zt_searchdict`、**53 是度量维度** `zt_dimension`、
**54 是接口文档库** `zt_api`/`zt_apispec`/`zt_apistruct`/`zt_apistruct_spec`/`zt_api_lib_release`、
**55 是 Webhook** `zt_webhook`（调用日志复用 `zt_log`）、
**56 是侧边栏清理**（把没编进 jar 的顶层菜单 `status=1` 停用：CRM/ERP/WMS/MES/OA/FMS/IoT/MP/CMS/支付/商城/统计，以及 7 个「候选承载方」bpm/report/HRM/PMS/IM/AI/会员 —— 点它们本来只会返回 501「模块已禁用」；`filterDisableMenus` 递归生效，改顶层一行即可，可逆）），不随容器初始化自动执行，
需要手动灌进远端（每次都要带 `--default-character-set=utf8mb4`，否则中文会变乱码）：

```bash
# 以 38-zt_workestimation.sql 为例，脚本本身是幂等的（CREATE TABLE IF NOT EXISTS + ON DUPLICATE KEY UPDATE）
ssh -o StrictHostKeyChecking=no ubuntu@192.168.0.119 \
  "docker exec -i yudao-mysql mysql -uroot -p'<口令见 deploy/_secrets.sh>' --default-character-set=utf8mb4 ruoyi-vue-pro" \
  < deploy/sql/38-zt_workestimation.sql
```

其中 `23-file-storage-setup.sql` 会把**主存储切换成「数据库」客户端（id=4）**，
让附件字节落到 `infra_file_content`，并把 `infra_file_config.config.domain` 设成
`http://127.0.0.1:48080`（**不能带 `/admin-api` 前缀**，否则会拼出双重路径 404）。
改完这个配置必须**重启后端**：文件配置是带缓存的。

## 常用操作

```bash
cd /data/yudao

# 启动 / 停止 / 重启
docker compose up -d
docker compose stop
docker compose restart

# 查看状态与日志
docker compose ps
docker compose logs -f mysql
docker compose logs -f redis

# 进入 MySQL
docker exec -it yudao-mysql mysql -uyudao -p'<口令见 deploy/_secrets.sh>' ruoyi-vue-pro

# 进入 Redis
docker exec -it yudao-redis redis-cli -a '<口令见 deploy/_secrets.sh>'

# 备份数据库
docker exec yudao-mysql mysqldump -uroot -p'<口令见 deploy/_secrets.sh>' \
  --single-transaction --routines --triggers ruoyi-vue-pro \
  > /data/mysql-backup/yudao-$(date +%F).sql
```

## 重新初始化

`sql/` 下的脚本**只在 `data/mysql` 为空时执行一次**。若需重建：

```bash
cd /data/yudao
docker compose down
mv data/mysql data/mysql.bak.$(date +%s)   # 先留档，别直接删
docker compose up -d
```

---

## 回归测试

### 两种运行模式（同一套脚本，用环境变量换目标）

| 模式 | 后端/前端在哪 | 一条命令 |
|---|---|---|
| **服务器模式（当前，2026-09-28 起在 192.168.0.119）** | 都在 `192.168.0.119`（systemd `yudao-server` + `yudao-ui`，端口 48080 / 8081） | `bash deploy/resume-verification-remote.sh` |
| 本机栈（兜底） | 本机容器 + 本机 jar（内存够用时用） | `LOCAL=1 bash deploy/resume-verification.sh` |

单跑一个模块时用环境变量指定目标：

```bash
ZENTAO_API_BASE=http://192.168.0.119:48080/admin-api bash deploy/test-entry-module.sh
ZENTAO_UI_BASE=http://192.168.0.119:8081 ZENTAO_API_BASE=http://192.168.0.119:48080/admin-api \
  node deploy/ui-check/entry.mjs
```

直连查库的断言认 `MYSQL_TARGET=local|remote|auto`（`_mysql.sh`）；服务器模式下本机容器已停，
`auto` 会自动落到 `remote`（走 ssh，SQL 用 base64 传，避开远端 shell 的反引号展开）。

### 接口回归脚本（**只依赖 curl + python3**，不需要装任何东西）

```bash
bash deploy/test-execution-module.sh    # 执行模块            34 项断言
bash deploy/test-branch-module.sh       # 分支/平台           38 项断言
bash deploy/test-module-module.sh       # 模块树              55 项断言
bash deploy/test-module-integration.sh  # 模块/分支 × 需求/任务/缺陷 联调  31 项断言
bash deploy/test-productplan-module.sh  # 产品计划            76 项断言
bash deploy/test-build-module.sh       # 构建                62 项断言
bash deploy/test-release-module.sh     # 发布                67 项断言
bash deploy/test-projectstory-module.sh # 项目需求范围        37 项断言
bash deploy/test-stage-module.sh        # 阶段（瀑布流程）    30 项断言
bash deploy/test-organization-module.sh # 组织与权限视图      16 项断言
bash deploy/test-file-module.sh         # 附件（上传/下载/gid 绑定） 30 项断言
bash deploy/test-doc-module.sh          # 文档（库/章节/版本链/草稿位） 59 项断言
bash deploy/test-testcase-module.sh     # 测试用例（步骤/版本规则/评审/待确认） 42 项断言
bash deploy/test-testtask-module.sh     # 测试单（用例编排/执行/三处回写/状态机/建缺陷） 54 项断言
bash deploy/test-report-module.sh       # 测试报告 + 用例集（汇总规则/幂等/删除保护） 26 项断言
bash deploy/test-storytree-module.sh    # 父子需求（分解/聚合/状态级联/版本冻结） 22 项断言
bash deploy/test-storytask-module.sh    # 需求转任务（需求版本冻结/已变更提示） 19 项断言
bash deploy/test-effort-module.sh       # 工时明细（流水/重算规则/汇总/悬空引用） 47 项断言
bash deploy/test-program-module.sh      # 项目集（三角色隔离/逗号 path/移动子树/删除保护） 55 项断言
bash deploy/test-todo-my-module.sh      # 待办 + 我的地盘（个人清单口径/私有/挪到今天/聚合自洽） 59 项断言
bash deploy/test-team-module.sh         # 团队（成员表/可用工时/全量保存/执行层级口径） 33 项断言 ✅
bash deploy/test-quality-chain.sh        # 质量链（缺陷解决→构建/发布清单自动回写） 25 项断言 ✅
bash deploy/test-stakeholder-module.sh   # 干系人（内部/外部/关键/批量） 23 项断言 ✅
bash deploy/test-project-view-module.sh  # 项目视角四模块（工作量估算/计划/构建/发布视图） 20 项断言 ✅
bash deploy/test-storytype-module.sh      # 需求分层（类型字典/类型数量/分层树/类型过滤/父子层级规则/排序） 35 项断言 ✅
bash deploy/test-caselib-module.sh        # 用例库（共用表区分/库内用例/产品→库导入/来源版本冻结） 29 项断言 ✅
bash deploy/test-kanban-module.sh         # 看板（七层聚合/默认布局/WIP 规则/卡片移动/物理删除与级联） 45 项断言 ✅
bash deploy/test-metric-module.sh         # 度量（定义/字典/计算与清旧数据/交叉校验/未迁移口径报错/数据查询） 29 项断言 ✅
bash deploy/test-bi-module.sh             # BI（数据视图/SQL 白名单八条/图表聚合与过滤器/版本） 37 项断言 ✅
bash deploy/test-action-module.sh         # 操作日志（回收站/还原/隐藏/动态/备注/动作渲染） 30 项断言 ✅
bash deploy/test-annual-report-module.sh # 报表（年度数据/每日提醒/产出统计） 39 项断言 ✅
bash deploy/test-burn-module.sh          # 执行燃尽图（快照/补齐/理想线/延期段/采样） 32 项断言 ✅
bash deploy/test-qa-module.sh            # 测试仪表盘（质量统计口径/三个列表块/按产品过滤） 25 项断言 ✅
bash deploy/test-repo-module.sh          # 代码库（仓库 CRUD/真实 git 同步/提交与对象关联） 37 项断言 ✅
bash deploy/test-holiday-module.sh       # 节假日（假期/补班/工作日口径/燃尽图横轴） 31 项断言 ✅
bash deploy/test-entry-module.sh         # 应用接入（校验链/两种签名/防重放/IP 六态/调用日志/清理） 67 项断言 ✅
bash deploy/test-company-module.sh       # 公司信息（本公司/唯一性/http:// 归一/外部公司/超管口径对照） ✅
bash deploy/test-score-module.sh         # 积分（规则表/次数与时间窗/四条特例/总分/与 entry 联调） ✅
bash deploy/test-search-module.sh        # 保存查询（条件 JSON/快捷方式/越权保护/拼音码表/全文检索边界） ✅
bash deploy/test-dimension-module.sh     # 维度（可见性过滤/末次维度四级兜底链/1.5 级导航下拉）
bash deploy/test-api-module.sh           # 接口文档库（接口/结构/发布版本快照）
bash deploy/test-webhook-module.sh       # Webhook（白名单/字段映射/mock 端到端/失败只落日志）
```

> 合计 **43 个脚本 / 1704 项断言**，已在**远端服务器 `192.168.0.119`**（真实 MySQL 8.0 + Redis + 后端 + 前端）上完整跑通
> （本机栈 `deploy/local-stack` 是远端不可达时的替代路径，同一份 SQL、同一批脚本），
> 一键全量：`MYSQL_TARGET=direct bash deploy/resume-verification-remote.sh`。
> 浏览器检查 21 个页面 + 团队/干系人/需求分层/用例库/看板/度量/BI/回收站/我的地盘/报表/燃尽图/测试仪表盘/代码库/节假日/应用接入/公司信息/积分/保存查询/维度/接口文档库/Webhook 专项也全绿。
>
浏览器端验证脚本在 `deploy/ui-check/` 下，用 Playwright 驱动真实页面：

```bash
# 需要全局安装 playwright：npm i -g playwright && npx playwright install chromium
node deploy/ui-check/all-pages.mjs      # 禅道页面一次性巡检（表头数、行数、pageerror）
node deploy/ui-check/execution.mjs      # 执行页：新建 + 状态流转 + 跳任务
node deploy/ui-check/branch.mjs         # 分支页：主干行、设为默认、平台文案
node deploy/ui-check/module.mjs         # 模块树页：树形渲染、上级模块选择
node deploy/ui-check/story-filter.mjs   # 需求列表按分支/模块（含子树）过滤
node deploy/ui-check/plan.mjs           # 计划页：待定/父子标记、新建弹窗、关联需求抽屉
node deploy/ui-check/build.mjs          # 构建页：集成构建、关联抽屉、缺陷解决版本下拉
node deploy/ui-check/release.mjs        # 发布页：新建弹窗、发布清单抽屉（三个 Tab）
node deploy/ui-check/projectstory.mjs   # 项目需求页：关联产品、需求范围、版本变更标记
node deploy/ui-check/stage.mjs          # 阶段页：阶段模板、占比提示、项目阶段
node deploy/ui-check/organization.mjs   # 组织与权限页：用户/权限包/部门树/映射对照
node deploy/ui-check/file.mjs           # 附件：需求详情抽屉里的「附件」Tab（渲染/上传/下载/删除）
node deploy/ui-check/doc.mjs            # 文档页：章节树过滤、详情抽屉、版本历史回读、新建章节弹窗
node deploy/ui-check/testcase.mjs       # 用例页：状态过滤、详情步骤层级编号、版本回读、步骤编辑器
node deploy/ui-check/storytree.mjs      # 需求树：父标记、子需求 Tab、分解子需求、需求转任务
node deploy/ui-check/effort.mjs         # 工时页 + 任务页「工时」抽屉：登记/汇总/删除后重算
node deploy/ui-check/program.mjs        # 项目集页：树渲染、详情抽屉（项目/产品）、新建弹窗、项目页的「所属项目集」列
node deploy/ui-check/my.mjs             # 我的地盘：概览卡片与列表交叉校验、待办完成流转、范围切换、我的动态
node deploy/ui-check/team.mjs           # 团队抽屉（项目/执行）：成员渲染、可用工时、添加/改/移除
node deploy/ui-check/stakeholder.mjs    # 干系人抽屉（项目集/项目）：内部外部、关键标记、外部人员添加/移除
node deploy/ui-check/storytype.mjs      # 需求分层：类型页签角标、分层视图三层缩进、弹窗建业务需求
node deploy/ui-check/caselib.mjs        # 用例库：库列表、库内用例、建库建用例、从产品导入、删除保护
node deploy/ui-check/kanban.mjs         # 看板：网格渲染、默认布局、建卡片、移动、完成
node deploy/ui-check/metric.mjs         # 度量：概览卡片、度量项列表、口径定义、计算、数据表
node deploy/ui-check/bi.mjs             # BI：数据视图列表、预览、试跑、白名单拦截、图表渲染
node deploy/ui-check/action.mjs         # 回收站：列表、还原、隐藏、动态渲染
node deploy/ui-check/my-workspace.mjs   # 我的地盘第二组：我参与的项目/执行/团队/测试单/用例/文档/日历
node deploy/ui-check/report.mjs         # 报表：年度数据、贡献与雷达、月度趋势、每日提醒、产出统计
node deploy/ui-check/burn.mjs           # 燃尽图：抽屉、三条线、重新计算、含周末切换
node deploy/ui-check/qa.mjs             # 测试仪表盘：汇总卡片、按产品质量统计、列表块、按产品过滤
node deploy/ui-check/repo.mjs           # 代码库：同步真实 git 仓库、提交抽屉、提交详情与对象关联
node deploy/ui-check/holiday.mjs        # 节假日：假期/补班列表、新增弹窗、工作日试算、删除
node deploy/ui-check/entry.mjs          # 应用接入：列表不露 gitfox、无限制开关、签名助手、403/405、调用日志
node deploy/ui-check/company.mjs        # 公司信息：本公司/编辑弹窗/http:// 归一/外部公司/超管口径对照/复用组织与动态
node deploy/ui-check/score.mjs          # 积分：概览/记录/38 条规则/计分试算（含未知规则报错）
node deploy/ui-check/search.mjs         # 保存查询：列表/弹窗/快捷方式切换/拼音码表/边界说明
node deploy/ui-check/dimension.mjs      # 维度：可见性过滤/末次维度兜底链/1.5 级导航下拉
node deploy/ui-check/api.mjs            # 接口文档库：接口列表/详情/结构/发布版本
node deploy/ui-check/webhook.mjs        # Webhook：列表/新建弹窗/mock 端到端/发送日志
```

> 登录逻辑抽到了 `ui-check/_login.mjs`：等按钮可用 → 点击 → 最多重试 3 轮、URL 等待 90s。
> 远端 MySQL/Redis 的网络抖动会让登录和后端聚合接口慢到 20s+，
> 固定 sleep + 短超时会把「慢」误报成「坏」（详见docs/IMPLEMENTATION-NOTES.md 5.3.1）。
>
> 所有脚本都在异常退出时 `b.close()`。**不要去掉它** —— 每失败一次留一个 headless Chromium，
> 攒到几十个之后机器负载升高，后面的检查会连锁失败（实测攒到 31 个）。

脚本里的截图会写在 `/tmp/zentao-*.png`，可直接打开查看。

### 服务器模式的环境前提

| 前提 | 为什么 | 怎么办 |
|---|---|---|
| 后端所在机器要有 **git ≥ 1.8.5** | 代码库（repo）模块会跑 `git -C <path> log`；CentOS 7 自带的 git 1.8.3 **不支持 `-C`** | 服务器上已装 git 2.43.0（`/opt/git` → `/usr/local/bin/git`；编译脚本 `/opt/src/build-git.sh`） |
| 前端是 vite dev server（8081），不是静态包 | 本 checkout 的 `vite build` 跑不通（yudao 自带的 oa/attendance 引用了一个不存在的模块） | 见docs/IMPLEMENTATION-NOTES.md 5.0；改前端 = 同步 `src/` 到 `/data/yudao/frontend` 后 `systemctl restart yudao-ui` |
| 本机到 192.168.0.0/24 走代理，偶发断连 | 一次断连会把整个脚本的断言全打成红 | 全量脚本对每个 test/ui-check 都「失败重跑一次」；查库断言走 `MYSQL_TARGET=direct` 直连 3307（0.7s/次） |

### 新增一张禅道表之后必跑的三个预检

```bash
bash deploy/check-reserved-columns.sh   # MySQL 保留字列（要加反引号）
bash deploy/check-camel-columns.sh      # 驼峰列名漏 @TableField（MP 会拼成下划线）
bash deploy/check-tenant-ignore.sh      # 新表没登记进 yudao.tenant.ignore-tables（漏了会 500：Unknown column 'tenant_id'）
# 三个脚本都跟随 `_mysql.sh` 选目标：默认先探远端、探不到自动落到本机栈；
# 也可以用 MYSQL_TARGET=local|remote 强制。取不到列清单会直接以非 0 退出 ——
# 早期版本写死远端 ssh，远端不可达时列清单是空文件，脚本照样打印「OK：0 张表」，
# 把「连不上库」伪装成「检查通过」（和坑位 #42 同一类问题）。
```

- `check-reserved-columns.sh` 查 `information_schema.KEYWORDS`（`reserved=1`），
  一条 SQL 拿全量保留字列，再真实执行一次 `SELECT 全列 FROM 表` 兜底。
  **早期版本是「拼 SELECT 试跑、靠报错定位」，这个做法有盲区**：
  MySQL 一条语句只报第一个语法错，所以每张表只能看到第一个保留字 ——
  `zt_doc` 就这么漏掉了藏在 `order` 后面的 `groups`。
- `check-camel-columns.sh` 静态比对 DO 字段与真实列名，
  专治「`baseUrl` 被 MP 拼成 `base_url`」这一类问题（第 1 条坑，每张表都会遇到）。

- `check-tenant-ignore.sh` 比对「SQL 里建出来的 zt_ 表」与 `application.yaml` 的 `ignore-tables`：
  yudao 的租户插件会往每条 SQL 里塞 `tenant_id = ?`，漏登记的表**启动不报错、一查就 500**，
  日志里只有一句 `Unknown column 'tenant_id' in 'where clause'`（`zt_score` 这一轮就漏了）。

三个脚本覆盖的是「MySQL 保留字」「驼峰列名」和「租户忽略表」；**JSqlParser 那一份保留字清单只能在
应用层暴露，所以新增表之后还要真的调一次列表接口**（详见docs/IMPLEMENTATION-NOTES.md 第四节第 10、11、20、27 条）。

### 模块级审计：还剩哪些「能做」、哪些「不用做」

```bash
bash deploy/audit-module-feasibility.sh                 # 打印到屏幕（只读，不连服务器，bash 3.2 可跑）
bash deploy/audit-module-feasibility.sh > docs/audit-output.txt   # 复现 docs/audit-output.txt
```

它以 **121 个候选**为全集（`module/` 下 99 个目录 + `config/zentaopms.php` 的 `$config->programPriv->scrum/waterfall`
点名但目录缺失的 22 个），逐个模块输出一行 CSV：PHP 行数 / action 数 / 目录是否存在 / 引用的自有表名 /
**该表是否真被 `db/zentao.sql` 建出来** / `edition != 'open'` 出现文件数 / 是否依赖外部系统 / 判定，
最后打印「各判定各多少个模块」的汇总。结论与逐条证据见 `docs/MODULE-FEASIBILITY-AUDIT.md`。

两个容易踩的匹配坑（脚本本身就是被这两个坑逼出来的）：
1. **表名 ≠ 模块名**：`config/zentaopms.php` 里 23 个表常量是硬编码 **`ops_` 前缀**
   （`space`→`ops_space`、`repo`→`ops_repo`、`codescan`→`ops_scan_*`），
   还有 `weekly`→`zt_weeklyreport`、`personnel`→`zt_userview`、`mail`/`message`→`zt_notify`、
   `setting`→`zt_config`、`programplan`→`zt_projectspec` —— 只 grep `zt_<模块名>` 会全漏；
2. **建表语句有两种写法**：带 `IF NOT EXISTS` 与不带（`zt_deliverable` 就没写），
   只用带 `IF NOT EXISTS` 的模式去 grep 会把「已发布」误判成「未发布」，且匹配时要加反引号做表名边界。

### 关系表（成员、关联）不要用逻辑删除

`zt_team` 这种「在/不在」的关系表禅道是**物理增删**的：如果用 `BaseDO` 的逻辑删除，
移除成员后再添加同一个人会撞 `UNIQUE(root,type,account)`（坑位 #4 同款）。
所以 `TeamDO` 不继承 `BaseDO`，删除用 `@Delete` 原生 SQL（`deploy/check-camel-columns.sh` 也认这种 DO）。

### 「聚合状态」与「库里的枚举值」要分开

接口上可以暴露 `undone`（未完成）这种**聚合状态**，但落到 SQL 必须翻译成条件 ——
`status = 'undone'` 一条都查不到（`zt_todo.status` 只有 wait/doing/done/closed）。
同一个语义在两个查询方法里各写一遍，迟早会漏一个（坑位 #39）。

### 共用表（zt_project 这种三角色表）移动层级时的坑

- **path/grade 的口径只能从禅道的「写入代码」抄**：`zt_project` 的 path 是**逗号**格式、grade 从 **1** 开始
  （`module/program/model.php#setTreePath`），不是斜杠、不是从 0 开始（坑位 #37）。
- **移动节点时，子孙的 path 要用「新父」的 path 拼**，不是「自己移动后的新 path」——
  后者会把自己那一段拼两遍（`,9001,P,P,SUB,`）。禅道 `processNode()` 用的是
  `rtrim($parent->path, ',')` + 子孙从 `,自己,` 起截取的含前导逗号的相对位置（坑位 #38）。

### 新增工时类表时的两个坑（这一轮踩的）

- **清空字段不能用 `updateById(new DO())`**：MyBatis-Plus 默认把 null 字段从 SQL 里剔除，
  一列都不会更新；必须用 `LambdaUpdateWrapper.set(字段, 值)` 显式 set。
  而 `zt_task` 的 `finishedBy` / `canceledBy` / `closedBy` / `closedReason` 是 **NOT NULL**，
  只能清成 `""`，三个时间列才置 `null`（坑位 #35）。
- **手测数组参数（日期区间）要写 `%5B0%5D`**：curl 直接写 `date[0]=` 会被 Tomcat 在进入 Spring
  之前拒掉（400 HTML），并且要加 `-g` 关掉 curl 自己的通配（坑位 #36）。

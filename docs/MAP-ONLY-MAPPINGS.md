# map-only 10 个模块的映射交付（禅道 → 本项目）

> 本文是**映射交付**，不是迁移实现。它回答一个问题：第 43 轮把 10 个模块定性为
> **map-only（只做映射、不写业务代码）** 之后，「这些模块不是漏做」这句话到底凭什么成立。
>
> 对象是 `ai`、`convert`、`admin`、`dev`、`zai`、`extension`、`cron`、`misc`、`jenkins`、`mark`
> 这 10 个模块。实测口径：`control.php` 的 `public function` 合计 **120 个**
> （其中 `ai` 与 `jenkins` 各含 1 个 `__construct`，故**可路由 action 118 个**；`jenkins` / `mark` 的可路由 action 各 0 个），
> **核心 PHP（`control` + `model` + `zen` + `tao`）合计 20,289 行**，全模块 `.php` 合计 75,833 行。
> （本文各节「核心」一律指 `control` + `model` + `zen` + `tao`，**不含** `config.php`；`config.php` 单独标注。）
>
> 一句话结论：**它们要么是设置 / 调度 / 编辑器 / 外部服务代理层，要么目标栈（yudao）已有等价设施；
> 硬搬只会把 PHP 的后台工具在 Java 里重复实现一遍** —— 0 张表、0 个接口、0 个前端页是本轮的正常交付量。
>
> ### 判定口径（全文统一用这一套措辞）
>
> 本次术语对齐后的总口径是：**凡是 yudao 侧已有等价能力的模块，一律算「不迁移」——不是「做不了」，
> 而是「不需要做」。** 每节给出一个**主判定**：
>
> | 判定 | 含义 | 必须交代什么 |
> |---|---|---|
> | **A：不迁移（yudao 现有能力替代）** | 禅道的这项能力在 yudao 侧**已经有等价设施**，重写等于重复实现 | 具体承载方：**表名、菜单 / 页面路径、接口模块**，且证据能在本仓库里指出来 |
> | **B：不迁移（部分替代，记缺口）** | 主体被 yudao 覆盖，但**有明确的残缺** | 逐条列「**缺的是什么**」，并注明「**若要，只能重写，不是迁移**」 |
> | **C：真能力缺口（yudao 也没有）** | yudao 侧**完全没有**这项能力 | 明说**需重写才能有**；并交代是否值得补、归谁补 |
>
> A / B / C **都不是「本轮要写代码」** —— 本轮的交付量仍然是 **0 张表 / 0 个接口 / 0 个前端页**。
> 判定只决定「这件事以后要不要做、做的话算重写还是算迁移」。
>
> **「本项目」= yudao 侧**，仓根为 `ruoyi-vue-pro/`（后端）、`yudao-ui-admin-vue3/`（前端）、
> `deploy/sql/`（建库与菜单）。禅道源码在仓根 `module/<name>/`。
>
> 所有数字均为本次实测，命令与关键输出逐条列在各节的「证据」里。现有文档路径约定为
> `yudao-ui-admin-vue3/src/views/...`（相对仓根）；注意 `ruoyi-vue-pro/yudao-ui/yudao-ui-admin-vue3/`
> 是一个几乎为空的副本（仅 5 个文件），**真正的 Vue3 工程在仓根的 `yudao-ui-admin-vue3/`**。

---

## `ai`

### 1. 禅道里它是什么

- **入口**：`module/ai/control.php` 共 **27 个 `public function`**（含 `__construct`，可路由 action 26 个）：
  `adminIndex`、`miniPrograms`、`editMiniProgramCategory`、`publishMiniProgram`、`unpublishMiniProgram`、
  `importMiniProgram`、`prompts`、`promptView`、`createPrompt`、`promptEdit`、`promptDelete`、
  `promptBasicInfo`、`promptAssignRole`、`promptSelectDataSource`、`promptSetInputFields`、`promptSetPurpose`、
  `promptSetInputForm`、`promptSetTargetForm`、`promptFinalize`、`promptExecute`、`promptExecutionReset`、
  `promptAudit`、`promptPublish`、`promptUnpublish`、`ajaxTestPrompt`、`roleTemplates`。
- **代码量**（`wc -l` 实测）：`control.php` 1,045 / `model.php` 4,283 / `zen.php` 32
  → **核心 5,360 行**（`config.php` 另 451 行）；全模块 `.php` **21,375 行**（其中 `lang/` 5,715、`ui/` 601）。
- **它操作的设置项 / 表**：9 个 `TABLE_AI_*` 常量，**9/9 都在开源建库脚本 `db/zentao.sql` 里**：

  | 常量 | 表名 | `db/zentao.sql` 行号 |
  |---|---|---|
  | `TABLE_AI_MODEL` | `zt_ai_model` | 14922（`CREATE TABLE IF NOT EXISTS`） |
  | `TABLE_AI_AGENT` | `zt_ai_agent` | 14940（同上） |
  | `TABLE_AI_AGENTFIELD` | `zt_ai_agentfield` | 14984（同上） |
  | `TABLE_AI_AGENTROLE` | `zt_ai_agentrole` | 15009（同上） |
  | `TABLE_AI_MINIPROGRAM` | `zt_ai_miniprogram` | 15030（同上） |
  | `TABLE_AI_MESSAGE` | `zt_ai_message` | 15065（同上） |
  | `TABLE_AI_MINIPROGRAMFIELD` | `zt_ai_miniprogramfield` | 15076（同上） |
  | `TABLE_AI_MINIPROGRAMSTAR` | `zt_ai_miniprogramstar` | 15164（同上） |
  | `TABLE_AI_ASSISTANT` | `zt_ai_assistant` | 15176（同上） |

  **唯一缺表**：`TABLE_IM_CHAT` → `zt_im_chat`，`model.php:507/533/557` 在改/停用/删模型时要同步 IM 群，
  但 `db/zentao.sql` 里 `zt_im_` 命中 **0**，`module/im` 也不在开源包内。
- **强外部依赖**：`config.php:21-24` 的 `vendorList` 需要 openai / azure / baidu 等 LLM 凭据，
  `model.php:602 makeRequest()` 用 curl 直连；没有 key 时 `hasModelsAvailable()` 为 false，模块只能停在配置页。
  另有 **17 处 `edition` 字样**，把提示词设计向导、AI 小程序市场等能力在开源版摘掉。

### 2. 本项目对应什么

**等价覆盖，而且覆盖得很完整** —— `ruoyi-vue-pro/yudao-module-ai/`：

- 192 个主源 `.java`、**14 个 Controller**：`AiApiKeyController`、`AiModelController`、`AiChatRoleController`、
  `AiToolController`、`AiChatConversationController`、`AiChatMessageController`、`AiKnowledgeController`、
  `AiKnowledgeDocumentController`、`AiKnowledgeSegmentController`、`AiWriteController`、`AiWorkflowController`、
  `AiMindMapController`、`AiImageController`、`AiMusicController`。
- 前端 `yudao-ui-admin-vue3/src/views/ai/` 下 **69 个 `.vue`**：`chat/`、`model/{apiKey,model,chatRole,tool}`、
  `knowledge/{knowledge,document,segment}`、`write/`、`workflow/`、`mindmap/`、`image/`、`music/`。
- 菜单已注册：`deploy/sql/01-ruoyi-vue-pro.sql` 里 **17 行**含 `ai/` 组件的 `system_menu`
  （AI 对话 / AI 绘画 / AI 写作 / AI 音乐 / AI 知识库 / AI 思维导图 / AI 工作流 + 管理侧 API 密钥 / 模型配置 /
  聊天角色 / 工具管理 …），字典 `ai_model_type` 6 条。

映射关系：`zt_ai_model` + `zt_ai_assistant` → `AiModelDO`/`AiApiKeyDO`；`zt_ai_agent` + `ai_agentfield` +
`ai_agentrole` → `AiChatRoleDO`（提示词角色）；`zt_ai_message` → `AiChatMessageDO`；`zt_ai_miniprogram*`
→ **不迁**（禅道 AI 小程序市场）；`zt_im_chat` → **不迁**（开源包无此表）；ZAI 侧 skills / 知识库绑定 → 见下文 `zai`。

**两处必须说清的现状（本次实测推翻了「yudao 直接就有」的乐观假设）**：

1. `yudao-module-ai` 在 **`ruoyi-vue-pro/yudao-server/pom.xml` 里被注释掉**
   （"AI 大模型相关模块。默认注释，保证编译速度"），即**后端默认不装配**。
2. **`ai_*` 的建表脚本在本仓库 0 命中**：`grep -rn "CREATE TABLE" --include=*.sql . | grep -c "ai_"` = **0**，
   `deploy/sql/01-ruoyi-vue-pro.sql`、`ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql` 里都没有
   `ai_model` / `ai_chat_role` / `ai_knowledge_segment` 等任何一张表的 DDL（只有字典与菜单 `INSERT`）。

### 3. 差距与结论

**主判定：A（不迁移，yudao 现有能力替代）。** 承载方是 `yudao-module-ai`（14 个 Controller / 69 个 Vue /
17 行 `system_menu`），表名由 DO 注解给定（`ai_model`、`ai_api_key`、`ai_chat_role`、`ai_chat_conversation`、
`ai_chat_message`、`ai_tool`、`ai_knowledge`、`ai_knowledge_document`、`ai_knowledge_segment`）、接口齐全，
属「现有能力替代」，不属重写；唯一缺的是这些表的建表脚本（见下）。

- **差距**：如果只按「AI 大模型平台层」看，能力齐备（代码 + 前端 + 菜单 + 字典）；但要在本仓库跑起来，
  缺**建表脚本**与**pom 装配**这两步 —— 这是启用配置，不是业务功能开发。
- **是否需写代码**：**本轮不写**（判定 A：启用 AI 能力是补 DDL 与装配，不是重写 `ai` 模块的业务逻辑）。若产品要求 AI 真可用，工作量是「补 `ai_*` DDL + 打开 pom 依赖」，
  与禅道 `ai` 模块的 26 个 action 没有对应关系。
- **不写代码用户会缺什么（诚实写）**：缺禅道那 15 条内置智能体（需求润色 / 一键拆用例 / 任务润色 / 需求转任务 /
  Bug 润色 / 文档润色 / Bug 转需求 / 拆分计划 / 需求评审 / 设计文档 / 原型图 / 发布新闻稿 / 立项报告 / 结项报告 /
  自动化测试脚本）的**一键入口**，以及 `zt_ai_miniprogram` 的小程序市场。前者可以后补成 yudao 的「内置聊天角色」字典，
  后者明确不迁。

### 4. 证据

```bash
# 代码量
wc -l module/ai/{control,model,zen,config}.php      # 1045 4283 32 451
find module/ai -name '*.php' | xargs wc -l | tail -1 # 21375

# action 数
grep -cE "public function " module/ai/control.php    # 27

# 表引用与建表脚本
grep -rhoE "TABLE_AI_[A-Z]+" module/ai --include=*.php | sort -u | wc -l   # 9
grep -nE "CREATE TABLE.*zt_ai_model" db/zentao.sql   # 14922:CREATE TABLE IF NOT EXISTS `zt_ai_model` (
# 其余 8 张同上，行号 14940 / 14984 / 15009 / 15030 / 15065 / 15076 / 15164 / 15176
grep -cE "CREATE TABLE.*zt_im_chat" db/zentao.sql    # 0
grep -rn "edition" module/ai --include=*.php | wc -l # 17

# yudao 侧
find ruoyi-vue-pro/yudao-module-ai/src/main/java -name '*.java' | wc -l    # 192
find ruoyi-vue-pro/yudao-module-ai/src -name '*Controller.java' | wc -l    # 14
find yudao-ui-admin-vue3/src/views/ai -name '*.vue' | wc -l                # 69
grep -c "ai/" deploy/sql/01-ruoyi-vue-pro.sql                              # 17
grep -n "yudao-module-ai" ruoyi-vue-pro/yudao-server/pom.xml               # 112（被 <!-- --> 注释）
grep -rn "CREATE TABLE" --include=*.sql . | grep -c "ai_"                  # 0
```

---

## `convert`

### 1. 禅道里它是什么

- **入口**：`module/convert/control.php` **18 个 action**：`index`、`selectSource`、`setConfig`、`setBugFree`、
  `setRedmine`、`checkConfig`、`checkBugFree`、`checkRedmine`、`execute`、`convertBugFree`、`convertRedmine`、
  `importJiraNotice`、`getNextKey`、`getBackKey`、`mapJira2Zentao`、`initJiraUser`、`importJira`、`quickImportJiraData`。
- **代码量**：`control.php` 605 / `model.php` 1,784 / `tao.php` 3,245
  → **核心 5,634 行**（`config.php` 另 208 行，另有 `converter/` 1,850 行：`bugfree1.php` 318、`bugfree2.php` 530、
  `redmine1.1.php` 826、`bugfree.php` 88、`redmine.php` 88）；全模块 `.php` **19,829 行**（`lang/` 1,277、`ui/` 699）。
- **它操作的设置项 / 表**：**它没有自己的业务表** ——
  `grep -c "zt_convert" db/zentao.sql` = **0**。它是**异构系统数据迁移向导**（BugFree 1/2、Redmine 1.1、Jira → 禅道），
  一次性把源系统的历史数据写进禅道已有的 `zt_*` 表（`zt_story`、`zt_task`、`zt_bug`、`zt_case`、`zt_action`、
  `zt_project`…，这些**都在** `db/zentao.sql`）。
- **源数据在外部系统的库里**，禅道侧只定义了常量：
  `module/convert/config.php` 里 **33 行 `define('JIRA_*')`**（全模块共 44 行 `define('JIRA_`），
  `REDMINE_TABLE_*` **47 行**、`BUGFREE_TABLE_*` **11 行**；`model.php:34 connectDB()` 用 `new dbh($params)` 直连源库，
  `model.php:1051 callJiraAPI()` 走 `commonModel::http` 打 Jira REST。
  这些源库表在开源建库脚本里 **0 命中**：`jiraissue` 0、`issuetype` 0、`buginfo` 0、`testuser` 0、
  `issues` 0、`journals` 0、`wiki_pages` 0。

### 2. 本项目对应什么

- **本项目没有对应物**。yudao 侧的数据搬运设施是 **`ruoyi-vue-pro/yudao-framework/yudao-spring-boot-starter-excel`**
  （`ExcelUtils` + 导入导出 handler），它面向 **Excel 文件**，不是「连 Jira/Redmine/BugFree 的库或 REST」。
- `ruoyi-vue-pro/yudao-module-zentao/` 里**没有任何 convert / import 服务**（537 个 java 文件、47 个 Controller 中
  没有对应的）。
- yudao 自身的建库迁移是 `deploy/sql/*.sql` 的编号脚本（`01`~`55`）与 `deploy/sql/migration/`，
  这跟「搬别人的历史数据」是两件事。

### 3. 差距与结论

**主判定：C（真能力缺口，yudao 也没有）。** 本项目**没有**任何「异构系统历史数据导入向导」，
`yudao-spring-boot-starter-excel` 面向 Excel 文件、不是连外部库；**若要，只能重写**（一次性脚本），不是迁移。
缺口本身成立，但归**实施交付层**，不进主工程。

- **差距**：整块缺失，而且**注定缺失** —— 它的源数据不在本仓库、也不在禅道的库里。
- **是否需写代码**：**本轮不写**（判定 C：要补，只能重写一个一次性迁移脚本，不是迁移）；如果客户确需搬 Jira / Redmine 历史数据，正确做法是另立一个
  **一次性独立脚本**（读源系统 DB/REST → 写 yudao 表），不建表、不做前端页、不做接口回归。
- **不写代码用户会缺什么**：缺「从 Jira/Redmine/BugFree 一键导入历史数据」的向导。
  这个缺口是真实存在的，但它属于**实施交付 / 数据迁移服务**，不属于产品功能。
  可以照抄的规则仍有参考价值：`config.php` 的 `jiraFieldControl`（14 种 customfieldtype → 控件 / 类型 / 长度）、
  `importDeafaultValue`（bug/feedback/ticket 的默认 action/reason）、`getJiraStatusList`/`getZentaoStatus`
  的状态兜底、`jiraUserMode = account|email` 的用户匹配策略。

### 4. 证据

```bash
grep -oE "public function [a-zA-Z0-9_]+" module/convert/control.php | wc -l   # 18
wc -l module/convert/{control,model,tao,config}.php                            # 605 1784 3245 208
wc -l module/convert/converter/*.php                                           # 1850 total
find module/convert -name '*.php' | xargs wc -l | tail -1                      # 19829

grep -c "zt_convert" db/zentao.sql                                             # 0
grep -cE "define\('JIRA_" module/convert/config.php                            # 33
grep -rhE "define\('REDMINE_TABLE_" module/convert --include=*.php | wc -l     # 47
grep -rhE "define\('BUGFREE_TABLE_" module/convert --include=*.php | wc -l     # 11
for t in jiraissue issuetype buginfo testuser issues journals wiki_pages; do
  echo "$t: $(grep -c "\`$t\`" db/zentao.sql)"; done                            # 全 0
grep -rn "edition" module/convert --include=*.php | wc -l                      # 40 行命中

find ruoyi-vue-pro/yudao-framework/yudao-spring-boot-starter-excel -name 'ExcelUtils.java'  # 存在
```

---

## `admin`

### 1. 禅道里它是什么

- **入口**：`module/admin/control.php` **22 个 action**：`index`、`ajaxSetZentaoData`、`safe`、`checkWeak`、`sso`、
  `setModule`、`ajaxSendCode`、`log`、`deleteLog`、`resetPWDSetting`、`tableEngine`、`metriclib`、
  `ajaxChangeTableEngine`、`ajaxGetDropMenu`、`execSqliteQueue`、`register`、`unBindCommunity`、`changeAgreeUX`、
  `getCaptcha`、`sendCode`、`planModal`、`giftPackage`。
- **代码量**：`control.php` 740 / `model.php` 516 / `zen.php` 243
  → **核心 1,499 行**（`config.php` 另 95 行）；
  全模块 `.php` **7,008 行**（`lang/` 1,536、`ui/` 1,638、`js/` 557、`css/` 88）。
- **它操作的设置项 / 表**（8 个 `TABLE_*`）：

  | 常量 | 表名 | `db/zentao.sql` |
  |---|---|---|
  | `TABLE_ACTION` | `zt_action` | 17 `CREATE TABLE IF NOT EXISTS` |
  | `TABLE_DEPT` | `zt_dept` | 774（同上） |
  | `TABLE_LOG` | `zt_log` | 1289（同上） |
  | `TABLE_METRICLIB` | `zt_metriclib` | 15338（同上） |
  | `TABLE_PROJECT` | `zt_project` | 1458（同上） |
  | `TABLE_PROJECTDELIVERABLE` | `zt_projectdeliverable` | 1591（同上） |
  | `TABLE_USER` | `zt_user` | 2272（同上） |
  | `TABLE_SQLITE_QUEUE` | `zt_sqlite_queue` | **0 —— 不在开源建库脚本** |

  它真正「设置的」是 `zt_config` 里的 `system.common.safe.*`（密码安全策略）、`system.admin.log.saveDays`
  （日志保留天数）、`system.common.*`（功能开关），以及 `zt_config` 里的后台导航树。
  `deleteLog` 清的是 `TABLE_LOG`（`zt_log`），`saveDays` 默认 30。

### 2. 本项目对应什么

**逐条映射（每条给出真实文件；没有的明确写「没有」）**：

| 禅道 action | 本项目对应 | 状态 |
|---|---|---|
| `index` | `yudao-ui-admin-vue3/src/views/Home/Index.vue`（工作台） | 等价 |
| `log` / `deleteLog` | `infra_job` + `infra_job_log`；清理 Job：`yudao-module-infra/.../job/logger/AccessLogCleanJob.java`、`ErrorLogCleanJob.java`、`job/job/JobLogCleanJob.java`；表 `infra_api_access_log` / `infra_api_error_log` | 等价（**登录日志/操作日志没有清理 Job**） |
| `setModule`（功能开关） | `infra_config`（`ConfigController`，`views/infra/config/`）+ `system_dict_data`（1366 行字典种子） | 等价 |
| `ajaxGetDropMenu` | `system/permission/MenuController.java` + 已迁的 group/user RBAC | 等价 |
| `sso` | `yudao-module-system/.../framework/justauth/` + OAuth2：`OAuth2ClientController` / `OAuth2OpenController` / `OAuth2TokenController` | 等价 |
| `safe`（密码安全设置） | 部分：登录验证码开关 = `yudao.captcha.enable`（`AdminAuthServiceImpl.java:76`，默认 true）；初始密码 = `infra_config` 的 `system.user.init-password`（`deploy/sql/01-ruoyi-vue-pro.sql:206`）；注册开关 = `system.user.register-enabled`（同文件 `:212`） | **部分等价**；密码强度（弱/中/强）、首次登录强制改密、邮件重置密码**没有** |
| `checkWeak` / `resetPWDSetting` | **没有**。全仓 `grep -rni "弱口令\|weakPassword\|password.*pattern"` 在 yudao java 里 0 命中；`UserSaveReqVO.java:71` 的 `password` 字段只有 `@NotEmpty` 类校验，没有强度规则 | **需要新做 / 目前没有** |
| `tableEngine` / `ajaxChangeTableEngine` | 不需要：yudao 的建库脚本全部 `ENGINE = InnoDB` | 环境层，不迁 |
| `metriclib`（一次性索引 DDL） | 归 `metric` 模块迁移（本仓已交付 `MetricController` + `deploy/sql/41-zt_metric.sql`） | 等价（归 metric） |
| `execSqliteQueue` | 不需要：`zt_sqlite_queue` 不在开源建库脚本 | 环境层，不迁 |
| `ajaxSetZentaoData` / `register` / `unBindCommunity` / `changeAgreeUX` / `getCaptcha` / `sendCode` / `ajaxSendCode` / `planModal` / `giftPackage` | **不迁**：全部打 `https://www.zentao.net` / `https://api.zentao.net`（zentao.net 云专属） | 删除 |

### 3. 差距与结论

**主判定：B（不迁移，部分替代，记缺口）。** 承载方是 `yudao-module-system`（用户 / 角色 / 菜单 /
`system_login_log` / `system_operate_log` / OAuth2 / aj-captcha）与 `yudao-module-infra`
（`infra_job` / `infra_job_log` / `infra_config` / `infra/druid`），22 个 action 里绝大多数有等价物；
**缺口见下，若要，只能在「系统管理」里重写，不是迁移。**

- **差距**：yudao 覆盖了「后台/运维台」的绝大多数面（用户、权限、日志、验证码、OAuth、开关、定时任务、
  DB 监控），**唯一实打实的空白是密码安全策略**：密码强度分级、弱口令扫描（含「与账号/手机/生日相同」判定）、
  首次登录强制改密、邮件重置密码开关。
- **是否需写代码**：**本轮不写**（判定 B：缺口就是「一组设置项 + 一个密码校验钩子」）。
  如果产品要，属于在 yudao「系统管理」里补一个安全设置页 + 一个密码校验器，工作量与 `admin` 的 22 个 action 无关。
- **不写代码用户会缺什么**：缺后台安全设置页与弱口令扫描报告。目前 yudao 侧只有「初始密码」和「注册开关」
  两个 `infra_config` 项，密码是否弱完全不管。

### 4. 证据

```bash
grep -oE "public function [a-zA-Z0-9_]+" module/admin/control.php | wc -l   # 22
wc -l module/admin/{control,model,zen,config}.php                            # 740 516 243 95
find module/admin -name '*.php' | xargs wc -l | tail -1                      # 7008

for t in action dept log metriclib project projectdeliverable user sqlite_queue; do
  grep -nE "CREATE TABLE( IF NOT EXISTS)? \`zt_$t\`" db/zentao.sql; done
# action 17 / dept 774 / log 1289 / metriclib 15338 / project 1458 /
# projectdeliverable 1591 / user 2272；zt_sqlite_queue 无输出（= 0）
grep -n "system.common.safe.mode" module/install/control.php   # 368: setItem('...safe.mode','1')
grep -n "saveDays" module/admin/config.php                     # 3: $config->admin->log->saveDays = 30;
grep -n "safe->weak" module/admin/config.php                   # 5: 常见弱口令串（123456,password,...）
grep -n "admin->apiRoot" module/admin/config.php               # 仅剩 https://www.zentao.net（云专属）

# yudao 侧
grep -rn "yudao.captcha.enable" ruoyi-vue-pro/yudao-module-system/src/main/java --include=*.java
# AdminAuthServiceImpl.java:76
grep -n "system.user.init-password\|system.user.register-enabled" deploy/sql/01-ruoyi-vue-pro.sql  # 206 / 212
grep -rni "弱口令\|weakPassword\|password.*pattern" ruoyi-vue-pro --include=*.java   # 0 命中
ls yudao-ui-admin-vue3/src/views/system/{loginlog,operatelog}                        # 都存在
grep -rln "implements JobHandler" ruoyi-vue-pro --include=*.java | wc -l             # 29
```

---

## `dev`

### 1. 禅道里它是什么

- **入口**：`module/dev/control.php` **6 个 action**：`api`、`restAPI`、`db`、`editor`、`langItem`、`resetLang`。
- **代码量**：`control.php` 217 / `model.php` 1,212
  → **核心 1,429 行**（`config.php` 另 298 行；**没有 `zen.php`**）；
  全模块 `.php` **5,286 行**（`lang/` 1,045、`ui/` 676、`js/` 118、`css/` 49）。
- **它操作的设置项 / 表**：只有两个常量，**都在** `db/zentao.sql`：
  `TABLE_LANG` → `zt_lang`（1275，`CREATE TABLE IF NOT EXISTS`，`langItem`/`resetLang` 写它）、
  `TABLE_WORKFLOW` → `zt_workflow`（13105，同上）。
- `api` / `restAPI` 是 **PHP `ReflectionClass` 源码自省**，把 `control.php` 的 action 反射成接口文档页面；
  `db` 是 `SHOW TABLES` + `DESC` 的表结构浏览器；`ui/editor.html.php` 只有 21 行占位，
  真正的编辑器是另一个模块 `module/editor`（`control.php:25` 在未开启时 `locate` 回 `dev/editor`）。

### 2. 本项目对应什么

| 禅道 action | 本项目对应 | 真实文件 / 菜单 |
|---|---|---|
| `api` / `restAPI` | **knife4j + springdoc**（Java 无源码反射语义，用注解式 OpenAPI） | `ruoyi-vue-pro/yudao-framework/yudao-spring-boot-starter-web/pom.xml:51` `knife4j-openapi3-jakarta-spring-boot-starter`；`yudao-dependencies/pom.xml:23` `<knife4j.version>4.5.0`，`:22` `<springdoc.version>3.0.3`；菜单 `deploy/sql/01-ruoyi-vue-pro.sql:2347` id=20「API 接口」`infra:swagger:list`；页面 `yudao-ui-admin-vue3/src/views/infra/swagger/index.vue` |
| `db`（表结构浏览） | **代码生成器的「数据库表」页** + MySQL 监控 | `infra_codegen_table`（`ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql` 有 DDL；`CodegenController.java`）；页面 `yudao-ui-admin-vue3/src/views/infra/codegen/index.vue` + `ImportTable.vue`；菜单 id=19「代码生成」`infra:codegen:query`；另 `infra/druid`（菜单 id=15「MySQL 监控」） |
| `langItem` / `resetLang` | **前端 i18n 资源** | `yudao-ui-admin-vue3/src/locales/zh-CN.ts` + `en.ts`（`grep` 全仓 `messages*.properties` **0**，后端没有 i18n 表） |
| `editor` | **不迁**：禅道的真实实现是 `module/editor`（改 PHP 源码），Java 侧无对应物也不该有 | —— |

### 3. 差距与结论

**主判定：B（不迁移，部分替代，记缺口）。** 承载方逐个可指：
`ruoyi-vue-pro/yudao-framework/yudao-spring-boot-starter-web/pom.xml:51`（knife4j）+ 菜单 id 20 `infra:swagger`
+ `views/infra/swagger/index.vue`；`infra_codegen_table` + 菜单 id 19 `infra:codegen` + `views/infra/codegen/`；
前端 `yudao-ui-admin-vue3/src/locales/{zh-CN,en}.ts`。
**缺口是「运行时可编辑的文案」与「禅道术语对照表」，若要，只能在 yudao 侧重写，不是迁移。**

- **差距**：`api` 的**语义**不可搬（源码反射 → 注解/运行时扫描），但**用途**被 knife4j 完整覆盖；
  `db` 的用途被代码生成器覆盖（禅道表结构与 yudao 表结构本就不同）；`langItem` 是「把界面文案落库」，
  yudao 用资源文件 + 字典，不需要 `zt_lang` 这种运行时可编辑的文案表；`editor` 是 PHP 专属。
- **是否需写代码**：**本轮不写**（判定 B：若要「在线改界面文案」这类缺口，是重写，不是迁移）。
- **不写代码用户会缺什么**：① 没有一个「在线改界面文案」的后台页（已有 i18n 文件，改文案要改代码/发版）；
  ② 没有「禅道术语 → 中文名」对照表（约 130 项在 `module/dev/lang/zh-cn.php` 的 `$lang->dev->tableList` 里，
  可作迁移期参考但不落库）；③ 老系统 `zt_lang` 里 `system=0` 的自定义文案不会自动带过来，
  需要一次性导出 CSV/属性文件人工映射（不建表、不做 CRUD）。

### 4. 证据

```bash
grep -oE "public function [a-zA-Z0-9_]+" module/dev/control.php | wc -l   # 6
wc -l module/dev/{control,model,config}.php                               # 217 1212 298
find module/dev -name '*.php' | xargs wc -l | tail -1                     # 5286

grep -nE "CREATE TABLE.*zt_(lang|workflow)\`" db/zentao.sql
# 1275:CREATE TABLE IF NOT EXISTS `zt_lang` (
# 13105:CREATE TABLE IF NOT EXISTS `zt_workflow` (

# yudao 侧
grep -n "knife4j" ruoyi-vue-pro/yudao-framework/yudao-spring-boot-starter-web/pom.xml   # 51
grep -n "knife4j.version\|springdoc.version" ruoyi-vue-pro/yudao-dependencies/pom.xml    # 23 / 22
grep -n "API 接口" deploy/sql/01-ruoyi-vue-pro.sql | grep system_menu                    # 2347 (id 20)
grep -n "代码生成" deploy/sql/01-ruoyi-vue-pro.sql | grep system_menu                    # 2346 (id 19)
ls yudao-ui-admin-vue3/src/views/infra/{swagger,codegen,config,druid}                     # 都存在
find . -name 'messages*.properties' | wc -l                                               # 0
ls yudao-ui-admin-vue3/src/locales/                                                       # en.ts zh-CN.ts
```

---

## `zai`

### 1. 禅道里它是什么

- **入口**：`module/zai/control.php` **7 个 action**：`setting`、`ajaxGetToken`、`vectorized`、
  `ajaxEnableVectorization`、`ajaxSyncVectorization`、`ajaxSearchKnowledges`、`ajaxGetUserAgent`。
- **代码量**：`control.php` 251 / `model.php` 2,269 → **核心 2,520 行**（`config.php` 另 3 行）；
  全模块 `.php` **8,546 行**（`lang/` 384、`ui/` 239、`js/` 310、`css/` 38）。
- **它操作的设置项 / 表**：19 个 `TABLE_*` 常量，**19/19 都能在 `db/zentao.sql` 找到建表语句**：

  | 常量 | 表名 | `db/zentao.sql` |
  |---|---|---|
  | `TABLE_AI_USERAGENT` | `zt_ai_useragent` | 15088（**`CREATE TABLE` 且无 `IF NOT EXISTS`**） |
  | `TABLE_BUG` | `zt_bug` | 有 |
  | `TABLE_BUILD` | `zt_build` | 有 |
  | `TABLE_DEMAND` / `TABLE_DEMANDSPEC` | `zt_demand` / `zt_demandspec` | 有 |
  | `TABLE_DESIGN` / `TABLE_DESIGNSPEC` | `zt_design` / `zt_designspec` | 有 |
  | `TABLE_DOC` / `TABLE_DOCCONTENT` / `TABLE_DOCLIB` | `zt_doc` / `zt_doccontent` / `zt_doclib` | 有 |
  | `TABLE_EXECUTION` | `zt_project`（别名，config 里指向 `project`） | `zt_execution` 本身 0 命中（预期） |
  | `TABLE_FEEDBACK` | `zt_feedback` | 有 |
  | `TABLE_FILE` | `zt_file` | 有 |
  | `TABLE_PRODUCT` / `TABLE_PRODUCTPLAN` | `zt_product` / `zt_productplan` | 有 |
  | `TABLE_PROJECT` | `zt_project` | 有 |
  | `TABLE_STORY` / `TABLE_STORYSPEC` | `zt_story` / `zt_storyspec` | 有 |
  | `TABLE_TASK` / `TABLE_TESTTASK` | `zt_task` / `zt_testtask` | 有 |

  它自己**只拥有 1 张表**（`zt_ai_useragent`，account → agent id 映射，唯一写库是 `model.php:223`），
  其余全是读已迁移的表。
- **强外部依赖**：`model.php:105 getSetting()` 在 host/appID/token 缺失时直接 `return null`；
  `model.php:253-328 callAPI()` 用 curl 打外部 ZAI 服务的 `/v8/memories`、`/v8/files/extract`、`/v8/agents`；
  `config.php` 的 installUrl 指向 zentao.net。**注意：`zai` 是「AI 服务接入 + 后台配置」的桥接模块**，
  不是 AI 业务本身（业务在 `ai` 里）。

### 2. 本项目对应什么

- `ruoyi-vue-pro/yudao-module-ai/` 的**知识库 RAG**：`AiKnowledgeController` + `AiKnowledgeDocumentController` +
  `AiKnowledgeSegmentController`（DO：`AiKnowledgeDO` / `AiKnowledgeDocumentDO` / `AiKnowledgeSegmentDO`），
  切分器 `service/knowledge/splitter/MarkdownQaSplitter.java`、`SemanticTextSplitter.java`；前端 `views/ai/knowledge/`。
- 用户级 agent → `AiChatConversationController` / `AiChatRoleController`（`ai_chat_role`）。
- 向量化配置 → `AiApiKeyController` / `AiModelController`（`AiPlatformEnum` 里 `AiModelTypeEnum.VECTOR` 已支持「向量」类型，
  字典 `ai_model_type` 值 5 = 向量、6 = 重排，见 `deploy/sql/01-ruoyi-vue-pro.sql:902-903`）。
- `zt_ai_useragent`（1 张表、8 列级别）**不单独迁移**。

### 3. 差距与结论

**主判定：A（不迁移，yudao 现有能力替代）。** 承载方是 `yudao-module-ai` 的
`AiKnowledgeController` / `AiKnowledgeDocumentController` / `AiKnowledgeSegmentController`
（表 `ai_knowledge` / `ai_knowledge_document` / `ai_knowledge_segment`）、`AiChatConversationController` /
`AiChatRoleController`，前端 `views/ai/knowledge/`，菜单 id 850「AI 知识库」。
**不迁的部分是外部服务本身（`/v8/*` 协议与运行时），不是能力缺口。**

- **差距**：从「RAG + 会话 + 模型接入」的能力视角，yudao 覆盖完整（但同样受 `ai` 节里那两条现状约束：
  pom 被注释、`ai_*` 无 DDL）。**不可对齐的是外部 ZAI 服务本身**：`/v8/*` 协议、`ak-/ek-` token、
  外部 agent 运行时（`execution_runtime='pi_coding_agent'`、`opencode_mode='serve'`）。
- **是否需写代码**：**本轮不写**（判定 A：承载方已在 `yudao-module-ai`，无需重写）。
- **不写代码用户会缺什么**：缺「把禅道的需求/缺陷/文档一键向量化到外部 ZAI 知识库」这条通道。
  可保留的参考：`zaiModel::$syncTables`（story/demand/bug/doc/design/feedback 6 张表转 Markdown）、
  `getFieldAliasMap`（78 行字段别名）、`convert*ToMarkdown` 系列、以及**向量化前按产品/项目/权限过滤**
  （`canViewObject`、`filterKnowledgesByPriv`）这条规则 —— 后者对 yudao 知识库的文档可见性设计有直接价值。
- **口径修正**：既有文档（`REMAINING-MODULE-VERDICTS.md`）已经把 zai 的行数从清单第 198 行的 2,907 改成 2,520，
  本次实测复核**确认 2,520**（`control.php` 251 + `model.php` 2,269）。

### 4. 证据

```bash
grep -oE "public function [a-zA-Z0-9_]+" module/zai/control.php | wc -l   # 7
wc -l module/zai/{control,model,config}.php                               # 251 2269 3
find module/zai -name '*.php' | xargs wc -l | tail -1                     # 8546
grep -nE "CREATE TABLE.*zt_ai_useragent" db/zentao.sql                   # 15088:CREATE TABLE `zt_ai_useragent` (
grep -nE "define\('TABLE_AI_USERAGENT" config/zentaopms.php               # 452

# yudao 侧
ls ruoyi-vue-pro/yudao-module-ai/src/main/java/cn/iocoder/yudao/module/ai/controller/admin/knowledge/
# AiKnowledgeController.java AiKnowledgeDocumentController.java AiKnowledgeSegmentController.java
grep -n "ai_model_type" deploy/sql/01-ruoyi-vue-pro.sql | head -8   # 898-903（含「向量」「重排」）
```

---

## `extension`

### 1. 禅道里它是什么

- **入口**：`module/extension/control.php` **11 个 action**：`browse`、`obtain`、`upload`、`install`、`uninstall`、
  `activate`、`deactivate`、`erase`、`upgrade`、`structure`、`safe`。
- **代码量**：`control.php` 399 / `model.php` 764 / `zen.php` 645
  → **核心 1,808 行**（`config.php` 另 4 行）；
  全模块 `.php` **6,179 行**（`lang/` 625、`ui/` 928、`js/` 63、`css/` 3）。
- **它操作的设置项 / 表**：只引用 **1 个**常量 `TABLE_EXTENSION` → `zt_extension`，
  **在** `db/zentao.sql:1019`（`CREATE TABLE IF NOT EXISTS`）。`zt_extuser` 虽然在开源脚本里建了，
  但模块从未引用。
- **性质**：**官方插件市场客户端 + PHP 源码覆盖安装器** —— 从 `api.zentao.net` 下载 zip，
  把包内文件覆盖进 `module/`、`www/`、`config/`，执行包内 PHP 钩子与 install/uninstall SQL。
  无 `edition` 门槛。

### 2. 本项目对应什么

- **没有运行时对应物，也不该有**。它管理的「产物」是 **PHP 源码文件本身**；
  Spring Boot 的 JAR 不能在运行时覆盖自身 class，更不能执行 PHP 钩子。
- 本项目的扩展机制是**构建期的 Maven 多模块**：`ruoyi-vue-pro/yudao-module-*` 各自打 JAR，
  由 `ruoyi-vue-pro/yudao-server/pom.xml` 用 dependency 决定装不装 —— 该 pom 里
  `yudao-module-crm` / `yudao-module-erp` / `yudao-module-ai` / `yudao-module-iot-biz` / `yudao-module-mes`
  全部**以注释形式存在**（"默认注释，保证编译速度"），这就是本项目的「插件开关」形态。
- 换句话说：禅道 `extension` 的等价物是**产品发布与模块装配流程**，不是某个页面或某张表。

### 3. 差距与结论

**主判定：C（真能力缺口，yudao 也没有）。** 本项目**没有任何运行时插件市场 / 热装机制**，
承载方是构建期的 Maven 多模块（`ruoyi-vue-pro/yudao-server/pom.xml` 的注释式依赖 = 本项目的「插件开关」）。
**若要，只能重写（等于在 Java 里造一套代码分发与热加载），不是迁移；且安全上不建议补。**

- **差距**：整块不可搬，而且是**架构性不可搬**，不是「还没做」。
- **是否需写代码**：**不写**（判定 C：若要运行时插件市场，是重写，且在编译型 Java 服务上等于远程任意代码执行，安全上也不该实现）。
- **不写代码用户会缺什么**：缺「应用市场里点一下装插件」的体验。诚实地说，这是禅道生态的特殊能力，
  yudao 侧没有等价需求；若将来要「给客户定制」，正确路径是 fork / 扩展模块后重新构建发布，而不是运行时市场。

### 4. 证据

```bash
grep -oE "public function [a-zA-Z0-9_]+" module/extension/control.php | wc -l   # 11
wc -l module/extension/{control,model,zen,config}.php                            # 399 764 645 4
find module/extension -name '*.php' | xargs wc -l | tail -1                      # 6179
grep -rhoE "TABLE_[A-Z_]+" module/extension --include=*.php | sort -u            # 只有 TABLE_EXTENSION
grep -nE "CREATE TABLE.*zt_extension" db/zentao.sql                              # 1019
grep -rn "edition" module/extension --include=*.php
# 1 处：model.php:651 只是拼 URL 查询参数（'&edition='），不是门槛
grep -rc "extuser\|EXTUSER" module/extension --include=*.php | grep -v ':0' | wc -l   # 0（zt_extuser 建了但模块不引用）

grep -n "yudao-module-ai" -B 2 -A 2 ruoyi-vue-pro/yudao-server/pom.xml           # 注释式依赖
```

---

## `cron`

### 1. 禅道里它是什么

- **入口**：`module/cron/control.php` **13 个 action**：`index`、`turnon`、`openProcess`、`create`、`edit`、
  `toggle`、`delete`、`ajaxExec`、`rrSchedule`、`rrConsume`、`schedule`、`consumeTasks`、`consumeTask`。
  （`grep` 到 13 个 `public function`；另有 2 个 `protected`：`canSchedule`、`applyExecRoles`。）
- **代码量**：`control.php` 462 / `model.php` 352 → **核心 814 行**（`config.php` 另 24 行）；
  全模块 `.php` **2,415 行**（`lang/` 294、`ui/` 379、`css/` 1）。
- **它操作的设置项 / 表**：3 个常量，**全部在** `db/zentao.sql`：

  | 常量 | 表名 | `db/zentao.sql` |
  |---|---|---|
  | `TABLE_CRON` | `zt_cron` | 722（`CREATE TABLE IF NOT EXISTS`），索引 737；**17 条种子数据在 2429-2447** |
  | `TABLE_QUEUE` | `zt_queue` | 1666（同上） |
  | `TABLE_CONFIG` | `zt_config` | 705（同上） |

- 它是**通用后台定时任务调度器**：crontab 表达式 → 写 `zt_queue` 排队 → 消费并执行
  `moduleName/methodName` 内部调用或系统命令 → 落 cron 日志并回写 `lastTime`。
  **本身没有业务语义**，全是调度基础设施 + 一个管理 CRUD 页。
- **真正跑起来依赖外部触发者**：开源版默认走浏览器驱动
  （`module/index/js/index.ui.js` 里 `window.startCron` 轮询 `cron/ajaxExec`，
  `control.php:133-172` 是常驻 AJAX 守护循环，需要一个已登录页签长期开着）；
  生产部署走 RoadRunner（`roadrunner/scheduler.php`、`consumer.php`）。
  `control.php:440` 的 `exec($task->command)` 执行系统命令，默认被 `config/config.php` 的
  `cronSystemCall=false` 关闭。

### 2. 本项目对应什么

- **表**：`infra_job`（`ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql:328`，含 27 行种子）+
  `infra_job_log`（同文件 `:383`）；调度内核是 Quartz，**11 张 `QRTZ_*` 表**在
  `deploy/sql/02-quartz.sql`（`grep -c "CREATE TABLE"` = 11）与 `ruoyi-vue-pro/sql/mysql/quartz.sql`。
- **任务处理器 SPI**：`ruoyi-vue-pro/yudao-framework/yudao-spring-boot-starter-job/src/main/java/cn/iocoder/yudao/framework/quartz/core/handler/JobHandler.java`
  与 `JobHandlerInvoker.java`；全仓 **29 个 `implements JobHandler`**。
- **前端**：`yudao-ui-admin-vue3/src/views/infra/job/`（`index.vue` / `JobDetail.vue` / `JobForm.vue` / `logger/`）；
  菜单 `deploy/sql/01-ruoyi-vue-pro.sql:2341` id=14「定时任务」`infra:job`，按钮 68/69/70（新增/修改/删除）。
- **映射**：`zt_cron` + `zt_queue` → **不建表**，对应 `infra_job` + `infra_job_log`；
  `zt_config` 里 `scheduler.execId` / `scheduler.lastTime` / `consumer.<execId>` 心跳行**作废**
  （Quartz 集群自处理选主与错失触发）；禅道的「UPDATE … WHERE status=wait AND execId=0 抢占 + `usleep` 回读复核」
  整体删除，由 Quartz 集群语义替代；`cron/ajaxExec` 的浏览器驱动与 RoadRunner 双进程模型**不重建**。

### 3. 差距与结论

**主判定：A（不迁移，yudao 现有能力替代）。** 承载方逐项可指：
表 `infra_job` / `infra_job_log`（`ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql:328` / `:383`）+
Quartz 的 11 张 `QRTZ_*`（`deploy/sql/02-quartz.sql`）+
`ruoyi-vue-pro/yudao-framework/yudao-spring-boot-starter-job/.../JobHandler.java`（SPI）+
前端 `yudao-ui-admin-vue3/src/views/infra/job/` + 菜单 id 14 `infra:job`。
调度层属**现有能力替代**；剩余的不是缺口而是「任务内容还没写」。

- **差距**：调度**基础设施**零差距（表、SPI、前端、集群语义都更完备）；真正的差别在**任务内容**：
  禅道 `zt_cron` 的 17 条种子里，**没有任何一条在 yudao 侧有对应的 JobHandler**
  （29 个实现里没有 zentao 模块的，`grep -rln "implements JobHandler"` 输出里 0 个 `zentao`）。
- **是否需写代码**：**`cron` 模块本身不写**（判定 A：调度层由 `infra_job` + Quartz 替代）；但它的 17 条种子对应的**业务 Job**是未来按需新写的对象
  （例如「删除过期日志」已由 `AccessLogCleanJob` 覆盖；「刷新产品/项目集统计」「计算度量」「更新燃尽图」
  需要在对应模块迁移时各自落一个 JobHandler）。
  其中：第 1 条空命令丢弃；第 12 条 `effort.remindNotRecord` 种子 `status='stop'`（默认停用）；
  `auditplan.ajaxCreateCycleAuditplan` 因 `auditplan` 模块不在开源包内**不可迁**。
- **不写代码用户会缺什么**：缺「禅道式的一键把 17 个内置定时任务装好」的开箱体验 —— 需要人工在
  「基础设施 → 定时任务」里按需建 Job（cron 表达式五段式可直接搬，但要折算成 Quartz 六段式/`?` 语义）。
- **附带口径**：`admin.deleteLog`（每 5 分钟清 `zt_log`）在 yudao 侧只覆盖了访问日志/错误日志/任务日志，
  **登录日志与操作日志没有清理 Job**，这一条见 `admin` 节。

### 4. 证据

```bash
grep -cE "public function " module/cron/control.php      # 13
wc -l module/cron/{control,model,config}.php              # 462 352 24
find module/cron -name '*.php' | xargs wc -l | tail -1    # 2415
grep -nE "CREATE TABLE.*zt_(cron|queue|config)\`" db/zentao.sql
# 722 zt_cron / 1666 zt_queue / 705 zt_config
sed -n '2429,2447p' db/zentao.sql | grep -c "moduleName="  # 17 条种子

# yudao 侧
grep -n "CREATE TABLE \`infra_job\`\|CREATE TABLE \`infra_job_log\`" ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql
# 328 / 383
grep -c "CREATE TABLE" deploy/sql/02-quartz.sql                                   # 11
find ruoyi-vue-pro/yudao-framework/yudao-spring-boot-starter-job -name 'JobHandler*.java'
grep -rln "implements JobHandler" ruoyi-vue-pro --include=*.java | wc -l           # 29
grep -rln "implements JobHandler" ruoyi-vue-pro --include=*.java | grep -c zentao  # 0
ls yudao-ui-admin-vue3/src/views/infra/job/                                        # index.vue JobDetail.vue JobForm.vue logger
grep -n "定时任务" deploy/sql/01-ruoyi-vue-pro.sql | grep system_menu              # 2341 (id 14)
```

---

## `misc`

### 1. 禅道里它是什么

- **入口**：`module/misc/control.php` **15 个 action**：`ping`、`phpinfo`、`about`、`checkUpdate`、
  `checkExtension`、`downNotify`、`ajaxIgnoreBrowser`、`changeLog`、`checkNetConnect`、`captcha`、
  `ajaxSetUnfoldID`、`features`、`ajaxSaveViewed`、`ajaxSendEvent`、`installEvent`。
- **代码量**：`control.php` 366 / `model.php` 474 / `zen.php` 60
  → **核心 900 行**（`config.php` 另 56 行）；
  全模块 `.php` **3,997 行**（`lang/` 2,153、`ui/` 253、`view/` 110、`js/` 112、`css/` 23）。
- **它操作的设置项 / 表**：9 个 `TABLE_*` 常量，**全部在** `db/zentao.sql`
  （`grep -cE "CREATE TABLE( IF NOT EXISTS)? \`zt_X\`"` 逐个 = 1）：
  `TABLE_BUG`→`zt_bug`、`TABLE_CASE`→`zt_case`、`TABLE_CONFIG`→`zt_config`、`TABLE_DOC`→`zt_doc`、
  `TABLE_PRODUCT`→`zt_product`、`TABLE_PROJECT`→`zt_project`、`TABLE_STORY`→`zt_story`、
  `TABLE_TASK`→`zt_task`、`TABLE_USER`→`zt_user`。**模块无自有业务表**。
- **明确存根 / 断链（实测）**：
  - `zen.php:21` `return 'hello world from hello()<br />';`（`checkExtension` 直接 echo）。
  - `control.php:34-37` `phpinfo()` 一行透传；`:218-224` `checkNetConnect()` 只 `print($status)`。
  - **孤儿 action**：`config/privilege.php:373-376` 引用的 `misc.ajaxgetclientpackage` / `ajaxgetpackagesize` /
    `ajaxsetclientconfig`，`:172` 的 `misc.qrcode`，以及 `downloadClient` ——
    在 `control.php` 里 `grep -ci "function <name>"` **全 0**（无定义），对应的
    `ui/downloadclient.html.php`、`view/getsid.html.php`、`view/links.html.php` 是无 action 可渲染的孤儿视图。
  - `model.php:94 getMetriclibRemind()` 在开源版直接 `return ''`；`getRemind()` 全仓仅被自身测试引用。
  - `control.php:344-365` 与 `model.php:400-473` 只做官网埋点外发（api.zentao.net / www.zentao.net）。

### 2. 本项目对应什么

逐条覆盖 15 个 action + 8 个 model 方法：

| 禅道 action / 方法 | 本项目对应 | 状态 |
|---|---|---|
| `captcha` | **aj-captcha**：`ruoyi-vue-pro/yudao-module-system/.../framework/captcha/`（`YudaoCaptchaConfiguration`、`RedisCaptchaServiceImpl`、`PictureWordCaptchaServiceImpl`）+ `CaptchaController`；配置 `application.yaml` 的 `aj.captcha.*`（滑动拼图/文字点选/水印/频控 12 项）+ 开关 `yudao.captcha.enable`（`AdminAuthServiceImpl.java:76`） | 等价，且更强 |
| `getTableAndStatus`（model） | `infra/druid`（菜单 id=15「MySQL 监控」，`views/infra/druid/index.vue`）+ `infra/db/DataSourceConfigController` | 等价（运维口径） |
| `getRemind`（model） | `zentao` 模块的 `report`（`ZentaoReportController` + `deploy/sql/44-zt_report.sql`）已迁，删除 | 等价 |
| `ajaxSetUnfoldID` / `ajaxIgnoreBrowser` / `ajaxSaveViewed`（前端偏好/已读） | 前端 localStorage / pinia 持久化 | 等价（**不落库**：全仓无 `user_preference` 表） |
| `ping` | 不需要：yudao 用 JWT，没有 PHP session 的「保活」概念 | 不迁 |
| `phpinfo` / `about` / `changeLog` / `features` | **没有**对应页面（Java 侧无 phpinfo 语义） | 需要新做或在运维层提供 / 不迁 |
| `downNotify`（打包桌面通知 zip 下载） | **没有对应物**（禅道桌面客户端专用） | 不迁 |
| `checkUpdate` / `checkNetConnect` / `ajaxSendEvent` / `installEvent` / `checkExtension`（`encodeStatistics`/`sendInstallEvent`/`getStatisticsForAPI`/`getLatestVersionList`） | **不迁**：全部指向禅道官网（api.zentao.net / www.zentao.net / api.zentao.pm / qucheng.com），含 pack/bin2hex 埋点编码 | 删除 |
| `getPluginRemind` / `getMetriclibRemind` | **不迁**：禅道插件市场 / 付费门槛 | 删除 |
| `checkOneClickPackage`（model） | **不迁**：扫 zentaobiz/zentaoep/zentao max 兄弟库的 `admin/123456`，与 yudao 部署形态无关 | 删除 |
| 5 个未定义 action + 3 个孤儿视图 | **不迁**（开源版本身即为断链） | 删除 |

唯一有「数据落点」的是 `ajaxSetUnfoldID` 写 `zt_config`（owner/module/section/key 条件 + json 合并语义），
本项目改用前端用户偏好，**不建 `zt_config` 等价表**。

### 3. 差距与结论

**主判定：B（不迁移，部分替代，记缺口）。** 承载方逐条可指：
aj-captcha（`yudao-module-system/.../framework/captcha/` + `application.yaml` 的 `aj.captcha.*` + 菜单 / 登录页）、
`infra/druid`（菜单 id 15）+ `infra/db/DataSourceConfigController`、前端 localStorage / pinia 偏好、
已迁的 `report`（`ZentaoReportController`）。**缺口是 3 个静态页（`about`/`changelog`/`features`）与
2 个入口（`ping`、`downNotify`），若要只能在 yudao 侧重写，不是迁移**
（另有 5 个 action 在禅道开源版本身就是断链，7 个是官网埋点 / 插件市场，不属缺口）。

- **差距**：`misc` 是「系统-管理」杂项工具箱，15 个 action 里 **5 个在开源版本身就是断链/存根**，
  约 7 个是纯官网埋点/插件市场（不该搬），剩下 3~4 个（验证码、表状态、前端偏好、提醒）yudao 侧都已经有。
- **是否需写代码**：**本轮不写**（判定 B：3 个静态页与 2 个入口若要，是重写，不是迁移）。
- **不写代码用户会缺什么**：① 没有 `about` / `changelog` / `features` 这类静态页；
  ② 没有 `ping` 保活端点（Java 侧不需要）；③ 没有「下载桌面客户端通知包」的入口（禅道桌面端专属）。
  这三点都不是业务缺失。

### 4. 证据

```bash
grep -oE "public function [a-zA-Z0-9_]+" module/misc/control.php | wc -l   # 15
wc -l module/misc/{control,model,zen,config}.php                            # 366 474 60 56
find module/misc -name '*.php' | xargs wc -l | tail -1                      # 3997
for t in bug case config doc product project story task user; do
  echo "$t: $(grep -cE "CREATE TABLE( IF NOT EXISTS)? \`zt_$t\`" db/zentao.sql)"; done   # 全 1

for n in ajaxgetclientpackage ajaxgetpackagesize ajaxsetclientconfig qrcode downloadClient; do
  echo "$n: $(grep -ci "function $n" module/misc/control.php)"; done        # 全 0
grep -n "misc.qrcode\|misc.ajaxgetclientpackage" config/privilege.php       # 172 / 373-376

# yudao 侧
ls ruoyi-vue-pro/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/framework/captcha/core/
grep -n "aj:" -A 3 ruoyi-vue-pro/yudao-server/src/main/resources/application.yaml   # 96
grep -rni "user_preference" --include=*.sql deploy/sql/ | wc -l                     # 0
```

---

## `jenkins`

### 1. 禅道里它是什么

- **入口**：`module/jenkins/control.php` **29 行，只有一个 `__construct`，0 个 action**；
  无 `view/`、无 `ui/`、无 `config/`。`module/common/lang/common.php:276` 的 `$lang->noMenuModule`
  明确把 `jenkins` 列为**无菜单模块**。
- **代码量**：`control.php` 29 / `model.php` 161 / `zen.php` 14 → **核心 204 行**；
  全模块 `.php` **746 行**（`lang/` 134）。
- **它操作的设置项 / 表**：2 个常量，**都在** `db/zentao.sql`：

  | 常量 | 表名 | `db/zentao.sql` |
  |---|---|---|
  | `TABLE_PIPELINE` | `ops_pipeline` | 15711（`CREATE TABLE IF NOT EXISTS`） |
  | `TABLE_PIPELINEEXEC` | `ops_pipeline_executions` | 15753（同上），索引 15774/15775 |

  注意：这两张表**不属于 jenkins 模块**，它只是用；表的所有者是 `pipeline`。
- **6 个 model 方法**（全是真实 `common::http` 调用，带 `CURLOPT_USERPWD`/Basic Auth，**不是存根**）：
  `getDepthJobs`、`checkParameterizedBuild`、`apiCreatePipeline`、`apiGetExecInfo`、
  `apiGetJobNumberByQueueID`、`getLogs`。
- 它是**外部 CI 服务的对接 driver**：触发参数化构建、查 job 树（`/api/json`）、
  建 job（POST + `config.xml`）、查队列（`/queue/item/{id}/api/json`，取 `executable.number`）、
  取 console 日志（`{buildNumber}/consoleText`）。**没有外部 Jenkins 服务就完全跑不起来**；
  它的注册与增删改查实际在 `module/provider`（Jenkins 作为 type 枚举），调用方只有未迁移的 `pipeline`/`ci`。

### 2. 本项目对应什么

- **本项目的代码库/流水线口径在 `zentao` 模块的 repo 相关服务里**：
  `ruoyi-vue-pro/yudao-module-zentao/src/main/java/cn/iocoder/yudao/module/zentao/service/repo/RepoService.java`
  （`createRepo` / `updateRepo` / `deleteRepo` / `getRepo` / `getRepoPage` / `getSimpleList` / `sync` /
  `getCommitPage` / `getCommit`）+ `GitLogReader.java`；
  Controller `controller/admin/repo/ZentaoRepoController.java`；前端 `yudao-ui-admin-vue3/src/views/zentao/repo/`；
  菜单与权限在 `deploy/sql/47-zt_repo.sql`（id 90159「代码库」+ 90160~90163 四个按钮）。
- **并且这条边界已经在文档里写死了**：`deploy/sql/47-zt_repo.sql:15-16` 明确写着
  「**只做「本地 Git 仓库」这一种来源**：禅道的 GitLab/Gitea/Gitea/SVN 走 `module/provider` +
  `module/gitlab` 等一整套「代码服务商」体系（**本实现未迁移**），这里只支持配置一个本地路径、用 `git log` 同步。」
  `RepoDO.java:35-37` 的 `scmType` 注释也写着「本实现只支持 git」。
- **流水线 / CI 本身**：yudao 侧**没有对应物** —— `grep -rn "jenkins\|gitlab\|gitea\|gogs"`
  在 `yudao-module-zentao` 只命中 `RepoDO.java` 一处（枚举里的字符串），
  没有 `ops_pipeline` / `ops_pipeline_executions` 的迁移，也没有 Jenkins provider 实体。

### 3. 差距与结论

**主判定：C（真能力缺口，yudao 也没有）。** 本项目**没有** CI / Jenkins 对接，也没有
`ops_pipeline` / `ops_pipeline_executions` 的迁移；`zentao` 模块只做到了「代码库」（`RepoService` /
`GitLogReader` / 菜单 id 90159）。**若要，只能重写**（Jenkins 适配器 + 流水线实体），不是迁移；
而且它归 `pipeline` / `ci` 模块，单独重写这 204 行的 driver 没有意义。

- **差距**：整块 CI 对接不存在；代码库（repo）已经迁了，但只支持本地 Git。
- **是否需写代码**：**本轮不写**（判定 C：CI 对接要补，只能重写 Jenkins 适配器 + 流水线实体，不是迁移）。
  正确动作是「映射 + 说明」：
  ① **不引入新表**（`ops_pipeline` / `ops_pipeline_executions` 归 `pipeline` 所有）；
  ② 0 个接口、0 个页面；
  ③ 登记「Jenkins 作为外部服务提供方」→ yudao 的外部服务/配置实体（对应禅道 `provider` 的 `type='Jenkins'`，
  需 account + token，baseURL 校验走 `/api/json`）；
  ④ 若将来做流水线前端，`'jenkins'` 作为 provider/engine 枚举项照抄。
- **不写代码用户会缺什么**：缺「触发 Jenkins 构建 / 看构建日志 / 把构建号回写」的完整能力。
  诚实地说这是**真实缺失**，不是「等价覆盖」；但它属于 `pipeline` / `ci` 模块的范畴
  （那两个模块本轮未定性为可迁），`jenkins` 只是它们的一个 driver，单独重写 204 行的 driver 没有意义。
- **可照抄的规则（留给未来 pipeline 迁移）**：auth 用 `base64_decode(token)` 作 `CURLOPT_USERPWD`；
  建 job 后从 Location 头正则 `!Location: .*item/(.*)/!` 取 pipeline 名；
  用 queueID 轮询 `/queue/item/{id}/api/json` 取 `executable.number`（未分配返回 0）；
  参数化构建判定靠 `config.xml` 含 `hudson.model.ParametersDefinitionProperty`；
  日志取 `{buildNumber}/consoleText` 并回写 `ops_pipeline_executions.logs`；
  递归取 job 树深度上限 4、按 `_class` 含 `.multibranch/.folder/.OrganizationFolder` 判非 job、`buildable` 为真才算 job。

### 4. 证据

```bash
wc -l module/jenkins/{control,model,zen}.php                       # 29 161 14
grep -cE "public function " module/jenkins/control.php             # 1（只有 __construct，action 0）
find module/jenkins -name '*.php' | xargs wc -l | tail -1          # 746
grep -n "function " module/jenkins/model.php                       # 6 个方法
grep -n "noMenuModule" module/common/lang/common.php               # 276（列表含 jenkins）
grep -nE "CREATE TABLE.*ops_pipeline" db/zentao.sql                # 15711 / 15753

# yudao 侧
ls ruoyi-vue-pro/yudao-module-zentao/src/main/java/cn/iocoder/yudao/module/zentao/service/repo/
# GitLogReader.java RepoService.java
sed -n '13,18p' deploy/sql/47-zt_repo.sql                          # 「只做本地 Git 仓库…provider/gitlab 未迁移」
grep -n "scmType" ruoyi-vue-pro/yudao-module-zentao/src/main/java/cn/iocoder/yudao/module/zentao/dal/dataobject/repo/RepoDO.java
# 35-37「源码管理类型，本实现只支持 git」
grep -rn "jenkins" ruoyi-vue-pro/yudao-module-zentao/src/main/java --include=*.java | wc -l   # 0
```

---

## `mark`

### 1. 禅道里它是什么

- **入口**：**没有 `control.php`**。`ls module/mark/` 只有 `model.php` 与 `test/`，
  没有 `zen.php` / `lang/` / `config/` / `view/` / `ui/` / `js/` / `css/`
  → **不可路由、0 个 action、无任何页面入口**。
- **代码量**：`model.php` **121 行**（这是模块的全部业务代码）；
  全模块 `.php` 452 行（差额来自 `test/`）。
- **它操作的设置项 / 表**：只引用 **1 个**常量 `TABLE_MARK` → `zt_mark`，
  **在** `db/zentao.sql:15431`（`CREATE TABLE IF NOT EXISTS`），索引 `idx_object(objectType,objectID)` 在 15442、
  `idx_account(account)` 在 15443。字段 8 个：`objectType` / `objectID` / `version` / `account` / `date` / `mark` / `extra` / `id`。
- **无 `edition` 门槛**：`grep -rn "edition" module/mark/` 退出码 1（0 命中）；
  `module/upgrade/config.php` 的 `$config->upgrade->openModules` 明确含 `'mark'`，证明它随开源版发布。
- **它是 `pivot` 模块的内部「已读标记」服务**：5 个 `public` 方法
  （`getNeededMarks` / `getMarks` / `isMark` / `hasMark` / `setMark`）全是真实 SQL，
  用来渲染 BI 透视表的「新 / 新版本」小红标（`mark='view'` = 看过该版本，`mark='version'` = 点开过版本）。
  **消费者只有 `pivot`**：`grep -rn "loadModel('mark')" module/` 共 **8 处**，
  6 处在 `module/pivot/{control.php:101,102; zen.php:93,143,214,216,243}`，2 处在 `module/mark/test/`。
- **已知瑕疵**：`model.php:119 return dao::isError();` —— 布尔语义反了（出错才返回 true），迁移时勿照抄。

### 2. 本项目对应什么

- **BI 已迁，标记没迁**：`ruoyi-vue-pro/yudao-module-zentao/.../service/bi/BiServiceImpl.java` +
  `controller/admin/bi/BiController.java`（dataview / chart 两套 CRUD + 预览），
  DO：`dal/dataobject/bi/DataViewDO.java`、`ChartDO.java`，SQL：`deploy/sql/42-zt_bi.sql`，
  菜单 90140「数据视图」+ 90141~90144 四个按钮。
- **但没有「已读 / 新版本」标记**：`grep -rn "zt_mark\|readMark\|hasMark\|isMark\|已读"` 在
  `yudao-module-zentao` 只命中 **1 处**，是 `ActionDO.java:91` 的 `readFlag`（列 `` `read` ``，
  `ActionServiceImpl.java:101` 初始化 0）—— 那是 **`action` 模块自己的已读位**（服务「我的地盘 / 动态」），
  与 `zt_mark` 的「账号 + 对象类型 + 对象ID + 版本」通用标记**不是一回事**；
  全仓也没有 `zt_mark` 的迁移（`grep -rn "zt_mark" deploy/sql/` = **0**）。

### 3. 差距与结论

**主判定：C（真能力缺口，yudao 也没有）。** 本项目侧**没有**任何「用户级通用对象标记 / 新版本标记」设施
（`grep -rn "zt_mark" deploy/sql/` = 0；`yudao-module-zentao` 里唯一带「已读」的是 `ActionDO.readFlag`，
那是 `action` 模块自己的单对象已读位，不是通用服务）。
**若要，只能重写** —— 但代价极小（一张极简表或一个字段），所以在需求出现前**不建表**。

- **差距**：`zt_mark` 及其服务整体未迁。但因为消费者只有 `pivot`（BI 透视表），
  而 yudao 侧 BI 是「数据视图 + 图表」的另一种形态，暂时**没有红标需求**。
- **是否需写代码**：**本轮不写**（判定 C：若要，是重写一张极简表，不是迁移）。
- **不写代码用户会缺什么**：缺 BI 界面上的「新版本」小红点。若将来要做，代价极小 ——
  一张极简的「用户已读/已查看标记」表（`objectType` / `objectID` / `version` / `account` / `mark` / `extra` / `date`，
  含 `idx_object` 与 `idx_account` 两个索引语义），或者直接在当前用户维度上存一个字段。
  在需求出现之前**不建这张表、不留空表**。

### 4. 证据

```bash
ls module/mark/                                        # model.php  test     ← 没有 control.php
wc -l module/mark/model.php                            # 121
grep -n "function " module/mark/model.php              # 5 个 public
find module/mark -name '*.php' | xargs wc -l | tail -1 # 452
grep -rn "edition" module/mark/                        # 0 命中（exit 1）
grep -nE "CREATE TABLE.*zt_mark" db/zentao.sql         # 15431（索引 15442 / 15443）
grep -rn "loadModel('mark')" module/ | wc -l           # 8（6 处 pivot + 2 处 test）

# yudao 侧
grep -rn "zt_mark" deploy/sql/ | wc -l                 # 0
grep -rn "zt_mark\|readMark\|hasMark\|isMark\|已读" \
  ruoyi-vue-pro/yudao-module-zentao/src/main/java --include=*.java | wc -l
# 1 处：ActionDO.java:91 的 readFlag（action 模块自己的已读位，不是通用标记服务）
ls ruoyi-vue-pro/yudao-module-zentao/src/main/java/cn/iocoder/yudao/module/zentao/dal/dataobject/bi/
# ChartDO.java DataViewDO.java
```

---

## 汇总表

| 模块 | 禅道代码量 | 主判定(A/B/C) | 本项目承载方 | 缺口 | 一句话结论 |
|---|---|---|---|---|---|
| `ai` | `control` 1,045 + `model` 4,283 + `zen` 32 = **核心 5,360**（`config` 451）；全模块 `.php` 21,375；`public function` 27（可路由 26） | **A** | `yudao-module-ai`（192 java / 14 Controller / 69 Vue / 17 行 `system_menu` / 字典 `ai_model_type`） | 启用前置：`ai_*` 无建表脚本、`yudao-server/pom.xml` 依赖被注释；另 15 条内置智能体与 `zt_ai_miniprogram` 市场不迁 | LLM 平台层已被 yudao-module-ai 等价覆盖（含 RAG 与 function call），属现有能力替代。 |
| `convert` | `control` 605 + `model` 1,784 + `tao` 3,245 = **核心 5,634**（`config` 208，`converter/` 1,850）；全模块 19,829；action 18 | **C** | 无（源数据在 Jira / Redmine / BugFree 的库里；`yudao-spring-boot-starter-excel` 只面向 Excel 文件） | 「从异构系统导入历史数据」整体缺失 | 需重写才能有：另立一次性独立脚本，属实施交付层，不进主工程。 |
| `admin` | `control` 740 + `model` 516 + `zen` 243 = **核心 1,499**（`config` 95）；全模块 7,008；action 22 | **B** | `yudao-module-system`（用户/角色/菜单/`system_login_log`/`system_operate_log`/OAuth2/aj-captcha）+ `yudao-module-infra`（`infra_job`/`infra_job_log`/`infra_config`/`infra/druid`） | 缺密码强度分级、弱口令扫描（含与账号/手机/生日相同）、首次登录强制改密、邮件重置密码；登录/操作日志没有清理 Job | 若要，只能在「系统管理」里重写一个安全设置页 + 密码校验器，不是迁移。 |
| `dev` | `control` 217 + `model` 1,212 = **核心 1,429**（`config` 298）；全模块 5,286；action 6 | **B** | knife4j/springdoc（菜单 id 20 `infra:swagger` + `views/infra/swagger/`）+ `infra_codegen_table`（菜单 id 19 + `views/infra/codegen/`）+ 前端 `locales/{zh-CN,en}.ts` | 缺「运行时可编辑的界面文案」（禅道 `zt_lang` + `langItem`）；缺禅道术语对照表；`zt_lang` 自定义文案不自动带过来 | 若要，只能重写（yudao 侧的后台文案编辑页），不是迁移。 |
| `zai` | `control` 251 + `model` 2,269 = **核心 2,520**（`config` 3）；全模块 8,546；action 7 | **A** | `yudao-module-ai`：`AiKnowledgeController`/`AiKnowledgeDocumentController`/`AiKnowledgeSegmentController` + `AiChatConversationController`/`AiChatRoleController`；菜单 id 850 | 外部 ZAI 服务通道（`/v8/*`、`ak-/ek-` token、外部 agent 运行时）与 `zt_ai_useragent` 1 张表不迁 | 桥接层被 yudao 知识库 RAG 替代；不迁的是外部服务本身，不是能力缺口。 |
| `extension` | `control` 399 + `model` 764 + `zen` 645 = **核心 1,808**（`config` 4）；全模块 6,179；action 11 | **C** | 无运行时对应物；等价物是构建期 Maven 多模块（`yudao-server/pom.xml` 的注释式依赖 = 本项目的插件开关） | 「运行时插件市场 + 热装源码」整体缺失 | 需重写才能有（等于造一套代码分发与热加载），且安全上不建议补。 |
| `cron` | `control` 462 + `model` 352 = **核心 814**（`config` 24）；全模块 2,415；action 13 | **A** | `infra_job` + `infra_job_log`（`ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql:328`/`:383`）+ Quartz 11 张 `QRTZ_*`（`deploy/sql/02-quartz.sql`）+ `JobHandler` SPI + `views/infra/job/` + 菜单 id 14 | 17 条种子任务对应的业务 Job 在 yudao 侧一个都没有（29 个 `implements JobHandler` 里 0 个 zentao） | 调度层属现有能力替代；差的是任务内容，在各自模块迁移时按需新写 JobHandler。 |
| `misc` | `control` 366 + `model` 474 + `zen` 60 = **核心 900**（`config` 56）；全模块 3,997；action 15 | **B** | aj-captcha（`yudao-module-system/.../framework/captcha/` + `application.yaml` 的 `aj.captcha.*`）、`infra/druid`（菜单 id 15）+ `infra/db`、前端 localStorage/pinia 偏好、已迁 `report` | 缺 `about`/`changelog`/`features` 静态页、`ping` 保活端点、桌面客户端通知包下载（`downNotify`） | 若要，只能在 yudao 侧重写这几个页面/端点，不是迁移；官网埋点与插件市场本就不该搬。 |
| `jenkins` | `control` 29 + `model` 161 + `zen` 14 = **核心 204**；全模块 746；**可路由 action 0** | **C** | 部分：`zentao` 模块 repo（`RepoService` / `GitLogReader` / 菜单 id 90159）；CI 无对应物 | 「Jenkins 对接 + 流水线实体（`ops_pipeline`/`ops_pipeline_executions`）」整体缺失 | 需重写才能有，且归 `pipeline`/`ci` 模块，单独重写这个 driver 没有意义。 |
| `mark` | `model` 121 = **核心 121**；全模块 452；**可路由 action 0** | **C** | 无（BI 已迁：`BiController` + `deploy/sql/42-zt_bi.sql` 菜单 90140-90144，但没有已读标记） | 「用户级通用对象标记 / 新版本标记」整体缺失；`zt_mark` 未迁（项目里只有 `zt_action.read` 这种单对象已读位） | 需重写才能有，但代价极小；`pivot` 的内部服务层，需求出现再加一张极简表。 |

**判定分布**：**A 3 个**（`ai`、`zai`、`cron`，yudao 现有能力替代）、
**B 3 个**（`admin`、`dev`、`misc`，部分替代并记缺口）、
**C 4 个**（`convert`、`extension`、`jenkins`、`mark`，真能力缺口，需重写才能有）。

**合计**：核心 PHP **20,289 行**（10 个模块的 `control` + `model` + `zen` + `tao`，不含 `config.php`；
`config.php` 另 1,139 行，全模块 `.php` 合计 75,833 行），
`control.php` 的 `public function` 合计 **120 个 / 可路由 action 118 个**
（`jenkins` / `mark` 的可路由 action 各 0 个）。
**0 张表 / 0 个接口 / 0 个前端页**是本轮的交付量 ——
按本次对齐的总口径：**凡是 yudao 侧已有等价能力的模块，一律算「不迁移」，不是「做不了」，而是「不需要做」**；
A / B 两类（6 个模块）都是这个意思，只有 C 的 4 个才是「yudao 也没有、要补就得重写」。
硬搬只会把 PHP 的后台工具在 Java 里重复实现一遍。

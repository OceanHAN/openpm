# 禅道模块可迁移性审计（实测重推导）

> 审计脚本：`deploy/audit-module-feasibility.sh`（只读，macOS bash 3.2 可跑，不连服务器）
> 原始输出：`docs/audit-output.txt`（由脚本重定向生成，可随时重跑复现）
> 审计日期：2026-09-17　禅道源码：`/Library/allproject/02code/zentaopms`（HEAD `ab2efdde9f`）　yudao：`/Library/allproject/02code/yudao`
> 本文件**只新建**，未修改 `docs/MIGRATION-INVENTORY.md`；要替换的文本见文末。

---

## 0. 一句话结论

`MIGRATION-INVENTORY.md` 第 131 行「不在开源版（无实现可搬）：21 个模块」**名单和计数都不准**，而且它把
「模块没代码」和「表没发布」两件完全不同的事混成了一条。实测结果是：

- **「模块目录不在开源版」是 22 个**（不是 6 个，也不是 21 个）：`$config->programPriv->scrum/waterfall`
  点名的 22 个模块在 `module/` 下没有目录。
- **「表没随开源版发布」在模块级别是 0 个**。清单里被判「表缺失」的 `space`/`weekly`/`personnel`/`artifact`/
  `mail`/`message`/`pipeline`/`ppm`/`setting`/`programplan`/`sso`/`datatable`/`pivot`/`screen`/`codescan`
  **表全都在 `db/zentao.sql` 里**——错因是只按 `CREATE TABLE IF NOT EXISTS \`zt_<模块名>\`` 去 grep，
  而禅道有 **23 个常量是硬编码 `ops_` 前缀**（`TABLE_SPACE`=`ops_space`、`TABLE_ARTIFACT`=`ops_artifact_libs`…），
  另有一批表名与模块名不同（`weekly`→`zt_weeklyreport`、`personnel`→`zt_userview`、`mail`/`message`→`zt_notify`、
  `setting`→`zt_config`、`programplan`→`zt_projectspec`、`codescan`→`ops_scan_*`、`mr` 才是真的没有 `zt_mr`）。
- **`pivot`/`screen` 的自相矛盾**：第 131 行把它们算进「无实现可搬」，第 207/215 行又写「❌ 未做 P2」。
  实测两者**代码与表都在开源版**（`module/pivot` 6,872 行、`module/screen` 4,261 行、`zt_pivot`/`zt_pivotspec`/`zt_pivotdrill`/`zt_screen` 均建表），
  正确归类是「待做（可迁移）」，不是「无实现」。
- 按**追加的 A/B/C 新口径**：真正 yudao 也没有的能力缺口（C）是 **35 个模块**；有相近能力只需补缺口（B）**18 个**；
  已被 yudao 现有能力替代或本就无需实现（A）**16 个**。

---

## 1. 审计方法与口径

脚本对**候选模块全集 121 个**逐个输出一行 CSV：
`module/` 下的 **99 个目录** + `$config->programPriv->scrum/waterfall` 点名但目录缺失的 **22 个**。
（清单标题说「99 个模块」，但只有把缺失目录的付费模块也纳入，才能复核「哪些没有实现可搬」，所以是 121。）

每行 15 列：

| 列 | 口径 | 复现命令（脚本内） |
|---|---|---|
| PHP行数 | `module/<m>/` 下全部 `*.php`，**排除 `*.html.php` 模板、排除 `test/`** | `find ... -name '*.php' ! -name '*.html.php' -not -path '*/test/*' \| xargs wc -l` |
| action数 | `control.php` 里 `public function ` 数量 | `grep -cE '^[[:space:]]*public function '` |
| 目录存在 | `module/<m>` 是否存在 | `[ -d ]` |
| 引用表 | 代码里 `TABLE_*` 常量按 `config/zentaopms.php` 的 `define` 解析成**物理表名**（含硬编码 `ops_`） | `grep -rhoE 'TABLE_[A-Z0-9_]+'` + 常量表 |
| 缺表 | 已定义常量映射出的表里，不在建库脚本的 | 见下 |
| 未定义常量 | 引用了但 `config` 里没有定义（多为外部源系统表/占位符） | — |
| 同名表在开源建库脚本 | `^(zt\|ops)_<m>$` 精确命中 | — |
| edition!=open文件数 | 含 `edition != 'open'` 的文件数 | `grep -rlE "edition[[:space:]]*!=[[:space:]]*'open'"` |
| 外部关键字文件数 | 含 `gitfox\|gitee\|码云\|jenkins\|sonarqube` 的文件数 | — |
| 外部服务调用次数 | `control/model/zen/tao.php` 里 `loadModel('gitfox'\|'jenkins'\|'gitlab')` / `commonModel::http` / `curl_*` 次数 | — |
| 判定 | 原始可迁移性（6 值，见 §3） | `classify()` |
| 主判定 / 承载方 / 缺口 | A/B/C 新口径 | 脚本内 `PRIMARY` 表，承载方均为仓库实测 |

**建表匹配口径**（这次纠错的核心）：`db/zentao.sql` 里 **两种建表写法都匹配**（带/不带 `IF NOT EXISTS`）、
表名**加反引号做边界**、**同时接受 `zt_` 与 `ops_` 两种前缀**。实测该脚本建表 **281 张**、
`TABLE_*` 常量 **272 个**（其中 **23 个**是硬编码 `ops_` 前缀）。

判定规则（`classify()`，按优先级）：

1. `module/<m>` 目录不存在 → **开源版无实现**
2. 自有表（物理名带模块名前缀，或该模块全部已定义表）在建库脚本里都没有 → **表缺失(付费版)**
3. 在 `PARTIAL_MODULES`（`metric` `bi` `common` `block`）→ **部分**
4. yudao 侧映射目录的端点注解数 > 0 → **已迁移**
5. `php ≤ 60` 且 `action = 0` → **开源版无实现（存根）**
6. 在 `EXTERNAL_MODULES`（`codescan` `gitfox` `jenkins` `gitlab` `provider` `pipeline`）→ **外部依赖不可搬**
7. 否则 → **待做**

> 注：macOS 自带 awk 用 `-v` 传中文再比较会恒真，脚本已改用 `cut + grep -Fxc` 做列匹配（脚本内有注释）。

---

## 2. 汇总数字（实测）

### 2.1 原始可迁移性判定（121 个候选）

| 判定 | 模块数 | 说明 |
|---|---|---|
| 已迁移 | **48** | yudao 侧有映射目录且端点注解 > 0 |
| 部分 | **4** | `metric` `bi` `common` `block` |
| 开源版无实现 | **23** | 22 个目录缺失 + `feedback` 存根 |
| 表缺失(付费版) | **0** | 模块级别为 0，详见 §3.2 |
| 外部依赖不可搬 | **6** | `codescan` `gitfox` `gitlab` `jenkins` `pipeline` `provider` |
| 待做 | **40** | 代码与表都在开源版、只是没做 |
| 合计 | 121 | 99 个目录 + 22 个目录缺失 |

### 2.2 主判定（A/B/C 新口径）

| 主判定 | 模块数 | 含义 |
|---|---|---|
| 已迁移 | **48** | 已有 yudao 实现（其中 `metric`/`bi`/`common`/`block` 记「部分」） |
| 部分 | **4** | `metric` `bi` `common` `block` |
| **A. 已被 yudao 替代 / 无需实现 → 不迁移** | **16** | 见 §4.1 |
| **B. 部分替代 → 不迁移但记缺口** | **18** | 见 §5.1 |
| **C. yudao 也没有 → 真能力缺口** | **35** | 见 §5.2 |

### 2.3 与清单旧数字的对照

| 项 | 清单旧值 | 本次实测 |
|---|---|---|
| 已完成模块 | 47（第 129 行） | **48**（多 `dataview`，见 §6.7） |
| 部分 | 4 | 4（一致） |
| 不在开源版 | 21（名单错） | 模块无代码 **22**；表缺失 **0** |
| 可继续迁移 | ~31（第 132 行） | 待做 **40** + 外部依赖 **6** |
| 开源建库脚本 `zt_*` 表数 | 227（第 156 行） | `CREATE TABLE` 共 **281 张**（`zt_` 231 + `ops_` 50） |

---

## 3. 原始可迁移性名单（实测）

### 3.1 「模块无代码」——开源版无实现（23 个）

**22 个 `module/` 目录缺失**（数据来源：`$config->programPriv->scrum/waterfall` 点名 ∩ 目录不存在，脚本附录 A）：

`approval` `auditplan` `budget` `cm` `durationestimation` `gapanalysis` `issue` `measrecord` `meeting`
`milestone` `mr` `nc` `opportunity` `projectchange` `projectdeliverable` `pssp` `researchplan` `researchreport`
`review` `reviewissue` `risk` `trainplan`

证据（脚本 CSV 列）：`目录存在=否`、`PHP行数=0`、`action数=0`。
其中 `approval` 按新口径是 **A**（yudao-module-bpm 整块替代），其余 21 个是 **C**。

**1 个存根**：`feedback` —— `目录存在=是`、`PHP行数=44`、`action数=0`；
`module/feedback/model.php` 的 `getFeedbackPairs()` 直接返回硬编码假数据，`zt_feedback`/`zt_feedbackview` 表在开源版但真实逻辑被 `edition != 'open'` 挡在付费版。

> **「有目录」不等于「有实现」**：`workestimation` 也只有 29 行、0 个 action，但它已被 yudao 等价实现
> （`workestimation` 映射目录 2 个端点），所以判定顺序把「yudao 已实现」放在「存根」之前 —— 这一步是脚本调过的。

### 3.2 「表未随开源版发布」——模块级 0 个（关键纠正）

脚本附录 B 实测：**没有任何有目录的模块，其自有表整体缺席建库脚本**。
只有少数**辅助表**没发布，且都是被已发布模块顺带引用的：

| 模块 | 缺席的辅助表 |
|---|---|
| `admin` | `zt_sqlite_queue` |
| `ai` | `zt_im_chat` |
| `upgrade` | `zt_dashboard` `zt_im_chat` `zt_im_chat_message_index` `zt_im_chatuser` `zt_im_message` |
| `user` | `zt_im_userdevice` |

整模块级别「连表都没有」的只有 `mr`（`zt_mr` 不存在，`TABLE_MR` 也未定义）。
另有 4 个外部源系统/占位符常量（`convert` 的 BugFree/Redmine/Jira 源表、`project` 的 `TABLE_NOT_EXISTS` 等），
**不是禅道表，不算缺失**（脚本单列「未定义常量」列）。

**被清单误判「表缺失」的模块，真实表名如下（全部实测在建库脚本里）**：

| 模块 | 清单以为的表 | 实际表 | 位置 |
|---|---|---|---|
| `space` | `zt_space` | `ops_space` / `ops_spaceuser` | `config/zentaopms.php:480`（硬编码）+ `db/zentao.sql` |
| `artifact` | `zt_artifact` | `ops_artifact_libs` / `_assets` / `_blobs` / `_groups` / `_packages` / `_versions` | `config:496-501` |
| `pipeline` | `zt_pipeline` | `ops_pipeline` / `ops_pipeline_content` / `ops_pipeline_executions` / `ops_triggers` | `config:492-495` |
| `ppm` | `zt_ppm` | `ops_ppm` / `ops_request_reviewers` | `config:482-483` |
| `provider` | — | `ops_provider` | `config`（硬编码） |
| `repo` | `zt_repo` | `ops_repo` / `ops_repohistory` / `ops_repofiles` / `ops_repobranch` | `config:484-488` |
| `weekly` | `zt_weekly` | `zt_weeklyreport` | `TABLE_WEEKLYREPORT` |
| `personnel` | `zt_personnel` | `zt_userview` | `TABLE_USERVIEW` |
| `mail` / `message` | `zt_mail` / `zt_message` | `zt_notify` | `TABLE_NOTIFY` |
| `setting` | `zt_setting` | `zt_config` | `TABLE_CONFIG` |
| `sso` | `zt_sso` | 无自有表（`TABLE_USER`/`TABLE_TASK`/`TABLE_BUG`） | — |
| `datatable` | `zt_datatable` | 无自有表（`TABLE_PRODUCT`/`TABLE_PROJECTPRODUCT`） | — |
| `programplan` | `zt_programplan` | `zt_projectspec`（+ IPD 表 `zt_deliverable`/`zt_approval*`/`zt_decision`/`zt_review` 都在） | `TABLE_PROJECTSPEC` |
| `codescan` | `zt_codescan` | `ops_scan_plans` / `ops_scan_rules` / `ops_scan_tasks` 等 17 张 | `db/zentao.sql` |
| `pivot` | — | `zt_pivot` / `zt_pivotspec` / `zt_pivotdrill` | `db/zentao.sql` |
| `screen` | — | `zt_screen` | `db/zentao.sql` |

### 3.3 「外部依赖 / 无法搬」（6 个，理由已复核）

| 模块 | 判定 | 可复核证据（脚本列） |
|---|---|---|
| `codescan` | 外部依赖不可搬 | `control.php` 构造函数第 17 行 `loadModel('gitfox')->checkHealth()`，health 失败就跳 `gitfox/installGitFox`；`control/model` 里 49 处 GitFox 调用；`外部关键字文件=8`。**表 `ops_scan_*` 其实在开源版**，所以它的理由**不是**「表缺失」 |
| `gitfox` | 外部依赖不可搬 | 模块本身就是 GitFox 商业代码扫描服务的客户端（`php=2325`、`action=4`、`外部关键字文件=55`） |
| `jenkins` | 外部依赖不可搬 | `php=338`、`action=1`、`外部关键字文件=11`，是 Jenkins API 客户端 |
| `gitlab` | 外部依赖不可搬 | `action=0`（`control.php` 已被上游删除，见 §6.6），只剩 249 行 `model.php` GitLab REST 客户端；`loadModel+curl=7` |
| `pipeline` | 外部依赖不可搬 | `php=4228`、`action=31`、`loadModel+curl=28`、`外部关键字文件=29`，驱动外部 CI |
| `provider` | 外部依赖不可搬 | `php=843`、`action=6`、`外部关键字文件=17`，代码库服务商（GitLab/Gitea/Gitee/SVN）API 客户端 |

> 这 6 个的**表大多在开源版**（`ops_pipeline*`/`ops_provider`/`ops_scan_*`），
> 「不可搬」的原因是**依赖外部系统**，与「表缺失」是两回事。

### 3.4 「待做」40 个（代码与表都在开源版）

`admin` `ai` `aiapp` `artifact` `backup` `cache` `ci` `convert` `cron` `custom` `datatable` `design` `dev`
`editor` `extension` `index` `install` `mail` `mark` `message` `misc` `personnel` `pivot` `ppm` `programplan`
`repobranchrule` `repobranchtype` `reporeviewflow` `screen` `setting` `space` `sso` `system` `transfer` `tutorial`
`upgrade` `weekly` `zahost` `zai` `zanode`

（`pivot` 与 `screen` 就在这张表里 —— 它们有 4,000+ 行真实代码和已发布的表，属于「未做 ≠ 做不了」。）

---

## 4. 主判定 A：已被 yudao 现有能力替代 / 无需实现（16 个，不迁移）

> 承载方全部在仓库里实测存在（表名见 `ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql`，页面见 `yudao-ui-admin-vue3/src/views/`，控制器见 `yudao-module-*/src/main/java/...`）。
>
> ⚠️ **「已有等价能力」≠「开箱可用」**（2026-09-18 实测补充）：这些承载方**当前一个都没有编进 jar** ——
> `ruoyi-vue-pro/yudao-server/pom.xml` 里除 `yudao-module-system` / `yudao-module-infra` / `yudao-module-zentao` 外，
> 其余模块依赖**全部是注释状态**；实测调用它们的管理端接口返回
> `{"code":501,"msg":"[CRM 模块 yudao-module-crm - 已禁用][参考 … 开启]"}`（HRM/FMS/OA/CMS 等连控制器都没有，返回 404）。
> 所以判 A / B 的正确读法是「**框架里有这个能力，装上即可用，因此不必迁移**」；真要用时是固定三步：
> ① 打开 pom 里那行依赖 → ② 执行它的建表 SQL → ③ 给角色分配菜单权限，然后重新 build。
> 侧边栏上这些「死菜单」已在 `deploy/sql/56-hide-unused-menus.sql` 里统一停用（可逆）。

| 禅道模块 | 类型 | 承载方（实测路径 / 表） | 差距 |
|---|---|---|---|
| `approval` | 替代 | **yudao-module-bpm**（Flowable）：`BpmModelController`、`BpmTaskController`、`BpmProcessInstanceController`、**`BpmProcessInstanceCopyController`（抄送）**、**`BpmUserTaskApproveMethodEnum`（或签/会签）**；页面 `src/views/bpm` | 禅道 `zt_approval*` 7 张表按对象挂审批，yudao 走流程实例；审批流能力整体由 bpm 承接 |
| `mail` | 替代 | 表 `system_mail_account` / `system_mail_template` / `system_mail_log`（建表脚本实测）；页面 `src/views/system/mail` | 无（账号/模板/日志齐） |
| `message` | 替代 | 表 `system_notify_message` / `system_notify_template` / `system_notice`；页面 `src/views/system/notify`、`src/views/system/notice` | 无 |
| `sso` | 替代 | 表 `system_oauth2_client` + `system_social_client` / `system_social_user`；页面 `src/views/system/oauth2`、`src/views/system/social` | 无 |
| `setting` | 替代 | 表 `infra_config`；页面 `src/views/infra/config` | 无 |
| `cron` | 替代 | 表 `infra_job` / `infra_job_log`（Quartz）；页面 `src/views/infra/job` | 无 |
| `system` | 替代 | **yudao-module-system**：`system_users` / `system_role` / `system_menu` / `system_dept` / `system_dict_type` | 无 |
| `admin` | 替代 | yudao-module-system + **yudao-module-infra** 后台；页面 `src/views/system`、`src/views/infra` | 无 |
| `cache` | 替代 | Redis + 页面 `src/views/infra/redis` + Spring Cache 抽象 | 禅道 `cache` 只有 341 行/2 action，是缓存管理页，无业务逻辑 |
| `editor` | 替代 | 前端组件 `yudao-ui-admin-vue3/src/components/Editor`（另有 `JsonEditor`）+ 已迁 `infra_file` | 无 |
| `transfer` | 替代 | `yudao-framework/yudao-spring-boot-starter-excel`（`ExcelUtils` / `PoiExcelUtils`） | 禅道 transfer 是对象 Excel 导入导出，能力等价 |
| `index` | 替代 | `yudao-ui-admin-vue3/src/views/Home/Index.vue` | 无 |
| `ai` | 替代 | **yudao-module-ai**：`AiChatConversationController`、`AiModelController`、`AiKnowledgeController`、`AiWorkflowController` 等 14 个控制器；页面 `src/views/ai` | 模型渠道配置可映射到 `AiModelController` |
| `install` | 无需求 | 无 Web 安装向导；`ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql` + 部署脚本初始化 | yudao 部署方式不同，不需要这个模块 |
| `tutorial` | 无需求 | 禅道在 220 处代码里插 `tutorialMode` 钩子喂假数据（`php=16434`） | 教学演示层，yudao 无此需求 |
| `mark` | 无需求 | 禅道 `mark` 是 121 行内部已读标记服务（0 个 control action，只被 `pivot` 调用） | yudao 列表页无此需求 |

---

## 5. 真能力缺口汇总（C 全表 + B 的「缺什么」）

### 5.1 判定 B：部分替代（18 个，不迁移但记缺口）

> 与 §4 同一条提醒：下表「yudao 已有的相近能力」同样**未编进当前 jar**（pom 注释、接口 501），属于「装上才有」。

| 禅道模块 | yudao 已有的相近能力（实测） | 缺的是什么（要做得重写，不是迁移） |
|---|---|---|
| `pivot` | `yudao-module-report` 引入 **jimureport 2.5.1**（`yudao-module-report/pom.xml`）+ 页面 `src/views/report/jmreport` | 禅道 `zt_pivot` 是基于 `zt_*` 表的透视表/下钻（含 `zt_pivotspec`/`zt_pivotdrill`），取数层要重写 |
| `screen` | `yudao-module-report` 的 **GoView 大屏**（`GoViewProjectController` + `src/views/report/goview`） | 大屏数据源不是禅道 `zt_*` 口径 |
| `pipeline` | 表 `infra_job`（定时任务）+ 页面 `src/views/infra/job` | 流水线/触发器/制品（`ops_pipeline*` 表在开源版）需自建或接外部 CI |
| `ppm` | **yudao-module-pms**：`PmsProjectGroupController`、`PmsProjectController`、`PmsIterationController` + 已迁 `program` | 禅道 ppm 的评审/决策（`zt_review`/`zt_decision`）无对应 |
| `programplan` | 已迁 `program`/`project`（`zt_project type='program'`）；`zt_projectspec`/`zt_deliverable` 在开源版 | IPD 交付物/评审点无实现可搬 |
| `personnel` | **yudao-module-hrm**（`HrmEmployeeController` 等 60+ 控制器）+ 已迁 `effort`/`team` | 禅道 personnel 的跨项目工时/风险视图（`zt_userview`）需基于已迁表重写 |
| `repobranchrule` | 已迁 `repo`（`zt_repo`… 实为 `ops_repo`/`ops_repohistory`/`ops_repofiles`） | 分支保护规则（`ops_branch_ruleset` 在开源版）未迁 |
| `repobranchtype` | 已迁 `repo` | 分支类型（`ops_branch_type` 在开源版）未迁 |
| `reporeviewflow` | 已迁 `repo` | 代码评审流（`ops_review_flow` 在开源版）未迁 |
| `custom` | `system_dict_type` / `system_dict_data` + `infra_codegen_table`/`infra_codegen_column` | 禅道「自定义字段 + 自定义流程」引擎（`zt_workflow*` 表在开源版但无实现可搬） |
| `datatable` | `infra_codegen_table`/`infra_codegen_column`（字段配置）+ 前端 `Table` 组件列设置 | 禅道 datatable 是**用户级列表字段可见性配置**，yudao 无等价功能 |
| `dev` | `src/views/infra/swagger`（接口调试）、`views/infra/druid`（SQL 监控）、`views/infra/codegen` | 语言项管理、数据库表结构查看器无对应（可用 IDE/DB 客户端替代） |
| `extension` | `infra_codegen`（生成新模块） | yudao 无插件市场/扩展包机制 |
| `misc` | **`CaptchaController`**（`yudao-module-system/framework/captcha`）+ infra 监控页 | `checkUpdate`/`checkExtension`/`checkNetConnect`（禅道官网连通性检查）无对应且不需要 |
| `upgrade` | 一次性执行 `sql/mysql/ruoyi-vue-pro.sql` | yudao **无 flyway/liquibase**（`pom.xml` 实测无），没有自动升级器 |
| `convert` | `yudao-spring-boot-starter-excel` 可承接文件型导入 | 异构源库（BugFree/Redmine/Jira）适配器需重写；且只需一次性执行 |
| `aiapp` | yudao-module-ai（chat/workflow/model + `src/views/ai`） | 禅道「AI 小程序/应用编排」无对应 |
| `zai` | yudao-module-ai（`AiChatConversationController`/`AiKnowledgeController`） | 禅道内 AI 助手的场景提示词（`zt_zoutput`）需重写 |

**B 类小结**：这 18 个模块**不需要从零写**，但也都**不能靠「搬迁」完成** —— 缺口集中在
「基于禅道 `zt_*` 表的取数层」（`pivot`/`screen`/`personnel`）、「CI/代码仓库增值功能」（`pipeline`/`repobranch*`/`reporeviewflow`）、
「自定义/扩展引擎」（`custom`/`datatable`/`extension`）三块。

### 5.2 判定 C：yudao 也没有 —— 真能力缺口（35 个）

> 判定方法：在 yudao 全仓库 grep `Meeting`/`WeeklyReport`/`Artifact`/`Portfolio`/`Risk` 等关键词，
> 非 zentao 模块命中均为 0；下列模块在 yudao 侧**没有任何表、页面或控制器**。

#### C-1　模块目录都不在开源版（21 个，禅道自己也没实现可搬，要做只能从零写）

| 模块 | `zt_*` 表是否随开源版发布 | 备注 |
|---|---|---|
| `auditplan` | 是 | IPD 审计计划 |
| `budget` | 是 | IPD 预算 |
| `cm` | **否** | 配置管理 |
| `durationestimation` | 是 | 工期估算（`workestimation` 只覆盖工作量/成本） |
| `gapanalysis` | 是 | 差距分析 |
| `issue` | 是 | 问题管理 |
| `measrecord` | **否** | 测量记录 |
| `meeting` | 是 | 会议管理 |
| `milestone` | **否** | 里程碑 |
| `mr` | **否**（无 `zt_mr`） | 合并请求；**唯一「代码与表都没有」的模块** |
| `nc` | 是 | 不符合项 |
| `opportunity` | 是 | 机会管理 |
| `projectchange` | 是 | 变更管理 |
| `projectdeliverable` | 是 | 交付物 |
| `pssp` | **否** | 项目特定软件过程 |
| `researchplan` | 是 | 研究计划 |
| `researchreport` | 是 | 研究报告 |
| `review` | 是 | 同行评审 |
| `reviewissue` | 是 | 评审问题 |
| `risk` | 是 | 风险管理 |
| `trainplan` | 是 | 培训计划 |

#### C-2　开源版有实现、但 yudao 无任何替代（14 个）

| 模块 | 类型 | 实测特征（脚本列） | 要做得重写什么 |
|---|---|---|---|
| `feedback` | 存量存根 | `php=44`、`action=0`、`zt_feedback*` 在开源版 | 真实实现属付费版 |
| `artifact` | devops | `php=2014`、`action=19`、`外部服务调用=19`、`ops_artifact_*` 在开源版 | 制品库 + 与仓库/流水线联动 |
| `space` | devops | `php=2218`、`action=19`、`ops_space`/`ops_spaceuser` 在开源版 | devops 空间 |
| `ci` | devops | `php=402`、`action=4`、引用 `ops_pipeline`/`zt_compile` | 持续集成构建结果回传 |
| `codescan` | 外部服务 | `php=5932`、`action=42`、`loadModel('gitfox')` 49 处 | 接 SonarQube 或自建扫描 |
| `gitfox` | 外部服务 | `php=2325`、`action=4` | GitFox 商业服务客户端 |
| `gitlab` | 外部服务 | `action=0`（control 被上游删除）、`model.php` 249 行 | GitLab 集成 |
| `jenkins` | 外部服务 | `php=338`、`action=1` | Jenkins 集成 |
| `provider` | 外部服务 | `php=843`、`action=6` | 代码库服务商接入 |
| `design` | 业务 | `php=1906`、`action=16`、`zt_designspec` 在开源版 | 设计稿/UI 规范管理 |
| `weekly` | 协作 | `php=1605`、`action=5`、`zt_weeklyreport` 在开源版 | 周报 |
| `zahost` | 自动化测试 | `php=1601`、`action=11`、`zt_host` 在开源版 | 测试主机 |
| `zanode` | 自动化测试 | `php=2859`、`action=29`、`zt_instance`/`zt_image` 在开源版 | 测试节点/虚拟机 |
| `backup` | 运维 | `php=1482`、`action=11` | DB 备份/还原（或直接用 mysqldump） |

**C 类小结**：按新口径，**禅道剩下真需要写代码的是这 35 个模块**；其中
- 21 个连禅道开源版都没实现（要做得从零写，且 16 个的表已经在开源版，可直接建表复用口径）；
- 14 个开源版有实现可参考，但要重写成 yudao 的形态（`codescan`/`gitlab`/`jenkins`/`provider`/`gitfox` 这 5 个本质是外部服务集成，属「接服务」而非「搬代码」）。

---

## 6. 与 `MIGRATION-INVENTORY.md` 的差异清单

| # | 清单位置 | 清单原说法 | 实测结论 | 错在什么 |
|---|---|---|---|---|
| 1 | 第 131 行 | 「不在开源版（无实现可搬）」**21 个**，名单为 `feedback`/`risk`/`reviewissue`/`meeting`/`approval`/`mr` + `space`/`weekly`/`personnel`/`artifact`/`mail`/`message`/`pipeline`/`ppm`/`datatable`/`setting`/`sso`/`programplan`/`pivot`/`screen`/`codescan` | 「模块目录缺失」实测 **22 个**，是另一批名字（含 `auditplan`/`budget`/`cm`/`durationestimation`/`gapanalysis`/`issue`/`measrecord`/`milestone`/`nc`/`opportunity`/`projectchange`/`projectdeliverable`/`pssp`/`researchplan`/`researchreport`/`review`/`trainplan` 等 16 个原名单没有的）；「表缺失」模块级 **0 个** | 计数错、名单错、两类理由混为一谈 |
| 2 | 第 131 行 vs 第 207/215 行 | `pivot`/`screen` 既算「无实现可搬」又标「❌ 未做 P2」 | 代码（6,872 / 4,261 行）与表（`zt_pivot`/`zt_pivotspec`/`zt_pivotdrill`/`zt_screen`）**都在开源版** → 属「待做（可迁移）」 | 自相矛盾；误把「表在前缀 `zt_` 之外/名字不同」当成「没发布」 |
| 3 | 第 164 行 | 「不在开源版（无实现可搬）」列 `feedback`+`risk`+`reviewissue`+`meeting`+`approval`+`mr`，理由统一写「模块目录本身就不在发行包里」 | `feedback` **目录存在**（44 行存根），理由应是「付费版存根」而不是「目录不在」；`risk`/`reviewissue`/`meeting`/`approval` 目录确实不在，但**表都在开源版** | 把「有目录但是存根」和「目录不存在」混成一类 |
| 4 | 第 165 行 | 「表结构不在开源版」列 `space`/`weekly`/`personnel`/`artifact`/`mail`/`message`/`pipeline`/`ppm`/`datatable`/`setting`/`sso`/`programplan`，理由「逐个 grep `CREATE TABLE IF NOT EXISTS \`zt_<module>\`` 无结果」 | **全部不成立**。`space`=`ops_space`、`artifact`=`ops_artifact_*`、`pipeline`=`ops_pipeline*`、`ppm`=`ops_ppm`、`weekly`=`zt_weeklyreport`、`personnel`=`zt_userview`、`mail`/`message`=`zt_notify`、`setting`=`zt_config`、`programplan`=`zt_projectspec`、`repo`=`ops_repo` 全在建库脚本；`datatable`/`sso` 本就没有自有表 | 一是只试 `zt_` 前缀（漏掉 23 个硬编码 `ops_` 常量），二是假设表名等于模块名（`weekly`≠`weeklyreport`），三是只试了带 `IF NOT EXISTS` 的写法 |
| 5 | 第 156 行 | 「开源版一共建了 227 张 `zt_*` 表」 | `db/zentao.sql` 共 **281 条 `CREATE TABLE`**（`zt_` 231 + `ops_` 50），两种写法都有 | 漏计 `ops_` 前缀表与非 `IF NOT EXISTS` 写法 |
| 6 | 第 113 行 | `not-possible`：`codescan`「只是外部 GitFox 服务的 UI 代理」、`gitlab`「实现被上游整包删除」、`feedback`「44 行存根」 | 三条**方向都对**，但需区分理由：`codescan` 的表 `ops_scan_*` **在开源版**（是外部依赖不是表缺失）；`gitlab` 的 `module/` 目录**仍然存在**（`model.php` 249 行 + lang，0 action），只是 control/config 被删；`feedback` 44 行存根成立 | 未把「外部依赖」与「表缺失」区分开；「整包删除」表述过强 |
| 7 | 第 129 行 + 第 238 行 | 「✅ 已完成 47」；`dataview` 行标「❌ 未做 P2」 | 实测已迁移 **48**：`dataview` 的 `zt_dataview` 有 yudao `@TableName`，且 `BiController` 有 `/dataview/create|page|get|update|delete|preview|preview-sql` 端点 → `dataview` 应算已完成 | 与第 189 行「bi 的 `zt_dataview` 已实现」自相矛盾 |
| 8 | 全文 | 第 130 行「部分 4 个」 | `metric`/`bi`/`common`/`block`，**一致** | 无 |
| 9 | 计量口径 | 第 32 行附近 PHP 行数混用两种口径（`testcase` 只数顶层 `*.php` = 9,635，`doc` 数全部 `*.php` = 14,505） | 本审计统一为「全部 `*.php` 排除模板与 `test/`」，故 `testcase`=**12,312**、`testtask`=**6,716**、`testreport`=**2,337**、`testsuite`=**1,372** | 同一张表里两种口径不可比 |
| 10 | 第 132 行 | 「可继续迁移 ~31」 | 「待做」实测 **40** 个（另 6 个「外部依赖不可搬」） | 数字偏小，且把 `gitfox`/`gitlab`/`jenkins`/`provider` 与「表与代码都在开源版」并列 |

---

## 7. 可直接粘贴的替换文本

> 以下代码块内容可直接覆盖 `docs/MIGRATION-INVENTORY.md` 对应行。本文件不改那个文件。

### 7.1 第 131 行整行（「最终分类」表里的 `❌ 不在开源版` 一行）

```markdown
| ❌ **不在开源版（无实现可搬）** | **23** | 拆成两类：① **22 个 `module/<name>` 目录不存在**（`approval`/`auditplan`/`budget`/`cm`/`durationestimation`/`gapanalysis`/`issue`/`measrecord`/`meeting`/`milestone`/`mr`/`nc`/`opportunity`/`projectchange`/`projectdeliverable`/`pssp`/`researchplan`/`researchreport`/`review`/`reviewissue`/`risk`/`trainplan`，名字取自 `config/zentaopms.php` 的 `$config->programPriv->scrum/waterfall`）；② **1 个存根** `feedback`（44 行 model、0 action、付费版才实现）。⚠️ **「模块没代码」不等于「表没发布」**：这 22 个里只有 `cm`/`measrecord`/`milestone`/`mr`/`pssp` 连表都没有，其余 17 个的 `zt_*` 表都随开源版发布（`db/zentao.sql` 带/不带 `IF NOT EXISTS` 两种写法都命中）。被本文档旧版列进这一类的 `space`/`weekly`/`personnel`/`artifact`/`mail`/`message`/`pipeline`/`ppm`/`datatable`/`setting`/`sso`/`programplan`/`pivot`/`screen`/`codescan` **表全在开源版**，属「待做」或「外部依赖」，不属此类（`module/pivot` 6,872 行 / `module/screen` 4,261 行 + `zt_pivot*`/`zt_screen` 均已建表）。详见 `docs/MODULE-FEASIBILITY-AUDIT.md` |
```

### 7.2 第 164/165 行两张表（可迁移性审计表的两行）

```markdown
| **模块目录不在开源版（无实现可搬）** | `approval`、`auditplan`、`budget`、`cm`、`durationestimation`、`gapanalysis`、`issue`、`measrecord`、`meeting`、`milestone`、`mr`、`nc`、`opportunity`、`projectchange`、`projectdeliverable`、`pssp`、`researchplan`、`researchreport`、`review`、`reviewissue`、`risk`、`trainplan`（22 个） | 数据来源：`grep -oE "programPriv->(scrum\|waterfall)[^;]*" config/zentaopms.php \| grep -oE "'[a-zA-Z0-9_]+'"` 得到名字，再逐个 `[ -d module/<name> ]` 判定；22 个的 `PHP行数=0`、`action数=0`（`deploy/audit-module-feasibility.sh` 的 `目录存在=否` 列）。其中 17 个的 `zt_*` 表**在** `db/zentao.sql`，`cm`/`measrecord`/`milestone`/`mr`/`pssp` 5 个连表都没有（`mr` 无 `zt_mr`） |
| **表未随开源版发布（模块级 0 个）** | 没有整模块级别「表缺失」的模块；只有被已发布模块顺带引用的辅助表缺席：`zt_sqlite_queue`（`admin`）、`zt_im_chat`（`ai`）、`zt_dashboard`/`zt_im_*`（`upgrade`）、`zt_im_userdevice`（`user`） | ⚠️ 旧版此行是**错的**：`space`→`ops_space`、`artifact`→`ops_artifact_libs`、`pipeline`→`ops_pipeline`、`ppm`→`ops_ppm`、`provider`→`ops_provider`、`repo`→`ops_repo`、`weekly`→`zt_weeklyreport`、`personnel`→`zt_userview`、`mail`/`message`→`zt_notify`、`setting`→`zt_config`、`programplan`→`zt_projectspec`、`codescan`→`ops_scan_*` **全部建在 `db/zentao.sql` 里**（`config/zentaopms.php` 有 23 个常量是硬编码 `ops_` 前缀，只按 `zt_<模块名>` grep 会全漏）；`datatable`/`sso` 本就没有自有表。教训：建表匹配必须**两种写法（带/不带 `IF NOT EXISTS`）+ 双前缀（`zt_`/`ops_`）+ 表名反引号边界**，且不能假设「表名 = 模块名」 |
```

### 7.3 第 113 行 `not-possible` 一条（理由修正版）

```markdown
| **not-possible** | 3 | `codescan`、`gitlab`、`feedback`（**理由已修正，且三者都不是「表缺失」**）：`codescan` 是**外部 GitFox 服务的代理**（`module/codescan/control.php:17` 构造函数第一件事就是 `loadModel('gitfox')->checkHealth()`，全模块 49 处 GitFox 调用），但它的表 `ops_scan_plans`/`ops_scan_rules`/`ops_scan_tasks` 等 17 张**都在开源版**，所以属于「外部依赖不可搬」而不是「表缺失」；`gitlab` 的 **`control.php`(1,369 行)/`config.php` 等确实被上游删除**（`git log --diff-filter=D -- module/gitlab` 有据：commit `53c5c88791`，2026-01-21「remove module.」），但 `module/gitlab/` **目录仍在**（剩 `model.php` 249 行 GitLab REST 客户端 + lang，`action=0`），说「模块目录不存在」不成立；`feedback` 是 **44 行存根**成立（`getFeedbackPairs()` 返回硬编码假数据，真实逻辑被 `edition != 'open'` 挡在付费版） |
```

### 7.4 第 129~132 行「最终分类」表里的 `❌ 不在开源版` 一行

```markdown
| ❌ **不在开源版** | **23** | 见 §7.1：22 个目录缺失 + 1 个 `feedback` 存根；**模块级「表缺失」为 0** |
```

（同表里 `❌ 可继续迁移` 一行建议同时改为 **40**：`~31` 偏小，且 `gitfox`/`gitlab`/`jenkins`/`provider` 应归入「外部依赖不可搬」6 个中的一部分。）

### 7.5 附：第 129 行 `✅ 已完成` 的修正（顺带发现）

```markdown
| ✅ **已完成** | **48** | 原写 47 漏了 `dataview`：`zt_dataview` 在 yudao 有 `@TableName`，`BiController` 有 `/dataview/create|page|get|update|delete|preview|preview-sql` 端点（第 238 行「`dataview` ❌ 未做」与第 189 行「`bi` 的 `zt_dataview` 已实现」自相矛盾） |
```

---

## 8. 没能核实 / 存疑的条目

1. **`approval` → bpm 的能力边界**：已核实 bpm 有流程模型/任务/实例/抄送/或签会签（控制器与枚举实测存在），
   但**没有逐字段核对**禅道 `zt_approval*` 7 张表的「按对象挂审批、审批节点、审批角色、免审」与 bpm 流程实例语义的差异。
   判 A 是「整块由 bpm 承接」的结论，不排除个别字段（如按对象的 `objectType/objectID` 关联）需要额外建模。
2. **`pivot`/`screen` → jimureport/GoView**：只核实了依赖与控制器存在（`yudao-module-report/pom.xml` 的 jimureport 2.5.1、
   `GoViewProjectController`），**没有评估**它们能否覆盖 `zt_pivot` 的多行多列交叉表与 `zt_pivotdrill` 下钻语义。判 B 的「缺口」描述基于此。
3. **`ppm` → yudao-module-pms**：只核对了控制器清单（`PmsProjectGroupController` 等），
   未读业务语义，不确定 pms 的「项目群/迭代」是否就是禅道 ppm 的「项目组合/评审/决策」。
4. **`ops_scan_*` 等 devops 表的真实用途**：这些表在 `db/zentao.sql` 里建了，但 `module/codescan` 代码里
   **没有任何引用**（它走 GitFox HTTP API），因此无法从开源代码确认它们由谁读写（推测是 GitFox 服务侧或 `extension/devops`）。
5. **`TABLE_TESTTASKPRODUCT` 未定义**：`module/testtask/model.php:2618` 引用了该常量，但 `config/zentaopms.php` 里没有定义
   （`zt_testtaskproduct` 表却在建库脚本里）。疑似上游 bug，未继续深究。
6. **`yudao-module-oa`**：前端有 `src/views/oa`，但 Java 侧无 `yudao-module-oa`（只有 `BpmOALeaveController`，是 bpm 的请假流）。
   我按「无 meeting 等价物」判 `meeting` 为 C，未逐字段核对 OA 相关能力。
7. **`hrm` 与 `personnel`/`weekly`**：`personnel` 判 B（hrm + 已迁 effort/team 可覆盖人员/工时），
   `weekly` 判 C（hrm 无周报控制器）—— 这两条基于控制器名的静态检索，未读实现细节。
8. 本次审计**只做静态源码/SQL 检索**：未跑构建、未连服务器、未执行任何 PHP 或 Java 代码。

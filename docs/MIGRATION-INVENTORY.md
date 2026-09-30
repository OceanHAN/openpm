# 禅道迁移清单（99 个模块）

> 自动生成自禅道源码 `module/` 目录，行数已排除 `test/` 目录。
>
> 最近更新：2026-09-17（第 45 轮：**度量快照口径修正 + 「能力替代」三分类** ——
> ① 两个红脚本收尾：`metric` 的「正好 2 倍」不是 JOIN 放大，而是 **`zt_metriclib` 的 nodate 快照跨天累积 + 读路径漏了 `date >= today`**
> （禅道 `tao.php#fetchMetricRecordsWithOption` 有这条过滤），修完 `dataCount` 也从 42 回到真值 23；`todo-my` 是脚本不可重复（已加幂等重置）；
> ② 用户新口径落地：**凡 yudao 已有等价能力 → 判「不迁移」，只写映射**，三分类 **A/B/C**，
> 新增 `docs/MAP-ONLY-MAPPINGS.md`（10 个 map-only 模块逐条映射）与 `docs/MODULE-FEASIBILITY-AUDIT.md`
> + 可复现脚本 `deploy/audit-module-feasibility.sh`（121 个候选逐个判定，分布 A 16 / B 18 / C 35）；
> ③ **纠正本清单自身多处错误**：`❌ 不在开源版` 由错写的 21 改为 **23**（22 个模块目录缺失 + `feedback` 存根，名字取自 `$config->programPriv`）；
> 「表未随开源版发布」在**模块级其实是 0 个**（`space`→`ops_space`、`artifact`→`ops_artifact_libs`、`weekly`→`zt_weeklyreport`、`personnel`→`zt_userview`、
> `mail`/`message`→`zt_notify`、`setting`→`zt_config`、`programplan`→`zt_projectspec` 全在 `db/zentao.sql`，错因是只 grep `zt_<模块名>` + 只试 `IF NOT EXISTS`）；
> `pivot`/`screen` 从「无实现」改回「待做」（表与 6,872/4,261 行代码都在）；已完成 47 → **48**（漏了 `dataview`）；开源版建表数 227 → **281**（`zt_` 231 + `ops_` 50））
>
> 第 44 轮：**`search` / `dimension` / `api` / `webhook` 四个模块已实现并验证**
> （43 个脚本 / 1704 项断言全绿，模块 43 → 47 / 47.5%，表 58 → 67）——
> `search` 完成了「保存查询 + 拼音码表」（全文检索有意不做）；`dimension` 只读维度 + 末次维度四级兜底链；
> `api` 接口文档库（5 张表全在开源版，OpenAPI 导入导出是付费扩展）；`webhook`（白名单/字段映射/失败只落日志）。
> 第 43 轮：**用 25 个子代理并行取证，把剩余 24 个模块逐个定了性**
> （4 个 do-it / 10 个 map-only / 3 个 not-possible / 7 个 not-worth），产出 `docs/REMAINING-MODULE-VERDICTS.md`；
> **并且纠正了本清单自身的两处错误**：`api` 的五张表其实都在开源版（我按直觉猜的 `zt_apilib`/`zt_apirelease` 根本不存在），
> `zt_deliverable` 也在开源版（当初用「CREATE TABLE IF NOT EXISTS」grep，而它没写 IF NOT EXISTS）。
> 第 42 轮：`score`（积分）✅ 已完成（模块 42 → 43 / 43.4%）——
> 规则驱动的计分器：38 条规则 + 扩展加成（严重程度/优先级/密码强度/执行关闭）全部照抄进 `ScoreRules.java`；
> 顺带挖出一条**死规则**（禅道 `story.close` 因 `$object = true` 短路，实际只给创建者 2 分、关闭人 0 分），
> 并把第 40 轮记下的偏离补上（entry 校验通过 → 计一次登录分）。
> 第 41 轮：`company`（公司信息，禅道界面叫「组织视图」）✅ 已完成（模块 41 → 42 / 42.4%）——
> 一张 `zt_company` + 两条禅道口径（`admins` 逗号串的超管口径对照、`id != 1` 的外部公司），
> 组织成员与组织动态分别复用 `organization` / `action` 模块；**同时纠正了这一行的错误描述** ——
> 原先写的是「公司信息单行配置，可放 system_config」，实际上它是三个入口的组织视图，不是单行配置。
> 第 40 轮：`entry`（应用接入）✅ 已完成（模块 40 → 41 / 41.4%）——
> 第三方免登录通道，规则不在 `module/entry` 里而在 `common::checkEntry/checkEntryToken/checkIP`；
> 同时**修正了两个模块的用途标注**：`api` 是「接口文档库」（不是「对外 REST 接口」）、
> `convert` 是「从 BugFree / Redmine / Jira 导入的异构系统迁移向导」（不是「数据导入导出」）。详见 IMPLEMENTATION-NOTES.md 3.43）

> 生成时间：2026-09-13

## 总体进度

| 维度 | 禅道 | 已完成 | 覆盖率 | 说明 |
|---|---|---|---|---|
| 模块 | 99 | **48** | **48.5%**（4 个「部分」；其余已 100% 定性：**23 个「开源版无实现可搬」**＝22 个模块目录缺失 + `feedback` 存根、6 个「外部依赖不可搬」、其余为「待做」；按新口径的主判定 **A 16 / B 18 / C 35** —— A 已被 yudao 现有能力替代、B 部分替代、C 真能力缺口，见 `docs/MODULE-FEASIBILITY-AUDIT.md`） | 主干
| 禅道业务代码（不含模板） | 447,330 行 | 216,281 行 | **48.3%** | 按「模块被触及」整模块计，是覆盖率**上限**（含 epic 829 + requirement 810 + caselib 2,293 + kanban 9,545；**metric 只计框架 29,667 行、bi 的 20,147 行未计**；含 chart 2,141） |
| 数据表 | 281 | 67 | **23.8%** | `zt_project` 同表承载项目+执行（3.7）；`zt_module` 是通用树（3.8）；`zt_file` 的字节不落库、交给 yudao 文件服务（3.16）；`zt_doc` 同表承载章节+文档，版本链带草稿位（3.17）；`zt_case` 的步骤是一对多子结构、无 `deleted` 列（3.18）；`zt_testrun` 排用例、`zt_testresult` 存每次执行（3.19）；`zt_effort` 是**通用**工时流水（本实现只落任务，3.23）；`zt_todo` 是个人待办（3.25）；`zt_team` 是项目/执行的成员表（3.27，**团队人数以它为准**，不是 `zt_project.team` 那个逗号串）；`zt_stakeholder` 是干系人（3.29，**不是团队成员**）；`zt_workestimation` 是项目工作量/成本估算（3.30）；`zt_burn` 是**燃尽图的每日快照**（3.39，没有 `deleted` 列，只增不改）；`zt_repo`/`zt_repohistory`/`zt_repofiles` 是代码库与提交记录（3.41，后两张没有 `deleted` 列），`zt_relation` 是通用关系表（提交↔需求/任务/缺陷）；`zt_holiday` 是节假日与补班（3.42，`getActualWorkingDays` 是全项目工作日的唯一出口）；`zt_entry` 是应用接入（3.43，第三方免登录：code+key+token），`zt_log` 是它的**通用**调用日志（同表还被 webhook 用）；`zt_company` 是公司信息（3.44，界面叫「组织视图」，`admins` 是判超管的逗号串、`id != 1` 是外部公司）；`zt_score` 是积分流水（3.45，无 `deleted` 列，规则与扩展加成在代码里） |
| REST 端点（对比禅道 action） | 1,709 | 500 | **29.3%** | 禅道一个 action 常对应多个页面/分支，不是一对一 |
| 已接入的关联字段 | — | `story/bug/task.module`、`story/bug.branch`、`file.objectType/objectID`、`doc.lib/parent`、`case.module/branch`、`testrun.task/case`、`bug.case/caseVersion/testtask`、`story.parent/root/path`、`effort.objectType/objectID` | — | 需求/缺陷/任务列表支持按模块（含子树）与分支过滤；附件通过 `(objectType, objectID)` 可挂到任意业务对象；文档的章节树用 `doc.path` 前缀匹配取子树 |
| 前端（自研 Vue3 代码） | 154,682 行模板 | 24,277 行 | — | 未逐行对齐，禅道模板含大量 UI 框架代码 |

⚠️ **口径修正**：自动扫描把 `testcase` / `testtask` / `testsuite` / `testreport` 四个模块的
PHP 与模板行数都记成了 `0` —— 因为它们的模板放在 `ui/` 目录而不是 `view/`，
扫描脚本只统计了 `view/`，PHP 那几个文件的归属也跟着丢了。本表已按 `module/<name>/*.php`
与 `module/<name>/{view,ui}/*.php` 重新实测（详见「按类别汇总」里业务-测试一行的变化），
因此总量的分母从 430,219 修正为 447,330。

⚠️ **口径说明**：`user` / `dept` / `group` 三个模块是**映射交付**（不建表、复用 yudao 的 RBAC +
提供禅道视角的翻译视图），与其余「真迁移」的模块不算同一量级的完成度，但确实已给出可评审的方案与实现。

⚠️ **上表按「模块被触及」计算，是覆盖率的上限。**
四十七个已完成模块只做了核心生命周期，未做关联关系、批量操作、
导入导出、通知、数据权限等，真实**语义覆盖率约 5%**。

⚠️ **`epic` / `requirement` 记的是「等价覆盖」，不是「另建两个模块」** ——
禅道 `module/epic/model.php` 与 `module/requirement/model.php` 都只有 15 行
（`class epicModel extends model`），两个 control 的每个 action 都只是把 `storyType`
换成 epic/requirement 后转调 story，数据存的是同一张 `zt_story`（靠 `type` 区分）。
本实现在 story 上做分层（类型字典 / 类型数量 / 分层树 / 类型过滤 / 父子层级规则），
所以模块数 +2，但**表数不变**、PHP 行数按模块整计 +1,639 行（829 + 810）。

⚠️ **工时明细（`zt_effort`）不是禅道的独立模块** —— 它的 control/model/view 都在
`module/task/` 里（`recordWorkhour` / `editWorkhour` / `deleteWorkhour`），所以这一轮把工时流水
做完后，模块数（22/99）与 PHP 行数口径都不变，变的只有表（+1）与端点（+8）。

**已完成模块的「完成」边界**（避免误读）：

| 模块 | 已做 | 未做 |
|---|---|---|
| `product` | CRUD、名称唯一、关闭/激活、删除保护、实时统计 | 产品规划、分支/平台、路线图、产品线 |
| `project` | CRUD、**归属项目集（parent）与层级(path/grade，逗号格式）**、四级角色、开始/挂起/激活/关闭级联、团队人数 | 项目模板、复制项目、项目内多执行批量创建 |
| `execution` | CRUD、与项目共表隔离、挂到项目、开始/挂起/激活/关闭、按项目统计、**动作按 `objectType='execution'` 记账（与项目分开）**、**`multiple` 继承所属项目**（多迭代项目下的迭代，报表与执行列表都靠它过滤）、**燃尽图（`zt_burn` 每日快照 + 实际/理想/延期三条线 + 采样与周末切换，3.39）** | 累积流图（cfd，依赖看板卡片的历史流转）、看板视图、多执行批量创建、关联产品/计划 |
| `program` | CRUD、父子项目集（逗号 path/grade）、状态流转、**移动项目集时整棵子树重算**、下级项目集/项目/产品统计、删除保护、产品归属（`zt_product.program`） | 项目集看板、干系人（stakeholder）、预算超支校验、白名单权限、导出、`refreshStats` 定时统计 |
| `story` | CRUD、版本链、正式变更、评审聚合、关闭/激活、**父子需求（分解）与父需求聚合/状态级联**（3.21）、**需求转任务与需求版本冻结**（3.22）、**需求分层（业务需求/用户需求/研发需求：类型字典、类型数量、分层树、类型过滤、父子层级规则，3.31）** | 关联发布、批量编辑、需求矩阵视图、需求关联用例/缺陷的统计视图、批量改父/批量改层级、导入导出 |
| `action` | 回收集合（回收站/动态/备注/动作渲染）：回收站列表（回查对象名、能否还原与原因）、还原（对象表 `deleted=0` + `undeleted` 动作）、隐藏/全部隐藏（`extra='beHidden'` + `hidden` 动作）、动态 feed（人/周期/产品/项目/执行）、备注（`commented` + 作者校验）、**动作渲染**（`renderedDesc`） | 回收站的「已隐藏」视图、按搜索/查询条件筛回收站、定时清理（`cleanActions`）、多视图（vision）隔离、评论附件与 @提醒 |
| `bi`（数据视图，SQL 模式） | `zt_dataview`：只读 SELECT 的增删改查 + 字段自动解析（带聚合的算指标、其余算维度）+ 预览（包一层 LIMIT）+ 试跑（先过白名单）+ 被图表引用时拒绝删除；`SqlGuard` 八条安全规则（单语句/无注释/必须 SELECT/关键字黑名单/只允许 zt_* 表/字段名白名单/聚合取枚举/结果限行） | DuckDB + Parquet 引擎、`zt_pivot` 透视表（多行多列交叉表）与下钻、`zt_sqlbuilder` 可视化建视图、`screen` 大屏、数据集权限与共享 |
| `chart` | 图表 CRUD（类型/数据视图引用/查询设置 `{dimensionField,metricField,agg,limit,sort}`/过滤器 eq/ne/like/gt/ge/lt/le/in/between）、**数据接口**（分组 + 聚合 → `[{name,value}]`）、类型字典（8 种 + echarts 映射）、版本 +1、前端 ECharts 渲染（饼/折线/柱状） | 钻取、导出、水球图与雷达图的真实渲染、图表权限、定时刷新 |
| `metric` | 度量框架：`zt_metric` 定义 + `zt_metriclib` 数据两张表；计算流程（跑口径 → 按周期清旧数据 → 落库 → 回写）；快照型（nodate）/周期型（year/month/week/day）两套取数与清理语义；度量视图接口（范围维度 + 对象名解析 + 时间过滤 + 分页）；字典与概览；**15 个已迁移口径**（MetricRegistry 里一条 code → SQL，注释标明对应的禅道 calc 类）；未迁移口径明确报错 | 其余 399 个 calc 类口径（依赖 repo/bi/代码库/流水线等未迁模块）、定时采集（cron/collector 与 yudao 定时任务对接）、多度量对比与图表、收藏、DuckDB dataset 引擎 |
| `kanban` | 八张表（空间/看板/区域/分组/泳道/列/卡片/格子）；空间与看板 CRUD 与状态流转；**新建看板自动生成默认布局**（区域+分组+默认泳道+四个默认列+所有格子）；区域/泳道/列 CRUD；**WIP 规则**（-1 或正整数、子列之和 ≤ 父列限额、父列有限额时子列不能不限）；卡片 CRUD、**移动**（按源泳道类型从同区域所有格子摘除再追加）、完成/激活（进度 0~99）、归档/还原、物理删除（顺手摘掉格子里的编号）；看板视图数据（`overWip` 提示，与禅道一样不硬拦） | 拆分子列、拖拽排序、从其他看板/需求/任务/缺陷导入卡片、研发看板（执行维度）、看板复制、空间成员与白名单的界面与数据权限、导出 |
| `caselib` | 用例库 CRUD（名称全局唯一，全局唯一口径来自禅道 `check('name','unique',"deleted='0'")`）、库内用例列表/详情/新建（复用 testcase 的版本与步骤规则）、**产品用例 → 用例库导入**（`fromCaseID`/`fromCaseVersion` 来源冻结 + `zt_module.from` 模块幂等同步 + 「源用例已更新」）、删除保护 | 库内用例导出/Excel 导入、批量建/批量编辑、库 → 产品导入（`testcase/importFromLib`）、库维度报表 |
| `epic` / `requirement` | **等价覆盖**：它们是 `zt_story` 的 `type` 分层（薄壳模块），本实现的做法见 `story` 一行与 IMPLEMENTATION-NOTES.md 3.31；两个模块的 501/482 行 control 全部是转调 story | 看板视图、需求分层报表、导入导出模板、`batchChangeParent`/`batchChangeGrade` |
| `task` | CRUD、工时三件套、开始/完成/关闭/取消/激活、按需求/执行列表、**工时明细流水（`zt_effort`：登记/修改/删除 + 任务已消耗/剩余/状态重算 + 按账号汇总，3.23）** | 多人任务(zt_taskteam)与按人分摊的工时、计时器、导入导出 |
| `bug` | CRUD、解决/关闭/激活、duplicate/fixed 联动校验、`branch` 归属 | 批量操作、Bug 转需求/任务、与版本/构建联动 |
| `stage` | 阶段模板 CRUD/批量/排序 + **占比累计 ≤ 100% 校验** + **按模板为项目生成阶段实例**（`zt_project type='stage'`，状态流转复用执行模块） | TR/DCP 评审点配置、IPD 阶段类型、阶段模板的导入导出、融合瀑布（waterfallplus）的额外阶段 |
| `projectstory` | 项目关联产品（可限定分支/计划）、纳入/移出需求、**关联时版本冻结与「版本已变更」标记**、子执行保护、移除后重编号 | 项目需求的报表/矩阵视图（`track`）、批量评审/批量指派/批量改计划、导出、用例联动（`linkCases`，依赖 testcase 模块） |
| `release` | CRUD、发布/失败/停止维护状态、**全局唯一版本号**、**影子构建**、从构建同步需求与 Bug、三份清单（需求/解决 Bug/遗留 Bug）、泛化关系表 | 发布通知与邮件（`notify`/`mailto`）、导出 HTML、按系统（`zt_system`）维度管理、逃逸 Bug 统计 |
| `build` | CRUD、集成构建（子构建合并）、按产品/执行/分支列表、需求与 Bug 关联、**关联 Bug 自动解决并写 resolvedBuild** | 制品库对接、提交测试单、构建与代码库（repo）联动、打包地址的自动化采集 |
| `productplan` | CRUD、状态机（开始/完成/关闭/激活）、待定计划、多分支、父子计划与状态聚合、需求/Bug 关联、需求数实时统计 | 批量编辑/批量改状态、计划看板、创建子执行、关联项目（`zt_projectproduct`） |
| `branch` | CRUD、关闭/激活、默认分支（唯一性）、虚拟主干、删除保护（需求/缺陷/模块） | 分支合并（`mergeBranch` 会把计划/发布/构建/需求/Bug/用例整体并到目标分支，工作量大）、批量编辑 |
| `file` | 上传（含 gid **两阶段绑定**）、按对象/gid 查询、下载累加计数、重命名（扩展名同步）、删除、按对象批量删除、**字节复用 yudao 文件服务**（不另建存储） | 编辑器内粘贴上传、图片/附件预览水印、秒传（`onlybody`/`uid` 去重）、编辑器与富文本联动、导入导出时的附件打包 |
| `doc` | 文档库 CRUD（主库保护 + 库内有文档拒绝删除）、章节树（path 前缀取子树）、文档 CRUD、**版本链（正文变了才 +1）**、**草稿位（version=0）与发布**、按库/章节/标题/类型/状态过滤、跨库移动与子树 path/grade 重算、浏览计数 | 文档模板与模板类型、文档水印、收藏（`collects` 只留计数）、权限白名单的实际过滤、API 接口库、Word/PPT/Excel 在线预览与 Office 转换、编辑器图片自动上传、文档基线（baseline）联动 |
| `testsuite` | CRUD、幂等加入/移出用例、按产品过滤、可加入用例列表、删除保护（集合内还有用例时拒绝） | 从集合批量排进测试单、集合复制/导入导出、按模块筛选用例、单元测试用例集 |
| `testreport` | CRUD、**汇总预览**（不落库）、读时现算的执行汇总（用例数/已执行/执行次数/通过/失败）、按用例明细、生成时留档需求/缺陷/用例清单、测试单必须同产品校验 | 按用例/执行人/模块/类型的图表统计、报告导出（HTML/Word）、定时发送、与发布单联动 |
| `testtask` | 测试单 CRUD、状态机（wait→doing→done，doing⇄blocked，关闭校验完成时间）、**用例编排**（排入/移除/指派，重复排入保留结果）、**执行用例**（步骤结果 → 用例结果，一次写三处）、执行历史 | 联调测试单(joint)、自动化测试与单元测试结果导入、测试报告(testreport)联动、测试套件(testsuite)、批量执行/批量指派、按用例场景编排 |
| `testcase` | CRUD、**步骤的一对多子结构**（步骤组 + 组内步骤，层级编号 1./1.1/1.1.1 由后端算出）、**版本规则：只有步骤变才 +1 并打回待评审**、版本快照与历史版本回看、评审（wait → normal/blocked/investigate）、**storyVersion 冻结与「待确认」**、按产品/分支/模块（含子树）/类型/环节/状态/优先级过滤 | 场景（scene）、**库 → 产品导入（`testcase/importFromLib`，反向导入已做：产品 → 库，见 caselib 一行）**、自动化脚本与脚本执行、批量创建/编辑/评审/移动、导入导出（Excel）、缺陷转用例、与测试单/测试报告的联动 |
| `holiday` | 假期/补班 CRUD（`year` 由 `begin` 自动推出）、按年/类型筛选、**实际工作日口径**（补班 > 假期 > 周末，左闭右开）并提供「工作日试算」接口；**燃尽图的横轴已接入该口径** | 按国家法定节假日一键导入（依赖外部数据源）、节假日审批流转、按部门/地区配置不同假期表 |
| `repo` | 代码库 CRUD（只支持本地 Git 仓库，路径必须存在且含 `.git`）、**同步提交记录**（`git log` 增量、幂等、重命名带 oldPath）、提交分页与详情（改动文件 + 关联对象）、**提交 ↔ 需求/任务/缺陷 双向关联**（解析 `Story #1 / Task #2,3 / Bug #4` 写 `zt_relation`，可反查）、删库时物理清理提交与文件 | 服务商 API 接入（GitLab/Gitea/SVN，属 `module/provider`）、在线代码浏览/Blame/Diff 文件内容、分支与标签同步（`zt_repobranch`）、合并请求（`mr`）、代码度量（`codescan`）、提交与构建/发布的联动 |
| `qa` | **等价覆盖**：按产品的质量统计（区间新增/解决/关闭、有效缺陷、已修复、修复率、未完成测试单、待评审用例）+ 待处理缺陷 + 待评审用例 + 未完成测试单四个列表块；口径取自 `module/bi/config/metrics.php` 的度量定义 | block 的积木引擎（可配置摆放的看板）、按部门/人员的质量视图、质量趋势折线、`feedback`→缺陷/需求 的转化链（依赖付费版） |
| `report` | 年度数据（公司/部门含子部门/个人三视角：登录/动作数/待办/工时/贡献+历年雷达/产品与执行产出/需求·任务·缺陷·用例的状态分布与月度趋势）、每日提醒（快到期的任务/缺陷/待办/测试单/看板卡片，按人聚合，产出数据不发邮件）、产出统计（`getOutput4API`）、项目状态总览 | `zt_report` 自定义报表（v20 已被 BI 取代）、提醒的实际发信、年度报告导出/分享、按自定义维度（如按产品线）的年报 |
| `my` | 概览统计（今日/未完成/已过期待办、我的任务/缺陷/需求/工时）；**我的待办/任务/缺陷/需求/工时/动态**；**我参与的项目/执行**（`PM/PO/QD/RD 或 FIND_IN_SET(team)`）；**我的团队**（`zt_team` 里 account=我的行 + 姓名 + 可用工时 `days×hours` + 对象名/状态）；**我的测试单**（`owner 或 createdBy`）；**我的用例**（`openedBy 或 reviewedBy`：OR 关系拆两次查、按 id 去重合并）；**我的文档**（`addedBy/assignedTo/editedBy`，章节默认剔除）；**我的日历**（待办 `date` / 任务 `estStarted` / 测试单 `begin` 按天归组，TreeMap 升序）；账号一律由后端从登录上下文取，前端不传 | 评审/风险/会议/MR/审批的聚合（依赖未迁移的 `reviewissue`/`risk`/`meeting`/`mr`/`approval`）；跨对象的全文检索与自定义关注列表；日历的周/日视图与导出 |
| `todo` | CRUD、批量创建、状态流转（开始·完成·关闭·激活）、指派、挪到今天、私有待办、「我的待办」口径 | 待办提醒/通知、重复待办（周期规则）、导出 |
| `score` | **规则驱动的计分器**：`zt_score` 流水（before/after 快照）+ `ScoreRules.java` 的 38 条规则与扩展加成；4 个端点（规则/明细/总览/计分）；四条特例（缺陷确认给提单人、任务完成含优先级与工时、执行关闭 PM+成员、需求关闭只给创建者）；次数与时间窗（hour=0 全量 / hour>0 当天）；已接 entry 的登录计分 | **总分不冗余在用户表**（= SUM 流水；禅道在 `zt_user.score`+`scoreLevel`）；`task.finish` 的「有子任务不给分」是空操作（未迁 `zt_task.parent`）；其余二十多处调用点未接（规则引擎与通用入口已就绪）；等级与重置积分未做 |
| `company` | **组织视图**：`zt_company` CRUD（本公司=id 最小的一条；name 唯一且不过滤已删除；`website`/`backyard` 只填 `http://` 会被清空）、**外部公司下拉**（`id != 1`，返回禅道 `text/value/keys` 三件套）、**超管口径对照**（`zt_company.admins` 逗号串 ↔ yudao `super_admin` 角色，列出 matched/onlyInZentao/onlyInYudao）；组织成员与组织动态**复用 organization / action 模块**；前端一页五个 Tab | **没有 delete**（禅道也没有删除 action）；`guest`（匿名登录）只存不生效（yudao 无匿名登录）；组织动态的「上周/上月」未支持（action 模块 period 只有 5 个值）；干系人表单还没接外部公司下拉（本项目外部人员是存名字，没建 `zt_user` 行） |
| `entry` | **第三方免登录通道**：`zt_entry` CRUD（code / 32 位 key / 免密开关 / IP 白名单，gitfox 内置应用不露出）+ **校验接口 `POST /zentao/entry/verify`（@PermitAll）**：8 步校验链（缺参 → 应用不存在 → 没配密钥 → IP 拒绝 → 签名 → 未绑账号 → 账号不存在）+ **两种签名**（time 模式 `md5(code+key+time)` 且 `time > calledTime` 防重放并回写；query 模式 `md5(md5(query)+key)`；前者失败 fallthrough 到后者）+ **IP 白名单六种形态**（`*` / 精确 / 逗号 / `a-b` / `192.168.1.*` / CIDR，留空等于不限制）+ 调用日志（通用表 `zt_log`，只记成功）+ 签名助手 `/sign`；错误码与提示文案照抄禅道（401/403/404/405/406/407 ） | **不建立登录会话**（有意偏离：yudao 的认证归 OAuth2，`verify` 只返回账号信息 + 记账，禅道的「免密跳转」换成等价的认证流程）；按应用统计调用量、`zt_log` 的 webhook 那一半、`gitfox` 那条内置应用的真实用途 |

## 第 43 轮：剩余 24 个模块的取证定性（99 个模块至此 100% 有结论）

第 43 轮用 **25 个子代理并行取证**（每个模块一个 agent，逐条查目录/代码量/action 数/表是否在开源建库脚本/
`edition` 门槛/存根迹象/外部系统依赖），产出 `docs/REMAINING-MODULE-VERDICTS.md`（95KB，含逐模块证据与建议顺序）。
24 个模块合计 **104,018 行** PHP、**317 个 action**，结论四类：

| 结论 | 个数 | 模块 | 含义 |
|---|---|---|---|
| **do-it** | 4 | `search`、`webhook`、`api`、`dimension` | 表与代码都在开源版、不依赖外部系统 → 按「表 + 接口 + 前端 + 回归 + 界面检查」完整迁移（`search` 已完成：23 项接口断言 + 18 项界面检查） |
| **map-only** | 10 | `ai`、`convert`、`admin`、`dev`、`zai`、`extension`、`cron`、`misc`、`jenkins`、`mark` | 本质是后台设置/调度器/编辑器/外部存储/内部服务层，yudao 已有等价能力（`cron`→`infra_job`、`mark` 是 `pivot` 的内部已读标记服务，连 control 都没有）→ **只做映射 + 说明，不写业务代码** |

| **not-possible** | 3 | `codescan`、`gitlab`、`feedback`（**理由已修正，且三者都不是「表缺失」**）：`codescan` 是**外部 GitFox 服务的代理**（`module/codescan/control.php:17` 构造函数第一件事就是 `loadModel('gitfox')->checkHealth()`，全模块 49 处 GitFox 调用），但它的表 `ops_scan_*` 等 17 张**都在开源版**，所以属「外部依赖不可搬」；`gitlab` 的 `control.php`(1,369 行) 确实被上游删除（commit `53c5c88791`，2026-01-21），但 `module/gitlab/` **目录仍在**（剩 `model.php` 249 行、0 action），说「模块目录不存在」不成立；`feedback` 是 **44 行存根**成立 |
| **not-worth** | 7 | `upgrade`、`tutorial`、`zanode`、`aiapp`、`gitfox`、`provider`、`zahost` | 有真实实现但目标侧不成立：`upgrade` 是禅道自己的 DB 补丁升级器（Java 侧只需一次性导入）；`tutorial` 是教学层（220 处跨模块 `tutorialMode` 钩子喂假数据） |

#### map-only 10 个模块的映射（详见 docs/MAP-ONLY-MAPPINGS.md）

判定口径：**A = 不迁移（yudao 现有能力替代）、B = 不迁移（部分替代，记缺口）、C = 真能力缺口（yudao 也没有）**；
总口径是「yudao 已有等价能力的一律算不迁移 —— 不是做不了，而是不需要做」，三个判定都不写代码。

| 模块 | 禅道职责 | 本项目对应 | 结论 |
|---|---|---|---|
| `ai` | LLM 平台层：模型接入、提示词智能体、会话消息、AI 小程序市场（27 个 `public function`、9 张自有表） | `ruoyi-vue-pro/yudao-module-ai`：14 个 Controller（`AiModel`/`AiApiKey`/`AiChatRole`/`AiChatConversation`/`AiKnowledge*`/`AiTool`/`AiWorkflow`/`AiWrite`）+ 69 个 Vue + 17 行 `system_menu` | **A**：现有能力替代（启用前置 = 补 `ai_*` 建表 + 打开 `yudao-server/pom.xml` 里被注释的依赖） |
| `convert` | BugFree 1/2、Redmine、Jira → 禅道的历史数据迁移向导，18 个 action 且**没有自有业务表**（源数据在外部系统的库里） | 无对应物；`yudao-spring-boot-starter-excel` 只面向 Excel 文件，不是连外部库 | **C**：真能力缺口，要补只能重写一次性脚本，属实施交付层、不进主工程 |
| `admin` | 系统后台/运维台：安全设置、弱口令检查、日志保留、功能开关、后台导航树、SSO、表引擎（22 个 action） | `yudao-module-system`（用户/角色/菜单/`system_login_log`/`system_operate_log`/OAuth2/aj-captcha）+ `yudao-module-infra`（`infra_job`/`infra_job_log`/`infra_config`/`infra/druid`） | **B**：缺口 = 密码强度分级、弱口令扫描、首次登录强制改密、邮件重置密码；若要只能在「系统管理」重写 |
| `dev` | 后台二次开发工具：PHP 反射生成接口文档、表结构浏览、语言项自定义、编辑器占位（6 个 action） | knife4j/springdoc（菜单 id 20 `infra:swagger` + `views/infra/swagger/`）+ `infra_codegen_table`（菜单 id 19 + `views/infra/codegen/`）+ 前端 `locales/{zh-CN,en}.ts` | **B**：缺口 = 运行时可编辑的界面文案、禅道术语对照表；若要只能重写 |
| `zai` | 外部 ZAI 服务接入桥接层：数据向量化、知识库检索、用户级 agent（7 个 action、只拥有 `zt_ai_useragent` 1 张表） | `yudao-module-ai` 的 `AiKnowledgeController`/`AiKnowledgeDocumentController`/`AiKnowledgeSegmentController` + `AiChatConversation`/`AiChatRole`；菜单 id 850「AI 知识库」 | **A**：现有能力替代（不迁的是外部 `/v8/*` 服务本身，不是能力缺口） |
| `extension` | 官方插件市场客户端 + PHP 源码覆盖安装器（11 个 action、`zt_extension` 1 张表） | 无运行时对应物；等价物是构建期 Maven 多模块（`yudao-server/pom.xml` 的注释式依赖 = 本项目的插件开关） | **C**：真能力缺口，运行时插件市场需重写才能有，且安全上不建议补 |
| `cron` | 通用后台定时任务调度器：`zt_cron`/`zt_queue` 排队抢占 + 17 条种子任务（13 个 action） | `infra_job` + `infra_job_log` + Quartz 的 11 张 `QRTZ_*`（`deploy/sql/02-quartz.sql`）+ `JobHandler` SPI + `views/infra/job/` + 菜单 id 14 | **A**：调度层现有能力替代；缺的是任务内容（17 条种子在 29 个 `implements JobHandler` 里 0 命中） |
| `misc` | 「系统-管理」杂项箱：验证码、心跳、静态页、更新检查、表检查（15 个 action，其中 5 个在开源版即断链） | aj-captcha（`ruoyi-vue-pro/yudao-module-system/.../framework/captcha/` + `aj.captcha.*`）、`infra/druid`（菜单 id 15）+ `infra/db`、前端 localStorage/pinia 偏好、已迁 `report` | **B**：缺口 = `about`/`changelog`/`features` 静态页、`ping` 保活、`downNotify`；若要只能重写 |
| `jenkins` | 外部 CI 服务的对接 driver：触发构建/查队列/取 console 日志（**可路由 action 0**、无页面无菜单） | 部分：`zentao` 模块 repo（`RepoService`/`GitLogReader`/菜单 id 90159）已覆盖「代码库」；CI 与 `ops_pipeline` 无对应物 | **C**：真能力缺口，需重写 Jenkins 适配器 + 流水线实体，且归 `pipeline`/`ci` 范畴 |
| `mark` | `pivot` 的内部「已读标记」服务层，**连 `control.php` 都没有**（可路由 action 0、无页面） | 无对应物；BI 已迁（`BiController` + `deploy/sql/42-zt_bi.sql` 菜单 90140-90144）但没有「新版本」标记 | **C**：真能力缺口，需重写才能有，但代价极小，需求出现再加一张极简表 |

10 个模块的入口、代码量、`TABLE_*` → 表名与 `db/zentao.sql` 实测、yudao 侧真实文件/表/菜单路径、逐条差距，见 [docs/MAP-ONLY-MAPPINGS.md](./MAP-ONLY-MAPPINGS.md)。

**这次并行取证还纠正了本清单自身的两处错误**（详见文件头「最近更新」与 `api` 行）：
`api` 的五张表其实都在开源版（我先前猜的 `zt_apilib`/`zt_apirelease` 两个名字根本不存在，
接口库落在 `zt_doclib` 的 `type='api'` 里）；`zt_deliverable` 也在开源版
（它没写 `IF NOT EXISTS`，被当初的 grep 漏掉）。教训写进了「可迁移性审计」一节。

**第 43 轮定的执行顺序，现已全部落地**：
① `search` ✅ → ② `dimension` ✅ → ③ `api` ✅ → ④ `webhook` ✅（第 44 轮，43 脚本 / 1704 断言 / 21 页面全绿）；
⑤ map-only 那 10 个 ✅（第 45 轮，`docs/MAP-ONLY-MAPPINGS.md`，判 A 3 / B 3 / C 4）；
⑥ not-possible / not-worth 的结论 ✅ 已固化，并升级为覆盖 **121 个候选**的 A/B/C 审计（`docs/MODULE-FEASIBILITY-AUDIT.md`）。

## 结论：99 个模块的最终分类

| 分类 | 模块数 | 说明 |
|---|---|---|
| ✅ **已完成** | **48** | 主干链（产品/项目/执行）、交付链、质量链、代码库、节假日、度量框架+15 口径、BI 的 `zt_dataview`（`@TableName` + `/dataview/*` 端点，**旧版漏计**）、`epic`/`requirement`（story 的 type 分层）、`caselib`（与用例集共用表）、`qa`（等价仪表盘）、`action` 收口、`user`/`dept`/`group`（复用 yudao RBAC + 禅道视角视图）等；4 个「部分」见下一行 |
| ⚠️ **部分** | 4 | `metric`（框架 + 15 个口径，其余 399 个 calc 类未迁）、`bi`（SQL 模式已通，DuckDB/Parquet 引擎与 `zt_pivot` 未做）、`common`（按需重建了操作日志/diff，未整体迁移）、`block`（看板积木引擎未搬，`qa` 用等价聚合替代） |
| ❌ **不在开源版（无实现可搬）** | **23** | 拆成两类：① **22 个 `module/<name>` 目录不存在**（`approval`/`auditplan`/`budget`/`cm`/`durationestimation`/`gapanalysis`/`issue`/`measrecord`/`meeting`/`milestone`/`mr`/`nc`/`opportunity`/`projectchange`/`projectdeliverable`/`pssp`/`researchplan`/`researchreport`/`review`/`reviewissue`/`risk`/`trainplan`，名字取自 `config/zentaopms.php` 的 `$config->programPriv->scrum/waterfall`）；② **1 个存根** `feedback`（44 行 model）。⚠️ **「模块没代码」不等于「表没发布」**：这 22 个里只有 `cm`/`measrecord`/`milestone`/`mr`/`pssp` 连表都没有，其余 17 个的 `zt_*` 表都随开源版发布（`db/zentao.sql` 带/不带 `IF NOT EXISTS` 两种写法都命中）。被旧版列进这一类的 `space`/`weekly`/`personnel`/`artifact`/`mail`/`message`/`pipeline`/`ppm`/`datatable`/`setting`/`sso`/`programplan`/`pivot`/`screen`/`codescan` **表全在开源版**（`space`→`ops_space`、`weekly`→`zt_weeklyreport`、`personnel`→`zt_userview`、`mail`/`message`→`zt_notify`、`setting`→`zt_config`、`programplan`→`zt_projectspec`…），属「待做」或「外部依赖」。详见 `docs/MODULE-FEASIBILITY-AUDIT.md` |
| ❌ **可继续迁移** | 40 | 表与代码都在开源版、只是还没做：`upgrade`（DB 升级器，yudao 已有自己的迁移机制）、`tutorial`、`convert`（**异构系统数据迁移向导**：BugFree 1/2、Redmine、Jira → 禅道）、`api`（**接口文档库**：接口空间/库/接口/结构/版本）、`search`、`cron`、`webhook`、`mark`、`dimension`、`admin`、`dev`、`extension`、`misc`、`gitfox`/`gitlab`/`jenkins`/`provider`（外部服务接入）、`zahost`/`zanode`（自动化测试节点）、`ai`/`aiapp`/`zai`（AI 能力）等 |

> **口径说明**：本表后两行的计数取自 `deploy/audit-module-feasibility.sh` 的 **121 个候选全集**
> （= `module/` 下 99 个目录 + `config/zentaopms.php` 的 `$config->programPriv->scrum/waterfall` 点名但目录缺失的 22 个），
> 所以「已完成 48 + 部分 4 + 无实现 23 + 可继续迁移 40 = 115」并不等于 99 —— 那 22 个缺目录的模块本来就不在 99 个目录里。
> 同一批候选按**新口径**（凡 yudao 已有等价能力即判「不迁移」）的分布是 **A 16 / B 18 / C 35**，详见 `docs/MODULE-FEASIBILITY-AUDIT.md`。


**「主干链路」这一条已经做完并做了加深**：产品 / 项目 / 执行三个模块本身在第 5~13 轮完成，
此后又按「被引用最广」的顺序补了它们依赖或向外辐射的能力 ——
团队（3.27）、干系人（3.29）、工时估算（3.30）、需求分层（3.31）、用例库（3.32）、看板（3.33）、
度量（3.34）、BI（3.35）、操作日志收口（3.36）、我的地盘两组（3.26/3.37）、报表（3.38）、
**执行燃尽图（3.39）**、测试仪表盘（3.40）、代码库（3.41）、**节假日与统一的工作日口径（3.42）**、
**第三方免登录的应用接入（entry，3.43）**、**组织视图与超管口径（company，3.44）**、**积分与规则引擎（score，3.45）**。

**如果还要继续做，建议这个顺序**（按「价值 ÷ 依赖」排）：

1. `api`（接口文档库）✅ **已完成**（第 44 轮，5 张表 + 12 端点 + 前端 1 页）；
   `convert`（异构系统迁移向导）判 **C**：它没有自有业务表、源库在 BugFree/Redmine/Jira 里，
   要用只能另立一次性的独立导入工具（属实施交付层，不进主工程）；
2. `cron` + `webhook` + `message`：`webhook` ✅ **已完成**；`cron`→`infra_job`、`message`→`system_notify_message`/`system_notice` 判 **A**（不迁移，只写映射）；
3. `metric` 的剩余 399 个口径 + `bi` 的 DuckDB/Parquet 引擎与透视表：**这是剩下的主要增量方向**（两个模块都判「部分」，不是缺表也不是缺替代）；
4. 小模块扫尾：`dimension`/`search` ✅ 已完成，`mark` 判 **C**（一张极简表即可，代价极小，需求出现再做）；
5. 真能力缺口（**C 35 个**，其中 21 个连禅道开源版都没实现，5 个本质是外部服务集成）：要做只能是**重写**，不是迁移。

## 可迁移性审计：哪些「未做」其实是「没有实现可搬」

第 39 轮为了给剩余模块定优先级，写了个脚本逐个模块做两项检查：

1. **它引用的 `TABLE_*` 常量对应的表，是否真的被开源建库脚本（`db/zentao.sql`）创建？**
   开源版一共建了 **281 张表**（`zt_` 231 + `ops_` 50 —— `config/zentaopms.php` 里 23 个表常量是**硬编码 `ops_` 前缀**，只按 `zt_<模块名>` grep 会全部漏掉）；模块引用了却不在其中的表，说明这部分数据结构**不随开源版发布**；
2. **代码里有多少处 `$this->config->edition != 'open'` 判断？**
   出现得多，说明该模块的主体功能是**付费版**能力（开源版只留了入口或存根）。

结论（逐条可复核，脚本输出见本轮提交记录）：

| 结论 | 模块 | 证据 |
|---|---|---|
| **模块目录不在开源版（无实现可搬）** | `approval`、`auditplan`、`budget`、`cm`、`durationestimation`、`gapanalysis`、`issue`、`measrecord`、`meeting`、`milestone`、`mr`、`nc`、`opportunity`、`projectchange`、`projectdeliverable`、`pssp`、`researchplan`、`researchreport`、`review`、`reviewissue`、`risk`、`trainplan`（22 个） | 名字来自 `config/zentaopms.php` 的 `$config->programPriv->scrum/waterfall`，再逐个 `[ -d module/<name> ]` 判定（`deploy/audit-module-feasibility.sh` 的 `目录存在=否` 列，实测这 22 个 `PHP行数=0`、`action数=0`）。**其中 17 个的 `zt_*` 表在 `db/zentao.sql`**，只有 `cm`/`measrecord`/`milestone`/`mr`/`pssp` 连表都没有 |
| **表未随开源版发布（模块级 0 个）** | 没有整模块级别「表缺失」的模块；只有被已发布模块顺带引用的辅助表缺席：`zt_sqlite_queue`（`admin`）、`zt_im_chat`（`ai`）、`zt_dashboard`/`zt_im_*`（`upgrade`）、`zt_im_userdevice`（`user`） | ⚠️ 旧版此行是**错的**：`space`→`ops_space`、`artifact`→`ops_artifact_libs`、`pipeline`→`ops_pipeline`、`ppm`→`ops_ppm`、`provider`→`ops_provider`、`repo`→`ops_repo`、`weekly`→`zt_weeklyreport`、`personnel`→`zt_userview`、`mail`/`message`→`zt_notify`、`setting`→`zt_config`、`programplan`→`zt_projectspec`、`codescan`→`ops_scan_*` **全部建在 `db/zentao.sql` 里**（`config/zentaopms.php` 有 23 个常量硬编码 `ops_` 前缀，只按 `zt_<模块名>` grep 会全漏）；`datatable`/`sso` 本就没有自有表。教训：建表匹配必须**两种写法 + 双前缀（`zt_`/`ops_`）+ 表名反引号边界**，且不能假设「表名 = 模块名」 |
| ~~**被 9 个模块引用、但开源版没有的表**~~ **（此结论有误，已更正）** | `zt_deliverable`（IPD 交付物）**其实在开源建库脚本里** | 第 42 轮逐表复核：`db/zentao.sql` 里有 `CREATE TABLE \`zt_deliverable\``（无 IF NOT EXISTS，所以按「CREATE TABLE IF NOT EXISTS」grep 会漏掉它）—— 这正是当初误判的原因。教训：**用固定格式 grep 建表脚本时要顺带跑一遍不带 `IF NOT EXISTS` 的变体** |
| **可以继续迁移（表与代码都在开源版）** | `cron`、`webhook`、`mark`、`dimension`、`search`、`upgrade`、`admin`、`tutorial`、`dev`、`extension`、`misc`、`zahost`、`zanode`、`ai`/`aiapp`/`zai`、`gitfox`、`gitlab`、`jenkins`、`provider`、`repobranch*`/`reporeviewflow`、`metric` 的剩余口径 | 表齐全、`edition != 'open'` 出现次数低或为 0 |
| **可继续迁移，但「模块性质」要看清** | `api`（**接口文档库** —— 用途已澄清；**先前「四张表不在开源版」的说法是错的**，五张表都在，只有 OpenAPI 导入/导出被 edition 挡住）、`convert`（**异构系统迁移向导**，无自有业务表，源数据在 BugFree/Redmine/Jira） | 第 42 轮 24 模块并行取证逐表复核，详见 `docs/REMAINING-MODULE-VERDICTS.md` |

**这份审计的意义**：迁移清单如果只写「❌ 未做」，会让人误以为「再花时间就能补上」。
实际上有 20 多个模块**在开源版里没有可搬的实现**（付费版能力或表结构未随包发布）——
把这一层如实标出来，才是「完整清单」该有的样子：**未做 ≠ 做不了**。

## 优先级说明

| 级别 | 含义 |
|---|---|
| **P0** | 主干地基。被其他模块大量外键引用，缺了后面每个模块都要用裸数字占位 |
| P1 | 核心业务闭环。禅道日常使用最频繁的功能 |
| P2 | 增强与集成。有替代方案或可延后 |
| P3 | 可选。演示/教程/边角功能 |

## 完整清单（按禅道代码量排序）

| # | 模块 | 禅道 PHP | 模板 | action | 分类 | 状态 | 优先级 | 备注 |
|---|---|---|---|---|---|---|---|---|
| 1 | `metric` | 29,667 | 1,188 | 12 | 度量-BI | ⚠️ 部分 | P1 | **度量框架已完整迁移**：`zt_metric`（定义）+ `zt_metriclib`（数据）两张表、计算流程（跑口径 → **按周期清旧数据** → 落库 → 回写 lastCalcRows/lastCalcTime）、快照型（nodate，按 `date >= 今天` 取）与周期型（year/month/week/day）两套语义、度量视图接口（维度 + 对象名解析 + 分页 + 时间过滤）、字典与概览、页面。**口径只迁了 15 个**「只用已迁表就能算出来」的（产品维度需求/缺陷/用例/发布/计划、项目维度执行/人员/工时、人员维度需求/缺陷/用例、三个年度新增）；其余 399 个 calc 类（module/metric/calc 共 20,368 行，依赖 repo/bi/代码库等未迁模块）属于数据资产，未迁移的 code 计算时**明确报错**。未做：定时采集（cron/collector）、多度量对比与图表（echarts）、收藏、度量项自定义 CRUD（禅道新版只读）、DuckDB dataset 引擎 |
| 2 | `upgrade` | 22,207 | 2,082 | 36 | 系统-运维 | ❌ 未做 | P2 |  |
| 3 | `bi` | 20,147 | 0 | 6 | 度量-BI | ⚠️ 部分 | P1 | **数据视图的 SQL 模式已通**：`zt_dataview`（只读 SELECT + 字段解析 + 预览 + 试跑）+ 图表 `zt_chart`（CRUD/版本 + 维度分组聚合 + ECharts）。安全边界写成 `SqlGuard`：单语句 / 必须 SELECT / 关键字黑名单 / 只允许 `zt_*` 表 / 字段名白名单 / 聚合函数取自枚举 / 结果限行，逐条有断言。**未做**：禅道底层的 DuckDB + Parquet 引擎（module/bi 25,589 行的主体）、`zt_pivot` 透视表的多行多列交叉表与下钻（`zt_pivotspec`/`zt_pivotdrill`）、`zt_sqlbuilder` 可视化建视图、`screen` 大屏。因为引擎与透视表都没做，本行 20,147 行**不计入**完成行数 |
| 4 | `story` | 17,004 | 2,536 | 54 | 业务-需求 | ✅ 已完成 | — | 已实现核心生命周期（详见 IMPLEMENTATION-NOTES.md 第 2 节） |
| 5 | `execution` | 16,918 | 7,871 | 72 | 主干-项目 | ✅ 已完成 | — | **没有独立的表**：与项目共用 `zt_project`，靠 `type=sprint/stage/kanban` 区分（IMPLEMENTATION-NOTES.md 3.7）。已做 CRUD + 状态机 + 与项目互斥隔离 + **层级口径（parent/path/grade，见 3.27）与执行团队** |
| 6 | `tutorial` | 16,434 | 266 | 8 | 系统-其他 | ❌ 未做 | P3 |  |
| 7 | `doc` | 14,505 | 4,864 | 70 | 协作-文档 | ✅ 已完成 | — | **库 → 章节 → 文档 → 版本**四层：`zt_doclib`/`zt_doc`/`zt_doccontent`。`zt_doc` 同表装章节（`type=chapter`）与文档；`path` 前缀取子树；**版本链 + `version=0` 草稿位**（本项目第二个追加式版本链，见 IMPLEMENTATION-NOTES.md 3.17）。未做：文档模板、水印、收藏、Office 在线预览、API 接口库 |
| 8 | `group` | 12,761 | 2,190 | 12 | 组织-权限 | ✅ 已交付（映射） | — | **不迁移**：zt_group+zt_grouppriv 映射到 system_role+system_role_menu，权限串 `zentao:模块:方法` 与 zt_grouppriv(module,method) 一一对应 |
| 9 | `task` | 12,442 | 3,715 | 36 | 业务-任务 | ✅ 已完成 | — | 已实现核心生命周期（详见 IMPLEMENTATION-NOTES.md 第 2 节） |
| 10 | `repo` | 12,103 | 3,913 | 71 | 集成-代码 | ✅ 已完成 | — | **只做「本地 Git 仓库」这一条链路**（服务商 GitLab/Gitea/SVN 属于 `module/provider`，未迁移）：`zt_repo`（代码库定义）+ `zt_repohistory`（提交，`revision`=sha、`commit`=自增序号）+ `zt_repofiles`（改动文件，含重命名的 oldPath）+ 通用关系表 `zt_relation`（提交说明里的 `Story #1 / Task #2,3 / Bug #4` → 对象关联，双向可查）。同步跑 `git log` 增量拉取、幂等（同 sha 不重复、文件与关系先清后写）。未做：服务商 API 接入（GitLab/Gitea/SVN）、在线代码浏览/Blame/Diff 文件内容渲染、分支与标签同步（`zt_repobranch`）、合并请求（`mr` 模块）、代码度量（`codescan`）（IMPLEMENTATION-NOTES.md 3.41） |
| 11 | `project` | 11,977 | 4,487 | 46 | 主干-项目 | ✅ 已完成 | — | 含层级(path/grade，**parent 指的是所属项目集**，逗号 path、grade 从 1 起)、四级角色、两个级联开关、**团队表 `zt_team`（团队人数以成员表为准）**（IMPLEMENTATION-NOTES.md 3.24 / 3.27） |
| 12 | `ai` | 11,707 | 601 | 27 | 智能-AI | ❌ 未做 | P2 |  |
| 13 | `action` | 10,933 | 176 | 10 | 系统-日志 | ✅ 已完成 | — | **操作日志收口**：`zt_action`/`zt_history` 记录（已接入全部已迁模块）+ **动作渲染**（`renderedDesc`：`admin 编辑：状态 激活 → 已关闭`；无变化退化成 `admin 关闭`；备注带内容）+ **回收站**（列表回查对象名与删除状态、还原=`deleted=0` 并记 undeleted、隐藏=`extra='beHidden'` 并记 hidden、全部隐藏；对象类型走 `ActionObjectMap` 白名单，未登记的类型明确标「不能还原」）+ **动态 feed**（按人/周期/产品/项目/执行过滤）+ **备注**（commented 动作、改备注校验作者）。未做：回收站的「隐藏列表」视图（禅道 type=hidden）、按搜索条件筛回收站、`cleanActions` 定时清理、`vision` 多视图隔离 |
| 14 | `bug` | 10,263 | 2,252 | 43 | 业务-缺陷 | ✅ 已完成 | — | 已实现核心生命周期（详见 IMPLEMENTATION-NOTES.md 第 2 节） |
| 15 | `kanban` | 9,545 | 2,971 | 60 | 协作-看板 | ✅ 已完成 | — | **看板**：七层聚合（空间 → 看板 → 区域 → 分组 → 泳道/列 → 卡片），位置存在 `zt_kanbancell.cards`（泳道×列的逗号列表）。已做：八张表 + 空间 CRUD/开关、看板 CRUD/**设置**/开关、**新建即自动建默认布局**（默认区域+分组+默认泳道+四个默认列+所有格子）、区域/泳道/列 CRUD 与 WIP 规则（子列之和 ≤ 父列限额）、卡片 CRUD/**移动（按源泳道类型摘除再追加）**/完成/激活/归档/还原/删除、看板视图数据（含 overWip 提示，与禅道一致不硬拦）。未做：拆分子列（splitColumn）、拖拽排序（sortRegion/Group/Lane/Column/Card）、从其他看板或需求/任务/缺陷导入卡片（importCard/importObject）、研发看板（getRDKanban 与执行的 setWIP）、看板复制、空间成员/白名单的界面与数据权限、导出（IMPLEMENTATION-NOTES.md 3.33） |
| 16 | `common` | 9,312 | 414 | 0 | 系统-基础 | ⚠️ 部分 | **P0** | 未迁移，但其中的公共能力（操作日志、diff）已按需重建 |
| 17 | `convert` | 8,969 | 699 | 18 | 系统-运维 | ❌ 未做 | P2 | **用途修正（原先被写成「数据导入导出/转换」，不准确）**：这是**异构系统数据迁移向导** —— 把外部系统（`converter/` 下有 BugFree 1/2、Redmine 1.1、以及配置里成体系的 **Jira** 字段与工作流映射）的历史数据搬进禅道：先连源库、再映射字段/状态/用户，最后写进 `zt_*`。它**没有自己的业务表**（源数据在外部系统里），所以「表缺失」这条对它不适用；迁移到 yudao 时它更适合做成**一次性导入工具**（或数据迁移脚本），而不是常驻业务模块 |
| 18 | `block` | 8,968 | 7,256 | 9 | 协作-看板 | ⚠️ 部分 | P3 | 未迁移，仪表盘区块未做 |
| 19 | `product` | 8,577 | 2,373 | 41 | 主干-产品 | ✅ 已完成 | — | 已做 CRUD + 关闭/激活 + 删除保护 + 实时统计；未做产品规划/分支/路线图 |
| 20 | `my` | 7,925 | 2,655 | 44 | 协作-个人 | ✅ 已完成 | — | **查询层**（不建表、不重写查询）：概览统计 + 我的待办/任务/缺陷/需求/工时/动态 + **我参与的项目/执行、我的团队、我的测试单/用例/文档、我的日历**。参与关系口径：项目/执行看 `PM/PO/QD/RD 或 FIND_IN_SET(team)`、测试单看 `owner/createdBy`、用例看 `openedBy 或 reviewedBy`（OR 拆两次查再按 id 去重）、文档看 `addedBy/assignedTo/editedBy`；账号一律由后端从登录上下文取，不从前端传（IMPLEMENTATION-NOTES.md 3.26 / 3.37）。仍未做：评审/风险/会议/MR/审批的聚合 —— 它们依赖尚未迁移的 `reviewissue`/`risk`/`meeting`/`mr`/`approval` |
| 21 | `pivot` | 6,872 | 979 | 6 | 协作-看板 | ❌ 未做 | P2 |  |
| 22 | `user` | 6,859 | 2,263 | 37 | 组织-用户 | ✅ 已交付（映射） | — | **不迁移**：zt_user → system_users；提供 zt_user 视角的用户列表与权限明细视图。密码哈希（md5+salt → BCrypt）需重置 |
| 23 | `codescan` | 5,932 | 3,533 | 42 | 业务-测试 | ❌ 未做 | P2 |  |
| 24 | `programplan` | 5,082 | 1,159 | 21 | 主干-项目集 | ❌ 未做 | P2 |  |
| 25 | `program` | 4,778 | 1,164 | 24 | 主干-项目集 | ✅ 已完成 | — | 与项目/执行**共用 `zt_project`**（禅道三个常量指向同一张表），靠 `type='program'` 区分。已做 CRUD、父子项目集（逗号 path/grade）、状态流转、下级项目集/项目/产品统计、删除保护、移动时子树 path/grade 重算（IMPLEMENTATION-NOTES.md 3.24） |
| 26 | `ppm` | 4,491 | 1,510 | 23 | 主干-项目 | ❌ 未做 | P3 |  |
| 27 | `custom` | 4,437 | 1,669 | 25 | 系统-管理 | ❌ 未做 | P2 |  |
| 28 | `productplan` | 4,331 | 1,752 | 27 | 主干-产品 | ✅ 已完成 | — | 计划：多分支（branch 逗号列表）、待定（日期哨兵 2030-01-01）、父子计划与状态聚合、需求/Bug 关联。需求列表也已支持按计划过滤 |
| 29 | `screen` | 4,261 | 371 | 10 | 协作-看板 | ❌ 未做 | P2 |  |
| 30 | `pipeline` | 4,228 | 1,264 | 31 | 业务-测试 | ❌ 未做 | P2 |  |
| 31 | `tree` | 4,162 | 719 | 14 | 系统-基础 | ✅ 已完成 | — | 通用模块树：`(root,type,branch)` 定位一棵树、逗号 path、删除改挂业务数据。文档/接口/看板等视图的树未做 |
| 32 | `release` | 3,971 | 1,578 | 17 | 主干-项目 | ✅ 已完成 | — | 发布：全局唯一版本号、影子构建、从构建同步、三份清单（需求/解决Bug/遗留Bug）、关系表。通知/导出未做 |
| 33 | `api` | 3,848 | 2,379 | 33 | 集成-接口 | ✅ 可迁移（本轮取证更正） | P2 | **用途与表结构都更正了**：① 它是**接口文档库**（接口空间/库/接口/结构/发布版本），界面是「接口空间 / 创建库 / 创建接口 / 发布接口 / 导入禅道 API」；② **五张业务表全部在开源建库脚本里** —— `zt_api`、`zt_apispec`、`zt_apistruct`、`zt_apistruct_spec`、`zt_api_lib_release`（`db/zentao.sql` 逐条 CREATE 过）；③ 开源版**从来没有** `zt_apilib` / `zt_apirelease` 这两张表（第 40 轮我按直觉猜了这两个名字并据此判定「四张表缺失」，**是我错了** —— 接口库实际落在 `zt_doclib`(`type='api'`)，不存在独立的库表）；代码侧也是完整实现（control 1,141 行 / model 1,097 行 / zen 181 行、32 个 action、13 个 ui 页面），**唯一的 7 处 edition 门槛只挡「OpenAPI(Swagger) 导入/导出」这一个付费扩展**，主体在开源版可跑。→ 结论：从「表缺失」改为**可迁移**（预计 2 轮：表 + 接口 + 前端 + 回归） |
| 34 | `todo` | 3,549 | 1,913 | 21 | 协作-个人 | ✅ 已完成 | — | **个人清单，与 `zt_task` 不是一回事**：可不挂对象（custom）、也可指向 task/bug/story/testtask。已做 CRUD/批量创建/状态流转（开始·完成·关闭·激活）/指派/挪到今天/私有待办/「我的待办」口径（IMPLEMENTATION-NOTES.md 3.25） |
| 35 | `search` | 3,428 | 393 | 11 | 系统-管理 | ✅ 已完成（保存查询） | — | **保存查询 + 拼音码表已实现**（8 个端点、前端 1 页、23 项接口断言 + 18 项界面检查）：`zt_userquery` 存**结构化条件 JSON**而不是 `sql` 列里的 SQL 片段（照搬会有注入风险且 MP 不接受半截 SQL）、`zt_searchdict` 拼音首字母码表（`需→x`、`求→q`）。**跨对象全文检索 `zt_searchindex` 有意不做**（要覆盖所有对象类型 + 依赖 InnoDB FULLTEXT 分词，收益低成本高），测试里把这条边界也钉住了（库里确实没有这张表） |
| 36 | `install` | 3,425 | 1,237 | 11 | 系统-运维 | ❌ 未做 | P3 |  |
| 37 | `admin` | 3,226 | 1,638 | 22 | 系统-管理 | ❌ 未做 | P3 |  |
| 38 | `misc` | 3,110 | 363 | 15 | 系统-管理 | ❌ 未做 | P3 |  |
| 39 | `report` | 3,048 | 647 | 4 | 度量-BI | ✅ 已完成 | — | **只读聚合，不建表**（v20 起这个模块只剩「年度数据」+「每日提醒」，旧的自定义报表 `zt_report` 被 BI 取代，本实现不做、自定义报表走 `bi` 的 SQL 模式）。已做：**年度数据**（公司/部门含子部门/个人三视角：登录/动作数/待办/工时/贡献+历年雷达/产品与执行产出/需求·任务·缺陷·用例的状态分布与月度趋势）、**每日提醒**（快到期的任务/缺陷/待办/测试单/看板卡片，按人聚合；不发邮件）、**产出统计**（`getOutput4API`）、项目状态总览。两处有意偏离：登录数读 `system_login_log`；「完成任务/解决缺陷」按 `finishedDate`/`resolvedDate` 数（本实现这两个操作记的是 `changed`，动作名分不出来）。未做：`zt_report` 自定义报表、提醒的实际发信、年度报告导出（IMPLEMENTATION-NOTES.md 3.38） |
| 40 | `build` | 3,028 | 1,073 | 19 | 主干-项目 | ✅ 已完成 | — | 构建：集成构建、需求/Bug 关联、关联 Bug 自动解决并写 resolvedBuild。缺陷「解决版本」已改为构建下拉 |
| 41 | `file` | 3,015 | 613 | 21 | 系统-基础 | ✅ 已完成 | — | **所有业务对象的公共能力**：`(objectType, objectID)` 挂附件、`gid` 两阶段绑定、下载计数；字节交给 yudao 文件服务，元数据留在 `zt_file`（IMPLEMENTATION-NOTES.md 3.16）。已作为「附件」Tab 嵌入需求详情 |
| 42 | `zai` | 2,907 | 239 | 7 | 智能-AI | ❌ 未做 | P3 |  |
| 43 | `zanode` | 2,859 | 1,187 | 29 | 业务-测试 | ❌ 未做 | P3 |  |
| 44 | `dev` | 2,772 | 676 | 6 | 集成-代码 | ❌ 未做 | P2 |  |
| 45 | `system` | 2,605 | 1,523 | 30 | 系统-管理 | ❌ 未做 | P3 |  |
| 46 | `extension` | 2,437 | 928 | 11 | 未分类 | ❌ 未做 | P3 |  |
| 47 | `gitfox` | 2,325 | 265 | 4 | 集成-代码 | ❌ 未做 | P2 |  |
| 48 | `caselib` | 2,293 | 985 | 16 | 业务-测试 | ✅ 已完成 | — | **用例库**：与「用例集」共用 `zt_testsuite`（库 = `product=0` + `type='library'`），库内用例是 `zt_case` 的 `(product=0, lib=<库>)`，库的模块树是 `(root=库, type='caselib')`。已做：库 CRUD（名称全局唯一）、库内用例列表/详情/新建（复用 testcase 的版本+步骤+评审规则）、**产品用例 → 用例库导入**（复制用例与步骤、`fromCaseID`/`fromCaseVersion` 版本冻结、模块用 `zt_module.from` 幂等同步、「源用例已更新」标记）、删除保护（IMPLEMENTATION-NOTES.md 3.32）。未做：库内用例导出/Excel 导入、批量建/批量编辑、库 → 产品导入（`testcase/importFromLib`）、用例库评审看板 |
| 49 | `space` | 2,218 | 1,553 | 19 | 主干-项目 | ❌ 未做 | P2 |  |
| 50 | `webhook` | 2,213 | 524 | 10 | 集成-通知 | ✅ 已完成 | — | **14 个端点 + 前端 1 页、62 项接口断言 + 28 项界面检查**：`zt_webhook`（日志复用已迁的 `zt_log`，`objectType=webhook`）。照抄：对象类型/动作白名单、payload 字段映射与 **`text` 现拼**（`create` 无条件追加 vs `update` 只在缺时补 —— 这个不对称也照抄）、`products` 是**交集**（空串 = 不限）、钉钉/飞书**加签**、**发送失败只落日志且接口仍返回成功**、群机器人强制 `application/json`。附 `@PermitAll` 的 mock 接收端做端到端断言（它是三方系统替身，回**裸文本** success 而不是 yudao 信封）。不建 `zt_notify`（调度留给 yudao Quartz），三种「应用消息」（钉钉/企微/飞书企业应用）因需凭据未投递，**发送时记「未投递」失败日志，不假装成功** |
| 51 | `aiapp` | 2,198 | 554 | 9 | 智能-AI | ❌ 未做 | P3 |  |
| 52 | `dataview` | 2,184 | 550 | 1 | 协作-看板 | ❌ 未做 | P2 |  |
| 53 | `mail` | 2,160 | 648 | 12 | 系统-通知 | ❌ 未做 | P2 |  |
| 54 | `artifact` | 2,014 | 627 | 19 | 业务-测试 | ❌ 未做 | P2 |  |
| 55 | `transfer` | 1,990 | 260 | 5 | 系统-运维 | ❌ 未做 | P2 |  |
| 56 | `design` | 1,906 | 780 | 16 | 集成-代码 | ❌ 未做 | P2 |  |
| 57 | `branch` | 1,621 | 360 | 10 | 集成-代码 | ✅ 已完成 | — | 产品维度分支/平台；id=0 虚拟主干；删除保护查需求/缺陷/模块。分类应是「主干-产品」，见下方备注 |
| 58 | `weekly` | 1,605 | 204 | 5 | 协作-个人 | ❌ 未做 | P2 |  |
| 59 | `zahost` | 1,601 | 770 | 11 | 业务-测试 | ❌ 未做 | P3 |  |
| 60 | `gitlab` | 1,578 | 0 | 0 | 集成-代码 | ❌ 未做 | P2 |  |
| 61 | `stakeholder` | 1,546 | 654 | 13 | 主干-项目 | ✅ 已完成 | — | **干系人（项目集/项目相关的人），与团队成员不是一回事**。已做 CRUD/批量/关键标记/内部外部（type 由 from 推导）/按对象+账号删除/我参与的（IMPLEMENTATION-NOTES.md 3.29） |
| 62 | `chart` | 1,525 | 188 | 4 | 协作-看板 | ✅ 已完成 | — | 图表 CRUD（名称/代码/类型/数据视图引用/查询设置/过滤器）+ **数据接口**（按维度分组、对指标做聚合，返回 `[{name,value}]` 直接喂 ECharts）+ 类型字典（8 种，含 echarts 渲染类型映射）+ **版本化**（改一次 version+1）+ 前端渲染（饼图/折线/柱状）。未做：钻取、图表导出、水球图/雷达图的真实渲染（类型保留、渲染退化为柱状图） |
| 63 | `personnel` | 1,508 | 305 | 5 | 组织-用户 | ❌ 未做 | P2 |  |
| 64 | `backup` | 1,483 | 239 | 11 | 系统-运维 | ❌ 未做 | P2 |  |
| 65 | `message` | 1,435 | 733 | 10 | 系统-通知 | ❌ 未做 | P2 |  |
| 66 | `editor` | 1,205 | 196 | 8 | 协作-文档 | ❌ 未做 | P3 |  |
| 67 | `cron` | 1,132 | 379 | 13 | 系统-运维 | ❌ 未做 | P2 |  |
| 68 | `stage` | 1,128 | 486 | 9 | 主干-项目 | ✅ 已完成 | — | 瀑布阶段：模板（占比 ≤ 100%）+ 按模板生成项目阶段，阶段实例复用执行模块流转状态。TR/DCP 评审点未做 |
| 69 | `datatable` | 1,086 | 219 | 6 | 度量-BI | ❌ 未做 | P2 |  |
| 70 | `company` | 1,079 | 470 | 7 | 组织-用户 | ✅ 已完成 | — | **用途修正（原先写的是「公司信息单行配置，可放 system_config」，不对）**：禅道 `$lang->company->common` 就是**组织视图**，三个入口 = 公司信息（view/edit）、组织成员（browse，按部门/内部外部看 `zt_user`）、组织动态（dynamic，全公司 action feed），共 7 个 action。本实现只落 `zt_company` 一张表 + 两条禅道口径：① `admins` 是**逗号串**（`,admin,`），是禅道判超管的唯一依据，yudao 侧是 `super_admin` 角色（且硬编码放行、不查权限表）—— 给了 `/admins` 口径对照接口；② `getOutsideCompanies()` 就是 `id != 1`，服务外部干系人的所属公司（返回禅道 `text/value/keys` 原样结构）。组织成员与组织动态**复用 organization / action 模块**（同 `my` 的原则：同一份过滤只允许一处实现）。照抄两条细节（`http://` 归一、name 唯一且不过滤已删除），三个「有意不做」（没有 delete、`guest` 只存不生效、动态的「上周/上月」未支持）都写进了 IMPLEMENTATION-NOTES.md 3.44。**遗留**：干系人表单还没接这个公司下拉（本项目的外部人员是存名字，没有建 `zt_user` 行） |
| 71 | `projectrelease` | 1,060 | 174 | 17 | 主干-项目 | ✅ 等价覆盖 | — | 禅道里是**项目视角的发布列表**（发布用 `zt_release.project` 逗号列表记录涉及项目）。不另建表，提供 `GET /zentao/projectrelease/release-list?project=`（`FIND_IN_SET` 反查）；并给 `release/create` 补了手工指定 `projects` 的能力（IMPLEMENTATION-NOTES.md 3.30） |
| 72 | `score` | 982 | 35 | 2 | 组织-用户 | ✅ 已完成 | — | **规则驱动的计分器**（不是页面）：`zt_score` 流水 + `ScoreRules.java` 里的 38 条规则与扩展加成（严重程度 s1+3/s2+2/s3+1、任务优先级 p1+2/p2+1、密码强度、执行关闭的 PM/成员与按期加成），全部照抄 `config.php`。四条特例：缺陷「确认」的分给**提单人**；任务完成 = 1 + 优先级加成 + round(预计/10)（且有子任务不给分 —— 本项目没迁 `zt_task.parent`，这条暂时是空操作）；执行关闭 PM 20 / 成员 5；**需求关闭只给创建者 2 分**（禅道 `$object = true` 短路，config 里关闭人那 1 分是**死规则**，本实现照抄）。次数与时间窗：hour=0 数全量、hour>0 数当天，命中上限静默跳过；0 分不落库。**有意偏离**：总分 = `SUM(zt_score.score)`（禅道冗余在 `zt_user.score`，本项目不迁用户表），开关用配置项 `zentao.score.enabled`（禅道在设置表里）。已接：entry 校验通过计一次登录分（补上第 40 轮记下的偏离）。未做：其余二十多处 `score->create` 调用点（任务/缺陷/用例/计划/发布/搜索等）逐个接上（规则引擎与通用入口已就绪）、等级 `scoreLevel`、重置积分 |
| 73 | `sso` | 928 | 119 | 13 | 集成-接口 | ❌ 未做 | P2 |  |
| 74 | `repobranchtype` | 903 | 322 | 5 | 未分类 | ❌ 未做 | P3 |  |
| 75 | `holiday` | 901 | 310 | 6 | 系统-其他 | ✅ 已完成 | — | **假期 + 补班**：`type='holiday'` 假期不算工作日、`type='working'` 补班算工作日（调休）；`getActualWorkingDays` 的优先级是「补班 > 假期 > 周末」，并照抄禅道**左闭右开**的区间语义。真正的价值是把**燃尽图的工作日口径**从「只跳周末」补成「跳周末 + 跳假期 + 补班日算工作日」（IMPLEMENTATION-NOTES.md 3.42）。另提供「工作日试算」接口与页面工具。未做：按国家法定节假日一键导入（禅道 holiday/import 依赖外部数据源）、节假日审批流 |
| 76 | `projectstory` | 872 | 177 | 20 | 主干-项目 | ✅ 已完成 | — | 项目需求范围：关联产品、纳入/移出需求、关联时版本冻结与版本变更标记、子执行保护。报表/导出/批量操作未做 |
| 77 | `provider` | 843 | 91 | 6 | 集成-接口 | ❌ 未做 | P2 |  |
| 78 | `epic` | 829 | 0 | 33 | 业务-需求 | ✅ 等价覆盖 | — | **业务需求（ER）**：禅道里是 story 的薄壳（`module/epic/model.php` 只有 15 行 `class epicModel extends model`），三层需求共用 `zt_story` 一张表、靠 `type` 区分。本实现不另建表，在 story 上做分层：`story/type-list`、`story/type-summary`、`story/type-tree`、分页 `type/types` 过滤 + **父子类型规则（父层级不能低于子）** + 分解出的子类型按父推导（IMPLEMENTATION-NOTES.md 3.31）。未做：看板视图、导入导出、批量改父/批量改层级 |
| 79 | `dept` | 828 | 212 | 6 | 组织-用户 | ✅ 已交付（映射） | — | **不迁移**：zt_dept → system_dept；视图补出禅道风格的 grade 与逗号 path |
| 80 | `requirement` | 810 | 0 | 32 | 业务-需求 | ✅ 等价覆盖 | — | **用户需求（UR）**：同 78，`module/requirement/model.php` 同为 15 行薄壳，同样是 `zt_story.type='requirement'`。父子规则、分解规则、分层树与类型字典一并覆盖；未做与 78 相同 |
| 81 | `entry` | 761 | 420 | 5 | 组织-用户 | ✅ 已完成 | — | **第三方免登录通道**：`zt_entry`（应用定义：code/32 位 key/免密开关/IP 白名单）+ 通用日志表 `zt_log`。**关键认知：规则不在 `module/entry` 里**（本体只有 control 149 行 + model 188 行 + 5 个 action），而在 `common::checkEntry()`（8 步校验链）+ `checkEntryToken()`（两种签名）+ `checkIP()`（六种白名单形态），共约 140 行 —— 只抄 entry 模块会得到一个谁都能过的空壳。已做：CRUD（密钥自动生成、代号校验、gitfox 内置应用不露出）、**校验接口 `POST /zentao/entry/verify`（@PermitAll）**、两种签名（time 模式 `md5(code+key+time)` + `time > calledTime` 防重放并回写；query 模式 `md5(md5(query)+key)`；前者失败会 fallthrough 到后者）、IP 白名单六种形态、调用日志分页、签名助手 `/sign`、前端页面（含接入自测卡片）。**有意偏离**：校验通过只返回账号信息 + 记账，**不建立登录会话**（yudao 的认证归 OAuth2）。未做：禅道的「免密跳转 + PHP 会话建立」本身、按应用统计调用量、`webhook` 复用 `zt_log` 的那部分（IMPLEMENTATION-NOTES.md 3.43） |
| 82 | `reporeviewflow` | 724 | 336 | 5 | 未分类 | ❌ 未做 | P3 |  |
| 83 | `repobranchrule` | 532 | 61 | 3 | 未分类 | ❌ 未做 | P3 |  |
| 84 | `dimension` | 469 | 30 | 2 | 度量-BI | ✅ 已完成 | — | **只读维度 + 切换语义**（5 个端点、前端 1 页、37 项接口断言 + 20 项界面检查）：`zt_dimension` + 3 条预置（宏观/效能/质量）。照抄三条：① 可见性 `acl=open OR createdBy=我 OR FIND_IN_SET(我,whitelist)`，超管短路；② **末次维度四级兜底链**（配置→会话→可见性校验→第一条）与「可见集合为空时不改写 id」的守卫；③ 导航下拉两处参数例外（`pivot+design→browse`、`bi+tree+browsegroup→加 groupID=0&type=viewType`）。**开源版没有维度 CRUD**（唯一写入口在升级器里），所以连写接口都没实现（测试断言 `POST /zentao/dimension/create` 返回 404） |
| 85 | `setting` | 410 | 0 | 0 | 未分类 | ❌ 未做 | P3 |  |
| 86 | `index` | 409 | 361 | 6 | 系统-管理 | ❌ 未做 | P3 |  |
| 87 | `ci` | 402 | 0 | 4 | 业务-测试 | ❌ 未做 | P2 |  |
| 88 | `cache` | 341 | 227 | 2 | 系统-运维 | ❌ 未做 | P2 |  |
| 89 | `jenkins` | 338 | 0 | 1 | 集成-CI | ❌ 未做 | P2 |  |
| 90 | `projectbuild` | 333 | 0 | 11 | 主干-项目 | ✅ 等价覆盖 | — | 禅道里是纯 redirect（`project/build`）。不另建表，提供 `GET /zentao/projectbuild/build-list?project=`：项目下所有执行的构建 + 直挂项目的构建（IMPLEMENTATION-NOTES.md 3.30） |
| 91 | `qa` | 153 | 0 | 1 | 业务-测试 | ✅ 已完成 | — | **等价覆盖**：禅道 `qa` 只有 153 行、1 个 action，`index()` 只做 `echo $this->fetch('block','dashboard','dashboard=qa')` —— 它是「仪表盘页面」而不是「质量业务模块」。本实现**不搬 block 的积木引擎、也不依赖 metric 框架**，直接按 `module/bi/config/metrics.php` 的口径用 SQL 算出四块内容：按产品的质量统计（区间新增/解决/关闭、**有效缺陷**、已修复、修复率、未完成测试单、待评审用例）+ 待处理缺陷 + 待评审用例 + 未完成测试单。未做：可配置摆放的看板块、按部门/人员的质量视图、质量趋势图（IMPLEMENTATION-NOTES.md 3.40） |
| 92 | `projectplan` | 139 | 0 | 6 | 主干-项目 | ✅ 等价覆盖 | — | 禅道里是纯 redirect（`productplan/browse`）——计划本来就是产品维度的。不另建表，提供 `GET /zentao/projectplan/plan-list?project=`：项目关联产品（可限定分支）的计划并集（IMPLEMENTATION-NOTES.md 3.30） |
| 93 | `mark` | 121 | 0 | 0 | 协作-个人 | ❌ 未做 | P2 |  |
| 94 | `feedback` | 44 | 0 | 0 | 业务-反馈 | ❌ 不迁移（开源版仅存根） | — | **没有实现可搬**：开源仓库里 `module/feedback` 只有 44 行 model —— `getFeedbackPairs()` 直接 `return array('admin' => 'Admin', 'user1' => 'User1')`（硬编码假数据），`getByList/getList` 只是按条件读 `zt_feedback`；而 bug/story/todo 里所有真正的反馈逻辑都带 `$this->config->edition != 'open'` 判断 —— **真实实现属于付费版**。表结构（`zt_feedback`/`zt_feedbackview` + `zt_bug.feedback`/`zt_story.feedback`）在开源建库脚本里存在，本实现**不建这两张空表**（没有代码读它，建了只会虚高表数量的覆盖率）。要迁移需先拿到付费版实现（IMPLEMENTATION-NOTES.md 3.40） |
| 95 | `workestimation` | 29 | 0 | 0 | 主干-项目 | ✅ 已完成 | — | 项目工作量/成本估算：自建表 + 两个派生值（工期 = 规模 ÷ 生产率、总人工成本 = 工期 × 每天工时 × 单位成本，均由服务端算、不接受入参）。禅道开源版只有 model 没有界面（IMPLEMENTATION-NOTES.md 3.30） |
| 96 | `testcase` | 9,635 | 3,620 | 53 | 业务-测试 | ✅ 已完成 | — | **用例 = 头部 + 版本快照 + 步骤**三张表：`zt_case`/`zt_casespec`/`zt_casestep`。第三条版本规则：**只有「步骤」变了才 version+1**，并把状态打回 `wait`（待评审）；`storyVersion` 冻结关联时的需求版本，需求升版后进入「待确认」（IMPLEMENTATION-NOTES.md 3.18）。未做：场景（scene）、用例库（caselib）、自动化脚本、批量导入导出、缺陷转用例 |
| 97 | `testreport` | 1,584 | 1,722 | 6 | 业务-测试 | ✅ 已完成 | — | 报告**只存条件与结论**，用例数/通过/失败读时现算；汇总规则是**每条 run 只取区间内最后一次结果**（否则「先通过后失败」会被算成两次）。未做：按用例/按执行人/按模块的图表统计、导出、定时发送（IMPLEMENTATION-NOTES.md 3.20） |
| 98 | `testsuite` | 842 | 350 | 14 | 业务-测试 | ✅ 已完成 | — | 用例集 CRUD + 幂等加入/移出；**本实现补了 `UNIQUE(suite,case)`** —— 禅道用 REPLACE 但没有唯一键，同一用例会重复插入。未做：从集合批量排进测试单、集合复制 |
| 99 | `testtask` | 5,050 | 2,795 | 33 | 业务-测试 | ✅ 已完成 | — | **测试链闭环**：测试单 `zt_testtask` + 排入的用例 `zt_testrun`（UNIQUE(task,case)）+ 每次执行的 `zt_testresult`。执行一次**写三处**（结果历史 / 用例的最近结果 / run 状态）；用例级结果由步骤结果算出（fail 优先）。并打通「执行失败 → 建缺陷」（缺陷记录来源用例+版本+测试单，复现步骤自动生成）。未做：联调测试单(joint)、自动化测试、测试报告关联、批量执行、单元测试结果导入（IMPLEMENTATION-NOTES.md 3.19） |

## 按类别汇总

> **本轮两处分类修正**：`branch` 的自动分类是「集成-代码」（禅道里它挨着 git 相关代码），
> 但业务语义是**产品维度的分支/平台**，应归入「主干-产品」；
> `tree` 归在「系统-基础」，实际承担的是**所有对象的模块树**。
> 下表保持自动生成的分类口径，仅在此说明。

| 分类 | 模块数 | PHP 行数 | 模板行数 |
|---|---|---|---|
| 度量-BI | 5 | 54,417 | 2,084 |
| 主干-项目 | 13 | 47,710 | 19,563 |
| 系统-运维 | 7 | 39,547 | 5,123 |
| 协作-看板 | 6 | 33,355 | 12,315 |
| 集成-代码 | 6 | 22,305 | 5,994 |
| 业务-测试 | 12 | 36,593 | 16,853 |
| 业务-需求 | 3 | 18,643 | 2,536 |
| 系统-其他 | 2 | 17,335 | 576 |
| 系统-管理 | 6 | 17,215 | 5,947 |
| 智能-AI | 3 | 16,812 | 1,394 |
| 系统-基础 | 3 | 16,489 | 1,746 |
| 协作-文档 | 2 | 15,710 | 5,060 |
| 协作-个人 | 4 | 13,200 | 4,772 |
| 主干-产品 | 2 | 12,908 | 4,125 |
| 组织-权限 | 1 | 12,761 | 2,190 |
| 业务-任务 | 1 | 12,442 | 3,715 |
| 组织-用户 | 6 | 12,017 | 3,705 |
| 系统-日志 | 1 | 10,933 | 176 |
| 业务-缺陷 | 1 | 10,263 | 2,252 |
| 主干-项目集 | 2 | 9,860 | 2,323 |
| 集成-接口 | 3 | 5,619 | 2,589 |
| 未分类 | 5 | 5,006 | 1,647 |
| 系统-通知 | 2 | 3,595 | 1,381 |
| 集成-通知 | 1 | 2,213 | 524 |
| 集成-CI | 1 | 338 | 0 |
| 业务-反馈 | 1 | 44 | 0 |

## 下一步建议顺序

1. ~~**P0 主干链路**：`product` / `project` / `execution`~~ ✅ **已完成**。
   产品→项目→执行→需求/任务/缺陷 这条主干链已打通，`zt_task.execution` 有真实实体可挂。
2. ~~**P0 基础维度**：`branch`（分支/平台）、`tree`（模块树）~~ ✅ **已完成**。
   `zt_story.module` / `zt_story.branch` / `zt_bug.module` / `zt_bug.branch` / `zt_task.module`
   现在都有实体可挂，并且需求/缺陷/任务的**列表与表单都已接入**（按模块过滤时自动包含子模块，
   与禅道一致），数据不再需要裸数字占位。
3. ~~**P0 产品规划 → 构建 → 发布**：`productplan` + `build` + `release`~~ ✅ **已完成**。
   产品侧「规划（计划）→ 开发（执行/任务）→ 打包（构建）→ 交付（发布）」闭环已打通，
   `zt_bug.resolvedBuild` 指向构建，发布里能看到完成的需求 / 解决的 Bug / 遗留的 Bug。
   另外 **P0 项目需求范围 `projectstory`** ✅ 也已完成：项目先关联产品，需求按「关联时的版本」纳入范围。
4. ~~**P0 组织与权限**：`user` / `group` / `dept`~~ ✅ **已交付「不迁移 + 映射 + 视图」方案**
   （IMPLEMENTATION-NOTES.md 3.15）：`zt_grouppriv(module, method)` 与 yudao 的 `zentao:模块:方法` 权限串一一对应，
   `/zentao/organization/*` 提供翻译回禅道视角的只读接口与字段对照表。
5. ~~**P0 公共能力**：`file`（附件）~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.16）。
   需求 / 任务 / Bug / 文档共用一套 `(objectType, objectID)` 附件接口，
   `gid` 两阶段绑定与下载计数照搬禅道；字节交给 yudao 文件服务，**不引入第二套存储**。
   前端以公共组件 `AttachmentPanel.vue` 嵌入详情页，已先落在需求详情。

6. ~~**P1 文档库**：`doc`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.17）。
   `zt_doclib` → `zt_doc`（章节 + 文档同表）→ `zt_doccontent`（版本 + 草稿位）三层打通，
   章节树复用 `zt_module` 的 path 规则，版本链复用需求快照那套「追加式」写法。

   至此主干链（产品/项目/执行/阶段/需求/任务/缺陷）、交付链（计划/构建/发布/项目需求范围）、
   组织权限、公共附件能力与文档库都已有可运行、可评审的落地。

7. ~~**P1 测试用例**：`testcase`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.18）。
   新增点是「步骤」这种**一对多子结构**（`zt_casestep`，带步骤组与层级编号），
   并且给出了**第三条版本规则**：只有步骤变化才升版本并把状态打回待评审。

8. ~~**P1 测试执行**：`testtask`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.19）。
   `zt_testtask` + `zt_testrun` + `zt_testresult` 打通，执行一次写三处，
   `zt_case` 上的「最近执行结果/执行人/执行时间」终于有数据来源。

**接下来建议**：

9. ~~**P1 测试报告与套件**：`testreport` + `testsuite`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.19/3.20）。
   报告从测试单的执行结果**汇总**出来（先通过后失败这类顺序问题有个坑，见坑位 #31），
   套件则是「可复用的用例集合」。

10. ~~**P1 工时明细**：`effort`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.23）。
    它不是一个独立模块，而是 `module/task` 里的 `recordWorkhour` 三件套 ——
    关键在于 `task.left` 的语义：**以最后一条工时声明的剩余为准**，不是「预计 - 已消耗」。
    剩下 **P1 个人地盘**：`my`（我的地盘，各个「我参与/我指派」的聚合视图）—— 已在第 34 轮补齐（见第 12 条）。
11. ~~**P0 项目集**：`program`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.24）。
    这一轮最有价值的发现不是「多了一个模块」，而是**禅道有三个常量指向同一张表**：
    `TABLE_PROGRAM` / `TABLE_PROJECT` / `TABLE_EXECUTION` 都是 `zt_project`。
    项目集靠 `type='program'` 区分、项目靠 `parent` 指项目集、执行靠 `project` 指项目；
    顺带纠正了上一轮把项目 `path` 写成斜杠格式的错误（禅道是逗号且 grade 从 1 起）。

12. ~~**P1 个人协作**：`todo` + `my`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.25 / 3.26 / **3.37**）。
    `zt_todo` 是本项目唯一「属于个人」的表（和 `zt_task` 不是一回事），
    `my` 则是纯查询层 —— 刻意不重写查询，而是带上当前账号去调各模块已有接口，
    避免过滤逻辑出现第二份实现。第 34 轮把第二组也补齐了：我参与的项目/执行、我的团队、
    我的测试单/用例/文档、我的日历（IMPLEMENTATION-NOTES.md 3.37），`my` 由「⚠️ 部分」变为「✅ 已完成」。
    仍未做的是依赖未迁移模块的那部分聚合（评审/风险/会议/MR/审批）。

13. ~~**P1 质量链收口**：缺陷 → 构建/发布 清单的自动回写~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.28）。
    缺陷解决时填的「解决版本」是构建编号 → 自动并进 `zt_build.bugs`，
    再找 `FIND_IN_SET(构建) 或 shadow=构建` 的发布并进 `zt_release.bugs` + 同步关系行；
    「关联 Bug 到构建」这条反向路径也走同一条链（关联即解决）。
    质量链至此闭环：用例失败 → 建缺陷 → 解决 → 进构建 → 进发布。
14. **P2 主干补齐**：`programplan`（项目集计划）。
    ~~团队表 `zt_team`~~ ✅ 已完成（IMPLEMENTATION-NOTES.md 3.27）；~~干系人 `stakeholder`~~ ✅ 已完成（IMPLEMENTATION-NOTES.md 3.29）——
    它顺带纠正了「团队人数从 `zt_project.team` 逗号串推导」与「执行不在层级里」两个口径问题。
    接口（33 项断言）与界面（团队抽屉）都已验证通过；
    全量回归 31 个脚本 / 1211 项断言 + 21 个页面全绿（跑在本机栈，见 README 2.4）。
    干系人 `stakeholder` 也已完成（IMPLEMENTATION-NOTES.md 3.29），接口 23 项断言 + 界面检查都通过。
15. **P2 度量与 BI**：`metric`（禅道最大单模块）+ `bi`，配置驱动，工作量大但独立。
    至此 **P1 层全部收口**：主干链、交付链、质量链、个人协作、报表、测试仪表盘、代码库都已落地；
    `feedback` 经核实开源版只有存根，记为「不迁移」。
    ~~测试仪表盘 `qa`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.40，等价覆盖）；`feedback` 经核实**开源版只有存根**，
    真实实现属付费版，故记为「不迁移」而不是假完成。
    ~~报表 `report`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.38）：只读聚合，不建表；顺带纠正了
    「执行的动作 objectType 应为 execution」「执行行的 multiple 继承项目」两个主干链路口径。

18. ~~**P2 积分**：`score`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.45）。
    这个模块的价值不在页面（只有 2 个 action），而在**规则表**与 `create()` 里按模块分支的特例：
    38 条规则 + 四种扩展加成全部照抄进 `ScoreRules.java`；四条特例照抄（缺陷确认给提单人、
    任务完成 = 1 + 优先级加成 + round(预计/10)、执行关闭 PM 20/成员 5、需求关闭只给创建者）。
    最有意思的一条是**死规则**：`config.php` 里 `story.close = 1 分`，但实现里 `$object = true`
    提前返回，关闭人那 1 分永远不落库 —— 只有创建者拿 2 分。这种「配置写着、代码短路掉」的地方，
    迁移时最容易顺手"修好"而和禅道对不上，所以照抄并写进断言。
    顺带补掉了第 40 轮记下的偏离：entry 校验通过 → 计一次 `user.login` 分（跨模块断言）。

17. ~~**P2 组织视图**：`company`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.44）。
    这一轮的价值在两处「口径」而不是 CRUD：① `zt_company.admins` 是逗号串，是禅道判超管的
    唯一依据，而 yudao 侧超管是 `super_admin` 角色且**硬编码放行、不查权限表** ——
    给了口径对照接口，实测差异 7 个（环境数据决定，接口只把差异摆出来）；
    ② `getOutsideCompanies()` 就是 `id != 1`，服务外部干系人的所属公司。
    另外**纠正了清单里这一行的错误描述**：原先写「公司信息单行配置，可放 system_config」，
    实际它是三个入口的组织视图（公司信息/组织成员/组织动态），7 个 action。
    组织成员与组织动态按 `my` 的原则复用已有模块，不重写查询。
    顺带把「本机只当浏览器」的最后两块搬走：**编译搬到服务器**（`/opt/yudao-build` + Maven +
    阿里云镜像，`build.sh --deploy` 一条命令）、**查库断言从 ssh 改直连 3307**
    （16s/次 → 0.7s/次，全量回归从几小时降到几十分钟）。

16. ~~**P2 集成-应用接入**：`entry`~~ ✅ **已完成**（IMPLEMENTATION-NOTES.md 3.43）。
    这一轮的收获不是「又做了一个 CRUD」，而是两条纠错：
    ① 禅道 `entry` 的**规则不在 `module/entry` 里**（本体 337 行、5 个 action），
    而在 `common` 的 `checkEntry/checkEntryToken/checkIP` 三段共约 140 行 ——
    按模块目录抄会得到一个谁都能过的空壳；
    ② 顺手把清单里两个模块的用途标注改对了：`api` 是**接口文档库**（接口空间/库/接口/结构/版本，
    并且 `zt_apilib`/`zt_apirelease`/`*_spec` 四张表不在开源版），
    `convert` 是**异构系统迁移向导**（BugFree/Redmine/Jira → 禅道，没有自己的业务表），
    原先分别被写成「对外 REST 接口」与「数据导入导出/转换」。

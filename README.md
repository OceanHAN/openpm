# 禅道 → Java + MySQL 迁移验证项目

把 [禅道（ZenTao）](https://github.com/easysoft/zentaopms) 从 PHP 迁到 Java 技术栈的**可行性验证**。
不打散仗：沿着禅道最核心的**主干业务链路**（产品 → 项目 → 执行 → 需求 / 任务 / 缺陷）
做一条完整的垂直切片，用禅道真实的表结构和业务规则当规格，
在 [yudao / ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) 上完整实现一遍，
用来回答一个具体问题：

> 把禅道这种量级的系统搬到 Java，到底难在哪、贵在哪？

---

## 一、结论摘要

| 问题 | 答案 |
|---|---|
| 技术栈能不能扛住？ | **能**。MySQL/Redis 在远端、Java 后端、Vue3 前端全链路一次跑通，没有架构级障碍 |
| 禅道的业务规则能不能原样搬？ | **能**。版本链、评审、状态机、级联关闭、共用表、通用树表、团队/干系人/看板/度量/BI/操作日志等规则全部 1:1 复刻（有意偏离的地方逐条写明）；
**43 个脚本 / 1704 项断言 + 21 个页面全部通过**（真实 MySQL + Redis；后端与前端都跑在 192.168.0.119），
团队、干系人、需求分层、用例库、看板、度量、BI、回收站、我的地盘、报表、燃尽图、测试仪表盘、代码库、节假日、应用接入、公司信息、积分、保存查询、维度、接口文档库、Webhook 另有浏览器专项检查 |
| 框架约定差异多不多？ | **不多，但必须知道**。踩了 58 个坑，绝大多数是配置级、一次性的（见第四节） |
| 真正的成本在哪？ | **业务规则的覆盖面**，不是技术栈。infra 搭建是一次性成本，业务逻辑是线性的 |

**一句话**：真正的障碍是禅道的复杂度，不是 PHP 到 Java 的翻译。

**现在的结论**：99 个模块里 **48 个已完成**（48.5%），4 个「部分」；其余已 **100% 定性**：
**23 个「开源版无实现可搬」**（22 个模块目录就不在发行包里 + `feedback` 存根）、6 个「外部依赖不可搬」、其余为「待做」。
第 45 轮起改用**新口径**：**凡 yudao 已有等价能力的一律判「不迁移」，只写能力替代映射**，三分类 **A 16 / B 18 / C 35** ——
**A** 已被 yudao 现有能力替代（`approval`→`yudao-module-bpm`、`mail`→`system_mail_*`、`message`→`system_notify_*`、`sso`→OAuth2、`setting`→`infra_config`、`cron`→`infra_job`、`ai`→`yudao-module-ai` …）、
**B** 部分替代（`pivot`/`screen`/`pipeline`/`ppm`/`personnel` … 逐条记缺口）、
**C** 真能力缺口（`risk`/`meeting`/`mr`/`weekly`/`artifact`/`space`/`codescan`/`gitlab`/`jenkins` 等，要用只能重写、不是迁移）。
清单与理由见 `docs/MIGRATION-INVENTORY.md`、`docs/REMAINING-MODULE-VERDICTS.md`、**`docs/MAP-ONLY-MAPPINGS.md`**、
**`docs/MODULE-FEASIBILITY-AUDIT.md`**（含可复现脚本 `deploy/audit-module-feasibility.sh`，121 个候选逐个判定）。
**`product` / `project` / `execution` 这三条主干链路不仅做完，还按「被引用最广」向外补了一整圈**
（团队、干系人、需求分层、用例库、看板、度量、BI、操作日志、我的地盘、报表、燃尽图、测试仪表盘、
代码库、节假日与统一的工作日口径、第三方免登录的应用接入、组织视图（公司信息/超管口径）、积分与它的规则引擎、保存查询、维度、接口文档库、Webhook。
完整分类与「如果还要继续做」的建议见
`docs/MIGRATION-INVENTORY.md` 文末的《结论》与《可迁移性审计》两节。

**最值得先看的一条**：禅道的「执行」没有独立数据表 —— `TABLE_EXECUTION` 就是 `zt_project`，
项目和执行同表、靠 `type` 区分（见 3.7）。这类「表结构与直觉不符」的设计，
才是迁移时真正花时间的地方。

---

## 二、已完成范围

### 2.1 数据模型（67 张表，列名对齐禅道 `zt_*`）

| 表 | 说明 |
|---|---|
| `zt_product` | **产品（根对象）**，需求/计划/发布/缺陷都挂在产品下。含实时统计 |
| `zt_story` | 需求头部：当前状态、当前版本号、供列表展示的标题 |
| `zt_storyspec` | **需求版本快照**，`(story, version)` 唯一，保存每一版的 title/spec/verify |
| `zt_storyreview` | **需求评审**，`(story, version, reviewer)` 唯一，评审绑定到具体版本 |
| `zt_stage` | **瀑布流程阶段模板**。`workflowGroup` 下若干阶段各带工作量占比（合计 ≤ 100%）；项目里实际的阶段是 `zt_project` 里 `type='stage'` 的记录，按模板生成 |
| `zt_projectstory` | **项目/执行关联需求**。`version` 记录的是**关联时的需求版本**，需求后续变更不影响已排期的内容（列表标「版本已变更」）；`order` 移除后会重新编号 |
| `zt_projectproduct` | **项目关联产品**，是需求候选范围的来源（可限定分支与计划） |
| `zt_project` | **项目集 + 项目 + 执行**（同一张表！`type='program'` 是项目集、`project` 是项目、`sprint/stage/kanban` 是执行），主干链中间层：层级(path/grade，逗号格式)、四级角色、两个级联开关。项目集的 `parent` 指上级项目集，项目的 `parent` 指所属项目集，执行的 `project` 指所属项目（3.7 / 3.24） |
| `zt_task` | **任务**，含 `estimate`/`consumed`/`left` 工时模型 |
| `zt_bug` | **缺陷**，生命周期 active → resolved → closed，可反复激活 |
| `zt_branch` | **分支/平台**（产品维度）。id=0 是虚拟「主干」，不落库 |
| `zt_release` | **发布**（对外交付版本）。引用构建与计划，维护三份清单：完成的需求 / 解决的 Bug / 遗留的 Bug。**版本号全局唯一**，创建时自动生成「影子构建」 |
| `zt_releaserelated` | **发布关联表**。`(release, objectType, objectID)` 泛化关系，支持按对象反查发布 |
| `zt_build` | **构建**（一次打包）。记录本次完成的需求与解决的 Bug（都是逗号列表）；缺陷的 `resolvedBuild` 存的就是构建编号。`builds` 列子构建，形成「集成构建」 |
| `zt_productplan` | **产品计划**。需求用 `zt_story.plan` 挂到计划上。注意 `branch` 是**逗号列表**（一个计划覆盖多个分支），「待定」用日期哨兵 `2030-01-01`，`parent=-1` 表示有子计划 |
| `zt_module` | **通用模块树**。`(root, type, branch)` 定位一棵树：需求/缺陷/用例挂产品、任务挂执行、产品线 root=0。path 用逗号格式 `,5,6,` |
| `zt_action` | **操作日志**，一次操作一行：谁、何时、对哪个对象、做了什么 |
| `zt_history` | **字段级变更明细**，挂在某个 action 下，记录字段的旧值/新值/逐行 diff |
| `zt_file` | **附件**。靠 `(objectType, objectID)` 挂在任意业务对象上，所以它是**所有模块的公共能力**；`gid` 支撑禅道的「先传附件、后绑对象」两阶段上传，`downloads` 是下载计数。**字节不在这里**：交给 yudao 的文件服务（本地/DB/S3 由 `infra_file_config` 决定），`pathname` 存它返回的 URL |
| `zt_doclib` | **文档库**。`type=product/project/execution` 是跟随对象走的主库（`main=1`，不允许删除），`type=custom` 是团队空间下的自定义库（必须挂在空间 `parent` 下） |
| `zt_doc` | **文档 + 章节**（同一张表！`type='chapter'` 是章节即目录节点，其余才是文档），`parent`/`path`/`grade` 组成章节树，规则与 `zt_module` 一致 |
| `zt_case` | **测试用例头部**。`version` 是当前版本号；**`storyVersion` 冻结关联时的需求版本**，需求升版后用例会进入「待确认」 |
| `zt_casespec` | **用例版本快照**。`(case, version)` 唯一，只存标题 + 前置条件 —— **步骤不在这张表里** |
| `zt_casestep` | **用例步骤**，本项目第一个真正的**一对多子结构**：一个 `(case, version)` 下 N 条步骤；`type=group` 是步骤组、`parent` 指向所属组；层级编号 `1./1.1/1.1.1` 由后端算出不落库。**没有 `deleted` 列**（禅道原样），删除即物理删 |
| `zt_testsuite` | **用例集**：把用例打包复用（排进测试单时一次选完）。不产生任何执行数据 |
| `zt_suitecase` | **集合里的用例**。禅道原表没有唯一键，本实现补了 `UNIQUE(suite, case)` —— 禅道用 `REPLACE INTO` 但没有唯一索引，同一个用例会被重复插入 |
| `zt_testreport` | **测试报告**：只存条件（汇总哪些测试单、时间范围）与结论，**用例数/通过/失败都是读的时候现算**，所以不会过期失真 |
| `zt_testtask` | **测试单**（一次测试任务）：产品/项目/执行/构建、负责人、起止日期、状态机 `wait→doing→done`（`doing`⇄`blocked`） |
| `zt_testrun` | **测试单里排的用例**，`UNIQUE(task, case)`。`zt_case` 上的「最近执行结果」其实就是执行时从这里回写过去的 |
| `zt_testresult` | **执行结果历史**：一条 run 可以跑很多次，每次一行；步骤级结果以 JSON 存在这里，用例级结果由它算出来 |
| `zt_workestimation` | **项目工作量/成本估算**：规模、生产率、工期（= 规模 ÷ 生产率）、每天工时、单位人工成本、总人工成本（= 工期 × 每天工时 × 单位成本）。禅道开源版**只有 model 没有界面**，属于 IPD/企业版能力（3.30） |
| `zt_stakeholder` | **干系人**（项目集/项目相关的人）。**不是团队成员**：团队是"要干活的人"（有工时），干系人是"需要知情/被影响的人"（甲方、领导、外部顾问）。`type`（内部/外部）**由 `from`（团队/公司/外部）推导**；同一对象下同一人只能有一条（3.29） |
| `zt_team` | **项目/执行团队成员**（同一张表用 `type` 区分 project/execution）。**「团队人数」来自这张表**（禅道 `fetchMemberCountByIdList`），可用工时 = `days × hours`（默认 7）。成员是**物理增删**：本表没有 `deleted` 列，逻辑删除会撞 `UNIQUE(root,type,account)`（3.27） |
| `zt_todo` | **待办**（个人清单，`zt_task` 不是一回事）：可以完全不挂对象（`type='custom'`），也可以指向 task/bug/story/testtask 当快捷入口。`account` 是「谁的清单」、`assignedTo` 是「谁来做」；「我的待办」的条件是 `assignedTo/finishedBy/closedBy = 我`（3.25） |
| `zt_effort` | **工时流水**（禅道原表是通用的：`objectType`+`objectID`，本实现只落任务）。一条记录 = 谁、哪天、为哪个任务花了多久（`consumed`）、**这之后还剩多久（`left`）**。任务的已消耗 = 所有工时之和，剩余 = **最后一条工时声明的值**（3.23） |
| `zt_doccontent` | **文档版本内容**。`(doc, version)` 唯一，**version=0 是草稿位**：草稿期间反复保存都改这一行，发布时才升成 v1 —— 这是本项目第二个「追加式版本链」，第一个是 `zt_storyspec` |
| `zt_holiday` | **节假日/补班**：一张表两种记录（`type=holiday` 不算工作日、`type=working` 算工作日）。`getActualWorkingDays` 是全项目**工作日口径的唯一出口**（燃尽图横轴、工期计算都走它），注意它是**左闭右开**的（3.42） |
| `zt_entry` | **应用接入**：第三方系统免登录进来用的应用定义（`code` + 32 位 `key` + 免密开关 + IP 白名单）。`key`、`desc` 都是 MySQL 关键字，DO 里要加反引号。内置的 `code='gitfox'` 不出现在列表里（3.43） |
| `zt_log` | **通用调用日志**（`objectType` + `objectID` 指向任意对象）。目前只有应用接入在写（`objectType='entry'`），禅道里 `webhook` 也用它。**没有 `deleted` 列**，只增不改（3.43） |
| `zt_score` | **积分流水**：一条 = 谁因为哪个模块的哪个动作得了多少分，`before`/`after` 是总分快照。规则（38 条 + 扩展加成）在代码里（`ScoreRules.java`）。**没有 `deleted` 列**，只增不改；`desc`、`before` 都是保留字。总分 = `SUM(score)`（禅道冗余在 `zt_user.score`，本项目不迁用户表）（3.45） |
| `zt_company` | **公司信息**（禅道界面叫「组织视图」）。两个容易忽略的点：`admins` 是**逗号串**（如 `,admin,`），是禅道判超管的唯一依据；`id=1` 是本公司，`getOutsideCompanies()` 就是 `id != 1`（服务外部干系人的所属公司）。编辑表单里**没有 admins**，`website`/`backyard` 只填 `http://` 会被清空（3.44） |

`zt_story` 里**没有** `spec`/`verify` 两列 —— 这是禅道的原始设计，描述与验收标准只存在于
`zt_storyspec`，读的时候按版本叠加。这一点是迁移时最容易做错的地方。

### 2.2 后端（`yudao-module-zentao`，537 个 Java 文件 / 57,036 行）

500 个 REST 端点：

```
需求 CRUD      POST   /zentao/story/create
              PUT    /zentao/story/update          普通编辑，不产生新版本
              PUT    /zentao/story/change          正式变更，版本号 +1 并留存历史
              DELETE /zentao/story/delete
              DELETE /zentao/story/delete-list
              GET    /zentao/story/get             支持 ?version= 读历史版本
              GET    /zentao/story/spec-list       版本历史
              GET    /zentao/story/page
              GET    /zentao/story/list-by-product

状态机         PUT    /zentao/story/close           关闭（含 duplicate 三重校验）
              PUT    /zentao/story/activate

需求评审       PUT    /zentao/story/start-review    提交评审
              PUT    /zentao/story/review            评审表决（含聚合与状态流转）
              GET    /zentao/story/review-list

需求分层       GET    /zentao/story/type-list       业务需求/用户需求/研发需求（同表靠 type 区分）
              GET    /zentao/story/type-summary    各类型数量（需求池页签角标，GROUP BY type）
              GET    /zentao/story/type-tree       分层树：业务需求 → 用户需求 → 研发需求
              （page 支持 ?type= / ?types= 按分层类型过滤；父子规则见 3.31）

用例库 CRUD    POST   /zentao/caselib/create        名称全局唯一（zt_testsuite.deleted=0）
              PUT    /zentao/caselib/update
              DELETE /zentao/caselib/delete        库内还有用例时拒绝删除
              GET    /zentao/caselib/get / page / list
库内用例       GET    /zentao/caselib/case-page     product=0 + lib=<库>，带「源用例已更新」标记
              GET    /zentao/caselib/case-get
              POST   /zentao/caselib/create-case     库内建用例（版本/步骤/评审规则复用 testcase）
产品用例→库     GET    /zentao/caselib/can-import-case-page   已导入的按 fromCaseID 排除
              POST   /zentao/caselib/import-to-lib          复制用例与步骤 + 同步模块树

看板空间       POST   /zentao/kanban/space/create    私人/协作/公共空间；私人空间负责人=创建人
              PUT    /zentao/kanban/space/update / activate / close
              DELETE /zentao/kanban/space/delete      空间下还有看板时拒绝
              GET    /zentao/kanban/space/get / page / list
看板           POST   /zentao/kanban/create          **自动建默认布局**：区域+分组+默认泳道+四个默认列+所有格子
              PUT    /zentao/kanban/update / setting   setting 管 displayCards/showWIP/流式列宽/对齐
              PUT    /zentao/kanban/activate / close
              DELETE /zentao/kanban/delete           级联软删区域/泳道/列/卡片，格子物理清理
              GET    /zentao/kanban/get / page / list
看板视图       GET    /zentao/kanban/data            区域 → 泳道 × 列 + 卡片（含 overWip 提示）
区域/泳道/列   POST   /zentao/kanban/region|lane|column/create
              PUT    /zentao/kanban/region|lane|column/update（列含 setWIP）
              PUT    /zentao/kanban/column/archive / restore
              DELETE /zentao/kanban/region|lane|column/delete
              GET    /zentao/kanban/region|lane|column/list
卡片           POST   /zentao/kanban/card/create      位置记在 泳道×列 的格子里
              PUT    /zentao/kanban/card/update / finish / activate / archive / restore
              POST   /zentao/kanban/card/move        换列/换泳道，按源泳道类型摘除后再追加
              GET    /zentao/kanban/card/get / page
              DELETE /zentao/kanban/card/delete      物理删除并从所有格子摘掉

度量字典       GET    /zentao/metric/dict          目的/范围/对象/单位/时间维度/计算方式
度量概览       GET    /zentao/metric/summary       内置数 / 已迁移口径数 / 数据量 / 上次计算时间
度量项         GET    /zentao/metric/list / page / get    定义 + 口径说明 + 是否已迁移 + 数据量
度量计算       POST   /zentao/metric/calc           跑口径 → 按周期清旧数据 → 写 zt_metriclib → 回写
              POST   /zentao/metric/calc-all       批量算所有已迁移口径
度量数据       GET    /zentao/metric/data           维度 + 期间 + 值（带对象名解析与分页）

数据视图       GET    /zentao/bi/dataview/list / page / get
              POST   /zentao/bi/dataview/create        只读 SELECT；字段按 SQL 自动解析
              PUT    /zentao/bi/dataview/update
              DELETE /zentao/bi/dataview/delete        被图表引用时拒绝
              GET    /zentao/bi/dataview/preview       预览（结果包一层 LIMIT，最多 200 行）
              POST   /zentao/bi/dataview/preview-sql   试跑 SQL（过白名单再执行）
图表           GET    /zentao/bi/chart/list / page / get
              POST   /zentao/bi/chart/create           settings={维度,指标,聚合,limit,sort}
              PUT    /zentao/bi/chart/update           版本 +1
              DELETE /zentao/bi/chart/delete
              GET    /zentao/bi/chart/data             按维度分组 + 聚合 → [{name,value}] 喂 ECharts
BI 字典        GET    /zentao/bi/dict                 图表类型 / 聚合方式 / 排序 / 可用数据视图

操作时间线     GET    /zentao/action/list        对象维度的日志（含字段变化 + 动作渲染文本）
动态           GET    /zentao/action/dynamic     按人/周期(today..all)/产品/项目/执行过滤
备注           POST   /zentao/action/comment     对任意对象发备注（就是一条 commented 动作）
              PUT    /zentao/action/comment/update  只能改自己的
回收站         GET    /zentao/action/trash       「删除」动作列表 + 回查对象名与能否还原
              POST   /zentao/action/undelete    还原（对象表 deleted=0）+ 记一条 undeleted
              POST   /zentao/action/hide        从回收站隐藏（对象仍删除）+ 记一条 hidden
              POST   /zentao/action/hide-all    一次隐藏全部可还原的删除记录

产品           GET    /zentao/product/simple-list
产品 CRUD      POST   /zentao/product/create     名称唯一校验
              PUT    /zentao/product/update
              DELETE /zentao/product/delete     产品下有需求时拒绝删除
              DELETE /zentao/product/delete-list
              GET    /zentao/product/get        附带实时统计
              GET    /zentao/product/page
产品状态       PUT    /zentao/product/close
              PUT    /zentao/product/activate

项目集 CRUD     POST   /zentao/program/create     同级唯一 + 日期区间校验；path/grade 自动算（逗号格式）
               PUT    /zentao/program/update       换上级会重算整棵子树的 path/grade
               DELETE /zentao/program/delete       下级还有项目集/项目/产品时拒绝
               DELETE /zentao/program/delete-list
               GET    /zentao/program/get          附带下级项目集/项目/产品计数
               GET    /zentao/program/page / list / simple-list / list-by-parent
               GET    /zentao/program/project-list 项目集下的项目（项目靠 parent 归属）
               GET    /zentao/program/product-list 项目集下的产品（zt_product.program）
项目集状态     PUT    /zentao/program/start / suspend / activate / close

项目 CRUD      POST   /zentao/project/create     名称唯一 + 模型枚举 + 日期区间校验 + 所属项目集必须是项目集
              PUT    /zentao/project/update
              DELETE /zentao/project/delete       有执行时拒绝删除
              DELETE /zentao/project/delete-list
              GET    /zentao/project/get
              GET    /zentao/project/page
              GET    /zentao/project/simple-list
              GET    /zentao/project/list-by-parent
项目状态       PUT    /zentao/project/start        未开始/已挂起 → 进行中
              PUT    /zentao/project/suspend
              PUT    /zentao/project/activate
              PUT    /zentao/project/close        按 multiple/hasProduct 级联

执行 CRUD      POST   /zentao/execution/create     必须挂到项目下；type 必须是 sprint/stage/kanban
              PUT    /zentao/execution/update       不允许改所属项目（防止执行漂移）
              DELETE /zentao/execution/delete
              DELETE /zentao/execution/delete-list
              GET    /zentao/execution/get          传项目 id 会报「是项目而非执行」
              GET    /zentao/execution/page         永远带 type IN (sprint,stage,kanban)
              GET    /zentao/execution/list-by-project
              GET    /zentao/execution/count-by-project
执行状态       PUT    /zentao/execution/start
              PUT    /zentao/execution/suspend
              PUT    /zentao/execution/activate
              PUT    /zentao/execution/close

燃尽图         GET    /zentao/execution/burn-data    三条线（实际/理想/延期）+ 每日快照
              POST   /zentao/execution/compute-burn  把当天汇总写成一条快照（REPLACE 当天那行）

任务 CRUD      POST   /zentao/task/create
              PUT    /zentao/task/update
              DELETE /zentao/task/delete
              DELETE /zentao/task/delete-list
              GET    /zentao/task/get
              GET    /zentao/task/page
              GET    /zentao/task/list-by-story

任务状态机     PUT    /zentao/task/start            未开始/已暂停 → 进行中
              PUT    /zentao/task/finish           登记工时；剩余归零才算完成
              PUT    /zentao/task/close
              PUT    /zentao/task/cancel
              PUT    /zentao/task/activate

缺陷 CRUD      POST   /zentao/bug/create
              PUT    /zentao/bug/update
              DELETE /zentao/bug/delete
              DELETE /zentao/bug/delete-list
              GET    /zentao/bug/get
              GET    /zentao/bug/page
              GET    /zentao/bug/list-by-story

缺陷状态机     PUT    /zentao/bug/resolve          含 duplicate/fixed 两条联动校验
              PUT    /zentao/bug/close
              PUT    /zentao/bug/activate           激活次数 +1

分支/平台      POST   /zentao/branch/create       产品类型必须是 branch/platform
              PUT    /zentao/branch/update
              PUT    /zentao/branch/close          关闭会顺带取消默认标记
              PUT    /zentao/branch/activate
              PUT    /zentao/branch/set-default    branchId=0 表示默认还原到主干
              DELETE /zentao/branch/delete         分支下有需求/缺陷/模块时拒绝
              DELETE /zentao/branch/delete-list
              GET    /zentao/branch/get            id=0 返回虚拟主干「主干」
              GET    /zentao/branch/page           指定产品时第一页补一行主干
              GET    /zentao/branch/list-by-product
              GET    /zentao/branch/count-by-product

组织权限视图   GET    /zentao/organization/user-list          用户列表（zt_user 视角，含可访问模块）
（只读）      GET    /zentao/organization/user-permissions   某人的禅道权限（跨角色并集）
              GET    /zentao/organization/role-list          权限包（zt_group 视角）
              GET    /zentao/organization/role-permissions   权限包的 (模块, 方法) 明细
              GET    /zentao/organization/dept-tree          部门树（补出禅道风格的 grade/path）
              GET    /zentao/organization/mapping            禅道 → yudao 字段映射对照表

阶段模板       POST   /zentao/stage/create         同组内名称唯一；占比累计 ≤ 100%
              POST   /zentao/stage/batch-create    批量新建（禅道 batchCreate）
              PUT    /zentao/stage/update           修改时按 total+new-old 校验占比
              DELETE /zentao/stage/delete           只删模板，已生成的项目阶段不受影响
              PUT    /zentao/stage/update-order     按传入编号顺序重排
              GET    /zentao/stage/get / list / list-by-project-type
              GET    /zentao/stage/total-percent    占比合计（前端据此提示还能填多少）
              GET    /zentao/stage/type-list
项目阶段       POST   /zentao/stage/generate       按模板为项目生成 zt_project(type='stage')
              GET    /zentao/stage/project-stages
              DELETE /zentao/stage/delete-project-stages

项目需求范围   POST   /zentao/projectstory/link-product     项目关联产品（可限定分支/计划）
              DELETE /zentao/projectstory/unlink-product   产品下还有需求时拒绝
              GET    /zentao/projectstory/product-list
              PUT    /zentao/projectstory/link-story       批量纳入需求；draft/reviewing/closed 跳过
              DELETE /zentao/projectstory/unlink-story     子执行已关联时拒绝；剩余重新编号
              GET    /zentao/projectstory/story-list       带「版本已变更」标记
              GET    /zentao/projectstory/unlinked-story-list
              GET    /zentao/projectstory/story-projects   需求被哪些项目关联
              GET    /zentao/projectstory/count

发布 CRUD      POST   /zentao/release/create     版本号全局唯一；自动生成影子构建；可从构建同步需求/Bug
              PUT    /zentao/release/update       名称/构建/日期变化会同步影子构建
              DELETE /zentao/release/delete       被别的发布包含时拒绝；连带删影子构建与关系
              GET    /zentao/release/get
              GET    /zentao/release/page
              GET    /zentao/release/list-by-product
发布状态       PUT    /zentao/release/publish      状态→已发布，写实际发布日期，需求阶段→已发布
              PUT    /zentao/release/change-status fail/terminate/wait
发布清单       GET    /zentao/release/story-list
              GET    /zentao/release/unlinked-story-list
              GET    /zentao/release/bug-list           type=bug / leftBug
              GET    /zentao/release/unlinked-bug-list
              PUT    /zentao/release/link-story
              DELETE /zentao/release/unlink-story
              PUT    /zentao/release/link-bug
              DELETE /zentao/release/unlink-bug

构建 CRUD      POST   /zentao/build/create      多分支产品必须选分支；集成构建 execution=0、分支取子构建并集
              PUT    /zentao/build/update       被集成构建/发布引用的构建不能改产品/执行/子构建
              DELETE /zentao/build/delete
              GET    /zentao/build/get          集成构建会合并子构建的需求/Bug
              GET    /zentao/build/page
              GET    /zentao/build/list-by-product
              GET    /zentao/build/list-by-execution
              GET    /zentao/build/branch-label 产品类型 → 分支/平台文案
构建关联       GET    /zentao/build/story-list
              GET    /zentao/build/unlinked-story-list
              GET    /zentao/build/bug-list
              GET    /zentao/build/unlinked-bug-list
              PUT    /zentao/build/link-story
              DELETE /zentao/build/unlink-story
              PUT    /zentao/build/link-bug     未解决的 Bug 会被自动置为「已解决」
              DELETE /zentao/build/unlink-bug   不回退解决状态（与禅道一致）

计划 CRUD      POST   /zentao/plan/create       多分支产品必须选分支；子计划日期须在父计划内
              PUT    /zentao/plan/update       分支范围缩小会自动摘掉超范围的需求/Bug
              DELETE /zentao/plan/delete       有子计划的父计划不能删；删除会摘掉关联需求/Bug
              GET    /zentao/plan/get          带需求数/Bug 数实时统计
              GET    /zentao/plan/page
              GET    /zentao/plan/list-by-product
计划状态       PUT    /zentao/plan/start        未开始 → 进行中
              PUT    /zentao/plan/finish       写入完成时间
              PUT    /zentao/plan/close        reason=done/cancel；done 会顺带写完成时间
              PUT    /zentao/plan/activate     已关闭 → 进行中（禅道语义）
计划关联       GET    /zentao/plan/story-list
              GET    /zentao/plan/unlinked-story-list
              PUT    /zentao/plan/link-story   type=story 独占；其它类型累加逗号列表
              DELETE /zentao/plan/unlink-story
              PUT    /zentao/plan/link-bug
              DELETE /zentao/plan/unlink-bug
              GET    /zentao/plan/reason-list

模块树         POST   /zentao/module/create       通用树：root + type + branch 定位一棵树
              PUT    /zentao/module/update         移动模块会递归重算子孙 path/grade
              PUT    /zentao/module/update-order   批量排序
              DELETE /zentao/module/delete         连带子孙；挂在被删模块上的数据改挂到上级
              GET    /zentao/module/get
              GET    /zentao/module/list           平铺
              GET    /zentao/module/tree           嵌套
              GET    /zentao/module/type-list      story/task/bug/case/caselib/doc/api/line

操作日志       GET    /zentao/action/list           按对象取时间线，附带字段级变更明细

测试单         POST   /zentao/testtask/create       结束日期不能早于开始日期
              PUT    /zentao/testtask/update
              DELETE /zentao/testtask/delete         排入的用例与执行历史一起清理
              GET    /zentao/testtask/get            带用例数/通过/失败/阻塞/未执行统计
              GET    /zentao/testtask/page           type 是逗号列表，FIND_IN_SET 匹配
              GET    /zentao/testtask/list-by-product / list-by-execution / simple-list
测试单状态     PUT    /zentao/testtask/start          只有 wait/blocked 能开始，写实际开始日期
              PUT    /zentao/testtask/block
              PUT    /zentao/testtask/activate       清空完成时间与测试总结
              PUT    /zentao/testtask/close          必填完成时间；≥ 计划开始、≤ 明天
用例编排       POST   /zentao/testtask/link-case      已排过的只更新版本与指派，**保留执行结果**
              DELETE /zentao/testtask/unlink-case    连带清理执行历史
              PUT    /zentao/testtask/assign-case
              GET    /zentao/testtask/run-list       用例标题/指派/最近结果/用例是否已变更
              GET    /zentao/testtask/linkable-list  还没排进来的同产品用例
              GET    /zentao/testtask/run-list-by-case
执行           POST   /zentao/testtask/run-case       步骤结果 → 用例结果（fail 优先），一次写三处
              GET    /zentao/testtask/result-list     某条 run 的执行历史
执行→缺陷      POST   /zentao/testtask/create-bug     从失败的用例执行建缺陷（写来源用例+版本+测试单）
              GET    /zentao/testtask/bug-list-by-case / bug-list-by-task

需求转任务     POST   /zentao/task/batch-create-from-story  把一个需求拆成若干任务（需求版本冻结在任务上）

需求分解       GET    /zentao/story/child-list        某需求分解出来的子需求
              POST   /zentao/story/subdivide         把已有需求挂到父需求下（冻结父需求当前版本）
              POST   /zentao/story/batch-create-child 一条需求拆成 N 条（只给标题，其余继承）

工时明细       POST   /zentao/effort/create        登记工时；登记完自动重算任务的已消耗/剩余/状态
               PUT    /zentao/effort/update        改一条工时；任务工时总和跟着重算
               DELETE /zentao/effort/delete        删一条；删的是最后一条则剩余回到上一条声明的值
               GET    /zentao/effort/get / list    单条 / 某任务的全部工时（按日期正序）
               GET    /zentao/effort/page          按任务/账号/项目/执行/日期区间查，回填任务名
               GET    /zentao/effort/summary       按账号汇总（工时报表）
               GET    /zentao/effort/task-stat     任务的预计/已消耗/剩余/状态 + 明细

工作量估算     GET    /zentao/workestimation/get      没有估算时返回 null（不是错误）
               PUT    /zentao/workestimation/save     工期/总人工成本服务端算，不接受入参
项目计划视图   GET    /zentao/projectplan/plan-list    项目关联产品下的计划并集
项目构建视图   GET    /zentao/projectbuild/build-list  项目下所有执行的构建 + 直挂项目的构建
项目发布视图   GET    /zentao/projectrelease/release-list  FIND_IN_SET 反查 zt_release.project

干系人         POST   /zentao/stakeholder/create       type 由 from 推导；同一对象下同一人不能重复
               POST   /zentao/stakeholder/batch-create   批量（已存在的静默跳过）
               PUT    /zentao/stakeholder/update
               DELETE /zentao/stakeholder/delete          按记录编号
               DELETE /zentao/stakeholder/delete-by-user  按「对象 + 账号」（禅道 delete(userID)）
               GET    /zentao/stakeholder/get / list      列表：关键干系人排前面、内部人员回填姓名
               GET    /zentao/stakeholder/list-by-user    我作为干系人参与的对象
               GET    /zentao/stakeholder/type-list / from-list

团队           GET    /zentao/team/list          成员列表（带姓名与可用工时 = 天数 × 每天小时数）
               GET    /zentao/team/total-hours   团队可用工时合计
               POST   /zentao/team/add-member
               PUT    /zentao/team/update-member 改角色/天数/小时数/受限
               DELETE /zentao/team/remove-member 物理删除（没有 deleted 列）
               PUT    /zentao/team/update-members 全量保存（先删后插，老成员加入日期保留）

待办           POST   /zentao/todo/create        不填日期默认今天；type 非 custom/cycle 时必须带 objectID
               POST   /zentao/todo/batch-create   一次给一批名称
               PUT    /zentao/todo/update
               DELETE /zentao/todo/delete / delete-list
               PUT    /zentao/todo/start / finish / close / activate
               PUT    /zentao/todo/assign         指派给别人
               PUT    /zentao/todo/import-to-today 把之前没完成的待办挪到今天（禅道 import2Today）
               GET    /zentao/todo/get / page     管理视角：归属账号/指派人/状态/类型/日期区间
               GET    /zentao/todo/my-list        「我的待办」：assignedTo/finishedBy/closedBy 命中我
               GET    /zentao/todo/type-list / status-list / pri-list

我的地盘       GET    /zentao/my/overview       待办（今天/未完成/已过期）+ 指派给我的任务/缺陷/需求 + 本月工时
（查询层）     GET    /zentao/my/task-page      等效于 task/page?assignedTo=我
               GET    /zentao/my/bug-page / story-page / effort-page
               GET    /zentao/my/todo-list       browseType：today/tomorrow/thisweek/before/future/all/cycle
               GET    /zentao/my/action-list     我最近的动态（zt_action.actor = 我）
               GET    /zentao/my/project-page    我参与的项目（负责人字段 或 FIND_IN_SET(team)）
               GET    /zentao/my/execution-page  我参与的执行（同一口径，强制 type in 执行类型）
               GET    /zentao/my/team-list       我的团队：我在哪些项目/执行里、什么角色、可用工时
               GET    /zentao/my/testtask-page   我的测试单（owner 或 createdBy）
               GET    /zentao/my/case-page       我的用例（我创建 或 我评审过：两次查合并去重）
               GET    /zentao/my/doc-page        我的文档（创建人/指派给/最后修改人），章节默认剔除
               GET    /zentao/my/calendar        我的日历：待办/任务/测试单按天归组（month=YYYY-MM）

 报表           GET    /zentao/report/options         可选年份（从第一条动作算起）/部门树/人员
 （只读聚合）   GET    /zentao/report/annual-data     年度数据：不传参数=全公司、dept=部门、account=个人
                GET    /zentao/report/reminder-list   每日提醒：快到期的任务/缺陷/待办/测试单/看板卡片
                GET    /zentao/report/output          产出统计：各类对象本年各动作的条数
                GET    /zentao/report/project-status  项目状态总览（按团队成员过滤）

 测试仪表盘     GET    /zentao/qa/dashboard         按产品的质量统计 + 待处理缺陷/待评审用例/未完成测试单

 代码库         POST   /zentao/repo/create          只支持本地 git 仓库（路径必须含 .git）
               PUT    /zentao/repo/update          改路径会清掉「同步到哪儿了」
               DELETE /zentao/repo/delete          提交记录与改动文件一起物理清理
               GET    /zentao/repo/get / page / simple-list
               POST   /zentao/repo/sync            跑 git log（增量）→ 提交 + 改动文件 + 对象关联
               GET    /zentao/repo/commit-page     可按 repo，也可按 objectType/objectID 反查
               GET    /zentao/repo/commit-get      提交详情：改动文件 + 关联对象

 节假日         GET    /zentao/holiday/list / years / get
               POST   /zentao/holiday/create          type=holiday 假期 / working 补班
               PUT    /zentao/holiday/update
               DELETE /zentao/holiday/delete
               GET    /zentao/holiday/working-days    实际工作日（补班算、假期不算、周末不算；左闭右开）

测试报告       POST   /zentao/testreport/create       汇总的测试单必须与报告同产品
              PUT    /zentao/testreport/update         条件变了才重算留档清单
              DELETE /zentao/testreport/delete
              GET    /zentao/testreport/get            用例数/通过/失败**读时现算**
              GET    /zentao/testreport/page
              GET    /zentao/testreport/preview        建报告前先预览汇总（不落库）
用例集         POST   /zentao/testreport/suite/create     同产品名称唯一
              PUT    /zentao/testreport/suite/update
              DELETE /zentao/testreport/suite/delete     集合里还有用例时拒绝
              GET    /zentao/testreport/suite/get / page / list-by-product
              POST   /zentao/testreport/suite/link-case  幂等；用例必须与集合同产品
              DELETE /zentao/testreport/suite/unlink-case
              GET    /zentao/testreport/suite/case-list / unlinked-case-list
              GET    /zentao/testtask/status-list / result-enum-list

测试用例       POST   /zentao/testcase/create     步骤的 parent 是「本次提交数组里父步骤组的 0 基下标」
              PUT    /zentao/testcase/update       **只有步骤变化才 version+1 并打回「待评审」**
              DELETE /zentao/testcase/delete       步骤与版本快照一起物理清理
              GET    /zentao/testcase/get          支持 ?version= 读历史版本（标题/前置条件取快照 + 该版本步骤）
              GET    /zentao/testcase/page         module 展开含子树；needConfirm 只看关联需求已升版的
              GET    /zentao/testcase/step-list    带 1./1.1 层级编号
              GET    /zentao/testcase/spec-list    版本历史（每条带该版本步骤数）
              GET    /zentao/testcase/list-by-story
              GET    /zentao/testcase/type-list / stage-list / status-list
用例评审       PUT    /zentao/testcase/review      只有 wait 能评审；结果 normal/blocked/investigate
              PUT    /zentao/testcase/confirm-story-change   把冻结的 storyVersion 追平到需求当前版本

文档库         POST   /zentao/doc/lib/create       product/project/execution 主库（main=1）或 custom 空间库
              PUT    /zentao/doc/lib/update
              DELETE /zentao/doc/lib/delete       主库不允许删除；库内还有文档时也拒绝
              GET    /zentao/doc/lib/get / list / type-list
文档           POST   /zentao/doc/create           type=chapter 建章节（无正文）；url 校验链接；attachment 必须有附件
              PUT    /zentao/doc/update           正文/标题变了才 version+1；只改基础信息版本不动
              DELETE /zentao/doc/delete           章节下还有子节点时拒绝
              GET    /zentao/doc/get              支持 ?version= 读历史版本
              GET    /zentao/doc/view             浏览，views +1（仅已发布文档计数）
              GET    /zentao/doc/page             spaceType+spaceObjectID 可跨库查；excludeChapter 排除章节
              GET    /zentao/doc/list-by-lib
              GET    /zentao/doc/chapter-tree     只含章节，节点带直属文档数
              GET    /zentao/doc/content-list     版本历史（草稿 version=0 排最后）
              GET    /zentao/doc/type-list / status-list
文档结构       PUT    /zentao/doc/move           可跨库；不能移到自己的子节点下；子树 path/grade 一起重算
              PUT    /zentao/doc/publish        草稿位 v0 → v1

附件           POST   /zentao/file/upload           multipart；objectID 可空（配合 gid 两阶段上传）
（公共能力）   POST   /zentao/file/bind-by-gid       对象保存后按 gid 一次性补绑
              GET    /zentao/file/list               某对象的附件
              GET    /zentao/file/list-by-gid        某临时分组下「待绑定」的附件
              GET    /zentao/file/get / count
              GET    /zentao/file/download           返回存储地址并把 downloads +1
              PUT    /zentao/file/rename             扩展名跟着文件名一起更新
              DELETE /zentao/file/delete
              DELETE /zentao/file/delete-by-object   对象被删时清理附件
积分           GET    /zentao/score/rule         38 条规则（分值/次数上限/时间窗 + 扩展加成说明）
              GET    /zentao/score/page          流水（不传 account 就是「我的积分」）
              GET    /zentao/score/total         总分（SUM 流水）+ 昨日新增 + 禅道 getNotice 的提示语
              POST   /zentao/score/create        计分：按规则表算分，命中次数/时间窗就静默跳过
应用接入       GET    /zentao/entry/page         列表（gitfox 是内置应用，照禅道过滤掉）
（第三方       GET    /zentao/entry/get / simple-list / random-key
 免登录）      POST   /zentao/entry/create        密钥留空自动生成 32 位；免密登录可不绑账号
              PUT    /zentao/entry/update
              DELETE /zentao/entry/delete
              GET    /zentao/entry/log-page      调用日志（通用表 zt_log，objectType=entry）
              GET    /zentao/entry/sign          签名助手：md5(code+key+time) 或 md5(md5(query)+key)
              POST   /zentao/entry/verify        **@PermitAll**：校验 code/token/time/IP/账号 + 记日志 + 回写 calledTime
公司信息       GET    /zentao/company/get-first  禅道 getFirst()：id 最小的一条就是「本公司」
（组织视图）   GET    /zentao/company/get / list / page
              GET    /zentao/company/outside-list id != 1 的公司，返回 text/value/keys（禅道 ajaxGetOutsideCompany 原样结构）
              POST   /zentao/company/create       禅道没有独立入口（干系人流程里顺手 insert），这里显式提供
              PUT    /zentao/company/update       name 唯一且不过滤已删除；website/backyard 只填 http:// 会被清空；**没有 admins 字段**
              GET    /zentao/company/admins       超管口径对照：zt_company.admins 逗号串 ↔ yudao super_admin 角色
              （组织成员复用 /zentao/organization/*，组织动态复用 /zentao/action/dynamic；**没有删除接口**，禅道也没有）
```

### 2.3 前端（Vue3 + Element Plus，130 个文件 / 24,299 行）

```
src/api/zentao/{story,product,plan,build,release,projectstory,stage,organization,branch,module,project,execution,action,task,bug,file,doc,testcase,testtask,testreport,caselib,kanban,metric,bi,effort,program,todo,my,team,report,qa,repo,holiday,entry,company,score,search,dimension,api,webhook}/index.ts
src/views/zentao/my/                   我的地盘（查询层 + 待办）
├── index.vue                          概览卡片 + 六个 Tab（待办/我的任务/缺陷/需求/工时/动态）
└── TodoForm.vue                       新建/修改待办（类型、关联对象、私有开关）
src/views/zentao/report/               报表（只读聚合：年度数据 / 每日提醒 / 产出统计）
└── index.vue                          三个 Tab + 年份/部门/人员三种视角切换
src/views/zentao/qa/                   测试仪表盘（质量统计 + 三个列表块）
└── index.vue                          汇总卡片 + 按产品质量统计 + 待处理缺陷/待评审用例/未完成测试单
src/views/zentao/repo/                 代码库（本地 git 仓库 + 提交记录）
└── index.vue                          代码库列表/编辑 + 同步 + 提交抽屉 + 提交详情（文件与关联对象）
src/views/zentao/holiday/              节假日（假期/补班 + 工作日口径）
└── index.vue                          列表 + 新增弹窗 + 工作日试算小工具
src/views/zentao/caselib/              用例库（与用例集共用 zt_testsuite 的第二类记录）
└── index.vue                          左库列表 + 右库内用例；建库/建用例/从产品导入/详情
src/views/zentao/program/              项目集（项目集/项目/执行共表的第一种角色）
├── constants.ts                       状态枚举
├── index.vue                          前端建树（parent 自关联）+ 详情抽屉（项目 / 产品两个 Tab）
└── ProgramForm.vue                    新建/修改，上级项目集用下拉（不列项目与执行）
src/views/zentao/product/              产品
├── constants.ts / index.vue
└── ProductForm.vue
src/views/zentao/project/              项目
├── constants.ts / index.vue           列表含「查看任务」跳转与状态流转按钮
└── ProjectForm.vue
src/views/zentao/execution/            执行（迭代 / 阶段 / 看板）
├── constants.ts / index.vue           类型 + 状态过滤，所属项目下拉取项目列表
└── ExecutionForm.vue                  明确提示「执行与项目共用一张表」
src/views/zentao/branch/               分支 / 平台（产品维度）
├── constants.ts / index.vue           主干行不可关闭/删除，可设为默认
└── BranchForm.vue                     文案随产品类型在「分支 / 平台」之间切换
src/views/zentao/organization/         组织与权限（禅道视角的只读视图）
└── index.vue                          四个 Tab：用户 / 权限包 / 部门树 / 迁移映射对照
src/views/zentao/stage/                阶段（瀑布流程）
├── constants.ts                       阶段类型 + 项目流程类型
├── index.vue                          上半部分阶段模板（含占比合计标签）+ 下半部分项目阶段
└── StageForm.vue                      模板表单，带「已占用 X%，最多还能填 Y%」提示
src/views/zentao/projectstory/         项目需求范围
├── index.vue                          项目/执行切换 + 关联产品 + 需求范围（版本变更标记）
└── ProjectStoryLinkDrawer.vue         已纳入 / 可纳入两栏，勾选纳入
src/views/zentao/release/              发布
├── constants.ts / index.vue           状态、里程碑、三份清单数量、发布/停止维护操作
├── ReleaseForm.vue                    构建多选 + 「同步构建数据」开关 + 状态/日期联动必填
└── ReleaseLinkDrawer.vue              三个 Tab：需求 / 解决的 Bug / 遗留的 Bug
src/views/zentao/build/                构建
├── constants.ts / index.vue           集成/被引用标记，需求数与 Bug 数可点开关联抽屉
├── BuildForm.vue                      集成构建开关 + 子构建多选
└── BuildLinkDrawer.vue                两个 Tab：需求 / Bug（关联 Bug 会提示自动解决）
src/views/zentao/plan/                 产品计划
├── constants.ts / index.vue           待定/父计划/子计划标记，需求数可点开关联抽屉
├── PlanForm.vue                       分支多选、待定开关、父计划选择
└── PlanLinkStoryDrawer.vue            左右两栏：已关联 / 未关联候选
src/views/zentao/effort/               工时明细（流水账 + 报表）
└── index.vue                          左列表格（任务/账号/日期筛选）+ 右侧「按账号汇总」卡片
src/views/zentao/components/           跨模块复用组件
├── BranchSelect.vue                   分支/平台下拉（按产品类型决定是否显示、文案）
├── ModuleSelect.vue                   模块树选择（el-tree-select，按 root+type+branch 拉树）
├── EffortForm.vue                     登记/修改工时（消耗、剩余、起止、工作内容）
├── EffortPanel.vue                    任务行的「工时」抽屉：预计/已消耗/剩余 + 明细，任务页复用
├── TeamPanel.vue                      项目/执行行的「团队」抽屉：成员增删改 + 可用工时合计
├── BurnChart.vue                      执行行的「燃尽图」抽屉：ECharts 三条线 + 每日快照表 + 重新计算
└── StakeholderPanel.vue               项目集/项目行的「干系人」抽屉：内部/外部、关键标记、来源
src/views/zentao/module/               模块树（通用树）
├── constants.ts / index.vue           树类型 + 根对象 + 分支三级定位，表格树 + 本地过滤
└── ModuleForm.vue                     上级模块用 el-tree-select，自动排除自己与子孙
src/views/zentao/story/                需求（含详情抽屉与操作时间线）
├── constants.ts                       枚举常量 + 字段中文标签
├── index.vue                          列表页
├── StoryForm.vue / StoryChangeForm.vue / StoryCloseForm.vue
├── StoryReviewStartForm.vue
├── StoryDetailDrawer.vue              四 Tab：基本信息 / 版本历史 / 评审情况 / 操作日志
└── ActionTimeline.vue                 操作时间线（字段级变更 + diff 弹层）
src/views/zentao/task/                 任务
├── constants.ts / index.vue           含「所属执行」下拉与列（按执行过滤）+ 每行「工时」抽屉
├── TaskForm.vue
└── TaskFinishForm.vue                 完成任务（工时三件套登记，含归零规则提示）
src/views/zentao/bug/                  缺陷
├── constants.ts / index.vue
├── BugForm.vue
└── BugResolveForm.vue                 解决缺陷（含两条联动校验的前端预校验）
```

### 2.4 端到端验证结果

**验证总量**：43 个接口回归脚本共 **1704 项断言**（全部通过；后端 / MySQL / Redis / 前端全跑在 `192.168.0.119`，
查库用 `MYSQL_TARGET=direct` 直连 3307 端口），
**21 个页面**浏览器实测全部可用（`ui-check/all-pages.mjs`）；另有**模块专项浏览器检查**
（`resume-verification-remote.sh` 固定跑 20 个，`deploy/ui-check/` 下共 40 个脚本 = 1 共用登录 + 1 页面巡检 + 38 个专项/页内交互），
团队抽屉另有专项浏览器检查（`ui-check/team.mjs`）、
干系人另有专项浏览器检查（`ui-check/stakeholder.mjs`）、
需求分层另有专项浏览器检查（`ui-check/storytype.mjs`）、
用例库另有专项浏览器检查（`ui-check/caselib.mjs`）、
看板另有专项浏览器检查（`ui-check/kanban.mjs`）、
度量另有专项浏览器检查（`ui-check/metric.mjs`）、
BI 另有专项浏览器检查（`ui-check/bi.mjs`）、
回收站另有专项浏览器检查（`ui-check/action.mjs`：回收站列表/还原/隐藏/动态渲染）、
我的地盘第二组另有专项浏览器检查（`ui-check/my-workspace.mjs`：我参与的项目/执行/团队/测试单/用例/文档/日历）、
报表另有专项浏览器检查（`ui-check/report.mjs`：年度数据/每日提醒/产出统计）、
燃尽图另有专项浏览器检查（`ui-check/burn.mjs`：抽屉/三条线/重新计算/周末切换）、
测试仪表盘另有专项浏览器检查（`ui-check/qa.mjs`：汇总卡片/按产品质量统计/列表块/按产品过滤）、
代码库另有专项浏览器检查（`ui-check/repo.mjs`：同步真实 git 仓库/提交抽屉/提交详情与对象关联）、
节假日另有专项浏览器检查（`ui-check/holiday.mjs`：假期/补班列表/工作日试算）、
应用接入另有专项浏览器检查（`ui-check/entry.mjs`：列表不露 gitfox/签名助手/校验链 403 与 405/调用日志），
公司信息另有专项浏览器检查（`ui-check/company.mjs`：本公司唯一性/外部公司下拉/超管口径对照）、
积分另有专项浏览器检查（`ui-check/score.mjs`：规则/次数与时间窗/总分）、
保存查询另有专项浏览器检查（`ui-check/search.mjs`：条件 JSON/公开私有/维度过滤）、
度量维度另有专项浏览器检查（`ui-check/dimension.mjs`：四级回退/下拉树/删除保护）、
接口文档库另有专项浏览器检查（`ui-check/api.mjs`：空间/库/接口/结构/版本发布与冻结）、
Webhook 另有专项浏览器检查（`ui-check/webhook.mjs`：定义/加密开关/事件触发/mock 接收/调用日志）、
外加三个静态预检脚本（MySQL 保留字 / 驼峰列名漏 `@TableField` / 新表是否登记进 `tenant.ignore-tables`）。下面按模块列关键场景。

需求模块（12 个边界场景，全部精确拦截）：

| 场景 | 结果 |
|---|---|
| 创建后默认值 | `status=draft`, `stage=wait`, `version=1` ✅ |
| 普通编辑 | `version` 保持不变，内容原地更新，最后一版快照被改写 ✅ |
| 正式变更 | `version+1`，**旧版本快照完整保留** ✅ |
| 回溯读取 v1 | 变更到 v2 后，v1 内容仍能原样读出 ✅ |
| 关闭原因=duplicate 但不给编号 | `1020000005` 必须指定重复需求 ✅ |
| duplicate 指向自己 | `1020000007` ✅ |
| duplicate 目标不存在 | `1020000006` 不存在：99999 ✅ |
| 重复关闭 | `1020000003` 已是关闭状态 ✅ |
| 关闭后修改 | `1020000002` 请先激活后再修改 ✅ |
| 非关闭态激活 | `1020000004` 只有已关闭的才能激活 ✅ |
| 评审全票通过 | 聚合 `pass` → `status=active` ✅ |
| **评审 revert** | **version 2→1，v2 快照被物理删除，标题描述全部还原** ✅ |
| 评审 reject | 聚合 `reject` → `closed`, `closedReason=willnotdo` ✅ |
| 多人评审聚合 | admin=pass + tester=reject → 非全员通过 → 无多数派 → 取反对意见 → `reject` ✅ |

产品模块（11 个场景）：

| 场景 | 结果 |
|---|---|
| 分页查询 | `total=3`，按 order/id 排序 ✅ |
| 新建时名称重复 | `1020004001` 产品名称已存在 ✅ |
| 正常新建 | 成功，写入 createdBy/createdDate ✅ |
| 改名撞其他产品 | `1020004001` ✅ |
| 正常改名 | 成功 ✅ |
| 关闭产品 | `normal` → `closed` ✅ |
| 关闭后修改 | `1020004002` 产品已关闭，请先激活 ✅ |
| 激活产品 | `closed` → `normal` ✅ |
| **删除有需求的产品** | **`1020004004` 产品下还有 10 条需求，不能删除** ✅ |
| 删除空产品 | 成功 ✅ |
| **实时统计** | **需求 10（激活 4 / 关闭 3），缺陷 1（未解决 1 / 关闭 0）** ✅ |

项目模块（14 个场景）：

| 场景 | 结果 |
|---|---|
| 非法模型 | `1020005006` 项目模型不合法 ✅ |
| 结束早于开始 | `1020005007` 计划结束不能早于开始 ✅ |
| 正常创建 | 写入 openedBy/openedDate ✅ |
| 名称重复 | `1020005001` ✅ |
| **项目下还有执行时删除** | **`1020005005` 项目下还有 N 个执行，不能删除** ✅ |
| **项目归属项目集** | **`parent=9001（项目集）, path=,9001,1,, grade=2`** ✅ |
| 开始项目 | 写入 realBegan，`wait` → `doing` ✅ |
| 重复开始 | `1020005003` 状态不允许 ✅ |
| 挂起 / 激活 | `doing` ⇄ `suspended` ✅ |
| 关闭项目 | 写入 realEnd/closedBy/closedDate/closedReason ✅ |
| 关闭后修改 | `1020005002` 项目已关闭 ✅ |
| 团队人数推导 | `team='admin,dev1'` → `teamCount=2` ✅ |

执行模块（34 项断言，脚本：`deploy/test-execution-module.sh`）：

| 场景 | 结果 |
|---|---|
| 不传所属项目 | `400` 所属项目不能为空 ✅ |
| `project=0` | `1020006002` 执行必须指定所属项目 ✅ |
| 类型非法（xxx） | `1020006001` 执行类型不合法 ✅ |
| 所属项目不存在 | `1020006003` 所属项目不存在：99999999 ✅ |
| **拿另一个执行当所属项目** | **`1020006004` 编号 N 不是项目（type=project）** ✅ |
| 在项目下创建迭代 | 成功，`type=sprint`、`project=父项目 id`、`status=wait` ✅ |
| **执行 ID 查项目接口** | **`1020005008` 编号 N 是执行而非项目** ✅ |
| **项目 ID 查执行接口** | **`1020006005` 编号 N 是项目而非执行** ✅ |
| **项目分页 / 项目下拉** | **不混入任何执行（全为 type=project）** ✅ |
| 执行分页按项目过滤 | `total=1`，`type=sprint` ✅ |
| 按类型 stage 过滤 | 结果全为 stage ✅ |
| 某项目下的执行列表 / 数量 | `list=1` / `count=1` ✅ |
| 开始执行 | `wait` → `doing`，写入 `realBegan` ✅ |
| 重复开始 | `1020005003` 状态不允许 ✅ |
| 挂起 → 激活 | `doing` ⇄ `suspended` ✅ |
| 关闭 / 重复关闭 | 关闭成功；再关闭报 `1020005004` ✅ |
| 关闭后修改 | `1020005002` 项目已关闭 ✅ |
| 修改执行 | 名称生效，**所属项目不可改**（传了也被忽略） ✅ |
| 删除执行 / 删除后再查 | `0`；再查报 `1020006000` 执行不存在 ✅ |

分支/平台模块（38 项断言，脚本：`deploy/test-branch-module.sh`）：

| 场景 | 结果 |
|---|---|
| normal 类型产品建分支 | `1020007003` 产品类型是普通产品，未启用分支/平台 ✅ |
| 产品不存在 / 缺产品 / 空名称 | `1020007002` / `400` / `400` ✅ |
| **建后默认值** | **`order` 自动 = 同级 max+1、`status=active`、`branchLabel=平台`（随产品类型变文案）** ✅ |
| 同产品内重名 | `1020007001` 平台名称已存在 ✅ |
| **虚拟主干 id=0** | **`get` 返回「主干」、`mainBranch=true`；关闭/修改/删除主干全部报 `1020007005`** ✅ |
| **分页含主干** | **`total=3`（主干+2）且首行是主干；第 2 页 total 仍为 3、不重复插主干** ✅ |
| `status=closed` 过滤 | 不补主干（关闭态没有主干的概念） ✅ |
| 关闭 / 重复关闭 / 激活 | `closed` + `closedDate`；重复报 `1020007006`；激活回 `active` ✅ |
| **设为默认（唯一性）** | **`0:0 7:1 8:0` —— 设置前先清空该产品全部 default；`branchId=0` 表示默认还原到主干** ✅ |
| 改名 / 改名撞其他分支 | 生效 / `1020007001` ✅ |
| **删除保护** | **分支下挂了需求时报 `1020007004`（需求），数据清掉后才能删** ✅ |

模块树模块（54 项断言，脚本：`deploy/test-module-module.sh`）：

| 场景 | 结果 |
|---|---|
| 非法树类型 / 缺 root / 空白名称 | `1020008002` / `400` / `400` ✅ |
| **一级模块自动维护 path/grade/order** | **`path=,8,`、`grade=1`、`order=10`（同级 max+10）** ✅ |
| 同级重名 | `1020008001` ✅ |
| **子模块** | **`path=,8,10,`、`grade=2`；第二个一级模块 `order=20`** ✅ |
| 跨树挂载 / 跨分支挂载 | `1020008004`（不在同一棵树 / 不在同一分支） ✅ |
| **树接口** | **嵌套结构：一级 3 个、`childCount=1`、子模块名正确** ✅ |
| 平铺列表按分支过滤 | `branch=0` → 3；`branch=1` → 1；不传 → 4 ✅ |
| **移动模块** | **改名+换父级后 `path=,9,10,`、`grade=2` 全部重算** ✅ |
| **移到自己的子孙 / 自己下** | **`1020008005` 不能把模块移动到自己或自己的子模块下（防成环）** ✅ |
| 改树类型（root/type） | `1020008004` 直接拒绝（禅道会弹危险确认） ✅ |
| 批量排序 | 排序值落库；树接口按 order 升序返回 ✅ |
| **删除模块：业务数据改挂** | **删含子模块的父模块后，挂在子模块上的需求 `module` 自动变为父模块的父级（0）** ✅ |
| 子孙一起删除 | 父、子模块再查均报 `1020008000`；动作日志记录「N 条数据已改挂到上级模块」 ✅ |
| 任务树（root=执行）/ 产品线（root=0） | 均可用，`type/root/path` 正确 ✅ |
| **列表按子模块过滤** | 挂子模块的需求，按子模块查命中 1 条 ✅ |
| **列表按父模块过滤** | **连带查出子模块下的数据（禅道行为），命中 1 条** ✅ |
| 按不存在的模块过滤 | 返回 0 条，**不会退化成查全部** ✅ |
| 按分支过滤（需求列表） | `branch=0` 命中 2 条，`branch=9` 命中 0 条 ✅ |

组织与权限视图（16 项断言，脚本：`deploy/test-organization-module.sh`）：

| 场景 | 结果 |
|---|---|
| 用户列表（zt_user 视角） | `admin / 芋道源码 / 研发部门 / 超级管理员,普通角色` ✅ |
| 按账号模糊查询 | 3 条 ✅ |
| **超管权限展开** | **`super_admin` 在 yudao 里不写权限表，展开后 = 14 个模块 / 52 条权限** ✅ |
| **非超管用户** | **只有 `common` 角色的用户 → 0 个模块 0 条权限（未被误展开）** ✅ |
| 权限包列表 | 超管展开 52 条 / 覆盖 14 模块；`common` 0 条 ✅ |
| 单角色权限明细 | 14 模块，`bug → create,delete,query,update` ✅ |
| `withPermissions=false` | 不返回权限明细（省一次查询）✅ |
| **部门树（zt_dept 视角）** | **邻接表补出 `grade=3` 与禅道风格的逗号 `path`，带人数** ✅ |
| 迁移映射对照表 | 4 组对象，各带字段对照与差异说明 ✅ |

阶段（瀑布流程）模块（30 项断言，脚本：`deploy/test-stage-module.sh`）：

| 场景 | 结果 |
|---|---|
| 新建阶段模板 | 成功，`order` 自动递增 ✅ |
| 同组重名 / 占比非数字 / 类型非法 | `1020013001` / `1020013002` / `1020013004` ✅ |
| **占比累计超过 100%** | **`1020013003`；合计刚好 100% 后再加也拒绝** ✅ |
| 批量新建（20+20+30+20+10） | 成功，合计 = 100 ✅ |
| 模板列表与顺序 | 5 个阶段，`order` = 1..5 ✅ |
| 修改模板（20%→5%） | 合计回落到 85%，可继续新增 ✅ |
| 修改后超 100%（85+50） | `1020013003` ✅ |
| **按模板为项目生成阶段** | **生成 5 条 `zt_project(type='stage')`，名称/占比/order/workflowGroup 与模板一致** ✅ |
| 重复生成 / 项目不存在 | `1020013006` / `1020013005` ✅ |
| **阶段状态流转复用执行模块** | **`execution/start` 后 `type=stage`、`status=doing`、`percent=30`** ✅ |
| 不给 workflowGroup 时按项目 model 匹配 | 自动匹配到内置模板组 1，生成 5 个阶段 ✅ |
| 删除项目阶段后重新生成 | 删除后为 0 条 ✅ |

项目需求范围（37 项断言，脚本：`deploy/test-projectstory-module.sh`）：

| 场景 | 结果 |
|---|---|
| 项目不存在 | `1020012007` ✅ |
| 项目关联产品（限定计划） | 成功；明细含 `planNames`、`branchName`、`storyCount` ✅ |
| 重复关联同一分支 | `1020012005` ✅ |
| **纳入需求（走真实评审流程把需求推到 active）** | 只关联了 **active 且有计划** 的 2 条；**草稿与无计划的被静默跳过**，接口返回实际关联的 id ✅ |
| 重复关联 | 返回空数组（跳过，不报错）✅ |
| 需求列表 | `order` 递增 1/2、标题与关联版本正确 ✅ |
| **版本变更标记** | 需求正式变更（v1→v2）后 `linkVersion=1`、`currentVersion=2`、`versionChanged=true` ✅ |
| 需求被哪些项目关联 | 返回所在项目列表 ✅ |
| 执行也能挂需求 | 成功 ✅ |
| **子执行已关联时从项目移除** | `1020012003` 拒绝 ✅ |
| 移除后重新编号 | 剩余关系 order 变回 1 ✅ |
| 移除不存在的关系 | `1020012000` ✅ |
| **产品下还有需求时解除关联** | `1020012006`；清空后可以解除 ✅ |

发布模块（67 项断言，脚本：`deploy/test-release-module.sh`）：

| 场景 | 结果 |
|---|---|
| 缺版本号 / 缺产品 | `400` ×2 ✅ |
| **wait 缺计划日期 / normal 缺实际日期** | `1020011002` / `1020011003`（必填随状态变）✅ |
| **实际发布日期在未来** | `1020011004` 不能晚于今天 ✅ |
| 多平台产品不选分支 / 构建不存在 | `1020011009` / `1020011007` ✅ |
| **自动生成影子构建** | **`shadow>0`，且同名同产品同分支（branch 已规范化成 `0`）** ✅ |
| **版本号全局唯一** | 另一个产品用同样的版本号也被拒 `1020011001` ✅ |
| **从构建同步** | **`stories='需求1,需求2'`、`bugCount=1`，`build=',26,27,'`、`project=',90032,'`（前后带逗号格式）** ✅ |
| 关联集成构建 | 子构建的需求/Bug 也并进来 ✅ |
| `syncFromBuilds=false` | 不同步，需求数为 0 ✅ |
| 三份清单分离 | 解决的 Bug 与**遗留的 Bug** 分别统计与查询 ✅ |
| 发布 publish | `status=normal`、写入实际发布日期、**关联需求阶段推进为 `released`** ✅ |
| changeStatus | `terminate` 成功；非法状态报 `1020011006` ✅ |
| **影子构建同步** | 改发布名称/日期/构建后，影子构建同步为同样的 name/date/builds ✅ |
| wait 清空实际发布日期 | 改回 wait 后 `releasedDate` 被置空（原生 UPDATE）✅ |
| **删除保护** | **被别的发布包含的子发布不能删 `1020011005`；解除后父发布可删，且影子构建连带删除** ✅ |
| 列表按产品/状态/分支过滤 | 3 / 2 / 1 ✅ |

构建模块（62 项断言，脚本：`deploy/test-build-module.sh`）：

| 场景 | 结果 |
|---|---|
| 缺名称 / 缺日期 / 缺构建者 | `400` ×3 ✅ |
| 缺执行 / 执行位置传了项目 id | `1020010002` / `1020010003` ✅ |
| **多平台产品不选分支 / 分支不属于产品** | `1020010005` ✅ |
| 集成构建不给子构建 / 子构建不存在 | `1020010006` / `1020010008` ✅ |
| 建后默认值 | `branch=0`、`project` 由执行推导、`branchName=主干` ✅ |
| 同产品同分支重名 | `1020010001` ✅ |
| **集成构建** | **`execution=0`、`builds='4,5'`、子构建名按 builds 顺序返回** ✅ |
| **集成构建合并数据** | **读取时 `stories` 是子构建需求的并集（`storyCount=2`）** ✅ |
| 集成构建再被集成 / 子构建被引用 | `1020010008` / `child=true` ✅ |
| **子构建改产品** | **`1020010007` 不能修改；改名称/日期允许** ✅ |
| **关联 Bug 自动解决** | **`status=resolved`、`resolution=fixed`、`resolvedBuild=构建编号`、`resolvedBy` 取传入值、`assignedTo` 回到创建人、`confirmed=1`** ✅ |
| 已解决 Bug 再关联到别的构建 | 不会覆盖原来的 `resolvedBuild`（禅道跳过 resolved/closed）✅ |
| 不传 resolvedBy | 缺省取当前登录账号 ✅ |
| 解除关联 | 需求从 `stories` 移除；**Bug 状态不回退**（与禅道一致）✅ |
| 列表按产品/执行/分支过滤 | 3 / 3 / 1（`FIND_IN_SET` 命中多分支构建）✅ |

产品计划模块（76 项断言，脚本：`deploy/test-productplan-module.sh`）：

| 场景 | 结果 |
|---|---|
| 缺名称 / 缺产品 | `400` / `400` ✅ |
| **多平台产品不选分支** | **`1020009001` 必须选择平台** ✅ |
| 普通产品传真实分支 / 分支不属于产品 | `1020009010` ✅ |
| 非待定但缺日期 / 结束早于开始 | `1020009002` / `1020009003` ✅ |
| **待定计划** | **`begin=end=2030-01-01`，`future=true`（哨兵日期）** ✅ |
| **多分支计划** | **`branch='31,32'`，`branchName='平台A,平台B'`** ✅ |
| 子计划开始早于 / 结束晚于父计划 | `1020009004` ✅ |
| 父计划被标记 | 子计划创建后父计划 `parent=-1`、`childCount=1` ✅ |
| 删除父计划 / 父计划日期不覆盖子计划 | `1020009007` / `1020009005` ✅ |
| 开始 / 重复开始 / 完成 | `doing` / `1020009008` / `done` + `finishedDate` ✅ |
| **父计划状态聚合** | **子计划开始→父 `doing`；子计划完成→父 `done`** ✅ |
| 关闭（done）/ 重复关闭 | `closed` + `reason=done` + 两个时间都写 / `1020009009` ✅ |
| 关闭后修改 | `1020009008` ✅ |
| **激活后状态** | **回到 `doing` 且清空关闭信息（禅道语义，不是回到 wait）** ✅ |
| 关联需求 / 计划下需求列表 | 成功 / 2 条 ✅ |
| **需求列表按计划过滤** | `FIND_IN_SET` 命中 2 条 ✅ |
| **type=story 独占计划** | **改挂到新计划后从旧计划移走（旧计划只剩 1 条）** ✅ |
| **type=requirement 累加** | **`plan='6,7'`；移除其中一个后剩 `'7'`** ✅ |
| 未关联需求候选 | 同产品 + 分支匹配 + 未挂计划 ✅ |
| 关联 / 移除 Bug | `bug.plan=计划id` → 移除后回 0 ✅ |
| 列表页需求数实时统计 | 一次查询算出每行需求数（非 N+1）✅ |
| **删除计划** | **关联需求/Bug 被自动摘除（本实现的有意改进，禅道会留脏引用）** ✅ |
| 删除子计划后父计划标记回滚 | `parent` 回到 0 ✅ |

模块/分支 与业务对象联调（31 项断言，脚本：`deploy/test-module-integration.sh`）：

| 场景 | 结果 |
|---|---|
| 需求写入 module/branch 后读回 | `module=子模块, branch=0`（主干）✅ |
| **需求列表按子模块过滤** | 命中 1 条 ✅ |
| **需求列表按父模块过滤** | **连带查出子模块下的需求（禅道行为），命中 1 条** ✅ |
| 需求列表按分支过滤 | 分支命中 1 条 / 主干命中 1 条 ✅ |
| 需求列表传不存在的模块 | 返回 0 条，**不退化成全量** ✅ |
| 缺陷写入/读回 module+branch | `module=子模块, branch=0` ✅ |
| 缺陷列表按父模块过滤 | 命中 1 条（子树连带）✅ |
| 任务写入任务模块（树挂执行下） | 读回 `module=任务模块` ✅ |
| 任务列表按模块过滤 / 不存在的模块 | 命中 1 条 / 0 条 ✅ |
| **分支删除保护（模块占用）** | 分支下只剩模块时，删除报 `1020007004`（模块）✅ |

附件（30 项断言，脚本：`deploy/test-file-module.sh`；浏览器实测：`deploy/ui-check/file.mjs`）：

| 场景 | 结果 |
|---|---|
| 上传到 `story#1` | `title/extension/size/sizeText/objectType/objectID` 全部正确（`24 B`）✅ |
| **存储地址可直接访问** | **URL 取回来的内容与上传内容逐字节一致** ✅ |
| 列表 / 计数 / 详情 | 各 1 条，`downloads` 初始为 0 ✅ |
| **下载累加计数** | **下载 3 次后 `downloads=3`（数据库自增，不是读改写）** ✅ |
| 重命名 | `spec-x.txt` → `需求说明书-x.md`，**扩展名同步变更** ✅ |
| 空文件名 | `400` 参数校验拦下 ✅ |
| **gid 两阶段上传** | **上传时 `objectType` 空、`objectID=0`、`gid` 有值，`image=true`（1x1 PNG）** ✅ |
| **按 gid 绑定** | **绑定到 `bug#7` 后 `gid` 清空、对象归属正确，gid 列表回到 0 条** ✅ |
| 绑定不存在的 gid | `1020014004` ✅ |
| 查询 / 下载 / 删除不存在的附件 | `1020014000` ×3 ✅ |
| **超过 50MB** | **`1020014002`（业务校验，不是框架 500）** ✅ |
| 空文件 | `1020014001` ✅ |
| 删除单个附件 | 成功，再查 `1020014000`，计数归 0 ✅ |
| **按对象批量删除** | **返回实际删除条数 2，计数归 0** ✅ |
| **对象隔离** | **`story#1` / `bug#7` 各自 1 条；`objectType` 不同时不串（`story#7` = 0 条）** ✅ |
| 浏览器：抽屉里的「附件」Tab | 角标 `附件 (N)`、列表列、大小文案、上传人、下载数全部正确渲染 ✅ |
| 浏览器：界面上传 | `el-upload` 走 `http-request`，上传后列表变 2 行且新文件在最前 ✅ |
| **浏览器：点「下载」** | **真的调用了 `/zentao/file/download`，返回 `.../infra/file/4/get/...`，把该地址内容取回来与上传内容一致，列表下载数变 1** ✅ |
| 浏览器：删除 | 二次确认后列表回到 1 行 ✅ |

文档（59 项断言，脚本：`deploy/test-doc-module.sh`；浏览器实测：`deploy/ui-check/doc.mjs`）：

| 场景 | 结果 |
|---|---|
| 产品 1 的库列表 | 1 个「产品文档库」，`main=true`，库内 3 篇文档 ✅ |
| 库类型非法 / 对象库缺对象 / 自定义库缺空间 | `1020015002` / `1020015003` / `1020015004` ✅ |
| 同空间重名库 | `1020015001` ✅ |
| **删除主库（产品库 / 执行库）** | **`1020015005` 内置主库不允许删除** ✅ |
| **库内还有文档时删库** | **`1020015006`（本实现的有意改进，禅道原版会把文档留成孤儿）** ✅ |
| 章节树 | 2 个一级章节，各自带直属文档数 1 ✅ |
| **文档列表排除章节** | **3 篇文档，章节不出现在列表里** ✅ |
| 按章节过滤文档 | 命中 1 篇 ✅ |
| 新建章节（无正文） | `path=,id,`、`grade=1`、`chapter=true` ✅ |
| 子章节 | `path=,父,子,`、`grade=2` ✅ |
| **删除有子节点的章节** | **`1020015020` 章节下还有 1 个子节点** ✅ |
| 同章节下重名 | `1020015012` ✅ |
| 上级不是章节 / 上级不在同库 | `1020015017` / `1020015019` ✅ |
| html 不给正文 / url 链接非法 / attachment 不给附件 | `1020015014` / `1020015015` / `1020015025` ✅ |
| **只改基础信息（关键词）** | **版本仍是 v1（正文没变就不该产生版本）** ✅ |
| **改正文** | **version 1 → 2，v1 快照保留且可回读** ✅ |
| 连续三次编辑正文 | 三个快照 `3|2|1`，只有一个 `current=true` ✅ |
| **新建草稿** | **`status=draft`、`version=0`** ✅ |
| **反复保存草稿** | **始终只有 1 条 version=0 的记录，内容被就地改写** ✅ |
| **发布草稿** | **草稿行升成 v1，状态转已发布** ✅ |
| 重复发布 | `1020015023` ✅ |
| **发布后再编辑** | **v2，且没有残留的草稿行** ✅ |
| 浏览计数 | 三次浏览 `views` 递增；**草稿浏览不计数** ✅ |
| **移动章节到库根** | **自己的 `path/grade` 重算，子树文档跟着上浮** ✅ |
| **再移回原章节** | **子树 `path` 完全回位（`,父,子,文档,` / grade 3）** ✅ |
| 移到自己的子孙下 / 非章节下 / 跨库到别的库章节 | `1020015018` / `1020015017` / `1020015019` ✅ |
| 删文档后版本记录 | 一起物理清理 ✅ |
| 浏览器：库过滤 + 章节树 + 点章节过滤 | 列表显示章节名（不是「库根」）✅ |
| 浏览器：详情抽屉 | 正文显示 v2；版本历史 2 条；点「查看该版」回读 v1 并提示「正在查看历史版本」✅ |
| 浏览器：新建章节弹窗 | 标题正确且**不出现正文输入框** ✅ |
| 浏览器：删除 | 二次确认后列表变 0 行 ✅ |

测试用例（42 项断言，脚本：`deploy/test-testcase-module.sh`；浏览器实测：`deploy/ui-check/testcase.mjs`）：

| 场景 | 结果 |
|---|---|
| 演示用例与类型 | 5 条：功能×3 / 接口×1 / 性能×1 ✅ |
| **按父模块过滤（含子树）** | **模块 93301 命中自己 + 子模块 93303 下的用例，共 3 条** ✅ |
| **stage 是逗号列表** | **`FIND_IN_SET(smoke)` 命中 1 条** ✅ |
| **待确认列表** | **`storyVersion=1` 而需求已是 v2 → 命中；`needConfirm` 过滤与列表里的标记完全一致** ✅ |
| 版本叠加 | 当前版本标题取 `zt_casespec`（v2 标题带「补充验证码」）✅ |
| 历史版本 | v1 标题与 3 个步骤独立于 v2（6 个步骤）✅ |
| **步骤层级编号** | **`1 / 1.1 / 1.2 / 2 / 2.1 / 2.2`，grade 为 `1,2,2,1,2,2`** ✅ |
| 版本历史 | `2|1` 两个版本，步骤数 `6|3`，只有 v2 是 current ✅ |
| 类型/环节/状态非法 | `1020016002` / `1020016003` / `1020016004` ✅ |
| 关联不存在的需求 | `1020000000` 需求不存在（复用需求模块的错误码）✅ |
| **步骤组带预期结果** | **`1020016008`（新建时也会校验 —— 这条一开始漏了，见第 28 条坑）** ✅ |
| 父指向不存在的位置 | `1020016009` ✅ |
| **只改标题/优先级** | **版本仍是 v1，状态不变** ✅ |
| **改步骤** | **version 1→2，状态打回「待评审」** ✅ |
| **步骤原样再提交** | **版本不动，也不多出快照** ✅ |
| 评审结果非法 | `1020016012` ✅ |
| 评审（wait → normal） | 写入 `reviewedBy` / `reviewedDate` ✅ |
| 重复评审 | `1020016013` 只有「待评审」才能评审 ✅ |
| **再改步骤 → 重新评审为 blocked** | **v3 + 被阻塞** ✅ |
| **确认需求变更** | **`storyVersion` 1→2，待确认列表从 1 降到 0** ✅ |
| 已是最新时再确认 | `1020016014` ✅ |
| 删除用例 | **步骤与版本快照物理清理，残留 0 行** ✅ |
| 浏览器：详情抽屉 | 步骤表显示 5 行、编号 `1|1.1|1.2|2|2.1` ✅ |
| 浏览器：回看 v1 | 提示「正在查看历史版本 v1」，步骤变成 1 行 ✅ |
| 浏览器：步骤编辑器 | 默认 1 行；点「添加步骤组」后变 2 行且标记为「组」 ✅ |
| 浏览器：待确认开关 | 列表只剩 1 条并带「需求已变更」标记 ✅ |

测试单（45 项断言，脚本：`deploy/test-testtask-module.sh`）：

| 场景 | 结果 |
|---|---|
| 演示测试单统计 | `3 用例 / 通过 1 / 失败 1 / 未执行 1`、构建名正确 ✅ |
| `type` 逗号列表过滤 | `FIND_IN_SET(interface)` 命中 1 条 ✅ |
| run 列表 | 3 条，最近结果 `通过/失败/未执行` ✅ |
| 排进来的用例版本 | `caseVersion=2`、`latestCaseVersion=2`、`caseChanged=false`、步骤数 6 ✅ |
| 可排入列表 | 排除已排的 93101–93103 ✅ |
| **全 pass → pass** | ✅ |
| **pass + n/a → pass** | **n/a（忽略）不影响用例结果** ✅ |
| **blocked 在前 + fail 在后 → fail** | **fail 优先，不是「按顺序取第一个」也不是多数派** ✅ |
| **只有 blocked → blocked** | **且用例状态被置为「被阻塞」** ✅ |
| 步骤不属于该用例 | `1020017015` ✅ |
| 结果值非法 | `1020017014` ✅ |
| **执行一次写三处** | **4 次执行 → 4 条结果历史；run 上写入结果+状态；用例上写入最近结果/执行人/状态** ✅ |
| **结果历史（最新在前）** | `blocked\|fail\|pass\|pass` ✅ |
| **重复排入同一条用例** | **run 还是同一条、结果与历史都还在（禅道 REPLACE 会清空）** ✅ |
| 跨产品用例 | `1020017012` ✅ |
| 用例变更提示 | 排入后用例升版 → `caseChanged=true` ✅ |
| 状态机 | `wait→doing`（写实际开始日期）、重复开始拒绝、`doing→blocked` ✅ |
| 关闭校验 | 缺完成时间 `1020017003`、早于计划开始 `1020017004`、晚于明天 `1020017005` ✅ |
| **关闭 → 激活** | **完成时间与测试总结被清空（第 19 条坑的写法）** ✅ |
| 移除用例 / 删除测试单 | **run 与执行历史一起物理清理** ✅ |
| **从执行结果建缺陷** | **归属（产品/分支/模块/需求）与来源用例+版本+测试单全部写对** ✅ |
| **复现步骤自动生成** | **按用例步骤拼出来，并标出失败的那一步「← 这一步失败」** ✅ |
| 手动传复现步骤 | 不被自动内容覆盖 ✅ |
| 按用例 / 按测试单反查缺陷 | 两个方向都能查到 ✅ |
| **用例版本是冻结值** | **用例升到 v3 后，缺陷仍指向当初执行的 v2** ✅ |
| run 不存在 / 缺标题 | `1020017010` / `400` ✅ |

测试报告 + 用例集（26 项断言，脚本：`deploy/test-report-module.sh`）：

| 场景 | 结果 |
|---|---|
| 演示报告汇总 | 用例 5 / 已执行 4 / 执行 4 次 / 通过 3 / 失败 1 ✅ |
| 按用例汇总明细 | 93101 通过、93102 失败（1 次）、93104 通过 ✅ |
| **时间范围生效** | **范围挪到执行之前 → 0 次执行，但「用例数」仍是 5** ✅ |
| **每条 run 只取最后一次结果** | **同一条 run 先 pass 后 fail → 执行 2 次、只算 1 条结果、失败 1** ✅ |
| 报告 CRUD | 新建/修改/删除，详情带现算汇总与测试单名 ✅ |
| 结束早于开始 / 不给测试单 | `1020018012` / `400` ✅ |
| **汇总别的产品的测试单** | **`1020018014`（否则数字会串产品）** ✅ |
| 演示用例集与用例数 | 冒烟 2 条 / 登录专项 3 条 ✅ |
| 同产品重名集合 | `1020018001` ✅ |
| **重复加入用例是幂等的** | **集合里仍是 1 条（不会重复插行）** ✅ |
| 跨产品用例 / 集合内还有用例时删除 | `1020018002` / `1020018004` ✅ |
| 可加入列表 | 排除已加入的 ✅ |
| 移出后删除集合 | 成功，再查 `1020018000` ✅ |

需求转任务（19 项断言，脚本：`deploy/test-storytask-module.sh`；浏览器实测并入 `storytree.mjs`）：

| 场景 | 结果 |
|---|---|
| 演示任务的需求信息 | `story=1 / storyVersion=2 / latest=2 / 未变更` ✅ |
| **批量把需求转成任务** | **任务挂在执行 90001、需求版本冻结为 1、优先级从需求继承** ✅ |
| 任务详情 | 带需求标题，未变更时 `storyChanged=false` ✅ |
| **需求升版后** | **任务仍冻结在 v1，`latestStoryVersion=2`，提示「需求已变更」** ✅ |
| 直接建任务并挂需求 | 冻结的是需求**当前**版本（v2）✅ |
| 不挂需求的任务 | `storyVersion=0`、`storyChanged=false`（不是 null）✅ |
| 挂不存在的需求 / 查不存在的需求 | `1020000000` / 返回空列表 ✅ |
| 浏览器：抽屉「任务」Tab | 有「需求转任务」按钮，填两个名称后接口 200、列表变 2 行 ✅ |

父子需求（分解）（22 项断言，脚本：`deploy/test-storytree-module.sh`；浏览器实测：`deploy/ui-check/storytree.mjs`）：

| 场景 | 结果 |
|---|---|
| 演示父需求聚合 | 已分解 / 2 个子需求 / 工时 10（=4+6）/ 一级 ✅ |
| 子需求列表 | 2 条，层级都是 2，带父需求标题 ✅ |
| **分解已有需求** | **parent/root/path/grade/parentVersion 全部写对（`,4,92201,`）** ✅ |
| **父需求聚合** | **工时 = 子需求之和；isParent 自动置位** ✅ |
| 重复分解 / 挂到子下 / 挂到自己 | `1020000011` / `1020000009` / `1020000009` ✅ |
| **批量分解成子需求** | **只给标题，产品/类型/优先级从父需求继承，层级都是 2** ✅ |
| **状态级联：全部子需求关闭** | **父需求自动关闭** ✅ |
| **状态级联：子需求被激活** | **已关闭的父需求自动激活** ✅ |
| **父需求版本冻结** | **父需求升版后子需求提示「父需求已变更」** ✅ |
| 父需求还有子需求 | `1020000010` 不能删除（本实现的有意改进）✅ |

✅ **团队模块：接口 + 界面都已验证通过** —— 33/33 断言（接口），6 个界面场景（`ui-check/team.mjs`）
都跑在本机栈 `deploy/local-stack`（真实 MySQL 8.0 + Redis）上。

```
===== 结果：通过 33 / 失败 0 =====
✅ 加人后 teamCount/team 同步 = 4 admin,tester,dev1,dev2
✅ 团队可用工时合计 = 285.00              （20×7 + 10×4 + 15×7）
✅ 重复添加同一个人 → 1020022001 / type 非法 → 1020022002
✅ 移除后再加同一个人 → 允许（物理删除，不是逻辑删除）
✅ 全量保存：成员换成 2 人，admin 的加入日期仍是 2026-01-01（没被刷成今天）
✅ 建项目时带 3 个团队成员 → 成员落表、teamCount=3
✅ 执行层级：project=parent=项目、path=,项目,执行,、grade=1
✅ 执行的负责人自动进团队成员（角色来自 ownerFields）= 2 admin:项目经理 tester:测试负责人
✅ 项目下还有执行 → 拒绝删除 1020005005 → 先删执行 → 删项目 → 成员行一起被删掉
✅ 演示团队/演示执行层级原样未动
```

**"跑起来"抓到的三个真问题**（静态检查、编译、类型检查全都发现不了）：

| # | 问题 | 怎么发现的 | 修法 |
|---|---|---|---|
| 1 | `addMember/removeMember/updateMembers` 不刷新 `zt_project.teamCount/team` 缓存 —— `teamCount` 是读时现算的、`team` 却还是旧值，两列不一致 | 断言「加人后 teamCount/team 同步」失败 | 四个成员变更出口统一调 `syncTeamInfo()` |
| 2 | **执行的负责人一条都没进团队**：`syncOwners` 里写成 `"execution".equals(row.getType())`，而执行的 type 是 `sprint/stage/kanban` —— 那个分支永远不成立，函数直接 return | 断言「执行团队」拿到 0 条 | 改成 `ExecutionTypeEnum.isExecution(row.getType())` 判断 |
| 3 | 演示数据缺口（见坑位 #42）：`deploy/sql` 建不出项目/需求/任务/缺陷 | 本机栈从空库初始化后 `30/31/32/33/35` 的 UPDATE 全成空操作 | 新增 `deploy/sql/36-zt_demo_seed.sql` |
| 4 | **接口全通、页面上「添加成员」按钮却不见了** —— 忘了给团队建 `system_menu` 权限行（`zentao:team:query/update`），而前端 `v-hasPermi` 是拿「角色→菜单」算出来的权限串判定；后端因为是超管硬编码放行照旧能调 | 浏览器检查里点击按钮超时 | `35-zt_team.sql` 补两行 type=3 的按钮菜单（挂在「项目管理」下），并清一次 Redis 权限缓存（坑位 #43） |

**顺带验证的边界**：所有测试脚本的清理都是「先删执行/阶段、再删项目」，
所以新加的「项目下有执行时拒绝删除」保护不会误伤它们（只有 `test-team-module.sh` 自己是反例，已改成先删执行）。

✅ **干系人模块（`zt_stakeholder`）：接口 23/23 + 界面检查都通过**（`ui-check/stakeholder.mjs`：抽屉渲染、外部干系人添加、移除）。

### 全量回归：43 个脚本 / 1704 项断言 + 21 个页面，全绿

> **两种跑法，同一套断言**（详见 5.0）：
> - **本机栈**：`LOCAL=1 bash deploy/resume-verification.sh`（本机容器 + 本机后端，内存够用时用）；
> - **服务器模式（当前）**：`bash deploy/resume-verification-remote.sh`（SQL 同步到 192.168.0.119 → 重启远端两个服务 → 跑同一批脚本）。
>   脚本通过 `ZENTAO_API_BASE` / `ZENTAO_UI_BASE` 换目标，所以两边跑的是同一份断言，不存在「只在某台机器上才成立」的测试。
>
> **2026-09-18 服务器模式全量实测（一次跑完，全绿，零重试）**：
> **43 个脚本 / 1704 项断言 / 0 失败**，页面巡检 **21/21**
> **20 个浏览器专项**（team / storytype / caselib / kanban / metric / bi / action / my-workspace /
> report / burn / qa / repo / holiday / entry / company / score / **search / dimension / api / webhook**）**全部通过**
> （新式专项脚本的断言合计 **409 项**，另有 `team.mjs` 等老式脚本按「退出码 0 且无 ❌」判定；
> 这一轮 43 个脚本 + 页面巡检 + 20 个专项**一次跑完全部首次通过**，日志里 0 条重试、0 条失败）。
> 原始日志存档：`docs/verification-2026-09-18.log`（脚本汇总 + 每条专项的通过数，含 0 重试 / 0 失败）。
> 浏览器仍在 Mac 上跑（Playwright），打的是 `192.168.0.119:48080` + `:8081`。
> 这一轮修掉三件**只在服务器模式或跨天跑才暴露**的事：
> ① `zt_metriclib` 的 nodate 历史快照被读路径当当前值求和 —— **度量值翻倍、`dataCount` 也偏大 82.6%**（详见 3.47 与坑位 #55）；
> ② **页面巡检会撞 Vite 冷启动** —— 首次访问要在 dev server 上现编译模块，实测 `page.goto` 90s 超时直接退出，而专项脚本本来就有「失败重跑一次」、它没有；已把同一层重试补进 `resume-verification*.sh`（补完后的这一轮页面巡检一次过）；
> ③ 复跑还抓到三处**「按日历日 / 按 UTC 定位」的断言脆弱**：燃尽图 `labels` 默认跳过周末（坑位 #56），两个界面检查用 `toISOString()` 算「今天」（坑位 #57）—— 都是测试侧的时间口径问题，改完同一天内整轮复跑全绿。
> 上一轮（09-14）修的是另外三个服务器模式专属问题：`zt_score` 漏登记租户忽略表（一查就 500）、
> 服务器上没有支持 `git -C` 的 git（CentOS 7 自带 1.8.3 不支持）、repo 夹具仓库建在 Mac 上（现改为建在后端所在机器）。

服务器模式（`resume-verification-remote.sh` 一把过）的最终结果 —— 每个数字都取自这一轮的实测日志：

```
  test-action-module.sh              ✅ 30   test-annual-report-module.sh     ✅ 39
  test-api-module.sh                 ✅ 72   test-bi-module.sh                ✅ 37
  test-branch-module.sh              ✅ 38   test-build-module.sh             ✅ 62
  test-burn-module.sh                ✅ 32   test-caselib-module.sh           ✅ 29
  test-company-module.sh             ✅ 28   test-dimension-module.sh         ✅ 37
  test-doc-module.sh                 ✅ 59   test-effort-module.sh            ✅ 47
  test-entry-module.sh               ✅ 67   test-execution-module.sh         ✅ 34
  test-file-module.sh                ✅ 30   test-holiday-module.sh           ✅ 31
  test-kanban-module.sh              ✅ 45   test-metric-module.sh            ✅ 29
  test-module-integration.sh         ✅ 31   test-module-module.sh            ✅ 55
  test-my-workspace-module.sh        ✅ 45   test-organization-module.sh      ✅ 16
  test-productplan-module.sh         ✅ 76   test-program-module.sh           ✅ 55
  test-project-view-module.sh        ✅ 20   test-projectstory-module.sh      ✅ 37
  test-qa-module.sh                  ✅ 25   test-quality-chain.sh            ✅ 25
  test-release-module.sh             ✅ 67   test-repo-module.sh              ✅ 37
  test-report-module.sh              ✅ 26   test-score-module.sh             ✅ 40
  test-search-module.sh              ✅ 23   test-stage-module.sh             ✅ 30
  test-stakeholder-module.sh         ✅ 23   test-storytask-module.sh         ✅ 19
  test-storytree-module.sh           ✅ 22   test-storytype-module.sh         ✅ 35
  test-team-module.sh                ✅ 33   test-testcase-module.sh          ✅ 43
  test-testtask-module.sh            ✅ 54   test-todo-my-module.sh           ✅ 59
  test-webhook-module.sh             ✅ 62  
  ---- 脚本通过 43 / 43，断言 1704
  页面可用: 21/21
  ui-check/team.mjs                  ✅（抽屉/可用工时/添加/改天数/移除/执行团队）
  ui-check/stakeholder.mjs           ✅（干系人抽屉/内部外部/外部人员添加/移除）
  ui-check/storytype.mjs             ✅（类型页签角标/分层视图三层缩进与父子顺序/弹窗建业务需求）
  ui-check/caselib.mjs               ✅（库列表/库内用例/建库建用例/从产品导入/删除保护）
  ui-check/kanban.mjs                ✅（网格渲染/默认布局/建卡片/移动/完成）
  ui-check/metric.mjs                ✅（概览卡片/度量项列表/口径定义/计算/数据表）
  ui-check/bi.mjs                    ✅（数据视图列表/预览/试跑/SQL白名单/图表渲染）
  ui-check/action.mjs                ✅（回收站列表/还原/隐藏/动态渲染文本）
  ui-check/my-workspace.mjs          ✅（我参与的项目/执行/团队/测试单/用例/文档/日历）
  ui-check/report.mjs                ✅（年度数据/贡献与雷达/月度趋势/每日提醒/产出统计）
  ui-check/burn.mjs                  ✅（燃尽图抽屉/三条线渲染/重新计算/含周末切换）
  ui-check/qa.mjs                    ✅（汇总卡片/按产品质量统计与修复率/三个列表块/按产品过滤）
  ui-check/repo.mjs                  ✅（同步真实 git 仓库/提交抽屉/提交详情与对象关联/重命名）
  ui-check/holiday.mjs               ✅（假期/补班列表/新增弹窗/工作日试算/删除）
  ui-check/entry.mjs                 ✅（列表不露 gitfox/新增弹窗与无限制开关/签名助手/403 与 405/调用日志）
  ui-check/company.mjs               ✅（本公司/编辑弹窗与必填校验/外部公司三件套/超管口径四集合/复用组织与动态）
  ui-check/score.mjs                 ✅（概览与口径/积分记录/38 条规则抽查/计分试算含未知规则报错）
  ui-check/search.mjs               ✅（条件 JSON 回填/公开私有切换/保存与执行/维度过滤）
  ui-check/dimension.mjs            ✅（末次维度四级兜底/下拉树/只读无写入口/删除保护）
  ui-check/api.mjs                  ✅（接口空间与库/接口 CRUD 与版本/结构树/发布快照与冻结/引用校验）
  ui-check/webhook.mjs              ✅（定义与加密开关/事件触发/mock 接收端到端/调用日志四分页）
```

**"跑起来"顺带修掉的测试基础设施问题**（不然这些断言在别的环境上就是假的）：

- `test-testcase` / `test-testtask` / `test-organization` 里几处**写死了远端 ssh** 的直连查库
  （还原演示数据、断言物理清理）—— 远端不可达时整段断言静默失效。抽成 `deploy/_mysql.sh`，
  按 `MYSQL_TARGET=local|remote|auto` 选目标，并把调用方带来的 `\`` 归一成反引号。
- 演示用例 93103 的「需求版本冻结」会被 testcase 脚本消费掉（确认变更后就追平了），
  以前靠手工在远端改回来；现在 `36-zt_demo_seed.sql` 每次重灌都会重置，脚本才真正可重复执行。
- 第 34 轮又修了一次 `_mysql.sh` 的**远端分支**：远端那条路是
  `ssh "docker exec ... mysql -e \"$SQL\""`，SQL 里的反引号（库名 `ruoyi-vue-pro` 必须加）
  出现在**远端 shell 的源码里**，会被远端当成命令替换执行掉，SQL 静默变成 `FROM .zt_project`，
  断言全部拿到空串 —— 看起来像「数据不存在」，其实是 SQL 根本没跑对。
  现在远端改成 **base64 传 SQL**，绕开一切引号/反引号/变量展开规则；`auto` 也改成
  「先探本机栈，探不到再走远端」（原来先 `ping` 远端，远端 ping 通但 docker 没起时同样静默失效）。

<details>
<summary>原本的待验证说明（保留备查）</summary>


> 写完之后远端 MySQL/Redis（192.168.0.183）变得不可达 —— 本机的网段从 `192.168.0.x` 换到了 `192.168.5.x`
> （默认路由走了 VPN 的 `utun4`），`ping`/`ssh` 全部超时。所以：
>
> - 已经**验证过**的是「代码能编译」（`mvn package` BUILD SUCCESS）与「前端类型检查无新增错误」；
> - **没验证**的是接口行为、数据纠正（`35-zt_team.sql` 里执行层级那段 UPDATE 还没执行）、浏览器交互。
>
> 一条命令就能补完（脚本里已经把顺序、等待、失败即停都写好了）：
>
> ```bash
> bash deploy/resume-verification.sh           # 远端栈（先探测 192.168.0.183 通不通）
> LOCAL=1 bash deploy/resume-verification.sh   # 走本机栈 deploy/local-stack
> ```
>
> 它做五件事：① 灌 `35-zt_team.sql`；② 起后端并等就绪；③ 跑 `test-team-module.sh`；
> ④ 跑全部 21 个脚本（执行模块的 path/parent/grade 这轮改过，必须全量重跑）；
> ⑤ 跑浏览器检查（`all-pages.mjs` + 新增的 `ui-check/team.mjs`）。

</details>

**第 20~22 轮遇到的两个环境问题**，都不是代码问题，但都挡住了验证，记在这里备查：

| 问题 | 现象 | 绕法 |
|---|---|---|
| 远端 192.168.0.183 不可达 | 本机网段从 `192.168.0.x` 换到 `192.168.5.x`，默认路由走了 VPN 的 `utun4`；`ping`/`ssh` 全超时。`sudo` 要密码、本会话不允许提权，所以补静态路由这条路也走不通 | 用 **`deploy/local-stack/`**：同一份 `deploy/sql/01~35` 在本机起等价的 MySQL(3307)+Redis(6380)，后端只要把地址覆盖成 `127.0.0.1` 即可（命令见 `deploy/local-stack/README.md`）。**副作用是好的**：它会从空库执行 01~35，等于把「这套迁移脚本能不能从零建出完整环境」也验证一遍 |
| 本机容器引擎 wedged | OrbStack 的 VM 还在（`orbctl status` = Running），但 Docker API 卡住：`docker ps -a` 无输出、`docker compose up -d` 挂住不返回；更麻烦的是会留下**半开的端口转发**（端口 LISTEN、TCP 能连上，但后面的容器已经死了 → MySQL 不回握手包、Redis 不回 PING），此时用 `docker ps` 判断状态会误判 | ① 用 MySQL greeting / Redis PING 探活，而不是 `docker ps`（`deploy/local-stack/README.md` 里有两行 python 探针）；② 只有 Redis 挂时可以用本机 `redis-server --port 6381` 顶上，后端加 `--spring.data.redis.port=6381` |

脚本覆盖的断言（与代码一一对应，等网络恢复即可跑）：

| 场景 | 期望 |
|---|---|
| 演示团队 | 项目 1：admin 项目经理 140h / tester 测试 40h / dev1 研发 105h（`days × hours`） |
| 姓名与受限 | `realname` 回填；dev1 `limited=yes` |
| 合计可用工时 | 285 |
| **团队人数来自成员表** | 加一个成员后 `zt_project.teamCount`/`team` 跟着变 |
| 新成员默认值 | 加入日期=今天、每天 7 小时、可用工时 = 天数 × 7 |
| 重复添加 / 类型非法 | `1020022001` / `1020022002` |
| 修改与移除 | 改角色/天数/小时数/受限生效；移除后再加同一个人**允许**（物理删除） |
| **全量保存** | 先删后插，**老成员的加入日期保留**（不被刷成今天） |
| 项目表单的团队成员 | 建/改项目时 `team` 逗号串落到成员表，`teamCount` 同步 |
| **执行的层级口径** | 新建执行 `project=parent=项目`、`path=,项目,执行,`、`grade=1`；演示执行 90001 也被纠正 |
| 执行团队 | 负责人（PO/PM/QD/RD）自动进成员表，角色用「项目经理/测试负责人」等 |
| 删除项目 | 成员行一起删掉 |

待办 + 我的地盘（59 项断言，脚本：`deploy/test-todo-my-module.sh`；浏览器实测：`deploy/ui-check/my.mjs`）：

| 场景 | 结果 |
|---|---|
| 演示数据的四个范围 | 今天 3 条、未完成 5 条、已过期 1 条、明天 1 条 ✅ |
| 过期标记 | 未完成且日期早于今天 → `overdue=true`；已完成的不算过期 ✅ |
| 新建默认值 | 只给名称：日期=今天、归属与指派都是自己、状态 `wait`、优先级 3 ✅ |
| 类型校验 | `type=task` 没给 objectID → `1020021005`；类型非法 → `1020021004` ✅ |
| 批量创建 | 一次给 2 个名称 → 2 条待办，共用日期与优先级 ✅ |
| 状态流转 | 开始（wait→doing）→ 完成（写完成人/时间）→ 关闭 → 激活；重复操作各自被拦（`1020021001`/`21002`/`21003`）✅ |
| **关闭改指派人** | **`assignedTo` 变成伪用户 `closed`；激活后从 `finishedBy` 还原** ✅ |
| **我的待办口径** | **别人指派给我的（assignedTo=tester 时在 tester 列表里）与我指派给别人的（assignedToOther）都能查到** ✅ |
| **私有待办** | **别人建的私有待办在我这里显示「这是私有待办」；归属人自己看到真实名称** ✅ |
| 挪到今天 | 未完成的过期待办（含演示数据那条）被挪到今天，**已完成的不动** ✅ |
| **概览自洽** | **概览里的 今天/未完成/已过期 与「我的待办」三个范围的条数完全一致** ✅ |
| 我的任务/缺陷/需求 | `assignedTo` 被强制成当前账号：指派给 tester 的任务不会出现在我的列表里 ✅ |
| 我的工时 | 登记 2.5 小时后，`my/effort-page` 能查到、概览的本月工时正好是 2.5 ✅ |
| 我的动态 | `zt_action.actor = 我`，含待办记录且全部是我 ✅ |
| 枚举与清理 | 类型 6 种、状态 4 种 + 聚合状态 `undone`；清理并把演示数据还原回 3/5/1/1 ✅ |

我的地盘第二组（45 项断言，脚本：`deploy/test-my-workspace-module.sh`；浏览器实测：`deploy/ui-check/my-workspace.mjs`）：

| 场景 | 结果 |
|---|---|
| **我参与的项目：两条路径都算** | **PM=我 的项目、以及 PM 是别人但团队成员里有我的项目，都能查到；与我无关的（PM 别人 + 团队里没我）查不到** ✅ |
| **团队命中靠 `FIND_IN_SET`** | **团队是逗号串，命中来源与 `zt_project.team` 的真实值交叉核对过** ✅ |
| 项目列表不混入执行 | 返回行的 `type` 全部是 `project`（共用表的老坑） ✅ |
| 条件里的引号 | `name=%27` 当普通字符处理（预编译参数，无注入） ✅ |
| **我参与的执行** | **团队成员命中 → 出现；团队里没我 → 不出现；QD=我 → 也出现（负责人字段路径）** ✅ |
| **团队字符串与成员表同步** | **`add-member` 之后 `zt_project.team` 变成 `tester,admin`（两处数据源一起刷）** ✅ |
| 执行的 type 过滤 | `type=sprint` 只剩迭代，阶段被排除 ✅ |
| **我的团队** | **只返回 account=我的行；补上了对象名/状态/姓名；可用工时 5 × 7.5 = 37.5，受限标记 `limited=yes` 原样透出** ✅ |
| 团队行数与库一致 | 接口条数 == `zt_team` 里 `account=admin` 的行数 ✅ |
| **我的测试单** | **owner=我 的算；owner 换成别人但 createdBy=我 的也算；两个都换成别人后消失** ✅ |
| **我的用例是 OR** | **我创建的（C1）+ 别人创建但我评审过的（C2）合并成 2 条；只创建没评审的（C3）不算** ✅ |
| **用例按 id 去重** | **既是我创建又是我评审的用例在列表里只出现一次** ✅ |
| 用例口径与库一致 | 合并后的条数 == 库里 `openedBy=admin OR FIND_IN_SET(admin, reviewedBy)` 的条数 ✅ |
| **我的文档** | **我创建的正文算；章节（`type=chapter`）默认剔除；创建人换成别人、最后由我修改的也算；三处都不含我就消失** ✅ |
| **我的日历** | **不传 month 取当月，今天的待办落到今天那一格；每天带 count；日期升序；只收待办/任务/测试单三类** ✅ |
| 日历的测试单 | 我负责的测试单按 `begin` 落到日历上（owner 不是我的不落） ✅ |
| 空月份与非法月份 | `month=2030-01` 返回空数组（不是 null）；`month=abc` 被拦 ✅ |
| **鉴权** | **不带令牌访问「我参与的项目」返回 code=401（账号来自登录上下文，不是请求参数）** ✅ |
| 既有能力回归 | 概览、我的任务分页不受影响；清理后删掉的项目不再出现 ✅ |

报表（39 项断言，脚本：`deploy/test-annual-report-module.sh`；浏览器实测：`deploy/ui-check/report.mjs`）：

| 场景 | 结果 |
|---|---|
| **动作数与库一致** | **公司视角的 `actions` 正好等于 `zt_action` 本年条数** ✅ |
| 待办与工时 | 待办总数/未完成、`SUM(consumed)` 都与库交叉核对一致 ✅ |
| **贡献排除已删除对象** | **需求状态分布只数未删除的对象（8）；月度趋势数全部动作（473）—— 两个口径不同且都与禅道一致** ✅ |
| 雷达关系 | 雷达五类之和 ≥ 贡献明细之和，差额不超过「多标签任务数」（`task.create` 同时归执行与研发）✅ |
| 执行统计的过滤 | 只收 `type='sprint' AND multiple=1`（多迭代项目下的迭代）且起止在本年 ✅ |
| 用例「执行」 | 等于 `zt_testresult` 的流水条数 ✅ |
| **个人视角** | **`who` 是姓名、`logins` 等于 `system_login_log` 里该账号本年成功登录数、`actions` 等于 `actor=admin` 的条数** ✅ |
| 部门视角 | `who` 是部门名，动作数 ≤ 公司动作数（含子部门）✅ |
| 空年份 | 2019 年返回 `0 / {} / []`，不报错、不留空壳条目 ✅ |
| **每日提醒** | **快到期的缺陷数、未完成待办数都与库一致；每人 `total` = 五类之和；按条数倒序** ✅ |
| 提醒是活的 | 现建一个「指派给我、今天到期」的任务 → 进提醒；删掉后消失 ✅ |
| **产出统计** | **每类合计 = 动作明细之和；任务类有「完成」（按 finishedDate）、用例类有「执行」** ✅ |
| 项目状态总览 | 全部 = 有团队行的项目数；按人过滤 = 我参与的团队项目数 ✅ |
| 鉴权 | 不带令牌 → `code=401` ✅ |

执行燃尽图（32 项断言，脚本：`deploy/test-burn-module.sh`；浏览器实测：`deploy/ui-check/burn.mjs`）：

| 场景 | 结果 |
|---|---|
| **快照口径** | **原计划 30 / 剩余 25 / 已消耗 0 —— 已取消任务的 99 没算进来；已关闭任务的剩余与已完成任务的预计都被扣掉** ✅ |
| 当天覆盖 | 重复计算只覆盖当天那行（唯一键 `execution + date + task`），不产生第二行 ✅ |
| **缺失日期补齐** | **手工插一条 10 天前的快照（剩余 50）→ 从那天到昨天全是 50，今天换成真实值 25；首条快照之前是 0** ✅ |
| 数组对齐 | labels / burnLine / baseLine 三条线等长（今天之后的值是 null）✅ |
| **理想线** | **从首值线性降到计划结束日为 0；单调不增；中点仍大于 0** ✅ |
| burnBy 切换 | `estimate` 取原计划、`consumed` 取已消耗；非法值回落 `left` ✅ |
| **周末过滤** | **7 天区间：含周末 7 个点、跳过周末 5 个点；跳过的模式里一个周末都没有** ✅ |
| **自动采样** | **120 天区间自动 `interval=3`，点数压到 25~40；计划结束日仍被保留（否则理想线的 0 点会丢）** ✅ |
| **延期段** | **过了计划结束日还没结束 → 自动带 `withdelay` 并给出延期线；结束日之后实际线是 null、延期线有值（两线在结束日交汇）** ✅ |
| 未延期 | 计划结束日在未来时不给延期线 ✅ |
| 边界 | 对项目取燃尽图 `1020006007`、执行不存在 `1020006000`、没有起止日期 `1020006006` ✅ |
| 已关闭的执行 | 不参与 `computeBurn`（返回 0 条）✅ |

测试仪表盘（25 项断言，脚本：`deploy/test-qa-module.sh`；浏览器实测：`deploy/ui-check/qa.mjs`）：

| 场景 | 结果 |
|---|---|
| **汇总与库交叉核对** | **缺陷总数/激活、有效缺陷、用例总数/待评审、测试单总数/未完成都与库逐个核对一致** ✅ |
| **有效缺陷的口径** | **= 状态激活 或 解决方案为已修复/延期处理/不予解决**（两个激活缺陷 → 有效 2）✅ |
| **修复率的分母** | **一个 `fixed` + 一个 `bydesign` → 有效 1、已修复 1、修复率 100%（不是 50%：无效缺陷会被踢出分母）** ✅ |
| 按产品统计 | 同一行也是「有效 1 / 已修复 1」；区间内新增/解决数同步 ✅ |
| 分布 | 缺陷按状态与解决方案分组一致（resolved 2 / fixed 1）✅ |
| **列表块** | **解决完的缺陷从「待处理缺陷」消失；新建激活缺陷立刻出现；待评审用例与未完成测试单一并进块** ✅ |
| 过滤 | 按产品过滤后汇总卡片跟着变；页签计数带数字 ✅ |
| 边界 | `days=0` 回落 7 天；不存在的产品汇总 0 但产品列表仍全量（不报错）✅ |
| 鉴权 | 不带令牌 → `code=401` ✅ |

代码库（37 项断言，脚本：`deploy/test-repo-module.sh`；浏览器实测：`deploy/ui-check/repo.mjs`）：

| 场景 | 结果 |
|---|---|
| **校验** | **重名 → 拒；路径不存在/没有 `.git` → 拒；`scmType=svn` → 拒（只支持 git）** ✅ |
| **首次同步** | **一个真实的本地 git 仓库（3 次提交）→ 进来 3 条；代码库上记录「同步到 <sha>」与条数** ✅ |
| **幂等** | **再同步一次 → 0 条；库里仍只有 3 条** ✅ |
| 提交顺序 | 按 `commit` 序号倒序，最早那条就是仓库的 root sha ✅ |
| **重命名** | **`git mv` 的提交带 `action=R` 与 `oldPath`（`README.md → docs.md`）** ✅ |
| 不存在 revision | `1020029004` 提交记录不存在 ✅ |
| **对象关联** | **`Task #1` / `Story #1 Task #1,2` / `Bug #1` 解析成 4 个关联对象，写进 `zt_relation`** ✅ |
| **双向可查** | **提交详情里带关联对象（并回查了标题）；反查「任务 1 被第 1、2 次提交关联」** ✅ |
| **增量同步** | **新增一次提交后只拉 1 条，总数变 4，文件行数不翻倍（先清后写）** ✅ |
| 修改 | 改描述生效、`synced` 保留；**改路径 → 同步痕迹清零**（否则会拿旧 sha 去新库里找）✅ |
| 删除 | 提交记录与改动文件一起物理清理（这两张表没有 `deleted` 列）✅ |
| 鉴权 | 不带令牌 → `code=401` ✅ |

节假日（31 项断言，脚本：`deploy/test-holiday-module.sh`；浏览器实测：`deploy/ui-check/holiday.mjs`）：

| 场景 | 结果 |
|---|---|
| CRUD | 建假期/补班、`year` 由 `begin` 自动推出、按年与按类型筛选、改与删 ✅ |
| **假期口径** | **10-01~10-07 全设成假期后，这 7 天里 0 个工作日** ✅ |
| **补班口径** | **10-10（周六）设成补班后，[10-09, 10-12) 里有 2 个工作日（10-09 周五 + 10-10 周六）** ✅ |
| 普通一周 | 没有节假日的一周 → 5 个工作日 ✅ |
| **左闭右开** | **单日假期 → 0、单日工作日 → 1、单日周一 → 1、单日周六 → 0**（照抄禅道的区间语义）✅ |
| **燃尽图跳过假期** | **跨国庆的迭代（09-21~10-09）横轴 10 个点：10-01~10-07 全被跳过，10-08/10-09 仍在** ✅ |
| **燃尽图算上补班** | **把 09-26（周六）设成补班 → 横轴 10 → 11 个点；再把一个普通工作日设成假期 → 回到 10 个点** ✅ |
| weekend 口径不受影响 | `type=weekend` 仍是自然日 19 天 ✅ |
| 删除与回归 | 删完假期后 10-01~10-07 又变回 5 个工作日；节假日表清空以免影响 burn 的绝对点数断言 ✅ |
| 鉴权 | 不带令牌 → `code=401` ✅ |

**这一轮顺带修好的两个主干链路口径**（回归里 33 个脚本全绿，说明改动没有连带破坏）：

| 修正 | 现象 | 依据 |
|---|---|---|
| 执行的动作 `objectType` 由 `project` 改成 `execution` | 报表按 `objectType` 统计时项目与执行混在一起；回收站里「执行」显示成项目 | 禅道 demo 是 `('execution', 3, execution=3)`（481 条历史数据一起搬） |
| 执行行的 `multiple` 继承所属项目 | 项目 `multiple=1`、执行 `multiple=0`，报表年度执行统计一个都查不到 | 禅道建执行时沿用项目的 `multiple` |

项目视角四模块（20 项断言，脚本：`deploy/test-project-view-module.sh`）：

| 场景 | 结果 |
|---|---|
| **工作量估算的派生值** | **演示数据 规模 100 / 生产率 5 → 工期 20；20×8×1500 = 240000** ✅ |
| 保存后重算 | 规模 90 / 生产率 6 → 工期 15；15×8×2000 = 240000，并记录修改人 ✅ |
| **派生值不接受入参** | **请求里塞 `duration=999, totalLaborCost=999` 会被忽略** ✅ |
| 生产率为 0 | 不做除法（工期 0，不报错）✅ |
| 项目不存在 / 没有估算 | `1020005000` / 返回 `null`（不是错误，禅道 `getBudget` 也是 null）✅ |
| 项目计划视图 | 项目 1 关联 1 个产品 → 2 个计划；没关联产品的项目 → `0 0` ✅ |
| **项目构建视图** | **项目 1 → 3 个构建（含执行 90001 的）；给新项目的执行建构建后，项目视图里立刻能看到** ✅ |
| 项目发布视图 | 项目 1 → 3 个发布（`FIND_IN_SET` 反查逗号列表）；手工指定 `projects` 建发布 → 新项目能反查到 ✅ |


干系人（23 项断言，脚本：`deploy/test-stakeholder-module.sh`；浏览器实测：`deploy/ui-check/stakeholder.mjs`）：

| 场景 | 结果 |
|---|---|
| 演示数据 | 项目集 9001：admin（关键/团队成员）、张三（甲方）（关键/外部）、tester（公司同事）；项目 1：2 人 ✅ |
| **干系人 ≠ 团队成员** | **给项目加干系人后 `teamCount` 仍是 3（不进 `zt_team`）** ✅ |
| **`type` 由 `from` 推导** | **`from=outside` → `type=outside`（外部人员直接用存下来的名字当作 realname）** ✅ |
| 唯一性 | 同一个人重复加同一对象 → `1020023001` ✅ |
| 校验 | 来源非法 `1020023003`；挂到产品下 `1020023002`；不填干系人 `400` ✅ |
| 批量添加 | 4 个里已有 2 个 → 只新增 2 个（已存在的静默跳过，与禅道一致）✅ |
| 修改 | 改来源后 `type` 跟着变、关键标记生效；关键干系人排在列表最前面 ✅ |
| 删除 | 按编号删、按「对象 + 账号」删（禅道 `delete(userID)`）；不存在 → `1020023000` ✅ |
| 我参与的 | `list-by-user` 按账号反查对象编号 ✅ |
| 枚举 | 类型 2 种（内部/外部）、来源 3 种（团队/公司/外部）✅ |

质量链（25 项断言，脚本：`deploy/test-quality-chain.sh`）：

| 场景 | 结果 |
|---|---|
| **缺陷解决 → 构建清单** | **解决版本=构建 A → `zt_build.bugs` 自动并进该缺陷** ✅ |
| **缺陷解决 → 发布清单** | **包含构建 A 的发布 A 也自动拿到它（`zt_release.bugs` + `zt_releaserelated` 同步）** ✅ |
| 发布清单接口 | 「解决的 Bug」里查得到；「未关联候选」里已经消失 ✅ |
| 构建不属于任何发布 | 只回写构建，发布 A 不被误加 ✅ |
| **影子构建** | **不带构建创建的发布 C → 缺陷填它的 shadow 编号 → 发布 C 同样拿到** ✅ |
| 解决版本填版本名（非编号） | 静默跳过，不报错也不污染清单 ✅ |
| 去重 | 同一缺陷重复关联，发布清单里只出现一次 ✅ |
| **反向：关联即解决** | **把未解决缺陷关联到构建 A → 自动 resolved 且写 resolvedBuild=A，发布 A 同步拿到** ✅ |

项目集（55 项断言，脚本：`deploy/test-program-module.sh`；浏览器实测：`deploy/ui-check/program.mjs`）：

| 场景 | 结果 |
|---|---|
| 演示项目集 9001 | `path=,9001, / grade=1 / doing`，1 个子项目集、2 个项目、1 个产品 ✅ |
| 项目集下的项目 | 2 个项目，`path=,9001,1,` 与 `,9001,2,`、`grade=2`、`parent=9001` ✅ |
| 项目集下的产品 | `zt_product.program=9001`，1 个 ✅ |
| **三种角色互不串味** | **项目列表查不到项目集、执行列表查不到项目集、项目集列表查不到项目** ✅ |
| **拿错 id** | **项目/执行 id 查项目集 → `1020020000`；项目集 id 查项目 → `1020005008`；项目集 id 查执行 → `1020006005`** ✅ |
| 新建顶级项目集 | `path=,id,`、`grade=1` ✅ |
| 新建子项目集 | `path=父.path+id+','`、`grade=2` ✅ |
| 同级重名 | `1020020001`；**换个上级后同名 → 允许**（禅道只在本级查重）✅ |
| 日期校验 | 结束早于开始 → `1020020008`；缺开始/结束/名称 → `400` ✅ |
| 上级不是项目集 | 传项目 id / 执行 id / 自己 → `1020020009` ✅ |
| **项目挂到项目集** | **`path=,P,项目,, grade=2, parent=P`；项目列表按所属项目集过滤** ✅ |
| 项目的所属项目集传项目 id | `1020005009` ✅ |
| **移动项目集** | **挂到 9001 下：自己 `,9001,P,`/grade2，子项目集 `,9001,P,SUB,`/grade3，项目同步 → 再挪回顶级全部还原** ✅ |
| 产品归属项目集 | `program=P` 过滤生效；项目集不存在 `1020020011`、产品线不存在 `1020020012` ✅ |
| 状态流转 | 开始（wait→doing，写 `realBegan`）/挂起/激活/关闭；重复开始 `1020020003`、重复关闭 `1020020004`、已关闭不能改 `1020020002` ✅ |
| **删除保护** | **有子项目集 `1020020005` / 有项目 `1020020006` / 有产品 `1020020007`，逐层清空后才删得掉** ✅ |
| 演示数据完好 | 测完 9001/9002 与项目 1/2 的层级数据原样 ✅ |

工时明细（47 项断言，脚本：`deploy/test-effort-module.sh`；浏览器实测：`deploy/ui-check/effort.mjs`）：

| 场景 | 结果 |
|---|---|
| 演示数据自洽性 | 条数 ≥ 2、`consumed` = 所有工时之和、`left` = **最后一条**工时的 `left` ✅ |
| 未开始的任务登记工时 | 消耗 3、剩 5 → 任务 3/5，状态仍 `wait`（没干活就不自动完成）✅ |
| **剩余归零** | **`left=0` → 任务自动 `done`** ✅ |
| **已完成又冒出剩余** | **再登记一条剩 3 → 任务退回 `doing`** ✅ |
| 不填剩余工时 | 按「当前剩余 - 本次消耗」推算（3-2=1）✅ |
| 改一条工时 | 消耗按总和重算（1+2+1+2=6），剩余仍是最后一条声明的 1 ✅ |
| 删非最后一条 | 消耗 -2，**剩余不变**（还是最后一条说了算）✅ |
| **删最后一条** | **剩余回到新的最后一条声明的 3** ✅ |
| **删光所有工时** | **消耗归零、状态退回 `wait`、剩余回到 `estimate`，完成人/完成时间/关闭原因一并清空** ✅ |
| 未开始的任务删光工时 | 已消耗归零，但剩余**保持任务字段原值 5**（禅道口径，见坑位 #36）✅ |
| 参数校验 | 负数消耗 / 空工作内容 / 空任务编号 → `400`；任务不存在 → `1020002000` ✅ |
| 工时改挂到另一个任务 | `1020019001` 拒绝（要同时重算两个任务，禅道也不允许）✅ |
| 列表 / 分页 / 汇总 | 按日期正序 + 回填任务名；账号/日期区间过滤；按账号汇总降序且任务数/条数/合计正确 ✅ |
| **任务被删、工时还在** | **列表照常返回、`taskName` 为空而不是 500**（悬空引用容忍）✅ |
| 操作日志 | 任务的动态里留下 `recordworkhour`（消耗/修改/删除三种文案）✅ |

任务状态机（7 个场景）：

| 场景 | 结果 |
|---|---|
| 创建后默认值 | `status=wait`, `consumed=0`, `left=estimate` ✅ |
| 未完成就关闭 | `1020002005` 只有已完成的任务才能关闭 ✅ |
| 开始任务 | `wait` → `doing`，写入 `realStarted` ✅ |
| **完成但剩余工时=3** | **状态回到 `doing`，不是 done** ✅ |
| **完成且剩余工时=0** | **转为 `done`，`consumed` 累计为 8（5+3，不是覆盖）** ✅ |
| 已完成再关闭 | 成功 ✅ |
| 已关闭再开始 | `1020002003` 状态不允许 ✅ |

缺陷状态机（10 个场景）：

| 场景 | 结果 |
|---|---|
| 未解决就关闭 | `1020003003` 只有已解决的缺陷才能关闭 ✅ |
| 解决方案=fixed 但不给版本 | `1020003008` 必须填写解决版本 ✅ |
| 解决方案=duplicate 但不给编号 | `1020003006` 必须指定重复缺陷 ✅ |
| duplicate 指向自己 | `1020003009` ✅ |
| duplicate 目标不存在 | `1020003007` 不存在：99999 ✅ |
| 非法解决方案 | `1020003005` 解决方案不合法 ✅ |
| 正常解决 | `resolved` + `resolution=fixed` + `resolvedBuild=v1.1` ✅ |
| 重复解决 | `1020003002` 已经是解决状态 ✅ |
| 已解决再关闭 | 成功 ✅ |
| **重新激活** | `closed` → `active`，`activatedCount` 0→1，**解决信息被清空** ✅ |

操作日志（任务与缺陷均已接入）：

| 场景 | 结果 |
|---|---|
| 任务工时变更留痕 | `consumed: '0.00' → '5.00' → '8.00'`，`left: '8.00' → '3' → '0'` ✅ |
| 任务状态流转留痕 | `status: 'wait' → 'doing' → 'done' → 'closed'`，附带各阶段操作人/时间 ✅ |
| 缺陷解决留痕 | `status`/`resolution`/`resolvedBuild`/`resolvedBy`/`resolvedDate` 全部记录 ✅ |
| 缺陷激活留痕 | `activatedCount: '0' → '1'`，且记录被清空的 `resolution`/`closedBy` ✅ |

前端页面（浏览器实测）：

| 步骤 | 结果 |
|---|---|
| 菜单与路由 | `/zentao` → `/zentao/story` 动态注册成功 ✅ |
| 列表渲染 | 11 列、分页「共 9 条」、中文与产品名正常 ✅ |
| 新建需求 | 弹窗 9 个字段，提交后列表刷新 ✅ |
| 详情抽屉 | 三个 Tab，基本信息 14 项全部正确 ✅ |
| 版本历史 | v2 与 v1 两行，各自内容独立 ✅ |
| 提交评审 | 状态变为「评审中」✅ |
| 评审表决 | 投票「确认通过」后状态变为「激活」✅ |
| 操作日志 | 时间线按时间倒序，字段中文标签，变更行数准确 ✅ |
| 差异弹层 | 点击「查看差异」显示行级 diff ✅ |
| 任务列表页 | 11 列，状态标签、工时三列（预计/已消耗/剩余）正确 ✅ |
| 任务新增弹窗 | 11 个字段，提交后列表从 1 条变 2 条 ✅ |
| 缺陷列表页 | 11 列，严重程度着色、激活次数标记正确 ✅ |
| 执行管理页 | 14 列（含所属项目），`/zentao/execution` 路由正常，无 404 ✅ |
| 执行新增弹窗 | 10 个字段；所属项目下拉可选；提交提示「新增成功」并刷新列表 ✅ |
| 执行状态流转 | 点「开始」后该行状态标签变为「进行中」，按钮切换为「挂起」✅ |
| 执行 → 任务联动 | `/zentao/task?execution=N` 自动带过滤，任务页新增「所属执行」列/下拉 ✅ |
| 分支/平台页 | `/zentao/branch` 12 列；主干行没有编辑/删除按钮 ✅ |
| 分支设为默认 | 点击后该行出现「默认」标签、按钮消失；产品类型是 platform 时文案显示「平台」✅ |
| 模块树页 | `/zentao/module` 表格树按层级展开（子模块 grade=2、path=,28,30,）✅ |
| 模块新建/编辑弹窗 | 8 个字段，上级模块用树选择器；编辑回填名称与排序 ✅ |
| 需求列表按模块/分支过滤 | 选产品→列 2 条；选分支→1 条；回主干→1 条；选**父模块**→连带查出挂在子模块下的需求 ✅ |
| 计划管理页 | `/zentao/plan` 10 列；待定计划显示「待定」标签与「待定」周期；子计划/父计划有标记 ✅ |
| 计划新增弹窗 | 7 个字段；打开「待定」开关后开始/结束日期自动隐藏 ✅ |
| 计划关联需求抽屉 | 左右两栏「已关联(1) / 未关联(0)」，标题带计划名 ✅ |
| 需求表单/列表接计划 | 需求表单有「所属计划」下拉；列表新增「计划」列与过滤，显示计划名 ✅ |
| 构建管理页 | `/zentao/build` 10 列；集成构建带「集成」标签、被引用的构建带「被引用」标签，并显示包含的子构建名 ✅ |
| 构建新增弹窗 | 打开「集成构建」后出现「包含构建」多选；普通产品不显示分支字段 ✅ |
| 构建关联抽屉 | 两个 Tab「需求 / Bug」，未关联列表可多选关联，Bug 关联前有「会自动解决」的二次确认 ✅ |
| 缺陷解决弹窗接构建 | 「解决版本」由手工输入改为构建下拉（`resolvedBuild` 存构建编号，与禅道一致）✅ |
| 发布管理页 | `/zentao/release` 11 列；里程碑/被包含有标记；「发布」按钮一键把版本置为已发布 ✅ |
| 发布新增弹窗 | 9 个字段；含「同步构建数据」开关；wait 状态不要求实际发布日期（表单校验联动）✅ |
| 发布清单抽屉 | 三个 Tab「需求 / 解决的 Bug / 遗留的 Bug」，未关联候选可勾选关联 ✅ |
| 项目需求范围页 | `/zentao/projectstory` 默认选中第一个项目；「关联的产品」与「需求范围」两张表；演示数据显示 **v1 → v2 版本已变更** 与「同时被关联 #1」✅ |
| 纳入需求抽屉 | 左右两栏「已纳入(2) / 可纳入(2)」，可勾选纳入 ✅ |
| 阶段管理页 | `/zentao/stage` 两块：阶段模板（5 个阶段 + 占比合计标签）与项目阶段（选项目/生成/删除/开始/关闭）✅ |
| 阶段模板表单 | 5 个字段，并提示「当前该流程已占用 100%，本次最多还能填 0%」✅ |
| 组织与权限页 | `/zentao/organization` 四个 Tab：用户（含可访问模块标签、权限明弹窗）/ 权限包（模块·条数标签）/ 部门树 / 迁移映射对照 ✅ |
| 需求/缺陷/任务表单 | 新增「分支/平台」「所属模块」控件：分支选项随产品类型出现，模块树随产品/执行联动 ✅ |
| **14 个页面一次性巡检** | **产品 / 分支 / 需求 / 项目 / 执行 / 任务 / 缺陷 / 模块 / 计划 / 构建 / 项目需求 / 阶段 / 组织权限 / 发布 全部渲染出表格，无 404、无 pageerror** ✅ |
| 控制台 | 无真实错误；网络请求无 4xx/5xx ✅ |

---

## 三、架构决策

### 3.1 表结构与字段名对齐禅道，不做命名转换

`zt_story` 的列名保持禅道的驼峰风格（`openedBy` / `assignedTo` / `closedReason`），
而不是改成 Java 惯例的下划线。理由：

1. 迁移期可以直接从禅道库导数据，字段一一对应，不需要中间映射层
2. 出问题时能和 PHP 实现左右对照，降低理解成本
3. 禅道已有报表、习惯叫法可以原样沿用

代价：MyBatis-Plus 默认把驼峰转下划线，所以这些字段必须用 `@TableField("openedBy")`
显式声明列名，否则报 `Unknown column 'opened_by'`。

### 3.2 需求版本机制：头部 + 追加式快照

```
zt_story       头部：当前状态、当前版本号
zt_storyspec   快照：(story, version) 唯一，保存每一版内容，只追加不修改（除非普通编辑）
```

两条写路径语义完全不同，不能混用：

| 方法 | 对应禅道 | 行为 |
|---|---|---|
| `updateStory()` | `model.php update()` + `tao.php doUpdateSpec()` | **原地 UPDATE** 当前版本的快照，版本号不变 |
| `changeStory()` | `model.php change()` + `tao.php doCreateSpec()` | **INSERT** 一条 `version+1` 的新快照，旧版本永久保留 |

### 3.3 评审绑定到版本，聚合后流转状态

评审记录存 `(story, version, reviewer)`。只有**当前版本的全部评审人都提交后**才触发流转：

```
pass    -> active
clarify -> draft（若曾变更过则 changing），清空 reviewedBy
revert  -> active，且 version-1，并删除当前版本的快照与评审记录
reject  -> closed，指派给 closed
```

聚合规则（对应禅道 `getReviewResult`）：`allpass` 全员通过才算通过；未通过时按多数派
（`floor(n/2)+1`）判定，没有多数派则取最强反对意见（clarify > revert > reject）。

### 3.4 操作日志：动作 + 字段级差异

对应禅道 `action::create()` + `logHistory()` + `common::createChanges()`：

```
zt_action    一次操作一行（谁、何时、对哪个对象、做了什么）
zt_history   挂在 action 下的字段级差异（field / old / new / diff）
```

**黑名单是这套机制的关键**。`lastEditedDate`、`assignedDate`、`uid` 这类字段每次保存都会变，
如果照实记录，日志会被「最后修改时间从 A 变成 B」淹没，真正的业务变更反而看不见。
`ChangeDetector` 保持与禅道一致的跳过策略：黑名单字段 + 空值日期字段都不记录。

**长文本用行级 diff**。禅道用 `common::diff()` 生成 HTML（`<del>`/`<ins>`）。这里刻意
**不生成 HTML** —— 日志内容会直接渲染到页面，存 HTML 会引入 XSS 风险。改为输出统一 diff
风格的纯文本（`- ` 删除行 / `+ ` 新增行 / 两空格 未变行），前端放在 `<pre>` 里着色：

```
  第一行
- 第二行
+ 第二行已修改
  第三行
+ 新增第四行
```

这是本项目对禅道**唯一有意的格式偏离**，原因安全优先，已在 `LineDiff` 类注释里说明。

### 3.5 任务工时模型：剩余归零才算完成

禅道任务的精髓是三个互相牵制的数字：

```
estimate  预计工时
consumed  已消耗工时 —— 累计值，每次完成时把「本次消耗」加上去，不是覆盖
left      剩余工时   —— 用最新登记值覆盖
```

**关键规则**：完成任务时登记「本次消耗」与「剩余工时」，**剩余归零才算真正完成**；
剩余大于 0 时任务回到「进行中」。这条规则保证了「已完成」的任务一定没有未做完的活，
项目的进度与燃尽图才能由这三个数字可靠推导。

### 3.6 缺陷解决的两条联动校验

禅道在 `resolve()` 里用 `checkIF` 做了强联动，本实现保持一致：

| 解决方案 | 强制要求 |
|---|---|
| `duplicate`（重复Bug） | 必须指定 `duplicateBug`，且该缺陷必须存在、不能是自己 |
| `fixed`（已解决） | 必须指定 `resolvedBuild` |

另外激活缺陷时会**清空上一次的解决信息**（`resolution` / `resolvedBy` / `closedBy`），
避免出现「状态是 active 但还留着 fixed 解决方案」的不一致。

### 3.7 三种角色、一张表：`TABLE_PROGRAM` / `TABLE_PROJECT` / `TABLE_EXECUTION` 都是 `zt_project`

这是整个迁移里**最容易做错、也最值得单独讲**的一条。

在禅道源码里（`config/zentaopms.php`）：

```php
define('TABLE_PROGRAM',   '`' . $config->db->prefix . 'project`');   // 项目集
define('TABLE_PROJECT',   '`' . $config->db->prefix . 'project`');   // 项目
define('TABLE_EXECUTION', '`' . $config->db->prefix . 'project`');   // 执行
```

**三条常量、一张表、三种角色**，靠 `type` 字段区分：

| `type` | 角色 | 怎么归属上级 |
|---|---|---|
| `program` | 项目集 | `parent` → 上级项目集（项目集可以套项目集） |
| `project` | 项目 | `parent` → 所属项目集 |
| `sprint` / `stage` / `kanban` | 执行（迭代/阶段/看板） | `project` → 所属项目 |

三者共用 `path` / `grade`：**项目集与项目构成一棵树，执行不在树里**。
移植时最容易搞错的两点：

1. **`parent` 对项目来说不是「父项目」而是「所属项目集」** ——
   禅道 `module/project/model.php` 里写得很直白：
   `$program = $project->parent ? $this->getByID((int)$project->parent) : new stdclass();`
   所以项目之间是**平级**的，不存在「项目套项目」（这是本项目上一轮的误读，已纠正）。
2. **`path` 是逗号格式、`grade` 从 1 开始**（`module/program/model.php#setTreePath`）：
   顶级 `,9001,` grade=1，下级 `父.path + id + ','` grade=父.grade+1。
   节点 `path` **包含自己**，所以「取整棵子树」就是一次 `path LIKE '%,id,%'`。
   （`zt_module` 的 path 也是逗号格式，但它是按 `(root, type, branch)` 分树的另一套；
   `zt_project` 这份**不是**斜杠格式。）

**产品也挂在项目集下**：`zt_product.program` 指向项目集（`zt_product.line` 才是产品线）。

禅道这么设计有充分理由：任务（`zt_task.execution`）只需要一个外键就能同时挂到项目和执行上，
权限、团队、工时、燃尽图这些逻辑也全部复用，不用维护两套。

**代价是：任何一次查询都必须显式带上 type 条件**，漏一个就会把项目集当成项目、
或把项目当成执行。本实现把这条约束落到四处：

1. `ProjectMapper` 的项目查询（`selectPage` / `selectSimpleList` / `selectListByParent` / `selectByName`）
   统一加 `type = 'project'`
2. `ExecutionMapper` 侧（复用 `ProjectMapper`）统一加 `type IN ('sprint','stage','kanban')`
3. `ProgramMapper` 的每个查询都写死 `type = 'program'`
4. 三个 `validateXxxExists` 互相拦截：拿执行 id 调项目接口报 `1020005008`，
   拿项目 id 调执行接口报 `1020006005`，拿项目/执行 id 调项目集接口报 `1020020000`，
   拿项目 id 当「所属项目集」报 `1020005009`

迁移建议：**先确认每张表在禅道里是不是被复用的**。除了看 `define('TABLE_*')`，
还要警惕**多个常量指向同一个表**这种写法（`grep` 一下同名表名最省事）——
只看表名会以为是三张表，只看常量名会以为是三套数据。

### 3.8 模块树：一张表存所有对象的树

`zt_module` 也是一张被复用的表 —— 它不是「需求的模块表」，而是**所有对象的通用树**：

| root | type | 含义 |
|---|---|---|
| 产品 id | `story` | 需求模块树（产品视图） |
| 产品 id | `bug` | 缺陷模块树（测试视图） |
| 产品 id | `case` | 用例模块树 |
| 执行 id | `task` | 任务模块树（执行视图） |
| 0 | `line` | 产品线（`objectTables['productline'] = zt_module`） |

树的定位键是 `(root, type, branch)`，所以同一棵「用户中心」在需求视图和缺陷视图里是
**两条独立记录**，互不影响。禅道这么设计的收益是：模块的增删改移、树形渲染、
下拉选项只写一套代码，新增业务对象时加一个 type 就行。

**path 的格式很关键**，禅道用逗号分隔且**以逗号开头**，并且**包含自己的 id**：

```
root 哨兵            path = ','        grade = 0
一级模块 id=9        path = ',9,'      grade = 1
二级模块 id=10       path = ',9,10,'   grade = 2
```

由此推出的两条实现要点（都踩过坑，见第四节第 14 条）：
1. 取子孙用 `path LIKE ',9,%'`，**前缀就是模块自己的 path，不能再拼一次 id**
2. 移动/删除后必须调用 `fixModulePath()` 递归重算 path 与 grade

注意这套逗号约定和 `zt_project` 的 `/1/2/` 斜杠约定**不是一回事**，两个模块的树代码不能互相抄。

**删除模块会改挂业务数据**。禅道 `remove()` 不是简单删记录：它把被删模块（含子孙）上的
需求/任务/缺陷统一改成挂到**被删模块的父模块**上。这样删模块不会让数据消失，
列表页按模块筛选时也不会漏。本实现按同样规则做了三张表的改挂。

### 3.22 需求转任务：第 4 处「冻结版本」

任务可以挂在需求下（`zt_task.story`），并且记下**建任务时需求的版本**（`storyVersion`）。
需求后来正式变更，任务不会跟着变，而是提示「需求已变更」由人确认 ——
**已经按老需求做完的工作不该被无声改写**。

这是本项目第 4 处「冻结版本」，四处放在一起看，规则完全一致：

| 场景 | 冻结字段 | 冻结的是什么 |
|---|---|---|
| 项目/执行纳入需求 | `zt_projectstory.version` | 排期那一刻的需求版本（3.13） |
| 用例关联需求 | `zt_case.storyVersion` | 关联那一刻的需求版本（3.18） |
| 子需求挂父需求 | `zt_story.parentVersion` | 分解那一刻的父需求版本（3.21） |
| **任务挂需求** | **`zt_task.storyVersion`** | **建任务那一刻的需求版本（3.22）** |

**「已变更」只在需求是激活态时才算**：草稿需求还没人在做，谈不上「已排期的工作被改了」。
所以 `storyChanged` 的条件是 `需求版本 > 冻结版本 && 需求状态 = active`
（这与 `zt_projectstory` 的「版本已变更」、`zt_case` 的 needConfirm 是同一个判据）。

顺带补一句测试上的坑：草稿需求**不能**用「激活」接口变成激活态 ——
禅道的 `activate` 只处理「已关闭 → 激活」，草稿要走**评审**
（提交评审 → 评审人全票通过 → 状态变 active）。测试里为了验证「已变更」提示，
必须先跑一遍评审流程。

### 3.47 第 45 轮：度量快照口径修正 + 「能力替代」三分类（A/B/C）

这一轮没有新模块，做的是**收尾与定性**两件事：把上一轮全量回归里仅剩的两个红脚本清掉，然后把「剩下没做的模块」按一条新口径重新定性。

**一、最后两个红脚本（41/43 → 43/43）**

| 脚本 | 症状 | 根因 | 修法 |
|---|---|---|---|
| `test-metric-module.sh` | 3 项断言红，而且数值**正好是库里的 2 倍**（52/26、14.00/7.00、4/2） | **不是 JOIN 行放大，也没有重复数据**：15 条口径 SQL 全是单表聚合，单独拿到 MySQL 里跑就是真值。真凶是 `zt_metriclib` 里 **nodate 快照按天累积**（库里同时留着 09-14、09-17 两份），而读路径 `MetricServiceImpl.getMetricData → MetricLibMapper.selectListByCode` **没写日期条件**，把历史快照一起取回来按维度求和 —— 「正好 2 倍」只是因为恰好存了 2 天，多留一天就变 3 倍。禅道 `module/metric/tao.php#fetchMetricRecordsWithOption`（390 行）读 nodate 时是要加 `date >= today` 的，同一个 Mapper 的 `selectPageByCode` 也写了，只有这条读路径漏了 | 把 nodate 过滤**收敛到 Mapper 一处** `applyNodateFilter`（`date >= CURDATE()`，只对 `nodate` 生效），`selectListByCode` / `countByCode` / `selectPageByCode` 三条读路径统一调用；**周期型（year/month/week/day）语义零改动** —— 禅道 `clearOutDatedRecords` 只清周期型，nodate 的历史是故意留的 |
| `test-todo-my-module.sh` | 2 项演示数据计数红（`4 5 1 0`） | **不是产品 bug，是脚本不可重复**：演示数据的日期相对 `CURDATE()` 落库，而 `import2Today` 隔天会把所有未完成过期待办挪到今天，脚本结尾只还原了其中 1 条 | 加幂等函数 `demo_todo_reset()`（照 `deploy/sql/34-zt_todo.sql` 的形状先 DELETE 再 INSERT），开跑前与第 8 节各调一次 |
| `test-burn-module.sh`（**复跑时才暴露**） | 复跑一次红了 1 项：「首日值 实际=」，值是空的 | 断言用「**日历日相等**」去 `labels` 里定位那个点，而燃尽图 labels 默认 `noweekend`（跳过周末与节假日）：第一次跑时「今天 − 20 天」是周五（在 labels 里），跨过零点复跑时变成**周六**（不在），`[...][0]` 直接 IndexError | 改成按**位置**取：`labels` 里第一个 `>= 目标日期` 的下标，首日值取 `labels[0]`（快照落在周末时值会被后一个工作日承接）；连跑两次 32 项全绿。坑位 **#56** |

顺带把同类副作用一起修掉：`dataCount` 原本也按「所有 metriclib 行」统计（把历史快照算进去），`/metric/summary` 的 dataCount 实测 **42 vs 真值 23（偏大 82.6%）**、单 code `/metric/get` 是 **7 vs 3**；收敛到 `applyNodateFilter` 后为 **23 / 3**，周期型不变。
这条坑位记成了 **#55**（见第四节），教训是「**整数倍关系不等于重复数据**，先问一句『这个值是不是把多个时间片加起来了』」。
三个脚本连跑均 0 失败（metric 29 项 ×3、todo 59 项 ×2、burn 32 项 ×2），且**测试断言一条都没放宽**。
复跑还顺带暴露了**两个界面检查的时区 bug**：`my-workspace.mjs` / `burn.mjs` 用 `toISOString()`（UTC）算「今天」，在 UTC+8 的凌晨 0~8 点会比本地日期少一天，「今天那一格/那一行」于是假红（坑位 **#57**）。改成用本地时区拼日期后两个脚本各连跑两次全绿（21 项 / 12 项）。
**最终整轮复跑：43 个脚本 / 1704 项断言 / 页面巡检 21/21 / 20 个浏览器专项全绿，且零重试。**

**二、新口径：yudao 已有的能力 → 判「不迁移」**

用户拍板的新判据：**凡 yudao 侧已有等价能力的模块，一律算「不迁移」—— 不是做不了，而是不需要做**，只写能力替代映射；三分类：

| 判定 | 含义 | 处置 |
|---|---|---|
| **A** | 已被 yudao 现有能力替代 | 不迁移 + 写清承载方（表 / 菜单 / 接口模块） |
| **B** | 部分替代 | 不迁移 + 逐条记录缺什么 |
| **C** | yudao 也没有 | **真能力缺口**：要用只能重写，不是迁移 |

10 个 map-only 模块按此重判：**A 3 个**（`ai`→`yudao-module-ai`、`zai`→知识库 RAG、`cron`→`infra_job`）、**B 3 个**（`admin`/`dev`/`misc`，缺的都是安全设置、运行时文案、静态页这类后台细节）、**C 4 个**（`convert`/`extension`/`jenkins`/`mark`），逐条映射与实测证据见 **`docs/MAP-ONLY-MAPPINGS.md`**（843 行）。
99 个模块的同一套判定、以及「去掉可替代后剩下的真缺口」汇总表，见 **`docs/MODULE-FEASIBILITY-AUDIT.md`** 与可复现脚本 **`deploy/audit-module-feasibility.sh`**。

**这条口径带来的最重要结论**：原来记成「开源版无实现可搬」的那批模块（`mail`/`message`/`sso`/`setting`/`personnel`/`approval`/`ai` …），**大半其实是 yudao 已经替我们做好了** ——
`system_mail_*` 顶邮件、`system_notify_message` 顶站内信、`system_oauth2_client` 顶单点登录、`infra_config` 顶设置、`yudao-module-hrm` 顶人员、**`yudao-module-bpm`（Flowable）整块顶审批流**。
所以「**做不了**」与「**不需要做**」两张名单高度重合，真正属于「禅道有、yudao 无、且值得写」的能力只剩十来个（`risk`/`reviewissue`/`meeting`/`mr`/`feedback`/`weekly`/`artifact`/`programplan`/`pipeline` 等），其中还有一半在开源版里连代码都没有。

> **一条要记账的启用前置（2026-09-18 实测补齐）**：A/B 类点名的承载方里，除了 `system_mail_*`/`system_notify_*`/`infra_config`/`infra_job` 这些
> **落在 `system`/`infra` 两个已启用模块**上的能力，其余（`yudao-module-bpm` / `report` / `hrm` / `pms` / `im` / `ai`）**当前一个都没编进 jar** ——
> `yudao-server/pom.xml` 里除 `system`/`infra`/`zentao` 外其余模块依赖**全是注释**，实测调用返回 `{"code":501,"msg":"[CRM 模块 yudao-module-crm - 已禁用]…"}`
> （HRM/FMS/OA/CMS 等连控制器都没有，返回 404）。另外 `ai_*` **也没有建表脚本**（全仓 `.sql` 里 `CREATE TABLE … ai_*` 0 命中，表名只能从 DO 的 `@TableName` 推出）。
> 所以判 A 的正确读法是「**框架里有这个能力、装上即可用，因此不必迁移**」，不等于开箱可用；启用是固定三步：
> ① 打开 `pom.xml` 里那行依赖 → ② 执行它的建表 SQL → ③ 给角色分配菜单权限，然后重新 build（约 2.5 分钟）。
> 侧边栏上这批点进去只会 501/404 的死菜单，已由 `deploy/sql/56-hide-unused-menus.sql` 统一停用（改 `system_menu.status=1`，`filterDisableMenus` 递归生效，可逆）。

**三、顺手纠正的文档错误**（这轮的另一半价值）

- `MIGRATION-INVENTORY.md` 里「不在开源版（无实现可搬）」的**名单与计数本身有错**：`pivot`、`screen` 的表其实都在 `db/zentao.sql` 里（`zt_pivot` 3 张、`zt_screen` 1 张），属于「能做但没做」；而 `risk`/`reviewissue`/`meeting`/`approval` 是**模块代码不在发行包里、但表其实随开源版发布了** —— 先前把两类原因混成了一类。已按审计实测改正（并把 README 里与之打架的另一个数字一并统一）。
- `REMAINING-MODULE-VERDICTS.md` 里 `ai`/`convert`/`admin`/`cron` 四个标题的行数（11,707 / 9,668 / 3,226 / 1,132）不属于任何自然口径，已统一成**核心行数**（`control`+`model`+`zen`+`tao`，实测 5,360 / 5,634 / 1,499 / 814），并在该节开头写明口径。

### 3.46 第 44 轮：**三个子代理并行**做掉 `search` + `dimension` + `api` + `webhook`

这一轮换了打法：实现仍然串行收口（同一个 jar、同一个库、同一段菜单 ID、同一次全量回归，并行写码会互相踩），
但**取证与实现**交给子代理并行做。事实证明确实有效 —— 4 个模块 194 项接口断言 + 4 个界面检查全部落地。

| 模块 | 做了什么 | 照抄的关键规则 / 坑 | 回归 |
|---|---|---|---|
| `search`（保存查询） | `zt_userquery` + `zt_searchdict`、8 个端点、前端 1 页 | **`sql` 列不存 SQL**：禅道把条件序列化成 SQL 片段直接拼 WHERE，本实现存**结构化条件 JSON**（`field/op/value`），由各模块的强类型查询 VO 翻译；`zt_userquery` **没有 `deleted` 列**（物理删除），DO 不能继承 BaseDO。**全文检索 `zt_searchindex` 有意不做** | 接口 **23** / 界面 **18** |
| `dimension`（维度） | `zt_dimension`、5 个端点（只读 + 切换 + 可见性自检）、前端 1 页 | ① 可见性 = `acl='open' OR createdBy=我 OR FIND_IN_SET(我, whitelist)`，超管短路（坑位 #24 的延续）；② **末次维度四级兜底链**（配置→会话→可见性校验→取第一条），且「可见集合为空时不改写 id」这个守卫照抄；③ 下拉的两处参数例外（`pivot+design→browse`、`bi+tree+browsegroup→加 groupID=0`）。**开源版没有维度 CRUD**，所以只做只读 + 切换（连写接口都没有，测试里钉住了） | 接口 **37** / 界面 **20** |
| `api`（接口文档库） | 5 张表、12 个端点、前端 1 页（接口/结构/发布快照） | ① **接口库不在自己的表里**，是 `zt_doclib` 的 `type='api'` 记录；② 接口 **有变更才 version+1**、结构 **编辑无条件 +1**（两条版本链规则不同）；③ 发布 = **快照 JSON**（modules+apis+structs 一起 encode）；④ 唯一性**不过滤已删除**、`editedDate` 乐观锁；⑤ 结构版本表**按 name 关联**（禅道真实缺陷，照抄并标注）。**OpenAPI/Swagger 导入导出是付费扩展**（`editionLimited` + 扩展不存在），明确不做 | 接口 **72** / 界面 **44** |
| `webhook` | `zt_webhook`（日志复用 `zt_log`）、14 个端点、前端 1 页 | ① 对象类型/动作**白名单**；② payload 字段映射 + `text` **现拼**（`create` 无条件追加、`update` 只在缺时补 —— 这个不对称照抄）；③ `products` 是**交集**、空串 = 不限；④ 钉钉/飞书加签；⑤ **发送失败只落日志、接口仍返回成功、业务不回滚**；⑥ 群机器人强制 `application/json`。附一个 `@PermitAll` 的 **mock 接收端**做端到端断言（它是三方系统的替身，所以回**裸文本**而不是 yudao 信封） | 接口 **62** / 界面 **28** |

**顺带记一条新坑位（#54）**：`ServiceExceptionUtil.exception(code, args...)` 的第二参起是**给错误码 message 里的 `{}` 占位符用的参数**，不是「附加说明」。
给一个没有占位符的错误码多传一个字符串，那段文案会被**静默丢掉** —— `api` 模块第一版就这么写的，
结果 8 个断言拿到的都是框架的通用「请求参数不正确」。修法是把「有精确文案」的场景补成**独立错误码 + 占位符**。

**并行取证的第二次价值**：24 个模块的定性（见 `docs/REMAINING-MODULE-VERDICTS.md`）里，
子代理把清单自身的两处错误也翻了出来 —— `api` 的五张表其实都在开源版（我先前猜的 `zt_apilib`/`zt_apirelease` 根本不存在），
`zt_deliverable` 也在（它没写 `IF NOT EXISTS`，被当初的 grep 漏掉）。两条都已改正。

### 3.45 积分（`score`）：规则驱动的计分器，和它里面一条「死规则」

`module/score` 很小（`control.php` 42 行 + `model.php` 374 行，只有 `ajax` / `rule` 两个 action），
但它是**被别的模块调**的那一类：任务完成、缺陷解决、执行关闭、登录、保存搜索条件……
全项目有二十多处 `loadModel('score')->create(...)`。真正的内容在 `config.php` 的两张表上：

**① 规则表：38 条 `(模块, 动作, 次数上限, 时间窗, 分值)`**

```
user.login      = 3 次 / 24 小时 / 1 分        ← 一天最多 3 分
tutorial.finish = 1 次 / 100 分                ← 新手教程
task.finish     = 不限 / 1 分 + 优先级加成 + round(预计 / 10)
bug.resolve     = 1 分 + 严重程度加成(s1 +3 / s2 +2 / s3 +1)
execution.close = PM 20 分 / 成员各 5 分（按期或提前再 +10 / +5）
…共 38 条
```

**② 扩展加成**（`ruleExtended`）：严重程度、任务优先级、密码强度、执行关闭的（经理/成员 × 关闭/按期）四种成对加成。
本实现把它们连同规则一起放进 `ScoreRules.java` —— 禅道里这些也是**配置**不是数据（管理员只能开关积分功能，
不能改分数），所以端口化最忠实，也避免多一张没人维护的表。

**③ 三条照抄的「业务特例」**（都在 `create()` 的 switch 里，不看代码根本猜不到）

| 特例 | 禅道实际行为 |
|---|---|
| 缺陷「确认」 | 分给 **提单人**（`bug.openedBy`），不是确认的人 —— 鼓励提有效缺陷；按严重程度加成 |
| 任务「完成」 | `1 + 优先级加成 + round(预计工时 / 10)`；另外「有子任务的任务不给分」 |
| 执行「完成」 | **PM 与每个执行成员都计分**：PM 20 / 成员 5，`end > 今天`（按期或提前）各再加 10 / 5；PM 不重复算成员那一份 |

**④ 一条真正的「死规则」：`story.close` 只给创建者 2 分，关闭人拿不到那 1 分**

`config.php` 里明明写着 `story.close = 1 分`，但实现里是这样：

```php
case 'story':
    if($method == 'close') {
        $openedBy = ...fetch('openedBy');
        $object   = true;                                  // ← 先置 true
        if(!empty($openedBy)) {
            $newRule['score'] = $extended['createID'];      // 2 分
            $object = $this->saveScore($openedBy, $newRule, ...);
        }
    }
    break;
...
return $object === '' ? $this->saveScore($user, $rule, ...) : $object;   // ← 这里直接返回，关闭人那 1 分永远不落库
```

也就是说：**需求关闭时，只有创建者拿 2 分，关闭人 0 分**，配置里的那 1 分是死规则。
这是个「迁移时最容易顺手修好、然后和禅道对不上」的地方 —— 本实现照抄这个行为，
并在 `test-score-module.sh` 里用断言把它钉住（只有 1 条流水、拿分的是创建者、关闭人 0 条）。

**⑤ 两处有意偏离（都写进 README）**

- **总分不冗余在用户表上**：禅道 `saveScore` 会顺手 `UPDATE zt_user SET score = score + N, scoreLevel = scoreLevel + N`。
  本项目 `zt_user` 是映射交付（不迁表），所以总分 = `SUM(zt_score.score)`，`before`/`after` 在插入时按当时总分算快照落库；
  等级（`scoreLevel`）不迁。
- **开关换了位置**：禅道的 `system.common.global.scoreStatus` 存在设置表里（`zt_setting` 不在开源版），
  本项目用配置项 `zentao.score.enabled`（默认开）。

**⑥ 顺手把上一轮记下的偏离补掉了**

`entry`（应用接入）那一轮写明「禅道校验通过后会计一次登录分，本实现没接」。这一轮 `score` 进来之后，
`EntryService.verify` 校验通过就会 `createQuietly("user","login", …)` —— 计分失败不影响校验（禅道也是这个态度）。
`test-score-module.sh` 第 9 节专门断言了这条跨模块链路：走一次 `entry/verify` → `admin` 多一条 `user.login` 流水。

**规模**：1 张表（`zt_score`，无 `deleted` 列、`desc`/`before` 都是保留字，57 → **58** 张）、
4 个端点（规则 / 明细 / 总览 / 计分）、1 个前端页面（3 个 Tab：记录、规则、计分试算）。
回归：`test-score-module.sh` **40 项断言**（规则表 / 次数与时间窗 / 四条特例 / 总分与昨日 / 与 entry 联调）
与 `ui-check/score.mjs` **14 项界面检查**（概览、38 条规则抽查、计分试算含未知规则报错）。

### 3.44 公司信息（`company`）：禅道叫「组织视图」，本实现只落表 + 两条口径

`module/company` 在禅道里不只是「公司信息」那一页 —— `$lang->company->common` 写的就是**组织视图**，
它有三个入口：**公司信息**（view/edit）、**组织成员**（browse，按部门/内部外部看 `zt_user`）、
**组织动态**（dynamic，全公司的 action feed）。规模：`control.php` 211 行 + `model.php` 132 行 +
`zen.php` 177 行 + 配置 288 行，**7 个 action**。

**① 只做表 + 两条禅道口径，另外两个入口复用已有模块**

```
公司信息 view/edit   →  本模块（zt_company 一张表）
组织成员 browse      →  organization 模块（/zentao/organization/user-list + /dept-tree）
组织动态 dynamic     →  action 模块（/zentao/action/dynamic）
index（跳 browse）   →  前端菜单直接指向本页
ajaxGetOutsideCompany →  /zentao/company/outside-list
```

这和 `my`（我的地盘）是同一个原则：**同一份过滤逻辑只允许有一处实现**。
组织成员的「部门树 + 用户 + 权限」在组织权限那一轮已经做过，这里再写一遍必然两边跑偏。

**② `admins` 是「谁是超管」的口径 —— 这个模块最值钱的一条**

`zt_company.admins` 是个**逗号串**（安装时写的是 `,admin,`），禅道判超管就一句：

```php
$user->admin = strpos($this->app->company->admins, ",{$user->account},") !== false;
```

而 yudao 侧超管是 `super_admin` **角色**，而且 `PermissionServiceImpl` 对它**硬编码放行、不查权限表**
（第 24 轮踩过的坑）。这两套口径不会自动对上，所以本实现给了个对照接口
`GET /zentao/company/admins`，把「禅道侧有、yudao 侧没有」和「yudao 侧有、禅道侧没有」都列出来。
**在 183 的实库上跑出来的真实差异**：禅道侧 1 个（`admin`），yudao 侧 8 个（演示库里好几个账号都带了
`super_admin` 角色），`matched = [admin]`，`onlyInYudao` 7 个 —— 这正是迁移时必须人工拍板的东西，
接口只负责把差异摆出来（测试断言的是「差异集合自洽」，不是「差异为 0」—— 后者取决于你的数据）。

**③ 「外部公司」= `id != 1`，服务的是外部干系人**

`getOutsideCompanies()` 的实现就是 `where('id')->ne(1)`：**id=1 是本公司，其余都是外部公司**。
它服务的是「外部干系人的所属公司」这一条链路 —— 禅道加外部人员时会往 `zt_user` 里建一条
`type=outside` 的记录，`zt_user.company` 指向这里，**公司不存在还能顺手新建一条**
（`stakeholder/model.php` 里的 insert-on-the-fly）。所以「新建公司」在禅道里不是一个独立入口，
而是干系人流程里的一步 —— 本实现把两个零件备齐了（`/outside-list` 下拉 + `/create`），
并在测试里验了 `text/value/keys` 三件套就是禅道 `ajaxGetOutsideCompany` 的原样结构。

> ⚠️ **遗留项（明确记下来，不假装做完）**：本项目的干系人模块把「外部人员」简化成了**存名字**
> （没有建 `zt_user` 行），所以干系人表单里**还没有接**这个公司下拉。要补齐得先决定
> 「外部人员到底建不建用户行」—— 这一条记在「已知限制」里。

**④ 三处照抄的细节，和三个「有意不做」**

| 照抄的细节 | 为什么不是废话 |
|---|---|
| `website`/`backyard` **正好等于 `http://` 就清空** | 表单预填 `http://`，不清就会在公司信息里永远留一个没有域名的 http://（`setIF($post->website == 'http://', 'website', '')`） |
| `name` 必填 + unique，**且不过滤已删除** | 与 `entry.code` 同一个坑（坑位 #52 那一段）：禅道 `batchCheck('name','unique',"id != x")` 没有 `deleted` 条件 |
| `getFirst()` = id 最小的一条 | 单公司部署时它就是「本公司」；多公司（付费版）也按这个约定取 |

| 有意不做 | 理由 |
|---|---|
| **没有 delete** | 禅道 `module/company` 根本没有删除 action（多公司是付费版能力，开源版里 `id != 1` 的公司由干系人流程产生）；本实现也不提供，免得误删被引用的公司。测试里专门验了 `DELETE /zentao/company/delete` 返回「请求地址不存在」 |
| `guest`（匿名登录）只存不生效 | yudao 的认证统一走 OAuth2，没有匿名登录这个概念；字段照存（不丢数据），页面上标注「yudao 侧不生效」 |
| 组织动态没有「上周 / 上月」 | `action` 模块的 period 目前支持 全部/今天/昨天/本周/本月；禅道的 featureBar 多了上周/上月。缺口写在页面上和 README 的「已知限制」里，而不是假装有 |

**⑤ 本轮的真错，和一个新坑位（#53）**

写好的页面第一次跑浏览器检查时报了一条 `pageerror`，Playwright 只说 `Array(1): Object`，
在页面里挂 `window.addEventListener('error'/'unhandledrejection')` 才拿到真身：

```json
{"name":[{"message":"公司名称不能为空","fieldValue":"","field":"name"}]}
```

这是 **Element Plus `validate()` 校验失败时 reject 出来的字段错误对象** ——
`await formRef.value.validate()` 不 catch，用户「点确定但必填没填」就会冒一条**未捕获的
unhandledrejection**。页面看起来完全正常（表单照样飘红），只有浏览器检查会判成 JS 报错。
修法是四行：

```ts
try { await formRef.value.validate() } catch { return }
```

顺手把 `entry`（应用接入）页里同样的写法一起修了 —— 这是**同一个模式抄第二遍就会再踩一次**的典型，
所以它进了坑位表（#53）。

**⑥ 顺带把「本机只当浏览器」的最后两块也搬走了**

- **编译搬到服务器**：`/data/yudao/build`（Maven 3.9.16 + 阿里云镜像 + JDK 25），源码同步过去后
  `bash build.sh --deploy` 直接编译、替换 `/data/yudao/server/yudao-server.jar` 并重启服务。
  本机不再跑 `mvn`（峰值 1~2G 内存），编译全程在服务器上（首次拉依赖 599MB，之后约 2.5 分钟一次）。
- **查库断言从 ssh 改成直连**：原先 `_mysql.sh` 的 remote 分支每断言一次就 `ssh + docker exec`，
  而本机到 `192.168.0.0/24` **建一次 ssh 要 16 秒**（实测），一个脚本几十个断言就是十几分钟；
  现在新增 `MYSQL_TARGET=direct`（本机 mysql 客户端直连服务器 3307，应用账号 `yudao`），
  **0.7 秒/次，快 30 倍**，37 个脚本的全量回归从「几小时」变成「几十分钟」。

**规模**：1 张表（`zt_company`，56 → **57** 张）、8 个端点、1 个前端页面（5 个 Tab：公司信息/外部公司/
超管口径对照/组织成员/组织动态），菜单权限 4 行（`50-zt_company.sql`）。
回归：`test-company-module.sh` **28 项断言**（本公司/唯一性/http:// 归一/admins 不可改/口径对照自洽/
没有删除接口）与 `ui-check/company.mjs` **30 项界面检查**（含编辑弹窗与必填校验、外部公司三件套、
口径对照四个集合、复用组织成员与组织动态两个 Tab）。

### 3.43 应用接入（`entry`）：模块只有 337 行，规则全在 `common` 里

禅道 `module/entry` 本体很小：`control.php` 149 行 + `model.php` 188 行 + 配置 116 行，**5 个 action**
（`browse/create/edit/delete/log`），没有 `zen.php`、没有 `tao.php`。
但它管的是禅道**唯一一条第三方免登录通道**：OA / 门户 / 移动端拿着 `code + token` 就能调禅道的接口，
勾了「免密登录」还能直接以某个账号的身份跳进来。

**迁移时最容易漏的一点**：真正的规则**不在 `module/entry` 里**，而在 `module/common/model.php` 的三段
公共方法里 —— `checkEntry()`（校验链，约 38 行）、`checkEntryToken()`（两种签名，约 37 行）、
`checkIP()`（白名单六种形态，约 66 行）。只照 `module/entry` 抄一遍，会得到一个**能增删改查、
但谁都校验不过或谁都能过**的空壳。这三段合起来 140 行，才是这个模块的全部价值。

**① 校验链的顺序与错误码**（`config.php` 的 `errcode` 原样搬，提示文案照抄 `lang/zh-cn.php` 的 `errmsg`）：

| 顺序 | 条件 | 错误码语义 | 本实现 |
|---|---|---|---|
| 1 | 缺 `code` / 缺 `token` | 401 | `1_020_030_003/004` 缺少code参数 / 缺少token参数 |
| 2 | 按 `code` 查不到应用 | **404** EMPTY_ENTRY | `1_020_030_000` 应用不存在 |
| 3 | 应用没配 `key` | 401 EMPTY_KEY | `1_020_030_005` 应用未设置密钥 |
| 4 | 来源 IP 不在白名单 | **403** IP_DENIED | `1_020_030_006` 该IP被限制访问：{ip} |
| 5 | token 不对 | 401 INVALID_TOKEN | `1_020_030_007` 无效的token参数 |
| 6 | 非免密且没绑账号 | **403** ACCOUNT_UNBOUND | `1_020_030_008` 未绑定用户 |
| 7 | 账号在用户表里不存在 | **406** INVALID_ACCOUNT | `1_020_030_009` 用户不存在：{account} |
| 8 | 时间戳不大于上次调用时间 | **405** CALLED_TIME | `1_020_030_010` Token已失效：时间戳不大于上次调用时间，可能是重放请求 |
| 9 | 时间戳格式不对 | **407** ERROR_TIMESTAMP | `1_020_030_011` 错误的时间戳 |

顺序很重要：**IP 在签名之前、账号在签名之后**。所以「IP 被拒」时不该写调用日志、
「签名错」时也不该去查用户表 —— 测试里专门断言了「失败的校验不写日志」（日志条数不变）。

**② 两种签名，和一个照抄的怪癖**

```
带时间戳：token = md5(code + key + time)，且必须 time > calledTime（防重放），通过后回写 calledTime
不带    ：token = md5(md5(查询串去掉 token) + key)
```

- 第一种失败时**不返回**，会继续用第二种再算一次（此时查询串里仍含 `time`）—— 禅道的 fallthrough 行为，
  测试里专门造了「给个错 time 但 query 签名正确 → 仍然通过」这一条。
- **怪癖**：时间戳只校验「截断到 10 位、且首位 < `'4'`」，但**摘要用的是原始字符串**。
  所以 13 位毫秒时间戳完全合法（对接方按原样拼接即可），而 `4700000000`（百年之后）会被 407 拒掉。
  这个不对称是禅道原样（`task #5384` 加的），本实现照抄并写了注释 —— 这种地方「顺手改成对称」，
  对接方的签名就会全部对不上。
- 防重放是**每个应用一个 `calledTime`**（不是按 IP、不是按 token），并且**只在成功的时间戳模式下更新**。

**③ IP 白名单的六种形态，和「留空 = 不限制」**

`*` / 精确 IP / 逗号列表（递归） / `a-b` 区间（`ip2long` 比较） / `192.168.1.*` 通配（按点的个数补 `0`/`255`，
也支持 `192.*`、`192.168.*`） / CIDR（`192.168.0.0/24`，禅道注释里还留着 "Thanks to zcat"）。
六种形态在测试里全部走接口验了一遍（正面命中 + 反面 403）。
**一个照抄的默认值**：白名单留空时用的是全局 `$config->ipWhiteList`，而它默认是 `'*'` ——
所以**「IP 栏留空」等于「不限制」**，不是「谁都不许」。`'*'` 还是最前面短路的：
配了「无限制」的应用不会去校验来源 IP 的合法性（这一点也照抄了，测试里两条都断言）。
除此之外本实现比禅道严一点：**不命中 `'*'` 时，非法 IPv4 直接拒绝**（禅道 `ip2long` 失败会退化成 0，
有「非法 IP 落在 a-b 区间内被放行」的风险）。

**④ 一处有意偏离：只校验，不建立登录会话**

禅道校验通过后会 `session->set('user', $user)` 直接把登录态建起来，免密模式下再 `header("Location: webRoot")`
跳走。yudao 的认证由 OAuth2（`Admin-Token` + Redis）统一负责，**业务模块不应该自己写会话** ——
所以本实现的 `verify` 做三件事：**校验 + 记账（写 `zt_log`、回写 `calledTime`）+ 返回账号信息**
（`userId` / `userNickname`），需要真正登录时由调用方走 yudao 的登录接口。
这是「协议网关」与「业务模块」的边界，也说明**禅道的「免密登录」在 Java 侧不是逐行翻译，
而是换成等价的认证流程**。另外补了一个管理端助手 `/zentao/entry/sign`（需要 `zentao:entry:query` 权限），
让管理员/对接方能自己算出 token 联调，不用手搓 md5。

**⑤ 本轮的真错：`@PermitAll` 路径上不能直接用 `update(entity, wrapper)`**（坑位 #52）

`verify` 是免登录接口，走完签名校验后要回写 `calledTime`。第一版这么写：

```java
EntryDO update = new EntryDO();
update.setCalledTime(time);
return update(update, new LambdaQueryWrapperX<EntryDO>().eq(EntryDO::getCode, code));  // ✗
```

结果**每一次成功的校验都 500「系统异常」**：`Column 'updater' cannot be null`。
原因是 MyBatis-Plus 的填充列 `updater` **即使拿不到登录用户也依然会进 SET 子句**
（`DefaultDBFieldHandler.updateFill` 只在「有登录用户」时才赋值，但字段本身已被注册为填充字段），
于是写了个 NULL 出去，撞上 `updater varchar(64) NOT NULL`。改成裸 SQL 后一切正常：

```java
@Update("UPDATE zt_entry SET calledTime = #{time} WHERE code = #{code}")
int updateCalledTime(@Param("code") String code, @Param("time") Integer time);
```

这既是禅道的原意（`entry::updateCalledTime` 就只更新这一列），也顺手说明了那条通用教训：
**`@PermitAll`、定时任务、MQ 消费者这类「没有登录态」的写路径，审计列要显式处理或绕开。**

**⑥ 一个细节：`unique` 校验不过滤已删除记录**

禅道 `check('code','unique')` 生成的 SQL 是 `SELECT COUNT(1) FROM zt_entry WHERE code = 'x'`，
**没有 `deleted` 条件**（见 `lib/base/dao/dao.class.php` 的 unique 分支）。用 MyBatis-Plus 的普通查询会自动
带上 `deleted = 0`，语义就悄悄变了 —— 所以这里用了一条自定义 SQL 绕开逻辑删除。
有意思的是禅道自己的报错文案就写着答案：
「『代号』已经有『oa』这条记录了。**如果您确定该记录已删除，请到后台-系统设置-回收站还原。**」
本实现把这条文案也一起搬了过来。

**规模**：2 张表（`zt_entry` + 通用日志表 `zt_log`，54 → **56** 张）、10 个端点、1 个前端页面
（列表 + 新增/编辑弹窗 + 调用日志抽屉 + **接入自测卡片**：生成签名 → 发起校验 → 看 403/405 的真实提示），
菜单权限 5 行（`49-zt_entry.sql`）。回归：`test-entry-module.sh` **67 项断言**（校验链 9 种错误码 +
六种 IP 形态 + 两种签名 + 毫秒时间戳怪癖 + 日志只写成功）与 `ui-check/entry.mjs` **36 项界面检查**。

### 3.42 节假日：这个模块的价值不在「多一张表」，而在把工作日口径做对

`module/holiday` 只有 688 行、6 个 action，却管着全项目**唯一**的一件事：**什么叫工作日**。

**① 一张表两种记录**

| type | 含义 | 对工作日的影响 |
|---|---|---|
| `holiday` 假期 | 法定假期、公司放假 | 这段日期**不算**工作日（即使是周一到周五） |
| `working` 补班 | 调休上班 | 这段日期**算**工作日（即使是周六周日） |

判定优先级（禅道 `getActualWorkingDays`）：
**补班 > 假期 > 周末（`weekend=2` 指周六周日）> 其它算工作日**。

**② 真正的价值：把第 36 轮燃尽图的「简化口径」补对了**

做燃尽图那一轮，`type=noweekend` 我只实现了「跳过周末」（当时在 README 里就记着这个简化）。
这一轮把它接到 `HolidayService.getActualWorkingDays()`，于是两件事同时成立：

- **国庆假期那几天不再占燃尽图的横轴格子**（10-01 ~ 10-07 全被跳过）；
- **调休的周六要占格子**（把 09-26 设成补班后，横轴从 10 个点变 11 个点）。

测试就是照着这两条写的：建一个跨国庆的迭代（09-21 ~ 10-09，工作日 < 31 个点所以不触发采样），
断言横轴 10 个点 → 加补班 11 个点 → 再把一个普通工作日设成假期变回 10 个点。
**这类「口径补全」比新做一个页面有价值得多**：它让已经交付的功能从「近似对」变成「真的对」。

**③ 一个照抄的怪癖**：禅道 `getActualWorkingDays($begin, $end)` 是**左闭右开**的
（循环条件 `$currentDay < $end`），只有 `$begin == $end` 时才返回那一天。
测试里把这四种边界都断言了（单日假期 → 0、单日工作日 → 1、单日周一 → 1、单日周六 → 0）。
本实现保持一致 —— 否则燃尽图的点数会和禅道对不上。

**④ 顺带做了两件事**

- 页面上的「**工作日试算**」小工具：选一个区间就能看到实际工作日是哪几天（排查排期问题很好用）；
- `year` 由 `begin` 自动推出（禅道也这么存：按年筛选直接用这一列，不用函数算）。

**规模**：1 张表（`zt_holiday`，53 → **54** 张）、6 个端点、1 个前端页面，菜单权限 5 行（`48-zt_holiday.sql`）。
回归：`test-holiday-module.sh` 31 项断言 + `ui-check/holiday.mjs` 16 项界面检查。

**⑤ 这一轮还顺手做了一次「可迁移性审计」**：为了给剩下的模块定优先级，写了个脚本逐个模块检查
「它引用的 `TABLE_*` 常量对应的表，是否真的被开源建库脚本创建」+「代码里有多少处
`$this->config->edition != 'open'` 判断」。结论写进了清单：像 `space`/`weekly`/`personnel`/`artifact`/
`mail`/`message`/`pipeline`/`ppm` 这些模块引用的表**在开源版里根本不存在**（属付费版），
`zt_deliverable`（IPD 交付物）被 9 个模块引用却同样缺表 —— 这些不是「没做」，而是**没有实现可搬**。
清单里按证据逐条标注，避免把「做不了」记成「还没做」。

### 3.41 代码库（`repo`）：把「提交」和「需求 / 任务 / 缺陷」接起来

这是最后一个 P1 模块，也是禅道里**唯一一个「数据来自系统外部」**的模块 ——
其余 50 张表的数据都是人在禅道里填的，只有 `zt_repohistory` / `zt_repofiles` 是从 **git 仓库**里读出来的。

**① 做的是哪一段**：禅道 `module/repo` 有 16,016 行、71 个 action，来源可以是本地 Git、SVN、
GitLab / Gitea / Gitea 等服务商（后者走 `module/provider` + `module/gitlab` 一整套「代码服务商」体系）。
本实现**只做「本地 Git 仓库」这一条链路**，因为它才是这个模块真正的价值所在：

```
配一个本地 git 仓库路径
  → 同步：git log（增量，从上次同步到的 sha 往后 200 条）
  → 提交写进 zt_repohistory（revision=sha、commit=自增序号、committer、time、comment）
  → 本次改动的文件写进 zt_repofiles（action 取 git --name-status 的首字母：
      A 新增 / M 修改 / D 删除 / R 重命名[带 oldPath]）
  → 提交说明里的 Story #1 / Task #2,3 / Bug #4 解析成对象关联，写进通用的 zt_relation
      （AType='revision', relation='commit'）
```

于是两个方向都能查：**从提交看它做了什么**（详情里带关联对象与文件清单）、
**从需求/任务/缺陷看是哪几次提交做完的**（`commit-page?objectType=task&objectID=1`）。
接服务商 API 属于接入层，不影响这条主链路。

**② 三个必须做对的点**

| 点 | 为什么 |
|---|---|
| **同步必须幂等** | 同步可以反复点（禅道还有定时任务）。同一个 sha 只入库一次；重新同步某条提交时先 `delete` 它的文件清单与关系行再写 —— 不然点两次就会出现重复文件、`commit-page` 反查也会一条变四条 |
| **`commit` 是自增序号，不是 sha** | 禅道就是这么设计的（`revision` 才存 sha）。所以新提交要接着 `MAX(commit)` 往下编号，而且 `git log` 是「新 → 旧」，**入库前要倒过来**，序号才和时间同向 |
| **重命名要带 oldPath** | `git mv` 在 `--name-status` 里是 `R100\0old\0new`：迁移时如果只取第一个路径，用户会看到「删了一个文件又加了一个文件」，实际是改名 |

**③ 跑 `git log` 的两个工程细节**

- **格式串的「分隔符放最前面」**：`--pretty` 与 `--name-status -z` 拼在一起时，文件清单是
  **跟在记录分隔符之后**的（`<RS>sha<US>author<US>time<US>comment<US>\nM\0path\0…`）——
  按常规「字段在前、分隔符在后」的写法切，会把上一条提交的文件算到下一条头上。
  所以格式写成 `<RS>%H<US>%an<US>%aI<US>%B<US>`，末尾再补一个字段分隔符，一条记录一刀切干净。
- **安全**：用 `ProcessBuilder` 的**参数数组**（不拼 shell 字符串），且只跑 `log` / `rev-parse`
  这类只读命令 —— 仓库路径是管理员配的，但也没必要给一条能注入的路径。

**④ 本轮的三个坑**

- **`Unknown column 'create_time'`**：`RepoDO extends BaseDO`，但建表时只照着禅道的列写了
  `createdBy/createdDate/deleted`，漏了框架的 `create_time/update_time/creator/updater` ——
  本项目其余 50 张 `zt_*` 表都有这四列，新表要一起建。
- **索引长度**：`path varchar(1000)` 上建普通索引，utf8mb4 下 `1000 × 4 = 4000 > 3072` 字节，
  建表直接失败（`Specified key was too long`）。改成 `varchar(500)` + 前缀索引 `path(191)`。
- **时间字段是时间戳**：前端渲染提交时间时写了 `(row.time || '').replace('T',' ')`，
  但 yudao 的 Jackson 把 `LocalDateTime` 序列化成**数字时间戳** → `replace is not a function`
  → **整张表的行都渲染不出来**（表头在、`共 N 条` 在、tbody 是空的）。改用项目里的 `formatDate`。
  这类错误的表现特别有迷惑性：接口 200、数据也对，只是表格空 —— 排查时先看 `pageerror`。

**规模**：4 张表（`zt_repo` / `zt_repohistory` / `zt_repofiles` + 通用关系表 `zt_relation`，49 → **53** 张）、
9 个端点、1 个前端页面 + 提交抽屉 + 提交详情弹窗，菜单权限 6 行（`47-zt_repo.sql`）。
回归：`test-repo-module.sh` 37 项断言（**用一个真实的本地 git 仓库做夹具**，含增量同步与 `git mv`）
+ `ui-check/repo.mjs` 20 项界面检查。

**依赖**：后端宿主机要能执行 `git`（`build/backend` 的部署说明里已注明）。

### 3.40 测试仪表盘（`qa`）：模块只有 153 行，价值全在「口径」上

**先说这个模块到底有多大**：禅道 `module/qa` 一共 153 行、**1 个 action**，`index()` 里一行业务计算都没有：

```php
$this->qa->setMenu($productID, $branch);
echo $this->fetch('block', 'dashboard', 'dashboard=qa');   // 把看板拼出来就完事了
```

真正的「质量统计」在 `module/block`（16,224 行）里，而看板那块统计又去调 `module/metric`
的度量口径（`metric->getResultByCodeWithArray('count_of_daily_created_bug_in_product')`）。
也就是说：**`qa` 是「仪表盘页面」，不是「质量业务模块」**。

**所以本实现做的是等价覆盖**，但**不搬 block 的积木引擎、也不依赖 metric 框架** ——
直接按禅道的口径把那四块内容用 SQL 算出来。真正有价值的是口径，不是那套可以配置摆放位置的积木框架：

| 看板块 | 口径（出处：`module/bi/config/metrics.php`） |
|---|---|
| 质量统计（按产品） | `count_of_daily_created/resolved/closed_bug_in_product`（区间内新增/解决/关闭）、`count_of_effective_bug_in_product`、`count_of_fixed_bug_in_product`、`rate_of_fixed_bug_in_product` |
| 待处理缺陷 | `status = 'active'`，按严重程度 + 优先级排序 |
| 待评审用例 | `status = 'wait'`（只算产品用例，库用例不算） |
| 未完成测试单 | `status in ('wait','doing')` |

**最容易写错的一条是「有效缺陷」**：禅道的定义是
**「解决方案为已修复 / 延期处理 / 不予解决，或者状态为激活」** —— 它是**修复率的分母**。
所以：

- 一个还挂着的激活缺陷，**算有效**（分母里要算它，否则「还没修」会被当成「修得好」）；
- 一个被判为「重复 / 设计如此 / 无法重现」的缺陷，**不算有效**（分母里踢掉，否则修复率被稀释）。

测试脚本就是照着这两个反直觉点写的：两个激活缺陷 → 有效 2、修复率 0；把一个解决成 `fixed`、
另一个解决成 `bydesign` → 有效 1、已修复 1、修复率 100%（而不是 50%）。

**本轮踩的坑**：`summary` 一开始是把**全部产品**的行无条件累加的，于是「按产品过滤」时
卡片显示的还是全公司的数（产品行只显示一个、卡片却是全部）—— 接口不报错、数字看着也「有值」，
是最难发现的一类。修法是汇总时跟着 `product` 过滤走（产品列表仍然返回全量，因为页面要横向对比）。

**顺带说清 `feedback` 为什么不迁移**：`module/feedback` 在开源仓库里只有 44 行存根 ——
`getFeedbackPairs()` 直接 `return array('admin' => 'Admin', 'user1' => 'User1')`，
而 bug/story/todo 里所有真正的反馈逻辑都带 `$this->config->edition != 'open'` 判断，
即**真实实现属于付费版，不在开源仓库里**。没有实现可搬，就不硬造一个「看起来像」的模块：
清单里 `feedback` 记的是「不迁移（开源版仅存根）」，而不是假完成。

**规模**：5 个口径查询 + 1 个端点（`GET /zentao/qa/dashboard`），**不新增表**，
菜单 2 行（`46-zt_qa_menu.sql`）。回归：`test-qa-module.sh` 25 项断言 + `ui-check/qa.mjs` 20 项界面检查。

### 3.39 执行燃尽图：曲线是「每天的快照」连起来的，不是实时算的

这一轮补的是 `execution` 缺口里的第一条（3.7 起一直挂着的「燃尽图」）。
它看着只是「画一条线」，真正的设计点其实有两个：**为什么要落表**，以及**三条线各自的含义**。

**① 为什么必须落表（`zt_burn`）**：燃尽图不是打开页面时现算的，而是**每天把当天的工作量汇总成一行快照**：

```
computeBurn（可手动触发；禅道另有定时任务）
  → 汇总该执行下所有任务（不含已删除、不含已取消）
  → left       = Σ任务 left       − Σ「已关闭任务」的 left          ← 关闭的任务不该再占剩余
  → estimate   = Σ任务 estimate   − Σ「已完成/已关闭任务」的 estimate ← 做完的从原计划里扣掉
  → consumed   = Σ任务 consumed
  → storyPoint = 本执行关联的「未关闭且阶段未完成」的非父需求的 estimate 合计
  → REPLACE INTO zt_burn（唯一键 execution + date + task，当天那一行覆盖）
```

现算只能得到**今天一个点**，画不出趋势；而任务会被改、会被删、完成后 `estimate` 会被改写 ——
只有把每天的值落库，历史曲线才留得住。这也是为什么 `zt_burn` **没有 `deleted` 列**：
快照是只增不改的事实数据（与 `zt_team` 一样不继承 `BaseDO`）。

**② 三条线**（禅道 `buildBurnData`）：

| 线 | 含义 | 怎么来 |
|---|---|---|
| `burnLine` 实际 | 每天真正剩多少 | 读 `zt_burn`；**缺失的日期用前一个有值的日期补上**（禅道 `createSingleJSON` 的 preValue），第一个快照之前的日期是 0 |
| `baseLine` 理想 | 从第一天的工作量线性降到计划结束日为 0 | 首值 ÷（结束日在横轴上的下标），逐日递减 |
| `delayLine` 延期 | 计划结束日之后的那一段实际值 | 只有「已经过了计划结束日还没结束」才给；与 `burnLine` 在计划结束日那天**交汇**（那天的值两条线都有） |

页面上的四个开关：`burnBy`（剩余 / 原计划 / 已消耗 / 需求规模）、`type`（跳过周末 / 含周末）、
`interval`（采样间隔），外加一个「重新计算」。

**③ 采样与周末**：横轴最多 31 个点（禅道 `maxBurnDay`），日期多了就按 `interval = 工作日数 / 31` 采样，
**但计划结束日永远保留** —— 否则理想线的 0 点会丢。跳过周末用的是 `date::getDateList`
（`config->execution->weekend = 2`，即周六周日都跳过）。

**④ 与禅道的三处偏离**（都写在代码注释里）：① 禅道在「今天之后」的日期上会直接 `break`，
返回的 `burnLine` 比 `labels` 短；本实现改用 `null` 对齐，前端不用自己补空位、ECharts 画出来一样是断线；
② 禅道汇总任务时排除父任务（`isParent = 0`），本实现的 `zt_task` **没有 isParent 列**（父子任务还没做），这条条件省掉；
③ 状态枚举里没有 `done`（我们用 `closed` 表达结束），语义一致、取值不同。

**⑤ 本轮踩的两个坑（都值得记）**

- **`left` + 驼峰列再加一次**：`BurnDO` 第一版忘了给 `left` 加反引号、忘了给 `storyPoint` 加 `@TableField`，
  生成的 SQL 直接是 `SELECT ... estimate, left, consumed, story_point FROM zt_burn`，MySQL 报语法错。
  这正是坑位 #10（`left` 是保留字）与 #1（驼峰列）的又一次 —— 而 `deploy/check-camel-columns.sh`
  本来就能提前查出来，**所以「新加表之后先跑那两个预检脚本」这条纪律要放在写 DO 之前，不是之后**。
- **顺序写反 = 功能静默失效**：`dateList()` 里原本先「按 step 生成日期列表」、再在外面算 `step`，
  于是自动采样永远走不到（`step = 0` 时直接返回全部日期）—— **接口能通、断言却对不上**：
  120 天的区间本该压到 31 个点，实际返回 121 个。修法是把间隔提到生成列表之前算。这条也进了坑位表。

**规模**：新增 1 张表（`zt_burn`，48 → **49** 张）、2 个端点
（`GET /zentao/execution/burn-data`、`POST /zentao/execution/compute-burn`）、
1 个前端抽屉组件（`components/BurnChart.vue`，ECharts 三条线）。
回归：`test-burn-module.sh` 32 项断言 + `ui-check/burn.mjs` 12 项界面检查。

### 3.38 报表：年度数据 / 每日提醒 / 产出统计 —— 顺带纠正两个主干链路口径

先说清这个模块现在是什么。禅道 v20 的 `module/report`（3,048 行）**只剩两件事**：
**年度数据**（公司/部门/个人三种视角的年度总结）和**每日提醒**（把快到期的任务/缺陷/待办/测试单
发邮件催人）。老版本那个「自定义报表」`zt_report` 表（`code/dimension/sql/vars/step`）已经被 BI 取代 ——
现在只有 `tree` 和 `upgrade` 在读它，所以本实现**不做它**：要自定义报表请用已经迁移的 `bi`（SQL 模式）。

**① 年度数据的口径**

| 指标 | 来源 | 说明 |
|---|---|---|
| 登录次数 | `system_login_log` | 禅道每次登录往 `zt_action` 写一条 `login` 动作；本实现不写，所以直接读框架的登录日志（真实数据，还带成功/失败） |
| 动作数 | `zt_action` | 本条就是「这一年有多少操作」 |
| 待办 / 工时 | `zt_todo` / `zt_effort` | 条数 + 未完成 + 消耗合计 |
| 贡献 | `zt_action` 按 `objectType + action` | 映射表与禅道 `config/report.php` 的 `contributions` 一一对应 |
| 完成任务 / 解决缺陷 | `zt_task.finishedDate` / `zt_bug.resolvedDate` | 见下面第 ③ 点 |
| 产品 / 执行产出 | `zt_product` / `zt_projectstory` / `zt_bug` | 本年创建的、我参与的、以及统计里出现过的对象 |
| 状态分布 + 月度趋势 | `zt_action` + 业务表 | 状态分布只数**未删除**的对象；月度趋势数全部动作 |

**雷达图的「自洽」要小心**：同一条贡献可以同时归到两条产线（`task.create` 既算「执行」也算「研发」，
禅道 `radar` 配置就是这么写的），所以雷达五类之和 **≥** 贡献明细之和，差额正好等于多标签任务动作数。
这不是 bug，测试脚本里就是这么断言的。

**② 三种视角**：不传参数 = 全公司（不加 `actor` 过滤）、传 `dept` = 部门 **含子部门**、传 `account` = 个人。
部门子树是在 `organization` 的部门树上现算的（yudao 用邻接表，禅道用 `path/grade`，这里在内存里展开）。
「年初默认看去年」（禅道 `minMonth = 2`：1~2 月当年的数据太少）也照搬了。

**③ 两处有意偏离，都写在代码注释里**

- **登录次数**读 yudao 的 `system_login_log`，不读 `zt_action`（理由见上表）。
- **「完成任务 / 解决缺陷」按事实列数，不按动作名数**：禅道有独立的 `finished` / `resolved` 动作可直接数，
  本实现里这两个操作记的是 `changed`（一次提交同时改了状态和工时，用 CHANGED 更能表达），
  动作名分不出来 —— 所以数 `finishedDate/finishedBy`、`resolvedDate/resolvedBy`，
  这跟禅道「年度执行里完成任务数」那条查询是同一个口径。
- **每日提醒不发邮件**，只产出「谁、有哪些事」的数据；发信交给 yudao 的通知能力。

**④ 顺带纠正两个主干链路口径（都是报表算不准才暴露出来的）**

1. **执行的动作对象类型应该是 `execution`，不是 `project`**。
   一开始图省事写成了 `project`（「反正都在 `zt_project` 里」），但禅道 demo 数据里
   执行的动作是 `('execution', 3, execution=3)`、项目的动作是 `('project', 2, project=2)` —— 两者必须分开。
   不分开的话：报表按 `objectType` 统计贡献/产出时项目与执行会混在一起，回收站里「执行」也会显示成项目。
   代码已改（`ExecutionServiceImpl`），历史数据用 `44-zt_report.sql` 一起搬过去（481 条）。
2. **执行行的 `multiple` 必须跟所属项目一致**。`multiple=1` 的语义是「多迭代项目下的迭代」，
   禅道建执行时沿用项目的 `multiple`，而本实现之前没有继承 —— 于是演示数据里
   「项目 `multiple=1`、它下面的执行 `multiple=0`」，报表的年度执行统计（`type='sprint' AND multiple=1`）
   一个都查不到。这一轮补上继承逻辑，并把演示数据对齐。

**⑤ 还有一个「看着像报表写错了」的坑**：报表是按 `zt_action` 统计的，而本项目的演示数据是
直接把对象 `REPLACE` 进业务表的、**没有配套的动作行** —— 于是年度数据里需求/任务全是 0。
禅道的 `db/demo.sql` 是给每个对象都配了动作的。所以 `44-zt_report.sql` 里按同样思路
给本年「还活着」的需求/任务/缺陷补了创建动作（日期取对象自己的创建时间，只补缺失的，重复执行安全）。

**⑥ 这一轮的命名三连撞（坑位 #25 第三次上演）**：禅道的「测试报告」模块在本项目里已经占了
`testreport.ReportController` / `ReportService` / `ReportServiceImpl`，而报表模块天然也想叫这些名字。
组件扫描按**短类名**生成 bean 名、`@Resource` **先按字段名**注入、MyBatis 的 mapper bean 名取**短类名首字母小写** ——
三个机制各撞一次，启动依次报：

```
ConflictingBeanDefinitionException: bean name 'reportController' ...
The bean 'reportServiceImpl' could not be injected because it is a JDK dynamic proxy ...
The bean 'reportMapper' could not be injected because it is a JDK dynamic proxy ...
```

最后全部加 `Zentao` 前缀/改成 `zentaoReportMapper` 这类字段名。**新模块开工前先全局搜一遍同名类，
并且连「注入字段名」一起搜** —— 这是本次踩坑之后最该记住的一条。

**规模**：5 个端点（`options` / `annual-data` / `reminder-list` / `output` / `project-status`），
**不新增表**（`zt_report` 有意不做），菜单 2 行（`44-zt_report.sql`）。
回归：`test-annual-report-module.sh` 39 项断言 + `ui-check/report.mjs` 16 项界面检查。

### 3.37 「我的地盘」补齐第二组：我参与的对象、我的团队、我的日历

3.26 里把 `my` 做成了「概览 + 我的待办/任务/缺陷/需求/工时/动态」，
这一轮把剩下的一半补上：**我参与的项目 / 执行、我的团队、我的测试单 / 用例 / 文档、我的日历**。
做法沿用了同一条原则 —— **my 是查询层，不重写过滤逻辑**，
所以每个页签都是「把『我是谁』塞进各模块已有的分页接口」，而不是在 my 里再实现一遍查询。

**① 判定口径（这才是这一轮真正要定下来的东西）**

| 页签 | 判定条件 | 为什么 |
|---|---|---|
| 我参与的项目 / 执行 | `PM 或 PO 或 QD 或 RD = 我` **或** `FIND_IN_SET(我, team)` | 禅道的参与关系有两处：四个负责人字段 + 团队成员。`team` 是逗号串（历史遗留），只能 `FIND_IN_SET` |
| 我的团队 | `zt_team WHERE account = 我`，一行 = 我在某个项目/执行里的成员关系 | 一行装的是「什么角色、从哪天开始、每天几小时」；**可用工时 = days × hours**，与 `team` 模块的 `getTotalHours` 用同一个公式 |
| 我的测试单 | `owner = 我` **或** `createdBy = 我` | 测试单没有成员表（禅道也没有），只有负责人和创建人两个「人」的字段 |
| 我的用例 | `openedBy = 我` **或** `FIND_IN_SET(我, reviewedBy)` | 注意是 **OR**：我建的算我的，我评审的也算我的 |
| 我的文档 | `addedBy 或 assignedTo 或 editedBy = 我` | 文档没有成员概念，只能用「谁创建/派给谁/谁最后改的」 |
| 我的日历 | 待办按 `date`、任务按 `estStarted`、测试单按 `begin` 落到某一天 | 三类数据各有一个「属于哪一天」的字段，归到一个月里就是日历 |

**② 三处实现细节，都是「不做就会错」的**

- **用例的 OR 要拆成两次查询。** `CaseMapper` 里 `openedBy` 与 `reviewedBy` 是两个独立的等值/`FIND_IN_SET` 条件，
  拼在一条 SQL 里是 **AND**。而要的是 OR，所以后端查两次、按 id 用 `LinkedHashMap` 合并去重
  （同一条用例既是我创建又是我评审时只出现一次）。这也是全项目唯一一处「用两次查询换一个 OR」的地方 ——
  值得，因为写成 `apply("(openedBy = {0} OR FIND_IN_SET({0}, reviewedBy))")` 会把两个语义完全不同的字段塞进一个匿名条件里，
  以后谁想单独改「我评审的」这条口径都找不到入口。
- **我的账号不从前端传。** `resolveAccount()` 优先用登录上下文里的用户名，前端只传分页和筛选条件。
  否则「我的地盘」就变成了「别人的地盘」—— 传个 `account` 参数就能看别人的待办。
- **`LambdaQueryWrapperX` 的链尾退化（坑 #13）在这里又遇到一次。** 项目/执行的 member 条件必须写成
  `wrapper.apply("(PM = {0} OR QD = {0} OR RD = {0} OR PO = {0} OR FIND_IN_SET({0}, team))", member)`，
  而 `apply` 不在 `LambdaQueryWrapperX` 的覆写清单里 —— 写在链尾会让整条表达式退化成基类型、赋不回变量。
  所以这几个 `selectPage` 一律先 `new LambdaQueryWrapperX<>()`，再一条一条 `wrapper.xxx(...)`。

**③ 顺手发现并记下来的一个坑：执行创建不收 `team` 字段**

`ExecutionSaveReqVO` 里有 `team`，但执行创建**不读它** —— 执行成员来自 `PO/PM/QD/RD`
（`teamService.syncOwners`），`zt_project.team` 那个逗号串也会被这次同步**覆盖**。
所以「把我加进某个执行的团队」必须走 `team/add-member`，加完之后 `syncTeamInfo` 会把
`team` 串和 `teamCount` 一起刷成成员表的真实内容。这两处必须一起动：
只改一处就会出现「团队人数现算、`team` 串还是旧值」的两种真相。
测试脚本第一版就是踩在这个上 —— 建执行时传 `team: "admin"`，结果成员是 PM，
「我参与的执行」自然查不到（这条也写进了脚本注释，免得下次再踩）。

**④ 日历的返回形状**：`[{date, count, items:[{type,id,name,status,extra}]}]`，
日期用 `TreeMap` 排好序，不在本月的直接丢弃。`extra` 放的是「这条数据在这一天为什么重要」——
待办放优先级、任务和测试单放截止日期。前端每天一张小表，不需要自己分组排序。

**规模**：新增 7 个端点（`/zentao/my/project-page`、`/execution-page`、`/team-list`、
`/testtask-page`、`/case-page`、`/doc-page`、`/calendar`），
`MyTeamRespVO` 补了对象名/状态/姓名/可用工时，**不新增表**（用的是当时既有的 48 张）。
回归：`test-my-workspace-module.sh` 45 项断言 + `ui-check/my-workspace.mjs` 21 项界面检查。

### 3.36 操作日志收口：回收站、动态、备注，以及「动作渲染」

前几轮已经把 `zt_action` / `zt_history` 写进了每个模块（谁、何时、对什么、做了什么、字段从什么变成什么），
但清单里 `action` 一直标着「⚠️ 部分：动作渲染未做」。这一轮把它补齐，做法照抄禅道的四个能力：

**① 回收站 = 一批「删除」动作，不是另一张表。** 删除对象时记的那条 `action='deleted'` 就是回收站条目：

```
回收站列表  zt_action WHERE action='deleted' AND extra <> 'beHidden'（回查对象名与删除状态）
还原        UPDATE <对象表> SET deleted = 0 WHERE id = ?   + 记一条 undeleted
隐藏        UPDATE zt_action SET extra='beHidden'          + 记一条 hidden（对象仍是删除状态）
全部隐藏    UPDATE zt_action SET extra='beHidden' WHERE action='deleted' AND extra <> 'beHidden'
```

对象类型 → 表/名称列的映射写在 `ActionObjectMap` 白名单里（禅道对应
`actionTao->getUndeleteParamsByObjectType`）——**没登记的类型会标成「不能还原」并给出原因，不猜、不误删**。
两处有意偏离：禅道用 `extra='canUndelete'` 标记「可还原的删除」，本实现把所有逻辑删除都当可还原
（`extra` 只用来记已隐藏）；禅道的隐藏会连对象一起记 `hidden` 动作，本实现同样记，但不动对象。

**② 动作渲染**（禅道 `renderAction` + `renderChanges`）：后端直接给出 `renderedDesc`，
如 `admin 编辑：状态 激活 → 已关闭`；没有字段变化时退化成 `admin 关闭`，
备注动作则带上内容 `admin 备注：先把这条需求挂起`。前端既可以直接显示这一行，
也可以用结构化返回的 `histories` 自己渲染（原有的 `ActionTimeline.vue` 就是这么做的，两边都保留）。

**③ 动态（feed）**：`getDynamic` 按人 / 周期（今天/昨天/本周/本月/全部）/ 产品 / 项目 / 执行过滤，
和「我的地盘」里的动态是同一份数据、不同的过滤维度。

**④ 备注**：`comment` 就是替对象写一条 `commented` 动作；改备注时会校验**作者**（只能改自己的），
非备注动作不允许改。这一条在测试里是用「直接插一条 actor=tester 的备注，再用 admin 去改」验的。

顺带补一个很容易踩的坑：这几个接口用的是表单参数（`@RequestParam`），
断言脚本里如果带着 `Content-Type: application/json` 发 form 数据，Spring 会去解析 JSON body、
直接报「请求参数缺失:xxx」——所以脚本里单独准备了一套不带 JSON header 的请求头（第 46 条坑的同类问题）。

### 3.35 BI：数据视图 + 图表 —— 禅道底下的 DuckDB，本实现走「SQL 模式」

禅道 20+ 的 BI 是一整条链路：`zt_dataview`（数据视图/数据集）→ `zt_pivot`（透视表：行/列维度 + 指标）
→ `zt_chart`（图表），底层查询引擎在 `module/bi`（25,589 行）里，做法是**把 MySQL 数据同步成
Parquet 文件、再用 DuckDB 查**。也就是说：BI 的价值一半在「数据模型」，一半在「列式引擎」。

本实现**不引入 DuckDB**，走禅道自己也支持的 **SQL 模式**（`mode='sql'`）：
数据视图存一条只读 SELECT，直接在 MySQL 上执行；图表在其上做「按维度分组 + 聚合指标」。
这样用户可见的链路（建视图 → 预览 → 配图表 → 渲染）能完整跑通，引擎部分留作后续。

**代价是「安全边界得自己守住」**，这是全项目唯一拼 SQL 的地方，所以规则写成一个类
（`SqlGuard`）并且逐条有断言：

| 规则 | 拦下来的例子 |
|---|---|
| 单语句 | `SELECT ...; DROP TABLE zt_story` → 拒绝（出现分号） |
| 不许注释 | `... WHERE 1=1 -- x` → 拒绝（注释符可以用来藏后半段） |
| 必须 SELECT / WITH | `DELETE FROM zt_story` → 拒绝 |
| 关键字黑名单 | insert/update/drop/alter/…/outfile/sleep（词边界匹配，`settings` 里的 set 不误伤） |
| 只允许 `zt_*` 表 | `system_users` / `information_schema.tables` / `` `ruoyi-vue-pro`.zt_story `` → 拒绝 |
| 字段名白名单 | 维度/指标/过滤字段必须 `[A-Za-z_][A-Za-z0-9_]*`，`status; DROP TABLE` → 拒绝 |
| 聚合函数取自枚举 | `median` → 拒绝；拼进 SQL 的函数名只能来自 `AggTypeEnum` |
| 结果限行 | 预览包一层 `SELECT * FROM (sql) t LIMIT n`（≤200），图表最多 200 个分组 |

**图表的查询设置**放在 `settings` JSON 里：`{dimensionField 维度, metricField 指标, agg 聚合, limit, sort}`
—— 对应禅道 pivot 的「行维度 / 指标」语义；过滤器 `filters: [{field, operator, value}]`
支持 eq/ne/like/gt/ge/lt/le/in/between，值是**字面量转义**后拼进 WHERE（同样先过字段白名单）。
另外图表是**版本化对象**：改一次 `version + 1`（禅道 pivot/chart 也是版本化的）。

**迁移边界**：`chart`（模块 #62）本轮做完（CRUD + 数据 + ECharts 渲染）；`bi`（#3）标为 ⚠️ 部分
—— 数据视图与图表的 SQL 模式已通，**DuckDB/Parquet 引擎、`zt_pivot` 透视表（多行多列交叉表）、
`screen` 大屏都没做**，所以 `bi` 的 20,147 行**不计入**完成行数。

### 3.34 度量：定义在表里、口径在代码里、数据在库里

`metric` 是清单里最大的模块（29,667 行业务 PHP + 414 个 calc 类共 20,368 行 = 50,045 行），
但拆开看结构非常清楚 —— 三块职责：

```
zt_metric     度量项定义：目的 purpose / 范围 scope / 对象 object / 单位 unit /
              时间维度 dateType / 口径说明 definition / 上次计算行数与时间
口径          禅道：module/metric/calc/** 下 414 个 calc 类（类名 == zt_metric.code）
              本实现：MetricRegistry —— 一张 code → 取数 SQL 的表，每条都注明对应哪个 calc 类
zt_metriclib  度量数据：一行 = 维度组合（system/program/project/product/execution/user/...）
              + 时间粒度（year[/month[/day]] 或 year+week）+ value（字符串）
```

**记录的主键逻辑是「度量项 + 维度 + 时间」，不是自增 id**。所以重算前必须先清旧数据，
清的范围由**记录里出现的时间列**决定（禅道 `getMetricCycle` + `clearOutDatedRecords`）：

| 周期 | 清的范围 | dateType |
|---|---|---|
| year | 该年 | `year` |
| month | 该年该月 | `month` |
| week | 该年该周 | `week` |
| day | 该年该月该日 | `day` |
| （无时间列） | 清「今天的快照」，查询也只看 `date >= 今天` | `nodate` |

本实现在这批规则上做了三件"边界"上的事，都进了断言：
**① 幂等**（同一度量项连算两次，数据条数不变）；**② 交叉校验**（算出来的值直接和
`SELECT COUNT(*) FROM zt_story ...` 对账，不是接口自说自话）；**③ 未迁移口径必须报错**
（`count_of_story_in_stage_in_product` 这类没实现的 code 返回「口径尚未迁移」，
而不是返回 0 或算错的值）。

**迁移边界（这一轮明确写清）**：框架（定义/数据两张表 + 计算流程 + 清旧数据 + 视图接口 + 页面）
已完整搬过来，口径**只迁了 15 个**「能用已迁表算出来」的（产品维度的需求/缺陷/用例/发布/计划数、
项目维度的执行/人员/工时、人员维度的需求/缺陷/用例、三个年度新增口径）。
其余 399 个 calc 类是**数据资产**（比如代码库/流水线/发布质量那些依赖 `repo`/`bi` 模块的口径），
按需补即可 —— 补的时候只需要在 `MetricRegistry` 加一行 `code → SQL`，表结构与页面都不用动。
种子数据里故意留了 3 个「只导入定义、不实现口径」的度量项，用来持续验证这条边界。

### 3.33 看板：位置不在卡片上，而在「泳道 × 列」的格子里

看板是清单里除了 `metric`/`bi` 之外最大的模块（9,545 行 / 8 张表），但它一点都不"玄" —— 就是七层聚合，
每一层都有独立的表和独立的操作日志对象：

```
空间 zt_kanbanspace            私人/协作/公共空间
 └── 看板 zt_kanban            acl、archived(是否启用归档)、showWIP/displayCards/流式列宽
       └── 区域 zt_kanbanregion      一块独立的板（一个看板可以有多块）
             └── 分组 zt_kanbangroup 泳道与列**通过 group 归属**：同一个分组里的泳道共享同一批列
                   ├── 泳道 zt_kanbanlane   横向：common / story / bug / task
                   └── 列   zt_kanbancolumn 纵向：limit = 在制品上限（WIP），-1 不限
                         └── 格子 zt_kanbancell  = 泳道 × 列，`cards` 是逗号列表
```

**最要紧的一条：卡片的位置不在卡片表里。** `zt_kanbancard` 只有「哪个看板、哪个分组」，
真正的位置与顺序是 `zt_kanbancell.cards`（`,96501,96502,`）—— 和 `zt_story.plan` 同一套路。
所以「移动卡片」= 先按**源泳道类型**把卡片从该区域所有相关格子里摘掉，再追加到目标格子末尾，
并把 `card.group` 改成目标泳道的分组（禅道 `model.php:moveCard`）。

**默认布局是自动建的**（禅道 `createRegion` → `createDefaultLane` + `createDefaultColumns`）：
建看板 / 建区域时会一次生成「默认区域 + 分组 + 默认泳道 + 未开始/进行中/已完成/已关闭 四列 + 所有格子」。
少了这一步，前端会拿到一块空板 —— 断言里专门验了「新看板就有 4 列 + 1 泳道 + 4 格子」。

**在制品上限（WIP）的两条规则**（`createColumn` / `checkChildColumn`）：

| 规则 | 表现 |
|---|---|
| limit 只能是 -1（不限）或正整数 | 传 0 / -2 都拒绝 |
| 子列的 limit 之和 ≤ 父列限额；父列有限额时子列不能「不限」 | 父列 5、已有一个子列 3 时，再建 3 的子列拒绝（3+3>5） |

但**WIP 超限本身后端不拦**：禅道只在界面把「卡片数/上限」标红（`module/kanban/js/view.ui.js:63`），
所以这里也只在视图数据里给 `overWip`，移动照旧放行 —— 界面上的约束不要在后端擅自升级成硬校验。

**删除策略按禅道的原样分两套**：泳道/区域/看板/空间是**逻辑删除**，列与卡片是**物理删除**
（`control.php` 里就是 `dao->delete`）。两处有意偏离写在接口注释里：① 禅道删看板只软删看板本体，
区域/泳道/列全成孤儿，本实现级联软删；② 禅道物理删卡片时不清格子里的编号（靠重新查卡片掩盖），
本实现顺手摘掉，不留悬空编号（测试里直接查库断言 `cards` 里没有它）。

### 3.32 用例库：和「用例集」是同一张表的两类记录，一行代码都没建表

清单里 `caselib`（2,293 行 / 16 个 action）看名字像又一个测试模块，其实它**没有自己的表**：

```php
// module/caselib/model.php:142
$this->dao->select('id, name')->from(TABLE_TESTSUITE)      // ← 就是用例集的表
    ->where('product')->eq(0)->andWhere('deleted')->eq(0)
    ->andWhere('type')->eq('library')                      // ← 靠 type 区分
```

| | 用例集 `testsuite` | 用例库 `caselib` |
|---|---|---|
| 表 | `zt_testsuite` | `zt_testsuite`（同一张） |
| 区分 | `product = <产品>`，`type in ('public','private')` | `product = 0`，`type = 'library'` |
| 里面的用例 | `zt_case`（`product=<产品>`, `lib=0`） | `zt_case`（`product=0`, `lib=<库>`） |
| 模块树 | `(root=产品, type='case')` | `(root=库, type='caselib')` |

所以这一轮的**表数是 0 增加**（还是 36 张），代价全在「区分条件」上：两边查询必须各带一半条件。
只加一边就会串数据 —— 这正是坑位 #12 的同一个坑，这次是**双向**的（用例集列表要排除库、
产品用例列表要排除库用例），所以专门写成了坑位 #45。

**产品用例 → 用例库（`testcase/importToLib`）** 是库真正有价值的地方，规则照抄禅道：

```
① 复制用例（标题/前置条件/关键词/优先级/类型/环节）+ 当前版本的全部步骤
② fromCaseID / fromCaseVersion 记下来源与「导入时来源的版本」（第 5 处版本冻结）
③ 来源模块同步到库的模块树：zt_module.from 记住来源模块编号，重复导入直接复用，不重复建
④ 来源后来升版 → 库里那条只标「源用例已更新」，不自动同步（与需求/用例的版本冻结同一套路）
⑤ 已经导入过的用例不再出现在「可导入清单」里（禅道 getCanImportCases 就是按 fromCaseID 筛）
```

**两处有意偏离**（都写进接口注释）：禅道删用例库不做任何检查（直接软删，库里那批用例变孤儿），
本实现沿用项目统一的「还有子对象就拒绝删除」策略；禅道重复导入会把库里那条**覆盖**成最新版本
（并给来源侧计数），本实现直接拒绝重复导入，把「要不要同步」交给使用者。

**顺带补的坑**：`zt_case` 早期建表时没有 `lib` / `fromCaseID` / `fromCaseVersion` 三列
（那时还没做用例库），`deploy/sql/39-zt_caselib.sql` 用 `information_schema` 判断后动态 `ALTER`
补上，保证这个脚本可以重复执行（`resume-verification.sh` 每次都灌）。

### 3.31 需求分层：业务需求 / 用户需求 / 研发需求，是一张表的三层，不是三个模块

清单里 `epic`（829 行）和 `requirement`（810 行）看起来是两个大模块，实际拆开看是**薄壳**：

```
module/epic/model.php         15 行，内容只有 class epicModel extends model
module/requirement/model.php  15 行，同上
module/epic/control.php      501 行 —— 每个 action 都只是把 storyType 换成 epic 后转调 story
module/requirement/control.php 482 行 —— 同理，换成 requirement
```

也就是说：**三层需求共用 `zt_story` 一张表，靠 `zt_story.type` 区分**：

```php
// module/story/lang/zh-cn.php
if($config->enableER) $lang->story->typeList['epic']        = $lang->ERCommon;  // 业务需求
if($config->URAndSR)  $lang->story->typeList['requirement'] = $lang->URCommon;  // 用户需求
$lang->story->typeList['story'] = $lang->SRCommon;                              // 研发需求
```

本实现照着这个数据模型做（**不建表、不建独立 Service**），在 story 上加了分层能力：

| 能力 | 接口 | 说明 |
|---|---|---|
| 类型字典 | `GET /zentao/story/type-list` | 三个类型 + 层级 + 各自允许的父类型 + 分解出的子类型 |
| 类型数量 | `GET /zentao/story/type-summary?product=` | 需求池页签角标；数据库 `GROUP BY type` 一次算完，没有数据的类型补 0 |
| 分层树 | `GET /zentao/story/type-tree?product=&rootId=` | 业务需求 → 用户需求 → 研发需求 的嵌套结构，可按 `rootId` 只看一棵子树 |
| 类型过滤 | `GET /zentao/story/page?type=&types=` | 列表页签；`types` 支持多选（如只要业务需求 + 用户需求） |

**最核心的一条规则：父需求的层级不能低于子需求。** 它不是我编的，是禅道三个方法归纳出来的
（`module/story/model.php` 的 `getEpicParents` / `getRequirementParents` / `getStoryParents`）：

```
getEpicParents()        父只能是 epic
getRequirementParents() 父可以是 epic + requirement
getStoryParents()       父可以是 epic + requirement
⇒ parentLevel <= childLevel
```

于是合法组合只有：业务需求下挂任意层、用户需求下挂用户需求/研发需求、研发需求下只挂研发需求
（最后一条是「研发需求自己的分解」）。违反时报 `「用户需求」不能挂在「研发需求」下，父需求的层级不能低于子需求`。

**分解出来的子需求类型由父推导，不是沿用父类型**（禅道：业务需求的分解按钮跳 `requirement/batchCreate`、
用户需求的跳 `story/batchCreate`）：

```
业务需求 --分解--> 用户需求 --分解--> 研发需求 --分解--> 研发需求（四级子需求）
```

三层共用已有的整套机制，这也是"薄壳"的代价为零的原因：`path/root/grade` 的树规则、
`zt_storyspec` 版本快照、父需求 `estimate` = 子需求之和、状态级联关闭，一行都不用改。
演示数据里放了一条完整链路（业务需求 99301 → 用户需求 99302 → 研发需求 99303）。

**刻意不做的一件事**：禅道用 `$config->enableER` / `$config->URAndSR` 两个开关决定要不要
展示业务需求/用户需求（开源版默认都关掉，这两个模块等于藏起来）。本实现**不做这个开关**：
它属于界面裁剪，不属于数据模型；关掉开关的禅道库里只有 `type='story'` 的数据，
打开后照样能读能建，不存在兼容问题。前端就是把三层做成页签 + 一个「分层视图」树表，
类型在弹窗里选、创建后不可改（禅道也不支持改类型，只有 IPD 版有转换流程）。

顺带修掉一处历史错误码撞号：`STORY_DUPLICATE_SELF` / `STORY_NOT_DRAFT_CANNOT_UPDATE`
原本复用了 `1_020_000_007` / `1_020_000_008`（与「父需求不存在」「父需求不属于同一产品」撞号），
现在是独立的 `012` / `013`，新增类型相关错误码 `014`（类型不合法）/ `015`（父子类型不匹配）。

### 3.30 项目视角的四个小模块：三个「redirect」+ 一个真表

清单里有四个小模块，很容易被当成"还没做"而低估 —— 其实它们在禅道里的形态各不相同，
照着它们的**真实形态**做，比给每个都建一张表要诚实得多：

| 模块 | 禅道里是什么 | 本实现怎么做 |
|---|---|---|
| `projectplan` | 纯 redirect：`echo $this->fetch('productplan', 'browse', ...)` —— 计划本来就是**产品维度**的 | 不建表，提供 `GET /zentao/projectplan/plan-list?project=`：项目关联的每个产品（可限定分支）的计划并集 |
| `projectbuild` | 纯 redirect：`project/build` | 不建表，`GET /zentao/projectbuild/build-list?project=`：**项目下所有执行的构建 + 直挂项目的构建**（构建挂执行，`zt_build.execution`） |
| `projectrelease` | 项目的发布列表 | 不建表，`GET /zentao/projectrelease/release-list?project=`：`zt_release.project` 是逗号列表，用 `FIND_IN_SET` 反查 |
| `workestimation` | **有自己的表**（`zt_workestimation`），但开源版只有 model 没有界面 | 建表 + 接口，两个派生值由服务端算 |

**工作量估算的两个公式**（表里没有"公式"列，但字段名就是它的定义）：

```
duration       = scale / productivity                  （工期 = 规模 ÷ 生产率）
totalLaborCost = duration × dayHour × unitLaborCost    （总人工成本）
```

两处刻意的处理：**生产率填 0 时不做除法**（工期 0，不报错）；**两个派生值不接受入参**
（前端传了也忽略，防止算出来不一致的工期/成本被写进库）。

顺带补了发布的一个缺口：`release/create` 现在支持 `projects` 手工指定「这个发布涉及哪些项目」
（以前只能从构建反推，等于没有项目的发布反查不到）。

### 3.29 干系人：和「团队成员」是两回事

`zt_stakeholder` 很容易被当成 `zt_team` 的另一种叫法，但禅道把它们做成了**两张表、两个模块**，
因为语义根本不同：

| | `zt_team` 团队成员 | `zt_stakeholder` 干系人 |
|---|---|---|
| 是什么 | **要干活的人** | **需要知情 / 被影响的人** |
| 字段 | 角色、可用天数、每天小时（算工时） | 关键与否、内部/外部、来源 |
| 挂在哪 | 项目 / 执行 | 项目集 / 项目 |
| 典型 | 研发、测试 | 甲方、领导、外部顾问 |

所以「加一个干系人」**不会**影响团队人数 —— 本轮的断言里专门验了这一条（干系人不进 `zt_team`）。

**三条从禅道抄来的规则**：

1. **`type` 由 `from` 推导**，不是独立选的：`from='outside' → type='outside'`，其余 → `inside`
   （`module/stakeholder/model.php#create`）；
2. **同一个人不能重复加到同一个对象下**（禅道的 check 是 `user unique(objectID = ? AND deleted = '0')`）；
3. **删除是按「对象 + 账号」**的（禅道 `delete(userID)`），所以接口同时给了按记录编号和按账号两种删法。

**简化掉的一处**：`from='outside'` 时禅道会在 `zt_user` 里建一条 `type='outside'` 的外部用户记录，
本实现直接把名字存在 `stakeholder.user` 列里（外部人员本来就不该进用户表，见「已知限制」）。

### 3.28 质量链收口：缺陷解决时，构建与发布的清单自动跟着动

前面几轮把「测试链」做闭环了（用例 → 测试单 → 执行 → 失败建缺陷），但**缺陷解决之后**那条线是断的：
缺陷的「解决版本」填了构建编号，可这个构建的 Bug 清单、以及包含这个构建的发布的 Bug 清单，
都还得靠人去点一次「关联」。禅道不是这样 —— `module/bug/model.php:2075` 里解决缺陷时会顺手做两件事：

```
① zt_build.bugs   ← 并进这个缺陷（解决版本指向的那个构建）
② 找到 product 相同、且 (FIND_IN_SET(构建编号, `build`) 或 shadow = 构建编号) 的发布
   zt_release.bugs ← 并进这个缺陷，并重建 zt_releaserelated 里的 (release, 'bug') 关系行
```

第 ② 步里那个 `shadow` 分支是给「不带构建创建的发布」准备的 ——
禅道会给这种发布自动生成一个**影子构建**，缺陷如果填的是影子构建的编号，同样要能并进发布。

**本实现**把这段逻辑抽成 `ReleaseService#appendBugByResolvedBuild(产品, 缺陷, 解决版本)`，
两个入口都走它：

| 入口 | 场景 |
|---|---|
| `bug/resolve` | 解决缺陷时填了「解决版本」 |
| `build/link-bug` | 把未解决的缺陷关联到构建（关联即解决，`updateLinkedBug`）—— 既然算「在这个构建里解决」，发布也该拿到 |

几处刻意的行为：
- **解决版本不是纯数字就跳过**：禅道这一列存的是**构建编号的字符串**，但界面上也允许填版本名；
  解析失败时静默跳过，不报错、不污染任何清单；
- **去重**：构建/发布的 Bug 清单都是逗号列表，重复关联只保留一份（复用 `linkBugs` 的去重逻辑）；
- **没有匹配的发布就只回写构建**：构建可以不属于任何发布。

这样整条质量链才算真正闭上：**用例失败 → 建缺陷（带用例/版本/测试单）→ 解决（带构建）→
自动进构建清单 → 自动进发布清单 → 发布的三份清单里有据可查**。

### 3.27 团队：主干链路的「谁」，顺带又纠正了一次层级口径

`zt_team` 是项目与执行的**成员表**（同一张表用 `type` 区分）。它值得单独做一轮，是因为
之前的实现里有一个**看起来没问题、其实站不住的捷径**：

> 项目上的 `teamCount`（团队人数）是拿 `zt_project.team` 这个**逗号字符串**数出来的。

禅道不是这么算的 —— `module/project/tao.php#fetchMemberCountByIdList` 是从 `zt_team` 统计的
（`SELECT root, COUNT(1) FROM zt_team JOIN zt_user ... GROUP BY root`）。
字符串只装得下账号，回答不了「他在这个项目里什么角色、从哪天开始、每天投入几小时」，
而这三件事正是工时统计、访问控制、负载分配的基础。

**四条从禅道抄来的规则**：

| 规则 | 出处 | 实现要点 |
|---|---|---|
| 可用工时 = `days × hours`，`hours` 默认 7.0 | `project/model.php:556`、`config/execution.php` | `total-hours` 接口与列表里的 `totalHours` |
| 全量保存是「先删后插」，但**老成员的加入日期要保留** | `project/model.php:2029` + `tao.php#insertMember` | `updateMembers` 先记 `oldJoin` 再重建 |
| 成员是**物理增删**（本表没有 `deleted` 列） | 禅道建表 | DO 不继承 `BaseDO`，用 `@Delete` 物理删。**逻辑删除会撞 `UNIQUE(root,type,account)`**（坑位 #4 同款） |
| 执行的成员来自负责人字段（PO/PM/QD/RD） | `execution/model.php:598` | `syncOwners`：只补不删，手工加的成员不受影响 |

**顺带纠正的第二个口径错误**：上一轮我推断「执行不在项目集树里，所以 `path` 留空、`grade` 归零」——
错。执行模块**有自己的** `setTreePath`（`module/execution/model.php:5004`）：

```
parent = 所属项目（嵌套阶段时是父阶段）
path   = ,项目id,执行id,        grade = 1
```

禅道自己的 demo 数据就是这个形状（`(3, project=2, parent=2, path=',2,3,', grade=1, type='sprint')`）。
本轮把 `ExecutionServiceImpl`、`StageServiceImpl` 与已有数据（`35-zt_team.sql` 里的 UPDATE）一起纠正过来。

**这两次纠错是同一个教训**（见坑位 #37/#41）：**层级字段的语义只能从「那张表自己的写入代码」+「禅道 demo 数据」抄**，
不能从一个模块（program）的函数推断另一个模块（execution）的行为。

### 3.26 我的地盘：一条不产生数据的「查询层」

`my` 是禅道里最典型的**查询层**：它自己不建表（除了待办），只是把「指派给我的 / 我登记的」
聚合出来。本实现刻意**不重写任何查询**，而是「带上我的账号」去调各模块已有的分页接口：

| Tab | 实际请求 | 口径 |
|---|---|---|
| 我的任务 | `task/page?assignedTo=我` | 指派给我 |
| 我的缺陷 | `bug/page?assignedTo=我&status=active` | 指派给我且未解决 |
| 我的需求 | `story/page?assignedTo=我&status=active` | 指派给我且激活 |
| 我的工时 | `effort/page?account=我` | 我登记的流水 |
| 我的动态 | `zt_action.actor = 我` | 我干过什么 |
| 我的待办 | `zt_todo` 的专用查询 | 见 3.25 |
| 我参与的项目/执行 | `project/page?member=我`、`execution/page?member=我` | 负责人字段 或 团队成员命中我（见 3.37） |
| 我的团队 | `zt_team WHERE account = 我` | 我在哪些项目/执行里、什么角色、可用工时（见 3.37） |
| 我的测试单/用例/文档 | `testtask/page?member=我`、`testcase/page?openedBy/reviewedBy`、`doc/page?member=我` | 见 3.37 |
| 我的日历 | 待办/任务/测试单按天归组 | 见 3.37 |

这么做的收益是：各模块的过滤逻辑（模块子树展开、多值字段 `FIND_IN_SET`、分支匹配…
全都在各自的 Mapper 里）**不会出现第二份实现**。代价是「我的地盘」的性能取决于各模块的分页接口，
但对个人视图来说数据量很小。

**第二组已在第 34 轮补齐**（我参与的项目/执行、我的团队、我的测试单/用例/文档、我的日历，见 3.37）。

**仍未做的部分**：禅道 `my` 里还有评审、风险、会议、MR、审批的聚合，
它们分别依赖 `reviewissue` / `risk` / `meeting` / `mr` / `approval` 这些尚未迁移的模块 ——
这几项在清单里仍如实记为「未做」。

### 3.25 待办：唯一属于「个人」的表，和任务不是一回事

`zt_todo` 很容易被当成「任务」的别名，但它和 `zt_task` 是两种东西：

| | `zt_task` | `zt_todo` |
|---|---|---|
| 属于谁 | 执行（项目里的工作） | 个人（谁的清单） |
| 有工时吗 | 有（estimate/consumed/left） | 没有 |
| 必须挂对象吗 | 必须挂执行 | **可以不挂任何东西**（`type='custom'`） |
| 列表怎么查 | 按 project/execution/assignedTo | 按 `assignedTo / finishedBy / closedBy` 命中我 |

**第一条容易做错的是「我的待办」的口径**：禅道 `module/todo/tao.php#getListBy` 的条件是
`assignedTo = 我 OR finishedBy = 我 OR closedBy = 我` —— 别人指派给我的、我完成过的、
我关闭过的**都算**「我的地盘」里的待办；如果按 `account = 我` 查，就会漏掉别人指派给我的那些。

**第二条是「关闭」对指派人做的改动**：`closeTodo` 会把 `assignedTo` 写成伪用户 `'closed'`
并刷新 `assignedDate`，所以关闭后的待办**不再出现在任何人的「指派给我」里**；
「激活」时再把它还原成 `finishedBy`（谁完成的就回到谁手上）。

**第三条是私有待办**：`private=1` 时列表里仍然会出现（因为 `assignedTo` 命中），
但**看的人不是它的 `account`**（归属人）时，名称被替换成「这是私有待办」。

**顺手做掉的「挪到今天」**（`import2Today`）：把某人「今天之前且未完成」的待办日期改成今天 ——
这是禅道里很常用的一个动作（昨天没干完的活今天接着看），实现上要注意
**已完成的不能被挪**。

### 3.24 项目集：把一个「新模块」做成一条对主干链路的纠正

项目集（`module/program`）在清单里是 **P0**：它是产品与项目共同的上级维度。
但这一轮真正的收获不是「又多了一个模块」，而是**顺着它把主干链路的误读纠了回来**：

| 上一轮的认识 | 禅道的真实设计 | 影响 |
|---|---|---|
| 项目和执行共表（2 种角色） | **项目集 / 项目 / 执行共表**（3 种角色） | 少了一种角色，所有 `type` 过滤都要重审 |
| `project.parent` = 父项目 | `project.parent` = **所属项目集** | 项目之间是平级；「项目套项目」在禅道里不存在 |
| `project.path` = `/1/2/`（斜杠） | `,9001,1,`（逗号，grade 从 1 起） | 层级数据全错，跨模块按 path 找子树会失效 |

**项目集模块做了这些**（15 个端点）：

- **CRUD + 父子项目集**：`path = 父.path + id + ','`、`grade = 父.grade + 1`，顶级 grade=1；
  同名只在**本级**查重（禅道 unique 条件是 `type='program' and parent=当前父`），
  所以两个不同的项目集下可以有同名子项目集；
- **状态流转**：未开始 → 进行中 →（挂起 ⇄ 进行中）→ 已关闭，写 `realBegan` / `realEnd`；
- **移动项目集要重算整棵子树**（禅道 `processNode`）：子孙的 path 里含 `,自己,` 的那一段
  就是它在这棵树里的**相对位置**，前面换成**新父**的 path、grade 按层级差平移；
- **统计读时现算**：下级项目集数（`parent` 指自己且 type='program'）、
  项目数（`parent` 指自己且 type='project'）、产品数（`zt_product.program` 指自己）；
- **删除保护**：下级还有项目集 / 项目 / 产品就拒绝。禅道的 `delete` 会连带处理下级，
  本实现选择显式报错 —— **静默级联删掉一个项目集下的项目和产品，比报错危险得多**；
- **产品归属**：`zt_product.program` 写入前校验目标确实是项目集，
  产品线（`zt_product.line`）校验目标确实是 `zt_module` 里 `type='line'` 的节点。

前端把项目集做成**一棵树**：后端 `/list` 返回平铺的项目集（含统计），
前端按 `parent` 现场建树（项目集就是一张自关联的表），
详情抽屉分「项目」「产品」两个 Tab —— 正好对应项目集的两条下级关系。

### 3.23 工时明细：剩余工时不是一个减法结果，而是一句声明

前面的「工时三件套」（3.5）讲了 `estimate` / `consumed` / `left` 怎么牵制状态，
但那是**三个数字**。这一轮补上它们的**来源**：`zt_effort` 工时流水。
一条工时记录 = 谁、哪天、为哪个任务花了多久（`consumed`）、**这之后还剩多久（`left`）**。

**关键在 `left` 不是算出来的，而是每次报工时时顺手声明一次**：

```
task.consumed = SUM(effort.consumed)          ← 加总
task.left     = 最后一条 effort.left          ← 以最后一次声明为准
```

不是 `estimate - consumed`。这是禅道刻意留的余地：估时不准很正常，
发现比预想简单或复杂时，**改的是「还剩多少」这句声明，历史记录不用动**。
所以同一个任务连续报工时，`left` 完全可以忽高忽低 —— 那是有人在修正估时，不是算错。

**由此推出的状态联动**（每登记/修改/删除一条工时都重算一次）：

| 情形 | 结果 |
|---|---|
| `left = 0` 且状态是 未开始 / 进行中 / 已暂停 | 自动置为**已完成** |
| `left > 0` 而状态已是已完成 | 退回**进行中**（还剩活就不算完成） |
| 删的是最后一条 | 剩余回到**新的最后一条**声明的值 |
| 工时被删光 | 消耗归零；状态不是未开始就退回未开始、剩余回到 `estimate`，并把完成人/完成时间/取消人/关闭人/关闭原因一并清空 |

最后一条要清痕迹，是因为「已完成但没有一条工时支撑」会直接污染绩效与燃尽图统计。

**一个容易被当成 bug 的边角**：如果任务本来就是「未开始」，删光工时时**只把已消耗归零、
不动任务自己的 `left`**（禅道就是这个口径）—— 因为未开始的任务允许在编辑表单里手填剩余工时，
删一条工时不该把手填的值冲掉。只有「非未开始」的任务才走「退回未开始 + 剩余回到 `estimate`」。

**实现上和禅道有意不同的一点：重算，而不是增量维护。**
禅道是增量的（加一条 `+=`，删一条 `-=`），于是要为「删的是不是最后一条」「是不是删到一条不剩」
写一长串 if/else（`module/task/tao.php#getTaskAfterDeleteWorkhour` 里通篇是特判，
甚至同一个函数里对「最后一条」用了两套口径：判断 isLast 按 id，取上一条的 left 按 date,id）。
本实现改成**每次改动后从工时流水重算一遍**：结果与禅道一致，但不会因为漏掉某个特判而算歪，
而且「删任意一条都不会错」这件事不再依赖穷举。一个任务通常只有几条到几十条工时，重算成本可以忽略。
这也是这一轮唯一一处「行为一致但算法更稳」的改动。

**工时的对象是通用的**：`zt_effort` 用 `objectType` + `objectID` 指向对象
（禅道同一张表也记需求和缺陷的工时）。本实现只落任务，但字段保留着，
将来要做「需求工时」不用改表。

### 3.21 父子需求：父需求是「汇总器」，不是「执行体」

需求可以**分解**成若干子需求，父需求只做汇总、不直接开发。禅道为此在 `zt_story` 上留了六列，
本轮补齐：`parent` / `parentVersion` / `root` / `path` / `grade` / `isParent`。

**需求树和模块树是同一套规则**：`path` 用逗号包起来且包含自己（一级 `,4,`、二级 `,4,92201,`），
`root` 是顶层祖先，`grade` 是层级 —— 于是「捞出整棵树」就是一次 `root=` 查询 + 一次 path 前缀匹配，
不需要递归。`parentVersion` 又是那个熟悉的**冻结值**：它记的是分解那一刻父需求的版本，
父需求后来正式变更，子需求要提示「父需求已变更」。

**三条「子动父跟着动」的规则**（禅道 `updateParentStatus` + `computeEstimate`）：

1. `isParent`：有子需求就是 1，没有就是 0（列表上显示「父」标记）
2. **父需求的 `estimate` = 所有子需求工时之和** —— 父需求自己不填工时，它是算出来的
3. **状态级联**：子需求全部关闭 → 父需求自动关闭；父需求已关闭但还有子需求没关 → 父需求自动激活

第 3 条是「级联」里最容易被漏掉的一环：它要求**所有会改变子需求状态的入口都回头调一次父需求刷新**。
我一开始只在新建议务里调了，关闭/激活两条路径忘了，于是测试里父需求一直停在 `draft`。
另外这里还踩了一个更隐蔽的坑（见第 32 条）。

### 3.20 测试报告与用例集：一个「打包」，一个「汇总」

这两个模块都不产生新的执行数据，都是对既有测试数据的组织方式：

- **用例集**（`zt_testsuite` + `zt_suitecase`）：把用例打包成可复用的集合，
  排进测试单时一次选完，不用一条条勾。它只是「编排的便利」。
- **测试报告**（`zt_testreport`）：把**一段时间内若干测试单**的执行结果汇总出来。
  它只是「结果的汇总」。

**报告只存条件，数字现算**。`zt_testreport` 里存的是「汇总哪些测试单」（`tasks` 逗号列表）、
时间范围（`begin` / `end`）、以及人写的结论；用例数、通过、失败这些数字都是**读的时候算**的
（禅道 `getResultSummary`）。好处是报告不会因为数据变化而过期失真 ——
但它同时也会把「生成时算出来的需求/缺陷/用例清单」落库留档，因为**已发出的报告是一份快照**，
之后新增的用例不该改变它的结论。

**汇总规则里有一个容易忽略的点**：一条 run 在统计区间里可能跑了很多次，
**只取最后一次结果**，而不是把每次执行都算一遍。否则「先通过、后失败」会被算成
「一次通过 + 一次失败」，通过率直接虚高。实现时这里踩过一个很具体的坑（见第 31 条）。

**用例集有一个禅道原版的缺陷**：`linkCase` 用的是 `REPLACE INTO zt_suitecase`，
但禅道**没有给这张表建唯一键** —— REPLACE 依赖唯一键判断「已存在」，
没有唯一键就变成「每次都插一行」，同一个用例在集合里会出现多次。
本实现补了 `UNIQUE(suite, case)`，并把 REPLACE 换成「存在就只更新版本」，
顺带保住了 `create_time`。

### 3.19 测试单：执行一次写三处，测试链在这里闭环

到测试单为止，测试这条线才真正闭合。前面做的 `testcase` 只是「用例仓库」，
用例身上的「最近执行结果 / 执行人 / 执行时间」三个字段**只有在测试单里执行过才会有值** ——
它们不是用例自己维护的，而是执行时被回写的。

三张表分工明确：

```
zt_testtask   测试单：这次要测哪个包（build）、谁负责、什么时候测完
   └ zt_testrun     排进来的用例，UNIQUE(task, case)：「这个测试单里有哪些用例」
        └ zt_testresult  每次执行一行：「这条用例在这个测试单里跑过几次、每次什么结果」
```

**执行一次写三处**（禅道 {@code createResult} 的等价实现）：

1. `INSERT zt_testresult` —— 一行执行历史，步骤级结果以 JSON 存进去
2. `UPDATE zt_case` —— 最近执行结果 / 执行人 / 执行时间（**这就是那三个字段的唯一来源**）
3. `UPDATE zt_testrun` —— 状态（`blocked` / `normal`）+ 最近结果

**用例级结果是由步骤结果算出来的**，规则很具体：默认 `pass`；逐个看步骤结果，
`n/a` 和 `pass` 跳过；第一个「既不是 n/a 也不是 pass」的结果就成为用例结果，
遇到 `fail` 立即结束。所以是 **fail 优先、其次按出现顺序**，不是多数派。
这个规则有反直觉的一面：`blocked` 出现在前、`fail` 出现在后，最终结果必须是 `fail`
（测试里专门断言了这一条）——如果按「第一个有效结果」实现就会错。

**有意偏离禅道：重复排入不抹执行结果**。禅道 {@code linkCase} 用
`REPLACE INTO zt_testrun`，而表上有 `UNIQUE(task, case)` —— REPLACE 是「先删后插」，
于是「把同一个用例再排一次」会把它之前的执行结果和历史全部清空。
本实现改成「存在就只更新用例版本与指派」，把结果原样保留。

**执行失败 → 建缺陷，测试链在这里接到质量链**：缺陷的归属（产品来自测试单，
分支/模块/需求来自用例）和「来源三件套」（`case` / `caseVersion` / `testtask`）
都是自动带过去的，用户只需要填标题和严重程度。`caseVersion` 同样是**冻结值**：
它记的是**实际执行的那一版**，用例后来再改步骤，缺陷依然指向当初跑的那一版 ——
和 `zt_projectstory.version`（3.13）、`zt_case.storyVersion`（3.18）是同一个思路。

复现步骤不填时会**自动生成**：按用例步骤拼出来，并在指定的失败步骤后面标一个
「← 这一步失败」。禅道是把用例步骤整段带过去让人改，这里只是多做了一步标注。

**关闭测试单的两条时间校验**（禅道原规则）：完成时间不能早于计划开始日期，
也不能晚于明天。这里又踩了第 21 条坑：`realFinishedDate` 不显式指定反序列化器时，
`"2099-01-01 10:00:00"` 会被静默解析成 1970 —— 于是「不能晚于明天」永远不触发，
反而报「不能早于计划开始日期」。**同一个坑在一个新模块里又出现了一次**，
说明它不是偶然，而是「凡是时间字符串进、业务规则判断」的字段都会中招。

### 3.18 测试用例：第三条版本规则，判据是「步骤」

到这一节为止，项目里已经有三条**形态相似、判据完全不同**的版本链：

| 模块 | 版本表 | 什么时候 version+1 | 额外副作用 |
|---|---|---|---|
| 需求 `story` | `zt_storyspec` | **正式变更**（`change` 接口）才 +1；普通编辑原地改写最后一版快照 | 无 |
| 文档 `doc` | `zt_doccontent` | **正文变了**才 +1；标题/关键词变了不动 | 有 `version=0` 的草稿位 |
| 用例 `case` | `zt_casespec` + `zt_casestep` | **只有「步骤」变了**才 +1 | **状态被打回 `wait`（待评审）** |

最后一条最容易踩：改标题、改前置条件、改优先级、改状态**都不升版本**，
因为步骤才是用例的实质。而且一旦步骤变了，用例会被打回「待评审」——
「改了实质内容就必须重新评审」这条规则，禅道是写在代码里而不是配置里的。

**步骤是本项目第一个真正的「一对多子结构」**：一个 `(case, version)` 下 N 条步骤，
`type=group` 的步骤组只有描述没有预期结果，`parent` 指向所属的组（最多三级）。
前端看到的编号 `1. / 1.1 / 1.1.1` **不落库**，是读的时候按禅道 `processSteps`
的算法现算的：维护一个三级计数器，遇到更深的层级就进位、遇到更浅的层级就清零。
移植这段算法时我漏掉了循环末尾的状态更新，结果同级步骤编号全部重复（见第 29 条坑）——
**「算法抄对了、状态更新漏了」是移植里最阴的一类 bug**。

**步骤的提交约定**：`parent` 是「本次提交数组里父步骤组的 **0 基下标**」，
不是数据库 id；顶层步骤不传（或传负数）。这样做是因为新增用例时步骤还没有 id，
天然只能用下标表达层级；编辑时前端把当前形态整体提交回来，不用关心哪些行是新的。
服务端会先校验（父必须是排在它前面的 group、组不能有预期结果、最多三级），
再把下标翻译成真实行 id。**注意 `0` 是合法下标**，别拿它当「顶层」用 —— 这个歧义
我在实现时踩过一次，最后靠「顶层传 -1」把语义钉死。

**`storyVersion` 冻结与「待确认」**：用例关联需求时记下需求当时的版本。
需求后来升版，用例不会自动跟着变，而是进入「待确认」：
列表里 `needConfirm=true` 的过滤条件是
`zt_story.version > zt_case.storyVersion AND zt_story.status = 'active'`，
要人点一下「确认需求」才把 `storyVersion` 追平。这与 `zt_projectstory`
冻结关联时的需求版本（3.13）是同一个思路，只是动作不同（那边是标记，这边是确认）。

### 3.17 文档：第二个「追加式版本链」，多了一个草稿位

文档看着是「写文章」，但禅道把它做成了**三层结构 + 一棵树 + 一条版本链**：

```
zt_doclib      文档库     product / project / execution / custom
   └ zt_doc    文档        type=chapter 是章节（目录节点）｜其余才是文档
        └ zt_doccontent   版本内容   (doc, version) 唯一，version=0 是草稿
```

**一根筋的复用**：`zt_doc` 一张表同时装章节和文档，靠 `type='chapter'` 区分 ——
和 `zt_project` 同时装项目与执行（3.7）是同一类设计。所以任何「文档列表」都必须
记得排除章节，否则用户会在列表里看到一堆点不开的空行。

**章节树和 `zt_module` 是一套规则**：`parent` + `path` + `grade`，`path` 用逗号包起来
且**包含自己**（一级 `,1,`、二级 `,1,2,`），于是「取子孙」就是一次前缀匹配，
不需要递归。**一个禅道原版的坑**：它的 `zt_doc.path` 拼接会多出一个逗号变成 `,1,,2,`
（`$path = ',' . trim($parentDoc->path, ',') . ',' . $path` 里 `$path` 本身已经带了逗号），
本实现统一成单逗号格式，否则前缀匹配会失效 —— 这就是第 14 条坑的同类问题。

**版本链的第二个实现**：`zt_doccontent` 和 `zt_storyspec`（3.2）几乎一样，
`(doc, version)` 唯一、编辑内容追加新快照、只改基础信息版本不动。
**唯一的差异是多了一个 `version=0` 的草稿位**：

| 操作 | 禅道 behavior | 本实现 |
|---|---|---|
| 新建草稿 | `status=draft`、`version=0`，正文同时写 `zt_doc.draft` | 同 |
| 反复保存草稿 | 每次都在改 **version=0 那一行**，不产生版本 | 同（测试里断言「始终只有 1 条 v0」） |
| 发布草稿 | 把 v0 那一行**改版本号为 1** | 同（不是「插一行 v1 再删草稿行」） |
| 已发布后编辑正文 | `version+1` 追加新快照 | 同 |
| 已发布后只改关键词 | 版本不变 | 同 |

写 `zt_doccontent` 时，「这次到底该 UPDATE 还是 INSERT」是**本模块最容易写错的一处**，
判断顺序必须是：① 有没有 v0 草稿行（有 → 改它，草稿转正）→ ② 目标版本行在不在
（在 → UPDATE，避免撞唯一键）→ ③ 都没有才 INSERT。少任何一步都会出问题：
漏 ①，发布后草稿行永远留在表里；漏 ②，重复保存同一版本直接撞
`UNIQUE(doc, version)`（README 第 4 条坑在需求快照上已经踩过一次）。

**为什么库的删除要额外加一道保护**：禅道删库时不检查库内文档，文档会变成孤儿
（还挂在已删的库 id 上）。本实现显式拒绝（`1020015006`），和「产品下还有需求不能删除」
保持同一套策略；同时**主库（跟随产品/项目/执行的库）一律不许删**，这与禅道一致。

**浏览计数沿用附件下载计数的写法**：`views = views + 1` 交给数据库自增，
而不是「查出来 +1 再写回」；并且只在 `status=normal` 时计数（草稿的浏览没有意义）。

### 3.16 附件：唯一「存储不迁移、语义照搬」的模块

附件看着小，但它决定了**所有业务对象的详情页**能不能用 —— 需求、任务、Bug、文档
共用同一套上传/下载，所以它在迁移清单里是 P0 的公共能力。禅道这边是 `zt_file`：

| 禅道字段 | 含义 | 本实现 |
|---|---|---|
| `objectType` / `objectID` | 挂在哪个对象上（story/task/bug/doc…） | 照搬，前端的 `AttachmentPanel` 只要传这两个参数就能用 |
| `gid` | **临时分组**：新建对象时对象 id 还不存在，先给这批上传一个 gid，保存后再一次性绑定 | 照搬，`/zentao/file/bind-by-gid` 对应禅道的 `file->updateObjectID` |
| `pathname` | 相对存储路径 | 改存 **yudao 文件服务返回的可访问 URL** |
| `downloads` | 下载计数 | 照搬，下载时 `UPDATE ... downloads = downloads + 1`（数据库自增，避免并发覆盖） |
| `title` / `extension` / `size` | 展示信息 | 照搬；重命名时**扩展名跟着文件名一起变** |

**为什么存储不迁移**：禅道自己管 `upload/` 目录和相对路径。如果照搬，系统里就会同时存在
两套文件后端（yudao 的 `infra_file_config` 与禅道的目录约定），还要自己处理备份、鉴权、S3 切换。
本实现把**字节交给 yudao 的文件服务**（`FileApi.createFile`，本地/DB/S3 由配置决定），
**元数据留在 `zt_file`** —— 于是「一套系统只有一个存储后端」，
同时 `(objectType, objectID, gid, downloads)` 这些禅道语义一条不丢，历史数据导入时能逐列对照。
`pathname` 里存的是 URL，所以详情页拿到就能直接下载，不需要再做一次路径拼接。

**两个实现细节**：

1. **下载计数用原生 UPDATE**：`downloads = downloads + 1` 在数据库里做，
   而不是「查出来 → +1 → updateById」（并发下载会互相覆盖，而且多一次查询）。
2. **类名全部带 `Zentao` 前缀**：`ZentaoFileDO` / `ZentaoFileMapper` /
   `ZentaoFileService(Impl)` / `ZentaoFileController`。这不是风格问题 ——
   infra 模块已经有同名的 `FileDO`/`FileMapper`/…，bean 名与 MyBatis 别名都是**全局命名空间**，
   不改名直接启动失败（见第四节第 25 条）。

**上传上限有两道闸**：业务侧 50MB（`ZentaoFileServiceImpl.MAX_SIZE`），
框架侧 `spring.servlet.multipart.max-file-size` 抬到 64MB 留余量，
超过框架上限由 `ZentaoFileExceptionHandler` 兜底成同一句中文提示（见第四节第 26 条）。

### 3.15 组织与权限：唯一「有意不迁移」的核心对象

禅道的组织权限是四张表：`zt_user` / `zt_dept` / `zt_group` / `zt_grouppriv`。
**本项目不迁移它们**，而是映射到 yudao 的 RBAC：

| 禅道 | yudao | 说明 |
|---|---|---|
| `zt_user` | `system_users` | 一个账号一个部门，语义一致 |
| `zt_dept` | `system_dept` | 禅道带 `path`/`grade` 冗余，yudao 是邻接表 |
| `zt_group` | `system_role` | 权限包 ↔ 角色 |
| `zt_grouppriv(group, module, method)` | `system_role_menu` + `system_menu.permission` | **权限粒度完全对得上** |

**为什么这是唯一「不该迁移」的地方**：yudao 的 RBAC 已经实现了
「用户-角色-菜单-权限 + 数据范围（data_scope）」，再搬一套禅道权限包，
就会得到两套并行的权限系统 —— 之后每次调整权限都要双写，且必然漂移。这是**负收益**。

**为什么映射是可行的**：yudao 的权限串形如 `zentao:story:create`，
正好是「前缀:模块:方法」三段，把它拆开就是禅道的
`zt_grouppriv(group, module, method)` 两级结构。所以本模块做的是
**把 yudao 的权限翻译回禅道的视角**（`/zentao/organization/role-permissions`），
让迁移评审能逐条对照「这个权限包对应新系统的哪些菜单权限」。

**踩到的坑（见第四节第 24 条）**：yudao 的 `super_admin` 是**硬编码放行**
（`PermissionServiceImpl` 里直接 `return true`），不会往 `system_role_menu` 写记录。
所以按表查超管的权限永远是 0 条 —— 如果照实展示，评审时会得出
「超管在新系统里什么都不能做」的荒谬结论。本实现显式展开了（= 全部禅道权限），
并把这个差异写在接口返回与页面上。

**迁移注意**：禅道密码是 `md5(md5(pass) + salt)`，yudao 是 BCrypt，
**老密码无法直接复用**，只能重置或改成「首次登录时升级哈希」。

### 3.14 阶段：模板与实例是两张表

禅道把瀑布流程拆成两层，**这是最容易看错的地方**：

| 层 | 表 | 含义 |
|---|---|---|
| 模板 | `zt_stage` | 一套流程的阶段定义：`workflowGroup=1` 下有 需求20% / 设计20% / 开发30% / 测试20% / 发布10%。多个项目共用 |
| 实例 | `zt_project`（`type='stage'`） | 某个项目按模板生成的实际阶段，各有日期、状态、工时 |

项目用 `zt_project.workflowGroup` 记住自己用的是哪套模板；阶段实例和执行**共用一张表**，
所以阶段的状态流转（wait → doing → closed）可以直接复用执行模块的服务，不需要另写一套。

**占比校验是本模块的核心业务规则**（禅道 `stage::create/update`）：

```
新增：当前合计 + percent ≤ 100
修改：当前合计 - 本条旧值 + percent ≤ 100
批量：base 在循环里累加，不能每次都去查库
```

最后一条是踩出来的：如果 `checkPercentNotOver()` 内部再查一次数据库合计，
批量创建时会把「已累计的部分」重复计入，报出 `当前 80+30>100` 这种假错误
（实际只用了 60%）。**校验函数要接收「已占用基数」而不是自己去查库。**

### 3.13 项目需求范围：把「需求版本」冻结在关联那一刻

`zt_projectproduct` + `zt_projectstory` 回答一个问题：**这个项目/执行要做哪些产品的哪些需求？**

**1）`zt_projectstory.version` 不是当前版本，而是关联时的版本**

```sql
`project` bigint, `story` bigint, `version` smallint, `order` int
```

需求体系是「头部 + 追加式快照」（见 3.2），`zt_story.version` 会随着正式变更不断变大。
项目在关联需求的那一刻把版本记下来，含义是「我按这一版排期」：
需求之后变更到 v3，项目仍然按 v2 的内容执行，列表里标出「版本已变更」提醒双方对齐。
这就是**计划冻结**的落点 —— 如果只存 story id，需求的任何变更都会悄悄改掉已排期的工作量。

**2）两张表的删除语义完全不同**

| 表 | 删除方式 | 说明 |
|---|---|---|
| `zt_projectstory` / `zt_projectproduct` | **物理删除** | 关系数据，禅道原表连 `deleted` 列都没有，不继承 BaseDO |
| 业务表（story/project/…） | 逻辑删除 | 带 `@TableLogic` |

**3）几处容易漏的保护规则**

- 纳入范围时，`draft / reviewing / closed` 状态的需求**静默跳过**（不报错），接口返回实际关联的 id
- 从**项目**移除需求时，若它的**子执行**已关联该需求 → 拒绝，否则子执行会出现「需求不在项目范围内」
- 移除后把剩余关系 `order` 重新编号为 `1..n`，否则排序会越来越稀疏
- 解除「项目↔产品」关联前，要检查该产品下还有没有需求在范围内

### 3.12 发布：全局唯一的版本号、影子构建、三份清单

`zt_release` 是交付链的最后一段：**需求 → 构建 → 发布**。三个设计值得单独说。

**1）版本号全局唯一**

禅道对发布的唯一性校验是 {@code check('name','unique', "system = X AND deleted = 0")}。
`system` 是「产品系统」概念，默认 0 —— 也就是说**没启用系统时，所有发布共用一个 system=0，
版本号实际上跨产品全局唯一**：产品 A 发布了 `V1.0`，产品 B 就不能再用 `V1.0`。
本实现照做（`selectByName` 不带 product 条件），并在错误码里写明了这一点。

**2）创建发布自动生成「影子构建」**

```php
if($release->name) { /* 用发布的 name/product/branch/date/builds 插一条 zt_build */ }
$release->shadow = $lastBuildID;   // 回填 release.shadow
```

为什么？因为「关联需求/Bug」这套能力是挂在**构建**上的（`build.stories` / `build.bugs`），
发布要记录交付内容，最省事的办法就是让每个发布都自带一个构建实体。
编辑发布时名称/构建/日期会同步到这个影子构建（`syncShadowBuild`）。

**3）三份清单 + 前端渲染时合并**

| 字段 | 含义 |
|---|---|
| `stories` | 本次完成的需求 |
| `bugs` | 本次解决的 Bug |
| `leftBugs` | **遗留的 Bug**（已知但带着上线的） |

`bugs` 与 `leftBugs` 靠 `linkBug` 的 `type` 参数区分，是两个独立清单。
另外发布还有 `releases` 字段（包含子发布），删除前要检查「是否被别的发布包含」。

**4）第三种多值编码**

同一个系统里，禅道对「多值字段」用了三种写法：

| 表.列 | 写法 | 说明 |
|---|---|---|
| `zt_story.plan` | `1,2` | 纯逗号列表 |
| `zt_productplan.branch` | `1,2` | 同上 |
| `zt_build.stories` | `1,2` | 同上 |
| **`zt_release.build/branch/project`** | **`,1,2,`** | **前后都带逗号**（拼接时省一次判断） |

三种写法用 `FIND_IN_SET` 都能正确处理，但**拼接与去重时必须按各自格式来**，
否则 `wrap/unwrap` 一错，`FIND_IN_SET` 就会漏数据。

### 3.11 构建：集成构建与「关联即解决」

`zt_build` 是「一次打包」，它把三件事串起来：**需求 → 构建 → 发布**，同时是
**「Bug 在哪个版本修好」的锚点**（`zt_bug.resolvedBuild` 存的就是构建编号）。

**1）集成构建**

```
builds    varchar(255)  -- 子构建编号的逗号列表
execution bigint        -- 集成构建固定为 0
branch    varchar(255)  -- 由子构建的分支并集算出来
```

读取时（禅道 `joinChildBuilds()`）要把所有子构建的 `stories`/`bugs` **合并进集成构建**，
所以「集成构建完成了哪些需求」不需要额外存一份。
被别的构建的 `builds` 引用、或被发布的 `build` 引用的构建是「子构建」（`isChild`），
禅道不允许这类构建改所属产品/执行/包含构建 —— 否则父级的合并结果会错乱。

**2）关联 Bug 会顺手解决它**

`build::linkBug()` 不是单纯的关联：对**还没解决/关闭**的 Bug，它会直接写成

```
status = resolved      resolution = fixed
resolvedBuild = 构建编号   resolvedBy = 传入的解决者（缺省当前登录人）
assignedTo = 创建人       confirmed = 1
```

这条副作用是「测试提 Bug → 开发修复 → 打包 → 关闭 Bug」流程的自动化关键：
把 Bug 勾进构建，等于宣告「这个包修好了这些问题」。
注意**解除关联不会回退解决状态**（禅道如此），否则历史就乱了。

**3）必填字段是条件性的**

禅道 `create->requiredFields = 'execution,product,name,builder,date'`，但会按情况删字段：
集成构建去掉 `execution`，项目未关联产品（`project.hasProduct = 0`）时去掉 `product`。
构建名则在 `(product, branch)` 维度唯一。

### 3.10 计划：多分支、待定哨兵、父子聚合

`zt_productplan` 是产品维度的排期单元，需求用 `zt_story.plan` 挂上来，发布再引用计划。
它有三个必须照着抄的设计：

**1）`branch` 是逗号列表（多值）**

```sql
`branch` varchar(255) NOT NULL DEFAULT '0'   -- '0' 主干 / '1,2' 同时覆盖两个平台
```

一个计划可以覆盖多个分支，所以**过滤要写成 `FIND_IN_SET(1, branch)`**，写成 `branch = 1`
只能查到「恰好只覆盖一个分支」的计划。多分支产品的计划**必须选分支**（禅道 `create()` 里的校验）。

**2）「待定」是日期哨兵，不是状态**

```php
$config->productplan->future = '2030-01-01';
```

没定日期的计划，`begin` 与 `end` 都写 `2030-01-01`，状态仍是 `wait`。
好处是 SQL 里日期比较永远成立（不用处理 NULL），代价是**展示层必须翻译成「待定」**，
否则用户会看到一个 2030 年的计划。

**3）`parent` 三态与父计划状态聚合**

| parent | 含义 | 约束 |
|---|---|---|
| `0` | 独立计划 | 可自由删除 |
| `> 0` | 子计划 | 日期必须落在父计划范围内 |
| `-1` | **该计划有子计划** | **不能删除**（`$lang->productplan->cannotDeleteParent`） |

父计划的**状态由子计划聚合推导**：子计划全关闭 → 父关闭；子计划全部完成（无 wait/doing）→ 父完成；
只要有子计划在 wait/doing → 父进行中。这套聚合写在 `refreshParentStatus()` 里，
每次子计划状态流转后回写父计划。

**4）需求关联有两套语义**

```php
// module/productplan/model.php linkStory()
$newPlanID = $story->type == 'story' ? $planID : trim($story->plan, ',') . ',' . $planID;
```

- `type='story'`（研发需求）：**独占**一个计划，挂到新计划会从旧计划移走
- `type='requirement' / 'epic'`：**累加**成逗号列表，可以同时挂在多个计划上

同一个 `zt_story.plan` 字段，两种类型两种语义 —— 关联、移除、统计都要按类型分支处理。

### 3.9 分支/平台：一套数据，两种叫法

`zt_branch` 是**产品维度**的分支表。产品类型决定它叫什么：

```php
$lang->product->branchName['normal']   = '';       // 普通产品，没有分支
$lang->product->branchName['branch']   = '分支';   // 多分支产品
$lang->product->branchName['platform'] = '平台';   // 多平台产品
```

禅道在报错文案、按钮标题、表头里全部用 `str_replace('@branch@', ...)` 切换，
所以后端把「类型 → 文案」抽成了 `ProductTypeEnum.branchNameOf()`，
接口返回 `branchLabel` 字段，前端直接用，不再判断类型。

**id = 0 是虚拟主干**，这一行**不在表里**：`getById(0)` 直接返回「主干」，
需求/缺陷/模块的 `branch = 0` 就表示挂在主干上。主干不能关闭、不能删除，只能被设为默认。
这个「虚拟行」给接口带来一个容易做错的点：**分页时要把它算进 total，但只能插在第一页**
（见第四节第 15 条）。

---

## 四、踩过的坑（迁移时每条都会重现）

| # | 现象 | 根因 | 解法 |
|---|---|---|---|
| 1 | `Unknown column 'opened_by'` | MyBatis-Plus 驼峰转下划线，与禅道驼峰列名冲突 | 驼峰字段加 `@TableField("openedBy")` |
| 2 | `Unknown column 'tenant_id'` | yudao 默认开多租户，给所有表自动加租户条件 | 每张禅道表都要加进 `yudao.tenant.ignore-tables` |
| 3 | 原生 `@Delete` 也被改写 | 租户拦截器会重写自定义 SQL，不只是 MP 生成的 SQL | 同上，必须加 ignore |
| 4 | **逻辑删除会撞唯一键** | `BaseDO.deleted` 带 `@TableLogic`，`zt_storyspec` / `zt_storyreview` 有 `(story, version)` 唯一键。revert 回滚后再变更到同一版本号会冲突 | revert 时用原生 `@Delete` 做**物理删除**，与禅道一致 |
| 5 | 拿不到「账号」 | 禅道人员引用存账号，但 `LoginUser` 上下文只有昵称，`AdminUserRespDTO` 也没 username | 给 DTO 补 `username` 字段（BeanUtils 自动映射），跨模块调 `AdminUserApi` |
| 6 | 前端请求全失败 | 前端 `.env` 开了 API 加密，后端 `api-encrypt.enable=false` | `.env.local` 覆盖为 `false` |
| 7 | 中文变乱码 | 手动 `docker exec mysql` 未带字符集参数，客户端按 latin1 处理导致双重编码 | 一律加 `--default-character-set=utf8mb4` |
| 8 | 变更日志里「旧值」是空的 | `spec`/`verify` 是 `@TableField(exist=false)` 瞬态字段，从主表读出来的旧对象这两个字段是 null，diff 就变成「空 -> 新值」 | 做 diff 的旧对象必须用 `getStoryByVersion(id, null)` 取（会叠加快照内容），不能用 `validateStoryExists(id)` |
| 9 | `read` / `old` / `new` / `date` 是 MySQL 关键字 | `zt_action.read`、`zt_history.old/new` 直接当列名会报语法错 | DO 字段重命名（`readFlag` / `new_`）+ `@TableField("`read`")` 显式加反引号 |
| 10 | `left` 也是 MySQL 保留字 | 建任务表时只记得给 `desc` 加反引号，漏了 `left`（`LEFT` 是字符串函数），插入时报 `syntax error near 'left, version, status...'` | `@TableField("`left`")`。**建议建表时把 `desc`/`left`/`read`/`old`/`new`/`date`/`status` 一并当关键字处理** |

| 11 | `zt_project` 查询全部 500，报 `JSqlParser ParseException` | 用 JSqlParser 4.5 直接跑 SQL 二分定位到三个列名：**`output`**（T-SQL 的 OUTPUT 子句）、**`begin`** / **`end`**（SQL 块关键字）。MySQL 能执行，但 MyBatis-Plus 的拦截器用 JSqlParser 解析时会失败 | 三个字段加 `@TableField("\`xxx\`")`。**注意 `order` 加反引号是可以正常解析的**，排查时别误伤 |
| 12 | **项目列表里冒出了「执行」** | 执行与项目共用 `zt_project`，`selectPage` / `selectSimpleList` 忘了加 `type='project'`，于是迭代/阶段全部混进了项目列表和项目下拉框 | 所有项目查询统一加 `type` 条件；再补一层「项目 id 与执行 id 互相拦截」的校验。**共用表要把 type 过滤当成强制项** |
| 13 | 创建执行报「项目模型不能为空」 | 一开始让执行复用了 `ProjectSaveReqVO`，而项目 VO 上 `model` 是 `@NotBlank`；但执行没有自己的模型（从所属项目继承），调用方被迫传一个无意义的值 | 拆出独立的 `ExecutionSaveReqVO`：`project` 必填、`type` 必填、`model` 可选（缺省继承父项目）。**复用 VO 前先确认校验规则也一致** |
| 58 | **改完代码提交给服务器编译，编译「成功」但行为没变**（3.4 秒就 BUILD SUCCESS，jar 的时间戳也没更新） | `tar` 默认**保留源文件的 mtime**，而 Mac 上那个文件是 10:38 改的（CST）、服务器上的 `.class` 是 02:44 编的（UTC）—— 去掉时区后源码反而「比 class 还旧」，Maven 的增量编译就认定「没变」，直接跳过；`package` 照旧把旧 class 打进 jar。**编译成功 ≠ 编译了新代码**，而且部署脚本会老老实实换上一个内容没变的 jar、重启、看起来一切正常 | 同步源码时加 `-m`（`tar czmf - … | ssh … 'tar xzmf - …'`，等价于 `--touch`：落地文件用当前时间），或者同步完 `find … -newer … ` 之类的方式强刷 mtime。判据很简单：**编译耗时**——只改一个模块的增量编译不该低于 ~20 秒，3 秒「成功」基本就是没编译；`ls -l` 看 jar 时间戳是否更新是第二重保险。这类问题的症状是「改了代码、重启了服务、行为一模一样」，最容易被当成「改错了地方」 |
| 57 | **同一批界面检查，白天全绿、凌晨跑就红两项**（`my-workspace` 的「今天那一格有今天新建的待办」、`burn` 的「快照表里有今天那一行」） | 两个脚本都用 `new Date().toISOString().slice(0, 10)` 算「今天」—— **`toISOString()` 是 UTC**，在 UTC+8 的凌晨 0~8 点它比本地日期**少一天**；而页面上的日期来自服务端（本地时间）。于是断言去找「09-17」那一格/那一行，页面给的是「09-18」，**假红**；产品与数据都没问题 | 改成用本地时区拼日期（`getFullYear()` / `getMonth()+1` / `getDate()` + 补零），改完两个脚本各连跑两次全绿（21 项 / 12 项）。教训：**`toISOString()` 不是「取当天日期」的通用写法** —— 凡是「拿今天去对账」的脚本都要显式声明时区；这类 flake 的症状是「只在凌晨跑才红」，最容易被误归因成「服务端数据错了」 |
| 56 | **同一个脚本昨天全绿、今天红一项**（`test-burn-module.sh` 的「首日值 实际=」，值是空的） | 断言写的是「`labels` 里**等于某个日历日**的那个点的值」，而燃尽图的 labels 默认 `noweekend` —— **跳过周末与节假日**（`HolidayService`）。脚本里的日期是「今天 − 20 天」算出来的：09-17 跑时它是**周五**（在 labels 里，绿），09-18 跑时它是**周六**（不在 labels 里），`[v for l,v in zip(...) if l==那天][0]` 直接 IndexError → 断言拿到空串。**不是产品 bug、也不是数据问题，是断言依赖了「某个相对日期恰好落在工作日」** | 改成按**位置**取：`labels` 里第一个 `>= 目标日期` 的下标（快照落在周末时，值会被后一个工作日自然承接），首日值直接取 `labels[0]` / `burnLine[0]`；改完连跑两次 32 项全绿。教训：**凡「日期 → 坐标」的断言，先想清楚这条坐标轴跳过了什么**（周末/节假日/无数据的日期），别用「日历日相等」当定位条件 —— 这类 flake 的典型症状就是「代码没动、昨天绿今天红、而且只红一项」 |
| 55 | **度量接口返回的值正好是库里的 2 倍**（52/26、14.00/7.00、4/2），而口径 SQL 全部正确、单独拿到 MySQL 里跑就是真值 | 不是 JOIN 行放大、也没有重复数据。真凶是 **`zt_metriclib` 里 nodate 快照按天累积**（库里同时有 09-14、09-17 两份），而读路径 `MetricServiceImpl.getMetricData → MetricLibMapper.selectListByCode` **没写日期条件**，把历史快照一起取回来按维度求和 —— 「正好 2 倍」只是因为库里恰好存了 2 天，多留一天就变 3 倍。同一个 Mapper 的 `selectPageByCode` 其实已经写了 `date >= today`（禅道 `module/metric/tao.php#fetchMetricRecordsWithOption` 第 390 行也是这么做的），只有这条读路径漏了 | 在读路径补 nodate 过滤（`date >= 今天`）；**周期型（year/month/week/day）的语义一个字都不改** —— 禅道 `clearOutDatedRecords` 只清周期型，nodate 的历史快照是故意留的，读的时候才过滤。教训有二：① **「整数倍关系」不等于「重复数据」**，先问一句「这个值是不是把多个时间片加起来了」；② 同一个 Mapper 里两个方法的日期条件不一致，就是这种静默翻倍的温床 —— 加新读路径时先把同类方法搜一遍。附带同类修法：`metric/get`、`metric/summary` 的 `dataCount` 也要按同一口径只统计当前有效快照 |
| 26 | **上传超过 16MB 的附件直接 500「系统异常」，而代码里那行「不能超过 50MB」的校验永远不执行** | 上传有**两道**限制，而且框架那道更靠前：`spring.servlet.multipart.max-file-size` 配的是 16MB，超过时 **DispatcherServlet 在进入 Controller 之前**的 `checkMultipart` 就抛 `MaxUploadSizeExceededException`，业务里的 50MB 判断根本没机会跑；multipart 异常没人接，于是变成 500 | 把框架上限抬到 64MB（留出余量）让 50MB 以内/略超的文件走业务校验，返回可读的中文错误；再加 `ZentaoFileExceptionHandler`（`@RestControllerAdvice(basePackages=...)` **只圈禅道模块**）把 `MaxUploadSizeExceededException` / `MultipartException` 翻译成同一套提示。**框架级限制总是先于业务校验生效**，两个数字要么对齐、要么显式兜底；`@ExceptionHandler` 对 `checkMultipart` 抛出的异常依然有效（此时 HandlerMethod 已经确定了） |
| 25 | **启动直接失败**：先报 `ConflictingBeanDefinitionException: bean name 'fileController'`，改完类名又报 `The alias 'FileDO' is already mapped`，再改完又报 **`The bean 'fileMapper' could not be injected because it is a JDK dynamic proxy`** | 附件模块在 infra 里叫 `FileController`/`FileService`/`FileMapper`/`FileDO`，我照禅道的模块名（file）原样命名，于是三处撞车：①组件扫描按**短类名首字母小写**命名 bean，两个 `fileController` 直接冲突；②`mybatis-plus.type-aliases-package` 是按 `module.*.dal.dataobject` 通配注册别名的，两个 **`FileDO` 别名冲突**，SqlSessionFactory 建不起来；③`@Resource` 是**先按字段名**注入的，字段还叫 `fileMapper` 就会命中 infra 的 mapper，然后「期望 ZentaoFileMapper，拿到 JDK 动态代理」 | 全部加前缀：`ZentaoFileController` / `ZentaoFileService` / `ZentaoFileServiceImpl` / `ZentaoFileMapper` / `ZentaoFileDO`，**注入字段名也一起改**（`zentaoFileMapper`）。顺手记一条：**别用 `mvn install` 的增量编译验证「删掉了旧类」** —— `target/classes` 里上一次编译的 `.class` 还在，jar 里会同时存在新旧两个类，冲突照旧报，必须 `rm -rf target/classes` 或 `mvn clean`。教训：**新模块命名前先全局搜一遍同名类**，`file`/`notice`/`area`/`config` 这类通用名几乎必然和框架模块重名 |
| 14 | **删除模块后子模块还在、需求没改挂，全程不报错** | `zt_module.path` **已经包含自己的 id**（一级模块 `,9,`）。我按直觉写成 `path + id + ','` = `,9,9,` 去匹配子孙，结果一条都匹配不到 —— `selectSelfAndDescendants` 只返回自己，改挂 SQL 影响 0 行。**这类 bug 不会报错，只会静静地什么都不做** | 子孙前缀就用模块自己的 `path`；成环校验同理。教训：**path/树这类字段，先写一条 SQL 把真实值打印出来再写代码** |
| 24 | **组织权限视图里，超级管理员的权限是 0 条** | yudao 的 `super_admin` 是**硬编码放行**：`PermissionServiceImpl.hasAnyPermissions()` 直接返回 true，根本不查 `system_role_menu`。我按「角色 → 菜单」查权限，超管自然是 0 条 —— 数据没错，结论完全错 | 视图层对超管做**等价展开**（= 系统里全部禅道权限），并在接口文档与页面上写明「超管不走权限表」。**任何「把框架的权限数据反查出来展示」的工具，都要先处理这类硬编码旁路** |
| 23 | **演示数据「生成错了」：阶段实例的 project/parent/status 全是旧值** | 阶段实例演示数据用了 `id=90010+`，撞上了历史测试执行留下的**软删除行**。`ON DUPLICATE KEY UPDATE` 只更新写出来的列，`project`/`parent`/`status` 仍是旧行的值，看起来像"生成逻辑写错了" | 演示数据用远离测试区间的高位 id（本次改成 92001+），并在 `ON DUPLICATE KEY UPDATE` 里把关键列全部列出。**同一教训在 build/release 的演示数据上已经踩过一次**（见 `deploy/sql/18-zt_build.sql` 注释）—— 结论是"演示数据的 id 要预留专用段" |
| 22 | **「关联需求」接口返回成功，但一条都没关联上** | 禅道对状态不允许的需求（`draft`/`reviewing`/`closed`）是**静默跳过**而不是报错，接口照样返回 200。我的测试用默认的 `draft` 需求去关联，结果全部被跳过，断言里拿到的是**空数组**，看起来像"接口没生效" | 关联类接口必须返回**实际生效的 id 列表**，调用方也要拿它对账（本次实现里 `linkStory` 返回 `List<Long>` 就是这个用途）。**"静默跳过"是业务规则，不是 bug，但它要求调用方别把 200 当成"全都成功"** |
| 21 | **「发布不能晚于今天」的校验形同虚设：传 `2099-01-01` 也能建成功** | yudao 给 `LocalDateTime` 注册的反序列化器是**按 Long 时间戳解析**（`TimestampLocalDateTimeDeserializer`），它不认 `@JsonFormat`（只有序列化器认）。于是 JSON 里的字符串 `"2099-01-01 10:00:00"` 被 `getValueAsLong()` 静默解析成 **1970-01-01**（返回 0），既不报错、也不在未来，校验自然不触发 | 字段上加 `@JsonDeserialize(using = …LocalDateTimeDeserializer.class)`（**Jackson 3 的包名是 `tools.jackson.databind`**，写 Jackson 2 的 `com.fasterxml.jackson.databind` 会被运行时忽略）+ `@JsonFormat(pattern=…)`。**凡是「时间戳 or 可读时间字符串」这种双格式入口，一定要用真实值验证一次边界**，别只看接口能通 |
| 34 | **任务列表整页 500：`{"code":1020000000,"msg":"需求不存在"}`** | 任务详情/列表要补「需求标题 / 需求是否已变更」，我调的是 `storyService.getStory(id)` —— 而这个方法内部是 `validateStoryExists`，**取不到就抛异常**。于是只要有一条任务挂着一个已删除的需求（悬空引用），**整个任务分页接口就挂了**，页面表现为「表头在、一行都没有 + 一条 pageerror」 | 容忍取不到需求：捕获异常后跳过需求信息、`storyChanged=false`。**教训：给列表做「关联信息补全」时必须容忍脏引用** —— 一条脏数据不该让整页打不开；排查线索是「表头渲染了但 0 行 + pageerror」，说明接口返回了错误码而不是空列表。顺带清掉了库里的悬空任务（`LEFT JOIN zt_story ... WHERE s.id IS NULL`） |
| 46 | **接口测试报 `no String-argument constructor ... (\'column\')`，服务端收到的 body 是半截字符串** | 断言写成了 `check "..." N "$(curl ... -d "{\"a\":$X}")"` —— **macOS 的 bash 3.2** 在 `"$( )"` 里遇到嵌套的 `\"` 会把它当成字符串结束，于是 `-d` 只拿到 JSON 的一小段（实测收到的是裸词 `column`/`name`/`begin`）。更坑的是这种写法在别的脚本里"看起来一直是对的"，因为只有 body 里带转义引号才会触发 | 统一改成**两步走**：`R=$(curl ...)` 先把响应取到变量，再 `check "..." N "$R" "关键字"`。同时把这条写进脚本头部注释。**bash 3.2 上不要相信「嵌套引号 + $( ) + JSON」**，多字节变量后面紧跟中文也要写成 `${VAR}`（同一类问题的另一个面） |
| 51 | **表头在、`共 N 条` 在、tbody 是空的** —— 接口 200、数据也对，就是一行都不渲染 | 提交时间那一列写了 `(row.time \|\| '').replace('T', ' ')`，但 yudao 的 Jackson 把 `LocalDateTime` 序列化成**数字时间戳**，`number.replace` 直接抛错 → 该单元格渲染失败，**整张表的行都不出来**（Element Plus 的表头与分页是另一套渲染，所以看起来"半好"） | 用项目里的 `formatDate`（`@/utils/formatTime`）统一格式化时间。教训：**「表头渲染了但 0 行」优先怀疑单元格模板里的类型错误**，排查第一步是看 `pageerror`（这张表的报错不在 console 里，只在 `pageerror`） |
| 50 | **「按产品过滤」时汇总卡片还是全公司的数**（产品明细表只剩一行，卡片却是全部） | 汇总卡片是从「按产品的统计结果」里**无条件累加全部行**算出来的，忘了跟着 `product` 参数过滤 —— 接口不报错、数字也「有值」，只是口径不对。**这类「筛选只作用于一半输出」的 bug 在仪表盘里特别常见**：明细跟着筛选走了、汇总没走（或者反过来） | 汇总时判断 `product != null && !product.equals(行.product)` 就 `continue`；明细列表仍返回全量（页面要横向对比）。教训：**一个接口里同时有「明细 + 汇总」两种输出时，必须显式确认两者都吃到了同一套筛选条件** |
| 49 | **自动采样静默失效：接口能通、断言对不上**（120 天的燃尽图本该压到 31 个点，实际返回 121 个） | `dateList()` 里写成「先按 step 生成日期列表，再在外面算 step」—— 传进去的 step 恒为 0，函数直接 `return` 全部日期，自动采样那段代码永远走不到。**没有报错、没有异常，只是结果不对** | 把间隔提到生成列表**之前**算：`int step = interval > 0 ? interval : weekdays(...).size() / MAX_BURN_DAY;`。教训：**「先算参数再用参数」的顺序错误，在只读聚合里是最难发现的一类 bug** —— 它不会抛异常，只会让输出悄悄退化成另一种口径；所以这类功能一定要有一条「数量级」断言（点数 ≤ 40），而不是只看接口 200 |
| 48 | **报表上线后年度数据里需求/任务全是 0，看着像报表写错了** | 报表按 `zt_action` 统计（禅道也是），而本项目的演示数据是直接把对象 `REPLACE` 进业务表的、**没有配套动作行**；禅道 `db/demo.sql` 是给每个对象都配了动作的 | 在 `44-zt_report.sql` 里给本年「还活着」的需求/任务/缺陷补创建动作：日期取对象自己的 `openedDate`（保证落在正确年月）、`NOT EXISTS` 判断保证重复执行安全。**教训：任何「按动作统计」的功能，都要先确认数据源真的有动作** |
| 47 | **新模块起名撞了三次，启动报三种不同的错**：先是 `ConflictingBeanDefinitionException: bean name 'reportController'`，改完类名又报 `The bean 'reportServiceImpl' could not be injected because it is a JDK dynamic proxy`，再改完又报 `reportMapper` 同样的问题 | 禅道的「测试报告」模块在本项目里已经占了 `testreport.ReportController/ReportService/ReportServiceImpl`，并且它的 `TestReportServiceImpl` 里有个 `@Resource private TestReportMapper reportMapper;`。三个机制各撞一次：①组件扫描按**短类名**生成 bean 名；②`@Resource` **先按字段名**注入；③MyBatis 的 mapper bean 名取**短类名首字母小写** | 报表这一侧全部加前缀：`ZentaoReportController` / `ZentaoReportService` / `ZentaoReportServiceImpl` / `ZentaoReportMapper`，注入字段也改成 `zentaoReportService` / `zentaoReportMapper`。**新模块开工前的检查清单：全局搜同名类（含框架模块）+ 搜同名注入字段 + 搜同名 mapper** —— 这是坑位 #25 的第三次上演 |
| 45 | **同一张表的两类记录，只给一边加了过滤条件**：加上用例库之后，用例集列表里冒出了「用例库」、产品用例列表里冒出了库里的用例，而**接口回归全绿**（因为新写的用例库测试只查库、没查另一边） | `zt_testsuite` 同时装用例集（`product>0, type in public/private`）和用例库（`product=0, type='library'`），`zt_case` 同时装产品用例（`lib=0`）和库用例（`lib>0`）。我按直觉给「库侧」加了 `type='library'` / `lib=<id>` 条件，却没给「另一侧」加排除条件 —— 于是两边都串了 | 两侧都写死区分条件：`TestSuiteMapper.selectPage/selectListByProduct` 加 `type <> 'library'`，`CaseMapper.selectPage` 在没有 `lib` 时强制 `lib=0`，并给 `validateSuiteExists` 加「拿到的是库就当不存在」。**共用表要问的不是「我要查什么」，而是「我怎么把另一半排除掉」**；断言也要两边都写（本轮的测试就同时验了「库列表不含用例集」和「用例集列表不含库」） |
| 44 | **两个完全不同的业务错误返回同一个错误码**（`1_020_000_007`「父需求不存在」vs「不能把需求标记为重复于自身」） | `ErrorCodeConstants` 里手写的数字是**唯一性靠人眼保证**的：追加常量时复制粘贴了上一行、只改了名字没改数字。因为 `ErrorCode` 自带 message，接口提示还是对的，所以接口回归**一直是绿的** —— 只有把 code 打出来对比才会发现撞号 | 把撞号的两个常量改成独立编号（`012`/`013`），新增的类型错误码从 `014` 起编号。**教训：错误码这种「半结构化」常量要有机械检查**；至少新加常量时 grep 一下这个数字出现过没有（本次是靠写「期望码」断言时发现的：断言期望 `1020000015`，而顺手一查 `1020000007` 有两个定义） |
| 43 | **接口全通，但页面上「添加成员」按钮不见了** | 团队模块忘了建 `system_menu` 权限行，而前端 `v-hasPermi="['zentao:team:update']"` 是拿「角色 → 菜单」算出来的权限串判定的 —— 没有菜单行就没有这个权限串，按钮被直接隐藏。后端却是超管硬编码放行，所以接口一切正常：**"接口测试全绿"完全掩盖了这个洞**，只有浏览器检查里点不到按钮才暴露 | `35-zt_team.sql` 补两行 type=3 的按钮菜单（`zentao:team:query` / `zentao:team:update`，挂在「项目管理」下），并清一次 Redis 的权限缓存（菜单变了必须清，否则要等到重新登录）。**教训：新增模块时"表 + 接口 + 页面"之外还有第三件事 —— 权限菜单行**；只跑接口回归永远发现不了 |
| 42 | **`deploy/sql` 灌不出演示数据：项目/需求/任务/缺陷一条都没有，`30/31/32/33/35` 里那几条 UPDATE 全是空操作** | 早几轮是在**远端已有库**上分批开发的：项目 1「禅道迁移一期」、需求 1/4、任务 1、缺陷 1 这几条演示数据当时是用接口或手工 SQL 造出来的，**从来没写进 `deploy/sql`**。于是从空库初始化时，后面那些"把 X 挂到 Y 上"的脚本（30 把 92201/92202 挂到需求 4、31 回填 task 1 的 storyVersion、32 把 task 1 的工时对平、33 把项目挂到项目集、35 回填 teamCount）**全都没有对象可改，静默跳过** —— 远端一直有这些数据，所以三年都没暴露 | 新增 `deploy/sql/36-zt_demo_seed.sql` 把缺的演示数据补齐（并把它当年该生效的那几条 UPDATE 一起补上），幂等可重复执行。**教训：「在已有环境上分批开发」必然会造成"演示数据只存在于那台机器上"** —— 判断办法只有一个：**真的从空库初始化一次**。这次是因为远端不可达、被迫用本机栈从零建库，才第一次跑出来（并且顺带发现客户端字符集没给 utf8mb4 会把中文双重编码） |
| 53 | **页面检查报 JS 报错，但报的是 `Array(1): Object`** —— 页面本身看起来完全正常（表单该飘红的都飘了） | `await formRef.value.validate()`：Element Plus 的 `validate()` 在校验失败时是 **reject**（带字段错误对象 `{name:[{message:'公司名称不能为空',...}]}`），async 提交函数不 catch 就是一条**未捕获的 unhandledrejection**。Playwright 的 `pageerror` 对这种对象序列化不出来，只能给出 `{log:[],name:'Array(1)'}`，等于没线索 | 两层修：① 页面里 `try { await formRef.value.validate() } catch { return }`（`entry` 与 `company` 两页同款问题一起修）；② 浏览器检查里补一个 `window.addEventListener('error'/'unhandledrejection')` 把 message 与来源行号记下来 —— **排查时先让错误可读，再谈修**。另外「ResizeObserver loop …」是浏览器良性告警（Element Plus 表格/页签常见），检查里要显式过滤，别让它冒充错误 |
| 52 | **`@PermitAll` 的写接口一律 500「系统异常」**：`entry` 的校验接口走完签名校验要回写 `calledTime`，一执行就炸；而同样的代码在管理端（有登录态）**手动调一次却正常** | MyBatis-Plus 的填充列 **`updater` 即使拿不到登录用户也会进 SET 子句** —— `DefaultDBFieldHandler.updateFill` 只在「有登录用户」时才赋值，但字段本身已注册为填充字段，于是 `update(entity, wrapper)` 写出一个 NULL，撞上 `updater varchar(64) NOT NULL` 报 `Column 'updater' cannot be null` | 这一处改成裸 SQL（禅道 `entry::updateCalledTime` 本来就只更新一列）：`@Update("UPDATE zt_entry SET calledTime = #{time} WHERE code = #{code}")`。教训：**`@PermitAll` / 定时任务 / MQ 消费者这类「没有登录态」的写路径，不能照抄管理端的 `update(entity, wrapper)`**；排查线索是 `Cannot be null` 打在 `creator`/`updater`/`create_time` 这些审计列上 |
| 41 | **「执行不在项目集树里」—— 又一次凭一个模块的代码推断另一个模块，结果 `path` 留空、`grade` 归零** | 做项目集那一轮，我看到 `program/model.php#setTreePath` 只处理 `type in ('program','project')`，就断定执行不参与层级、把 `path` 写成空串。实际执行模块**有自己的** `setTreePath`（`module/execution/model.php:5004`）：`parent=所属项目`、`path=,项目,执行,`、`grade=1`；禅道自己的 demo 数据也是这个形状。做团队表时顺手翻 `execution/model.php` 才发现 | 改 `ExecutionServiceImpl`/`StageServiceImpl`，并用 `35-zt_team.sql` 里的 UPDATE 纠正已有数据。**教训：判断「某张表的某个字段是什么语义」，要看那张表所在模块自己的写入代码，再拿禅道的 demo 数据核对一遍** —— 同一个字段名在不同模块里可能是完全不同的两套规则（项目集用 `setTreePath`、执行也用 `setTreePath`，但两个实现完全不同名同实不同） |
| 40 | **浏览器检查里「概览卡片」和「列表行数」对不上：卡片抓出来是 0** | Element Plus 里 `ContentWrap` **本身就是个 `el-card`**，我用 `.el-card` 抓概览卡片时，第一个命中的是整个内容区（innerText 从说明文字开始），于是正则 `^(\d+)` 抓不到数字。页面本身没问题，是选择器太宽 | 选择器限定层级：`.el-row .el-card`（概览卡片都在 `el-row` 里），并在断言里把「卡片数字 = 列表行数」这种**交叉校验**留下 —— 它正是发现这个问题的原因。教训：**Element Plus 的类名会嵌套复用**（ContentWrap/el-card、el-drawer 的多个实例都留在 DOM 里），选择器要么限定层级、要么取 `:visible` |
| 39 | **「未完成」待办一条都查不到**：设计里明明白白有 5 条未完成，`import2Today` 却只挪了 0 条，概览数字和列表也各说各话 | 我把「未完成」这个**聚合状态**当成数据库列值用了：`status = 'undone'` —— 而 `zt_todo.status` 只有 `wait/doing/done/closed` 四个值，于是 SQL 匹配不到任何行。同一个方法在「我的待办」列表里是特判的（`notIn(done,closed)`），但 `selectListByAccount`（import2Today 用的）没做特判 | 在 `selectListByAccount` 里同样识别 `undone` → `NOT IN ('done','closed')`，并把这段翻译逻辑写成注释。教训：**接口上可以暴露「聚合状态」（undone/overdue/thisweek），但落到 SQL 必须翻译成不等值/多值/区间条件** —— 千万别让聚合值直接进 `eq`；另外「同一个状态在两个查询方法里语义不同」是这种 bug 的温床，抽成一个方法最稳 |
| 38 | **移动项目集后，子孙的 path 变成 `,9001,P,P,SUB,` —— 自己那一段被拼了两遍** | 我是用「自己移动后的新 path」去拼子孙的相对位置的：`newPath(",9001,P,") + relative("P,SUB,")`。禅道 `processNode()` 用的是**新父**的 path（`rtrim($parent->path, ',')`）拼接子孙**含前导逗号**的相对位置：`,9001` + `,P,SUB,`。更麻烦的是第一版测试**没发现**这个 bug —— 因为那条用例是把「已经是顶级的项目集」再设成顶级，`parentChanged=false`，重算逻辑根本没执行 | 按 `processNode()` 逐行对齐，并补了「真的换上级」的两个方向断言（挂到项目集下 / 挪回顶级）。**教训：测试里把字段设成它已有的值等于没测** —— 移动/重排/换父这类用例，必须保证前置状态与新状态不同，否则代码路径压根没走到 |
| 37 | **项目层级的 path 我写成了斜杠 `/1/2/`、grade 从 0 开始，`parent` 当成了「父项目」** | 上一轮做项目/执行时，我看了 `zt_module` 的 path 约定（逗号、按 `(root,type,branch)` 分树）就直接类推到 `zt_project`，于是：① path 写成斜杠格式；② grade 从 0 开始；③ 把 `project.parent` 理解成「父项目」。实际禅道是 `setTreePath()` 的**逗号格式、grade 从 1**，而 `project.parent` 是**所属项目集**（`$program = $this->getByID((int)$project->parent)`）—— 项目之间根本没有父子关系 | 做项目集模块时逐行对齐 `setTreePath` / `processNode`，写 `deploy/sql/33-zt_program.sql` 把已有数据的 path/grade/归属全部纠正过来。**教训：层级字段（path/grade/parent）必须先去读禅道的「写入代码」** —— 不同表的约定完全不同（`zt_module` 是按 root 分树的逗号 path，`zt_project` 是项目集+项目共树的逗号 path）；光看表结构或类比另一张表，100% 会错 |
| 36 | **测试脚本里手测「日期区间」接口返回 400，但拿到的是 HTML 而不是 JSON** —— python 解析直接抛 `JSONDecodeError`，第一反应是接口崩了 | 前端 axios 把数组参数序列化成**URL 编码**的 `date%5B0%5D=…`，Spring 能正常绑定到 `LocalDate[] date`；而我在 curl 里直接写了 `date[0]=…` —— 嵌入式 Tomcat 因为 `[` `]` 不在 `relaxedQueryChars` 里，**在进入 Spring 之前**就返回 `400 Bad Request`（HTML）。另外 curl 自己会把 `[0]` 当通配符，还得加 `-g` | 手测数组参数一律写 `%5B0%5D` / `%5B1%5D`（并加 `-g`）。**教训：拿到「不是 JSON 的响应」时，先怀疑请求在进应用之前就被拒了**（Tomcat/网关的 400/413 都返回 HTML），而不是先去读业务代码 |
| 35 | **删光工时后，任务的完成人/完成时间清不掉；换成显式置空又报 `Column 'finishedBy' cannot be null`** | 两个坎叠在一起：① `updateById(只填了要清空字段的 DO)` 是**没用的** —— MyBatis-Plus 默认 `FieldStrategy.NOT_NULL` 会把 null 字段从 SQL 里剔除，一个列都不会更新；② 改用 `LambdaUpdateWrapper.set(字段, null)` 真的发出 `SET finishedBy = NULL` 后，被 `zt_task` 的 **NOT NULL 约束**顶回来（`SQLIntegrityConstraintViolationException`）—— 禅道这几列是 `NOT NULL DEFAULT ''` | 用 `LambdaUpdateWrapper` 显式 `set()`（这是绕开 NOT_NULL 策略的唯一办法），并且**字符串列传 `""`、只有时间列传 null** —— 与禅道 `tao.php` 里 `$data->finishedBy = ''` + `$data->finishedDate = null` 的写法逐列对齐。教训：**「把字段恢复成空」在 MyBatis-Plus 里有两道坎**（更新策略 + 数据库约束），先 `SHOW COLUMNS` 看清哪列可空再写清空语句 |
| 33 | **迁移脚本里的「回填」写在「插演示数据」之前，结果演示任务一上来就显示「需求已变更」** | 给 `zt_task` 加 `storyVersion` 时，我先写了「把已挂需求的任务的 storyVersion 对齐到需求当前版本」的回填，**再**写「给演示任务挂上需求 1」的演示数据。于是刚挂上的那条任务错过了回填，`storyVersion` 停在 1，而需求 1 已经是 v2 —— 看起来像「有一个变更没确认」，其实只是脚本顺序问题 | 把两条 SQL 调换顺序（先插演示数据、再统一回填），并在注释里写明「这条必须放在上面那句之后」。**教训：迁移脚本里的「数据修正」要写在所有「造数据」之后**，否则新造的行不在修正范围内；这类问题的症状是「数据看起来有个合理但错误的状态」，比直接报错更难查 |
| 32 | **父需求聚合整套失效：isParent 不置位、工时是 0、状态级联不触发** | 我写了 `refreshParent(Long childId)`（收**子需求**编号，自己往上找父）和 `refreshParentByParentId(Long parentId)`（直接收父编号）两个方法，但在分解入口 `subdivide` 里传的是**父需求编号** —— 于是方法内部 `child.getParent() == 0` 直接 return，父需求的一切刷新都被静默跳过。更麻烦的是它**不报错**：接口返回 200、子需求也真的挂上了，只有父需求那几个汇总字段没动 | 分解入口改调 `refreshParentByParentId(parentId)`，并把两个方法的入参语义写进 Javadoc。**教训：「收子 id 往上找父」和「直接收父 id」是两个方法，名字必须把方向说清楚**（`refreshParent` 这种名字两种语义都说得通，正是坑的来源）；另外补了「关闭/激活子需求后也要刷新父需求」的调用 —— 级联逻辑必须挂在**所有**改变子状态的入口上，漏一个就有一条路径不生效 |
| 31 | **「先通过后失败」被汇总成「通过」** —— 测试报告里同一条 run 跑了两次（第一次 pass、第二次 fail），统计出来的却是 pass | 我在汇总时遍历执行记录并 `map.put(runId, result)`，想当然认为「后写的覆盖先写的 = 最后一条」。但 `selectListByRun` 是 **`orderByDesc(id)`**（最新在前）的，于是最后写入的其实是**最旧**的那条 —— 结果不是「最后一次结果」，而是「第一次结果」。用 date 比较大小也不稳：同一秒内跑两次的 `date` 可能完全相同 | 改成 `putIfAbsent`（列表已是最新在前，第一个就是最新），并补了一条断言：造一条用例先 pass 后 fail，要求 `resultCount=2 但 failCount=1`。**教训：任何「取最新一条」的代码，都要先确认查询的排序方向 —— 覆盖写入的语义完全取决于它**；顺带一条：拿时间字段做排序/去重时，要想到「同秒」这种情况 |
| 30 | **「不能晚于明天」的校验又不生效了，反而报「不能早于计划开始日期」** | **第 21 条坑在新模块（测试单）里原样重现**：`TestTaskStatusReqVO.realFinishedDate` 没写 `@JsonDeserialize`，于是 yudao 那个「按时间戳解析」的 `LocalDateTime` 反序列化器把 `"2099-01-01 10:00:00"` 静默读成 **1970-01-01**。1970 当然「早于计划开始日期」，所以先触发的是另一条校验 —— 报错信息看着像业务规则写反了，其实是入参被悄悄改成了 1970 | 与发布模块同样的修法：字段上补 `@JsonFormat(pattern=…)` + `@JsonDeserialize(using = LocalDateTimeDeserializer.class)`（**Jackson 3 的包名是 `tools.jackson.databind.ext.javatime.deser`**）。**这已经是第二次踩**，所以结论要升级：不是「发布模块要小心」，而是**凡是从 JSON 进来的 `LocalDateTime` 都要显式指定反序列化器**；顺带记一条排查线索 —— 报错说的是哪条业务规则，不代表错误就在那条规则上，先确认入参有没有被解析器改过 |
| 29 | **移植 `processSteps` 时漏了一行 `preGrade = grade`，步骤编号全错**：同一组下的第 2、3 个步骤都显示成 `1.1`（应该是 `1.1` / `1.2`） | 禅道那段算法是「维护三级计数器 + 记住上一步的层级」：遇到更深的层级就进位、更浅就清零。我照抄了计数逻辑，**唯独漏掉了循环末尾的 `$preGrade = $grade`** —— 于是「当前层级比上一层深还是浅」永远拿第 1 步的层级去比，同级步骤只会重复第一个的编号 | 补上 `preGrade = grade`。**这类「算法抄对了、状态更新漏了」的移植 bug 最阴**：不报错、不抛异常，只是结果静默错误。凡是带「上一步状态」的循环，移植后都要用**多步、同层级**的数据验一遍（只测 1 步或每条都换层级都发现不了） |
| 28 | **`curl -d` 发出去的中文 JSON 被截断成非法 UTF-8，接口报 `Invalid UTF-8 start byte 0xbc`** | 不是服务端的问题：**macOS 自带的 bash 3.2.57 在「变量紧跟多字节字符」时会吞字节**。`title="版本规则-$TS（只改标题）"` 里 `$TS` 后面紧跟全角 `（`（`EF BC 88`），bash 把 `$TS` 的值和 `EF` 一起丢了，只剩 `BC 88` —— 于是 JSON 里的字符串成了非法 UTF-8。同样的写法在**回显消息**里也会静默丢值（之前测试输出里那些 `（id=��` 就是这个原因，我当时忽略了） | 变量一律写成 `${TS}`（花括号消歧义），已用脚本把所有测试里的 14 处 `$VAR` + 多字节字符全部改成 `${VAR}`。**教训：脚本里给变量「紧跟中文」时永远加花括号**；另外，测试输出里出现的乱码不是「终端问题」，要当成真 bug 查 |
| 27 | **文档模块启动/查询就报 1064，第一遍只报 `order`（可我已经加了反引号），真正的 `groups` 被掩盖** | `groups` 是 **MySQL 8 的保留字**（GROUPS 用于窗口函数排序）。但更值得记的是**排查方式本身**：我当时用的预检脚本是「把每张表的列名不加反引号拼成 SELECT 丢给 MySQL 试跑」，而 **MySQL 一条语句只报第一个语法错**，报完整条就失败 —— 所以 `zt_doc` 永远只报排在前面的 `order`，藏在后面的 `groups` 一次都没露过面。同理 `zt_doclib` 也是先报 `order` | 给两个 DO 的 `groups` 加 `@TableField("\`groups\`")`；同时**把预检脚本改成查 `information_schema.KEYWORDS`**（MySQL 8.0.13+ 自带的关键字表，`reserved=1` 即保留字），一条 SQL 拿全量，不再依赖「报错定位」。改完后立刻又扫出 `zt_branch` 有 `default`、`zt_project`/`zt_task` 有 `left` 等一批此前没被报出来的列 —— 说明旧的检查方式一直在漏。**教训：靠「试跑报错」做静态检查，等于把「一条语句只报一个错」的解析器行为当成了检查器的能力上限** |
| 20 | **建构建报 1064，错误指向 `system` 列** | 构建表有个禅道原样的 `system` 列。`system` 是 **MySQL 8 的保留字**，不加反引号直接语法错。有意思的是 **JSqlParser 4.5 能正常解析 `system`** —— 我先跑了 JSqlParser 预检，全绿，于是以为没问题 | DO 上加 `@TableField("\`system\`")`。**「MySQL 保留字」和「JSqlParser 保留字」是两份不同的清单，必须各查一遍**：`output`/`begin`/`end` 是 MySQL 能跑、JSqlParser 报错；`system` / `release` 反过来。已把这个检查固化成脚本，**新增一张禅道表后跑一次**：`deploy/check-reserved-columns.sh` 查 `information_schema.KEYWORDS` 拿全量 MySQL 保留字，`deploy/check-camel-columns.sh` 静态比对 DO 字段与真实列名（专治第 1 条坑，`baseUrl` 就是被它抓出来的）。**注意：这两个脚本只覆盖「MySQL 保留字」和「驼峰列名」，JSqlParser 那一份清单只能在应用层暴露，必须真的调一次接口** |
| 19 | **「已完成再激活」清不掉完成时间**（计划、发布上都又踩了一遍） | 状态流转我用 `updateById(updateObj)` 实现，只给要改的字段赋值、其余留 null 想表达「清空」；但 MyBatis-Plus 默认更新策略是**跳过 null 字段**，于是 `finishedDate`/`closedDate`/`releasedDate` 根本没被清掉 —— 发布从「已发布」改回「未开始」时，实际发布日期仍然留着 | 状态流转统一改成 Mapper 里的原生 `UPDATE ... SET status=?, releasedDate=?`，把 null 也显式写进去（`PlanMapper.updateStatusFields`、`ReleaseMapper.updateStatusAndReleasedDate`）。**用 `updateById` 表达「置空」是无效的；一个坑会在每个带「清空」语义的状态机里重现一次** |
| 18 | **同一个业务概念，在不同表里类型不一样** | `zt_story.plan` 是 **text 逗号列表**（需求可挂多个计划），`zt_bug.plan` 是 **int 单值**；`zt_productplan.branch` 是 **varchar 逗号列表**，而 `zt_story.branch` 是 **bigint 单值**。我一开始照前面的表抄，过滤条件全写成 `= ?`，结果「一计划多分支」「一需求多计划」的数据全查不出来 | 过滤一律 `FIND_IN_SET(值, 列)`；关联逻辑按「独占 or 累加」分别实现（`type=story` 独占、其它累加）。**迁移前先把每张表的同名字段类型列一张对照表** |
| 17 | **产品页、项目页突然变成空白（菜单能点，页面没内容）** | yudao 的动态路由里，**只要菜单带子菜单，它自己就被当成「目录」**：`generateRoute` 会把该菜单的 `component` 换成 `Layout`，页面组件被丢弃。我按禅道界面的直觉把「执行管理」挂在「项目管理」下、「分支管理」挂在「产品管理」下，于是 `/zentao/project`、`/zentao/product` 双双变成目录 | 把这两个子菜单提升为禅道下的一级菜单。**在 yudao 里「有自己页面的菜单」不能挂子菜单；要分组就用「目录 + 子菜单」，而不是「页面 + 子菜单」** |
| 16 | **按模块过滤时，传一个不存在的模块，结果反而查出全部数据** | 服务层把模块展开成「自己+子孙」的 id 列表；模块不存在时列表为空，而 `LambdaQueryWrapperX.inIfPresent(col, 空集合)` 的语义是「集合为空就不加这个条件」—— 过滤条件被整条丢掉，于是变成了全量查询 | 展开结果为空时直接 `return PageResult.empty()`，不要把空列表交给 `inIfPresent`。**`xxxIfPresent` 系列的条件在「空值」时是静默跳过的**，凡是「空 = 什么都查不到」的语义都要自己兜住 |
| 15 | 分支分页第 2 页的 total 和第 1 页不一样 | 「主干」是虚拟行（id=0，不落库），我在第一页把它插进列表并 `total+1`，第二页没插但也没加，于是 `total` 随页码变化，前端分页器直接错乱 | 拆成两个判断：`shouldCountMainBranch()`（与页码无关，决定 total）与 `isFirstPage()`（决定要不要真的插行）。**虚拟行必须「永远计入总数、只在一处展示」** |

**第 2/3/4 条是每新增一张禅道表都会遇到的**，建议后续做成模板或加检查。
**第 12 条是共用表（1 张表存两种对象）的典型翻车**：过滤条件漏在某个查询分支上，
功能测试全过、只有列表里混入了不该出现的行，很容易漏到线上。
**第 13 条提醒 VO 复用要看校验规则**：字段名一样不代表语义一样。
**第 14 条是最危险的一类 bug**：逻辑看起来对、SQL 语法也没错，
但匹配不到任何行，于是「删除」变成了「部分删除」，「改挂」变成了「不改挂」，
而且没有任何报错。凡是靠字符串前缀匹配树路径的地方，都要写断言把行数验出来。
**第 15 条是「虚拟行」的通用陷阱**：不落库的展示行与分页参数天生冲突，
必须显式区分「计数」与「展示」两件事。
**第 24 条是"读权限表 ≠ 读有效权限"**：框架为了方便，常把超级管理员/系统账号做成
硬编码旁路。做权限报表、权限审计、迁移对照这类工具时，必须显式处理这些旁路，
否则会得到"什么权限都没有"的结论。

**第 23 条是"数据看起来错了、其实是旧行残留"**：排查这类问题的第一步永远是
`SELECT id, deleted+0 FROM 表 WHERE id IN (可疑 id)`，先确认那行是不是老数据。

**第 22 条提醒「批量接口的成功语义」**：批量操作里"部分成功"几乎必然存在，
关键是**接口要把成功的那部分明确返回**，否则前端只能假装全部成功。

**第 21 条是「框架帮你做了太多事」的典型**：全局反序列化器把字符串悄悄变成 1970，
接口 200、数据也写进去了，只是「未来日期」这条业务规则永远不生效 —— 这类问题只有
**用边界值（未来时间、超长字符串、负数）真跑一遍**才会暴露。
**第 19 条第三次出现说明它不是偶发**：只要一个状态机要「清空」某个字段，
就必须用原生 UPDATE；建议新增状态机时直接照抄 `updateStatusFields` 的写法。

**第 20 条把第 11 条补完整了**：保留字要过两道闸 —— 数据库那一关（MySQL）和
应用层 SQL 解析器那一关（MyBatis-Plus 用的 JSqlParser）。两关的名单不一样，
所以「JSqlParser 预检全绿」并不代表 SQL 能执行。最稳的做法是建表后用**真实驱动**跑一次
`SELECT 全列 FROM 新表`（或直接调一次列表接口），而不是只跑解析器。

**第 19 条是最「逆直觉」的一条**：写得最规范的 MyBatis-Plus 代码反而做不成事。
凡是「清空某字段」的语义，都要回到原生 SQL。
**第 18 条说明「同名不同型」比「异名」更危险**：名字一样会让人下意识照抄前一个模块的写法，
而类型不同意味着查询、统计、关联的写法全都不同。

**第 31 条是「排序方向决定语义」的典型**：同一个 `put`，在「最新在前」的列表上
等价于「保留最旧」，在「最旧在前」的列表上才等于「取最新」。这类 bug 的特点是
**代码看起来完全正确、数字也拿得到**，只是拿错了那一条 —— 所以凡是「取最新一条」，
都要么用 `putIfAbsent` + 明确注释排序，要么把排序写进同一行代码里（`ORDER BY id DESC LIMIT 1`）。

**第 30 条和第 21 条是同一个坑踩了第二次**，价值在于它把结论从「某模块要注意」
升级成了「**凡是从 JSON 进来的 `LocalDateTime` 都要显式指定反序列化器**」——
第一次踩完我只是修了发布模块，没有做全模块排查，于是测试单又中了一次。
判断方法：把所有「时间字符串进、业务规则判断」的字段列出来，逐个用**未来时间**验一遍边界。
另外它还给了一条排查线索：**报错说的是哪条规则，不代表错误就在那条规则上** ——
这次报的是「不能早于计划开始」，真凶却是入参被解析成了 1970。

**第 29 条是「移植算法」的通用陷阱**：把一段 30 行的循环从 PHP 翻成 Java，
读起来「一模一样」，但只要漏掉循环里的**状态更新**（比如 `preGrade = grade`），
结果就会静默错位 —— 不报错、不抛异常。所以移植循环时不要只对「输入 → 输出」，
还要对「每一步的中间状态」；测试数据要能**制造同层级连续多步**，
否则这类 bug 永远不暴露。

**第 28 条不在业务里，但在「验证手段」里**：测试脚本本身也是代码，也会有自己的坑。
它的隐蔽之处在于**症状出现在服务端**（UTF-8 解析失败），很容易往「后端编码问题」上查，
而真正吞字节的是本地 shell。判断方法很简单：把出错的那条请求原样用文件体
（`--data-binary @body.json`）再发一次 —— 文件体正确、变量体错误，就说明问题在 shell。

**第 27 条和第 1 条是同一类问题的两面**：第 1 条是「列名写法不对」，第 27 条是
「检查手段本身有盲区」—— 一个只报第一个错的工具，看起来能用，实际每次都只给你
一个不完整的答案。这类「工具的上限 = 被检查对象的上限」的陷阱，在静态检查、
linter、SQL 预检里都很常见，判断方法很简单：**故意造两个错误，看它报几个**。

**第 26 条是「业务校验被框架抢先」的代表**：写好的大小/格式校验看起来没错，
但只要框架在上游先把请求拒了，那些代码就是死代码 —— 排查时先确认异常是在**哪一层**抛的。
**第 25 条说明「同一份代码在单体里能跑、拆成多模块就炸」**：bean 名、MyBatis 别名、
`@Resource` 的按名注入，这三套命名空间都是**全局的**，跨模块重名必翻车。
命名冲突的报错经常只暴露第一个（扫描到冲突就中断），修完一个还会蹦下一个，
所以遇到 `ConflictingBeanDefinitionException` 时，直接把「同类名、同字段名」一次性全排掉。

**第 17 条最容易被误判**：页面空白、控制台无报错、路由也能匹配，
很容易以为「这个页面的组件路径写错了」，实际上是**菜单层级**把页面组件吃掉了。
判断方法很直接：如果某个菜单多了一个同级/子级菜单之后原来的页面才变空白，
就去查它是不是变成了「目录」。给菜单加子级之后，一定要回头点一遍父菜单。

**第 16 条和第 14 条是同一类问题的两面**：第 14 条是「条件写了但匹配不到」，
第 16 条是「条件没写，悄悄变成全量」。两者的共同点都是**不报错**，
所以「过滤类接口」至少要有一条「用一个不可能命中的值去查，必须返回 0 条」的断言。
**第 11 条是最费时的一类**：SQL 在 MySQL 里完全合法，却被应用层的 SQL 解析器拒绝。
JSqlParser 的保留字集合比 MySQL 更宽（多出 T-SQL 方言的词），禅道列名里已踩到 3 个。
建议建表后先用 JSqlParser 跑一遍 `SELECT 全列 FROM 表` 做预检。
**第 10 条说明关键字清单要一次性列全**：`left` 这种「看起来像普通列名」的保留字最容易漏。
（本轮用原生 SQL 修正演示数据时又踩了一次 —— `desc=` 直接报 1064，加反引号才过。）
**第 8 条是禅道「头部 + 快照」两表结构的必然产物**：任何做变更追溯的地方，
都必须先从快照表把内容叠加回来，再参与比较。

---

## 五、如何运行

### 5.0 目前的部署形态：整套栈都在 192.168.0.119（本机只留浏览器）

> **2026-09-28：环境从 192.168.0.183 整体迁到 192.168.0.119**（Ubuntu 26.04 / 16 核 / 31G / Docker）。
> 迁移口径：数据库用 `mysqldump` 逻辑备份整库恢复（126 张表 / 67 张 `zt_` 表）、后端源码从本仓库**在新机器上重新编译**、
> 前端源码重新 `pnpm install`、两个 systemd 单元原样重建、`infra_file_config` 的存储域名与 154 条 `zt_file.pathname` 改成新地址。
> 一个坑：119 拉不到 Docker Hub（`registry-1.docker.io` 超时），镜像改用 `docker save | ssh | docker load` **从 183 经内网直接搬**（两台在同一网段，0.5ms）。
> **落地位置：`/data/yudao`**（2026-09-29 CST 按用户要求从 `/opt` 挪过来，一次 `mv` + 重建容器，数据/服务零改动）：
> `build/`（后端源码 + build.sh + maven-settings.xml）、`frontend/`、`server/`、`sql/`、`data/{mysql,redis}`、`docker-compose.yml`；
> 搬迁脚本 `deploy/move-to-data-119.sh`（幂等、默认 dry-run）。
> **时区**：119 宿主机是 UTC，但 MySQL 容器用 `TZ=Asia/Shanghai`、JVM 用 `-Duser.timezone=Asia/Shanghai`
> （两边实测都是 CST）—— 数据库里所有「今天」口径（度量 nodate 快照、todo 的 import2Today、燃尽图横轴）依赖它，不能只靠宿主机时区。
> 183 上属于本项目的东西在 119 验收通过后清理（`deploy/decommission-183.sh`，dry-run 默认）。
>
> 为什么要「服务器模式」（2026-09-14 起的决定）：本机（Mac）内存不够 —— 一个 JDK 25 的 Spring Boot
> 进程 2G 起步，加上容器里跑 MySQL/Redis，跑全量验证时直接把内存吃满。
> 现在 MySQL / Redis / 后端 / 前端**四件都在 192.168.0.119 上**，由 systemd 托管；
> 本机只负责发请求和开浏览器（`deploy/ui-check/*.mjs` 的 Playwright 仍在 Mac 上跑）。
> 本机栈（`deploy/local-stack`）保留为**离线兜底**：远端不可达时 `LOCAL=1 bash deploy/resume-verification.sh`。

| 组件 | 位置 | 端口 | 由谁托管 |
|---|---|---|---|
| MySQL | `192.168.0.119` 容器 `yudao-mysql` | 3307 | `docker compose`（`/data/yudao`） |
| Redis | `192.168.0.119` 容器 `yudao-redis` | 6380 | `docker compose` |
| 后端 | `192.168.0.119:/data/yudao/server` | 48080 | systemd `yudao-server`（系统自带 JDK 25 = `/usr/lib/jvm/java-25-openjdk-amd64`） |
| 前端 | `192.168.0.119:/data/yudao/frontend` | 8081 | systemd `yudao-ui`（vite dev server） |
| 编译环境 | `192.168.0.119:/data/yudao/build` | — | `bash /data/yudao/build/build.sh [--deploy]`（JDK 25 + Maven 3.9.12） |

```bash
# 服务器上（ssh ubuntu@192.168.0.119，本机公钥已装好，免密）
sudo systemctl status yudao-server yudao-ui
sudo journalctl -u yudao-server -n 50       # 或 tail -50 /data/yudao/server/logs/stdout.log
sudo systemctl restart yudao-server yudao-ui
cd /data/yudao && docker compose ps

# 浏览器直接开（本机不需要装任何东西）
open http://192.168.0.119:8081/             # admin / admin123（租户 tenant-id: 1）
```

**一条命令跑全量验证**（本机执行）：

```bash
bash deploy/resume-verification-remote.sh    # 同步 SQL → 重启服务器服务 → 43 个脚本 + 21 个页面 + 20 个浏览器专项
bash deploy/resume-verification-remote.sh    # 幂等，可重复跑
```

`deploy/test-*.sh` 与 `deploy/ui-check/*.mjs` 都支持用环境变量换目标，所以同一套脚本
既能在本机栈跑、也能在服务器上跑：

```bash
ZENTAO_API_BASE=http://192.168.0.119:48080/admin-api bash deploy/test-entry-module.sh
ZENTAO_UI_BASE=http://192.168.0.119:8081 ZENTAO_API_BASE=http://192.168.0.119:48080/admin-api \
  node deploy/ui-check/entry.mjs
```

**改代码之后怎么部署**（在服务器上编译，本机只负责传源码）：

```bash
# 后端：传源码 → 服务器编译 → 换 jar 并重启
cd /Library/allproject/02code/yudao
tar czmf - --exclude='.git' --exclude='target' -C ruoyi-vue-pro . \
  | ssh ubuntu@192.168.0.119 'tar xzmf - -C /data/yudao/build/ruoyi-vue-pro'   # `-m` 必须带，见坑位 #58
ssh ubuntu@192.168.0.119 'bash /data/yudao/build/build.sh --deploy'

# 前端是 dev server 直读源码：改完同步到 /data/yudao/frontend 后重启 yudao-ui
tar czf - --exclude='.git' --exclude='node_modules' --exclude='dist' -C yudao-ui-admin-vue3 . \
  | ssh ubuntu@192.168.0.119 'tar xzf - -C /data/yudao/frontend'
ssh ubuntu@192.168.0.119 'sudo systemctl restart yudao-ui'
```

**为什么前端是 dev server 而不是 nginx 静态部署**：本 checkout 的 `vite build` 跑不通
（yudao 自带的 `views/oa/attendance/*` 引用了一个在 HEAD 里就不存在的
`@/views/oa/utils/constants`，属既有问题，见「已知限制」），所以前端一直以
「dev server + Playwright 实跑」作为验证口径；从 183 挪到 119 之后这一条没有变。

### 5.0.1 服务器模式的两个「环境前提」（都是这一轮踩出来的）

| 前提 | 症状 | 处理 |
|---|---|---|
| **后端所在机器要有 `git`（≥ 1.8.5）** | 代码库（repo）模块跑 `git -C <path> log`；本机栈天然有 git，服务器模式没有 → 建代码库直接报「仓库路径不可用」、同步报 `Unknown option: -C` | 服务器上装了 **git 2.43.0**（`/opt/git`，软链到 `/usr/local/bin/git`）。注：CentOS 7 自带的 git 1.8.3 **不支持 `-C`**，必须升 |
| **每张新的 `zt_*` 表都要登记进 `yudao.tenant.ignore-tables`** | 漏登记的表一查就 500，日志里是 `Unknown column 'tenant_id' in 'where clause'`（`zt_score` 这一轮就漏了） | 新增 `deploy/check-tenant-ignore.sh` 预检：比对 SQL 里建出来的 zt_ 表与 yaml 的 ignore-tables，漏一张就报错退出 |

### 5.1 服务端容器栈（192.168.0.119）

```bash
# 容器栈位于 /data/yudao（2026-09-28 从 183 迁来；183 上那份已删除）
cd /data/yudao
docker compose ps
docker compose up -d      # 启动
docker compose logs -f mysql
```

| 服务 | 地址 | 账号 | 密码 |
|---|---|---|---|
| MySQL | `192.168.0.119:3307` | `yudao` | `<口令见 deploy/_secrets.sh>` |
| MySQL（管理） | `192.168.0.119:3307` | `root` | `<口令见 deploy/_secrets.sh>` |
| Redis | `192.168.0.119:6380` | — | `<口令见 deploy/_secrets.sh>` |

数据库名：`ruoyi-vue-pro`。SQL 文件放在 `/data/yudao/sql/`（**119 上没有挂 initdb**，
库是 `mysqldump` 整库恢复出来的；`./sql` 只作从零重建的备份，日常用 `resume-verification-remote.sh` 逐个灌，都是幂等的）。
附件服务用的是「数据库」客户端（`infra_file_config.id=4`），它的 `config.domain` 必须是
**浏览器能访问到的地址**（现在是 `http://192.168.0.119:48080`）—— 每换一次环境这一条都要改，
漏了附件能上传但显示不出来。迁移时同时把历史遗留的 154 条 `zt_file.pathname`（写着
`127.0.0.1:48080` / `192.168.0.183:48080`）一起改写成了新地址。

> ⚠️ 这是开发/测试环境密码，上生产前必须更换；Redis 不应直接暴露公网。

### 5.2 后端（服务器上）

```bash
# 服务器上：/data/yudao/server/start.sh（由 systemd 拉起，日志见上面 5.0）
# 关键参数：JDK 25、profile=local、MySQL/Redis 指向 127.0.0.1（容器栈发布在本机端口）
ssh ubuntu@192.168.0.119 'sudo systemctl restart yudao-server'
```

**服务器上编译（现在的主路径，不再在本机编译）**：

```bash
ssh ubuntu@192.168.0.119 'bash /data/yudao/build/build.sh --deploy'
# build.sh = JDK 25 + Maven 3.9.12 + maven-settings.xml（阿里云镜像）→ package → 换 jar → 重启
```

本机编译（JDK 25 必须先切）仍然可用，只是内存吃紧时不建议：

```bash
cd /Library/allproject/02code/yudao/ruoyi-vue-pro
source ../use-jdk25.sh
mvn -s ../maven-settings.xml -Dmaven.legacyLocalRepo=true package -DskipTests
```

**构建脚本**：`bash build-backend.sh` —— 带 SIGKILL 看门狗 + `caffeinate`，能扛住
网络抖动和 macOS 休眠导致的构建挂死，会自动重试并累积进度（本机用）。

**本机栈兜底**：`LOCAL=1 bash deploy/resume-verification.sh`（起本机容器 + 本机后端，
端口也是 48080/3307/6380，与服务器模式互斥，别同时开）。

### 5.3 前端（服务器上）

```bash
# 服务器上：/data/yudao/frontend/start.sh（systemd yudao-ui，vite --host 0.0.0.0 --port 8081）
# 接口地址在 /data/yudao/frontend/.env.local 的 VITE_BASE_URL（当前 http://192.168.0.119:48080）
ssh ubuntu@192.168.0.119 'sudo systemctl restart yudao-ui'
open http://192.168.0.119:8081/     # admin / admin123
```

本机也可以起前端（`cd yudao-ui-admin-vue3 && pnpm dev`，默认 80 端口），
`VITE_BASE_URL` 已指向服务器后端，所以本地前端 + 远程后端同样可用。

登录后左侧菜单「禅道」下依次是：我的地盘 / 产品管理 / 项目集 / 需求管理 / 项目管理 / 执行管理 / 任务管理 / 缺陷管理 /
模块维护 / 分支管理 / 计划管理 / 构建管理 / 发布管理 / 阶段管理 / 组织权限 / 文档管理 / 测试用例 / 测试单 / 测试报告 / 工时明细 /
回收站与动态 / 报表 / 测试仪表盘 / 代码库 / 节假日 / 应用接入 / 公司信息，
全部是**一级扁平菜单**（URL 形如 `/zentao/product`、`/zentao/execution`）。

**工时明细既是一个页面，也嵌在任务列表里**：`/zentao/effort` 是流水账 + 按账号汇总；
任务列表每行的「工时」按钮打开同一个抽屉（`components/EffortPanel.vue`），
在任务上下文里看预计/已消耗/剩余并直接登记。

**附件没有独立页面**：它是公共能力，以「附件」Tab 的形式嵌在需求详情抽屉里
（`views/zentao/components/AttachmentPanel.vue`），任何业务详情页传 `objectType` + `objectID` 即可复用。

> ⚠️ 改完菜单后，已登录的浏览器需要**重新登录**才会拿到新的路由表
> （yudao 把 `roleRouters` 缓存在 localStorage 里，登录时才重新拉取）。

### 5.3.1 浏览器巡检为什么会「偶发全红」

`deploy/ui-check/*.mjs` 依赖三件事同时正常：前端 dev server、后端、**远端 MySQL/Redis 的网络往返**。
后端每个请求都会读写远端 Redis 缓存，所以一旦这条链路抖动（实测 RTT 从 ~1ms 劣化到 **150ms+**），
`get-permission-info` 这种聚合接口从 0.3s 变成 **20s+**，于是：

- 登录按钮长时间 `loading`，`waitForURL` 超时 → 表现成「登录失败」；
- 每个页面首屏要 20s 以上 → 巡检脚本里那些 15s 的超时全部误报「表头 0 列」。

**这不是页面坏了，是等得不够久。** 处理办法（都已在脚本里落地）：

1. 登录改用共用助手 `ui-check/_login.mjs`：等按钮可用再点、最多重试 3 轮、URL 等待给到 90s；
2. `all-pages.mjs` 等「页面自己的列表请求」的上限从 15s 提到 60s；
3. 所有脚本在异常退出时都会 `b.close()` —— 否则每失败一次就留一个 headless Chromium，
   攒到几十个之后机器负载升高，会让后面的检查**连锁失败**（实测攒到 31 个）。

先自测链路再排查页面：

```bash
python3 - <<'PY'
import socket, time
s = socket.create_connection(('192.168.0.119', 6380), timeout=5)
t = time.time(); s.sendall(b'AUTH <口令见 deploy/_secrets.sh>
'); s.recv(100)
for _ in range(10): s.sendall(b'PING
'); s.recv(100)
print('Redis 单次往返 %.0f ms' % ((time.time()-t)/10*1000))
PY
```

### 5.4 从零重建数据库

`deploy/sql/` 下的脚本按编号顺序执行（容器首次初始化时自动跑）。

```bash
# 重建（会先留档旧数据）
cd /data/yudao && docker compose down
mv data/mysql data/mysql.bak.$(date +%s)
docker compose up -d
```

`deploy/sql/migration/` 里是**一次性增量脚本**，不参与初始化，只在已有库上升级时手动执行。

---

## 六、目录结构

```
/Library/allproject/02code/yudao/
├── ruoyi-vue-pro/                  后端（yudao 完整版，分支 master-jdk25）
│   └── yudao-module-zentao/        本项目的禅道业务模块
├── yudao-ui-admin-vue3/            前端（Vue3 + Element Plus）
│   └── src/views/zentao/, src/api/zentao/
├── deploy/                         部署编排与 SQL
│   ├── docker-compose.yml
│   ├── sql/                        初始化脚本（01~48，可从头建库；36 演示数据、39 用例库、40 看板八张表、41 度量、42 BI、43 回收站/动态的菜单权限、44 报表菜单 + 两个主干链路口径纠正、45 燃尽图表、46 测试仪表盘菜单、47 代码库四张表、48 节假日表）
│   ├── sql/migration/              一次性增量脚本
│   ├── test-execution-module.sh    执行模块接口回归（34 项断言）
│   ├── test-branch-module.sh       分支/平台接口回归（38 项断言）
│   ├── test-module-module.sh       模块树接口回归（55 项断言）
│   ├── test-module-integration.sh  模块/分支与 story·bug·task 联调（31 项断言）
│   ├── test-productplan-module.sh  产品计划接口回归（76 项断言）
│   ├── test-build-module.sh        构建接口回归（62 项断言）
│   ├── test-release-module.sh      发布接口回归（67 项断言）
│   ├── test-projectstory-module.sh 项目需求范围接口回归（37 项断言）
│   ├── test-stage-module.sh        阶段（瀑布流程）接口回归（30 项断言）
│   ├── test-organization-module.sh 组织与权限视图接口回归（16 项断言）
│   ├── test-file-module.sh         附件（上传/下载/gid 绑定）接口回归（30 项断言）
│   ├── test-doc-module.sh          文档（库/章节/版本链/草稿位）接口回归（59 项断言）
│   ├── test-testcase-module.sh     测试用例（步骤/版本规则/评审/待确认）接口回归（43 项断言）
│   ├── test-testtask-module.sh     测试单（编排/执行/三处回写/状态机/建缺陷）接口回归（54 项断言）
│   ├── test-report-module.sh       测试报告 + 用例集（汇总规则/幂等/删除保护）接口回归（26 项断言）
│   ├── test-storytree-module.sh    父子需求（分解/聚合/状态级联/版本冻结）接口回归（22 项断言）
│   ├── test-storytask-module.sh    需求转任务（需求版本冻结/已变更提示）接口回归（19 项断言）
│   ├── test-effort-module.sh       工时明细（流水/重算规则/汇总/悬空引用）接口回归（47 项断言）
│   ├── test-program-module.sh      项目集（三角色隔离/逗号 path/移动子树/删除保护）接口回归（55 项断言）
│   ├── test-todo-my-module.sh      待办 + 我的地盘（个人清单口径/私有/挪到今天/聚合自洽）接口回归（59 项断言）
│   ├── test-team-module.sh         团队（成员表/可用工时/全量保存/执行层级口径）接口回归（33 项断言）
│   ├── test-quality-chain.sh       质量链（缺陷解决→构建/发布清单自动回写）接口回归（25 项断言）
│   ├── test-stakeholder-module.sh  干系人（内部/外部/关键/批量/与团队的区别）接口回归（23 项断言）
│   ├── test-project-view-module.sh 项目视角四模块（工作量估算/计划/构建/发布视图）接口回归（20 项断言）
│   ├── test-caselib-module.sh      用例库（与用例集同表/从产品导入/来源冻结）接口回归（29 项断言）
│   ├── test-storytype-module.sh    需求分层（业务/用户/研发需求：类型字典/分层树/层级规则）接口回归（35 项断言）
│   ├── test-kanban-module.sh       看板（八张表/默认布局/WIP/移动/完成）接口回归（45 项断言）
│   ├── test-metric-module.sh       度量（定义/口径计算/快照/清理/周期）接口回归（29 项断言）
│   ├── test-bi-module.sh           BI（数据视图/SQL 白名单/图表聚合）接口回归（37 项断言）
│   ├── test-action-module.sh       操作日志（回收站/还原/隐藏/备注/动作渲染/动态）接口回归（30 项断言）
│   ├── test-my-workspace-module.sh 我的地盘第二组（我参与的项目/执行/团队/测试单/用例/文档/日历）接口回归（45 项断言）
│   ├── test-annual-report-module.sh 报表（年度数据/每日提醒/产出统计/项目状态）接口回归（39 项断言）
│   ├── test-burn-module.sh         执行燃尽图（快照/补齐/理想线/延期段/采样）接口回归（32 项断言）
│   ├── test-qa-module.sh           测试仪表盘（质量统计口径/三个列表块/按产品过滤）接口回归（25 项断言）
│   ├── test-repo-module.sh         代码库（仓库 CRUD/真实 git 同步/提交与对象双向关联）接口回归（37 项断言）
│   ├── test-holiday-module.sh      节假日（假期/补班/工作日口径/燃尽图横轴）接口回归（31 项断言）
│   ├── test-entry-module.sh        应用接入（校验链/两种签名/防重放/IP 六态/调用日志）接口回归（67 项断言）
│   ├── test-company-module.sh      公司信息（本公司/唯一性/http:// 归一/外部公司/超管口径对照）接口回归（28 项断言）
│   ├── test-score-module.sh        积分（规则表/次数与时间窗/四条特例/总分/与 entry 联调）接口回归（40 项断言）
│   ├── test-search-module.sh       保存查询（条件 JSON/公开私有/字段解析/维度过滤）接口回归（23 项断言）
│   ├── test-dimension-module.sh    度量维度（四级回退/下拉树/删除保护/对象名解析）接口回归（37 项断言）
│   ├── test-api-module.sh          接口文档库（空间/库/接口/结构/版本发布与冻结/引用校验）接口回归（72 项断言）
│   ├── test-webhook-module.sh      Webhook（定义/加密开关/事件触发/mock 接收/调用日志）接口回归（62 项断言）
│   ├── _mysql.sh                   直连查库小工具（MYSQL_TARGET=local|remote|auto）
│   ├── local-stack/                本机验证栈（远端不可达时的替代路径，同一份 SQL）
│   ├── resume-verification.sh      补验证：灌 SQL → 起后端 → 团队断言 → 全量回归 → 浏览器检查
│   ├── check-reserved-columns.sh   MySQL 保留字预检（新加表后跑一次）
│   ├── check-camel-columns.sh      驼峰列名漏 @TableField 预检（新加表后跑一次）
│   ├── check-tenant-ignore.sh      新表是否登记进 yudao.tenant.ignore-tables（漏了会 500：Unknown column 'tenant_id'）
│   ├── audit-module-feasibility.sh 121 个候选模块的 A/B/C 能力替代审计（只读，输出 docs/audit-output.txt）
│   └── ui-check/                   Playwright 浏览器验证脚本（40 个脚本 = 1 共用登录 + 1 个 21 页面巡检 + 38 个专项/页内交互；
│                                   resume-verification 固定跑其中的 all-pages + 20 个模块专项）
│                                   all-pages.mjs（21 页面巡检）/ _login.mjs（共用登录）
│                                   专项：team / stakeholder / storytype / caselib / kanban
│                                         metric / bi / action / my-workspace / report / burn / qa / repo / holiday / entry / company / score / search / dimension / api / webhook / my
│                                   模块页：plan / build / release / execution / module / program
│                                           file / doc / testcase / effort / organization
│                                           branch / stage / projectstory / story-filter / storytree
├── use-jdk25.sh                    JDK 切换（不动全局 .zshrc）
├── maven-settings.xml              Maven 阿里云镜像（不改全局 ~/.m2）
└── build-backend.sh                带看门狗的构建脚本
```

---

## 七、后续路线

### 优先建议

1. **继续横向铺开**：主干链已打通（产品 / 项目集 → 项目 → 执行 → 需求 / 任务 / 缺陷），
   产品维度的 `branch`（分支/平台）与通用树 `tree`（模块树）也已完成。
   计划 / 发布 / 构建、瀑布 `stage`（阶段）、项目需求范围 `projectstory`、
   组织权限视图、公共能力 `file`（附件）、文档库 `doc`、测试用例 `testcase`、测试单 `testtask`、
   测试报告 `testreport` 与用例集 `testsuite`、**需求分层（`epic`/`requirement`，即业务需求/
   用户需求/研发需求，3.31）**、**用例库（`caselib`：与用例集同表，3.32）**、**看板（`kanban`：七层聚合 + 格子存位置，3.33）**、
   **度量（`metric`：框架 + 15 个口径，3.34）**、**BI（`bi` 的 SQL 模式 + `chart`，3.35）**、**操作日志收口（`action`：回收站/动态/备注/动作渲染，3.36）**、
   **我的地盘第二组（`my`：我参与的项目/执行、我的团队、我的测试单/用例/文档、我的日历，3.37）**、
   **应用接入（`entry`：第三方免登录的校验链/两种签名/防重放/IP 白名单，3.43）**
   也**均已完成**。
   **测试链已经完整闭环**：用例 → 排进测试单 → 逐条执行 → 结果回写三处 →
   失败建缺陷（带溯源）→ 汇总成测试报告。
   质量链也已收口：缺陷解决时**自动回写构建与发布的 Bug 清单**（3.28）。
2. **把「新增禅道表」固化成清单**：第 2/3/4/10/12 条坑每张表都会遇到，建议写成一个
   建表检查清单（租户忽略、逻辑删除与唯一键、MySQL 保留字、**共用表的 type 过滤**），
   或直接做成代码模板。
3. **需求变更的并发保护**：禅道 `change()` 里用 version 做乐观锁。当前实现没做 ——
   两个人同时变更同一需求会覆盖。接入时可以在 `changeStory` 里加
   `WHERE id=? AND version=?` 的条件更新。

### 尚未覆盖的禅道能力

- **`my` 里依赖未迁移模块的那几个聚合**（评审 reviewissue / 风险 risk / 会议 meeting / MR / 审批 approval）
- **周期待办**（`zt_todo.cycle=1` 的下一次自动生成）
- **项目集的看板/干系人/预算超支校验**（`program` 模块的 kanban / stakeholder / budget）
- **任务的多人工时**（禅道的 `zt_taskteam`：一个任务拆给多人、各自登记工时，
  删除某人工时时还要按 `order` 找到「上一条属于同一个人的记录」——
  `getLeftAfterDeleteWorkhour` 后半段那一大坨逻辑就是干这个的，本轮只落了单人任务的工时流水）
- 任务的父子任务与「子任务工时汇总到父任务」
- 工时的计时器（`zt_effort.begin/end` 字段已存，但没有「开始计时/结束计时」的界面与自动登记）
- 批量操作、Excel 导入导出
- 需求转 Bug（`fromBug` 字段已建，转换逻辑未做）
- 字段自定义（`custom->createFields`）、邮件通知、数据权限

### 已知限制

- 前端未做字段级权限控制，仅依赖后端 `@PreAuthorize`
- **前端生产构建在本 checkout 上跑不通（与本次迁移无关）**：yudao 自带的 `views/oa/attendance/*`
  引用了 `@/views/oa/utils/constants`，而这个文件**在 HEAD 里就不存在**（`git cat-file -e HEAD:...` 已确认）。
  `vite build` 因此失败在 `UNLOADABLE_DEPENDENCY`，`vue-tsc` 也把它算作 20 条既有错误之一。
  所以前端的验证口径一直是：**dev server + Playwright 实跑 + `vue-tsc` 无新增错误**，而不是生产构建
- 评审人输入是自由文本（可输入任意账号），未接用户选择器；禅道会校验账号存在
- 变更未做乐观锁，并发变更会互相覆盖
- 操作日志的 `zt_history.diff` 用纯文本而非禅道的 HTML 格式（安全考虑，见 3.4）
- 工时明细只覆盖单人任务：`zt_taskteam`（多人任务）未迁移
- 工时记录的「剩余工时」在未开始任务被删光时保持任务字段原值（与禅道一致，含意说明见 3.23）
- 应用接入的 `verify` **只做校验**（返回账号信息 + 记 `zt_log` + 回写 `calledTime`），
  **不建立登录会话** —— yudao 的认证由 OAuth2 统一负责，禅道的「免密登录」在此换成了等价的认证流程（详见 3.43）
- 「应用接入」的 `query` 模式签名是按**调用方给出的原始查询串**算 `md5(md5(query)+key)` 的。
  禅道是 `http_build_query(parse_str(...))`，两边在参数只含字母数字时完全一致；
  参数里带空格/中文时，调用方需要按 PHP `urlencode` 规则自己编码

---

## 八、参考

- 禅道源码：https://github.com/easysoft/zentaopms
- yudao（ruoyi-vue-pro）：https://github.com/YunaiV/ruoyi-vue-pro
- 业务规则来源：`module/story/model.php`、`module/story/tao.php`、`module/story/lang/zh-cn.php`
- 表结构来源：`db/zentao.sql`

---

## 九、迁移与清理记录（2026-09-28 ~ 09-29：183 → 119）

整套栈从 `192.168.0.183`（CentOS 7，与 tyarchive / dg / dangan-hot / panwatch / ruoyi-archives 等**共用**的机器）
迁到 `192.168.0.119`（Ubuntu 26.04，16 核 / 31G / 292G，本机公钥已装、免密），并按用户要求把项目落在 **`/data/yudao`**。

### 9.1 怎么搬的

| 项 | 做法 | 备注 |
|---|---|---|
| 数据库 | 183 上 `mysqldump --single-transaction --routines --triggers --events --databases ruoyi-vue-pro`（8.3MB / 126 表）→ Mac 中转 → 119 上 `mysql` 恢复 | 校验：**126 张表 / 67 张 `zt_` 表 / 904 需求 / 2248 菜单 / 顶层可用菜单 3 个**（含之前隐藏死菜单的结果） |
| 容器镜像 | **`docker save mysql:8.0 redis:7-alpine \| gzip \| ssh 119 'gunzip \| docker load'`** | 119 **拉不到 Docker Hub**（`registry-1.docker.io` i/o timeout），而 183 与 119 在同一网段（ping 0.5ms），直接从 183 搬最快最稳 |
| 后端 | 源码从 Mac 传到 119 → **在 119 上编译**（JDK 25 + Maven 3.9.12 + 阿里云镜像）→ 164M jar | 119 上 16 核，全量编译 ~5 分钟（首拉依赖），之后增量 ~30 秒 |
| 前端 | 源码传过去（不含 node_modules）→ `pnpm install`（1 分 59 秒，1.1G）→ vite dev server | `vite build` 跑不通是既有问题，验证口径一直是 dev server + Playwright |
| 进程管理 | 重建 `yudao-server.service` / `yudao-ui.service`（`User=ubuntu`，`WorkingDirectory=/data/yudao/...`） | 119 上 `ubuntu` 免密 sudo |
| 时区 | MySQL 容器 `TZ=Asia/Shanghai` + `default-time-zone=+08:00`；JVM `-Duser.timezone=Asia/Shanghai` | 宿主机是 UTC，但两边实测都是 CST；所有「今天」口径依赖它 |
| 配置 | `infra_file_config.id=4` 的 `config.domain` → `http://192.168.0.119:48080`；历史遗留的 **154 条 `zt_file.pathname`** + **157 条 `infra_file.url`**（写着 `127.0.0.1:48080` / `192.168.0.183:48080`）一起改写 | 漏了这一步，附件能上传但显示不出来 |
| 仓库脚本 | `deploy/_mysql.sh`、`deploy/resume-verification*.sh` 全部改指 119（`ubuntu` + 密钥免密，`SSH_PASS` 留空即不走 sshpass）；33 处 `/opt/yudao*` → `/data/yudao*` | 顺带修了 `test-repo-module.sh` / `ui-check/repo.mjs` 里写死的 `root@<API_HOST>`（那是 183 的连法） |

### 9.2 迁移路上踩到的四个坑（都写进了坑位表 / 脚本注释）

1. **Docker Hub 不可达** → 用 `docker save | ssh | docker load` 走内网直搬（见上表）。
2. **`yudao` 这个 MySQL 应用账号不在 dump 里**（用户存在 `mysql` 系统库，逻辑备份只带业务库）→ 到 119 后第一件事是
   `CREATE USER 'yudao'@'%' IDENTIFIED BY '<口令见 deploy/_secrets.sh>'; GRANT ALL ON \`ruoyi-vue-pro\`.* TO 'yudao'@'%';`，
   否则后端启动报 `Access denied for user 'yudao'@'172.18.0.1'`。
3. **`tar` 保留 mtime 让 Maven 跳过编译**（坑位 #58）：改完代码同步到服务器，编译 3.4 秒「BUILD SUCCESS」、jar 时间戳没变、
   行为一模一样 —— 因为 Mac 上的文件 mtime（本地 10:38）比服务器上的 `.class`（UTC 02:44）还旧。同步必须 `tar -m`；
   `build.sh` 现在会在编译前自检并报警，编译后比对 jar 时间戳。
4. **`pkill -f` 自杀**：清理脚本里写 `pkill -f 'vite.js --mode'`，模式串本身就出现在远端执行命令的 cmdline 里，
   结果把跑命令的 shell 一起杀了（ssh 直接 channel closed）。改用 `vite[.]js` 这种「正则不匹配自己」的写法；
   另外**只 stop + 删单元文件不够** —— 进程若还活着，vite 会把 `/opt/yudao-stack/frontend/.vite` 缓存目录重新建出来。

### 9.3 119 上的验收（2026-09-29）

| 项 | 结果 |
|---|---|
| 接口回归 | **43 / 43 脚本、1704 项断言、0 失败**（全量那一轮 41/43 跑完，其中 `test-burn` / `test-caselib` 各修一处后各自连跑两次 32/0、29/0） |
| 页面巡检 | **21 / 21** |
| 浏览器专项 | **20 / 20 全绿**（新式脚本断言合计 409 项 + `team.mjs`） |
| 存储/服务 | MySQL 3307、Redis 6380、后端 48080、前端 8081；`/data/yudao` 下 build/frontend/server/sql/data + compose |

顺手修掉的两个真问题：① **燃尽图快照口径**（禅道 `report::createSingleJSON` 按**自然日**推进 `preValue`，
落在周末/节假日的快照要被后一个工作日继承；我们原来只遍历工作日 labels，会把周末快照整条丢掉 —— 今天刚好「今天−10 天」是周六就暴露了）；
② **用例库测试脆弱性**（上一次 UI 检查被断网打断，留下一个 `UI用例库-…` 导致「只有一个 library」断言变红，非产品问题；
已加兜底清理）。

### 9.4 183 的清理（只删本项目，别人的一行没碰）

删除：`yudao-server.service`、`yudao-ui.service`、容器 `yudao-mysql` / `yudao-redis`、
`/opt/yudao-stack`、`/opt/yudao-build`、`/opt/jdk25`、`/opt/maven`、`/opt/src`（git 源码）。
**刻意保留**：`/usr/local/bin/node`（7 月 30 日就装好，早于本项目）、`/opt/git` + `/usr/local/bin/git`
（删了会让全机回落到 CentOS 7 自带的 **1.8.3**）、以及 183 上所有别人项目的容器与数据。

清理后复核（脚本 `deploy/decommission-183.sh` 的第 4 步会打印这些）：
yudao 容器 0 / 单元 0 / 进程 0、3307·6380·8081·48080 **无监听**、`/opt` 下已无本项目目录；
**别人的 16 个容器全部还在跑**（tyarchive-* 6 个、dg-* 5 个、dangan-hot* 2 个、ruoyi-archives-* 2 个、panwatch），
`tyarchive.service` 仍 active，`git 2.43.0` 与 `node v22.23.2` 仍可用。

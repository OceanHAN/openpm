# 剩余 24 个模块的取证结论（do-it / map-only / not-possible / not-worth）

这份文档是对禅道剩余 24 个未迁移模块的**逐模块取证结论**，与 `MIGRATION-INVENTORY.md`
的「完整清单」与「可迁移性审计」两节配套使用：清单回答「还差哪些」，这份文档回答
「每一个到底能不能搬、值不值得搬」。

结论不是靠读清单里的行数得来的，而是 24 个模块**并行独立取证**：每个模块一个独立执行体，
实地跑命令（`grep` 命中数与退出码、`wc -l` 行数、`grep -n "CREATE TABLE" db/zentao.sql`、
`git log --diff-filter=D` 删除记录）、逐函数读源码、逐个 `TABLE_*` 常量回查开源建库脚本。
因此下面每一条结论都带**可复核证据**（文件名 + 行号 + 命令口径），凡是取证与旧清单不一致的地方
都单独指出——本次取证推翻了 `api`、`codescan`、`gitlab`、`upgrade`、`admin` 五处的旧判断
（见文末「需回写清单的修正项」）。

四类结论的含义：

| 结论 | 含义 | 交付物 |
|---|---|---|
| **do-it** | 值得完整迁移。三条硬条件同时成立：表在开源建库脚本里、代码是真实实现（非存根）、不依赖不可替代的外部系统；且 yudao 侧没有等价能力，业务价值独立。 | 按「表 + 接口 + 前端 + 接口回归 + 界面检查」完整迁移 |
| **map-only** | 只做映射。能力真实存在，但它本身是**系统/后台/配置/调度器/外部适配器**一类，不是有独立边界的业务模块；yudao 已有等价能力，重写等于把同一件事再实现一遍，还会与现有面板冲突。 | 一份「禅道实体 → yudao 实体」映射说明，**0 张表 / 0 接口 / 0 前端页** |
| **not-possible** | 开源版**没有实现可搬**。三种形态：模块目录不存在、表结构不随开源版发布、真实实现被删除或只剩存根。 | 登记 + 说明；要迁移须先拿到付费版源码或上游实现，不是「再花时间就能补」 |
| **not-worth** | 不值得迁。实现是真的、表也在，但在 Java 目标上业务价值不成立（教学演示层、上游历史债、外部基础设施的空壳控制台）。 | 登记 + 说明；只在清单里留痕，不排开发轮次 |

**口径说明**：

- 「PHP 行数」沿用 `MIGRATION-INVENTORY.md` 的「禅道 PHP」列口径（`module/<name>/**/*.php`
  排除 `test/`）。同一模块的两种常见算法会差出几十个百分点，凡有出入的都在各自小节标注
  （如 `search` 核心 2,854 / 含 `lang` 3,428；`zahost` 纯逻辑 801 / 含 ui·view·config 1,601）。
- 「action 数」= `control.php` 的 `public function` 计数；个别模块把 `__construct` 也计进去
  （`zanode` 29 = 28 action + 1 构造函数），已在正文注明。
- 「edition 门槛」= 模块内 `edition != 'open'` 与 `edition == 'open'` 的出现次数之和。
  **门槛为 0 不等于能迁，门槛不为 0 也不等于不能迁**——关键看它挡的是主体还是增量，正文逐条说明。
- 「预计轮数」1 轮 = 一次「实现 + 单模块接口回归 + 界面检查 + 全量回归」的完整迭代；
  map-only 的 0.5 轮是**纯文档**，不含编译与回归。

---

## 一、总表

> 排序：先按结论分组（do-it → map-only → not-possible → not-worth），组内按 PHP 行数降序。

| 模块 | PHP 行数 | action 数 | 缺哪些表 | edition 门槛 | 结论 | 预计轮数 |
|---|---|---|---|---|---|---|
| `search` | 2,854 | 11 | 无 | 4 | **do-it** | 2 |
| `webhook` | 2,737 | 10 | zt_ai_task | 0 | **do-it** | 2 |
| `api` | 2,419 | 32 | 无 | 7 | **do-it** | 2 |
| `dimension` | 499 | 2 | 无 | 0 | **do-it** | 0.5 |
| `ai` | 11,707 | 27 | zt_im_chat | 17 | map-only | 0 |
| `convert` | 9,668 | 18 | 无 | 12 | map-only | 0.5 |
| `admin` | 3,226 | 22 | zt_sqlite_queue | 3 | map-only | 0.5 |
| `dev` | 2,772 | 6 | 无 | 1 | map-only | 0.5 |
| `zai` | 2,520 | 7 | 无 | 2 | map-only | 0 |
| `extension` | 1,808 | 11 | 无 | 0 | map-only | 0 |
| `cron` | 1,132 | 13 | 无 | 1 | map-only | 0.5 |
| `misc` | 900 | 15 | 无 | 1 | map-only | 0.5 |
| `jenkins` | 204 | 0 | 无 | 0 | map-only | 0 |
| `mark` | 121 | 0 | 无 | 0 | map-only | 0 |
| `codescan` | 3,659 | 42 | 无 | 0 | not-possible | 0 |
| `gitlab` | 1,578 | 0 | 无 | 0 | not-possible | 0 |
| `feedback` | 44 | 0 | 无 | 0 | not-possible | 0 |
| `upgrade` | 30,114 | 35 | dashboard、im_chat、im_chat_message_index、im_chatuser、im_message | 12 | not-worth | 0 |
| `tutorial` | 16,700 | 8 | 无 | 1 | not-worth | 0 |
| `zanode` | 4,046 | 28 | 无 | 0 | not-worth | 0 |
| `aiapp` | 2,198 | 9 | 无 | 0 | not-worth | 0 |
| `gitfox` | 1,468 | 4 | 无 | 0 | not-worth | 0 |
| `provider` | 843 | 6 | 无 | 0 | not-worth | 1（仅假设开工时；当前不建议开工） |
| `zahost` | 801 | 11 | 无 | 0 | not-worth | 0 |
| **合计 24 个** | **104,018** | **317** | 6 张（`webhook` 1 + `admin` 1 + `upgrade` 5，另 `ai` 的 `zt_im_chat` 与 `upgrade` 的 `im_chat` 同表） | — | do-it 4 / map-only 10 / not-possible 3 / not-worth 7 | **9 轮**（do-it 6.5 + map-only 2.5；not-worth 的 1 轮不计入计划） |

关于「缺表」的必要提醒：**缺表是 not-possible 的证据之一，但不是 do-it 的反面**。
`webhook` 缺的 `zt_ai_task` 只是「aitask」这一种对象类型的死支路（常量在全仓只有 2 处使用、
开源版连 `define` 都没有），不影响主流程；反过来 `upgrade` 只缺 5 张边缘表，照样判 not-worth。
判断依据始终是「有没有可搬、值得搬的实现」，不是单纯数表。

---

## 二、do-it：值得完整迁移（4 个）

这一组的共同点：**表在开源建库脚本里、代码是真实实现、不依赖不可替代的外部系统、yudao 无等价能力**。
四个模块里 `dimension`/`api` 是独立业务能力，`webhook`/`search` 是横切基础设施——
它们不是「锦上添花」，而是现有已迁模块缺的出口（事件外发、跨对象检索、BI 维度前置）。

### `search`（2,854 行 / 11 action / 预计 2 轮）

**它在禅道里做什么**：两层能力叠在一个模块里——① **全局搜索页**：对全部业务对象做跨模块全文检索，
按对象类型分组出结果；② **列表页的高级搜索表单 + 保存查询**基础设施：每个列表页的「高级搜索」
「保存查询」「菜单栏快捷查询」都由它提供。所以它既服务「跨对象找东西」，也服务「列表页筛选复用」。

**证据**：

- **表全在**：主代码引用的 `TABLE_*` 逐个映射到 `zt_searchindex`、`zt_searchdict`、`zt_userquery`、
  `zt_doccontent`、`zt_doclib`、`zt_storyspec`、`zt_casestep`、`zt_action`、`zt_task`、`zt_project`、
  `zt_product`、`zt_file`，`db/zentao.sql` 全部有 `CREATE TABLE IF NOT EXISTS`（searchdict 第 1761 行、
  searchindex 第 1770 行、userquery 第 2351 行……），缺表为 0。
- **代码是真的**：`control.php` 322 行 + `model.php` 889 行 + `zen.php` 182 行 + `tao.php` 1,266 行
  + `config.php` 195 行 = 2,854 行（含 `lang/` 5 个文件 574 行则合计 3,428 行，与清单第 191 行的
  3,428 / 393 / 11 完全对得上）。`getList()`（`model.php:633-714`）是真实三元 SQL：
  MySQL `MATCH(title,content) AGAINST(... IN BOOLEAN MODE)`、PostgreSQL `ts_rank/tsquery`、
  达梦 `LIKE` 分支；写侧 `saveIndex()`（`model.php:717`）/`deleteIndex()`（`model.php:884`）/
  `buildAllIndex()`（`model.php:829`，按 `type+lastID` 增量续建并返回 `finished/unfinished`）/
  `saveDict()`（`model.php:764`）全部真写真删。`config.php` 的 `$config->search->fields` 为
  bug/build/case/doc/product/project/story/task 等十多个对象定义了 `id/title/content/addedDate/editedDate`
  映射，是全对象搜索配置。
- **门槛只挡增强**：`edition != 'open'` 共 4 处（`control.php:253,297`、`model.php:49,724`），
  作用是 `workflow->appendSearchConfig()`（付费版自定义工作流字段入检索）和 doc 附件内容入索引；
  核心搜索在开源版照常跑，`edition == 'open'` 为 0 处。另有 `tao.php:800/803/804/805/999/1000`
  的 `edition=='max'||'ipd'`，加的是 issue/risk/assetLib 这些开源版本就没有的模块字段，属可跳过增量。
- **无外部系统**：检索用的是数据库自带的 `FULLTEXT KEY title_content(title,content)`
  （`zentao.sql:1779`）+ 本地 `zt_searchdict` 中文分词词典，不需要 ES/Sphinx/向量库/LLM key；
  模块内唯一的 `file_get_contents`（`tao.php:1256/1259`）读的是本地已转换的 doc 文件做摘要。
- **无存根**：`grep -rniE "TODO|FIXME|not implemented|stub|mock|占位|假数据" module/search --include=*.php`
  排除 `test/` 后 0 命中，没有空方法体或只 `echo` 一行的实现。

**结论与理由**：表在、代码在、不依赖外部系统、且是「列表页筛选」与「跨对象检索」两条线的共同底座——
已迁移的 `testcase`/`productplan`/`stakeholder`/`release`/`caselib`/`testsuite` 都在调
`search->setSearchParams()/setQuery()/getQuery()/processBuildinFields()`
（如 `module/testcase/model.php:2972,3018`、`module/productplan/model.php:109,1175`）。
yudao 目前列表只有简单条件查询，既没有保存查询也没有跨模块全文索引，因此它是**真实缺口**，
不是后台配置类。判 do-it。

**如果要做，scope 是**：建 3 张表（`zt_searchindex`：`objectType+objectID` 唯一键 +
`FULLTEXT(title,content)` + `vision`；`zt_searchdict`：key/value 中文词典；`zt_userquery`：保存查询）。
接口约 8~11 个：`buildForm`（高级搜索表单：字段 + 操作符 + and/or + `$today/$thisWeek` 动态变量）、
`buildQuery`（组装校验后落 session）、`saveQuery/deleteQuery`（含 `shortcut=onMenuBar`）、
`ajaxGetQuery`、`ajaxRemoveMenu`、`buildIndex`（`mode=build` 分片续建 / `mode=show` 页面）、
`index`（全局搜索）。前端 4 页：结果列表、检索表单、建索引进度、保存查询弹窗。
必须照抄的关键规则：① 三元 SQL 分支（MySQL `MATCH…AGAINST` / PG `ts_rank` / 达梦 `LIKE`）；
② 结果必须过 `allowedObjects` 白名单 + `vision` + `addedDate<=now`，再走逐对象 ACL
（`searchTao->checkPriv`）；③ 中文靠 `zt_searchdict` 分词而非 LIKE 全表；
④ `buildAllIndex` 必须支持 `type+lastID` 断点续建，否则大库一次建完必超时；
⑤ `config.php` 的 `fields` 映射作为「可搜对象」注册表，并处理 `case ↔ testcase` 这类
module 名与 objectType 的差异。
**决定性一步**：把 `saveIndex()/deleteIndex()` 挂到所有已迁模块（product/project/execution/story/
task/bug/doc/testcase/testtask/release/build/productplan/stakeholder…）的新增/编辑/删除钩子上，
否则索引永远为空——这也是它排在其他模块之后做的原因（见「建议的执行顺序」）。

### `webhook`（2,737 行 / 10 action / 预计 2 轮）

**它在禅道里做什么**：禅道**事件外发的通用出口**。业务动作（新增/编辑/删除需求、任务、缺陷……）
发生后，由 message 模块按 `message->setting` 触发 webhook，把对象数据 POST 到用户配置的 URL；
支持通用 JSON、钉钉群/企微群/飞书群机器人、以及钉钉/企微/飞书三类「应用消息」（按 openID 发给个人）。

**证据**：

- **表基本齐全**：`zt_webhook`(2407)、`zt_oauth`(1348)、`zt_notify`(1328)、`zt_log`(1289)、
  `zt_action`(17)、`zt_kanbancard`(1182)、`zt_meeting`(13956)、`zt_file`(1050) 全部在 `db/zentao.sql`
  有 CREATE。**唯一缺失 `zt_ai_task`**：`TABLE_AI_TASK` 常量在 `config/zentaopms.php` 里根本没有
  `define`（该文件只有 `TABLE_AI_AGENT/USERAGENT/AGENTFIELD/AGENTROLE/MINIPROGRAM`），
  全仓仅 `module/webhook/model.php:335`、`:842` 两处使用，属「aitask」对象类型的死支路，不影响主流程。
- **代码是真的**：`control.php` 347 行 / `model.php` 914 行 / `zen.php` 92 行 / `tao.php`，
  共 2,737 行；`control.php` 9 个业务 action（browse/create/edit/delete/log/bind/chooseDept/
  ajaxGetFeishuDeptList/asyncSend）。`model.php` 25 个 public function 全部落到真实 SQL 或
  `curl`/`dingapi`/`wechatapi`/`feishuapi` 调用（`fetchHook model.php:729-775` 真发 curl、
  `sendToUser :791-820` 真调三方类、`saveData/saveLog` 真写 `zt_notify`/`zt_log`）。
  `lib/dingapi`（318 行）、`lib/wechatapi`（181 行）、`lib/feishuapi`（505 行）都是开源版真实代码。
- **门槛 0 处**：模块内 `edition` 判断 0 次；调用链上唯一有 edition 判断的是
  `module/message/model.php:139/350`，而 webhook 触发点在其外部，开源版照常可用。
- **无存根**：`grep -rn "TODO|not implement|stub|placeholder|dummy|die(|exit;"` 零命中；
  3 处 `return true`（`control.php:314,345`、`model.php:327`）是「异步任务无数据即成功」的正常语义；
  `test/` 下 26 个真实单测 + yaml 期望文件。
- **外部依赖是功能本身且可回归**：generic 类型向用户配置的任意 URL POST JSON，
  可用本地 mock HTTP 接收端完成接口回归；钉钉/企微/飞书是可选适配，不需要 LLM key / 虚拟机节点 /
  扫描器 / 外部数据库。

**结论与理由**：yudao 侧目前只有站内信/通知模板（`NotifyTemplateTypeEnum` 的
SYSTEM_MESSAGE/NOTIFICATION_MESSAGE），**没有出站 webhook**；而它是禅道每次业务动作后的统一出口，
价值明确且没有等价物，故判 do-it。

**如果要做，scope 是**：主表 `zt_webhook` 1 张（type/name/url/domain/secret/contentType/sendType/
products/executions/params/actions/desc…），复用 `zt_oauth`（用户 openID 绑定）、`zt_notify`
（异步待发队列，status=wait/senting/sended/fail）、`zt_log`（发送日志）。接口 9 个：
browse/create/edit/delete/log/bind/chooseDept/ajaxGetFeishuDeptList/asyncSend。
核心规则：① 按 `config/webhook.php` 的 `objectTypes` 白名单做「对象类型 + 动作」匹配，
`needAssignTypes` 决定是否 @ 指派人 openID；② `buildData` 里 products/executions 两个交集过滤；
③ 群机器人类强制 `application/json`；④ 钉钉群加签 `timestamp(ms)+\n+secret` HMAC-SHA256 +
`urlencode(base64)`，飞书群 `timestamp+\n+secret` 且 body 内塞 `timestamp/sign`；
⑤ `sendType=async` 走 `zt_notify` 落库，由 cron（`zentao.sql:2433` 的
`*/1 * * * * moduleName=webhook&methodName=asyncSend`）消费，发完删 `sended` 行；
⑥ `getDataByType` 产出 5 种报文（dingding/bearychat/weixin/feishu/default）；
⑦ `getViewLink` 的 `kanbancard→kanban`、`case→testcase` 转换与 meeting/aitask 特判；
⑧ 发送日志全量落 `zt_log(objectType='webhook')`。
前端 6 页（列表/新建/编辑/日志/绑定/选部门）。**与 yudao 对齐**：把触发挂到等价于 message 的
业务事件上，`asyncSend` 用 yudao 的 Quartz 承接（不要重造调度器，见 `cron` 一节）。
回归：单模块接口 + 界面检查后跑全量，重点验证 message→webhook 触发链与 asyncSend 消费闭环。

### `api`（2,419 行 / 32 action / 预计 2 轮）

**它在禅道里做什么**：**接口文档库**（注意不是「对外 REST 接口」）——按产品/项目/独立空间挂载
接口库，库→接口→可复用数据结构→发布版本四层；每个接口存请求参数与响应示例，发布版本时把当时的
接口与结构快照冻结，支持历史版本回溯。

**证据**：

- **表全在，且旧清单写错了**：`db/zentao.sql` 里 `zt_api`(91)、`zt_apispec`(120)、`zt_apistruct`(144)、
  `zt_apistruct_spec`(161)、`zt_api_lib_release`(79) 全部存在。禅道**从来没有 `zt_apilib` 表**——
  接口库就存在 `zt_doclib`(type='api'，第 921 行)。旧清单「`zt_apilib`/`zt_apirelease`/`*_spec`
  四张表不在开源版」是把表名猜错了，不是表没发布。此外还复用已迁移的 `zt_doclib`、`zt_module`、
  `zt_product`、`zt_project`、`zt_user`，10/10 声明表全部命中。
- **代码是真的**：`control.php` 1,141 行 / `model.php` 1,097 行 / `zen.php` 181 行，32 个 public action，
  13 个 `ui/` 页面，36 个 model 测试。`grep "TODO|FIXME|not implemented|未实现|placeholder"` 0 命中；
  所有 `echo/print` 都是 AJAX JSON 响应。`model.php:94` 真 `insert(TABLE_API)` + `:108`
  `insert(TABLE_API_SPEC)`；`:39` `publishLib` 真把 modules/apis/structs 打成 `$snap` JSON 写
  `TABLE_API_LIB_RELEASE`；`:548` `sql()` 真 `$this->dao->query()`。
- **门槛只挡 OpenAPI 互导**：7 处 edition 判断全部只否掉「Swagger/OpenAPI 导入导出」这一个付费扩展——
  `control.php:594` `if(edition == 'open') return ...editionLimited`、`:596/:598` 调
  `$this->api->loadExtension('openapiimport')`，而该扩展在开源包里根本不存在
  （`grep -rn openapiimport` 只命中 `control.php` 一行），`export/exportOpenApi/importOpenApi`
  三个 action 在开源 `control.php` 里连方法都没有。**主体能力一个不少。**
- **无外部系统**：全模块只依赖 `zt_*` 表与本机 doc 应用；`file_get_contents` 只出现在 `zen.php:178`，
  请求的是 `common::getSysURL()` 即禅道自己的同机 URL（自调用），没有 curl/GitLab/Jenkins/SVN/LLM key。
- 另有 3 个 action（`getModel`、`sql`、`debug`）是开发者在线调试入口，被 `config.php:157-158`
  默认关闭（`apiGetModel=false` / `apiSQL=false`），属可裁剪范围，不算门槛。

**结论与理由**：yudao 只有 springdoc 自动生成的自身 Admin API 文档，没有「按产品/项目挂载、
可复用数据结构、发布版本快照与历史回溯」的接口文档库。表在、实现真、依赖本地、价值独立，
旧清单的否决理由（表缺失）已被证伪，故判 do-it，唯一需如实标注的是 Swagger/OpenAPI 互导不做。

**如果要做，scope 是**：表 5 张（`zt_api` / `zt_apispec` / `zt_apistruct` / `zt_apistruct_spec` /
`zt_api_lib_release`），复用 `zt_doclib`（接口库 = doclib.type='api'）与 `zt_module`（目录树）。
接口约 29 个：库 CRUD（createLib/editLib/deleteLib）、接口 CRUD（create/edit/delete/view/index/
ajaxGetList/ajaxGetLibApiList/ajaxGetApi）、目录 CRUD 与排序（editCatalog/deleteCatalog/sortCatalog/
ajaxGetChild）、结构 CRUD（struct/createStruct/editStruct/deleteStruct/ajaxGetRefOptions/
ajaxGetRefInfo/ajaxGetParamsTypeOptions）、发布版本（releases/createRelease/deleteRelease）、
空间首页与下拉（ajaxGetHome/ajaxGetData/ajaxGetDropMenu）、getSessionID；不做 getModel/sql/debug。
前端 13 页，并入已迁移的 doc 空间。关键规则：① `title` 在 `(lib,module)` 内唯一、
`path` 在 `(lib,module,method)` 内唯一；② 任何 create/update 都往 `apispec` 追加版本记录，
主表只存当前值，查历史 join spec 按 version 过滤（`model.php:312` `getByID`）；
③ 发布 release 时把 id+version 打成 `snap` JSON 冻结，读某个 release 时以 snap 里的 version
回查 spec（`model.php:362` `getApiListByRelease`）；删除用 `deleted` 软删；
④ 数据结构 `attribute` 是可嵌套 JSON 字段树，支持引用其他 struct；
⑤ 接口库名称在同一产品/项目/空间内唯一。
验收：单模块接口回归 + 13 页界面检查，再全量回归。

### `dimension`（499 行 / 2 action / 预计 0.5 轮）

**它在禅道里做什么**：BI 的「管理维度」层——把大屏（screen）、透视表（pivot）、图表（chart）
归类到「宏观 / 效能 / 质量」三个维度下，提供 1.5 级导航下拉，并记住用户上次所在维度。
是 `chart`/`pivot`/`screen` 的真实前置依赖。

**证据**：

- **表全在**：`zt_dimension` 在 `db/zentao.sql:651` CREATE（id/name/code/desc/acl/whitelist/
  createdBy/createdDate/editedBy/editedDate/deleted，第 665 行 `CREATE INDEX code`），第 14579 行
  预置 3 行 macro/efficiency/quality 种子；常量在 `config/zentaopms.php:343`。无其它表引用，缺表 0。
- **代码是真的**：`control.php` 72 行（2 个 action：`ajaxGetDropMenu`、`ajaxGetOldDropMenu`，
  都真实取数并 `$this->display()`）、`model.php` 118 行（`model.php:29/43/56` 真实 DAO 查询，
  `:67-117` `getDimension`/`saveState` 是完整的「末次维度」持久化链）。`zen.php` 14 行
  `class dimensionZen extends dimension {}` 属框架占位而非假数据。全模块非测试 PHP 499 行。
- **门槛 0 处**：`grep -rn "edition" module/dimension/` 0 命中。
- **无外部依赖**：无 curl/HTTP/LLM/扫描器/GitLab/Jenkins/SVN；唯一上游是本地
  `biModel::getViewableObject()`（`module/bi/model.php:14-66`，纯 DAO 查 acl/whitelist）。
- **唯一缺口是开源版不含维度 CRUD/管理界面**（lang 里的 `aclList` 无调用点，
  全库只有 `upgrade/model.php:6971` 直接 INSERT `TABLE_DIMENSION`）——开源版只发布**只读的
  维度读取/切换能力**。

**结论与理由**：既非 not-possible（表与实现都在开源版、非存根、无门槛），也非 map-only
（不是设置/权限/编辑器一类，yudao 没有「维度」实体），也非 not-worth（被 chart/pivot/screen
实际消费，且是 0.5 轮的小投入）。唯一风险是开源版没有维度 CRUD，迁移时按**只读维度 + 切换语义**
实现，管理端留 P3。判 do-it。

**如果要做，scope 是**：表 1 张（`zt_dimension`，含 3 行种子）。接口 3~4 个：维度列表/可见维度、
按 id 取维度、当前维度 `getDimension`、1.5 级导航下拉（`ajaxGetDropMenu` 的
`data/link/labelMap` JSON 结构）。前端 1 个维度切换下拉组件（复用 zen 的
`items[{id,text,keys(拼音)}]` 结构）。关键规则：① `saveState` 的四级兜底链
`config->dimensions->lastDimension → session->dimension → bi.getViewableObject 校验 → getFirst`；
② 可见性过滤 `acl=open 或 createdBy=自己 或命中 whitelist`，admin 直通
（`bi/model.php:41-65`）；③ 末次维度双写 session(app->tab) + setting 项
`{account}.common.dimension.lastDimension`；④ 链接参数例外：`pivot-design` 改写为 `browse`、
`bi+tree-browsegroup` 追加 `groupID=0&type={viewType}`。
**有意不做**：维度 CRUD/管理界面（开源版无）、chartUpgrade/pivotUpgrade 的升级映射表（纯升级器数据）。

---

## 三、map-only：只做映射（10 个）

这一组不是「做不了」，而是「不需要重写」。它们的共同形态是：**yudao 已经有等价能力，
或者它本身没有独立业务边界**（调度器、后台设置、外部适配器、内部服务层）。
把它们按「表 + 接口 + 前端」重写一遍，等于在 yudao 里再造一个同功能面板，既冲突又白花成本。
交付物统一是一份映射说明，**0 张表 / 0 接口 / 0 前端页**。

> **行数口径（第 45 轮统一）**：下面每个模块标题里的行数是**核心行数** = `control.php` + `model.php` + `zen.php` + `tao.php`，
> 不含 `config/`、`lang/`、`ui/`、`view/`、`js/`、`css/`。此前 `ai` / `convert` / `admin` / `cron` 四个标题的数字
> （11,707 / 9,668 / 3,226 / 1,132）不属于任何自然口径，已按实测改正为 5,360 / 5,634 / 1,499 / 814；
> 全模块 `.php` 行数（含 config/lang 等）另见 `docs/MAP-ONLY-MAPPINGS.md` 每节的实测证据。

### `ai`（核心 5,360 行 / 27 action / 预计 0 轮）

**它在禅道里做什么**：LLM 平台层——模型接入（厂商/凭据/代理）、提示词智能体（agent + 字段 + 角色）、
AI 小程序市场、会话消息、助手编排。

**证据**：9 张 `TABLE_AI_*` 全部在 `db/zentao.sql` 有 CREATE（`zt_ai_model` 14922、`zt_ai_agent` 14940、
`zt_ai_agentfield` 14984、`zt_ai_agentrole` 15009、`zt_ai_miniprogram` 15030、
`zt_ai_miniprogramfield` 15076、`zt_ai_miniprogramstar` 15164、`zt_ai_message`、`zt_ai_assistant`），
跨模块的 `zt_bug/zt_task/zt_story/...` 也全有；**唯一缺 `zt_im_chat`**
（`module/ai/model.php:507/533/557` 在 update/toggle/delete model 时同步 IM 群，
但 `db/zentao.sql` 中 `CREATE TABLE.*zt_im_` 命中 0，`module/im` 也不在开源包内）。
代码不是存根（`control.php` 1,045 行 / `model.php` 4,283 行 / `zen.php` 32 行，27 个 public function）。
**硬依赖外部 LLM**：`config.php:20-24` 的 vendorList 与 `:31-45/:73-76` 把请求打到
api.openai.com / `*.openai.azure.com` / aip.baidubce.com，凭据需要 key/key+resource+deployment/key+secret，
`model.php:602 makeRequest()` 用 curl 直连；`model.php:45/68` 的 `getZaiBaseUrl()/generateToken()`
与 `:4181 http()` 指向自建 ZAI/知识库服务，`control.php:730` 与 `model.php:4064/4094` 还
`loadModel('zai')` 做可见性校验——没有 LLM key 时 `hasModelsAvailable()` 为 false，模块只能停在配置页。
17 处 edition 门槛把真正需要重写的能力全砍了：`model.php:2224/2284` 直接返回空数组
（工作流 function-call schema），`config/actions.php:5/7` 与 `ui/prompts.html.php:28`、
`ui/miniprograms.html.php:39`、`ui/promptview.html.php:28/162` 摘掉提示词设计向导与小程序
创建/编辑/测试/导出按钮。

**结论与理由**：它是 **LLM 平台层而非 PM 业务域**（9 张自有表没有一张业务数据表），
且必须外部 LLM 才能跑；yudao 的 `yudao-module-ai` 已基于 spring-ai 提供
`AiApiKeyController`/`AiModelController`/`AiChatRoleController`/`AiChatConversationController`/
`AiChatMessageController`/`AiKnowledge*/AiKnowledgeSegment*`（RAG）/`AiToolServiceImpl`/
`AiWorkflowServiceImpl`（function call）/`AiWriteServiceImpl`，模型接入/角色/会话/知识库/工具调用
全部覆盖。重写等于把 yudao 已有能力再做一遍。判 map-only。

**如果要做，scope 是（只做映射 + 说明）**：① 表映射：`zt_ai_model`/`zt_ai_assistant` →
yudao `ai_model` + `ai_api_key`；`zt_ai_agent` + `ai_agentfield` + `ai_agentrole` →
`ai_chat_role` + 提示词模板；把 `zentao.sql:14966` 与 `module/ai/config/init.php` 里 15 条内置智能体
（需求润色/一键拆用例/任务润色/需求转任务/Bug 润色/文档润色/Bug 转需求/拆分计划/需求评审/设计文档/
原型图/发布新闻稿/立项报告/结项报告/自动化测试脚本）原样导入为 yudao 的「内置角色」字典；
`zt_ai_message` → `ai_chat_message`；`zt_ai_miniprogram*` 不进 yudao，记为「禅道 AI 小程序市场」不迁。
② 能力映射：LLM 接入 → AiModel/AiApiKey；对话 → AiChatConversation/AiChatMessage；
知识库 RAG → AiKnowledge*；目标表单 function call → AiTool/AiWorkflow；写作提示 → AiWrite。
③ 明确不迁：8 步提示词设计向导、AI 小程序市场、`zai` 侧 skills/知识库绑定、`zt_im_chat` 同步
（后者要么被门槛挡住，要么依赖开源包不发布的 `zt_im_*`）。
④ 可选增量（业务坚持要「详情页一键 AI 生成回填」时）：只补一条薄胶水——把
`www/js/zui3/ai.js` 的按钮挂到对应详情页，后端一个 controller 拉对象字段 → 调 yudao AI → 按 targetForm
白名单写回，不做通用平台。

### `convert`（核心 5,634 行 / 18 action / 预计 0.5 轮）

**它在禅道里做什么**：**异构系统数据迁移向导**——把 BugFree 1/2、Redmine 1.1、Jira 的历史数据
（用户/项目/需求/任务/Bug/版本/文档/工时/附件/动作）一次性搬进禅道的 `zt_*` 表。

**证据**：39/39 个被引用的禅道表都在 `db/zentao.sql` 有 CREATE（`zt_action`、`zt_bug`、`zt_case`、
`zt_project`、`zt_story`、`zt_workflow*`…）；实现是真实的（`control.php` 605 行 / `model.php` 1,784 行 /
`tao.php` 3,245 行，converter 下 bugfree1 318、bugfree2 530、redmine1.1 826 行），
空函数体与 TODO 均 0 命中，14 处 `return array()` 全部是守卫（空 session/空 relation/文件不存在/
method==api 分流/edition 门槛），无硬编码假数据。**但它没有自有业务表**，全部是往已有表做一次性写入。
12 处 edition 门槛只挡 Jira 自定义字段与 Jira 工作流/流程导入
（`model.php:828 getJiraCustomField`、`:935 getJiraWorkflowActions`、`tao.php:1738/2423/2538/2623/2731/2878`），
主体在开源版可跑。**它只在连外部系统时才成立**：`model.php:34 connectDB()` 用 `new dbh($params)`
连 Jira/Redmine/BugFree 源库，`model.php:1051 callJiraAPI()` 用 domain+admin+token 走
`commonModel::http` 打 Jira REST（`/rest/api/3|2`、`/rest/agile/1.0`）；而 33 个 `JIRA_*`、
47 个 `REDMINE_TABLE_*`、11 个 `BUGFREE_TABLE_*` 指向的源库表在开源包里 0 命中。
`lang` 与两个 js 里的 Confluence 字样在 control/model/tao 中 0 命中，属未落地的前端残留。

**结论与理由**：它本质是**后台运维 / 一次性异构数据迁移向导**，与 yudao 侧的数据导入基础设施
+ 本项目自身的建库迁移是同一层能力。不选 do-it（依赖外部系统）、不选 not-possible
（模块与真实实现都在开源版、非存根）、不选 not-worth（Jira 历史数据导入对采用方有实际价值）。
判 map-only：产出「源系统对象 → yudao 实体」的字段/状态/优先级/用户/附件映射与说明即可。

**如果要做，scope 是（只做映射 + 说明，半天量级）**：① 对象对应：
Jira project→yudao project、issue/issuetype→story/task/bug、app_user/cwd_user→system_users、
worklog→effort、fileattachment→file、issuelink→对象关联、sprint(`ao_60db71_sprint`)/board→execution、
projectversion/fixversion/affectsversion→build/release；Redmine issues/journal_details/documents/
wiki_pages→story/task/bug/doc/comment；BugFree TestUser/TestModule/BugInfo/CaseInfo/ResultInfo/
TestFile/TestHistory→user/module/bug/case/testresult/file/history。
② 字段级映射以 `config.php` 的 `jiraFieldControl`（14 种 customfieldtype → control/type/length）
与 `importDeafaultValue`（bug/feedback/ticket 的默认 action/reason）为权威来源照抄。
③ 状态/优先级/用户匹配照抄 `getJiraStatusList`/`getZentaoStatus`/`getJiraAccount`/`initJiraUser`/
`processJiraUser` 的兜底策略（`jiraUserMode = account|email`）。
④ 门槛差异：开源版**不做** Jira 工作流与自定义字段导入；`!open` 的 2 处
（`tao.php:1112/1133` `importJiraIssueLink`）仅商业版建 issue link 关联。
⑤ 若客户确需搬 Jira 历史数据，另立**独立一次性脚本**（读 Jira REST/DB → 写 yudao 表），
不进 yudao 主工程，不做前端页与回归。Confluence 相关前端残留直接排除。

### `admin`（核心 1,499 行 / 22 action / 预计 0.5 轮）

**它在禅道里做什么**：系统后台/运维控制台——安全设置、弱口令检查、日志保留、功能开关、
后台导航树、表引擎修改、一次性索引 DDL、SQLite 桌面版队列、SSO 绑定、禅道官网社区绑定。

**证据**：`control.php` 740 行 / 22 action，`model.php` 516 行，`zen.php` 243 行，`ui/` 13 页
1,638 行模板。**缺表 1 张：`zt_sqlite_queue`**（`TABLE_SQLITE_QUEUE` 定义在
`config/zentaopms.php:463`，用于 `control.php:519-539 execSqliteQueue` 与
`lib/dbh/dbh.class.php:1198`，但 `db/` 下 grep 无任何 CREATE——旧清单的「表齐全」不准确）。
3 处 edition 门槛（`lang/menu.php:47` 隐藏 metriclib 索引修复菜单、`model.php:396` 加 stage 导航、
`zen.php:215` 选营销插件列表）**没挡任何业务实现**，所以不是 not-possible。代码没有编造数据的存根
（`safe`/`log`/`setModule`/`resetPWDSetting`/`tableEngine`/`checkWeak` 都做真事），
最弱处是 `zen.php:202-231 getZentaoData()` 在官网不可达时返回 `hasData=false` + 3 条硬编码插件文案、
`control.php:538` 裸 `echo 'success'`、`:389/:454` 静默吞 `PDOException`。
**大量代码是 zentao.net 云专属**：`control.php:571/656/675/711`、`model.php:27/34/65`、
`:437-452` curl 探网、`zen.php:32/55/84/116-127/137-193` 硬编码
`https://www.zentao.net`/`https://api.zentao.net`；SSO 打外部 ranzhi（`module/sso` 只有 70 行 model，
`zt_sso` 不在开源 schema）；`safe()` 里有 GaussDB 专属 plpgsql 分支（`control.php:104-169`）。

**结论与理由**：模块定义就是**系统后台/设置/运维台**，22 个 action 每一个在 yudao 都有对应物
（system config / 密码策略 / 日志保留任务 / 功能开关 / 验证码 / OAuth-SSO / 标准 InnoDB schema），
且相当一部分是 zentao.net 云专属代码，硬搬到 yudao 只会得到「没有后端可连」的代码。
判 map-only，最高价值的动作是给出「22 action → yudao 等价物（或 drop）」对照表 + 明确
「不要建」清单。

**如果要做，scope 是（不建表、不写接口、不写 Vue 页）**：一份映射/说明文档，
把 22 个 action 分三组：① **映射到 yudao 等价物**——`safe`→安全/密码策略配置；
`checkWeak`+`resetPWDSetting`→user 模块密码规则（转调已迁 user）；`log/deleteLog`→
infra 日志保留定时任务（`saveDays` 默认 30，`config.php:3`）；`setModule`→yudao 功能开关/配置
（`system.common.closedFeatures`、productER/productUR、scoreStatus、setCode）；
`ajaxGetDropMenu`+`setMenu/checkPrivMenu/setSubMenu/setTabMenu/getHasPrivLink/getMenuKey`→
yudao RBAC 菜单树（group/user 映射已交付）；`index`→yudao 工作台（离线时 `hasData=false`）。
② **作为 zentao.net 云专属删除**——`register/unBindCommunity/changeAgreeUX/getCaptcha/sendCode/
planModal/giftPackage` + `getSecretKey/getApiConfig/getSignature/checkInternet` +
`zen` 的 `syncExtensions/syncPublicClasses/syncDynamics/fetchAPI/sendCodeByAPI/certifyByAPI/
setCompanyByAPI`。③ **作为环境/运维专属删除**——`tableEngine/ajaxChangeTableEngine`（yudao schema
已是 InnoDB）、metriclib 一次性索引 DDL（归 metric 迁移 README）、`execSqliteQueue`
（`zt_sqlite_queue` 不在开源建库脚本）、`safe()` 的 GaussDB 分支、`sso`（ZDOO/ranzhi 绑定靠
`zt_user.ranzhi`，yudao 自身 SSO/OAuth 覆盖）。顺带修正旧清单第 37 行与审计节
「admin 表齐全」的说法（缺 `zt_sqlite_queue`）。不排编译/回归轮次。

### `dev`（2,772 行 / 6 action / 预计 0.5 轮）

**它在禅道里做什么**：后台「二次开发」工具集——接口文档（反射 PHP control.php 生成页面→JSON 接口文档）、
数据库表结构浏览（`SHOW TABLES` + `DESC`）、语言项自定义（写 `zt_lang`）、编辑器入口占位。

**证据**：只引用 `TABLE_LANG`（`control.php:193,202`、`model.php:548,1025,1053`）与
`TABLE_WORKFLOW`（`model.php:840`），`zt_lang`(1275) 与 `zt_workflow`(13105) 都在
`db/zentao.sql` 有 CREATE，缺表 0。**没有 zen.php**；24 个 model 方法均无空实现（awz 扫空函数体为 0），
`grep -E "^(echo|print)"` 为 0。`ui/editor.html.php` 只有 21 行（一句提示 + 一个开关按钮），
真正实现是另一个模块 `module/editor`（`control.php:25` 在未开启时 `locate` 回 `dev/editor`）。
唯一 edition 门槛 `model.php:288`（`edition != 'open'`）只决定是否额外扫描付费版扩展目录。
2772 = `control.php` 217 + `model.php` 1,212 + `config.php` 298 + `lang/` 1,045，与清单第 44 行吻合。

**结论与理由**：四块功能全部有等价物或不可搬——① api/restAPI 是 **PHP ReflectionClass 源码自省**，
Java 无对应语义，yudao 用 springdoc/knife4j；② db 表结构浏览，yudao 代码生成器已有「数据库表」页，
且禅道表结构与 yudao 完全不同；③ editor 是 21 行占位，真实实现是改 PHP 源码的 `module/editor`，
Java 侧既无对应物也不该有；④ langItem/resetLang 是界面文案落 `zt_lang`，yudao 已有 i18n 资源体系。
它属于题面 map-only 明确列举的「后台设置/编辑器，yudao 已有等价能力」，定 map-only 而非 not-worth
是因为它**本来就不是业务模块**。判 map-only。

**如果要做，scope 是（0.5 轮 = 一份对照文档，0 表 / 0 接口 / 0 页）**：① 接口文档 api/restAPI →
yudao springdoc/knife4j（`/v3/api-docs`、`/doc.html`、`@Operation`），明确说明源码反射语义不可搬、不做；
② 数据字典 db → yudao 代码生成器只读页，只保留一张可选的「禅道术语 → 中文名」对照
（来自 `module/dev/lang/zh-cn.php` 的 `$lang->dev->tableList`，约 130 项），标注为迁移期参考、不落库；
③ langItem/resetLang → yudao i18n；若需承接老系统自定义文案，只给一次性导出方案
（`zt_lang` 中 `system=0` 的自定义行导出 CSV/属性文件人工映射），不建表、不做 CRUD；
④ 编辑器 dev/editor 明确不迁；⑤ 记录 dev 挂在 admin 菜单组 dev/entry/editor 下，yudao 侧不需要对应菜单。

### `zai`（2,520 行 / 7 action / 预计 0 轮）

**它在禅道里做什么**：**AI 服务接入 + 后台配置的桥接模块**——连接外部 ZAI 服务做数据向量化、
知识库检索、用户级 AI agent 创建。

**证据**：19/19 个 `TABLE_*` 都在 `db/zentao.sql` 有 CREATE（含 `zt_ai_useragent` 15088），缺表 0；
`control.php` 251 行 + `model.php` 2,269 行，7 个 public function（setting/vectorized 两页 +
5 个 ajax），56 个函数全为真实实现，唯一写库是 `model.php:223 insert(TABLE_AI_USERAGENT)`。
2 处门槛（`model.php:196/879 edition == 'open'`）只挡「open 版不挂载私有 AI 技能、不下发 feedback
同步类型」，主流程完整可用。**强外部依赖**：`getSetting()` 在 host/appID/token 缺失时直接
`return null`（`model.php:105`），`config.php` 的 installUrl 指向 zentao.net 的 zai-install 文档，
`callAPI()`（`model.php:253-328`）拼 `$protocol.$host:$port.$path` 用 curl 打
`/v8/memories`、`/v8/memories/{id}/embeddings-search-contents`、`/v8/files/extract`、`/v8/agents`；
`createUserAgent()` 以 `execution_runtime='pi_coding_agent'`、`opencode_mode='serve'` 建外部 agent。
它只拥有 1 张表（`zt_ai_useragent`，account→agent id 映射），其余全是读已迁移表。

**结论与理由**：不能 do-it（依赖外部服务）、不是 not-possible（目录在、表齐、代码非存根、门槛没挡实现）、
也不是完全无价值的 not-worth（向量化时的按产品/项目权限过滤有参考价值）。它是「AI 服务接入 + 后台配置」
型桥接模块，yudao 已有 `yudao-module-ai` 的等价能力。判 map-only。
**口径修正**：清单第 198 行记 zai 2,907 行 / 239 model / 7 action，实测为 control 251 + model 2,269 = 2,520 行。

**如果要做，scope 是（只做映射 + 说明，0 轮编译与回归）**：① ZAI 配置
（host/port/appID/token/adminToken，存 `zt_config` 的 `system.zai.global.setting`）→
yudao `AiApiKeyController`/`AiModelController`；② 数据向量化同步
（`zaiModel::$syncTables = story/demand/bug/doc/design/feedback` 6 张表转 Markdown）→
yudao 知识库 RAG（`AiKnowledge*`）；③ 知识库检索 `ajaxSearchKnowledges` → yudao 分段检索；
④ 用户 AI agent（`zt_ai_useragent`）→ `AiChatConversation`/`AiChatRole`，该表仅 1 个字段映射、
不单独迁移；⑤ 明确不搬：外部 ZAI 客户端（`callAPI`/`callAdminAPI` 与 `/v8/*`、curl、ak-/ek- token）、
外部 agent 运行时（`pi_coding_agent`/`opencode_mode=serve`）、open 版被摘除的 feedback 同步与私有技能；
⑥ 记录可对齐的有价值规则：向量化前按产品/项目/权限过滤（`canViewObject`、`filterKnowledgesByPriv`）、
各对象转 Markdown 的字段映射（`getFieldAliasMap` 78 行、`convert*ToMarkdown` 系列）可作为
yudao 知识库文档切分的参考。

### `extension`（1,808 行 / 11 action / 预计 0 轮）

**它在禅道里做什么**：**官方插件市场客户端 + PHP 源码覆盖安装器**——从 api.zentao.net 下载插件包，
把包内文件覆盖进 `module/`、`www/`、`config/`，执行包内 PHP 钩子与 install/uninstall SQL。

**证据**：`grep -rhoE "TABLE_[A-Z_]+" module/extension` 只有 `TABLE_EXTENSION`（6 处，全在
`model.php:61/79/92/501/593/632`），`zt_extension` 在 `db/zentao.sql:1019` CREATE，缺表 0；
`zt_extuser` 虽在开源脚本里建但模块从未引用。无存根：`grep -rniE "todo|fixme|not implemented|占位|
placeholder"` 只 1 处误命中（`ui/obtain.html.php:22` 的 `set::placeholder` 输入框占位符），
`grep -rnw "echo" control.php model.php zen.php` 0 命中，`zen.php` 18 个函数、
`model.php` 28 个函数均为真实实现（`fetchAPI` 走 `common::http`、`executeDB` 拆句执行包内 SQL、
`getExpireDate` 解析 license txt、`copyPackageFiles` 扫描 zip 覆盖源码）。
门槛 0 处。**硬依赖外部市场**：`config.php:3 apiRoot = 'https://api.zentao.net/extension-'`。

**结论与理由**：它管理的「产物」是 **PHP 源码文件本身**，Spring Boot 的 JAR 不能在运行时覆盖自身
class 或执行 PHP，架构上无对应物；11 个 action 全部围绕「把可执行代码热装进宿主」，
yudao 的等价能力是构建期的 Maven 多模块 / 代码级自定义，没有也不需要运行时插件市场。
不属于 do-it（依赖外部系统 + 无真实业务价值）、不属于 not-possible（目录在、`zt_extension` 在开源
建库脚本、0 门槛、无存根），更接近 map-only：后台/系统级能力，做映射说明即可。判 map-only。

**如果要做，scope 是（不写任何代码）**：一份映射说明（约 1 条清单行 + 3~5 行备注）：
① 把清单第 46 行 extension 的「❌ 未做」改注为「映射交付（不迁）」；
② 说明 yudao 的扩展机制是构建期 Maven 模块，禅道的运行期插件包安装（覆盖源码 + 执行包内 PHP 钩子
与 SQL）在编译型 Java 服务上无对应实现，**也不建议实现**（安全上等于远程任意代码执行）；
③ 记录被有意放弃的资产：`zt_extension` 1 张表、11 个 action、`ui/` 10 个页面、api.zentao.net 接入；
④ 备注边界：若将来确实需要，等价物是 yudao 的模块化开发与发布流程，不是运行时市场。
关键规则：无。前端页数 0、接口数 0。

### `cron`（核心 814 行 / 13 action / 预计 0.5 轮）

**它在禅道里做什么**：通用后台定时任务调度器——crontab 表达式解析 → 写 `zt_queue` 排队 →
消费并执行 `moduleName/methodName` 内部调用或系统命令 → 落 cron 日志并回写 `lastTime`。
它本身没有任何业务语义，全是调度基础设施 + 一个管理 CRUD 页。

**证据**：3 张表（`zt_cron` 722 + 索引 737、`zt_queue` 1666 + 两个索引、`zt_config` 705）全部在
`db/zentao.sql`，`zt_cron` 带 17 条种子数据（2429-2447），缺表 0。`control.php` 462 行 13 个 public
+ 2 个 protected（`canSchedule:245`、`applyExecRoles:262`）；队列抢占是真的
（`control.php:395-401` `UPDATE zt_queue SET status=doing,execId... WHERE status=wait AND execId=0`
判 `affectedRows!=1` 即退出，再 `usleep(500000)` 回读复核归属，`:404-406`）。
`model.php:122` 的 `die()` 是给生成的 `cron.Ymd.log.php` 加首行执行守卫，不是占位。
门槛仅 1 处且是**反向**的：`model.php:39` `beginIF($this->config->edition != 'max')` 在非 max 版
（即开源版）列表里额外排除两条 max 专属 cron，开源版功能完整。
**真正跑起来依赖外部触发者**：开源版默认走浏览器驱动
（`module/index/js/index.ui.js:1582` `window.startCron` 轮询 `cron/ajaxExec`，
`control.php:133-172` 是 `while(true){...sleep(20);}` 常驻 AJAX 守护循环，需要一个已登录页签长期开着）；
生产部署走 RoadRunner（`roadrunner/scheduler.php:33`、`consumer.php:33`，两个入口硬校验 CLI，
`control.php:183/216` `if('cli' !== PHP_SAPI) return;`）；`control.php:440 exec($task->command,...)`
执行系统命令，默认被 `config/config.php:159 cronSystemCall=false` 关闭。17 条种子里有 11 条指向
其它模块，其中 `auditplan` 模块在本检出里 MISSING，那条 cron 是死链。测试 12 用例 + 2 fixture（904 行）。

**结论与理由**：正属题面点名的「调度器」类。yudao 已有完整等价能力且仓库内可验证：
`ruoyi-vue-pro/yudao-framework/yudao-spring-boot-starter-job/.../quartz/core/handler/JobHandler.java`
与 `JobHandlerInvoker.java`（任务处理器 SPI）、`infra_job`/`infra_job_log` 表、
`yudao-ui-admin-vue3/src/views/infra/job` + `src/api/infra/job`。重写 `zt_cron`/`zt_queue` 的排队抢占、
心跳 `execId/lastTime`、RoadRunner 双进程模型等于用 Quartz 集群模式再实现一遍，
投入产出比不合理且会与 yudao 现有调度面板冲突。判 map-only。

**如果要做，scope 是（纯文档/映射，无写码）**：① 表映射：`zt_cron` + `zt_queue` → 不建表，
对应 yudao `infra_job` + `infra_job_log`；`zt_config` 里 `scheduler.execId`/`scheduler.lastTime`/
`consumer.<execId>` 心跳行作废（Quartz 集群自处理选主与错失触发）。
② 17 条种子行 → JobHandler 清单：丢弃第 1 条空命令；第 12 条 `status='stop'`（effort.remindNotRecord）
标注默认停用；`auditplan.ajaxCreateCycleAuditplan` 因模块在开源版 MISSING 标注不可迁；
其余 14 条（metric.updateDashboardMetricLib、mail.asyncSend、webhook.asyncSend、admin.deleteLog、
program.refreshStats、product.refreshStats、weekly.createCycleReport、backup.backup、todo.createCycle、
metric.updateMetricLib、report.remind、execution.computeTaskEffort、execution.computeburn、
execution.computecfd）给出目标 JobHandler 名 + 建议 Quartz 表达式（五段式可直接搬），
并标注 mail/webhook/weekly/backup 依赖前置模块未迁移。
③ 照抄的 4 条规则：五段式 crontab 校验与取值范围（m 0-59 / h 0-23 / dom 1-31 / mon 1-12 / dow 0-6，
dom、dow 额外允许 `?LWC` / `?LC#`，`checkRule model.php:245-257`）；`type=zentao` 的
`parse_str($task->command,$params)` + moduleName/methodName 分发（`control.php:426-435`）→
Java 按 Bean 名调 JobHandler；`type=system` 的 `exec()` 默认关闭，Java 侧不开放或改白名单；
`consumeTask` 的 UPDATE 抢占 + 0.5s 回读复核由 Quartz 集群语义替代、整体删除。
④ 结论性说明：13 个 action 里只有 index/create/edit/toggle/delete/turnon/openProcess 是人机界面，
其余 6 个是进程循环入口，全部不需要在 yudao 重建。

### `misc`（900 行 / 15 action / 预计 0.5 轮）

**它在禅道里做什么**：「系统-管理」杂项工具箱——验证码、心跳、phpinfo/about/changelog/features
静态页、更新检查与官网埋点、插件市场到期提醒、表检查修复、一键安装包扫描、前端偏好（展开态/
忽略浏览器提示/已读记录）。

**证据**：9/9 引用表（config/user/project/task/product/story/bug/case/doc）全部在 `db/zentao.sql`
有 CREATE（281 张表逐个 grep 命中），缺表 0，模块无自有业务表。门槛 1 处。
**明确存根/断链**：`zen.php:23 return 'hello world from hello()<br />';`（由 `control.php:110-113`
`checkExtension()` 直接 echo，注释自述「检查模型扩展逻辑」）；`control.php:34-37 phpinfo()` 一行透传；
`:218-224 checkNetConnect()` 只 `print($check ? 'success' : 'fail')`；`:344-350/359-365` 与
`model.php:444-473` 只做官网埋点外发。**孤儿**：`config/privilege.php:373-376` 引用的
misc.ajaxgetclientpackage/ajaxgetpackagesize/ajaxsetclientconfig、`:172` 的 misc.qrcode、
`module/common/model.php:3114` 生成的 `misc/downloadClient` 链接在 `control.php` 里均无定义
（`grep -ci "function <name>"` 全 0），对应 `ui/downloadclient.html.php`、`view/getsid.html.php`、
`view/links.html.php` 是无 action 可渲染的孤儿视图。`model.php:50 getRemind()` 全仓仅被自身测试引用，
`model.php:97 getMetriclibRemind()` 开源版直接 `return ''`。

**结论与理由**：表与代码都在开源版、`edition != 'open'` 0 次，不属 not-possible；但模块性质是
「系统-管理」杂项工具箱，相当一部分在开源版里已是存根、断链或纯官网埋点，
要么 yudao 已有等价能力（验证码/ping/phpinfo/表运维/DB 运维），要么本就不该搬
（api.zentao.net 埋点、禅道插件市场、一键安装包扫描）。写代码不如写清映射。判 map-only。

**如果要做，scope 是（0 张表 / 0 接口 / 0 前端页）**：逐条覆盖 15 个 action + 8 个 model 方法：
① 映射到 yudao 已有能力——captcha→aj-captcha；ping→会话/网关心跳；phpinfo→不迁或运维端点；
about/changelog/features→不迁或静态页；ajaxSetUnfoldID/ajaxIgnoreBrowser/ajaxSaveViewed→
前端 localStorage 或用户偏好；getTableAndStatus→yudao DB 运维/监控；getRemind→report 已迁，删除。
② 明确删除项与理由——checkUpdate/getLatestVersionList/checkNetConnect/ajaxSendEvent/installEvent/
sendInstallEvent/getStatisticsForAPI/encodeStatistics（依赖 api.zentao.net、www.zentao.net、
api.zentao.pm、qucheng.com 等禅道官网，含 pack/bin2hex 埋点编码）；getPluginRemind（禅道插件市场）；
getMetriclibRemind（`model.php:97` paid 门槛）；checkOneClickPackage（一键安装包专用，
扫 zentaobiz/zentaoep/zentao max 兄弟库的 admin/123456，与 yudao 部署形态无关）。
③ 标注开源版即为存根/断链、不迁：checkExtension(hello world)、downloadClient/qrcode 等 5 个
未定义 action 及孤儿视图。关键规则：唯一有「数据落点」的是 ajaxSetUnfoldID 写 `zt_config`
（owner/module/section/key 条件 + json 合并语义），迁移若保留改用 yudao 用户偏好而非 `zt_config`。

### `jenkins`（204 行 / 0 action / 预计 0 轮）

**它在禅道里做什么**：Jenkins 服务端适配器——触发参数化构建、查 job 树、查队列与构建号、取
console 日志。它没有自己的页面、没有自己的表，是 CI 集成的一个 driver。

**证据**：`control.php` 29 行只有 `__construct`（**0 个 action**），`zen.php` 14 行空类，
无 `view/`、无 `ui/`、无 `config/`；`module/common/lang/common.php:276` 的 `$lang->noMenuModule`
明确把 jenkins 列为**无菜单模块**。2 张表（`ops_pipeline` 15711、`ops_pipeline_executions` 15753）
都在 `db/zentao.sql`，缺表 0，门槛 0 处。`model.php` 6 个方法（getDepthJobs/checkParameterizedBuild/
apiCreatePipeline/apiGetExecInfo/apiGetJobNumberByQueueID/getLogs）全是真实 `common::http` 调用
（`/api/json`、`config.xml`、POST 建 job、`/queue/item/{id}/api/json`、`consoleText`，
带 `CURLOPT_USERPWD`/Basic Auth），不是存根，但**没有外部 Jenkins 服务就完全跑不起来**。
它的注册与增删改查实际在 `module/provider`（Jenkins 作为 type 枚举），调用方只有未迁移的
`pipeline`/`ci`（`pipeline/model.php:459/462/490`、`pipeline/zen.php:198`）。
`zen.php:12-14` 的空类体与 `test/lib/zen.class.php:57/75` 仍反射调用 `jenkinsZen::buildTree()/
checkTokenAccess()`——已消失的方法，死引用。

**结论与理由**：不选 do-it（依赖外部 Jenkins 服务）；不选 not-possible（模块目录存在、2 张表都在
开源建库脚本、门槛 0、model 保留真实实现）；不选 not-worth（CI 集成有真实价值，且 yudao 未内建
Jenkins 能力，谈不上被覆盖）。它是外部服务适配器，正确动作是「映射 + 说明」：
把 Jenkins 服务器登记映射为 yudao 的外部服务/配置实体，`ops_pipeline.engine='jenkins'` 作为枚举口径记录，
6 个 HTTP 方法作为 pipeline 迁移时的适配器片段随迁，不单独重写。判 map-only。

**如果要做，scope 是（只做映射与说明）**：① 表：不引入新表（`ops_pipeline`/`ops_pipeline_executions`
归 pipeline 所有）；② 接口：0 个 action、后端 API 0 个，登记「Jenkins 作为外部服务提供方」→
yudao 的外部服务/配置实体（对应禅道 provider type='Jenkins'，需 account+token，baseURL 校验走
`/api/json`）；③ 前端：0 页；若 pipeline 前端需要 engine 下拉，`'jenkins'` 作为 provider/engine
枚举项照抄（`pipeline/lang/zh-cn.php:137 engineList['jenkins']`）。
④ 照抄的规则：auth 用 `base64_decode(token)` 作 `CURLOPT_USERPWD`（`model.php:110/150`）；
建 job 后从 Location 头正则 `!Location: .*item/(.*)/!` 取 pipeline 名（`:93`）；
用 queueID 轮询 `/queue/item/{id}/api/json` 取 `executable.number`，未分配返回 0（`:129-135`）；
参数化构建判定靠 config.xml 含 `hudson.model.ParametersDefinitionProperty`（`:74`）；
日志取 `{buildNumber}/consoleText` 并回写 `ops_pipeline_executions.logs`（`:147-159`）；
递归取 job 树深度上限 4、按 `_class` 含 `.multibranch/.folder/.OrganizationFolder` 判非 job、
`buildable` 为真才算 job（`:25-59`）。
⑤ 本模块自身 0 接口 0 页面，无独立回归项，仅在 pipeline 迁移时做 Jenkins 引擎联调。

### `mark`（121 行 / 0 action / 预计 0 轮）

**它在禅道里做什么**：跨模块的通用「用户级对象标记（seen flag）」服务层——按
「账号 + 对象类型 + 对象ID(+版本) + 标记名」记录用户对 BI 透视表（pivot）的已查看/版本标记，
用来渲染「新 / 新版本」小红标。

**证据**：`module/mark/` 只有 1 个业务文件 `model.php`（121 行）+ `test/`，
**没有 control.php、zen.php、lang/、config/、view/、ui/、js/、css/**，即不可路由、无任何页面入口
（action 0 个）。表：只引用 `TABLE_MARK` 一个常量（`config/zentaopms.php:315`），
`zt_mark` 在 `db/zentao.sql:15431` CREATE 并建 `idx_object`/`idx_account` 索引（15442-15443），
缺表 0。门槛：`grep -rn "edition" module/mark/` 退出码 1，模块内部完全无授权门槛
（门槛在消费方 pivot 里，`module/upgrade/config.php:415` 的 `$config->upgrade->openModules`
明确含 `'mark'`，证明它随开源版发布）。**无存根**：5 个 public 方法
（getNeededMarks/getMarks/isMark/hasMark/setMark）全部是真实 SQL
（`model.php:29/58/80` select、`:116` insert），另有完整单测 5 个 + 4 个 yaml 数据集。
唯一瑕疵是 `model.php:119 return dao::isError();`——布尔语义反了（出错才返回 true），
属真实逻辑的潜在 bug。消费者只有 pivot：`grep -rn "loadModel('mark')"` 全仓仅 6 处，
全在 `module/pivot/{control.php:101,102; zen.php:93,143,214,216,243}`。

**结论与理由**：它不是有独立边界的业务模块，而是通用服务层——没有任何接口、没有任何页面可迁移、
没有可回归的单模块界面，因此「表 + 接口 + 前端 + 接口回归 + 界面检查」这套 do-it scope 根本无从书写。
其能力在 yudao 里用一个极简的「按用户记录某对象某版本已读」字段/表即可等价表达。
正确动作是映射 + 说明，而非重写。判 map-only。

**如果要做，scope 是（仅映射 + 说明，不写代码）**：① 在迁移清单中把 mark 标注为「内部服务层，
非业务模块，无接口无页面」；② 记录表映射 `zt_mark` → yudao 侧按需的「用户已读/已查看标记」表或字段
（objectType/objectID/version/account/mark/extra/date 八个字段，含 `idx_object(objectType,objectID)`
与 `idx_account(account)` 两个索引语义）；③ 记录语义映射：`mark='view'` 表示已看过该版本
（pivot 用于去掉「新版本」红标）、`mark='version'` 表示用户点开过版本（hasVersionMark）；
④ 标注已知瑕疵 `model.php:119 setMark` 返回 `dao::isError()` 语义相反，迁移时勿照抄；
⑤ 说明真实消费者是 pivot（BI 透视表，开源版仍按 `grade==1` 过滤），若将来不迁移 pivot/BI，
则 `zt_mark` 及其服务可整体不建、不留空表。

---

## 四、not-possible：开源版无实现可搬（3 个）

这一组的判定标准是「**开源包里没有可搬的实现**」，三种形态各占一个：
模块只剩存根（`feedback`）、真实逻辑全在外部服务（`codescan`）、真实实现被上游删除（`gitlab`）。
三者的共同点是——**不是「再花时间就能补上」，而是先得拿到别处的东西**：
付费版源码、外部 GitFox 服务、或已被删除的 2,500 行上游代码。

### `feedback`（44 行 / 0 action / 预计 0 轮）

**它在禅道里做什么**：反馈管理——从外部（客户/客服）收集反馈，评审后转化为
Bug/需求/任务/工单/待办，并做状态同步。这是付费版（biz/max/ipd）的能力。

**证据**：开源包里 `module/feedback/` **只有 44 行 model.php 一个文件**，
没有 `control.php`、`zen.php`、`tao.php`、`view/`、`ui/`、`lang/`、`config/`，action 0 个。
`model.php:6 getFeedbackPairs($type)` 直接 `return array('admin' => 'Admin', 'user1' => 'User1')`
（硬编码假数据），另两个方法只是 5 行 DAO select。
**并且这个存根是不完整的**：其它模块调用 12 个不同的 feedbackModel 方法，其中 6 个在本文件里
根本不存在——`getByID`、`getGrantProducts`、`setMenu`、`updateStatus`、`getUserFeedbackPairs`、
`updateSubStatus`（逐个 `grep -q "function <m>"` 全部 MISSING），调用方包括
`module/bug/model.php:539`、`module/todo/model.php:74`、`module/tree/zen.php:55`、
`module/product/zen.php:472`、`module/file/model.php:704`、`module/search/tao.php:657`、
`module/zai/model.php`、`module/upgrade/model.php:9346`——**运行时被调用的 model 不在开源树里**。
表结构在（`zt_feedback` 2603、`zt_feedbackview` 2713），`db/zentao.sql:3617+` 还预置了 99 条
`feedback-*` 权限行、`config/apiv1.php`/`apiv2.php` 仍路由 `/feedbacks*`——全是死路由。
真实逻辑被 edition 挡住：模块内 `edition != 'open'` 为 0 是因为**没有代码可挡**，
但 `module/bug`、`module/todo` 等处共 38 行用 `$this->config->edition != 'open'` 守卫 feedback 调用，
`module/group/packagemanager.php` 把 35 条 feedback 权限标 `'edition' => 'biz,max,ipd'`——
**门槛挡的正是整个 feedback 生命周期与反馈→bug/story/task/ticket/todo 转化链，即全部业务价值**。

**结论与理由**：这是「实现被排除在开源包之外」的典型，与 `MIGRATION-INVENTORY.md:250/:134`
的判断一致。没有外部系统依赖（存根里没有 curl/exec/DB 引用），所以不是「需要基建」，
而是**付费版源码未发布**。要做得先拿到 biz/max/ipd 源码；照 40 列 `zt_feedback` 从零写
是净新增产品设计，不是迁移。判 not-possible。

**如果要做，scope 是**：**当前无物可迁**。仅允许映射/说明：记录 `zt_feedback` + `zt_feedbackview`
在开源 schema 里存在但 Java 目标**有意不建**（建空表只会虚高表覆盖率而无读写方），
记录 feedback→{bug,story,task,ticket,todo} 转化语义是付费版触点，
并说明已迁的 bug/todo 模块带的 `feedback` 外键列在开源版里语义是惰性的。
只有拿到 biz/max/ipd 源码才重新评估，届时是完整模块（~40 列 `zt_feedback` + `zt_feedbackview`，
约 25~30 个 action，外加转化链）。

### `codescan`（3,659 行 / 42 action / 预计 0 轮）

**它在禅道里做什么**：代码扫描（SAST）的**禅道侧 UI + REST 代理**——规则/规则集/方案/计划/任务/
问题全部由外部 GitFox 服务存储与执行，禅道只做展示与「扫描问题转 Bug」。

**证据**：模块目录在（`control.php` 1,547 行 / 42 public function = 41 action + 构造函数，
`model.php` 1,170 行 / 53 public function，`zen.php` 942 行，`ui/` 35 个模板 3,533 行），
**门槛 0 处**——`grep -rn "edition" module/codescan/` 与 `grep -rniE "edition\s*[!=]="` 均 0 命中，
旧清单「被 edition != 'open' 挡住」不成立；`db/zentao.sql:12833` 起把 codescan 权限写进开源版权限包，
`module/group/packagemanager.php:4403-4441` 的 codescan-* 权限标 `'edition' => 'open,biz,max,ipd'`，
开源版确实带这个模块。**但开源包里没有自包含实现**：
`control.php:14-18` 构造函数第一件事就是
`$serverHeath = $this->loadModel('gitfox')->checkHealth(); if(!$serverHeath) return $this->locate(...installGitFox)`；
`config/config.php:246-248` 默认 `gitfoxURL='http://localhost'` / `gitfoxPort=3000` /
`gitfoxVersion='2.0'`；`module/gitfox/config.php:128-131` 从
`https://pkg.zentao.net/gitfox/<version>/linux-amd64.zip` 下载独立二进制，
`module/gitfox/control.php:75-140 installGitFox` 负责安装外部服务。
业务逻辑全在外网：40+ 个 model 函数只是 `gitfox->request('/scan/...','POST',...)`，
`grep -rhoE "/scan/[^'\"]*"` 得到约 44 个 `/scan/*` 端点；禅道本地只读 `zt_bug`
（`model.php:883/932/1011/1030`）用于「扫描问题转 Bug」和 `zt_user` 做解决人排行。
模块只用 `TABLE_BUG`/`TABLE_USER` 两个常量，二者都在 `db/zentao.sql`（331/2272），缺表 0；
`db/zentao.sql` 里确有 14 张 `ops_scan_*` 表（16026-16291），但 PHP 侧从不通过 DAO 访问
（`grep -rn "this->dao" module/codescan/` 只命中 4 处 `TABLE_BUG`），只走 GitFox REST。

**结论与理由**：搬过来的只是一层 API 客户端，离开 GitFox 服务（连同它的二进制与数据库）完全跑不起来。
若做成 do-it，等于在 yudao 里重写一个 SAST 引擎并自带 GitFox，远超迁移范围。
判 not-possible（并修正旧清单的「edition 门槛」描述）。0 行存根/0 表，scope 为登记与说明：
若 yudao 侧确实需要代码扫描，唯一路径是**新建能力**（对接 GitFox 2.0 API 或引入 SonarQube/Semgrep
等 SAST），不属于本次 PHP→Java 迁移范围。

### `gitlab`（1,578 行 / 0 action / 预计 0 轮）

**它在禅道里做什么**：GitLab SaaS 集成——项目/用户/组/分支管理、Issue 导入、推送 Webhook、
流水线触发与日志。它是 `pipeline` 模块的一个外部服务 driver（`ops_provider.type=gitlab`）。

**证据**：**真实实现已被上游整包删除**。`git log --oneline --diff-filter=D -- module/gitlab/control.php`
→ `53c5c88791 + [task#156063,156039] remove module.`（2026-01-21）；
`git show --stat 53c5c88791 | grep gitlab` 的删除清单：`model.php` -2,510 行、`control.php` -1,369 行、
`zen.php` -367 行、`config.php` -124、`config/dtable.php` -233、`config/form.php` -92、
24 个 `ui/*.html.php`、`js/` 25 个、`css/` 10 个、`lang/` 5 个、`test/` 130+ 个，
以及 `lib/scm/gitlab.class.php` -1,100 行。当前 `ls module/gitlab/` 只剩 `lang/ model.php test/`，
**没有 control.php**，`model.php` 仅 249 行 8 个方法（apiGet/addPushWebhook/isWebhookExists/
checkTokenAccess/apiCreatePipeline/apiErrorHandling/apiGetExecInfo/getLogs），
`find module/gitlab -name "*.php" | xargs wc -l` = 2,065 行（model+lang = 249+1,329 = 1,578 行，
与清单第 60 行吻合）。模块内门槛 0 处（`grep -rni edition` 0 命中）。表：只引用
`TABLE_PIPELINEEXEC` → `ops_pipeline_executions`（15753，有）与 test fixture 的 `zt_oauth`（1348，有），
缺表 0，模块无自有业务表。**测试是纯 mock 存根**：`test/lib/model.class.php` 注释自述
「本类所有测试方法均为纯 mock，不需要真实 model 实例」，`apiGetTest()` 直接返回字符串 `'success'`，
`checkTokenAccessTest()` 比对写死的 host/token（10.0.7.242 等），`getApiRootTest()` 返回写死的
`https://gitlabdev.qc.oop.cc/...`。**跨模块调用指向已删除方法**：`module/repo/tao.php:289`
`apiGetProjects`、`repo/model.php:2448 apiGetByGraphql`、`:301 apiCreateProject`、
`repo/control.php:1731 getFileLastCommit` 全树无定义（`lib/scm/gitlab.class.php` 已删）。

**结论与理由**：开源版里没有可搬的 gitlab 模块实现——0 action、0 页面、0 自有表，
没有「表 + 接口 + 前端」可迁移面；残余 249 行是打外部 GitLab 服务的 HTTP 包装，
硬依赖真实 GitLab 服务器 + Token。旧清单把它列入「可以继续迁移」是**误判**：
它只查了 `TABLE_*` 与 edition 计数，漏查「模块目录是否真有实现」。
判 not-possible。scope 为登记：把清单第 60 行 `gitlab` 从「❌ 未做 P2」改为「无法迁移 / 已被 OSS 移除」，
并在审计表补一行说明删除提交与残余范围。
若未来要接 GitLab 流水线，正确做法是照 `pipeline`+`ops_provider`（表已建）在 yudao 侧实现通用
「外部 CI 服务提供方」适配器，GitLab 只是其中一个 driver（其余 driver 见
`provider/model.php:179-187`：GitLab/Gitea/Gogs/Subversion/GitHub/Jenkins），**不是迁本模块**。

---

## 五、not-worth：不值得迁（7 个）

这一组要特别说清「**不迁不是偷懒**」：这 7 个模块的代码是真的、表也大多在开源版，
取证过程中**没有发现「其实是存根」或「架构上不可能」的借口**。它们被判 not-worth 是因为
**价值不成立**——搬过去的目标产物在 Java 侧没有意义，或只剩一个点不动的空壳：
`upgrade` 是把禅道自己的历史 schema 漂移补丁链（10 年债）照搬成 Java 死代码；
`tutorial` 是教学假数据层，生效前提是返工 220 处已迁模块；
`aiapp`/`gitfox`/`zahost`/`zanode` 剥掉外部服务后只剩 UI 外壳；
`provider` 的下游 `pipeline` 整个未迁。**投入产出比不合理，所以不做**——
这与 not-possible 的「没有实现可搬」是两回事，与 do-it 的「做不了才不做」也是两回事。

### `upgrade`（30,114 行 / 35 action / 预计 0 轮）

**它在禅道里做什么**：禅道**自身的版本升级向导**——把老版本禅道实例的数据库与数据阶梯式升到
当前代码版本。`control.php index()`（37-52 行）先检查 `www/upgrade.php` 入口，再
`getOpenVersion($this->config->installedVersion)`，版本 ≤6.4 才走 license，随后跳 backup；
主链 `selectVersion(86)` → `confirm(132)` → `execute(158)` → `afterExec(583)`；
`model.php getVersionsToUpdate(68)` 用 `$this->lang->upgrade->fromVersions` + `version_compare`
生成待执行版本列表，`execSQL(1224)` 逐个执行 `db/update{VERSION}.sql`（`db/` 下实测 182 个
`update*.sql`）；`model.php` 13,696 行 / 352 个方法，大量是逐版本数据修复
（updateNL1_2/updateNL1_3/updateTasks/updateCases/addPriv4_0_1/addORPriv/toLowerTable/addFlowFields…），
另有 backup(73)、checkExtension(707)、consistency(678)/fixConsistency、safeDelete(828)、
moveExtFiles(794)、mergeProgram(321)（老 productline → program 合并向导）、to18Guide(280)、
upgradeDocs(895)/upgradeDocTemplates(967)/upgradeProjectReports(1045)。

**证据**：136 张唯一引用表中 131 张在 `db/zentao.sql`（`grep -ohE 'TABLE_[A-Z_]+'` 得 142 个，
减去 4 个 information_schema 列名，经常量映射得 136 张，去 `zt_` 前缀与 281 条 CREATE 比对命中 131）；
**缺 5 张**：`dashboard`、`im_chat`、`im_chat_message_index`、`im_chatuser`、`im_message`
（`grep -icE "zt_dashboard|zt_im_chat|zt_im_message" db/zentao.sql` 0 命中，只出现在
`db/update17.4.sql` 与 `db/standard/zentao18.11+*.sql`，而 `config/config.php:76` 明确
`$config->db->fileName = 'zentao.sql'` 才是安装库；实际被升级器读写于 `model.php:5799…6574`、
`:7208/7481/7654`）。**无存根**：空方法体扫 0、单句 return 扫 0、`echo` 仅 5 处（`control.php:770`
json、`:783` 真 DB 取值、`:859/872/885` 的 `ok` 紧跟真实写入逻辑）。
12 处 edition 门槛挡的都是非开源能力（workflow/审批流扩展字段与对象关联、ipd 分支、PMS 阶段），
剩给开源版的核心就是「执行 SQL 补丁 + 数据整形」。**不依赖任何外部系统**：
全模块无 curl_/fsockopen/http_get/SDK，唯一跨模块调用是 `model.php:13627`
`loadModel('repo')->migrateRepoData()`（纯本地表搬运）；`processGitlabRepo(4668)` 是纯本地 DB 改写。
**清单笔误**：第 136 行称 `zt_deliverable` 开源版没有，实测 `db/zentao.sql` 有——本次复核为「有」。

**结论与理由**：表基本齐全（131/136）、实现真实、零外部依赖，**但业务价值在 Java 目标上不成立**：
本模块的全部输入是「禅道旧版 schema 的历史漂移」，全部输出是「禅道新版 schema」；
yudao 的 schema 由自身 Flyway/迁移体系管理，**不存在「Java 版禅道从 1.0 增量升到 21.x」这一场景**——
本项目对禅道数据的处理是**一次性导入**，而不是把 `update0.1.sql…update21.x.sql` 这条 10 年历史
补丁链照抄进 Java。照搬 = 把上游历史债照搬成 Java 死代码。不选 do-it（价值不成立）、
不选 map-only（它带 18 个 UI 页 2,082 行与 35 个 action，有界面与接口外形，不是后台设置/调度器一类）、
不选 not-possible（实现真实完整）。判 not-worth，并**推翻旧清单第 2 行/第 102/137/158 行**
「可继续迁移 P2」的结论——旧判断只看了表齐全与门槛次数，未评估「Java 侧没有可升级的禅道旧库」这一前提。

**如果要做，scope 是**：**建议零代码迁移**。仅在 `MIGRATION-INVENTORY.md` 增补一条映射说明（≤5 行）：
本模块 = 禅道自身的「DB 补丁 + 数据整形」升级器；Java 侧对应物 = yudao 自带的 Flyway/迁移体系
+ 本项目既有的「禅道 → yudao」一次性导入脚本，不需要任何 upgrade 业务功能。
因此 0 张表 / 0 个接口 / 0 个前端页。唯一可选产出：把「禅道专有历史脏数据整形规则」中少量通用规则
摘给一次性导入脚本复用（按需，非本模块交付物）——`updateCases(1019 lastRun/lastResult 回填)`、
`updateActivatedCountOfBug(999)`、`processTaskFinish`、`initTaskRelation/initReleaseRelated/
processObjectRelation`（关系表重建）、`upgradeStage4PMS(12785)`、`updateProjectType(1038)`、
`toLowerTable(1578)`。若未来确需兼容「客户自带老禅道库」，正确做法是在导入器里写针对性 ETL。

### `tutorial`（16,700 行 / 8 action / 预计 0 轮）

**它在禅道里做什么**：新手教程/引导向导——`lang` 自述「使用教程」/「通过完成一系列任务，
快速了解禅道的基本使用方法」。它的作用是在 `tutorialMode` 下**拦截其它模块的查询、返回假数据**
以便教学演示。

**证据**：**整模块存根**。`model.php` 2,640 行里 **43 处 `new stdclass()`** 手工捏造假对象
（行号 68,148,279,…,2629），只有 2 处真实 SQL（`model.php:28` 读 `TABLE_ACTION`、
`:784` 读 `TABLE_CONFIG`）。硬证据：`model.php:42-45 getProductPairs()` → `return array(1 => 'Test product')`；
`:54-57 getModulePairs()` → `return array(1 => 'Test module')`；`:66-106 getProduct()` 写死
`id=1/name='Test product'/code='test'/createdVersion='8.1.3'`；`:794-850 getTask()` 写死
`name='Test task'/openedBy='admin'`。`lang/zh-cn.php` 的 `dataNotSave = "教程任务中，数据不会保存。"`
是官方自述。表只有 `zt_action`(17) 与 `zt_config`(705)——都是禅道通用基础表，非 tutorial 专属，
缺表 0。1 处门槛（`config/zenguides.php:26` `if(edition != 'open')`）只影响「反馈管理」一条向导，
不挡教程主体。**耦合面最广**：`grep -rn "isTutorialMode" --include=*.php module/` 共 **220 处**，
横跨 30+ 模块（common/product/project/execution/story/task/bug/doc/testcase/testtask/branch/
productplan/release/design/tree/user/kanban/stakeholder/qa…），其中绝大多数是已迁模块。
`grep -rni "curl|gitlab|jenkins|svn|http://|apiKey|token|openai|llm|docker|k8s"` 模块内 0 命中。

**结论与理由**：正属题面 not-worth 定义里点名的「新手教程」。
① 无业务价值可搬（43 处假数据、2 处真 SQL、明示不落库）；
② 迁移成本极高——要和 yudao 生效必须把 220 处已迁模块的查询入口再改一遍；
③ 与 yudao 能力重叠方向明确——新手引导属纯前端 onboarding overlay，不需要后端领域模型，
禅道的价值主要靠 8,094 行静态步骤脚本 + 708 行 `index.ui.js` + 2,060 行文案撑着，可丢弃；
④ 唯一门槛只屏蔽 feedbackManage，不是 not-possible；⑤ 无外部依赖，不是 not-possible；
⑥ 没有自己的表，连「建表」这一步都没有产出，也不是 do-it 的最小工作量。
判 not-worth。**口径**：清单第 6 行记 16,434 行，JSON 取证为 16,700 行，以取证为准。

**如果要做，scope 是**：**不迁移**（0 张新表 / 0 接口 / 0 前端页）。只在清单留一条说明：
① 是「新手教程/向导」教学层，复用通用表 `zt_action`/`zt_config`，无专属表结构，故无 DDL 工作；
② 2,640 行 model 为纯假数据存根（43 处 `new stdclass`，仅 2 处真实 SQL），业务价值为 0；
③ 生效前提是 220 处跨模块 `tutorialMode` 钩子（覆盖已迁模块），照搬会强制返工所有已迁模块；
④ 唯一门槛仅屏蔽 feedbackManage 一条向导，非功能主体；⑤ 无外部系统依赖。
建议把清单里 tutorial 从「可继续迁移」移出，归入「不建议迁移（教学/演示层）」。
可选替代：如业务方确需新手引导，由前端在 yudao 侧实现 onboarding overlay（不需要后端接口）。

### `zanode`（4,046 行 / 28 action / 预计 0 轮）

**它在禅道里做什么**：自动化测试执行节点（KVM/容器虚拟机）的控制面板——创建/启动/关闭/挂起/
恢复/销毁节点、制作与快照还原镜像、获取 VNC、安装 ZTF 自动化框架并执行脚本。

**证据**：**事实层完全满足「真模块」**：`control.php` 649 行 / `model.php` 882 行 / `zen.php` 201 行 /
`tao.php` 60 行，模块内 PHP 合计 4,046 行（含 11 个 `ui/` 页与 4 个语言包），
`control.php` 有 29 个 `public function`（28 action + `__construct`）；5 个 `TABLE_*`
（`zt_action` 17、`zt_testresult` 2137、`zt_host` 2739、`zt_image` 2780、`zt_automation` 2802）
全部命中 `db/zentao.sql`，缺表 0；`grep 'edition'` 全模块 0 命中；无存根
（`return false/''` 全是真实错误分支，4 个短函数均为正常单表读写）。
**但功能完全外置**：`model.php` 8 处 + `zen.php` 4 处 `commonModel::http` 调远程 ZAgent API
（`/api/v1/kvm/create`、`/virtual/getVncToken`、`/kvm/exportVm`、`/task/getStatus`、
`/kvm/addCreateSnap`、`/service/setup`、`/jobs/add` 等，鉴权头 `Authorization:$node->tokenSN`，
超时 10s），依赖宿主机 ZAgent/ZVM 服务、KVM 虚拟化、noVNC+websockify
（`ui/getvnc.html.php:17` 直连 `http://$url/novnc/vnc.html`）与 ZTF 框架；
安装命令从外网 pkg.qucheng.com 拉 `zagent.sh`/`zagent-vm.ps1`（`config.php:initBash/initPosh`，
含默认端口 55001、默认账号 z/admin 与硬编码默认口令）；**无本地降级实现**——连不上 agent 直接
`notFoundAgent`/`createVmFail`。

**结论与理由**：它是外部基础设施的控制台。yudao/ruoyi-vue-pro 没有任何等价的虚拟化编排能力
（不是 map-only 能覆盖的），迁移范围也不含重建 ZAgent+KVM+noVNC+ZTF 平台，照搬只能得到一套
点不动的空壳。not-possible 的三条判据（目录不存在／表不随开源包发布／仅存根且被门槛挡住）
逐条为假，故只能是 not-worth。不纳入迁移范围（0 表 / 0 接口 / 0 前端页），仅在清单登记
「外部基础设施依赖，跳过」。若后续确要迁，前置硬条件必须先自建或对接 ZAgent 服务 + KVM 宿主机 +
noVNC/websockify + ZTF；届时迁 `zt_host`、`zt_image`、`zt_automation` 3 张表，
后端约 28 个接口，前端 11 页，以及 12 处 ZAgent HTTP 调用、tokenSN 鉴权、
开机/休眠/恢复/销毁状态机、快照默认还原规则（`model.php:770` isDefault 快照禁止编辑/删除）、
`zt_automation` 与 `testcase.automation`/`testtask.cases` 的联动。

### `aiapp`（2,198 行 / 9 action / 预计 0 轮）

**它在禅道里做什么**：AI 小程序广场与收藏——小程序列表/分类、详情聊天视图、收藏、历史消息。

**证据**：3 张直接引用表（`zt_ai_miniprogram` 15030、`zt_ai_message` 15065、
`zt_ai_miniprogramstar` 15164）全在 `db/zentao.sql`，经 ai model 间接引用的 `zt_ai_model`、
`zt_ai_miniprogramfield` 等也全在，缺表 0；`edition` 判断 0 处；无存根
（`control.php` 217 行 / `model.php` 198 行，9 个 `public function` = 8 action + 构造函数，
model 全为真实 DAO，`test/model` 下 8 个真实单测 + 10 个 yaml 期望集）。
**但依赖外部系统才能跑**：`miniProgramChat` 只做参数校验后调 `$this->ai->converse()`
（`module/ai/model.php:1014`），最终走 `makeRequest()` 的 curl 打外部 LLM，前提是 `zt_ai_model`
里已配好 API Key 且 enabled，否则直接返回 `noModelError`；`conversation` 与 `models` 两页 PHP
只有 `$this->display()`，数据全来自前端 `zui.AIPanel.shared`（`js/conversation.ui.js:3`、
`js/models.ui.js:18/24`）即外部 ZAI 云服务；`browseMiniProgram` 的聊天视图也是
`window.aiBrowseMiniProgram.initAIChatView`（`ui/browseminiprogram.html.php:232`）这个 ZAI 前端 SDK；
`toolkit` 是纯静态 Markdown 文档。**真实业务实现在未迁移的 ai 模块**：aiapp 核心只有 415 行
（217+198），`getMiniProgramByID`/`getMiniPrograms`/`getMiniProgramFields`/`collectMiniProgram`/
`hasModelsAvailable`/`converse` 全在 `module/ai`（1,045+4,283 行）与 `module/zai`（2,269 行）；
`ai/config/actions.php:7` 把 editminiprogram/testminiprogram/exportminiprogram 划归非 open 版。

**结论与理由**：不是 not-possible（有实现可搬）、不是 map-only（带表带 UI 的业务模块），
判 not-worth 的三条硬证据：① 依赖外部系统（外部 LLM + ZAI 云服务）才能跑；
② 真实业务实现在未迁移的 ai/zai 模块，本模块只是「广场/收藏」薄壳（415 行核心，清单第 51 行亦标 P3）；
③ yudao-module-ai 已有「模型 + API Key + 会话 + 消息 + 角色/应用 + 知识库」整套等价能力。
投入 ≥ 产出且外部依赖不可搬运，不值得按「表 + 接口 + 前端 + 回归」完整迁移。

**如果要做，scope 是**：**不做代码迁移**，只交付一条映射说明：aiapp 的「会话/模型列表」→
yudao-module-ai 的 AiChatConversation/AiChatMessage/AiModel/AiApiKey/AiChatRole；
「广场/收藏/历史消息」（`zt_ai_miniprogram`/`zt_ai_message`/`zt_ai_miniprogramstar`）在 yudao
无对应，属可选前端壳，暂不实现。若日后确要做，最小可行范围：1 张新表映射 + 约 5 个接口
（square 列表、browseMiniProgram 详情、collectMiniProgram、getHistoryMessages、
saveMiniProgramMessage）+ 1 个前端页 + 1 条关键规则（未发布小程序禁止非 test 调用、
publishedDate 近 1 个月才进「最新」、历史消息按 user+appID 只保留最近 20 条先查后删）；
聊天一律转发到 yudao AI 模块，不重写 LLM 调用；前提是先迁 `module/ai` 并配好 LLM/ZAI。

### `gitfox`（1,468 行 / 4 action / 预计 0 轮）

**它在禅道里做什么**：禅道**私有商业 GitFox 服务端**的 HTTP 客户端 + 安装脚本。

**证据**：`control.php` 162 行 / 4 个 action，`model.php` 1,090 行，`config.php` 216 行，
无 `zen.php`；模块自身 1,468 行（含 lang/ui/test 共 4,268 行）。引用的 6 张表
（`zt_entry`、`ops_repo`、`ops_repohistory`、`zt_story`、`zt_task`、`zt_bug`）全部在 `zentao.sql`，
缺表 0；`edition != 'open'`/`edition == 'open'` 0 处。**不是假数据存根**：`model.php` 有 29 处
真实 `common::http(...)` 调外部接口（checkHealth/apiCreateBranch/apiGetRepos/getCommits 等）
+ 3 处 DAO 真读写（`model.php:50 TABLE_ENTRY`、`:387 TABLE_REPO`、`:948 TABLE_REPOHISTORY`）。
**但存在明显空壳/死代码**：`control.php` 只有 4 个 public function，而 `lang/zh-cn.php` 声明了
browse/create/edit/bindUser/webhook 等大批动作，`ui/binduser.html.php:20` 链接不存在的
`gitfox.binduser`，`ui/edit.html.php` 无对应 action，`config.php:29` 的 webhookURL 指向的
webhook 方法在 control/model 里都不存在——OSS 包里 gitfox 的「业务 UI」不存在，
只剩介绍页 + 安装脚本 + 健康检查 + 一坨 API 客户端。**硬依赖外部服务**：
`config/config.php:246-248` 默认 `http://localhost:3000`；`config.php:128-131` 从
`https://pkg.zentao.net/gitfox/<version>/*.zip` 下载独立二进制；`control.php:155-160` 的
`ajaxCheckGitFoxHealth` 是 `GET {gitfoxURL}/public/health`；`control.php:75-146 installGitFox`
只负责写安装脚本（脚本内 `gitfox install`）。除 `ajaxGetProjectBranches` 走本地 scm 引擎
（该能力已被已迁 repo 覆盖）外，其余全部依赖该外部服务端。

**结论与理由**：排除 do-it（依赖外部服务）；不满足 map-only 的「yudao 已有等价能力」前提
（yudao/ruoyi-vue-pro 没有也不需要 GitFox 服务端）；也不是 not-possible（实现确实在开源包里、表也在）。
实现存在但在目标栈里价值 ≈ 0——把 1,090 行 API 客户端搬成 Java 只会得到一个「没有服务端可连」
的死客户端；本项目 repo 模块迁移时已明确放弃服务商 API 接入（GitLab/Gitea/SVN），gitfox 属同类。
判 not-worth。scope：不迁移、不建表、不写码，仅登记映射说明——gitfox = 禅道私有 GitFox 服务端的
HTTP 客户端（29 处 `common::http` 指向 `config->devops->gitfoxURL:3000`，安装包来自 pkg.zentao.net，
与 GitLab/Gitea/SVN 同类「服务商接入」）；yudao 侧用自身第三方集成能力替代，
若要保留 DevOps 代码托管链路，应走 yudao 已有的代码库/Git 集成方案，而不是照搬 GitFox 客户端。

### `provider`（843 行 / 6 action / 预计 0 轮，JSON 记 1 轮为「假设开工时」）

**它在禅道里做什么**：**外部 DevOps 服务连接配置注册表**——登记并探活 GitLab/Gitea/Gogs/GitHub/
Jenkins/Subversion，供 `pipeline`/`repo` 使用。

**证据**：`control.php` 167 行（5 个 action + `__construct`）、`model.php` 227 行、`zen.php` 236 行，
非测试 PHP 共 843 行；`TABLE_PROVIDER`→`ops_provider`（`zentao.sql:15851`，注释「外部服务表」）、
`TABLE_REPO`→`ops_repo`（15885）都在，缺表 0；`grep -rn edition module/provider/` 0 命中，
`module/group/packagemanager.php:4076-4079` 明确 provider-browse/create/edit/delete 的
edition = `open,biz,max,ipd`，不属 not-possible；无存根（`grep -rniE "TODO|FIXME|not implemented|
未实现|stub|占位"` 退出码 1；唯一 `print` 在 `control.php:165` 是正常 AJAX 输出）。
**但整模块的目的就是登记并探活外部服务**：`model.php:123-150 getApiRoot` 按类型拼私有 token 的
API 地址，`zen.php:40 common::http($apiUrl,...)` 真调外部 `/user` 或 Jenkins `/api/json`，
`zen.php:130` 对 SVN 发 HEAD，`:154 fsockopen($host,$port=3690)`，
`control.php:68/:120` 在 create/edit 时强制 `checkServiceUrl` 不通过就报错——**没有可达的外部服务
连一条 provider 都建不出来**，本环境的「单模块接口 + 界面检查 + 回归」无法真实验证。
**下游已断**：唯一已迁的消费者 `repo` 在清单第 10 行被明确限定为「本地 Git 仓库」，本地库不走 provider；
另一个真实消费者 `pipeline`（`module/pipeline/*.php` 十余处 `loadModel('provider')`）整个未迁。

**结论与理由**：有实现但脱离外部系统与 pipeline 后只剩一个 6 接口/3 页面的配置壳，
投入报建与回归成本不划算。判 not-worth，不建议开工。
**如果日后 decide 要做**，最小范围是：`ops_provider` 1 张表 + 5 个接口（browse 分页排序、create、
edit、delete（有 repo 关联时拒绝，`control.php:145-146`）、ajaxGetProviders）+ 3 个前端页/弹窗
（含按 type 联动 token/account 必填与 SVN 只填 url），并把 `zen.php` 的探活规则改成可注入的 mock，
否则无法回归。核心规则照抄：create 必填 type/name/url，edit 必填 name/url；Jenkins 需要
account+token 且存库前 base64 拼接；`getApiRoot` 的 GitLab `/api/v4`、Gitea/Gogs `/api/v1`、
GitHub 走 header 的差异。（JSON 里的 1 轮即此假设成本，当前不排。）

### `zahost`（801 行 / 11 action / 预计 0 轮）

**它在禅道里做什么**：为「禅道自动化测试」登记并管理 ZAgent+KVM 宿主机、从官方源分发虚拟机镜像、
探活依赖服务，最终给 `zanode`（自动化测试执行节点 VM）提供算力。

**证据**：`module/zahost/` 有 `control.php` 293 行 / `model.php` 414 行 / `zen.php` 24 行 +
`tao.php` 70 行 + config/ui/view，逻辑四文件合计 801 行；`control.php` 11 个 public function。
表：`TABLE_ZAHOST`→`zt_host`（`zentao.sql:2739`，**表名是 `zt_host` 不是 `zt_zahost`**，
同一张表也被 zanode 复用，靠 `type='zahost'/'node'` 区分）、`TABLE_IMAGE`→`zt_image`（2780），
缺表 0。门槛 0 处（模块由软开关控制：`module/group/lang/resource.php:2358` 在
`!helper::hasFeature('devops')` 时 unset zahost 权限，`module/common/lang/menu.php:903` 在
`hasFeature('automated')` 为假时 unset automation 菜单，而 `zentao.sql:2517` 默认
`disabledFeatures='otherOA'` 不含它们，即开源版默认开启）。无存根。
**硬依赖外部系统（否定 do-it 的决定性一条）**：模块本身不含任何虚拟机/镜像能力，全部是对宿主机
ZAgent 服务的 HTTP 控制面客户端——`model.php:277/284 http://{extranet}:{zap}/api/v1/download/add`、
`:311/312 .../task/getStatus`、`:349/354 .../download/cancel`、`zen.php:17 .../service/check`，
鉴权头 `Authorization:$host->tokenSN`，zap 端口默认 55001（`config.php:6`）；
镜像清单源写死 `https://pkg.qucheng.com/zenagent/list.json`（`config.php:11`），
装机脚本亦为 `curl -sSL https://pkg.qucheng.com/zenagent/zagent.sh | bash`（`:15`）；
运行栈依赖 KVM/nodvc/novnc/websockify（`:56-61`）；`model.php:158-170` 还用 `exec("ping ...")`
调宿主 OS 命令。

**结论与理由**：剥掉外部依赖后，yudao 里真正能独立跑通的只剩 `zt_host` 元数据 CRUD
（browse/view/create/edit/delete + search + action 日志，约 5 个接口 2 个页面）；
而 `browseImage` 在外部源不可达时固定返回空数组（`model.php:228`），
`downloadImage`/`cancelDownload`/`ajaxGetServiceStatus`/`ajaxImageDownloadProgress` 必然失败——
即「迁过去 1 个接口能用、4 个接口必然报错」，且要有价值还须连带迁移 `zanode`（4,046 行 / 28 action）
并自建 ZAgent 节点池与镜像源。故判 not-worth（非 do-it：依赖外部系统；非 not-possible：
实现与表都在开源版、无门槛；非 map-only：不是设置/权限类，yudao 也没有可映射的等价能力）。
**口径**：清单第 59 行记 1,601 行，那是把 ui/view/config 一起算的口径；
本行 801 行是纯 control+model+zen+tao，两者不可直接比。
scope：不迁移（0 表 / 0 接口 / 0 前端页），仅在清单登记说明；若将来确要接自动化测试执行环境，
应先确认是否已具备 ZAgent 节点池，再把 zahost 与 zanode 作为一个整体重新评估（二者共用 `zt_host`，
唯一区分是 `type`）。

---

## 六、建议的执行顺序

这份取证把 24 个模块分成了「要写代码的 4 个 / 只写文档的 10 个 / 只登记结论的 10 个」。
建议按下面三步走，**不要**按 PHP 行数从大到小排——`upgrade`（30,114 行）与 `tutorial`（16,700 行）
排在最前面只会浪费轮次。

### 第一步：do-it 的 4 个（合计约 6.5 轮），按「价值 ÷ 依赖」排序

1. **`dimension`（0.5 轮）先做**。它是这 4 个里投入最小的（499 行、2 个 action、1 张表、0 门槛），
   而且是 `chart`/`pivot`/`screen` 的真实前置依赖——`chart` 已迁移，把维度这层补上，
   BI 侧的只读导航就通了。注意开源版不含维度 CRUD，按只读维度 + 切换语义实现，管理端留 P3。
2. **`api`（2 轮）第二个做**。接口文档库边界清晰、独立成块，只依赖已迁移的 `doc`/`doclib`/`module`，
   与其它在途模块没有耦合，适合作为「完整 do-it 流程」（表 + 29 接口 + 13 页 + 回归）的第一块练兵。
   旧清单否决它的理由（4 张表缺失）已被证伪，可以直接开工。
3. **`webhook`（2 轮）第三个做**。它是禅道「事件外发」的通用出口，yudao 目前完全没有等价能力
   （只有站内信），是真实缺口；对外部服务的依赖可以用本地 mock 接收端完成回归。
   放在 `cron` 的映射结论之后做，因为它的异步发送要交给 yudao Quartz 而不是重建调度器。
4. **`search`（2 轮）最后做**。它价值最高也最重：既要做全局检索（三元 SQL 分支 + 权限过滤 +
   中文词典），又要把 `saveIndex/deleteIndex` 挂到所有已迁模块的增删改钩子上——
   **挂在最后做，钩子面才是稳定的**；如果先做，后续每迁一个模块都要回头补钩子。
   这也是它排第 4 而不是第 1 的原因。

**顺序理由汇总**：`dimension` 最小且解除 BI 依赖 → `api` 最独立 → `webhook` 补真实缺口、
依赖 `cron` 结论 → `search` 最重且需要所有模块的钩子面稳定。

### 第二步：map-only 的 10 个（合计约 2.5 轮，全部是文档，不写业务代码）

这一步可以按主题**三批并行**完成，每批产出一份映射说明（0 表 / 0 接口 / 0 页）：

- **批 A：调度与事件桥接** —— `cron`（配 `webhook` 一起做：`zt_cron`+`zt_queue` → yudao
  `infra_job`+`infra_job_log`，17 条种子逐条给 JobHandler 名与 Quartz 表达式）、
  `jenkins`（Jenkins 作为 provider/engine 枚举登记，6 个 HTTP 方法随 pipeline 迁移时随迁）。
- **批 B：后台/配置/工具** —— `admin`（22 个 action → yudao 等价物 / drop 三分类对照表，
  并修正「admin 表齐全」）、`misc`（15 action + 8 model 方法的映射与删除清单）、
  `dev`（api/restAPI、db、langItem、editor 四块的映射与不迁说明）、`extension`（运行时插件市场
  在编译型 Java 上无对应物且不建议实现）、`mark`（内部服务层，记录表/语义映射与 `dao::isError()`
  反语义 bug）。
- **批 C：外部平台/数据桥接** —— `ai`（9 张表 → yudao-module-ai，15 条内置智能体导入说明）、
  `zai`（ZAI 配置/向量化/检索 → yudao 知识库 RAG，外部客户端不搬）、
  `convert`（Jira/Redmine/BugFree → yudao 实体的字段/状态/用户映射，含 12 处门槛差异）。

这 10 个模块**不需要编译、不需要接口回归、不需要界面检查**，交付物就是文档；
它们的价值在于把「yudao 已有等价能力」这件事写清楚，避免后续有人把它们当成缺口重复开工。

### 第三步：把 not-possible / not-worth 的结论一次性固化进清单（不需要写代码）

最后一步不做任何开发，只回写 `MIGRATION-INVENTORY.md`，让清单不再误导后来者。
**这一步和写代码同等重要**：旧清单把 `upgrade`/`gitlab`/`codescan`/`api` 的判断写错了，
不修正的话，下一步做优先级排序的人还会在它们身上浪费时间。

需要回写的修正项：

| 清单位置 | 旧写法 | 应改为 | 依据 |
|---|---|---|---|
| 第 2 行 `upgrade` | 「❌ 未做 P2」，审计节列为「可继续迁移」 | **not-worth**：禅道自身的 DB 补丁+数据整形升级器，Java 侧无「升级旧禅道库」场景 | 本文件 `upgrade` 一节 |
| 第 6 行 `tutorial` | 「❌ 未做 P3」 | **not-worth**：教学假数据层，43 处 `new stdclass`、220 处跨模块钩子 | 本文件 `tutorial` 一节 |
| 第 23 行 `codescan` / 审计节 | 「❌ 未做 P2」、审计称「被 `edition != 'open'` 挡住」 | **not-possible**：门槛为 0，主体是外部 GitFox 服务的 UI + REST 代理 | 本文件 `codescan` 一节 |
| 第 33 行 `api` / 审计节 | 「`zt_apilib`/`zt_apirelease`/`*_spec` 四张表不在开源版」 | **do-it**：5 张表全在，禅道从来没有 `zt_apilib`（接口库就是 `zt_doclib` type='api'） | 本文件 `api` 一节 |
| 第 37 行 `admin` / 审计节 | 「表齐全」 | 缺 1 张 `zt_sqlite_queue`；结论 map-only | 本文件 `admin` 一节 |
| 第 51 行 `aiapp` | 「❌ 未做 P3」 | **not-worth**：外部 LLM/ZAI 依赖 + yudao-module-ai 已覆盖 | 本文件 `aiapp` 一节 |
| 第 59 行 `zahost` | 「1,601 行」 | 口径备注：纯逻辑 801 行 / 含 ui·view·config 1,601 行；结论 **not-worth** | 本文件 `zahost` 一节 |
| 第 60 行 `gitlab` / 审计节 | 「❌ 未做 P2」、审计列为「可以继续迁移」 | **not-possible**：实现已于 `53c5c88791` 整包删除，仅剩 249 行 API client | 本文件 `gitlab` 一节 |
| 第 47 行 `gitfox` / 第 77 行 `provider` / 第 43 行 `zanode` | 「❌ 未做 P2/P3」 | **not-worth**：外部私有服务/基础设施的空壳控制台 | 本文件对应小节 |
| 第 137 行审计表 | 把 `cron`/`webhook`/`mark`/`dimension`/`search`/`upgrade`/`admin`/`tutorial`/`dev`/`extension`/`misc`/`zahost`/`zanode`/`ai`/`aiapp`/`zai`/`gitfox`/`gitlab`/`jenkins`/`provider` 笼统列为「可以继续迁移」 | 按本文件的四分类拆开：do-it 4 / map-only 10 / not-possible 3 / not-worth 7 | 本文件总表 |
| 第 136 行 | 「`zt_deliverable` 开源版没有」 | **有**（本文件 `upgrade` 一节复核为「有」，属原清单笔误） | 本文件 `upgrade` 一节 |
| 第 198 行 `zai` | 「2,907 行 / 239 model / 7 action」 | 实测 control 251 + model 2,269 = 2,520 行 | 本文件 `zai` 一节 |

**最后强调一句「不迁 ≠ 偷懒」**：这 24 个模块里，真正「没实现可搬」的只有 3 个
（`feedback` 只剩 44 行存根、`codescan` 逻辑全在外部 GitFox、`gitlab` 实现被上游删除），
其余 21 个都有真实代码。判 map-only 的 10 个是因为 **yudao 已有等价能力、重写只会冲突**；
判 not-worth 的 7 个是因为**搬过去的目标产物在 Java 侧没有意义**。
这两类结论都是取证得出的结论，不是省事的托词——恰恰相反，它们比「照抄一遍」更花力气，
因为要逐条证明「为什么不需要抄」。真正的偷懒是把它们继续标成「❌ 未做」，
让下一个人以为「再花时间就能补上」。

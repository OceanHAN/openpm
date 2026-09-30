-- ---------------------------------------------------------------------------
-- 接口文档库（api）模块
--
-- 【禅道语义】注意：api 模块**不是**「对外 REST 接口」，而是「接口文档库」——
-- 按产品/项目/独立空间挂载接口库，库 → 目录 → 接口 → 可复用数据结构 → 发布版本。
-- 每个接口存请求参数树 / 请求示例 / 响应示例；发布版本时把当时的接口与结构**快照冻结**，
-- 支持按 release 回溯历史。
--
-- 【5 张表，全部在开源版】db/zentao.sql：
--   zt_api                接口（第 91 行起）。头部表，只存**当前值** + 当前版本号
--   zt_apispec            接口的版本内容（第 120 行起）。(doc, version) 一条，追加不修改
--   zt_apistruct          可复用数据结构（第 144 行起）。attribute 是可嵌套的 JSON 字段树
--   zt_apistruct_spec     数据结构的版本内容（第 161 行起）。**只存 name，没有 struct id**
--   zt_api_lib_release    发布版本（第 79 行起）。snap 是把 modules/apis/structs 打成的 JSON 快照
--
-- 【没有 zt_apilib！】禅道从来没有这张表。接口库就是 zt_doclib 里 type='api' 的记录
-- （db/zentao.sql 第 921 行的 zt_doclib；model.php:636 createDemoLib 往 TABLE_DOCLIB 插 type='api'）。
-- 所以本模块**不建** zt_doclib（它属于已迁移的 doc 模块），只往它里面插一条演示接口库。
-- 接口目录树同理复用 zt_module（type='api'，root=接口库 id；model.php:42 publishLib 就是这么查的）。
--
-- 【三条照抄的核心规则】
--   ① 版本链：create 写 zt_api + apispec(v1)；update 只在**真有变更**时 version+1，
--      并且对同一个 (doc, version) 先 DELETE 再 INSERT（model.php:162-163）——
--      等价于「原地重写当前版本的快照」，历史版本的 spec 行永久保留。
--   ② 发布是快照不是引用：publishLib（model.php:39-70）把
--      {modules:[整行], apis:[{id,version}], structs:[{id,version}]} 序列化进 snap 列。
--      读某个 release 时拿 snap 里的 version 回查 apispec（model.php:312 getByID、
--      :362 getApiListByRelease）。
--   ③ 唯一性（model.php:99-100 / :146-147）：
--      title 在 (lib, module) 内唯一、path 在 (lib, module, method) 内唯一；
--      且禅道的 unique 检查**不过滤已删除行**（与 zt_company.name / zt_entry.code 同一个坑），
--      所以这里是应用层校验 + 查询时故意不带 deleted 条件，不建数据库唯一键
--      （建了反而会和逻辑删除打架，见 README 第 4 条坑）。
--      另外 zt_apispec 的 (doc,version) 建唯一键是安全的：它没有 deleted 列，是物理删除。
--
-- 【保留字】列名里只有 `desc` 是 MySQL 保留字（另有 `order`/`groups`/`key` 等在本模块没出现）。
-- DO 里对 `desc` 显式加了反引号。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 接口（头部表） ============================
-- zt_api 有 deleted 列（禅道原样：tinyint 0/1），所以 DO 继承 BaseDO（@TableLogic 生效）。
CREATE TABLE IF NOT EXISTS `zt_api` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '接口编号',
  `product`         bigint       NOT NULL DEFAULT 0 COMMENT '所属产品（禅道原列是 varchar，实际恒为 0，本实现按 id 归一为 bigint）',
  `lib`             bigint       NOT NULL DEFAULT 0 COMMENT '所属接口库（zt_doclib 里 type=api 的记录）',
  `module`          bigint       NOT NULL DEFAULT 0 COMMENT '所属目录（zt_module，type=api，root=lib）',
  `title`           varchar(100) NOT NULL DEFAULT '' COMMENT '接口名称（(lib,module) 内唯一）',
  `path`            varchar(255) NOT NULL DEFAULT '' COMMENT '请求路径（(lib,module,method) 内唯一）',
  `protocol`        varchar(10)  NOT NULL DEFAULT '' COMMENT '协议：HTTP/HTTPS/WS/WSS',
  `method`          varchar(10)  NOT NULL DEFAULT '' COMMENT '请求方式：GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD',
  `requestType`     varchar(100) NOT NULL DEFAULT '' COMMENT '请求格式（驼峰列名）',
  `responseType`    varchar(100) NOT NULL DEFAULT '' COMMENT '响应格式（驼峰列名；禅道的 create/edit 表单里都没有它，属于只读历史列）',
  `status`          varchar(20)  NOT NULL DEFAULT '' COMMENT '开发状态：done 开发完成 / doing 开发中 / hidden 不显示',
  `owner`           varchar(30)  NOT NULL DEFAULT '' COMMENT '负责人账号',
  `desc`            mediumtext   DEFAULT NULL COMMENT '接口说明（desc 是 MySQL 保留字）',
  `version`         int          NOT NULL DEFAULT 1 COMMENT '当前版本号；每次真有变更 +1',
  `params`          text         DEFAULT NULL COMMENT '请求参数树 JSON：{header:[],params:[],paramsType,query:[]}',
  `paramsExample`   text         DEFAULT NULL COMMENT '请求示例（驼峰列名）',
  `responseExample` text         DEFAULT NULL COMMENT '响应示例（驼峰列名）',
  `response`        text         DEFAULT NULL COMMENT '响应字段树 JSON',
  `commonParams`    text         DEFAULT NULL COMMENT '公共参数（驼峰列名；禅道表单里没有它，恒为空）',
  `addedBy`         varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `addedDate`       datetime     NULL COMMENT '创建时间（驼峰列名）',
  `editedBy`        varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `editedDate`      datetime     NULL COMMENT '最后修改时间（驼峰列名）。**同时是乐观锁**：编辑时带上被改回的值会被拒绝',
  -- yudao BaseDO 约定字段
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`         varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`         varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_lib_module` (`lib`, `module`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口文档表（禅道 zt_api）';

-- ============================ 2. 接口版本内容（快照表） ============================
-- **没有 deleted 列**（禅道原样）→ DO 不继承 BaseDO，否则每条查询都会带 deleted = 0 直接报错。
-- (doc, version) 唯一：update 时先 DELETE 同版本再 INSERT，等于原地重写当前版本（model.php:162）。
CREATE TABLE IF NOT EXISTS `zt_apispec` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `doc`             bigint       NOT NULL DEFAULT 0 COMMENT '接口编号（zt_api.id）',
  `module`          bigint       NOT NULL DEFAULT 0 COMMENT '当时的目录编号',
  `title`           varchar(100) NOT NULL DEFAULT '' COMMENT '当时的接口名称',
  `path`            varchar(255) NOT NULL DEFAULT '' COMMENT '当时的请求路径',
  `protocol`        varchar(10)  NOT NULL DEFAULT '' COMMENT '当时的协议',
  `method`          varchar(10)  NOT NULL DEFAULT '' COMMENT '当时的请求方式',
  `requestType`     varchar(100) NOT NULL DEFAULT '' COMMENT '当时的请求格式（驼峰列名）',
  `responseType`    varchar(100) NOT NULL DEFAULT '' COMMENT '当时的响应格式（驼峰列名）',
  `status`          varchar(20)  NOT NULL DEFAULT '' COMMENT '当时的开发状态',
  `owner`           varchar(30)  NOT NULL DEFAULT '' COMMENT '当时的负责人',
  `desc`            mediumtext   DEFAULT NULL COMMENT '当时的接口说明（保留字）',
  `version`         int          NOT NULL DEFAULT 1 COMMENT '版本号',
  `params`          text         DEFAULT NULL COMMENT '当时的请求参数树 JSON',
  `paramsExample`   text         DEFAULT NULL COMMENT '当时的请求示例（驼峰列名）',
  `responseExample` text         DEFAULT NULL COMMENT '当时的响应示例（驼峰列名）',
  `response`        text         DEFAULT NULL COMMENT '当时的响应字段树 JSON',
  `addedBy`         varchar(64)  NOT NULL DEFAULT '' COMMENT '该版本的写者（驼峰列名）',
  `addedDate`       datetime     NULL COMMENT '该版本的写入时间（驼峰列名）',
  -- yudao 框架列（本表无 deleted，DO 不继承 BaseDO，这四列由数据库默认值兜底）
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`         varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`         varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_version` (`doc`, `version`),
  KEY `idx_doc` (`doc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口版本内容表（禅道 zt_apispec，(doc,version) 唯一）';

-- ============================ 3. 可复用数据结构 ============================
-- zt_apistruct 有 deleted 列 → DO 继承 BaseDO。
-- attribute 是可嵌套 JSON 字段树：[{field,paramsType,required,desc,structType,sub,key,children:[]}]
-- （见 module/api/js/common.ui.js 的 processRow/buildNestedParams）。
CREATE TABLE IF NOT EXISTS `zt_apistruct` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '结构编号',
  `lib`         bigint       NOT NULL DEFAULT 0 COMMENT '所属接口库',
  `name`        varchar(30)  NOT NULL DEFAULT '' COMMENT '结构名',
  `type`        varchar(50)  NOT NULL DEFAULT '' COMMENT '结构类型：formData/json/array/object',
  `desc`        mediumtext   DEFAULT NULL COMMENT '结构说明（保留字）',
  `version`     int          NOT NULL DEFAULT 1 COMMENT '当前版本号',
  `attribute`   text         DEFAULT NULL COMMENT '字段树 JSON',
  `addedBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `addedDate`   datetime     NULL COMMENT '创建时间（驼峰列名）',
  `editedBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `editedDate`  datetime     NULL COMMENT '最后修改时间（驼峰列名）',
  -- yudao BaseDO 约定字段
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_lib` (`lib`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口数据结构表（禅道 zt_apistruct）';

-- ============================ 4. 数据结构版本内容 ============================
-- 【禅道的一处真实设计缺陷，照抄但标注】这张表**没有 struct id，也没有 lib**，
-- 只有 name；禅道读取时是按 `object.name = spec.name` 关联的
-- （model.php:512 getStructListByRelease）。于是「两个库里同名的结构」会串版本。
-- 因此这里**不建** (name,version) 唯一键（那样会让第二个库建同名结构直接失败），
-- 只建普通索引。本实现的升级版本接口用 (name, version) 定位 —— 与禅道一致。
CREATE TABLE IF NOT EXISTS `zt_apistruct_spec` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `name`        varchar(255) NOT NULL DEFAULT '' COMMENT '结构名（禅道唯一的关联键）',
  `type`        varchar(50)  NOT NULL DEFAULT '' COMMENT '结构类型',
  `desc`        varchar(255) NOT NULL DEFAULT '' COMMENT '结构说明',
  `attribute`   text         DEFAULT NULL COMMENT '字段树 JSON',
  `version`     int          NOT NULL DEFAULT 1 COMMENT '版本号',
  `addedBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '该版本的写者（驼峰列名）',
  `addedDate`   datetime     NULL COMMENT '该版本的写入时间（驼峰列名）',
  -- yudao 框架列（本表无 deleted，DO 不继承 BaseDO）
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  PRIMARY KEY (`id`),
  KEY `idx_name_version` (`name`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口数据结构版本表（禅道 zt_apistruct_spec，只按 name 关联）';

-- ============================ 5. 发布版本（快照） ============================
-- 没有 deleted 列 → DO 不继承 BaseDO；删除发布是真删（禅道 deleteRelease 就是物理 delete）。
-- version 是 varchar（禅道原样），同一 lib 内唯一（应用层校验，禅道 control.php:441）。
CREATE TABLE IF NOT EXISTS `zt_api_lib_release` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '发布编号',
  `lib`         bigint       NOT NULL DEFAULT 0 COMMENT '所属接口库',
  `desc`        varchar(255) NOT NULL DEFAULT '' COMMENT '版本说明（保留字）',
  `version`     varchar(255) NOT NULL DEFAULT '' COMMENT '版本号（字符串；(lib,version) 内唯一）',
  `snap`        mediumtext   DEFAULT NULL COMMENT '快照 JSON：{"modules":[...],"apis":[{id,version}],"structs":[{id,version}]}',
  `addedBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '发布人（驼峰列名）',
  `addedDate`   datetime     NULL COMMENT '发布时间（驼峰列名）',
  -- yudao 框架列（本表无 deleted，DO 不继承 BaseDO）
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  PRIMARY KEY (`id`),
  KEY `idx_lib` (`lib`),
  KEY `idx_version` (`lib`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口库发布版本表（禅道 zt_api_lib_release，snap 是冻结快照）';

-- ============================ 6. 演示数据 ============================
-- id 分段（各表互不相邻，理由见 25-zt_doc.sql 的注释）：
--   接口库(zt_doclib) 92751 ｜ 接口目录(zt_module) 92701+ ｜ 接口 92711+
--   ｜ 接口版本 92721+ ｜ 结构 92731+ ｜ 结构版本 92741+ ｜ 发布版本 92761+
-- 高位段是为了避开测试区间（README 第 23 条坑：低位 id 会撞上历史测试留下的软删除行）。

-- 6.1 接口库：**插进 zt_doclib**（type='api'），不是新建库表
INSERT INTO `zt_doclib` (`id`, `type`, `parent`, `product`, `project`, `execution`, `name`, `baseUrl`, `acl`, `main`, `desc`, `order`, `addedBy`, `addedDate`, `creator`, `updater`)
VALUES
(92751, 'api', 0, 1, 0, 0, '禅道接口库', 'https://demo.zentao.net/api.php/v1', 'open', 0, '接口文档库演示：库 → 目录 → 接口 → 结构 → 发布版本', 7, 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `type` = VALUES(`type`), `product` = VALUES(`product`), `name` = VALUES(`name`),
    `baseUrl` = VALUES(`baseUrl`), `acl` = VALUES(`acl`), `desc` = VALUES(`desc`),
    `order` = VALUES(`order`), `deleted` = b'0';

-- 6.2 接口目录（zt_module，root=接口库 id，type='api'）
INSERT INTO `zt_module` (`id`, `root`, `branch`, `name`, `parent`, `path`, `grade`, `order`, `type`, `creator`, `updater`)
VALUES
(92701, 92751, 0, '用户与认证', 0,     ',92701,',           1, 1, 'api', 'admin', 'admin'),
(92702, 92751, 0, '需求管理',   0,     ',92702,',           1, 2, 'api', 'admin', 'admin'),
(92703, 92751, 0, '用户登录',   92701, ',92701,92703,',     2, 3, 'api', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `parent` = VALUES(`parent`), `path` = VALUES(`path`),
    `grade` = VALUES(`grade`), `type` = VALUES(`type`), `deleted` = b'0';

-- 6.3 两个接口：
--   92711「获取当前登录用户」v2 —— 演示版本链（v1 初稿、v2 加了 avatar 字段与 fields 查询参数）
--   92712「获取需求列表」v1 —— 演示「没有请求体、只有 query/header」的接口
-- 两条都被 6.7 的发布版本 v1.0 冻结：所以「删除接口」对它们会命中「被发布版本引用」的保护，
-- 这正是演示数据想展示的行为（要删接口得先删发布版本）。
INSERT INTO `zt_api` (`id`, `product`, `lib`, `module`, `title`, `path`, `protocol`, `method`,
                      `requestType`, `responseType`, `status`, `owner`, `desc`, `version`,
                      `params`, `paramsExample`, `responseExample`, `response`, `commonParams`,
                      `addedBy`, `addedDate`, `editedBy`, `editedDate`, `creator`, `updater`)
VALUES
(92711, 0, 92751, 92703, '获取当前登录用户', '/api.php/v1/user', 'HTTP', 'GET',
    'application/json', '', 'done', 'admin', '返回当前 Token 对应的用户信息', 2,
    '{"header":[{"field":"Token","required":true,"desc":"认证凭证"}],"params":[],"paramsType":"","query":[{"field":"fields","required":"","desc":"需要返回的字段，逗号分隔"}]}',
    '{"fields": "account,realname"}',
    '{\n    "id": 1,\n    "account": "admin",\n    "realname": "管理员",\n    "avatar": ""\n}',
    '[{"field":"id","paramsType":"int","required":"","desc":"用户编号","structType":"json","sub":1,"key":"demoUser1","children":[]},
      {"field":"account","paramsType":"string","required":"","desc":"登录名","structType":"json","sub":1,"key":"demoUser2","children":[]},
      {"field":"realname","paramsType":"string","required":"","desc":"姓名","structType":"json","sub":1,"key":"demoUser3","children":[]},
      {"field":"avatar","paramsType":"string","required":"","desc":"头像地址（v2 新增）","structType":"json","sub":1,"key":"demoUser4","children":[]}]',
    '',
    'admin', '2026-04-01 09:00:00', 'admin', '2026-04-10 14:30:00', 'admin', 'admin'),
(92712, 0, 92751, 92702, '获取需求列表', '/api.php/v1/stories', 'HTTP', 'GET',
    'application/json', '', 'doing', 'admin', '分页返回需求列表', 1,
    '{"header":[{"field":"Token","required":true,"desc":"认证凭证"}],"params":[],"paramsType":"","query":[{"field":"product","required":true,"desc":"产品编号"},{"field":"page","required":"","desc":"第几页，默认 1"}]}',
    '',
    '{\n    "page": 1,\n    "total": 1,\n    "stories": [{"id": 1, "title": "示例需求"}]\n}',
    '[{"field":"page","paramsType":"int","required":"","desc":"当前页","structType":"json","sub":1,"key":"demoStory1","children":[]},
      {"field":"total","paramsType":"int","required":"","desc":"总数","structType":"json","sub":1,"key":"demoStory2","children":[]},
      {"field":"stories","paramsType":"array","required":"","desc":"需求列表","structType":"json","sub":1,"key":"demoStory3","children":[
        {"field":"id","paramsType":"int","required":"","desc":"需求编号","structType":"json","sub":1,"key":"demoStory4","children":[]},
        {"field":"title","paramsType":"string","required":"","desc":"需求标题","structType":"json","sub":1,"key":"demoStory5","children":[]}]}]',
    '',
    'admin', '2026-04-02 10:00:00', 'admin', '2026-04-02 10:00:00', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `lib` = VALUES(`lib`), `module` = VALUES(`module`), `title` = VALUES(`title`),
    `path` = VALUES(`path`), `protocol` = VALUES(`protocol`), `method` = VALUES(`method`),
    `requestType` = VALUES(`requestType`), `status` = VALUES(`status`), `owner` = VALUES(`owner`),
    `desc` = VALUES(`desc`), `version` = VALUES(`version`), `params` = VALUES(`params`),
    `paramsExample` = VALUES(`paramsExample`), `responseExample` = VALUES(`responseExample`),
    `response` = VALUES(`response`), `deleted` = b'0';

-- 6.4 接口版本内容：(92711,v1) 与 (92711,v2) 两条 —— 只追加不修改
INSERT INTO `zt_apispec` (`id`, `doc`, `module`, `title`, `path`, `protocol`, `method`, `requestType`, `responseType`,
                          `status`, `owner`, `desc`, `version`, `params`, `paramsExample`, `responseExample`, `response`,
                          `addedBy`, `addedDate`)
VALUES
(92721, 92711, 92703, '获取当前登录用户', '/api.php/v1/user', 'HTTP', 'GET', 'application/json', '',
    'done', 'admin', '返回当前 Token 对应的用户信息', 1,
    '{"header":[{"field":"Token","required":true,"desc":"认证凭证"}],"params":[],"paramsType":"","query":[]}',
    '', '{\n    "id": 1,\n    "account": "admin"\n}',
    '[{"field":"id","paramsType":"int","required":"","desc":"用户编号","structType":"json","sub":1,"key":"demoUserV1a","children":[]},
      {"field":"account","paramsType":"string","required":"","desc":"登录名","structType":"json","sub":1,"key":"demoUserV1b","children":[]}]',
    'admin', '2026-04-01 09:00:00'),
(92722, 92711, 92703, '获取当前登录用户', '/api.php/v1/user', 'HTTP', 'GET', 'application/json', '',
    'done', 'admin', '返回当前 Token 对应的用户信息', 2,
    '{"header":[{"field":"Token","required":true,"desc":"认证凭证"}],"params":[],"paramsType":"","query":[{"field":"fields","required":"","desc":"需要返回的字段，逗号分隔"}]}',
    '{"fields": "account,realname"}', '{\n    "id": 1,\n    "account": "admin",\n    "realname": "管理员",\n    "avatar": ""\n}',
    '[{"field":"id","paramsType":"int","required":"","desc":"用户编号","structType":"json","sub":1,"key":"demoUser2a","children":[]},
      {"field":"account","paramsType":"string","required":"","desc":"登录名","structType":"json","sub":1,"key":"demoUser2b","children":[]},
      {"field":"realname","paramsType":"string","required":"","desc":"姓名","structType":"json","sub":1,"key":"demoUser2c","children":[]},
      {"field":"avatar","paramsType":"string","required":"","desc":"头像地址（v2 新增）","structType":"json","sub":1,"key":"demoUser2d","children":[]}]',
    'admin', '2026-04-10 14:30:00'),
(92723, 92712, 92702, '获取需求列表', '/api.php/v1/stories', 'HTTP', 'GET', 'application/json', '',
    'doing', 'admin', '分页返回需求列表', 1,
    '{"header":[{"field":"Token","required":true,"desc":"认证凭证"}],"params":[],"paramsType":"","query":[{"field":"product","required":true,"desc":"产品编号"},{"field":"page","required":"","desc":"第几页，默认 1"}]}',
    '', '{\n    "page": 1,\n    "total": 1,\n    "stories": [{"id": 1, "title": "示例需求"}]\n}',
    '[{"field":"page","paramsType":"int","required":"","desc":"当前页","structType":"json","sub":1,"key":"demoStoryV1a","children":[]}]',
    'admin', '2026-04-02 10:00:00')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `path` = VALUES(`path`), `status` = VALUES(`status`),
    `version` = VALUES(`version`), `params` = VALUES(`params`), `responseExample` = VALUES(`responseExample`),
    `response` = VALUES(`response`);

-- 6.5 数据结构：92731 user（v2，演示结构也有版本链）；92732 需求摘要
INSERT INTO `zt_apistruct` (`id`, `lib`, `name`, `type`, `desc`, `version`, `attribute`,
                            `addedBy`, `addedDate`, `editedBy`, `editedDate`, `creator`, `updater`)
VALUES
(92731, 92751, 'user', 'json', '禅道用户对象', 2,
    '[{"field":"id","paramsType":"int","required":"","desc":"用户编号","structType":"json","sub":1,"key":"demoStruct1","children":[]},
      {"field":"account","paramsType":"string","required":"","desc":"登录名","structType":"json","sub":1,"key":"demoStruct2","children":[]},
      {"field":"realname","paramsType":"string","required":"","desc":"姓名","structType":"json","sub":1,"key":"demoStruct3","children":[]},
      {"field":"avatar","paramsType":"string","required":"","desc":"头像地址（v2 新增）","structType":"json","sub":1,"key":"demoStruct4","children":[]}]',
    'admin', '2026-04-01 09:05:00', 'admin', '2026-04-10 14:35:00', 'admin', 'admin'),
(92732, 92751, 'story', 'json', '禅道需求对象', 1,
    '[{"field":"id","paramsType":"int","required":"","desc":"需求编号","structType":"json","sub":1,"key":"demoStruct5","children":[]},
      {"field":"title","paramsType":"string","required":"","desc":"需求标题","structType":"json","sub":1,"key":"demoStruct6","children":[]},
      {"field":"pri","paramsType":"int","required":"","desc":"优先级","structType":"json","sub":1,"key":"demoStruct7","children":[]}]',
    'admin', '2026-04-02 10:05:00', 'admin', '2026-04-02 10:05:00', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `type` = VALUES(`type`), `desc` = VALUES(`desc`),
    `version` = VALUES(`version`), `attribute` = VALUES(`attribute`), `deleted` = b'0';

-- 6.6 结构版本内容：按 (name, version) 追加。user 有 v1/v2 两条 —— 对应禅道「只按 name 关联」的口径
INSERT INTO `zt_apistruct_spec` (`id`, `name`, `type`, `desc`, `attribute`, `version`, `addedBy`, `addedDate`)
VALUES
(92741, 'user', 'json', '禅道用户对象',
    '[{"field":"id","paramsType":"int","required":"","desc":"用户编号","structType":"json","sub":1,"key":"demoSpecUser1","children":[]},
      {"field":"account","paramsType":"string","required":"","desc":"登录名","structType":"json","sub":1,"key":"demoSpecUser2","children":[]},
      {"field":"realname","paramsType":"string","required":"","desc":"姓名","structType":"json","sub":1,"key":"demoSpecUser3","children":[]}]',
    1, 'admin', '2026-04-01 09:05:00'),
(92742, 'user', 'json', '禅道用户对象',
    '[{"field":"id","paramsType":"int","required":"","desc":"用户编号","structType":"json","sub":1,"key":"demoSpecUser4","children":[]},
      {"field":"account","paramsType":"string","required":"","desc":"登录名","structType":"json","sub":1,"key":"demoSpecUser5","children":[]},
      {"field":"realname","paramsType":"string","required":"","desc":"姓名","structType":"json","sub":1,"key":"demoSpecUser6","children":[]},
      {"field":"avatar","paramsType":"string","required":"","desc":"头像地址（v2 新增）","structType":"json","sub":1,"key":"demoSpecUser7","children":[]}]',
    2, 'admin', '2026-04-10 14:35:00'),
(92743, 'story', 'json', '禅道需求对象',
    '[{"field":"id","paramsType":"int","required":"","desc":"需求编号","structType":"json","sub":1,"key":"demoSpecStory1","children":[]},
      {"field":"title","paramsType":"string","required":"","desc":"需求标题","structType":"json","sub":1,"key":"demoSpecStory2","children":[]}]',
    1, 'admin', '2026-04-02 10:05:00')
ON DUPLICATE KEY UPDATE
    `type` = VALUES(`type`), `desc` = VALUES(`desc`), `attribute` = VALUES(`attribute`),
    `version` = VALUES(`version`);

-- 6.7 发布版本：v1.0 冻结「接口 92711@v2、92712@v1 + 结构 92731@v2、92732@v1 + 三个目录」
--     snap 的形状与 model.php:61 完全一致（modules 是整行，apis/structs 只有 id+version）
INSERT INTO `zt_api_lib_release` (`id`, `lib`, `desc`, `version`, `snap`, `addedBy`, `addedDate`)
VALUES
(92761, 92751, '首个对外版本', 'v1.0',
    '{"modules":[{"id":92701,"root":92751,"branch":0,"name":"用户与认证","parent":0,"path":",92701,","grade":1,"order":1,"type":"api"},{"id":92702,"root":92751,"branch":0,"name":"需求管理","parent":0,"path":",92702,","grade":1,"order":2,"type":"api"},{"id":92703,"root":92751,"branch":0,"name":"用户登录","parent":92701,"path":",92701,92703,","grade":2,"order":3,"type":"api"}],"apis":[{"id":92711,"version":2},{"id":92712,"version":1}],"structs":[{"id":92731,"version":2},{"id":92732,"version":1}]}',
    'admin', '2026-04-11 09:00:00')
ON DUPLICATE KEY UPDATE
    `desc` = VALUES(`desc`), `snap` = VALUES(`snap`), `version` = VALUES(`version`);

-- ============================ 7. 菜单与权限 ============================
-- 90195 页面 + 90196~90199 四个 type=3 按钮权限。
-- type=3 的行不能省：前端 v-hasPermi 是拿「角色 → 菜单」算出来的权限串判定的，
-- 少了菜单行按钮会被直接隐藏（README 坑位 #43）。
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90195, '接口文档库', 'zentao:api:query',  2, 29, 90001, 'api', 'ep:link', 'zentao/api/index', 'ZentaoApi', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90196, '接口查询',   'zentao:api:query',  3, 1, 90195, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90197, '接口创建',   'zentao:api:create', 3, 2, 90195, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90198, '接口修改',   'zentao:api:update', 3, 3, 90195, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90199, '接口删除',   'zentao:api:delete', 3, 4, 90195, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

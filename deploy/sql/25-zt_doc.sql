-- ---------------------------------------------------------------------------
-- 文档（doc）模块
--
-- 【禅道语义】doc 是「文档库 + 文档 + 版本内容」三层结构，三张表：
--   zt_doclib      文档库。type ∈ product/project/execution/custom，
--                  product/project/execution 类型的库挂在对应对象上，且是**主库**（main=1）
--   zt_doc         文档。type='chapter' 是**章节**（树的中间节点，没有内容），
--                  其余（html/markdown/url/word/ppt/excel/attachment）才是真正的文档；
--                  parent/path/grade 组成章节树 —— 和 zt_module 是同一套树规则
--   zt_doccontent  版本内容。`(doc, version)` 唯一，version=0 是**草稿**，
--                  正式发布/编辑才产生 version>=1 的快照
--
-- 【和需求版本链是同一个套路】
--   这是本项目第二个「头部 + 追加式快照」的模块（第一个是 zt_story + zt_storyspec）。
--   差异在于 doc 多了一个 **version=0 的草稿位**：草稿期间反复保存都改 version=0 那一行，
--   发布时才升到 1；之后再编辑就 version+1 追加。所以同一个 (doc,version) 唯一键
--   一会儿被 UPDATE、一会儿被 INSERT —— 与禅道 model.php 的 saveDocContent 完全一致。
--
-- 【章节不是文档】
--   type='chapter' 的行不写 zt_doccontent，它只是树节点。因此：
--     - 建章节：lib + title + type=chapter
--     - 建文档：必须挂在某个 lib 下，可以选一个 chapter 作为 parent
--
-- 【本表只建「本轮用到的列」】
--   禅道 zt_doc 有 50+ 列（模板、周期、水印、交付物、冻结…）。这里只保留核心语义列，
--   其余列在导入历史数据时按需补齐 —— 与 zt_stage 等表的处理方式一致。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 文档库 ============================
CREATE TABLE IF NOT EXISTS `zt_doclib` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '文档库编号',
  `type`        varchar(30)  NOT NULL DEFAULT '' COMMENT '库类型：product/project/execution/custom',
  `parent`      bigint       NOT NULL DEFAULT 0 COMMENT '父空间（custom 类型的库挂在「团队空间」下）',
  `product`     bigint       NOT NULL DEFAULT 0 COMMENT '所属产品（type=product 时）',
  `project`     bigint       NOT NULL DEFAULT 0 COMMENT '所属项目（type=project 时）',
  `execution`   bigint       NOT NULL DEFAULT 0 COMMENT '所属执行（type=execution 时）',
  `name`        varchar(255) NOT NULL DEFAULT '' COMMENT '库名称（同空间内唯一）',
  `baseUrl`     varchar(255) NOT NULL DEFAULT '' COMMENT 'API 库的 baseUrl（本实现保留字段）',
  `acl`         varchar(10)  NOT NULL DEFAULT 'open' COMMENT '权限：open 公开 / private 私有',
  `groups`      varchar(255) NOT NULL DEFAULT '' COMMENT '私有库可见角色（逗号列表）',
  `users`       text         DEFAULT NULL COMMENT '私有库可见用户（逗号列表）',
  `main`        tinyint      NOT NULL DEFAULT 0 COMMENT '是否内置主库（主库不允许删除）',
  `desc`        mediumtext   DEFAULT NULL COMMENT '库描述（desc 是 MySQL 保留字）',
  `order`       int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `addedBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `addedDate`   datetime     NULL COMMENT '创建时间（驼峰列名）',
  `archived`    tinyint      NOT NULL DEFAULT 0 COMMENT '是否归档',
  `orderBy`     varchar(30)  NOT NULL DEFAULT 'id_asc' COMMENT '库内默认排序（驼峰列名）',
  -- yudao BaseDO 约定字段
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_object` (`type`, `product`, `project`, `execution`),
  KEY `idx_parent` (`parent`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档库表';

-- ============================ 2. 文档 ============================
CREATE TABLE IF NOT EXISTS `zt_doc` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '文档编号',
  `lib`          bigint       NOT NULL DEFAULT 0 COMMENT '所属文档库（必填）',
  `product`      bigint       NOT NULL DEFAULT 0 COMMENT '所属产品（从库继承）',
  `project`      bigint       NOT NULL DEFAULT 0 COMMENT '所属项目（从库继承）',
  `execution`    bigint       NOT NULL DEFAULT 0 COMMENT '所属执行（从库继承）',
  `module`       bigint       NOT NULL DEFAULT 0 COMMENT '所属模块（通用树 zt_module，type=doc）',
  `title`        varchar(255) NOT NULL DEFAULT '' COMMENT '文档标题',
  `keywords`     varchar(255) NOT NULL DEFAULT '' COMMENT '关键词',
  `type`         varchar(30)  NOT NULL DEFAULT '' COMMENT '类型：chapter/html/markdown/url/word/ppt/excel/attachment',
  `status`       varchar(30)  NOT NULL DEFAULT 'normal' COMMENT '状态：normal 已发布 / draft 草稿',
  `parent`       bigint       NOT NULL DEFAULT 0 COMMENT '父章节（章节树的上级）',
  `path`         varchar(255) NOT NULL DEFAULT '' COMMENT '章节树路径，逗号格式 ,1,2,（含自己）',
  `grade`        tinyint      NOT NULL DEFAULT 1 COMMENT '层级，从 1 开始',
  `order`        int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `views`        int          NOT NULL DEFAULT 0 COMMENT '浏览次数（浏览时 views = views + 1）',
  `collects`     int          NOT NULL DEFAULT 0 COMMENT '收藏次数（本实现保留计数）',
  `draft`        longtext     DEFAULT NULL COMMENT '草稿内容（status=draft 时正文存这里）',
  `version`      int          NOT NULL DEFAULT 1 COMMENT '当前版本号；0 表示只有草稿、还没发布',
  `from`         bigint       NOT NULL DEFAULT 0 COMMENT '复制来源文档（copy 时记录）',
  `fromVersion`  int          NOT NULL DEFAULT 1 COMMENT '复制来源版本（驼峰列名）',
  `addedBy`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `addedDate`    datetime     NULL COMMENT '创建时间（驼峰列名）',
  `assignedTo`   varchar(64)  NOT NULL DEFAULT '' COMMENT '指派给（驼峰列名）',
  `assignedDate` datetime     NULL COMMENT '指派时间（驼峰列名）',
  `editedBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `editedDate`   datetime     NULL COMMENT '最后修改时间（驼峰列名）',
  `acl`          varchar(10)  NOT NULL DEFAULT 'open' COMMENT '权限：open 公开 / private 私有',
  `groups`       varchar(255) NOT NULL DEFAULT '' COMMENT '私有文档可见角色（逗号列表）',
  `users`        text         DEFAULT NULL COMMENT '私有文档可见用户（逗号列表）',
  `vision`       varchar(10)  NOT NULL DEFAULT 'rnd' COMMENT '视野（禅道原字段，保留）',
  -- yudao BaseDO 约定字段
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`      varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_lib` (`lib`, `deleted`),
  KEY `idx_parent_order` (`parent`, `order`),
  KEY `idx_object` (`product`, `project`, `execution`),
  KEY `idx_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表（type=chapter 表示章节）';

-- ============================ 3. 文档版本内容 ============================
-- 与 zt_storyspec 一样有 UNIQUE(doc, version)；注意 version=0 是草稿位。
-- 这张表的行**必须能物理删除**（回滚到历史版本时要清掉多余快照），
-- 所以 DO 里对「删版本」用的是原生 DELETE，见 README 第 4 条坑。
CREATE TABLE IF NOT EXISTS `zt_doccontent` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `doc`         bigint       NOT NULL DEFAULT 0 COMMENT '文档编号',
  `title`       varchar(255) NOT NULL DEFAULT '' COMMENT '该版本的标题',
  `digest`      varchar(255) NOT NULL DEFAULT '' COMMENT '该版本的摘要',
  `content`     longtext     DEFAULT NULL COMMENT '该版本的正文',
  `rawContent`  longtext     DEFAULT NULL COMMENT 'Markdown 原始内容（驼峰列名）',
  `files`       text         DEFAULT NULL COMMENT '附件编号，逗号列表（zt_file.id）',
  `type`        varchar(10)  NOT NULL DEFAULT '' COMMENT '内容类型：html/markdown/text',
  `addedBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '该版本创建人（驼峰列名）',
  `addedDate`   datetime     NULL COMMENT '该版本创建时间（驼峰列名）',
  `editedBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '该版本修改人（驼峰列名）',
  `editedDate`  datetime     NULL COMMENT '该版本修改时间（驼峰列名）',
  `version`     int          NOT NULL DEFAULT 1 COMMENT '版本号，0 为草稿',
  `fromVersion` int          NOT NULL DEFAULT 0 COMMENT '衍生自哪个版本（驼峰列名）',
  -- yudao BaseDO 约定字段
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_version` (`doc`, `version`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档版本内容表（version=0 为草稿）';

-- ============================ 4. 演示数据 ============================
-- id 段刻意用 92101+（文档）/ 92151+（文档库）/ 92111+（版本内容）三个**互不相邻**的段：
--   1. 文档库/文档是测试里最常「建了删」的对象，用低位 id 会撞上历史测试留下的软删除行
--      （README 第 23 条坑）；
--   2. 三张表的自增是**各自独立**的，但演示数据如果把它们放得太近（比如库 91001+、文档 91011+），
--      zt_doclib 的自增很快就会走到 91011 —— 于是「库 id」和「文档 id」变成同一个数字。
--      只要有任何一段代码（哪怕只是测试）把库 id 当文档 id 用，就会静默删掉演示文档。
--      段与段之间留出足够间隔，能让这种混淆一眼可见。
INSERT INTO `zt_doclib` (`id`, `type`, `parent`, `product`, `project`, `execution`, `name`, `acl`, `main`, `desc`, `order`, `addedBy`, `addedDate`, `creator`, `updater`)
VALUES
(92151, 'product',   0, 1, 0, 0,     '产品文档库',     'open', 1, '产品线对外文档',       1, 'admin', NOW(), 'admin', 'admin'),
(92152, 'product',   0, 3, 0, 0,     '门户产品文档库', 'open', 1, '客户门户相关文档',     2, 'admin', NOW(), 'admin', 'admin'),
(92153, 'project',   0, 0, 1, 0,     '项目文档库',     'open', 1, '项目过程文档',         3, 'admin', NOW(), 'admin', 'admin'),
(92154, 'execution', 0, 0, 1, 90001, '迭代文档库',     'open', 1, '迭代内的设计/会议记录', 4, 'admin', NOW(), 'admin', 'admin'),
(92155, 'custom',    0, 0, 0, 0,     '团队空间',       'open', 0, '自定义空间（可放多个库）', 5, 'admin', NOW(), 'admin', 'admin'),
(92156, 'custom',    92155, 0, 0, 0, '公共规范库',     'open', 0, '公司级研发规范',       6, 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `type` = VALUES(`type`), `parent` = VALUES(`parent`), `product` = VALUES(`product`),
    `project` = VALUES(`project`), `execution` = VALUES(`execution`), `name` = VALUES(`name`),
    `acl` = VALUES(`acl`), `main` = VALUES(`main`), `desc` = VALUES(`desc`), `order` = VALUES(`order`),
    `deleted` = b'0';

-- 章节（type=chapter，没有内容）与文档。
-- path 格式与 zt_module 一致：逗号包起来且**包含自己**，如一级 ,92101,、二级 ,92101,92103,。
-- （禅道原来的拼法会产生连续两个逗号 `,92101,,92103,`，本实现统一成单逗号格式，
--   否则子树前缀匹配会失效 —— 见 README 第 14 条坑的同类问题。）
INSERT INTO `zt_doc` (`id`, `lib`, `product`, `project`, `execution`, `module`, `title`, `keywords`, `type`, `status`,
                      `parent`, `path`, `grade`, `order`, `views`, `collects`, `draft`, `version`,
                      `addedBy`, `addedDate`, `editedBy`, `editedDate`, `acl`, `vision`, `creator`, `updater`)
VALUES
(92101, 92151, 1, 0, 0, 0, '需求文档', '需求,规格', 'chapter', 'normal', 0, ',92101,', 1, 92101, 0, 0, NULL, 1,
    'admin', NOW(), 'admin', NOW(), 'open', 'rnd', 'admin', 'admin'),
(92102, 92151, 1, 0, 0, 0, '设计文档', '设计,架构', 'chapter', 'normal', 0, ',92102,', 1, 92102, 0, 0, NULL, 1,
    'admin', NOW(), 'admin', NOW(), 'open', 'rnd', 'admin', 'admin'),
(92103, 92151, 1, 0, 0, 0, '产品需求说明书', '需求,说明书', 'html', 'normal', 92101, ',92101,92103,', 2, 92103, 12, 0, NULL, 2,
    'admin', NOW(), 'admin', NOW(), 'open', 'rnd', 'admin', 'admin'),
(92104, 92151, 1, 0, 0, 0, '架构设计说明', '架构', 'markdown', 'normal', 92102, ',92102,92104,', 2, 92104, 5, 0, NULL, 1,
    'admin', NOW(), 'admin', NOW(), 'open', 'rnd', 'admin', 'admin'),
(92105, 92156, 0, 0, 0, 0, 'Git 提交规范', 'git,规范', 'markdown', 'draft', 0, ',92105,', 1, 92105, 0, 0, '# 草稿\n\n提交前请先跑测试。', 0,
    'admin', NOW(), 'admin', NOW(), 'open', 'rnd', 'admin', 'admin'),
(92106, 92151, 1, 0, 0, 0, '禅道官网', '禅道,链接', 'url', 'normal', 0, ',92106,', 1, 92106, 3, 0, NULL, 1,
    'admin', NOW(), 'admin', NOW(), 'open', 'rnd', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `lib` = VALUES(`lib`), `product` = VALUES(`product`), `project` = VALUES(`project`),
    `execution` = VALUES(`execution`), `title` = VALUES(`title`), `type` = VALUES(`type`),
    `status` = VALUES(`status`), `parent` = VALUES(`parent`), `path` = VALUES(`path`),
    `grade` = VALUES(`grade`), `order` = VALUES(`order`), `views` = VALUES(`views`),
    `draft` = VALUES(`draft`), `version` = VALUES(`version`), `deleted` = b'0';

-- 版本内容：
--   92103 有 v1（初稿）和 v2（加了验收标准）两个快照 —— 用来演示版本链
--   92105 只有 version=0 的草稿行 —— 用来演示「草稿位」
INSERT INTO `zt_doccontent` (`id`, `doc`, `title`, `digest`, `content`, `rawContent`, `files`, `type`,
                             `addedBy`, `addedDate`, `editedBy`, `editedDate`, `version`, `fromVersion`, `creator`, `updater`)
VALUES
(92111, 92103, '产品需求说明书', '初稿', '<h1>产品需求说明书</h1><p>第一版内容。</p>', NULL, '', 'html',
    'admin', '2026-01-10 09:00:00', 'admin', '2026-01-10 09:00:00', 1, 0, 'admin', 'admin'),
(92112, 92103, '产品需求说明书', '补充验收标准', '<h1>产品需求说明书</h1><p>第二版内容，补充了验收标准。</p>', NULL, '', 'html',
    'admin', '2026-02-01 10:30:00', 'admin', '2026-02-01 10:30:00', 2, 1, 'admin', 'admin'),
(92113, 92104, '架构设计说明', '分层设计', '# 架构设计说明

- 接入层
- 业务层
- 存储层', '# 架构设计说明

- 接入层
- 业务层
- 存储层', '', 'markdown',
    'admin', '2026-02-05 14:00:00', 'admin', '2026-02-05 14:00:00', 1, 0, 'admin', 'admin'),
(92114, 92105, 'Git 提交规范', '', '# 草稿

提交前请先跑测试。', '# 草稿

提交前请先跑测试。', '', 'markdown',
    'admin', '2026-03-01 08:00:00', 'admin', '2026-03-01 08:00:00', 0, 0, 'admin', 'admin'),
(92115, 92106, '禅道官网', '', 'https://www.zentao.net/', NULL, '', 'html',
    'admin', '2026-03-02 08:00:00', 'admin', '2026-03-02 08:00:00', 1, 0, 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `content` = VALUES(`content`), `rawContent` = VALUES(`rawContent`),
    `type` = VALUES(`type`), `version` = VALUES(`version`), `deleted` = b'0';

-- ============================ 5. 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90072, '文档管理', 'zentao:doc:query',  2, 11, 90001, 'doc', 'ep:document', 'zentao/doc/index', 'ZentaoDoc', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90073, '文档查询', 'zentao:doc:query',  3, 1, 90072, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90074, '文档创建', 'zentao:doc:create', 3, 2, 90072, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90075, '文档修改', 'zentao:doc:update', 3, 3, 90072, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90076, '文档删除', 'zentao:doc:delete', 3, 4, 90072, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

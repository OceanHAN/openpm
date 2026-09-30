-- ---------------------------------------------------------------------------
-- BI：数据视图（zt_dataview）+ 图表（zt_chart）
--
-- 【禅道语义】禅道 20+ 的 BI 是「**数据视图（数据集）→ 透视表 / 图表**」三层：
--     zt_dataview  数据视图：mode=builder（可视化建）或 mode=sql（写只读 SQL），
--                  配 fields（字段：维度/指标）与 langs（字段中文名），objects 记录数据来源对象
--     zt_pivot     透视表：行/列维度 + 指标 + 过滤器，带 version 与 zt_pivotspec 版本快照
--     zt_chart     图表：name/type（pie/line/bar...）+ settings（维度、指标、聚合）+ filters
-- 底层查询引擎在禅道里是 **bi 模块的 DuckDB + Parquet**（module/bi，25,589 行），
-- 需要把 MySQL 数据同步成 Parquet 文件再查。
--
-- 【本实现的取舍（写进 README 3.35）】不引入 DuckDB，走**禅道自己也支持的 SQL 模式**：
--     zt_dataview.sql 存一条**只读 SELECT**，直接在 MySQL 上执行；
--     执行前过 SqlGuard 白名单（单语句、必须 SELECT/WITH、禁 DML/DDL、只允许 zt_* 表）。
-- 这样「数据视图 + 图表」这条用户可见的链路能完整跑通，DuckDB/Parquet 引擎留给后续。
--
-- 保留字提醒：`group`（两张表都有）、`desc`（chart）、`view` / `sql`（dataview）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 数据视图 ============================
CREATE TABLE IF NOT EXISTS `zt_dataview` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '数据视图编号',
  `group`       bigint       NOT NULL DEFAULT 0 COMMENT '所属分组（group 是 MySQL 保留字）',
  `name`        varchar(155) NOT NULL DEFAULT '' COMMENT '名称',
  `code`        varchar(50)  NOT NULL DEFAULT '' COMMENT '代码（图表用它引用数据视图）',
  `mode`        varchar(50)  NOT NULL DEFAULT 'sql' COMMENT 'builder 可视化建 / sql 写 SQL',
  `driver`      varchar(10)  NOT NULL DEFAULT 'mysql' COMMENT '驱动',
  `view`        varchar(57)  NOT NULL DEFAULT '' COMMENT '物理视图名（本实现只读 SQL，恒为空）',
  `sql`         text         DEFAULT NULL COMMENT '只读查询 SQL',
  `fields`      text         DEFAULT NULL COMMENT '字段定义 JSON：[{field,name,type:dimension|metric,agg}]',
  `langs`       text         DEFAULT NULL COMMENT '字段中文名 JSON',
  `objects`     text         DEFAULT NULL COMMENT '数据来源对象 JSON',
  `createdBy`   varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `createdDate` datetime     DEFAULT NULL COMMENT '创建时间（驼峰列名）',
  `editedBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `editedDate`  datetime     DEFAULT NULL COMMENT '最后修改时间（驼峰列名）',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_group` (`group`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BI 数据视图表';

-- ============================ 2. 图表 ============================
CREATE TABLE IF NOT EXISTS `zt_chart` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '图表编号',
  `name`        varchar(255) NOT NULL DEFAULT '' COMMENT '图表名称',
  `code`        varchar(255) NOT NULL DEFAULT '' COMMENT '图表代码',
  `driver`      varchar(10)  NOT NULL DEFAULT 'mysql' COMMENT '驱动',
  `mode`        varchar(10)  NOT NULL DEFAULT 'sql' COMMENT 'builder / sql',
  `dimension`   bigint       NOT NULL DEFAULT 0 COMMENT '数据维度（禅道指向 zt_dimension；本实现用 settings.dimensionField）',
  `viewCode`    varchar(50)  NOT NULL DEFAULT '' COMMENT '数据视图代码（本实现新增的列：图表引用哪个数据视图）',
  `type`        varchar(30)  NOT NULL DEFAULT 'pie' COMMENT '图表类型：pie/line/cluBarX/stackedBar/...',
  `group`       varchar(255) NOT NULL DEFAULT '' COMMENT '分组（group 是 MySQL 保留字）',
  `desc`        text         DEFAULT NULL COMMENT '描述（desc 是 MySQL 保留字）',
  `acl`         varchar(10)  NOT NULL DEFAULT 'open' COMMENT 'open 公开 / private 私有',
  `whitelist`   text         DEFAULT NULL COMMENT '白名单',
  `settings`    mediumtext   DEFAULT NULL COMMENT '查询设置 JSON：{dimensionField,metricField,agg,limit,sort}',
  `filters`     mediumtext   DEFAULT NULL COMMENT '过滤器 JSON：[{field,operator,value}]',
  `step`        tinyint      NOT NULL DEFAULT 0 COMMENT '创建步骤',
  `fields`      mediumtext   DEFAULT NULL COMMENT '可用字段 JSON（来自数据视图）',
  `langs`       text         DEFAULT NULL COMMENT '字段中文名 JSON',
  `sql`         mediumtext   DEFAULT NULL COMMENT '数据源：只读 SQL（mode=sql）或数据视图代码（viewCode）',
  `version`     varchar(10)  NOT NULL DEFAULT '1' COMMENT '版本号',
  `stage`       varchar(10)  NOT NULL DEFAULT 'draft' COMMENT 'draft 草稿 / published 已发布',
  `builtin`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否内置',
  `objects`     mediumtext   DEFAULT NULL COMMENT '数据来源对象 JSON',
  `createdBy`   varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `createdDate` datetime     DEFAULT NULL COMMENT '创建时间（驼峰列名）',
  `editedBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `editedDate`  datetime     DEFAULT NULL COMMENT '最后修改时间（驼峰列名）',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_code` (`code`, `deleted`),
  KEY `idx_type` (`type`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BI 图表表';

-- ============================ 2.1 老库升级：zt_chart 补 viewCode 列 ============================
-- （第一版建表时没这列；MySQL 8 没有 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后动态执行）
SET @has_view_code := (SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 'zt_chart' AND column_name = 'viewCode');
SET @ddl := IF(@has_view_code = 0,
  'ALTER TABLE `zt_chart` ADD COLUMN `viewCode` varchar(50) NOT NULL DEFAULT '''' COMMENT ''数据视图代码（本实现新增）'' AFTER `dimension`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================ 3. 演示数据：3 个数据视图 ============================
-- 只读 SELECT，直接跑在禅道自己的表上（SqlGuard 只允许 zt_* 表）
INSERT INTO `zt_dataview` (`id`, `group`, `name`, `code`, `mode`, `driver`, `view`, `sql`, `fields`, `langs`, `objects`,
                           `createdBy`, `createdDate`, `creator`, `updater`)
VALUES
(97001, 0, '需求数据', 'story_data', 'sql', 'mysql', '',
 'SELECT id, product, status, pri, estimate, openedBy FROM zt_story WHERE deleted = 0',
 '[{"field":"id","name":"需求数","type":"metric","agg":"count"},{"field":"status","name":"状态","type":"dimension"},{"field":"pri","name":"优先级","type":"dimension"},{"field":"product","name":"产品","type":"dimension"},{"field":"estimate","name":"预计工时","type":"metric","agg":"sum"}]',
 '{"id":"需求数","status":"状态","pri":"优先级","product":"产品","estimate":"预计工时"}',
 '[{"object":"story"}]', 'admin', NOW(), 'admin', 'admin'),
(97002, 0, '任务数据', 'task_data', 'sql', 'mysql', '',
 'SELECT id, project, execution, status, pri, consumed, `left`, assignedTo FROM zt_task WHERE deleted = 0',
 '[{"field":"id","name":"任务数","type":"metric","agg":"count"},{"field":"status","name":"状态","type":"dimension"},{"field":"project","name":"项目","type":"dimension"},{"field":"assignedTo","name":"指派给","type":"dimension"},{"field":"consumed","name":"已消耗工时","type":"metric","agg":"sum"},{"field":"left","name":"剩余工时","type":"metric","agg":"sum"}]',
 '{"id":"任务数","status":"状态","project":"项目","assignedTo":"指派给","consumed":"已消耗工时","left":"剩余工时"}',
 '[{"object":"task"}]', 'admin', NOW(), 'admin', 'admin'),
(97003, 0, '缺陷数据', 'bug_data', 'sql', 'mysql', '',
 'SELECT id, product, severity, status, pri, openedBy FROM zt_bug WHERE deleted = 0',
 '[{"field":"id","name":"缺陷数","type":"metric","agg":"count"},{"field":"severity","name":"严重程度","type":"dimension"},{"field":"status","name":"状态","type":"dimension"},{"field":"product","name":"产品","type":"dimension"},{"field":"openedBy","name":"创建人","type":"dimension"}]',
 '{"id":"缺陷数","severity":"严重程度","status":"状态","product":"产品","openedBy":"创建人"}',
 '[{"object":"bug"}]', 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `group` = VALUES(`group`), `mode` = VALUES(`mode`), `sql` = VALUES(`sql`),
    `fields` = VALUES(`fields`), `langs` = VALUES(`langs`), `objects` = VALUES(`objects`), `deleted` = b'0';

-- ============================ 4. 演示数据：3 个图表 ============================
-- settings 就是「查询设置」：按哪个维度分组、对哪个字段做什么聚合
INSERT INTO `zt_chart` (`id`, `name`, `code`, `driver`, `mode`, `dimension`, `viewCode`, `type`, `group`, `desc`, `acl`, `whitelist`,
                        `settings`, `filters`, `step`, `fields`, `langs`, `sql`, `version`, `stage`, `builtin`, `objects`,
                        `createdBy`, `createdDate`, `creator`, `updater`)
VALUES
(97101, '需求状态分布', 'story_status_pie', 'mysql', 'sql', 0, 'story_data', 'pie', '', '按状态统计研发需求条数', 'open', '',
 '{"dimensionField":"status","metricField":"id","agg":"count","limit":10,"sort":"value_desc"}', '[]', 3,
 '[{"field":"id","name":"需求数","type":"metric","agg":"count"},{"field":"status","name":"状态","type":"dimension"}]',
 '{"id":"需求数","status":"状态"}',
 'SELECT id, status FROM zt_story WHERE deleted = 0', '1', 'published', 1, '[{"object":"story"}]',
 'admin', NOW(), 'admin', 'admin'),
(97102, '任务状态工时分布', 'task_status_hour_bar', 'mysql', 'sql', 0, 'task_data', 'cluBarX', '', '按状态统计任务已消耗工时', 'open', '',
 '{"dimensionField":"status","metricField":"consumed","agg":"sum","limit":10,"sort":"value_desc"}', '[]', 3,
 '[{"field":"consumed","name":"已消耗工时","type":"metric","agg":"sum"},{"field":"status","name":"状态","type":"dimension"}]',
 '{"consumed":"已消耗工时","status":"状态"}',
 'SELECT status, consumed FROM zt_task WHERE deleted = 0', '1', 'published', 1, '[{"object":"task"}]',
 'admin', NOW(), 'admin', 'admin'),
(97103, '缺陷严重程度分布', 'bug_severity_pie', 'mysql', 'sql', 0, 'bug_data', 'pie', '', '按严重程度统计缺陷条数', 'open', '',
 '{"dimensionField":"severity","metricField":"id","agg":"count","limit":10,"sort":"value_desc"}', '[]', 3,
 '[{"field":"id","name":"缺陷数","type":"metric","agg":"count"},{"field":"severity","name":"严重程度","type":"dimension"}]',
 '{"id":"缺陷数","severity":"严重程度"}',
 'SELECT id, severity FROM zt_bug WHERE deleted = 0', '1', 'published', 1, '[{"object":"bug"}]',
 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `type` = VALUES(`type`), `viewCode` = VALUES(`viewCode`), `settings` = VALUES(`settings`), `sql` = VALUES(`sql`),
    `fields` = VALUES(`fields`), `langs` = VALUES(`langs`), `stage` = 'published', `deleted` = b'0';

-- ============================ 5. 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90140, '数据视图', 'zentao:bi:query',  2, 18, 90001, 'bi', 'ep:histogram', 'zentao/bi/index', 'ZentaoBi', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90141, '视图查询', 'zentao:bi:query',  3, 1, 90140, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90142, '视图创建', 'zentao:bi:create', 3, 2, 90140, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90143, '视图修改', 'zentao:bi:update', 3, 3, 90140, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90144, '视图删除', 'zentao:bi:delete', 3, 4, 90140, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

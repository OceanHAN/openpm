-- ---------------------------------------------------------------------------
-- 任务表 zt_task + 缺陷表 zt_bug
--
-- 与需求一样：表名与核心字段名对齐禅道，便于迁移与左右对照。
-- 追加 yudao BaseDO 约定字段（create_time / update_time / creator / updater / deleted）。
--
-- 注意 MySQL 关键字：zt_task.desc 必须加反引号。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 任务 ============================
CREATE TABLE IF NOT EXISTS `zt_task` (
  `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '任务编号',
  -- 归属
  `project`         bigint        NOT NULL DEFAULT 0 COMMENT '所属项目',
  `execution`       bigint        NOT NULL DEFAULT 0 COMMENT '所属执行',
  `module`          bigint        NOT NULL DEFAULT 0 COMMENT '所属模块',
  `story`           bigint        NOT NULL DEFAULT 0 COMMENT '关联需求',
  `fromBug`         bigint        NOT NULL DEFAULT 0 COMMENT '来源 Bug',
  -- 内容
  `name`            varchar(255)  NOT NULL DEFAULT '' COMMENT '任务名称',
  `type`            varchar(20)   NOT NULL DEFAULT '' COMMENT '任务类型',
  `pri`             tinyint       NOT NULL DEFAULT 3 COMMENT '优先级 1~4',
  `estimate`        decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '预计工时',
  `consumed`        decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '已消耗工时',
  `left`            decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '剩余工时',
  `deadline`        date          NULL COMMENT '截止日期',
  `keywords`        varchar(255)  NOT NULL DEFAULT '' COMMENT '关键词',
  `desc`            mediumtext    NULL COMMENT '任务描述（desc 是 MySQL 关键字，需反引号）',
  `version`         smallint      NOT NULL DEFAULT 1 COMMENT '版本号',
  -- 状态机
  `status`          varchar(10)   NOT NULL DEFAULT 'wait' COMMENT '状态：wait/doing/done/pause/cancel/closed',
  -- 生命周期
  `openedBy`        varchar(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  `openedDate`      datetime      NULL COMMENT '创建时间',
  `assignedTo`      varchar(64)   NOT NULL DEFAULT '' COMMENT '指派给',
  `assignedDate`    datetime      NULL COMMENT '指派时间',
  `estStarted`      date          NULL COMMENT '预计开始',
  `realStarted`     datetime      NULL COMMENT '实际开始',
  `finishedBy`      varchar(64)   NOT NULL DEFAULT '' COMMENT '完成人',
  `finishedDate`    datetime      NULL COMMENT '完成时间',
  `canceledBy`      varchar(64)   NOT NULL DEFAULT '' COMMENT '取消人',
  `canceledDate`    datetime      NULL COMMENT '取消时间',
  `closedBy`        varchar(64)   NOT NULL DEFAULT '' COMMENT '关闭人',
  `closedDate`      datetime      NULL COMMENT '关闭时间',
  `closedReason`    varchar(30)   NOT NULL DEFAULT '' COMMENT '关闭原因',
  `lastEditedBy`    varchar(64)   NOT NULL DEFAULT '' COMMENT '最后修改人',
  `lastEditedDate`  datetime      NULL COMMENT '最后修改时间',
  `activatedDate`   datetime      NULL COMMENT '激活时间',
  -- yudao BaseDO 约定字段
  `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`         varchar(64)   NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`         varchar(64)   NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`         bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_execution_status` (`execution`, `status`),
  KEY `idx_assignedTo` (`assignedTo`),
  KEY `idx_story` (`story`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- ============================ 缺陷 ============================
CREATE TABLE IF NOT EXISTS `zt_bug` (
  `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '缺陷编号',
  -- 归属
  `product`         bigint        NOT NULL DEFAULT 0 COMMENT '所属产品',
  `project`         bigint        NOT NULL DEFAULT 0 COMMENT '所属项目',
  `execution`       bigint        NOT NULL DEFAULT 0 COMMENT '所属执行',
  `module`          bigint        NOT NULL DEFAULT 0 COMMENT '所属模块',
  `plan`            bigint        NOT NULL DEFAULT 0 COMMENT '所属计划',
  `story`           bigint        NOT NULL DEFAULT 0 COMMENT '关联需求',
  `task`            bigint        NOT NULL DEFAULT 0 COMMENT '关联任务',
  -- 内容
  `title`           varchar(255)  NOT NULL DEFAULT '' COMMENT '缺陷标题',
  `keywords`        varchar(255)  NOT NULL DEFAULT '' COMMENT '关键词',
  `severity`        tinyint       NOT NULL DEFAULT 3 COMMENT '严重程度 1~4',
  `pri`             tinyint       NOT NULL DEFAULT 3 COMMENT '优先级 1~4',
  `type`            varchar(30)   NOT NULL DEFAULT '' COMMENT '缺陷类型',
  `os`              varchar(255)  NOT NULL DEFAULT '' COMMENT '操作系统',
  `browser`         varchar(255)  NOT NULL DEFAULT '' COMMENT '浏览器',
  `steps`           mediumtext    NULL COMMENT '重现步骤',
  -- 状态机
  `status`          varchar(10)   NOT NULL DEFAULT 'active' COMMENT '状态：active/resolved/closed',
  `confirmed`       tinyint       NOT NULL DEFAULT 0 COMMENT '是否已确认',
  `activatedCount`  smallint      NOT NULL DEFAULT 0 COMMENT '激活次数',
  `activatedDate`   datetime      NULL COMMENT '最后激活时间',
  -- 生命周期
  `openedBy`        varchar(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  `openedDate`      datetime      NULL COMMENT '创建时间',
  `openedBuild`     varchar(255)  NOT NULL DEFAULT '' COMMENT '影响版本',
  `assignedTo`      varchar(64)   NOT NULL DEFAULT '' COMMENT '指派给',
  `assignedDate`    datetime      NULL COMMENT '指派时间',
  `deadline`        date          NULL COMMENT '截止日期',
  `resolvedBy`      varchar(64)   NOT NULL DEFAULT '' COMMENT '解决人',
  `resolution`      varchar(30)   NOT NULL DEFAULT '' COMMENT '解决方案',
  `resolvedBuild`   varchar(30)   NOT NULL DEFAULT '' COMMENT '解决版本',
  `resolvedDate`    datetime      NULL COMMENT '解决时间',
  `closedBy`        varchar(64)   NOT NULL DEFAULT '' COMMENT '关闭人',
  `closedDate`      datetime      NULL COMMENT '关闭时间',
  `duplicateBug`    bigint        NOT NULL DEFAULT 0 COMMENT '重复缺陷编号',
  `lastEditedBy`    varchar(64)   NOT NULL DEFAULT '' COMMENT '最后修改人',
  `lastEditedDate`  datetime      NULL COMMENT '最后修改时间',
  -- yudao BaseDO 约定字段
  `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`         varchar(64)   NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`         varchar(64)   NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`         bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product_status` (`product`, `status`),
  KEY `idx_assignedTo` (`assignedTo`),
  KEY `idx_resolvedBy` (`resolvedBy`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缺陷表';

-- ============================ 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90007, '任务管理', 'zentao:task:query', 2, 2, 90001, 'task', 'ep:list', 'zentao/task/index', 'ZentaoTask', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90008, '任务查询', 'zentao:task:query',  3, 1, 90007, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90009, '任务创建', 'zentao:task:create', 3, 2, 90007, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90010, '任务修改', 'zentao:task:update', 3, 3, 90007, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90011, '任务删除', 'zentao:task:delete', 3, 4, 90007, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90012, '缺陷管理', 'zentao:bug:query', 2, 3, 90001, 'bug', 'ep:warning', 'zentao/bug/index', 'ZentaoBug', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90013, '缺陷查询', 'zentao:bug:query',  3, 1, 90012, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90014, '缺陷创建', 'zentao:bug:create', 3, 2, 90012, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90015, '缺陷修改', 'zentao:bug:update', 3, 3, 90012, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90016, '缺陷删除', 'zentao:bug:delete', 3, 4, 90012, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

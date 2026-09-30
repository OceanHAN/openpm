-- ---------------------------------------------------------------------------
-- 项目表 zt_project
--
-- 项目是「产品 → 项目 → 执行 → 任务」主干链的中间层：
--   产品(product) 管需求池
--   项目(project) 管交付范围与周期
--   执行(execution) 是项目下的迭代/阶段，任务挂在执行上
--
-- 禅道 zt_project 有 70+ 字段，其中一部分是看板展示设置（colWidth / fluidBoard /
-- displayCards）属于纯 UI 偏好，本实现不纳入。
--
-- 注意 MySQL 保留字：desc、order 必须加反引号。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_project` (
  `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '项目编号',
  -- 层级与模型
  `parent`         bigint        NOT NULL DEFAULT 0 COMMENT '父项目',
  `path`           varchar(255)  NOT NULL DEFAULT '' COMMENT '层级路径',
  `grade`          tinyint       NOT NULL DEFAULT 0 COMMENT '层级深度',
  `isTpl`          tinyint       NOT NULL DEFAULT 0 COMMENT '是否为项目模板',
  `model`          varchar(30)   NOT NULL DEFAULT 'scrum' COMMENT '模型：scrum/waterfall/kanban/agileplus/waterfallplus',
  `type`           varchar(30)   NOT NULL DEFAULT '' COMMENT '项目类型',
  `category`       varchar(30)   NOT NULL DEFAULT '' COMMENT '项目分类',
  `lifetime`       varchar(30)   NOT NULL DEFAULT '' COMMENT '生命周期',
  -- 内容
  `name`           varchar(90)   NOT NULL DEFAULT '' COMMENT '项目名称',
  `code`           varchar(45)   NOT NULL DEFAULT '' COMMENT '项目代号',
  `desc`           mediumtext    NULL COMMENT '项目描述（desc 是保留字）',
  `output`         text          NULL COMMENT '交付物',
  -- 产品关联
  `hasProduct`     tinyint       NOT NULL DEFAULT 0 COMMENT '是否关联产品；为 0 时关闭项目会连带关闭自动创建的产品',
  `multiple`       tinyint       NOT NULL DEFAULT 0 COMMENT '是否多执行；为 0 时关闭项目会连带关闭其执行',
  `storyType`      varchar(30)   NOT NULL DEFAULT 'story' COMMENT '需求类型',
  -- 预算
  `budget`         decimal(14,2) NOT NULL DEFAULT 0.00 COMMENT '预算',
  `budgetUnit`     varchar(30)   NOT NULL DEFAULT 'CNY' COMMENT '预算币种',
  -- 周期
  `begin`          date          NULL COMMENT '计划开始',
  `end`            date          NULL COMMENT '计划结束',
  `realBegan`      date          NULL COMMENT '实际开始',
  `realEnd`        date          NULL COMMENT '实际结束',
  `days`           smallint      NOT NULL DEFAULT 0 COMMENT '可用工作日',
  -- 状态机
  `status`         varchar(10)   NOT NULL DEFAULT 'wait' COMMENT '状态：wait/doing/suspended/closed/delay',
  `pri`            tinyint       NOT NULL DEFAULT 3 COMMENT '优先级 1~4',
  `milestone`      tinyint       NOT NULL DEFAULT 0 COMMENT '是否为里程碑',
  -- 工时（与任务同构：预计/剩余/已消耗）
  `estimate`       decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '预计工时',
  `left`           decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '剩余工时（left 是保留字）',
  `consumed`       decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '已消耗工时',
  `progress`       decimal(5,2)  NOT NULL DEFAULT 0.00 COMMENT '进度百分比',
  -- 角色与团队
  `PO`             varchar(64)   NOT NULL DEFAULT '' COMMENT '产品负责人',
  `PM`             varchar(64)   NOT NULL DEFAULT '' COMMENT '项目经理',
  `QD`             varchar(64)   NOT NULL DEFAULT '' COMMENT '测试负责人',
  `RD`             varchar(64)   NOT NULL DEFAULT '' COMMENT '研发负责人',
  `team`           varchar(255)  NOT NULL DEFAULT '' COMMENT '团队成员，逗号分隔（禅道原为 varchar(90)）',
  `teamCount`      int           NOT NULL DEFAULT 0 COMMENT '团队人数',
  `acl`            varchar(10)   NOT NULL DEFAULT 'open' COMMENT '访问控制：open/private',
  `whitelist`      text          NULL COMMENT '白名单',
  -- 生命周期
  `openedBy`       varchar(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  `openedDate`     datetime      NULL COMMENT '创建时间',
  `lastEditedBy`   varchar(64)   NOT NULL DEFAULT '' COMMENT '最后修改人',
  `lastEditedDate` datetime      NULL COMMENT '最后修改时间',
  `closedBy`       varchar(64)   NOT NULL DEFAULT '' COMMENT '关闭人',
  `closedDate`     datetime      NULL COMMENT '关闭时间',
  `closedReason`   varchar(30)   NOT NULL DEFAULT '' COMMENT '关闭原因',
  `canceledBy`     varchar(64)   NOT NULL DEFAULT '' COMMENT '取消人',
  `canceledDate`   datetime      NULL COMMENT '取消时间',
  `suspendedDate`  datetime      NULL COMMENT '挂起时间',
  `activatedDate`  datetime      NULL COMMENT '激活时间',
  `order`          int           NOT NULL DEFAULT 0 COMMENT '排序（order 是保留字）',
  -- yudao BaseDO 约定字段
  `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)   NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)   NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_parent` (`parent`),
  KEY `idx_PM` (`PM`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

-- 菜单与权限
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90022, '项目管理', 'zentao:project:query', 2, 1, 90001, 'project', 'ep:folder', 'zentao/project/index', 'ZentaoProject', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90023, '项目查询', 'zentao:project:query',  3, 1, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90024, '项目创建', 'zentao:project:create', 3, 2, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90025, '项目修改', 'zentao:project:update', 3, 3, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90026, '项目删除', 'zentao:project:delete', 3, 4, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

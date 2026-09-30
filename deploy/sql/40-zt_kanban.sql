-- ---------------------------------------------------------------------------
-- 看板（kanban）—— 空间 / 看板 / 区域 / 泳道 / 列 / 卡片 / 格子
--
-- 【禅道语义】看板是一个**七层聚合**，八张表（module/kanban，9,545 行）：
--     zt_kanbanspace   空间（私人/协作/公共空间）—— 看板的容器
--     zt_kanban        看板本体（acl、archived 启用位、displayCards/showWIP/fluidBoard/colWidth、object）
--     zt_kanbanregion  区域（一个看板可以有多个区域，每个区域是一块独立的板）
--     zt_kanbangroup   分组（**泳道与列通过 group 归属**；一个区域默认一个 group）
--     zt_kanbanlane    泳道（横向；type=common 或 story/bug/task 这类「按对象分泳道」）
--     zt_kanbancolumn  列（纵向；parent>0 是子列，limit 是在制品上限 WIP，-1 = 不限）
--     zt_kanbancard    卡片（status: doing/done，progress 0~100，archived 归档位）
--     zt_kanbancell    格子 = 泳道 × 列，`cards` 是**逗号列表**（卡片在板上的位置与顺序）
--
-- 【几条必须照抄的规则】
--   1. 新建看板/区域时会**自动建一套默认布局**：1 个 group + 「默认泳道」+
--      4 个默认列（未开始/进行中/已完成/已关闭，limit=-1 不限），并给每个
--      泳道×列 建好 cell（module/kanban/model.php:createRegion → createDefaultLane/Columns）
--   2. 列的 limit（WIP）只允许 -1 或正整数；**子列的 limit 之和不能超过父列的 limit**，
--      且父列有限额时子列不能是 -1（createColumn / checkChildColumn）
--   3. 卡片移动 = 从该区域「同类型泳道」的所有 cell 里摘掉，再追加到目标 cell 的 cards 末尾，
--      并把 card.group 改成目标泳道的 group（model.php:moveCard）
--   4. **WIP 超限后端不拦**：禅道只在界面把 (卡片数/限额) 标红（module/kanban/js/view.ui.js:63），
--      所以本实现也只在数据接口里返回 overWip 提示，不阻止移动
--   5. 卡片完成 = progress 100 + status done；激活 = status doing + progress（0~100，不能到 100）
--   6. 删除：泳道/区域/看板/空间是逻辑删除，**列与卡片是物理删除**（禅道 control 里就是 dao->delete）
--
-- 保留字提醒：本模块一口气踩了 `order` / `limit` / `group` / `column` / `desc` / `begin` / `end`
-- 七个（前四个是 MySQL 保留字，后三个是 JSqlParser 的雷，见根 README 第 10、11 条坑）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 空间 ============================
CREATE TABLE IF NOT EXISTS `zt_kanbanspace` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '空间编号',
  `name`           varchar(255) NOT NULL DEFAULT '' COMMENT '空间名称',
  `type`           varchar(50)  NOT NULL DEFAULT '' COMMENT 'private 私人 / cooperation 协作 / public 公共',
  `owner`          varchar(30)  NOT NULL DEFAULT '' COMMENT '负责人',
  `team`           text         DEFAULT NULL COMMENT '团队成员（逗号列表）',
  `desc`           mediumtext   DEFAULT NULL COMMENT '描述（desc 是 MySQL 保留字）',
  `acl`            varchar(10)  NOT NULL DEFAULT 'open' COMMENT 'open 公开 / private 私有',
  `whitelist`      text         DEFAULT NULL COMMENT '白名单（逗号列表）',
  `status`         varchar(10)  NOT NULL DEFAULT 'active' COMMENT 'active 正常 / closed 已关闭',
  `order`          int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `createdBy`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `createdDate`    datetime     DEFAULT NULL COMMENT '创建时间（驼峰列名）',
  `lastEditedBy`   varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `lastEditedDate` datetime     DEFAULT NULL COMMENT '最后修改时间（驼峰列名）',
  `closedBy`       varchar(64)  NOT NULL DEFAULT '' COMMENT '关闭人（驼峰列名）',
  `closedDate`     datetime     DEFAULT NULL COMMENT '关闭时间（驼峰列名）',
  `activatedBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '激活人（驼峰列名）',
  `activatedDate`  datetime     DEFAULT NULL COMMENT '激活时间（驼峰列名）',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_type_status` (`type`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板空间表';

-- ============================ 2. 看板 ============================
CREATE TABLE IF NOT EXISTS `zt_kanban` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '看板编号',
  `space`          bigint       NOT NULL DEFAULT 0 COMMENT '所属空间',
  `name`           varchar(255) NOT NULL DEFAULT '' COMMENT '看板名称',
  `owner`          varchar(30)  NOT NULL DEFAULT '' COMMENT '负责人',
  `team`           text         DEFAULT NULL COMMENT '团队（逗号列表）',
  `desc`           mediumtext   DEFAULT NULL COMMENT '描述（desc 是 MySQL 保留字）',
  `acl`            varchar(10)  NOT NULL DEFAULT 'open' COMMENT 'open 公开 / private 私有',
  `whitelist`      text         DEFAULT NULL COMMENT '白名单（逗号列表）',
  `archived`       tinyint      NOT NULL DEFAULT 1 COMMENT '是否启用归档功能',
  `performable`    tinyint      NOT NULL DEFAULT 0 COMMENT '是否可执行',
  `status`         varchar(10)  NOT NULL DEFAULT 'active' COMMENT 'active 正常 / closed 已关闭',
  `order`          int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `displayCards`   smallint     NOT NULL DEFAULT 0 COMMENT '卡片显示数量，0=不限',
  `showWIP`        tinyint      NOT NULL DEFAULT 1 COMMENT '是否显示在制品数量',
  `fluidBoard`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否流式布局',
  `colWidth`       smallint     NOT NULL DEFAULT 264 COMMENT '列宽（流式关）',
  `minColWidth`    smallint     NOT NULL DEFAULT 200 COMMENT '列最小宽（流式开）',
  `maxColWidth`    smallint     NOT NULL DEFAULT 384 COMMENT '列最大宽（流式开）',
  `object`         varchar(255) NOT NULL DEFAULT '' COMMENT '关联对象',
  `alignment`      varchar(10)  NOT NULL DEFAULT 'center' COMMENT '对齐：center 居中 / left 居左',
  `createdBy`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `createdDate`    datetime     DEFAULT NULL COMMENT '创建时间（驼峰列名）',
  `lastEditedBy`   varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `lastEditedDate` datetime     DEFAULT NULL COMMENT '最后修改时间（驼峰列名）',
  `closedBy`       varchar(64)  NOT NULL DEFAULT '' COMMENT '关闭人（驼峰列名）',
  `closedDate`     datetime     DEFAULT NULL COMMENT '关闭时间（驼峰列名）',
  `activatedBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '激活人（驼峰列名）',
  `activatedDate`  datetime     DEFAULT NULL COMMENT '激活时间（驼峰列名）',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_space` (`space`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板表';

-- ============================ 3. 区域 ============================
CREATE TABLE IF NOT EXISTS `zt_kanbanregion` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '区域编号',
  `space`          bigint       NOT NULL DEFAULT 0 COMMENT '所属空间',
  `kanban`         bigint       NOT NULL DEFAULT 0 COMMENT '所属看板',
  `name`           varchar(255) NOT NULL DEFAULT '' COMMENT '区域名称',
  `order`          int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `createdBy`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `createdDate`    datetime     DEFAULT NULL COMMENT '创建时间（驼峰列名）',
  `lastEditedBy`   varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `lastEditedDate` datetime     DEFAULT NULL COMMENT '最后修改时间（驼峰列名）',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_kanban` (`kanban`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板区域表';

-- ============================ 4. 分组（**没有 deleted 列**，跟着区域一起维护） ============================
CREATE TABLE IF NOT EXISTS `zt_kanbangroup` (
  `id`     bigint NOT NULL AUTO_INCREMENT COMMENT '分组编号',
  `kanban` bigint NOT NULL DEFAULT 0 COMMENT '所属看板',
  `region` bigint NOT NULL DEFAULT 0 COMMENT '所属区域',
  `order`  int    NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  PRIMARY KEY (`id`),
  KEY `idx_region` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板分组表（无 deleted 列）';

-- ============================ 5. 泳道 ============================
CREATE TABLE IF NOT EXISTS `zt_kanbanlane` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '泳道编号',
  `execution`      bigint       NOT NULL DEFAULT 0 COMMENT '所属执行（研发看板用，普通看板为 0）',
  `type`           varchar(30)  NOT NULL DEFAULT '' COMMENT 'common 普通 / story 需求 / bug 缺陷 / task 任务',
  `region`         bigint       NOT NULL DEFAULT 0 COMMENT '所属区域',
  `group`          bigint       NOT NULL DEFAULT 0 COMMENT '所属分组（group 是 MySQL 保留字）',
  `groupby`        varchar(30)  NOT NULL DEFAULT '' COMMENT '按什么分组',
  `extra`          varchar(30)  NOT NULL DEFAULT '' COMMENT '扩展值',
  `name`           varchar(255) NOT NULL DEFAULT '' COMMENT '泳道名称',
  `color`          char(7)      NOT NULL DEFAULT '' COMMENT '泳道颜色',
  `order`          int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `lastEditedTime` datetime     DEFAULT NULL COMMENT '最后修改时间（驼峰列名）',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_group` (`group`, `deleted`),
  KEY `idx_region` (`region`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板泳道表';

-- ============================ 6. 看板列 ============================
CREATE TABLE IF NOT EXISTS `zt_kanbancolumn` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '列编号',
  `parent`      bigint       NOT NULL DEFAULT 0 COMMENT '父列（>0 是子列；拆列后父列是 -1）',
  `type`        varchar(30)  NOT NULL DEFAULT '' COMMENT '列类型（禅道存 column{自己id}）',
  `region`      bigint       NOT NULL DEFAULT 0 COMMENT '所属区域',
  `group`       bigint       NOT NULL DEFAULT 0 COMMENT '所属分组（group 是 MySQL 保留字）',
  `name`        varchar(255) NOT NULL DEFAULT '' COMMENT '列名称',
  `color`       char(7)      NOT NULL DEFAULT '' COMMENT '列颜色',
  `limit`       int          NOT NULL DEFAULT -1 COMMENT '在制品上限 WIP，-1=不限（limit 是 MySQL 保留字）',
  `order`       int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `archived`    tinyint      NOT NULL DEFAULT 0 COMMENT '是否归档',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_group` (`group`, `deleted`),
  KEY `idx_parent` (`parent`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板列表';

-- ============================ 7. 卡片 ============================
CREATE TABLE IF NOT EXISTS `zt_kanbancard` (
  `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '卡片编号',
  `kanban`         bigint        NOT NULL DEFAULT 0 COMMENT '所属看板',
  `region`         bigint        NOT NULL DEFAULT 0 COMMENT '所属区域',
  `group`          bigint        NOT NULL DEFAULT 0 COMMENT '所属分组（group 是 MySQL 保留字）',
  `fromID`         bigint        NOT NULL DEFAULT 0 COMMENT '来源对象编号（驼峰列名）',
  `fromType`       varchar(30)   NOT NULL DEFAULT '' COMMENT '来源对象类型（驼峰列名）：空=看板自建卡片',
  `name`           varchar(255)  NOT NULL DEFAULT '' COMMENT '卡片标题',
  `status`         varchar(30)   NOT NULL DEFAULT 'doing' COMMENT 'doing 进行中 / done 已完成',
  `pri`            int           NOT NULL DEFAULT 0 COMMENT '优先级',
  `assignedTo`     text          DEFAULT NULL COMMENT '指派给（驼峰列名，逗号列表）',
  `desc`           mediumtext    DEFAULT NULL COMMENT '描述（desc 是 MySQL 保留字）',
  `begin`          date          DEFAULT NULL COMMENT '预计开始（begin 是 JSqlParser 的雷）',
  `end`            date          DEFAULT NULL COMMENT '截止日期（end 是 JSqlParser 的雷）',
  `estimate`       decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '预计工时',
  `progress`       decimal(5,2)  NOT NULL DEFAULT 0.00 COMMENT '进度 0~100',
  `color`          char(7)       NOT NULL DEFAULT '' COMMENT '卡片颜色',
  `acl`            varchar(10)   NOT NULL DEFAULT 'open' COMMENT 'open 公开 / private 私有',
  `whitelist`      text          DEFAULT NULL COMMENT '白名单（逗号列表）',
  `order`          int           NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `archived`       tinyint       NOT NULL DEFAULT 0 COMMENT '是否归档',
  `createdBy`      varchar(64)   NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `createdDate`    datetime      DEFAULT NULL COMMENT '创建时间（驼峰列名）',
  `lastEditedBy`   varchar(64)   NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `lastEditedDate` datetime      DEFAULT NULL COMMENT '最后修改时间（驼峰列名）',
  `archivedBy`     varchar(64)   NOT NULL DEFAULT '' COMMENT '归档人（驼峰列名）',
  `archivedDate`   datetime      DEFAULT NULL COMMENT '归档时间（驼峰列名）',
  `assignedBy`     varchar(64)   NOT NULL DEFAULT '' COMMENT '指派人人（驼峰列名）',
  `assignedDate`   datetime      DEFAULT NULL COMMENT '指派时间（驼峰列名）',
  `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)   NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)   NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_kanban` (`kanban`, `archived`, `deleted`),
  KEY `idx_region` (`region`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板卡片表';

-- ============================ 8. 格子（泳道 × 列，**没有 deleted 列**） ============================
-- cards 是**逗号列表**，两头都带逗号（如 ',96501,'）—— 与 zt_story.plan 同一套路，
-- 增删改都用字符串替换；格子存在与否不影响卡片本体。
CREATE TABLE IF NOT EXISTS `zt_kanbancell` (
  `id`      bigint    NOT NULL AUTO_INCREMENT COMMENT '格子编号',
  `kanban`  bigint    NOT NULL DEFAULT 0 COMMENT '所属看板',
  `lane`    bigint    NOT NULL DEFAULT 0 COMMENT '所属泳道',
  `column`  bigint    NOT NULL DEFAULT 0 COMMENT '所属列（column 是 MySQL 保留字）',
  `type`    varchar(30) NOT NULL DEFAULT '' COMMENT '格子类型（跟随泳道 type）',
  `cards`   mediumtext DEFAULT NULL COMMENT '卡片编号逗号列表',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lane_column` (`kanban`, `type`, `lane`, `column`),
  KEY `idx_lane` (`lane`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板格子表（泳道×列，无 deleted 列）';

-- ============================ 9. 演示数据 ============================
-- id 分段：空间 96001 ｜ 看板 96101 ｜ 区域 96201 ｜ 分组 96251 ｜ 泳道 96301+ ｜ 列 96401+
--           ｜ 卡片 96501+ ｜ 格子 96601+
INSERT INTO `zt_kanbanspace` (`id`, `name`, `type`, `owner`, `team`, `desc`, `acl`, `whitelist`, `status`, `order`,
                              `createdBy`, `createdDate`, `lastEditedBy`, `lastEditedDate`, `creator`, `updater`)
VALUES (96001, '禅道研发空间', 'cooperation', 'admin', 'admin,tester',
        '看板空间：放所有与禅道迁移相关的看板。', 'open', '', 'active', 1,
        'admin', NOW(), 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `type` = VALUES(`type`), `owner` = VALUES(`owner`),
    `team` = VALUES(`team`), `desc` = VALUES(`desc`), `status` = 'active', `deleted` = b'0';

INSERT INTO `zt_kanban` (`id`, `space`, `name`, `owner`, `team`, `desc`, `acl`, `whitelist`, `archived`, `performable`,
                         `status`, `order`, `displayCards`, `showWIP`, `fluidBoard`, `colWidth`, `minColWidth`, `maxColWidth`,
                         `object`, `alignment`, `createdBy`, `createdDate`, `lastEditedBy`, `lastEditedDate`, `creator`, `updater`)
VALUES (96101, 96001, '禅道迁移看板', 'admin', 'admin,tester',
        '演示看板：默认区域 + 两条泳道 + 四个默认列 + 两张卡片。', 'extend', '', 1, 0,
        'active', 1, 0, 1, 0, 264, 200, 384,
        '', 'center', 'admin', NOW(), 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `space` = VALUES(`space`), `owner` = VALUES(`owner`),
    `team` = VALUES(`team`), `desc` = VALUES(`desc`), `acl` = VALUES(`acl`),
    `showWIP` = VALUES(`showWIP`), `status` = 'active', `deleted` = b'0';

INSERT INTO `zt_kanbanregion` (`id`, `space`, `kanban`, `name`, `order`, `createdBy`, `createdDate`,
                               `lastEditedBy`, `lastEditedDate`, `creator`, `updater`)
VALUES (96201, 96001, 96101, '默认区域', 1, 'admin', NOW(), 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `kanban` = VALUES(`kanban`), `deleted` = b'0';

INSERT INTO `zt_kanbangroup` (`id`, `kanban`, `region`, `order`)
VALUES (96251, 96101, 96201, 1)
ON DUPLICATE KEY UPDATE `kanban` = VALUES(`kanban`), `region` = VALUES(`region`), `order` = VALUES(`order`);

INSERT INTO `zt_kanbanlane` (`id`, `execution`, `type`, `region`, `group`, `groupby`, `extra`, `name`, `color`, `order`,
                             `lastEditedTime`, `creator`, `updater`)
VALUES
(96301, 0, 'common', 96201, 96251, '', '', '默认泳道', '#7ec5ff', 1, NOW(), 'admin', 'admin'),
(96302, 0, 'story',  96201, 96251, '', '', '需求泳道', '#476BDA', 2, NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `color` = VALUES(`color`), `order` = VALUES(`order`),
    `type` = VALUES(`type`), `deleted` = b'0';

INSERT INTO `zt_kanbancolumn` (`id`, `parent`, `type`, `region`, `group`, `name`, `color`, `limit`, `order`, `archived`,
                               `creator`, `updater`)
VALUES
(96401, 0, 'column96401', 96201, 96251, '未开始', '#333', -1, 1, 0, 'admin', 'admin'),
(96402, 0, 'column96402', 96201, 96251, '进行中', '#2b519c', 3, 2, 0, 'admin', 'admin'),
(96403, 0, 'column96403', 96201, 96251, '已完成', '#2a9f23', -1, 3, 0, 'admin', 'admin'),
(96404, 0, 'column96404', 96201, 96251, '已关闭', '#777', -1, 4, 0, 'admin', 'admin')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `limit` = VALUES(`limit`), `color` = VALUES(`color`),
    `order` = VALUES(`order`), `archived` = 0, `deleted` = b'0';

INSERT INTO `zt_kanbancard` (`id`, `kanban`, `region`, `group`, `fromID`, `fromType`, `name`, `status`, `pri`, `assignedTo`,
                             `desc`, `begin`, `end`, `estimate`, `progress`, `color`, `acl`, `whitelist`, `order`, `archived`,
                             `createdBy`, `createdDate`, `lastEditedBy`, `lastEditedDate`, `creator`, `updater`)
VALUES
(96501, 96101, 96201, 96251, 0, '', '打通登录链路', 'doing', 2, 'dev1',
 '登录接口 + 校验 + 失败提示，一条链走通。', '2026-03-01', '2026-03-10', 8.00, 40.00, '#fff', 'open', '', 0, 0,
 'admin', NOW(), 'admin', NOW(), 'admin', 'admin'),
(96502, 96101, 96201, 96251, 0, '', '需求池分层视图', 'doing', 3, 'admin',
 '需求池按业务需求/用户需求/研发需求三层展示。', NULL, NULL, 5.00, 0.00, '#fff', 'open', '', 0, 0,
 'admin', NOW(), 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = VALUES(`status`), `progress` = VALUES(`progress`),
    `archived` = 0, `deleted` = b'0';

-- 格子：两条泳道 × 四个列都要有（与 createDefaultColumns 的行为一致），再把两张卡片放进对应格子
INSERT INTO `zt_kanbancell` (`id`, `kanban`, `lane`, `column`, `type`, `cards`)
VALUES
(96601, 96101, 96301, 96401, 'common', ''),
(96602, 96101, 96301, 96402, 'common', ',96501,'),
(96603, 96101, 96301, 96403, 'common', ''),
(96604, 96101, 96301, 96404, 'common', ''),
(96605, 96101, 96302, 96401, 'story', ',96502,'),
(96606, 96101, 96302, 96402, 'story', ''),
(96607, 96101, 96302, 96403, 'story', ''),
(96608, 96101, 96302, 96404, 'story', '')
ON DUPLICATE KEY UPDATE `type` = VALUES(`type`), `cards` = VALUES(`cards`);

-- ============================ 10. 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90125, '看板', 'zentao:kanban:query',  2, 16, 90001, 'kanban', 'ep:grid', 'zentao/kanban/index', 'ZentaoKanban', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90126, '看板查询', 'zentao:kanban:query',  3, 1, 90125, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90127, '看板创建', 'zentao:kanban:create', 3, 2, 90125, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90128, '看板修改', 'zentao:kanban:update', 3, 3, 90125, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90129, '看板删除', 'zentao:kanban:delete', 3, 4, 90125, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

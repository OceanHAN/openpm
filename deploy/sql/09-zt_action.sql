-- ---------------------------------------------------------------------------
-- 操作日志：zt_action + zt_history
--
-- 禅道的变更追溯由两张表配合完成：
--   zt_action   一次操作一行：谁、在什么时候、对哪个对象、做了什么
--   zt_history  一个字段一行：挂在某个 action 下，记录字段的旧值/新值/差异
--
-- 对应禅道的：
--   module/action/model.php  create()      写 zt_action
--   module/action/model.php  logHistory()  写 zt_history
--   module/common/model.php  createChanges() 生成字段级差异
--
-- 注意：createChanges 有一份「黑名单」，lastEditedDate / assignedDate / uid 这类
--       每次编辑都会变的字段不记录，否则日志会被噪声淹没。Java 侧同样实现。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_action` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '日志编号',
  `objectType`  varchar(30)  NOT NULL DEFAULT '' COMMENT '对象类型，如 story',
  `objectID`    bigint       NOT NULL DEFAULT 0 COMMENT '对象编号',
  `product`     varchar(255) NOT NULL DEFAULT '' COMMENT '所属产品，多个逗号分隔（禅道原为 text）',
  `project`     bigint       NOT NULL DEFAULT 0 COMMENT '所属项目',
  `execution`   bigint       NOT NULL DEFAULT 0 COMMENT '所属执行',
  `actor`       varchar(64)  NOT NULL DEFAULT '' COMMENT '操作人账号',
  `action`      varchar(80)  NOT NULL DEFAULT '' COMMENT '动作，如 created/edited/changed/closed',
  `date`        datetime     NULL COMMENT '操作时间',
  `comment`     text         NULL COMMENT '备注',
  `files`       text         NULL COMMENT '附件',
  `extra`       text         NULL COMMENT '额外信息',
  `read`        tinyint      NOT NULL DEFAULT 0 COMMENT '是否已读（read 是 MySQL 保留字，需反引号）',
  `efforted`    tinyint      NOT NULL DEFAULT 0 COMMENT '是否已登记工时',
  `vision`      varchar(10)  NOT NULL DEFAULT 'rnd' COMMENT '视图',
  -- yudao BaseDO 约定字段
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  -- 时间线查询是最高频的访问路径：按对象取日志、按时间倒序
  KEY `idx_object` (`objectType`, `objectID`, `id`),
  KEY `idx_actor` (`actor`),
  KEY `idx_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

CREATE TABLE IF NOT EXISTS `zt_history` (
  `id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '变更明细编号',
  `action`     bigint      NOT NULL DEFAULT 0 COMMENT '所属操作日志编号',
  `field`      varchar(64) NOT NULL DEFAULT '' COMMENT '字段名',
  `old`        longtext    NULL COMMENT '旧值（禅道原字段名）',
  `oldValue`   text        NULL COMMENT '旧值展示文本',
  `new`        longtext    NULL COMMENT '新值（禅道原字段名）',
  `newValue`   text        NULL COMMENT '新值展示文本',
  `diff`       mediumtext  NULL COMMENT '长文本差异，行为类似统一 diff 的纯文本',
  -- yudao BaseDO 约定字段
  `create_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`    varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`    varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`    bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志字段变更明细表';

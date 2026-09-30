-- ---------------------------------------------------------------------------
-- 需求表 zt_story —— 垂直切片
--
-- 设计原则：表名与核心字段名对齐禅道 zt_story，便于迁移期直接导数据、
--           并与 PHP 实现左右对照。追加字段遵循 yudao 的 BaseDO 约定
--           （create_time / update_time / creator / updater / deleted）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_story` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '需求编号',
  -- 归属
  `product`         bigint       NOT NULL DEFAULT 0 COMMENT '所属产品',
  `module`          bigint       NOT NULL DEFAULT 0 COMMENT '所属模块',
  `plan`            varchar(255) NOT NULL DEFAULT '' COMMENT '所属计划，多个逗号分隔',
  `branch`          bigint       NOT NULL DEFAULT 0 COMMENT '所属分支',
  -- 内容
  `title`           varchar(255) NOT NULL DEFAULT '' COMMENT '需求标题',
  `keywords`        varchar(255) NOT NULL DEFAULT '' COMMENT '关键词',
  -- 注意：spec / verify 不在本表。禅道把需求描述与验收标准放在 zt_storyspec，
  --       按 (story, version) 存多份快照以支持需求变更历史，见 05-zt_storyspec.sql。
  `type`            varchar(20)  NOT NULL DEFAULT 'story' COMMENT '需求类型',
  `category`        varchar(30)  NOT NULL DEFAULT 'feature' COMMENT '需求分类',
  `pri`             tinyint      NOT NULL DEFAULT 3 COMMENT '优先级 1~4',
  `estimate`        decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '预计工时',
  -- 状态机
  `status`          varchar(16)  NOT NULL DEFAULT 'draft' COMMENT '状态',
  `stage`           varchar(16)  NOT NULL DEFAULT 'wait' COMMENT '研发阶段',
  `version`         smallint     NOT NULL DEFAULT 1 COMMENT '版本号，每次变更 +1',
  -- 来源
  `source`          varchar(20)  NOT NULL DEFAULT '' COMMENT '需求来源',
  `sourceNote`      varchar(255) NOT NULL DEFAULT '' COMMENT '来源备注',
  `fromBug`         bigint       NOT NULL DEFAULT 0 COMMENT '来源 Bug 编号',
  -- 生命周期
  `openedBy`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人',
  `openedDate`      datetime     NULL COMMENT '创建时间',
  `assignedTo`      varchar(64)  NOT NULL DEFAULT '' COMMENT '指派给',
  `assignedDate`    datetime     NULL COMMENT '指派时间',
  `closedBy`        varchar(64)  NOT NULL DEFAULT '' COMMENT '关闭人',
  `closedDate`      datetime     NULL COMMENT '关闭时间',
  `closedReason`    varchar(30)  NOT NULL DEFAULT '' COMMENT '关闭原因',
  `duplicateStory`  bigint       NOT NULL DEFAULT 0 COMMENT '重复需求编号',
  `activatedDate`   datetime     NULL COMMENT '激活时间',
  `lastEditedBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人',
  `lastEditedDate`  datetime     NULL COMMENT '最后修改时间',
  `reviewedBy`      varchar(255) NOT NULL DEFAULT '' COMMENT '已评审人，逗号分隔',
  `reviewedDate`    datetime     NULL COMMENT '最后评审时间',
  -- yudao BaseDO 约定字段
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`         varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`         varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product_status` (`product`, `status`),
  KEY `idx_assignedTo` (`assignedTo`),
  KEY `idx_openedDate` (`openedDate`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求表';

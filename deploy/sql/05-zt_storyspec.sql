-- ---------------------------------------------------------------------------
-- 需求版本快照表 zt_storyspec —— 垂直切片（第二阶段）
--
-- 禅道的需求版本机制：
--   zt_story      = 头部，保存当前状态与当前版本号
--   zt_storyspec  = 追加式快照，按 (story, version) 保存每一版内容
--
-- 两条写路径（对应禅道 model.php 的 update / change）：
--   普通编辑 update()  -> UPDATE zt_storyspec ... WHERE story=? AND version=当前版（原地改）
--   正式变更 change()  -> INSERT 一行新版本（version+1，追加）
--
-- 读取时（对应禅道 getById）：
--   version=0 取当前版：先读 zt_story 拿到 version，再按 (story, version) 取快照
--   列表页 join 用 t1.version = t2.version 只取当前版
--
-- 注意：zt_story 表本身没有 spec / verify 列（禅道原设计如此），
--       这两个字段只存在于本表。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_storyspec` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `story`       bigint       NOT NULL DEFAULT 0 COMMENT '需求编号',
  `version`     smallint     NOT NULL DEFAULT 1 COMMENT '版本号',
  `title`       varchar(255) NOT NULL DEFAULT '' COMMENT '该版本的需求标题',
  `spec`        mediumtext   NULL COMMENT '该版本的需求描述',
  `verify`      mediumtext   NULL COMMENT '该版本的验收标准',
  `files`       text         NULL COMMENT '附件编号，逗号分隔',
  `docs`        text         NULL COMMENT '关联文档',
  `docVersions` text         NULL COMMENT '关联文档版本',
  -- yudao BaseDO 约定字段
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  -- 一个需求的同一版本只能有一条快照，这个唯一键是版本机制的正确性基石
  UNIQUE KEY `uk_story_version` (`story`, `version`),
  KEY `idx_story` (`story`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求版本快照表';

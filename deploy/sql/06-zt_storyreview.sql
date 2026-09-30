-- ---------------------------------------------------------------------------
-- 需求评审表 zt_storyreview —— 垂直切片（第三阶段）
--
-- 核心设计：评审是「绑定到某个版本」的。
--   同一需求的不同版本可以有完全不同的评审人与结果；
--   只有「当前版本的评审人全部提交」后，才会触发状态流转。
--
-- 禅道的聚合规则（module/story/model.php getReviewResult）：
--   配置 reviewRules，默认 allpass —— 全部评审人都投 pass 才算通过
--   若未通过，则按多数派判定 clarify / revert / reject；
--   没有多数派时，只要有任意一人投了 clarify/revert/reject，就取该结果。
--
-- 状态映射（setStatusByReviewResult）：
--   pass    -> active
--   clarify -> draft（若曾变更过则 changing），并清空 reviewedBy
--   revert  -> active，且 version-1，同时删除当前版本的快照与评审记录（真正的回滚）
--   reject  -> closed，指派给 closed
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_storyreview` (
  `id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `story`      bigint      NOT NULL DEFAULT 0 COMMENT '需求编号',
  `version`    smallint    NOT NULL DEFAULT 1 COMMENT '被评审的版本号',
  `reviewer`   varchar(64) NOT NULL DEFAULT '' COMMENT '评审人账号',
  `result`     varchar(30) NOT NULL DEFAULT '' COMMENT '评审结果：pass/clarify/revert/reject，空表示尚未表决',
  `reviewDate` datetime    NULL COMMENT '评审时间',
  -- yudao BaseDO 约定字段
  `create_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`    varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`    varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`    bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  -- 同一需求同一版本的同一评审人只能有一条记录，表决就是更新这一行
  UNIQUE KEY `uk_story_version_reviewer` (`story`, `version`, `reviewer`),
  KEY `idx_story_version` (`story`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求评审表';

-- ---------------------------------------------------------------------------
-- 父子需求（需求分解）—— 给 zt_story 补六列
--
-- 【禅道语义】需求可以「分解」成若干子需求，父需求只做汇总、不直接开发：
--   parent         父需求
--   parentVersion  分解时**父需求的版本（冻结）** —— 父需求后来正式变更，
--                  子需求要提示「父需求已变更」，和 zt_projectstory.version 是同一思路
--   root           顶层祖先（便于一次捞出整棵树，不用递归）
--   path           树路径，逗号包起来且**包含自己**：一级 ,5,、二级 ,5,6,
--   grade          层级，从 1 开始
--   isParent       是否已分解（有子需求）—— 列表上要显示「父」标记
--
-- 【三种状态级联（禅道 updateParentStatus）】
--   1. 父需求有子需求 → isParent=1，否则 0
--   2. 父需求的 estimate = **所有子需求工作量之和**（父自己不填工时）
--   3. 子需求全部关闭 → 父需求自动关闭；
--      父需求已关闭但还有子需求没关 → 父需求自动激活
--   这三条都是「子动父跟着动」，所以任何改变子需求状态/工时的入口都要回头调一次。
--
-- 【为什么用存储过程】MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
--   而脚本要能重复执行；同时老数据要回填 root/path/grade（否则树是断的）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

DROP PROCEDURE IF EXISTS `zt_add_story_parent_columns`;
DELIMITER $$
CREATE PROCEDURE `zt_add_story_parent_columns`()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_story' AND column_name = 'parent') THEN
    ALTER TABLE `zt_story` ADD COLUMN `parent` bigint NOT NULL DEFAULT 0 COMMENT '父需求';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_story' AND column_name = 'parentVersion') THEN
    ALTER TABLE `zt_story` ADD COLUMN `parentVersion` smallint NOT NULL DEFAULT 1 COMMENT '分解时父需求的版本（冻结，驼峰列名）';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_story' AND column_name = 'root') THEN
    ALTER TABLE `zt_story` ADD COLUMN `root` bigint NOT NULL DEFAULT 0 COMMENT '顶层祖先（root 是 MySQL 8 保留字）';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_story' AND column_name = 'path') THEN
    ALTER TABLE `zt_story` ADD COLUMN `path` varchar(255) NOT NULL DEFAULT '' COMMENT '树路径，逗号包起来且包含自己';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_story' AND column_name = 'grade') THEN
    ALTER TABLE `zt_story` ADD COLUMN `grade` tinyint NOT NULL DEFAULT 1 COMMENT '层级，从 1 开始';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_story' AND column_name = 'isParent') THEN
    ALTER TABLE `zt_story` ADD COLUMN `isParent` tinyint NOT NULL DEFAULT 0 COMMENT '是否已分解（有子需求，驼峰列名）';
    ALTER TABLE `zt_story` ADD INDEX `idx_parent` (`parent`);
    ALTER TABLE `zt_story` ADD INDEX `idx_root` (`root`);
  END IF;
END$$
DELIMITER ;
CALL `zt_add_story_parent_columns`();
DROP PROCEDURE `zt_add_story_parent_columns`;

-- 老数据回填：没有父的一级需求，root=自己、path=,自己,、grade=1
UPDATE `zt_story` SET `root` = `id`, `path` = CONCAT(',', `id`, ','), `grade` = 1
 WHERE `parent` = 0 AND (`root` = 0 OR `path` = '');

-- 演示数据：需求 4「支持需求批量导入（含校验）」分解成两条子需求（5、6 是 draft，这里改用 9/11 不合适，
-- 所以直接造两条新的子需求），便于演示「父需求聚合」与「全部子需求关闭后父需求自动关闭」
INSERT INTO `zt_story` (`id`, `product`, `module`, `plan`, `branch`, `title`, `type`, `category`, `pri`,
                        `estimate`, `status`, `stage`, `version`, `source`, `parent`, `parentVersion`,
                        `root`, `path`, `grade`, `isParent`, `openedBy`, `openedDate`,
                        `lastEditedBy`, `lastEditedDate`, `creator`, `updater`)
VALUES
(92201, 1, 0, '', 0, '批量导入-解析 Excel', 'story', 'feature', 2, 4, 'active', 'wait', 1, '',
    4, 2, 4, ',4,92201,', 2, 0, 'admin', NOW(), 'admin', NOW(), 'admin', 'admin'),
(92202, 1, 0, '', 0, '批量导入-校验与提示', 'story', 'feature', 2, 6, 'active', 'wait', 1, '',
    4, 2, 4, ',4,92202,', 2, 0, 'admin', NOW(), 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `parent` = VALUES(`parent`), `parentVersion` = VALUES(`parentVersion`),
    `root` = VALUES(`root`), `path` = VALUES(`path`), `grade` = VALUES(`grade`), `deleted` = b'0';

-- 需求 4 变成「父需求」：isParent=1、estimate = 4+6 = 10（父自己不估工时）
UPDATE `zt_story` SET `isParent` = 1, `estimate` = 10 WHERE `id` = 4;

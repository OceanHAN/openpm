-- ---------------------------------------------------------------------------
-- 需求转任务（任务分解）—— 给 zt_task 补两列
--
-- 【禅道语义】任务可以挂在需求下（zt_task.story），并且记下**建任务时需求的版本**：
--   storyVersion  冻结值：需求后来正式变更，任务要提示「需求已变更」，
--                 而不是悄悄跟着变 —— 和 zt_projectstory.version、zt_case.storyVersion、
--                 zt_story.parentVersion 是同一套思路（本项目里这是第 4 处「冻结版本」）
--   fromBug       任务由缺陷转来时记来源缺陷（禅道 transfer 流程），本轮只把列建上
--
-- 【为什么冻结很重要】需求变更后，已经排好的任务不应该被无声改写：
--   团队可能已经按老需求写完了。所以任务记住「我当初是按哪一版需求做的」，
--   需求升版后由人来判断要不要同步。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

DROP PROCEDURE IF EXISTS `zt_add_task_story_columns`;
DELIMITER $$
CREATE PROCEDURE `zt_add_task_story_columns`()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_task' AND column_name = 'storyVersion') THEN
    ALTER TABLE `zt_task` ADD COLUMN `storyVersion` smallint NOT NULL DEFAULT 1 COMMENT '建任务时需求的版本（冻结，驼峰列名）';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_task' AND column_name = 'fromBug') THEN
    ALTER TABLE `zt_task` ADD COLUMN `fromBug` bigint NOT NULL DEFAULT 0 COMMENT '来源缺陷（驼峰列名，0=非缺陷转来）';
  END IF;
END$$
DELIMITER ;
CALL `zt_add_task_story_columns`();
DROP PROCEDURE `zt_add_task_story_columns`;

-- 演示数据：给演示需求 1 下已有的一条任务补上需求关联
UPDATE `zt_task` SET `story` = 1, `storyVersion` = 1 WHERE `story` = 0 AND `deleted` = 0 LIMIT 1;

-- 老数据回填：已挂需求的任务，把 storyVersion 对齐到需求当前版本
-- （否则迁移完一上来所有任务都在提示「需求已变更」——那只是迁移的假象，不是真的变更）
-- 注意这条必须放在上面那句**之后**，否则刚挂上的演示任务会被漏掉
UPDATE `zt_task` t JOIN `zt_story` s ON s.id = t.story
   SET t.`storyVersion` = s.`version`
 WHERE t.`story` > 0 AND t.`storyVersion` = 1 AND s.`version` > 1;

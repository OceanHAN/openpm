-- ---------------------------------------------------------------------------
-- 一次性迁移：把 zt_story 里内联的 spec / verify 搬到 zt_storyspec
--
-- 背景：第一阶段为控制范围把 spec/verify 内联在主表，但禅道原设计里
--       这两个字段只存在于 zt_storyspec。本脚本把数据补齐并删掉多余列，
--       让表结构回到与禅道一致的状态。
--
-- 幂等性：本脚本只用于一次性迁移，重复执行会因列已删除而报错，属预期行为。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- 1. 把主表当前的 spec/verify 作为一个版本快照补进去
INSERT INTO `zt_storyspec` (`story`, `version`, `title`, `spec`, `verify`, `creator`, `updater`)
SELECT `id`, `version`, `title`, `spec`, `verify`, `creator`, `updater`
FROM `zt_story`
WHERE `deleted` = 0
ON DUPLICATE KEY UPDATE
    `title`  = VALUES(`title`),
    `spec`   = VALUES(`spec`),
    `verify` = VALUES(`verify`);

-- 2. 删掉主表里禅道本就没有的两列
ALTER TABLE `zt_story`
    DROP COLUMN `spec`,
    DROP COLUMN `verify`;

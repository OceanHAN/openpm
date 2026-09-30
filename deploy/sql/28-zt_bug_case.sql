-- ---------------------------------------------------------------------------
-- 缺陷 ↔ 用例的关联（给 zt_bug 补三列）
--
-- 【为什么现在才补】
--   「用例执行失败 → 建 Bug」是测试链的最后一环。禅道的 zt_bug 上有三列专门记录
--   「这个 Bug 是从哪条用例跑出来的」：
--     case         来源用例
--     caseVersion  来源用例的**版本**（和 storyVersion 一样是冻结值：
--                  用例后来改了步骤，Bug 仍然指向当初跑的那一版）
--     testtask     来源测试单
--   前面做 testcase / testtask 时用不到它们，所以建表时省掉了；现在补上。
--
-- 【为什么用存储过程】
--   MySQL 8.0 不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`，
--   而这个脚本要能被重复执行（其它 SQL 都是幂等的），所以查一下 information_schema。
--   `case` 是 MySQL 保留字，必须加反引号；`caseVersion` 是驼峰列名，
--   对应的 DO 字段要写 @TableField("caseVersion")，否则 MyBatis-Plus 会拼成 case_version。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

DROP PROCEDURE IF EXISTS `zt_add_bug_case_columns`;
DELIMITER $$
CREATE PROCEDURE `zt_add_bug_case_columns`()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_bug' AND column_name = 'case') THEN
    ALTER TABLE `zt_bug` ADD COLUMN `case` bigint NOT NULL DEFAULT 0 COMMENT '来源用例（`case` 是保留字）';
    ALTER TABLE `zt_bug` ADD INDEX `idx_case` (`case`);
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_bug' AND column_name = 'caseVersion') THEN
    ALTER TABLE `zt_bug` ADD COLUMN `caseVersion` smallint NOT NULL DEFAULT 1 COMMENT '来源用例的版本（冻结，驼峰列名）';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'zt_bug' AND column_name = 'testtask') THEN
    ALTER TABLE `zt_bug` ADD COLUMN `testtask` bigint NOT NULL DEFAULT 0 COMMENT '来源测试单';
    ALTER TABLE `zt_bug` ADD INDEX `idx_testtask` (`testtask`);
  END IF;
END$$
DELIMITER ;
CALL `zt_add_bug_case_columns`();
DROP PROCEDURE `zt_add_bug_case_columns`;

-- 演示数据：给已有的那条缺陷挂上「来源用例」，便于列表/按钮上有东西可看
UPDATE `zt_bug` SET `case` = 93102, `caseVersion` = 1, `testtask` = 94101 WHERE `id` = 1 AND `case` = 0;

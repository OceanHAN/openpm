-- ---------------------------------------------------------------------------
-- 一次性迁移：为 zt_story 补齐评审相关字段
--
-- 禅道 zt_story 里有 reviewedBy / reviewedDate 两列：
--   reviewedBy   已提交评审结果的人，逗号分隔（如 "zhangsan,lisi"）
--   reviewedDate 最后一次评审时间
-- 第一阶段切片没建这两列，做评审时需要补上。
--
-- 幂等性：本脚本只用于一次性迁移，重复执行会因列已存在而报错，属预期行为。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

ALTER TABLE `zt_story`
    ADD COLUMN `reviewedBy`   varchar(255) NOT NULL DEFAULT '' COMMENT '已评审人，逗号分隔',
    ADD COLUMN `reviewedDate` datetime     NULL COMMENT '最后评审时间';

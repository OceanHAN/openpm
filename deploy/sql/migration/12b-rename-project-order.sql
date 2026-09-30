-- ---------------------------------------------------------------------------
-- 一次性迁移：zt_project.order 改名为 orderNum
--
-- 原因：`order` 是 MySQL 保留字，DO 里用 @TableField("`order`") 能生成合法 SQL，
-- 但 MyBatis-Plus 的分页/数据权限拦截器会用 JSqlParser 重新解析 SQL，
-- 而 JSqlParser 在处理 ORDER BY `order` 时报 ParseException: unexpected token ","，
-- 导致所有涉及 zt_project 的查询直接 500。
--
-- 对比：zt_task 的 `desc` / `left` 同样加了反引号却能正常工作，
-- 说明问题只出在 `order` 这个在 ORDER BY 语境下有特殊含义的关键字上。
--
-- 这是本项目对禅道**唯一被迫的列名偏离**。代价是迁移时需要一个字段映射：
--   禅道 zt_project.order  ->  本项目 zt_project.orderNum
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

ALTER TABLE `zt_project` CHANGE COLUMN `order` `orderNum` int NOT NULL DEFAULT 0 COMMENT '排序（原禅道列名 order，因 JSqlParser 无法解析而改名）';

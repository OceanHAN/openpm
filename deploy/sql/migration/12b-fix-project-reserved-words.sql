-- ---------------------------------------------------------------------------
-- 一次性迁移：修正 zt_project 的三个 JSqlParser 保留字列
--
-- 排查过程（用 JSqlParser 4.5 直接跑 SQL 二分定位）：
--   单列测试发现 `output` 会让解析器直接失败；
--   `begin` / `end` 单独测试也失败（它们是 SQL 块关键字）；
--   而 `desc` / `left` / `order` / `PO` / `type` / `path` 加反引号后都正常。
--
-- 因此：
--   1. orderNum 改回禅道原列名 `order`（反引号可正常解析，不必偏离）
--   2. 给 begin / end / output 加反引号（Java 侧 @TableField("`xxx`")）
--
-- 结论：JSqlParser 的保留字集合比 MySQL 更宽（含 T-SQL 的 OUTPUT），
--       禅道的列名里踩到 3 个。这是排查时最费时的一类问题——
--       同一张表在 MySQL 里执行完全合法，但会被 MyBatis-Plus 的拦截器拒绝。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

ALTER TABLE `zt_project` CHANGE COLUMN `orderNum` `order` int NOT NULL DEFAULT 0 COMMENT '排序';

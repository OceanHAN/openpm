-- ---------------------------------------------------------------------------
-- 节假日（holiday）：假期 + 补班
--
-- 禅道语义（module/holiday，688 行、6 个 action）：
--   1. 一张表两种记录：
--        type='holiday'  假期 —— 这段日期**不算工作日**（即使是周一到周五）
--        type='working'  补班 —— 这段日期**算工作日**（即使是周六周日，即调休）
--   2. 「实际工作日」的判定优先级（禅道 getActualWorkingDays）：
--        补班日 → 算    >    假期 → 不算    >    周末（weekend=2 指周六周日）→ 不算    >    其它 → 算
--   3. 它被这些地方用到：燃尽图/甘特图的工作日口径、项目与执行的工期计算、日历
--
-- 本实现把第 2 条抽成 HolidayService.getActualWorkingDays()，并**把燃尽图的工作日口径从
-- 「只跳周末」升级成「跳周末 + 跳假期 + 补班日算工作日」**（README 3.42）—— 这是这个模块
-- 真正的价值：不是多了一张表，而是让已有功能的口径变正确。
--
-- 一个照抄的怪癖：禅道的 getActualWorkingDays(begin, end) 是**左闭右开**（循环条件是
-- `$currentDay < $end`），只有 begin == end 时才返回那一天。本实现保持一致，
-- 免得「燃尽图的点数」和禅道对不上。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_holiday` (
  `id`    bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`  varchar(30) NOT NULL DEFAULT '' COMMENT '名称，例如「国庆节」「春节调休」',
  `type`  varchar(10) NOT NULL DEFAULT 'holiday' COMMENT '类型：holiday 假期 / working 补班',
  `desc`  text DEFAULT NULL COMMENT '描述',
  `year`  char(4) NOT NULL DEFAULT '' COMMENT '年份（按年筛选用）',
  `begin` date DEFAULT NULL COMMENT '开始日期（含）',
  `end`   date DEFAULT NULL COMMENT '结束日期（含）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creator`     varchar(64) NOT NULL DEFAULT '',
  `updater`     varchar(64) NOT NULL DEFAULT '',
  `deleted`     tinyint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_year` (`year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节假日与补班';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90165, '节假日',   'zentao:holiday:query',  2, 23, 90001, 'holiday', 'ep:calendar', 'zentao/holiday/index', 'ZentaoHoliday', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90166, '节假日查询', 'zentao:holiday:query',  3, 1, 90165, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90167, '节假日创建', 'zentao:holiday:create', 3, 2, 90165, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90168, '节假日修改', 'zentao:holiday:update', 3, 3, 90165, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90169, '节假日删除', 'zentao:holiday:delete', 3, 4, 90165, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

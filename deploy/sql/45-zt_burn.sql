-- ---------------------------------------------------------------------------
-- 执行燃尽图（zt_burn）
--
-- 禅道 module/execution 的 burn（燃尽图）与 cfd（累积流图）都靠这张表存**每天的快照**：
-- 「今天还剩多少工时 / 原计划多少工时 / 已消耗多少 / 需求规模多少」。
-- 燃尽图不是实时算出来的 —— 每天（computeBurn）把当天任务汇总写一行，
-- 图上看到的是一条条历史快照连起来的折线，这样即使任务后来被改、被删，历史曲线也不会跟着变。
--
-- 为什么单独一张表而不是现算：现算只能得到「今天」一个点，画不出趋势；
-- 而且任务完成后会被关闭、estimate 会被改写，历史值必须落库才留得住。
--
-- 唯一键 (execution, date, task)：task 是禅道为「多任务燃尽图」留的维度（按任务分别记），
-- 本实现只用执行维度（task 恒为 0）。
--
-- 本表**没有 deleted 列**（禅道原样）：快照是只增不改的事实数据，computeBurn 用 REPLACE 覆盖当天那一行。
-- 另外要在 yudao 的 `yudao.tenant.ignore-tables` 里登记本表（坑位 #2/#3）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_burn` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `execution`  bigint unsigned NOT NULL DEFAULT 0 COMMENT '执行编号（zt_project 里 type=sprint/stage 的行）',
  `product`    bigint unsigned NOT NULL DEFAULT 0 COMMENT '产品编号（禅道保留字段，本实现恒为 0）',
  `task`       bigint unsigned NOT NULL DEFAULT 0 COMMENT '任务编号（禅道多任务燃尽图用，本实现恒为 0）',
  `date`       date DEFAULT NULL COMMENT '快照日期',
  `estimate`   decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '原计划工时（扣掉已完成任务的预计）',
  `left`       decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '剩余工时（扣掉已关闭任务的剩余）',
  `consumed`   decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '已消耗工时',
  `storyPoint` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '需求规模合计（未关闭、未完成的需求的 estimate）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `execution_task` (`execution`, `date`, `task`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行燃尽图快照';

-- 燃尽图按钮的权限（前端 v-hasPermi 判定，没有菜单行按钮会被隐藏，坑位 #43）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90156, '燃尽图', 'zentao:execution:burn', 3, 5, 90027, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`);

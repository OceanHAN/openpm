-- ---------------------------------------------------------------------------
-- 产品计划（productplan）模块
--
-- 【禅道语义】zt_productplan 是**产品维度**的迭代计划：需求通过 zt_story.plan 挂到计划上，
-- 发布（release）再引用计划。它是「产品规划 → 排期 → 发布」这条链的中间环节。
--
-- 【三个必须注意的字段设计】
--  1. branch 是 varchar(255) 而不是 bigint —— 禅道允许**一个计划覆盖多个分支/平台**，
--     存的是逗号分隔的分支 id 列表（'0' 表示主干）。需求/缺陷上的 branch 是单值，
--     到了计划/发布/构建就变成多值，这是迁移时最容易照着前面的表抄错的地方。
--  2. 「待定」计划不是一个状态，而是**日期哨兵值**：$config->productplan->future = '2030-01-01'。
--     没有设定日期的计划，begin/end 都写 2030-01-01。展示时再翻译成「待定」。
--  3. parent 有三态：0 = 独立计划，> 0 = 子计划，**-1 = 该计划有子计划**。
--     禅道靠 -1 这个标记来拒绝「删除父计划」（$lang->productplan->cannotDeleteParent）。
--     本实现采用**即时标记**：建子计划时就把父计划置为 -1（禅道是延迟到状态流转时才置），
--     这样「父计划不能删」这条规则立刻生效。
--
-- 【状态机（对齐 $lang->productplan->statusList）】
--   wait 未开始 → doing 进行中 → done 已完成
--   任意非关闭态 → closed 已关闭（closedReason: done 已完成 / cancel 已取消）
--   closed → doing（activate 激活，注意禅道激活后是「进行中」而不是「未开始」）
--   父计划的状态由其子计划**聚合推导**（见 PlanServiceImpl#updateParentStatus）。
--
-- 【order 列是 text】禅道用它保存「计划内需求的排序」，不是排序数字；
--   计划列表本身按 begin 排序。为了不和其它表的 int order 混淆，
--   DO 里字段名叫 planOrder，列名仍然对齐禅道。
--
-- 关键字：desc / order 需要反引号（已用 JSqlParser 4.5 验证）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_productplan` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '计划编号',
  `product`       bigint       NOT NULL DEFAULT 0 COMMENT '所属产品',
  `branch`        varchar(255) NOT NULL DEFAULT '0' COMMENT '分支/平台，多个逗号分隔；0 为主干',
  `parent`        bigint       NOT NULL DEFAULT 0 COMMENT '0 独立 / >0 父计划编号 / -1 有子计划',
  `title`         varchar(90)  NOT NULL DEFAULT '' COMMENT '计划名称',
  `status`        varchar(10)  NOT NULL DEFAULT 'wait' COMMENT 'wait/doing/done/closed',
  `desc`          mediumtext   NULL COMMENT '描述',
  `begin`         date         NULL COMMENT '开始日期；待定为 2030-01-01',
  `end`           date         NULL COMMENT '结束日期；待定为 2030-01-01',
  `finishedDate`  datetime     NULL COMMENT '完成时间',
  `closedDate`    datetime     NULL COMMENT '关闭时间',
  `order`         text         NULL COMMENT '计划内需求的排序数据（禅道原样保留）',
  `closedReason`  varchar(20)  NOT NULL DEFAULT '' COMMENT '关闭原因：done 已完成 / cancel 已取消',
  `createdBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人',
  `createdDate`   datetime     NULL COMMENT '创建时间',
  -- yudao BaseDO 约定字段
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`       varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`       varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product_status` (`product`, `status`),
  KEY `idx_parent` (`parent`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品计划表';

-- ============================ 演示数据 ============================
-- 产品 1（普通产品）：两个计划，含一个待定计划
-- 产品 2（多平台）：计划覆盖两个平台，branch = '1,2'
-- 产品 3（多分支）：计划覆盖 v1.0 分支
INSERT INTO `zt_productplan` (`id`, `product`, `branch`, `parent`, `title`, `status`, `desc`, `begin`, `end`, `createdBy`, `createdDate`) VALUES
(1, 1, '0', 0, 'V1.0 迭代计划', 'doing', '第一个可交付版本', '2026-01-05', '2026-02-28', 'admin', NOW()),
(2, 1, '0', 0, 'V2.0 迭代计划', 'wait',  '待排期',            '2030-01-01', '2030-01-01', 'admin', NOW()),
(3, 2, '1,2', 0, '平台基线计划', 'wait', '两个平台同步发布', '2026-03-01', '2026-04-30', 'admin', NOW()),
(4, 3, '3', 0, '门户 1.0 计划', 'done', '已经在 1.x 分支发布', '2025-09-01', '2025-12-31', 'admin', NOW())
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `status` = VALUES(`status`), `branch` = VALUES(`branch`);

-- 关联演示：把产品 1 的部分需求挂到 V1.0 计划上（只挂还没挂计划的）
UPDATE `zt_story` SET `plan` = '1' WHERE `product` = 1 AND (`plan` = '' OR `plan` IS NULL) AND `deleted` = 0 LIMIT 3;

-- ============================ 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90042, '计划管理', 'zentao:plan:query',  2, 8, 90001, 'plan', 'ep:calendar', 'zentao/plan/index', 'ZentaoPlan', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90043, '计划查询', 'zentao:plan:query',  3, 1, 90042, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90044, '计划创建', 'zentao:plan:create', 3, 2, 90042, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90045, '计划修改', 'zentao:plan:update', 3, 3, 90042, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90046, '计划删除', 'zentao:plan:delete', 3, 4, 90042, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

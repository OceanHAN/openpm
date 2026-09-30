-- ---------------------------------------------------------------------------
-- 阶段（stage）模块 —— 瀑布流程
--
-- 【禅道语义：zt_stage 是「阶段模板」，不是项目里的阶段】
--   zt_stage 描述的是**一套瀑布流程的阶段定义**：
--     workflowGroup + name + percent + type + projectType + order
--   例如 workflowGroup=1（waterfall 流程）下有：需求 20% / 设计 20% / 开发 30% / 测试 20% / 发布 10%。
--   它是"流程模板"，多个瀑布项目共用同一套。
--
--   而一个项目里**实际的阶段**是 zt_project 里 type='stage' 的记录：
--   项目创建时按模板生成，各自有自己的日期、状态、工时。
--   也就是说：模板在 zt_stage，实例在 zt_project（和执行共用表，见 README 3.7）。
--   判断一个项目用的是哪套模板，看 zt_project.workflowGroup。
--
-- 【模板的校验规则（stage::create / update）】
--   - name 在同一个 workflowGroup 内唯一
--   - percent 必须是数字，且**同一模板下累计不超过 100%**
--     （新增：total + percent ≤ 100；修改：total + new - old ≤ 100）
--   - order 自动取同模板最大值 +1
--
-- 【本次迁移新增的 zt_project 列】
--   percent      阶段的工作量占比（从模板复制过来，可按项目调整）
--   workflowGroup 项目使用的流程模板编号（瀑布项目 >0；scrum 项目为 0）
--   attribute    阶段属性（禅道原字段，用于区分里程碑阶段等）
--
-- 关键字：percent / type / order 中只有 order 需要反引号。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 补齐 zt_project 的阶段列 ============================
DROP PROCEDURE IF EXISTS `zt_stage_add_project_columns`;
DELIMITER $$
CREATE PROCEDURE `zt_stage_add_project_columns`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'ruoyi-vue-pro' AND TABLE_NAME = 'zt_project' AND COLUMN_NAME = 'percent') THEN
    ALTER TABLE `zt_project` ADD COLUMN `percent` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '阶段工作量占比' AFTER `progress`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'ruoyi-vue-pro' AND TABLE_NAME = 'zt_project' AND COLUMN_NAME = 'workflowGroup') THEN
    ALTER TABLE `zt_project` ADD COLUMN `workflowGroup` bigint NOT NULL DEFAULT 0 COMMENT '使用的流程模板编号' AFTER `percent`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'ruoyi-vue-pro' AND TABLE_NAME = 'zt_project' AND COLUMN_NAME = 'attribute') THEN
    ALTER TABLE `zt_project` ADD COLUMN `attribute` varchar(30) NOT NULL DEFAULT '' COMMENT '阶段属性（禅道原字段）' AFTER `workflowGroup`;
  END IF;
END$$
DELIMITER ;
CALL `zt_stage_add_project_columns`();
DROP PROCEDURE `zt_stage_add_project_columns`;

-- ============================ 2. 阶段模板表 ============================
CREATE TABLE IF NOT EXISTS `zt_stage` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '阶段模板编号',
  `workflowGroup` bigint       NOT NULL DEFAULT 0 COMMENT '所属流程模板组（一个瀑布流程一套阶段）',
  `name`          varchar(255) NOT NULL DEFAULT '' COMMENT '阶段名称',
  `percent`       varchar(255) NOT NULL DEFAULT '' COMMENT '工作量占比（%），同组累计不超过 100',
  `type`          varchar(255) NOT NULL DEFAULT '' COMMENT '阶段类型：request/design/dev/qa/release/review/other',
  `projectType`   varchar(30)  NOT NULL DEFAULT '' COMMENT '适用的项目流程类型：waterfall/waterfallplus/ipd',
  `createdBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人',
  `createdDate`   datetime     NULL COMMENT '创建时间',
  `editedBy`      varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人',
  `editedDate`    datetime     NULL COMMENT '最后修改时间',
  `order`         int          NOT NULL DEFAULT 0 COMMENT '排序（order 是保留字）',
  -- yudao BaseDO 约定字段
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`       varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`       varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_group_order` (`workflowGroup`, `order`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='瀑布流程阶段模板表';

-- ============================ 3. 演示数据 ============================
-- 内置一套 waterfall 流程模板（workflowGroup=1），合计 100%
INSERT INTO `zt_stage` (`id`, `workflowGroup`, `name`, `percent`, `type`, `projectType`, `order`, `createdBy`, `createdDate`, `creator`, `updater`) VALUES
(1, 1, '需求',   '20', 'request', 'waterfall', 1, 'admin', NOW(), 'admin', 'admin'),
(2, 1, '设计',   '20', 'design',  'waterfall', 2, 'admin', NOW(), 'admin', 'admin'),
(3, 1, '开发',   '30', 'dev',     'waterfall', 3, 'admin', NOW(), 'admin', 'admin'),
(4, 1, '测试',   '20', 'qa',      'waterfall', 4, 'admin', NOW(), 'admin', 'admin'),
(5, 1, '发布',   '10', 'release', 'waterfall', 5, 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `percent` = VALUES(`percent`), `type` = VALUES(`type`),
    `projectType` = VALUES(`projectType`), `order` = VALUES(`order`), `deleted` = b'0';

-- 演示：把已关闭的项目 1 标成瀑布项目（用第 1 套模板），并生成它的阶段实例
UPDATE `zt_project` SET `model` = 'waterfall', `workflowGroup` = 1 WHERE `id` = 1;

-- 注意：id 用 92001+ 这种远离测试区间的高位段。
-- 之前用 90010+ 撞上了历史上测试执行留下的软删除行，ON DUPLICATE KEY UPDATE
-- 只会更新列出的字段，project/parent/status 仍是旧值，数据看起来"生成错了"。
INSERT INTO `zt_project` (`id`, `project`, `parent`, `name`, `type`, `model`, `status`, `pri`, `begin`, `end`,
                          `percent`, `workflowGroup`, `attribute`, `estimate`, `left`, `consumed`, `progress`,
                          `PM`, `team`, `teamCount`, `openedBy`, `openedDate`, `creator`, `updater`, `deleted`)
VALUES
(92001, 1, 0, '需求', 'stage', 'waterfall', 'closed', 3, '2026-01-05', '2026-01-20', 20.00, 1, '', 20, 0, 20, 100, 'admin', 'admin', 1, 'admin', NOW(), 'admin', 'admin', b'0'),
(92002, 1, 0, '设计', 'stage', 'waterfall', 'closed', 3, '2026-01-21', '2026-02-05', 20.00, 1, '', 20, 0, 20, 100, 'admin', 'admin', 1, 'admin', NOW(), 'admin', 'admin', b'0'),
(92003, 1, 0, '开发', 'stage', 'waterfall', 'doing',  3, '2026-02-06', '2026-03-10', 30.00, 1, '', 30, 20, 10, 33, 'admin', 'admin', 1, 'admin', NOW(), 'admin', 'admin', b'0'),
(92004, 1, 0, '测试', 'stage', 'waterfall', 'wait',   3, '2026-03-11', '2026-03-31', 20.00, 1, '', 20, 20, 0, 0, 'admin', 'admin', 1, 'admin', NOW(), 'admin', 'admin', b'0'),
(92005, 1, 0, '发布', 'stage', 'waterfall', 'wait',   3, '2026-04-01', '2026-04-10', 10.00, 1, '', 10, 10, 0, 0, 'admin', 'admin', 1, 'admin', NOW(), 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `type` = VALUES(`type`), `project` = VALUES(`project`),
    `parent` = VALUES(`parent`), `status` = VALUES(`status`), `percent` = VALUES(`percent`),
    `workflowGroup` = VALUES(`workflowGroup`), `model` = VALUES(`model`),
    `begin` = VALUES(`begin`), `end` = VALUES(`end`), `deleted` = b'0';

-- ============================ 4. 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90061, '阶段管理', 'zentao:stage:query',  2, 12, 90001, 'stage', 'ep:set-up', 'zentao/stage/index', 'ZentaoStage', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90062, '阶段查询', 'zentao:stage:query',  3, 1, 90061, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90063, '阶段创建', 'zentao:stage:create', 3, 2, 90061, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90064, '阶段修改', 'zentao:stage:update', 3, 3, 90061, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90065, '阶段删除', 'zentao:stage:delete', 3, 4, 90061, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

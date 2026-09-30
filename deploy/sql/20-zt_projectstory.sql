-- ---------------------------------------------------------------------------
-- 项目/执行的需求范围（projectstory + projectproduct）
--
-- 【禅道语义】这两张表回答一个问题：**这个项目/执行要做哪些产品的哪些需求？**
--   - zt_projectproduct：项目关联了哪些产品（可指定分支与计划），是需求候选范围的来源。
--     `plan` 是逗号列表（项目可以只吃某个计划下的需求），`roadmap` 同理。
--   - zt_projectstory：项目/执行具体关联了哪些需求。
--     **注意 project 列存的是执行编号**：禅道里项目和执行共用 zt_project，
--     关联需求时写的是当前执行（或项目自身）的 id，靠 zt_project.type 区分。
--
-- 【最容易忽略的一列：version】
--   zt_projectstory.version 记录的是**关联时需求的版本号**，不是需求的当前版本。
--   需求后来发生了正式变更（version+1），项目这边仍然"看到"当初规划的版本，
--   列表里要把这种差异标出来（版本已变更）。这是"计划冻结"语义的落点：
--   项目按某个版本排期，需求后续变更不会悄悄改掉已排期的内容。
--
-- 【关联规则（execution::linkStory）】
--   - 已关联的跳过；需求状态属于 draft/reviewing/closed 的跳过
--   - order 从该执行已有关系的最大 order +1 开始递增
--   - 动作日志按执行类型区分：linked2project / linked2execution / linked2kanban
--
-- 【移除规则（execution::unlinkStory）】
--   - 需求被冻结（zt_story.frozen）时禁止移除
--   - 如果当前是**项目**且它的某个**子执行**已经关联了这条需求 → 禁止移除
--     （否则子执行会出现"需求不在项目范围内"的不一致）
--   - 移除后要把剩余关系重新编号为 1..n
--
-- 关键字：order 需要反引号（MySQL 与 JSqlParser 都敏感）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 项目关联产品 ============================
CREATE TABLE IF NOT EXISTS `zt_projectproduct` (
  `id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '自增编号',
  `project`   bigint       NOT NULL DEFAULT 0 COMMENT '项目/执行编号',
  `product`   bigint       NOT NULL DEFAULT 0 COMMENT '产品编号',
  `branch`    bigint       NOT NULL DEFAULT 0 COMMENT '分支/平台（单值，0 主干）',
  `plan`      varchar(255) NOT NULL DEFAULT '' COMMENT '关联的计划，逗号列表',
  `roadmap`   varchar(255) NOT NULL DEFAULT '' COMMENT '关联的路线图（禅道原字段）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_product_branch` (`project`, `product`, `branch`),
  KEY `idx_product` (`product`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目关联产品表';

-- ============================ 2. 项目/执行关联需求 ============================
CREATE TABLE IF NOT EXISTS `zt_projectstory` (
  `id`      bigint   NOT NULL AUTO_INCREMENT COMMENT '自增编号',
  `project` bigint   NOT NULL DEFAULT 0 COMMENT '项目/执行编号',
  `product` bigint   NOT NULL DEFAULT 0 COMMENT '需求所属产品',
  `branch`  bigint   NOT NULL DEFAULT 0 COMMENT '需求所属分支',
  `story`   bigint   NOT NULL DEFAULT 0 COMMENT '需求编号',
  `version` smallint NOT NULL DEFAULT 1 COMMENT '关联时的需求版本（不是当前版本）',
  `order`   int      NOT NULL DEFAULT 0 COMMENT '排序（order 是保留字）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_story` (`project`, `story`),
  KEY `idx_story` (`story`),
  KEY `idx_product` (`product`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目/执行关联需求表';

-- ============================ 演示数据 ============================
-- 项目 1（禅道迁移一期）关联产品 1，并把产品 1 的三个需求纳入范围
INSERT INTO `zt_projectproduct` (`id`, `project`, `product`, `branch`, `plan`, `roadmap`) VALUES
(1, 1, 1, 0, '1', '')
ON DUPLICATE KEY UPDATE `plan` = VALUES(`plan`);

-- 同时给未关闭的项目 2（子项目A）也配一份，这样「项目需求」页默认选中时就能看到数据
INSERT INTO `zt_projectproduct` (`id`, `project`, `product`, `branch`, `plan`, `roadmap`) VALUES
(2, 2, 1, 0, '', '')
ON DUPLICATE KEY UPDATE `plan` = VALUES(`plan`);

INSERT INTO `zt_projectstory` (`id`, `project`, `product`, `branch`, `story`, `version`, `order`) VALUES
(1, 1, 1, 0, 1, 1, 1),
(2, 1, 1, 0, 2, 1, 2),
(3, 1, 1, 0, 4, 1, 3),
(4, 2, 1, 0, 1, 1, 1),
(5, 2, 1, 0, 4, 1, 2)
ON DUPLICATE KEY UPDATE `order` = VALUES(`order`), `version` = VALUES(`version`);

-- ============================ 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90057, '项目需求', 'zentao:projectstory:query',  2, 11, 90001, 'projectstory', 'ep:collection', 'zentao/projectstory/index', 'ZentaoProjectStory', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90058, '需求范围查询', 'zentao:projectstory:query',  3, 1, 90057, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90059, '需求范围关联', 'zentao:projectstory:update', 3, 2, 90057, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90060, '需求范围移除', 'zentao:projectstory:delete', 3, 3, 90057, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

-- ---------------------------------------------------------------------------
-- 工作量估算（workestimation）+ 项目视角的三个「入口模块」的权限
--
-- 【禅道语义】zt_workestimation 是**项目级的工作量/成本估算**表：
--     scale          规模（人天）
--     productivity   生产率（规模 ÷ 生产率 = 工期）
--     duration       工期（天）= scale / productivity
--     dayHour        每天工时（默认 8）
--     unitLaborCost  单位人工成本（元/人天）
--     totalLaborCost 总人工成本 = duration × dayHour × unitLaborCost
--     assignedTo     指派给（谁去估）
-- 注意：开源版里这张表**只有 model（getBudget）没有 control/view** ——
-- 也就是说开源版没有界面用它（project/config.php 的 linkMap 指向 workestimation/index，但该控制器不存在），
-- 属于 IPD/企业版的能力。本实现按表结构 + 通用公式把它补齐，接口保留。
--
-- 【顺带说明三个"入口模块"】禅道里 projectplan / projectbuild / projectrelease 都是**纯 redirect**：
--     projectplan   → productplan/browse（计划是产品维度的，项目视角只是换入口）
--     projectbuild  → project/build   （项目下的构建列表）
--     projectrelease→ 项目相关的发布列表
-- 它们没有自己的表和 model，所以本实现给项目的三个列表视图各加一个按项目聚合的只读接口，
-- 而不是另建三张表（清单里也据此标记为"等价覆盖"）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_workestimation` (
  `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '编号',
  `project`        bigint        NOT NULL DEFAULT 0 COMMENT '项目编号',
  `scale`          decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '规模（人天）',
  `productivity`   decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '生产率',
  `duration`       decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '工期（天）= scale / productivity',
  `unitLaborCost`  decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '单位人工成本（元/人天）',
  `totalLaborCost` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '总人工成本 = duration × dayHour × unitLaborCost',
  `dayHour`        decimal(10,2) NOT NULL DEFAULT 8.00 COMMENT '每天工时',
  `assignedTo`     varchar(64)   NOT NULL DEFAULT '' COMMENT '指派给',
  `assignedDate`   datetime      DEFAULT NULL COMMENT '指派时间',
  `createdBy`      varchar(64)   NOT NULL DEFAULT '' COMMENT '创建人（禅道列名）',
  `createdDate`    datetime      DEFAULT NULL COMMENT '创建时间（禅道列名）',
  `editedBy`       varchar(64)   NOT NULL DEFAULT '' COMMENT '最后修改人（禅道列名）',
  `editedDate`     datetime      DEFAULT NULL COMMENT '最后修改时间（禅道列名）',
  -- yudao BaseDO 约定字段
  `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creator`        varchar(64)   NOT NULL DEFAULT '',
  `updater`        varchar(64)   NOT NULL DEFAULT '',
  `deleted`        bit(1)        NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工作量估算表';

-- 演示数据：项目 1 的估算（规模 100 人天、生产率 5 → 工期 20 天、每天 8 小时、单位成本 1500 → 总成本 240000）
INSERT INTO `zt_workestimation` (`id`, `project`, `scale`, `productivity`, `duration`, `unitLaborCost`, `totalLaborCost`,
                                 `dayHour`, `assignedTo`, `assignedDate`, `createdBy`, `createdDate`, `creator`, `updater`)
VALUES (99201, 1, 100.00, 5.00, 20.00, 1500.00, 240000.00, 8.00, 'admin', NOW(), 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `scale` = VALUES(`scale`), `productivity` = VALUES(`productivity`), `duration` = VALUES(`duration`),
    `unitLaborCost` = VALUES(`unitLaborCost`), `totalLaborCost` = VALUES(`totalLaborCost`),
    `dayHour` = VALUES(`dayHour`), `deleted` = b'0';

-- ============================ 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90115, '工作量估算查询', 'zentao:workestimation:query',  3, 24, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90116, '工作量估算维护', 'zentao:workestimation:update', 3, 25, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90117, '项目计划视图',   'zentao:projectplan:query',     3, 26, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90118, '项目版本视图',   'zentao:projectbuild:query',    3, 27, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90119, '项目发布视图',   'zentao:projectrelease:query',  3, 28, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`);

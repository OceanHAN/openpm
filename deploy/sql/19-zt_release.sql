-- ---------------------------------------------------------------------------
-- 发布（release）模块
--
-- 【禅道语义】zt_release 是产品的**对外交付版本**：它引用构建（build）与计划（plan），
-- 记录这个版本「完成了哪些需求、解决了哪些 Bug、还遗留哪些 Bug」，形成交付清单。
--   需求 → 构建 → 发布  这条链的最后一段。
--
-- 【五个必须注意的设计】
--  1. **发布名全局唯一**：禅道唯一性校验是 check('name','unique', "system=X AND deleted=0")，
--     而 system 默认 0（没启用「系统」概念时所有发布都是 0），等价于**跨产品全局唯一**。
--     所以不能出现两个叫「1.0」的发布 —— 这是禅道的行为，本实现保持一致。
--  2. **自动创建影子构建（shadow）**：创建发布时会**顺带插一条 zt_build**（同名/同产品/同分支/
--     同日期），并把 release.shadow 指向它。改发布的名称/构建/日期时，影子构建同步更新。
--     这样「发布」也天然拥有了一个可关联需求/Bug 的构建实体。
--  3. **多值字段的第三种编码**：build / branch / project 存的是
--     **前后都带逗号的逗号列表**（',1,2,'），而 story.plan 是 '1,2'、productplan.branch 也是 '1,2'。
--     同一套系统里三种写法并存，查询统一用 FIND_IN_SET 才不会错。
--  4. **stories/bugs/leftBugs 三份清单**：stories=完成的需求、bugs=解决的 Bug、
--     leftBugs=**遗留的 Bug**（已知但带着上线的）。bugs 与 leftBugs 靠 linkBug 的 type 区分。
--  5. **从构建同步**：创建/编辑时如果选了构建，会把构建（含集成构建的子构建）里的
--     stories/bugs **并进发布**（禅道 processReleaseForCreate，isSync 控制）。
--     所以发布的需求/Bug 清单通常不需要手工维护。
--
-- 【状态】wait 未开始 / normal 已发布 / fail 发布失败 / terminate 停止维护
--   publish 与 changeStatus 都只是改 status（publish 到 normal 时还会把需求阶段置为 released）。
--   必填字段随状态变化：wait 不要求 releasedDate，normal 不要求 date。
--
-- 【关联表】zt_releaserelated 存 (release, objectType, objectID)，
--   objectType ∈ project/build/branch/release/story/bug/leftBug —— 一份泛化的关系表。
--
-- 关键字：desc / system / status 需要反引号（system 是 MySQL 8 保留字，见 README 第 20 条）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_release` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '发布编号',
  `project`      varchar(255) NOT NULL DEFAULT '0' COMMENT '所属项目，逗号列表（前后带逗号）',
  `product`      bigint       NOT NULL DEFAULT 0 COMMENT '所属产品',
  `branch`       varchar(255) NOT NULL DEFAULT '0' COMMENT '分支/平台，逗号列表',
  `shadow`       bigint       NOT NULL DEFAULT 0 COMMENT '影子构建编号（创建发布时自动生成的 zt_build）',
  `build`        varchar(255) NOT NULL DEFAULT '' COMMENT '包含的构建，逗号列表',
  `name`         varchar(255) NOT NULL DEFAULT '' COMMENT '发布版本号（全局唯一）',
  `system`       bigint       NOT NULL DEFAULT 0 COMMENT '所属系统（禅道原字段，未启用时恒为 0）',
  `releases`     varchar(255) NOT NULL DEFAULT '' COMMENT '被包含的子发布，逗号列表',
  `marker`       tinyint      NOT NULL DEFAULT 0 COMMENT '是否里程碑',
  `date`         date         NULL COMMENT '计划发布日期',
  `releasedDate` datetime     NULL COMMENT '实际发布日期',
  `stories`      text         NULL COMMENT '本次完成的需求，逗号列表',
  `bugs`         text         NULL COMMENT '本次解决的 Bug，逗号列表',
  `leftBugs`     text         NULL COMMENT '遗留的 Bug，逗号列表',
  `mailto`       text         NULL COMMENT '抄送给（禅道原字段）',
  `notify`       varchar(255) NOT NULL DEFAULT '' COMMENT '是否发送通知（禅道原字段）',
  `status`       varchar(20)  NOT NULL DEFAULT 'wait' COMMENT 'wait/normal/fail/terminate',
  `subStatus`    varchar(30)  NOT NULL DEFAULT '' COMMENT '子状态（禅道原字段）',
  `desc`         mediumtext   NULL COMMENT '描述',
  `createdBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人',
  `createdDate`  datetime     NULL COMMENT '创建时间',
  -- yudao BaseDO 约定字段
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`      varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product_status` (`product`, `status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品发布表';

-- 泛化关联表：发布 ↔ 项目/构建/分支/子发布/需求/Bug/遗留Bug
CREATE TABLE IF NOT EXISTS `zt_releaserelated` (
  `id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '自增编号',
  `release`    bigint      NOT NULL DEFAULT 0 COMMENT '发布编号',
  `objectID`   bigint      NOT NULL DEFAULT 0 COMMENT '关联对象编号',
  `objectType` varchar(10) NOT NULL DEFAULT '' COMMENT 'project/build/branch/release/story/bug/leftBug',
  PRIMARY KEY (`id`),
  KEY `idx_release_type` (`release`, `objectType`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发布关联对象表';

-- ============================ 演示数据 ============================
-- 产品 1：V1.0 已发布（引用构建 1、2），V1.1 未开始；产品 3：门户 1.0 停止维护
INSERT INTO `zt_release` (`id`, `project`, `product`, `branch`, `shadow`, `build`, `name`, `marker`, `date`,
                          `releasedDate`, `stories`, `bugs`, `leftBugs`, `status`, `desc`, `createdBy`, `createdDate`)
VALUES
(1, ',1,', 1, ',0,', 0, ',1,2,', 'V1.0',   1, '2026-02-28', '2026-03-01 10:00:00', '1,2,4', '', '', 'normal',
    '第一个正式版本', 'admin', NOW()),
(2, ',1,', 1, ',0,', 0, '',      'V1.1',   0, '2026-06-30', NULL, '', '', '', 'wait',
    '下一个迭代版本', 'admin', NOW()),
(3, ',1,', 3, ',3,', 0, '',      '门户 1.0', 0, '2025-12-31', '2026-01-05 09:00:00', '', '', '', 'terminate',
    '客户门户首个版本，已停止维护', 'admin', NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = VALUES(`status`),
    `build` = VALUES(`build`), `stories` = VALUES(`stories`), `deleted` = b'0';

-- 关系表（发布 ↔ 构建/项目/分支/需求）
DELETE FROM `zt_releaserelated` WHERE `release` IN (1, 2, 3);
INSERT INTO `zt_releaserelated` (`release`, `objectID`, `objectType`) VALUES
(1, 1, 'project'), (1, 1, 'build'), (1, 2, 'build'), (1, 0, 'branch'), (1, 1, 'story'), (1, 2, 'story'), (1, 4, 'story'),
(2, 1, 'project'), (2, 0, 'branch'),
(3, 1, 'project'), (3, 3, 'branch');

-- ============================ 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90052, '发布管理', 'zentao:release:query',  2, 10, 90001, 'release', 'ep:promotion', 'zentao/release/index', 'ZentaoRelease', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90053, '发布查询', 'zentao:release:query',  3, 1, 90052, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90054, '发布创建', 'zentao:release:create', 3, 2, 90052, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90055, '发布修改', 'zentao:release:update', 3, 3, 90052, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90056, '发布删除', 'zentao:release:delete', 3, 4, 90052, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

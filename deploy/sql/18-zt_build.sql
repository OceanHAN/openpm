-- ---------------------------------------------------------------------------
-- 构建（build）模块
--
-- 【禅道语义】zt_build 是「一次打包」的记录：它属于某个执行（execution）、引用某个产品，
-- 并记录**本次完成的需求**（stories）和**本次解决的 Bug**（bugs）。
--   - 缺陷的 resolvedBuild 存的就是构建编号（zt_bug.resolvedBuild varchar(30)），
--     所以「Bug 在哪个版本修好的」这条追溯链是：bug.resolvedBuild → zt_build.id
--   - 发布（zt_release.build）再引用构建，形成 需求 → 构建 → 发布 的交付链
--
-- 【三个特色】
--  1. **集成构建**：builds 字段是子构建编号的逗号列表（varchar），集成构建的
--     execution 固定为 0、branch 由子构建的分支**并集**算出来，而且读取时
--     stories/bugs 要把所有子构建的并进来（禅道 joinChildBuilds）。
--     判断一个构建是不是「子构建」：它被别的构建的 builds 包含，或被某个发布的 build 引用。
--  2. **stories / bugs 是逗号列表**：和 zt_story.plan 一样是多值文本，
--     查询要用 FIND_IN_SET。
--  3. **关联 Bug 会顺手解决它**：禅道 build::linkBug() 对「还没解决/关闭」的 Bug
--     直接把 status 改成 resolved、resolution 固定为 fixed、resolvedBuild 指向本构建，
--     并把 Bug 指派回创建人。所以「关联 Bug」在构建里是一个带副作用的操作。
--
-- 【唯一性】同一 (product, branch) 下构建名不能重复（禅道 check('name','unique', ...)）。
--   和 zt_branch 一样用应用层校验，不建唯一索引 —— 逻辑删除下唯一索引会让
--   「删掉后重建同名构建」失败。
--
-- 【必填字段】禅道 $config->build->create->requiredFields = 'execution,product,name,builder,date'：
--   集成构建去掉 execution；项目未关联产品（project.hasProduct = 0）时去掉 product。
--
-- 关键字：desc 需要反引号。已用 JSqlParser 4.5 验证 build/stories/bugs/builds 列名可解析。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_build` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '构建编号',
  `project`        bigint       NOT NULL DEFAULT 0 COMMENT '所属项目',
  `product`        bigint       NOT NULL DEFAULT 0 COMMENT '所属产品',
  `branch`         varchar(255) NOT NULL DEFAULT '0' COMMENT '分支/平台，逗号列表；0 主干',
  `execution`      bigint       NOT NULL DEFAULT 0 COMMENT '所属执行；集成构建固定为 0',
  `builds`         varchar(255) NOT NULL DEFAULT '' COMMENT '集成构建包含的子构建，逗号列表',
  `name`           varchar(150) NOT NULL DEFAULT '' COMMENT '构建名称',
  `system`         bigint       NOT NULL DEFAULT 0 COMMENT '所属系统（禅道原字段）',
  `scmPath`        varchar(255) NOT NULL DEFAULT '' COMMENT '源代码地址',
  `filePath`       varchar(255) NOT NULL DEFAULT '' COMMENT '下载地址',
  `date`           date         NULL COMMENT '打包日期',
  `stories`        text         NULL COMMENT '本次完成的需求，逗号列表',
  `bugs`           text         NULL COMMENT '本次解决的 Bug，逗号列表',
  `artifactRepoID` bigint       NOT NULL DEFAULT 0 COMMENT '制品库（禅道原字段）',
  `builder`        varchar(64)  NOT NULL DEFAULT '' COMMENT '构建者',
  `desc`           mediumtext   NULL COMMENT '描述',
  `createdBy`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人',
  `createdDate`    datetime     NULL COMMENT '创建时间',
  -- yudao BaseDO 约定字段
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product_branch` (`product`, `branch`),
  KEY `idx_execution` (`execution`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='构建表';

-- ============================ 演示数据 ============================
-- 先补一个演示执行（构建必须挂在执行下）。
-- 注意：id 用 90001 而不是 3 —— 3 这类小 id 会被各种测试反复占用（逻辑删除后行还在），
-- 用 ON DUPLICATE KEY UPDATE 撞上旧行时只会改 name/status，type 仍然是旧的 'project'。
-- 所以这里显式指定一个远离测试区间的 id，并把 type / deleted 一并写进 UPDATE。
INSERT INTO `zt_project` (`id`, `project`, `name`, `type`, `model`, `status`, `pri`, `begin`, `end`,
                          `estimate`, `left`, `consumed`, `progress`, `PM`, `team`, `teamCount`,
                          `openedBy`, `openedDate`, `creator`, `updater`, `deleted`)
VALUES (90001, 1, 'V1.0 迭代', 'sprint', 'scrum', 'doing', 3, '2026-01-05', '2026-02-28',
        80, 80, 0, 0, 'admin', 'admin', 1, 'admin', NOW(), 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `type` = VALUES(`type`), `project` = VALUES(`project`),
    `status` = VALUES(`status`), `deleted` = b'0';

INSERT INTO `zt_build` (`id`, `project`, `product`, `branch`, `execution`, `builds`, `name`, `date`,
                        `stories`, `bugs`, `builder`, `scmPath`, `filePath`, `desc`, `createdBy`, `createdDate`)
VALUES
(1, 1, 1, '0', 90001, '', 'V1.0-beta1', '2026-01-20', '1,2', '', 'admin', 'git@example.com:zentao.git', 'http://example.com/pkg/v1.0-beta1.zip', '第一个测试包', 'admin', NOW()),
(2, 1, 1, '0', 90001, '', 'V1.0-beta2', '2026-02-10', '4', '', 'admin', 'git@example.com:zentao.git', 'http://example.com/pkg/v1.0-beta2.zip', '修复若干问题', 'admin', NOW()),
(3, 1, 1, '0', 0, '1,2', 'V1.0 集成版', '2026-02-28', '', '', 'admin', '', 'http://example.com/pkg/v1.0.zip', '集成构建：包含 beta1 与 beta2', 'admin', NOW())
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `stories` = VALUES(`stories`), `builds` = VALUES(`builds`),
    `execution` = VALUES(`execution`), `deleted` = b'0';

-- ============================ 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90047, '构建管理', 'zentao:build:query',  2, 9, 90001, 'build', 'ep:box', 'zentao/build/index', 'ZentaoBuild', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90048, '构建查询', 'zentao:build:query',  3, 1, 90047, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90049, '构建创建', 'zentao:build:create', 3, 2, 90047, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90050, '构建修改', 'zentao:build:update', 3, 3, 90047, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90051, '构建删除', 'zentao:build:delete', 3, 4, 90047, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

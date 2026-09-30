-- ---------------------------------------------------------------------------
-- 测试用例集（testsuite）+ 测试报告（testreport）
--
-- 【为什么放一起】两个模块都在「测试链的上一层」，而且都不产生新的执行数据：
--   zt_testsuite / zt_suitecase  可复用的用例集合 —— 把一批用例打包，排进测试单时一次选完
--   zt_testreport                测试报告 —— 把**一段时间内**若干测试单的执行结果**汇总**出来
--   前者是「编排的便利」，后者是「结果的汇总」，都是对已有数据的组织方式，不改变数据本身。
--
-- 【报告是算出来的，不是存出来的】
--   zt_testreport 自己只存条件（哪些测试单 tasks、哪些构建 builds、时间范围 begin/end）
--   与一段人写的结论 report；用例数/通过/失败这些数字都是**读的时候现算**的
--   （禅道 getResultSummary / getPerCaseResult4Report）。
--   汇总规则有一个容易忽略的点：**一条 run 在一段时间里可能跑了很多次，
--   只取最后一次结果**（禅道按 date 排序后覆盖写入 $runCasesResults[$run]），
--   而不是把每次执行都算一遍 —— 否则「通过之后又失败」会被算成一次通过一次失败。
--
-- 【用例集用 REPLACE，所以它需要唯一键】
--   禅道 linkCase 用 REPLACE INTO，靠 (suite, case) 保证幂等。禅道原表没建唯一索引，
--   这里补上 UNIQUE KEY —— 否则 REPLACE 会变成「每次都插一行」，同一个用例在集合里出现多次。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 用例集 ============================
CREATE TABLE IF NOT EXISTS `zt_testsuite` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '用例集编号',
  `project`        bigint       NOT NULL DEFAULT 0 COMMENT '所属项目',
  `product`        bigint       NOT NULL DEFAULT 0 COMMENT '所属产品',
  `name`           varchar(255) NOT NULL DEFAULT '' COMMENT '集合名称',
  `desc`           mediumtext   DEFAULT NULL COMMENT '描述（desc 是 MySQL 保留字）',
  `type`           varchar(20)  NOT NULL DEFAULT '' COMMENT '类型：public 公共 / private 私有',
  `order`          int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `addedBy`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `addedDate`      datetime     NULL COMMENT '创建时间（驼峰列名）',
  `lastEditedBy`   varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `lastEditedDate` datetime     NULL COMMENT '最后修改时间（驼峰列名）',
  -- yudao BaseDO 约定字段
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例集表';

-- ============================ 2. 集合里的用例 ============================
-- 没有 deleted 列（禅道原样），移除即物理删。
-- UNIQUE(suite, case) 是**本实现补的**：禅道用 REPLACE INTO 但没有唯一键，
-- 同一个用例会被重复插入（见文件头说明）。
CREATE TABLE IF NOT EXISTS `zt_suitecase` (
  `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `suite`       bigint   NOT NULL DEFAULT 0 COMMENT '用例集编号',
  `product`     bigint   NOT NULL DEFAULT 0 COMMENT '所属产品',
  `case`        bigint   NOT NULL DEFAULT 0 COMMENT '用例编号（`case` 是 MySQL 保留字）',
  `caseVersion` smallint NOT NULL DEFAULT 0 COMMENT '排进来时的用例版本（驼峰列名）',
  `version`     smallint NOT NULL DEFAULT 1 COMMENT '版本（禅道原字段）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_suite_case` (`suite`, `case`),
  KEY `idx_case` (`case`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用例集与用例的关联表';

-- ============================ 3. 测试报告 ============================
-- tasks / builds 都是**逗号列表**（一次报告可以汇总多个测试单/构建）。
-- cases / stories / bugs 是报告生成时算出来并**落库留档**的清单（禅道也是存下来的），
-- 而通过/失败等数字则在读取时按 tasks + 时间范围现算。
CREATE TABLE IF NOT EXISTS `zt_testreport` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '报告编号',
  `project`     bigint       NOT NULL DEFAULT 0 COMMENT '所属项目',
  `product`     bigint       NOT NULL DEFAULT 0 COMMENT '所属产品',
  `execution`   bigint       NOT NULL DEFAULT 0 COMMENT '所属执行',
  `tasks`       varchar(255) NOT NULL DEFAULT '' COMMENT '汇总的测试单，逗号列表',
  `builds`      varchar(255) NOT NULL DEFAULT '' COMMENT '涉及的构建，逗号列表',
  `title`       varchar(255) NOT NULL DEFAULT '' COMMENT '报告标题',
  `begin`       date         NULL COMMENT '统计开始日期（begin 是 JSqlParser 保留字）',
  `end`         date         NULL COMMENT '统计结束日期（end 是 JSqlParser 保留字）',
  `owner`       varchar(64)  NOT NULL DEFAULT '' COMMENT '负责人',
  `stories`     text         DEFAULT NULL COMMENT '涉及的需求，逗号列表（生成时留档）',
  `bugs`        text         DEFAULT NULL COMMENT '涉及的缺陷，逗号列表（生成时留档）',
  `cases`       text         DEFAULT NULL COMMENT '涉及的用例，逗号列表（生成时留档）',
  `report`      text         DEFAULT NULL COMMENT '结论 / 人工总结',
  `createdBy`   varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `createdDate` datetime     NULL COMMENT '创建时间（驼峰列名）',
  -- yudao BaseDO 约定字段
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试报告表';

-- ============================ 4. 演示数据 ============================
-- id 分段：用例集 95101+ ｜ 集合内用例 95151+ ｜ 报告 95201+
INSERT INTO `zt_testsuite` (`id`, `project`, `product`, `name`, `desc`, `type`, `order`,
                            `addedBy`, `addedDate`, `lastEditedBy`, `lastEditedDate`, `creator`, `updater`)
VALUES
(95101, 1, 1, '冒烟用例集', '每次发版必跑的几条', 'public', 1, 'admin', NOW(), 'admin', NOW(), 'admin', 'admin'),
(95102, 1, 1, '登录专项集', '登录相关的全部用例', 'public', 2, 'admin', NOW(), 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `desc` = VALUES(`desc`), `type` = VALUES(`type`),
    `order` = VALUES(`order`), `deleted` = b'0';

DELETE FROM `zt_suitecase` WHERE `suite` BETWEEN 95101 AND 95102;
INSERT INTO `zt_suitecase` (`id`, `suite`, `product`, `case`, `caseVersion`, `version`, `creator`, `updater`)
VALUES
(95151, 95101, 1, 93101, 2, 1, 'admin', 'admin'),
(95152, 95101, 1, 93102, 1, 1, 'admin', 'admin'),
(95153, 95102, 1, 93101, 2, 1, 'admin', 'admin'),
(95154, 95102, 1, 93102, 1, 1, 'admin', 'admin'),
(95155, 95102, 1, 93103, 1, 1, 'admin', 'admin');

INSERT INTO `zt_testreport` (`id`, `project`, `product`, `execution`, `tasks`, `builds`, `title`,
                             `begin`, `end`, `owner`, `stories`, `bugs`, `cases`, `report`,
                             `createdBy`, `createdDate`, `creator`, `updater`)
VALUES
(95201, 1, 1, 0, '94101,94103', '1,3', 'V1.0 测试报告', '2026-02-01', '2026-03-10', 'admin',
    '1,4', '1', '93101,93102,93103,93104,93105',
    '整体质量可控：核心流程通过，登录失败用例暴露了一个 500 缺陷。',
    'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `tasks` = VALUES(`tasks`), `builds` = VALUES(`builds`), `title` = VALUES(`title`),
    `begin` = VALUES(`begin`), `end` = VALUES(`end`), `owner` = VALUES(`owner`),
    `stories` = VALUES(`stories`), `bugs` = VALUES(`bugs`), `cases` = VALUES(`cases`),
    `report` = VALUES(`report`), `deleted` = b'0';

-- ============================ 5. 菜单与权限 ============================
-- 两个模块合用一个页面（两个 Tab）：它们都是「对已有测试数据的组织」，放一起更好理解。
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90087, '测试报告', 'zentao:testreport:query',  2, 14, 90001, 'testreport', 'ep:data-analysis', 'zentao/testreport/index', 'ZentaoTestreport', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90088, '报告查询', 'zentao:testreport:query',  3, 1, 90087, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90089, '报告创建', 'zentao:testreport:create', 3, 2, 90087, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90090, '报告修改', 'zentao:testreport:update', 3, 3, 90087, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90091, '报告删除', 'zentao:testreport:delete', 3, 4, 90087, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90092, '用例集查询', 'zentao:testsuite:query',  3, 5, 90087, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90093, '用例集创建', 'zentao:testsuite:create', 3, 6, 90087, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90094, '用例集修改', 'zentao:testsuite:update', 3, 7, 90087, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90095, '用例集删除', 'zentao:testsuite:delete', 3, 8, 90087, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

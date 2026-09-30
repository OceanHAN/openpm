-- ---------------------------------------------------------------------------
-- 测试用例（testcase）模块
--
-- 【禅道语义】三张表：
--   zt_case       用例头部：当前版本、标题（冗余展示）、优先级、类型、状态、
--                 关联需求 + **storyVersion（冻结的需求版本）**、模块/分支归属
--   zt_casespec   版本快照 (case, version) 唯一：该版本的标题 / 前置条件 / 附件
--   zt_casestep   步骤 (case, version 分组)：desc + expect，parent 指向所属「步骤组」
--
-- 【第三条版本规则 —— 这是本模块最值得记的地方】
--   前面已经有两个「追加式版本链」：zt_storyspec（正式变更才 +1）、
--   zt_doccontent（正文变了才 +1）。用例是第三条，而且判据完全不同：
--       **只有「步骤」变了才 version+1**，标题/前置条件/优先级/状态改了都不升版本。
--   更要紧的是：步骤一变，状态会被打回 wait（待评审）—— 因为步骤是用例的核心，
--   改了步骤等于换了用例，必须重新评审。见 README 3.18。
--
-- 【storyVersion 冻结】
--   用例关联需求时记下当时的 storyVersion。需求后来升版了，用例并不会自动跟着变，
--   而是进入「待确认」状态（列表里按 needconfirm 过滤：
--   zt_story.version > zt_case.storyVersion AND zt_story.status='active'），
--   要人点一下「确认需求变更」才把 storyVersion 追平。这与 zt_projectstory 冻结
--   关联时的需求版本是同一个思路（README 3.13）。
--
-- 【步骤的层级】
--   zt_casestep.type ∈ step / group，group 是「步骤组」（只有 desc、没有 expect）。
--   parent 指向所属 group，最多三级（禅道 processSteps 里 grade 只算到 3）。
--   前端展示的编号 1. / 1.1 / 1.1.1 是**算出来的**，不落库。
--
-- 【本表只建「本轮用到的列」】
--   禅道 zt_case 有 45 列，脚本/自动化/颜色/频率/场景等未迁移，按需再补。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 用例头部 ============================
CREATE TABLE IF NOT EXISTS `zt_case` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '用例编号',
  `product`        bigint       NOT NULL DEFAULT 0 COMMENT '所属产品',
  `branch`         bigint       NOT NULL DEFAULT 0 COMMENT '所属分支/平台（0=主干）',
  `module`         bigint       NOT NULL DEFAULT 0 COMMENT '所属模块（通用树 zt_module，type=case）',
  `story`          bigint       NOT NULL DEFAULT 0 COMMENT '关联需求',
  `storyVersion`   smallint     NOT NULL DEFAULT 1 COMMENT '关联需求在关联那一刻的版本（冻结，驼峰列名）',
  `title`          varchar(255) NOT NULL DEFAULT '' COMMENT '标题（冗余，按当前版本同步）',
  `precondition`   text         DEFAULT NULL COMMENT '前置条件（驼峰列名）',
  `keywords`       varchar(255) NOT NULL DEFAULT '' COMMENT '关键词',
  `pri`            tinyint      NOT NULL DEFAULT 3 COMMENT '优先级 1-4',
  `type`           varchar(30)  NOT NULL DEFAULT '' COMMENT '类型：unit/interface/feature/install/config/performance/security/other',
  `stage`          varchar(255) NOT NULL DEFAULT '' COMMENT '适用测试环节，**逗号列表**（禅道可多选）',
  `status`         varchar(30)  NOT NULL DEFAULT 'wait' COMMENT '状态：wait 待评审 / normal 正常 / blocked 被阻塞 / investigate 研究中',
  `lastRunResult`  varchar(30)  NOT NULL DEFAULT '' COMMENT '最近执行结果（驼峰列名，由 testtask 回写）',
  `lastRunner`     varchar(64)  NOT NULL DEFAULT '' COMMENT '最近执行人（驼峰列名）',
  `lastRunDate`    datetime     NULL COMMENT '最近执行时间（驼峰列名）',
  `version`        int          NOT NULL DEFAULT 1 COMMENT '当前版本号（只有步骤变了才 +1）',
  `order`          int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `sort`           int          NOT NULL DEFAULT 0 COMMENT '排序（禅道 sort 列）',
  `openedBy`       varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `openedDate`     datetime     NULL COMMENT '创建时间（驼峰列名）',
  `reviewedBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '评审人（驼峰列名）',
  `reviewedDate`   date         NULL COMMENT '评审时间（驼峰列名）',
  `lastEditedBy`   varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `lastEditedDate` datetime     NULL COMMENT '最后修改时间（驼峰列名）',
  `fromBug`        bigint       NOT NULL DEFAULT 0 COMMENT '来源缺陷（驼峰列名，0=非缺陷转来）',
  -- yudao BaseDO 约定字段
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product_module` (`product`, `module`, `deleted`),
  KEY `idx_story` (`story`),
  KEY `idx_branch` (`branch`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例表';

-- ============================ 2. 用例版本快照 ============================
-- 与 zt_storyspec 同构：UNIQUE(case, version)。
-- 差异：这张表只存「标题 + 前置条件 + 附件」，**步骤不在里面**（步骤单独一张表）。
CREATE TABLE IF NOT EXISTS `zt_casespec` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `case`         bigint       NOT NULL DEFAULT 0 COMMENT '用例编号（`case` 是 MySQL 保留字）',
  `version`      int          NOT NULL DEFAULT 1 COMMENT '版本号',
  `title`        varchar(255) NOT NULL DEFAULT '' COMMENT '该版本的标题',
  `precondition` text         DEFAULT NULL COMMENT '该版本的前置条件（驼峰列名）',
  `files`        text         DEFAULT NULL COMMENT '附件编号，逗号列表（zt_file.id）',
  -- yudao BaseDO 约定字段
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`      varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_case_version` (`case`, `version`),
  KEY `idx_case` (`case`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例版本快照表';

-- ============================ 3. 用例步骤 ============================
-- 一对多：一个 (case, version) 下 N 条步骤。
-- `type=group` 是步骤组（只有 desc），`parent` 指向所属 group，最多三级。
-- 这张表**没有 deleted 列**（禅道原样）—— 步骤是纯粹的子数据，
-- 删版本/删用例时整批物理删除，不做逻辑删除。
CREATE TABLE IF NOT EXISTS `zt_casestep` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '步骤编号',
  `parent`      bigint      NOT NULL DEFAULT 0 COMMENT '所属步骤组（0=顶层）',
  `case`        bigint      NOT NULL DEFAULT 0 COMMENT '用例编号（`case` 是 MySQL 保留字）',
  `version`     int         NOT NULL DEFAULT 1 COMMENT '所属用例版本',
  `type`        varchar(10) NOT NULL DEFAULT 'step' COMMENT 'step 步骤 / group 步骤组',
  `desc`        text        DEFAULT NULL COMMENT '步骤描述（desc 是 MySQL 保留字）',
  `expect`      text        DEFAULT NULL COMMENT '预期结果',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  PRIMARY KEY (`id`),
  KEY `idx_case_version` (`case`, `version`),
  KEY `idx_parent` (`parent`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例步骤表（无 deleted 列，整批物理删除）';

-- ============================ 4. 演示数据 ============================
-- id 分段（三张表互不相邻，理由见 25-zt_doc.sql 的注释）：
--   用例 93101+ ｜ 步骤 93151+ ｜ 版本快照 93201+ ｜ 用例模块树 93301+
--
-- 【用例模块树】用例挂的是 (root=产品, type='case', branch) 那棵树 —— 与需求模块树
-- 是**同一张 zt_module 的不同分支**。所以这里补几个 type='case' 的模块节点。
INSERT INTO `zt_module` (`id`, `root`, `branch`, `name`, `parent`, `path`, `grade`, `order`, `type`, `creator`, `updater`)
VALUES
(93301, 1, 0, '登录模块', 0,     ',93301,',       1, 1, 'case', 'admin', 'admin'),
(93302, 1, 0, '支付模块', 0,     ',93302,',       1, 2, 'case', 'admin', 'admin'),
(93303, 1, 0, '密码校验', 93301, ',93301,93303,', 2, 3, 'case', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `parent` = VALUES(`parent`), `path` = VALUES(`path`),
    `grade` = VALUES(`grade`), `type` = VALUES(`type`), `deleted` = b'0';

-- 用例：93101 是「步骤变过两次」的用例（v1→v2），用来演示版本规则；
--       93103 关联需求 1 但 storyVersion=1（需求已是 v2）→ 演示「待确认」。
INSERT INTO `zt_case` (`id`, `product`, `branch`, `module`, `story`, `storyVersion`, `title`, `precondition`,
                       `keywords`, `pri`, `type`, `stage`, `status`, `version`, `order`, `sort`,
                       `openedBy`, `openedDate`, `lastEditedBy`, `lastEditedDate`, `creator`, `updater`)
VALUES
(93101, 1, 0, 93301, 1, 2, '正常登录-用户名密码正确', '已注册用户 admin/admin123', '登录,冒烟',
    1, 'feature', 'smoke,feature', 'normal', 2, 93101, 93101,
    'admin', '2026-03-01 09:00:00', 'admin', '2026-03-05 10:00:00', 'admin', 'admin'),
(93102, 1, 0, 93303, 1, 2, '登录失败-密码错误', '已注册用户',
    '登录,异常', 2, 'feature', 'feature', 'normal', 1, 93102, 93102,
    'admin', '2026-03-01 09:30:00', 'admin', '2026-03-01 09:30:00', 'admin', 'admin'),
(93103, 1, 0, 93302, 1, 1, '下单-库存不足时的提示', '商品库存为 0',
    '下单,库存', 1, 'feature', 'feature', 'wait', 1, 93103, 93103,
    'admin', '2026-03-02 11:00:00', 'admin', '2026-03-02 11:00:00', 'admin', 'admin'),
(93104, 1, 0, 93302, 0, 1, '接口-查询用户信息返回 200', '服务已启动',
    '接口', 3, 'interface', 'intergrate', 'normal', 1, 93104, 93104,
    'admin', '2026-03-03 14:00:00', 'admin', '2026-03-03 14:00:00', 'admin', 'admin'),
(93105, 1, 0, 93301, 0, 1, '性能-1000 并发登录', '压测环境就绪',
    '性能', 4, 'performance', 'system', 'blocked', 1, 93105, 93105,
    'admin', '2026-03-04 15:00:00', 'admin', '2026-03-04 15:00:00', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `product` = VALUES(`product`), `branch` = VALUES(`branch`), `module` = VALUES(`module`),
    `story` = VALUES(`story`), `storyVersion` = VALUES(`storyVersion`), `title` = VALUES(`title`),
    `precondition` = VALUES(`precondition`), `pri` = VALUES(`pri`), `type` = VALUES(`type`),
    `stage` = VALUES(`stage`), `status` = VALUES(`status`), `version` = VALUES(`version`),
    `deleted` = b'0';

-- 版本快照：93101 有 v1/v2；其余各 1 条
INSERT INTO `zt_casespec` (`id`, `case`, `version`, `title`, `precondition`, `files`, `creator`, `updater`)
VALUES
(93201, 93101, 1, '正常登录-用户名密码正确', '已注册用户 admin/admin123', '', 'admin', 'admin'),
(93202, 93101, 2, '正常登录-用户名密码正确（补充验证码）', '已注册用户 admin/admin123', '', 'admin', 'admin'),
(93203, 93102, 1, '登录失败-密码错误', '已注册用户', '', 'admin', 'admin'),
(93204, 93103, 1, '下单-库存不足时的提示', '商品库存为 0', '', 'admin', 'admin'),
(93205, 93104, 1, '接口-查询用户信息返回 200', '服务已启动', '', 'admin', 'admin'),
(93206, 93105, 1, '性能-1000 并发登录', '压测环境就绪', '', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `precondition` = VALUES(`precondition`), `deleted` = b'0';

-- 步骤：
--   93101 v1：3 步（打开登录页 / 填账号密码 / 点登录）
--   93101 v2：4 步，且带了 2 个步骤组（演示 group + parent 的层级编号）
--   93102 v1：2 步
--   93103/93104/93105 v1：各 1-2 步
-- 这张表没有 deleted 列，重灌前先物理删掉这几条（幂等）。
DELETE FROM `zt_casestep` WHERE `case` BETWEEN 93101 AND 93105;
INSERT INTO `zt_casestep` (`id`, `parent`, `case`, `version`, `type`, `desc`, `expect`, `creator`, `updater`)
VALUES
-- 93101 v1
(93151, 0, 93101, 1, 'step', '打开登录页面', '页面正常加载，显示账号/密码输入框', 'admin', 'admin'),
(93152, 0, 93101, 1, 'step', '输入正确的账号和密码', '输入框回显正确', 'admin', 'admin'),
(93153, 0, 93101, 1, 'step', '点击登录按钮', '跳转到首页，右上角显示 admin', 'admin', 'admin'),
-- 93101 v2：两个步骤组
(93154, 0, 93101, 2, 'group', '第一步：输入凭证', '', 'admin', 'admin'),
(93155, 93154, 93101, 2, 'step', '打开登录页面', '页面正常加载', 'admin', 'admin'),
(93156, 93154, 93101, 2, 'step', '输入正确的账号和密码', '输入框回显正确', 'admin', 'admin'),
(93157, 0, 93101, 2, 'group', '第二步：验证并登录', '', 'admin', 'admin'),
(93158, 93157, 93101, 2, 'step', '输入图形验证码', '验证码校验通过', 'admin', 'admin'),
(93159, 93157, 93101, 2, 'step', '点击登录按钮', '跳转到首页', 'admin', 'admin'),
-- 93102 v1
(93160, 0, 93102, 1, 'step', '打开登录页并输入错误密码', '输入框回显正确', 'admin', 'admin'),
(93161, 0, 93102, 1, 'step', '点击登录按钮', '提示「账号或密码错误」，停留在登录页', 'admin', 'admin'),
-- 93103 v1
(93162, 0, 93103, 1, 'step', '选择库存为 0 的商品下单', '提交按钮可点击', 'admin', 'admin'),
(93163, 0, 93103, 1, 'step', '点击提交订单', '提示「库存不足」，订单未创建', 'admin', 'admin'),
-- 93104 v1
(93164, 0, 93104, 1, 'step', 'GET /api/user/1', 'HTTP 200，返回 JSON 含 id/name', 'admin', 'admin'),
-- 93105 v1
(93165, 0, 93105, 1, 'step', '用 1000 并发执行登录', '平均响应时间 < 500ms，无 5xx', 'admin', 'admin');

-- ============================ 5. 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90077, '测试用例', 'zentao:testcase:query',  2, 12, 90001, 'testcase', 'ep:files', 'zentao/testcase/index', 'ZentaoTestcase', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90078, '用例查询', 'zentao:testcase:query',  3, 1, 90077, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90079, '用例创建', 'zentao:testcase:create', 3, 2, 90077, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90080, '用例修改', 'zentao:testcase:update', 3, 3, 90077, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90081, '用例删除', 'zentao:testcase:delete', 3, 4, 90077, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

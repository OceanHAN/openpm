-- ---------------------------------------------------------------------------
-- 用例库（caselib）
--
-- 【禅道语义】用例库**没有自己的表**，它是 zt_testsuite 里 type='library' 的那批行：
--     module/caselib/model.php:142   from(TABLE_TESTSUITE)->where('product')->eq(0)
--                                    ->andWhere('type')->eq('library')
-- 也就是说「用例集」和「用例库」是同一张表的两类记录，靠 (product, type) 区分：
--     用例集 testsuite：product=<产品>，type in ('public','private')
--     用例库 caselib  ：product=0，     type='library'
-- 库里的用例是 zt_case 里 product=0 且 lib=<用例库编号> 的行；库的模块树是
-- zt_module 里 (root=<用例库编号>, type='caselib') 那棵树（ModuleTypeEnum.CASELIB）。
--
-- 这正是项目里反复出现的「共用表必须按 type 强过滤」问题（README 坑位 #12）：
-- 加上用例库之后，用例集列表必须排除 type='library'，用例列表必须排除 lib>0 的库用例。
--
-- 【本文件为什么有 ALTER】早期建 zt_case 时没建 lib 列（当时没有用例库模块）。
-- MySQL 8 没有 ADD COLUMN IF NOT EXISTS，所以用 information_schema 判一下再动态执行，
-- 保证这个文件可以重复执行（resume-verification.sh 每次都灌）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. zt_case 补 lib / fromCaseID / fromCaseVersion ============================
-- lib            ：所属用例库（product=0 时为库内用例）
-- fromCaseID     ：库内用例的**来源产品用例**（禅道 testcase/importToLib 用它做「已导入」判重）
-- fromCaseVersion：导入时来源用例的版本；来源升版后比它大 → 库里的这条「源用例已更新」
SET @has_lib := (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'zt_case' AND column_name = 'lib');
SET @ddl := IF(@has_lib = 0,
  'ALTER TABLE `zt_case` ADD COLUMN `lib` bigint NOT NULL DEFAULT 0 COMMENT ''所属用例库（0=不属于任何库）'' AFTER `product`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_from_case := (SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 'zt_case' AND column_name = 'fromCaseID');
SET @ddl := IF(@has_from_case = 0,
  'ALTER TABLE `zt_case` ADD COLUMN `fromCaseID` bigint NOT NULL DEFAULT 0 COMMENT ''来源产品用例编号（驼峰列名，0=库里手建）'' AFTER `fromBug`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_from_case_ver := (SELECT COUNT(*) FROM information_schema.columns
                            WHERE table_schema = DATABASE() AND table_name = 'zt_case' AND column_name = 'fromCaseVersion');
SET @ddl := IF(@has_from_case_ver = 0,
  'ALTER TABLE `zt_case` ADD COLUMN `fromCaseVersion` smallint NOT NULL DEFAULT 1 COMMENT ''导入时来源用例的版本（驼峰列名）'' AFTER `fromCaseID`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_lib_idx := (SELECT COUNT(*) FROM information_schema.statistics
                      WHERE table_schema = DATABASE() AND table_name = 'zt_case' AND index_name = 'idx_lib');
SET @ddl := IF(@has_lib_idx = 0, 'ALTER TABLE `zt_case` ADD KEY `idx_lib` (`lib`, `deleted`)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================ 2. 演示数据 ============================
-- id 分段：用例库 95301 ｜ 库内用例 95401+ ｜ 库内用例快照 95451+ ｜ 库内用例步骤 95461+
--           ｜ 库的模块树 95501+
-- 用例库 95301：product=0 + type='library'（与用例集共用 zt_testsuite）
INSERT INTO `zt_testsuite` (`id`, `project`, `product`, `name`, `desc`, `type`, `order`,
                            `addedBy`, `addedDate`, `lastEditedBy`, `lastEditedDate`, `creator`, `updater`)
VALUES
(95301, 0, 0, '公共用例库', '跨产品复用的基础用例：登录、权限、通用接口规范。', 'library', 1,
 'admin', NOW(), 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `desc` = VALUES(`desc`), `product` = 0, `type` = 'library', `deleted` = b'0';

-- 库的模块树：(root=用例库编号, type='caselib')
INSERT INTO `zt_module` (`id`, `root`, `branch`, `name`, `parent`, `path`, `grade`, `order`, `type`, `creator`, `updater`)
VALUES
(95501, 95301, 0, '登录与权限', 0, ',95501,', 1, 1, 'caselib', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `root` = VALUES(`root`), `type` = VALUES(`type`), `deleted` = b'0';

-- 库内用例：product=0 + lib=95301。
--   95401 正常状态、挂在库的模块树下、有 2 步
--   95402 待评审状态（演示库用例同样走 testcase 的评审与版本规则）
INSERT INTO `zt_case` (`id`, `product`, `lib`, `branch`, `module`, `story`, `storyVersion`, `title`, `precondition`,
                       `keywords`, `pri`, `type`, `stage`, `status`, `version`, `order`, `sort`,
                       `openedBy`, `openedDate`, `lastEditedBy`, `lastEditedDate`, `creator`, `updater`)
VALUES
(95401, 0, 95301, 0, 95501, 0, 1, '库用例：账号密码正确登录', '库里已存在 admin 账号',
    '登录,公共', 2, 'feature', 'smoke', 'normal', 1, 95401, 95401,
    'admin', '2026-03-06 09:00:00', 'admin', '2026-03-06 09:00:00', 'admin', 'admin'),
(95402, 0, 95301, 0, 0, 0, 1, '库用例：连续三次密码错误锁定账号', '库里已存在 admin 账号',
    '登录,安全', 1, 'security', 'feature', 'wait', 1, 95402, 95402,
    'admin', '2026-03-06 09:30:00', 'admin', '2026-03-06 09:30:00', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `product` = 0, `lib` = VALUES(`lib`), `module` = VALUES(`module`), `title` = VALUES(`title`),
    `precondition` = VALUES(`precondition`), `pri` = VALUES(`pri`), `type` = VALUES(`type`),
    `stage` = VALUES(`stage`), `status` = VALUES(`status`), `version` = VALUES(`version`),
    `deleted` = b'0';

INSERT INTO `zt_casespec` (`id`, `case`, `version`, `title`, `precondition`, `files`, `creator`, `updater`)
VALUES
(95451, 95401, 1, '库用例：账号密码正确登录', '库里已存在 admin 账号', '', 'admin', 'admin'),
(95452, 95402, 1, '库用例：连续三次密码错误锁定账号', '库里已存在 admin 账号', '', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `precondition` = VALUES(`precondition`), `deleted` = b'0';

INSERT INTO `zt_casestep` (`id`, `parent`, `case`, `version`, `type`, `desc`, `expect`, `creator`, `updater`)
VALUES
(95461, 0, 95401, 1, 'step', '打开登录页', '登录页正常显示', 'admin', 'admin'),
(95462, 0, 95401, 1, 'step', '输入正确的账号密码并提交', '登录成功并跳转到首页', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `desc` = VALUES(`desc`), `expect` = VALUES(`expect`);

-- ============================ 3. 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90120, '用例库', 'zentao:caselib:query',  2, 15, 90001, 'caselib', 'ep:collection', 'zentao/caselib/index', 'ZentaoCaselib', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90121, '用例库查询', 'zentao:caselib:query',  3, 1, 90120, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90122, '用例库创建', 'zentao:caselib:create', 3, 2, 90120, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90123, '用例库修改', 'zentao:caselib:update', 3, 3, 90120, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90124, '用例库删除', 'zentao:caselib:delete', 3, 4, 90120, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

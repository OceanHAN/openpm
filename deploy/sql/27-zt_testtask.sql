-- ---------------------------------------------------------------------------
-- 测试单（testtask）模块 —— 测试链真正闭环的一环
--
-- 【禅道语义】三张表：
--   zt_testtask    测试单（一次测试任务）：属于哪个产品/项目/执行/构建，负责人、起止日期、状态
--   zt_testrun     测试单里排了哪些用例，**UNIQUE(task, case)**
--   zt_testresult  每次执行的**历史记录**（一条 run 可以执行很多次，每次一行）
--
-- 【执行结果的三处回写 —— 这是本模块的核心】
--   createResult(runID, caseID, version, stepResults) 一次写三个地方：
--     1. INSERT zt_testresult        一行历史（步骤结果序列化在里面）
--     2. UPDATE zt_case              最近执行结果/执行人/执行时间
--     3. UPDATE zt_testrun           状态（blocked / normal）+ 最近结果
--   所以「用例的 lastRunResult」其实来自**测试单执行**，而不是用例自己维护的字段。
--   只做 testcase 不做 testtask，这三个字段永远是空的 —— 这也是为什么本模块必须做。
--
-- 【用例结果是由步骤结果算出来的】
--   默认 pass；逐个看步骤结果，遇到 n/a 或 pass 跳过；
--   第一个「既不是 n/a 也不是 pass」的结果就成为用例结果，遇到 fail 直接跳出。
--   即：**fail 优先、其次按出现顺序**，而不是「多数派」。
--
-- 【有意偏离禅道：重新关联用例不会抹掉执行结果】
--   禅道 linkCase 用 `REPLACE INTO zt_testrun`，而表的唯一键是 (task, case) ——
--   REPLACE 会**先删后插**，于是「把同一个用例再排一次」会把它之前的执行结果清空。
--   本实现改成「存在就只更新用例版本与指派，保留结果」，避免这个数据丢失陷阱。
--
-- 【本表只建「本轮用到的列」】禅道的联调测试单(joint)、团队(members)、
--   自动化(auto)、测试报告关联(testreport) 等未迁移，按需再补。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 测试单 ============================
CREATE TABLE IF NOT EXISTS `zt_testtask` (
  `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '测试单编号',
  `project`          bigint       NOT NULL DEFAULT 0 COMMENT '所属项目',
  `execution`        bigint       NOT NULL DEFAULT 0 COMMENT '所属执行',
  `product`          bigint       NOT NULL DEFAULT 0 COMMENT '所属产品',
  `build`            bigint       NOT NULL DEFAULT 0 COMMENT '所属构建（测的是哪个包）',
  `name`             varchar(255) NOT NULL DEFAULT '' COMMENT '测试单名称',
  `type`             varchar(255) NOT NULL DEFAULT '' COMMENT '类型（逗号列表，禅道可多选）',
  `owner`            varchar(64)  NOT NULL DEFAULT '' COMMENT '负责人',
  `pri`              tinyint      NOT NULL DEFAULT 0 COMMENT '优先级',
  `begin`            date         NULL COMMENT '计划开始日期（begin 是 JSqlParser 保留字）',
  `end`              date         NULL COMMENT '计划结束日期（end 是 JSqlParser 保留字）',
  `realBegan`        date         NULL COMMENT '实际开始日期（驼峰列名）',
  `realFinishedDate` datetime     NULL COMMENT '实际完成时间（驼峰列名）',
  `desc`             mediumtext   DEFAULT NULL COMMENT '描述（desc 是 MySQL 保留字）',
  `report`           text         DEFAULT NULL COMMENT '测试总结',
  `status`           varchar(30)  NOT NULL DEFAULT 'wait' COMMENT '状态：wait 未开始 / doing 进行中 / done 已关闭 / blocked 被阻塞',
  `createdBy`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `createdDate`      datetime     NULL COMMENT '创建时间（驼峰列名）',
  -- yudao BaseDO 约定字段
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`          varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`          varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`          bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product`, `deleted`),
  KEY `idx_execution` (`execution`),
  KEY `idx_build` (`build`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试单表';

-- ============================ 2. 测试单里的用例（run） ============================
-- UNIQUE(task, case)：一个测试单里一个用例只能排一次。
-- 注意它**没有 deleted 列**（禅道原样），移除用例就是物理删。
CREATE TABLE IF NOT EXISTS `zt_testrun` (
  `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '执行记录编号',
  `task`          bigint      NOT NULL DEFAULT 0 COMMENT '所属测试单',
  `case`          bigint      NOT NULL DEFAULT 0 COMMENT '用例编号（`case` 是 MySQL 保留字）',
  `caseVersion`   smallint    NOT NULL DEFAULT 0 COMMENT '排进来时的用例版本（驼峰列名）',
  `version`       smallint    NOT NULL DEFAULT 1 COMMENT '执行轮次（禅道原字段）',
  `assignedTo`    varchar(64) NOT NULL DEFAULT '' COMMENT '指派给（驼峰列名）',
  `lastRunner`    varchar(64) NOT NULL DEFAULT '' COMMENT '最近执行人（驼峰列名）',
  `lastRunDate`   datetime    NULL COMMENT '最近执行时间（驼峰列名）',
  `lastRunResult` varchar(30) NOT NULL DEFAULT '' COMMENT '最近执行结果（驼峰列名）',
  `status`        varchar(30) NOT NULL DEFAULT 'normal' COMMENT 'normal 正常 / blocked 被阻塞',
  `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`       varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`       varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_case` (`task`, `case`),
  KEY `idx_case` (`case`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试单用例执行表（无 deleted 列）';

-- ============================ 3. 执行结果历史 ============================
-- 一次执行一行：步骤结果以文本形式存起来，便于「这条用例历史上跑过几次、每次什么结果」。
-- 禅道存的是 PHP serialize() 字符串；本实现存 JSON（可读、跨语言），语义一致。
CREATE TABLE IF NOT EXISTS `zt_testresult` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '结果编号',
  `run`         bigint      NOT NULL DEFAULT 0 COMMENT '所属执行记录（zt_testrun.id）',
  `case`        bigint      NOT NULL DEFAULT 0 COMMENT '用例编号（`case` 是 MySQL 保留字）',
  `version`     smallint    NOT NULL DEFAULT 1 COMMENT '执行的用例版本',
  `caseResult`  varchar(30) NOT NULL DEFAULT '' COMMENT '用例级结果：pass/fail/blocked/n-a（驼峰列名）',
  `stepResults` text        DEFAULT NULL COMMENT '步骤级结果，JSON 数组（驼峰列名）',
  `lastRunner`  varchar(64) NOT NULL DEFAULT '' COMMENT '执行人（驼峰列名）',
  `date`        datetime    NULL COMMENT '执行时间（date 是 MySQL 关键字）',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  PRIMARY KEY (`id`),
  KEY `idx_run` (`run`),
  KEY `idx_case` (`case`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试执行结果历史表';

-- ============================ 4. 演示数据 ============================
-- id 分段（三张表互不相邻）：测试单 94101+ ｜ run 94151+ ｜ 结果 94201+
INSERT INTO `zt_testtask` (`id`, `project`, `execution`, `product`, `build`, `name`, `type`, `owner`, `pri`,
                           `begin`, `end`, `realBegan`, `realFinishedDate`, `desc`, `report`, `status`,
                           `createdBy`, `createdDate`, `creator`, `updater`)
VALUES
(94101, 1, 0,     1, 1, 'V1.0 冒烟测试', 'feature', 'admin', 1,
    '2026-03-01', '2026-03-10', '2026-03-02', NULL, '覆盖登录与下单主流程', '', 'doing',
    'admin', NOW(), 'admin', 'admin'),
(94102, 1, 90001, 1, 2, '迭代 1 功能测试', 'feature,interface', 'admin', 2,
    '2026-03-05', '2026-03-20', NULL, NULL, '迭代 1 的全部用例', '', 'wait',
    'admin', NOW(), 'admin', 'admin'),
(94103, 1, 0,     1, 3, 'V1.0 回归测试', 'feature', 'admin', 3,
    '2026-02-01', '2026-02-28', '2026-02-02', '2026-02-20 18:00:00', '版本发布前的回归', '全部通过', 'done',
    'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `project` = VALUES(`project`), `execution` = VALUES(`execution`), `product` = VALUES(`product`),
    `build` = VALUES(`build`), `name` = VALUES(`name`), `type` = VALUES(`type`),
    `owner` = VALUES(`owner`), `pri` = VALUES(`pri`), `begin` = VALUES(`begin`), `end` = VALUES(`end`),
    `realBegan` = VALUES(`realBegan`), `realFinishedDate` = VALUES(`realFinishedDate`),
    `desc` = VALUES(`desc`), `report` = VALUES(`report`), `status` = VALUES(`status`),
    `deleted` = b'0';

-- 测试单 94101 排了 3 条用例（93101/93102/93103），其中两条已经跑过：
--   93101 pass（v2 版本）、93102 fail（v1）—— 用来演示「用例上的最近执行结果来自测试单」
-- 94103（已关闭）也排了两条，都是 pass
DELETE FROM `zt_testrun` WHERE `task` BETWEEN 94101 AND 94103;
INSERT INTO `zt_testrun` (`id`, `task`, `case`, `caseVersion`, `version`, `assignedTo`,
                          `lastRunner`, `lastRunDate`, `lastRunResult`, `status`, `creator`, `updater`)
VALUES
(94151, 94101, 93101, 2, 1, 'admin', 'admin', '2026-03-02 10:00:00', 'pass', 'normal', 'admin', 'admin'),
(94152, 94101, 93102, 1, 1, 'admin', 'admin', '2026-03-02 10:05:00', 'fail', 'normal', 'admin', 'admin'),
(94153, 94101, 93103, 1, 1, 'admin', '', NULL, '', 'normal', 'admin', 'admin'),
(94154, 94103, 93104, 1, 1, 'admin', 'admin', '2026-02-20 17:00:00', 'pass', 'normal', 'admin', 'admin'),
(94155, 94103, 93105, 1, 1, 'admin', 'admin', '2026-02-20 17:30:00', 'pass', 'normal', 'admin', 'admin');

DELETE FROM `zt_testresult` WHERE `run` BETWEEN 94151 AND 94155;
INSERT INTO `zt_testresult` (`id`, `run`, `case`, `version`, `caseResult`, `stepResults`, `lastRunner`, `date`, `creator`, `updater`)
VALUES
(94201, 94151, 93101, 2, 'pass',
    '[{"id":93154,"result":"pass"},{"id":93155,"result":"pass"},{"id":93156,"result":"pass"},{"id":93157,"result":"pass"},{"id":93158,"result":"pass"},{"id":93159,"result":"pass"}]',
    'admin', '2026-03-02 10:00:00', 'admin', 'admin'),
(94202, 94152, 93102, 1, 'fail',
    '[{"id":93160,"result":"pass"},{"id":93161,"result":"fail"}]',
    'admin', '2026-03-02 10:05:00', 'admin', 'admin'),
(94203, 94154, 93104, 1, 'pass', '[{"id":93164,"result":"pass"}]',
    'admin', '2026-02-20 17:00:00', 'admin', 'admin'),
(94204, 94155, 93105, 1, 'pass', '[{"id":93165,"result":"pass"}]',
    'admin', '2026-02-20 17:30:00', 'admin', 'admin');

-- ============================ 5. 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90082, '测试单', 'zentao:testtask:query',  2, 13, 90001, 'testtask', 'ep:list', 'zentao/testtask/index', 'ZentaoTesttask', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90083, '测试单查询', 'zentao:testtask:query',  3, 1, 90082, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90084, '测试单创建', 'zentao:testtask:create', 3, 2, 90082, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90085, '测试单修改', 'zentao:testtask:update', 3, 3, 90082, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90086, '测试单删除', 'zentao:testtask:delete', 3, 4, 90082, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

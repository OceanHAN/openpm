-- ---------------------------------------------------------------------------
-- 演示数据补种（从空库初始化时缺的那部分）
--
-- 【为什么需要这个脚本】
-- 早几轮是在**远端已有库**上分批开发的：项目 / 需求 / 任务 / 缺陷 这几条演示数据
-- 当时是通过接口或手工 SQL 造出来的，**从来没有进过 deploy/sql**。
-- 后果是 `deploy/sql/01~35` 虽然能把表结构和大部分演示数据建出来，但从空库初始化时会缺：
--     zt_project  1「禅道迁移一期」、2「子项目A」
--     zt_story    1「支持需求批量导入」、4「支持需求批量导入（含校验）」（父需求）
--                 99301/99302/99303 需求分层链（业务需求 → 用户需求 → 研发需求）
--     zt_task     1「实现登录接口」（8/7/1，closed）
--     zt_bug      1
-- 而下面这些脚本/断言都**假设它们存在**：
--     30-zt_story_parent.sql  ← story 4 当父需求（childCount=2 / estimate=10）
--     31-zt_task_story.sql    ← task 1 挂 story 1，回填 storyVersion
--     32-zt_effort.sql        ← task 1 的 2 条工时（consumed=7、left=1）
--     33-zt_program.sql       ← project 1/2 挂到项目集 9001
--     35-zt_team.sql          ← project 1 的 3 名成员
--     test-effort / test-storytask / test-storytree / test-program / test-team 等脚本的「演示数据」断言
-- 这个缺口是**本机验证栈从空库初始化**时才第一次暴露出来的（远端一直有这些数据，所以察觉不到）。
--
-- 本脚本放在 01~35 之后执行，所以它自己把上面那些脚本当年"没生效"的 UPDATE 一起补上
-- （比如 story 4 的 isParent/estimate、project 1 的 teamCount、task 1 的 consumed/left）。
-- 幂等：全部 ON DUPLICATE KEY UPDATE，可以重复执行。
--
-- 灌脚本时务必让客户端是 utf8mb4（`mysql --default-character-set=utf8mb4`，
-- 或者像本机栈那样在 conf 里设 `[client] default-character-set`），否则中文会双重编码。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 项目 ============================
-- 项目集 9001 下的两个项目；path/grade 用禅道的逗号格式（见 33-zt_program.sql）
INSERT INTO `zt_project` (`id`, `project`, `parent`, `path`, `grade`, `name`, `code`, `model`, `type`,
                          `status`, `pri`, `begin`, `end`, `realBegan`, `realEnd`,
                          `estimate`, `left`, `consumed`, `progress`, `percent`,
                          `PM`, `PO`, `QD`, `RD`, `team`, `teamCount`,
                          `hasProduct`, `multiple`, `isTpl`, `milestone`, `acl`, `order`,
                          `openedBy`, `openedDate`, `closedBy`, `closedDate`, `closedReason`,
                          `creator`, `updater`, `deleted`)
VALUES
(1, 0, 9001, ',9001,1,', 2, '禅道迁移一期', 'ZENTAO-P1', 'scrum', 'project',
 'closed', 3, '2026-01-01', '2026-06-30', '2026-01-05', '2026-06-28',
 0.00, 0.00, 0.00, 100.00, 100.00,
 'admin', 'admin', 'tester', 'dev1', 'admin,tester,dev1', 3,
 1, 1, 0, 0, 'open', 5,
 'admin', NOW(), 'admin', NOW(), 'done',
 'admin', 'admin', b'0'),
(2, 0, 9001, ',9001,2,', 2, '子项目A', 'ZENTAO-P2', 'scrum', 'project',
 'wait', 3, '2026-02-01', '2026-06-30', NULL, NULL,
 0.00, 0.00, 0.00, 0.00, 0.00,
 'admin', '', '', '', '', 0,
 1, 1, 0, 0, 'open', 10,
 'admin', NOW(), '', NULL, '',
 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `project` = VALUES(`project`), `parent` = VALUES(`parent`), `path` = VALUES(`path`),
    `grade` = VALUES(`grade`), `name` = VALUES(`name`), `type` = VALUES(`type`),
    `status` = VALUES(`status`), `PM` = VALUES(`PM`), `team` = VALUES(`team`),
    `teamCount` = VALUES(`teamCount`), `deleted` = b'0';

-- ============================ 2. 需求 ============================
-- 需求 4 是父需求（30-zt_story_parent.sql 把 92201/92202 挂到它下面，并给它 estimate=10）；
-- 需求 1 是 task 1 挂的那条需求，版本 2（31 的回填要求它存在）
INSERT INTO `zt_story` (`id`, `product`, `module`, `plan`, `branch`, `title`, `type`, `category`, `pri`,
                        `estimate`, `status`, `stage`, `version`, `source`, `parent`, `parentVersion`,
                        `root`, `path`, `grade`, `isParent`, `openedBy`, `openedDate`, `assignedTo`, `assignedDate`,
                        `reviewedBy`, `reviewedDate`, `lastEditedBy`, `lastEditedDate`, `creator`, `updater`, `deleted`)
VALUES
(1, 1, 0, '', 0, '支持需求批量导入', 'story', 'feature', 2, 3.00, 'active', 'wait', 2, '', 0, 1,
 1, ',1,', 1, 0, 'admin', NOW(), 'admin', NOW(), 'admin', NOW(), 'admin', NOW(), 'admin', 'admin', b'0'),
(4, 1, 0, '', 0, '支持需求批量导入（含校验）', 'story', 'feature', 1, 10.00, 'active', 'wait', 2, '', 0, 2,
 4, ',4,', 1, 1, 'admin', NOW(), 'admin', NOW(), 'admin', NOW(), 'admin', NOW(), 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `product` = VALUES(`product`), `version` = VALUES(`version`),
    `status` = VALUES(`status`), `estimate` = VALUES(`estimate`), `isParent` = VALUES(`isParent`),
    `parent` = VALUES(`parent`), `root` = VALUES(`root`), `path` = VALUES(`path`),
    `grade` = VALUES(`grade`), `deleted` = b'0';

-- 需求版本快照：主表只存"当前版本"，标题/描述必须能在 zt_storyspec 里按版本读到
INSERT INTO `zt_storyspec` (`story`, `version`, `title`, `spec`, `verify`, `creator`, `updater`, `deleted`)
VALUES
(1, 1, '支持需求批量导入', '一次导入多条需求，减少手工录入。', '导入 100 条不报错。', 'admin', 'admin', b'0'),
(1, 2, '支持需求批量导入', '一次导入多条需求（v2：补充了字段校验与错误提示）。', '导入 100 条不报错，错误行给出提示。', 'admin', 'admin', b'0'),
(4, 1, '支持需求批量导入（含校验）', '父需求：只做汇总，不直接开发。', '子需求全部关闭后父需求自动关闭。', 'admin', 'admin', b'0'),
(4, 2, '支持需求批量导入（含校验）', '父需求：只做汇总（v2）。', '子需求全部关闭后父需求自动关闭。', 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `spec` = VALUES(`spec`), `verify` = VALUES(`verify`), `deleted` = b'0';

-- 父需求的聚合值（30 里那句 UPDATE 当年是空操作，因为 story 4 不存在）
UPDATE `zt_story` SET `isParent` = 1, `estimate` = 10.00 WHERE `id` = 4;
-- 子需求的 parentVersion 对齐父需求当前版本（30 写的是 2，这里保证一致）
UPDATE `zt_story` SET `parentVersion` = 2 WHERE `parent` = 4;

-- ------------- 需求分层演示链：业务需求 99301 → 用户需求 99302 → 研发需求 99303 -------------
-- 禅道把三层需求放在同一张 zt_story 表里、靠 type 区分（epic / requirement / story），
-- 这里给出一条完整的三层链路，供「需求分层树」页面与接口测试使用。
INSERT INTO `zt_story` (`id`, `product`, `module`, `plan`, `branch`, `title`, `type`, `category`, `pri`,
                        `estimate`, `status`, `stage`, `version`, `source`, `parent`, `parentVersion`,
                        `root`, `path`, `grade`, `isParent`, `openedBy`, `openedDate`, `assignedTo`, `assignedDate`,
                        `reviewedBy`, `reviewedDate`, `lastEditedBy`, `lastEditedDate`, `creator`, `updater`, `deleted`)
VALUES
(99301, 1, 0, '', 0, '业务需求：需求池支撑多层级管理', 'epic', 'feature', 2, 2.00, 'active', 'wait', 1, '', 0, 0,
 99301, ',99301,', 1, 1, 'admin', NOW(), 'admin', NOW(), '', NULL, 'admin', NOW(), 'admin', 'admin', b'0'),
(99302, 1, 0, '', 0, '用户需求：按层级浏览需求', 'requirement', 'feature', 2, 2.00, 'active', 'wait', 1, '', 99301, 1,
 99301, ',99301,99302,', 2, 1, 'admin', NOW(), 'admin', NOW(), '', NULL, 'admin', NOW(), 'admin', 'admin', b'0'),
(99303, 1, 0, '', 0, '研发需求：实现需求分层树接口', 'story', 'feature', 3, 2.00, 'active', 'wait', 1, '', 99302, 1,
 99301, ',99301,99302,99303,', 3, 0, 'admin', NOW(), 'admin', NOW(), '', NULL, 'admin', NOW(), 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `product` = VALUES(`product`), `type` = VALUES(`type`),
    `version` = VALUES(`version`), `status` = VALUES(`status`), `estimate` = VALUES(`estimate`),
    `isParent` = VALUES(`isParent`), `parent` = VALUES(`parent`), `parentVersion` = VALUES(`parentVersion`),
    `root` = VALUES(`root`), `path` = VALUES(`path`), `grade` = VALUES(`grade`), `deleted` = b'0';

INSERT INTO `zt_storyspec` (`story`, `version`, `title`, `spec`, `verify`, `creator`, `updater`, `deleted`)
VALUES
(99301, 1, '业务需求：需求池支撑多层级管理', '业务需求只描述「为什么做」，不直接落到开发。', '能向下拆分出用户需求。', 'admin', 'admin', b'0'),
(99302, 1, '用户需求：按层级浏览需求', '用户需求描述「用户要什么」，可以再拆成研发需求。', '能在需求池按层级查看。', 'admin', 'admin', b'0'),
(99303, 1, '研发需求：实现需求分层树接口', '研发需求是可直接排期开发的粒度。', '接口返回三层嵌套结构。', 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `spec` = VALUES(`spec`), `verify` = VALUES(`verify`), `deleted` = b'0';

-- 父需求的聚合值（父的 estimate = 直接子需求之和，与 refreshParent 的算法一致）
UPDATE `zt_story` SET `isParent` = 1, `estimate` = 2.00 WHERE `id` IN (99301, 99302);

-- ============================ 3. 任务 ============================
-- 演示任务 1：预计 8 小时、消耗 7、剩余 1（与 zt_effort 的两条工时对得上）、已完成并关闭
INSERT INTO `zt_task` (`id`, `project`, `execution`, `module`, `story`, `storyVersion`, `fromBug`,
                       `name`, `type`, `pri`, `estimate`, `consumed`, `left`,
                       `status`, `openedBy`, `openedDate`, `assignedTo`, `assignedDate`,
                       `estStarted`, `realStarted`, `finishedBy`, `finishedDate`,
                       `closedBy`, `closedDate`, `closedReason`, `creator`, `updater`, `deleted`)
VALUES
(1, 1, 90001, 0, 1, 2, 0,
 '实现登录接口', 'devel', 3, 8.00, 7.00, 1.00,
 'closed', 'admin', NOW(), 'admin', NOW(),
 '2026-02-01', '2026-02-01 09:00:00', 'admin', NOW(),
 'admin', NOW(), 'done', 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `project` = VALUES(`project`), `execution` = VALUES(`execution`), `story` = VALUES(`story`),
    `storyVersion` = VALUES(`storyVersion`), `name` = VALUES(`name`), `estimate` = VALUES(`estimate`),
    `consumed` = VALUES(`consumed`), `left` = VALUES(`left`), `status` = VALUES(`status`),
    `deleted` = b'0';

-- 32-zt_effort.sql 里那句「让演示任务与工时对得上」当年也是空操作，这里补一次
UPDATE `zt_task` SET `consumed` = 7.00, `left` = 1.00 WHERE `id` = 1;

-- ============================ 4. 缺陷 ============================
INSERT INTO `zt_bug` (`id`, `product`, `project`, `execution`, `module`, `plan`, `story`, `task`,
                      `title`, `severity`, `pri`, `type`, `steps`, `status`, `confirmed`,
                      `openedBy`, `openedDate`, `openedBuild`, `assignedTo`, `assignedDate`,
                      `resolvedBy`, `resolution`, `resolvedBuild`, `resolvedDate`,
                      `closedBy`, `closedDate`, `creator`, `updater`, `deleted`)
VALUES
(1, 1, 1, 90001, 0, 0, 1, 1,
 '登录接口偶发 500', 3, 3, 'codeerror', '压测时偶发 500，日志里是空指针。', 'active', 0,
 'admin', NOW(), 'trunk', 'admin', NOW(),
 '', '', '', NULL,
 '', NULL, 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `product` = VALUES(`product`), `title` = VALUES(`title`), `story` = VALUES(`story`),
    `status` = VALUES(`status`), `assignedTo` = VALUES(`assignedTo`), `deleted` = b'0';

-- ============================ 5. 跨产品的需求 + 与测试单关联的缺陷 ============================
-- 需求 6 属于**产品 2**：测试用例模块要验「关联其它产品的需求 → 拒绝」，靠它当反例
INSERT INTO `zt_story` (`id`, `product`, `module`, `plan`, `branch`, `title`, `type`, `category`, `pri`,
                        `estimate`, `status`, `stage`, `version`, `source`, `parent`, `parentVersion`,
                        `root`, `path`, `grade`, `isParent`, `openedBy`, `openedDate`, `assignedTo`, `assignedDate`,
                        `lastEditedBy`, `lastEditedDate`, `creator`, `updater`, `deleted`)
VALUES
(6, 2, 0, '', 0, '数据治理需求样例', 'story', 'feature', 3, 2.00, 'active', 'wait', 1, '', 0, 1,
 6, ',6,', 1, 0, 'admin', NOW(), 'admin', NOW(), 'admin', NOW(), 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `product` = VALUES(`product`), `status` = VALUES(`status`), `deleted` = b'0';

INSERT INTO `zt_storyspec` (`story`, `version`, `title`, `spec`, `verify`, `creator`, `updater`, `deleted`)
VALUES (6, 1, '数据治理需求样例', '另一个产品下的需求，用来做跨产品校验的反例。', '不允许挂到别的产品的用例上。', 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `deleted` = b'0';

-- 缺陷与测试单的关联：测试报告的「涉及缺陷」是按 zt_bug.testtask 统计的
-- （bugMapper.selectListByTestTask，按 id 倒序 → 141,1）。
-- 141 是当年在远端由「用例执行失败建缺陷」临时造出来的，从来没进过脚本 —— 这里补上。
INSERT INTO `zt_bug` (`id`, `product`, `project`, `execution`, `module`, `plan`, `story`, `task`,
                      `case`, `caseVersion`, `testtask`,
                      `title`, `severity`, `pri`, `type`, `steps`, `status`, `confirmed`,
                      `openedBy`, `openedDate`, `openedBuild`, `assignedTo`, `assignedDate`,
                      `resolvedBy`, `resolution`, `resolvedBuild`, `resolvedDate`,
                      `closedBy`, `closedDate`, `creator`, `updater`, `deleted`)
VALUES
(141, 1, 1, 90001, 0, 0, 1, 0,
 93101, 1, 94101,
 '用例执行失败：登录后跳转异常', 3, 2, 'codeerror', '执行用例时复现。', 'active', 0,
 'admin', NOW(), 'trunk', 'admin', NOW(),
 '', '', '', NULL,
 '', NULL, 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `product` = VALUES(`product`), `title` = VALUES(`title`), `testtask` = VALUES(`testtask`),
    `case` = VALUES(`case`), `status` = VALUES(`status`), `deleted` = b'0';

-- 演示缺陷 1 也挂在同一张测试单下（报告期望的缺陷串是 "141,1"）
UPDATE `zt_bug` SET `testtask` = 94101 WHERE `id` = 1;

-- ============================ 6. 演示用例的「需求版本冻结」状态 ============================
-- 用例 93103 关联需求 1：storyVersion 要**冻结在 1**（需求已是 v2）才会进「待确认」列表；
-- 而 test-testcase-module.sh 会走一遍「确认需求变更」把它追平到 2 —— **测试会消耗这个状态**。
-- 所以这里显式重置，让本脚本（resume-verification.sh 每次也会重灌）把演示状态恢复，测试才能重复执行。
-- 注意只能有一条「待确认」：93101/93102 保持已追平（storyVersion=2），
-- 否则 `page?needConfirm=true` 的总数断言（期望 1）就挂了。
UPDATE `zt_case` SET `storyVersion` = 2 WHERE `id` = 93101;
UPDATE `zt_case` SET `storyVersion` = 1 WHERE `id` = 93103;

-- ============================ 7. 联动回填 ============================
-- 项目的团队人数以 zt_team 为准（35-zt_team.sql 里那句 UPDATE 当年同样是空操作）
UPDATE `zt_project` p
SET p.`teamCount` = (SELECT COUNT(1) FROM `zt_team` t WHERE t.`root` = p.`id` AND t.`type` = 'project'),
    p.`team` = COALESCE((SELECT GROUP_CONCAT(t.`account` ORDER BY t.`order`, t.`id`)
                         FROM `zt_team` t WHERE t.`root` = p.`id` AND t.`type` = 'project'), '')
WHERE p.`type` = 'project';

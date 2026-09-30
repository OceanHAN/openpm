-- ---------------------------------------------------------------------------
-- 项目集（program）—— 补齐 zt_project 的**第三种角色**
--
-- 【重要发现】禅道 config/zentaopms.php 里有三个常量指向同一张表：
--     define('TABLE_PROGRAM',   '`zt_project`');   // 项目集
--     define('TABLE_PROJECT',   '`zt_project`');   // 项目
--     define('TABLE_EXECUTION', '`zt_project`');   // 执行（迭代/阶段/看板）
-- 也就是说：**项目集、项目、执行是同一张表里的三种 type**，不是三张表。
-- 本项目之前只实现了后两种，这一轮补齐项目集：
--     type = 'program'                     → 项目集
--     type = 'project'                     → 项目
--     type IN ('sprint','stage','kanban')  → 执行
--
-- 【三者的层级关系】
--   项目集 ──parent──> 上级项目集（项目集可以套项目集）
--   项目   ──parent──> 所属项目集（禅道 module/project/model.php: $program = getByID($project->parent)）
--   执行   ──project─> 所属项目（执行不在项目集树里）
--
-- 【path / grade 的口径纠正】
--   上一轮我把项目的 path 写成了斜杠格式 `/1/2/`（想当然类比 zt_module 的 path 规则），
--   实际禅道用的是**逗号格式**且 grade 从 1 开始（module/program/model.php#setTreePath）：
--       顶级：path = ',1,'      grade = 1
--       下级：path = 父.path + id + ','   grade = 父.grade + 1
--   本脚本把已有数据的 path/grade 一并纠正过来。
--
-- 【产品与项目集】zt_product.program = 产品所属项目集（0 = 独立产品）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 演示数据 ============================
-- 两个项目集（9002 挂在 9001 下，用来展示项目集套项目集）
INSERT INTO `zt_project` (`id`, `project`, `parent`, `type`, `name`, `code`, `model`, `status`, `PM`,
                          `budget`, `budgetUnit`, `begin`, `end`, `realBegan`, `pri`, `estimate`, `left`, `consumed`,
                          `progress`, `percent`, `teamCount`, `hasProduct`, `multiple`, `isTpl`, `milestone`, `acl`, `order`,
                          `openedBy`, `openedDate`, `creator`, `updater`)
VALUES
(9001, 0, 0,    'program', '禅道迁移项目集', 'ZENTAO-PGM', '', 'doing', 'admin',
 1000000.00, 'CNY', '2026-01-01', '2026-12-31', '2026-01-01', 1, 0.00, 0.00, 0.00,
 0.00, 0.00, 2, 1, 1, 0, 0, 'open', 45005, 'admin', '2026-01-01 09:00:00', 'admin', 'admin'),
(9002, 0, 9001, 'program', '数据治理项目集', 'DG-PGM',      '', 'wait',  'admin',
  300000.00, 'CNY', '2026-03-01', '2026-09-30', NULL,         2, 0.00, 0.00, 0.00,
 0.00, 0.00, 1, 1, 1, 0, 0, 'open', 45010, 'admin', '2026-03-01 09:00:00', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `parent` = VALUES(`parent`), `type` = VALUES(`type`), `name` = VALUES(`name`),
    `status` = VALUES(`status`), `PM` = VALUES(`PM`), `begin` = VALUES(`begin`), `end` = VALUES(`end`),
    `order` = VALUES(`order`), `deleted` = b'0';

-- 演示项目挂到项目集下（项目 2「子项目A」在禅道语义里不是「项目套项目」，
-- 而是**同一个项目集下的另一个项目**，所以它的 parent 也指向项目集）
UPDATE `zt_project` SET `parent` = 9001 WHERE `id` IN (1, 2) AND `type` = 'project';

-- 演示产品归属：产品 1 挂 9001，产品 2 挂 9002，产品 3 保持独立（program=0）
UPDATE `zt_product` SET `program` = 9001 WHERE `id` = 1;
UPDATE `zt_product` SET `program` = 9002 WHERE `id` = 2;
UPDATE `zt_product` SET `program` = 0    WHERE `id` = 3;

-- ============================ path / grade 纠偏 ============================
-- 1) 顶级（项目集或没有所属项目集的项目）：path=,id, grade=1
UPDATE `zt_project`
SET `path` = CONCAT(',', `id`, ','), `grade` = 1
WHERE `type` IN ('program', 'project') AND (`parent` IS NULL OR `parent` = 0);

-- 2) 有父节点的项目集/项目：path=父.path+id+','，grade=父.grade+1
--    （MySQL 8 的 UPDATE ... JOIN 可以一次算完，这里只处理一层：
--     演示数据只有一个下级项目集，真实数据的层级深度由应用层维护）
UPDATE `zt_project` c
JOIN `zt_project` p ON p.`id` = c.`parent` AND p.`deleted` = 0
SET c.`path` = CONCAT(p.`path`, c.`id`, ','), c.`grade` = p.`grade` + 1
WHERE c.`parent` > 0 AND c.`type` IN ('program', 'project');

-- 3) 执行有自己的 setTreePath（module/execution/model.php:5004）：
--    parent=所属项目、path=,项目,执行,、grade=1（见 35-zt_team.sql 里的同款纠正）
UPDATE `zt_project`
SET `parent` = `project`, `path` = CONCAT(',', `project`, ',', `id`, ','), `grade` = 1
WHERE `type` IN ('sprint', 'stage', 'kanban') AND `project` > 0
  AND (`path` <> CONCAT(',', `project`, ',', `id`, ',') OR `grade` <> 1);

-- ============================ 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90101, '项目集', 'zentao:program:query',  2, 0, 90001, 'program', 'ep:collection', 'zentao/program/index', 'ZentaoProgram', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90102, '项目集查询', 'zentao:program:query',  3, 1, 90101, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90103, '项目集新建', 'zentao:program:create', 3, 2, 90101, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90104, '项目集修改', 'zentao:program:update', 3, 3, 90101, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90105, '项目集删除', 'zentao:program:delete', 3, 4, 90101, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`), `sort` = VALUES(`sort`);

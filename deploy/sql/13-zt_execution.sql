-- ---------------------------------------------------------------------------
-- 执行（execution）模块
--
-- 【关键架构发现】禅道没有独立的执行表：
--     define('TABLE_EXECUTION', '`' . $config->db->prefix . 'project`');
--   执行和项目**共用 zt_project 一张表**，靠 type 字段区分：
--     type = 'project'                    → 项目
--     type IN ('sprint','stage','kanban') → 执行（迭代 / 阶段 / 看板）
--   执行通过 where('project')->eq($projectID) 归属到项目。
--
--   这样做的好处是任务(task)只需一个 execution 外键就能同时挂到项目和执行上，
--   权限、工时、团队等逻辑也全部复用。代价是查询必须永远带上 type 过滤，
--   漏了就会把项目当成执行查出来。
--
-- 本迁移：
--   1. 补 `project` 列（执行所属项目）
--   2. 把已有项目的 type 回填为 'project'
--   3. 加 (project, type) 联合索引——这是执行列表的主查询路径
--   4. 插入执行菜单与权限
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- 1. 补 project 列
ALTER TABLE `zt_project`
    ADD COLUMN `project` bigint NOT NULL DEFAULT 0 COMMENT '所属项目（执行专用；项目自身为 0）' AFTER `id`;

-- 2. 回填：已有的都是项目
UPDATE `zt_project` SET `type` = 'project' WHERE `type` = '' OR `type` IS NULL;

-- 3. 执行列表的主查询路径：(project, type)
ALTER TABLE `zt_project` ADD KEY `idx_project_type` (`project`, `type`);

-- 4. 菜单与权限
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90027, '执行管理', 'zentao:execution:query', 2, 2, 90022, 'execution', 'ep:refresh', 'zentao/execution/index', 'ZentaoExecution', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90028, '执行查询', 'zentao:execution:query',  3, 1, 90027, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90029, '执行创建', 'zentao:execution:create', 3, 2, 90027, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90030, '执行修改', 'zentao:execution:update', 3, 3, 90027, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90031, '执行删除', 'zentao:execution:delete', 3, 4, 90027, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

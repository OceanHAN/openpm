-- ---------------------------------------------------------------------------
-- 组织与权限（视图）的菜单与权限
--
-- 【重要】这个模块**没有新建任何禅道表**。
--   zt_user / zt_dept / zt_group / zt_grouppriv 不迁移，映射到 yudao 的 RBAC：
--     zt_user      → system_users
--     zt_dept      → system_dept
--     zt_group     → system_role
--     zt_grouppriv → system_role_menu + system_menu.permission
--   原因：yudao 的 RBAC 已覆盖「用户-角色-菜单-权限 + 数据范围」，再搬一套权限包
--   会长期双写，收益为负。本页只做**把 yudao 权限翻译回禅道视角**的只读展示，
--   供迁移评审逐条对照（接口 /zentao/organization/mapping 里有字段对照表）。
--
-- 因此这里只加菜单与一个查询权限，不加 create/update/delete。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90066, '组织与权限', 'zentao:organization:query', 2, 13, 90001, 'organization', 'ep:user', 'zentao/organization/index', 'ZentaoOrganization', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90067, '权限查询',   'zentao:organization:query', 3, 1, 90066, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

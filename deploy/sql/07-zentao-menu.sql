-- ---------------------------------------------------------------------------
-- 禅道模块的前端菜单与权限
--
-- yudao 的前端路由是「菜单驱动」的：permission.ts 启动时调后端 /system/auth/list-menus，
-- 用返回结果动态 addRoute。所以新增一个页面必须在这里插菜单，光写 .vue 文件不会生效。
--
-- 菜单类型 type：1=目录  2=菜单  3=按钮(权限)
-- 组件路径 component 相对 src/views/，不带 .vue 后缀。
--
-- 用显式 ID（90001+）避免和 yudao 自带菜单（当前 AUTO_INCREMENT≈12732）冲突，
-- ON DUPLICATE KEY UPDATE 保证可重复执行。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
-- 一级目录：禅道
(90001, '禅道', '', 1, 60, 0, '/zentao', 'ep:notebook', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
-- 二级菜单：需求管理
(90002, '需求管理', 'zentao:story:query', 2, 1, 90001, 'story', 'ep:document', 'zentao/story/index', 'ZentaoStory', 0, b'1', b'1', b'1', 'admin', 'admin'),
-- 按钮级权限
(90003, '需求查询', 'zentao:story:query',  3, 1, 90002, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90004, '需求创建', 'zentao:story:create', 3, 2, 90002, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90005, '需求修改', 'zentao:story:update', 3, 3, 90002, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90006, '需求删除', 'zentao:story:delete', 3, 4, 90002, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name`       = VALUES(`name`),
    `permission` = VALUES(`permission`),
    `type`       = VALUES(`type`),
    `sort`       = VALUES(`sort`),
    `parent_id`  = VALUES(`parent_id`),
    `path`       = VALUES(`path`),
    `icon`       = VALUES(`icon`),
    `component`  = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

-- ---------------------------------------------------------------------------
-- 操作日志（action）的菜单与权限
--
-- 本文件不建表：zt_action / zt_history 早就在 09-zt_action.sql 里建好了，
-- 这一轮补的是「回收站 / 动态 / 备注」三个能力（README 3.36），它们需要自己的权限行。
--
-- 注意：前端按钮是 v-hasPermi 判定的，**没有 system_menu 行就没有权限串、按钮会被隐藏**
-- （坑位 #43），所以新能力一定要补菜单行；改完还要清一次 Redis 权限缓存。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90150, '回收站与动态', 'zentao:action:query',    2, 19, 90001, 'action', 'ep:delete', 'zentao/action/index', 'ZentaoAction', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90151, '日志查询',     'zentao:action:query',    3, 1, 90150, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90152, '备注',         'zentao:action:comment',  3, 2, 90150, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90153, '还原隐藏',     'zentao:action:undelete', 3, 3, 90150, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

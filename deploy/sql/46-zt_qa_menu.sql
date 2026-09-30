-- ---------------------------------------------------------------------------
-- 测试仪表盘（qa）的菜单与权限
--
-- 本文件不建表：qa 是「仪表盘页面」，它读的全是已经迁移的表
-- （zt_bug / zt_case / zt_testtask / zt_product），所以只补菜单行。
--
-- 注意：前端按钮/菜单是 v-hasPermi + 动态路由判定的，**没有 system_menu 行就没有权限串**
-- （坑位 #43）；改完要清一次 Redis 权限缓存。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90157, '测试仪表盘', 'zentao:qa:query', 2, 21, 90001, 'qa', 'ep:data-line', 'zentao/qa/index', 'ZentaoQa', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90158, '仪表盘查询', 'zentao:qa:query', 3, 1, 90157, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

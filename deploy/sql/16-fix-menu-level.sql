-- ---------------------------------------------------------------------------
-- 菜单层级修正：执行管理 / 分支管理 改为禅道一级菜单
--
-- 【问题】yudao 的动态路由规则（src/utils/routerHelper.ts 的 generateRoute）里，
--   只要一个菜单**存在子菜单**，它自己就被当成「目录」：
--     data.component = Layout / getParentLayout()
--   菜单自身的 component（也就是页面组件）会被**丢弃**。
--   只有「唯一子菜单与父菜单同名且带组件」这一种情况才会折叠回父级。
--
--   我一开始按禅道界面的直觉，把「执行管理」挂在「项目管理」下、「分支管理」挂在
--   「产品管理」下。于是：
--     /zentao/project  → 变成目录，项目页面不再渲染（点进去是空白）
--     /zentao/product  → 同样变成目录，产品页面不再渲染
--   而 /zentao/project/execution、/zentao/product/branch 能打开，
--   很容易误判为「只是产品/项目页还没做」。
--
-- 【修正】把这两个子菜单提升为禅道（90001）下的一级菜单，
--   与 产品/需求/项目/任务/缺陷/模块 保持一致的**扁平一层**结构：
--     产品 /zentao/product        需求 /zentao/story
--     项目 /zentao/project        执行 /zentao/execution
--     任务 /zentao/task           缺陷 /zentao/bug
--     模块 /zentao/module         分支 /zentao/branch
--   这样每个页面菜单都是叶子节点，不会再被当成目录。
--
-- 【经验】在 yudao 里，「有自己页面的菜单」不能有子菜单；
--   要分组就用「目录(type=1) + 子菜单」，不要用「页面 + 子菜单」。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- 1. 提升为一级菜单
UPDATE `system_menu` SET `parent_id` = 90001 WHERE `id` IN (90027, 90032);

-- 2. 重排禅道下的菜单顺序，避免 sort 相同导致顺序随机
UPDATE `system_menu` SET `sort` = 0 WHERE `id` = 90017; -- 产品管理
UPDATE `system_menu` SET `sort` = 1 WHERE `id` = 90002; -- 需求管理
UPDATE `system_menu` SET `sort` = 2 WHERE `id` = 90022; -- 项目管理
UPDATE `system_menu` SET `sort` = 3 WHERE `id` = 90027; -- 执行管理
UPDATE `system_menu` SET `sort` = 4 WHERE `id` = 90007; -- 任务管理
UPDATE `system_menu` SET `sort` = 5 WHERE `id` = 90012; -- 缺陷管理
UPDATE `system_menu` SET `sort` = 6 WHERE `id` = 90037; -- 模块维护
UPDATE `system_menu` SET `sort` = 7 WHERE `id` = 90032; -- 分支管理

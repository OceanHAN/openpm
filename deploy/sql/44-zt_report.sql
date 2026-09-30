-- ---------------------------------------------------------------------------
-- 报表（report）的菜单与权限，外加两处**主干链路口径纠正**
--
-- 一、菜单：报表本身不建表（README 3.38），但前端按钮是 v-hasPermi 判定的，
--     **没有 system_menu 行就没有权限串、按钮会被隐藏**（坑位 #43）。
--     改完要清一次 Redis 权限缓存。
--
-- 二、口径纠正（这一轮为了报表能算对，顺带修的两个历史问题）：
--     1. 执行的动作 objectType 应该是 'execution'，不是 'project'。
--        禅道 demo 数据里，执行的动作写成 ('execution', 3, execution=3)、
--        项目的动作写成 ('project', 2, project=2)，两者分开；否则报表按 objectType
--        统计贡献/产出时项目与执行会混在一起，回收站里「执行」也会显示成项目。
--        代码已改（ExecutionServiceImpl），这里把历史数据一起搬过去。
--     2. 执行行的 multiple 必须跟所属项目一致（multiple=1 = 多迭代项目下的迭代）。
--        禅道建执行时沿用项目的 multiple；本实现在这一轮之前没有继承，导致
--        「项目是多迭代、执行却一个都统计不到」（报表的年度执行统计、执行列表默认过滤都靠它）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ---- 1. 历史数据：执行的动作改到 execution 这个 objectType 下 ----
-- 只搬「objectID 确实是一条执行（sprint/stage/kanban）」的行，项目的动作不动。
UPDATE `zt_action` a
JOIN `zt_project` p ON p.id = a.objectID
SET a.objectType = 'execution',
    a.`execution` = a.objectID
WHERE a.objectType = 'project'
  AND p.type IN ('sprint', 'stage', 'kanban');

-- ---- 2. 执行行的 multiple 跟所属项目对齐 ----
UPDATE `zt_project` e
JOIN `zt_project` p ON p.id = e.project
SET e.multiple = p.multiple
WHERE e.type IN ('sprint', 'stage', 'kanban')
  AND e.deleted = 0
  AND (e.multiple IS NULL OR e.multiple <> p.multiple);

-- ---- 3. 报表的菜单与权限 ----
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90154, '报表',   'zentao:report:query', 2, 20, 90001, 'report', 'ep:data-analysis', 'zentao/report/index', 'ZentaoReport', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90155, '报表查询', 'zentao:report:query', 3, 1, 90154, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

-- ---- 4. 演示数据补齐：给本年「还活着」的需求/任务/缺陷补上创建动作 ----
-- 为什么需要：报表的「贡献 / 月度趋势」是按 **zt_action** 统计的（禅道也是），
-- 而本项目的演示数据是直接把对象 REPLACE 进业务表的、没有配套动作 ——
-- 于是年度数据里需求/任务全是 0，看着像报表没生效。
-- 禅道的 db/demo.sql 是给每个对象都配了动作行的，这里按同样思路补上，
-- 日期直接取对象自己的创建时间，保证落在正确的年份/月份上。
-- 只在「没有对应动作」时才插，重复执行安全。
INSERT INTO `zt_action` (`objectType`, `objectID`, `product`, `project`, `execution`, `actor`, `action`, `date`, `comment`, `extra`, `read`, `vision`, `efforted`, `creator`, `updater`)
SELECT CASE s.type WHEN 'requirement' THEN 'requirement' WHEN 'epic' THEN 'epic' ELSE 'story' END,
       s.id, s.product, 0, 0, s.openedBy, 'created', s.openedDate, '', '', 0, 'rnd', 0, 'admin', 'admin'
FROM `zt_story` s
WHERE s.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `zt_action` a
                  WHERE a.objectID = s.id AND a.objectType IN ('story', 'requirement', 'epic') AND a.action = 'created');

INSERT INTO `zt_action` (`objectType`, `objectID`, `product`, `project`, `execution`, `actor`, `action`, `date`, `comment`, `extra`, `read`, `vision`, `efforted`, `creator`, `updater`)
SELECT 'task', t.id, 0, t.project, t.execution, t.openedBy, 'created', t.openedDate, '', '', 0, 'rnd', 0, 'admin', 'admin'
FROM `zt_task` t
WHERE t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `zt_action` a
                  WHERE a.objectID = t.id AND a.objectType = 'task' AND a.action = 'created');

INSERT INTO `zt_action` (`objectType`, `objectID`, `product`, `project`, `execution`, `actor`, `action`, `date`, `comment`, `extra`, `read`, `vision`, `efforted`, `creator`, `updater`)
SELECT 'bug', b.id, b.product, b.project, b.execution, b.openedBy, 'created', b.openedDate, '', '', 0, 'rnd', 0, 'admin', 'admin'
FROM `zt_bug` b
WHERE b.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `zt_action` a
                  WHERE a.objectID = b.id AND a.objectType = 'bug' AND a.action = 'created');

-- ---------------------------------------------------------------------------
-- 项目/执行团队（zt_team）—— 主干链路的「谁」
--
-- 【禅道语义】zt_team 是**项目与执行的成员表**（同一张表用 type 区分）：
--     type='project'   → root 是项目编号
--     type='execution' → root 是执行编号
-- 一条记录 = 某人在这个项目/执行里的角色、加入日期、每天工时与可用天数。
--
-- 【为什么这一轮要补它】上一轮（3.24）把项目层级的 path/parent 纠正成禅道口径之后，
-- 团队这一块还留着一个明显的偏差：项目上的 teamCount（团队人数）我原来是拿
-- zt_project.team 这个**逗号字符串**推导出来的，而禅道是从 zt_team 统计的
-- （module/project/tao.php#fetchMemberCountByIdList → project/model.php:363）。
-- 字符串推导出来的数字和真实成员表对不上，而且没法回答「谁在这个项目里、什么角色、
-- 每天投入几小时」——而这三件事正是工时、权限、负载统计的基础。
--
-- 【关键列】
--   days  该成员在这个项目里的可用天数
--   hours 每天投入小时数（禅道默认 7.0，见 config/execution.php: defaultWorkhours）
--        days * hours = 这个人在这个项目里的**可用工时**（禅道 project/model.php:556 就是这么算的）
--   limited yes/no：受限访问（禅道的访问控制用）
--   join  加入日期（重新保存团队时，老成员的 join 要保留）
--   role  成员角色（项目里是「项目经理/产品经理/测试/研发…」；执行里由 ownerFields 转成角色名）
--
-- 【表结构上的两个决定】
--   1. 不加 deleted 列：成员是「在/不在」的关系数据，禅道对它是**物理删除**
--      （updateTeamMembers 先 delete 再 insert）。如果用逻辑删除，
--      重新添加同一个人会撞 UNIQUE(root,type,account) —— 这正是坑位 #4 的同款问题。
--   2. 保留 UNIQUE(root,type,account)：一个人在一个项目里只能有一条成员记录。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_team` (
  `id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '成员编号',
  `root`      bigint       NOT NULL DEFAULT 0 COMMENT '所属对象编号（项目或执行）',
  `type`      varchar(15)  NOT NULL DEFAULT 'project' COMMENT 'project 项目 / execution 执行',
  `account`   varchar(64)  NOT NULL DEFAULT '' COMMENT '成员账号',
  `role`      varchar(64)  NOT NULL DEFAULT '' COMMENT '在本项目/执行里的角色',
  `position`  varchar(64)  NOT NULL DEFAULT '' COMMENT '岗位',
  `limited`   varchar(5)   NOT NULL DEFAULT 'no' COMMENT '是否受限访问 yes/no',
  `join`      date         NULL COMMENT '加入日期（join 是 MySQL 关键字）',
  `days`      int          NOT NULL DEFAULT 0 COMMENT '可用天数',
  `hours`     decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '每天投入小时数（禅道默认 7.0）',
  `estimate`  decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '预计工时',
  `consumed`  decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '已消耗工时',
  `left`      decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '剩余工时',
  `order`     int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 关键字）',
  `create_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`   varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`   varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_root_type_account` (`root`, `type`, `account`),
  KEY `idx_account` (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目/执行团队成员表（无 deleted 列，成员变更即物理增删）';

-- ============================ 演示数据 ============================
-- 项目 1（禅道迁移一期）：admin 项目经理、tester 测试、dev1 研发
-- 执行 90001（V1.0 迭代）：admin 项目经理、dev1 研发（执行的成员来自 ownerFields）
-- 可用工时 = days * hours，这里让演示数据算得出来：
--   admin  20 天 * 7h = 140h
--   tester 10 天 * 4h = 40h
--   dev1   15 天 * 7h = 105h
INSERT INTO `zt_team` (`id`, `root`, `type`, `account`, `role`, `position`, `limited`, `join`, `days`, `hours`, `estimate`, `consumed`, `left`, `order`, `creator`, `updater`)
VALUES
(98101, 1,     'project',   'admin',  '项目经理', '', 'no',  '2026-01-01', 20, 7.00, 0.00, 0.00, 0.00, 0, 'admin', 'admin'),
(98102, 1,     'project',   'tester', '测试',     '', 'no',  '2026-02-01', 10, 4.00, 0.00, 0.00, 0.00, 1, 'admin', 'admin'),
(98103, 1,     'project',   'dev1',   '研发',     '', 'yes', '2026-02-15', 15, 7.00, 0.00, 0.00, 0.00, 2, 'admin', 'admin'),
(98104, 90001, 'execution', 'admin',  '项目经理', '', 'no',  '2026-03-01', 10, 7.00, 0.00, 0.00, 0.00, 0, 'admin', 'admin'),
(98105, 90001, 'execution', 'dev1',   '研发',     '', 'no',  '2026-03-01', 10, 7.00, 0.00, 0.00, 0.00, 1, 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `role` = VALUES(`role`), `limited` = VALUES(`limited`), `days` = VALUES(`days`),
    `hours` = VALUES(`hours`), `join` = VALUES(`join`);

-- 项目 1 的团队人数按成员表回填（禅道就是这么算的）
UPDATE `zt_project` SET `teamCount` = 3, `team` = 'admin,tester,dev1' WHERE `id` = 1;

-- ---------------------------------------------------------------------------
-- 顺带修正：执行的层级口径（上一轮写成 path='' 是错的）
--
-- 上一轮做项目集时，我看 program/model.php#setTreePath 只处理 program/project，
-- 就推断「执行不在树里，所以 path 留空、grade 归零」。但执行模块**有自己的**
-- setTreePath（module/execution/model.php:5004）：
--     parent = 所属项目（嵌套阶段时是父阶段）
--     path   = ,项目id,执行id,      grade = 1
-- 禅道自己的 demo 数据也是这个形状：
--     (3, project=2, parent=2, path=',2,3,', grade=1, type='sprint')
-- 所以这里把已有执行的 parent/path/grade 纠正过来。
-- 修正范围：直接挂在项目下的执行（parent=0 或 parent 指向项目）。
-- ---------------------------------------------------------------------------
-- 1) 直接挂在项目下的执行：parent=所属项目、path=,项目,执行,、grade=1
UPDATE `zt_project`
SET `parent` = `project`, `path` = CONCAT(',', `project`, ',', `id`, ','), `grade` = 1
WHERE `type` IN ('sprint', 'stage', 'kanban')
  AND `project` > 0
  AND (`parent` = 0 OR `parent` = `project`)
  AND (`path` <> CONCAT(',', `project`, ',', `id`, ',') OR `grade` <> 1);

-- 2) 嵌套阶段（parent 指向另一个阶段）：path = 父.path + id + ','、grade = 父.grade + 1
UPDATE `zt_project` c
JOIN `zt_project` p ON p.`id` = c.`parent` AND p.`type` IN ('stage', 'sprint', 'kanban') AND p.`deleted` = 0
SET c.`path` = CONCAT(p.`path`, c.`id`, ','), c.`grade` = p.`grade` + 1
WHERE c.`type` IN ('sprint', 'stage', 'kanban') AND c.`parent` > 0 AND c.`parent` <> c.`project`;

-- 3) 兜底：拿不到所属项目的执行（脏数据）保持原样，但要能看出来
SELECT id, project, parent, path, grade, type, name FROM `zt_project`
WHERE `type` IN ('sprint', 'stage', 'kanban') AND `deleted` = 0 ORDER BY id;

-- ============================ 菜单与权限 ============================
-- 团队没有独立页面（它是项目/执行列表里的一个抽屉），但**权限点仍然要建**：
-- yudao 的前端指令 `v-hasPermi` 是拿「角色→菜单」算出来的权限串来判定的，
-- 没有对应的 system_menu 行 → 按钮直接被隐藏（后端接口因为是超管硬编码放行所以照样能调），
-- 表现就是「接口能过、页面上的按钮不见了」。这一条是浏览器检查才发现的。
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90111, '团队查询', 'zentao:team:query',  3, 20, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90112, '团队维护', 'zentao:team:update', 3, 21, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`);

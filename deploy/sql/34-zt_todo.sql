-- ---------------------------------------------------------------------------
-- 待办（todo）+ 我的地盘（my）的演示数据
--
-- 【禅道语义】zt_todo 是**个人**的任务清单，和 zt_task 完全不是一回事：
--   task  = 项目里要交付的工作（有工时、有执行、有需求）
--   todo  = 「我今天要干的几件事」，可以完全不挂在任何项目上（type='custom'），
--           也可以指向一个已有对象（type='task'/'bug'/'story'/'testtask' + objectID）当快捷入口。
-- 所以它属于「协作-个人」类，是「我的地盘」的骨架数据。
--
-- 【status】wait → doing → done，另外可以 closed（关闭后 assignedTo 会被置成伪用户 'closed'）；
--   激活（activate）把状态退回 wait，并从 finishedBy 恢复指派人（禅道 module/todo/model.php#activate）。
--
-- 【date / begin / end】date 是「哪天做」，begin/end 是「几点到几点」（四位 HHMM）。
--   三个都是关键字：date 是 MySQL 关键字，begin/end 是 JSqlParser 保留字，必须加反引号。
--
-- 【cycle】周期待办（cycle=1）禅道会按 config 生成下一次，本实现只存字段不生成（见 README 已知限制）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_todo` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '待办编号',
  `account`       varchar(64)  NOT NULL DEFAULT '' COMMENT '归属账号（谁的待办）',
  `date`          date         NULL COMMENT '哪天做（date 是 MySQL 关键字）',
  `begin`         char(4)      NOT NULL DEFAULT '' COMMENT '开始时间 HHMM（begin 是 JSqlParser 保留字）',
  `end`           char(4)      NOT NULL DEFAULT '' COMMENT '结束时间 HHMM（end 是 JSqlParser 保留字）',
  `feedback`      bigint       NOT NULL DEFAULT 0 COMMENT '关联反馈（禅道原字段，本实现不用）',
  `type`          varchar(15)  NOT NULL DEFAULT 'custom' COMMENT 'custom/cycle/bug/task/story/testtask',
  `cycle`         tinyint      NOT NULL DEFAULT 0 COMMENT '是否周期待办',
  `objectID`      bigint       NOT NULL DEFAULT 0 COMMENT '关联对象编号（type!=custom 时有效）',
  `pri`           tinyint      NOT NULL DEFAULT 3 COMMENT '优先级 1~4',
  `name`          varchar(150) NOT NULL DEFAULT '' COMMENT '待办名称',
  `desc`          mediumtext   DEFAULT NULL COMMENT '描述（desc 是 MySQL 关键字）',
  `status`        varchar(10)  NOT NULL DEFAULT 'wait' COMMENT 'wait/doing/done/closed',
  `private`       tinyint      NOT NULL DEFAULT 0 COMMENT '是否私有（只有自己可见）',
  `config`        varchar(1000) NOT NULL DEFAULT '' COMMENT '周期配置（禅道原字段）',
  `assignedTo`    varchar(64)  NOT NULL DEFAULT '' COMMENT '指派给',
  `assignedBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '指派人',
  `assignedDate`  datetime     DEFAULT NULL COMMENT '指派时间',
  `finishedBy`    varchar(64)  NOT NULL DEFAULT '' COMMENT '完成人',
  `finishedDate`  datetime     DEFAULT NULL COMMENT '完成时间',
  `closedBy`      varchar(64)  NOT NULL DEFAULT '' COMMENT '关闭人',
  `closedDate`    datetime     DEFAULT NULL COMMENT '关闭时间',
  `vision`        varchar(10)  NOT NULL DEFAULT 'rnd' COMMENT '视野',
  -- yudao BaseDO 约定字段
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`       varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`       varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_account_date` (`account`, `date`, `deleted`),
  KEY `idx_status` (`status`),
  KEY `idx_object` (`type`, `objectID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待办表';

-- ============================ 演示数据 ============================
-- 今天的、明天的、已过期的、已完成的、已关闭的、别人指派给我的 —— 每种状态各来一条，
-- 「我的地盘」首页和过滤器才有东西可看
INSERT INTO `zt_todo` (`id`, `account`, `date`, `begin`, `end`, `type`, `objectID`, `pri`, `name`, `desc`,
                       `status`, `private`, `assignedTo`, `assignedBy`, `assignedDate`,
                       `finishedBy`, `finishedDate`, `closedBy`, `closedDate`, `vision`, `creator`, `updater`)
VALUES
(97101, 'admin', CURDATE(),                          '0900', '1000', 'custom', 0, 1, '评审需求变更', '禅道迁移一期 v2 的需求变更需要过一遍评审', 'wait',  0, 'admin',  'admin',  NOW(), '', NULL, '', NULL, 'rnd', 'admin', 'admin'),
(97102, 'admin', CURDATE(),                          '1400', '1500', 'custom', 0, 2, '写工时明细的迁移说明', NULL, 'doing', 0, 'admin',  'admin',  NOW(), '', NULL, '', NULL, 'rnd', 'admin', 'admin'),
(97103, 'admin', DATE_ADD(CURDATE(), INTERVAL 1 DAY),'0930', '1100', 'task',   1, 3, '看一下登录接口的实现', '关联任务 1', 'wait', 0, 'admin', 'tester', NOW(), '', NULL, '', NULL, 'rnd', 'tester', 'tester'),
(97104, 'admin', DATE_SUB(CURDATE(), INTERVAL 2 DAY),'1000', '1200', 'custom', 0, 2, '补上遗漏的回归脚本', '已经过期但还没做，用来验证「今日待办」的过期提示', 'wait', 0, 'admin', 'admin', NOW(), '', NULL, '', NULL, 'rnd', 'admin', 'admin'),
(97105, 'admin', DATE_SUB(CURDATE(), INTERVAL 1 DAY),'1500', '1600', 'bug',    1, 4, '确认缺陷 1 是否已修复', NULL, 'done',  0, 'admin', 'admin', NOW(), 'admin', NOW(), '', NULL, 'rnd', 'admin', 'admin'),
(97106, 'admin', DATE_SUB(CURDATE(), INTERVAL 3 DAY),'0800', '0900', 'custom', 0, 4, '整理上周的会议纪要', NULL, 'closed', 0, 'closed', 'admin', NOW(), '', NULL, 'admin', NOW(), 'rnd', 'admin', 'admin'),
(97107, 'admin', CURDATE(),                          '1600', '1700', 'custom', 0, 1, '私事：取快递', '私有待办只有自己列表里能看到', 'wait', 1, 'admin', 'admin', NOW(), '', NULL, '', NULL, 'rnd', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `date` = VALUES(`date`), `name` = VALUES(`name`), `status` = VALUES(`status`),
    `assignedTo` = VALUES(`assignedTo`), `private` = VALUES(`private`), `deleted` = b'0';

-- ============================ 菜单与权限 ============================
-- 禅道里「我的地盘」是首页级入口，这里同样做成禅道菜单下的第一个扁平页面
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90106, '我的地盘', 'zentao:my:query',      2, 0, 90001, 'my', 'ep:home-filled', 'zentao/my/index', 'ZentaoMy', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90107, '我的地盘查询', 'zentao:my:query',    3, 1, 90106, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90108, '待办新建', 'zentao:todo:create',  3, 2, 90106, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90109, '待办修改', 'zentao:todo:update',  3, 3, 90106, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90110, '待办删除', 'zentao:todo:delete',  3, 4, 90106, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`), `sort` = VALUES(`sort`);

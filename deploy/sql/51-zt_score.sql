-- ---------------------------------------------------------------------------
-- 积分（score）：规则驱动的计分器
--
-- 禅道语义（module/score：control 42 行 + model 374 行，config.php 里是**规则表**）：
--   1. 规则 = (module, method, times, hour, score)[+扩展加成]，全部照抄进 ScoreRules.java：
--        user.login      = 3 次 / 24 小时 / 1 分      （一天最多 3 分）
--        task.finish     = 不限次 / 1 分 + 优先级加成(p1+2,p2+1) + round(预计/10)
--        bug.confirm     = 1 分 + 严重程度加成(s1+3,s2+2,s3+1)，**分给提单人**
--        story.close     = 1 分，另外给需求创建者 2 分
--        execution.close = PM 20 分 / 成员 5 分（按期或提前再加 10 / 5）
--        tutorial.finish = 1 次 / 100 分（新手教程）
--        …共 38 条，见模块里的「积分规则」页
--   2. 次数与时间窗：hour = 0 时按「全量历史」数条数；hour > 0 时按**当天**数（禅道实现如此）
--   3. 分值 0 不落库；命中上限也不落库（静默跳过）
--   4. 总分与等级：禅道把总分冗余在 zt_user.score / scoreLevel 上（saveScore 里顺手 UPDATE）
--
-- 本实现的一处**有意偏离**：不迁 zt_user（它是映射交付，见「组织权限」那一轮），
-- 所以总分 = SUM(zt_score.score)，before/after 在插入时按当时总分算快照落库。
-- 等级（scoreLevel）不迁。
--
-- 表结构照抄 db/zentao.sql：**没有 deleted 列**（积分流水只增不改），DO 也不继承 BaseDO。
-- 保留字：`desc`（MySQL 关键字）、`before`（BEFORE 是触发器关键字）都要加反引号。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_score` (
  `id`      bigint unsigned NOT NULL AUTO_INCREMENT,
  `account` varchar(30)  NOT NULL DEFAULT '' COMMENT '账号',
  `module`  varchar(30)  NOT NULL DEFAULT '' COMMENT '模块（task/story/bug/execution/user/ajax…）',
  `method`  varchar(30)  NOT NULL DEFAULT '' COMMENT '动作（create/finish/close/resolve/confirm/login…）',
  `desc`    varchar(250) NOT NULL DEFAULT '' COMMENT '描述。列名 desc 是关键字',
  `before`  int          NOT NULL DEFAULT 0 COMMENT '计分前总分（本实现按 SUM 快照）。列名 before 也是保留字',
  `score`   int          NOT NULL DEFAULT 0 COMMENT '本次得分',
  `after`   int          NOT NULL DEFAULT 0 COMMENT '计分后总分',
  `time`    datetime     DEFAULT NULL COMMENT '计分时间',
  PRIMARY KEY (`id`),
  KEY `idx_account` (`account`),
  KEY `idx_module_method` (`module`, `method`),
  KEY `idx_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水（禅道 zt_score，无 deleted 列）';

-- 演示数据：给 admin 几条不同模块的流水（幂等：先按 desc 前缀清掉）
DELETE FROM `zt_score` WHERE `desc` LIKE '演示%';
INSERT INTO `zt_score` (`account`, `module`, `method`, `desc`, `before`, `score`, `after`, `time`) VALUES
('admin', 'user',  'login',  '登录',            0,  1,  1, NOW() - INTERVAL 2 DAY),
('admin', 'task',  'finish', '完成任务ID:1',    1,  3,  4, NOW() - INTERVAL 1 DAY),
('admin', 'bug',   'resolve','解决BugID:1',     4,  2,  6, NOW() - INTERVAL 1 DAY),
('admin', 'story', 'close',  '需求关闭ID:1',    6,  1,  7, NOW() - INTERVAL 3 HOUR);

-- 菜单与权限：90180 页面 + 3 个按钮权限（type=3 的行不能省，否则 v-hasPermi 的按钮不显示，坑位 #43）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90180, '积分',     'zentao:score:query',  2, 26, 90001, 'score', 'ep:medal', 'zentao/score/index', 'ZentaoScore', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90181, '积分查询', 'zentao:score:query',  3, 1, 90180, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90182, '积分计分', 'zentao:score:create', 3, 2, 90180, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

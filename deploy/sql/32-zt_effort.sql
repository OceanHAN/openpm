-- ---------------------------------------------------------------------------
-- 工时明细（effort）—— 「谁在什么时间、为哪个任务、花了多少小时」
--
-- 【禅道语义】zt_effort 是一张**通用**的工时流水表：objectType/objectID 指向被记录的对象
-- （本实现只做 task，禅道还用在同一张表记需求/缺陷的工时）。
-- 一条记录包含：谁(account)、哪天(date)、花了多久(consumed)、**这之后还剩多久(left)**、
-- 干了什么(work)、起止时间(begin/end，'HHMM' 四位)。
--
-- 【为什么要有「剩余工时」这一列】
--   禅道的工时不是「加总」这么简单：
--     task.consumed = 所有工时记录之和（这个确实是加总）
--     task.left     = **最后一条工时记录里填的 left**（不是加总、也不是 estimate - consumed）
--   也就是「每报一次工时，同时声明一下现在还剩多少」——最后由最后一次声明说了算。
--   这样团队可以随时修正剩余量（比如发现比预想简单），而不用去改历史记录。
--
-- 【本实现的算法选择】
--   禅道是「增量维护」：加一条就 += ，删一条就 -= ，再为「删的是最后一条」「删到一条不剩」
--   等情况写一堆特判（getTaskAfterDeleteWorkhour 里全是 if/else）。
--   本实现改成**每次改动后从工时流水重新算一遍任务的 consumed/left/status**：
--   结果与禅道一致（consumed=SUM、left=最后一条的 left），但不会因为漏掉某个特判而算错。
--   工时记录本身不多（一个任务通常几条到几十条），重算的成本可以忽略。
--
-- 【状态联动】重算时顺带处理两件事（对齐禅道）：
--   left == 0 且状态还在 wait/doing/pause → 置为 done
--   left != 0 且状态已经是 done → 回到 doing
--
-- 【未做】多人任务(zt_taskteam)的工时分摊、按人统计的燃尽图、工时的审批流。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_effort` (
  `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '工时编号',
  `objectType`  varchar(30)   NOT NULL DEFAULT 'task' COMMENT '对象类型：task（本实现只做任务）',
  `objectID`    bigint        NOT NULL DEFAULT 0 COMMENT '对象编号（任务编号）',
  `product`     text          DEFAULT NULL COMMENT '所属产品（禅道原字段，逗号列表）',
  `project`     bigint        NOT NULL DEFAULT 0 COMMENT '所属项目',
  `execution`   bigint        NOT NULL DEFAULT 0 COMMENT '所属执行',
  `account`     varchar(64)   NOT NULL DEFAULT '' COMMENT '谁报的工时（账号）',
  `work`        text          DEFAULT NULL COMMENT '做了什么',
  `date`        date          NULL COMMENT '工作日期（date 是 MySQL 关键字）',
  `left`        decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '**这之后还剩多少**（任务剩余工时以最后一条为准）',
  `consumed`    decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '本次消耗工时',
  `begin`       char(4)       NOT NULL DEFAULT '' COMMENT '开始时间 HHMM（begin 是 JSqlParser 保留字）',
  `end`         char(4)       NOT NULL DEFAULT '' COMMENT '结束时间 HHMM（end 是 JSqlParser 保留字）',
  `extra`       varchar(255)  NOT NULL DEFAULT '' COMMENT '扩展字段',
  `order`       int           NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  -- yudao BaseDO 约定字段
  `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)   NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)   NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_object` (`objectType`, `objectID`, `deleted`),
  KEY `idx_account_date` (`account`, `date`),
  KEY `idx_project` (`project`, `execution`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工时明细表';

-- ============================ 演示数据 ============================
-- 任务 1「实现登录接口」：estimate 8h，两条工时记录 → consumed 7h、left 1h
INSERT INTO `zt_effort` (`id`, `objectType`, `objectID`, `product`, `project`, `execution`, `account`, `work`,
                         `date`, `left`, `consumed`, `begin`, `end`, `order`, `creator`, `updater`)
VALUES
(96101, 'task', 1, '1', 1, 90001, 'admin', '搭好接口骨架与参数校验', '2026-03-02', 5.00, 3.00, '0900', '1200', 1, 'admin', 'admin'),
(96102, 'task', 1, '1', 1, 90001, 'admin', '接通数据库并补单测',       '2026-03-03', 1.00, 4.00, '1330', '1730', 2, 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `objectID` = VALUES(`objectID`), `account` = VALUES(`account`), `work` = VALUES(`work`),
    `date` = VALUES(`date`), `left` = VALUES(`left`), `consumed` = VALUES(`consumed`),
    `begin` = VALUES(`begin`), `end` = VALUES(`end`), `deleted` = b'0';

-- 让演示任务与工时对得上（consumed = 3+4 = 7，left = 最后一条的 1）
UPDATE `zt_task` SET `consumed` = 7.00, `left` = 1.00 WHERE `id` = 1;

-- ============================ 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90096, '工时明细', 'zentao:effort:query',  2, 15, 90001, 'effort', 'ep:alarm-clock', 'zentao/effort/index', 'ZentaoEffort', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90097, '工时查询', 'zentao:effort:query',  3, 1, 90096, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90098, '工时录入', 'zentao:effort:create', 3, 2, 90096, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90099, '工时修改', 'zentao:effort:update', 3, 3, 90096, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90100, '工时删除', 'zentao:effort:delete', 3, 4, 90096, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

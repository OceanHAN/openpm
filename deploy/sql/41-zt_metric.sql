-- ---------------------------------------------------------------------------
-- 度量（metric）—— 度量项定义（zt_metric）+ 度量数据（zt_metriclib）
--
-- 【禅道语义】禅道的度量是「**定义在代码里、结果落在库里**」的两张表：
--     zt_metric     度量项定义：目的 purpose / 范围 scope / 对象 object / 单位 unit /
--                   时间维度 dateType（year/month/week/day/nodate）/ 口径 definition /
--                   采集方式 collector / 状态 stage / 是否内置 builtin /
--                   上次计算行数 lastCalcRows 与时间 lastCalcTime
--     zt_metriclib  度量数据：一行一个「维度组合 + 值」
--                   —— metricCode + metricID 定位度量项
--                   —— 维度列：system/program/project/product/execution/user/dept/code/pipeline/repo
--                   —— 时间列：year/month/week/day（dateType=nodate 时都不填，靠 `date` 取当天快照）
--                   —— value 是字符串（禅道原样，单位靠度量项定义）
--                   —— calcType：cron 定时采集 / inference 人工触发 / manual
--
-- 【两条必须照抄的规则】
--   1. **记录的主键是「维度 + 时间」**，不是自增 id：同一个度量项在同一个周期的数据只有一份，
--      重算前先按周期清掉（module/metric/model.php:clearOutDatedRecords）：
--        year        → 清该年的
--        month       → 清该年该月的
--        week        → 清该年该周的
--        day         → 清该年该月该日的
--      nodate 型（快照）没有时间列，靠 `date` 记「算的那一天」，查询时取 `date >= 今天`。
--   2. **口径来自代码**：414 个内置度量项的口径写成 calc 类（module/metric/calc/**，
--      每个类实现 getStatement/calculate/getResult），由升级脚本导入 zt_metric。
--      本次迁移把框架 + 15 个能从已迁表算出来的口径搬成了 Java（见 README 3.34），
--      其余口径属于「数据资产」，后续按需补。
--
-- 保留字提醒：`desc` / `order` / `when`（zt_metric）与 `system` / `value` / `date`（zt_metriclib）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 度量项定义 ============================
CREATE TABLE IF NOT EXISTS `zt_metric` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '度量项编号',
  `purpose`        varchar(50)  NOT NULL DEFAULT '' COMMENT '目的：scale 规模 / qc 质量 / hour 工时 / cost 成本 / rate 效率 / time 工期',
  `scope`          varchar(30)  NOT NULL DEFAULT '' COMMENT '范围：system/program/project/product/execution/user',
  `object`         varchar(30)  NOT NULL DEFAULT '' COMMENT '对象：story/bug/case/task/effort/project/...',
  `stage`          varchar(10)  NOT NULL DEFAULT 'wait' COMMENT '状态：wait 未发布 / released 已发布 / delisted 已下架',
  `type`           varchar(10)  NOT NULL DEFAULT 'php' COMMENT '实现方式：php 代码口径 / sql SQL 口径',
  `name`           varchar(90)  NOT NULL DEFAULT '' COMMENT '度量名称',
  `alias`          varchar(90)  NOT NULL DEFAULT '' COMMENT '别名（列表里显示的短名）',
  `code`           varchar(90)  NOT NULL DEFAULT '' COMMENT '度量项代码（与口径实现一一对应）',
  `unit`           varchar(10)  NOT NULL DEFAULT '' COMMENT '单位：count/measure/hour/day/manday/percentage/times/people/row',
  `dateType`       varchar(50)  NOT NULL DEFAULT '' COMMENT '时间维度：year/month/week/day/nodate（驼峰列名）',
  `collector`      text         DEFAULT NULL COMMENT '采集方式',
  `desc`           text         DEFAULT NULL COMMENT '描述（desc 是 MySQL 保留字）',
  `definition`     text         DEFAULT NULL COMMENT '口径定义',
  `when`           varchar(30)  NOT NULL DEFAULT '' COMMENT '采集时机（when 是 MySQL 保留字）',
  `event`          varchar(30)  NOT NULL DEFAULT '' COMMENT '触发事件',
  `cronCFG`        varchar(30)  NOT NULL DEFAULT '' COMMENT '定时配置（驼峰列名）',
  `time`           varchar(30)  NOT NULL DEFAULT '' COMMENT '采集时间点',
  `createdBy`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建人（驼峰列名）',
  `createdDate`    datetime     DEFAULT NULL COMMENT '创建时间（驼峰列名）',
  `editedBy`       varchar(64)  NOT NULL DEFAULT '' COMMENT '最后修改人（驼峰列名）',
  `editedDate`     datetime     DEFAULT NULL COMMENT '最后修改时间（驼峰列名）',
  `implementedBy`  varchar(64)  NOT NULL DEFAULT '' COMMENT '发布人（驼峰列名）',
  `implementedDate` datetime    DEFAULT NULL COMMENT '发布时间（驼峰列名）',
  `delistedBy`     varchar(64)  NOT NULL DEFAULT '' COMMENT '下架人（驼峰列名）',
  `delistedDate`   datetime     DEFAULT NULL COMMENT '下架时间（驼峰列名）',
  `builtin`        tinyint      NOT NULL DEFAULT 0 COMMENT '是否内置（1 内置，随代码走）',
  `fromID`         bigint       NOT NULL DEFAULT 0 COMMENT '来源度量项（驼峰列名）',
  `order`          int          NOT NULL DEFAULT 0 COMMENT '排序（order 是 MySQL 保留字）',
  `lastCalcRows`   int          NOT NULL DEFAULT 0 COMMENT '上次计算出的记录数（驼峰列名）',
  `lastCalcTime`   datetime     DEFAULT NULL COMMENT '上次计算时间（驼峰列名）',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`        varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`        varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_scope_object` (`scope`, `object`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='度量项定义表';

-- ============================ 2. 度量数据 ============================
CREATE TABLE IF NOT EXISTS `zt_metriclib` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '记录编号',
  `metricID`     bigint       NOT NULL DEFAULT 0 COMMENT '度量项编号（驼峰列名）',
  `metricCode`   varchar(100) NOT NULL DEFAULT '' COMMENT '度量项代码（驼峰列名）',
  `system`       tinyint      NOT NULL DEFAULT 0 COMMENT '系统级度量（system 是 MySQL 保留字）',
  `program`      bigint       NOT NULL DEFAULT 0 COMMENT '项目集维度',
  `project`      bigint       NOT NULL DEFAULT 0 COMMENT '项目维度',
  `product`      bigint       NOT NULL DEFAULT 0 COMMENT '产品维度',
  `execution`    bigint       NOT NULL DEFAULT 0 COMMENT '执行维度',
  `code`         varchar(30)  NOT NULL DEFAULT '' COMMENT '代码库维度',
  `pipeline`     varchar(30)  NOT NULL DEFAULT '' COMMENT '流水线维度',
  `repo`         varchar(30)  NOT NULL DEFAULT '' COMMENT '仓库维度',
  `user`         varchar(30)  NOT NULL DEFAULT '' COMMENT '人员维度（旧版保留字，加反引号）',
  `dept`         varchar(30)  NOT NULL DEFAULT '' COMMENT '部门维度',
  `year`         char(4)      NOT NULL DEFAULT '' COMMENT '年',
  `month`        char(2)      NOT NULL DEFAULT '' COMMENT '月',
  `week`         char(2)      NOT NULL DEFAULT '' COMMENT '周',
  `day`          char(2)      NOT NULL DEFAULT '' COMMENT '日',
  `value`        varchar(100) NOT NULL DEFAULT '' COMMENT '度量值（字符串，单位看度量项定义）',
  `calcType`     varchar(10)  NOT NULL DEFAULT 'cron' COMMENT '计算方式：cron 定时 / inference 人工触发（驼峰列名）',
  `calculatedBy` varchar(64)  NOT NULL DEFAULT '' COMMENT '计算人（驼峰列名）',
  `date`         datetime     DEFAULT NULL COMMENT '计算时间（date 是 MySQL 关键字）',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`      varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`      varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_code_system_date` (`metricCode`, `system`, `date`),
  KEY `idx_code_project_date` (`metricCode`, `project`, `date`),
  KEY `idx_code_product_date` (`metricCode`, `product`, `date`),
  KEY `idx_code_execution_date` (`metricCode`, `execution`, `date`),
  KEY `idx_code_user_date` (`metricCode`, `user`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='度量数据表';

-- ============================ 3. 内置度量项定义（口径来自禅道 calc 类，代码原样） ============================
-- 这 15 个口径的共同点是「只用已迁移的表就能算出来」：产品维度的需求/缺陷/用例/发布/计划数、
-- 项目维度的执行/人员/工时数、人员维度的需求/缺陷/用例数，以及三个年度新增口径。
-- 另外 3 条（按阶段统计的需求数、Bug 密度、年度新增项目数）**故意只导入定义、不实现口径**：
-- 用来验证「口径未迁移时报错而不是算错」，也是清单里「其余 399 个口径待补」的样本。
-- 名称/别名/单位/描述/口径定义都直接从禅道 calc 类的文档块抽取，不做改写。
INSERT INTO `zt_metric` (`purpose`, `scope`, `object`, `stage`, `type`, `name`, `alias`, `code`, `unit`,
                         `desc`, `definition`, `when`, `createdBy`, `createdDate`, `builtin`, `deleted`,
                         `dateType`, `order`)
VALUES
('scale', 'product', 'story', 'released', 'php', '按产品统计的研发需求总数', '研发需求总数', 'count_of_story_in_product', '个', '按产品统计的研发需求总数是指产品中创建的所有研发需求的数量。这个度量项可以反映团队需进行研发工作的规模。研发需求总数越多，可能意味着产品规模越大，面临的开发工作越多。', '产品中研发需求的个数求和;过滤已删除的研发需求;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'nodate', 1),
('scale', 'product', 'bug', 'released', 'php', '按产品统计的Bug总数', 'Bug总数', 'count_of_bug_in_product', '个', '按产品统计的Bug总数是指在产品中发现的所有Bug的数量。这个度量项反映了产品整体Bug质量情况。Bug总数越多可能代表产品的代码质量存在问题，需要进行进一步的解决和改进。', '产品中Bug的个数求和;过滤已删除的Bug;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'nodate', 2),
('scale', 'product', 'case', 'released', 'php', '按产品统计的用例总数', '用例总数', 'count_of_case_in_product', '个', '按产品统计的用例总数是指系统或项目中的测试用例总数量。用例是用来验证系统功能和性能的测试场景。统计用例总数可以帮助评估测试覆盖的广度和深度。用例总数越高可能意味着项目进行了全面和充分的测试。', '产品中用例的个数求和;过滤已删除的用例;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'nodate', 3),
('scale', 'product', 'release', 'released', 'php', '按产品统计的发布总数', '发布总数', 'count_of_release_in_product', '个', '按产品统计的发布总数是指产品中所有发布的数量。这个度量项可以反映产品团队对产品发布的频率和稳定性的掌控程度。发布总数越多，说明产品团队有更多的迭代和产品版本更新。', '产品中发布的个数求和;过滤已删除的发布;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'nodate', 4),
('scale', 'product', 'productplan', 'released', 'php', '按产品统计的计划总数', '计划总数', 'count_of_productplan_in_product', '个', '按产品统计的计划总数是指产品团队创建的所有计划数量。这个度量项可以反映产品团队的规划能力。适当的计划数量可以促进团队高效完成需求。', '产品中计划的个数求和;过滤已删除的计划;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'nodate', 5),
('scale', 'project', 'execution', 'released', 'php', '按项目统计的执行总数', '执行总数', 'count_of_execution_in_project', '个', '按项目统计的执行总数表示在项目中所有执行的数量，可以用来评估项目的规模、项目执行进度、工作负荷、绩效评估、风险控制和项目管理的有用信息。', '项目的执行个数求和;过滤已删除的执行;过滤已删除的项目;', '', 'system', NOW(), 1, 0, 'nodate', 6),
('scale', 'project', 'user', 'released', 'php', '按项目统计的人员总数', '人员总数', 'count_of_user_in_project', '个', '按项目统计的人员总数是指参与项目的全部人员的数量。这个度量项用于了解项目团队的规模和组成，对项目资源的分配和管理起到重要作用。', '项目中团队成员个数求和;过滤已移除的人员;过滤已删除的项目;', '', 'system', NOW(), 1, 0, 'nodate', 7),
('hour', 'project', 'task', 'released', 'php', '按项目统计的任务消耗工时数', '任务消耗工时数', 'consume_of_task_in_project', '小时', '按项目统计的任务消耗工时数是指已经花费的工时总和，用于完成所有任务。该度量项可以用来评估项目在任务执行过程中的工时投入情况，以及在完成任务方面的效率和资源利用情况。较高的任务消耗工时总数可能表明需要审查工作流程和资源分配，以提高工作效率。', '项目中任务的消耗工时数求和;过滤已删除的任务;过滤父任务;过滤已删除执行的任务;过滤已删除的项目;', '', 'system', NOW(), 1, 0, 'nodate', 8),
('hour', 'project', 'effort', 'released', 'php', '按项目统计的项目内所有消耗工时数', '项目内所有消耗工时数', 'consume_of_all_in_project', '小时', '按项目统计的项目内所有消耗工时数是指项目实际花费的总工时数。该度量项可以用来评估项目的工时投入情况和对资源的利用效率。较高的消耗工时数可能需要审查工作流程和资源分配，以提高工作效率和进度控制。', '项目中所有日志记录的工时之和;记录时间在某年;过滤已删除的项目;', '', 'system', NOW(), 1, 0, 'nodate', 9),
('scale', 'product', 'story', 'released', 'php', '按产品统计的年度新增研发需求数', '年度新增研发需求数', 'count_of_annual_created_story_in_product', '个', '按产品统计的年度新增研发需求数是指产品在某年度新增的研发需求数量。这个度量项可以反映产品团队在该年度内需求的增长或变化情况。', '产品中研发需求的个数求和;创建时间为某年;过滤已删除的研发需求;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'year', 10),
('scale', 'product', 'bug', 'released', 'php', '按产品统计的年度新增Bug数', '年度新增Bug数', 'count_of_annual_created_bug_in_product', '个', '按产品统计的年度新增Bug数是指产品在某年度新发现的Bug数量。这个度量项反映了产品在某年度出现的新问题数量。年度新增Bug数越多可能意味着质量控制存在问题，需要及时进行处理和改进。', '产品中Bug的个数求和;创建时间为某年;过滤已删除的Bug;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'year', 11),
('scale', 'product', 'case', 'released', 'php', '按产品统计的年度新增用例数', '年度新增用例数', 'count_of_annual_created_case_in_product', '个', '按产品统计的年度新增用例数是指产品在某年度新增的测试用例数量。统计年度新增用例数可以帮助评估系统或项目在不同阶段的测试覆盖和测试深度。年度新增用例数的增加可能意味着对新功能和需求进行了充分的测试。', '产品中用例的个数求和;创建时间为某年;过滤已删除的用例;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'year', 12),
('scale', 'user', 'story', 'released', 'php', '按人员统计的创建的研发需求数', '创建的研发需求数', 'count_of_story_in_user', '个', '按人员统计的创建的研发需求数。', '所有研发需求个数求和;', '', 'system', NOW(), 1, 0, 'nodate', 13),
('scale', 'user', 'bug', 'released', 'php', '按人员统计的创建Bug数', '创建Bug数', 'count_of_created_bug_in_user', '个', '按人员统计的创建Bug数是指每个人创建修复Bug总量。该度量项可以帮助我们了解每个人对已解决的Bug进行确认与关闭的速度和效率。', '截止当前时间;统计每个人创建Bug数的求和;过滤已删除的Bug;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'nodate', 14),
('scale', 'user', 'case', 'released', 'php', '按人员统计的创建用例数', '创建用例数', 'count_of_created_case_in_user', '个', '按人员统计的创建用例数是指每个人创建修复用例总量。该度量项可以帮助我们了解每个人对已解决的用例进行确认与关闭的速度和效率。', '截止当前时间;统计每个人创建用例数的求和;过滤已删除的用例;过滤已删除的产品;', '', 'system', NOW(), 1, 0, 'nodate', 15),
('scale', 'stage', 'story', 'released', 'php', '按阶段统计的需求数', '按阶段统计的需求数', 'count_of_story_in_stage_in_product', '个', '按阶段统计的需求数', '按阶段统计的需求数', '', 'system', NOW(), 1, 0, 'nodate', 16),
('scale', 'product', 'bug', 'released', 'php', '按产品统计的研发完毕研需规模的Bug密度', '研发完毕研需规模的Bug密度', 'bug_concentration_of_developed_story_in_product', '个', '按产品统计的研发完毕研需规模的Bug密度表示按产品统计的有效Bug数相对于按产品统计的研发完成的研发需求规模数。该度量项反映了研发完毕的研需的质量表现，密度越低代表研发完毕的研需质量越高。', '复用：;按产品统计的有效Bug数;按产品统计的研发完成的研发需求规模数;公式：;按产品统计的研发完成需求的Bug密度=按产品统计的有效Bug数/按产品统计的研发完成的研发需求规模数;', '', 'system', NOW(), 1, 0, 'nodate', 17),
('scale', 'system', 'project', 'released', 'php', '按系统统计的年度新增项目数', '按系统统计的年度新增项目数', 'count_of_annual_created_project', '个', '按系统统计的年度新增项目数是指某年度新创建的项目数量。这个度量项可以帮助团队了解某年度的项目规模和工作负荷，以及项目管理和资源分配的需求。较高的年度新增项目数可能需要团队根据资源和能力进行优先级和规划管理。', '所有的项目个数求和;创建时间为某年;过滤已删除的项目;', '', 'system', NOW(), 1, 0, 'year', 18)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `alias` = VALUES(`alias`), `purpose` = VALUES(`purpose`),
    `scope` = VALUES(`scope`), `object` = VALUES(`object`), `unit` = VALUES(`unit`),
    `desc` = VALUES(`desc`), `definition` = VALUES(`definition`), `dateType` = VALUES(`dateType`),
    `stage` = 'released', `builtin` = 1, `order` = VALUES(`order`), `deleted` = b'0';

-- ============================ 4. 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90130, '度量', 'zentao:metric:query',  2, 17, 90001, 'metric', 'ep:data-line', 'zentao/metric/index', 'ZentaoMetric', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90131, '度量查询', 'zentao:metric:query',  3, 1, 90130, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90132, '度量计算', 'zentao:metric:calc',   3, 2, 90130, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90133, '度量修改', 'zentao:metric:update', 3, 3, 90130, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90134, '度量删除', 'zentao:metric:delete', 3, 4, 90130, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

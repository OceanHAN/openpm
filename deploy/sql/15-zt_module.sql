-- ---------------------------------------------------------------------------
-- 模块树（module）模块
--
-- 【禅道语义】zt_module 是一张**通用树表**，用 (root, type, branch) 定位一棵树：
--   - type = 'story' + root = 产品 id    → 需求模块树（产品视图）
--   - type = 'bug'   + root = 产品 id    → 缺陷模块树（测试视图）
--   - type = 'task'  + root = 执行 id    → 任务模块树（执行视图）
--   - type = 'case'  + root = 产品 id    → 用例模块树
--   - type = 'line'  + root = 0          → 产品线（objectTables['productline'] = zt_module）
--   也就是说，禅道没有为每种对象单独建模块表，而是靠 type 复用同一棵树。
--
-- 【path / grade 约定】禅道用**逗号分隔**的路径，且以逗号开头：
--   root 的哨兵值：path = ','      grade = 0
--   一级模块：      path = ',5,'    grade = 1
--   二级模块：      path = ',5,6,'  grade = 2
--   注意这和 zt_project 的 '/1/2/' 斜杠格式**不一样**，两套约定不要混用。
--   删除/移动后由 fixModulePath() 按 parent 重算整棵树的 path 与 grade。
--
-- 【删除规则（remove()）】删一个模块 = 删它和它所有子孙，并把挂在这些模块上的
--   业务对象**改挂到被删模块的父模块**上：
--     type=task  → zt_task.module = parent
--     type=bug   → zt_bug.module  = parent
--     type=story → zt_story.module / zt_task.module / zt_bug.module = parent
--   这是禅道「删模块不丢需求」的关键设计 —— 数据不会被孤立在已删除的模块下。
--
-- 【唯一性】同一 (root, type, branch, parent) 下模块名不能重复，由应用层校验。
--   (root, type, branch) 还要注意：type 为 bug/case 时禅道会连 story 一起查
--   （mergeModule），本实现按「同 type 同 parent」校验，见 ModuleServiceImpl 注释。
--
-- 注意 MySQL/JSqlParser 关键字：`order`、`from` 必须加反引号（已用 JSqlParser 4.5 验证）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_module` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '模块编号',
  `root`        bigint       NOT NULL DEFAULT 0 COMMENT '所属根对象：产品id / 执行id / 0',
  `branch`      bigint       NOT NULL DEFAULT 0 COMMENT '所属分支/平台（0 主干）',
  `name`        varchar(60)  NOT NULL DEFAULT '' COMMENT '模块名称',
  `parent`      bigint       NOT NULL DEFAULT 0 COMMENT '上级模块（0 为一级）',
  `path`        varchar(255) NOT NULL DEFAULT ',' COMMENT '路径，逗号分隔且以逗号开头：,5,6,',
  `grade`       tinyint      NOT NULL DEFAULT 1 COMMENT '层级，一级模块为 1',
  `order`       int          NOT NULL DEFAULT 0 COMMENT '排序（order 是保留字）',
  `type`        varchar(30)  NOT NULL DEFAULT '' COMMENT '树类型：story/task/bug/case/line/doc/api/caselib',
  `from`        int          NOT NULL DEFAULT 0 COMMENT '来源模块编号（禅道用于「复制模块」）',
  `owner`       varchar(30)  NOT NULL DEFAULT '' COMMENT '负责人',
  `collector`   text         NULL COMMENT '抄送人（禅道原字段）',
  `short`       varchar(60)  NOT NULL DEFAULT '' COMMENT '简称',
  `extra`       varchar(30)  NOT NULL DEFAULT '' COMMENT '扩展字段（禅道原字段）',
  -- yudao BaseDO 约定字段
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_root_type_branch` (`root`, `type`, `branch`),
  KEY `idx_parent` (`parent`),
  KEY `idx_path` (`path`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用模块树表';

-- ============================ 演示数据 ============================
-- 产品 1 / 2 / 3 的需求模块树（type=story，root=产品id，path 用禅道的逗号格式）
INSERT INTO `zt_module` (`id`, `root`, `branch`, `name`, `parent`, `path`, `grade`, `order`, `type`, `creator`, `updater`) VALUES
(1, 1, 0, '用户中心', 0, ',1,',  1, 10, 'story', 'admin', 'admin'),
(2, 1, 0, '订单中心', 0, ',2,',  1, 20, 'story', 'admin', 'admin'),
(3, 1, 0, '登录注册', 1, ',1,3,', 2, 10, 'story', 'admin', 'admin'),
(4, 1, 0, '个人资料', 1, ',1,4,', 2, 20, 'story', 'admin', 'admin'),
(5, 2, 0, '数据采集', 0, ',5,',  1, 10, 'story', 'admin', 'admin'),
(6, 2, 0, '数据治理', 0, ',6,',  1, 20, 'story', 'admin', 'admin'),
(7, 3, 0, '门户首页', 0, ',7,',  1, 10, 'story', 'admin', 'admin')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `path` = VALUES(`path`), `grade` = VALUES(`grade`);

-- ============================ 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90037, '模块维护', 'zentao:module:query',  2, 10, 90001, 'module', 'ep:files', 'zentao/module/index', 'ZentaoModule', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90038, '模块查询', 'zentao:module:query',  3, 1, 90037, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90039, '模块创建', 'zentao:module:create', 3, 2, 90037, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90040, '模块修改', 'zentao:module:update', 3, 3, 90037, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90041, '模块删除', 'zentao:module:delete', 3, 4, 90037, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

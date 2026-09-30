-- ---------------------------------------------------------------------------
-- 干系人（stakeholder）
--
-- 【禅道语义】zt_stakeholder 记的是「某个项目集/项目相关的人」——不是团队成员：
--   团队成员 = 要干活的人（有工时、有角色，见 zt_team）
--   干系人   = 需要知情/被影响的人（甲方、领导、外部顾问……）
-- 所以两者的字段完全不同，禅道也是两张表、两个模块。
--
-- 【关键列】
--   objectType / objectID  挂在谁身上：program（项目集）或 project（项目）
--   user                   账号；from='outside' 时是外部人员（禅道会在 zt_user 里建一条 type=outside 的记录，
--                          本实现简化为直接存名字，见 README 已知限制）
--   type                   inside / outside —— 注意它**由 from 推导**，不是独立选的：
--                          from='outside' → type='outside'，其余 → 'inside'
--   `key`                  是否关键干系人（0/1），列表上会有标记
--   `from`                 来源：team（团队成员）/ company（公司同事）/ outside（外部人员）
--
-- 【唯一性】禅道的 check 是 `user unique(objectID = ? AND deleted = '0')`：
--   同一个人不能重复加到同一个对象下。
--
-- 【保留字】`key` 与 `from` 都是 MySQL 保留字，必须加反引号。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_stakeholder` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '干系人编号',
  `objectID`    bigint      NOT NULL DEFAULT 0 COMMENT '对象编号（项目集/项目）',
  `objectType`  varchar(30) NOT NULL DEFAULT '' COMMENT '对象类型：program / project',
  `user`        varchar(64) NOT NULL DEFAULT '' COMMENT '账号（from=outside 时是外部人员名字）',
  `type`        varchar(30) NOT NULL DEFAULT 'inside' COMMENT 'inside 内部 / outside 外部（由 from 推导）',
  `key`         tinyint     NOT NULL DEFAULT 0 COMMENT '是否关键干系人（`key` 是 MySQL 保留字）',
  `from`        varchar(30) NOT NULL DEFAULT '' COMMENT '来源：team/company/outside（`from` 是 MySQL 保留字）',
  `createdBy`   varchar(64) NOT NULL DEFAULT '' COMMENT '创建人（禅道列名）',
  `createdDate` datetime    DEFAULT NULL COMMENT '创建时间（禅道列名）',
  `editedBy`    varchar(64) NOT NULL DEFAULT '' COMMENT '最后修改人（禅道列名）',
  `editedDate`  datetime    DEFAULT NULL COMMENT '最后修改时间（禅道列名）',
  -- yudao BaseDO 约定字段
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_object` (`objectType`, `objectID`, `deleted`),
  KEY `idx_user` (`user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='干系人表（项目集/项目相关的人，不是团队成员）';

-- ============================ 演示数据 ============================
-- 项目集 9001：内部 admin（关键）、内部 tester、外部「张三（甲方）」
-- 项目 1：内部 admin、内部 tester
INSERT INTO `zt_stakeholder` (`id`, `objectID`, `objectType`, `user`, `type`, `key`, `from`,
                              `createdBy`, `createdDate`, `editedBy`, `editedDate`, `creator`, `updater`)
VALUES
(99101, 9001, 'program', 'admin',  'inside',  1, 'team',    'admin', NOW(), 'admin', NOW(), 'admin', 'admin'),
(99102, 9001, 'program', 'tester', 'inside',  0, 'company', 'admin', NOW(), 'admin', NOW(), 'admin', 'admin'),
(99103, 9001, 'program', '张三（甲方）', 'outside', 1, 'outside', 'admin', NOW(), 'admin', NOW(), 'admin', 'admin'),
(99104, 1,    'project', 'admin',  'inside',  1, 'team',    'admin', NOW(), 'admin', NOW(), 'admin', 'admin'),
(99105, 1,    'project', 'tester', 'inside',  0, 'team',    'admin', NOW(), 'admin', NOW(), 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `type` = VALUES(`type`), `key` = VALUES(`key`), `from` = VALUES(`from`), `deleted` = b'0';

-- ============================ 菜单与权限 ============================
-- 干系人同样没有独立页面（它是项目集/项目列表里的一个抽屉），但权限点必须建，
-- 否则前端 v-hasPermi 会把按钮隐藏（坑位 #43）。
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90113, '干系人查询', 'zentao:stakeholder:query',  3, 22, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90114, '干系人维护', 'zentao:stakeholder:update', 3, 23, 90022, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`);

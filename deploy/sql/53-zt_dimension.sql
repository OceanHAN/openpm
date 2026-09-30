-- ---------------------------------------------------------------------------
-- 维度（dimension）：BI / 透视表 / 大屏的「1.5 级导航」
--
-- 禅道语义（module/dimension：control 72 行 + model 118 行 + zen 14 行，2 个 action）：
--   1. 一张 zt_dimension 把 screen（大屏）/ pivot（透视表）/ chart（图表）归到「维度」下，
--      并提供 1.5 级导航下拉（ajaxGetDropMenu）与「记住用户上次所在维度」（getDimension）。
--      开源版**预置 3 行**：macro 宏观 / efficiency 效能 / quality 质量（db/zentao.sql:14579）。
--   2. 可见性**不在 dimension 模块里**，而在 biModel::getViewableObject('dimension')
--      （module/bi/model.php:14-66）：
--        acl='open' 或 createdBy=自己 或 whitelist 命中自己；超管（admins）直通全部。
--      注意它选的是 id/createdBy/acl/whitelist 四列，acl 与 whitelist 都存在时才有过滤。
--   3. 「末次维度」四级兜底链（model.php:83-117 saveState）：
--        config->dimensions->lastDimension → session->dimension
--        → 可见性校验（不在可见集合里就取可见的第一条）→ getFirst()（第一条）
--      拿到之后双写：session（按 app->tab 分桶）+ setting 项 {account}.common.dimension.lastDimension。
--   4. 下拉链接的**两处参数例外**（control.php:39-46）：
--        module=pivot 且 method=design  → method 改写为 browse
--        tab=bi 且 module=tree 且 method=browsegroup → 参数追加 groupID=0&type={viewType}
--      最终 JSON 结构是 data / searchHint / link / labelMap / expandName / itemType。
--
-- 三处**有意偏离**（都写进 README 的「有意偏离」）：
--   a. **维度 CRUD / 管理界面不做**：开源版没有（lang 里的 aclList 无调用点，全库只有
--      upgrade/model.php:6971 直接 INSERT TABLE_DIMENSION）。本实现只发布「只读维度 + 切换语义」，
--      管理端留 P3。
--   b. **session → Redis 的「用户级末次访问记录」**：禅道把末次维度写 session（按 app->tab 分桶）
--      再写 setting 表。本项目不迁 zt_setting，也没有可写的用户级配置 API，所以：
--        step ① 配置   = Spring 配置项 zentao.dimension.last-dimension（同 zentao.score.enabled 的做法）
--        step ② 会话   = Redis 键 zentao:dimension:last:{tab}:{account}（与 session 语义等价，重启后仍在）
--      兜底顺序、可见性校验、getFirst 完全照抄。
--   c. **树的链接形状**：禅道 createLink 生成 PHP 路由 `index.php?m=x&f=y&...`；本实现返回
--      `/{module}/{method}?{params}`（yudao 前端可直接拼），参数与上面两条例外完全照抄，
--      并把 module/method/params 单列出来便于前端与断言。
--
-- 表结构照抄 db/zentao.sql（id 按本项目惯例放宽成 bigint unsigned，同 49/50/51/52 各表），
-- 只补框架需要的 create_time / update_time / creator / updater。
-- 保留字：`desc` 是 MySQL 关键字，建表与 DO 都要加反引号。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_dimension` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`        varchar(90)  NOT NULL DEFAULT '' COMMENT '维度名称（1.5 级导航下拉的显示文本）',
  `code`        varchar(45)  NOT NULL DEFAULT '' COMMENT '维度代号（macro/efficiency/quality）',
  `desc`        text DEFAULT NULL COMMENT '描述。列名 desc 是 MySQL 关键字',
  `acl`         varchar(10)  NOT NULL DEFAULT 'open' COMMENT '访问控制：open 公开 / private 仅创建者与白名单',
  `whitelist`   text DEFAULT NULL COMMENT '白名单账号逗号串（acl=private 时用 FIND_IN_SET 命中）',
  `createdBy`   varchar(30)  NOT NULL DEFAULT '' COMMENT '创建人账号（可见性判据之一：createdBy=自己）',
  `createdDate` datetime     DEFAULT NULL COMMENT '创建时间',
  `editedBy`    varchar(30)  NOT NULL DEFAULT '' COMMENT '最后修改人账号',
  `editedDate`  datetime     DEFAULT NULL COMMENT '最后修改时间',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creator`     varchar(64)  NOT NULL DEFAULT '',
  `updater`     varchar(64)  NOT NULL DEFAULT '',
  `deleted`     tinyint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维度（禅道 zt_dimension，BI 的 1.5 级导航）';

-- 演示数据：禅道开源版预置的 3 行（db/zentao.sql:14579 原样，包括 system 这个创建人）。
-- id 用 1/2/3 —— 与禅道预置一致；测试用的临时维度请用 94201+ 高位段（坑位 #23）。
-- ON DUPLICATE KEY UPDATE 把关键列全部列出：否则撞上历史软删除行时会留下旧值。
INSERT INTO `zt_dimension` (`id`, `name`, `code`, `desc`, `acl`, `whitelist`, `createdBy`, `createdDate`, `editedBy`, `editedDate`)
VALUES
(1, '宏观管理维度', 'macro',      '', 'open', NULL, 'system', '2023-04-27 20:22:16', '', NULL),
(2, '效能管理维度', 'efficiency', '', 'open', NULL, 'system', '2023-04-27 20:22:16', '', NULL),
(3, '质量管理维度', 'quality',    '', 'open', NULL, 'system', '2023-04-27 20:22:16', '', NULL)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `code` = VALUES(`code`), `desc` = VALUES(`desc`),
    `acl` = VALUES(`acl`), `whitelist` = VALUES(`whitelist`),
    `createdBy` = VALUES(`createdBy`), `createdDate` = VALUES(`createdDate`),
    `editedBy` = VALUES(`editedBy`), `editedDate` = VALUES(`editedDate`),
    `deleted` = 0;

-- 菜单与权限：90190 页面 + 90191 查询按钮（type=3 的行不能省，否则 v-hasPermi 的按钮不显示，坑位 #43）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90190, '维度',     'zentao:dimension:query', 2, 28, 90001, 'dimension', 'ep:data-analysis', 'zentao/dimension/index', 'ZentaoDimension', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90191, '维度查询', 'zentao:dimension:query', 3, 1, 90190, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

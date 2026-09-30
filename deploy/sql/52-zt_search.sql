-- ---------------------------------------------------------------------------
-- 保存查询 / 搜索（search）
--
-- 禅道语义（module/search：control 11 个 action + model 1,211 行）：
--   1. zt_userquery = 「保存搜索条件」：列表页搜索后可以存下来、下次一键用，还能设为快捷方式/公共查询
--   2. zt_searchdict = 「拼音首字母码表」：把中文按 Unicode 编码映射成拼音首字母，实现「按拼音搜中文」
--   3. zt_searchindex = 「全文检索索引」（objectType/objectID/title/content + InnoDB FULLTEXT）
--
-- ⚠️ 一处**必须有意偏离**（写进 README）：
--   禅道把搜索条件序列化成**一段 SQL 片段**存进 zt_userquery.sql，列表页直接拼进 WHERE。
--   Java 侧照搬等于把 SQL 注入的口子开到数据层，而且换成 MyBatis-Plus 之后根本拼不进去。
--   本实现：form 列存「表单定义快照」，sql 列**列名与类型照旧**但存**结构化条件 JSON**：
--     [{"field":"status","op":"eq","value":"active"}, {"field":"title","op":"like","value":"登录"}]
--   由调用方把它翻译成对应模块的强类型查询 VO（本项目每个模块的查询条件都是强类型 VO）。
--
-- 两张表都**没有 deleted 列**（禅道原样：保存的查询是物理删除），DO 也不继承 BaseDO。
-- 保留字：`key`、`value`、`sql` 都要加反引号（前两个在 MySQL/JSqlParser 里都是关键字）。
--
-- zt_searchindex（全文检索）**本实现不做**，理由与结论写在 README：它要覆盖所有对象类型 +
-- 依赖 InnoDB FULLTEXT 分词，收益低、成本高，属于「有意不做」而不是「忘了做」。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_userquery` (
  `id`       bigint unsigned NOT NULL AUTO_INCREMENT,
  `account`  varchar(30) NOT NULL DEFAULT '' COMMENT '账号（谁的查询）',
  `module`   varchar(30) NOT NULL DEFAULT '' COMMENT '模块（story/task/bug/…）',
  `title`    varchar(90) NOT NULL DEFAULT '' COMMENT '查询名称',
  `form`     text DEFAULT NULL COMMENT '表单定义快照（JSON）',
  `sql`      text DEFAULT NULL COMMENT '**结构化条件 JSON**（不是 SQL —— 见文件头说明）',
  `shortcut` tinyint unsigned NOT NULL DEFAULT 0 COMMENT '是否快捷方式：1 是',
  `common`   tinyint unsigned NOT NULL DEFAULT 0 COMMENT '是否公共查询：1 是',
  PRIMARY KEY (`id`),
  KEY `idx_account_module` (`account`, `module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保存的查询（禅道 zt_userquery，无 deleted 列）';

CREATE TABLE IF NOT EXISTS `zt_searchdict` (
  `id`    bigint unsigned NOT NULL AUTO_INCREMENT,
  `key`   smallint unsigned NOT NULL DEFAULT 0 COMMENT '编码（汉字 Unicode 高位）',
  `value` char(3) NOT NULL DEFAULT '' COMMENT '拼音首字母',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_value` (`key`, `value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索词典（禅道 zt_searchdict，拼音首字母码表）';

-- 演示数据：给 admin 存两条查询（story 的「激活需求」、bug 的「未解决」）
DELETE FROM `zt_userquery` WHERE `account` = 'admin' AND `title` LIKE '演示%';
INSERT INTO `zt_userquery` (`account`, `module`, `title`, `form`, `sql`, `shortcut`, `common`) VALUES
('admin', 'story', '演示：激活的需求',
 '{"fields":[{"field":"status","label":"状态","control":"select"},{"field":"title","label":"标题","control":"input"}]}',
 '[{"field":"status","op":"eq","value":"active"}]', 1, 0),
('admin', 'bug', '演示：未解决的 Bug',
 '{"fields":[{"field":"status","label":"状态","control":"select"},{"field":"severity","label":"严重程度","control":"select"}]}',
 '[{"field":"status","op":"ne","value":"resolved"},{"field":"severity","op":"in","value":["1","2"]}]', 0, 1);

-- 词典演示（真实码表是逐字一条、上千行；这里给两条能真正命中的：
--   需 U+9700 = 38656 → x（xū）、求 U+6C42 = 27714 → q（qiú），于是「需求」→「xq」）
DELETE FROM `zt_searchdict`;
INSERT INTO `zt_searchdict` (`key`, `value`) VALUES (38656, 'x'), (27714, 'q');

-- 菜单与权限：90185 页面 + 2 个按钮权限
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90185, '保存查询', 'zentao:search:query',  2, 27, 90001, 'search', 'ep:search', 'zentao/search/index', 'ZentaoSearch', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90186, '查询条件', 'zentao:search:query',  3, 1, 90185, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90187, '保存查询', 'zentao:search:save',   3, 2, 90185, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90188, '删除查询', 'zentao:search:delete', 3, 3, 90185, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

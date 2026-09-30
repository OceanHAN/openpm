-- ---------------------------------------------------------------------------
-- 产品表升级：把极简版 zt_product 补齐到可用形态
--
-- 背景：第一阶段只建了 (id, name, code, status, po, description) 用于需求归属与下拉。
-- 现在补 P0 主干链路，产品需要承载：所属项目集/产品线、类型（正常/多分支/多平台）、
-- 角色分工（PO/QD/RD）、访问控制（acl）。
--
-- 【有意的偏离】禅道 zt_product 有 20 多个预计算计数器字段
--   （draftStories / activeStories / totalStories / unresolvedBugs / totalBugs ...）
--   它们是禅道为了列表页性能做的冗余存储，需要靠触发器或业务代码维护一致性。
--   本实现改为**读时实时统计**（一条 GROUP BY 查询），换取永不漂移的正确性。
--   代价是列表页多一次聚合查询，用索引可以接受。
--
-- 注意 MySQL 保留字：desc、order 必须加反引号。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- 1. 重命名两个与禅道不一致的列
ALTER TABLE `zt_product`
    CHANGE COLUMN `po` `PO` varchar(64) NOT NULL DEFAULT '' COMMENT '产品经理',
    CHANGE COLUMN `description` `desc` mediumtext NULL COMMENT '产品描述';

-- 2. 补齐主干字段
ALTER TABLE `zt_product`
    ADD COLUMN `program`     bigint      NOT NULL DEFAULT 0 COMMENT '所属项目集',
    ADD COLUMN `line`        bigint      NOT NULL DEFAULT 0 COMMENT '所属产品线',
    ADD COLUMN `type`        varchar(30) NOT NULL DEFAULT 'normal' COMMENT '类型：normal正常/branch多分支/platform多平台',
    ADD COLUMN `QD`          varchar(64) NOT NULL DEFAULT '' COMMENT '测试负责人',
    ADD COLUMN `RD`          varchar(64) NOT NULL DEFAULT '' COMMENT '研发负责人',
    ADD COLUMN `acl`         varchar(10) NOT NULL DEFAULT 'open' COMMENT '访问控制：open公开/private私有',
    ADD COLUMN `createdBy`   varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
    ADD COLUMN `createdDate` datetime    NULL COMMENT '创建时间',
    ADD COLUMN `closedDate`  datetime    NULL COMMENT '关闭时间',
    ADD COLUMN `order`       int         NOT NULL DEFAULT 0 COMMENT '排序（order 是保留字）',
    ADD COLUMN `vision`      varchar(10) NOT NULL DEFAULT 'rnd' COMMENT '视图';

-- 3. 回填已有数据的 createdBy/createdDate
UPDATE `zt_product` SET `createdBy` = 'admin', `createdDate` = `create_time` WHERE `createdBy` = '';

-- 4. 菜单与权限
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90017, '产品管理', 'zentao:product:query', 2, 0, 90001, 'product', 'ep:box', 'zentao/product/index', 'ZentaoProduct', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90018, '产品查询', 'zentao:product:query',  3, 1, 90017, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90019, '产品创建', 'zentao:product:create', 3, 2, 90017, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90020, '产品修改', 'zentao:product:update', 3, 3, 90017, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90021, '产品删除', 'zentao:product:delete', 3, 4, 90017, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

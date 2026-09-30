-- ---------------------------------------------------------------------------
-- 分支/平台（branch）模块
--
-- 【禅道语义】zt_branch 是**产品维度**的分支（平台）表：
--   - product 类型为 normal   → 产品没有分支概念，$lang->product->branchName['normal'] = ''
--   - product 类型为 branch   → 叫「分支」，$lang->product->branchName['branch'] = '分支'
--   - product 类型为 platform → 叫「平台」，$lang->product->branchName['platform'] = '平台'
--   同一套数据、同一套接口，只是文案随产品类型变（module/branch/model.php 里
--   到处用 str_replace('@branch@', $this->lang->product->branchName[$productType], ...)）。
--
-- 【虚拟主干】禅道约定 branchID = 0 就是「主干」，**不落库**：
--   - getByID(0) 直接返回名字「主干」
--   - 需求/缺陷/模块的 branch 字段为 0 即表示挂在主干上
--   所以列表里要人为补一行「主干」，且主干不能被关闭/删除。
--
-- 【删除保护】checkBranchData() 会检查该分支下是否已有数据
--   （module / story / productplan / bug / case / release / build / 项目关联的产品分支），
--   有数据就拒绝删除。本实现已存在 zt_story / zt_bug / zt_module 三张表，
--   就检查这三张；其余表建好后按同样方式追加即可。
--
-- 【为什么不做唯一键】禅道用应用层 unique 校验（checkIF(..., 'name', 'unique', ...)），
--   没有建 (product, name) 唯一索引。原因和 zt_storyspec 那条坑一样：
--   yudao 的 @TableLogic 逻辑删除下，唯一键会把「删掉后重建同名分支」卡死。
--   这里保持应用层校验。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

-- ============================ 1. 分支表 ============================
CREATE TABLE IF NOT EXISTS `zt_branch` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '分支编号',
  `product`     bigint       NOT NULL DEFAULT 0 COMMENT '所属产品',
  `name`        varchar(255) NOT NULL DEFAULT '' COMMENT '分支/平台名称',
  `default`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否默认分支（default 是保留字）',
  `status`      varchar(10)  NOT NULL DEFAULT 'active' COMMENT '状态：active激活/closed已关闭',
  `desc`        varchar(255) NOT NULL DEFAULT '' COMMENT '描述',
  `createdDate` datetime     NULL COMMENT '创建时间（对齐禅道 createdDate）',
  `closedDate`  datetime     NULL COMMENT '关闭时间（对齐禅道 closedDate）',
  `order`       int          NOT NULL DEFAULT 0 COMMENT '排序（order 是保留字）',
  -- yudao BaseDO 约定字段
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_product_status` (`product`, `status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品分支/平台表';

-- ============================ 2. 缺陷补 branch 列 ============================
-- 禅道 zt_bug 有 branch 列（缺陷要归属到具体分支/平台），前面建表时漏了。
-- 用存储过程做幂等：列已存在时跳过。
DROP PROCEDURE IF EXISTS `zt_add_column_if_absent`;
DELIMITER $$
CREATE PROCEDURE `zt_add_column_if_absent`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'ruoyi-vue-pro' AND TABLE_NAME = 'zt_bug' AND COLUMN_NAME = 'branch') THEN
    ALTER TABLE `zt_bug` ADD COLUMN `branch` bigint NOT NULL DEFAULT 0 COMMENT '所属分支/平台' AFTER `module`;
  END IF;
END$$
DELIMITER ;
CALL `zt_add_column_if_absent`();
DROP PROCEDURE `zt_add_column_if_absent`;

-- ============================ 3. 演示数据 ============================
-- 把两个演示产品改成「多平台 / 多分支」，并给出各自的分支数据（幂等）
UPDATE `zt_product` SET `type` = 'platform' WHERE `id` = 2;
UPDATE `zt_product` SET `type` = 'branch'   WHERE `id` = 3;

INSERT INTO `zt_branch` (`id`, `product`, `name`, `default`, `status`, `desc`, `createdDate`, `order`, `creator`, `updater`) VALUES
(1, 2, '政务云平台', 1, 'active', '面向政务行业的平台版本', NOW(), 1, 'admin', 'admin'),
(2, 2, '企业版',     0, 'active', '通用企业版',             NOW(), 2, 'admin', 'admin'),
(3, 3, 'v1.0',       1, 'active', '客户门户 1.x 分支',      NOW(), 1, 'admin', 'admin'),
(4, 3, 'v2.0',       0, 'closed', '客户门户 2.x 分支（已归档）', NOW(), 2, 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `default` = VALUES(`default`), `status` = VALUES(`status`),
    `product` = VALUES(`product`), `desc` = VALUES(`desc`);

-- ============================ 4. 菜单与权限 ============================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90032, '分支管理', 'zentao:branch:query',  2, 5, 90017, 'branch', 'ep:share', 'zentao/branch/index', 'ZentaoBranch', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90033, '分支查询', 'zentao:branch:query',  3, 1, 90032, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90034, '分支创建', 'zentao:branch:create', 3, 2, 90032, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90035, '分支修改', 'zentao:branch:update', 3, 3, 90032, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90036, '分支删除', 'zentao:branch:delete', 3, 4, 90032, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

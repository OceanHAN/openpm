-- ---------------------------------------------------------------------------
-- 产品表 zt_product —— 极小实现
--
-- 需求必须归属某个产品（zt_story.product）。禅道的 zt_product 有 30+ 字段，
-- 这里只保留做下拉选择和列表展示所需的最小集合，后续按需扩展。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_product` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '产品编号',
  `name`        varchar(255) NOT NULL DEFAULT '' COMMENT '产品名称',
  `code`        varchar(64)  NOT NULL DEFAULT '' COMMENT '产品代号',
  `status`      varchar(16)  NOT NULL DEFAULT 'normal' COMMENT '状态：normal正常/closed关闭',
  `po`          varchar(64)  NOT NULL DEFAULT '' COMMENT '产品负责人',
  `description` text         NULL COMMENT '产品描述',
  -- yudao BaseDO 约定字段
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品表';

-- 演示数据（幂等）
INSERT INTO `zt_product` (`id`, `name`, `code`, `status`, `po`, `description`, `creator`, `updater`) VALUES
(1, '禅道研发管理平台', 'ZENTAO', 'normal', 'admin', '对标禅道的研发管理产品，用于迁移验证', 'admin', 'admin'),
(2, '数据治理平台',     'DG',     'normal', 'admin', '数据资产与治理', 'admin', 'admin'),
(3, '客户门户',         'PORTAL', 'normal', 'admin', '面向客户的统一入口', 'admin', 'admin')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `code` = VALUES(`code`);

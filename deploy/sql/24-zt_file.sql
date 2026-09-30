-- ---------------------------------------------------------------------------
-- 附件（file）模块
--
-- 【禅道语义】zt_file 的每一行是一个附件，靠 (objectType, objectID) 挂在业务对象上：
--   objectType ∈ story/task/bug/doc/...，objectID 是对象编号。
--   所以「附件」不是独立功能，而是**所有业务模块的公共能力** —— 这也是它在清单里是 P0 的原因。
--
-- 【两个必须复刻的机制】
--  1. **gid 两阶段绑定**：新建对象时附件还没法挂（对象 id 还不存在），
--     禅道的做法是先给这批上传一个临时 `gid`，等对象保存后再把该 gid 下的附件
--     一次性 updateObjectID 绑到对象上。前端的「先传附件、再点保存」就靠它。
--     → 本实现提供 file/bind-by-gid 接口。
--  2. `downloads` 下载计数：禅道会累加，用于附件热度统计。
--
-- 【存储不迁移】
--   禅道自己管文件（upload 目录 + pathname 相对路径），本实现**不搬存储**：
--   字节交给 yudao 的文件服务（FileApi），zt_file.pathname 存它返回的可访问 URL。
--   这样一套系统里只有一个存储后端（本地/DB/S3 由 infra_file_config 决定，见 23-file-storage-setup.sql），
--   同时保留禅道侧的元数据语义（objectType/objectID/title/downloads/gid），便于历史数据导入与对照。
--
-- 【没有菜单】禅道也没有「附件」独立页面 —— 附件永远出现在业务对象的详情里，
--   所以本模块只提供接口 + 前端公共组件 AttachmentPanel.vue，由各业务页面嵌入。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_file` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '附件编号',
  `pathname`      varchar(255) NOT NULL DEFAULT '' COMMENT '存储路径/可访问 URL（禅道原字段名，这里存 yudao 文件服务返回的 URL）',
  `title`         varchar(255) NOT NULL DEFAULT '' COMMENT '文件名（展示用）',
  `extension`     varchar(30)  NOT NULL DEFAULT '' COMMENT '扩展名',
  `size`          bigint       NOT NULL DEFAULT 0 COMMENT '字节大小',
  `objectType`    varchar(30)  NOT NULL DEFAULT '' COMMENT '所属对象类型：story/task/bug/doc…（驼峰列名）',
  `objectID`      bigint       NOT NULL DEFAULT 0 COMMENT '所属对象编号（驼峰列名）',
  `gid`           varchar(48)  NOT NULL DEFAULT '' COMMENT '临时分组 id，用于「先传附件、后绑对象」',
  `addedBy`       varchar(64)  NOT NULL DEFAULT '' COMMENT '上传人（驼峰列名）',
  `addedDate`     datetime     NULL COMMENT '上传时间（驼峰列名）',
  `downloads`     int          NOT NULL DEFAULT 0 COMMENT '下载次数',
  `extra`         varchar(255) NOT NULL DEFAULT '' COMMENT '扩展字段（禅道原字段）',
  -- yudao BaseDO 约定字段
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator`       varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `updater`       varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_object` (`objectType`, `objectID`),
  KEY `idx_gid` (`gid`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='附件表';

-- ============================ 权限 ============================
-- 附件没有独立页面（它永远嵌在业务对象详情里），但**权限点必须有行**：
--   1. 非超管角色要能通过「角色-菜单」勾选这些权限；
--   2. super_admin 的权限展开是按 `zentao:%` 前缀扫 system_menu 得到的（见 22 号脚本的说明），
--      没有行就扫不到。
-- 所以这里建 4 个 type=3（按钮）的权限行，直接挂在禅道根目录 90001 下 —— 按钮不参与路由生成，
-- 挂在目录下不会产生空页面。
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90068, '附件上传', 'zentao:file:create', 3, 20, 90001, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90069, '附件查询', 'zentao:file:query',  3, 21, 90001, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90070, '附件修改', 'zentao:file:update', 3, 22, 90001, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90071, '附件删除', 'zentao:file:delete', 3, 23, 90001, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`);

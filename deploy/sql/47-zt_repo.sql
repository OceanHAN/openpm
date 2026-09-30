-- ---------------------------------------------------------------------------
-- 代码库（repo）：zt_repo / zt_repohistory / zt_repofiles + 通用关系表 zt_relation
--
-- 禅道语义（module/repo，16,016 行、71 个 action）：
--   1. zt_repo 是「代码库」的定义（SCM 类型、地址/本地路径、默认分支、关联产品、权限）
--   2. «提交记录» 同步进 zt_repohistory（一条 commit 一行），改动的文件进 zt_repofiles
--   3. 提交信息里的 「Story #12」「Task #3,4」「Bug #7」会被解析出来，
--      写进**通用关系表 zt_relation**（AType='revision', relation='commit', BType=story/bug/task）
--      —— 于是「这个需求是哪几次提交做完的」可以直接反查
--   4. 分支/标签在 zt_repobranch
--
-- 三处有意偏离（都写在这里，免得后来人以为是漏了）：
--   a) **表名**：禅道 v21 起把这几张表从 zt_repo 改名成 ops_repo（devops 域）。本实现仍用
--      zt_ 前缀，一是与其余 50 张表一致，二是两个静态预检脚本按 `table_name LIKE 'zt%'` 扫表。
--   b) **只做「本地 Git 仓库」这一种来源**：禅道的 GitLab/Gitea/Gitea/SVN 走 module/provider +
--      module/gitlab 等一整套「代码服务商」体系（本实现未迁移），这里只支持配置一个本地路径、
--      用 `git log` 同步。这样「提交 → 对象」的链路是完整可验证的，接服务商 API 属于接入层。
--   c) **不做在线代码浏览/Blame/Diff 文件内容渲染**（禅道 repo/view|browse|blame|monaco 那一套），
--      只落「提交 + 改动文件」；要看代码请直接在 Git 客户端里看。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_repo` (
  `id`               bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`             varchar(255) NOT NULL DEFAULT '' COMMENT '代码库名称（唯一）',
  `product`          varchar(255) NOT NULL DEFAULT '' COMMENT '关联产品，逗号分隔',
  `scmType`          varchar(10)  NOT NULL DEFAULT 'git' COMMENT '源码管理类型，本实现只支持 git',
  `path`             varchar(500) NOT NULL DEFAULT '' COMMENT '本地仓库路径（禅道是远程地址+凭据，本实现读本地 git 仓库）',
  `defaultBranch`    varchar(255) NOT NULL DEFAULT '' COMMENT '默认分支',
  `desc`             varchar(500) NOT NULL DEFAULT '' COMMENT '代码库描述',
  `acl`              varchar(30)  NOT NULL DEFAULT 'open' COMMENT '权限：open 公开 / private 私有',
  `status`           varchar(30)  NOT NULL DEFAULT 'active' COMMENT '状态：active 正常 / closed 关闭',
  `synced`           tinyint unsigned NOT NULL DEFAULT 0 COMMENT '是否已同步过',
  `lastSyncRevision` varchar(40)  NOT NULL DEFAULT '' COMMENT '最近同步到的 revision',
  `lastSyncDate`     datetime DEFAULT NULL COMMENT '最近同步时间',
  `lastSyncCount`    int unsigned NOT NULL DEFAULT 0 COMMENT '最近一次同步进来的提交数',
  `createdBy`        varchar(30)  NOT NULL DEFAULT '',
  `createdDate`      datetime DEFAULT NULL,
  `editedBy`         varchar(30)  NOT NULL DEFAULT '',
  `editedDate`       datetime DEFAULT NULL,
  `deleted`          tinyint unsigned NOT NULL DEFAULT 0,
  -- 下面四列来自框架的 BaseDO（create_time/update_time/creator/updater）：
  -- 本项目其余的 zt_* 表也都有这四列，DO 继承 BaseDO 就必须建
  -- （踩过：漏了会报 Unknown column 'create_time'）
  `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creator`          varchar(64) NOT NULL DEFAULT '',
  `updater`          varchar(64) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码库';

CREATE TABLE IF NOT EXISTS `zt_repohistory` (
  `id`        bigint unsigned NOT NULL AUTO_INCREMENT,
  `repo`      bigint unsigned NOT NULL DEFAULT 0 COMMENT '代码库编号',
  `revision`  varchar(40) NOT NULL DEFAULT '' COMMENT 'commit sha',
  `commit`    int unsigned NOT NULL DEFAULT 0 COMMENT '自增序号（禅道用它做「第几次提交」，按同步顺序编号）',
  `comment`   text DEFAULT NULL COMMENT '提交说明',
  `committer` varchar(100) NOT NULL DEFAULT '' COMMENT '提交者',
  `time`      datetime DEFAULT NULL COMMENT '提交时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_repo_revision` (`repo`, `revision`),
  KEY `idx_repo` (`repo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码库提交记录';

CREATE TABLE IF NOT EXISTS `zt_repofiles` (
  `id`       bigint unsigned NOT NULL AUTO_INCREMENT,
  `repo`     bigint unsigned NOT NULL DEFAULT 0 COMMENT '代码库编号',
  `revision` varchar(40) NOT NULL DEFAULT '' COMMENT 'commit sha',
  `path`     varchar(500) NOT NULL DEFAULT '' COMMENT '文件路径',
  `oldPath`  varchar(500) NOT NULL DEFAULT '' COMMENT '重命名前的路径',
  `type`     varchar(20) NOT NULL DEFAULT '' COMMENT '类型：file/dir',
  `action`   char(1) NOT NULL DEFAULT '' COMMENT '动作：A 新增 / M 修改 / D 删除 / R 重命名',
  PRIMARY KEY (`id`),
  KEY `idx_repo_revision` (`repo`, `revision`),
  KEY `idx_path` (`path`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码库提交改动的文件';

CREATE TABLE IF NOT EXISTS `zt_relation` (
  `id`       bigint unsigned NOT NULL AUTO_INCREMENT,
  `AType`    varchar(30) NOT NULL DEFAULT '' COMMENT 'A 端类型，提交关系里恒为 revision',
  `AID`      bigint unsigned NOT NULL DEFAULT 0 COMMENT 'A 端编号（zt_repohistory.id）',
  `relation` varchar(30) NOT NULL DEFAULT '' COMMENT '关系名，提交关系里恒为 commit',
  `BType`    varchar(30) NOT NULL DEFAULT '' COMMENT 'B 端类型：story/bug/task/...',
  `BID`      bigint unsigned NOT NULL DEFAULT 0 COMMENT 'B 端编号',
  `product`  bigint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_relation` (`AType`, `AID`, `relation`, `BType`, `BID`),
  KEY `idx_b` (`BType`, `BID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用关系表（禅道原样）';

-- 代码库的菜单与权限（前端 v-hasPermi 判定，没有菜单行按钮会被隐藏，坑位 #43）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90159, '代码库',   'zentao:repo:query',  2, 22, 90001, 'repo', 'ep:folder-opened', 'zentao/repo/index', 'ZentaoRepo', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90160, '代码库查询', 'zentao:repo:query',  3, 1, 90159, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90161, '代码库创建', 'zentao:repo:create', 3, 2, 90159, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90162, '代码库修改', 'zentao:repo:update', 3, 3, 90159, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90163, '代码库删除', 'zentao:repo:delete', 3, 4, 90159, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90164, '同步提交',   'zentao:repo:sync',   3, 5, 90159, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

-- ---------------------------------------------------------------------------
-- 应用接入（entry）：第三方系统免登录调禅道
--
-- 禅道语义（module/entry 2 个 action 的 CRUD + common::checkEntry 的校验链）：
--   1. 一张 zt_entry 描述「哪个第三方应用可以进来、以谁的身份进来、从哪些 IP 进来」
--   2. 两种签名（禅道 task #5384 之后）：
--        time  : token = md5(code + key + time)，且 time 必须 > calledTime（防重放），通过后回写 calledTime
--        query : token = md5(md5(queryString 去掉 token) + key)
--      第一种失败会继续尝试第二种（且 queryString 里仍含 time），这是禅道的 fallthrough
--   3. 校验链的顺序与错误码（禅道 module/entry/config.php 的 errcode 原样）：
--        缺 code / 缺 token / 没配 key / token 不对           → 401
--        IP 不在白名单 / 非免密又没绑账号                     → 403
--        应用不存在                                           → 404
--        时间戳不大于 calledTime（重放）                      → 405
--        账号在用户表里不存在                                 → 406
--        时间戳格式不对（截断后非 10 位或首位 >= '4'）        → 407
--   4. 调用日志落在**通用**表 zt_log（objectType='entry'），禅道只有 entry 与 webhook 用它
--
-- 两处**有意偏离**（README 3.43 有说明）：
--   a. 校验通过后**不建立登录会话**：禅道直接 session->set('user') 完成免密登录；
--      yudao 的认证由 OAuth2 统一负责，业务模块不自己写会话。本接口只做「校验 + 记账 + 返回账号」。
--   b. 多了一个管理端助手 /sign 帮管理员算 token（需要 zentao:entry:query 权限）。
--
-- 表结构照抄 db/zentao.sql，只补框架需要的 create_time / update_time / creator / updater
-- （zt_log 是只增不改的事实表，**不补**框架列，DO 也不继承 BaseDO）。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_entry` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`        varchar(50)  NOT NULL DEFAULT '' COMMENT '应用名称',
  `account`     varchar(30)  NOT NULL DEFAULT '' COMMENT '绑定账号（免密登录的对象）',
  `code`        varchar(20)  NOT NULL DEFAULT '' COMMENT '应用代号（字母或数字的组合，唯一）',
  `key`         char(32)     NOT NULL DEFAULT '' COMMENT '密钥。列名 key 是 MySQL 关键字，代码里要加反引号',
  `freePasswd`  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '是否免密登录：1 开启',
  `ip`          varchar(100) NOT NULL DEFAULT '' COMMENT '允许的来源 IP：留空=不限制（禅道全局默认 *）、逗号列表、a-b 区间、192.168.1.*、CIDR',
  `desc`        mediumtext DEFAULT NULL COMMENT '描述。列名 desc 是 MySQL 关键字',
  `createdBy`   varchar(30)  NOT NULL DEFAULT '' COMMENT '创建人账号',
  `createdDate` datetime     DEFAULT NULL COMMENT '创建时间',
  `calledTime`  int unsigned NOT NULL DEFAULT 0 COMMENT '最近一次调用请求的时间戳（防重放）',
  `editedBy`    varchar(30)  NOT NULL DEFAULT '' COMMENT '最后编辑人账号',
  `editedDate`  datetime     DEFAULT NULL COMMENT '最后编辑时间',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creator`     varchar(64)  NOT NULL DEFAULT '',
  `updater`     varchar(64)  NOT NULL DEFAULT '',
  `deleted`     tinyint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用接入（禅道 zt_entry）';

CREATE TABLE IF NOT EXISTS `zt_log` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `objectType`  varchar(30) NOT NULL DEFAULT '' COMMENT '对象类型，应用接入恒为 entry',
  `objectID`    bigint unsigned NOT NULL DEFAULT 0 COMMENT '对象编号（zt_entry.id）',
  `action`      int unsigned NOT NULL DEFAULT 0 COMMENT '动作（禅道保留字段）',
  `date`        datetime DEFAULT NULL COMMENT '请求时间。列名 date 是 MySQL 关键字',
  `url`         varchar(255) NOT NULL DEFAULT '' COMMENT '被调用的 URL',
  `contentType` varchar(30) NOT NULL DEFAULT '' COMMENT '内容类型（禅道保留字段）',
  `data`        text DEFAULT NULL COMMENT '请求数据（禅道保留字段）',
  `result`      text DEFAULT NULL COMMENT '结果（禅道原表没有这一列的用途，本实现写入校验结果摘要）',
  PRIMARY KEY (`id`),
  KEY `idx_objectType` (`objectType`),
  KEY `idx_objectID` (`objectID`),
  KEY `idx_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用日志（禅道 zt_log，目前 entry 在用）';

-- 演示数据：覆盖「普通应用 / 免密应用 / 只看列表不命中的 IP 段应用 / 内置 gitfox / 绑定不存在的账号」
DELETE FROM `zt_entry` WHERE `code` IN ('oa', 'portal', 'restricted', 'gitfox', 'ghost');
INSERT INTO `zt_entry` (`name`, `account`, `code`, `key`, `freePasswd`, `ip`, `desc`, `createdBy`, `createdDate`, `calledTime`)
VALUES
('OA 办公系统', 'admin', 'oa',         'c0f0e1d2a3b4c5d6e7f8091a2b3c4d5e', 0, '*',              '走 time 模式签名，绑定 admin', 'admin', NOW(), 0),
('门户免密进入', 'admin', 'portal',     'a1b2c3d4e5f60718293a4b5c6d7e8f90', 1, '127.0.0.1,192.168.1.*', '免密登录：可带 account 指定账号', 'admin', NOW(), 0),
('内网受限应用', 'admin', 'restricted', 'ffeeddccbbaa99887766554433221100', 0, '10.0.0.0/8',      '只在 10.0.0.0/8 内可调', 'admin', NOW(), 0),
('gitfox',      'admin', 'gitfox',      '0000000000000000000000000000gitf', 0, '*',              '内置代码库接入，列表里不显示（禅道 getList 过滤）', 'admin', NOW(), 0),
('绑定了不存在的账号', 'ghost', 'ghost', '1234567890abcdef1234567890abcdef', 0, '*',              '用于验证 406 INVALID_ACCOUNT', 'admin', NOW(), 0);

DELETE FROM `zt_log` WHERE `objectType` = 'entry';
INSERT INTO `zt_log` (`objectType`, `objectID`, `date`, `url`, `result`)
SELECT 'entry', `id`, NOW() - INTERVAL 30 MINUTE, '/index.php?m=user&f=apilogin&account=admin', 'success:time' FROM `zt_entry` WHERE `code` = 'oa';
INSERT INTO `zt_log` (`objectType`, `objectID`, `date`, `url`, `result`)
SELECT 'entry', `id`, NOW() - INTERVAL 5 MINUTE,  '/index.php?m=user&f=apilogin&account=admin', 'success:query' FROM `zt_entry` WHERE `code` = 'portal';

-- 菜单与权限：90170 列表页 + 4 个按钮权限（type=3 的行不能省，否则 v-hasPermi 的按钮不显示，坑位 #43）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90170, '应用接入',   'zentao:entry:query',  2, 24, 90001, 'entry', 'ep:connection', 'zentao/entry/index', 'ZentaoEntry', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90171, '应用接入查询', 'zentao:entry:query',  3, 1, 90170, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90172, '应用接入创建', 'zentao:entry:create', 3, 2, 90170, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90173, '应用接入修改', 'zentao:entry:update', 3, 3, 90170, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90174, '应用接入删除', 'zentao:entry:delete', 3, 4, 90170, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

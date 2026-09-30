-- ---------------------------------------------------------------------------
-- 公司信息（company）—— 禅道界面上叫「组织视图」
--
-- 禅道语义（module/company：control 211 行 + model 132 行 + zen 177 行、5 个 action）：
--   1. 一张 zt_company 撑起「公司信息」：name/phone/fax/address/zipcode/website/backyard/guest/admins
--   2. admins 是**逗号串**（安装时写的是 ",admin,"），禅道判超管的唯一依据：
--        strpos($this->app->company->admins, ",{$user->account},") !== false
--      yudao 侧超管是 super_admin 角色，而且是**硬编码放行、不查权限表**（坑位 #24）——
--      两套口径必须人工对齐，所以本实现给了一个对照接口 GET /zentao/company/admins。
--   3. getFirst() 取 id 最小的一条当「本公司」；getOutsideCompanies() 就是 id != 1，
--      服务的是「外部干系人的所属公司」：加外部人员时禅道会往 zt_user 里建一条 type=outside 的记录，
--      zt_user.company 指向这里，公司不存在还能顺手新建一条。
--   4. update 的两条规则都照抄：name 必填 + unique（**不过滤已删除**，同 entry.code）；
--      website/backyard 如果正好等于 "http://" 就清空（表单预填值，不清会留下没有域名的 http://）。
--   5. 另外两个 action 不重复实现（同 my 模块的做法）：
--        browse（按部门/内部外部看用户） → organization 模块的 user-list + dept-tree
--        dynamic（组织动态，全公司 feed）→ action 模块的 /zentao/action/dynamic
--   6. 有意不提供 delete：禅道 module/company 没有删除 action（多公司是付费版能力，
--      开源版里 id != 1 的公司由「加外部干系人时顺手新建」产生），免得误删被引用的公司。
--
-- 表结构照抄 db/zentao.sql，只补框架需要的 create_time/update_time/creator/updater。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_company` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`        varchar(120) NOT NULL DEFAULT '' COMMENT '公司名称（必填、唯一，且唯一性不过滤已删除）',
  `phone`       varchar(20)  NOT NULL DEFAULT '' COMMENT '联系电话',
  `fax`         varchar(20)  NOT NULL DEFAULT '' COMMENT '传真',
  `address`     varchar(120) NOT NULL DEFAULT '' COMMENT '通讯地址',
  `zipcode`     varchar(10)  NOT NULL DEFAULT '' COMMENT '邮政编码',
  `website`     varchar(120) NOT NULL DEFAULT '' COMMENT '官网（只填 http:// 会被清空）',
  `backyard`    varchar(120) NOT NULL DEFAULT '' COMMENT '内网地址（同上）',
  `guest`       tinyint unsigned NOT NULL DEFAULT 0 COMMENT '是否允许匿名登录：1 允许（yudao 侧无此机制，仅存放）',
  `admins`      varchar(255) NOT NULL DEFAULT '' COMMENT '管理员账号逗号串，如 ,admin, —— 禅道判超管的依据，公司编辑表单里不出现这一列',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `creator`     varchar(64)  NOT NULL DEFAULT '',
  `updater`     varchar(64)  NOT NULL DEFAULT '',
  `deleted`     tinyint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司信息（禅道 zt_company，界面叫组织视图）';

-- 演示数据：id=1 本公司（禅道约定：1 是本公司，getOutsideCompanies 就是 id != 1），
-- 另外两家是外部公司，专门给「外部干系人所属公司」下拉用
INSERT INTO `zt_company` (`id`, `name`, `phone`, `fax`, `address`, `zipcode`, `website`, `backyard`, `guest`, `admins`)
VALUES
(1, '示例科技有限公司', '0532-88886666', '0532-88886667', '山东省青岛市崂山区示例路 1 号', '266100', 'https://www.example.com', 'http://192.168.0.10', 0, ',admin,'),
(2, '甲方信息科技有限公司', '010-66668888', '', '北京市海淀区中关村示例大厦 8 层', '100080', '', '', 0, ''),
(3, '乙方软件服务有限公司', '021-55556666', '', '上海市浦东新区示例园区 3 号楼', '200120', '', '', 0, '')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `phone` = VALUES(`phone`), `fax` = VALUES(`fax`),
    `address` = VALUES(`address`), `zipcode` = VALUES(`zipcode`), `website` = VALUES(`website`),
    `backyard` = VALUES(`backyard`), `guest` = VALUES(`guest`), `admins` = VALUES(`admins`),
    `deleted` = 0;

-- 菜单与权限：90175 页面 + 3 个按钮权限（type=3 的行不能省，否则 v-hasPermi 的按钮不显示，坑位 #43）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90175, '公司信息',   'zentao:company:query',  2, 25, 90001, 'company', 'ep:office-building', 'zentao/company/index', 'ZentaoCompany', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90176, '公司查询',   'zentao:company:query',  3, 1, 90175, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90177, '公司创建',   'zentao:company:create', 3, 2, 90175, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90178, '公司修改',   'zentao:company:update', 3, 3, 90175, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

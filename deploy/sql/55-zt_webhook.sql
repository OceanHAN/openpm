-- ---------------------------------------------------------------------------
-- Webhook（webhook）—— 禅道「事件外发的通用出口」
--
-- 禅道语义（module/webhook：control 347 行 + model 914 行 + zen 92 行 + tao 135 行、10 个 action）：
--   业务动作（新增/编辑/关闭需求、任务、缺陷……）发生后，由 message 模块触发 webhook：
--   遍历所有启用的 zt_webhook，用 buildData() 把这次 zt_action 的字段按 params 拼成 JSON，
--   HTTP POST 到用户配置的 URL，结果写进通用日志表 zt_log（objectType='webhook'）。
--
-- 【建表范围】只建 zt_webhook 1 张（禅道 db/zentao.sql:2407 逐列对齐）。
--   不建日志表：禅道把发送日志写进**通用**日志表 zt_log（zen 里 TABLE_LOG），
--   而 zt_log 已经由 entry 模块建好（deploy/sql/49-zt_entry.sql），本模块只插行，不重复造表。
--   不建 zt_notify 异步队列：sendType=async 在禅道里靠 zt_notify + cron(*/1 * * * * webhook.asyncSend)
--   消费，本项目不重造调度器（见 README 与 WebhookService 类注释的偏离①）。
--   不建 zt_oauth：dinguser/wechatuser/feishuuser 三种「应用消息」需要企业应用凭据 + openID 绑定，
--   属于外部系统适配，本实现未迁移（偏离②）。
--   不建 zt_ai_task：TABLE_AI_TASK 在禅道开源版连常量都没定义（全仓只有 2 处使用），是「aitask」
--   对象类型的死支路，不影响主流程（见 REMAINING-MODULE-VERDICTS 的 webhook 一节）。
--
-- 【保留字】desc 是 MySQL 关键字，列名必须加反引号（坑位 #10）；DO 里用 @TableField("`desc`")。
-- 【框架列】禅道原表已有 deleted，所以补上 create_time/update_time/creator/updater，
--   DO 继承 BaseDO；禅道自己的 createdBy/createdDate/editedBy/editedDate 是**另四列**，两者并存。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `zt_webhook` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Webhook 编号',
  `type`        varchar(15)  NOT NULL DEFAULT 'default' COMMENT '类型：default/dinggroup/wechatgroup/feishugroup（dinguser/wechatuser/feishuuser 需要企业应用凭据，本实现不投递）',
  `name`        varchar(50)  NOT NULL DEFAULT '' COMMENT '名称（必填，禅道 config/webhook.php requiredFields）',
  `url`         varchar(255) NOT NULL DEFAULT '' COMMENT 'Hook 地址。禅道要求 ^http(s)?://；编辑态必填、创建态可空',
  `domain`      varchar(255) NOT NULL DEFAULT '' COMMENT '禅道域名（拼查看链接用；空则回落到站内前端地址）',
  `secret`      varchar(255) NOT NULL DEFAULT '' COMMENT '加签密钥（钉钉群/飞书群）',
  `contentType` varchar(30)  NOT NULL DEFAULT 'application/json' COMMENT '内容类型。群机器人类在 fetchHook 里被强制成 application/json',
  `sendType`    varchar(10)  NOT NULL DEFAULT 'sync' COMMENT '发送方式：sync/async。禅道 async 落 zt_notify 由 cron 消费，本实现直接发',
  `products`    text         DEFAULT NULL COMMENT '只对这些产品发（逗号列表，与动作行 product 取交集；空 = 不限）',
  `executions`  text         DEFAULT NULL COMMENT '只对这个执行发（逗号包裹后子串匹配；空 = 不限）',
  `params`      varchar(100) NOT NULL DEFAULT '' COMMENT 'payload 字段列表（逗号），必然含 text —— text 是现拼的「动作文本+查看链接」',
  `actions`     text         DEFAULT NULL COMMENT '本 webhook 关注的对象类型+动作（JSON，如 {"story":["opened"]}）；空 = 用禅道白名单全量',
  `desc`        text         DEFAULT NULL COMMENT '描述（desc 是 MySQL 关键字，必须加反引号）',
  `createdBy`   varchar(30)  NOT NULL DEFAULT '' COMMENT '禅道侧创建人账号',
  `createdDate` datetime     DEFAULT NULL COMMENT '禅道侧创建时间',
  `editedBy`    varchar(30)  NOT NULL DEFAULT '' COMMENT '禅道侧最后编辑人账号',
  `editedDate`  datetime     DEFAULT NULL COMMENT '禅道侧最后编辑时间',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '框架侧创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '框架侧更新时间',
  `creator`     varchar(64)  NOT NULL DEFAULT '' COMMENT '框架侧创建者',
  `updater`     varchar(64)  NOT NULL DEFAULT '' COMMENT '框架侧更新者',
  `deleted`     tinyint unsigned NOT NULL DEFAULT 0 COMMENT '是否删除（禅道原表列，逻辑删）',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook（禅道 zt_webhook，事件外发的通用出口）';

-- ---------------------------------------------------------------------------
-- 演示数据
--   92200「本地联调接收端」：指向本项目**自己的 mock 接收端点**
--     /admin-api/zentao/webhook/mock-receive（在 ZentaoWebhookMockController 里），
--     这样端到端回归不用任何外部系统：配置 → 触发发送 → 断言收到的 payload 与 zt_log。
--   92201「演示：不可达地址」：指向 127.0.0.1:1（必然连不上），
--     用来验证「发送失败被记进 zt_log、但接口仍返回成功、不影响业务」。
--
-- 演示动作行（99200 / 99201）也在这里补：禅道是「先有动作、再有 webhook」，
-- buildData 的第一件事就是按 actionID 读 zt_action（actionID 可省，但演示时直观）。
-- 注意 id 用 99200+ 的高位段，避开历史测试留下的软删除行（坑位 #23）。
-- ---------------------------------------------------------------------------
DELETE FROM `zt_webhook` WHERE `id` IN (92200, 92201);

INSERT INTO `zt_webhook` (`id`, `type`, `name`, `url`, `domain`, `secret`, `contentType`, `sendType`,
                          `products`, `executions`, `params`, `actions`, `desc`,
                          `createdBy`, `createdDate`, `editedBy`, `editedDate`, `creator`, `updater`, `deleted`)
VALUES
(92200, 'default', '本地联调接收端',
 'http://127.0.0.1:48080/admin-api/zentao/webhook/mock-receive', '', '',
 'application/json', 'sync',
 '1', '', 'objectType,objectID,product,action,actor,date,comment,text',
 '{"story":["opened","edited","changed","closed","activated"],"task":["opened","edited","closed"]}',
 '演示用：指向本项目自带的 mock 接收端点，端到端回归断言 payload 用',
 'admin', NOW(), 'admin', NOW(), 'admin', 'admin', b'0'),
(92201, 'default', '演示：不可达地址（发送失败要落日志）',
 'http://127.0.0.1:1/zentao/unreachable', '', '',
 'application/json', 'sync',
 '1', '', 'objectType,objectID,text',
 '{"story":["opened","edited"]}',
 '演示用：必然连不上，用来验证「失败写 zt_log、接口仍返回成功、业务不回滚」',
 'admin', NOW(), 'admin', NOW(), 'admin', 'admin', b'0')
ON DUPLICATE KEY UPDATE
    `type` = VALUES(`type`), `name` = VALUES(`name`), `url` = VALUES(`url`), `domain` = VALUES(`domain`),
    `secret` = VALUES(`secret`), `contentType` = VALUES(`contentType`), `sendType` = VALUES(`sendType`),
    `products` = VALUES(`products`), `executions` = VALUES(`executions`), `params` = VALUES(`params`),
    `actions` = VALUES(`actions`), `desc` = VALUES(`desc`), `deleted` = 0;

-- 演示用的三条动作行（对应禅道 zt_action）：
--   99200：需求 1「支持需求批量导入」的 opened 动作（product='1'，命中 92200 的 products 过滤）
--   99201：需求 4「支持需求批量导入（含校验）」的 edited 动作
--   99202：需求 92201「批量导入-解析 Excel」的 edited 动作 —— **给测试脚本的稳定场景**：
--          它不会被其它模块的演示/测试数据改到，所以「按对象+动作取最新一条」拿到的就是它。
DELETE FROM `zt_action` WHERE `id` IN (99200, 99201, 99202);
INSERT INTO `zt_action` (`id`, `objectType`, `objectID`, `product`, `project`, `execution`, `actor`, `action`,
                         `date`, `comment`, `extra`, `read`, `vision`, `efforted`, `creator`, `updater`)
VALUES
(99200, 'story', 1, '1', 1, 90001, 'admin', 'opened', NOW(), '演示：新增需求', '', 0, 'rnd', 0, 'admin', 'admin'),
(99201, 'story', 4, '1', 1, 90001, 'admin', 'edited', NOW(), '演示：编辑需求', '', 0, 'rnd', 0, 'admin', 'admin'),
(99202, 'story', 92201, '1', 1, 90001, 'admin', 'edited', NOW(), '演示/测试：编辑批量导入需求', '', 0, 'rnd', 0, 'admin', 'admin');

-- ---------------------------------------------------------------------------
-- 菜单与权限：90200 页面 + 90201~90204 四个 type=3 的按钮权限
--   type=3 的行**不能省**（坑位 #43）：前端 v-hasPermi 是拿「角色 → 菜单」算出来的权限串，
--   没有这两类行，按钮会被直接隐藏 —— 而接口回归因为超管硬编码放行会全绿，发现不了。
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`)
VALUES
(90200, 'Webhook 管理', 'zentao:webhook:query',  2, 26, 90001, 'webhook', 'ep:connection', 'zentao/webhook/index', 'ZentaoWebhook', 0, b'1', b'1', b'1', 'admin', 'admin'),
(90201, 'Webhook 查询', 'zentao:webhook:query',  3, 1, 90200, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90202, 'Webhook 创建', 'zentao:webhook:create', 3, 2, 90200, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90203, 'Webhook 修改', 'zentao:webhook:update', 3, 3, 90200, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin'),
(90204, 'Webhook 删除', 'zentao:webhook:delete', 3, 4, 90200, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
    `type` = VALUES(`type`), `sort` = VALUES(`sort`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`);

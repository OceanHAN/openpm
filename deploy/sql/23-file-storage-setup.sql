-- ---------------------------------------------------------------------------
-- 附件存储的环境准备（不建禅道表，只调 yudao 的文件配置）
--
-- 【为什么需要这一步】
--   yudao 的文件能力是「配置驱动」的：infra_file_config 里存多套存储（本地/DB/S3/SFTP…），
--   其中 master=1 的那套生效。yudao 初始化的示例数据里，master 是「七牛存储器（示例）」，
--   指向 http://test.yudao.iocoder.cn —— 一个不存在的演示域名。于是：
--     - 上传「成功」但字节其实没落到可用存储
--     - 返回的 URL 也打不开
--   本项目的禅道附件直接复用 yudao 的文件服务（不自己写一套存储），
--   所以先把 master 切到「数据库（示例）」(id=4)。
--   注意 domain 只要写「主机」即可：yudao 的 AbstractFileClient.formatFileUrl() 会自己拼成
--     {domain}/admin-api/infra/file/{configId}/get/{path}
--   （第一次我在这里多写了一段 /admin-api/infra/file/4/get，结果 URL 出现两次前缀，直接 404。）
--   端点带 @PermitAll，所以返回的 URL 能被浏览器/curl 直接打开。
--
--   用「数据库」存储而不是「本地文件」存储，是为了让演示环境不依赖宿主机目录，
--   字节存在 infra_file.content 里，换机器也能跑。
--
-- 【这是迁移里的一个真实决策】
--   禅道 zt_file 自己管文件（upload 目录 + pathname），本实现**不搬存储**，
--   只保留禅道侧的元数据（objectType/objectID/title/downloadCount…），
--   字节交给 yudao 的文件服务 —— 一套系统里只有一个存储后端，避免出现两套。
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

UPDATE `infra_file_config` SET `master` = b'0' WHERE `id` <> 4;
UPDATE `infra_file_config`
   SET `master` = b'1',
       `config` = '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig","domain":"http://127.0.0.1:48080"}'
 WHERE `id` = 4;

-- 说明：改完配置需要重启后端（文件配置有缓存），否则仍会走旧的 master。

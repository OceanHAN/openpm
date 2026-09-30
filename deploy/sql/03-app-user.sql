-- ---------------------------------------------------------------------------
-- 创建 yudao 业务专用账号（不使用 root 直连）
-- 由 MySQL 容器首次初始化时自动执行
-- ---------------------------------------------------------------------------
USE `ruoyi-vue-pro`;

CREATE USER IF NOT EXISTS 'yudao'@'%' IDENTIFIED BY 'CHANGE_ME'   -- 换成 deploy/_secrets.sh 里的 MYSQL_PASS;

-- 业务账号只需本库权限
GRANT ALL PRIVILEGES ON `ruoyi-vue-pro`.* TO 'yudao'@'%';

-- 供 Quartz 集群表使用（同一库内，无需额外授权）

FLUSH PRIVILEGES;

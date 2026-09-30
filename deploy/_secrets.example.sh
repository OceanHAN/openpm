# ---------------------------------------------------------------------------
# 口令模板：复制成 deploy/_secrets.sh（**已被 .gitignore 忽略，不要提交**）后填真实值。
# 所有脚本都从这里取口令，仓库里不再出现明文：
#   source deploy/_secrets.sh && bash deploy/resume-verification-remote.sh
# ---------------------------------------------------------------------------
export MYSQL_PASS='CHANGE_ME_mysql_root_pass'      # MySQL root / 应用账号口令
export REDIS_PASS='CHANGE_ME_redis_pass'           # Redis 口令
export SSH_PASS='CHANGE_ME_ssh_pass'               # 旧 183 时代用的 SSH 口令（119 已改密钥免密）

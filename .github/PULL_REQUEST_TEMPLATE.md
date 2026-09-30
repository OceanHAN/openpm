## 做了什么

<!-- 一句话说清改动范围；涉及禅道规则的请写出处（文件 + 逻辑） -->

## 怎么验证的

<!-- 贴跑过的命令与结果，例如：
ZENTAO_API_BASE=http://127.0.0.1:48080/admin-api bash deploy/test-story-module.sh
  → 通过 55 项，失败 0 项
-->

## 检查清单

- [ ] 后端能编译：`cd ruoyi-vue-pro && mvn -pl yudao-server -am package -DskipTests`
- [ ] 新增/修改了 `zt_*` 表时，三个预检都跑过：`check-reserved-columns.sh` / `check-camel-columns.sh` / `check-tenant-ignore.sh`
- [ ] 与改动相关的接口回归脚本通过；新增模块已配套 `deploy/test-<模块>-module.sh` 与 `deploy/ui-check/<模块>.mjs`
- [ ] 没有把口令、Token、内网地址写进代码或文档（口令走环境变量与 `deploy/_secrets.sh`）
- [ ] 有意偏离禅道的地方已在 PR 描述与 `docs/IMPLEMENTATION-NOTES.md` 里写明

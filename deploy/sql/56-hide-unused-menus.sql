-- 56-hide-unused-menus.sql
-- 隐藏「没有编进 jar」的顶层菜单，让侧边栏只留本项目真正在用的三块：
--   系统管理（/system）、基础设施（/infra）、禅道（/zentao，自研迁移模块）
--
-- 为什么隐藏而不是删：
--   1) yudao-server/pom.xml 里除 system / infra / zentao 外的模块依赖**全部是注释状态**，
--      点这些菜单实测返回：{"code":501,"msg":"[CRM 模块 yudao-module-crm - 已禁用][参考 … 开启]"}
--      （hrm / fms / oa / member / mall / cms 连控制器都没有，返回 404）。留着就是死菜单。
--   2) `MenuServiceImpl.filterDisableMenus()` 是**递归**判定：父菜单 status=1（停用）时，
--      其下所有子菜单会一起被过滤掉 —— 所以只改顶层这一行就够，不需要动子菜单。
--   3) 可逆：要恢复只把 status 改回 0。要真正启用某个模块，是「打开 pom 依赖 + 建它的表 +
--      给角色分配菜单权限 + 重新 build」，不是靠改这行。
--
-- 保留（**不要**动）：1 /system、2 /infra、90001 /zentao
-- 其中 148 工作流程(bpm)、207 报表(report)、791 AI、1418 IM、1476 HRM、8000 PMS、373 会员
-- 是审计里判 A/B 类「yudao 已有等价能力」时引用的承载方，属于**候选**，现在同样隐藏，
-- 等真正要用某个能力时再启用对应模块并把这一行 status 改回 0。
--
-- 幂等：重复执行结果一致。

UPDATE `ruoyi-vue-pro`.system_menu
SET status = 1, update_time = NOW()
WHERE parent_id = 0
  AND id IN (
      114,   -- 支付管理      /pay       yudao-module-pay
      148,   -- 工作流程      /bpm       yudao-module-bpm（候选：禅道 approval）
      194,   -- 作者动态      https://www.iocoder.cn（框架作者外链）
      207,   -- 报表管理      /report    yudao-module-report（候选：禅道 pivot/screen）
      272,   -- MP公众号管理   /mp        yudao-module-mp
      347,   -- Boot开发文档   https://doc.iocoder.cn/
      348,   -- Cloud开发文档  https://cloud.iocoder.cn
      373,   -- 会员中心      /member    yudao-module-member（本项目积分已自建 score）
      449,   -- MALL商城系统   /mall      yudao-module-mall
      480,   -- CRM系统       /crm       yudao-module-crm
      597,   -- ERP系统       /erp       yudao-module-erp
      791,   -- AI大模型      /ai        yudao-module-ai（候选：禅道 ai/zai/aiapp）
      860,   -- IoT物联网     /iot       yudao-module-iot
      959,   -- MES系统       /mes       yudao-module-mes
      1348,  -- WMS系统       /wms       yudao-module-wms
      1418,  -- IM即时通讯     /im        yudao-module-im（候选：禅道 message 的一部分）
      1476,  -- HRM人力资源   /hrm       yudao-module-hrm（候选：禅道 personnel）
      1637,  -- CMS内容管理    /cms
      1894,  -- FMS财务管理   /fms       yudao-module-fms
      8000,  -- PMS项目管理   /pms       yudao-module-pms（候选：禅道 ppm/space）
      8200   -- OA办公协同    /oa
    );

-- 恢复（需要时手动执行；也可只恢复其中某一行）：
-- UPDATE `ruoyi-vue-pro`.system_menu SET status = 0, update_time = NOW()
-- WHERE parent_id = 0 AND id IN (114,148,194,207,272,347,348,373,449,480,597,791,860,959,1348,1418,1476,1637,1894,8000,8200);
--
-- 自检：应返回 3 行（系统管理 / 基础设施 / 禅道）
-- SELECT id, name, path, status FROM `ruoyi-vue-pro`.system_menu WHERE parent_id = 0 AND status = 0 ORDER BY sort;

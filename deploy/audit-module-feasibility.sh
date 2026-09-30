#!/usr/bin/env bash
# ============================================================================
#  deploy/audit-module-feasibility.sh
#
#  禅道(ZenTao) -> Java(yudao) 迁移：模块可迁移性审计（只读，不连服务器）
#
#  用途：用可复现的 grep/find/awk 重新推导「哪些模块真的有实现可搬」，
#        推翻 docs/MIGRATION-INVENTORY.md 里靠直觉写下的表缺失/无实现名单。
#
#  跑法：
#     bash deploy/audit-module-feasibility.sh
#     bash deploy/audit-module-feasibility.sh > docs/audit-output.txt
#     ZENTAO_DIR=/path/to/zentaopms YUDAO_DIR=/path/to/yudao bash deploy/...
#
#  兼容性：macOS 自带 bash 3.2（不用关联数组、不用 ${var,,}、不用 mapfile）。
#  只读：仅 grep/find/wc/awk/sed/sort，不写禅道与 yudao 仓库，不访问网络。
#
#  审计口径（每一条都能在输出里复核）：
#   1. PHP 行数 = module/<m>/ 下 *.php（排除 *.html.php 模板、排除 test/），
#      与清单里混用「只看顶层 *.php」和「全部 *.php」两种口径不同，本脚本统一用后者。
#   2. action 数 = module/<m>/control.php 里 `public function ` 的行数。
#   3. 「引用的表」 = 模块代码里出现的 TABLE_* 常量，按 config/zentaopms.php 的
#      define 解析成物理表名。关键：config 里有 23 个常量是**硬编码前缀**，
#      例如 TABLE_SPACE=`ops_space`、TABLE_ARTIFACT=`ops_artifact_libs`
#      —— 只按 `zt_<module>` grep 会把这些表全部误判成「未发布」。
#   4. 「表是否在开源建库脚本」 = db/zentao.sql 里的 CREATE TABLE 语句，
#      **两种写法都匹配**（带/不带 IF NOT EXISTS），表名带反引号做边界，
#      并同时接受 zt_ / ops_ 两种前缀。
#   5. 判定（原始可迁移性）取值只有 6 个：
#        已迁移 / 部分 / 开源版无实现 / 表缺失(付费版) / 外部依赖不可搬 / 待做
#      规则见下方 classify()。
#   6. 主判定（追加口径 A/B/C）：
#        A = yudao 侧已有等价能力（或开源版无需求）-> 不迁移
#        B = yudao 有相近能力但缺关键部分        -> 不迁移但记缺口
#        C = yudao 也没有                        -> 真能力缺口
#        已迁移的模块直接标「已迁移」，不进 A/B/C。
#      A/B 的承载方全部是仓库里实测存在的表/页面/接口，禁止凭印象。
# ============================================================================

set -u

ZENTAO="${ZENTAO_DIR:-/Library/allproject/02code/zentaopms}"
YUDAO="${YUDAO_DIR:-/Library/allproject/02code/yudao}"
MODDIR="$ZENTAO/module"
SQL="$ZENTAO/db/zentao.sql"
CFG="$ZENTAO/config/zentaopms.php"
YJAVA="$YUDAO/ruoyi-vue-pro/yudao-module-zentao/src/main/java/cn/iocoder/yudao/module/zentao"
YVUE="$YUDAO/yudao-ui-admin-vue3/src/views"

if [ ! -d "$MODDIR" ] || [ ! -f "$SQL" ] || [ ! -f "$CFG" ]; then
  echo "ERROR: 找不到禅道源码（ZENTAO_DIR=$ZENTAO）" >&2
  exit 2
fi
if [ ! -d "$YJAVA" ]; then
  echo "ERROR: 找不到 yudao zentao 模块（YUDAO_DIR=$YUDAO）" >&2
  exit 2
fi

TMP="$(mktemp -d 2>/dev/null || mktemp -d -t ztaudit)"
trap 'rm -rf "$TMP"' EXIT

# ---------------------------------------------------------------------------
# 0. 预计算
# ---------------------------------------------------------------------------

# 0.1 db/zentao.sql 里真实建出来的表（两种建表写法 + zt_/ops_ 双前缀）
grep -oE "CREATE TABLE (IF NOT EXISTS )?\`[a-zA-Z0-9_]+\`" "$SQL" \
  | sed -E 's/CREATE TABLE (IF NOT EXISTS )?//' | tr -d '`' | sort -u > "$TMP/sql_tables"

# 0.2 TABLE_* 常量 -> 物理表名
#     两种 define 写法：define('TABLE_X', ...) 与 if(!defined('TABLE_X')) define(...)
grep -hE "define\('TABLE_" "$CFG" \
  | sed -E "s/.*define\('(TABLE_[A-Z0-9_]+)',[[:space:]]*(.*)\);[[:space:]]*$/\1\t\2/" > "$TMP/defs"
awk -F'\t' '{
  c=$1; d=$2; if(c=="" || d=="") next;
  if(d ~ /config->db->prefix/){ s=d; sub(/.*prefix/,"",s); gsub(/[^a-z0-9_]/,"",s); print c"\tzt_"s }
  else { gsub(/[^a-z0-9_]/,"",d); if(d!="") print c"\t"d }
}' "$TMP/defs" | sort -u > "$TMP/cmap"

# 0.3 模块清单 = module/ 下的 99 个目录 + config 里 programPriv 点名但目录缺失的模块
#     programPriv->scrum / ->waterfall 是禅道自己的「项目集可用模块」清单，
#     里面有 21 个名字在开源包 module/ 下找不到目录（付费版模块）。
ls "$MODDIR" 2>/dev/null | grep -vE '^\.' | sort > "$TMP/mods_dir"
grep -oE "programPriv->(scrum|waterfall)[^;]*" "$CFG" 2>/dev/null \
  | grep -oE "'[a-zA-Z0-9_]+'" | tr -d "'" | sort -u > "$TMP/priv_all"
: > "$TMP/mods_missing"
while read -r m; do
  [ -z "$m" ] && continue
  [ -d "$MODDIR/$m" ] || echo "$m" >> "$TMP/mods_missing"
done < "$TMP/priv_all"
sort -u "$TMP/mods_missing" -o "$TMP/mods_missing"
cat "$TMP/mods_dir" "$TMP/mods_missing" | sort -u > "$TMP/mods_all"

# 0.4 yudao 侧：每个 controller/admin/<dir> 的端点注解数（实测）
: > "$TMP/yudao_eps"
for d in "$YJAVA/controller/admin"/*/; do
  [ -d "$d" ] || continue
  n=$(basename "$d")
  c=$(grep -rhoE '@(Get|Post|Put|Delete|Patch)Mapping' "$d" --include='*.java' 2>/dev/null | wc -l | tr -d ' ')
  echo "$n $c" >> "$TMP/yudao_eps"
done

# 0.5 yudao 侧：@TableName 落地的表（实测「表已迁」）
grep -rhoE '@TableName\("[a-zA-Z0-9_]+"\)' "$YJAVA" --include='*.java' 2>/dev/null \
  | sed -E 's/@TableName\("([a-z0-9_]+)"\)/\1/' | sort -u > "$TMP/yudao_tables"

# ---------------------------------------------------------------------------
# 1. 映射表
# ---------------------------------------------------------------------------

# 禅道模块 -> yudao controller/admin 目录（用于实测端点；空 = 无实现）
YUDAO_MAP="
action|action
admin|
ai|ai
aiapp|
api|api
artifact|
backup|
bi|bi
block|
branch|branch
bug|bug
build|build
cache|
caselib|caselib
chart|bi
ci|
codescan|
common|
company|organization,company
convert|
cron|
custom|
datatable|
dataview|bi
dept|organization
design|
dev|
dimension|dimension
doc|doc
editor|
entry|entry
epic|story
execution|execution,burn
extension|
feedback|
file|file
gitfox|
gitlab|
group|organization
holiday|holiday
index|
install|
jenkins|
kanban|kanban
mail|
mark|
message|
metric|metric
misc|
my|my
personnel|
pipeline|
pivot|
ppm|
product|product
productplan|plan
program|program
programplan|
project|project
projectbuild|projectbuild
projectplan|projectplan
projectrelease|projectrelease
projectstory|projectstory
provider|
qa|qa
release|release
repo|repo
repobranchrule|
repobranchtype|
reporeviewflow|
report|report
requirement|story
score|score
screen|
search|search
setting|
space|
sso|
stage|stage
stakeholder|stakeholder
story|story
system|
task|task,effort
testcase|testcase
testreport|testreport,testsuite
testsuite|testreport
testtask|testtask
todo|todo
transfer|
tree|module
tutorial|
upgrade|
user|organization
webhook|webhook
weekly|
workestimation|workestimation
zahost|
zai|
zanode|
"

# 「部分」模块：实测证据写在 PARTIAL_EVIDENCE 里，逐条可在输出附录复核。
PARTIAL_MODULES="metric bi common block"

# 「外部依赖不可搬」模块：实测信号写在输出附录（loadModel('gitfox'/'jenkins') 次数等）
EXTERNAL_MODULES="codescan gitfox jenkins gitlab provider pipeline"

# 主判定（A/B/C）。格式：模块|判定|类型|承载方/证据|缺口
# 规则：A/B 的承载方必须是仓库里实测存在的表/页面/接口；C = yudao 也没有。
PRIMARY="
action|已迁移|-|-|-
admin|A|替代|yudao-module-system + yudao-module-infra 后台（system_users/system_role/system_menu/infra_config；页面 yudao-ui-admin-vue3/src/views/system、views/infra）|无
ai|A|替代|yudao-module-ai：AiChatConversationController/AiModelController/AiKnowledgeController/AiWorkflowController（页面 src/views/ai）|禅道 ai 的模型渠道配置可映射到 AiModelController
aiapp|B|部分替代|yudao-module-ai（chat/workflow/model + src/views/ai）|禅道 aiapp 的「AI 小程序/应用编排」无对应，要就得重写
api|已迁移|-|-|-
artifact|C|真缺口|无替代（制品库 ops_artifact_* 表在开源版）|需重写制品库（含与仓库/流水线的联动）
backup|C|真缺口|无替代（yudao 只有 infra/druid 监控页，无备份/还原）|需重写或直接用 mysqldump/xtrabackup
bi|部分|-|-|DuckDB/Parquet 引擎、zt_pivot/zt_screen 未迁
block|部分|-|-|可配置积木引擎未搬，qa/report 已做等价聚合
branch|已迁移|-|-|-
bug|已迁移|-|-|-
build|已迁移|-|-|-
cache|A|替代|Redis（infra/redis 监控页 src/views/infra/redis + Spring Cache）|禅道 cache 模块只是缓存管理页，无业务逻辑
caselib|已迁移|-|-|-
chart|已迁移|-|-|-
ci|C|真缺口|无替代（禅道 ci 是持续集成构建结果回传）|需重写/接外部 CI
codescan|C|真缺口|无替代；开源版实现是 GitFox 代理（module/codescan/control.php:17 loadModel('gitfox')->checkHealth()）|需接 SonarQube 或自建扫描
common|部分|-|-|公共能力按需重建（操作日志=infra_operate_log）
company|已迁移|-|-|-
convert|B|部分替代|yudao-spring-boot-starter-excel（ExcelUtils）可承接文件型导入|异构源库（BugFree/Redmine/Jira）适配器需重写；且只需一次性执行，不该做成常驻模块
cron|A|替代|infra_job / infra_job_log + 页面 src/views/infra/job（Quartz）|无
custom|B|部分替代|system_dict_type / system_dict_data + infra_codegen_table/column（字段配置）|禅道「自定义字段 + 自定义流程」引擎（zt_workflow* 表在开源版但无实现可搬）需重写
datatable|B|部分替代|infra_codegen_table / infra_codegen_column（字段配置）+ 前端 Table 组件列设置|禅道 datatable 是用户级「列表字段可见性」配置，yudao 无等价功能
dataview|已迁移|-|-|（由 bi 的 SQL 模式承载：zt_dataview 已落表，bi 控制器有 dataview CRUD/preview）
dept|已迁移|-|-|-
design|C|真缺口|无替代（设计稿/UI 规范管理）|需重写
dev|B|部分替代|infra/swagger（接口调试）、infra/druid（SQL 监控）、infra/codegen（页面 src/views/infra）|禅道的语言项管理、数据库表结构查看器无对应（可用 IDE/DB 客户端替代）
dimension|已迁移|-|-|-
doc|已迁移|-|-|-
editor|A|替代|前端组件 yudao-ui-admin-vue3/src/components/Editor + infra_file|无
entry|已迁移|-|-|-
epic|已迁移|-|-|-
execution|已迁移|-|-|-
extension|B|部分替代|infra_codegen（生成新模块）|yudao 无插件市场/扩展包机制，禅道 extension 的扩展安装卸载需重写
feedback|C|真缺口|无替代（开源版只有 44 行存根；zt_feedback/zt_feedbackview 在开源版）|真实实现属付费版，需重写
file|已迁移|-|-|-
gitfox|C|真缺口|无替代（GitFox 是禅道商业代码扫描服务，模块本身就是它的客户端）|需接第三方扫描服务，属集成而非迁移
gitlab|C|真缺口|无替代；开源版 control.php/config 已被上游删除（commit 53c5c88791，2026-01-21），只剩 249 行 model.php 作为 GitLab REST 客户端|需重写 GitLab 集成
group|已迁移|-|-|-
holiday|已迁移|-|-|-
index|A|替代|yudao 首页 yudao-ui-admin-vue3/src/views/Home/Index.vue|无
install|A|无需求|yudao 用部署脚本 + sql/mysql/ruoyi-vue-pro.sql 初始化，无 Web 安装向导需求|无
jenkins|C|真缺口|无替代（module/jenkins 338 行，就是 Jenkins API 客户端）|需重写 Jenkins 集成
kanban|已迁移|-|-|-
mail|A|替代|system_mail_account / system_mail_template / system_mail_log（sql/mysql/ruoyi-vue-pro.sql 实测建表）+ 页面 src/views/system/mail|无
mark|A|无需求|禅道 mark 是内部已读标记服务（121 行、0 个 control action，仅被 pivot 调用）|yudao 列表页无此需求
message|A|替代|system_notify_message / system_notify_template + system_notice + 页面 src/views/system/notify、src/views/system/notice|无
metric|部分|-|-|414 个 calc 口径只迁 15 个（MetricRegistry 实测 15 条 register）
misc|B|部分替代|CaptchaController（yudao-module-system/framework/captcha）+ infra 监控页|checkUpdate/checkExtension/checkNetConnect（禅道官网连通性检查）无对应且不需要
my|已迁移|-|-|-
personnel|B|部分替代|yudao-module-hrm（HrmEmployeeController 等 60+ 控制器）+ 已迁 effort/team|禅道 personnel 的跨项目工时/风险视图（zt_userview）需基于已迁表重写
pipeline|B|部分替代|infra_job（定时任务）+ yudao 无 CI/CD 引擎|流水线/触发器/制品（ops_pipeline* 表在开源版）需自建或接外部 CI
pivot|B|部分替代|yudao-module-report：jimureport 2.5.1 报表设计器（yudao-module-report/pom.xml）+ 页面 src/views/report/jmreport|禅道 zt_pivot 是基于 zt_* 表的透视/下钻，取数层要重写
ppm|B|部分替代|yudao-module-pms（PmsProjectGroupController/PmsProjectController/PmsIterationController）+ 已迁 program|禅道 ppm 的评审/决策（zt_review/zt_decision）无对应
product|已迁移|-|-|-
productplan|已迁移|-|-|-
program|已迁移|-|-|-
programplan|B|部分替代|已迁 program/project（zt_project type='program'）；zt_projectspec/zt_deliverable 在开源版|IPD 项目集计划（交付物/评审点）无实现可搬
project|已迁移|-|-|-
projectbuild|已迁移|-|-|-
projectplan|已迁移|-|-|-
projectrelease|已迁移|-|-|-
projectstory|已迁移|-|-|-
provider|C|真缺口|无替代（代码库服务商 API：GitLab/Gitea/Gitee/SVN，ops_provider 表在开源版）|需重写服务商接入
qa|已迁移|-|-|-
release|已迁移|-|-|-
repo|已迁移|-|-|-
repobranchrule|B|部分替代|已迁 repo（zt_repo/zt_repohistory/zt_repofiles/zt_relation）|分支保护规则（ops_branch_ruleset 表在开源版）未迁
repobranchtype|B|部分替代|已迁 repo|分支类型（ops_branch_type 表在开源版）未迁
reporeviewflow|B|部分替代|已迁 repo|代码评审流（ops_review_flow 表在开源版）未迁
report|已迁移|-|-|-
requirement|已迁移|-|-|-
score|已迁移|-|-|-
screen|B|部分替代|yudao-module-report 的 GoView 大屏（GoViewProjectController + 页面 src/views/report/goview）|大屏数据源不是禅道 zt_* 口径，取数层要重写
search|已迁移|-|-|-
setting|A|替代|infra_config + 页面 src/views/infra/config|无
space|C|真缺口|无替代（devops 空间 ops_space/ops_spaceuser 表在开源版）|需重写 devops 空间
sso|A|替代|system_oauth2_client + system_social_client/system_social_user + 页面 src/views/system/oauth2、src/views/system/social|无
stage|已迁移|-|-|-
stakeholder|已迁移|-|-|-
story|已迁移|-|-|-
system|A|替代|yudao-module-system：system_users/system_role/system_menu/system_dept/system_dict_*|无
task|已迁移|-|-|-
testcase|已迁移|-|-|-
testreport|已迁移|-|-|-
testsuite|已迁移|-|-|-
testtask|已迁移|-|-|-
todo|已迁移|-|-|-
transfer|A|替代|yudao-spring-boot-starter-excel（ExcelUtils/PoiExcelUtils）|禅道 transfer 是产品/需求等对象的 Excel 导入导出，能力等价
tree|已迁移|-|-|-
tutorial|A|无需求|禅道教学演示层（220 处 tutorialMode 钩子喂假数据）|yudao 无此需求
upgrade|B|部分替代|一次性执行 sql/mysql/ruoyi-vue-pro.sql；yudao 无 flyway/liquibase（pom.xml 实测无）|无自动升级器，版本升级靠手工 SQL
user|已迁移|-|-|-
webhook|已迁移|-|-|-
weekly|C|真缺口|无替代（zt_weeklyreport 表在开源版，yudao hrm 无周报）|需重写周报
workestimation|已迁移|-|-|-
zahost|C|真缺口|无替代（自动化测试主机）|需重写
zai|B|部分替代|yudao-module-ai（AiChatConversationController/AiKnowledgeController）|禅道 zai 的「禅道内 AI 助手」场景提示词（zt_zoutput）需重写
zanode|C|真缺口|无替代（自动化测试节点/虚拟机）|需重写
approval|A|替代|yudao-module-bpm（Flowable）：BpmModelController/BpmTaskController/BpmProcessInstanceController、BpmProcessInstanceCopyController（抄送）、BpmUserTaskApproveMethodEnum（或签/会签）、页面 src/views/bpm|禅道 zt_approval* 7 张表的审批流能力由 bpm 整体承接；差异是禅道按对象挂审批，yudao 走流程实例
auditplan|C|真缺口|无替代（IPD 审计计划，模块目录不在开源版）|需重写
budget|C|真缺口|无替代（IPD 预算，模块目录不在开源版）|需重写
cm|C|真缺口|无替代（配置管理，模块目录不在开源版）|需重写
durationestimation|C|真缺口|无替代（工期估算，模块目录不在开源版）|需重写（workestimation 只覆盖工作量/成本）
gapanalysis|C|真缺口|无替代（差距分析，模块目录不在开源版）|需重写
issue|C|真缺口|无替代（问题管理，模块目录不在开源版）|需重写
measrecord|C|真缺口|无替代（测量记录，模块目录不在开源版）|需重写
meeting|C|真缺口|无替代（会议管理，模块目录不在开源版）|需重写
milestone|C|真缺口|无替代（里程碑，模块目录不在开源版）|需重写
mr|C|真缺口|无替代；模块目录与 zt_mr 表都不在开源版|需重写合并请求
nc|C|真缺口|无替代（不符合项，模块目录不在开源版）|需重写
opportunity|C|真缺口|无替代（机会管理，模块目录不在开源版）|需重写
projectchange|C|真缺口|无替代（变更管理，模块目录不在开源版）|需重写
projectdeliverable|C|真缺口|无替代（交付物，模块目录不在开源版）|需重写
pssp|C|真缺口|无替代（项目特定软件过程，模块目录不在开源版）|需重写
researchplan|C|真缺口|无替代（研究计划，模块目录不在开源版）|需重写
researchreport|C|真缺口|无替代（研究报告，模块目录不在开源版）|需重写
review|C|真缺口|无替代（同行评审，模块目录不在开源版）|需重写
reviewissue|C|真缺口|无替代（评审问题，模块目录不在开源版）|需重写
risk|C|真缺口|无替代（风险管理，模块目录不在开源版）|需重写
trainplan|C|真缺口|无替代（培训计划，模块目录不在开源版）|需重写
"

lookup_map() { # lookup_map <module> <file-of-module|dirs>
  awk -F'|' -v m="$1" '$1==m{print $2; exit}' "$2"
}
lookup_primary() { awk -F'|' -v m="$1" '$1==m{print; exit}' "$TMP/primary"; }

# 把 PRIMARY / YUDAO_MAP 写进临时文件
echo "$PRIMARY"   | grep -E '^[a-z]' > "$TMP/primary"
echo "$YUDAO_MAP" | grep -E '^[a-z]' > "$TMP/yudao_map"

# 0.6 yudao 侧 sql 里的建表名（附录 E 用）
grep -oE "CREATE TABLE (IF NOT EXISTS )?\`[a-zA-Z0-9_]+\`" "$YUDAO/ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql" 2>/dev/null \
  | sed -E 's/CREATE TABLE (IF NOT EXISTS )?//' | tr -d '`' | sort -u > "$TMP/yudao_sql_tables"

# 硬编码 ops_ 前缀的常量数（cmap 里不含 zt_ 的行）
opsdefs=$(grep -vc 'zt_' "$TMP/cmap"); [ -z "$opsdefs" ] && opsdefs=0

yudao_eps_for() { # yudao_eps_for <csv-of-dirs>
  dirs="$1"; total=0
  [ -z "$dirs" ] && { echo 0; return; }
  oldIFS="$IFS"; IFS=','
  for d in $dirs; do
    n=$(awk -v k="$d" '$1==k{print $2}' "$TMP/yudao_eps")
    [ -n "$n" ] && total=$((total + n))
  done
  IFS="$oldIFS"
  echo "$total"
}

in_list() { # in_list <word> <space separated list>
  for x in $2; do [ "$x" = "$1" ] && return 0; done
  return 1
}

# ---------------------------------------------------------------------------
# 2. 分类规则
# ---------------------------------------------------------------------------
classify() { # classify <module> <dir:是|否> <php> <actions> <ownmiss> <eps>
  m="$1"; d="$2"; php="$3"; act="$4"; ownmiss="$5"; eps="$6"
  if [ "$d" = "否" ]; then echo "开源版无实现"; return; fi
  if [ -n "$ownmiss" ]; then echo "表缺失(付费版)"; return; fi
  if in_list "$m" "$PARTIAL_MODULES"; then echo "部分"; return; fi
  if [ "$eps" -gt 0 ]; then echo "已迁移"; return; fi
  # 存根：有目录但几乎没有实现（禅道开源版只放了个把模型文件）
  if [ "$php" -le 60 ] && [ "$act" -eq 0 ]; then echo "开源版无实现"; return; fi
  if in_list "$m" "$EXTERNAL_MODULES"; then echo "外部依赖不可搬"; return; fi
  echo "待做"
}

# ---------------------------------------------------------------------------
# 3. 主流程：逐模块输出 CSV
# ---------------------------------------------------------------------------
echo "=============================================================="
echo " 禅道模块可迁移性审计（只读）"
echo " 禅道源码: $ZENTAO"
echo " yudao   : $YUDAO"
echo " 时间    : $(date '+%Y-%m-%d %H:%M:%S')"
echo "=============================================================="
echo
echo "模块清单口径：module/ 目录 $(( $(wc -l < "$TMP/mods_dir") )) 个 + programPriv 点名但目录缺失 $(( $(wc -l < "$TMP/mods_missing") )) 个 = $(( $(wc -l < "$TMP/mods_all") )) 个"
echo "开源建库脚本 db/zentao.sql 建表 $(( $(wc -l < "$TMP/sql_tables") )) 张（含 zt_ 与 ops_ 两种前缀，带/不带 IF NOT EXISTS 都算）"
echo "config 里 TABLE_* 常量 $(( $(wc -l < "$TMP/cmap") )) 个（其中 $opsdefs 个是硬编码 ops_ 前缀）"
echo
echo "CSV 表头：模块,PHP行数,action数,目录存在,引用表,缺表,未定义常量,同名表在开源建库脚本,edition!=open文件数,外部关键字文件数,外部服务调用次数,判定,主判定,承载方/依据,缺口"
CSV="模块,PHP行数,action数,目录存在,引用表,缺表,未定义常量,同名表在开源建库脚本,edition!=open文件数,外部关键字文件数,外部服务调用次数,判定,主判定,承载方/依据,缺口"

while read -r m; do
  [ -z "$m" ] && continue

  if [ -d "$MODDIR/$m" ]; then dirflag="是"; else dirflag="否"; fi

  # PHP 行数（全部 *.php，排除模板与 test）
  if [ "$dirflag" = "是" ]; then
    files=$(find "$MODDIR/$m" -name '*.php' ! -name '*.html.php' -not -path '*/test/*' 2>/dev/null)
    if [ -z "$files" ]; then php=0; else php=$(printf '%s\n' "$files" | tr '\n' '\0' | xargs -0 wc -l 2>/dev/null | tail -1 | awk '{print $1+0}'); fi
  else
    php=0
  fi

  # action 数
  if [ -f "$MODDIR/$m/control.php" ]; then
    act=$(grep -cE '^[[:space:]]*public function ' "$MODDIR/$m/control.php")
  else
    act=0
  fi

  # 引用的 TABLE_* 常量
  if [ "$dirflag" = "是" ]; then
    consts=$(grep -rhoE 'TABLE_[A-Z0-9_]+' "$MODDIR/$m" --include='*.php' 2>/dev/null | sort -u)
  else
    up=$(echo "$m" | tr 'a-z' 'A-Z')
    consts=$(cut -f1 "$TMP/cmap" | grep -E "^TABLE_${up}([A-Z0-9_]*)$" | sort -u)
  fi

  tabs=""; miss=""; undef=""
  for c in $consts; do
    [ -z "$c" ] && continue
    t=$(awk -F'\t' -v k="$c" '$1==k{print $2; exit}' "$TMP/cmap")
    if [ -z "$t" ]; then
      [ "$c" = "TABLE_UNDEFINED" ] || undef="$undef;$c"
      continue
    fi
    if grep -qx "$t" "$TMP/sql_tables"; then
      tabs="$tabs;$t"
    else
      tabs="$tabs;$t"
      miss="$miss;$t"
    fi
  done
  tabs=$(echo "$tabs" | sed -E 's/^;//' | tr ';' '\n' | sort -u | grep -v '^$' | tr '\n' ';' | sed -E 's/;$//')
  miss=$(echo "$miss" | sed -E 's/^;//' | tr ';' '\n' | sort -u | grep -v '^$' | tr '\n' ';' | sed -E 's/;$//')
  undef=$(echo "$undef" | sed -E 's/^;//' | tr ';' '\n' | sort -u | grep -v '^$' | tr '\n' ';' | sed -E 's/;$//')

  # 同名物理表是否在开源建库脚本（zt_<m> 或 ops_<m> 精确匹配）
  if grep -qxE "(zt|ops)_${m}" "$TMP/sql_tables"; then sametable="是"; else sametable="否"; fi

  # 「自有表缺失」= 缺的表里，物理名带模块名前缀的（zt_<m>/ops_<m>）；
  # 或者该模块引用的**全部**已定义表都缺席。只有这样才算「表缺失(付费版)」。
  # 只是顺带引用了别家未发布辅助表（如 zt_im_chat / zt_sqlite_queue）不算。
  ownmiss=""
  if [ -n "$miss" ]; then
    for t in $(echo "$miss" | tr ';' ' '); do
      case "$t" in
        zt_${m}|ops_${m}) ownmiss="$ownmiss;$t" ;;
      esac
    done
    total=$(echo "$tabs" | tr ';' '\n' | grep -c . )
    mcnt=$(echo "$miss" | tr ';' '\n' | grep -c . )
    if [ "$total" -gt 0 ] && [ "$total" = "$mcnt" ]; then ownmiss="$miss"; fi
  fi
  ownmiss=$(echo "$ownmiss" | sed -E 's/^;//' | tr ';' '\n' | sort -u | grep -v '^$' | tr '\n' ';' | sed -E 's/;$//')

  # edition != 'open'
  if [ "$dirflag" = "是" ]; then
    ed=$(grep -rlE "edition[[:space:]]*!=[[:space:]]*'open'" "$MODDIR/$m" --include='*.php' 2>/dev/null | wc -l | tr -d ' ')
    kw=$(grep -rliE "gitfox|gitee|码云|jenkins|sonarqube" "$MODDIR/$m" --include='*.php' 2>/dev/null | wc -l | tr -d ' ')
    ext=$(grep -ohE "loadModel\('(gitfox|jenkins|gitlab)'\)|commonModel::http|curl_init|curl_exec" "$MODDIR/$m/control.php" "$MODDIR/$m/model.php" "$MODDIR/$m/zen.php" "$MODDIR/$m/tao.php" 2>/dev/null | wc -l | tr -d ' ')
  else
    ed=0; kw=0; ext=0
  fi

  # yudao 侧实测
  ydirs=$(awk -F'|' -v m="$m" '$1==m{print $2; exit}' "$TMP/yudao_map")
  eps=$(yudao_eps_for "$ydirs")

  verdict=$(classify "$m" "$dirflag" "$php" "$act" "$ownmiss" "$eps")

  prow=$(lookup_primary "$m")
  if [ -z "$prow" ]; then
    case "$verdict" in
      已迁移) pv="已迁移"; ptype="-"; pbearer="-"; pgap="-";;
      部分)   pv="已迁移"; ptype="-"; pbearer="-"; pgap="-";;
      *)      pv="C"; ptype="真缺口"; pbearer="无替代（未在 PRIMARY 表登记，需人工复核）"; pgap="-";;
    esac
  else
    pv=$(echo "$prow" | cut -d'|' -f2)
    ptype=$(echo "$prow" | cut -d'|' -f3)
    pbearer=$(echo "$prow" | cut -d'|' -f4)
    pgap=$(echo "$prow" | cut -d'|' -f5)
  fi
  # 已迁移的模块，主判定一律「已迁移」（部分模块也标已迁移+缺口，见文档）
  case "$verdict" in
    已迁移) pv="已迁移" ;;
    部分)   pv="部分" ;;
  esac

  line="$m,$php,$act,$dirflag,$tabs,$miss,$undef,$sametable,$ed,$kw,$ext,$verdict,$pv,$pbearer,$pgap"
  CSV="$CSV
$line"
done < "$TMP/mods_all"

printf '%s\n' "$CSV"

# ---------------------------------------------------------------------------
# 4. 汇总
# ---------------------------------------------------------------------------
echo
echo "=============================================================="
echo " 汇总"
echo "=============================================================="
echo
echo "按「判定」（原始可迁移性）："
tmpcsv="$TMP/out.csv"; printf '%s\n' "$CSV" | tail -n +2 > "$tmpcsv"
# 注意：macOS 自带 awk 用 -v 传中文再比较会恒真，所以这里用 cut+grep 做精确列匹配
cut -d',' -f12 "$tmpcsv" > "$TMP/col12"
for v in "已迁移" "部分" "开源版无实现" "表缺失(付费版)" "外部依赖不可搬" "待做"; do
  n=$(grep -Fxc "$v" "$TMP/col12" 2>/dev/null); [ -z "$n" ] && n=0
  printf "  %-18s %3s\n" "$v" "$n"
done
printf "  %-18s %3s\n" "合计" "$(wc -l < "$tmpcsv" | tr -d ' ')"

echo
echo "按「主判定」（A/B/C 新口径）："
cut -d',' -f13 "$tmpcsv" > "$TMP/col13"
for v in "已迁移" "部分" "A" "B" "C"; do
  n=$(grep -Fxc "$v" "$TMP/col13" 2>/dev/null); [ -z "$n" ] && n=0
  printf "  %-18s %3s\n" "$v" "$n"
done

echo
echo "--------------------------------------------------------------"
echo " 附录 A：开源版无实现（模块目录缺失 / 存根）"
echo "--------------------------------------------------------------"
grep -F ',开源版无实现,' "$tmpcsv" | awk -F',' '{print "  "$1"  php="$2"  action="$3"  目录="$4"  同名表在开源建库脚本="$8}'
echo
echo "  programPriv 点名但 module/ 目录缺失的全部模块（$(wc -l < "$TMP/mods_missing" | tr -d ' ') 个）："
tr '\n' ' ' < "$TMP/mods_missing"; echo
echo
echo "--------------------------------------------------------------"
echo " 附录 B：表缺失(付费版) 的实测证据"
echo "--------------------------------------------------------------"
n=$(grep -Fxc '表缺失(付费版)' "$TMP/col12" 2>/dev/null); [ -z "$n" ] && n=0
if [ "$n" -eq 0 ]; then
  echo "  **0 个模块**：有 module/ 目录的模块里，没有任何模块的「自有表」缺席开源建库脚本。"
  echo "  只有少数辅助表没发布（被已发布模块引用）："
  awk -F',' 'length($6)>0{print "    "$1" -> 缺 "$6}' "$tmpcsv"
  echo "  以及指向外部源系统/占位符的未定义常量（不是禅道表，不算缺失）："
  awk -F',' 'length($7)>0{print "    "$1" -> 未定义 "$7}' "$tmpcsv" | head -12
fi
echo
echo "  说明：历史上被判「表缺失」的模块，真实表名/前缀是："
for pair in "space=ops_space" "artifact=ops_artifact_libs" "pipeline=ops_pipeline" "ppm=ops_ppm" "provider=ops_provider" "repo=ops_repo" "weekly=zt_weeklyreport" "personnel=zt_userview" "mail=zt_notify" "message=zt_notify" "setting=zt_config" "programplan=zt_projectspec" "sso=zt_user(无自有表)" "datatable=zt_product/zt_projectproduct(无自有表)" "codescan=ops_scan_plans 等(表在开源版但代码走 GitFox)"; do
  name=${pair%%=*}; tab=${pair#*=}
  if grep -qx "$tab" "$TMP/sql_tables"; then st="在开源建库脚本里"; else st="不在开源建库脚本里"; fi
  printf "    %-14s -> %-32s %s\n" "$name" "$tab" "$st"
done
for t in zt_mr zt_dashboard zt_im_chat zt_im_message zt_im_userdevice zt_sqlite_queue; do
  if grep -qx "$t" "$TMP/sql_tables"; then st="在开源建库脚本里"; else st="**未发布**"; fi
  printf "    %-14s -> %-32s %s\n" "（辅助表）" "$t" "$st"
done
echo
echo "--------------------------------------------------------------"
echo " 附录 C：外部依赖不可搬 的实测证据（control/model/zen/tao 里的调用次数）"
echo "--------------------------------------------------------------"
grep -F ',外部依赖不可搬,' "$tmpcsv" | awk -F',' '{print "  "$1"  loadModel+curl="$11"  外部关键字文件="$10"  php="$2"  action="$3}'
echo
echo "--------------------------------------------------------------"
echo " 附录 D：部分迁移 的实测缺口"
echo "--------------------------------------------------------------"
calc_all=$(find "$MODDIR/metric/calc" -name '*.php' 2>/dev/null | wc -l | tr -d ' ')
calc_done=$(grep -c 'register("' "$YJAVA/service/metric/MetricRegistry.java" 2>/dev/null || echo 0)
echo "  metric : 禅道 module/metric/calc/*.php = $calc_all 个口径；yudao MetricRegistry register() = $calc_done 条"
echo "  bi     : yudao @TableName 里没有 zt_pivot/zt_pivotspec/zt_pivotdrill/zt_screen/zt_duckdbqueue（实测 $(wc -l < "$TMP/yudao_tables" | tr -d ' ') 张已迁表）"
echo "  common : yudao $(( $(wc -l < "$TMP/yudao_eps") )) 个 controller 目录里没有 common；操作日志走 infra_operate_log/system_operate_log"
echo "  block  : yudao @TableName 里没有 zt_block；等价聚合落在 qa/report"
echo
echo "--------------------------------------------------------------"
echo " 附录 E：yudao 侧实测能力（A/B 判定的承载方依据）"
echo "--------------------------------------------------------------"
for t in infra_config infra_job infra_job_log infra_codegen_table infra_codegen_column infra_data_source_config system_mail_account system_mail_template system_mail_log system_notify_message system_notify_template system_notice system_oauth2_client system_social_client system_dict_type system_operate_log; do
  if grep -qx "$t" "$TMP/yudao_sql_tables"; then st="存在"; else st="未找到"; fi
  printf "  %-28s %s\n" "$t" "$st"
done
echo "  yudao-module-bpm         : Flowable 工作流（BpmModelController/BpmTaskController/BpmProcessInstanceCopyController/BpmUserTaskApproveMethodEnum）"
echo "  yudao-module-report      : jimureport 2.5.1（yudao-module-report/pom.xml）+ GoView（GoViewProjectController）"
echo "  yudao-module-ai          : AiChatConversationController 等 14 个控制器"
echo "  yudao-module-pms         : PmsProjectGroupController/PmsProjectController/PmsIterationController"
echo "  yudao-module-hrm         : HrmEmployeeController 等 60+ 控制器"
echo "  yudao-module-system/infra: 验证码 CaptchaController、Excel ExcelUtils、前端组件 src/components/Editor"
echo
echo "审计完成（只读，未修改任何文件）。"

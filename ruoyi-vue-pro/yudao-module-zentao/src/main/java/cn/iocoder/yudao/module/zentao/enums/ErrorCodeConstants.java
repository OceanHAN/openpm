package cn.iocoder.yudao.module.zentao.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * zentao 模块错误码
 *
 * 错误码区间：1-020-000-000 ~ 1-029-999-999
 * （yudao 约定每个模块独占一段，避免跨模块冲突）
 */
public interface ErrorCodeConstants {

    // ========== 需求 story 1-020-000-000 ==========
    ErrorCode STORY_NOT_EXISTS = new ErrorCode(1_020_000_000, "需求不存在");
    ErrorCode STORY_TITLE_DUPLICATE = new ErrorCode(1_020_000_001, "需求标题已存在");
    ErrorCode STORY_CLOSED_CANNOT_UPDATE = new ErrorCode(1_020_000_002, "需求已关闭，请先激活后再修改");
    ErrorCode STORY_ALREADY_CLOSED = new ErrorCode(1_020_000_003, "需求已经是关闭状态");
    ErrorCode STORY_NOT_CLOSED_CANNOT_ACTIVATE = new ErrorCode(1_020_000_004, "只有已关闭的需求才能激活");
    ErrorCode STORY_DUPLICATE_STORY_REQUIRED = new ErrorCode(1_020_000_005, "关闭原因为「重复」时，必须指定重复的需求编号");
    ErrorCode STORY_DUPLICATE_STORY_NOT_EXISTS = new ErrorCode(1_020_000_006, "重复的需求编号不存在：{}");
    ErrorCode STORY_PARENT_NOT_EXISTS = new ErrorCode(1_020_000_007, "父需求不存在：{}");
    ErrorCode STORY_PARENT_NOT_SAME_PRODUCT = new ErrorCode(1_020_000_008, "父需求 {} 不属于同一个产品，不能作为父需求");
    ErrorCode STORY_PARENT_IS_CHILD = new ErrorCode(1_020_000_009, "不能把需求挂到自己的子需求下");
    ErrorCode STORY_HAS_CHILDREN = new ErrorCode(1_020_000_010, "该需求已分解出 {} 条子需求，请先删除子需求");
    ErrorCode STORY_ALREADY_HAS_PARENT = new ErrorCode(1_020_000_011, "需求 {} 已经有父需求，不能重复分解");
    // 注意：下面两个原本复用了 007/008，与 STORY_PARENT_NOT_EXISTS / STORY_PARENT_NOT_SAME_PRODUCT 撞号
    // （「父需求不存在」和「不能标记为重复于自身」会返回同一个错误码），这里修正为独立编号。
    ErrorCode STORY_DUPLICATE_SELF = new ErrorCode(1_020_000_012, "不能把需求标记为重复于自身");
    ErrorCode STORY_NOT_DRAFT_CANNOT_UPDATE = new ErrorCode(1_020_000_013, "需求已提交评审，不能再直接修改");
    // 需求分层：业务需求 epic / 用户需求 requirement / 研发需求 story
    ErrorCode STORY_TYPE_INVALID = new ErrorCode(1_020_000_014, "需求类型不合法：{}");
    ErrorCode STORY_PARENT_TYPE_NOT_ALLOWED = new ErrorCode(1_020_000_015, "「{}」不能挂在「{}」下，父需求的层级不能低于子需求");

    // ========== 需求评审 story review 1-020-001-000 ==========
    ErrorCode STORY_REVIEW_NOT_IN_REVIEWING = new ErrorCode(1_020_001_000, "需求当前不在评审中，无法执行该操作");
    ErrorCode STORY_REVIEWER_EMPTY = new ErrorCode(1_020_001_001, "评审人不能为空");
    ErrorCode STORY_REVIEWER_NOT_EXISTS = new ErrorCode(1_020_001_002, "评审人不存在：{}");
    ErrorCode STORY_NOT_REVIEWER = new ErrorCode(1_020_001_003, "你不是该需求的评审人，无权评审");
    ErrorCode STORY_ALREADY_REVIEWED = new ErrorCode(1_020_001_004, "你已经提交过该版本的评审结果");
    ErrorCode STORY_REVIEW_RESULT_INVALID = new ErrorCode(1_020_001_005, "评审结果不合法：{}");
    ErrorCode STORY_REVIEW_NO_PREVIOUS_VERSION = new ErrorCode(1_020_001_006, "没有可回滚的历史版本（当前已是第 1 版）");

    // ========== 任务 task 1-020-002-000 ==========
    ErrorCode TASK_NOT_EXISTS = new ErrorCode(1_020_002_000, "任务不存在");
    ErrorCode TASK_CLOSED_CANNOT_UPDATE = new ErrorCode(1_020_002_001, "任务已关闭，不能再修改");
    ErrorCode TASK_CANCELED_CANNOT_UPDATE = new ErrorCode(1_020_002_002, "任务已取消，不能再修改");
    ErrorCode TASK_STATUS_ILLEGAL = new ErrorCode(1_020_002_003, "任务当前状态为「{}」，不允许执行该操作");
    ErrorCode TASK_ALREADY_DONE = new ErrorCode(1_020_002_004, "任务已经是完成状态");
    ErrorCode TASK_NOT_DONE_CANNOT_CLOSE = new ErrorCode(1_020_002_005, "只有已完成的任务才能关闭");

    // ========== 缺陷 bug 1-020-003-000 ==========
    ErrorCode BUG_NOT_EXISTS = new ErrorCode(1_020_003_000, "缺陷不存在");
    ErrorCode BUG_CLOSED_CANNOT_UPDATE = new ErrorCode(1_020_003_001, "缺陷已关闭，请先激活后再修改");
    ErrorCode BUG_ALREADY_RESOLVED = new ErrorCode(1_020_003_002, "缺陷已经是解决状态");
    ErrorCode BUG_NOT_RESOLVED_CANNOT_CLOSE = new ErrorCode(1_020_003_003, "只有已解决的缺陷才能关闭");
    ErrorCode BUG_ALREADY_CLOSED = new ErrorCode(1_020_003_004, "缺陷已经是关闭状态");
    ErrorCode BUG_RESOLUTION_INVALID = new ErrorCode(1_020_003_005, "解决方案不合法：{}");
    ErrorCode BUG_DUPLICATE_BUG_REQUIRED = new ErrorCode(1_020_003_006, "解决方案为「重复Bug」时，必须指定重复的缺陷编号");
    ErrorCode BUG_DUPLICATE_BUG_NOT_EXISTS = new ErrorCode(1_020_003_007, "重复的缺陷编号不存在：{}");
    ErrorCode BUG_RESOLVED_BUILD_REQUIRED = new ErrorCode(1_020_003_008, "解决方案为「已解决」时，必须填写解决版本");
    ErrorCode BUG_DUPLICATE_SELF = new ErrorCode(1_020_003_009, "不能把缺陷标记为重复于自身");
    ErrorCode BUG_ALREADY_ACTIVE = new ErrorCode(1_020_003_010, "缺陷已经是激活状态，无需重复激活");

    // ========== 产品 product 1-020-004-000 ==========
    ErrorCode PRODUCT_NOT_EXISTS = new ErrorCode(1_020_004_000, "产品不存在");
    ErrorCode PRODUCT_NAME_DUPLICATE = new ErrorCode(1_020_004_001, "产品名称已存在：{}");
    ErrorCode PRODUCT_CLOSED_CANNOT_UPDATE = new ErrorCode(1_020_004_002, "产品已关闭，请先激活后再修改");
    ErrorCode PRODUCT_ALREADY_CLOSED = new ErrorCode(1_020_004_003, "产品已经是关闭状态");
    ErrorCode PRODUCT_HAS_STORIES = new ErrorCode(1_020_004_004, "产品下还有 {} 条需求，不能删除");

    // ========== 项目 project 1-020-005-000 ==========
    ErrorCode PROJECT_NOT_EXISTS = new ErrorCode(1_020_005_000, "项目不存在");
    ErrorCode PROJECT_NAME_DUPLICATE = new ErrorCode(1_020_005_001, "项目名称已存在：{}");
    ErrorCode PROJECT_CLOSED_CANNOT_UPDATE = new ErrorCode(1_020_005_002, "项目已关闭，不能再修改");
    ErrorCode PROJECT_STATUS_ILLEGAL = new ErrorCode(1_020_005_003, "项目当前状态为「{}」，不允许执行该操作");
    ErrorCode PROJECT_ALREADY_CLOSED = new ErrorCode(1_020_005_004, "项目已经是关闭状态");
    ErrorCode PROJECT_HAS_CHILDREN = new ErrorCode(1_020_005_005, "项目下还有 {} 个执行，不能删除");
    ErrorCode PROJECT_MODEL_INVALID = new ErrorCode(1_020_005_006, "项目模型不合法：{}");
    ErrorCode PROJECT_END_BEFORE_BEGIN = new ErrorCode(1_020_005_007, "计划结束日期不能早于开始日期");
    ErrorCode PROJECT_ID_IS_NOT_PROJECT = new ErrorCode(1_020_005_008, "编号 {} 不是项目（可能是执行或项目集）");
    ErrorCode PROJECT_PARENT_NOT_PROGRAM = new ErrorCode(1_020_005_009, "编号 {} 不是项目集，不能作为项目的所属项目集");

    // ========== 执行 execution 1-020-006-000 ==========
    ErrorCode EXECUTION_NOT_EXISTS = new ErrorCode(1_020_006_000, "执行不存在");
    ErrorCode EXECUTION_TYPE_INVALID = new ErrorCode(1_020_006_001, "执行类型不合法：{}，只能是 sprint/stage/kanban");
    ErrorCode EXECUTION_PROJECT_REQUIRED = new ErrorCode(1_020_006_002, "执行必须指定所属项目");
    ErrorCode EXECUTION_PROJECT_NOT_EXISTS = new ErrorCode(1_020_006_003, "所属项目不存在：{}");
    ErrorCode EXECUTION_PROJECT_TYPE_INVALID = new ErrorCode(1_020_006_004, "编号 {} 不是项目（type=project），不能作为执行的所属项目");
    ErrorCode ID_IS_NOT_EXECUTION = new ErrorCode(1_020_006_005, "编号 {} 是项目而非执行，请到项目管理中操作");
    ErrorCode BURN_DATE_REQUIRED = new ErrorCode(1_020_006_006, "执行缺少起止日期，无法绘制燃尽图");
    ErrorCode BURN_CANNOT_COMPUTE = new ErrorCode(1_020_006_007, "只有「未开始 / 进行中」的迭代或阶段能计算燃尽图，当前状态：{}");

    // ========== 代码库（repo）1_020_029_xxx ==========
    ErrorCode REPO_NOT_EXISTS = new ErrorCode(1_020_029_000, "代码库不存在");
    ErrorCode REPO_NAME_DUPLICATE = new ErrorCode(1_020_029_001, "代码库名称已存在：{}");
    ErrorCode REPO_SCM_UNSUPPORTED = new ErrorCode(1_020_029_002, "暂不支持的源码管理类型：{}，本实现只支持 git");
    ErrorCode REPO_PATH_INVALID = new ErrorCode(1_020_029_003, "仓库路径不可用（必须是服务器上存在、且含 .git 的 git 仓库）：{}");
    ErrorCode COMMIT_NOT_EXISTS = new ErrorCode(1_020_029_004, "提交记录不存在：{}");

    // ========== 应用接入（entry）1_020_030_xxx ==========
    // 提示文案照抄禅道 module/entry/lang/zh-cn.php 的 errmsg —— 迁移期用户看到的报错应当是一致的。
    ErrorCode ENTRY_NOT_EXISTS = new ErrorCode(1_020_030_000, "应用不存在");
    /**
     * 代号重复。文案来自禅道 {@code $lang->error->unique}：
     * 「『代号』已经有『oa』这条记录了。如果您确定该记录已删除，请到后台-系统设置-回收站还原。」
     * —— 这句话本身就说明了「unique 校验不过滤已删除记录」（见 EntryMapper#selectIdByCodeIgnoreDeleted）。
     */
    ErrorCode ENTRY_CODE_DUPLICATE = new ErrorCode(1_020_030_001, "『代号』已经有『{}』这条记录了。如果您确定该记录已删除，请到后台-系统设置-回收站还原。");
    ErrorCode ENTRY_CODE_INVALID = new ErrorCode(1_020_030_002, "『代号』不符合格式，应当为:『字母或数字的组合』。");
    ErrorCode ENTRY_PARAM_CODE_MISSING = new ErrorCode(1_020_030_003, "缺少code参数");
    ErrorCode ENTRY_PARAM_TOKEN_MISSING = new ErrorCode(1_020_030_004, "缺少token参数");
    ErrorCode ENTRY_EMPTY_KEY = new ErrorCode(1_020_030_005, "应用未设置密钥");
    ErrorCode ENTRY_IP_DENIED = new ErrorCode(1_020_030_006, "该IP被限制访问：{}");
    ErrorCode ENTRY_INVALID_TOKEN = new ErrorCode(1_020_030_007, "无效的token参数");
    ErrorCode ENTRY_ACCOUNT_UNBOUND = new ErrorCode(1_020_030_008, "未绑定用户");
    ErrorCode ENTRY_INVALID_ACCOUNT = new ErrorCode(1_020_030_009, "用户不存在：{}");
    ErrorCode ENTRY_CALLED_TIME_REPLAY = new ErrorCode(1_020_030_010, "Token已失效：时间戳不大于上次调用时间，可能是重放请求");
    ErrorCode ENTRY_ERROR_TIMESTAMP = new ErrorCode(1_020_030_011, "错误的时间戳。应为 10 位秒级时间戳，且首位需小于 4");

    // ========== 公司信息（company，禅道的「组织视图」）1_020_031_xxx ==========
    ErrorCode COMPANY_NOT_EXISTS = new ErrorCode(1_020_031_000, "公司不存在");
    /** 文案来自禅道 {@code $lang->error->unique}，与「应用代号」重复是同一套话术 */
    ErrorCode COMPANY_NAME_DUPLICATE = new ErrorCode(1_020_031_001, "『公司名称』已经有『{}』这条记录了。如果您确定该记录已删除，请到后台-系统设置-回收站还原。");
    ErrorCode COMPANY_DEFAULT_NOT_EXISTS = new ErrorCode(1_020_031_002, "默认公司（id=1）不存在，环境没初始化好");
    ErrorCode COMPANY_NAME_REQUIRED = new ErrorCode(1_020_031_003, "公司名称不能为空");

    // ========== 积分（score）1_020_032_xxx ==========
    /** 禅道内部调用时对未知规则是**静默跳过**（返回 true）；接口显式调用时报错更利于排查 */
    ErrorCode SCORE_RULE_NOT_FOUND = new ErrorCode(1_020_032_000, "积分规则不存在：{}.{}");
    ErrorCode SCORE_DISABLED = new ErrorCode(1_020_032_001, "积分功能已关闭（禅道是 system.common.global.scoreStatus 这个设置项）");
    ErrorCode SCORE_ACCOUNT_REQUIRED = new ErrorCode(1_020_032_002, "账号不能为空");
    /* 注：没有「账号不存在」的错误码 —— 禅道对取不到的账号是静默跳过（saveScore 里 getById 取不到就 return false），
       本实现照抄这个口径（见 ScoreService#save 的注释），所以这里不需要这个错误码。 */
    ErrorCode SCORE_PARAM_REQUIRED = new ErrorCode(1_020_032_004, "这个积分规则需要对象编号（param）：{}.{}");

    // ========== 保存查询 / 搜索（search）1_020_033_xxx ==========
    ErrorCode SEARCH_QUERY_NOT_EXISTS = new ErrorCode(1_020_033_000, "保存的查询不存在");
    ErrorCode SEARCH_QUERY_NOT_MINE = new ErrorCode(1_020_033_001, "只能操作自己的保存查询");
    ErrorCode SEARCH_QUERY_TITLE_REQUIRED = new ErrorCode(1_020_033_002, "查询名称不能为空");
    ErrorCode SEARCH_QUERY_MODULE_REQUIRED = new ErrorCode(1_020_033_003, "模块不能为空");

    // ========== 维度（dimension，BI/透视表的前置）1_020_034_xxx ==========
    ErrorCode DIMENSION_NOT_EXISTS = new ErrorCode(1_020_034_000, "维度不存在：{}");
    ErrorCode DIMENSION_CODE_DUPLICATE = new ErrorCode(1_020_034_001, "维度代号已存在：{}");

    // ========== 接口文档库（api）1_020_035_xxx ==========
    ErrorCode API_NOT_EXISTS = new ErrorCode(1_020_035_000, "接口不存在");
    ErrorCode API_LIB_NOT_EXISTS = new ErrorCode(1_020_035_001, "接口库不存在：{}");
    ErrorCode API_TITLE_REQUIRED = new ErrorCode(1_020_035_002, "接口名称不能为空");
    ErrorCode API_STRUCT_NOT_EXISTS = new ErrorCode(1_020_035_003, "接口结构不存在");
    ErrorCode API_RELEASE_NOT_EXISTS = new ErrorCode(1_020_035_004, "接口发布版本不存在");
    /* 下面这几个是「有精确文案」的场景。**注意**：yudao 的 exception(code, args...) 里 args 是给
       错误码 message 里的 {} 占位符用的，不是「附加说明」—— 给没有占位符的错误码多传一个字符串，
       那段文案会被直接丢掉（第一版就踩了这个坑，8 个断言因此拿到「请求参数不正确」）。 */
    ErrorCode API_VERSION_NOT_EXISTS = new ErrorCode(1_020_035_005, "接口没有第 {} 版");
    ErrorCode API_EDITED_BY_OTHER = new ErrorCode(1_020_035_006, "接口已被其他人修改，请刷新后重试");
    ErrorCode API_FROZEN_BY_RELEASE = new ErrorCode(1_020_035_007, "接口「{}」已被发布版本冻结，请先删除相关发布版本");
    ErrorCode API_REFERENCED_BY_STRUCT = new ErrorCode(1_020_035_008, "接口「{}」被数据结构引用，不能删除");
    ErrorCode API_TITLE_DUPLICATE = new ErrorCode(1_020_035_009, "同一目录下已经有接口「{}」，接口名称不能重复");
    ErrorCode API_PATH_DUPLICATE = new ErrorCode(1_020_035_010, "同一目录下 {} {} 已被接口 #{} 占用");
    ErrorCode API_RELEASE_VERSION_DUPLICATE = new ErrorCode(1_020_035_011, "接口库下已经存在版本「{}」");

    // ========== Webhook 1_020_036_xxx ==========
    ErrorCode WEBHOOK_NOT_EXISTS = new ErrorCode(1_020_036_000, "Webhook 不存在");
    ErrorCode WEBHOOK_NAME_REQUIRED = new ErrorCode(1_020_036_001, "名称不能为空");
    ErrorCode WEBHOOK_URL_REQUIRED = new ErrorCode(1_020_036_002, "请求地址不能为空");
    ErrorCode WEBHOOK_PRODUCT_REQUIRED = new ErrorCode(1_020_036_003, "必须选择产品");
    ErrorCode WEBHOOK_SEND_FAILED = new ErrorCode(1_020_036_004, "Webhook 发送失败：{}");

    // ========== 分支/平台 branch 1-020-007-000 ==========
    ErrorCode BRANCH_NOT_EXISTS = new ErrorCode(1_020_007_000, "{}不存在");
    ErrorCode BRANCH_NAME_DUPLICATE = new ErrorCode(1_020_007_001, "{}名称已存在：{}");
    ErrorCode BRANCH_PRODUCT_NOT_EXISTS = new ErrorCode(1_020_007_002, "所属产品不存在：{}");
    ErrorCode BRANCH_PRODUCT_TYPE_UNSUPPORTED = new ErrorCode(1_020_007_003, "产品「{}」的类型是{}，未启用分支/平台");
    ErrorCode BRANCH_HAS_DATA = new ErrorCode(1_020_007_004, "该{}下已经有数据（{}），不能删除");
    ErrorCode BRANCH_MAIN_NOT_ALLOWED = new ErrorCode(1_020_007_005, "主干是虚拟{}，不能执行该操作");
    ErrorCode BRANCH_ALREADY_CLOSED = new ErrorCode(1_020_007_006, "该{}已经是关闭状态");
    ErrorCode BRANCH_STATUS_ILLEGAL = new ErrorCode(1_020_007_007, "该{}当前状态为「{}」，不允许执行该操作");

    // ========== 模块树 module 1-020-008-000 ==========
    ErrorCode MODULE_NOT_EXISTS = new ErrorCode(1_020_008_000, "模块不存在：{}");
    ErrorCode MODULE_NAME_DUPLICATE = new ErrorCode(1_020_008_001, "同级下已存在同名模块：{}");
    ErrorCode MODULE_TYPE_INVALID = new ErrorCode(1_020_008_002, "模块树类型不合法：{}");
    ErrorCode MODULE_ROOT_REQUIRED = new ErrorCode(1_020_008_003, "所属根对象不能为空");
    ErrorCode MODULE_PARENT_INVALID = new ErrorCode(1_020_008_004, "上级模块不合法：{}");
    ErrorCode MODULE_MOVE_TO_DESCENDANT = new ErrorCode(1_020_008_005, "不能把模块移动到自己或自己的子模块下");
    ErrorCode MODULE_NAME_BLANK = new ErrorCode(1_020_008_006, "模块名称不能为空");

    // ========== 产品计划 productplan 1-020-009-000 ==========
    ErrorCode PLAN_NOT_EXISTS = new ErrorCode(1_020_009_000, "计划不存在：{}");
    ErrorCode PLAN_BRANCH_REQUIRED = new ErrorCode(1_020_009_001, "多分支/多平台产品必须选择{}");
    ErrorCode PLAN_DATE_REQUIRED = new ErrorCode(1_020_009_002, "开始日期和结束日期都不能为空（待定计划请显式传 待定）");
    ErrorCode PLAN_END_BEFORE_BEGIN = new ErrorCode(1_020_009_003, "计划结束日期不能早于开始日期");
    ErrorCode PLAN_CHILD_OUT_OF_PARENT = new ErrorCode(1_020_009_004, "子计划的{}「{}」超出了父计划的{}「{}」");
    ErrorCode PLAN_PARENT_NOT_COVER_CHILD = new ErrorCode(1_020_009_005, "父计划的{}「{}」没有覆盖子计划的最{}「{}」");
    ErrorCode PLAN_PARENT_INVALID = new ErrorCode(1_020_009_006, "父计划不合法：{}");
    ErrorCode PLAN_HAS_CHILDREN = new ErrorCode(1_020_009_007, "该计划下还有 {} 个子计划，不能删除父计划");
    ErrorCode PLAN_STATUS_ILLEGAL = new ErrorCode(1_020_009_008, "计划当前状态为「{}」，不允许执行该操作");
    ErrorCode PLAN_ALREADY_CLOSED = new ErrorCode(1_020_009_009, "计划已经是关闭状态");
    ErrorCode PLAN_BRANCH_NOT_IN_PRODUCT = new ErrorCode(1_020_009_010, "分支 {} 不属于该产品");

    // ========== 构建 build 1-020-010-000 ==========
    ErrorCode BUILD_NOT_EXISTS = new ErrorCode(1_020_010_000, "构建不存在：{}");
    ErrorCode BUILD_NAME_DUPLICATE = new ErrorCode(1_020_010_001, "同一产品同一分支下已存在同名构建：{}");
    ErrorCode BUILD_EXECUTION_REQUIRED = new ErrorCode(1_020_010_002, "所属执行不能为空");
    ErrorCode BUILD_EXECUTION_NOT_EXISTS = new ErrorCode(1_020_010_003, "所属执行不存在：{}");
    ErrorCode BUILD_PRODUCT_REQUIRED = new ErrorCode(1_020_010_004, "所属产品不能为空");
    ErrorCode BUILD_BRANCH_REQUIRED = new ErrorCode(1_020_010_005, "多分支/多平台产品必须选择{}");
    ErrorCode BUILD_INTEGRATED_NEEDS_CHILDREN = new ErrorCode(1_020_010_006, "集成构建必须选择至少一个子构建");
    ErrorCode BUILD_CHILD_CANNOT_CHANGE = new ErrorCode(1_020_010_007, "该构建已经被集成构建或发布引用，不能修改{}");
    ErrorCode BUILD_CHILD_NOT_EXISTS = new ErrorCode(1_020_010_008, "子构建不存在：{}");

    // ========== 发布 release 1-020-011-000 ==========
    ErrorCode RELEASE_NOT_EXISTS = new ErrorCode(1_020_011_000, "发布不存在：{}");
    ErrorCode RELEASE_NAME_DUPLICATE = new ErrorCode(1_020_011_001, "发布版本号已存在：{}（禅道里发布名是全局唯一的）");
    ErrorCode RELEASE_DATE_REQUIRED = new ErrorCode(1_020_011_002, "状态为「已发布」时不需要计划发布日期，其它状态必填");
    ErrorCode RELEASE_RELEASED_DATE_REQUIRED = new ErrorCode(1_020_011_003, "状态为「已发布」时必须填写实际发布日期");
    ErrorCode RELEASE_DATE_IN_FUTURE = new ErrorCode(1_020_011_004, "实际发布日期不能晚于今天");
    ErrorCode RELEASE_IS_INCLUDED = new ErrorCode(1_020_011_005, "该发布已被 {} 个其它发布包含，不能删除");
    ErrorCode RELEASE_STATUS_ILLEGAL = new ErrorCode(1_020_011_006, "发布当前状态为「{}」，不允许执行该操作");
    ErrorCode RELEASE_BUILD_NOT_EXISTS = new ErrorCode(1_020_011_007, "构建不存在：{}");
    ErrorCode RELEASE_SHADOW_FAILED = new ErrorCode(1_020_011_008, "自动创建影子构建失败");
    ErrorCode RELEASE_BRANCH_REQUIRED = new ErrorCode(1_020_011_009, "多分支/多平台产品必须选择{}");

    // ========== 项目/执行需求范围 projectstory 1-020-012-000 ==========
    ErrorCode PROJECT_STORY_RELATION_NOT_EXISTS = new ErrorCode(1_020_012_000, "该需求没有关联到这个项目/执行");
    ErrorCode PROJECT_STORY_PRODUCT_NOT_LINKED = new ErrorCode(1_020_012_001, "项目还没有关联需求所属的产品，请先关联产品");
    ErrorCode PROJECT_STORY_STATUS_NOT_ALLOWED = new ErrorCode(1_020_012_002, "需求「{}」当前状态为「{}」，不能纳入项目范围");
    ErrorCode PROJECT_STORY_HAS_CHILD_EXECUTION = new ErrorCode(1_020_012_003, "子执行已经关联了该需求，不能从项目中移除");
    ErrorCode PROJECT_PRODUCT_NOT_EXISTS = new ErrorCode(1_020_012_004, "项目关联的产品不存在：{}");
    ErrorCode PROJECT_PRODUCT_DUPLICATE = new ErrorCode(1_020_012_005, "该项目已经关联了产品「{}」的该分支");
    ErrorCode PROJECT_PRODUCT_HAS_STORIES = new ErrorCode(1_020_012_006, "该产品下还有 {} 条需求在项目范围内，不能解除关联");
    ErrorCode PROJECT_STORY_TARGET_NOT_EXISTS = new ErrorCode(1_020_012_007, "项目/执行不存在：{}");

    // ========== 阶段模板 stage 1-020-013-000 ==========
    ErrorCode STAGE_NOT_EXISTS = new ErrorCode(1_020_013_000, "阶段模板不存在：{}");
    ErrorCode STAGE_NAME_DUPLICATE = new ErrorCode(1_020_013_001, "同一流程下已存在同名阶段：{}");
    ErrorCode STAGE_PERCENT_NOT_NUMBER = new ErrorCode(1_020_013_002, "工作量占比必须是数字：{}");
    ErrorCode STAGE_PERCENT_OVER = new ErrorCode(1_020_013_003, "工作量占比累计不能超过 100%（当前 {}+{}>100）");
    ErrorCode STAGE_TYPE_INVALID = new ErrorCode(1_020_013_004, "阶段类型不合法：{}");
    ErrorCode STAGE_PROJECT_NOT_EXISTS = new ErrorCode(1_020_013_005, "项目不存在或不是项目：{}");
    ErrorCode STAGE_ALREADY_GENERATED = new ErrorCode(1_020_013_006, "该项目已经生成过 {} 个阶段，请先删除后再生成");
    ErrorCode STAGE_TEMPLATE_EMPTY = new ErrorCode(1_020_013_007, "流程 {} 下还没有阶段模板，请先维护模板");
    ErrorCode STAGE_IS_NOT_STAGE = new ErrorCode(1_020_013_008, "编号 {} 不是该项目的阶段");
    ErrorCode STAGE_GROUP_REQUIRED = new ErrorCode(1_020_013_009, "流程模板组不能为空");

    // ========== 附件 file 1-020-014-000 ==========
    ErrorCode FILE_NOT_EXISTS = new ErrorCode(1_020_014_000, "附件不存在：{}");
    ErrorCode FILE_EMPTY = new ErrorCode(1_020_014_001, "上传的附件内容为空");
    ErrorCode FILE_SIZE_EXCEEDED = new ErrorCode(1_020_014_002, "附件大小超过上限 {}MB");
    ErrorCode FILE_READ_FAILED = new ErrorCode(1_020_014_003, "读取上传附件失败，请重试");
    ErrorCode FILE_GID_NOT_EXISTS = new ErrorCode(1_020_014_004, "没有找到该临时分组下的待绑定附件：{}");
    ErrorCode FILE_OBJECT_TYPE_REQUIRED = new ErrorCode(1_020_014_005, "所属对象类型不能为空");
    ErrorCode FILE_TITLE_REQUIRED = new ErrorCode(1_020_014_006, "附件名称不能为空");

    // ========== 文档 doc 1-020-015-000 ==========
    // 文档库
    ErrorCode DOC_LIB_NOT_EXISTS = new ErrorCode(1_020_015_000, "文档库不存在：{}");
    ErrorCode DOC_LIB_NAME_DUPLICATE = new ErrorCode(1_020_015_001, "同一空间下已存在同名文档库：{}");
    ErrorCode DOC_LIB_TYPE_INVALID = new ErrorCode(1_020_015_002, "文档库类型不合法：{}");
    ErrorCode DOC_LIB_OBJECT_REQUIRED = new ErrorCode(1_020_015_003, "{}类型文档库必须指定所属对象");
    ErrorCode DOC_LIB_PARENT_REQUIRED = new ErrorCode(1_020_015_004, "自定义文档库必须挂在某个团队空间下");
    ErrorCode DOC_LIB_IS_MAIN = new ErrorCode(1_020_015_005, "内置主库（跟随产品/项目/执行）不允许删除");
    ErrorCode DOC_LIB_HAS_DOCS = new ErrorCode(1_020_015_006, "该文档库下还有 {} 篇文档，不能删除");
    // 文档
    ErrorCode DOC_NOT_EXISTS = new ErrorCode(1_020_015_010, "文档不存在：{}");
    ErrorCode DOC_LIB_REQUIRED = new ErrorCode(1_020_015_011, "必须选择文档库");
    ErrorCode DOC_TITLE_DUPLICATE = new ErrorCode(1_020_015_012, "同一章节下已存在同名文档：{}");
    ErrorCode DOC_TYPE_INVALID = new ErrorCode(1_020_015_013, "文档类型不合法：{}");
    ErrorCode DOC_CONTENT_REQUIRED = new ErrorCode(1_020_015_014, "{}类型的文档必须填写正文");
    ErrorCode DOC_URL_INVALID = new ErrorCode(1_020_015_015, "链接地址不合法：{}");
    ErrorCode DOC_STATUS_INVALID = new ErrorCode(1_020_015_016, "文档状态不合法：{}");
    ErrorCode DOC_PARENT_NOT_CHAPTER = new ErrorCode(1_020_015_017, "上级必须是章节（chapter），编号 {} 不是章节");
    ErrorCode DOC_PARENT_IS_SELF_DESCENDANT = new ErrorCode(1_020_015_018, "不能把节点移动到它自己的子节点下");
    ErrorCode DOC_PARENT_NOT_SAME_LIB = new ErrorCode(1_020_015_019, "上级章节与当前文档不在同一个文档库");
    ErrorCode DOC_CHAPTER_NOT_LEAF = new ErrorCode(1_020_015_020, "章节下还有 {} 个子节点，不能删除");
    ErrorCode DOC_CHAPTER_IS_NOT_CHAPTER = new ErrorCode(1_020_015_021, "编号 {} 不是章节，不能作为目录节点");
    ErrorCode DOC_VERSION_NOT_EXISTS = new ErrorCode(1_020_015_022, "文档版本不存在：v{}");
    ErrorCode DOC_ALREADY_PUBLISHED = new ErrorCode(1_020_015_023, "文档当前不是草稿状态，无需发布");
    ErrorCode DOC_DRAFT_NO_CONTENT = new ErrorCode(1_020_015_024, "草稿还没有内容，不能发布");
    ErrorCode DOC_ATTACHMENT_REQUIRED = new ErrorCode(1_020_015_025, "附件类型文档必须上传附件");

    // ========== 测试用例 testcase 1-020-016-000 ==========
    ErrorCode CASE_NOT_EXISTS = new ErrorCode(1_020_016_000, "测试用例不存在：{}");
    ErrorCode CASE_TITLE_REQUIRED = new ErrorCode(1_020_016_001, "用例标题不能为空");
    ErrorCode CASE_TYPE_INVALID = new ErrorCode(1_020_016_002, "用例类型不合法：{}");
    ErrorCode CASE_STAGE_INVALID = new ErrorCode(1_020_016_003, "测试环节不合法：{}");
    ErrorCode CASE_STATUS_INVALID = new ErrorCode(1_020_016_004, "用例状态不合法：{}");
    ErrorCode CASE_PRODUCT_REQUIRED = new ErrorCode(1_020_016_005, "必须选择所属产品");
    ErrorCode CASE_STORY_NOT_IN_PRODUCT = new ErrorCode(1_020_016_007, "需求 {} 不属于该用例的产品，不能关联");
    ErrorCode CASE_STEP_GROUP_REQUIRED = new ErrorCode(1_020_016_008, "步骤组不能有预期结果（第 {} 步）");
    ErrorCode CASE_STEP_PARENT_INVALID = new ErrorCode(1_020_016_009, "第 {} 步的上级步骤组不在本次提交里");
    ErrorCode CASE_STEP_TOO_DEEP = new ErrorCode(1_020_016_010, "步骤最多三级，第 {} 步超出了层级");
    ErrorCode CASE_VERSION_NOT_EXISTS = new ErrorCode(1_020_016_011, "用例版本不存在：v{}");
    ErrorCode CASE_REVIEW_RESULT_INVALID = new ErrorCode(1_020_016_012, "评审结果不合法：{}");
    ErrorCode CASE_NOT_WAIT_REVIEW = new ErrorCode(1_020_016_013, "只有「待评审」的用例才能评审，当前状态：{}");
    ErrorCode CASE_STORY_VERSION_IS_LATEST = new ErrorCode(1_020_016_014, "该用例关联的需求版本已是最新，无需确认");
    ErrorCode CASE_STORY_VERSION_CHANGED = new ErrorCode(1_020_016_015, "需求「{}」的版本已从 v{} 变到 v{}，请先确认变更");
    ErrorCode CASE_RELATED_TO_TESTTASK = new ErrorCode(1_020_016_016, "该用例已被 {} 个测试单引用，不能删除");

    // ========== 测试单 testtask 1-020-017-000 ==========
    ErrorCode TEST_TASK_NOT_EXISTS = new ErrorCode(1_020_017_000, "测试单不存在：{}");
    ErrorCode TEST_TASK_DATE_INVALID = new ErrorCode(1_020_017_001, "计划结束日期不能早于开始日期");
    ErrorCode TEST_TASK_STATUS_ILLEGAL = new ErrorCode(1_020_017_002, "测试单当前状态为「{}」，不允许执行该操作");
    ErrorCode TEST_TASK_FINISHED_DATE_REQUIRED = new ErrorCode(1_020_017_003, "关闭测试单必须填写实际完成时间");
    ErrorCode TEST_TASK_FINISHED_DATE_LESS = new ErrorCode(1_020_017_004, "实际完成时间不能早于计划开始日期 {}");
    ErrorCode TEST_TASK_FINISHED_DATE_MORE = new ErrorCode(1_020_017_005, "实际完成时间不能晚于明天");
    ErrorCode TEST_TASK_PRODUCT_REQUIRED = new ErrorCode(1_020_017_006, "必须选择所属产品");
    ErrorCode TEST_RUN_NOT_EXISTS = new ErrorCode(1_020_017_010, "测试单里没有这条用例：{}");
    ErrorCode TEST_RUN_CASE_EMPTY = new ErrorCode(1_020_017_011, "至少要选择一条用例");
    ErrorCode TEST_RUN_CASE_NOT_IN_PRODUCT = new ErrorCode(1_020_017_012, "用例 {} 不属于该测试单的产品，不能排进来");
    ErrorCode TEST_RUN_CASE_REQUIRED = new ErrorCode(1_020_017_013, "执行用例必须提供步骤结果");
    ErrorCode TEST_RESULT_INVALID = new ErrorCode(1_020_017_014, "执行结果不合法：{}");
    ErrorCode TEST_STEP_NOT_BELONG = new ErrorCode(1_020_017_015, "步骤 {} 不属于该用例的当前版本");

    // ========== 用例集 / 测试报告 testreport 1-020-018-000 ==========
    ErrorCode TEST_SUITE_NOT_EXISTS = new ErrorCode(1_020_018_000, "用例集不存在：{}");
    ErrorCode TEST_SUITE_NAME_DUPLICATE = new ErrorCode(1_020_018_001, "同产品下已存在同名用例集：{}");
    ErrorCode TEST_SUITE_CASE_NOT_IN_PRODUCT = new ErrorCode(1_020_018_002, "用例 {} 不属于该用例集的产品，不能加进来");
    ErrorCode TEST_SUITE_CASE_EMPTY = new ErrorCode(1_020_018_003, "至少要选择一条用例");
    ErrorCode TEST_SUITE_HAS_CASE = new ErrorCode(1_020_018_004, "用例集里还有 {} 条用例，不能删除");
    ErrorCode TEST_REPORT_NOT_EXISTS = new ErrorCode(1_020_018_010, "测试报告不存在：{}");
    ErrorCode TEST_REPORT_DATE_REQUIRED = new ErrorCode(1_020_018_011, "统计的起止日期不能为空");
    ErrorCode TEST_REPORT_DATE_INVALID = new ErrorCode(1_020_018_012, "统计结束日期不能早于开始日期");
    ErrorCode TEST_REPORT_TASK_REQUIRED = new ErrorCode(1_020_018_013, "至少要选择一个要汇总的测试单");
    ErrorCode TEST_REPORT_TASK_NOT_IN_PRODUCT = new ErrorCode(1_020_018_014, "测试单 {} 不属于该报告的产品，不能汇总");

    // ========== 工时明细（zentao effort） 1_020_019_xxx ==========

    ErrorCode EFFORT_NOT_EXISTS = new ErrorCode(1_020_019_000, "工时记录不存在");
    ErrorCode EFFORT_CANNOT_MOVE = new ErrorCode(1_020_019_001, "工时记录不能改挂到另一个任务上");

    // ========== 项目集（zentao program） 1_020_020_xxx ==========

    ErrorCode PROGRAM_NOT_EXISTS = new ErrorCode(1_020_020_000, "项目集不存在");
    ErrorCode PROGRAM_NAME_DUPLICATE = new ErrorCode(1_020_020_001, "同级下已存在同名项目集：{}");
    ErrorCode PROGRAM_CLOSED_CANNOT_UPDATE = new ErrorCode(1_020_020_002, "项目集已关闭，不能再修改");
    ErrorCode PROGRAM_STATUS_ILLEGAL = new ErrorCode(1_020_020_003, "项目集当前状态为「{}」，不允许执行该操作");
    ErrorCode PROGRAM_ALREADY_CLOSED = new ErrorCode(1_020_020_004, "项目集已经是关闭状态");
    ErrorCode PROGRAM_HAS_CHILD_PROGRAM = new ErrorCode(1_020_020_005, "项目集下还有 {} 个子项目集，不能删除");
    ErrorCode PROGRAM_HAS_PROJECT = new ErrorCode(1_020_020_006, "项目集下还有 {} 个项目，不能删除");
    ErrorCode PROGRAM_HAS_PRODUCT = new ErrorCode(1_020_020_007, "项目集下还有 {} 个产品，不能删除");
    ErrorCode PROGRAM_END_BEFORE_BEGIN = new ErrorCode(1_020_020_008, "计划结束日期不能早于开始日期");
    ErrorCode PROGRAM_PARENT_NOT_PROGRAM = new ErrorCode(1_020_020_009, "编号 {} 不是项目集，不能作为上级项目集");
    ErrorCode PROGRAM_NAME_REQUIRED = new ErrorCode(1_020_020_010, "项目集名称不能为空");

    // ===== 产品 ⇄ 项目集 =====

    ErrorCode PRODUCT_PROGRAM_NOT_EXISTS = new ErrorCode(1_020_020_011, "所属项目集不存在：{}");
    ErrorCode PRODUCT_LINE_NOT_EXISTS = new ErrorCode(1_020_020_012, "产品线不存在：{}");

    // ========== 待办（zentao todo） 1_020_021_xxx ==========

    ErrorCode TODO_NOT_EXISTS = new ErrorCode(1_020_021_000, "待办不存在");
    ErrorCode TODO_STATUS_ILLEGAL = new ErrorCode(1_020_021_001, "待办当前状态为「{}」，不允许执行该操作");
    ErrorCode TODO_ALREADY_DONE = new ErrorCode(1_020_021_002, "待办已经是完成状态");
    ErrorCode TODO_ALREADY_CLOSED = new ErrorCode(1_020_021_003, "待办已经是关闭状态");
    ErrorCode TODO_TYPE_INVALID = new ErrorCode(1_020_021_004, "待办类型不合法：{}");
    ErrorCode TODO_OBJECT_REQUIRED = new ErrorCode(1_020_021_005, "{}类型的待办必须关联一个对象");

    // ========== 项目/执行团队（zentao team） 1_020_022_xxx ==========

    ErrorCode TEAM_MEMBER_NOT_EXISTS = new ErrorCode(1_020_022_000, "团队成员不存在");
    ErrorCode TEAM_MEMBER_EXISTS = new ErrorCode(1_020_022_001, "成员已在团队里：{}");
    ErrorCode TEAM_TYPE_INVALID = new ErrorCode(1_020_022_002, "团队成员类型不合法：{}");

    // ========== 干系人（zentao stakeholder） 1_020_023_xxx ==========

    ErrorCode STAKEHOLDER_NOT_EXISTS = new ErrorCode(1_020_023_000, "干系人不存在");
    ErrorCode STAKEHOLDER_USER_EXISTS = new ErrorCode(1_020_023_001, "该干系人已经在这个对象下：{}");
    ErrorCode STAKEHOLDER_OBJECT_TYPE_INVALID = new ErrorCode(1_020_023_002, "干系人只能挂在项目集/项目下，不支持：{}");
    ErrorCode STAKEHOLDER_FROM_INVALID = new ErrorCode(1_020_023_003, "干系人来源不合法：{}");

    // ========== 用例库（zentao caselib） 1_020_024_xxx ==========
    // 用例库不是独立表：它是 zt_testsuite 里 (product=0, type='library') 的那批行，
    // 库内用例是 zt_case 里 (product=0, lib=<用例库编号>) 的行（见 README 3.32）

    ErrorCode CASE_LIB_NOT_EXISTS = new ErrorCode(1_020_024_000, "用例库不存在：{}");
    ErrorCode CASE_LIB_NAME_DUPLICATE = new ErrorCode(1_020_024_001, "用例库名称已存在：{}");
    ErrorCode CASE_LIB_HAS_CASE = new ErrorCode(1_020_024_002, "用例库下还有 {} 条用例，请先删除用例");
    ErrorCode CASE_LIB_NOT_A_LIBRARY = new ErrorCode(1_020_024_003, "编号 {} 不是用例库（zt_testsuite.type 不是 library）");
    ErrorCode CASE_LIB_CASE_REQUIRED = new ErrorCode(1_020_024_004, "用例必须属于一个产品或者一个用例库");
    ErrorCode CASE_LIB_IMPORT_NOT_A_PRODUCT_CASE = new ErrorCode(1_020_024_005, "用例 {} 是用例库里的用例，不能再导入用例库");
    ErrorCode CASE_LIB_IMPORT_ALREADY = new ErrorCode(1_020_024_006, "用例 {} 已经导入过该用例库");

    // ========== 看板（zentao kanban） 1_020_025_xxx ==========
    // 看板是七层聚合：空间 → 看板 → 区域 → 分组 → 泳道/列 → 卡片，位置存在 zt_kanbancell.cards（见 README 3.33）

    ErrorCode KANBAN_SPACE_NOT_EXISTS = new ErrorCode(1_020_025_000, "看板空间不存在：{}");
    ErrorCode KANBAN_SPACE_NAME_DUPLICATE = new ErrorCode(1_020_025_001, "看板空间名称已存在：{}");
    ErrorCode KANBAN_SPACE_HAS_KANBAN = new ErrorCode(1_020_025_002, "空间下还有 {} 个看板，请先删除看板");
    ErrorCode KANBAN_SPACE_TYPE_INVALID = new ErrorCode(1_020_025_003, "空间类型不合法：{}");

    ErrorCode KANBAN_NOT_EXISTS = new ErrorCode(1_020_025_010, "看板不存在：{}");
    ErrorCode KANBAN_NAME_DUPLICATE = new ErrorCode(1_020_025_011, "同一空间下已存在同名看板：{}");
    ErrorCode KANBAN_HAS_CARD = new ErrorCode(1_020_025_012, "看板里还有 {} 张卡片，请先删除卡片");
    ErrorCode KANBAN_NOT_CLOSED_CANNOT_ACTIVATE = new ErrorCode(1_020_025_013, "看板已经是打开状态，无需激活");
    ErrorCode KANBAN_ALREADY_CLOSED = new ErrorCode(1_020_025_014, "看板已经是关闭状态");

    ErrorCode KANBAN_REGION_NOT_EXISTS = new ErrorCode(1_020_025_020, "看板区域不存在：{}");
    ErrorCode KANBAN_REGION_NAME_DUPLICATE = new ErrorCode(1_020_025_021, "同一看板下区域名称已存在：{}");

    ErrorCode KANBAN_LANE_NOT_EXISTS = new ErrorCode(1_020_025_030, "看板泳道不存在：{}");
    ErrorCode KANBAN_LANE_TYPE_INVALID = new ErrorCode(1_020_025_031, "看板泳道类型不合法：{}");

    ErrorCode KANBAN_COLUMN_NOT_EXISTS = new ErrorCode(1_020_025_040, "看板列不存在：{}");
    ErrorCode KANBAN_COLUMN_LIMIT_INVALID = new ErrorCode(1_020_025_041, "在制品上限（WIP）必须是不限（-1）或正整数：{}");
    ErrorCode KANBAN_COLUMN_CHILD_LIMIT_EXCEEDED = new ErrorCode(1_020_025_042,
            "子列的在制品上限之和（{}）不能超过父列的限额（{}），且父列有限额时子列不能不限");
    ErrorCode KANBAN_COLUMN_HAS_CHILD = new ErrorCode(1_020_025_043, "该列已拆分成 {} 个子列，不能删除");

    ErrorCode KANBAN_CARD_NOT_EXISTS = new ErrorCode(1_020_025_050, "看板卡片不存在：{}");
    ErrorCode KANBAN_CARD_ESTIMATE_INVALID = new ErrorCode(1_020_025_051, "预计工时不能为负数");
    ErrorCode KANBAN_CARD_DATE_INVALID = new ErrorCode(1_020_025_052, "截止日期不能早于预计开始日期");
    ErrorCode KANBAN_CARD_PROGRESS_INVALID = new ErrorCode(1_020_025_053, "进度必须在 0~99 之间（100 请用「完成卡片」）");

    // ========== 度量（zentao metric） 1_020_026_xxx ==========
    // 度量项定义在 zt_metric、数据在 zt_metriclib；口径实现在代码里（禅道 calc 类 / 本实现 Java 注册表）

    ErrorCode METRIC_NOT_EXISTS = new ErrorCode(1_020_026_000, "度量项不存在：{}");
    ErrorCode METRIC_CODE_DUPLICATE = new ErrorCode(1_020_026_001, "度量项代码已存在：{}");
    ErrorCode METRIC_CALC_NOT_IMPLEMENTED = new ErrorCode(1_020_026_002,
            "度量项 {} 的口径尚未迁移（禅道用 module/metric/calc 下的 calc 类实现，本实现按需迁移，见 README 3.34）");
    ErrorCode METRIC_DATE_TYPE_INVALID = new ErrorCode(1_020_026_003, "度量时间维度不合法：{}");
    ErrorCode METRIC_SCOPE_INVALID = new ErrorCode(1_020_026_004, "度量范围不合法：{}");
    ErrorCode METRIC_PURPOSE_INVALID = new ErrorCode(1_020_026_005, "度量目的不合法：{}");

    // ========== 数据视图 / 图表（zentao bi） 1_020_027_xxx ==========
    // 数据视图存一条**只读 SELECT**，执行前过 SqlGuard 白名单（单语句 / 必须 SELECT / 只允许 zt_* 表）

    ErrorCode BI_DATA_VIEW_NOT_EXISTS = new ErrorCode(1_020_027_000, "数据视图不存在：{}");
    ErrorCode BI_DATA_VIEW_CODE_DUPLICATE = new ErrorCode(1_020_027_001, "数据视图代码已存在：{}");
    ErrorCode BI_SQL_INVALID = new ErrorCode(1_020_027_002, "SQL 不合法：{}");
    ErrorCode BI_SQL_FORBIDDEN_TABLE = new ErrorCode(1_020_027_003, "只允许查询禅道自己的表（zt_ 开头），不能查：{}");
    ErrorCode BI_SQL_EXECUTE_FAILED = new ErrorCode(1_020_027_004, "SQL 执行失败：{}");
    ErrorCode BI_DATA_VIEW_USED_BY_CHART = new ErrorCode(1_020_027_005, "该数据视图被 {} 个图表引用，请先删除图表");

    ErrorCode BI_CHART_NOT_EXISTS = new ErrorCode(1_020_027_010, "图表不存在：{}");
    ErrorCode BI_CHART_TYPE_INVALID = new ErrorCode(1_020_027_011, "图表类型不合法：{}");
    ErrorCode BI_CHART_AGG_INVALID = new ErrorCode(1_020_027_012, "聚合方式不合法（只支持 count/sum/avg/max/min）：{}");
    ErrorCode BI_CHART_SETTINGS_INVALID = new ErrorCode(1_020_027_013, "图表设置不合法：{}");
    ErrorCode BI_CHART_FIELD_INVALID = new ErrorCode(1_020_027_014, "字段名不合法（只能是字母/数字/下划线）：{}");

    // ========== 操作日志 / 回收站 / 动态（zentao action） 1_020_028_xxx ==========
    ErrorCode ACTION_NOT_EXISTS = new ErrorCode(1_020_028_000, "操作日志不存在：{}");
    ErrorCode ACTION_NOT_DELETED = new ErrorCode(1_020_028_001, "只有「删除」类型的操作日志才能还原或隐藏：{}");
    ErrorCode ACTION_HIDDEN_CANNOT_UNDELETE = new ErrorCode(1_020_028_002, "该记录已从回收站隐藏，不能还原");
    ErrorCode ACTION_UNDELETE_UNSUPPORTED = new ErrorCode(1_020_028_003, "对象类型「{}」不支持回收站还原");
    ErrorCode ACTION_OBJECT_NOT_FOUND = new ErrorCode(1_020_028_004, "对象已经不存在，无法还原：{}#{}");
    ErrorCode ACTION_OBJECT_NOT_DELETED = new ErrorCode(1_020_028_005, "对象不是删除状态，无需还原：{}#{}");
    ErrorCode ACTION_COMMENT_EMPTY = new ErrorCode(1_020_028_006, "备注内容不能为空");
    ErrorCode ACTION_COMMENT_NOT_EXISTS = new ErrorCode(1_020_028_007, "备注不存在：{}");
    ErrorCode ACTION_COMMENT_NOT_OWNER = new ErrorCode(1_020_028_008, "只能修改自己发的备注");

}

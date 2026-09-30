package cn.iocoder.yudao.module.zentao.service.score;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分规则表（禅道 {@code module/score/config.php} 的 1:1 移植）。
 *
 * <h3>规则长什么样</h3>
 * 每条规则是四元组 {@code (module, method, times, hour, score)}：
 * <pre>
 *   score  —— 这次计几分
 *   times  —— 最多计几次（0 = 不限）
 *   hour   —— 时间窗口（0 = 不限；禅道配的都是 24，但实现是按「当天」数条数，见 ScoreMapper#countByDay）
 * </pre>
 * 例：`user.login = (3, 24, 1)` = 登录 1 分，但**一天最多 3 次**；`tutorial.finish = (1, 0, 100)` = 新手教程完成 1 次 100 分。
 *
 * <h3>还有一层「扩展加成」（ruleExtended）</h3>
 * 四种成对出现的加成分：缺陷严重程度（s1 +3 / s2 +2 / s3 +1）、任务优先级（p1 +2 / p2 +1 / p3 +0）、
 * 密码强度（中 +2 / 强 +5）、执行关闭的（项目经理 +20、成员 +5；按期或提前完成再加 +10 / +5）。
 * 另外「需求关闭」会给**需求的创建者**额外 2 分 —— 这条不在加成表里，是 create() 里的特例。
 *
 * <p>把这张表放在代码里而不是数据库：禅道的规则是**配置**不是数据（管理员只能开关积分功能，
 * 不能改分数），所以端口化最忠实，也避免多一张没人维护的表。
 */
public final class ScoreRules {

    /** 一条积分规则 */
    public record Rule(String module, String method, int times, int hour, int score) {

        public boolean unlimited() {
            return times == 0 && hour == 0;
        }
    }

    /** 模块中文名（禅道 {@code $lang->score->modules}） */
    private static final Map<String, String> MODULE_NAMES = new LinkedHashMap<>();
    /** 动作中文名（禅道 {@code $lang->score->methods}） */
    private static final Map<String, Map<String, String>> METHOD_NAMES = new LinkedHashMap<>();
    /** 规则本体：module → method → rule（LinkedHashMap 保证「积分规则」页面的顺序与禅道一致） */
    private static final Map<String, Map<String, Rule>> RULES = new LinkedHashMap<>();
    /** 扩展加成：module → method → (键 → 分值) */
    private static final Map<String, Map<String, Map<String, Integer>>> EXTENDED = new LinkedHashMap<>();

    /* 需求关闭给创建者的额外分（禅道 ruleExtended['story']['close']['createID'] = 2） */
    public static final int STORY_CLOSE_CREATOR_SCORE = 2;
    /** 执行关闭：项目经理 / 成员 的基础分与按期加分 */
    public static final int EXECUTION_MANAGER_CLOSE = 20;
    public static final int EXECUTION_MANAGER_ON_TIME = 10;
    public static final int EXECUTION_MEMBER_CLOSE = 5;
    public static final int EXECUTION_MEMBER_ON_TIME = 5;

    static {
        MODULE_NAMES.put("task", "任务");
        MODULE_NAMES.put("tutorial", "新手教程");
        MODULE_NAMES.put("user", "用户");
        MODULE_NAMES.put("ajax", "其它");
        MODULE_NAMES.put("doc", "文档");
        MODULE_NAMES.put("todo", "待办");
        MODULE_NAMES.put("story", "需求");
        MODULE_NAMES.put("bug", "Bug");
        MODULE_NAMES.put("testcase", "用例");
        MODULE_NAMES.put("testtask", "测试单");
        MODULE_NAMES.put("build", "版本");
        MODULE_NAMES.put("execution", "执行");
        MODULE_NAMES.put("productplan", "计划");
        MODULE_NAMES.put("release", "发布");
        MODULE_NAMES.put("block", "区块");
        MODULE_NAMES.put("search", "搜索");

        method("task", "create", "创建任务");
        method("task", "close", "关闭任务");
        method("task", "finish", "完成任务");
        method("tutorial", "finish", "学习完成");
        method("user", "login", "登录");
        method("user", "changePassword", "修改密码");
        method("user", "editProfile", "修改个人资料");
        method("ajax", "selectTheme", "切换主题");
        method("ajax", "selectLang", "切换语言");
        method("ajax", "showSearchMenu", "搜索高级用法");
        method("ajax", "customMenu", "自定义菜单");
        method("ajax", "dragSelected", "列表页面拖动选中");
        method("ajax", "lastNext", "快捷键翻页");
        method("ajax", "switchToDataTable", "使用高级表格");
        method("ajax", "submitPage", "修改分页数量");
        method("ajax", "quickJump", "使用快速跳转");
        method("ajax", "batchCreate", "首次使用批量添加");
        method("ajax", "batchEdit", "首次使用批量编辑");
        method("ajax", "batchOther", "其它批量操作");
        method("doc", "create", "创建文档");
        method("todo", "create", "创建待办");
        method("story", "create", "创建需求");
        method("story", "close", "需求关闭");
        method("bug", "create", "创建Bug");
        method("bug", "confirm", "确认Bug");
        method("bug", "createFormCase", "从用例创建");
        method("bug", "resolve", "解决Bug");
        method("bug", "saveTplModal", "保存模板");
        method("testtask", "runCase", "执行用例");
        method("testcase", "create", "创建用例");
        method("build", "create", "创建版本");
        method("execution", "create", "创建执行");
        method("execution", "close", "执行完成");
        method("productplan", "create", "创建计划");
        method("release", "create", "创建发布");
        method("block", "set", "区块自定义设置");
        method("search", "saveQuery", "保存搜索条件");
        method("search", "saveQueryAdvanced", "使用高级搜索");

        /* ===== 规则本体，分值/次数/时间窗全部照抄 config.php ===== */
        rule("user", "login", 3, 24, 1);
        rule("user", "editProfile", 1, 0, 10);
        rule("user", "changePassword", 1, 0, 10);

        rule("ajax", "lastNext", 1, 0, 20);
        rule("ajax", "batchEdit", 1, 0, 20);
        rule("ajax", "quickJump", 1, 0, 10);
        rule("ajax", "customMenu", 1, 0, 1);
        rule("ajax", "submitPage", 1, 0, 1);
        rule("ajax", "selectLang", 1, 0, 10);
        rule("ajax", "batchOther", 1, 0, 1);
        rule("ajax", "selectTheme", 1, 0, 10);
        rule("ajax", "batchCreate", 1, 0, 20);
        rule("ajax", "dragSelected", 1, 0, 20);
        rule("ajax", "showSearchMenu", 1, 0, 10);
        rule("ajax", "switchToDataTable", 1, 0, 1);

        rule("doc", "create", 0, 0, 5);

        rule("bug", "create", 0, 0, 1);
        rule("bug", "resolve", 0, 0, 1);
        rule("bug", "confirm", 0, 0, 1);
        rule("bug", "saveTplModal", 1, 0, 20);
        rule("bug", "createFormCase", 0, 0, 1);

        rule("task", "close", 0, 0, 1);
        rule("task", "create", 0, 0, 1);
        rule("task", "finish", 0, 0, 1);

        rule("todo", "create", 5, 24, 1);
        rule("block", "set", 1, 0, 20);
        rule("story", "close", 0, 0, 1);
        rule("story", "create", 0, 0, 1);
        rule("build", "create", 0, 0, 10);
        rule("execution", "close", 0, 0, 0);   /* 分值走扩展表：PM 20 / 成员 5 */
        rule("execution", "create", 0, 0, 10);
        rule("release", "create", 0, 0, 10);
        rule("testcase", "create", 0, 0, 1);
        rule("tutorial", "finish", 1, 0, 100);
        rule("testtask", "runCase", 0, 0, 1);
        rule("productplan", "create", 0, 0, 10);
        rule("search", "saveQuery", 1, 0, 1);
        rule("search", "saveQueryAdvanced", 1, 0, 1);

        /* ===== 扩展加成（ruleExtended）：键是「字段名 + 取值」，取值来自 zen 层的真实数据 ===== */
        EXTENDED.computeIfAbsent("user", k -> new LinkedHashMap<>()).put("changePassword", Map.of("strength1", 2, "strength2", 5));
        EXTENDED.computeIfAbsent("bug", k -> new LinkedHashMap<>()).put("confirm", Map.of("severity1", 3, "severity2", 2, "severity3", 1));
        EXTENDED.computeIfAbsent("bug", k -> new LinkedHashMap<>()).put("resolve", Map.of("severity1", 3, "severity2", 2, "severity3", 1));
        EXTENDED.computeIfAbsent("task", k -> new LinkedHashMap<>()).put("finish", Map.of("pri1", 2, "pri2", 1, "pri3", 0));
    }

    private ScoreRules() {
    }

    private static void method(String module, String method, String name) {
        METHOD_NAMES.computeIfAbsent(module, k -> new LinkedHashMap<>()).put(method, name);
    }

    private static void rule(String module, String method, int times, int hour, int score) {
        RULES.computeIfAbsent(module, k -> new LinkedHashMap<>()).put(method, new Rule(module, method, times, hour, score));
    }

    public static Rule find(String module, String method) {
        Map<String, Rule> moduleRules = RULES.get(module);
        return moduleRules == null ? null : moduleRules.get(method);
    }

    /** 「积分规则」页面用的扁平列表（顺序与禅道一致） */
    public static List<Rule> all() {
        List<Rule> list = new ArrayList<>();
        RULES.values().forEach(m -> list.addAll(m.values()));
        return list;
    }

    public static String moduleName(String module) {
        return MODULE_NAMES.getOrDefault(module, module);
    }

    public static String methodName(String module, String method) {
        Map<String, String> map = METHOD_NAMES.get(module);
        return map == null ? method : map.getOrDefault(method, method);
    }

    /** 严重程度加成：s1 +3 / s2 +2 / s3 +1（缺陷的确认与解决都用它） */
    public static int severityBonus(Integer severity) {
        return extendedScore("bug", "resolve", "severity" + severity);
    }

    /** 任务优先级加成：p1 +2 / p2 +1 / p3 +0 */
    public static int priBonus(Integer pri) {
        return extendedScore("task", "finish", "pri" + pri);
    }

    /** 密码强度加成：中 +2 / 强 +5 */
    public static int strengthBonus(Integer strength) {
        return extendedScore("user", "changePassword", "strength" + strength);
    }

    private static int extendedScore(String module, String method, String key) {
        Map<String, Integer> map = EXTENDED.getOrDefault(module, Map.of()).get(method);
        return map == null ? 0 : map.getOrDefault(key, 0);
    }

    /**
     * 动作名 → 计分动作（禅道 {@code fixKey}）：
     * created/opened → create、closed → close、finished → finish、bugconfirmed → confirm、resolved → resolve。
     */
    public static String fixKey(String action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case "created", "opened" -> "create";
            case "closed" -> "close";
            case "finished" -> "finish";
            case "bugconfirmed" -> "confirm";
            case "resolved" -> "resolve";
            default -> action;
        };
    }

    /** 规则说明（对应 $lang->score->extended 的那几句话） */
    public static String extendedDesc(String module, String method) {
        return switch (module + "." + method) {
            case "user.changePassword" -> "密码强度为中，额外获得 " + strengthBonus(1) + " 个积分；为强，额外获得 " + strengthBonus(2) + " 个积分。";
            case "execution.close" -> "项目经理增加 " + EXECUTION_MANAGER_CLOSE + " 个积分，执行成员增加 " + EXECUTION_MEMBER_CLOSE
                    + " 个积分。按期或者提前完成，项目经理额外增加 " + EXECUTION_MANAGER_ON_TIME + " 个积分，执行成员额外增加 "
                    + EXECUTION_MEMBER_ON_TIME + " 个积分。";
            case "bug.resolve", "bug.confirm" -> "额外增加严重程度积分：s1 + " + severityBonus(1) + "，s2 + " + severityBonus(2)
                    + "，s3 + " + severityBonus(3) + "。";
            case "task.finish" -> "额外增加工时积分 round(工时 / 10 * 预计 / 消耗)，以及优先级积分（p1 + " + priBonus(1)
                    + "，p2 + " + priBonus(2) + "）。子任务不给分。";
            case "story.close" -> "需求的创建者额外增加 " + STORY_CLOSE_CREATOR_SCORE + " 分。";
            default -> "";
        };
    }

}

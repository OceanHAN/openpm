package cn.iocoder.yudao.module.zentao.service.report;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgDeptRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.AnnualDataRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.ReportOptionRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.ReportOutputRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.ReminderRespVO;
import cn.iocoder.yudao.module.zentao.dal.mysql.report.ZentaoReportMapper;
import cn.iocoder.yudao.module.zentao.service.organization.OrganizationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 报表服务实现。
 *
 * <h3>这一模块为什么不建表</h3>
 * 禅道 {@code module/report} 全是「读别人的数据算出来」：年度数据、每日提醒、产出统计。
 * 唯一一张 {@code zt_report} 是**老版本自定义报表**的壳（v20 起被 BI 取代，
 * 现在只有 tree/upgrade 在读它），本实现不做，用户要自定义报表走已迁移的 {@code bi}（SQL 模式）。
 *
 * <h3>和禅道的三处有意偏离</h3>
 * <ol>
 *   <li><b>登录次数</b>：禅道每次登录往 {@code zt_action} 写一条 {@code login} 动作，本实现不写，
 *       所以登录数直接读 yudao 的 {@code system_login_log}（真实数据，还带成功/失败）；</li>
 *   <li><b>「完成任务 / 解决缺陷」</b>：禅道有独立的 {@code finished} / {@code resolved} 动作可直接数动作；
 *       本实现里这两个操作记的是 {@code changed}（一次提交同时改了状态和工时，用 CHANGED 更能表达），
 *       动作名分不出来 —— 所以改数 {@code finishedDate} / {@code resolvedDate} 这两个**事实列**，
 *       口径与禅道「年度执行里完成任务数」那条查询一致；</li>
 *   <li><b>每日提醒不发邮件</b>：只产出数据（谁、有哪些事），发信交给 yudao 的通知能力。</li>
 * </ol>
 */
@Service
@Slf4j
public class ZentaoReportServiceImpl implements ZentaoReportService {

    /** 对象类型 → 业务表（拼 SQL 前先过这张白名单，见 ReportMapper 的说明） */
    private static final Map<String, String> OBJECT_TABLES = new LinkedHashMap<>();

    static {
        OBJECT_TABLES.put("product", "zt_product");
        OBJECT_TABLES.put("story", "zt_story");
        OBJECT_TABLES.put("requirement", "zt_story");
        OBJECT_TABLES.put("epic", "zt_story");
        OBJECT_TABLES.put("productplan", "zt_productplan");
        OBJECT_TABLES.put("release", "zt_release");
        OBJECT_TABLES.put("project", "zt_project");
        OBJECT_TABLES.put("execution", "zt_project");
        OBJECT_TABLES.put("task", "zt_task");
        OBJECT_TABLES.put("bug", "zt_bug");
        OBJECT_TABLES.put("build", "zt_build");
        OBJECT_TABLES.put("case", "zt_case");
        OBJECT_TABLES.put("testtask", "zt_testtask");
        OBJECT_TABLES.put("testreport", "zt_testreport");
        OBJECT_TABLES.put("doc", "zt_doc");
    }

    /**
     * 贡献映射：对象类型 → （zt_action.action → 贡献名）。
     * 贡献名沿用禅道的 {@code config/report.php} 那套（create/edit/close/start/...）。
     */
    private static final Map<String, Map<String, String>> CONTRIBUTIONS = new LinkedHashMap<>();

    static {
        CONTRIBUTIONS.put("product", ordered("created", "create", "edited", "edit", "closed", "close"));
        CONTRIBUTIONS.put("story", ordered("created", "create", "reviewed", "review", "changed", "change",
                "closed", "close"));
        CONTRIBUTIONS.put("productplan", ordered("created", "create"));
        CONTRIBUTIONS.put("release", ordered("created", "create"));
        CONTRIBUTIONS.put("project", ordered("created", "create", "edited", "edit", "activated", "start",
                "closed", "close", "deleted", "delete"));
        CONTRIBUTIONS.put("execution", ordered("created", "create", "edited", "edit", "activated", "start",
                "closed", "close"));
        CONTRIBUTIONS.put("task", ordered("created", "create", "assigned", "assign", "activated", "activate",
                "closed", "close"));
        CONTRIBUTIONS.put("bug", ordered("created", "create", "activated", "activate", "closed", "close"));
        CONTRIBUTIONS.put("case", ordered("created", "create"));
        CONTRIBUTIONS.put("testtask", ordered("created", "create", "edited", "edit", "closed", "close"));
        CONTRIBUTIONS.put("doc", ordered("created", "create", "edited", "edit"));
    }

    /** 产出统计映射（禅道 {@code outputData}）：比贡献少几个动作，是「产出」视角 */
    private static final Map<String, Map<String, String>> OUTPUTS = new LinkedHashMap<>();

    static {
        OUTPUTS.put("story", ordered("created", "create", "changed", "change", "reviewed", "review",
                "closed", "close"));
        OUTPUTS.put("productplan", ordered("created", "create"));
        OUTPUTS.put("release", ordered("created", "create", "activated", "activate", "closed", "stop"));
        OUTPUTS.put("execution", ordered("created", "create", "activated", "start", "closed", "close"));
        OUTPUTS.put("task", ordered("created", "create", "assigned", "assign", "activated", "activate",
                "closed", "close"));
        OUTPUTS.put("bug", ordered("created", "create", "activated", "activate", "closed", "close"));
        OUTPUTS.put("case", ordered("created", "create"));
    }

    /** 月度趋势里要统计的动作（按对象类型） */
    private static final Map<String, List<String>> MONTH_ACTIONS = new LinkedHashMap<>();

    static {
        MONTH_ACTIONS.put("story", List.of("created", "activated", "closed", "changed", "reviewed", "deleted"));
        MONTH_ACTIONS.put("task", List.of("created", "activated", "closed", "changed", "deleted"));
        MONTH_ACTIONS.put("bug", List.of("created", "activated", "closed", "edited", "deleted"));
    }

    /** 年度统计里的对象类型分组（需求要把 epic/requirement 一起算进来） */
    private static final Map<String, List<String>> STAT_OBJECT_TYPES = new LinkedHashMap<>();

    static {
        STAT_OBJECT_TYPES.put("story", List.of("story", "requirement", "epic"));
        STAT_OBJECT_TYPES.put("task", List.of("task"));
        STAT_OBJECT_TYPES.put("bug", List.of("bug"));
    }

    private static final Map<String, String> STAT_TABLES = Map.of(
            "story", "zt_story", "task", "zt_task", "bug", "zt_bug");

    /** 雷达图归组：贡献名 → 归到哪几条产线（禅道 {@code annualData.radar}） */
    private static final Map<String, List<String>> RADAR = new LinkedHashMap<>();

    static {
        RADAR.put("product.create", List.of("product"));
        RADAR.put("product.edit", List.of("product"));
        RADAR.put("product.close", List.of("product"));
        RADAR.put("story.create", List.of("product"));
        RADAR.put("story.review", List.of("product"));
        RADAR.put("story.change", List.of("product"));
        RADAR.put("story.close", List.of("product"));
        RADAR.put("productplan.create", List.of("product"));
        RADAR.put("release.create", List.of("product"));
        RADAR.put("project.create", List.of("execution"));
        RADAR.put("project.edit", List.of("execution"));
        RADAR.put("project.start", List.of("execution"));
        RADAR.put("project.close", List.of("execution"));
        RADAR.put("project.delete", List.of("execution"));
        RADAR.put("execution.create", List.of("execution"));
        RADAR.put("execution.edit", List.of("execution"));
        RADAR.put("execution.start", List.of("execution"));
        RADAR.put("execution.close", List.of("execution"));
        RADAR.put("task.create", List.of("execution", "devel"));
        RADAR.put("task.assign", List.of("execution", "devel"));
        RADAR.put("task.activate", List.of("execution", "devel"));
        RADAR.put("task.close", List.of("execution", "devel"));
        RADAR.put("task.finish", List.of("execution", "devel"));
        RADAR.put("bug.resolve", List.of("devel"));
        RADAR.put("bug.create", List.of("qa"));
        RADAR.put("bug.activate", List.of("qa"));
        RADAR.put("bug.close", List.of("qa"));
        RADAR.put("case.create", List.of("qa"));
        RADAR.put("case.run", List.of("qa"));
        RADAR.put("testtask.create", List.of("qa"));
        RADAR.put("testtask.edit", List.of("qa"));
    }

    private static final List<String> RADAR_TYPES = List.of("product", "execution", "devel", "qa", "other");

    /** 算「贡献数」时计入的对象类型（对应禅道 {@code contributionCount}） */
    private static final List<String> CONTRIBUTION_COUNT_TYPES =
            List.of("task", "story", "bug", "case", "testtask", "doc");

    private static final Map<String, String> OBJECT_NAMES = Map.ofEntries(
            Map.entry("product", "产品"), Map.entry("story", "需求"), Map.entry("productplan", "产品计划"),
            Map.entry("release", "发布"), Map.entry("project", "项目"), Map.entry("execution", "执行"),
            Map.entry("task", "任务"), Map.entry("bug", "缺陷"), Map.entry("case", "用例"),
            Map.entry("testtask", "测试单"), Map.entry("doc", "文档"));

    private static final Map<String, String> ACTION_NAMES = Map.ofEntries(
            Map.entry("create", "创建"), Map.entry("edit", "编辑"), Map.entry("close", "关闭"),
            Map.entry("start", "开始"), Map.entry("delete", "删除"), Map.entry("change", "变更"),
            Map.entry("review", "评审"), Map.entry("finish", "完成"), Map.entry("activate", "激活"),
            Map.entry("assign", "指派"), Map.entry("stop", "停止维护"), Map.entry("run", "执行"),
            Map.entry("createBug", "建缺陷"));

    /** 禅道 annualData.minMonth：2 月及以前默认看去年（年初数据太少，看去年更有意义） */
    private static final int MIN_MONTH = 2;

    // 字段名必须带前缀（同 Controller 的理由）：testreport 那边也有一个叫 reportMapper 的字段，
    // @Resource 先按名字注入，会拿到 TestReportMapper 的代理然后类型不匹配（坑位 #25）
    @Resource
    private ZentaoReportMapper zentaoReportMapper;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private OrganizationService organizationService;

    // ==================== 筛选项 ====================

    @Override
    public ReportOptionRespVO getOptions() {
        ReportOptionRespVO vo = new ReportOptionRespVO();
        List<String> years = availableYears();
        vo.setYears(years);
        vo.setCurrent(defaultYear(years));
        vo.setDepts(deptOptions());
        vo.setUsers(listUsers(null));
        return vo;
    }

    // ==================== 年度数据 ====================

    @Override
    public AnnualDataRespVO getAnnualData(String year, Long deptId, String account) {
        List<String> years = availableYears();
        String targetYear = StringUtils.hasText(year) ? year : defaultYear(years);
        List<String> accounts = resolveAccounts(deptId, account);

        AnnualDataRespVO vo = new AnnualDataRespVO();
        vo.setYear(targetYear);
        vo.setMode(!StringUtils.hasText(account) ? (deptId != null && deptId > 0 ? "dept" : "company") : "user");
        vo.setWho(resolveWho(deptId, account));
        vo.setMonths(months(targetYear));

        // 1. 基础指标
        if ("company".equals(vo.getMode())) {
            vo.setUsers(accounts.isEmpty() ? listUsers(null).size() : accounts.size());
        } else if ("user".equals(vo.getMode())) {
            vo.setLogins(nvl(zentaoReportMapper.countLogins(targetYear, accounts)));
        }
        vo.setActions(nvl(zentaoReportMapper.countActions(targetYear, accounts)));
        Map<String, Object> todoStat = zentaoReportMapper.selectTodoStat(targetYear, accounts);
        if (todoStat != null) {
            vo.getTodos().setCount(intOf(todoStat.get("count")));
            vo.getTodos().setUndone(intOf(todoStat.get("undone")));
            vo.getTodos().setDone(intOf(todoStat.get("done")));
        }
        Map<String, Object> effortStat = zentaoReportMapper.selectEffortStat(targetYear, accounts);
        if (effortStat != null) {
            vo.setConsumed(decimalOf(effortStat.get("consumed")));
        }

        // 2. 贡献（含各年雷达）：每个对象类型一条 SQL，年份一次取回
        List<String> allYears = years.isEmpty() ? List.of(targetYear) : years;
        Map<String, Map<String, Map<String, Integer>>> byYear = new LinkedHashMap<>(); // year -> type -> name -> cnt
        for (String objectType : CONTRIBUTIONS.keySet()) {
            Map<String, String> actionMap = CONTRIBUTIONS.get(objectType);
            List<Map<String, Object>> rows = zentaoReportMapper.selectActionCountByYears(allYears, objectType,
                    OBJECT_TABLES.get(objectType), new ArrayList<>(actionMap.keySet()), accounts);
            for (Map<String, Object> row : rows) {
                String action = str(row.get("action"));
                String contributionName = actionMap.get(action);
                if (contributionName == null) {
                    continue;
                }
                String actionYear = str(row.get("year"));
                byYear.computeIfAbsent(actionYear, k -> new LinkedHashMap<>())
                        .computeIfAbsent(objectType, k -> new LinkedHashMap<>())
                        .merge(contributionName, intOf(row.get("cnt")), Integer::sum);
            }
        }
        // 三个「按事实列数」的贡献：完成任务、解决缺陷、执行用例。
        // 只在有条数时写入 —— 否则空年份会返回 {task:{}, bug:{}, case:{run:0}} 这种看着像有数据的空壳
        Map<String, Map<String, Integer>> taskYear = byYear.computeIfAbsent(targetYear, k -> new LinkedHashMap<>());
        mergePositive(taskYear, "task", "finish",
                intOf(nvl(zentaoReportMapper.countFinishedTasks(targetYear, accounts))));
        mergePositive(taskYear, "bug", "resolve",
                intOf(nvl(zentaoReportMapper.countResolvedBugs(targetYear, accounts))));
        mergePositive(taskYear, "case", "run",
                intOf(nvl(zentaoReportMapper.countCaseRuns(targetYear, accounts))));

        vo.setContributions(byYear.getOrDefault(targetYear, new LinkedHashMap<>()));
        int maxCount = 0;
        for (String yearValue : allYears) {
            Map<String, Integer> radar = emptyRadar();
            for (Map.Entry<String, Map<String, Integer>> typeEntry
                    : byYear.getOrDefault(yearValue, new LinkedHashMap<>()).entrySet()) {
                int sum = typeEntry.getValue().values().stream().mapToInt(Integer::intValue).sum();
                maxCount = yearValue.equals(targetYear) ? Math.max(maxCount, sum) : maxCount;
                for (Map.Entry<String, Integer> contribution : typeEntry.getValue().entrySet()) {
                    List<String> radarTypes = RADAR.get(typeEntry.getKey() + "." + contribution.getKey());
                    if (radarTypes == null) {
                        radarTypes = List.of("other");
                    }
                    for (String radarType : radarTypes) {
                        radar.merge(radarType, contribution.getValue(), Integer::sum);
                    }
                }
            }
            vo.getContributionGroups().put(yearValue, radar);
        }
        vo.setRadarData(vo.getContributionGroups().getOrDefault(targetYear, emptyRadar()));
        vo.setMaxCount(maxCount);
        vo.setContributionCount(contributionCount(vo.getContributions()));

        // 3. 产品 / 执行
        vo.setProductStat(productStat(targetYear, accounts));
        vo.setExecutionStat(executionStat(targetYear, accounts));

        // 4. 需求 / 任务 / 缺陷 / 用例
        vo.setStoryStat(objectStat(targetYear, accounts, "story"));
        vo.setTaskStat(objectStat(targetYear, accounts, "task"));
        vo.setBugStat(objectStat(targetYear, accounts, "bug"));
        vo.setCaseStat(caseStat(targetYear, accounts));

        // 5. 全量状态分布（只有公司视角给，禅道也是这么写的）+ 概述
        if ("company".equals(vo.getMode())) {
            vo.getStatusStat().put("story", toIntMap(zentaoReportMapper.selectAllTimeStoryStatus()));
            vo.getStatusStat().put("task", toIntMap(zentaoReportMapper.selectAllTimeTaskStatus()));
            vo.getStatusStat().put("bug", toIntMap(zentaoReportMapper.selectAllTimeBugStatus()));
        }
        vo.getOverview().put("story", overview(vo.getStoryStat().getStatusStat()));
        vo.getOverview().put("task", overview(vo.getTaskStat().getStatusStat()));
        vo.getOverview().put("bug", overview(vo.getBugStat().getStatusStat()));
        return vo;
    }

    // ==================== 每日提醒 ====================

    @Override
    public List<ReminderRespVO> getReminderList() {
        // 禅道 config/report.php：快到期的阀值是「今天 + 4 天」；看板卡片另有 expireDays（默认 1）
        String nearDate = LocalDate.now().plusDays(4).toString();
        String cardNearDate = LocalDate.now().plusDays(1).toString();

        Map<String, List<Map<String, Object>>> bugs = groupByUser(zentaoReportMapper.selectReminderBugs(nearDate), "user");
        Map<String, List<Map<String, Object>>> tasks = groupByUser(zentaoReportMapper.selectReminderTasks(nearDate), "user");
        Map<String, List<Map<String, Object>>> todos = groupByUser(zentaoReportMapper.selectReminderTodos(), "user");
        Map<String, List<Map<String, Object>>> testTasks =
                groupByUser(zentaoReportMapper.selectReminderTestTasks(), "user");

        // 看板卡片的 assignedTo 是逗号列表：一条卡片可能提醒多个人
        Map<String, List<Map<String, Object>>> cards = new LinkedHashMap<>();
        for (Map<String, Object> card : zentaoReportMapper.selectReminderCards(cardNearDate)) {
            for (String one : str(card.get("assignedTo")).split(",")) {
                if (StringUtils.hasText(one)) {
                    cards.computeIfAbsent(one.trim(), k -> new ArrayList<>()).add(card);
                }
            }
        }

        Set<String> users = new LinkedHashSet<>();
        users.addAll(bugs.keySet());
        users.addAll(tasks.keySet());
        users.addAll(todos.keySet());
        users.addAll(testTasks.keySet());
        users.addAll(cards.keySet());
        if (users.isEmpty()) {
            return List.of();
        }

        Map<String, String> names = new LinkedHashMap<>();
        for (AdminUserRespDTO user : adminUserApi.getUserListByUsernames(users)) {
            names.put(user.getUsername(), user.getNickname());
        }

        List<ReminderRespVO> list = new ArrayList<>();
        for (String user : users) {
            ReminderRespVO vo = new ReminderRespVO();
            vo.setAccount(user);
            vo.setRealname(names.getOrDefault(user, user));
            vo.setBugs(bugs.getOrDefault(user, List.of()));
            vo.setTasks(tasks.getOrDefault(user, List.of()));
            vo.setTodos(todos.getOrDefault(user, List.of()));
            vo.setTestTasks(testTasks.getOrDefault(user, List.of()));
            vo.setCards(cards.getOrDefault(user, List.of()));
            vo.setTotal(vo.getBugs().size() + vo.getTasks().size() + vo.getTodos().size()
                    + vo.getTestTasks().size() + vo.getCards().size());
            list.add(vo);
        }
        list.sort(Comparator.comparing(ReminderRespVO::getTotal).reversed());
        return list;
    }

    // ==================== 产出统计 ====================

    @Override
    public List<ReportOutputRespVO> getOutput(String year, String account) {
        String targetYear = StringUtils.hasText(year) ? year : defaultYear(availableYears());
        List<String> accounts = resolveAccounts(null, account);

        List<ReportOutputRespVO> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : OUTPUTS.entrySet()) {
            String objectType = entry.getKey();
            Map<String, String> actionMap = entry.getValue();
            List<Map<String, Object>> rows = zentaoReportMapper.selectActionCountByYears(List.of(targetYear), objectType,
                    OBJECT_TABLES.get(objectType), new ArrayList<>(actionMap.keySet()), accounts);

            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String name = actionMap.get(str(row.get("action")));
                if (name != null) {
                    counts.merge(name, intOf(row.get("cnt")), Integer::sum);
                }
            }
            // 与贡献一样，「完成/解决」按事实列补上
            if ("task".equals(objectType)) {
                counts.merge("finish", intOf(nvl(zentaoReportMapper.countFinishedTasks(targetYear, accounts))), Integer::sum);
            }
            if ("bug".equals(objectType)) {
                counts.merge("resolve", intOf(nvl(zentaoReportMapper.countResolvedBugs(targetYear, accounts))), Integer::sum);
            }
            if ("case".equals(objectType)) {
                counts.merge("run", intOf(nvl(zentaoReportMapper.countCaseRuns(targetYear, accounts))), Integer::sum);
            }

            ReportOutputRespVO vo = new ReportOutputRespVO();
            vo.setObjectType(objectType);
            vo.setObjectTypeName(OBJECT_NAMES.getOrDefault(objectType, objectType));
            int total = 0;
            for (Map.Entry<String, String> action : actionMap.entrySet()) {
                String name = action.getValue();
                Integer count = counts.get(name);
                if (count == null || count == 0) {
                    continue;
                }
                total += count;
                vo.getActions().add(new ReportOutputRespVO.ActionItem(
                        name, ACTION_NAMES.getOrDefault(name, name), count));
            }
            // finish/resolve/run 不在 actionMap 里，单独补进明细
            for (String extra : List.of("finish", "resolve", "run")) {
                Integer count = counts.get(extra);
                if (count != null && count > 0) {
                    total += count;
                    vo.getActions().add(new ReportOutputRespVO.ActionItem(
                            extra, ACTION_NAMES.getOrDefault(extra, extra), count));
                }
            }
            vo.setTotal(total);
            if (total > 0) {
                list.add(vo);
            }
        }
        return list;
    }

    // ==================== 项目状态总览 ====================

    @Override
    public Map<String, Integer> getProjectStatusOverview(String account) {
        List<String> accounts = resolveAccounts(null, account);
        return toIntMap(zentaoReportMapper.selectProjectStatusStat(accounts));
    }

    // ==================== 内部：年度数据的几块 ====================

    /** 产品统计：本年被创建/参与的产品，各带「计划数 / 本年创建的需求数（按类型）/ 关闭数」 */
    private List<Map<String, Object>> productStat(String year, List<String> accounts) {
        Map<String, Integer> plans = toIntMapBy(zentaoReportMapper.selectAnnualPlanStat(year, accounts), "product");
        Map<String, Map<String, Object>> createdStories = new LinkedHashMap<>();
        for (Map<String, Object> row : zentaoReportMapper.selectAnnualCreatedStoryStat(year, accounts)) {
            createdStories.put(str(row.get("product")), row);
        }
        Map<String, Integer> closedStories = toIntMapBy(zentaoReportMapper.selectAnnualClosedStoryStat(year, accounts), "product");

        Set<Long> extraIds = new LinkedHashSet<>();
        plans.keySet().forEach(k -> extraIds.add(longOf(k)));
        createdStories.keySet().forEach(k -> extraIds.add(longOf(k)));
        closedStories.keySet().forEach(k -> extraIds.add(longOf(k)));
        extraIds.remove(0L);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : zentaoReportMapper.selectAnnualProducts(year, accounts, new ArrayList<>(extraIds))) {
            Long id = longOf(row.get("id"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("name", row.get("name"));
            item.put("plan", plans.getOrDefault(String.valueOf(id), 0));
            Map<String, Object> created = createdStories.getOrDefault(String.valueOf(id), Map.of());
            item.put("requirement", intOf(created.get("requirement")));
            item.put("story", intOf(created.get("story")));
            item.put("epic", intOf(created.get("epic")));
            item.put("closed", closedStories.getOrDefault(String.valueOf(id), 0));
            result.add(item);
        }
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));
        return result;
    }

    /** 执行统计：本年开始/结束的多迭代执行，各带「完成任务 / 完成需求 / 解决缺陷」数 */
    private List<Map<String, Object>> executionStat(String year, List<String> accounts) {
        Map<String, Integer> finishedTasks = toIntMapBy(zentaoReportMapper.selectAnnualFinishedTaskStat(year, accounts), "execution");
        List<Long> taskExecutionIds = new ArrayList<>();
        for (String key : finishedTasks.keySet()) {
            taskExecutionIds.add(longOf(key));
        }
        taskExecutionIds.removeIf(id -> id == 0L);

        List<Map<String, Object>> executions =
                zentaoReportMapper.selectAnnualExecutions(year, accounts, taskExecutionIds);
        List<Long> executionIds = new ArrayList<>();
        for (Map<String, Object> row : executions) {
            executionIds.add(longOf(row.get("id")));
        }
        Map<String, Integer> finishedStories = executionIds.isEmpty() ? Map.of()
                : toIntMapBy(zentaoReportMapper.selectAnnualFinishedStoryStat(executionIds), "project");
        Map<String, Integer> resolvedBugs = executionIds.isEmpty() ? Map.of()
                : toIntMapBy(zentaoReportMapper.selectAnnualResolvedBugStat(year, executionIds, accounts), "execution");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : executions) {
            String id = String.valueOf(longOf(row.get("id")));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", longOf(row.get("id")));
            item.put("name", row.get("name"));
            item.put("task", finishedTasks.getOrDefault(id, 0));
            item.put("story", finishedStories.getOrDefault(id, 0));
            item.put("bug", resolvedBugs.getOrDefault(id, 0));
            result.add(item);
        }
        return result;
    }

    /** 需求/任务/缺陷的年度统计：状态分布 + 月度动作 */
    private AnnualDataRespVO.ObjectStat objectStat(String year, List<String> accounts, String objectType) {
        AnnualDataRespVO.ObjectStat stat = new AnnualDataRespVO.ObjectStat();
        List<String> objectTypes = STAT_OBJECT_TYPES.get(objectType);

        for (Map<String, Object> row : zentaoReportMapper.selectObjectStatusStat(year, STAT_TABLES.get(objectType),
                objectTypes, List.of("created"), accounts)) {
            stat.getStatusStat().put(str(row.get("status")), intOf(row.get("cnt")));
        }
        stat.setActionStat(monthActionStat(year, objectTypes, MONTH_ACTIONS.get(objectType), accounts));
        return stat;
    }

    /** 用例的年度统计：执行结果分布 + 月度动作（创建/执行/建缺陷） */
    private AnnualDataRespVO.CaseStat caseStat(String year, List<String> accounts) {
        AnnualDataRespVO.CaseStat stat = new AnnualDataRespVO.CaseStat();
        // 创建：按动作数（zt_action 里 objectType='case' 的 created）
        stat.setActionStat(monthActionStat(year, List.of("case"), List.of("created"), accounts));
        // 执行：按 zt_case 的 lastRunResult/lastRunDate（与禅道一致，不是数动作）
        stat.getActionStat().put("run", emptyMonths(year));
        for (Map<String, Object> row : zentaoReportMapper.selectCaseRunStat(year, accounts)) {
            stat.getResultStat().merge(str(row.get("result")), intOf(row.get("cnt")), Integer::sum);
            stat.getActionStat().get("run").merge(str(row.get("month")), intOf(row.get("cnt")), Integer::sum);
        }
        // 建缺陷：执行用例时建的缺陷（zt_bug.case <> 0 的创建动作）
        Map<String, Integer> createBug = emptyMonths(year);
        for (Map<String, Object> row : zentaoReportMapper.selectCaseBugMonthStat(year, accounts)) {
            createBug.merge(str(row.get("month")), intOf(row.get("cnt")), Integer::sum);
        }
        stat.getActionStat().put("createBug", createBug);
        return stat;
    }

    /** 动作 → 月份 → 条数，12 个月都补齐（前端直接画折线，不用自己补零） */
    private Map<String, Map<String, Integer>> monthActionStat(String year, List<String> objectTypes,
                                                             List<String> actions, List<String> accounts) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (String action : actions) {
            result.put(action, emptyMonths(year));
        }
        for (Map<String, Object> row : zentaoReportMapper.selectActionMonthStat(year, objectTypes, actions, accounts)) {
            String action = str(row.get("action"));
            result.computeIfAbsent(action, k -> emptyMonths(year))
                    .merge(str(row.get("month")), intOf(row.get("cnt")), Integer::sum);
        }
        return result;
    }

    // ==================== 内部：工具 ====================

    /** 可选年份：从最早一条动作的年份到今年（禅道 {@code assignAnnualBaseData} 同款） */
    private List<String> availableYears() {
        int currentYear = LocalDate.now().getYear();
        String first = zentaoReportMapper.selectFirstActionYear();
        int firstYear = StringUtils.hasText(first) ? Integer.parseInt(first) : currentYear;
        if (firstYear > currentYear) {
            firstYear = currentYear;
        }
        List<String> years = new ArrayList<>();
        for (int y = firstYear; y <= currentYear; y++) {
            years.add(String.valueOf(y));
        }
        return years;
    }

    /** 年初（1、2 月）默认看去年：禅道 {@code annualData.minMonth = 2} */
    private String defaultYear(List<String> years) {
        int currentYear = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        if (month <= MIN_MONTH && years.contains(String.valueOf(currentYear - 1))) {
            return String.valueOf(currentYear - 1);
        }
        return String.valueOf(currentYear);
    }

    /**
     * 视角 → 账号列表。空列表表示**不加 actor 过滤**（公司视角，所有人都算）。
     * 部门视角要把子部门的人一起算进来（禅道 {@code dept->getDeptUserPairs(0)} 拿的是整棵树）。
     */
    private List<String> resolveAccounts(Long deptId, String account) {
        if (StringUtils.hasText(account)) {
            return List.of(account);
        }
        if (deptId != null && deptId > 0) {
            List<Long> deptIds = deptSubtree(deptId);
            List<AdminUserRespDTO> users = adminUserApi.getUserListByDeptIds(deptIds);
            List<String> accounts = new ArrayList<>();
            for (AdminUserRespDTO user : users) {
                if (StringUtils.hasText(user.getUsername())) {
                    accounts.add(user.getUsername());
                }
            }
            // 部门里没人：返回一个不可能命中的账号，避免退化成「全公司」
            return accounts.isEmpty() ? List.of("__no_such_account__") : accounts;
        }
        return List.of();
    }

    /** 部门子树（含自己） */
    private List<Long> deptSubtree(Long deptId) {
        List<Long> ids = new ArrayList<>();
        ids.add(deptId);
        List<OrgDeptRespVO> flat = flattenDepts();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (OrgDeptRespVO dept : flat) {
                if (dept.getParentId() != null && ids.contains(dept.getParentId()) && !ids.contains(dept.getId())) {
                    ids.add(dept.getId());
                    changed = true;
                }
            }
        }
        return ids;
    }

    private List<OrgDeptRespVO> flattenDepts() {
        List<OrgDeptRespVO> flat = new ArrayList<>();
        for (OrgDeptRespVO root : organizationService.getDeptTree()) {
            collectDept(root, flat);
        }
        return flat;
    }

    private void collectDept(OrgDeptRespVO node, List<OrgDeptRespVO> flat) {
        flat.add(node);
        if (node.getChildren() != null) {
            for (OrgDeptRespVO child : node.getChildren()) {
                collectDept(child, flat);
            }
        }
    }

    private List<ReportOptionRespVO.DeptOption> deptOptions() {
        List<ReportOptionRespVO.DeptOption> options = new ArrayList<>();
        for (OrgDeptRespVO dept : flattenDepts()) {
            options.add(new ReportOptionRespVO.DeptOption(dept.getId(), dept.getName()));
        }
        return options;
    }

    private List<ReportOptionRespVO.UserOption> listUsers(Long deptId) {
        List<AdminUserRespDTO> users = deptId == null || deptId <= 0
                ? adminUserApi.getUserListByDeptIds(flattenDepts().stream().map(OrgDeptRespVO::getId).toList())
                : adminUserApi.getUserListByDeptIds(deptSubtree(deptId));
        List<ReportOptionRespVO.UserOption> options = new ArrayList<>();
        for (AdminUserRespDTO user : users) {
            if (StringUtils.hasText(user.getUsername())) {
                options.add(new ReportOptionRespVO.UserOption(user.getUsername(), user.getNickname(),
                        user.getDeptId()));
            }
        }
        options.sort(Comparator.comparing(ReportOptionRespVO.UserOption::getAccount));
        return options;
    }

    private String resolveWho(Long deptId, String account) {
        if (StringUtils.hasText(account)) {
            List<AdminUserRespDTO> users = adminUserApi.getUserListByUsernames(List.of(account));
            return users.isEmpty() ? account : users.get(0).getNickname();
        }
        if (deptId != null && deptId > 0) {
            for (OrgDeptRespVO dept : flattenDepts()) {
                if (deptId.equals(dept.getId())) {
                    return dept.getName();
                }
            }
        }
        return "";
    }

    private int contributionCount(Map<String, Map<String, Integer>> contributions) {
        int count = 0;
        for (String objectType : CONTRIBUTION_COUNT_TYPES) {
            Map<String, Integer> items = contributions.get(objectType);
            if (items != null) {
                count += items.values().stream().mapToInt(Integer::intValue).sum();
            }
        }
        return count;
    }

    private String overview(Map<String, Integer> statusStat) {
        int all = statusStat.values().stream().mapToInt(Integer::intValue).sum();
        int undone = 0;
        for (Map.Entry<String, Integer> entry : statusStat.entrySet()) {
            String status = entry.getKey();
            boolean finished = "closed".equals(status) || "done".equals(status)
                    || "cancel".equals(status) || "resolved".equals(status);
            if (!finished) {
                undone += entry.getValue();
            }
        }
        return String.format("共 %d 条，未完成 %d 条", all, undone);
    }

    /** 只在条数 > 0 时写入贡献，避免空年份留下空壳条目 */
    private void mergePositive(Map<String, Map<String, Integer>> target, String objectType,
                               String contributionName, int count) {
        if (count > 0) {
            target.computeIfAbsent(objectType, k -> new LinkedHashMap<>()).merge(contributionName, count, Integer::sum);
        }
    }

    private Map<String, Integer> emptyRadar() {
        Map<String, Integer> radar = new LinkedHashMap<>();
        for (String type : RADAR_TYPES) {
            radar.put(type, 0);
        }
        return radar;
    }

    private Map<String, Integer> emptyMonths(String year) {
        Map<String, Integer> result = new TreeMap<>();
        for (String month : months(year)) {
            result.put(month, 0);
        }
        return result;
    }

    private List<String> months(String year) {
        List<String> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            months.add(year + "-" + String.format("%02d", i));
        }
        return months;
    }

    private Map<String, List<Map<String, Object>>> groupByUser(List<Map<String, Object>> rows, String key) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String user = str(row.get(key));
            if (StringUtils.hasText(user)) {
                grouped.computeIfAbsent(user, k -> new ArrayList<>()).add(row);
            }
        }
        return grouped;
    }

    /** 按状态分组的结果 → {状态: 条数} */
    private Map<String, Integer> toIntMap(List<Map<String, Object>> rows) {
        return toIntMapBy(rows, "status");
    }

    /** 按某一列分组的结果 → {该列的值: 条数}（产品看 product、执行看 execution） */
    private Map<String, Integer> toIntMapBy(List<Map<String, Object>> rows, String column) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = str(row.get(column));
            if (key != null) {
                map.merge(key, intOf(row.get("cnt")), Integer::sum);
            }
        }
        return map;
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intOf(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static long longOf(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal decimalOf(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

}

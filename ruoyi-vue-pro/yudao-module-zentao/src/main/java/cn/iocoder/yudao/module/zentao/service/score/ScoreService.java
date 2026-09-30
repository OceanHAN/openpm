package cn.iocoder.yudao.module.zentao.service.score;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.score.vo.ScorePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.score.vo.ScoreRuleRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.score.vo.ScoreTotalRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.score.ScoreDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.score.ScoreMapper;
import cn.iocoder.yudao.module.zentao.service.bug.BugService;
import cn.iocoder.yudao.module.zentao.service.execution.ExecutionService;
import cn.iocoder.yudao.module.zentao.service.story.StoryService;
import cn.iocoder.yudao.module.zentao.service.task.TaskService;
import cn.iocoder.yudao.module.zentao.service.team.TeamService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 积分（禅道 {@code module/score}）。
 *
 * <h3>它是「规则驱动的计分器」，不是一个页面</h3>
 * 禅道这 374 行 model 的价值全在 {@link ScoreRules} 那张规则表 + {@code create()} 里按模块分支的
 * 特例上：同一句 {@code score->create('task','finish',$taskID)} 会因为任务优先级、预计工时、
 * 有没有子任务而算出不同的分；{@code ('bug','confirm',$bugID)} 的分是给**提单人**而不是确认人的。
 * 这些「看起来是业务细节、其实是规则」的地方，就是这个模块要照搬的东西。
 *
 * <h3>三处照抄的特例（都在 create 里）</h3>
 * <ol>
 *   <li><b>需求关闭只给创建者加分</b>：禅道这里有个短路（见下面 case "story" 的注释），
 *       实际行为是「创建者 +2 分、关闭人 0 分」，config 里 story.close 的那 1 分是死规则。</li>
 *   <li><b>缺陷确认的分给提单人</b>：{@code bug.confirm} 的 account 被换成 {@code bug.openedBy}
 *       （谁提的谁得分，鼓励提有效缺陷），并按严重程度加成；{@code bug.resolve} 是解决人得分 + 严重程度加成。</li>
 *   <li><b>执行关闭给项目经理 + 全体执行成员计分</b>：PM 基础 20 分、成员 5 分，
 *       并且 {@code end > 今天}（按期或提前）再各加 10 / 5 分；PM 不重复算成员那一份。</li>
 * </ol>
 *
 * <h3>两处有意偏离</h3>
 * <ol>
 *   <li><b>{@code task.finish} 的「有子任务不给分」暂时是空操作</b>：这条前置查的是 {@code zt_task.parent}，
 *       而本项目的任务表没有迁 {@code parent} 列（父子任务整体未迁，见 README 的「尚未覆盖」），
 *       所以这里只保留了「取任务 → 加成 → 计分」，前置判断留了注释与 TODO。</li>
 *   <li><b>总分不冗余在用户表上</b>：禅道 saveScore 会 {@code UPDATE zt_user SET score = score + N}；
 *       本项目没迁 {@code zt_user}，所以总分 = {@code SUM(zt_score.score)}，before/after 落库时按当前总分算快照。</li>
 *   <li><b>开关换了位置</b>：禅道的 {@code system.common.global.scoreStatus} 存在设置表里，
 *       本项目用配置项 {@code zentao.score.enabled}（默认开）。</li>
 * </ol>
 */
@Service
@Slf4j
public class ScoreService {

    /** 时间窗口径：禅道的 hour 分支实际是按「当天」数条数（见 ScoreMapper#countByDay） */
    @Resource
    private ScoreMapper scoreMapper;

    @Resource
    private TaskService taskService;

    @Resource
    private BugService bugService;

    @Resource
    private StoryService storyService;

    @Resource
    private ExecutionService executionService;

    @Resource
    private TeamService teamService;

    @Resource
    private AdminUserApi adminUserApi;

    /** 禅道 system.common.global.scoreStatus 的等价物 */
    @Value("${zentao.score.enabled:true}")
    private boolean enabled;

    // ==================== 查 ====================

    public PageResult<ScoreDO> getPage(ScorePageReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getAccount())) {
            reqVO.setAccount(currentAccount());
        }
        return scoreMapper.selectPage(reqVO);
    }

    public ScoreTotalRespVO getTotal(String account) {
        String target = StrUtil.isBlank(account) ? currentAccount() : account;
        ScoreTotalRespVO resp = new ScoreTotalRespVO();
        resp.setAccount(target);
        resp.setEnabled(enabled);
        if (StrUtil.isBlank(target)) {
            resp.setTotal(0);
            resp.setYesterday(0);
            resp.setCount(0);
            return resp;
        }
        Integer total = scoreMapper.selectTotalByAccount(target);
        Integer yesterday = scoreMapper.sumBetween(target,
                LocalDate.now().minusDays(1).atStartOfDay(),
                LocalDate.now().minusDays(1).atTime(LocalTime.MAX));
        resp.setTotal(total == null ? 0 : total);
        resp.setYesterday(yesterday == null ? 0 : yesterday);
        resp.setCount(scoreMapper.selectListByAccount(target).size());
        /* 禅道 getNotice：昨天有积分才提示 */
        if (resp.getYesterday() != 0) {
            resp.setTip("昨天增加了积分：" + resp.getYesterday() + "，总积分：" + resp.getTotal());
        }
        return resp;
    }

    /** 「积分规则」页面：把规则表摊平，并带上扩展加成的说明（禅道 {@code buildRules}） */
    public List<ScoreRuleRespVO> getRules() {
        List<ScoreRuleRespVO> list = new ArrayList<>();
        for (ScoreRules.Rule rule : ScoreRules.all()) {
            ScoreRuleRespVO vo = new ScoreRuleRespVO();
            vo.setModule(rule.module());
            vo.setModuleName(ScoreRules.moduleName(rule.module()));
            vo.setMethod(rule.method());
            vo.setMethodName(ScoreRules.methodName(rule.module(), rule.method()));
            vo.setTimes(rule.times() == 0 ? "不限制" : String.valueOf(rule.times()));
            vo.setHour(rule.hour() == 0 ? "不限制" : String.valueOf(rule.hour()));
            vo.setScore(rule.score());
            vo.setDesc(ScoreRules.extendedDesc(rule.module(), rule.method()));
            list.add(vo);
        }
        return list;
    }

    // ==================== 计分 ====================

    /**
     * 接口入口：规则不存在 / 功能关闭 / 缺 param 都**明确报错**（联调时好排查）。
     *
     * <p>禅道内部调用对未知规则是静默返回 true；那种「安静地什么都不做」的行为由
     * {@link #createQuietly} 保留给模块内部调用（见 entry 的登录计分）。
     */
    public ScoreDO create(String module, String method, Long param, String account, String time) {
        ScoreRules.Rule rule = ScoreRules.find(module, method);
        if (rule == null) {
            throw exception(SCORE_RULE_NOT_FOUND, module, method);
        }
        if (!enabled) {
            throw exception(SCORE_DISABLED);
        }
        if (StrUtil.isBlank(account) && StrUtil.isBlank(currentAccount())) {
            throw exception(SCORE_ACCOUNT_REQUIRED);
        }
        return doCreate(rule, module, method, param, account, parseTime(time));
    }

    /**
     * 模块内部调用：任何「不该计分」的情况都静默跳过（禅道 create 的语义）。
     *
     * @return 真正写进去的那条流水；跳过时返回 null
     */
    public ScoreDO createQuietly(String module, String method, Long param, String account, LocalDateTime time) {
        ScoreRules.Rule rule = ScoreRules.find(module, method);
        if (rule == null || !enabled) {
            return null;
        }
        try {
            return doCreate(rule, module, method, param, account, time == null ? LocalDateTime.now() : time);
        } catch (RuntimeException e) {
            /* 计分失败不该影响主流程（禅道也是这个态度：dao 出错只记录，不阻断业务） */
            log.warn("[createQuietly] 计分失败 module={} method={} param={} account={}", module, method, param, account, e.toString());
            return null;
        }
    }

    private ScoreDO doCreate(ScoreRules.Rule rule, String module, String method, Long param,
                             String account, LocalDateTime time) {
        String target = StrUtil.isBlank(account) ? currentAccount() : account;
        String desc = ScoreRules.moduleName(module) + (param != null ? "ID:" + param : "");
        int score = rule.score();
        ScoreDO executed = null;

        switch (module) {
            case "user" -> {
                if ("login".equals(method)) {
                    desc = ScoreRules.methodName(module, method);
                } else if ("changePassword".equals(method)) {
                    score += ScoreRules.strengthBonus(param == null ? null : param.intValue());
                    desc = ScoreRules.methodName(module, method);
                }
            }
            case "story" -> {
                if ("close".equals(method)) {
                    /* 特例①：需求关闭 —— **只给创建者** 2 分，关闭人那 1 分拿不到。
                       这是禅道里一个「短路」造成的实际行为（不是我们理解的「关闭人 1 分 + 创建者 2 分」）：
                         $object = true;
                         if(!empty($openedBy)) { ...; $object = saveScore($openedBy, 新规则2分, ...); }
                         return $object === '' ? saveScore($user, $rule, ...) : $object;   ← 这里直接返回了
                       也就是说 config 里 story.close 的那 1 分是**死规则**。本实现照抄这个行为，
                       并把这条写进 README（迁移时最容易「顺手修好」而和禅道对不上的地方）。 */
                    StoryDO story = safeStory(param);
                    if (story == null || StrUtil.isBlank(story.getOpenedBy())) {
                        return null;
                    }
                    ScoreRules.Rule creatorRule = new ScoreRules.Rule(module, method, rule.times(), rule.hour(),
                            ScoreRules.STORY_CLOSE_CREATOR_SCORE);
                    return save(story.getOpenedBy(), creatorRule, module, method, desc, time);
                }
            }
            case "task" -> {
                if ("finish".equals(method)) {
                    TaskDO task = param == null ? null : taskService.getTask(param);
                    if (task == null) {
                        return null;
                    }
                    /* TODO 禅道这里还有一条前置：有子任务的任务不给分（查 zt_task.parent）。
                       本项目的任务表没有 parent 列（父子任务未迁），所以这条暂时是空操作。 */
                    desc = ScoreRules.methodName(module, method) + "ID:" + param;
                    score += ScoreRules.priBonus(task.getPri());
                    double estimate = task.getEstimate() == null ? 0 : task.getEstimate().doubleValue();
                    double consumed = task.getConsumed() == null ? 0 : task.getConsumed().doubleValue();
                    if (estimate > 0) {
                        /* 禅道原式：round(consumed / 10 * estimate / consumed)，consumed > 0 时等价于 round(estimate / 10) */
                        score += consumed > 0 ? (int) Math.round(estimate / 10.0) : 0;
                    }
                }
            }
            case "bug" -> {
                if ("confirm".equals(method)) {
                    /* 特例②：确认缺陷的分给**提单人** */
                    BugDO bug = param == null ? null : bugService.getBug(param);
                    if (bug == null) {
                        return null;
                    }
                    target = StrUtil.blankToDefault(bug.getOpenedBy(), target);
                    score += ScoreRules.severityBonus(bug.getSeverity());
                    desc = ScoreRules.methodName(module, method) + "ID:" + param;
                } else if ("resolve".equals(method) && param != null) {
                    BugDO bug = bugService.getBug(param);
                    if (bug != null) {
                        score += ScoreRules.severityBonus(bug.getSeverity());
                    }
                }
            }
            case "testtask" -> {
                if ("runCase".equals(method)) {
                    desc = ScoreRules.methodName(module, method) + "ID:" + param;
                }
            }
            case "execution" -> {
                if ("close".equals(method)) {
                    /* 特例③：执行关闭 —— PM 与全体执行成员都计分，按期或提前再加分 */
                    ProjectDO execution = param == null ? null : executionService.getExecution(param);
                    if (execution == null) {
                        return null;
                    }
                    /* 执行关闭会写多条（PM + 每个成员），返回最后写的那条，与禅道「返回最后一个对象」一致 */
                    executed = saveExecutionClose(execution, target, module, method, time);
                }
            }
            case "ajax", "search" -> desc = ScoreRules.methodName(module, method);
            default -> {
                /* 其余模块用默认描述（模块中文名 + ID） */
            }
        }

        if (executed != null) {
            return executed;
        }
        return save(target, new ScoreRules.Rule(module, method, rule.times(), rule.hour(), score), module, method, desc, time);
    }

    /** 执行关闭：PM / 成员分别计分，返回**最后写进去的那条**（禅道返回的也是最后一个对象） */
    private ScoreDO saveExecutionClose(ProjectDO execution, String currentUser, String module, String method, LocalDateTime time) {
        boolean onTime = execution.getEnd() != null && execution.getEnd().isAfter(LocalDate.now());
        String desc = ScoreRules.methodName(module, method) + "," + ScoreRules.moduleName(module) + "ID:" + execution.getId();
        ScoreDO last = null;
        if (StrUtil.isNotBlank(execution.getPM())) {
            int managerScore = ScoreRules.EXECUTION_MANAGER_CLOSE
                    + (onTime ? ScoreRules.EXECUTION_MANAGER_ON_TIME : 0);
            last = save(execution.getPM(), new ScoreRules.Rule(module, method, 0, 0, managerScore), module, method, desc, time);
        }
        int memberScore = ScoreRules.EXECUTION_MEMBER_CLOSE + (onTime ? ScoreRules.EXECUTION_MEMBER_ON_TIME : 0);
        for (String account : teamService.getMemberAccounts(execution.getId(), "execution")) {
            if (account.equals(execution.getPM())) {
                continue;
            }
            ScoreDO saved = save(account, new ScoreRules.Rule(module, method, 0, 0, memberScore), module, method, desc, time);
            if (saved != null) {
                last = saved;
            }
        }
        return last;
    }

    /**
     * 落一条流水（禅道 {@code saveScore}）：0 分不落；命中「次数/时间窗」上限就跳过；然后写 before/after 快照。
     */
    private ScoreDO save(String account, ScoreRules.Rule rule, String module, String method, String desc, LocalDateTime time) {
        if (rule.score() == 0) {
            return null;
        }
        if (!rule.unlimited()) {
            int count;
            if (rule.hour() == 0) {
                count = scoreMapper.countAll(account, module, method);
            } else {
                LocalDateTime begin = time.toLocalDate().atStartOfDay();
                count = scoreMapper.countByDay(account, module, method, begin, time.toLocalDate().atTime(LocalTime.MAX));
            }
            if (count >= rule.times()) {
                return null;
            }
        }
        AdminUserRespDTO user = findUser(account);
        if (user == null) {
            /* 账号不存在 → 静默跳过（禅道 saveScore 里 getById 取不到就 return false）。
               照抄这一条很关键：zt_team 里可能挂着已经不是系统用户的历史账号，
               遇到一个就抛异常会让「执行关闭给全员计分」整条链路失败。 */
            log.warn("[save] 账号不存在，跳过计分 account={} module={} method={}", account, module, method);
            return null;
        }
        Integer before = scoreMapper.selectTotalByAccount(account);
        int beforeValue = before == null ? 0 : before;

        ScoreDO score = new ScoreDO();
        score.setAccount(account);
        score.setModule(module);
        score.setMethod(method);
        score.setDesc(StrUtil.maxLength(desc, 250));
        score.setBefore(beforeValue);
        score.setScore(rule.score());
        score.setAfter(beforeValue + rule.score());
        score.setTime(time);
        scoreMapper.insert(score);
        return score;
    }

    private StoryDO safeStory(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return storyService.getStory(id);
        } catch (RuntimeException e) {
            return null;  /* 需求可能已被删除：计分不该因此失败 */
        }
    }

    private AdminUserRespDTO findUser(String account) {
        if (StrUtil.isBlank(account)) {
            return null;
        }
        List<AdminUserRespDTO> users = adminUserApi.getUserListByUsernames(List.of(account));
        return users.isEmpty() ? null : users.get(0);
    }

    private LocalDateTime parseTime(String time) {
        if (StrUtil.isBlank(time)) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(time.replace(' ', 'T'));
    }

    /** 与 CaseServiceImpl / StoryServiceImpl 同一口径：登录用户 ID → 账号 */
    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StrUtil.isNotBlank(user.getUsername()) ? user.getUsername() : "";
    }

}

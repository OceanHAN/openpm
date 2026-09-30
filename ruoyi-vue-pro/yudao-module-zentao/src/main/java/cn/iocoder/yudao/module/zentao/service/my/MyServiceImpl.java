package cn.iocoder.yudao.module.zentao.service.my;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.my.vo.MyOverviewRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.my.vo.MyTeamRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.action.ActionDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.effort.EffortDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.action.ActionMapper;
import cn.iocoder.yudao.module.zentao.enums.bug.BugStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.task.TaskStatusEnum;
import cn.iocoder.yudao.module.zentao.service.bug.BugService;
import cn.iocoder.yudao.module.zentao.service.effort.EffortService;
import cn.iocoder.yudao.module.zentao.service.story.StoryService;
import cn.iocoder.yudao.module.zentao.service.task.TaskService;
import cn.iocoder.yudao.module.zentao.service.todo.TodoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 我的地盘 Service 实现
 *
 * <h3>实现思路：不重新写查询，而是「带上我的账号」去调各模块已有的分页接口</h3>
 * 「我的任务」就是 {@code task/page?assignedTo=我}，「我的工时」就是 {@code effort/page?account=我}……
 * 所以这里只做两件事：把 account 塞进各模块的查询条件、把结果转成各自的 RespVO。
 * 好处是各模块的过滤逻辑（含模块子树展开、多值字段匹配等）不会出现第二份实现。
 */
@Slf4j
@Service
public class MyServiceImpl implements MyService {

    @Resource
    private TaskService taskService;

    @Resource
    private BugService bugService;

    @Resource
    private StoryService storyService;

    @Resource
    private EffortService effortService;

    @Resource
    private TodoService todoService;

    @Resource
    private cn.iocoder.yudao.module.zentao.service.project.ProjectService projectService;

    @Resource
    private cn.iocoder.yudao.module.zentao.service.execution.ExecutionService executionService;

    @Resource
    private cn.iocoder.yudao.module.zentao.service.team.TeamService teamService;

    @Resource
    private cn.iocoder.yudao.module.zentao.service.testtask.TestTaskService testTaskService;

    @Resource
    private cn.iocoder.yudao.module.zentao.service.testcase.CaseService caseService;

    @Resource
    private cn.iocoder.yudao.module.zentao.service.doc.DocService docService;

    @Resource
    private cn.iocoder.yudao.module.zentao.dal.mysql.team.TeamMapper teamMapper;

    @Resource
    private cn.iocoder.yudao.module.zentao.dal.mysql.todo.TodoMapper todoMapper;

    @Resource
    private cn.iocoder.yudao.module.zentao.dal.mysql.task.TaskMapper taskMapper;

    @Resource
    private cn.iocoder.yudao.module.zentao.dal.mysql.testtask.TestTaskMapper testTaskMapper;

    @Resource
    private cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper projectMapper;

    @Resource
    private ActionMapper actionMapper;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public MyOverviewRespVO getOverview(String account) {
        String owner = resolveAccount(account);
        MyOverviewRespVO vo = new MyOverviewRespVO();
        vo.setAccount(owner);

        // 待办：今天的 / 未完成的 / 已过期的
        vo.setTodoToday(todoService.countToday(owner));
        vo.setTodoUndone(todoService.countUndone(owner));
        vo.setTodoOverdue(todoService.countOverdue(owner));

        // 指派给我的任务 / 缺陷 / 需求（与禅道 getOverview 的口径一致）
        TaskPageReqVO taskReq = new TaskPageReqVO();
        taskReq.setAssignedTo(owner);
        vo.setTaskTotal(taskService.getTaskPage(taskReq).getTotal());
        TaskPageReqVO doingReq = new TaskPageReqVO();
        doingReq.setAssignedTo(owner);
        doingReq.setStatus(TaskStatusEnum.DOING.getStatus());
        vo.setTaskDoing(taskService.getTaskPage(doingReq).getTotal());

        BugPageReqVO bugReq = new BugPageReqVO();
        bugReq.setAssignedTo(owner);
        bugReq.setStatus(BugStatusEnum.ACTIVE.getStatus());
        vo.setBugActive(bugService.getBugPage(bugReq).getTotal());

        StoryPageReqVO storyReq = new StoryPageReqVO();
        storyReq.setAssignedTo(owner);
        storyReq.setStatus(StoryStatusEnum.ACTIVE.getStatus());
        vo.setStoryActive(storyService.getStoryPage(storyReq).getTotal());

        // 我本月的消耗工时
        LocalDate today = LocalDate.now();
        EffortPageReqVO effortReq = new EffortPageReqVO();
        effortReq.setAccount(owner);
        effortReq.setDate(new LocalDate[]{today.withDayOfMonth(1), today});
        BigDecimal total = BigDecimal.ZERO;
        for (var item : effortService.getEffortSummary(effortReq)) {
            total = total.add(item.getConsumed() == null ? BigDecimal.ZERO : item.getConsumed());
        }
        vo.setEffortThisMonth(total);
        return vo;
    }

    @Override
    public PageResult<TaskRespVO> getMyTaskPage(TaskPageReqVO reqVO, String account) {
        reqVO.setAssignedTo(resolveAccount(account));
        PageResult<TaskDO> page = taskService.getTaskPage(reqVO);
        PageResult<TaskRespVO> result = BeanUtils.toBean(page, TaskRespVO.class);
        taskService.fillStoryInfo(result.getList());
        return result;
    }

    @Override
    public PageResult<BugRespVO> getMyBugPage(BugPageReqVO reqVO, String account) {
        reqVO.setAssignedTo(resolveAccount(account));
        PageResult<BugDO> page = bugService.getBugPage(reqVO);
        return BeanUtils.toBean(page, BugRespVO.class);
    }

    @Override
    public PageResult<StoryRespVO> getMyStoryPage(StoryPageReqVO reqVO, String account) {
        reqVO.setAssignedTo(resolveAccount(account));
        PageResult<StoryDO> page = storyService.getStoryPage(reqVO);
        PageResult<StoryRespVO> result = BeanUtils.toBean(page, StoryRespVO.class);
        storyService.fillParentInfo(result.getList());
        return result;
    }

    @Override
    public PageResult<EffortRespVO> getMyEffortPage(EffortPageReqVO reqVO, String account) {
        reqVO.setAccount(resolveAccount(account));
        PageResult<EffortDO> page = effortService.getEffortPage(reqVO);
        return BeanUtils.toBean(page, EffortRespVO.class);
    }

    @Override
    public List<TodoRespVO> getMyTodoList(TodoPageReqVO reqVO, String account) {
        List<TodoRespVO> list = new ArrayList<>();
        for (var todo : todoService.getMyTodoList(reqVO, resolveAccount(account))) {
            TodoRespVO vo = BeanUtils.toBean(todo, TodoRespVO.class);
            if (vo != null) {
                vo.setOverdue(todo.getDate() != null && todo.getDate().isBefore(LocalDate.now())
                        && !"done".equals(todo.getStatus()) && !"closed".equals(todo.getStatus()));
            }
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<ActionDO> getMyActionList(String account, int limit) {
        return actionMapper.selectListByActor(resolveAccount(account), limit <= 0 ? 20 : limit);
    }

    /** 不传账号时取当前登录用户 */
    private String resolveAccount(String account) {
        if (StringUtils.hasText(account)) {
            return account;
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }


    // ================================================================
    // 我的地盘：第二组（我参与的对象）
    // ================================================================

    @Override
    public PageResult<ProjectRespVO> getMyProjectPage(ProjectPageReqVO reqVO, String account) {
        reqVO.setMember(resolveAccount(account));
        return BeanUtils.toBean(projectService.getProjectPage(reqVO), ProjectRespVO.class);
    }

    @Override
    public PageResult<ProjectRespVO> getMyExecutionPage(ExecutionPageReqVO reqVO, String account) {
        reqVO.setMember(resolveAccount(account));
        return BeanUtils.toBean(executionService.getExecutionPage(reqVO), ProjectRespVO.class);
    }

    @Override
    public List<MyTeamRespVO> getMyTeamList(String account) {
        String me = resolveAccount(account);
        List<cn.iocoder.yudao.module.zentao.dal.dataobject.team.TeamDO> members = teamMapper.selectListByAccount(me);
        // 姓名从 system 模块批量取，避免每条成员关系都打一次 RPC
        List<String> accounts = new ArrayList<>();
        for (cn.iocoder.yudao.module.zentao.dal.dataobject.team.TeamDO member : members) {
            accounts.add(member.getAccount());
        }
        Map<String, String> nameMap = new java.util.LinkedHashMap<>();
        for (AdminUserRespDTO user : adminUserApi.getUserListByUsernames(accounts)) {
            nameMap.put(user.getUsername(), user.getNickname());
        }
        List<MyTeamRespVO> list = new ArrayList<>();
        for (cn.iocoder.yudao.module.zentao.dal.dataobject.team.TeamDO member : members) {
            MyTeamRespVO vo = BeanUtils.toBean(member, MyTeamRespVO.class);
            if (vo == null) {
                continue;
            }
            // 团队行只存了 root（项目/执行编号），这里补上名字与状态，前端不用再查一遍
            cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO root =
                    member.getRoot() == null ? null : projectMapper.selectById(member.getRoot());
            if (root != null) {
                vo.setRootName(root.getName());
                vo.setRootStatus(root.getStatus());
            }
            vo.setRealname(nameMap.getOrDefault(member.getAccount(), member.getAccount()));
            // 可用工时 = 天数 × 每天小时数（与 team 模块 getTotalHours 同一口径）
            BigDecimal days = BigDecimal.valueOf(member.getDays() == null ? 0 : member.getDays());
            BigDecimal hours = member.getHours() == null ? BigDecimal.ZERO : member.getHours();
            vo.setTotalHours(days.multiply(hours));
            list.add(vo);
        }
        return list;
    }

    @Override
    public PageResult<TestTaskRespVO> getMyTestTaskPage(TestTaskPageReqVO reqVO, String account) {
        reqVO.setMember(resolveAccount(account));
        return BeanUtils.toBean(testTaskService.getTestTaskPage(reqVO), TestTaskRespVO.class);
    }

    @Override
    public PageResult<CaseRespVO> getMyCasePage(CasePageReqVO reqVO, String account) {
        String me = resolveAccount(account);
        // 口径（与禅道 my/testcase 对齐）：我创建的「或」我评审过的。
        // 两者是 OR 关系，但 CaseMapper 里 openedBy 与 reviewedBy 是 AND —— 所以这里分成两次查再合并，
        // 用 id 去重（同一条用例既是我创建又是我评审时只出现一次）。
        CasePageReqVO openedReq = copyCaseReq(reqVO);
        openedReq.setOpenedBy(me);
        PageResult<CaseRespVO> opened = caseService.getCasePage(openedReq);

        CasePageReqVO reviewedReq = copyCaseReq(reqVO);
        reviewedReq.setReviewedBy(me);
        PageResult<CaseRespVO> reviewed = caseService.getCasePage(reviewedReq);

        java.util.LinkedHashMap<Long, CaseRespVO> merged = new java.util.LinkedHashMap<>();
        for (CaseRespVO vo : opened.getList()) {
            merged.put(vo.getId(), vo);
        }
        for (CaseRespVO vo : reviewed.getList()) {
            merged.putIfAbsent(vo.getId(), vo);
        }
        List<CaseRespVO> list = new ArrayList<>(merged.values());
        return new PageResult<>(list, (long) list.size());
    }

    /** 复制一份分页参数（两次查询要各带一个条件，不能共用一个对象） */
    private CasePageReqVO copyCaseReq(CasePageReqVO source) {
        CasePageReqVO copy = new CasePageReqVO();
        copy.setPageNo(source.getPageNo());
        copy.setPageSize(source.getPageSize());
        copy.setProduct(source.getProduct());
        copy.setLib(source.getLib());
        copy.setBranch(source.getBranch());
        copy.setModule(source.getModule());
        copy.setStory(source.getStory());
        copy.setTitle(source.getTitle());
        copy.setKeywords(source.getKeywords());
        copy.setType(source.getType());
        copy.setStage(source.getStage());
        copy.setStatus(source.getStatus());
        copy.setPri(source.getPri());
        copy.setNeedConfirm(source.getNeedConfirm());
        return copy;
    }

    @Override
    public PageResult<DocRespVO> getMyDocPage(DocPageReqVO reqVO, String account) {
        reqVO.setMember(resolveAccount(account));
        return BeanUtils.toBean(docService.getMyDocPage(reqVO), DocRespVO.class);
    }

    @Override
    public List<Map<String, Object>> getMyCalendar(String month, String account) {
        String me = resolveAccount(account);
        java.time.YearMonth ym = StringUtils.hasText(month)
                ? java.time.YearMonth.parse(month) : java.time.YearMonth.now();
        LocalDate begin = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        // 三类数据各自按「日期」落到某一天上：待办看 date，任务看预计开始/截止，测试单看起止
        Map<String, List<Map<String, Object>>> byDate = new java.util.TreeMap<>();
        for (cn.iocoder.yudao.module.zentao.dal.dataobject.todo.TodoDO todo : todoMapper.selectListByAccount(me, null)) {
            LocalDate day = todo.getDate();
            addCalendarItem(byDate, begin, end, day, "todo", todo.getId(), todo.getName(), todo.getStatus(),
                    todo.getPri() == null ? null : "P" + todo.getPri());
        }
        for (cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO task : taskMapper.selectListByAssignedTo(me)) {
            addCalendarItem(byDate, begin, end, task.getEstStarted(), "task", task.getId(), task.getName(),
                    task.getStatus(), task.getDeadline() == null ? null : "截止 " + task.getDeadline());
        }
        for (cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestTaskDO testTask
                : testTaskMapper.selectListByOwner(me)) {
            addCalendarItem(byDate, begin, end, testTask.getBegin(), "testtask", testTask.getId(),
                    testTask.getName(), testTask.getStatus(),
                    testTask.getEnd() == null ? null : "截止 " + testTask.getEnd());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        byDate.forEach((date, items) -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("date", date);
            row.put("items", items);
            row.put("count", items.size());
            result.add(row);
        });
        return result;
    }

    /** 把一条数据塞进它所属的那一天（不在本月就忽略） */
    private void addCalendarItem(Map<String, List<Map<String, Object>>> byDate, LocalDate begin, LocalDate end,
                                 LocalDate day, String type, Long id, String name, String status, String extra) {
        if (day == null || day.isBefore(begin) || day.isAfter(end)) {
            return;
        }
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("type", type);
        item.put("id", id);
        item.put("name", name);
        item.put("status", status);
        item.put("extra", extra);
        byDate.computeIfAbsent(day.toString(), key -> new ArrayList<>()).add(item);
    }
}

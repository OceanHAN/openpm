package cn.iocoder.yudao.module.zentao.controller.admin.my;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTimelineRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.my.vo.MyOverviewRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.my.vo.MyTeamRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoRespVO;
import cn.iocoder.yudao.module.zentao.service.my.MyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 我的地盘 Controller
 *
 * 对应禅道 {@code module/my/}。它是**查询层**：把「指派给我的 / 我登记的」聚合起来，
 * 所以每个接口都是「带上我的账号去调各模块已有的查询」。
 */
@Tag(name = "管理后台 - 我的地盘")
@RestController
@RequestMapping("/zentao/my")
@Validated
public class MyController {

    @Resource
    private MyService myService;

    @GetMapping("/overview")
    @Operation(summary = "我的地盘概览",
            description = "待办（今天/未完成/已过期）+ 指派给我的任务/缺陷/需求 + 我本月消耗工时")
    @Parameter(name = "account", description = "账号，不传取当前登录用户", example = "admin")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<MyOverviewRespVO> getOverview(@RequestParam(value = "account", required = false) String account) {
        return success(myService.getOverview(account));
    }

    @GetMapping("/task-page")
    @Operation(summary = "指派给我的任务")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<PageResult<TaskRespVO>> getMyTaskPage(@Valid TaskPageReqVO reqVO) {
        return success(myService.getMyTaskPage(reqVO, null));
    }

    @GetMapping("/bug-page")
    @Operation(summary = "指派给我的缺陷")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<PageResult<BugRespVO>> getMyBugPage(@Valid BugPageReqVO reqVO) {
        return success(myService.getMyBugPage(reqVO, null));
    }

    @GetMapping("/story-page")
    @Operation(summary = "指派给我的需求")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<PageResult<StoryRespVO>> getMyStoryPage(@Valid StoryPageReqVO reqVO) {
        return success(myService.getMyStoryPage(reqVO, null));
    }

    @GetMapping("/effort-page")
    @Operation(summary = "我登记的工时")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<PageResult<EffortRespVO>> getMyEffortPage(@Valid EffortPageReqVO reqVO) {
        return success(myService.getMyEffortPage(reqVO, null));
    }

    @GetMapping("/todo-list")
    @Operation(summary = "我的待办",
            description = "assignedTo = 我 或 finishedBy = 我 或 closedBy = 我；browseType 支持 today/tomorrow/thisweek/before/future/all")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<List<TodoRespVO>> getMyTodoList(@Valid TodoPageReqVO reqVO) {
        return success(myService.getMyTodoList(reqVO, null));
    }

    // ==================== 第二组：我参与的对象 ====================

    @GetMapping("/project-page")
    @Operation(summary = "我参与的项目（负责人字段或团队成员命中我）")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<PageResult<ProjectRespVO>> getMyProjectPage(@Valid ProjectPageReqVO reqVO) {
        return success(myService.getMyProjectPage(reqVO, null));
    }

    @GetMapping("/execution-page")
    @Operation(summary = "我参与的执行（负责人字段或团队成员命中我）")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<PageResult<ProjectRespVO>> getMyExecutionPage(@Valid ExecutionPageReqVO reqVO) {
        return success(myService.getMyExecutionPage(reqVO, null));
    }

    @GetMapping("/team-list")
    @Operation(summary = "我的团队：我在哪些项目/执行里、什么角色、可用工时")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<List<MyTeamRespVO>> getMyTeamList(
            @RequestParam(value = "account", required = false) String account) {
        return success(myService.getMyTeamList(account));
    }

    @GetMapping("/testtask-page")
    @Operation(summary = "我参与的测试单（负责人或创建人命中我）")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<PageResult<TestTaskRespVO>> getMyTestTaskPage(@Valid TestTaskPageReqVO reqVO) {
        return success(myService.getMyTestTaskPage(reqVO, null));
    }

    @GetMapping("/case-page")
    @Operation(summary = "我的用例（我创建的或我评审过的）")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<PageResult<CaseRespVO>> getMyCasePage(@Valid CasePageReqVO reqVO) {
        return success(myService.getMyCasePage(reqVO, null));
    }

    @GetMapping("/doc-page")
    @Operation(summary = "我的文档（创建人/指派给/最后修改人命中我）")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<PageResult<DocRespVO>> getMyDocPage(@Valid DocPageReqVO reqVO) {
        return success(myService.getMyDocPage(reqVO, null));
    }

    @GetMapping("/calendar")
    @Operation(summary = "我的日历：把待办/任务/测试单按日期归集到一个月")
    @Parameter(name = "month", description = "YYYY-MM，不传取当月", example = "2026-09")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<List<Map<String, Object>>> getMyCalendar(
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "account", required = false) String account) {
        return success(myService.getMyCalendar(month, account));
    }

    @GetMapping("/action-list")
    @Operation(summary = "我最近的动态")
    @Parameter(name = "limit", description = "取多少条", example = "20")
    @PreAuthorize("@ss.hasPermission('zentao:my:query')")
    public CommonResult<List<ActionTimelineRespVO>> getMyActionList(
            @RequestParam(value = "account", required = false) String account,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        return success(BeanUtils.toBean(myService.getMyActionList(account, limit), ActionTimelineRespVO.class));
    }

}

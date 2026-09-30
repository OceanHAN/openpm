package cn.iocoder.yudao.module.zentao.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskFinishReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO;
import cn.iocoder.yudao.module.zentao.service.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 任务 Controller
 */
@Tag(name = "管理后台 - 任务")
@RestController
@RequestMapping("/zentao/task")
@Validated
public class TaskController {

    @Resource
    private TaskService taskService;

    @PostMapping("/create")
    @Operation(summary = "创建任务")
    @PreAuthorize("@ss.hasPermission('zentao:task:create')")
    public CommonResult<Long> createTask(@Valid @RequestBody TaskSaveReqVO createReqVO) {
        return success(taskService.createTask(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改任务")
    @PreAuthorize("@ss.hasPermission('zentao:task:update')")
    public CommonResult<Boolean> updateTask(@Valid @RequestBody TaskSaveReqVO updateReqVO) {
        taskService.updateTask(updateReqVO);
        return success(true);
    }

    @PutMapping("/start")
    @Operation(summary = "开始任务")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:task:update')")
    public CommonResult<Boolean> startTask(@RequestParam("id") Long id) {
        taskService.startTask(id);
        return success(true);
    }

    @PutMapping("/finish")
    @Operation(summary = "完成任务",
            description = "登记本次消耗与剩余工时。剩余归零才算真正完成，否则回到进行中")
    @PreAuthorize("@ss.hasPermission('zentao:task:update')")
    public CommonResult<Boolean> finishTask(@Valid @RequestBody TaskFinishReqVO reqVO) {
        taskService.finishTask(reqVO);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭任务", description = "只有已完成的任务才能关闭")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:task:update')")
    public CommonResult<Boolean> closeTask(@RequestParam("id") Long id,
                                           @RequestParam(value = "reason", required = false) String reason) {
        taskService.closeTask(id, reason);
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "取消任务")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:task:update')")
    public CommonResult<Boolean> cancelTask(@RequestParam("id") Long id) {
        taskService.cancelTask(id);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活任务")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:task:update')")
    public CommonResult<Boolean> activateTask(@RequestParam("id") Long id) {
        taskService.activateTask(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除任务")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:task:delete')")
    public CommonResult<Boolean> deleteTask(@RequestParam("id") Long id) {
        taskService.deleteTask(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除任务")
    @Parameter(name = "ids", description = "任务编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:task:delete')")
    public CommonResult<Boolean> deleteTaskList(@RequestParam("ids") List<Long> ids) {
        taskService.deleteTaskList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得任务")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:task:query')")
    public CommonResult<TaskRespVO> getTask(@RequestParam("id") Long id) {
        TaskDO task = taskService.getTask(id);
        TaskRespVO vo = BeanUtils.toBean(task, TaskRespVO.class);
        // 需求标题 / 需求是否已变更 —— 只有服务层拿得到
        taskService.fillStoryInfo(List.of(vo));
        return success(vo);
    }

    @GetMapping("/page")
    @Operation(summary = "获得任务分页")
    @PreAuthorize("@ss.hasPermission('zentao:task:query')")
    public CommonResult<PageResult<TaskRespVO>> getTaskPage(@Valid TaskPageReqVO pageReqVO) {
        PageResult<TaskDO> pageResult = taskService.getTaskPage(pageReqVO);
        PageResult<TaskRespVO> page = BeanUtils.toBean(pageResult, TaskRespVO.class);
        taskService.fillStoryInfo(page.getList());
        return success(page);
    }

    @PostMapping("/batch-create-from-story")
    @Operation(summary = "需求转任务", description = "只给任务名称；优先级从需求继承，需求版本会冻结在任务上")
    @Parameter(name = "storyId", description = "需求编号", required = true, example = "1")
    @Parameter(name = "execution", description = "目标执行编号（任务必须挂在执行下）", required = true, example = "90001")
    @Parameter(name = "project", description = "目标项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:task:create')")
    public CommonResult<List<Long>> batchCreateFromStory(@RequestParam("storyId") Long storyId,
                                                         @RequestParam("execution") Long execution,
                                                         @RequestParam("project") Long project,
                                                         @RequestBody List<String> names) {
        return success(taskService.batchCreateFromStory(storyId, execution, project, names));
    }

    @GetMapping("/list-by-story")
    @Operation(summary = "获得某需求下的全部任务")
    @Parameter(name = "story", description = "需求编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:task:query')")
    public CommonResult<List<TaskRespVO>> getTaskListByStory(@RequestParam("story") Long story) {
        List<TaskDO> list = taskService.getTaskListByStory(story);
        List<TaskRespVO> vos = BeanUtils.toBean(list, TaskRespVO.class);
        taskService.fillStoryInfo(vos);
        return success(vos);
    }

}

package cn.iocoder.yudao.module.zentao.controller.admin.effort;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortSummaryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortTaskStatRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.effort.EffortDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO;
import cn.iocoder.yudao.module.zentao.service.effort.EffortService;
import cn.iocoder.yudao.module.zentao.service.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 工时明细 Controller
 *
 * 对应禅道 {@code module/task/control.php} 的 recordWorkhour / editWorkhour / deleteWorkhour。
 */
@Tag(name = "管理后台 - 工时明细")
@RestController
@RequestMapping("/zentao/effort")
@Validated
public class EffortController {

    @Resource
    private EffortService effortService;

    @Resource
    private TaskService taskService;

    @PostMapping("/create")
    @Operation(summary = "登记工时",
            description = "登记后自动重算任务的已消耗/剩余工时，剩余归零则任务自动变成已完成")
    @PreAuthorize("@ss.hasPermission('zentao:effort:create')")
    public CommonResult<Long> createEffort(@Valid @RequestBody EffortSaveReqVO createReqVO) {
        return success(effortService.createEffort(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改工时")
    @PreAuthorize("@ss.hasPermission('zentao:effort:update')")
    public CommonResult<Boolean> updateEffort(@Valid @RequestBody EffortSaveReqVO updateReqVO) {
        effortService.updateEffort(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工时", description = "删除后重算任务；删掉最后一条工时，任务回到未开始并按预计工时填充剩余")
    @Parameter(name = "id", description = "工时编号", required = true, example = "96101")
    @PreAuthorize("@ss.hasPermission('zentao:effort:delete')")
    public CommonResult<Boolean> deleteEffort(@RequestParam("id") Long id) {
        effortService.deleteEffort(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工时")
    @Parameter(name = "id", description = "工时编号", required = true, example = "96101")
    @PreAuthorize("@ss.hasPermission('zentao:effort:query')")
    public CommonResult<EffortRespVO> getEffort(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(effortService.getEffort(id), EffortRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得任务的工时列表", description = "按日期正序，最后一条决定任务剩余工时")
    @Parameter(name = "taskId", description = "任务编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:effort:query')")
    public CommonResult<List<EffortRespVO>> getEffortList(@RequestParam("taskId") Long taskId) {
        List<EffortRespVO> list = BeanUtils.toBean(effortService.getEffortListByTask(taskId), EffortRespVO.class);
        fillTaskName(list);
        return success(list);
    }

    @GetMapping("/page")
    @Operation(summary = "获得工时分页", description = "支持按账号/项目/执行/日期区间筛选，任务名会回填")
    @PreAuthorize("@ss.hasPermission('zentao:effort:query')")
    public CommonResult<PageResult<EffortRespVO>> getEffortPage(@Valid EffortPageReqVO pageReqVO) {
        PageResult<EffortDO> page = effortService.getEffortPage(pageReqVO);
        PageResult<EffortRespVO> result = BeanUtils.toBean(page, EffortRespVO.class);
        fillTaskName(result.getList());
        return success(result);
    }

    @GetMapping("/summary")
    @Operation(summary = "按账号汇总工时", description = "工时报表：每个人投入了多少小时、涉及多少个任务")
    @PreAuthorize("@ss.hasPermission('zentao:effort:query')")
    public CommonResult<List<EffortSummaryRespVO>> getEffortSummary(@Valid EffortPageReqVO reqVO) {
        return success(effortService.getEffortSummary(reqVO));
    }

    @GetMapping("/task-stat")
    @Operation(summary = "获得任务的工时统计",
            description = "预计/已消耗/剩余/状态 + 工时明细，用于「录完工时任务变成什么样」的核对")
    @Parameter(name = "taskId", description = "任务编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:effort:query')")
    public CommonResult<EffortTaskStatRespVO> getTaskStat(@RequestParam("taskId") Long taskId) {
        return success(effortService.getTaskStat(taskId));
    }

    /**
     * 回填任务名。工时表只存任务编号，列表里光看编号没法用。
     */
    private void fillTaskName(List<EffortRespVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Collection<Long> taskIds = list.stream().map(EffortRespVO::getObjectID)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return;
        }
        Map<Long, TaskDO> taskMap = taskService.getTaskMap(taskIds);
        for (EffortRespVO vo : list) {
            TaskDO task = taskMap.get(vo.getObjectID());
            if (task != null) {
                vo.setTaskName(task.getName());
            }
        }
    }

}

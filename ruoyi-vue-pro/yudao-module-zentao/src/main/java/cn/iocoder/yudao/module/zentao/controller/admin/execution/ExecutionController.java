package cn.iocoder.yudao.module.zentao.controller.admin.execution;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.burn.vo.BurnChartRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.service.burn.BurnService;
import cn.iocoder.yudao.module.zentao.service.execution.ExecutionService;
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
 * 执行 Controller
 *
 * 执行（迭代/阶段/看板）与项目共用 {@code zt_project} 表，靠 {@code type} 区分。
 * 因此这里的请求/响应 VO 直接复用了 Project 的 —— 字段完全一致。
 */
@Tag(name = "管理后台 - 执行")
@RestController
@RequestMapping("/zentao/execution")
@Validated
public class ExecutionController {

    @Resource
    private ExecutionService executionService;

    @Resource
    private BurnService burnService;

    // ==================== 燃尽图（禅道 execution 的 burn / computeBurn）====================

    @GetMapping("/burn-data")
    @Operation(summary = "燃尽图数据",
            description = "返回 labels / burnLine（实际）/ baseLine（理想）/ delayLine（延期段，只有真延期才给）"
                    + "三条与 labels 等长的曲线；数据来自 zt_burn 的每日快照，缺失日期用前一个快照补齐")
    @Parameter(name = "id", description = "执行编号", required = true, example = "90001")
    @Parameter(name = "type", description = "noweekend 跳过周末 / weekend 含周末", example = "noweekend")
    @Parameter(name = "burnBy", description = "left 剩余 / estimate 原计划 / consumed 已消耗 / storyPoint 需求规模",
            example = "left")
    @Parameter(name = "interval", description = "采样间隔，不传按总数/31 自动算", example = "5")
    @PreAuthorize("@ss.hasPermission('zentao:execution:burn')")
    public CommonResult<BurnChartRespVO> getBurnData(
            @RequestParam("id") Long id,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "burnBy", required = false) String burnBy,
            @RequestParam(value = "interval", required = false) Integer interval) {
        return success(burnService.getBurnData(id, type, burnBy, interval));
    }

    @PostMapping("/compute-burn")
    @Operation(summary = "重新计算燃尽图",
            description = "把当天任务的 estimate/left/consumed/需求规模汇总成一条快照写进 zt_burn（REPLACE 当天那一行）")
    @Parameter(name = "id", description = "执行编号；不传则算全部「未开始/进行中」的迭代与阶段", example = "90001")
    @PreAuthorize("@ss.hasPermission('zentao:execution:burn')")
    public CommonResult<Integer> computeBurn(@RequestParam(value = "id", required = false) Long id) {
        return success(burnService.computeBurn(id).size());
    }

    @PostMapping("/create")
    @Operation(summary = "创建执行", description = "必须指定所属项目，type 必须是 sprint/stage/kanban")
    @PreAuthorize("@ss.hasPermission('zentao:execution:create')")
    public CommonResult<Long> createExecution(@Valid @RequestBody ExecutionSaveReqVO createReqVO) {
        return success(executionService.createExecution(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改执行")
    @PreAuthorize("@ss.hasPermission('zentao:execution:update')")
    public CommonResult<Boolean> updateExecution(@Valid @RequestBody ExecutionSaveReqVO updateReqVO) {
        executionService.updateExecution(updateReqVO);
        return success(true);
    }

    @PutMapping("/start")
    @Operation(summary = "开始执行")
    @Parameter(name = "id", description = "执行编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('zentao:execution:update')")
    public CommonResult<Boolean> startExecution(@RequestParam("id") Long id) {
        executionService.startExecution(id);
        return success(true);
    }

    @PutMapping("/suspend")
    @Operation(summary = "挂起执行")
    @Parameter(name = "id", description = "执行编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('zentao:execution:update')")
    public CommonResult<Boolean> suspendExecution(@RequestParam("id") Long id) {
        executionService.suspendExecution(id);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活执行")
    @Parameter(name = "id", description = "执行编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('zentao:execution:update')")
    public CommonResult<Boolean> activateExecution(@RequestParam("id") Long id) {
        executionService.activateExecution(id);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭执行")
    @Parameter(name = "id", description = "执行编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('zentao:execution:update')")
    public CommonResult<Boolean> closeExecution(@RequestParam("id") Long id,
                                                @RequestParam(value = "reason", required = false) String reason) {
        executionService.closeExecution(id, reason);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除执行")
    @Parameter(name = "id", description = "执行编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('zentao:execution:delete')")
    public CommonResult<Boolean> deleteExecution(@RequestParam("id") Long id) {
        executionService.deleteExecution(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除执行")
    @Parameter(name = "ids", description = "执行编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:execution:delete')")
    public CommonResult<Boolean> deleteExecutionList(@RequestParam("ids") List<Long> ids) {
        executionService.deleteExecutionList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得执行", description = "传入项目编号会报错，避免把项目当执行操作")
    @Parameter(name = "id", description = "执行编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('zentao:execution:query')")
    public CommonResult<ProjectRespVO> getExecution(@RequestParam("id") Long id) {
        ProjectDO execution = executionService.getExecution(id);
        return success(BeanUtils.toBean(execution, ProjectRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得执行分页")
    @PreAuthorize("@ss.hasPermission('zentao:execution:query')")
    public CommonResult<PageResult<ProjectRespVO>> getExecutionPage(@Valid ExecutionPageReqVO pageReqVO) {
        PageResult<ProjectDO> pageResult = executionService.getExecutionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectRespVO.class));
    }

    @GetMapping("/list-by-project")
    @Operation(summary = "获得某个项目下的全部执行")
    @Parameter(name = "project", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:execution:query')")
    public CommonResult<List<ProjectRespVO>> getExecutionListByProject(@RequestParam("project") Long project) {
        List<ProjectDO> list = executionService.getExecutionListByProject(project);
        return success(BeanUtils.toBean(list, ProjectRespVO.class));
    }

    @GetMapping("/count-by-project")
    @Operation(summary = "统计某个项目下的执行数量")
    @Parameter(name = "project", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:execution:query')")
    public CommonResult<Long> countExecutionByProject(@RequestParam("project") Long project) {
        return success(executionService.countExecutionByProject(project));
    }

}

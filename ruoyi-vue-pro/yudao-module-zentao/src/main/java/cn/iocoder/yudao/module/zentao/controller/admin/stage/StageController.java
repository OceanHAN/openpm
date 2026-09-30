package cn.iocoder.yudao.module.zentao.controller.admin.stage;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.stage.vo.StageRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.stage.vo.StageSaveReqVO;
import cn.iocoder.yudao.module.zentao.enums.stage.StageTypeEnum;
import cn.iocoder.yudao.module.zentao.service.stage.StageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 阶段（瀑布流程）Controller
 *
 * <p>两张面孔：
 * <ul>
 *   <li>阶段<b>模板</b>（zt_stage）：一套瀑布流程的阶段定义与工作量占比，多个项目共用</li>
 *   <li>项目<b>阶段</b>（zt_project type='stage'）：某个项目按模板生成的实际阶段，
 *       状态流转直接复用执行模块（wait → doing → closed）</li>
 * </ul>
 */
@Tag(name = "管理后台 - 禅道阶段（瀑布流程）")
@RestController
@RequestMapping("/zentao/stage")
@Validated
public class StageController {

    @Resource
    private StageService stageService;

    // ==================== 阶段模板 ====================

    @PostMapping("/create")
    @Operation(summary = "新建阶段模板", description = "同组内名称唯一；工作量占比累计不能超过 100%")
    @PreAuthorize("@ss.hasPermission('zentao:stage:create')")
    public CommonResult<Long> createStage(@Valid @RequestBody StageSaveReqVO createReqVO) {
        return success(stageService.createStage(createReqVO));
    }

    @PostMapping("/batch-create")
    @Operation(summary = "批量新建阶段模板")
    @Parameter(name = "workflowGroup", description = "流程模板组", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:stage:create')")
    public CommonResult<List<Long>> batchCreateStages(@RequestParam("workflowGroup") Long workflowGroup,
                                                      @Valid @RequestBody List<StageSaveReqVO> stages) {
        return success(stageService.batchCreateStages(workflowGroup, stages));
    }

    @PutMapping("/update")
    @Operation(summary = "修改阶段模板")
    @PreAuthorize("@ss.hasPermission('zentao:stage:update')")
    public CommonResult<Boolean> updateStage(@Valid @RequestBody StageSaveReqVO updateReqVO) {
        stageService.updateStage(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除阶段模板", description = "只删模板，已生成到项目里的阶段不受影响")
    @Parameter(name = "id", description = "阶段模板编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:stage:delete')")
    public CommonResult<Boolean> deleteStage(@RequestParam("id") Long id) {
        stageService.deleteStage(id);
        return success(true);
    }

    @PutMapping("/update-order")
    @Operation(summary = "按传入的编号顺序重排阶段", description = "对应禅道拖拽排序")
    @PreAuthorize("@ss.hasPermission('zentao:stage:update')")
    public CommonResult<Boolean> updateOrder(@RequestBody List<Long> stageIds) {
        stageService.updateOrder(stageIds);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得阶段模板")
    @Parameter(name = "id", description = "阶段模板编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:stage:query')")
    public CommonResult<StageRespVO> getStage(@RequestParam("id") Long id) {
        return success(toRespVO(stageService.getStage(id)));
    }

    @GetMapping("/list")
    @Operation(summary = "获得流程模板下的阶段列表")
    @Parameter(name = "workflowGroup", description = "流程模板组", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:stage:query')")
    public CommonResult<List<StageRespVO>> getStageList(@RequestParam("workflowGroup") Long workflowGroup) {
        return success(BeanUtils.toBean(stageService.getStageListByGroup(workflowGroup),
                StageRespVO.class, this::fillRespVO));
    }

    @GetMapping("/list-by-project-type")
    @Operation(summary = "获得某类项目流程的阶段模板", description = "瀑布项目创建时用来自动生成阶段")
    @Parameter(name = "projectType", description = "waterfall/waterfallplus/ipd", example = "waterfall")
    @PreAuthorize("@ss.hasPermission('zentao:stage:query')")
    public CommonResult<List<StageRespVO>> getStageListByProjectType(
            @RequestParam(value = "projectType", required = false) String projectType) {
        return success(BeanUtils.toBean(stageService.getStageListByProjectType(projectType),
                StageRespVO.class, this::fillRespVO));
    }

    @GetMapping("/total-percent")
    @Operation(summary = "获得流程模板的工作量占比合计", description = "超过 100% 时前端应给出提示")
    @Parameter(name = "workflowGroup", description = "流程模板组", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:stage:query')")
    public CommonResult<BigDecimal> getTotalPercent(@RequestParam("workflowGroup") Long workflowGroup) {
        return success(stageService.getTotalPercent(workflowGroup));
    }

    @GetMapping("/type-list")
    @Operation(summary = "获得阶段类型列表")
    @PreAuthorize("@ss.hasPermission('zentao:stage:query')")
    public CommonResult<List<Map<String, String>>> getTypeList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (StageTypeEnum item : StageTypeEnum.values()) {
            list.add(Map.of("value", item.getType(), "label", item.getName()));
        }
        return success(list);
    }

    // ==================== 项目阶段（实例） ====================

    @PostMapping("/generate")
    @Operation(summary = "按模板为项目生成阶段",
            description = "写入 zt_project（type='stage'）；项目已经生成过阶段时会拒绝，避免重复")
    @Parameter(name = "project", description = "项目编号", required = true, example = "1")
    @Parameter(name = "workflowGroup", description = "流程模板组，不传则用项目自身的或按 model 匹配", example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:stage:create')")
    public CommonResult<List<Long>> generateStages(@RequestParam("project") Long project,
                                                   @RequestParam(value = "workflowGroup", required = false) Long workflowGroup) {
        return success(stageService.generateStages(project, workflowGroup));
    }

    @GetMapping("/project-stages")
    @Operation(summary = "获得项目下的阶段列表")
    @Parameter(name = "project", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:stage:query')")
    public CommonResult<List<StageRespVO>> getProjectStages(@RequestParam("project") Long project) {
        return success(stageService.getProjectStages(project));
    }

    @DeleteMapping("/delete-project-stages")
    @Operation(summary = "删除项目的全部阶段", description = "便于按模板重新生成")
    @Parameter(name = "project", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:stage:delete')")
    public CommonResult<Boolean> deleteProjectStages(@RequestParam("project") Long project) {
        stageService.deleteProjectStages(project);
        return success(true);
    }

    // ==================== 展示字段 ====================

    private StageRespVO toRespVO(cn.iocoder.yudao.module.zentao.dal.dataobject.stage.StageDO stage) {
        StageRespVO vo = BeanUtils.toBean(stage, StageRespVO.class);
        fillRespVO(vo);
        return vo;
    }

    private void fillRespVO(StageRespVO vo) {
        if (vo != null) {
            vo.setTypeName(StageTypeEnum.nameOf(vo.getType()));
        }
    }

}

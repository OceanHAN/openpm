package cn.iocoder.yudao.module.zentao.controller.admin.workestimation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zentao.controller.admin.workestimation.vo.WorkEstimationRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.workestimation.vo.WorkEstimationSaveReqVO;
import cn.iocoder.yudao.module.zentao.service.workestimation.WorkEstimationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 项目工作量估算 Controller
 *
 * 对应禅道 {@code module/workestimation/}。禅道开源版只有 model 没有界面，
 * 这里把接口补齐（含工期与总人工成本两个派生值）。
 */
@Tag(name = "管理后台 - 工作量估算")
@RestController
@RequestMapping("/zentao/workestimation")
@Validated
public class WorkEstimationController {

    @Resource
    private WorkEstimationService workEstimationService;

    @GetMapping("/get")
    @Operation(summary = "获得项目的工作量估算", description = "没有估算时返回 null（不是错误）")
    @Parameter(name = "project", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:workestimation:query')")
    public CommonResult<WorkEstimationRespVO> get(@RequestParam("project") Long project) {
        return success(workEstimationService.getByProject(project));
    }

    @PutMapping("/save")
    @Operation(summary = "保存项目的工作量估算",
            description = "duration = scale / productivity；totalLaborCost = duration × dayHour × unitLaborCost（两个派生值服务端算）")
    @PreAuthorize("@ss.hasPermission('zentao:workestimation:update')")
    public CommonResult<WorkEstimationRespVO> save(@Valid @RequestBody WorkEstimationSaveReqVO reqVO) {
        return success(workEstimationService.save(reqVO));
    }

}

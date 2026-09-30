package cn.iocoder.yudao.module.zentao.controller.admin.projectplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.plan.PlanDO;
import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectProductRespVO;
import cn.iocoder.yudao.module.zentao.service.plan.PlanService;
import cn.iocoder.yudao.module.zentao.service.projectstory.ProjectStoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;

/**
 * 项目计划视图 Controller（对应禅道 {@code module/projectplan/}）
 *
 * <h3>禅道里它是纯 redirect</h3>
 * {@code projectplan/control.php} 的每个方法都是
 * {@code echo $this->fetch('productplan', ...)} —— 计划本来就是**产品维度**的，
 * 「项目计划」只是换个入口看同一批数据。所以本实现不另建表，
 * 而是提供一个「按项目聚合它关联产品下的计划」的只读接口。
 */
@Tag(name = "管理后台 - 项目计划视图")
@RestController
@RequestMapping("/zentao/projectplan")
@Validated
public class ProjectPlanController {

    @Resource
    private ProjectStoryService projectStoryService;

    @Resource
    private PlanService planService;

    @GetMapping("/plan-list")
    @Operation(summary = "项目关联产品下的计划列表",
            description = "计划挂在产品下；项目视角 = 项目关联的每个产品（可限定分支）的计划并集")
    @Parameter(name = "project", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:projectplan:query')")
    public CommonResult<Map<String, Object>> planList(@RequestParam("project") Long project) {
        List<ProjectProductRespVO> products = projectStoryService.getLinkedProducts(project);
        List<PlanRespVO> plans = new ArrayList<>();
        for (ProjectProductRespVO link : products) {
            for (PlanDO plan : planService.getPlanListByProduct(link.getProduct(), link.getBranch())) {
                plans.add(BeanUtils.toBean(plan, PlanRespVO.class));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productCount", products.size());
        result.put("total", plans.size());
        result.put("list", plans);
        return success(result);
    }

}

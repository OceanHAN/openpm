package cn.iocoder.yudao.module.zentao.controller.admin.qa;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zentao.controller.admin.qa.vo.QaDashboardRespVO;
import cn.iocoder.yudao.module.zentao.service.qa.QaDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 测试仪表盘（禅道 {@code module/qa} 的 index）。
 *
 * <p><b>类名带 {@code Zentao} 前缀</b>是延续坑位 #47 的纪律：新模块开工前先全局搜同名类 /
 * 同名注入字段 / 同名 mapper，避免 bean 名冲突（`QaController` 这种短名字最容易撞）。
 */
@Tag(name = "管理后台 - 禅道测试仪表盘")
@RestController
@RequestMapping("/zentao/qa")
public class ZentaoQaController {

    @Resource
    private QaDashboardService qaDashboardService;

    @GetMapping("/dashboard")
    @Operation(summary = "测试仪表盘",
            description = "按产品统计质量（新增/解决/关闭、有效缺陷、修复率、未完成测试单与待评审用例）"
                    + "+ 待处理缺陷 / 待评审用例 / 未完成测试单三个列表块；口径取自禅道的度量定义")
    @Parameter(name = "product", description = "只看某个产品（不传=全部）", example = "1")
    @Parameter(name = "days", description = "统计区间：最近 N 天，默认 7", example = "7")
    @PreAuthorize("@ss.hasPermission('zentao:qa:query')")
    public CommonResult<QaDashboardRespVO> getDashboard(
            @RequestParam(value = "product", required = false) Long product,
            @RequestParam(value = "days", required = false) Integer days) {
        return success(qaDashboardService.getDashboard(product, days));
    }

}

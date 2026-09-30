package cn.iocoder.yudao.module.zentao.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductSimpleRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.service.product.ProductService;
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
 * 产品 Controller
 *
 * 产品是禅道的根对象，需求/计划/发布/缺陷都挂在产品下。
 */
@Tag(name = "管理后台 - 禅道产品")
@RestController
@RequestMapping("/zentao/product")
@Validated
public class ProductController {

    @Resource
    private ProductService productService;

    @PostMapping("/create")
    @Operation(summary = "创建产品")
    @PreAuthorize("@ss.hasPermission('zentao:product:create')")
    public CommonResult<Long> createProduct(@Valid @RequestBody ProductSaveReqVO createReqVO) {
        return success(productService.createProduct(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改产品")
    @PreAuthorize("@ss.hasPermission('zentao:product:update')")
    public CommonResult<Boolean> updateProduct(@Valid @RequestBody ProductSaveReqVO updateReqVO) {
        productService.updateProduct(updateReqVO);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭产品")
    @Parameter(name = "id", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:product:update')")
    public CommonResult<Boolean> closeProduct(@RequestParam("id") Long id) {
        productService.closeProduct(id);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活产品")
    @Parameter(name = "id", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:product:update')")
    public CommonResult<Boolean> activateProduct(@RequestParam("id") Long id) {
        productService.activateProduct(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除产品", description = "产品下还有需求时不允许删除")
    @Parameter(name = "id", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:product:delete')")
    public CommonResult<Boolean> deleteProduct(@RequestParam("id") Long id) {
        productService.deleteProduct(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除产品")
    @Parameter(name = "ids", description = "产品编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:product:delete')")
    public CommonResult<Boolean> deleteProductList(@RequestParam("ids") List<Long> ids) {
        productService.deleteProductList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得产品", description = "同时返回该产品下需求与缺陷的实时统计")
    @Parameter(name = "id", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:product:query')")
    public CommonResult<ProductRespVO> getProduct(@RequestParam("id") Long id) {
        ProductDO product = productService.validateProductExists(id);
        ProductRespVO vo = BeanUtils.toBean(product, ProductRespVO.class);
        vo.setStats(productService.getProductStats(id));
        return success(vo);
    }

    @GetMapping("/page")
    @Operation(summary = "获得产品分页")
    @PreAuthorize("@ss.hasPermission('zentao:product:query')")
    public CommonResult<PageResult<ProductRespVO>> getProductPage(@Valid ProductPageReqVO pageReqVO) {
        PageResult<ProductDO> pageResult = productService.getProductPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProductRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得产品精简列表", description = "用于需求页面的产品下拉选择")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<List<ProductSimpleRespVO>> getProductSimpleList() {
        List<ProductDO> list = productService.getProductSimpleList();
        return success(BeanUtils.toBean(list, ProductSimpleRespVO.class));
    }

}

package cn.iocoder.yudao.module.zentao.controller.admin.branch;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.branch.vo.BranchPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.branch.vo.BranchRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.branch.vo.BranchSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.enums.product.ProductTypeEnum;
import cn.iocoder.yudao.module.zentao.service.branch.BranchService;
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
 * 分支/平台 Controller
 *
 * <p>产品类型为 platform 时这套接口叫「平台」，为 branch 时叫「分支」，
 * 由 {@link BranchService#branchNameOf(Long)} 决定文案。id = 0 是虚拟主干。
 */
@Tag(name = "管理后台 - 禅道分支/平台")
@RestController
@RequestMapping("/zentao/branch")
@Validated
public class BranchController {

    @Resource
    private BranchService branchService;

    @Resource
    private ProductService productService;

    @PostMapping("/create")
    @Operation(summary = "创建分支", description = "产品类型必须是 branch 或 platform")
    @PreAuthorize("@ss.hasPermission('zentao:branch:create')")
    public CommonResult<Long> createBranch(@Valid @RequestBody BranchSaveReqVO createReqVO) {
        return success(branchService.createBranch(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改分支")
    @PreAuthorize("@ss.hasPermission('zentao:branch:update')")
    public CommonResult<Boolean> updateBranch(@Valid @RequestBody BranchSaveReqVO updateReqVO) {
        branchService.updateBranch(updateReqVO);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭分支", description = "关闭会同时取消默认分支标记")
    @Parameter(name = "id", description = "分支编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:branch:update')")
    public CommonResult<Boolean> closeBranch(@RequestParam("id") Long id) {
        branchService.closeBranch(id);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活分支")
    @Parameter(name = "id", description = "分支编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:branch:update')")
    public CommonResult<Boolean> activateBranch(@RequestParam("id") Long id) {
        branchService.activateBranch(id);
        return success(true);
    }

    @PutMapping("/set-default")
    @Operation(summary = "设置默认分支", description = "branchId 传 0 表示默认分支为主干（清空默认标记）")
    @Parameter(name = "product", description = "产品编号", required = true, example = "2")
    @Parameter(name = "branchId", description = "分支编号，0 表示主干", example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:branch:update')")
    public CommonResult<Boolean> setDefaultBranch(@RequestParam("product") Long product,
                                                  @RequestParam("branchId") Long branchId) {
        branchService.setDefaultBranch(product, branchId);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除分支", description = "分支下已有需求/缺陷时不允许删除")
    @Parameter(name = "id", description = "分支编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:branch:delete')")
    public CommonResult<Boolean> deleteBranch(@RequestParam("id") Long id) {
        branchService.deleteBranch(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除分支")
    @Parameter(name = "ids", description = "分支编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:branch:delete')")
    public CommonResult<Boolean> deleteBranchList(@RequestParam("ids") List<Long> ids) {
        branchService.deleteBranchList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得分支", description = "id 传 0 返回虚拟主干")
    @Parameter(name = "id", description = "分支编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:branch:query')")
    public CommonResult<BranchRespVO> getBranch(@RequestParam("id") Long id) {
        return success(toRespVO(branchService.getBranch(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得分支分页", description = "指定产品时第一页会补一行「主干」")
    @PreAuthorize("@ss.hasPermission('zentao:branch:query')")
    public CommonResult<PageResult<BranchRespVO>> getBranchPage(@Valid BranchPageReqVO pageReqVO) {
        PageResult<BranchDO> pageResult = branchService.getBranchPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, BranchRespVO.class, this::fillRespVO));
    }

    @GetMapping("/list-by-product")
    @Operation(summary = "获得产品下的全部分支", description = "含虚拟主干，用于需求/缺陷页面的分支下拉")
    @Parameter(name = "product", description = "产品编号", required = true, example = "2")
    @Parameter(name = "status", description = "状态过滤，传 closed 时不返回主干")
    @PreAuthorize("@ss.hasPermission('zentao:branch:query')")
    public CommonResult<List<BranchRespVO>> getBranchListByProduct(@RequestParam("product") Long product,
                                                                   @RequestParam(value = "status", required = false) String status) {
        return success(BeanUtils.toBean(branchService.getBranchListByProduct(product, status),
                BranchRespVO.class, this::fillRespVO));
    }

    @GetMapping("/count-by-product")
    @Operation(summary = "获得产品下的分支数量", description = "不含虚拟主干")
    @Parameter(name = "product", description = "产品编号", required = true, example = "2")
    @PreAuthorize("@ss.hasPermission('zentao:branch:query')")
    public CommonResult<Long> countBranchByProduct(@RequestParam("product") Long product) {
        return success(branchService.countBranchByProduct(product));
    }

    private BranchRespVO toRespVO(BranchDO branch) {
        BranchRespVO vo = BeanUtils.toBean(branch, BranchRespVO.class);
        fillRespVO(vo);
        return vo;
    }

    /**
     * 补三个展示字段：
     * <ul>
     *   <li>{@code mainBranch} —— 是否是虚拟主干，前端据此禁用关闭/删除按钮</li>
     *   <li>{@code productName} —— 产品真实名称，列表直接展示</li>
     *   <li>{@code branchLabel} —— 业务文案（「分支」还是「平台」）</li>
     * </ul>
     */
    private void fillRespVO(BranchRespVO vo) {
        if (vo == null) {
            return;
        }
        vo.setMainBranch(vo.getId() != null && BranchDO.MAIN_BRANCH_ID.equals(vo.getId()));
        if (vo.getProduct() == null) {
            return;
        }
        ProductDO product = productService.getProduct(vo.getProduct());
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setBranchLabel(ProductTypeEnum.branchNameOf(product.getType()));
        } else {
            // 产品被删掉时不阻塞分支本身展示
            vo.setBranchLabel("分支");
        }
    }

}

package cn.iocoder.yudao.module.zentao.controller.admin.plan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.plan.PlanDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.enums.plan.PlanClosedReasonEnum;
import cn.iocoder.yudao.module.zentao.enums.plan.PlanStatusEnum;
import cn.iocoder.yudao.module.zentao.service.branch.BranchService;
import cn.iocoder.yudao.module.zentao.service.plan.PlanService;
import cn.iocoder.yudao.module.zentao.service.product.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 产品计划 Controller
 *
 * <p>计划是产品维度的排期单元：需求挂到计划上（{@code zt_story.plan}），发布再引用计划。
 * 三个禅道特色都在这里体现：多分支（branch 是逗号列表）、待定（日期哨兵 2030-01-01）、
 * 父子计划（parent = -1 表示有子计划）。
 */
@Tag(name = "管理后台 - 禅道产品计划")
@RestController
@RequestMapping("/zentao/plan")
@Validated
public class PlanController {

    @Resource
    private PlanService planService;

    @Resource
    private ProductService productService;

    @Resource
    private BranchService branchService;

    @PostMapping("/create")
    @Operation(summary = "创建计划", description = "多分支/多平台产品必须选分支；子计划日期必须在父计划范围内")
    @PreAuthorize("@ss.hasPermission('zentao:plan:create')")
    public CommonResult<Long> createPlan(@Valid @RequestBody PlanSaveReqVO createReqVO) {
        return success(planService.createPlan(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改计划", description = "分支范围缩小后，超出范围的需求/Bug 会自动解除关联")
    @PreAuthorize("@ss.hasPermission('zentao:plan:update')")
    public CommonResult<Boolean> updatePlan(@Valid @RequestBody PlanSaveReqVO updateReqVO) {
        planService.updatePlan(updateReqVO);
        return success(true);
    }

    @PutMapping("/start")
    @Operation(summary = "开始计划", description = "未开始 → 进行中")
    @Parameter(name = "id", description = "计划编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:plan:update')")
    public CommonResult<Boolean> startPlan(@RequestParam("id") Long id) {
        planService.startPlan(id);
        return success(true);
    }

    @PutMapping("/finish")
    @Operation(summary = "完成计划", description = "写入完成时间")
    @Parameter(name = "id", description = "计划编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:plan:update')")
    public CommonResult<Boolean> finishPlan(@RequestParam("id") Long id) {
        planService.finishPlan(id);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭计划", description = "关闭原因 done 已完成 / cancel 已取消；选「已完成」会顺带写完成时间")
    @Parameter(name = "id", description = "计划编号", required = true, example = "1")
    @Parameter(name = "reason", description = "关闭原因：done/cancel", example = "done")
    @PreAuthorize("@ss.hasPermission('zentao:plan:update')")
    public CommonResult<Boolean> closePlan(@RequestParam("id") Long id,
                                           @RequestParam(value = "reason", required = false) String reason) {
        planService.closePlan(id, reason);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活计划", description = "已关闭 → 进行中（禅道激活后是「进行中」而不是「未开始」）")
    @Parameter(name = "id", description = "计划编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:plan:update')")
    public CommonResult<Boolean> activatePlan(@RequestParam("id") Long id) {
        planService.activatePlan(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除计划", description = "有子计划的父计划不能删除")
    @Parameter(name = "id", description = "计划编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:plan:delete')")
    public CommonResult<Boolean> deletePlan(@RequestParam("id") Long id) {
        planService.deletePlan(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得计划")
    @Parameter(name = "id", description = "计划编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:plan:query')")
    public CommonResult<PlanRespVO> getPlan(@RequestParam("id") Long id) {
        return success(toRespVO(planService.getPlan(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得计划分页")
    @PreAuthorize("@ss.hasPermission('zentao:plan:query')")
    public CommonResult<PageResult<PlanRespVO>> getPlanPage(@Valid PlanPageReqVO pageReqVO) {
        PageResult<PlanDO> pageResult = planService.getPlanPage(pageReqVO);
        List<PlanRespVO> list = BeanUtils.toBean(pageResult.getList(), PlanRespVO.class, this::fillRespVO);
        fillStoryCount(list);
        PageResult<PlanRespVO> result = new PageResult<>(list, pageResult.getTotal());
        return success(result);
    }

    @GetMapping("/list-by-product")
    @Operation(summary = "获得产品下的计划列表", description = "用于需求表单的计划下拉")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @Parameter(name = "branch", description = "分支/平台，可选")
    @PreAuthorize("@ss.hasPermission('zentao:plan:query')")
    public CommonResult<List<PlanRespVO>> getPlanListByProduct(@RequestParam("product") Long product,
                                                               @RequestParam(value = "branch", required = false) Long branch) {
        List<PlanRespVO> list = BeanUtils.toBean(planService.getPlanListByProduct(product, branch),
                PlanRespVO.class, this::fillRespVO);
        fillStoryCount(list);
        return success(list);
    }

    @GetMapping("/story-list")
    @Operation(summary = "获得计划下的需求")
    @Parameter(name = "plan", description = "计划编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:plan:query')")
    public CommonResult<List<StoryRespVO>> getPlanStoryList(@RequestParam("plan") Long plan) {
        return success(BeanUtils.toBean(planService.getPlanStories(plan), StoryRespVO.class));
    }

    @GetMapping("/unlinked-story-list")
    @Operation(summary = "获得还没关联到计划的需求", description = "同产品、分支在计划覆盖范围内、且未挂任何计划")
    @Parameter(name = "plan", description = "计划编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:plan:query')")
    public CommonResult<List<StoryRespVO>> getUnlinkedStoryList(@RequestParam("plan") Long plan) {
        return success(BeanUtils.toBean(planService.getUnlinkedStories(plan), StoryRespVO.class));
    }

    @PutMapping("/link-story")
    @Operation(summary = "关联需求到计划",
            description = "研发需求(type=story)独占一个计划，换计划会从旧计划移走；其它类型会累加到计划列表上")
    @PreAuthorize("@ss.hasPermission('zentao:plan:update')")
    public CommonResult<Boolean> linkStory(@Valid @RequestBody PlanLinkReqVO reqVO) {
        planService.linkStories(reqVO.getPlan(), reqVO.getIds());
        return success(true);
    }

    @DeleteMapping("/unlink-story")
    @Operation(summary = "从计划移除需求")
    @Parameter(name = "plan", description = "计划编号", required = true, example = "1")
    @Parameter(name = "story", description = "需求编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:plan:update')")
    public CommonResult<Boolean> unlinkStory(@RequestParam("plan") Long plan,
                                             @RequestParam("story") Long story) {
        planService.unlinkStory(plan, story);
        return success(true);
    }

    @PutMapping("/link-bug")
    @Operation(summary = "关联 Bug 到计划")
    @PreAuthorize("@ss.hasPermission('zentao:plan:update')")
    public CommonResult<Boolean> linkBug(@Valid @RequestBody PlanLinkReqVO reqVO) {
        planService.linkBugs(reqVO.getPlan(), reqVO.getIds());
        return success(true);
    }

    @DeleteMapping("/unlink-bug")
    @Operation(summary = "从计划移除 Bug")
    @Parameter(name = "plan", description = "计划编号", required = true, example = "1")
    @Parameter(name = "bug", description = "Bug 编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:plan:update')")
    public CommonResult<Boolean> unlinkBug(@RequestParam("plan") Long plan,
                                           @RequestParam("bug") Long bug) {
        planService.unlinkBug(plan, bug);
        return success(true);
    }

    @GetMapping("/reason-list")
    @Operation(summary = "获得计划关闭原因列表")
    @PreAuthorize("@ss.hasPermission('zentao:plan:query')")
    public CommonResult<List<Map<String, String>>> getReasonList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (PlanClosedReasonEnum item : PlanClosedReasonEnum.values()) {
            list.add(Map.of("value", item.getReason(), "label", item.getName()));
        }
        return success(list);
    }

    // ==================== 展示字段 ====================

    private PlanRespVO toRespVO(PlanDO plan) {
        PlanRespVO vo = BeanUtils.toBean(plan, PlanRespVO.class);
        fillRespVO(vo);
        // 详情接口也要给出实时统计，避免前端为了两个数字再发两次请求
        vo.setStoryCount(planService.countStoriesByPlan(plan.getId()));
        vo.setBugCount(planService.countBugsByPlan(plan.getId()));
        return vo;
    }

    /**
     * 补齐：状态文案、待定标记、子计划数、产品名、分支名
     */
    private void fillRespVO(PlanRespVO vo) {
        if (vo == null) {
            return;
        }
        vo.setStatusName(PlanStatusEnum.nameOf(vo.getStatus()));
        vo.setFuture(PlanDO.FUTURE_DATE.equals(vo.getBegin()) && PlanDO.FUTURE_DATE.equals(vo.getEnd()));
        vo.setChildCount(vo.getParent() != null && PlanDO.PARENT_HAS_CHILDREN.equals(vo.getParent())
                ? Long.valueOf(planService.getPlanChildrenCount(vo.getId())) : 0L);
        if (vo.getProduct() != null) {
            ProductDO product = productService.getProduct(vo.getProduct());
            vo.setProductName(product != null ? product.getName() : null);
        }
        vo.setBranchName(branchNames(vo.getProduct(), vo.getBranch()));
    }

    /**
     * 需求数：一次查询把本页所有计划的需求捞回来，再按逗号列表拆分计数，避免 N+1
     */
    private void fillStoryCount(List<PlanRespVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> planIds = list.stream().map(PlanRespVO::getId).filter(java.util.Objects::nonNull).toList();
        Map<Long, Long> counts = planService.countStoriesByPlans(planIds);
        for (PlanRespVO vo : list) {
            vo.setStoryCount(counts.getOrDefault(vo.getId(), 0L));
            vo.setBugCount(planService.countBugsByPlan(vo.getId()));
        }
    }

    /**
     * branch 是逗号列表，翻译成「政务云平台,企业版」这样的中文名；0 显示为「主干」
     */
    private String branchNames(Long product, String branch) {
        if (product == null || !org.springframework.util.StringUtils.hasText(branch)) {
            return null;
        }
        if ("0".equals(branch.trim())) {
            return "主干";
        }
        Map<Long, String> idToName = branchService.getBranchListByProduct(product, null).stream()
                .filter(b -> b.getId() != null)
                .collect(Collectors.toMap(BranchDO::getId, BranchDO::getName, (a, b) -> a));
        return java.util.Arrays.stream(branch.split(","))
                .map(String::trim)
                .filter(org.springframework.util.StringUtils::hasText)
                .map(id -> idToName.getOrDefault(Long.valueOf(id), "#" + id))
                .collect(Collectors.joining(","));
    }

}

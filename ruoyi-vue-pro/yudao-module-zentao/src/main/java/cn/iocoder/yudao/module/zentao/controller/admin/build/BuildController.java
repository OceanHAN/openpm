package cn.iocoder.yudao.module.zentao.controller.admin.build;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.build.BuildDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.enums.product.ProductTypeEnum;
import cn.iocoder.yudao.module.zentao.service.branch.BranchService;
import cn.iocoder.yudao.module.zentao.service.build.BuildService;
import cn.iocoder.yudao.module.zentao.service.product.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 构建 Controller
 *
 * <p>构建是「一次打包」：记录本次完成的需求与解决的 Bug，是「Bug 在哪个版本修好」的锚点
 * （{@code zt_bug.resolvedBuild} 存的就是构建编号），也是发布（release）的输入。
 */
@Tag(name = "管理后台 - 禅道构建")
@RestController
@RequestMapping("/zentao/build")
@Validated
public class BuildController {

    @Resource
    private BuildService buildService;

    @Resource
    private ProductService productService;

    @Resource
    private BranchService branchService;

    @Resource
    private ProjectMapper projectMapper;

    @PostMapping("/create")
    @Operation(summary = "创建构建", description = "多分支产品必须选分支；集成构建的 execution 固定为 0、branch 取子构建并集")
    @PreAuthorize("@ss.hasPermission('zentao:build:create')")
    public CommonResult<Long> createBuild(@Valid @RequestBody BuildSaveReqVO createReqVO) {
        return success(buildService.createBuild(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改构建", description = "已被集成构建/发布引用的构建不能改产品、执行、子构建")
    @PreAuthorize("@ss.hasPermission('zentao:build:update')")
    public CommonResult<Boolean> updateBuild(@Valid @RequestBody BuildSaveReqVO updateReqVO) {
        buildService.updateBuild(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除构建")
    @Parameter(name = "id", description = "构建编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:build:delete')")
    public CommonResult<Boolean> deleteBuild(@RequestParam("id") Long id) {
        buildService.deleteBuild(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得构建", description = "集成构建会把子构建的需求/Bug 合并返回")
    @Parameter(name = "id", description = "构建编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:build:query')")
    public CommonResult<BuildRespVO> getBuild(@RequestParam("id") Long id) {
        return success(toRespVO(buildService.getBuild(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得构建分页")
    @PreAuthorize("@ss.hasPermission('zentao:build:query')")
    public CommonResult<PageResult<BuildRespVO>> getBuildPage(@Valid BuildPageReqVO pageReqVO) {
        PageResult<BuildDO> pageResult = buildService.getBuildPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, BuildRespVO.class, this::fillRespVO));
    }

    @GetMapping("/list-by-product")
    @Operation(summary = "获得产品下的构建列表", description = "用于缺陷「解决版本」下拉")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @Parameter(name = "branch", description = "分支/平台，可选")
    @PreAuthorize("@ss.hasPermission('zentao:build:query')")
    public CommonResult<List<BuildRespVO>> getBuildListByProduct(@RequestParam("product") Long product,
                                                                 @RequestParam(value = "branch", required = false) Long branch) {
        return success(BeanUtils.toBean(buildService.getBuildListByProduct(product, branch),
                BuildRespVO.class, this::fillRespVO));
    }

    @GetMapping("/list-by-execution")
    @Operation(summary = "获得执行下的构建列表")
    @Parameter(name = "execution", description = "执行编号", required = true, example = "90001")
    @PreAuthorize("@ss.hasPermission('zentao:build:query')")
    public CommonResult<List<BuildRespVO>> getBuildListByExecution(@RequestParam("execution") Long execution) {
        return success(BeanUtils.toBean(buildService.getBuildListByExecution(execution),
                BuildRespVO.class, this::fillRespVO));
    }

    @GetMapping("/story-list")
    @Operation(summary = "获得构建下的需求", description = "集成构建返回子构建的需求并集")
    @Parameter(name = "build", description = "构建编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:build:query')")
    public CommonResult<List<StoryRespVO>> getBuildStoryList(@RequestParam("build") Long build) {
        return success(BeanUtils.toBean(buildService.getBuildStories(build), StoryRespVO.class));
    }

    @GetMapping("/unlinked-story-list")
    @Operation(summary = "获得还没关联到该构建的需求候选")
    @Parameter(name = "build", description = "构建编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:build:query')")
    public CommonResult<List<StoryRespVO>> getUnlinkedStoryList(@RequestParam("build") Long build) {
        return success(BeanUtils.toBean(buildService.getUnlinkedStories(build), StoryRespVO.class));
    }

    @GetMapping("/bug-list")
    @Operation(summary = "获得构建下的 Bug")
    @Parameter(name = "build", description = "构建编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:build:query')")
    public CommonResult<List<BugRespVO>> getBuildBugList(@RequestParam("build") Long build) {
        return success(BeanUtils.toBean(buildService.getBuildBugs(build), BugRespVO.class));
    }

    @GetMapping("/unlinked-bug-list")
    @Operation(summary = "获得还没关联到该构建的 Bug 候选")
    @Parameter(name = "build", description = "构建编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:build:query')")
    public CommonResult<List<BugRespVO>> getUnlinkedBugList(@RequestParam("build") Long build) {
        return success(BeanUtils.toBean(buildService.getUnlinkedBugs(build), BugRespVO.class));
    }

    @PutMapping("/link-story")
    @Operation(summary = "关联需求到构建")
    @PreAuthorize("@ss.hasPermission('zentao:build:update')")
    public CommonResult<Boolean> linkStory(@Valid @RequestBody BuildLinkReqVO reqVO) {
        buildService.linkStories(reqVO.getBuild(), reqVO.getIds());
        return success(true);
    }

    @DeleteMapping("/unlink-story")
    @Operation(summary = "从构建移除需求")
    @Parameter(name = "build", description = "构建编号", required = true, example = "1")
    @Parameter(name = "story", description = "需求编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:build:update')")
    public CommonResult<Boolean> unlinkStory(@RequestParam("build") Long build,
                                             @RequestParam("story") Long story) {
        buildService.unlinkStory(build, story);
        return success(true);
    }

    @PutMapping("/link-bug")
    @Operation(summary = "关联 Bug 到构建",
            description = "未解决的 Bug 会被自动置为「已解决」（resolution=fixed、resolvedBuild=本构建），并指派回创建人")
    @PreAuthorize("@ss.hasPermission('zentao:build:update')")
    public CommonResult<Boolean> linkBug(@Valid @RequestBody BuildLinkReqVO reqVO) {
        buildService.linkBugs(reqVO.getBuild(), reqVO.getIds(), reqVO.getResolvedBy());
        return success(true);
    }

    @DeleteMapping("/unlink-bug")
    @Operation(summary = "从构建移除 Bug", description = "不会回退 Bug 的解决状态（与禅道一致）")
    @Parameter(name = "build", description = "构建编号", required = true, example = "1")
    @Parameter(name = "bug", description = "Bug 编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:build:update')")
    public CommonResult<Boolean> unlinkBug(@RequestParam("build") Long build,
                                           @RequestParam("bug") Long bug) {
        buildService.unlinkBug(build, bug);
        return success(true);
    }

    // ==================== 展示字段 ====================

    private BuildRespVO toRespVO(BuildDO build) {
        BuildRespVO vo = BeanUtils.toBean(build, BuildRespVO.class);
        fillRespVO(vo);
        return vo;
    }

    /**
     * 补齐：集成构建标记、产品/执行/分支名称、子构建名、需求与 Bug 数量
     */
    private void fillRespVO(BuildRespVO vo) {
        if (vo == null) {
            return;
        }
        List<String> buildIds = split(vo.getBuilds());
        vo.setIntegrated(vo.getExecution() != null && vo.getExecution() == 0 && !buildIds.isEmpty());
        vo.setStoryCount((long) split(vo.getStories()).size());
        vo.setBugCount((long) split(vo.getBugs()).size());
        vo.setChild(buildService.isChildBuild(vo.getId()));

        if (vo.getProduct() != null) {
            ProductDO product = productService.getProduct(vo.getProduct());
            vo.setProductName(product != null ? product.getName() : null);
        }
        if (vo.getExecution() != null && vo.getExecution() > 0) {
            ProjectDO execution = projectMapper.selectById(vo.getExecution());
            vo.setExecutionName(execution != null ? execution.getName() : null);
        }
        vo.setBranchName(branchNames(vo.getProduct(), vo.getBranch()));
        if (!buildIds.isEmpty()) {
            // 按 builds 里记录的顺序返回名字，不能按列表默认排序（否则顺序和 builds 对不上）
            List<Long> ids = buildIds.stream().map(Long::valueOf).toList();
            java.util.Map<Long, String> nameMap = buildService.getBuildListByProduct(vo.getProduct(), null).stream()
                    .collect(Collectors.toMap(BuildDO::getId, BuildDO::getName, (a, b) -> a));
            vo.setBuildNames(ids.stream().map(id -> nameMap.getOrDefault(id, "#" + id)).toList());
        } else {
            vo.setBuildNames(new ArrayList<>());
        }
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(",")).map(String::trim)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
    }

    /**
     * branch 是逗号列表，翻译成中文名；0 显示为「主干」
     */
    private String branchNames(Long product, String branch) {
        if (product == null || !StringUtils.hasText(branch)) {
            return null;
        }
        if ("0".equals(branch.trim())) {
            return "主干";
        }
        Map<Long, String> idToName = branchService.getBranchListByProduct(product, null).stream()
                .filter(b -> b.getId() != null)
                .collect(Collectors.toMap(BranchDO::getId, BranchDO::getName, (a, b) -> a));
        return split(branch).stream()
                .map(id -> idToName.getOrDefault(Long.valueOf(id), "#" + id))
                .collect(Collectors.joining(","));
    }

    /**
     * 产品类型 → 分支/平台文案（前端表单用来决定标签）
     */
    @GetMapping("/branch-label")
    @Operation(summary = "获得产品的分支/平台文案")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:build:query')")
    public CommonResult<Map<String, Object>> getBranchLabel(@RequestParam("product") Long product) {
        ProductDO productDO = productService.getProduct(product);
        boolean enabled = productDO != null && ProductTypeEnum.supportsBranch(productDO.getType());
        return success(Map.of(
                "enabled", enabled,
                "label", productDO == null ? "分支" : ProductTypeEnum.branchNameOf(productDO.getType())));
    }

}

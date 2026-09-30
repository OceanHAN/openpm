package cn.iocoder.yudao.module.zentao.controller.admin.release;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleaseLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.build.BuildDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.release.ReleaseDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.enums.release.ReleaseStatusEnum;
import cn.iocoder.yudao.module.zentao.service.branch.BranchService;
import cn.iocoder.yudao.module.zentao.service.build.BuildService;
import cn.iocoder.yudao.module.zentao.service.product.ProductService;
import cn.iocoder.yudao.module.zentao.service.release.ReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 发布 Controller
 *
 * <p>发布是「对外交付版本」：引用构建与计划，维护三份清单
 * （完成的需求 / 解决的 Bug / 遗留的 Bug）。创建时会自动生成「影子构建」，
 * 发布名在禅道里是**全局唯一**的。
 */
@Tag(name = "管理后台 - 禅道发布")
@RestController
@RequestMapping("/zentao/release")
@Validated
public class ReleaseController {

    @Resource
    private ReleaseService releaseService;

    @Resource
    private ProductService productService;

    @Resource
    private BranchService branchService;

    @Resource
    private BuildService buildService;

    @Resource
    private ProjectMapper projectMapper;

    @PostMapping("/create")
    @Operation(summary = "创建发布",
            description = "自动创建影子构建；选了构建会把构建里的需求/Bug 同步进来；发布名全局唯一")
    @PreAuthorize("@ss.hasPermission('zentao:release:create')")
    public CommonResult<Long> createRelease(@Valid @RequestBody ReleaseSaveReqVO createReqVO) {
        return success(releaseService.createRelease(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改发布", description = "名称/包含构建/日期变化会同步影子构建")
    @PreAuthorize("@ss.hasPermission('zentao:release:update')")
    public CommonResult<Boolean> updateRelease(@Valid @RequestBody ReleaseSaveReqVO updateReqVO) {
        releaseService.updateRelease(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除发布", description = "被别的发布包含时不能删除；删除会连带清掉影子构建与关联关系")
    @Parameter(name = "id", description = "发布编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:release:delete')")
    public CommonResult<Boolean> deleteRelease(@RequestParam("id") Long id) {
        releaseService.deleteRelease(id);
        return success(true);
    }

    @PutMapping("/publish")
    @Operation(summary = "发布", description = "状态置为「已发布」并写入实际发布日期，需求阶段推进到「已发布」")
    @Parameter(name = "id", description = "发布编号", required = true, example = "1")
    @Parameter(name = "releasedDate", description = "实际发布日期，不传取当前时间")
    @PreAuthorize("@ss.hasPermission('zentao:release:update')")
    public CommonResult<Boolean> publishRelease(
            @RequestParam("id") Long id,
            @RequestParam(value = "releasedDate", required = false)
            @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND) LocalDateTime releasedDate) {
        releaseService.publishRelease(id, releasedDate);
        return success(true);
    }

    @PutMapping("/change-status")
    @Operation(summary = "修改发布状态", description = "normal 已发布 / fail 发布失败 / terminate 停止维护 / wait 未开始")
    @Parameter(name = "id", description = "发布编号", required = true, example = "1")
    @Parameter(name = "status", description = "目标状态", required = true, example = "terminate")
    @PreAuthorize("@ss.hasPermission('zentao:release:update')")
    public CommonResult<Boolean> changeStatus(
            @RequestParam("id") Long id,
            @RequestParam("status") String status,
            @RequestParam(value = "releasedDate", required = false)
            @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND) LocalDateTime releasedDate) {
        releaseService.changeStatus(id, status, releasedDate);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得发布")
    @Parameter(name = "id", description = "发布编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:release:query')")
    public CommonResult<ReleaseRespVO> getRelease(@RequestParam("id") Long id) {
        return success(toRespVO(releaseService.getRelease(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得发布分页")
    @PreAuthorize("@ss.hasPermission('zentao:release:query')")
    public CommonResult<PageResult<ReleaseRespVO>> getReleasePage(@Valid ReleasePageReqVO pageReqVO) {
        PageResult<ReleaseDO> pageResult = releaseService.getReleasePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ReleaseRespVO.class, this::fillRespVO));
    }

    @GetMapping("/list-by-product")
    @Operation(summary = "获得产品下的发布列表")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @Parameter(name = "branch", description = "分支/平台，可选")
    @PreAuthorize("@ss.hasPermission('zentao:release:query')")
    public CommonResult<List<ReleaseRespVO>> getReleaseListByProduct(@RequestParam("product") Long product,
                                                                     @RequestParam(value = "branch", required = false) Long branch) {
        return success(BeanUtils.toBean(releaseService.getReleaseListByProduct(product, branch),
                ReleaseRespVO.class, this::fillRespVO));
    }

    @GetMapping("/story-list")
    @Operation(summary = "获得发布下的需求")
    @Parameter(name = "release", description = "发布编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:release:query')")
    public CommonResult<List<StoryRespVO>> getReleaseStoryList(@RequestParam("release") Long release) {
        return success(BeanUtils.toBean(releaseService.getReleaseStories(release), StoryRespVO.class));
    }

    @GetMapping("/unlinked-story-list")
    @Operation(summary = "获得还没关联到该发布的需求候选")
    @Parameter(name = "release", description = "发布编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:release:query')")
    public CommonResult<List<StoryRespVO>> getUnlinkedStoryList(@RequestParam("release") Long release) {
        return success(BeanUtils.toBean(releaseService.getUnlinkedStories(release), StoryRespVO.class));
    }

    @GetMapping("/bug-list")
    @Operation(summary = "获得发布下的 Bug", description = "type=bug 本次解决 / leftBug 遗留")
    @Parameter(name = "release", description = "发布编号", required = true, example = "1")
    @Parameter(name = "type", description = "bug / leftBug", example = "bug")
    @PreAuthorize("@ss.hasPermission('zentao:release:query')")
    public CommonResult<List<BugRespVO>> getReleaseBugList(@RequestParam("release") Long release,
                                                           @RequestParam(value = "type", required = false) String type) {
        return success(BeanUtils.toBean(releaseService.getReleaseBugs(release, type), BugRespVO.class));
    }

    @GetMapping("/unlinked-bug-list")
    @Operation(summary = "获得还没关联到该发布的 Bug 候选")
    @Parameter(name = "release", description = "发布编号", required = true, example = "1")
    @Parameter(name = "type", description = "bug / leftBug", example = "bug")
    @PreAuthorize("@ss.hasPermission('zentao:release:query')")
    public CommonResult<List<BugRespVO>> getUnlinkedBugList(@RequestParam("release") Long release,
                                                            @RequestParam(value = "type", required = false) String type) {
        return success(BeanUtils.toBean(releaseService.getUnlinkedBugs(release, type), BugRespVO.class));
    }

    @PutMapping("/link-story")
    @Operation(summary = "关联需求到发布", description = "已发布的发布会把需求阶段推进到「已发布」")
    @PreAuthorize("@ss.hasPermission('zentao:release:update')")
    public CommonResult<Boolean> linkStory(@Valid @RequestBody ReleaseLinkReqVO reqVO) {
        releaseService.linkStories(reqVO.getRelease(), reqVO.getIds());
        return success(true);
    }

    @DeleteMapping("/unlink-story")
    @Operation(summary = "从发布移除需求")
    @Parameter(name = "release", description = "发布编号", required = true, example = "1")
    @Parameter(name = "story", description = "需求编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:release:update')")
    public CommonResult<Boolean> unlinkStory(@RequestParam("release") Long release,
                                             @RequestParam("story") Long story) {
        releaseService.unlinkStory(release, story);
        return success(true);
    }

    @PutMapping("/link-bug")
    @Operation(summary = "关联 Bug 到发布", description = "type=bug 本次解决 / leftBug 遗留")
    @PreAuthorize("@ss.hasPermission('zentao:release:update')")
    public CommonResult<Boolean> linkBug(@Valid @RequestBody ReleaseLinkReqVO reqVO) {
        releaseService.linkBugs(reqVO.getRelease(), reqVO.getType(), reqVO.getIds());
        return success(true);
    }

    @DeleteMapping("/unlink-bug")
    @Operation(summary = "从发布移除 Bug")
    @Parameter(name = "release", description = "发布编号", required = true, example = "1")
    @Parameter(name = "type", description = "bug / leftBug", example = "bug")
    @Parameter(name = "bug", description = "Bug 编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:release:update')")
    public CommonResult<Boolean> unlinkBug(@RequestParam("release") Long release,
                                           @RequestParam(value = "type", required = false) String type,
                                           @RequestParam("bug") Long bug) {
        releaseService.unlinkBug(release, type, bug);
        return success(true);
    }

    // ==================== 展示字段 ====================

    private ReleaseRespVO toRespVO(ReleaseDO release) {
        ReleaseRespVO vo = BeanUtils.toBean(release, ReleaseRespVO.class);
        fillRespVO(vo);
        return vo;
    }

    private void fillRespVO(ReleaseRespVO vo) {
        if (vo == null) {
            return;
        }
        vo.setStatusName(ReleaseStatusEnum.nameOf(vo.getStatus()));
        vo.setStoryCount((long) split(vo.getStories()).size());
        vo.setBugCount((long) split(vo.getBugs()).size());
        vo.setLeftBugCount((long) split(vo.getLeftBugs()).size());
        vo.setIncluded(releaseService.isIncludedByOther(vo.getId()));

        if (vo.getProduct() != null) {
            ProductDO product = productService.getProduct(vo.getProduct());
            vo.setProductName(product != null ? product.getName() : null);
        }
        vo.setBranchName(branchNames(vo.getProduct(), vo.getBranch()));
        vo.setBuildNames(buildNames(vo.getBuild()));
        vo.setProjectNames(projectNames(vo.getProject()));
    }

    private List<String> buildNames(String buildIds) {
        List<String> ids = split(buildIds);
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> nameMap = new java.util.HashMap<>();
        for (String id : ids) {
            BuildDO build = buildService.getBuild(Long.valueOf(id));
            nameMap.put(build.getId(), build.getName());
        }
        return ids.stream().map(id -> nameMap.getOrDefault(Long.valueOf(id), "#" + id)).collect(Collectors.toList());
    }

    private List<String> projectNames(String projectIds) {
        List<String> ids = split(projectIds);
        List<String> result = new ArrayList<>();
        for (String id : ids) {
            ProjectDO project = projectMapper.selectById(Long.valueOf(id));
            result.add(project != null ? project.getName() : "#" + id);
        }
        return result;
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(",")).map(String::trim)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
    }

    private String branchNames(Long product, String branch) {
        if (product == null || !StringUtils.hasText(branch)) {
            return null;
        }
        if ("0".equals(branch.replace(",", "").trim())) {
            return "主干";
        }
        Map<Long, String> idToName = branchService.getBranchListByProduct(product, null).stream()
                .filter(b -> b.getId() != null)
                .collect(Collectors.toMap(BranchDO::getId, BranchDO::getName, (a, b) -> a));
        return split(branch).stream()
                .map(id -> idToName.getOrDefault(Long.valueOf(id), "#" + id))
                .collect(Collectors.joining(","));
    }

    @GetMapping("/status-list")
    @Operation(summary = "获得发布状态列表")
    @PreAuthorize("@ss.hasPermission('zentao:release:query')")
    public CommonResult<List<Map<String, String>>> getStatusList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (ReleaseStatusEnum item : ReleaseStatusEnum.values()) {
            list.add(Map.of("value", item.getStatus(), "label", item.getName()));
        }
        return success(list);
    }

}

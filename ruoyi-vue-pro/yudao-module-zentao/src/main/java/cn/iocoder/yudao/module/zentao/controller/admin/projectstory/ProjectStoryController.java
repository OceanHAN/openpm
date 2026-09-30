package cn.iocoder.yudao.module.zentao.controller.admin.projectstory;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectProductLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectProductRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectStoryLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectStoryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.service.projectstory.ProjectStoryService;
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
 * 项目/执行需求范围 Controller
 *
 * <p>对应禅道的「项目需求」（projectstory）视图：先给项目关联产品，再把产品下的需求纳入项目范围。
 * 关联关系里会记录**需求当时的版本** —— 需求后续变更不影响已排期的内容，
 * 列表里会把「版本已变更」标出来。
 */
@Tag(name = "管理后台 - 禅道项目需求范围")
@RestController
@RequestMapping("/zentao/projectstory")
@Validated
public class ProjectStoryController {

    @Resource
    private ProjectStoryService projectStoryService;

    @PostMapping("/link-product")
    @Operation(summary = "项目关联产品", description = "关联后该产品的需求才会出现在「可关联需求」候选里")
    @PreAuthorize("@ss.hasPermission('zentao:projectstory:update')")
    public CommonResult<Long> linkProduct(@Valid @RequestBody ProjectProductLinkReqVO reqVO) {
        return success(projectStoryService.linkProduct(reqVO));
    }

    @DeleteMapping("/unlink-product")
    @Operation(summary = "解除项目与产品的关联", description = "产品下还有需求在范围内时拒绝")
    @Parameter(name = "project", description = "项目/执行编号", required = true, example = "1")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @Parameter(name = "branch", description = "分支/平台", example = "0")
    @PreAuthorize("@ss.hasPermission('zentao:projectstory:update')")
    public CommonResult<Boolean> unlinkProduct(@RequestParam("project") Long project,
                                               @RequestParam("product") Long product,
                                               @RequestParam(value = "branch", required = false) Long branch) {
        projectStoryService.unlinkProduct(project, product, branch);
        return success(true);
    }

    @GetMapping("/product-list")
    @Operation(summary = "获得项目关联的产品列表")
    @Parameter(name = "project", description = "项目/执行编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:projectstory:query')")
    public CommonResult<List<ProjectProductRespVO>> getProductList(@RequestParam("project") Long project) {
        return success(projectStoryService.getLinkedProducts(project));
    }

    @PutMapping("/link-story")
    @Operation(summary = "批量关联需求到项目/执行",
            description = "已关联的跳过；draft/reviewing/closed 状态的需求跳过；记录关联时的需求版本")
    @PreAuthorize("@ss.hasPermission('zentao:projectstory:update')")
    public CommonResult<List<Long>> linkStory(@Valid @RequestBody ProjectStoryLinkReqVO reqVO) {
        return success(projectStoryService.linkStories(reqVO.getProject(), reqVO.getStoryIds()));
    }

    @DeleteMapping("/unlink-story")
    @Operation(summary = "从项目/执行移除需求",
            description = "项目上移除时，若子执行已关联该需求则拒绝；移除后剩余关系重新编号")
    @Parameter(name = "project", description = "项目/执行编号", required = true, example = "1")
    @Parameter(name = "story", description = "需求编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:projectstory:delete')")
    public CommonResult<Boolean> unlinkStory(@RequestParam("project") Long project,
                                             @RequestParam("story") Long story) {
        projectStoryService.unlinkStory(project, story);
        return success(true);
    }

    @GetMapping("/story-list")
    @Operation(summary = "获得项目/执行下的需求", description = "带「版本已变更」标记与关联的项目列表")
    @Parameter(name = "project", description = "项目/执行编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:projectstory:query')")
    public CommonResult<List<ProjectStoryRespVO>> getStoryList(@RequestParam("project") Long project) {
        return success(projectStoryService.getProjectStories(project));
    }

    @GetMapping("/unlinked-story-list")
    @Operation(summary = "获得还没纳入项目范围的需求候选", description = "来自项目已关联的产品，且状态允许")
    @Parameter(name = "project", description = "项目/执行编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:projectstory:query')")
    public CommonResult<List<StoryRespVO>> getUnlinkedStoryList(@RequestParam("project") Long project) {
        return success(BeanUtils.toBean(projectStoryService.getUnlinkedStories(project), StoryRespVO.class));
    }

    @GetMapping("/story-projects")
    @Operation(summary = "获得需求被哪些项目/执行关联")
    @Parameter(name = "story", description = "需求编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:projectstory:query')")
    public CommonResult<List<Long>> getStoryProjects(@RequestParam("story") Long story) {
        return success(projectStoryService.getRelatedProjects(story));
    }

    @GetMapping("/count")
    @Operation(summary = "获得项目/执行下的需求数量")
    @Parameter(name = "project", description = "项目/执行编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:projectstory:query')")
    public CommonResult<Long> countStories(@RequestParam("project") Long project) {
        return success(projectStoryService.countStories(project));
    }

}

package cn.iocoder.yudao.module.zentao.controller.admin.story;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryChangeReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryCloseReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryReviewRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryReviewStartReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryReviewSubmitReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StorySaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StorySpecRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryTreeNodeRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryTypeRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryReviewDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StorySpecDO;
import cn.iocoder.yudao.module.zentao.service.story.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 需求 Controller
 *
 * 权限标识沿用 {@code zentao:story:xxx} 命名，后续接入前端菜单时
 * 只要在 system_menu 表里插入对应记录即可。
 *
 * 关于两个"修改"接口的区别：
 * <ul>
 *   <li>{@code PUT /update} —— 普通编辑，原地改当前版本内容，版本号不变</li>
 *   <li>{@code PUT /change} —— 正式变更，版本号 +1 并留存历史快照</li>
 * </ul>
 */
@Tag(name = "管理后台 - 需求")
@RestController
@RequestMapping("/zentao/story")
@Validated
public class StoryController {

    @Resource
    private StoryService storyService;

    @PostMapping("/create")
    @Operation(summary = "创建需求")
    @PreAuthorize("@ss.hasPermission('zentao:story:create')")
    public CommonResult<Long> createStory(@Valid @RequestBody StorySaveReqVO createReqVO) {
        return success(storyService.createStory(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改需求（普通编辑，不产生新版本）")
    @PreAuthorize("@ss.hasPermission('zentao:story:update')")
    public CommonResult<Boolean> updateStory(@Valid @RequestBody StorySaveReqVO updateReqVO) {
        storyService.updateStory(updateReqVO);
        return success(true);
    }

    @PutMapping("/change")
    @Operation(summary = "变更需求（正式变更，版本号 +1 并留存历史）")
    @PreAuthorize("@ss.hasPermission('zentao:story:update')")
    public CommonResult<Integer> changeStory(@Valid @RequestBody StoryChangeReqVO reqVO) {
        return success(storyService.changeStory(reqVO));
    }

    @PutMapping("/close")
    @Operation(summary = "关闭需求", description = "关闭原因为 duplicate 时必须指定 duplicateStory")
    @PreAuthorize("@ss.hasPermission('zentao:story:update')")
    public CommonResult<Boolean> closeStory(@Valid @RequestBody StoryCloseReqVO reqVO) {
        storyService.closeStory(reqVO);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活需求")
    @Parameter(name = "id", description = "需求编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:story:update')")
    public CommonResult<Boolean> activateStory(@RequestParam("id") Long id) {
        storyService.activateStory(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除需求")
    @Parameter(name = "id", description = "需求编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:story:delete')")
    public CommonResult<Boolean> deleteStory(@RequestParam("id") Long id) {
        storyService.deleteStory(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除需求")
    @Parameter(name = "ids", description = "需求编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:story:delete')")
    public CommonResult<Boolean> deleteStoryList(@RequestParam("ids") List<Long> ids) {
        storyService.deleteStoryList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得需求", description = "version 传 0 或不传，表示读取当前版本")
    @Parameter(name = "id", description = "需求编号", required = true, example = "1024")
    @Parameter(name = "version", description = "版本号，0 或不传表示当前版本", example = "0")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<StoryRespVO> getStory(@RequestParam("id") Long id,
                                              @RequestParam(value = "version", required = false) Integer version) {
        StoryDO story = storyService.getStoryByVersion(id, version);
        StoryRespVO vo = BeanUtils.toBean(story, StoryRespVO.class);
        // 父需求标题 / 子需求数 / 父需求是否已变更 —— 只有服务层拿得到这些
        storyService.fillParentInfo(java.util.List.of(vo));
        return success(vo);
    }

    @GetMapping("/spec-list")
    @Operation(summary = "获得需求版本历史", description = "按版本号倒序返回该需求的全部历史快照")
    @Parameter(name = "storyId", description = "需求编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<List<StorySpecRespVO>> getStorySpecList(@RequestParam("storyId") Long storyId) {
        List<StorySpecDO> list = storyService.getStorySpecList(storyId);
        return success(BeanUtils.toBean(list, StorySpecRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得需求分页")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<PageResult<StoryRespVO>> getStoryPage(@Valid StoryPageReqVO pageReqVO) {
        PageResult<StoryDO> pageResult = storyService.getStoryPage(pageReqVO);
        PageResult<StoryRespVO> page = BeanUtils.toBean(pageResult, StoryRespVO.class);
        storyService.fillParentInfo(page.getList());
        return success(page);
    }

    @GetMapping("/list-by-product")
    @Operation(summary = "获得某产品下的全部需求")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<List<StoryRespVO>> getStoryListByProduct(@RequestParam("product") Long product) {
        List<StoryDO> list = storyService.getStoryListByProduct(product);
        return success(BeanUtils.toBean(list, StoryRespVO.class));
    }

    // ==================== 需求评审 ====================

    @PutMapping("/start-review")
    @Operation(summary = "提交需求评审", description = "把需求置为评审中，并为指定评审人创建评审记录")
    @PreAuthorize("@ss.hasPermission('zentao:story:update')")
    public CommonResult<Boolean> startReview(@Valid @RequestBody StoryReviewStartReqVO reqVO) {
        storyService.startReview(reqVO);
        return success(true);
    }

    @PutMapping("/review")
    @Operation(summary = "评审表决",
            description = "pass/clarify/revert/reject。全部评审人提交后返回聚合结果并流转状态；"
                    + "还未评完时返回 null")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<String> submitReview(@Valid @RequestBody StoryReviewSubmitReqVO reqVO) {
        return success(storyService.submitReview(reqVO));
    }

    @GetMapping("/review-list")
    @Operation(summary = "获得需求评审情况")
    @Parameter(name = "storyId", description = "需求编号", required = true, example = "1024")
    @Parameter(name = "version", description = "版本号，0 或不传表示当前版本", example = "0")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<StoryReviewRespVO> getStoryReviewList(
            @RequestParam("storyId") Long storyId,
            @RequestParam(value = "version", required = false) Integer version) {
        List<StoryReviewDO> list = storyService.getStoryReviewList(storyId, version);
        StoryReviewRespVO respVO = new StoryReviewRespVO();
        respVO.setReviewers(BeanUtils.toBean(list, StoryReviewRespVO.ReviewerItem.class));
        boolean finished = !list.isEmpty()
                && list.stream().allMatch(r -> r.getResult() != null && !r.getResult().isEmpty());
        respVO.setFinished(finished);
        if (finished) {
            // 全部评完时把聚合结果一并返回，方便前端直接展示
            List<String> results = list.stream().map(StoryReviewDO::getResult).toList();
            respVO.setFinalResult(aggregateOf(results));
        }
        if (!list.isEmpty()) {
            respVO.setStory(list.get(0).getStory());
            respVO.setVersion(list.get(0).getVersion());
        }
        return success(respVO);
    }

    /**
     * 只用于「review-list」把当前结果展示给前端。
     * 真正的状态流转判定在 StoryService 内部完成，此处仅是展示层的同构计算。
     */
    private String aggregateOf(List<String> results) {
        long pass = results.stream().filter("pass"::equals).count();
        if (pass == results.size()) {
            return "pass";
        }
        long majority = results.size() / 2 + 1;
        long clarify = results.stream().filter("clarify"::equals).count();
        long revert = results.stream().filter("revert"::equals).count();
        long reject = results.stream().filter("reject"::equals).count();
        if (clarify >= majority) return "clarify";
        if (revert >= majority) return "revert";
        if (reject >= majority) return "reject";
        if (clarify > 0) return "clarify";
        if (revert > 0) return "revert";
        if (reject > 0) return "reject";
        return null;
    }


    // ==================== 父子需求（分解） ====================

    @GetMapping("/child-list")
    @Operation(summary = "获得某需求分解出来的子需求")
    @Parameter(name = "parentId", description = "父需求编号", required = true, example = "4")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<List<StoryRespVO>> getChildList(@RequestParam("parentId") Long parentId) {
        List<StoryRespVO> list = BeanUtils.toBean(storyService.getChildList(parentId), StoryRespVO.class);
        storyService.fillParentInfo(list);
        return success(list);
    }

    @PostMapping("/subdivide")
    @Operation(summary = "把已有需求挂到父需求下（需求分解）",
            description = "会冻结父需求当前版本；父需求的工时=子需求之和、全部子需求关闭时父需求自动关闭")
    @Parameter(name = "parentId", description = "父需求编号", required = true, example = "4")
    @PreAuthorize("@ss.hasPermission('zentao:story:update')")
    public CommonResult<Integer> subdivide(@RequestParam("parentId") Long parentId,
                                           @RequestBody List<Long> childIds) {
        return success(storyService.subdivide(parentId, childIds));
    }

    @PostMapping("/batch-create-child")
    @Operation(summary = "把一条需求拆成若干子需求",
            description = "只给标题，产品/模块/分支/计划/优先级都从父需求继承；"
                    + "子需求类型按父需求层级推导：业务需求→用户需求→研发需求→研发需求")
    @Parameter(name = "parentId", description = "父需求编号", required = true, example = "4")
    @PreAuthorize("@ss.hasPermission('zentao:story:create')")
    public CommonResult<List<Long>> batchCreateChild(@RequestParam("parentId") Long parentId,
                                                     @RequestBody List<String> titles) {
        return success(storyService.batchCreateChild(parentId, titles));
    }

    // ==================== 需求分层（业务需求 / 用户需求 / 研发需求） ====================

    @GetMapping("/type-list")
    @Operation(summary = "获得需求分层类型字典",
            description = "epic=业务需求 / requirement=用户需求 / story=研发需求。"
                    + "禅道把这三层放在同一张 zt_story 表里、靠 type 区分，"
                    + "对应的 module/epic 与 module/requirement 只是 story 的薄壳")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<List<StoryTypeRespVO>> getTypeList() {
        return success(storyService.getTypeList());
    }

    @GetMapping("/type-summary")
    @Operation(summary = "获得某产品各需求分层类型的数量（需求池页签角标）")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<Map<String, Long>> getTypeSummary(@RequestParam("product") Long product) {
        return success(storyService.getTypeSummary(product));
    }

    @GetMapping("/type-tree")
    @Operation(summary = "获得需求分层树（业务需求 → 用户需求 → 研发需求）")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @Parameter(name = "rootId", description = "只看某一棵子树，不传看整个产品的需求森林", example = "4")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<List<StoryTreeNodeRespVO>> getTypeTree(@RequestParam("product") Long product,
                                                               @RequestParam(value = "rootId", required = false) Long rootId) {
        return success(storyService.getTypeTree(product, rootId));
    }
}

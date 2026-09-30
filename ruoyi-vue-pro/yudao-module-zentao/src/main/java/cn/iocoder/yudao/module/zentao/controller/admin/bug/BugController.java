package cn.iocoder.yudao.module.zentao.controller.admin.bug;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugResolveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.service.bug.BugService;
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
 * 缺陷 Controller
 */
@Tag(name = "管理后台 - 缺陷")
@RestController
@RequestMapping("/zentao/bug")
@Validated
public class BugController {

    @Resource
    private BugService bugService;

    @PostMapping("/create")
    @Operation(summary = "创建缺陷")
    @PreAuthorize("@ss.hasPermission('zentao:bug:create')")
    public CommonResult<Long> createBug(@Valid @RequestBody BugSaveReqVO createReqVO) {
        return success(bugService.createBug(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改缺陷")
    @PreAuthorize("@ss.hasPermission('zentao:bug:update')")
    public CommonResult<Boolean> updateBug(@Valid @RequestBody BugSaveReqVO updateReqVO) {
        bugService.updateBug(updateReqVO);
        return success(true);
    }

    @PutMapping("/resolve")
    @Operation(summary = "解决缺陷",
            description = "解决方案为 duplicate 时必须给 duplicateBug；为 fixed 时必须给 resolvedBuild")
    @PreAuthorize("@ss.hasPermission('zentao:bug:update')")
    public CommonResult<Boolean> resolveBug(@Valid @RequestBody BugResolveReqVO reqVO) {
        bugService.resolveBug(reqVO);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭缺陷", description = "只有已解决的缺陷才能关闭")
    @Parameter(name = "id", description = "缺陷编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:bug:update')")
    public CommonResult<Boolean> closeBug(@RequestParam("id") Long id) {
        bugService.closeBug(id);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "重新激活缺陷", description = "激活次数会自动累加")
    @Parameter(name = "id", description = "缺陷编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:bug:update')")
    public CommonResult<Boolean> activateBug(@RequestParam("id") Long id,
                                             @RequestParam(value = "comment", required = false) String comment) {
        bugService.activateBug(id, comment);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除缺陷")
    @Parameter(name = "id", description = "缺陷编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:bug:delete')")
    public CommonResult<Boolean> deleteBug(@RequestParam("id") Long id) {
        bugService.deleteBug(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除缺陷")
    @Parameter(name = "ids", description = "缺陷编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:bug:delete')")
    public CommonResult<Boolean> deleteBugList(@RequestParam("ids") List<Long> ids) {
        bugService.deleteBugList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得缺陷")
    @Parameter(name = "id", description = "缺陷编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:bug:query')")
    public CommonResult<BugRespVO> getBug(@RequestParam("id") Long id) {
        BugDO bug = bugService.getBug(id);
        return success(BeanUtils.toBean(bug, BugRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得缺陷分页")
    @PreAuthorize("@ss.hasPermission('zentao:bug:query')")
    public CommonResult<PageResult<BugRespVO>> getBugPage(@Valid BugPageReqVO pageReqVO) {
        PageResult<BugDO> pageResult = bugService.getBugPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, BugRespVO.class));
    }

    @GetMapping("/list-by-story")
    @Operation(summary = "获得某需求下的全部缺陷")
    @Parameter(name = "story", description = "需求编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:bug:query')")
    public CommonResult<List<BugRespVO>> getBugListByStory(@RequestParam("story") Long story) {
        List<BugDO> list = bugService.getBugListByStory(story);
        return success(BeanUtils.toBean(list, BugRespVO.class));
    }

}

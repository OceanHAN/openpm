package cn.iocoder.yudao.module.zentao.controller.admin.action;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionDynamicReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTimelineRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTrashPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTrashRespVO;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
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
 * 操作日志 Controller（时间线 / 动态 / 回收站 / 备注）。
 *
 * <p>权限标识 {@code zentao:action:xxx}；时间线沿用业务模块自己的查询权限（前端在业务详情里调）。
 * 对应禅道 {@code module/action/control.php}：{@code comment} / {@code trash} / {@code undelete} /
 * {@code hideOne} / {@code hideAll} / {@code ajaxGetList} / {@code ajaxGetMoreActions}。
 */
@Tag(name = "管理后台 - 操作日志")
@RestController
@RequestMapping("/zentao/action")
@Validated
public class ActionController {

    @Resource
    private ActionService actionService;

    @GetMapping("/list")
    @Operation(summary = "获得对象的操作时间线",
            description = "按时间倒序，附带字段级变更明细与「动作渲染文本」（谁 + 动作 + 字段变化）")
    @Parameter(name = "objectType", description = "对象类型，如 story", required = true, example = "story")
    @Parameter(name = "objectID", description = "对象编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:story:query')")
    public CommonResult<List<ActionTimelineRespVO>> getActionList(@RequestParam("objectType") String objectType,
                                                                  @RequestParam("objectID") Long objectID) {
        return success(actionService.getTimeline(objectType, objectID));
    }

    @GetMapping("/dynamic")
    @Operation(summary = "获得动态（feed）",
            description = "按人 / 周期（today/yesterday/thisWeek/thisMonth/all）/ 产品 / 项目 / 执行过滤")
    @PreAuthorize("@ss.hasPermission('zentao:action:query')")
    public CommonResult<List<ActionTimelineRespVO>> getDynamic(@Valid ActionDynamicReqVO reqVO) {
        return success(actionService.getDynamic(reqVO));
    }

    @PostMapping("/comment")
    @Operation(summary = "给对象发一条备注", description = "本质上就是一条 commented 动作")
    @Parameter(name = "objectType", required = true, example = "story")
    @Parameter(name = "objectID", required = true, example = "1")
    @Parameter(name = "comment", required = true, example = "这条需求先挂起，等接口联调完再排")
    @PreAuthorize("@ss.hasPermission('zentao:action:comment')")
    public CommonResult<Long> comment(@RequestParam("objectType") String objectType,
                                      @RequestParam("objectID") Long objectID,
                                      @RequestParam("comment") String comment) {
        return success(actionService.comment(objectType, objectID, comment));
    }

    @PutMapping("/comment/update")
    @Operation(summary = "修改自己的备注")
    @Parameter(name = "id", description = "备注（动作）编号", required = true, example = "1024")
    @Parameter(name = "comment", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:action:comment')")
    public CommonResult<Boolean> updateComment(@RequestParam("id") Long id,
                                               @RequestParam("comment") String comment) {
        actionService.updateComment(id, comment);
        return success(true);
    }

    // ==================== 回收站 ====================

    @GetMapping("/trash")
    @Operation(summary = "获得回收站分页",
            description = "列出所有「删除」动作（排除已隐藏的），并回查对象名称与能否还原")
    @PreAuthorize("@ss.hasPermission('zentao:action:query')")
    public CommonResult<PageResult<ActionTrashRespVO>> getTrashPage(@Valid ActionTrashPageReqVO reqVO) {
        return success(actionService.getTrashPage(reqVO));
    }

    @PostMapping("/undelete")
    @Operation(summary = "从回收站还原",
            description = "把对象表里的 deleted 置回 0，并记一条 undeleted 动作；对象类型要在白名单里")
    @Parameter(name = "id", description = "删除动作的日志编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:action:undelete')")
    public CommonResult<Map<String, Object>> undelete(@RequestParam("id") Long id) {
        return success(actionService.undelete(id));
    }

    @PostMapping("/hide")
    @Operation(summary = "从回收站隐藏（对象保持删除状态）")
    @Parameter(name = "id", description = "删除动作的日志编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('zentao:action:undelete')")
    public CommonResult<Boolean> hide(@RequestParam("id") Long id) {
        actionService.hide(id);
        return success(true);
    }

    @PostMapping("/hide-all")
    @Operation(summary = "隐藏全部可还原的删除记录")
    @PreAuthorize("@ss.hasPermission('zentao:action:undelete')")
    public CommonResult<Integer> hideAll() {
        return success(actionService.hideAll());
    }

}

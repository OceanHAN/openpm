package cn.iocoder.yudao.module.zentao.controller.admin.kanban;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo.*;
import cn.iocoder.yudao.module.zentao.service.kanban.KanbanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 看板 Controller。
 *
 * <p>权限标识 {@code zentao:kanban:xxx}。七层聚合的每一层都有独立端点：
 * 空间 → 看板 → 区域 → 泳道 / 列 → 卡片，位置由 {@code zt_kanbancell.cards} 维护。
 * 看板视图用 {@code GET /data} 一次取回「区域 → 泳道 × 列 + 卡片」。
 */
@Tag(name = "管理后台 - 看板")
@RestController
@RequestMapping("/zentao/kanban")
@Validated
public class KanbanController {

    @Resource
    private KanbanService kanbanService;

    // ==================== 空间 ====================

    @PostMapping("/space/create")
    @Operation(summary = "新建看板空间")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:create')")
    public CommonResult<Long> createSpace(@Valid @RequestBody KanbanSpaceSaveReqVO reqVO) {
        return success(kanbanService.createSpace(reqVO));
    }

    @PutMapping("/space/update")
    @Operation(summary = "修改看板空间")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> updateSpace(@Valid @RequestBody KanbanSpaceSaveReqVO reqVO) {
        kanbanService.updateSpace(reqVO);
        return success(true);
    }

    @DeleteMapping("/space/delete")
    @Operation(summary = "删除看板空间", description = "空间下还有看板时拒绝删除")
    @Parameter(name = "id", required = true, example = "96001")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:delete')")
    public CommonResult<Boolean> deleteSpace(@RequestParam("id") Long id) {
        kanbanService.deleteSpace(id);
        return success(true);
    }

    @PutMapping("/space/activate")
    @Operation(summary = "激活看板空间")
    @Parameter(name = "id", required = true, example = "96001")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> activateSpace(@RequestParam("id") Long id) {
        kanbanService.activateSpace(id);
        return success(true);
    }

    @PutMapping("/space/close")
    @Operation(summary = "关闭看板空间")
    @Parameter(name = "id", required = true, example = "96001")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> closeSpace(@RequestParam("id") Long id) {
        kanbanService.closeSpace(id);
        return success(true);
    }

    @GetMapping("/space/get")
    @Operation(summary = "获得看板空间")
    @Parameter(name = "id", required = true, example = "96001")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<KanbanSpaceRespVO> getSpace(@RequestParam("id") Long id) {
        return success(kanbanService.getSpace(id));
    }

    @GetMapping("/space/page")
    @Operation(summary = "获得看板空间分页")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<PageResult<KanbanSpaceRespVO>> getSpacePage(@Valid KanbanSpacePageReqVO reqVO) {
        return success(kanbanService.getSpacePage(reqVO));
    }

    @GetMapping("/space/list")
    @Operation(summary = "获得看板空间列表（下拉用）")
    @Parameter(name = "type", description = "空间类型：private / cooperation / public")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<List<KanbanSpaceRespVO>> getSpaceList(
            @RequestParam(value = "type", required = false) String type) {
        return success(kanbanService.getSpaceList(type));
    }

    // ==================== 看板 ====================

    @PostMapping("/create")
    @Operation(summary = "新建看板",
            description = "自动建默认布局：默认区域 + 分组 + 默认泳道 + 四个默认列（未开始/进行中/已完成/已关闭）+ 所有格子")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:create')")
    public CommonResult<Long> createKanban(@Valid @RequestBody KanbanSaveReqVO reqVO) {
        return success(kanbanService.createKanban(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改看板")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> updateKanban(@Valid @RequestBody KanbanSaveReqVO reqVO) {
        kanbanService.updateKanban(reqVO);
        return success(true);
    }

    @PutMapping("/setting")
    @Operation(summary = "修改看板设置",
            description = "卡片显示数量 / 是否显示在制品数量 / 流式布局与列宽 / 对齐方式")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> updateKanbanSetting(@Valid @RequestBody KanbanSaveReqVO reqVO) {
        kanbanService.updateKanbanSetting(reqVO);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活看板")
    @Parameter(name = "id", required = true, example = "96101")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> activateKanban(@RequestParam("id") Long id) {
        kanbanService.activateKanban(id);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭看板")
    @Parameter(name = "id", required = true, example = "96101")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> closeKanban(@RequestParam("id") Long id) {
        kanbanService.closeKanban(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除看板", description = "级联软删区域/泳道/列/卡片，并物理清理格子")
    @Parameter(name = "id", required = true, example = "96101")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:delete')")
    public CommonResult<Boolean> deleteKanban(@RequestParam("id") Long id) {
        kanbanService.deleteKanban(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得看板")
    @Parameter(name = "id", required = true, example = "96101")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<KanbanRespVO> getKanban(@RequestParam("id") Long id) {
        return success(kanbanService.getKanban(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得看板分页")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<PageResult<KanbanRespVO>> getKanbanPage(@Valid KanbanPageReqVO reqVO) {
        return success(kanbanService.getKanbanPage(reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "获得某个空间的看板列表")
    @Parameter(name = "space", required = true, example = "96001")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<List<KanbanRespVO>> getKanbanList(@RequestParam("space") Long space) {
        return success(kanbanService.getKanbanList(space));
    }

    @GetMapping("/data")
    @Operation(summary = "获得看板视图数据（区域 → 泳道 × 列 + 卡片）",
            description = "WIP 超限只在 overWip 里提示（禅道也是界面标红，不阻止移动）")
    @Parameter(name = "kanbanId", required = true, example = "96101")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<KanbanDataRespVO> getKanbanData(@RequestParam("kanbanId") Long kanbanId) {
        return success(kanbanService.getKanbanData(kanbanId));
    }

    // ==================== 区域 ====================

    @PostMapping("/region/create")
    @Operation(summary = "新建看板区域", description = "区域自带默认分组 + 默认泳道 + 四个默认列")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:create')")
    public CommonResult<Long> createRegion(@Valid @RequestBody KanbanRegionSaveReqVO reqVO) {
        return success(kanbanService.createRegion(reqVO));
    }

    @PutMapping("/region/update")
    @Operation(summary = "修改看板区域")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> updateRegion(@Valid @RequestBody KanbanRegionSaveReqVO reqVO) {
        kanbanService.updateRegion(reqVO);
        return success(true);
    }

    @DeleteMapping("/region/delete")
    @Operation(summary = "删除看板区域", description = "级联软删区域下的泳道/列/卡片")
    @Parameter(name = "id", required = true, example = "96201")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:delete')")
    public CommonResult<Boolean> deleteRegion(@RequestParam("id") Long id) {
        kanbanService.deleteRegion(id);
        return success(true);
    }

    @GetMapping("/region/list")
    @Operation(summary = "获得看板的区域列表")
    @Parameter(name = "kanban", required = true, example = "96101")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<List<KanbanRegionRespVO>> getRegionList(@RequestParam("kanban") Long kanban) {
        return success(kanbanService.getRegionList(kanban));
    }

    // ==================== 泳道 ====================

    @PostMapping("/lane/create")
    @Operation(summary = "新建看板泳道", description = "会在该分组的每个列下补建格子")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:create')")
    public CommonResult<Long> createLane(@Valid @RequestBody KanbanLaneSaveReqVO reqVO) {
        return success(kanbanService.createLane(reqVO));
    }

    @PutMapping("/lane/update")
    @Operation(summary = "修改看板泳道")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> updateLane(@Valid @RequestBody KanbanLaneSaveReqVO reqVO) {
        kanbanService.updateLane(reqVO);
        return success(true);
    }

    @DeleteMapping("/lane/delete")
    @Operation(summary = "删除看板泳道")
    @Parameter(name = "id", required = true, example = "96301")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:delete')")
    public CommonResult<Boolean> deleteLane(@RequestParam("id") Long id) {
        kanbanService.deleteLane(id);
        return success(true);
    }

    @GetMapping("/lane/list")
    @Operation(summary = "获得区域的泳道列表")
    @Parameter(name = "region", required = true, example = "96201")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<List<KanbanLaneRespVO>> getLaneList(@RequestParam("region") Long region) {
        return success(kanbanService.getLaneList(region));
    }

    // ==================== 列 ====================

    @PostMapping("/column/create")
    @Operation(summary = "新建看板列",
            description = "limit=-1 表示不限；子列的 WIP 之和不能超过父列限额，且父列有限额时子列不能不限")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:create')")
    public CommonResult<Long> createColumn(@Valid @RequestBody KanbanColumnSaveReqVO reqVO) {
        return success(kanbanService.createColumn(reqVO));
    }

    @PutMapping("/column/update")
    @Operation(summary = "修改看板列（含设置 WIP）")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> updateColumn(@Valid @RequestBody KanbanColumnSaveReqVO reqVO) {
        kanbanService.updateColumn(reqVO);
        return success(true);
    }

    @PutMapping("/column/archive")
    @Operation(summary = "归档看板列")
    @Parameter(name = "id", required = true, example = "96401")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> archiveColumn(@RequestParam("id") Long id) {
        kanbanService.archiveColumn(id);
        return success(true);
    }

    @PutMapping("/column/restore")
    @Operation(summary = "还原看板列")
    @Parameter(name = "id", required = true, example = "96401")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> restoreColumn(@RequestParam("id") Long id) {
        kanbanService.restoreColumn(id);
        return success(true);
    }

    @DeleteMapping("/column/delete")
    @Operation(summary = "删除看板列", description = "物理删除（禅道原样）；已拆分的父列不能删")
    @Parameter(name = "id", required = true, example = "96401")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:delete')")
    public CommonResult<Boolean> deleteColumn(@RequestParam("id") Long id) {
        kanbanService.deleteColumn(id);
        return success(true);
    }

    @GetMapping("/column/list")
    @Operation(summary = "获得分组下的看板列")
    @Parameter(name = "group", required = true, example = "96251")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<List<KanbanColumnRespVO>> getColumnList(@RequestParam("group") Long group) {
        return success(kanbanService.getColumnList(group));
    }

    // ==================== 卡片 ====================

    @PostMapping("/card/create")
    @Operation(summary = "新建看板卡片", description = "卡片的位置记在 泳道×列 的格子里（追加到末尾）")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:create')")
    public CommonResult<Long> createCard(@Valid @RequestBody KanbanCardSaveReqVO reqVO) {
        return success(kanbanService.createCard(reqVO));
    }

    @PutMapping("/card/update")
    @Operation(summary = "修改看板卡片")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> updateCard(@Valid @RequestBody KanbanCardSaveReqVO reqVO) {
        kanbanService.updateCard(reqVO);
        return success(true);
    }

    @GetMapping("/card/get")
    @Operation(summary = "获得看板卡片")
    @Parameter(name = "id", required = true, example = "96501")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<KanbanCardRespVO> getCard(@RequestParam("id") Long id) {
        return success(kanbanService.getCard(id));
    }

    @GetMapping("/card/page")
    @Operation(summary = "获得看板卡片分页")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:query')")
    public CommonResult<PageResult<KanbanCardRespVO>> getCardPage(@Valid KanbanCardPageReqVO reqVO) {
        return success(kanbanService.getCardPage(reqVO));
    }

    @PostMapping("/card/move")
    @Operation(summary = "移动看板卡片（泳道 × 列）",
            description = "先从同类型泳道的所有格子里摘掉，再追加到目标格子；card.group 跟着目标泳道走")
    @Parameter(name = "cardId", required = true, example = "96501")
    @Parameter(name = "fromColumnId", required = true, example = "96402")
    @Parameter(name = "toColumnId", required = true, example = "96403")
    @Parameter(name = "fromLaneId", required = true, example = "96301")
    @Parameter(name = "toLaneId", required = true, example = "96301")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> moveCard(@RequestParam("cardId") Long cardId,
                                         @RequestParam("fromColumnId") Long fromColumnId,
                                         @RequestParam("toColumnId") Long toColumnId,
                                         @RequestParam("fromLaneId") Long fromLaneId,
                                         @RequestParam("toLaneId") Long toLaneId) {
        kanbanService.moveCard(cardId, fromColumnId, toColumnId, fromLaneId, toLaneId);
        return success(true);
    }

    @PutMapping("/card/finish")
    @Operation(summary = "完成卡片（进度 100 + 已完成）")
    @Parameter(name = "id", required = true, example = "96501")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> finishCard(@RequestParam("id") Long id) {
        kanbanService.finishCard(id);
        return success(true);
    }

    @PutMapping("/card/activate")
    @Operation(summary = "激活卡片（进度 0~99）")
    @Parameter(name = "id", required = true, example = "96501")
    @Parameter(name = "progress", description = "进度，必须是 0~99（100 请用完成卡片）", example = "30")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> activateCard(@RequestParam("id") Long id,
                                             @RequestParam(value = "progress", required = false) BigDecimal progress) {
        kanbanService.activateCard(id, progress);
        return success(true);
    }

    @PutMapping("/card/archive")
    @Operation(summary = "归档卡片")
    @Parameter(name = "id", required = true, example = "96501")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> archiveCard(@RequestParam("id") Long id) {
        kanbanService.archiveCard(id);
        return success(true);
    }

    @PutMapping("/card/restore")
    @Operation(summary = "还原卡片")
    @Parameter(name = "id", required = true, example = "96501")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:update')")
    public CommonResult<Boolean> restoreCard(@RequestParam("id") Long id) {
        kanbanService.restoreCard(id);
        return success(true);
    }

    @DeleteMapping("/card/delete")
    @Operation(summary = "删除卡片", description = "物理删除（禅道原样），并从所有格子里摘掉")
    @Parameter(name = "id", required = true, example = "96501")
    @PreAuthorize("@ss.hasPermission('zentao:kanban:delete')")
    public CommonResult<Boolean> deleteCard(@RequestParam("id") Long id) {
        kanbanService.deleteCard(id);
        return success(true);
    }

}

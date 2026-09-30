package cn.iocoder.yudao.module.zentao.service.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo.*;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 看板 Service 接口。
 *
 * <p>看板是七层聚合：空间 → 看板 → 区域 → 分组 → 泳道 / 列 → 卡片，
 * 卡片的位置存在 {@code zt_kanbancell.cards}（泳道 × 列 的逗号列表）。详见 README 3.33。
 */
public interface KanbanService {

    // ==================== 空间 ====================

    Long createSpace(KanbanSpaceSaveReqVO reqVO);

    void updateSpace(KanbanSpaceSaveReqVO reqVO);

    void deleteSpace(Long id);

    void activateSpace(Long id);

    void closeSpace(Long id);

    KanbanSpaceDO validateSpaceExists(Long id);

    KanbanSpaceRespVO getSpace(Long id);

    PageResult<KanbanSpaceRespVO> getSpacePage(KanbanSpacePageReqVO reqVO);

    List<KanbanSpaceRespVO> getSpaceList(String type);

    // ==================== 看板 ====================

    /**
     * 新建看板。会**自动建一套默认布局**：默认区域 + 分组 + 默认泳道 + 四个默认列 + 所有格子
     * （禅道 {@code model.php:create} → {@code createDefaultRegion}）
     */
    Long createKanban(KanbanSaveReqVO reqVO);

    void updateKanban(KanbanSaveReqVO reqVO);

    /** 看板设置：卡片显示数量 / 是否显示 WIP / 流式布局与列宽 / 对齐（禅道 kanban/setting） */
    void updateKanbanSetting(KanbanSaveReqVO reqVO);

    void activateKanban(Long id);

    void closeKanban(Long id);

    /** 删除看板：级联逻辑删除区域/泳道/列/卡片，并物理清理格子（禅道只软删看板本体） */
    void deleteKanban(Long id);

    KanbanDO validateKanbanExists(Long id);

    KanbanRespVO getKanban(Long id);

    PageResult<KanbanRespVO> getKanbanPage(KanbanPageReqVO reqVO);

    List<KanbanRespVO> getKanbanList(Long space);

    /** 看板视图数据：区域 → 泳道 × 列 + 卡片（含 overWip 提示） */
    KanbanDataRespVO getKanbanData(Long kanbanId);

    // ==================== 区域 ====================

    Long createRegion(KanbanRegionSaveReqVO reqVO);

    void updateRegion(KanbanRegionSaveReqVO reqVO);

    void deleteRegion(Long id);

    KanbanRegionDO validateRegionExists(Long id);

    List<KanbanRegionRespVO> getRegionList(Long kanban);

    // ==================== 泳道 ====================

    Long createLane(KanbanLaneSaveReqVO reqVO);

    void updateLane(KanbanLaneSaveReqVO reqVO);

    void deleteLane(Long id);

    KanbanLaneDO validateLaneExists(Long id);

    List<KanbanLaneRespVO> getLaneList(Long region);

    // ==================== 列 ====================

    Long createColumn(KanbanColumnSaveReqVO reqVO);

    void updateColumn(KanbanColumnSaveReqVO reqVO);

    void archiveColumn(Long id);

    void restoreColumn(Long id);

    void deleteColumn(Long id);

    KanbanColumnDO validateColumnExists(Long id);

    List<KanbanColumnRespVO> getColumnList(Long group);

    // ==================== 卡片 ====================

    Long createCard(KanbanCardSaveReqVO reqVO);

    void updateCard(KanbanCardSaveReqVO reqVO);

    KanbanCardDO validateCardExists(Long id);

    KanbanCardRespVO getCard(Long id);

    PageResult<KanbanCardRespVO> getCardPage(KanbanCardPageReqVO reqVO);

    /** 移动卡片：从同区域所有「同类型泳道」的格子里摘掉，追加到目标格子（禅道 model.php:moveCard） */
    void moveCard(Long cardId, Long fromColumnId, Long toColumnId, Long fromLaneId, Long toLaneId);

    /** 完成卡片：progress=100 + status=done */
    void finishCard(Long id);

    /** 激活卡片：status=doing + 进度（0~99，100 属于「完成」） */
    void activateCard(Long id, BigDecimal progress);

    void archiveCard(Long id);

    void restoreCard(Long id);

    /** 删除卡片：**物理删除**（禅道 control 里就是 dao->delete），并从所有格子里摘掉 */
    void deleteCard(Long id);

}

package cn.iocoder.yudao.module.zentao.service.action;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionDynamicReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTimelineRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTrashPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTrashRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.action.ActionDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.action.HistoryDO;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 操作日志 Service 接口
 *
 * 对应禅道 {@code module/action/model.php}。业务侧只需要调用两个方法：
 * <ul>
 *   <li>{@link #recordAction} —— 只记一条动作（如「关闭需求」）</li>
 *   <li>{@link #recordActionWithChanges} —— 记动作 + 字段级差异（如「编辑需求」）</li>
 * </ul>
 */
public interface ActionService {

    /**
     * 记录一次操作
     *
     * @param objectType 对象类型，如 story
     * @param objectID   对象编号
     * @param action     动作类型
     * @param comment    备注
     * @return 操作日志编号
     */
    Long recordAction(String objectType, Long objectID, ActionTypeEnum action, String comment);

    /**
     * 记录一次操作，并附加字段级变更明细
     *
     * @param objectType 对象类型
     * @param objectID   对象编号
     * @param action     动作类型
     * @param comment    备注
     * @param oldObj     变更前的对象
     * @param newObj     用于更新的对象（只比较非 null 的字段）
     * @return 操作日志编号
     */
    Long recordActionWithChanges(String objectType, Long objectID, ActionTypeEnum action,
                                 String comment, Object oldObj, Object newObj);

    /**
     * 获得对象的操作时间线，最新在前
     *
     * @param objectType 对象类型
     * @param objectID   对象编号
     * @return 操作日志列表
     */
    List<ActionDO> getActionList(String objectType, Long objectID);

    /**
     * 批量取多个操作的字段变更明细，供时间线一次性加载，避免 N+1 查询
     *
     * @param actionIds 操作日志编号集合
     * @return actionId → 变更明细列表
     */
    Map<Long, List<HistoryDO>> getHistoryMap(Collection<Long> actionIds);

    // ==================== 时间线 / 动态（动作渲染） ====================

    /**
     * 对象操作时间线（含「动作渲染文本」）：谁 + 动作 + 字段变化
     */
    List<ActionTimelineRespVO> getTimeline(String objectType, Long objectID);

    /**
     * 动态（feed）：按人 / 周期 / 产品 / 项目 / 执行过滤，最新在前
     */
    List<ActionTimelineRespVO> getDynamic(ActionDynamicReqVO reqVO);

    // ==================== 回收站 ====================

    /**
     * 回收站分页：列出所有「删除」动作（排除已隐藏的），并回查对象名称 / 能否还原
     */
    PageResult<ActionTrashRespVO> getTrashPage(ActionTrashPageReqVO reqVO);

    /**
     * 还原：把对象表里的 {@code deleted} 置回 0，并记一条 {@code undeleted} 动作
     *
     * @return 还原的对象信息（objectType / objectID / objectName）
     */
    Map<String, Object> undelete(Long actionId);

    /** 从回收站隐藏（对象保持删除状态），并记一条 {@code hidden} 动作 */
    void hide(Long actionId);

    /** 隐藏全部可还原的删除记录，返回条数 */
    int hideAll();

    // ==================== 备注 ====================

    /** 对某个对象发一条备注（就是一条 {@code commented} 动作） */
    Long comment(String objectType, Long objectID, String comment);

    /** 修改自己的备注 */
    void updateComment(Long actionId, String comment);

    /**
     * 动作渲染：把一条 action（+ 字段变化）拼成一句人话（禅道 renderAction + renderChanges）
     */
    String renderAction(cn.iocoder.yudao.module.zentao.dal.dataobject.action.ActionDO action,
                        List<cn.iocoder.yudao.module.zentao.dal.dataobject.action.HistoryDO> histories);

}

package cn.iocoder.yudao.module.zentao.service.action;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionDynamicReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTimelineRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTrashPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.action.vo.ActionTrashRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.action.ActionDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.action.ActionMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.action.ActionRestoreMapper;
import cn.iocoder.yudao.module.zentao.dal.dataobject.action.HistoryDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.action.ActionMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.action.HistoryMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作日志 Service 实现
 *
 * 对应禅道 {@code module/action/model.php} 的 create() + logHistory()。
 */
@Slf4j
@Service
public class ActionServiceImpl implements ActionService {

    /**
     * 操作日志的对象类型常量。禅道用模块名（story / task / bug ...），这里保持一致
     */
    public static final String OBJECT_TYPE_STORY = "story";

    /**
     * 用例库。与用例集（testsuite）、用例（case）区分开的第三个对象类型 ——
     * 三者共用 zt_testsuite / zt_case，靠对象类型把操作日志分开
     */
    public static final String OBJECT_TYPE_CASELIB = "caselib";

    /**
     * 看板族的对象类型。看板是七层聚合，禅道给每一层都单独记 action
     * （{@code kanbanSpace} / {@code kanban} / {@code kanbanRegion} / {@code kanbanLane} /
     * {@code kanbanColumn} / {@code kanbanCard}），这里保持一致，便于按层看操作日志。
     */
    public static final String OBJECT_TYPE_KANBAN_SPACE = "kanbanSpace";
    public static final String OBJECT_TYPE_KANBAN = "kanban";
    public static final String OBJECT_TYPE_KANBAN_REGION = "kanbanRegion";
    public static final String OBJECT_TYPE_KANBAN_LANE = "kanbanLane";
    public static final String OBJECT_TYPE_KANBAN_COLUMN = "kanbanColumn";
    public static final String OBJECT_TYPE_KANBAN_CARD = "kanbanCard";

    @Resource
    private ActionMapper actionMapper;

    @Resource
    private HistoryMapper historyMapper;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private ActionObjectMap actionObjectMap;

    @Resource
    private ActionRestoreMapper actionRestoreMapper;

    @Override
    public Long recordAction(String objectType, Long objectID, ActionTypeEnum action, String comment) {
        return recordActionWithChanges(objectType, objectID, action, comment, null, null);
    }

    @Override
    public Long recordActionWithChanges(String objectType, Long objectID, ActionTypeEnum action,
                                        String comment, Object oldObj, Object newObj) {
        ActionDO actionDO = new ActionDO();
        actionDO.setObjectType(objectType);
        actionDO.setObjectID(objectID);
        actionDO.setActor(currentAccount());
        actionDO.setAction(action.getAction());
        actionDO.setDate(LocalDateTime.now());
        actionDO.setComment(comment);
        actionDO.setReadFlag(0);
        actionDO.setEfforted(0);
        actionDO.setVision("rnd");
        actionMapper.insert(actionDO);

        // 写字段级变更明细（对应禅道 logHistory）
        if (oldObj != null && newObj != null) {
            List<HistoryDO> changes = ChangeDetector.detect(oldObj, newObj);
            for (HistoryDO change : changes) {
                change.setAction(actionDO.getId());
                change.setCreator(actionDO.getActor());
                change.setUpdater(actionDO.getActor());
                historyMapper.insert(change);
            }
        }
        return actionDO.getId();
    }

    @Override
    public List<ActionDO> getActionList(String objectType, Long objectID) {
        return actionMapper.selectListByObject(objectType, objectID);
    }

    @Override
    public Map<Long, List<HistoryDO>> getHistoryMap(Collection<Long> actionIds) {
        if (actionIds == null || actionIds.isEmpty()) {
            return Map.of();
        }
        List<HistoryDO> all = historyMapper.selectListByActions(actionIds);
        // 用 LinkedHashMap 保持数据库返回的顺序，前端按顺序渲染差异
        return all.stream().collect(Collectors.groupingBy(
                HistoryDO::getAction, LinkedHashMap::new, Collectors.toList()));
    }

    // ================================================================
    // 时间线 / 动态（动作渲染）
    // ================================================================

    @Override
    public List<ActionTimelineRespVO> getTimeline(String objectType, Long objectID) {
        return buildTimeline(actionMapper.selectListByObject(objectType, objectID));
    }

    @Override
    public List<ActionTimelineRespVO> getDynamic(ActionDynamicReqVO reqVO) {
        LocalDateTime begin = periodBegin(reqVO.getPeriod());
        List<ActionDO> actions = actionMapper.selectDynamic(reqVO.getActor(), begin,
                reqVO.getProduct() == null ? null : String.valueOf(reqVO.getProduct()),
                reqVO.getProject(), reqVO.getExecution(),
                reqVO.getLimit() == null ? 50 : reqVO.getLimit());
        return buildTimeline(actions);
    }

    /** 周期 → 起始时间（禅道的 period 也是这几个值） */
    private LocalDateTime periodBegin(String period) {
        if (!StringUtils.hasText(period) || "all".equals(period)) {
            return null;
        }
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "today" -> today.atStartOfDay();
            case "yesterday" -> today.minusDays(1).atStartOfDay();
            case "thisWeek" -> today.with(DayOfWeek.MONDAY).atStartOfDay();
            case "thisMonth" -> today.withDayOfMonth(1).atStartOfDay();
            default -> null;
        };
    }

    /** 把 action + history 组装成时间线（一次性取变更明细，避免 N+1） */
    private List<ActionTimelineRespVO> buildTimeline(List<ActionDO> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        List<Long> actionIds = actions.stream().map(ActionDO::getId).toList();
        Map<Long, List<HistoryDO>> historyMap = getHistoryMap(actionIds);
        List<ActionTimelineRespVO> result = new ArrayList<>(actions.size());
        for (ActionDO action : actions) {
            List<HistoryDO> histories = historyMap.getOrDefault(action.getId(), List.of());
            ActionTimelineRespVO vo = new ActionTimelineRespVO();
            vo.setId(action.getId());
            vo.setObjectType(action.getObjectType());
            vo.setObjectID(action.getObjectID());
            vo.setActor(action.getActor());
            vo.setAction(action.getAction());
            ActionTypeEnum type = ActionTypeEnum.of(action.getAction());
            vo.setActionName(type != null ? type.getName() : action.getAction());
            vo.setDate(action.getDate());
            vo.setComment(action.getComment());
            vo.setRenderedDesc(renderAction(action, histories));

            List<ActionTimelineRespVO.HistoryItem> items = new ArrayList<>(histories.size());
            for (HistoryDO history : histories) {
                ActionTimelineRespVO.HistoryItem item = new ActionTimelineRespVO.HistoryItem();
                item.setField(history.getField());
                item.setOldValue(history.getOldValue());
                item.setNewValue(history.getNewValue());
                item.setDiff(history.getDiff());
                items.add(item);
            }
            vo.setHistories(items);
            result.add(vo);
        }
        return result;
    }

    /**
     * 动作渲染：把一条 action（+ 字段变化）拼成一句人话。
     *
     * <p>对应禅道 {@code action/model.php#renderAction} + {@code renderChanges}：
     * 有字段变化时渲染成「admin 编辑：状态 激活 → 已关闭；优先级 3 → 1」，
     * 没有变化时退回「admin 关闭 需求 #1」这种简写。
     */
    @Override
    public String renderAction(ActionDO action, List<HistoryDO> histories) {
        StringBuilder text = new StringBuilder();
        if (StringUtils.hasText(action.getActor())) {
            text.append(action.getActor()).append(' ');
        }
        ActionTypeEnum type = ActionTypeEnum.of(action.getAction());
        text.append(type != null ? type.getName() : action.getAction());
        if (histories != null && !histories.isEmpty()) {
            text.append("：");
            List<String> parts = new ArrayList<>(histories.size());
            for (HistoryDO history : histories) {
                String oldValue = StringUtils.hasText(history.getOldValue()) ? history.getOldValue() : "（空）";
                String newValue = StringUtils.hasText(history.getNewValue()) ? history.getNewValue() : "（空）";
                parts.add(history.getField() + " " + oldValue + " → " + newValue);
            }
            text.append(String.join("；", parts));
        } else if (ActionTypeEnum.COMMENTED.getAction().equals(action.getAction())
                && StringUtils.hasText(action.getComment())) {
            // 备注动作的「内容」就是 comment，这里带上更自然；其余动作的 comment 单独返回，不重复拼
            text.append("：").append(action.getComment());
        }
        return text.toString();
    }

    // ================================================================
    // 回收站
    // ================================================================

    @Override
    public PageResult<ActionTrashRespVO> getTrashPage(ActionTrashPageReqVO reqVO) {
        PageResult<ActionDO> page = actionMapper.selectTrashPage(reqVO.getObjectType(), reqVO.getActor(), reqVO);
        List<ActionTrashRespVO> list = new ArrayList<>(page.getList().size());
        for (ActionDO action : page.getList()) {
            list.add(convertTrash(action));
        }
        return new PageResult<>(list, page.getTotal());
    }

    private ActionTrashRespVO convertTrash(ActionDO action) {
        ActionTrashRespVO vo = new ActionTrashRespVO();
        vo.setActionId(action.getId());
        vo.setObjectType(action.getObjectType());
        ActionObjectMap.Target target = actionObjectMap.find(action.getObjectType());
        vo.setObjectTypeName(actionObjectMap.displayName(action.getObjectType()));
        vo.setObjectID(action.getObjectID());
        vo.setDeletedBy(action.getActor());
        vo.setDeletedDate(action.getDate());
        vo.setComment(action.getComment());
        if (target == null) {
            vo.setCanUndelete(false);
            vo.setReason("对象类型「" + action.getObjectType() + "」不在回收站白名单里，不能还原");
            return vo;
        }
        Map<String, Object> object = actionRestoreMapper.selectObject(target.table(), target.nameColumn(),
                action.getObjectID());
        if (object == null) {
            vo.setCanUndelete(false);
            vo.setReason("对象已经不存在（可能被物理删除）");
            return vo;
        }
        vo.setObjectName(object.get("objectName") == null ? "" : String.valueOf(object.get("objectName")));
        boolean deleted = isDeletedFlag(object.get("deletedFlag"));
        vo.setCanUndelete(deleted);
        vo.setReason(deleted ? "" : "对象已经不是删除状态，无需还原");
        return vo;
    }

    /** {@code deleted} 在库里是 bit(1)，驱动可能给 Boolean / Number / byte[] */
    private boolean isDeletedFlag(Object flag) {
        if (flag == null) {
            return false;
        }
        if (flag instanceof Boolean bool) {
            return bool;
        }
        if (flag instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(flag);
        return "1".equals(text) || "true".equalsIgnoreCase(text) || text.contains("\u0001");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> undelete(Long actionId) {
        ActionDO action = validateTrashAction(actionId);
        ActionObjectMap.Target target = actionObjectMap.require(action.getObjectType());
        Map<String, Object> object = actionRestoreMapper.selectObject(target.table(), target.nameColumn(),
                action.getObjectID());
        if (object == null) {
            throw exception(ACTION_OBJECT_NOT_FOUND, action.getObjectType(), action.getObjectID());
        }
        if (!isDeletedFlag(object.get("deletedFlag"))) {
            throw exception(ACTION_OBJECT_NOT_DELETED, action.getObjectType(), action.getObjectID());
        }
        actionRestoreMapper.restoreObject(target.table(), action.getObjectID());
        // 记一条还原动作（禅道 undelete 成功后也会 create 一条）
        recordAction(action.getObjectType(), action.getObjectID(), ActionTypeEnum.UNDELETED,
                "从回收站还原");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectType", action.getObjectType());
        result.put("objectID", action.getObjectID());
        result.put("objectName", object.get("objectName") == null ? "" : String.valueOf(object.get("objectName")));
        result.put("actionId", action.getId());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hide(Long actionId) {
        ActionDO action = validateTrashAction(actionId);
        actionMapper.hideAction(actionId);
        recordAction(action.getObjectType(), action.getObjectID(), ActionTypeEnum.HIDDEN, "从回收站隐藏");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int hideAll() {
        return actionMapper.hideAllActions();
    }

    /** 回收站操作的公共校验：日志要存在、类型必须是「删除」、且没被隐藏 */
    private ActionDO validateTrashAction(Long actionId) {
        ActionDO action = actionId == null ? null : actionMapper.selectById(actionId);
        if (action == null) {
            throw exception(ACTION_NOT_EXISTS, actionId);
        }
        if (!ActionTypeEnum.DELETED.getAction().equals(action.getAction())) {
            throw exception(ACTION_NOT_DELETED, actionId);
        }
        if (ActionMapper.HIDDEN_FLAG.equals(action.getExtra())) {
            throw exception(ACTION_HIDDEN_CANNOT_UNDELETE);
        }
        return action;
    }

    // ================================================================
    // 备注
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long comment(String objectType, Long objectID, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw exception(ACTION_COMMENT_EMPTY);
        }
        return recordAction(objectType, objectID, ActionTypeEnum.COMMENTED, comment.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateComment(Long actionId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw exception(ACTION_COMMENT_EMPTY);
        }
        ActionDO action = actionId == null ? null : actionMapper.selectById(actionId);
        if (action == null) {
            throw exception(ACTION_COMMENT_NOT_EXISTS, actionId);
        }
        if (!ActionTypeEnum.COMMENTED.getAction().equals(action.getAction())) {
            throw exception(ACTION_COMMENT_NOT_EXISTS, actionId);
        }
        if (!Objects.equals(action.getActor(), currentAccount())) {
            throw exception(ACTION_COMMENT_NOT_OWNER);
        }
        actionMapper.updateComment(actionId, comment.trim());
    }

    /**
     * 取当前登录用户的账号。与 StoryServiceImpl 保持同样的口径：
     * 禅道的 actor 存的是账号，而 yudao 的登录上下文里只有昵称，需要跨模块解析。
     */
    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

}

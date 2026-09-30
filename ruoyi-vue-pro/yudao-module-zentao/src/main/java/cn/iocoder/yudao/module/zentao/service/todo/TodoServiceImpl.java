package cn.iocoder.yudao.module.zentao.service.todo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoAssignReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.todo.TodoDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.todo.TodoMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.todo.TodoStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.todo.TodoTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 待办 Service 实现
 *
 * 业务规则来源：禅道 {@code module/todo/model.php} + {@code module/todo/tao.php}。
 *
 * <h3>三个容易做错的地方</h3>
 * <ol>
 *   <li><b>「我的待办」不是按 account 查</b>：禅道的条件是
 *       {@code assignedTo = 我 OR finishedBy = 我 OR closedBy = 我}
 *       （{@code getListBy}）—— 别人指派给我的、我完成过的、我关闭过的都算
 *       「我的地盘」里的待办；</li>
 *   <li><b>关闭会改指派人</b>：{@code closeTodo} 把 {@code assignedTo} 写成伪用户
 *       {@code 'closed'} 并更新 {@code assignedDate}；激活时再从 {@code finishedBy} 还原
 *       （{@code module/todo/model.php#activate}）；</li>
 *   <li><b>私有待办只对自己可见内容</b>：列表里仍然会出现（因为 assignedTo 是我），
 *       但别人看到的名称会被替换成「这是私有待办」。</li>
 * </ol>
 *
 * 未做：周期待办（cycle=1）不会自动生成下一次；禅道的 {@code feedback} 关联属于企业版功能。
 */
@Slf4j
@Service
public class TodoServiceImpl implements TodoService {

    private static final String OBJECT_TYPE_TODO = "todo";

    /** 禅道关闭待办时把指派人写成这个伪用户 */
    private static final String CLOSED_ACCOUNT = "closed";

    /** 私有待办在别人眼里的名称（对齐禅道 $lang->todo->thisIsPrivate） */
    private static final String PRIVATE_NAME = "这是私有待办";

    @Resource
    private TodoMapper todoMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTodo(TodoSaveReqVO createReqVO) {
        TodoDO todo = buildTodo(createReqVO, true);
        todoMapper.insert(todo);
        actionService.recordAction(OBJECT_TYPE_TODO, todo.getId(), ActionTypeEnum.CREATED, null);
        return todo.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreate(TodoSaveReqVO baseReqVO, List<String> names) {
        List<Long> ids = new ArrayList<>();
        if (names == null) {
            return ids;
        }
        for (String name : names) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            TodoSaveReqVO item = BeanUtils.toBean(baseReqVO, TodoSaveReqVO.class);
            item.setId(null);
            item.setName(name.trim());
            ids.add(createTodo(item));
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTodo(TodoSaveReqVO updateReqVO) {
        TodoDO oldTodo = validateTodoExists(updateReqVO.getId());
        TodoDO updateObj = buildTodo(updateReqVO, false);
        updateObj.setId(oldTodo.getId());
        todoMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_TODO, oldTodo.getId(),
                ActionTypeEnum.EDITED, null, oldTodo, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTodo(Long id) {
        validateTodoExists(id);
        todoMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_TODO, id, ActionTypeEnum.DELETED, null);
    }

    @Override
    public void deleteTodoList(List<Long> ids) {
        ids.forEach(this::deleteTodo);
    }

    // ==================== 状态流转 ====================

    @Override
    public void startTodo(Long id) {
        TodoDO todo = validateTodoExists(id);
        if (!TodoStatusEnum.WAIT.getStatus().equals(todo.getStatus())) {
            throw exception(TODO_STATUS_ILLEGAL, statusName(todo.getStatus()));
        }
        updateStatus(id, TodoStatusEnum.DOING, todo);
    }

    @Override
    public void finishTodo(Long id) {
        TodoDO todo = validateTodoExists(id);
        if (TodoStatusEnum.DONE.getStatus().equals(todo.getStatus())) {
            throw exception(TODO_ALREADY_DONE);
        }
        TodoDO updateObj = new TodoDO();
        updateObj.setId(id);
        updateObj.setStatus(TodoStatusEnum.DONE.getStatus());
        updateObj.setFinishedBy(currentAccount());
        updateObj.setFinishedDate(LocalDateTime.now());
        todoMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_TODO, id,
                ActionTypeEnum.FINISHED, null, todo, updateObj);
    }

    @Override
    public void closeTodo(Long id) {
        TodoDO todo = validateTodoExists(id);
        if (TodoStatusEnum.CLOSED.getStatus().equals(todo.getStatus())) {
            throw exception(TODO_ALREADY_CLOSED);
        }
        // 禅道 closeTodo：assignedTo 置成伪用户 'closed'，assignedDate 更新为当前时间
        TodoDO updateObj = new TodoDO();
        updateObj.setId(id);
        updateObj.setStatus(TodoStatusEnum.CLOSED.getStatus());
        updateObj.setClosedBy(currentAccount());
        updateObj.setClosedDate(LocalDateTime.now());
        updateObj.setAssignedTo(CLOSED_ACCOUNT);
        updateObj.setAssignedDate(LocalDateTime.now());
        todoMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_TODO, id,
                ActionTypeEnum.CLOSED, null, todo, updateObj);
    }

    @Override
    public void activateTodo(Long id) {
        TodoDO todo = validateTodoExists(id);
        if (TodoStatusEnum.WAIT.getStatus().equals(todo.getStatus())) {
            throw exception(TODO_STATUS_ILLEGAL, statusName(todo.getStatus()));
        }
        TodoDO updateObj = new TodoDO();
        updateObj.setId(id);
        updateObj.setStatus(TodoStatusEnum.WAIT.getStatus());
        // 禅道：如果指派人被关闭时改成了 'closed'，激活时还原成完成人
        if (CLOSED_ACCOUNT.equals(todo.getAssignedTo()) && StringUtils.hasText(todo.getFinishedBy())) {
            updateObj.setAssignedTo(todo.getFinishedBy());
            updateObj.setAssignedDate(LocalDateTime.now());
        }
        todoMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_TODO, id,
                ActionTypeEnum.ACTIVATED, null, todo, updateObj);
    }

    @Override
    public void assignTodo(TodoAssignReqVO reqVO) {
        TodoDO todo = validateTodoExists(reqVO.getId());
        if (CLOSED_ACCOUNT.equals(todo.getStatus())) {
            throw exception(TODO_ALREADY_CLOSED);
        }
        TodoDO updateObj = new TodoDO();
        updateObj.setId(todo.getId());
        updateObj.setAssignedTo(reqVO.getAssignedTo());
        updateObj.setAssignedBy(currentAccount());
        updateObj.setAssignedDate(LocalDateTime.now());
        todoMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_TODO, todo.getId(),
                ActionTypeEnum.ASSIGNED, reqVO.getAssignedTo(), todo, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importToToday(String account, boolean all) {
        LocalDate today = LocalDate.now();
        String owner = StringUtils.hasText(account) ? account : currentAccount();
        List<TodoDO> todos = todoMapper.selectListByAccount(owner, "undone");
        int count = 0;
        for (TodoDO todo : todos) {
            // 只挪「今天以前」且没完成的；all=true 时把今天的也算上（重新排一次）
            if (todo.getDate() != null && (todo.getDate().isBefore(today)
                    || (all && todo.getDate().isEqual(today)))) {
                TodoDO updateObj = new TodoDO();
                updateObj.setId(todo.getId());
                updateObj.setDate(today);
                todoMapper.updateById(updateObj);
                count++;
            }
        }
        return count;
    }

    // ==================== 读 ====================

    @Override
    public TodoDO getTodo(Long id) {
        return validateTodoExists(id);
    }

    @Override
    public TodoDO validateTodoExists(Long id) {
        TodoDO todo = id == null ? null : todoMapper.selectById(id);
        if (todo == null) {
            throw exception(TODO_NOT_EXISTS);
        }
        return todo;
    }

    @Override
    public PageResult<TodoDO> getTodoPage(TodoPageReqVO reqVO) {
        return todoMapper.selectPage(reqVO);
    }

    @Override
    public List<TodoDO> getMyTodoList(TodoPageReqVO reqVO, String account) {
        String owner = StringUtils.hasText(account) ? account : currentAccount();
        LocalDate[] range = resolveDateRange(reqVO.getBrowseType());
        boolean assignedToOther = Boolean.TRUE.equals(reqVO.getAssignedToOther());
        List<TodoDO> list = todoMapper.selectMyList(owner, reqVO.getStatus(),
                range == null ? null : range[0], range == null ? null : range[1],
                "cycle".equals(reqVO.getBrowseType()), assignedToOther);
        // 私有待办：**看的人不是它归属人**时只显示「这是私有待办」（禅道 getList 的做法）。
        // 注意两个分支都要判：别人指派给我一条私有待办时，我照样能在列表里看到它，但看不到内容。
        for (TodoDO todo : list) {
            if (todo.getPrivateFlag() != null && todo.getPrivateFlag() == 1
                    && !owner.equals(todo.getAccount())) {
                todo.setName(PRIVATE_NAME);
            }
        }
        return list;
    }

    @Override
    public long countUndone(String account) {
        return todoMapper.selectMyList(account, "undone", null, null, false, false).size();
    }

    @Override
    public long countToday(String account) {
        LocalDate today = LocalDate.now();
        return todoMapper.selectMyList(account, "undone", today, today, false, false).size();
    }

    @Override
    public long countOverdue(String account) {
        LocalDate today = LocalDate.now();
        return todoMapper.selectMyList(account, "undone", null, today.minusDays(1), false, false).size();
    }

    // ==================== 内部 ====================

    /**
     * 禅道的「浏览范围」到日期区间的映射（module/todo/model.php#dateRange）
     */
    private LocalDate[] resolveDateRange(String browseType) {
        LocalDate today = LocalDate.now();
        if (browseType == null || "all".equals(browseType) || "cycle".equals(browseType)) {
            return null;
        }
        switch (browseType) {
            case "today":
                return new LocalDate[]{today, today};
            case "tomorrow":
                return new LocalDate[]{today.plusDays(1), today.plusDays(1)};
            case "thisweek":
                return new LocalDate[]{
                        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))};
            case "before":
                return new LocalDate[]{null, today.minusDays(1)};
            case "future":
                return new LocalDate[]{today.plusDays(1), null};
            default:
                return null;
        }
    }

    /**
     * 组装待办字段。created 为 true 时补默认值（今天/归属账号/指派给自己/未开始）。
     */
    private TodoDO buildTodo(TodoSaveReqVO reqVO, boolean created) {
        TodoDO todo = BeanUtils.toBean(reqVO, TodoDO.class);
        String type = StringUtils.hasText(reqVO.getType()) ? reqVO.getType() : TodoTypeEnum.CUSTOM.getType();
        if (TodoTypeEnum.of(type) == null) {
            throw exception(TODO_TYPE_INVALID, type);
        }
        todo.setType(type);
        if (TodoTypeEnum.needObject(type) && (reqVO.getObjectID() == null || reqVO.getObjectID() <= 0)) {
            throw exception(TODO_OBJECT_REQUIRED, TodoTypeEnum.of(type).getName());
        }
        if (!TodoTypeEnum.needObject(type)) {
            todo.setObjectID(0L);
        }
        if (todo.getDate() == null) {
            todo.setDate(LocalDate.now());
        }
        if (todo.getPri() == null) {
            todo.setPri(3);
        }
        if (todo.getPrivateFlag() == null) {
            todo.setPrivateFlag(0);
        }
        if (todo.getCycle() == null) {
            todo.setCycle(0);
        }
        todo.setBegin(reqVO.getBegin() == null ? "" : reqVO.getBegin());
        todo.setEnd(reqVO.getEnd() == null ? "" : reqVO.getEnd());
        if (created) {
            String operator = currentAccount();
            if (!StringUtils.hasText(todo.getAccount())) {
                todo.setAccount(operator);
            }
            todo.setStatus(TodoStatusEnum.WAIT.getStatus());
            if (!StringUtils.hasText(todo.getAssignedTo())) {
                todo.setAssignedTo(todo.getAccount());
            }
            todo.setAssignedBy(operator);
            todo.setAssignedDate(LocalDateTime.now());
            todo.setFeedback(0L);
            todo.setVision("rnd");
            todo.setFinishedBy("");
            todo.setClosedBy("");
        }
        return todo;
    }

    private void updateStatus(Long id, TodoStatusEnum status, TodoDO oldTodo) {
        TodoDO updateObj = new TodoDO();
        updateObj.setId(id);
        updateObj.setStatus(status.getStatus());
        todoMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_TODO, id,
                ActionTypeEnum.CHANGED, "状态：" + status.getName(), oldTodo, updateObj);
    }

    private String statusName(String status) {
        TodoStatusEnum item = TodoStatusEnum.of(status);
        return item != null ? item.getName() : String.valueOf(status);
    }

    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

}

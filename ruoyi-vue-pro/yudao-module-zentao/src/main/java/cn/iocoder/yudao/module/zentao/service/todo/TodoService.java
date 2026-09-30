package cn.iocoder.yudao.module.zentao.service.todo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoAssignReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.todo.TodoDO;

import java.util.List;

/**
 * 待办 Service
 *
 * 对应禅道 {@code module/todo/}。
 */
public interface TodoService {

    Long createTodo(TodoSaveReqVO createReqVO);

    /** 批量创建（禅道 batchCreate：一次给一批名称，其余字段共用） */
    List<Long> batchCreate(TodoSaveReqVO baseReqVO, List<String> names);

    void updateTodo(TodoSaveReqVO updateReqVO);

    void deleteTodo(Long id);

    void deleteTodoList(List<Long> ids);

    // ==================== 状态流转（禅道 start/finish/close/activate/assignTo） ====================

    void startTodo(Long id);

    void finishTodo(Long id);

    void closeTodo(Long id);

    void activateTodo(Long id);

    void assignTodo(TodoAssignReqVO reqVO);

    /** 把某天之前没完成的待办挪到今天（禅道 import2Today） */
    int importToToday(String account, boolean all);

    // ==================== 读 ====================

    TodoDO getTodo(Long id);

    TodoDO validateTodoExists(Long id);

    PageResult<TodoDO> getTodoPage(TodoPageReqVO reqVO);

    /**
     * 「我的待办」：assignedTo = 我 OR finishedBy = 我 OR closedBy = 我
     */
    List<TodoDO> getMyTodoList(TodoPageReqVO reqVO, String account);

    /** 未完成数量 / 今日数量 / 已过期数量 */
    long countUndone(String account);

    long countToday(String account);

    long countOverdue(String account);

}

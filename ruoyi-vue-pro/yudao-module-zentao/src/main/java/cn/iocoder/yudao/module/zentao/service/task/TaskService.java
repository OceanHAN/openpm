package cn.iocoder.yudao.module.zentao.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskFinishReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 任务 Service 接口
 *
 * <h3>状态机</h3>
 * <pre>
 *   wait(未开始) ──start──> doing(进行中) ──finish──> done(已完成) ──close──> closed(已关闭)
 *        │                      ↑   │                    │
 *        └────────cancel────────┴───┘                    │
 *                               pause(已暂停)             │
 *        closed ──activate─────────────────────────────> doing
 * </pre>
 */
public interface TaskService {

    /**
     * 创建任务。初始状态 wait，consumed 为 0，left 默认等于 estimate
     *
     * @param createReqVO 任务信息
     * @return 任务编号
     */
    Long createTask(TaskSaveReqVO createReqVO);

    /**
     * 修改任务。已关闭/已取消的任务不允许修改
     *
     * @param updateReqVO 任务信息
     */
    void updateTask(TaskSaveReqVO updateReqVO);

    /**
     * 开始任务：wait/pause → doing
     *
     * @param id 任务编号
     */
    void startTask(Long id);

    /**
     * 完成任务。
     * 禅道语义：登记本次消耗工时与剩余工时，**剩余归零才算真正完成**，
     * 否则任务回到「进行中」。
     *
     * @param reqVO 完成信息
     */
    void finishTask(TaskFinishReqVO reqVO);

    /**
     * 关闭任务：只有 done 状态才能关闭
     *
     * @param id     任务编号
     * @param reason 关闭原因
     */
    void closeTask(Long id, String reason);

    /**
     * 取消任务
     *
     * @param id 任务编号
     */
    void cancelTask(Long id);

    /**
     * 激活任务：closed/cancel → doing
     *
     * @param id 任务编号
     */
    void activateTask(Long id);

    /**
     * 删除任务（逻辑删除）
     *
     * @param id 任务编号
     */
    void deleteTask(Long id);

    /**
     * 批量删除任务
     *
     * @param ids 任务编号数组
     */
    void deleteTaskList(List<Long> ids);

    /**
     * 获得任务
     */
    TaskDO getTask(Long id);

    /**
     * 校验任务存在
     */
    TaskDO validateTaskExists(Long id);

    /**
     * 获得任务分页
     */
    PageResult<TaskDO> getTaskPage(TaskPageReqVO reqVO);

    /**
     * 获得某需求下的全部任务
     */
    List<TaskDO> getTaskListByStory(Long story);

    /**
     * 需求转任务：把一个需求拆成若干任务（只给名称，其余从需求与执行继承）。
     *
     * <p>任务必须挂在执行下（任务表里 project/execution 是必填），
     * 所以除了需求编号还要给执行编号 —— 禅道的「需求转任务」也是先选目标执行。
     *
     * @return 任务编号列表
     */
    List<Long> batchCreateFromStory(Long storyId, Long execution, Long project, List<String> names);

    /**
     * 把需求信息补进 VO（需求标题 / 需求是否已变更）
     */
    void fillStoryInfo(List<TaskRespVO> list);

    /**
     * 批量取任务，用于列表回填「所属任务名」等场景
     */
    Map<Long, TaskDO> getTaskMap(Collection<Long> taskIds);


}

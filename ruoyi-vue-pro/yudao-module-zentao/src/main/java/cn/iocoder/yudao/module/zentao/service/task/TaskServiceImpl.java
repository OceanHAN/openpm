package cn.iocoder.yudao.module.zentao.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskFinishReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.enums.story.StoryStatusEnum;
import java.util.ArrayList;
import cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.task.TaskStatusEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.story.StoryService;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.zentao.service.module.ModuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 任务 Service 实现
 *
 * 业务规则来源：禅道 {@code module/task/model.php}。
 *
 * <h3>工时模型</h3>
 * {@code consumed} 是累计值：每次完成任务时把「本次消耗」累加上去，
 * 而不是直接覆盖。{@code left} 用最新登记值覆盖。
 * 三者（estimate / consumed / left）是项目进度与燃尽图的数据源。
 */
@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    /**
     * 操作日志的对象类型，与禅道的模块名保持一致
     */
    private static final String OBJECT_TYPE_TASK = "task";

    @Resource
    private ModuleService moduleService;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private StoryService storyService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 写：CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(TaskSaveReqVO createReqVO) {
        TaskDO task = BeanUtils.toBean(createReqVO, TaskDO.class);
        // 禅道新建任务的初始值：未开始 / 已消耗 0 / 剩余等于预计
        task.setStatus(TaskStatusEnum.WAIT.getStatus());
        task.setVersion(1);
        task.setConsumed(BigDecimal.ZERO);
        if (task.getEstimate() == null) {
            task.setEstimate(BigDecimal.ZERO);
        }
        if (task.getLeft() == null) {
            task.setLeft(task.getEstimate());
        }
        String operator = currentAccount();
        task.setOpenedBy(operator);
        task.setOpenedDate(LocalDateTime.now());
        if (StringUtils.hasText(createReqVO.getAssignedTo())) {
            task.setAssignedDate(LocalDateTime.now());
        }
        // ★ 需求版本冻结：建任务时记下需求当时的版本。
        //   需求后来正式变更，任务不会跟着变，而是提示「需求已变更」由人确认 ——
        //   已经按老需求做完的工作不该被无声改写。
        if (task.getStory() != null && task.getStory() > 0) {
            StoryDO story = storyService.validateStoryExists(task.getStory());
            task.setStoryVersion(story.getVersion() == null ? 1 : story.getVersion());
        } else {
            task.setStoryVersion(0);
        }
        taskMapper.insert(task);

        actionService.recordAction(OBJECT_TYPE_TASK, task.getId(), ActionTypeEnum.CREATED, null);
        if (task.getStory() != null && task.getStory() > 0) {
            actionService.recordAction("story", task.getStory(), ActionTypeEnum.EDITED,
                    "分解出任务 #" + task.getId() + "：" + task.getName());
        }
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(TaskSaveReqVO updateReqVO) {
        TaskDO oldTask = validateTaskExists(updateReqVO.getId());
        checkUpdatable(oldTask);

        TaskDO updateObj = BeanUtils.toBean(updateReqVO, TaskDO.class);
        updateObj.setVersion(oldTask.getVersion() == null ? 1 : oldTask.getVersion() + 1);
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        if (StringUtils.hasText(updateReqVO.getAssignedTo())
                && !updateReqVO.getAssignedTo().equals(oldTask.getAssignedTo())) {
            updateObj.setAssignedDate(LocalDateTime.now());
        }
        taskMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_TASK, oldTask.getId(),
                ActionTypeEnum.EDITED, null, oldTask, updateObj);
    }

    // ==================== 写：状态流转 ====================

    @Override
    public void startTask(Long id) {
        TaskDO task = validateTaskExists(id);
        // 只有未开始 / 已暂停 / 已关闭 / 已取消 可以「开始」
        if (!TaskStatusEnum.WAIT.getStatus().equals(task.getStatus())
                && !TaskStatusEnum.PAUSE.getStatus().equals(task.getStatus())) {
            throw exception(TASK_STATUS_ILLEGAL, statusName(task.getStatus()));
        }

        TaskDO updateObj = new TaskDO();
        updateObj.setId(id);
        updateObj.setStatus(TaskStatusEnum.DOING.getStatus());
        updateObj.setRealStarted(LocalDateTime.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        taskMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_TASK, id,
                ActionTypeEnum.ACTIVATED, "开始任务", task, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishTask(TaskFinishReqVO reqVO) {
        TaskDO oldTask = validateTaskExists(reqVO.getId());
        checkUpdatable(oldTask);
        if (TaskStatusEnum.DONE.getStatus().equals(oldTask.getStatus())) {
            throw exception(TASK_ALREADY_DONE);
        }

        // consumed 是累计值：把本次消耗加上去，而不是覆盖
        BigDecimal totalConsumed = nz(oldTask.getConsumed()).add(nz(reqVO.getConsumed()));
        BigDecimal left = nz(reqVO.getLeft());

        TaskDO updateObj = new TaskDO();
        updateObj.setId(oldTask.getId());
        updateObj.setConsumed(totalConsumed);
        updateObj.setLeft(left);

        // 禅道语义：剩余工时归零才算真正完成；还有剩余则回到「进行中」
        if (left.compareTo(BigDecimal.ZERO) == 0) {
            updateObj.setStatus(TaskStatusEnum.DONE.getStatus());
            updateObj.setFinishedBy(currentAccount());
            updateObj.setFinishedDate(LocalDateTime.now());
        } else {
            updateObj.setStatus(TaskStatusEnum.DOING.getStatus());
        }
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        taskMapper.updateById(updateObj);

        String comment = StringUtils.hasText(reqVO.getComment())
                ? reqVO.getComment()
                : String.format("本次消耗 %s 工时，剩余 %s 工时", reqVO.getConsumed(), left);
        actionService.recordActionWithChanges(OBJECT_TYPE_TASK, oldTask.getId(),
                TaskStatusEnum.DONE.getStatus().equals(updateObj.getStatus())
                        ? ActionTypeEnum.CHANGED : ActionTypeEnum.EDITED,
                comment, oldTask, updateObj);
    }

    @Override
    public void closeTask(Long id, String reason) {
        TaskDO task = validateTaskExists(id);
        if (!TaskStatusEnum.DONE.getStatus().equals(task.getStatus())) {
            throw exception(TASK_NOT_DONE_CANNOT_CLOSE);
        }

        TaskDO updateObj = new TaskDO();
        updateObj.setId(id);
        updateObj.setStatus(TaskStatusEnum.CLOSED.getStatus());
        updateObj.setClosedBy(currentAccount());
        updateObj.setClosedDate(LocalDateTime.now());
        updateObj.setClosedReason(StringUtils.hasText(reason) ? reason : "done");
        taskMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_TASK, id,
                ActionTypeEnum.CLOSED, null, task, updateObj);
    }

    @Override
    public void cancelTask(Long id) {
        TaskDO task = validateTaskExists(id);
        if (TaskStatusEnum.CLOSED.getStatus().equals(task.getStatus())
                || TaskStatusEnum.CANCEL.getStatus().equals(task.getStatus())) {
            throw exception(TASK_STATUS_ILLEGAL, statusName(task.getStatus()));
        }

        TaskDO updateObj = new TaskDO();
        updateObj.setId(id);
        updateObj.setStatus(TaskStatusEnum.CANCEL.getStatus());
        updateObj.setCanceledBy(currentAccount());
        updateObj.setCanceledDate(LocalDateTime.now());
        taskMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_TASK, id,
                ActionTypeEnum.CLOSED, "取消任务", task, updateObj);
    }

    @Override
    public void activateTask(Long id) {
        TaskDO task = validateTaskExists(id);
        if (!TaskStatusEnum.CLOSED.getStatus().equals(task.getStatus())
                && !TaskStatusEnum.CANCEL.getStatus().equals(task.getStatus())) {
            throw exception(TASK_STATUS_ILLEGAL, statusName(task.getStatus()));
        }

        TaskDO updateObj = new TaskDO();
        updateObj.setId(id);
        updateObj.setStatus(TaskStatusEnum.DOING.getStatus());
        updateObj.setActivatedDate(LocalDateTime.now());
        updateObj.setClosedReason("");
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        taskMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_TASK, id,
                ActionTypeEnum.ACTIVATED, null, task, updateObj);
    }

    // ==================== 写：删除 ====================

    @Override
    public void deleteTask(Long id) {
        validateTaskExists(id);
        taskMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_TASK, id, ActionTypeEnum.DELETED, null);
    }

    @Override
    public void deleteTaskList(List<Long> ids) {
        ids.forEach(this::validateTaskExists);
        taskMapper.deleteByIds(ids);
        ids.forEach(id -> actionService.recordAction(OBJECT_TYPE_TASK, id,
                ActionTypeEnum.DELETED, null));
    }

    // ==================== 读 ====================

    @Override
    public TaskDO getTask(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public TaskDO validateTaskExists(Long id) {
        if (id == null) {
            return null;
        }
        TaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(TASK_NOT_EXISTS);
        }
        return task;
    }

    @Override
    public PageResult<TaskDO> getTaskPage(TaskPageReqVO reqVO) {
        // 禅道行为：选中一个父模块时，连带查出它所有子模块下的数据。
        // 做法是把 module 展开成「自己 + 全部子孙」的 id 列表再 IN 查询。
        if (reqVO.getModule() != null) {
            java.util.List<Long> moduleIds = moduleService.getSelfAndDescendantIds(reqVO.getModule());
            if (moduleIds.isEmpty()) {
                // 模块不存在（或已删除）→ 结果必然为空。
                // 注意：这里**不能**直接把空列表交给 inIfPresent —— 空集合会被当成
                // 「没有这个条件」整条丢掉，查询反而退化成「查全部」，这是很隐蔽的错。
                return PageResult.empty();
            }
            reqVO.setModuleIds(moduleIds);
            reqVO.setModule(null);
        }
        return taskMapper.selectPage(reqVO);
    }

    @Override
    public List<TaskDO> getTaskListByStory(Long story) {
        return taskMapper.selectListByStory(story);
    }

    // ==================== 内部 ====================

    /**
     * 已关闭/已取消的任务是终态，不允许再改
     */
    private void checkUpdatable(TaskDO task) {
        if (TaskStatusEnum.CLOSED.getStatus().equals(task.getStatus())) {
            throw exception(TASK_CLOSED_CANNOT_UPDATE);
        }
        if (TaskStatusEnum.CANCEL.getStatus().equals(task.getStatus())) {
            throw exception(TASK_CANCELED_CANNOT_UPDATE);
        }
    }

    private String statusName(String status) {
        TaskStatusEnum item = TaskStatusEnum.of(status);
        return item != null ? item.getName() : String.valueOf(status);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }


    // ==================== 需求转任务 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreateFromStory(Long storyId, Long execution, Long project, List<String> names) {
        StoryDO story = storyService.validateStoryExists(storyId);
        List<Long> ids = new ArrayList<>();
        if (names == null || names.isEmpty()) {
            return ids;
        }
        for (String name : names) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            TaskSaveReqVO reqVO = new TaskSaveReqVO();
            // 任务挂在执行下；模块/优先级从需求继承（需求模块与任务模块是两棵树，
            // 所以这里不硬套需求模块 —— 禅道也是让用户在执行下另选任务模块）
            reqVO.setProject(project);
            reqVO.setExecution(execution);
            reqVO.setPri(story.getPri());
            reqVO.setStory(storyId);
            reqVO.setName(name.trim());
            reqVO.setType("devel");
            ids.add(createTask(reqVO));
        }
        actionService.recordAction("story", storyId, ActionTypeEnum.EDITED,
                "把需求分解成 " + ids.size() + " 个任务");
        return ids;
    }

    @Override
    public void fillStoryInfo(List<TaskRespVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (TaskRespVO vo : list) {
            if (vo.getStory() == null || vo.getStory() <= 0) {
                // 没挂需求的任务也要给个明确的 false，别让前端拿到 null
                vo.setStoryChanged(false);
                continue;
            }
            // ★ 需求可能已经被删除，而任务还挂着旧编号（悬空引用）。
            //   这种情况**绝不能让整个列表接口 500** —— 一个脏引用不该把整页任务打挂，
            //   所以这里容忍「取不到需求」：跳过需求信息即可。
            //   （这也是链路里最常见的脏数据：删需求时没有级联处理挂在它上面的任务/用例）
            StoryDO story;
            try {
                story = storyService.getStory(vo.getStory());
            } catch (RuntimeException ex) {
                story = null;
            }
            if (story == null) {
                vo.setStoryChanged(false);
                continue;
            }
            vo.setStoryTitle(story.getTitle());
            vo.setLatestStoryVersion(story.getVersion());
            // 需求升版且仍是激活态 → 任务需要确认（与 projectstory / 父子需求同思路）
            vo.setStoryChanged(story.getVersion() != null && vo.getStoryVersion() != null
                    && story.getVersion() > vo.getStoryVersion()
                    && StoryStatusEnum.ACTIVE.getStatus().equals(story.getStatus()));
        }
    }

    @Override
    public Map<Long, TaskDO> getTaskMap(Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        // 任务可能已被删除（悬空引用），取不到的就不放进 map，调用方按 null 处理
        return taskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(TaskDO::getId, Function.identity(), (a, b) -> a));
    }
}

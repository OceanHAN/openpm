package cn.iocoder.yudao.module.zentao.service.effort;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortSummaryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortTaskStatRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.effort.EffortDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.effort.EffortMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StoryMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.task.TaskStatusEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.task.TaskService;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.EFFORT_CANNOT_MOVE;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.EFFORT_NOT_EXISTS;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工时明细 Service 实现
 *
 * <h3>为什么是「重算」而不是「增量维护」</h3>
 * 禅道是增量维护的：新增一条就 {@code consumed += x}，删除一条就 {@code consumed -= x}，
 * 然后为「删的是不是最后一条」「是不是删到一条不剩」写一堆特判
 * （{@code getTaskAfterDeleteWorkhour} 里通篇是 if/else）。
 * 这里改成每次改动后<b>从工时流水重算一遍</b>任务的 consumed/left/status：
 * <pre>
 *   consumed = SUM(未删除的工时.consumed)
 *   left     = 最后一条工时的 left（没有工时则回到 estimate）
 * </pre>
 * 结果与禅道一致，但不会因为漏掉某个特判而算错。一个任务的工时通常只有几条到几十条，
 * 重算成本可以忽略；换来的好处是「随时删任意一条都不会算歪」。
 */
@Slf4j
@Service
public class EffortServiceImpl implements EffortService {

    /** 工时对象类型：任务。禅道同一张表也记 story/bug，本实现只落任务 */
    private static final String OBJECT_TYPE_TASK = "task";

    @Resource
    private EffortMapper effortMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private StoryMapper storyMapper;

    @Resource
    private TaskService taskService;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEffort(EffortSaveReqVO createReqVO) {
        TaskDO task = taskService.validateTaskExists(createReqVO.getTaskId());

        EffortDO effort = new EffortDO();
        effort.setObjectType(OBJECT_TYPE_TASK);
        effort.setObjectID(task.getId());
        effort.setProduct(resolveProduct(task));
        effort.setProject(task.getProject());
        effort.setExecution(task.getExecution());
        effort.setAccount(StringUtils.hasText(createReqVO.getAccount())
                ? createReqVO.getAccount() : currentAccount());
        effort.setDate(createReqVO.getDate());
        effort.setConsumed(nvl(createReqVO.getConsumed()));
        effort.setLeft(createReqVO.getLeft() != null ? createReqVO.getLeft() : deduceLeft(task, effort.getConsumed()));
        effort.setWork(createReqVO.getWork());
        effort.setBegin(nvlString(createReqVO.getBegin()));
        effort.setEnd(nvlString(createReqVO.getEnd()));
        effort.setExtra("");
        effort.setOrder(nextOrder(task.getId()));
        effortMapper.insert(effort);

        recomputeTask(task.getId());
        actionService.recordAction(OBJECT_TYPE_TASK, task.getId(), ActionTypeEnum.RECORD_WORKHOUR,
                "消耗 " + effort.getConsumed().toPlainString() + " 小时");
        return effort.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEffort(EffortSaveReqVO updateReqVO) {
        EffortDO oldEffort = validateEffortExists(updateReqVO.getId());
        TaskDO task = taskService.validateTaskExists(updateReqVO.getTaskId());
        // 工时不允许换任务：换了任务意味着要同时重算两个任务，禅道也是按「同一对象内编辑」处理的
        if (!oldEffort.getObjectID().equals(task.getId())) {
            throw exception(EFFORT_CANNOT_MOVE);
        }

        EffortDO updateObj = new EffortDO();
        updateObj.setId(oldEffort.getId());
        updateObj.setDate(updateReqVO.getDate());
        updateObj.setConsumed(nvl(updateReqVO.getConsumed()));
        updateObj.setLeft(updateReqVO.getLeft() != null ? updateReqVO.getLeft() : deduceLeft(task, updateObj.getConsumed()));
        updateObj.setWork(updateReqVO.getWork());
        updateObj.setBegin(nvlString(updateReqVO.getBegin()));
        updateObj.setEnd(nvlString(updateReqVO.getEnd()));
        if (StringUtils.hasText(updateReqVO.getAccount())) {
            updateObj.setAccount(updateReqVO.getAccount());
        }
        effortMapper.updateById(updateObj);

        recomputeTask(task.getId());
        actionService.recordAction(OBJECT_TYPE_TASK, task.getId(), ActionTypeEnum.RECORD_WORKHOUR,
                "修改工时：" + updateObj.getConsumed().toPlainString() + " 小时");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEffort(Long id) {
        EffortDO effort = validateEffortExists(id);
        effortMapper.deleteById(id);
        recomputeTask(effort.getObjectID());
        actionService.recordAction(OBJECT_TYPE_TASK, effort.getObjectID(), ActionTypeEnum.RECORD_WORKHOUR,
                "删除工时：" + nvl(effort.getConsumed()).toPlainString() + " 小时");
    }

    @Override
    public EffortDO getEffort(Long id) {
        return validateEffortExists(id);
    }

    @Override
    public List<EffortDO> getEffortListByTask(Long taskId) {
        return effortMapper.selectListByTask(taskId);
    }

    @Override
    public PageResult<EffortDO> getEffortPage(EffortPageReqVO reqVO) {
        return effortMapper.selectPage(reqVO);
    }

    @Override
    public EffortTaskStatRespVO getTaskStat(Long taskId) {
        TaskDO task = taskService.validateTaskExists(taskId);
        List<EffortDO> efforts = effortMapper.selectListByTask(taskId);

        EffortTaskStatRespVO stat = new EffortTaskStatRespVO();
        stat.setTaskId(task.getId());
        stat.setTaskName(task.getName());
        stat.setEstimate(nvl(task.getEstimate()));
        stat.setConsumed(nvl(task.getConsumed()));
        stat.setLeft(nvl(task.getLeft()));
        stat.setStatus(task.getStatus());
        stat.setEffortCount(efforts.size());
        stat.setEfforts(BeanUtils.toBean(efforts, EffortRespVO.class));
        return stat;
    }

    @Override
    public List<EffortSummaryRespVO> getEffortSummary(EffortPageReqVO reqVO) {
        List<EffortDO> list = effortMapper.selectListByCondition(reqVO);
        Map<String, List<EffortDO>> grouped = list.stream().collect(Collectors.groupingBy(
                e -> StringUtils.hasText(e.getAccount()) ? e.getAccount() : "(未署名)",
                LinkedHashMap::new, Collectors.toList()));

        List<EffortSummaryRespVO> result = new ArrayList<>();
        for (Map.Entry<String, List<EffortDO>> entry : grouped.entrySet()) {
            List<EffortDO> items = entry.getValue();
            Set<Long> tasks = items.stream().map(EffortDO::getObjectID)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            BigDecimal consumed = items.stream().map(e -> nvl(e.getConsumed()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            EffortSummaryRespVO vo = new EffortSummaryRespVO();
            vo.setAccount(entry.getKey());
            vo.setTaskCount(tasks.size());
            vo.setEffortCount(items.size());
            vo.setConsumed(consumed);
            result.add(vo);
        }
        // 谁干得多谁排前面，便于看报表
        result.sort(Comparator.comparing(EffortSummaryRespVO::getConsumed).reversed());
        return result;
    }

    // ==================== 核心：任务工时重算 ====================

    /**
     * 依据工时流水重算任务的 consumed / left / status。
     *
     * <p>规则（对齐禅道 {@code module/task/tao.php#getTaskAfterDeleteWorkhour}）：
     * <ol>
     *   <li>consumed = 所有工时的 consumed 之和；</li>
     *   <li>left = 最后一条工时的 left（按 date、id 排序的最后一条）；</li>
     *   <li>left 归零且状态还在 未开始/进行中/已暂停 → 自动置为已完成；</li>
     *   <li>left 不为零而状态已是已完成 → 退回进行中（还剩活就不算完成）；</li>
     *   <li>工时被删光时（禅道对应「删掉的正是最后一条且 consumed 归零」）：
     *       状态只要不是未开始就退回未开始、剩余回到预计工时，并把
     *       完成人/完成时间、取消人/取消时间、关闭人/关闭时间/关闭原因一并清空 ——
     *       否则任务会停在「已完成」却没有任何工时支撑，绩效统计全是脏数据。</li>
     * </ol>
     *
     * <h3>与禅道的两处出入</h3>
     * <ul>
     *   <li>禅道把「left 归零」无条件写成 done（{@code module/task/tao.php:913} 的裸三元表达式），
     *       <b>连已关闭/已取消的任务都会被改回已完成</b>；这里只对 未开始/进行中/已暂停 生效 ——
     *       删一条工时把已关闭的任务「复活」成已完成，怎么看都是禅道自己的疏漏。</li>
     *   <li>禅道用「被删的是不是 id 最大的那条」判断 isLast，而取上一条的 left 时又按 date、id 排序
     *       （同一个函数里两套口径）。这里统一按 date、id 判断「最后一条」。</li>
     * </ul>
     */
    private void recomputeTask(Long taskId) {
        TaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        List<EffortDO> efforts = effortMapper.selectListByTask(taskId);

        BigDecimal consumed = efforts.stream().map(e -> nvl(e.getConsumed()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal left;
        String status = task.getStatus();
        boolean resetToWait = false;
        if (efforts.isEmpty()) {
            if (!TaskStatusEnum.WAIT.getStatus().equals(status)) {
                // 工时被删光：退回未开始，并抹掉「完成/取消/关闭」留下的痕迹
                status = TaskStatusEnum.WAIT.getStatus();
                left = nvl(task.getEstimate());
                resetToWait = true;
            } else {
                // 本来就是未开始：剩余保持原值，为 0 时回到预计工时
                left = nvl(task.getLeft());
                if (left.signum() == 0) {
                    left = nvl(task.getEstimate());
                }
            }
        } else {
            left = nvl(efforts.get(efforts.size() - 1).getLeft());
            if (left.signum() == 0
                    && isOneOf(status, TaskStatusEnum.WAIT, TaskStatusEnum.DOING, TaskStatusEnum.PAUSE)) {
                status = TaskStatusEnum.DONE.getStatus();
            } else if (left.signum() > 0 && isOneOf(status, TaskStatusEnum.DONE)) {
                status = TaskStatusEnum.DOING.getStatus();
            }
        }

        // 用 UpdateWrapper 显式 set，而不是写一个「只填非空字段」的 DO：
        // MyBatis-Plus 默认忽略 null 字段，那样重置状态时清不掉完成/取消/关闭的时间与人。
        LambdaUpdateWrapper<TaskDO> update = new LambdaUpdateWrapper<>();
        update.eq(TaskDO::getId, taskId)
                .set(TaskDO::getConsumed, consumed)
                .set(TaskDO::getLeft, left)
                .set(TaskDO::getStatus, status)
                .set(TaskDO::getLastEditedBy, currentAccount())
                .set(TaskDO::getLastEditedDate, LocalDateTime.now());
        if (resetToWait) {
            // 注意：finishedBy/canceledBy/closedBy/closedReason 在 zt_task 里是 NOT NULL，
            // 只能清成空串；三个时间列可为 NULL。这里跟禅道 tao.php 的写法一致。
            update.set(TaskDO::getFinishedBy, "")
                    .set(TaskDO::getFinishedDate, null)
                    .set(TaskDO::getCanceledBy, "")
                    .set(TaskDO::getCanceledDate, null)
                    .set(TaskDO::getClosedBy, "")
                    .set(TaskDO::getClosedDate, null)
                    .set(TaskDO::getClosedReason, "");
            // 禅道还有个约定：任务被关闭时会指派给伪用户 closed，回到未开始要改派给操作人。
            // 本实现没有使用这个伪用户，故不处理。
        }
        taskMapper.update(null, update);
    }

    // ==================== 工具 ====================

    /**
     * 没填「剩余工时」时的兜底：按任务当前剩余 - 本次消耗推算，不会小于 0。
     * 禅道的表单里这一项是必填的，这里做成可选，免得 UI 少一个字段就报错。
     */
    private BigDecimal deduceLeft(TaskDO task, BigDecimal consumed) {
        BigDecimal rest = nvl(task.getLeft()).subtract(nvl(consumed));
        return rest.signum() < 0 ? BigDecimal.ZERO : rest;
    }

    /**
     * 取工时所属产品：顺着「任务 -> 需求 -> 产品」找。
     * 禅道这里存的是产品编号列表，拿不到就留空（统计报表按账号汇总，不依赖该字段）。
     */
    private String resolveProduct(TaskDO task) {
        if (task.getStory() == null || task.getStory() <= 0) {
            return "";
        }
        StoryDO story = storyMapper.selectById(task.getStory());
        return story != null && story.getProduct() != null ? String.valueOf(story.getProduct()) : "";
    }

    /**
     * 新工时排在最后。禅道的 order 是为了支持「上移/下移」调整显示顺序。
     */
    private Integer nextOrder(Long taskId) {
        List<EffortDO> efforts = effortMapper.selectListByTask(taskId);
        return efforts.stream().map(e -> e.getOrder() == null ? 0 : e.getOrder())
                .max(Integer::compareTo).orElse(0) + 1;
    }

    private EffortDO validateEffortExists(Long id) {
        EffortDO effort = id == null ? null : effortMapper.selectById(id);
        if (effort == null) {
            throw exception(EFFORT_NOT_EXISTS);
        }
        return effort;
    }

    private boolean isOneOf(String status, TaskStatusEnum... candidates) {
        for (TaskStatusEnum candidate : candidates) {
            if (candidate.getStatus().equals(status)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String nvlString(String value) {
        return value == null ? "" : value;
    }

    /**
     * 取当前登录用户的账号。与 ActionServiceImpl 保持同样的口径：
     * 禅道存的是账号，而 yudao 的登录上下文里只有 userId，需要跨模块解析。
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

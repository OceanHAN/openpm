package cn.iocoder.yudao.module.zentao.service.execution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.execution.ExecutionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.project.ProjectModelEnum;
import cn.iocoder.yudao.module.zentao.enums.project.ProjectStatusEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.team.TeamService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 执行 Service 实现
 *
 * 执行与项目共用 {@code zt_project} 表（禅道 {@code TABLE_EXECUTION} 的定义就是这样），
 * 所有操作都要额外带上 {@code type IN ('sprint','stage','kanban')} 过滤，
 * 并且要防住「拿一个项目 id 当执行操作」的情况 —— 这是共用表最容易出的边界错误。
 */
@Slf4j
@Service
public class ExecutionServiceImpl implements ExecutionService {

    /**
     * 操作日志的对象类型：执行是 {@code execution}，不是 {@code project}。
     *
     * <p>一开始图省事写成了 project（「反正都在 zt_project 里」），但禅道的 demo 数据里
     * 执行的动作是 {@code ('execution', 3, execution=3)}、项目的动作是 {@code ('project', 2, project=2)} ——
     * 两者必须分开，否则：①报表按 objectType 统计贡献与产出时项目/执行会混在一起；
     * ②回收站里「执行」这一类的名字会显示成项目。
     */
    private static final String OBJECT_TYPE_EXECUTION = "execution";

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private TeamService teamService;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createExecution(ExecutionSaveReqVO createReqVO) {
        // 1. 必须指定所属项目
        if (createReqVO.getProject() == null || createReqVO.getProject() <= 0) {
            throw exception(EXECUTION_PROJECT_REQUIRED);
        }
        // 2. 所属项目必须存在，且必须真的是「项目」而不是另一个执行
        ProjectDO parentProject = projectMapper.selectById(createReqVO.getProject());
        if (parentProject == null) {
            throw exception(EXECUTION_PROJECT_NOT_EXISTS, createReqVO.getProject());
        }
        if (!ExecutionTypeEnum.PROJECT.getType().equals(parentProject.getType())) {
            throw exception(EXECUTION_PROJECT_TYPE_INVALID, createReqVO.getProject());
        }
        // 3. 类型必须是执行类型
        if (!ExecutionTypeEnum.isExecution(createReqVO.getType())) {
            throw exception(EXECUTION_TYPE_INVALID,
                    StringUtils.hasText(createReqVO.getType()) ? createReqVO.getType() : "(空)");
        }
        if (StringUtils.hasText(createReqVO.getModel())
                && !ProjectModelEnum.isValid(createReqVO.getModel())) {
            throw exception(PROJECT_MODEL_INVALID, createReqVO.getModel());
        }
        if (createReqVO.getBegin() != null && createReqVO.getEnd() != null
                && createReqVO.getEnd().isBefore(createReqVO.getBegin())) {
            throw exception(PROJECT_END_BEFORE_BEGIN);
        }

        ProjectDO execution = BeanUtils.toBean(createReqVO, ProjectDO.class);
        // 执行没有自己的模型，继承所属项目
        if (!StringUtils.hasText(execution.getModel())) {
            execution.setModel(parentProject.getModel());
        }
        execution.setStatus(ProjectStatusEnum.WAIT.getStatus());
        execution.setIsTpl(0);
        execution.setMilestone(0);
        // 执行的 multiple 必须跟所属项目一致（禅道 project/zen.php 的 multiple 复选框在建项目时定，
        // 建执行时沿用）：multiple=1 表示「多迭代项目下的执行」。
        // 报表的年度执行统计、执行列表的默认过滤（`multiple=1`）都靠这个字段，
        // 不继承就会出现「项目是多迭代、执行却一个都统计不到」。
        execution.setMultiple(parentProject.getMultiple() == null ? 0 : parentProject.getMultiple());
        // 执行的层级：禅道 execution::setTreePath（module/execution/model.php:5004）规定
        //   parent = 所属项目（嵌套阶段时是父阶段），path = ,项目id,执行id,，grade = 1
        // 禅道 demo 数据也是这个形状：sprint 行 project=2 parent=2 path=,2,3, grade=1
        execution.setParent(parentProject.getId());
        execution.setGrade(1);
        if (execution.getEstimate() == null) {
            execution.setEstimate(BigDecimal.ZERO);
        }
        if (execution.getLeft() == null) {
            execution.setLeft(execution.getEstimate());
        }
        execution.setConsumed(BigDecimal.ZERO);
        execution.setProgress(BigDecimal.ZERO);
        if (execution.getBudget() == null) {
            execution.setBudget(BigDecimal.ZERO);
        }
        if (!StringUtils.hasText(execution.getBudgetUnit())) {
            execution.setBudgetUnit("CNY");
        }
        if (!StringUtils.hasText(execution.getAcl())) {
            execution.setAcl("open");
        }
        if (execution.getOrder() == null) {
            execution.setOrder(0);
        }
        String operator = currentAccount();
        execution.setOpenedBy(operator);
        execution.setOpenedDate(LocalDateTime.now());
        projectMapper.insert(execution);

        // path 需要 id，插入后回填（逗号格式，与项目集/项目同一套约定）
        ProjectDO pathUpdate = new ProjectDO();
        pathUpdate.setId(execution.getId());
        pathUpdate.setPath("," + parentProject.getId() + "," + execution.getId() + ",");
        projectMapper.updateById(pathUpdate);

        // 执行成员来自 ownerFields（PO/PM/QD/RD）—— 禅道 module/execution/model.php:598
        teamService.syncOwners(execution);

        actionService.recordAction(OBJECT_TYPE_EXECUTION, execution.getId(), ActionTypeEnum.CREATED,
                "创建执行：" + ExecutionTypeEnum.of(execution.getType()).getName());
        return execution.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExecution(ExecutionSaveReqVO updateReqVO) {
        ProjectDO oldExecution = validateExecutionExists(updateReqVO.getId());
        if (ProjectStatusEnum.CLOSED.getStatus().equals(oldExecution.getStatus())) {
            throw exception(PROJECT_CLOSED_CANNOT_UPDATE);
        }
        if (StringUtils.hasText(updateReqVO.getType()) && !ExecutionTypeEnum.isExecution(updateReqVO.getType())) {
            throw exception(EXECUTION_TYPE_INVALID, updateReqVO.getType());
        }

        ProjectDO updateObj = BeanUtils.toBean(updateReqVO, ProjectDO.class);
        // 不允许改所属项目，避免执行漂移到别的项目下
        updateObj.setProject(null);
        if (StringUtils.hasText(updateReqVO.getTeam())) {
            updateObj.setTeamCount(countTeam(updateReqVO.getTeam()));
        }
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        projectMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_EXECUTION, oldExecution.getId(),
                ActionTypeEnum.EDITED, null, oldExecution, updateObj);
    }

    // ==================== 写：状态流转 ====================

    @Override
    public void startExecution(Long id) {
        ProjectDO execution = validateExecutionExists(id);
        if (!ProjectStatusEnum.WAIT.getStatus().equals(execution.getStatus())
                && !ProjectStatusEnum.SUSPENDED.getStatus().equals(execution.getStatus())) {
            throw exception(PROJECT_STATUS_ILLEGAL, statusName(execution.getStatus()));
        }
        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(id);
        updateObj.setStatus(ProjectStatusEnum.DOING.getStatus());
        updateObj.setRealBegan(LocalDate.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        projectMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_EXECUTION, id,
                ActionTypeEnum.ACTIVATED, "开始执行", execution, updateObj);
    }

    @Override
    public void suspendExecution(Long id) {
        ProjectDO execution = validateExecutionExists(id);
        if (!ProjectStatusEnum.DOING.getStatus().equals(execution.getStatus())) {
            throw exception(PROJECT_STATUS_ILLEGAL, statusName(execution.getStatus()));
        }
        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(id);
        updateObj.setStatus(ProjectStatusEnum.SUSPENDED.getStatus());
        updateObj.setSuspendedDate(LocalDateTime.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        projectMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_EXECUTION, id,
                ActionTypeEnum.CHANGED, "挂起执行", execution, updateObj);
    }

    @Override
    public void activateExecution(Long id) {
        ProjectDO execution = validateExecutionExists(id);
        if (!ProjectStatusEnum.SUSPENDED.getStatus().equals(execution.getStatus())) {
            throw exception(PROJECT_STATUS_ILLEGAL, statusName(execution.getStatus()));
        }
        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(id);
        updateObj.setStatus(ProjectStatusEnum.DOING.getStatus());
        updateObj.setActivatedDate(LocalDateTime.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        projectMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_EXECUTION, id,
                ActionTypeEnum.ACTIVATED, null, execution, updateObj);
    }

    @Override
    public void closeExecution(Long id, String reason) {
        ProjectDO execution = validateExecutionExists(id);
        if (ProjectStatusEnum.CLOSED.getStatus().equals(execution.getStatus())) {
            throw exception(PROJECT_ALREADY_CLOSED);
        }
        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(id);
        updateObj.setStatus(ProjectStatusEnum.CLOSED.getStatus());
        updateObj.setClosedBy(currentAccount());
        updateObj.setClosedDate(LocalDateTime.now());
        updateObj.setClosedReason(StringUtils.hasText(reason) ? reason : "done");
        updateObj.setRealEnd(LocalDate.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        projectMapper.updateById(updateObj);
        actionService.recordActionWithChanges(OBJECT_TYPE_EXECUTION, id,
                ActionTypeEnum.CLOSED, null, execution, updateObj);
    }

    // ==================== 写：删除 ====================

    @Override
    public void deleteExecution(Long id) {
        validateExecutionExists(id);
        projectMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_EXECUTION, id, ActionTypeEnum.DELETED, "删除执行");
    }

    @Override
    public void deleteExecutionList(List<Long> ids) {
        ids.forEach(this::deleteExecution);
    }

    // ==================== 读 ====================

    @Override
    public ProjectDO getExecution(Long id) {
        return validateExecutionExists(id);
    }

    @Override
    public ProjectDO validateExecutionExists(Long id) {
        if (id == null) {
            throw exception(EXECUTION_NOT_EXISTS);
        }
        ProjectDO execution = projectMapper.selectById(id);
        if (execution == null) {
            throw exception(EXECUTION_NOT_EXISTS);
        }
        // 共用表的关键防护：id 存在但它其实是个项目
        if (!ExecutionTypeEnum.isExecution(execution.getType())) {
            throw exception(ID_IS_NOT_EXECUTION, id);
        }
        return execution;
    }

    @Override
    public PageResult<ProjectDO> getExecutionPage(ExecutionPageReqVO reqVO) {
        return projectMapper.selectExecutionPage(reqVO);
    }

    @Override
    public List<ProjectDO> getExecutionListByProject(Long project) {
        return projectMapper.selectExecutionListByProject(project);
    }

    @Override
    public Long countExecutionByProject(Long project) {
        return projectMapper.countExecutionByProject(project);
    }

    // ==================== 内部 ====================

    private int countTeam(String team) {
        if (!StringUtils.hasText(team)) {
            return 0;
        }
        int count = 0;
        for (String item : team.split(",")) {
            if (StringUtils.hasText(item)) {
                count++;
            }
        }
        return count;
    }

    private String statusName(String status) {
        ProjectStatusEnum item = ProjectStatusEnum.of(status);
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

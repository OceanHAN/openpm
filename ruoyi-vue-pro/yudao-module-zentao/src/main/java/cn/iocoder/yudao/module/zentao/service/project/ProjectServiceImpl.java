package cn.iocoder.yudao.module.zentao.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.program.ProgramMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 项目 Service 实现
 *
 * 业务规则来源：禅道 {@code module/project/model.php} 的 start/suspend/activate/close。
 */
@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

    private static final String OBJECT_TYPE_PROJECT = "project";

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProgramMapper programMapper;

    @Resource
    private TeamService teamService;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 写：CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(ProjectSaveReqVO createReqVO) {
        // 禅道规则：项目名称唯一
        if (projectMapper.selectByName(createReqVO.getName()) != null) {
            throw exception(PROJECT_NAME_DUPLICATE, createReqVO.getName());
        }
        // 模型必须是合法枚举
        if (!ProjectModelEnum.isValid(createReqVO.getModel())) {
            throw exception(PROJECT_MODEL_INVALID, createReqVO.getModel());
        }
        checkDateRange(createReqVO.getBegin(), createReqVO.getEnd());

        ProjectDO project = BeanUtils.toBean(createReqVO, ProjectDO.class);
        // 禅道新建项目的初始值：未开始
        project.setStatus(ProjectStatusEnum.WAIT.getStatus());
        // 项目与执行共用 zt_project 表，项目自身的 type 固定为 'project'，
        // 执行则是 'sprint'/'stage'/'kanban'。这个字段是所有执行查询的过滤依据，必须写。
        project.setType(ExecutionTypeEnum.PROJECT.getType());
        project.setProject(0L); // 项目不属于任何项目
        project.setIsTpl(0);
        project.setMilestone(0);
        if (project.getEstimate() == null) {
            project.setEstimate(BigDecimal.ZERO);
        }
        if (project.getLeft() == null) {
            project.setLeft(project.getEstimate());
        }
        project.setConsumed(BigDecimal.ZERO);
        project.setProgress(BigDecimal.ZERO);
        if (project.getBudget() == null) {
            project.setBudget(BigDecimal.ZERO);
        }
        if (!StringUtils.hasText(project.getBudgetUnit())) {
            project.setBudgetUnit("CNY");
        }
        if (!StringUtils.hasText(project.getAcl())) {
            project.setAcl("open");
        }
        if (project.getOrder() == null) {
            project.setOrder(0);
        }
        // 层级：项目的 parent 是**所属项目集**（禅道 module/project/model.php: $program = getByID($project->parent)），
        // 不是「父项目」。项目集、项目、执行共用 zt_project，所以这里必须校验 parent 是 type='program'。
        Long parent = project.getParent() == null ? 0L : project.getParent();
        project.setParent(parent);
        validateProgramParent(parent);
        project.setGrade(buildGrade(parent));
        // 团队人数在插入后由 zt_team 统计（见 applyTeam），这里先给 0 占位
        project.setTeamCount(0);

        String operator = currentAccount();
        project.setOpenedBy(operator);
        project.setOpenedDate(LocalDateTime.now());
        projectMapper.insert(project);

        // path 需要 id，插入后再回填。格式与禅道一致：逗号包裹且包含自己，grade 从 1 开始
        //  顶级项目   path=',5,'        grade=1
        //  项目集下的 path=',9001,5,'   grade=2
        ProjectDO pathUpdate = new ProjectDO();
        pathUpdate.setId(project.getId());
        pathUpdate.setPath(buildPath(parent, project.getId()));
        projectMapper.updateById(pathUpdate);

        // 项目表单里的「团队成员」落到 zt_team，并把 teamCount/team 两列同步成缓存
        applyTeam(project);
        actionService.recordAction(OBJECT_TYPE_PROJECT, project.getId(), ActionTypeEnum.CREATED, null);
        return project.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(ProjectSaveReqVO updateReqVO) {
        ProjectDO oldProject = validateProjectExists(updateReqVO.getId());
        if (ProjectStatusEnum.CLOSED.getStatus().equals(oldProject.getStatus())) {
            throw exception(PROJECT_CLOSED_CANNOT_UPDATE);
        }
        ProjectDO sameName = projectMapper.selectByName(updateReqVO.getName());
        if (sameName != null && !sameName.getId().equals(oldProject.getId())) {
            throw exception(PROJECT_NAME_DUPLICATE, updateReqVO.getName());
        }
        if (StringUtils.hasText(updateReqVO.getModel())
                && !ProjectModelEnum.isValid(updateReqVO.getModel())) {
            throw exception(PROJECT_MODEL_INVALID, updateReqVO.getModel());
        }
        checkDateRange(updateReqVO.getBegin(), updateReqVO.getEnd());

        ProjectDO updateObj = BeanUtils.toBean(updateReqVO, ProjectDO.class);
        boolean teamChanged = updateReqVO.getTeam() != null;
        // 所属项目集变了 → 自己的 path/grade（以及挂在它下面的行）都要重算
        Long parent = updateReqVO.getParent() == null ? 0L : updateReqVO.getParent();
        validateProgramParent(parent);
        boolean parentChanged = !parent.equals(oldProject.getParent());
        if (parentChanged) {
            updateObj.setParent(parent);
            updateObj.setPath(buildPath(parent, oldProject.getId()));
            updateObj.setGrade(buildGrade(parent));
        }
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        projectMapper.updateById(updateObj);
        if (parentChanged) {
            moveDescendants(oldProject, parent);
        }
        if (teamChanged) {
            // 团队人数由成员表说了算：先同步成员，再把 teamCount/team 刷成缓存
            applyTeam(oldProject.getId(), updateReqVO.getTeam());
        }

        actionService.recordActionWithChanges(OBJECT_TYPE_PROJECT, oldProject.getId(),
                ActionTypeEnum.EDITED, null, oldProject, updateObj);
    }

    // ==================== 写：状态流转 ====================

    @Override
    public void startProject(Long id) {
        ProjectDO project = validateProjectExists(id);
        if (!ProjectStatusEnum.WAIT.getStatus().equals(project.getStatus())
                && !ProjectStatusEnum.SUSPENDED.getStatus().equals(project.getStatus())) {
            throw exception(PROJECT_STATUS_ILLEGAL, statusName(project.getStatus()));
        }

        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(id);
        updateObj.setStatus(ProjectStatusEnum.DOING.getStatus());
        updateObj.setRealBegan(LocalDate.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        projectMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_PROJECT, id,
                ActionTypeEnum.ACTIVATED, "开始项目", project, updateObj);
    }

    @Override
    public void suspendProject(Long id) {
        ProjectDO project = validateProjectExists(id);
        if (!ProjectStatusEnum.DOING.getStatus().equals(project.getStatus())
                && !ProjectStatusEnum.DELAY.getStatus().equals(project.getStatus())) {
            throw exception(PROJECT_STATUS_ILLEGAL, statusName(project.getStatus()));
        }

        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(id);
        updateObj.setStatus(ProjectStatusEnum.SUSPENDED.getStatus());
        updateObj.setSuspendedDate(LocalDateTime.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        projectMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_PROJECT, id,
                ActionTypeEnum.CHANGED, "挂起项目", project, updateObj);
    }

    @Override
    public void activateProject(Long id) {
        ProjectDO project = validateProjectExists(id);
        if (!ProjectStatusEnum.SUSPENDED.getStatus().equals(project.getStatus())) {
            throw exception(PROJECT_STATUS_ILLEGAL, statusName(project.getStatus()));
        }

        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(id);
        updateObj.setStatus(ProjectStatusEnum.DOING.getStatus());
        updateObj.setActivatedDate(LocalDateTime.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        projectMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_PROJECT, id,
                ActionTypeEnum.ACTIVATED, null, project, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeProject(Long id, String reason) {
        ProjectDO project = validateProjectExists(id);
        if (ProjectStatusEnum.CLOSED.getStatus().equals(project.getStatus())) {
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

        String comment = null;
        // 级联规则一：单执行项目（multiple=0）关闭时连带关闭其执行
        // 执行模块尚未实现，这里先在备注里说明，避免静默忽略
        if (project.getMultiple() == null || project.getMultiple() == 0) {
            comment = "关闭项目（单执行项目，需级联关闭其执行；执行模块尚未实现）";
        }
        // 级联规则二：未关联产品（hasProduct=0）时连带关闭自动创建的产品
        if (project.getHasProduct() == null || project.getHasProduct() == 0) {
            comment = (comment == null ? "" : comment + "；")
                    + "未关联产品，需级联关闭自动创建的产品（产品侧已支持 close）";
        }

        actionService.recordActionWithChanges(OBJECT_TYPE_PROJECT, id,
                ActionTypeEnum.CLOSED, comment, project, updateObj);
    }

    // ==================== 写：删除 ====================

    @Override
    public void deleteProject(Long id) {
        validateProjectExists(id);
        // 项目下面挂的是执行（不是子项目 —— 项目只在项目集里平级）
        Long executions = projectMapper.countExecutionByProject(id);
        if (executions != null && executions > 0) {
            throw exception(PROJECT_HAS_CHILDREN, executions);
        }
        // 禅道删项目时会把 zt_team 里属于它的成员行一起删掉（project/model.php:2029）
        teamService.removeAll(id, "project");
        projectMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_PROJECT, id, ActionTypeEnum.DELETED, null);
    }

    @Override
    public void deleteProjectList(List<Long> ids) {
        ids.forEach(this::deleteProject);
    }

    // ==================== 读 ====================

    @Override
    public ProjectDO getProject(Long id) {
        // 执行与项目共用 zt_project 表，项目接口不能读到执行
        return validateProjectExists(id);
    }

    @Override
    public ProjectDO validateProjectExists(Long id) {
        if (id == null) {
            return null;
        }
        ProjectDO project = projectMapper.selectById(id);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        // 执行、项目集都不是项目：三种角色共用这张表，拿错 id 必须报错
        if (ExecutionTypeEnum.isExecution(project.getType())
                || ExecutionTypeEnum.PROGRAM.getType().equals(project.getType())) {
            throw exception(PROJECT_ID_IS_NOT_PROJECT, id);
        }
        return project;
    }

    @Override
    public PageResult<ProjectDO> getProjectPage(ProjectPageReqVO reqVO) {
        return projectMapper.selectPage(reqVO);
    }

    @Override
    public List<ProjectDO> getProjectSimpleList() {
        return projectMapper.selectSimpleList();
    }

    /**
     * 某个项目集下的项目（项目靠 parent 指向项目集）
     */
    @Override
    public List<ProjectDO> getProjectListByParent(Long parent) {
        return projectMapper.selectListByParent(parent);
    }

    // ==================== 内部 ====================

    @Override
    public void fillTeamCount(List<ProjectDO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (ProjectDO project : list) {
            ids.add(project.getId());
        }
        Map<Long, Integer> counts = teamService.countByRoots(ids, "project");
        for (ProjectDO project : list) {
            project.setTeamCount(counts.getOrDefault(project.getId(), 0));
        }
    }

    /** 把 createReqVO/updateReqVO 里的 team 逗号串同步进成员表 */
    private void applyTeam(ProjectDO project) {
        applyTeam(project.getId(), project.getTeam());
    }

    private void applyTeam(Long projectId, String team) {
        List<String> accounts = new ArrayList<>();
        if (StringUtils.hasText(team)) {
            for (String account : team.split(",")) {
                if (StringUtils.hasText(account)) {
                    accounts.add(account.trim());
                }
            }
        }
        teamService.syncByAccounts(projectId, "project", accounts, "研发");
        teamService.syncTeamInfo(projectId, "project");
    }

    /**
     * 所属项目集必须是真实存在的项目集（0 表示不属于任何项目集）
     */
    private void validateProgramParent(Long parent) {
        if (parent == null || parent == 0) {
            return;
        }
        ProjectDO program = programMapper.selectById(parent);
        if (program == null || !ExecutionTypeEnum.PROGRAM.getType().equals(program.getType())) {
            throw exception(PROJECT_PARENT_NOT_PROGRAM, parent);
        }
    }

    /** path = 项目集.path + 自己 id + ','；不属于任何项目集时为 ,id, */
    private String buildPath(Long parent, Long id) {
        if (parent == null || parent == 0) {
            return "," + id + ",";
        }
        ProjectDO program = programMapper.selectById(parent);
        String programPath = program == null ? null : program.getPath();
        if (!StringUtils.hasText(programPath)) {
            return "," + id + ",";
        }
        return programPath + id + ",";
    }

    /** grade = 所属项目集.grade + 1；顶级项目为 1 */
    private Integer buildGrade(Long parent) {
        if (parent == null || parent == 0) {
            return 1;
        }
        ProjectDO program = programMapper.selectById(parent);
        int grade = program == null || program.getGrade() == null ? 1 : program.getGrade();
        return grade + 1;
    }

    /**
     * 换项目集时重算子树 path/grade（与项目集模块同一套规则，见 ProgramServiceImpl#moveDescendants）
     */
    private void moveDescendants(ProjectDO moved, Long newParentId) {
        String marker = "," + moved.getId() + ",";
        int oldGrade = moved.getGrade() == null ? 1 : moved.getGrade();
        String parentPath = "";
        int parentGrade = 0;
        if (newParentId != null && newParentId > 0) {
            ProjectDO newParent = programMapper.selectById(newParentId);
            if (newParent != null && StringUtils.hasText(newParent.getPath())) {
                parentPath = newParent.getPath().endsWith(",")
                        ? newParent.getPath().substring(0, newParent.getPath().length() - 1)
                        : newParent.getPath();
                parentGrade = newParent.getGrade() == null ? 1 : newParent.getGrade();
            }
        }
        for (ProjectDO child : projectMapper.selectDescendants(moved.getId())) {
            int pos = child.getPath() == null ? -1 : child.getPath().indexOf(marker);
            if (pos < 0) {
                continue;
            }
            String relative = child.getPath().substring(pos);
            int relativeGrade = (child.getGrade() == null ? 1 : child.getGrade()) - oldGrade + 1;
            ProjectDO update = new ProjectDO();
            update.setId(child.getId());
            update.setPath(parentPath + relative);
            update.setGrade(parentGrade + relativeGrade);
            projectMapper.updateById(update);
        }
    }

    /**
     * 禅道规则：计划结束不能早于开始
     */
    private void checkDateRange(LocalDate begin, LocalDate end) {
        if (begin != null && end != null && end.isBefore(begin)) {
            throw exception(PROJECT_END_BEFORE_BEGIN);
        }
    }

    /**
     * 团队成员用逗号分隔，团队人数由非空项数量推导
     */
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

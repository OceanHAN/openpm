package cn.iocoder.yudao.module.zentao.service.program;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.program.ProgramMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.execution.ExecutionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.project.ProjectStatusEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 项目集 Service 实现
 *
 * 业务规则来源：禅道 {@code module/program/model.php} + {@code module/program/control.php}。
 *
 * <h3>三个关键规则</h3>
 * <ol>
 *   <li><b>同名只在本级项目集内查重</b>：禅道的 unique 条件是
 *       {@code type='program' and parent=当前父项目集}，不同项目集下可以重名；</li>
 *   <li><b>path 是逗号格式且包含自己、grade 从 1 开始</b>
 *       （{@code setTreePath()}：顶级 {@code ,1,} grade=1，下级 {@code 父.path + id + ','} grade=父.grade+1）；</li>
 *   <li><b>移动项目集要重算整棵子树的 path/grade</b>（{@code processNode()}）：
 *       子孙的 path 里含 {@code ,自己,} 的那一段就是它在自己这棵树里的位置，
 *       把这一段前面换成新父的 path，grade 按层级差平移。</li>
 * </ol>
 */
@Slf4j
@Service
public class ProgramServiceImpl implements ProgramService {

    private static final String OBJECT_TYPE_PROGRAM = "program";

    @Resource
    private ProgramMapper programMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProgram(ProgramSaveReqVO createReqVO) {
        if (!StringUtils.hasText(createReqVO.getName())) {
            throw exception(PROGRAM_NAME_REQUIRED);
        }
        Long parent = createReqVO.getParent() == null ? 0L : createReqVO.getParent();
        validateParent(parent);
        checkDateRange(createReqVO.getBegin(), createReqVO.getEnd());
        // 禅道：同名只在同一级项目集里查重
        if (programMapper.selectByNameAndParent(createReqVO.getName(), parent) != null) {
            throw exception(PROGRAM_NAME_DUPLICATE, createReqVO.getName());
        }

        ProjectDO program = BeanUtils.toBean(createReqVO, ProjectDO.class);
        program.setId(null);
        program.setType(ExecutionTypeEnum.PROGRAM.getType());
        program.setProject(0L);
        program.setParent(parent);
        program.setStatus(ProjectStatusEnum.WAIT.getStatus());
        program.setIsTpl(0);
        program.setMilestone(0);
        program.setEstimate(BigDecimal.ZERO);
        program.setLeft(BigDecimal.ZERO);
        program.setConsumed(BigDecimal.ZERO);
        program.setProgress(BigDecimal.ZERO);
        program.setPercent(BigDecimal.ZERO);
        program.setHasProduct(1);
        program.setMultiple(1);
        program.setBudget(program.getBudget() == null ? BigDecimal.ZERO : program.getBudget());
        if (!StringUtils.hasText(program.getBudgetUnit())) {
            program.setBudgetUnit("CNY");
        }
        if (!StringUtils.hasText(program.getAcl())) {
            program.setAcl("open");
        }
        if (program.getPri() == null) {
            program.setPri(1);
        }
        program.setTeamCount(0);
        String operator = currentAccount();
        program.setOpenedBy(operator);
        program.setOpenedDate(LocalDateTime.now());
        programMapper.insert(program);

        // path/grade 需要 id，插入后再回填；order 跟禅道一样取 id*5，保持手工排序的空间
        ProjectDO pathUpdate = new ProjectDO();
        pathUpdate.setId(program.getId());
        pathUpdate.setPath(buildPath(parent, program.getId()));
        pathUpdate.setGrade(buildGrade(parent));
        pathUpdate.setOrder(program.getId().intValue() * 5);
        programMapper.updateById(pathUpdate);

        actionService.recordAction(OBJECT_TYPE_PROGRAM, program.getId(), ActionTypeEnum.CREATED, null);
        return program.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgram(ProgramSaveReqVO updateReqVO) {
        ProjectDO oldProgram = validateProgramExists(updateReqVO.getId());
        if (ProjectStatusEnum.CLOSED.getStatus().equals(oldProgram.getStatus())) {
            throw exception(PROGRAM_CLOSED_CANNOT_UPDATE);
        }
        Long parent = updateReqVO.getParent() == null ? 0L : updateReqVO.getParent();
        if (parent.equals(oldProgram.getId())) {
            throw exception(PROGRAM_PARENT_NOT_PROGRAM, parent);
        }
        validateParent(parent);
        checkDateRange(updateReqVO.getBegin(), updateReqVO.getEnd());
        ProjectDO sameName = programMapper.selectByNameAndParent(updateReqVO.getName(), parent);
        if (sameName != null && !sameName.getId().equals(oldProgram.getId())) {
            throw exception(PROGRAM_NAME_DUPLICATE, updateReqVO.getName());
        }

        ProjectDO updateObj = BeanUtils.toBean(updateReqVO, ProjectDO.class);
        updateObj.setType(ExecutionTypeEnum.PROGRAM.getType());
        updateObj.setParent(parent);
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        // 注意：VO 里没有 status，BeanUtils 转出来的 status 是 null，
        // MyBatis-Plus 默认忽略 null 字段，所以这里不会把状态改掉。
        // （已关闭的项目集在方法开头就抛 PROGRAM_CLOSED_CANNOT_UPDATE 了。）
        programMapper.updateById(updateObj);

        // 换了上级项目集 → 整棵子树的 path/grade 都要跟着挪
        if (!parent.equals(oldProgram.getParent())) {
            String newPath = buildPath(parent, oldProgram.getId());
            Integer newGrade = buildGrade(parent);
            ProjectDO selfPath = new ProjectDO();
            selfPath.setId(oldProgram.getId());
            selfPath.setPath(newPath);
            selfPath.setGrade(newGrade);
            programMapper.updateById(selfPath);
            moveDescendants(oldProgram, parent);
        }

        actionService.recordActionWithChanges(OBJECT_TYPE_PROGRAM, oldProgram.getId(),
                ActionTypeEnum.EDITED, null, oldProgram, updateObj);
    }

    /**
     * 移动项目集时重算子孙的 path/grade（逐行对齐禅道 {@code processNode()}）。
     *
     * <p>子孙的 path 里含 {@code ,自己,} 这一段，从那个逗号开始截到末尾，
     * 就是它在自己这棵树里的**相对位置**（含前导逗号）。新 path = 新父的 path（去掉末尾逗号）+ 相对位置；
     * 新 grade = 新父的 grade + 相对层级（相对层级 = 原子孙 grade - 原自己 grade + 1）。
     * 挪到顶级时新父的 path/grade 视作空/0，于是相对位置自己就带上了顶层的前导逗号。
     *
     * <p>注意这里用的是**新父**的 path，而不是「自己移动后的新 path」——
     * 后者会把自己那一段拼两遍（,9001,P,P,SUB, 这种）。
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
        for (ProjectDO child : programMapper.selectDescendants(moved.getId())) {
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
            programMapper.updateById(update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProgram(Long id) {
        validateProgramExists(id);
        // 禅道 delete 会连带处理下级对象，本实现选择显式拒绝：
        // 静默级联删除一个项目集下的项目和产品，比报错危险得多
        int childPrograms = programMapper.selectListByParent(id).size();
        if (childPrograms > 0) {
            throw exception(PROGRAM_HAS_CHILD_PROGRAM, childPrograms);
        }
        int projects = projectMapper.selectListByParent(id).size();
        if (projects > 0) {
            throw exception(PROGRAM_HAS_PROJECT, projects);
        }
        int products = productMapper.selectListByProgram(id).size();
        if (products > 0) {
            throw exception(PROGRAM_HAS_PRODUCT, products);
        }
        programMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_PROGRAM, id, ActionTypeEnum.DELETED, null);
    }

    @Override
    public void deleteProgramList(List<Long> ids) {
        ids.forEach(this::deleteProgram);
    }

    // ==================== 状态流转 ====================

    @Override
    public void startProgram(Long id) {
        ProjectDO program = validateProgramExists(id);
        if (!ProjectStatusEnum.WAIT.getStatus().equals(program.getStatus())
                && !ProjectStatusEnum.SUSPENDED.getStatus().equals(program.getStatus())) {
            throw exception(PROGRAM_STATUS_ILLEGAL, statusName(program.getStatus()));
        }
        ProjectDO update = new ProjectDO();
        update.setId(id);
        update.setStatus(ProjectStatusEnum.DOING.getStatus());
        update.setRealBegan(LocalDate.now());
        update.setLastEditedBy(currentAccount());
        update.setLastEditedDate(LocalDateTime.now());
        programMapper.updateById(update);
        actionService.recordActionWithChanges(OBJECT_TYPE_PROGRAM, id,
                ActionTypeEnum.ACTIVATED, "开始项目集", program, update);
    }

    @Override
    public void suspendProgram(Long id) {
        ProjectDO program = validateProgramExists(id);
        if (!ProjectStatusEnum.DOING.getStatus().equals(program.getStatus())) {
            throw exception(PROGRAM_STATUS_ILLEGAL, statusName(program.getStatus()));
        }
        ProjectDO update = new ProjectDO();
        update.setId(id);
        update.setStatus(ProjectStatusEnum.SUSPENDED.getStatus());
        update.setSuspendedDate(LocalDateTime.now());
        update.setLastEditedBy(currentAccount());
        update.setLastEditedDate(LocalDateTime.now());
        programMapper.updateById(update);
        actionService.recordActionWithChanges(OBJECT_TYPE_PROGRAM, id,
                ActionTypeEnum.CHANGED, "挂起项目集", program, update);
    }

    @Override
    public void activateProgram(Long id) {
        ProjectDO program = validateProgramExists(id);
        if (!ProjectStatusEnum.SUSPENDED.getStatus().equals(program.getStatus())) {
            throw exception(PROGRAM_STATUS_ILLEGAL, statusName(program.getStatus()));
        }
        ProjectDO update = new ProjectDO();
        update.setId(id);
        update.setStatus(ProjectStatusEnum.DOING.getStatus());
        update.setActivatedDate(LocalDateTime.now());
        update.setLastEditedBy(currentAccount());
        update.setLastEditedDate(LocalDateTime.now());
        programMapper.updateById(update);
        actionService.recordActionWithChanges(OBJECT_TYPE_PROGRAM, id,
                ActionTypeEnum.ACTIVATED, null, program, update);
    }

    @Override
    public void closeProgram(Long id, String reason) {
        ProjectDO program = validateProgramExists(id);
        if (ProjectStatusEnum.CLOSED.getStatus().equals(program.getStatus())) {
            throw exception(PROGRAM_ALREADY_CLOSED);
        }
        ProjectDO update = new ProjectDO();
        update.setId(id);
        update.setStatus(ProjectStatusEnum.CLOSED.getStatus());
        update.setClosedBy(currentAccount());
        update.setClosedDate(LocalDateTime.now());
        update.setClosedReason(StringUtils.hasText(reason) ? reason : "done");
        update.setRealEnd(LocalDate.now());
        update.setLastEditedBy(currentAccount());
        update.setLastEditedDate(LocalDateTime.now());
        programMapper.updateById(update);
        actionService.recordActionWithChanges(OBJECT_TYPE_PROGRAM, id,
                ActionTypeEnum.CLOSED, null, program, update);
    }

    // ==================== 读 ====================

    @Override
    public ProjectDO getProgram(Long id) {
        return validateProgramExists(id);
    }

    @Override
    public ProjectDO validateProgramExists(Long id) {
        if (id == null) {
            return null;
        }
        ProjectDO program = programMapper.selectById(id);
        if (program == null) {
            throw exception(PROGRAM_NOT_EXISTS);
        }
        // 项目/执行与项目集共用一张表：拿错 id 必须报错，不能把项目当项目集返回
        if (!ExecutionTypeEnum.PROGRAM.getType().equals(program.getType())) {
            throw exception(PROGRAM_NOT_EXISTS);
        }
        return program;
    }

    @Override
    public PageResult<ProjectDO> getProgramPage(ProgramPageReqVO reqVO) {
        return programMapper.selectPage(reqVO);
    }

    @Override
    public List<ProjectDO> getProgramList() {
        return programMapper.selectList();
    }

    @Override
    public List<ProjectDO> getProgramSimpleList() {
        return programMapper.selectSimpleList();
    }

    @Override
    public List<ProjectDO> getProgramListByParent(Long parent) {
        return programMapper.selectListByParent(parent == null ? 0L : parent);
    }

    @Override
    public ProgramRespVO fillStats(ProjectDO program) {
        ProgramRespVO vo = BeanUtils.toBean(program, ProgramRespVO.class);
        if (vo == null) {
            return null;
        }
        vo.setChildCount(programMapper.selectListByParent(program.getId()).size());
        vo.setProjectCount(projectMapper.selectListByParent(program.getId()).size());
        vo.setProductCount(productMapper.selectListByProgram(program.getId()).size());
        if (program.getParent() != null && program.getParent() > 0) {
            ProjectDO parent = programMapper.selectById(program.getParent());
            vo.setParentName(parent == null ? null : parent.getName());
        }
        return vo;
    }

    @Override
    public List<ProgramRespVO> fillStatsList(List<ProjectDO> programs) {
        List<ProgramRespVO> result = new ArrayList<>();
        for (ProjectDO program : programs) {
            result.add(fillStats(program));
        }
        return result;
    }

    @Override
    public List<ProjectDO> getProjectList(Long programId) {
        validateProgramExists(programId);
        return projectMapper.selectListByParent(programId);
    }

    @Override
    public List<ProductDO> getProductList(Long programId) {
        validateProgramExists(programId);
        return productMapper.selectListByProgram(programId);
    }

    @Override
    public Map<Long, String> getProgramNameMap(List<Long> ids) {
        Map<Long, String> map = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (ProjectDO program : programMapper.selectListByIds(ids)) {
            map.put(program.getId(), program.getName());
        }
        return map;
    }

    // ==================== 内部 ====================

    /**
     * 上级项目集必须是真实存在的项目集（0 表示顶级）
     */
    private void validateParent(Long parent) {
        if (parent == null || parent == 0) {
            return;
        }
        ProjectDO parentProgram = programMapper.selectById(parent);
        if (parentProgram == null || !ExecutionTypeEnum.PROGRAM.getType().equals(parentProgram.getType())) {
            throw exception(PROGRAM_PARENT_NOT_PROGRAM, parent);
        }
    }

    /** path = 父.path + 自己 id + ','；顶级就是 ,id, */
    private String buildPath(Long parent, Long id) {
        if (parent == null || parent == 0) {
            return "," + id + ",";
        }
        ProjectDO parentProgram = programMapper.selectById(parent);
        String parentPath = parentProgram == null ? null : parentProgram.getPath();
        if (!StringUtils.hasText(parentPath)) {
            return "," + id + ",";
        }
        return parentPath + id + ",";
    }

    /** grade = 父.grade + 1；顶级为 1（禅道从 1 开始，不是 0） */
    private Integer buildGrade(Long parent) {
        if (parent == null || parent == 0) {
            return 1;
        }
        ProjectDO parentProgram = programMapper.selectById(parent);
        int parentGrade = parentProgram == null || parentProgram.getGrade() == null ? 1 : parentProgram.getGrade();
        return parentGrade + 1;
    }

    private void checkDateRange(LocalDate begin, LocalDate end) {
        if (begin != null && end != null && end.isBefore(begin)) {
            throw exception(PROGRAM_END_BEFORE_BEGIN);
        }
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

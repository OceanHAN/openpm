package cn.iocoder.yudao.module.zentao.service.stage;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.stage.vo.StageRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.stage.vo.StageSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.stage.StageDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.stage.StageMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.execution.ExecutionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.project.ProjectStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.stage.StageTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 阶段（瀑布流程）Service 实现
 *
 * <p>对齐禅道 {@code module/stage/model.php} 的 create / batchCreate / update /
 * getTotalPercent / updateOrder，并补上「按模板为项目生成阶段」这一步。
 */
@Slf4j
@Service
public class StageServiceImpl implements StageService {

    private static final String OBJECT_TYPE_STAGE = "stage";
    private static final String OBJECT_TYPE_PROJECT = "project";

    /**
     * 禅道内置的瀑布流程模板组编号（install 时写入，zt_stage 里 projectType=waterfall 的那一套）
     */
    private static final String PROJECT_TYPE_WATERFALL = "waterfall";

    @Resource
    private StageMapper stageMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 模板：写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStage(StageSaveReqVO createReqVO) {
        if (createReqVO.getWorkflowGroup() == null) {
            throw exception(STAGE_GROUP_REQUIRED);
        }
        validateType(createReqVO.getType());
        validateNameUnique(createReqVO.getWorkflowGroup(), createReqVO.getName(), null);
        BigDecimal percent = parsePercent(createReqVO.getPercent());
        checkPercentNotOver(getTotalPercent(createReqVO.getWorkflowGroup()), percent);

        StageDO stage = new StageDO();
        stage.setWorkflowGroup(createReqVO.getWorkflowGroup());
        stage.setName(createReqVO.getName());
        stage.setPercent(createReqVO.getPercent());
        stage.setType(createReqVO.getType());
        stage.setProjectType(StringUtils.hasText(createReqVO.getProjectType())
                ? createReqVO.getProjectType() : PROJECT_TYPE_WATERFALL);
        // 禅道：order 取同组最大值 + 1
        stage.setOrder(stageMapper.selectMaxOrder(createReqVO.getWorkflowGroup()) + 1);
        stage.setCreatedBy(currentAccount());
        stage.setCreatedDate(LocalDateTime.now());
        stageMapper.insert(stage);

        actionService.recordAction(OBJECT_TYPE_STAGE, stage.getId(), ActionTypeEnum.CREATED,
                "新建阶段模板：" + stage.getName());
        return stage.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreateStages(Long workflowGroup, List<StageSaveReqVO> stages) {
        List<Long> ids = new ArrayList<>();
        BigDecimal total = getTotalPercent(workflowGroup);
        for (StageSaveReqVO item : stages) {
            if (!StringUtils.hasText(item.getName())) {
                continue;
            }
            item.setWorkflowGroup(workflowGroup);
            validateType(item.getType());
            validateNameUnique(workflowGroup, item.getName(), null);
            BigDecimal percent = parsePercent(item.getPercent());
            checkPercentNotOver(total, percent);
            total = total.add(percent);

            StageDO stage = new StageDO();
            stage.setWorkflowGroup(workflowGroup);
            stage.setName(item.getName());
            stage.setPercent(item.getPercent());
            stage.setType(item.getType());
            stage.setProjectType(StringUtils.hasText(item.getProjectType())
                    ? item.getProjectType() : PROJECT_TYPE_WATERFALL);
            stage.setOrder(stageMapper.selectMaxOrder(workflowGroup) + 1);
            stage.setCreatedBy(currentAccount());
            stage.setCreatedDate(LocalDateTime.now());
            stageMapper.insert(stage);
            ids.add(stage.getId());
        }
        actionService.recordAction(OBJECT_TYPE_STAGE, workflowGroup, ActionTypeEnum.CREATED,
                "批量新建阶段模板：" + ids.size() + " 个");
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStage(StageSaveReqVO updateReqVO) {
        StageDO oldStage = validateStageExists(updateReqVO.getId());
        if (StringUtils.hasText(updateReqVO.getType())) {
            validateType(updateReqVO.getType());
        }
        validateNameUnique(oldStage.getWorkflowGroup(), updateReqVO.getName(), oldStage.getId());

        // 禅道：total + new - old 不能超过 100（base 里先把本条旧值减掉）
        BigDecimal newPercent = parsePercent(updateReqVO.getPercent());
        BigDecimal oldPercent = parsePercent(oldStage.getPercent());
        checkPercentNotOver(getTotalPercent(oldStage.getWorkflowGroup()).subtract(oldPercent), newPercent);

        StageDO updateObj = new StageDO();
        updateObj.setId(oldStage.getId());
        updateObj.setName(updateReqVO.getName());
        updateObj.setPercent(updateReqVO.getPercent());
        updateObj.setType(StringUtils.hasText(updateReqVO.getType()) ? updateReqVO.getType() : oldStage.getType());
        updateObj.setProjectType(StringUtils.hasText(updateReqVO.getProjectType())
                ? updateReqVO.getProjectType() : oldStage.getProjectType());
        updateObj.setEditedBy(currentAccount());
        updateObj.setEditedDate(LocalDateTime.now());
        stageMapper.updateById(updateObj);

        StageDO newStage = stageMapper.selectById(oldStage.getId());
        actionService.recordActionWithChanges(OBJECT_TYPE_STAGE, oldStage.getId(),
                ActionTypeEnum.EDITED, null, oldStage, newStage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStage(Long id) {
        StageDO stage = validateStageExists(id);
        // 只删模板：已经生成到项目里的阶段是独立的数据，不受影响（禅道同样如此）
        stageMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_STAGE, id, ActionTypeEnum.DELETED,
                "删除阶段模板：" + stage.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Long> stageIds) {
        int order = 1;
        for (Long id : stageIds) {
            StageDO stage = validateStageExists(id);
            if (stage.getOrder() == null || stage.getOrder() != order) {
                StageDO updateObj = new StageDO();
                updateObj.setId(id);
                updateObj.setOrder(order);
                stageMapper.updateById(updateObj);
            }
            order++;
        }
    }

    // ==================== 模板：读 ====================

    @Override
    public StageDO getStage(Long id) {
        return validateStageExists(id);
    }

    @Override
    public StageDO validateStageExists(Long id) {
        StageDO stage = id == null ? null : stageMapper.selectById(id);
        if (stage == null) {
            throw exception(STAGE_NOT_EXISTS, id);
        }
        return stage;
    }

    @Override
    public List<StageDO> getStageListByGroup(Long workflowGroup) {
        return stageMapper.selectListByGroup(workflowGroup);
    }

    @Override
    public List<StageDO> getStageListByProjectType(String projectType) {
        return stageMapper.selectListByProjectType(
                StringUtils.hasText(projectType) ? projectType : PROJECT_TYPE_WATERFALL);
    }

    @Override
    public BigDecimal getTotalPercent(Long workflowGroup) {
        BigDecimal total = BigDecimal.ZERO;
        for (StageDO stage : stageMapper.selectListByGroup(workflowGroup)) {
            total = total.add(parsePercent(stage.getPercent()));
        }
        return total;
    }

    // ==================== 项目阶段（实例） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> generateStages(Long project, Long workflowGroup) {
        ProjectDO projectRow = validateProject(project);
        // 已经生成过就不再生成，避免重复（禅道在项目创建时就一次性生成）
        List<StageRespVO> existing = getProjectStages(project);
        if (!existing.isEmpty()) {
            throw exception(STAGE_ALREADY_GENERATED, existing.size());
        }

        Long group = workflowGroup;
        if (group == null || group <= 0) {
            group = projectRow.getWorkflowGroup();
        }
        List<StageDO> templates;
        if (group != null && group > 0) {
            templates = stageMapper.selectListByGroup(group);
        } else {
            // 项目没指定模板：按项目 model 找第一套（瀑布项目常见入口）。
            // 注意：selectListByProjectType 会跨多个模板组返回，所以拿到 group 之后
            // 必须再按 group 查一次，否则会把别的流程组的阶段一起生成进来。
            List<StageDO> candidates = stageMapper.selectListByProjectType(projectRow.getModel());
            if (candidates.isEmpty()) {
                templates = List.of();
            } else {
                group = candidates.get(0).getWorkflowGroup();
                templates = stageMapper.selectListByGroup(group);
            }
        }
        if (templates.isEmpty()) {
            throw exception(STAGE_TEMPLATE_EMPTY, group == null ? "(未指定)" : group);
        }

        List<Long> ids = new ArrayList<>();
        for (StageDO template : templates) {
            ProjectDO stage = new ProjectDO();
            stage.setProject(project);
            stage.setParent(0L);
            stage.setType(ExecutionTypeEnum.STAGE.getType());
            stage.setModel(projectRow.getModel());
            stage.setName(template.getName());
            stage.setPercent(parsePercent(template.getPercent()));
            stage.setWorkflowGroup(group);
            stage.setAttribute("");
            stage.setStatus(ProjectStatusEnum.WAIT.getStatus());
            stage.setPri(projectRow.getPri());
            stage.setMilestone(0);
            stage.setIsTpl(0);
            stage.setMultiple(1);
            stage.setHasProduct(projectRow.getHasProduct());
            stage.setStoryType(projectRow.getStoryType());
            stage.setBudget(BigDecimal.ZERO);
            stage.setBudgetUnit("CNY");
            stage.setDays(0);
            stage.setEstimate(BigDecimal.ZERO);
            stage.setLeft(BigDecimal.ZERO);
            stage.setConsumed(BigDecimal.ZERO);
            stage.setProgress(BigDecimal.ZERO);
            stage.setPM(projectRow.getPM());
            stage.setPO(projectRow.getPO());
            stage.setQD(projectRow.getQD());
            stage.setRD(projectRow.getRD());
            stage.setTeam(projectRow.getTeam());
            stage.setTeamCount(projectRow.getTeamCount());
            stage.setAcl(projectRow.getAcl());
            stage.setOrder(template.getOrder());
            stage.setOpenedBy(currentAccount());
            stage.setOpenedDate(LocalDateTime.now());
            projectMapper.insert(stage);

            // 阶段也是执行，层级口径与执行一致（禅道 execution::setTreePath）：
            // path = ,项目id,阶段id,、grade = 1
            ProjectDO pathUpdate = new ProjectDO();
            pathUpdate.setId(stage.getId());
            pathUpdate.setPath("," + project + "," + stage.getId() + ",");
            pathUpdate.setGrade(1);
            projectMapper.updateById(pathUpdate);
            ids.add(stage.getId());
        }

        // 把流程模板记到项目上，便于后续查看/重新生成
        ProjectDO projectUpdate = new ProjectDO();
        projectUpdate.setId(project);
        projectUpdate.setWorkflowGroup(group);
        projectMapper.updateById(projectUpdate);

        actionService.recordAction(OBJECT_TYPE_PROJECT, project, ActionTypeEnum.EDITED,
                "按流程模板 #" + group + " 生成 " + ids.size() + " 个阶段");
        return ids;
    }

    @Override
    public List<StageRespVO> getProjectStages(Long project) {
        List<ProjectDO> rows = new ArrayList<>();
        for (ProjectDO row : projectMapper.selectExecutionListByProject(project)) {
            if (ExecutionTypeEnum.STAGE.getType().equals(row.getType())) {
                rows.add(row);
            }
        }
        // 阶段自身的 project 列指向项目，这里直接按 order/id 排序
        rows.sort((a, b) -> {
            int oa = a.getOrder() == null ? 0 : a.getOrder();
            int ob = b.getOrder() == null ? 0 : b.getOrder();
            return oa != ob ? Integer.compare(oa, ob) : Long.compare(a.getId(), b.getId());
        });

        List<StageRespVO> result = new ArrayList<>();
        for (ProjectDO row : rows) {
            // 手工组装而不是 BeanUtils.toBean：ProjectDO.percent 是 BigDecimal、
            // StageRespVO.percent 是 String（要兼容模板那边的 varchar），类型不一致会让
            // 通用 Bean 拷贝抛转换异常。字段不多，直接映射更稳。
            StageRespVO vo = new StageRespVO();
            vo.setId(row.getId());
            vo.setProject(row.getProject());
            vo.setName(row.getName());
            vo.setPercent(row.getPercent() == null ? ""
                    : row.getPercent().stripTrailingZeros().toPlainString());
            vo.setWorkflowGroup(row.getWorkflowGroup());
            vo.setProjectType(row.getModel());
            vo.setOrder(row.getOrder());
            vo.setStatus(row.getStatus());
            vo.setStatusName(ProjectStatusEnum.nameOf(row.getStatus()));
            vo.setBegin(row.getBegin());
            vo.setEnd(row.getEnd());
            vo.setRealBegan(row.getRealBegan());
            vo.setRealEnd(row.getRealEnd());
            vo.setEstimate(row.getEstimate());
            vo.setConsumed(row.getConsumed());
            vo.setLeft(row.getLeft());
            vo.setProgress(row.getProgress());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProjectStages(Long project) {
        validateProject(project);
        List<StageRespVO> stages = getProjectStages(project);
        for (StageRespVO stage : stages) {
            projectMapper.deleteById(stage.getId());
        }
        actionService.recordAction(OBJECT_TYPE_PROJECT, project, ActionTypeEnum.EDITED,
                "删除项目的 " + stages.size() + " 个阶段");
    }

    // ==================== 内部 ====================

    private void validateType(String type) {
        if (!StageTypeEnum.isValid(type)) {
            throw exception(STAGE_TYPE_INVALID, type);
        }
    }

    private void validateNameUnique(Long workflowGroup, String name, Long excludeId) {
        if (stageMapper.selectByName(workflowGroup, name, excludeId) != null) {
            throw exception(STAGE_NAME_DUPLICATE, name);
        }
    }

    /**
     * percent 是 varchar，可能为空串；非数字直接报错（禅道 notNum）
     */
    private BigDecimal parsePercent(String percent) {
        if (!StringUtils.hasText(percent)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(percent.trim());
        } catch (NumberFormatException e) {
            throw exception(STAGE_PERCENT_NOT_NUMBER, percent);
        }
    }

    /**
     * 占比校验：{@code baseTotal + percent ≤ 100}。
     *
     * <p>参数 {@code baseTotal} 是**已占用的合计**，由调用方算好传进来 ——
     * 早期版本在这里又查了一次数据库合计，批量创建时会把已累计的部分重复计入
     * （20% 的已有 + 批量累加 → 变成 80+30>100 的假报错）。
     * 新增：base = 当前合计；修改：base = 当前合计 - 本条旧值；批量：base 由循环累加。
     */
    private void checkPercentNotOver(BigDecimal baseTotal, BigDecimal percent) {
        BigDecimal after = baseTotal.add(percent);
        if (after.setScale(0, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(100)) > 0) {
            throw exception(STAGE_PERCENT_OVER, baseTotal.stripTrailingZeros().toPlainString(),
                    percent.stripTrailingZeros().toPlainString());
        }
    }

    /**
     * 目标必须是「项目」（不能是执行/阶段）
     */
    private ProjectDO validateProject(Long id) {
        ProjectDO row = id == null ? null : projectMapper.selectById(id);
        if (row == null || ExecutionTypeEnum.isExecution(row.getType())) {
            throw exception(STAGE_PROJECT_NOT_EXISTS, id);
        }
        return row;
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

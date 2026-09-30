package cn.iocoder.yudao.module.zentao.service.workestimation;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.workestimation.vo.WorkEstimationRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.workestimation.vo.WorkEstimationSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.workestimation.WorkEstimationDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.workestimation.WorkEstimationMapper;
import cn.iocoder.yudao.module.zentao.service.project.ProjectService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 项目工作量估算 Service 实现
 *
 * <h3>两个公式（表结构里没有"公式"列，但字段名就是它的定义）</h3>
 * <pre>
 *   duration       = scale / productivity          （工期 = 规模 ÷ 生产率）
 *   totalLaborCost = duration × dayHour × unitLaborCost
 * </pre>
 * 生产率填 0 时不做除法（保留 0），避免除零；两个派生值都由服务端算，防止前端传进来不一致的值。
 */
@Slf4j
@Service
public class WorkEstimationServiceImpl implements WorkEstimationService {

    /** 每天工时默认值（禅道表默认 8.00） */
    private static final BigDecimal DEFAULT_DAY_HOUR = new BigDecimal("8.00");

    @Resource
    private WorkEstimationMapper workEstimationMapper;

    @Resource
    private ProjectService projectService;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public WorkEstimationRespVO getByProject(Long project) {
        WorkEstimationDO estimation = project == null ? null : workEstimationMapper.selectByProject(project);
        // 没有估算不是错误（禅道 getBudget 返回 null）—— 返回 null 而不是抛异常
        return estimation == null ? null : BeanUtils.toBean(estimation, WorkEstimationRespVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkEstimationRespVO save(WorkEstimationSaveReqVO reqVO) {
        projectService.validateProjectExists(reqVO.getProject());

        BigDecimal scale = nvl(reqVO.getScale());
        BigDecimal productivity = nvl(reqVO.getProductivity());
        BigDecimal dayHour = reqVO.getDayHour() == null ? DEFAULT_DAY_HOUR : reqVO.getDayHour();
        BigDecimal unitLaborCost = nvl(reqVO.getUnitLaborCost());
        // 派生值：工期与总人工成本
        BigDecimal duration = productivity.signum() == 0
                ? BigDecimal.ZERO
                : scale.divide(productivity, 2, RoundingMode.HALF_UP);
        BigDecimal totalLaborCost = duration.multiply(dayHour).multiply(unitLaborCost)
                .setScale(2, RoundingMode.HALF_UP);

        WorkEstimationDO old = workEstimationMapper.selectByProject(reqVO.getProject());
        WorkEstimationDO updateObj = new WorkEstimationDO();
        updateObj.setProject(reqVO.getProject());
        updateObj.setScale(scale);
        updateObj.setProductivity(productivity);
        updateObj.setDuration(duration);
        updateObj.setDayHour(dayHour);
        updateObj.setUnitLaborCost(unitLaborCost);
        updateObj.setTotalLaborCost(totalLaborCost);
        updateObj.setAssignedTo(reqVO.getAssignedTo() == null ? "" : reqVO.getAssignedTo());
        if (old == null) {
            String operator = currentAccount();
            updateObj.setAssignedDate(LocalDateTime.now());
            updateObj.setCreatedBy(operator);
            updateObj.setCreatedDate(LocalDateTime.now());
            workEstimationMapper.insert(updateObj);
        } else {
            updateObj.setId(old.getId());
            updateObj.setEditedBy(currentAccount());
            updateObj.setEditedDate(LocalDateTime.now());
            workEstimationMapper.updateById(updateObj);
        }
        return BeanUtils.toBean(workEstimationMapper.selectByProject(reqVO.getProject()), WorkEstimationRespVO.class);
    }

    private BigDecimal nvl(BigDecimal value) {
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

}

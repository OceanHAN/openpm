package cn.iocoder.yudao.module.zentao.service.workestimation;

import cn.iocoder.yudao.module.zentao.controller.admin.workestimation.vo.WorkEstimationRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.workestimation.vo.WorkEstimationSaveReqVO;

/**
 * 项目工作量估算 Service
 *
 * 对应禅道 {@code module/workestimation/model.php#getBudget}。
 */
public interface WorkEstimationService {

    /** 取某个项目的估算；没有就返回 null（禅道 getBudget 也是返回 null） */
    WorkEstimationRespVO getByProject(Long project);

    /**
     * 保存估算（不存在就新建）。duration 与 totalLaborCost 由服务端算，不接受入参：
     * <pre>
     *   duration       = scale / productivity
     *   totalLaborCost = duration × dayHour × unitLaborCost
     * </pre>
     */
    WorkEstimationRespVO save(WorkEstimationSaveReqVO reqVO);

}

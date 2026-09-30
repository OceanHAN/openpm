package cn.iocoder.yudao.module.zentao.service.effort;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortSummaryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortTaskStatRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.effort.EffortDO;

import java.util.List;

/**
 * 工时明细 Service
 *
 * 对应禅道 {@code module/task/model.php} 里的 deleteWorkhour / updateEffort / getTaskEfforts。
 */
public interface EffortService {

    /**
     * 登记一条工时，并重算任务的已消耗/剩余/状态
     *
     * @return 工时编号
     */
    Long createEffort(EffortSaveReqVO createReqVO);

    /**
     * 修改一条工时，并重算任务
     */
    void updateEffort(EffortSaveReqVO updateReqVO);

    /**
     * 删除一条工时，并重算任务
     */
    void deleteEffort(Long id);

    EffortDO getEffort(Long id);

    /**
     * 某个任务的全部工时，按日期正序
     */
    List<EffortDO> getEffortListByTask(Long taskId);

    PageResult<EffortDO> getEffortPage(EffortPageReqVO reqVO);

    /**
     * 任务工时统计：预计/已消耗/剩余/状态 + 明细。
     * 这是「录完工时任务变成什么样」的查询入口，也是测试断言口径的来源。
     */
    EffortTaskStatRespVO getTaskStat(Long taskId);

    /**
     * 按账号汇总工时（工时报表）
     */
    List<EffortSummaryRespVO> getEffortSummary(EffortPageReqVO reqVO);

}

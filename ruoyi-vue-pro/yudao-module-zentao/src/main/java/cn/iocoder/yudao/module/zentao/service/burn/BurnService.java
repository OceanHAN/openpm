package cn.iocoder.yudao.module.zentao.service.burn;

import cn.iocoder.yudao.module.zentao.controller.admin.burn.vo.BurnChartRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.burn.BurnDO;

import java.util.List;

/**
 * 执行燃尽图（禅道 {@code module/execution} 的 burn + computeBurn）。
 *
 * <p>职责只有两件事：**把今天的汇总落成一条快照**（{@link #computeBurn}）和
 * **把快照读成三条曲线**（{@link #getBurnData}）。历史快照一旦落库就不再改，
 * 所以任务被改被删都不会影响已经画出来的曲线。
 */
public interface BurnService {

    /**
     * 重新计算某个执行的今日快照（禅道 {@code execution::computeBurn}）。
     *
     * @param executionId 执行编号；不传则算全部「进行中/未开始」的迭代与阶段
     * @return 写进去的快照列表
     */
    List<BurnDO> computeBurn(Long executionId);

    /** 燃尽图三条线 + 原始快照 */
    BurnChartRespVO getBurnData(Long executionId, String type, String burnBy, Integer interval);

}

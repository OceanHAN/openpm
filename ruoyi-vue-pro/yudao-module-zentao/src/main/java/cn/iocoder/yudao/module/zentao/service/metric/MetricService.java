package cn.iocoder.yudao.module.zentao.service.metric;

import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricCalcRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricDataRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.dal.dataobject.metric.MetricDO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 度量 Service 接口。
 *
 * <p>禅道把度量拆成「定义在 {@code zt_metric}、数据在 {@code zt_metriclib}、口径在代码里」
 * 三块，本实现照搬这三块：口径是 Java 注册表（{@link MetricRegistry}），
 * 计算流程与清旧数据规则见 {@link MetricServiceImpl}。详见 README 3.34。
 */
public interface MetricService {

    // ==================== 度量项 ====================

    PageResult<MetricRespVO> getMetricPage(MetricPageReqVO reqVO);

    List<MetricRespVO> getMetricList(String purpose, String scope, String object);

    MetricRespVO getMetric(String code);

    MetricDO validateMetricExists(String code);

    /** 字典：目的/范围/对象/单位/时间维度/状态/计算方式 */
    Map<String, Object> getDict();

    /** 概览：内置总数、已迁移口径数、上次计算时间 */
    Map<String, Object> getSummary();

    /** 已迁移口径的度量项代码（Java 注册表里的） */
    Set<String> getImplementedCodes();

    // ==================== 计算 ====================

    /**
     * 计算一个度量项：跑口径 → 按周期清掉旧数据 → 写入 zt_metriclib → 回写 lastCalcRows/lastCalcTime
     *
     * @param calcType cron 定时 / inference 人工触发
     */
    MetricCalcRespVO calcMetric(String code, String calcType);

    /** 批量计算所有已迁移口径（失败的跳过并记录） */
    List<MetricCalcRespVO> calcAll(String calcType);

    // ==================== 数据 ====================

    /**
     * 查度量数据。nodate 型只返回「今天算的」快照（与禅道 fetchMetricRecords 一致）
     */
    MetricDataRespVO getMetricData(String code, String scope, String dateBegin, String dateEnd,
                                   Integer pageNo, Integer pageSize);

}

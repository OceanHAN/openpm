package cn.iocoder.yudao.module.zentao.service.bi;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.bi.vo.*;

import java.util.List;
import java.util.Map;

/**
 * BI（数据视图 + 图表）Service 接口。
 *
 * <p>禅道 20+ 的 BI 底层是 DuckDB + Parquet（module/bi）；本实现走禅道自己也支持的
 * **SQL 模式**：数据视图存一条只读 SELECT，直接在 MySQL 上执行，执行前过 {@link SqlGuard}。
 * 详见 README 3.35。
 */
public interface BiService {

    // ==================== 数据视图 ====================

    PageResult<DataViewRespVO> getDataViewPage(BiPageReqVO reqVO);

    List<DataViewRespVO> getDataViewList();

    DataViewRespVO getDataView(Long id);

    Long createDataView(DataViewSaveReqVO reqVO);

    void updateDataView(DataViewSaveReqVO reqVO);

    /** 删除数据视图：被图表引用时拒绝（本实现加的保护，禅道新版没有图表引用检查） */
    void deleteDataView(Long id);

    /** 预览数据视图的数据（最多 200 行） */
    DataViewPreviewRespVO previewDataView(Long id, Integer limit);

    /** 新建前先试跑一段 SQL（校验 + 看字段与数据） */
    DataViewPreviewRespVO previewSql(String sql, Integer limit);

    // ==================== 图表 ====================

    PageResult<ChartRespVO> getChartPage(BiPageReqVO reqVO);

    List<ChartRespVO> getChartList();

    ChartRespVO getChart(Long id);

    Long createChart(ChartSaveReqVO reqVO);

    void updateChart(ChartSaveReqVO reqVO);

    void deleteChart(Long id);

    /**
     * 图表数据：按 settings 里的维度字段分组、对指标字段做聚合，返回 [{name, value}]
     */
    ChartDataRespVO getChartData(Long id);

    /** 字典：图表类型 + 聚合方式 +（可选）可用的数据视图 */
    Map<String, Object> getDict();

}

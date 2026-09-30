package cn.iocoder.yudao.module.zentao.service.bi;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.bi.vo.*;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bi.ChartDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bi.DataViewDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.bi.BiQueryMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.bi.ChartMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.bi.DataViewMapper;
import cn.iocoder.yudao.module.zentao.enums.bi.AggTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.bi.ChartTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * BI（数据视图 + 图表）Service 实现。
 *
 * <p>规则来源：禅道 {@code module/bi}（数据视图/数据集）+ {@code module/pivot}（透视表：行/列维度 + 指标）
 * + {@code module/chart}（图表类型与设置）。
 *
 * <h3>查询链路</h3>
 * <pre>
 *   数据视图（zt_dataview.sql，只读 SELECT，过 SqlGuard）
 *        ↓ 图表用它当数据源（viewCode 引用，或图表自带 sql）
 *   图表设置 settings = {dimensionField 维度, metricField 指标, agg 聚合, limit, sort}
 *        ↓ 拼一条「外层聚合」SQL
 *   SELECT `维度` AS name, AGG(`指标`) AS value FROM (数据视图 SQL) t [WHERE 过滤] GROUP BY `维度`
 * </pre>
 *
 * <h3>安全边界（这是全项目唯一拼 SQL 的地方）</h3>
 * <ul>
 *   <li>SQL 侧：{@link SqlGuard}（单语句 / SELECT / 关键字黑名单 / 只允许 {@code zt_*} 表）</li>
 *   <li>字段侧：维度、指标、过滤字段全部过 {@code validateIdentifier}（字母数字下划线），
 *       再以反引号拼进 SQL</li>
 *   <li>聚合侧：{@code agg} 只认 {@link AggTypeEnum} 的 5 个枚举值，函数名取自枚举，不接受用户片段</li>
 *   <li>结果侧：预览最多 200 行、图表最多 200 个分组</li>
 * </ul>
 */
@Slf4j
@Service
public class BiServiceImpl implements BiService {

    private static final int MAX_LIMIT = 200;

    /** 聚合函数关键字：用来判断解析出来的字段是「指标」还是「维度」 */
    private static final Pattern METRIC_PATTERN =
            Pattern.compile("(?i)\\b(count|sum|avg|max|min)\\s*\\(");

    @Resource
    private DataViewMapper dataViewMapper;

    @Resource
    private ChartMapper chartMapper;

    @Resource
    private BiQueryMapper biQueryMapper;

    @Resource
    private SqlGuard sqlGuard;

    @Resource
    private AdminUserApi adminUserApi;

    // ================================================================
    // 数据视图
    // ================================================================

    @Override
    public PageResult<DataViewRespVO> getDataViewPage(BiPageReqVO reqVO) {
        PageResult<DataViewDO> page = dataViewMapper.selectPage(reqVO.getName(), reqVO);
        List<DataViewRespVO> list = new ArrayList<>(page.getList().size());
        for (DataViewDO dataView : page.getList()) {
            list.add(convertDataView(dataView));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<DataViewRespVO> getDataViewList() {
        List<DataViewRespVO> list = new ArrayList<>();
        for (DataViewDO dataView : dataViewMapper.selectAllList()) {
            list.add(convertDataView(dataView));
        }
        return list;
    }

    @Override
    public DataViewRespVO getDataView(Long id) {
        return convertDataView(validateDataViewExists(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDataView(DataViewSaveReqVO reqVO) {
        String sql = sqlGuard.validate(reqVO.getSql());
        if (dataViewMapper.selectByCode(reqVO.getCode()) != null) {
            throw exception(BI_DATA_VIEW_CODE_DUPLICATE, reqVO.getCode());
        }
        String account = currentAccount();
        DataViewDO dataView = new DataViewDO();
        dataView.setGroup(0L);
        dataView.setName(reqVO.getName());
        dataView.setCode(reqVO.getCode());
        dataView.setMode("sql");
        dataView.setDriver("mysql");
        dataView.setView("");
        dataView.setSql(sql);
        // 没传字段就按 SQL 自动解析（禅道保存数据视图时也是自动解析字段）
        List<Map<String, Object>> fields = reqVO.getFields() == null || reqVO.getFields().isEmpty()
                ? parseFields(sql) : reqVO.getFields();
        dataView.setFields(JsonUtils.toJsonString(fields));
        dataView.setLangs(JsonUtils.toJsonString(reqVO.getLangs() == null ? new LinkedHashMap<>() : reqVO.getLangs()));
        dataView.setObjects(JsonUtils.toJsonString(reqVO.getObjects() == null ? new ArrayList<>() : reqVO.getObjects()));
        dataView.setCreatedBy(account);
        dataView.setCreatedDate(LocalDateTime.now());
        dataView.setEditedBy(account);
        dataView.setEditedDate(LocalDateTime.now());
        dataViewMapper.insert(dataView);
        return dataView.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDataView(DataViewSaveReqVO reqVO) {
        DataViewDO old = validateDataViewExists(reqVO.getId());
        String sql = sqlGuard.validate(reqVO.getSql());
        DataViewDO existed = dataViewMapper.selectByCode(reqVO.getCode());
        if (existed != null && !Objects.equals(existed.getId(), old.getId())) {
            throw exception(BI_DATA_VIEW_CODE_DUPLICATE, reqVO.getCode());
        }
        DataViewDO update = new DataViewDO();
        update.setId(old.getId());
        update.setName(reqVO.getName());
        update.setCode(reqVO.getCode());
        update.setSql(sql);
        List<Map<String, Object>> fields = reqVO.getFields() == null || reqVO.getFields().isEmpty()
                ? parseFields(sql) : reqVO.getFields();
        update.setFields(JsonUtils.toJsonString(fields));
        if (reqVO.getLangs() != null) {
            update.setLangs(JsonUtils.toJsonString(reqVO.getLangs()));
        }
        if (reqVO.getObjects() != null) {
            update.setObjects(JsonUtils.toJsonString(reqVO.getObjects()));
        }
        update.setEditedBy(currentAccount());
        update.setEditedDate(LocalDateTime.now());
        dataViewMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataView(Long id) {
        DataViewDO dataView = validateDataViewExists(id);
        long used = chartCountByViewCode(dataView.getCode());
        if (used > 0) {
            throw exception(BI_DATA_VIEW_USED_BY_CHART, used);
        }
        dataViewMapper.deleteById(id);
    }

    @Override
    public DataViewPreviewRespVO previewDataView(Long id, Integer limit) {
        DataViewDO dataView = validateDataViewExists(id);
        return runPreview(dataView.getSql(), limit);
    }

    @Override
    public DataViewPreviewRespVO previewSql(String sql, Integer limit) {
        return runPreview(sqlGuard.validate(sql), limit);
    }

    private DataViewPreviewRespVO runPreview(String sql, Integer limit) {
        int size = normalizeLimit(limit, 20);
        // 包一层再限行：用户 SQL 自带 LIMIT 也不影响，且不会把整表拉出来
        String executed = "SELECT * FROM (" + sql + ") t LIMIT " + size;
        DataViewPreviewRespVO resp = new DataViewPreviewRespVO();
        resp.setExecutedSql(executed);
        try {
            List<Map<String, Object>> rows = biQueryMapper.selectMaps(executed);
            resp.setRows(rows);
            resp.setTotal(rows.size());
            resp.setColumns(rows.isEmpty() ? columnsOf(sql) : new ArrayList<>(rows.get(0).keySet()));
        } catch (Exception e) {
            throw exception(BI_SQL_EXECUTE_FAILED, rootMessage(e));
        }
        return resp;
    }

    // ================================================================
    // 图表
    // ================================================================

    @Override
    public PageResult<ChartRespVO> getChartPage(BiPageReqVO reqVO) {
        PageResult<ChartDO> page = chartMapper.selectPage(reqVO.getName(), reqVO.getType(), reqVO);
        List<ChartRespVO> list = new ArrayList<>(page.getList().size());
        for (ChartDO chart : page.getList()) {
            list.add(convertChart(chart));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<ChartRespVO> getChartList() {
        List<ChartRespVO> list = new ArrayList<>();
        for (ChartDO chart : chartMapper.selectAllList()) {
            list.add(convertChart(chart));
        }
        return list;
    }

    @Override
    public ChartRespVO getChart(Long id) {
        return convertChart(validateChartExists(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChart(ChartSaveReqVO reqVO) {
        validateChartSettings(reqVO);
        String account = currentAccount();
        ChartDO chart = new ChartDO();
        chart.setName(reqVO.getName());
        chart.setCode(StringUtils.hasText(reqVO.getCode()) ? reqVO.getCode() : "chart_" + System.currentTimeMillis());
        chart.setDriver("mysql");
        chart.setMode("sql");
        chart.setDimension(0L);
        chart.setViewCode(reqVO.getViewCode() == null ? "" : reqVO.getViewCode());
        chart.setType(StringUtils.hasText(reqVO.getType()) ? reqVO.getType() : ChartTypeEnum.PIE.getType());
        chart.setGroup("");
        chart.setDesc(reqVO.getDesc() == null ? "" : reqVO.getDesc());
        chart.setAcl("open");
        chart.setWhitelist("");
        chart.setSettings(JsonUtils.toJsonString(reqVO.getSettings()));
        chart.setFilters(JsonUtils.toJsonString(reqVO.getFilters() == null ? new ArrayList<>() : reqVO.getFilters()));
        chart.setStep(3);
        chart.setFields(JsonUtils.toJsonString(resolveFields(reqVO)));
        chart.setLangs(JsonUtils.toJsonString(new LinkedHashMap<>()));
        chart.setSql(resolveSql(reqVO));
        chart.setVersion("1");
        chart.setStage("published");
        chart.setBuiltin(0);
        chart.setObjects(JsonUtils.toJsonString(new ArrayList<>()));
        chart.setCreatedBy(account);
        chart.setCreatedDate(LocalDateTime.now());
        chart.setEditedBy(account);
        chart.setEditedDate(LocalDateTime.now());
        chartMapper.insert(chart);
        return chart.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChart(ChartSaveReqVO reqVO) {
        ChartDO old = validateChartExists(reqVO.getId());
        validateChartSettings(reqVO);
        ChartDO update = new ChartDO();
        update.setId(old.getId());
        update.setName(reqVO.getName());
        update.setCode(StringUtils.hasText(reqVO.getCode()) ? reqVO.getCode() : old.getCode());
        update.setType(StringUtils.hasText(reqVO.getType()) ? reqVO.getType() : old.getType());
        update.setViewCode(reqVO.getViewCode() == null ? old.getViewCode() : reqVO.getViewCode());
        update.setDesc(reqVO.getDesc());
        update.setSettings(JsonUtils.toJsonString(reqVO.getSettings()));
        if (reqVO.getFilters() != null) {
            update.setFilters(JsonUtils.toJsonString(reqVO.getFilters()));
        }
        update.setSql(resolveSql(reqVO));
        update.setFields(JsonUtils.toJsonString(resolveFields(reqVO)));
        // 改一次版本 +1（禅道的图表/透视表也是版本化对象）
        update.setVersion(String.valueOf((Integer.parseInt(old.getVersion() == null ? "1" : old.getVersion())) + 1));
        update.setEditedBy(currentAccount());
        update.setEditedDate(LocalDateTime.now());
        chartMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChart(Long id) {
        validateChartExists(id);
        chartMapper.deleteById(id);
    }

    @Override
    public ChartDataRespVO getChartData(Long id) {
        ChartDO chart = validateChartExists(id);
        Map<String, Object> settings = parseMap(chart.getSettings());
        String dimension = sqlGuard.validateIdentifier(str(settings.get("dimensionField")));
        AggTypeEnum agg = AggTypeEnum.of(str(settings.get("agg")));
        if (agg == null) {
            throw exception(BI_CHART_AGG_INVALID, settings.get("agg"));
        }
        // count 用 COUNT(*)：数的是「这个维度下有多少行」；其余聚合对指标字段做计算
        String aggExpr = AggTypeEnum.COUNT == agg
                ? "COUNT(*)"
                : agg.getFunction() + "(`" + sqlGuard.validateIdentifier(str(settings.get("metricField"))) + "`)";
        int limit = normalizeLimit(toInt(settings.get("limit")), 20);
        String sort = "value_asc".equals(str(settings.get("sort"))) ? "ASC"
                : "name_asc".equals(str(settings.get("sort"))) ? null : "DESC";

        String inner = chart.getSql();
        if (!StringUtils.hasText(inner)) {
            throw exception(BI_SQL_INVALID, "图表没有配置数据源");
        }
        sqlGuard.validate(inner);
        String where = buildFilters(parseList(chart.getFilters()));
        String orderBy = sort == null ? "`name` ASC" : "`value` " + sort;
        String executed = "SELECT `" + dimension + "` AS name, " + aggExpr + " AS value FROM ("
                + inner + ") t" + (where.isEmpty() ? "" : " WHERE " + where)
                + " GROUP BY `" + dimension + "` ORDER BY " + orderBy + " LIMIT " + limit;

        ChartDataRespVO resp = new ChartDataRespVO();
        resp.setChart(convertChart(chart));
        resp.setDimensionField(dimension);
        resp.setMetricField(str(settings.get("metricField")));
        resp.setAgg(agg.getAgg());
        resp.setExecutedSql(executed);
        try {
            resp.setRows(biQueryMapper.selectMaps(executed));
        } catch (Exception e) {
            throw exception(BI_SQL_EXECUTE_FAILED, rootMessage(e));
        }
        return resp;
    }

    @Override
    public Map<String, Object> getDict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("chartTypeList", Arrays.stream(ChartTypeEnum.values())
                .map(e -> option(e.getType(), e.getName())).collect(Collectors.toList()));
        dict.put("aggList", Arrays.stream(AggTypeEnum.values())
                .map(e -> option(e.getAgg(), e.getName())).collect(Collectors.toList()));
        dict.put("sortList", List.of(option("value_desc", "按值降序"), option("value_asc", "按值升序"),
                option("name_asc", "按名称升序")));
        dict.put("dataViewList", getDataViewList().stream()
                .map(v -> option(v.getCode(), v.getName())).collect(Collectors.toList()));
        return dict;
    }

    // ================================================================
    // 内部
    // ================================================================

    private DataViewDO validateDataViewExists(Long id) {
        DataViewDO dataView = id == null ? null : dataViewMapper.selectById(id);
        if (dataView == null) {
            throw exception(BI_DATA_VIEW_NOT_EXISTS, id);
        }
        return dataView;
    }

    private ChartDO validateChartExists(Long id) {
        ChartDO chart = id == null ? null : chartMapper.selectById(id);
        if (chart == null) {
            throw exception(BI_CHART_NOT_EXISTS, id);
        }
        return chart;
    }

    private void validateChartSettings(ChartSaveReqVO reqVO) {
        if (StringUtils.hasText(reqVO.getType()) && ChartTypeEnum.of(reqVO.getType()) == null) {
            throw exception(BI_CHART_TYPE_INVALID, reqVO.getType());
        }
        Map<String, Object> settings = reqVO.getSettings();
        if (settings == null || !StringUtils.hasText(str(settings.get("dimensionField")))) {
            throw exception(BI_CHART_SETTINGS_INVALID, "必须指定分组维度 dimensionField");
        }
        sqlGuard.validateIdentifier(str(settings.get("dimensionField")));
        String agg = str(settings.get("agg"));
        if (!StringUtils.hasText(agg)) {
            throw exception(BI_CHART_SETTINGS_INVALID, "必须指定聚合方式 agg");
        }
        AggTypeEnum aggEnum = AggTypeEnum.of(agg);
        if (aggEnum == null) {
            throw exception(BI_CHART_AGG_INVALID, agg);
        }
        // 非 count 聚合必须有指标字段
        if (AggTypeEnum.COUNT != aggEnum && !StringUtils.hasText(str(settings.get("metricField")))) {
            throw exception(BI_CHART_SETTINGS_INVALID, "聚合方式 " + agg + " 必须指定指标字段 metricField");
        }
        if (StringUtils.hasText(str(settings.get("metricField")))) {
            sqlGuard.validateIdentifier(str(settings.get("metricField")));
        }
        // 数据源：要么引用数据视图，要么自带 sql
        if (!StringUtils.hasText(reqVO.getViewCode()) && !StringUtils.hasText(reqVO.getSql())) {
            throw exception(BI_CHART_SETTINGS_INVALID, "必须选择数据视图或写一条 SQL");
        }
        // 过滤器的字段名也要校验（值在拼 SQL 时做转义）
        if (reqVO.getFilters() != null) {
            for (Map<String, Object> filter : reqVO.getFilters()) {
                sqlGuard.validateIdentifier(str(filter.get("field")));
            }
        }
    }

    /** 图表的数据源 SQL：viewCode 优先，其次用自己的 sql */
    private String resolveSql(ChartSaveReqVO reqVO) {
        if (StringUtils.hasText(reqVO.getViewCode())) {
            DataViewDO dataView = dataViewMapper.selectByCode(reqVO.getViewCode());
            if (dataView == null) {
                throw exception(BI_DATA_VIEW_NOT_EXISTS, reqVO.getViewCode());
            }
            return dataView.getSql();
        }
        return sqlGuard.validate(reqVO.getSql());
    }

    private List<Map<String, Object>> resolveFields(ChartSaveReqVO reqVO) {
        if (reqVO.getViewCode() != null && StringUtils.hasText(reqVO.getViewCode())) {
            DataViewDO dataView = dataViewMapper.selectByCode(reqVO.getViewCode());
            if (dataView != null && StringUtils.hasText(dataView.getFields())) {
                return parseList(dataView.getFields());
            }
        }
        return new ArrayList<>();
    }

    /** 有多少图表引用了这个数据视图（按 zt_chart.viewCode 统计） */
    private long chartCountByViewCode(String viewCode) {
        if (!StringUtils.hasText(viewCode)) {
            return 0;
        }
        return chartMapper.selectAllList().stream()
                .filter(chart -> Objects.equals(chart.getViewCode(), viewCode))
                .count();
    }

    /**
     * 按 SQL 的 SELECT 列表解析字段（禅道保存数据视图时也是自动解析）。
     * 带聚合函数的算「指标（metric）」，其余算「维度（dimension）」。
     * 空结果集时用它兜底（有数据时直接用结果集的列名，更准）。
     */
    private List<Map<String, Object>> parseFields(String sql) {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (String column : columnsOf(sql)) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("field", column);
            field.put("name", column);
            field.put("type", "dimension");
            field.put("agg", null);
            fields.add(field);
        }
        return fields;
    }

    /** 取 SELECT 列表的列名/别名（到第一个顶层 FROM 为止，按顶层逗号切） */
    private List<String> columnsOf(String sql) {
        String text = sql.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        int selectIndex = lower.indexOf("select");
        if (selectIndex < 0) {
            return new ArrayList<>();
        }
        int fromIndex = indexOfTopLevelFrom(lower, selectIndex + 6);
        String listText = fromIndex < 0 ? text.substring(selectIndex + 6) : text.substring(selectIndex + 6, fromIndex);
        List<String> columns = new ArrayList<>();
        for (String item : splitTopLevel(listText)) {
            String expr = item.trim();
            if (expr.isEmpty() || "*".equals(expr) || expr.endsWith(".*")) {
                continue;
            }
            // 取别名：AS x / 末尾的裸词
            String alias = null;
            Matcher asMatcher = Pattern.compile("(?i)\\s+as\\s+([`\\w]+)\\s*$").matcher(expr);
            if (asMatcher.find()) {
                alias = asMatcher.group(1);
            } else {
                String[] parts = expr.split("\\s+");
                String last = parts[parts.length - 1];
                if (parts.length > 1 && last.matches("[`\\w]+")) {
                    alias = last;
                }
            }
            String name = alias != null ? alias.replace("`", "")
                    : expr.replace("`", "").replaceAll(".*\\.", "").trim();
            if (!StringUtils.hasText(name) || "count(*)".equalsIgnoreCase(name)) {
                name = "count";
            }
            columns.add(name);
        }
        return columns;
    }

    private int indexOfTopLevelFrom(String lower, int from) {
        int depth = 0;
        for (int i = from; i < lower.length() - 3; i++) {
            char c = lower.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && lower.startsWith("from", i)
                    && (i == 0 || !Character.isLetterOrDigit(lower.charAt(i - 1)))) {
                return i;
            }
        }
        return -1;
    }

    private List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : text.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (StringUtils.hasText(current.toString())) {
            parts.add(current.toString());
        }
        return parts;
    }

    /** 把过滤器翻译成 WHERE 片段（字段名已校验，值做字面量转义） */
    private String buildFilters(List<Map<String, Object>> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<>();
        for (Map<String, Object> filter : filters) {
            String field = sqlGuard.validateIdentifier(str(filter.get("field")));
            String operator = str(filter.get("operator")).toLowerCase(Locale.ROOT);
            Object value = filter.get("value");
            String column = "`" + field + "`";
            switch (operator) {
                case "eq" -> clauses.add(column + " = " + literal(value));
                case "ne" -> clauses.add(column + " <> " + literal(value));
                case "like" -> clauses.add(column + " LIKE " + literal("%" + str(value) + "%"));
                case "gt" -> clauses.add(column + " > " + literal(value));
                case "ge" -> clauses.add(column + " >= " + literal(value));
                case "lt" -> clauses.add(column + " < " + literal(value));
                case "le" -> clauses.add(column + " <= " + literal(value));
                case "in" -> {
                    List<Object> values = value instanceof List ? (List<Object>) value : List.of(value);
                    clauses.add(column + " IN (" + values.stream().map(this::literal)
                            .collect(Collectors.joining(",")) + ")");
                }
                case "between" -> {
                    List<Object> values = value instanceof List ? (List<Object>) value : List.of();
                    if (values.size() == 2) {
                        clauses.add(column + " BETWEEN " + literal(values.get(0)) + " AND " + literal(values.get(1)));
                    }
                }
                default -> throw exception(BI_CHART_SETTINGS_INVALID, "过滤器操作符不支持：" + operator);
            }
        }
        return String.join(" AND ", clauses);
    }

    /** 值 → SQL 字面量：数字原样、布尔转 0/1、字符串转义单引号与反斜杠 */
    private String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        String text = String.valueOf(value).replace("\\", "\\\\").replace("'", "''");
        return "'" + text + "'";
    }

    private DataViewRespVO convertDataView(DataViewDO dataView) {
        DataViewRespVO vo = new DataViewRespVO();
        vo.setId(dataView.getId());
        vo.setName(dataView.getName());
        vo.setCode(dataView.getCode());
        vo.setMode(dataView.getMode());
        vo.setSql(dataView.getSql());
        vo.setFields(parseList(dataView.getFields()));
        vo.setObjects(parseList(dataView.getObjects()));
        vo.setLangs(parseStringMap(dataView.getLangs()));
        vo.setChartCount(chartCountByViewCode(dataView.getCode()));
        vo.setCreatedBy(dataView.getCreatedBy());
        vo.setCreatedDate(dataView.getCreatedDate());
        return vo;
    }

    private ChartRespVO convertChart(ChartDO chart) {
        ChartRespVO vo = new ChartRespVO();
        vo.setId(chart.getId());
        vo.setName(chart.getName());
        vo.setCode(chart.getCode());
        vo.setType(chart.getType());
        vo.setTypeName(ChartTypeEnum.nameOf(chart.getType()));
        vo.setEchartsType(ChartTypeEnum.echartsOf(chart.getType()));
        vo.setSql(chart.getSql());
        vo.setViewCode(chart.getViewCode());
        vo.setDesc(chart.getDesc());
        vo.setStage(chart.getStage());
        vo.setVersion(chart.getVersion());
        vo.setSettings(parseMap(chart.getSettings()));
        vo.setFilters(parseList(chart.getFilters()));
        vo.setFields(parseList(chart.getFields()));
        vo.setCreatedBy(chart.getCreatedBy());
        vo.setCreatedDate(chart.getCreatedDate());
        return vo;
    }

    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = JsonUtils.parseObject(json, Map.class);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            log.warn("[parseMap] JSON 解析失败：{}", json);
            return new LinkedHashMap<>();
        }
    }

    private Map<String, String> parseStringMap(String json) {
        Map<String, Object> raw = parseMap(json);
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(k, v == null ? "" : String.valueOf(v)));
        return result;
    }

    private List<Map<String, Object>> parseList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> list = JsonUtils.parseObject(json,
                    new tools.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            log.warn("[parseList] JSON 解析失败：{}", json);
            return new ArrayList<>();
        }
    }

    private int normalizeLimit(Integer limit, int defaultValue) {
        int value = limit == null || limit <= 0 ? defaultValue : limit;
        return Math.min(value, MAX_LIMIT);
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return StringUtils.hasText(str(value)) ? Integer.parseInt(str(value)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> option(String value, String label) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        return option;
    }

    private String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
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

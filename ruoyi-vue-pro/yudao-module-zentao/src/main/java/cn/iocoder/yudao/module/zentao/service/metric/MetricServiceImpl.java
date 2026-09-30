package cn.iocoder.yudao.module.zentao.service.metric;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricCalcRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricDataRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.metric.MetricDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.metric.MetricLibDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.metric.MetricLibMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.metric.MetricMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.enums.metric.MetricCalcTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.metric.MetricDateTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.metric.MetricPurposeEnum;
import cn.iocoder.yudao.module.zentao.enums.metric.MetricScopeEnum;
import cn.iocoder.yudao.module.zentao.enums.metric.MetricUnitEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 度量 Service 实现。
 *
 * <p>业务规则来源：禅道 {@code module/metric/model.php} + {@code tao.php} + {@code calc.class.php}。
 *
 * <h3>三块职责</h3>
 * <pre>
 *   zt_metric     定义：目的/范围/对象/单位/时间维度/口径说明（内置口径由代码导入，见 41-zt_metric.sql）
 *   口径          Java 注册表 MetricRegistry（禅道是 module/metric/calc 下的 414 个 calc 类）
 *   zt_metriclib  数据：一行 = 维度组合 + 时间粒度 + value
 * </pre>
 *
 * <h3>计算流程（照抄禅道）</h3>
 * <ol>
 *   <li>跑口径拿到「维度 → 值」的行（维度列名与库表列同名，不做映射）</li>
 *   <li>按记录里出现的时间列决定**周期**（year / year+month / year+week / year+month+day），
 *       全都没有时间列 → nodate（快照）</li>
 *   <li>清旧数据：周期型按周期清（禅道 {@code clearOutDatedRecords}），
 *       nodate 型清「今天的快照」（禅道查询也只看 {@code date >= today}）</li>
 *   <li>写入 zt_metriclib，并回写 zt_metric 的 {@code lastCalcRows/lastCalcTime}</li>
 * </ol>
 *
 * <h3>与禅道的差异</h3>
 * <ul>
 *   <li>口径只迁移了 15 个（能用已迁表算出来的），其余 code 计算时明确报错，
 *       不返回算错的值 —— 见 {@link MetricRegistry}</li>
 *   <li>禅道由定时任务（cron/collector）驱动计算，本实现保留 {@code calcType=cron|inference}
 *       两种来源但**只提供手动触发**，定时部分留给后续接 yudao 的定时任务</li>
 * </ul>
 */
@Slf4j
@Service
public class MetricServiceImpl implements MetricService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MetricMapper metricMapper;

    @Resource
    private MetricLibMapper metricLibMapper;

    @Resource
    private MetricRegistry metricRegistry;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private AdminUserApi adminUserApi;

    // ================================================================
    // 度量项
    // ================================================================

    @Override
    public PageResult<MetricRespVO> getMetricPage(MetricPageReqVO reqVO) {
        // 「只看已迁移口径」用注册表的 code 集合过滤（注册表是内存里的，直接翻译成 in 条件）
        Collection<String> codes = Boolean.TRUE.equals(reqVO.getOnlyImplemented()) ? metricRegistry.getCodes() : null;
        if (codes != null && codes.isEmpty()) {
            return PageResult.empty();
        }
        PageResult<MetricDO> page = metricMapper.selectPage(reqVO, codes);
        List<MetricRespVO> list = new ArrayList<>(page.getList().size());
        for (MetricDO metric : page.getList()) {
            list.add(convert(metric));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<MetricRespVO> getMetricList(String purpose, String scope, String object) {
        List<MetricRespVO> list = new ArrayList<>();
        for (MetricDO metric : metricMapper.selectListByFilter(purpose, scope, object)) {
            list.add(convert(metric));
        }
        return list;
    }

    @Override
    public MetricRespVO getMetric(String code) {
        return convert(validateMetricExists(code));
    }

    @Override
    public MetricDO validateMetricExists(String code) {
        MetricDO metric = StringUtils.hasText(code) ? metricMapper.selectByCode(code) : null;
        if (metric == null) {
            throw exception(METRIC_NOT_EXISTS, code);
        }
        return metric;
    }

    @Override
    public Map<String, Object> getDict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("purposeList", Arrays.stream(MetricPurposeEnum.values())
                .map(e -> option(e.getValue(), e.getName())).collect(Collectors.toList()));
        dict.put("scopeList", Arrays.stream(MetricScopeEnum.values())
                .map(e -> option(e.getValue(), e.getName())).collect(Collectors.toList()));
        dict.put("unitList", Arrays.stream(MetricUnitEnum.values())
                .map(e -> option(e.getValue(), e.getName())).collect(Collectors.toList()));
        dict.put("dateTypeList", Arrays.stream(MetricDateTypeEnum.values())
                .map(e -> option(e.getValue(), e.getName())).collect(Collectors.toList()));
        dict.put("objectList", metricMapper.selectListByFilter(null, null, null).stream()
                .map(MetricDO::getObject).filter(StringUtils::hasText).distinct()
                .map(o -> option(o, o)).collect(Collectors.toList()));
        dict.put("calcTypeList", Arrays.stream(MetricCalcTypeEnum.values())
                .map(e -> option(e.getValue(), e.getName())).collect(Collectors.toList()));
        return dict;
    }

    private Map<String, Object> option(String value, String name) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", name);
        return option;
    }

    @Override
    public Map<String, Object> getSummary() {
        List<MetricDO> all = metricMapper.selectListByFilter(null, null, null);
        long implemented = all.stream().filter(m -> metricRegistry.isImplemented(m.getCode())).count();
        LocalDateTime lastCalc = all.stream().map(MetricDO::getLastCalcTime).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", all.size());
        summary.put("implementedCount", implemented);
        summary.put("pendingCount", all.size() - implemented);
        summary.put("lastCalcTime", lastCalc == null ? null : lastCalc.format(DATE_TIME));
        summary.put("dataCount", all.stream().mapToLong(m -> {
            Long n = metricLibMapper.countByCode(m.getCode(), m.getDateType());
            return n == null ? 0 : n;
        }).sum());
        return summary;
    }

    @Override
    public Set<String> getImplementedCodes() {
        return metricRegistry.getCodes();
    }

    // ================================================================
    // 计算
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetricCalcRespVO calcMetric(String code, String calcType) {
        MetricDO metric = validateMetricExists(code);
        List<Map<String, Object>> rows = metricRegistry.calculate(code);
        if (rows == null) {
            throw exception(METRIC_CALC_NOT_IMPLEMENTED, code);
        }
        String type = StringUtils.hasText(calcType) ? calcType : MetricCalcTypeEnum.INFERENCE.getValue();
        LocalDateTime now = LocalDateTime.now();
        String cycle = cycleOf(rows, metric);

        // ① 清旧数据：周期型按周期清，nodate 清「今天的快照」
        if (MetricDateTypeEnum.NODATE.getValue().equals(cycle)) {
            metricLibMapper.deleteByDateFrom(code, LocalDate.now().atStartOfDay());
        } else {
            // 同一次计算可能跨多个周期（例如按年统计会一次算出好几年的数据）→ 按周期逐个清
            Set<String> periods = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                periods.add(periodKey(cycle, row));
            }
            for (String period : periods) {
                String[] parts = period.split("_", -1);
                metricLibMapper.deleteByPeriod(code,
                        parts[0].isEmpty() ? null : parts[0],
                        parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null,
                        parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null,
                        parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null);
            }
        }

        // ② 写数据：维度列 + 时间列 + value（维度列名与库表列同名，不用映射）
        String account = currentAccount();
        boolean systemScope = MetricScopeEnum.SYSTEM.getValue().equals(metric.getScope());
        for (Map<String, Object> row : rows) {
            MetricLibDO record = new MetricLibDO();
            record.setMetricID(metric.getId());
            record.setMetricCode(code);
            record.setSystem(systemScope ? 1 : 0);
            record.setProgram(toLong(row.get("program")));
            record.setProject(toLong(row.get("project")));
            record.setProduct(toLong(row.get("product")));
            record.setExecution(toLong(row.get("execution")));
            record.setCode(toStr(row.get("code")));
            record.setPipeline(toStr(row.get("pipeline")));
            record.setRepo(toStr(row.get("repo")));
            record.setUser(toStr(row.get("user")));
            record.setDept(toStr(row.get("dept")));
            record.setYear(toStr(row.get("year")));
            record.setMonth(toStr(row.get("month")));
            record.setWeek(toStr(row.get("week")));
            record.setDay(toStr(row.get("day")));
            record.setValue(toStr(row.get("value")));
            record.setCalcType(type);
            record.setCalculatedBy(MetricCalcTypeEnum.CRON.getValue().equals(type) ? "system" : account);
            // nodate 型靠 date 取「今天的快照」，所以每条都写计算时间
            record.setDate(now);
            metricLibMapper.insert(record);
        }

        // ③ 回写度量项的计算信息（禅道 insertMetricLib 里同步做）
        MetricDO update = new MetricDO();
        update.setId(metric.getId());
        update.setLastCalcRows(rows.size());
        update.setLastCalcTime(now);
        metricMapper.updateById(update);

        MetricCalcRespVO result = new MetricCalcRespVO();
        result.setCode(code);
        result.setName(metric.getName());
        result.setRecordCount(rows.size());
        result.setCycle(cycle);
        result.setCalcType(type);
        result.setCalcTime(now);
        log.info("[calcMetric] 度量项 {} 计算完成：{} 条，周期 {}，方式 {}", code, rows.size(), cycle, type);
        return result;
    }

    @Override
    public List<MetricCalcRespVO> calcAll(String calcType) {
        List<MetricCalcRespVO> results = new ArrayList<>();
        for (String code : metricRegistry.getCodes()) {
            try {
                results.add(calcMetric(code, calcType));
            } catch (Exception e) {
                // 批量计算不因为单个度量项失败而中断（禅道 zen.php 里也是 try/catch 每个 calc 组）
                log.warn("[calcAll] 度量项 {} 计算失败：{}", code, e.getMessage());
            }
        }
        return results;
    }

    /**
     * 从记录里推断时间粒度（禅道 {@code getMetricCycle}）：
     * 只有 year → year；year+month → month；year+week → week；year+month+day → day；都没有 → nodate。
     */
    private String cycleOf(List<Map<String, Object>> rows, MetricDO metric) {
        for (Map<String, Object> row : rows) {
            boolean hasYear = StringUtils.hasText(toStr(row.get("year")));
            boolean hasMonth = StringUtils.hasText(toStr(row.get("month")));
            boolean hasWeek = StringUtils.hasText(toStr(row.get("week")));
            boolean hasDay = StringUtils.hasText(toStr(row.get("day")));
            if (hasYear && hasMonth && hasDay) {
                return MetricDateTypeEnum.DAY.getValue();
            }
            if (hasYear && hasWeek) {
                return MetricDateTypeEnum.WEEK.getValue();
            }
            if (hasYear && hasMonth) {
                return MetricDateTypeEnum.MONTH.getValue();
            }
            if (hasYear) {
                return MetricDateTypeEnum.YEAR.getValue();
            }
        }
        // 没有数据时退回度量项声明的时间维度（保证空结果也能给出周期）
        return StringUtils.hasText(metric.getDateType()) ? metric.getDateType() : MetricDateTypeEnum.NODATE.getValue();
    }

    private String periodKey(String cycle, Map<String, Object> row) {
        String year = toStr(row.get("year"));
        String month = toStr(row.get("month"));
        String week = toStr(row.get("week"));
        String day = toStr(row.get("day"));
        if (MetricDateTypeEnum.YEAR.getValue().equals(cycle)) {
            return year + "___";
        }
        if (MetricDateTypeEnum.MONTH.getValue().equals(cycle)) {
            return year + "_" + month + "__";
        }
        if (MetricDateTypeEnum.WEEK.getValue().equals(cycle)) {
            return year + "__" + week + "_";
        }
        return year + "_" + month + "_" + day;
    }

    // ================================================================
    // 数据查询
    // ================================================================

    @Override
    public MetricDataRespVO getMetricData(String code, String scope, String dateBegin, String dateEnd,
                                          Integer pageNo, Integer pageSize) {
        MetricDO metric = validateMetricExists(code);
        String queryScope = StringUtils.hasText(scope) ? scope : metric.getScope();
        // nodate（快照）型只取「今天算的」那一份，由 Mapper 的 applyNodateFilter 统一挡掉历史快照。
        // 注意：这几条读当前值的路径必须都带上 dateType，否则历史快照会被当成当前值重复统计。
        List<MetricLibDO> records = metricLibMapper.selectListByCode(code, queryScope, metric.getDateType());
        // 时间维度过滤（禅道 tao.php 的 processDAOWithDate：按 dateType 决定比 year / year+month / ...）
        records = filterByDate(records, metric.getDateType(), dateBegin, dateEnd);
        long total = records.size();
        // 内存分页：库里的行数按「维度 × 周期」展开，量级可控（度量数据本来就是聚合后的结果）
        int from = Math.max(0, ((pageNo == null ? 1 : pageNo) - 1) * (pageSize == null ? 10 : pageSize));
        int to = Math.min(records.size(), from + (pageSize == null ? 10 : pageSize));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (from < to) {
            rows = convertRows(records.subList(from, to), queryScope);
        }
        MetricDataRespVO resp = new MetricDataRespVO();
        resp.setMetric(convert(metric));
        resp.setRows(rows);
        resp.setTotal(total);
        return resp;
    }

    private List<MetricLibDO> filterByDate(List<MetricLibDO> records, String dateType, String begin, String end) {
        if (!StringUtils.hasText(begin) && !StringUtils.hasText(end)) {
            return records;
        }
        return records.stream().filter(r -> {
            String key = dateKey(r, dateType);
            if (!StringUtils.hasText(key)) {
                return true;
            }
            if (StringUtils.hasText(begin) && key.compareTo(normalize(begin)) < 0) {
                return false;
            }
            return !StringUtils.hasText(end) || key.compareTo(normalize(end)) <= 0;
        }).collect(Collectors.toList());
    }

    /** 把 2026-03-01 这样的日期按 dateType 截成可与记录比较的 key */
    private String normalize(String date) {
        return date.replace("-", "");
    }

    private String dateKey(MetricLibDO record, String dateType) {
        if (MetricDateTypeEnum.YEAR.getValue().equals(dateType)) {
            return record.getYear();
        }
        if (MetricDateTypeEnum.MONTH.getValue().equals(dateType)) {
            return nvl(record.getYear()) + nvl(record.getMonth());
        }
        if (MetricDateTypeEnum.WEEK.getValue().equals(dateType)) {
            return nvl(record.getYear()) + nvl(record.getWeek());
        }
        if (MetricDateTypeEnum.DAY.getValue().equals(dateType)) {
            return nvl(record.getYear()) + nvl(record.getMonth()) + nvl(record.getDay());
        }
        return null;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    /** 记录 → 展示行（维度 + 时间 + 值 + 对象名），对象名让界面不用再查一遍 */
    private List<Map<String, Object>> convertRows(List<MetricLibDO> records, String scope) {
        List<Map<String, Object>> rows = new ArrayList<>(records.size());
        Map<Long, String> productNames = new HashMap<>();
        Map<Long, String> projectNames = new HashMap<>();
        Map<String, String> userNames = resolveUserNames(records, scope);
        for (MetricLibDO record : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", record.getId());
            row.put("metricCode", record.getMetricCode());
            row.put("scope", scope);
            row.put("scopeObjectId", scopeObjectId(record, scope));
            row.put("scopeObjectName", scopeObjectName(record, scope, productNames, projectNames, userNames));
            row.put("year", record.getYear());
            row.put("month", record.getMonth());
            row.put("week", record.getWeek());
            row.put("day", record.getDay());
            row.put("value", record.getValue());
            row.put("date", record.getDate() == null ? null : record.getDate().format(DATE_TIME));
            row.put("calcType", record.getCalcType());
            row.put("calculatedBy", record.getCalculatedBy());
            row.put("period", periodLabel(record));
            rows.add(row);
        }
        return rows;
    }

    private String periodLabel(MetricLibDO record) {
        if (StringUtils.hasText(record.getYear()) && StringUtils.hasText(record.getWeek())) {
            return record.getYear() + " 年第 " + record.getWeek() + " 周";
        }
        if (StringUtils.hasText(record.getYear()) && StringUtils.hasText(record.getMonth())
                && StringUtils.hasText(record.getDay())) {
            return record.getYear() + "-" + record.getMonth() + "-" + record.getDay();
        }
        if (StringUtils.hasText(record.getYear()) && StringUtils.hasText(record.getMonth())) {
            return record.getYear() + "-" + record.getMonth();
        }
        if (StringUtils.hasText(record.getYear())) {
            return record.getYear() + " 年";
        }
        return record.getDate() == null ? "快照" : record.getDate().toLocalDate().toString() + "（快照）";
    }

    private Long scopeObjectId(MetricLibDO record, String scope) {
        if (MetricScopeEnum.PRODUCT.getValue().equals(scope)) {
            return record.getProduct();
        }
        if (MetricScopeEnum.PROJECT.getValue().equals(scope)) {
            return record.getProject();
        }
        if (MetricScopeEnum.EXECUTION.getValue().equals(scope)) {
            return record.getExecution();
        }
        if (MetricScopeEnum.PROGRAM.getValue().equals(scope)) {
            return record.getProgram();
        }
        return null;
    }

    private String scopeObjectName(MetricLibDO record, String scope, Map<Long, String> productNames,
                                   Map<Long, String> projectNames, Map<String, String> userNames) {
        if (MetricScopeEnum.PRODUCT.getValue().equals(scope) && record.getProduct() != null && record.getProduct() > 0) {
            return productNames.computeIfAbsent(record.getProduct(), id -> {
                ProductDO product = productMapper.selectById(id);
                return product == null ? "#" + id : product.getName();
            });
        }
        if ((MetricScopeEnum.PROJECT.getValue().equals(scope) || MetricScopeEnum.EXECUTION.getValue().equals(scope))
                && ((record.getProject() != null && record.getProject() > 0)
                || (record.getExecution() != null && record.getExecution() > 0))) {
            Long id = MetricScopeEnum.PROJECT.getValue().equals(scope) ? record.getProject() : record.getExecution();
            return projectNames.computeIfAbsent(id, key -> {
                ProjectDO project = projectMapper.selectById(key);
                return project == null ? "#" + key : project.getName();
            });
        }
        if (MetricScopeEnum.USER.getValue().equals(scope) && StringUtils.hasText(record.getUser())) {
            return userNames.getOrDefault(record.getUser(), record.getUser());
        }
        if (MetricScopeEnum.SYSTEM.getValue().equals(scope)) {
            return "系统";
        }
        return "-";
    }

    private Map<String, String> resolveUserNames(List<MetricLibDO> records, String scope) {
        Map<String, String> result = new HashMap<>();
        if (!MetricScopeEnum.USER.getValue().equals(scope)) {
            return result;
        }
        Set<String> accounts = records.stream().map(MetricLibDO::getUser)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        if (accounts.isEmpty()) {
            return result;
        }
        try {
            for (AdminUserRespDTO user : adminUserApi.getUserListByUsernames(accounts)) {
                result.put(user.getUsername(), StringUtils.hasText(user.getNickname())
                        ? user.getNickname() : user.getUsername());
            }
        } catch (Exception e) {
            log.warn("[resolveUserNames] 取用户昵称失败：{}", e.getMessage());
        }
        return result;
    }

    // ================================================================
    // 工具
    // ================================================================

    private MetricRespVO convert(MetricDO metric) {
        MetricRespVO vo = BeanUtils.toBean(metric, MetricRespVO.class);
        vo.setPurposeName(MetricPurposeEnum.nameOf(metric.getPurpose()));
        vo.setScopeName(MetricScopeEnum.nameOf(metric.getScope()));
        vo.setUnitName(MetricUnitEnum.nameOf(metric.getUnit()));
        vo.setDateTypeName(MetricDateTypeEnum.nameOf(metric.getDateType()));
        vo.setObjectName(metric.getObject());
        vo.setBuiltin(metric.getBuiltin() != null && metric.getBuiltin() == 1);
        vo.setImplemented(metricRegistry.isImplemented(metric.getCode()));
        Long count = metricLibMapper.countByCode(metric.getCode(), metric.getDateType());
        vo.setDataCount(count == null ? 0 : count);
        return vo;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? Long.valueOf(text.trim()) : 0L;
    }

    private String toStr(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            // 年份/月份这类数值列在库里是 char，转字符串时不要出现 2026.0
            String text = number.toString();
            if (number instanceof Double || number instanceof Float) {
                text = text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
            }
            return text;
        }
        return String.valueOf(value);
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

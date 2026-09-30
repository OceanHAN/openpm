package cn.iocoder.yudao.module.zentao.service.burn;

import cn.iocoder.yudao.module.zentao.controller.admin.burn.vo.BurnChartRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.burn.BurnDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.burn.BurnMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.enums.execution.ExecutionTypeEnum;
import cn.iocoder.yudao.module.zentao.service.holiday.HolidayService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.BURN_CANNOT_COMPUTE;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.BURN_DATE_REQUIRED;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.EXECUTION_NOT_EXISTS;

/**
 * 执行燃尽图实现（禅道 {@code execution::computeBurn} / {@code buildBurnData} / {@code getBurnDataFlot}）。
 *
 * <h3>核心是「快照 + 补齐」两件事</h3>
 * <ol>
 *   <li><b>快照</b>：{@code computeBurn} 把当天所有任务的 estimate/left/consumed 汇总成一行写进 {@code zt_burn}。
 *       历史行不再改，所以任务后来被改、被删，已经画出来的曲线不会跟着变；</li>
 *   <li><b>补齐</b>：某天没跑 computeBurn 就没有那一行，画图时要**用前一个有值的日期补上**
 *       （禅道 {@code report::createSingleJSON} 的 preValue），否则曲线会断成一段段。</li>
 * </ol>
 *
 * <h3>与禅道的三处有意偏离</h3>
 * <ol>
 *   <li><b>数组长度对齐</b>：禅道在「今天之后」的日期上会直接 {@code break}，返回的 burnLine 比 labels 短；
 *       本实现改用 {@code null} 对齐（今天之后无数据），前端不用自己补空位、ECharts 画出来一样是断线；</li>
 *   <li><b>isParent</b>：禅道汇总任务时会排除父任务，本实现的 {@code zt_task} 没有 isParent 列（父子任务还没做），
 *       所以这条条件省掉；</li>
 *   <li><b>status 取值</b>：禅道按 {@code done/closed/suspended} 判「不再计算」，本实现同义，
 *       只是我们的状态枚举里没有 done（用 closed 表达结束）。</li>
 * </ol>
 */
@Service
@Slf4j
public class BurnServiceImpl implements BurnService {

    /** 禅道 {@code config->execution->maxBurnDay}：一张图最多 31 个点，多了就按间隔采样 */
    private static final int MAX_BURN_DAY = 31;

    private static final List<String> BURN_FIELDS = List.of("left", "estimate", "consumed", "storyPoint");

    @Resource
    private BurnMapper burnMapper;

    @Resource
    private ProjectMapper projectMapper;

    /** 工作日口径的唯一出口：跳周末 + 跳假期 + 补班日算工作日（禅道 getActualWorkingDays） */
    @Resource
    private HolidayService holidayService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<BurnDO> computeBurn(Long executionId) {
        List<ProjectDO> executions = projectMapper.selectList(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ProjectDO>()
                        .in(ProjectDO::getType, List.of(ExecutionTypeEnum.SPRINT.getType(),
                                ExecutionTypeEnum.STAGE.getType()))
                        .eqIfPresent(ProjectDO::getId, executionId)
                        .last("AND (lifetime IS NULL OR lifetime <> 'ops')")
                        .notIn(ProjectDO::getStatus, List.of("closed", "suspended")));

        List<BurnDO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (ProjectDO execution : executions) {
            Map<String, Object> sum = burnMapper.selectTaskSum(execution.getId());
            BigDecimal estimate = decimal(sum.get("estimate"));
            BigDecimal left = decimal(sum.get("left"));
            BigDecimal consumed = decimal(sum.get("consumed"));

            // 已完成/已关闭的任务不该再算进「还剩多少」和「原计划多少」
            BigDecimal closedLeft = decimal(burnMapper.selectClosedLeft(execution.getId()));
            BigDecimal finishedEstimate = decimal(burnMapper.selectFinishedEstimate(execution.getId()));
            left = left.subtract(closedLeft);
            estimate = estimate.subtract(finishedEstimate);
            BigDecimal storyPoint = decimal(burnMapper.selectStoryPoint(execution.getId()));

            burnMapper.replaceBurn(execution.getId(), today, estimate, left, consumed, storyPoint);

            BurnDO burn = new BurnDO();
            burn.setExecution(execution.getId());
            burn.setDate(today);
            burn.setEstimate(estimate);
            burn.setLeft(left);
            burn.setConsumed(consumed);
            burn.setStoryPoint(storyPoint);
            result.add(burn);
        }
        return result;
    }

    @Override
    public BurnChartRespVO getBurnData(Long executionId, String type, String burnBy, Integer interval) {
        ProjectDO execution = projectMapper.selectById(executionId);
        if (execution == null) {
            throw exception(EXECUTION_NOT_EXISTS);
        }
        if (!ExecutionTypeEnum.isExecution(execution.getType())) {
            throw exception(BURN_CANNOT_COMPUTE, execution.getType());
        }
        if (execution.getBegin() == null || execution.getEnd() == null) {
            throw exception(BURN_DATE_REQUIRED);
        }

        String burnField = StringUtils.hasText(burnBy) && BURN_FIELDS.contains(burnBy) ? burnBy : "left";
        String dateType = StringUtils.hasText(type) ? type : "noweekend";

        LocalDate today = LocalDate.now();
        // 截止日：进行中=今天；已关闭=关闭日；已挂起=挂起日（禅道 burn() 里的 deadline 计算）
        LocalDate deadline = today;
        if ("closed".equals(execution.getStatus()) && execution.getClosedDate() != null) {
            deadline = execution.getClosedDate().toLocalDate();
        } else if ("suspended".equals(execution.getStatus()) && execution.getSuspendedDate() != null) {
            deadline = execution.getSuspendedDate().toLocalDate();
        }

        // 已经过了计划结束日还没结束 → 自动带上延期段（禅道同样这么处理）
        boolean delayed = deadline.isAfter(execution.getEnd());
        if (delayed && !dateType.contains("withdelay")) {
            dateType = dateType + ",withdelay";
        }
        boolean withDelay = dateType.contains("withdelay");
        // 不带 withdelay 时横轴只画到计划结束日；带了就画到今天（延期段也要看得到）
        LocalDate rangeEnd = withDelay ? deadline : execution.getEnd();

        // 采样间隔要在生成日期列表**之前**定好：没传就按「工作日总数 / maxBurnDay」自动算，
        // 否则会把整段日期原样返回（踩过：先建列表再算间隔，等于没采样）
        int step = interval != null && interval > 0
                ? interval : weekdays(execution.getBegin(), rangeEnd, dateType).size() / MAX_BURN_DAY;
        List<LocalDate> dateList = dateList(execution.getBegin(), rangeEnd, dateType, step, execution.getEnd());

        List<BurnDO> rows = burnMapper.selectListByRange(executionId, execution.getBegin(), deadline);

        // 禅道把同一批快照按「计划结束日」切成两条线：计划内（burnLine）与延期段（delayLine），
        // 不属于自己那一段的日期一律置 null，所以两条线在计划结束日那天交汇。
        Map<LocalDate, Double> normalSets = new LinkedHashMap<>();
        Map<LocalDate, Double> delaySets = new LinkedHashMap<>();
        Double firstValue = 0.0;
        boolean firstSeen = false;
        for (BurnDO row : rows) {
            if (row.getDate() == null || row.getDate().isBefore(execution.getBegin())) {
                continue;
            }
            Double value = valueOf(row, burnField);
            boolean afterEnd = row.getDate().isAfter(execution.getEnd());
            boolean beforeEnd = row.getDate().isBefore(execution.getEnd());
            normalSets.put(row.getDate(), afterEnd ? null : value);
            delaySets.put(row.getDate(), beforeEnd ? null : value);
            if (!firstSeen) {
                firstSeen = true;
                firstValue = afterEnd ? 0.0 : value;
            }
        }
        for (LocalDate date : dateList) {
            if (!normalSets.containsKey(date) && date.isAfter(execution.getEnd())) {
                normalSets.put(date, null);
            }
            if (!delaySets.containsKey(date) && date.isBefore(execution.getEnd())) {
                delaySets.put(date, null);
            }
        }

        List<Double> burnLine = carryForward(normalSets, dateList, today);
        List<Double> delayLine = delayed || withDelay ? carryForward(delaySets, dateList, today) : null;

        // 理想线：从第一天的工作量线性降到「计划结束日」为 0
        int days = withDelay ? dateList.indexOf(execution.getEnd()) : dateList.size() - 1;
        if (days < 0) {
            days = dateList.size() - 1;
        }
        double rate = days > 0 ? firstValue / days : 0d;
        List<Double> baseLine = new ArrayList<>();
        for (int i = 0; i < dateList.size(); i++) {
            double value = i > days ? 0d : round3((days - i) * rate);
            baseLine.add(value);
        }

        BurnChartRespVO vo = new BurnChartRespVO();
        vo.setExecutionId(executionId);
        vo.setExecutionName(execution.getName());
        vo.setBegin(execution.getBegin().toString());
        vo.setEnd(execution.getEnd().toString());
        vo.setBurnBy(burnField);
        vo.setType(dateType);
        vo.setInterval(step);
        vo.setFirstValue(firstValue);
        vo.setLabels(dateList.stream().map(LocalDate::toString).toList());
        vo.setBurnLine(burnLine);
        vo.setBaseLine(baseLine);
        vo.setDelayLine(delayLine);
        vo.setRows(rows.stream().map(this::toRowMap).toList());
        return vo;
    }

    // ==================== 内部 ====================

    /**
     * 生成横轴日期列表（禅道 {@code date::getDateList} + {@code execution::getDateList}）。
     *
     * @param type       含 noweekend 时跳过周末；含 weekend 时含周末
     * @param interval   采样间隔；不传时按「总数 / maxBurnDay」自动算
     * @param deadline   执行计划结束日，采样时**永远保留**这一天（否则理想线断在没有 0 点的位置）
     */
    private List<LocalDate> dateList(LocalDate begin, LocalDate end, String type, int step, LocalDate deadline) {
        List<LocalDate> all = weekdays(begin, end, type);
        if (step <= 0) {
            return all;
        }
        List<LocalDate> sampled = new ArrayList<>();
        int counter = step;
        for (LocalDate date : all) {
            counter++;
            if (date.equals(deadline)) {
                sampled.add(date); // 计划结束日永远保留，否则理想线断在没有 0 点的位置
                continue;
            }
            if (counter <= step) {
                continue;
            }
            sampled.add(date);
            counter = 0;
        }
        return sampled;
    }

    /**
     * 生成横轴的候选日期。
     *
     * <p>{@code noweekend} 走 {@link HolidayService#getActualWorkingDays}：
     * **跳周末 + 跳假期 + 补班日算工作日**（禅道全项目的工作日唯一出口）；
     * {@code weekend} 则按自然日，一天不落。
     *
     * <p>注意 {@code getActualWorkingDays} 是左闭右开的，而横轴最后一格（计划结束日/今天）
     * 必须画出来 —— 所以这里对区间末尾单独补一天，再排序去重。
     */
    private List<LocalDate> weekdays(LocalDate begin, LocalDate end, String type) {
        if (!type.contains("noweekend")) {
            List<LocalDate> all = new ArrayList<>();
            for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
                all.add(date);
            }
            return all;
        }
        List<LocalDate> days = new ArrayList<>(holidayService.getActualWorkingDays(begin, end));
        LocalDate last = end;
        if (!days.contains(last) && holidayService.isWorkday(last)) {
            days.add(last);
        }
        days.sort(LocalDate::compareTo);
        return days;
    }

    /**
     * 缺失的日期用**前一个有值的日期**补上（禅道 {@code report::createSingleJSON} 的 preValue）。
     * 今天之后没有数据，返回 null 对齐 labels（禅道在这里会直接截断数组）。
     */
    private List<Double> carryForward(Map<LocalDate, Double> sets, List<LocalDate> dateList, LocalDate today) {
        List<Double> line = new ArrayList<>();
        Double preValue = 0.0;
        // 禅道 report::createSingleJSON 是**按自然日**推进 preValue 的（`for(...) { $current += 86400 }`
        // 里再判断当天在不在 dateList），所以落在周末/节假日那一天的快照，会被后面第一个工作日继承。
        // 早先这里只遍历 labels（工作日），等于把非工作日的快照整条丢掉 —— 2026-09-29 那次
        // 测试正好把快照插在周六，断言就红了（{0.0} 而不是 {50.0}）。
        // 现在同样按自然日推进：labels 仍只收工作日，但「吃快照」逐日走。
        LocalDate cursor = dateList.isEmpty() ? null : dateList.get(0);
        if (cursor != null && !sets.isEmpty()) {
            LocalDate earliest = sets.keySet().stream().min(LocalDate::compareTo).orElse(cursor);
            if (earliest.isBefore(cursor)) cursor = earliest; // 首个工作日之前的快照也要吃进来
        }
        for (LocalDate date : dateList) {
            if (date.isAfter(today)) {
                line.add(null);
                continue;
            }
            while (cursor != null && !cursor.isAfter(date)) {
                if (sets.containsKey(cursor)) preValue = sets.get(cursor);
                cursor = cursor.plusDays(1);
            }
            line.add(preValue);
        }
        return line;
    }

    private Double valueOf(BurnDO row, String field) {
        BigDecimal value = switch (field) {
            case "estimate" -> row.getEstimate();
            case "consumed" -> row.getConsumed();
            case "storyPoint" -> row.getStoryPoint();
            default -> row.getLeft();
        };
        return value == null ? 0d : value.doubleValue();
    }

    private Map<String, Object> toRowMap(BurnDO row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", row.getDate() == null ? null : row.getDate().toString());
        map.put("estimate", decimal(row.getEstimate()));
        map.put("left", decimal(row.getLeft()));
        map.put("consumed", decimal(row.getConsumed()));
        map.put("storyPoint", decimal(row.getStoryPoint()));
        return map;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static double round3(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

}

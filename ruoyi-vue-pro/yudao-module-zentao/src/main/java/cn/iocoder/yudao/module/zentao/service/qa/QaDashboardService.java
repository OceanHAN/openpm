package cn.iocoder.yudao.module.zentao.service.qa;

import cn.iocoder.yudao.module.zentao.controller.admin.qa.vo.QaDashboardRespVO;
import cn.iocoder.yudao.module.zentao.dal.mysql.qa.QaMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 测试仪表盘（禅道 {@code module/qa}）。
 *
 * <h3>这个模块到底是什么</h3>
 * 禅道的 {@code qa} 只有 153 行、1 个 action：{@code index()} 什么都不算，
 * 它只是把「块（block）」拼成一个看板 —— {@code echo $this->fetch('block', 'dashboard', 'dashboard=qa')}。
 * 看板本身在 {@code module/block}（16,224 行），而看板里那块「质量统计」又去调
 * {@code module/metric} 的度量口径。也就是说：**qa 是「仪表盘页面」，不是「质量业务模块」**。
 *
 * <p>所以本实现做的是**等价覆盖**：不引入 block 的积木引擎、也不依赖 metric 框架，
 * 直接把那四块内容（质量统计 / 待处理缺陷 / 待评审用例 / 未完成测试单）按禅道的口径
 * 用 SQL 算出来（口径出处见 {@link QaMapper} 的类注释）。这比搬 block 引擎划算得多 ——
 * 真正有价值的是口径，不是那套可以配置摆放位置的积木框架。
 */
@Service
@Slf4j
public class QaDashboardService {

    /** 列表类块默认取多少条（禅道看板默认也是 6~8 条，这里统一给 10） */
    private static final int LIST_LIMIT = 10;

    @Resource
    private QaMapper qaMapper;

    /** 测试仪表盘。days = 统计区间（最近 N 天）；product 传了就看单个产品 */
    public QaDashboardRespVO getDashboard(Long product, Integer days) {
        int rangeDays = days == null || days <= 0 ? 7 : days;
        LocalDate begin = LocalDate.now().minusDays(rangeDays - 1L);

        QaDashboardRespVO vo = new QaDashboardRespVO();
        vo.setDays(rangeDays);
        vo.setBegin(begin.toString());
        vo.setProduct(product);

        // ① 按产品的质量统计
        List<Map<String, Object>> productQuality = qaMapper.selectProductQuality(begin.toString());
        vo.setProductQuality(productQuality);

        QaDashboardRespVO.Summary summary = vo.getSummary();
        summary.setProductCount(product == null ? productQuality.size()
                : (int) productQuality.stream().filter(r -> product.equals(longOf(r.get("product")))).count());
        int bugTotal = 0;
        int bugActive = 0;
        int bugFixed = 0;
        int bugEffective = 0;
        int openedInRange = 0;
        int resolvedInRange = 0;
        int closedInRange = 0;
        int testTaskUnclosed = 0;
        int caseWait = 0;
        for (Map<String, Object> row : productQuality) {
            // 传了 product 时只汇总那一行 —— productQuality 永远是全量产品列表（页面要展示对比），
            // 但汇总卡片必须跟着筛选走，否则「看单个产品」时卡片还是全公司的数（踩过）
            if (product != null && !product.equals(longOf(row.get("product")))) {
                continue;
            }
            bugActive += intOf(row.get("active"));
            bugFixed += intOf(row.get("fixed"));
            bugEffective += intOf(row.get("effective"));
            openedInRange += intOf(row.get("opened"));
            resolvedInRange += intOf(row.get("resolved"));
            closedInRange += intOf(row.get("closed"));
            testTaskUnclosed += intOf(row.get("unclosedTestTasks"));
            caseWait += intOf(row.get("reviewCases"));
        }
        summary.setBugActive(bugActive);
        summary.setBugFixed(bugFixed);
        summary.setBugEffective(bugEffective);
        summary.setBugOpenedInRange(openedInRange);
        summary.setBugResolvedInRange(resolvedInRange);
        summary.setBugClosedInRange(closedInRange);
        summary.setTestTaskUnclosed(testTaskUnclosed);
        summary.setCaseWait(caseWait);
        // 修复率的分母是「有效缺陷」而不是缺陷总数（见 QaMapper 的口径说明）
        summary.setBugFixRate(bugEffective == 0 ? 0d
                : BigDecimal.valueOf(bugFixed * 100.0 / bugEffective).setScale(2, RoundingMode.HALF_UP).doubleValue());

        // ② 分布（状态/严重程度/解决方案/用例/测试单）
        vo.setBugStatus(qaMapper.selectBugStatusStat(product));
        vo.setBugSeverity(qaMapper.selectBugSeverityStat(product));
        vo.setBugResolution(qaMapper.selectBugResolutionStat(product));
        vo.setCaseStatus(qaMapper.selectCaseStatusStat(product));
        vo.setCaseResult(qaMapper.selectCaseResultStat(product));
        vo.setTestTaskStatus(qaMapper.selectTestTaskStatusStat(product));

        Map<String, Integer> bugStatusMap = QaDashboardRespVO.toMap(vo.getBugStatus());
        summary.setBugTotal(bugStatusMap.values().stream().mapToInt(Integer::intValue).sum());
        summary.setBugResolved(bugStatusMap.getOrDefault("resolved", 0));
        summary.setBugClosed(bugStatusMap.getOrDefault("closed", 0));
        Map<String, Integer> caseStatusMap = QaDashboardRespVO.toMap(vo.getCaseStatus());
        summary.setCaseTotal(caseStatusMap.values().stream().mapToInt(Integer::intValue).sum());
        Map<String, Integer> testTaskMap = QaDashboardRespVO.toMap(vo.getTestTaskStatus());
        summary.setTestTaskTotal(testTaskMap.values().stream().mapToInt(Integer::intValue).sum());

        // ③ 三个列表块
        vo.setPendingBugs(qaMapper.selectPendingBugs(product, LIST_LIMIT));
        vo.setReviewCases(qaMapper.selectReviewCases(product, LIST_LIMIT));
        vo.setUnclosedTestTasks(qaMapper.selectUnclosedTestTasks(product, LIST_LIMIT));
        return vo;
    }

    private static long longOf(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static int intOf(Object value) {
        if (value == null) {
            return 0;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

}

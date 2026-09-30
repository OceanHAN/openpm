package cn.iocoder.yudao.module.zentao.service.testreport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportCaseVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuiteCaseLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuitePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuiteRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuiteSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.SuiteCaseDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.TestReportDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.TestSuiteDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestResultDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestRunDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestTaskDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.bug.BugMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testcase.CaseMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testreport.SuiteCaseMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testreport.TestReportMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testreport.TestSuiteMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testtask.TestResultMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testtask.TestRunMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testtask.TestTaskMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.testreport.TestSuiteTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.testtask.TestResultEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 用例集 + 测试报告 Service 实现
 *
 * <p>对齐禅道 {@code module/testsuite} 与 {@code module/testreport}。
 *
 * <p>报告的核心是**汇总**（禅道 getResultSummary / getPerCaseResult4Report）：
 * 一条 run 在统计区间里可能跑了很多次，**只取最后一次结果**，
 * 而不是把每次执行都算一遍 —— 否则「先通过后失败」会被算成一次通过一次失败。
 */
@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    @Resource
    private TestSuiteMapper suiteMapper;

    @Resource
    private SuiteCaseMapper suiteCaseMapper;

    @Resource
    private TestReportMapper reportMapper;

    @Resource
    private CaseMapper caseMapper;

    @Resource
    private TestTaskMapper testTaskMapper;

    @Resource
    private BugMapper bugMapper;

    @Resource
    private TestRunMapper testRunMapper;

    @Resource
    private TestResultMapper testResultMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ================================================================
    // 用例集
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSuite(TestSuiteSaveReqVO reqVO) {
        validateSuiteNameUnique(reqVO.getProduct(), reqVO.getName(), null);
        String account = currentAccount();
        TestSuiteDO suite = new TestSuiteDO();
        suite.setProduct(reqVO.getProduct());
        suite.setProject(reqVO.getProject() == null ? 0L : reqVO.getProject());
        suite.setName(reqVO.getName());
        suite.setDesc(reqVO.getDesc() == null ? "" : reqVO.getDesc());
        suite.setType(StringUtils.hasText(reqVO.getType()) ? reqVO.getType() : "public");
        suite.setOrder(reqVO.getOrder() == null ? 0 : reqVO.getOrder());
        suite.setAddedBy(account);
        suite.setAddedDate(LocalDateTime.now());
        suite.setLastEditedBy(account);
        suite.setLastEditedDate(LocalDateTime.now());
        suiteMapper.insert(suite);
        actionService.recordAction("testsuite", suite.getId(), ActionTypeEnum.CREATED, "新建用例集：" + suite.getName());
        return suite.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSuite(TestSuiteSaveReqVO reqVO) {
        TestSuiteDO old = validateSuiteExists(reqVO.getId());
        validateSuiteNameUnique(reqVO.getProduct(), reqVO.getName(), reqVO.getId());
        TestSuiteDO updateObj = new TestSuiteDO();
        updateObj.setId(old.getId());
        updateObj.setProduct(reqVO.getProduct());
        updateObj.setProject(reqVO.getProject());
        updateObj.setName(reqVO.getName());
        updateObj.setDesc(reqVO.getDesc());
        updateObj.setType(reqVO.getType());
        updateObj.setOrder(reqVO.getOrder());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        suiteMapper.updateById(updateObj);
        actionService.recordActionWithChanges("testsuite", old.getId(), ActionTypeEnum.EDITED,
                "修改用例集：" + reqVO.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSuite(Long id) {
        TestSuiteDO suite = validateSuiteExists(id);
        // 【有意偏离禅道】禅道删集合不检查里面还有没有用例（关系数据会变成孤儿）。
        // 这里显式拒绝，和「产品下还有需求不能删除」保持同一套保护策略。
        long count = suiteCaseMapper.selectListBySuite(id).size();
        if (count > 0) {
            throw exception(TEST_SUITE_HAS_CASE, count);
        }
        suiteMapper.deleteById(id);
        actionService.recordAction("testsuite", id, ActionTypeEnum.DELETED, "删除用例集：" + suite.getName());
    }

    @Override
    public TestSuiteDO validateSuiteExists(Long id) {
        TestSuiteDO suite = id == null ? null : suiteMapper.selectById(id);
        // 用例集与用例库共用 zt_testsuite：拿用例库的编号来调用用例集接口，应当当作「不存在」，
        // 否则会对库行执行用例集的改名/删除，把两类数据搅在一起
        if (suite == null || TestSuiteTypeEnum.LIBRARY.getType().equals(suite.getType())) {
            throw exception(TEST_SUITE_NOT_EXISTS, id);
        }
        return suite;
    }

    @Override
    public TestSuiteRespVO getSuite(Long id) {
        return convertSuite(validateSuiteExists(id));
    }

    @Override
    public PageResult<TestSuiteRespVO> getSuitePage(TestSuitePageReqVO reqVO) {
        PageResult<TestSuiteDO> page = suiteMapper.selectPage(reqVO);
        List<TestSuiteRespVO> list = new ArrayList<>(page.getList().size());
        for (TestSuiteDO suite : page.getList()) {
            list.add(convertSuite(suite));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<TestSuiteDO> getSuiteListByProduct(Long product) {
        return suiteMapper.selectListByProduct(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int linkSuiteCase(TestSuiteCaseLinkReqVO reqVO) {
        TestSuiteDO suite = validateSuiteExists(reqVO.getSuiteId());
        if (reqVO.getCaseIds() == null || reqVO.getCaseIds().isEmpty()) {
            throw exception(TEST_SUITE_CASE_EMPTY);
        }
        int linked = 0;
        for (Long caseId : reqVO.getCaseIds()) {
            CaseDO caseDO = caseMapper.selectById(caseId);
            if (caseDO == null) {
                throw exception(CASE_NOT_EXISTS, caseId);
            }
            // 禅道不校验产品，本实现显式拦截（否则会出现「产品 A 的集合里装着产品 B 的用例」）
            if (!Objects.equals(caseDO.getProduct(), suite.getProduct())) {
                throw exception(TEST_SUITE_CASE_NOT_IN_PRODUCT, caseId);
            }
            SuiteCaseDO existed = suiteCaseMapper.selectBySuiteAndCase(suite.getId(), caseId);
            if (existed != null) {
                suiteCaseMapper.updateCaseVersion(suite.getId(), caseId, caseDO.getVersion());
                linked++;
                continue;
            }
            SuiteCaseDO suiteCase = new SuiteCaseDO();
            suiteCase.setSuite(suite.getId());
            suiteCase.setProduct(suite.getProduct());
            suiteCase.setCaseId(caseId);
            suiteCase.setCaseVersion(caseDO.getVersion());
            suiteCase.setVersion(1);
            suiteCaseMapper.insert(suiteCase);
            linked++;
        }
        actionService.recordAction("testsuite", suite.getId(), ActionTypeEnum.EDITED,
                "加入 " + linked + " 条用例到集合：" + suite.getName());
        return linked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkSuiteCase(Long suiteId, Long caseId) {
        validateSuiteExists(suiteId);
        suiteCaseMapper.deleteBySuiteAndCase(suiteId, caseId);
    }

    @Override
    public List<CaseDO> getSuiteCaseList(Long suiteId) {
        validateSuiteExists(suiteId);
        List<SuiteCaseDO> links = suiteCaseMapper.selectListBySuite(suiteId);
        List<CaseDO> result = new ArrayList<>(links.size());
        for (SuiteCaseDO link : links) {
            CaseDO caseDO = caseMapper.selectById(link.getCaseId());
            if (caseDO != null) {
                result.add(caseDO);
            }
        }
        return result;
    }

    @Override
    public List<CaseDO> getSuiteUnlinkedCaseList(Long suiteId, String title, Long module) {
        TestSuiteDO suite = validateSuiteExists(suiteId);
        List<Long> linked = suiteCaseMapper.selectListBySuite(suiteId).stream().map(SuiteCaseDO::getCaseId).toList();
        LambdaQueryWrapperX<CaseDO> wrapper = new LambdaQueryWrapperX<CaseDO>()
                .eq(CaseDO::getProduct, suite.getProduct())
                .likeIfPresent(CaseDO::getTitle, title)
                .eqIfPresent(CaseDO::getModule, module);
        if (!linked.isEmpty()) {
            wrapper.notIn(CaseDO::getId, linked);
        }
        wrapper.orderByDesc(CaseDO::getId);
        return caseMapper.selectList(wrapper);
    }

    // ================================================================
    // 测试报告
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReport(TestReportSaveReqVO reqVO) {
        validateReport(reqVO);
        String account = currentAccount();
        TestReportDO report = new TestReportDO();
        report.setProduct(reqVO.getProduct());
        report.setProject(reqVO.getProject() == null ? 0L : reqVO.getProject());
        report.setExecution(reqVO.getExecution() == null ? 0L : reqVO.getExecution());
        report.setTasks(reqVO.getTasks());
        report.setBuilds(reqVO.getBuilds() == null ? "" : reqVO.getBuilds());
        report.setTitle(reqVO.getTitle());
        report.setBegin(reqVO.getBegin());
        report.setEnd(reqVO.getEnd());
        report.setOwner(reqVO.getOwner());
        report.setReport(reqVO.getReport() == null ? "" : reqVO.getReport());
        report.setCreatedBy(account);
        report.setCreatedDate(LocalDateTime.now());
        // 生成时把「涉及的需求/缺陷/用例」清单落库留档：报告是**当时的快照**，
        // 之后再有新用例/新缺陷，不应该改变这份已发出的报告
        TestReportRespVO summary = summarize(reqVO.getProduct(), reqVO.getTasks(), reqVO.getBegin(), reqVO.getEnd());
        report.setCases(summary.getCases());
        report.setStories(summary.getStories());
        report.setBugs(summary.getBugs());
        reportMapper.insert(report);
        actionService.recordAction("testreport", report.getId(), ActionTypeEnum.CREATED,
                "新建测试报告：" + report.getTitle());
        return report.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReport(TestReportSaveReqVO reqVO) {
        TestReportDO old = validateReportExists(reqVO.getId());
        validateReport(reqVO);
        TestReportDO updateObj = new TestReportDO();
        updateObj.setId(old.getId());
        updateObj.setProduct(reqVO.getProduct());
        updateObj.setProject(reqVO.getProject());
        updateObj.setExecution(reqVO.getExecution());
        updateObj.setTasks(reqVO.getTasks());
        updateObj.setBuilds(reqVO.getBuilds());
        updateObj.setTitle(reqVO.getTitle());
        updateObj.setBegin(reqVO.getBegin());
        updateObj.setEnd(reqVO.getEnd());
        updateObj.setOwner(reqVO.getOwner());
        updateObj.setReport(reqVO.getReport());
        // 条件变了就重算留档清单（标题/结论变了不用重算）
        boolean scopeChanged = !Objects.equals(old.getTasks(), reqVO.getTasks())
                || !Objects.equals(old.getBegin(), reqVO.getBegin())
                || !Objects.equals(old.getEnd(), reqVO.getEnd());
        if (scopeChanged) {
            TestReportRespVO summary = summarize(reqVO.getProduct(), reqVO.getTasks(), reqVO.getBegin(), reqVO.getEnd());
            updateObj.setCases(summary.getCases());
            updateObj.setStories(summary.getStories());
            updateObj.setBugs(summary.getBugs());
        }
        reportMapper.updateById(updateObj);
        actionService.recordActionWithChanges("testreport", old.getId(), ActionTypeEnum.EDITED,
                "修改测试报告：" + reqVO.getTitle(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReport(Long id) {
        TestReportDO report = validateReportExists(id);
        reportMapper.deleteById(id);
        actionService.recordAction("testreport", id, ActionTypeEnum.DELETED,
                "删除测试报告：" + report.getTitle());
    }

    @Override
    public TestReportRespVO getReport(Long id) {
        TestReportDO report = validateReportExists(id);
        TestReportRespVO vo = BeanUtils.toBean(report, TestReportRespVO.class);
        // 汇总现算：报告的条件存着，数字随时可重算，不会因为数据变化而过期失真
        TestReportRespVO summary = summarize(report.getProduct(), report.getTasks(), report.getBegin(), report.getEnd());
        vo.setCaseCount(summary.getCaseCount());
        vo.setRunCaseCount(summary.getRunCaseCount());
        vo.setResultCount(summary.getResultCount());
        vo.setFailCount(summary.getFailCount());
        vo.setPassCount(summary.getPassCount());
        vo.setCaseSummaries(summary.getCaseSummaries());
        // 留档清单以库里的为准（报告是当时的快照）
        vo.setTaskNames(taskNames(report.getTasks()));
        return vo;
    }

    @Override
    public PageResult<TestReportRespVO> getReportPage(TestReportPageReqVO reqVO) {
        PageResult<TestReportDO> page = reportMapper.selectPage(reqVO);
        List<TestReportRespVO> list = new ArrayList<>(page.getList().size());
        for (TestReportDO report : page.getList()) {
            TestReportRespVO vo = BeanUtils.toBean(report, TestReportRespVO.class);
            vo.setTaskNames(taskNames(report.getTasks()));
            TestReportRespVO summary = summarize(report.getProduct(), report.getTasks(), report.getBegin(), report.getEnd());
            vo.setCaseCount(summary.getCaseCount());
            vo.setRunCaseCount(summary.getRunCaseCount());
            vo.setResultCount(summary.getResultCount());
            vo.setFailCount(summary.getFailCount());
            vo.setPassCount(summary.getPassCount());
            list.add(vo);
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public TestReportRespVO previewReport(Long product, String tasks, String begin, String end) {
        return summarize(product, tasks, begin == null ? null : LocalDate.parse(begin),
                end == null ? null : LocalDate.parse(end));
    }

    // ================================================================
    // 汇总（本模块的核心）
    // ================================================================

    /**
     * 按「测试单列表 + 时间范围」汇总执行情况。
     *
     * <p>规则（对齐禅道 getResultSummary）：
     * <ol>
     *   <li>涉及的用例 = 这些测试单里排过的用例（去重）</li>
     *   <li>范围内的执行记录 = {@code zt_testresult} 里 run 属于这些测试单、且时间在区间内的行</li>
     *   <li><b>每条 run 只取最后一次结果</b>：先按时间排序，后面的覆盖前面的。
     *       不这么做的话「先通过后失败」会被统计成一次通过 + 一次失败</li>
     *   <li>失败数 = 最后一次结果不是 pass 的 run 数</li>
     * </ol>
     */
    private TestReportRespVO summarize(Long product, String tasks, LocalDate begin, LocalDate end) {
        TestReportRespVO vo = new TestReportRespVO();
        List<Long> taskIds = parseIds(tasks);
        List<Long> caseIdList = new ArrayList<>();
        if (!taskIds.isEmpty()) {
            for (Long taskId : taskIds) {
                for (TestRunDO run : testRunMapper.selectListByTask(taskId)) {
                    if (!caseIdList.contains(run.getCaseId())) {
                        caseIdList.add(run.getCaseId());
                    }
                }
            }
        }
        vo.setCaseCount(caseIdList.size());
        vo.setCases(joinIds(caseIdList));

        // 范围：没给日期就不限
        LocalDateTime from = begin == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : begin.atStartOfDay();
        LocalDateTime to = end == null ? LocalDateTime.of(2999, 12, 31, 23, 59, 59) : end.atTime(23, 59, 59);

        // run → 该 run 在范围内的最后一次结果；同时按用例聚合
        Map<Long, TestResultDO> lastResultByRun = new LinkedHashMap<>();
        Map<Long, Integer> runCountByCase = new LinkedHashMap<>();
        Map<Long, TestResultDO> lastResultByCase = new LinkedHashMap<>();
        int resultCount = 0;
        for (Long taskId : taskIds) {
            for (TestRunDO run : testRunMapper.selectListByTask(taskId)) {
                for (TestResultDO result : testResultMapper.selectListByRun(run.getId())) {
                    if (result.getDate() == null || result.getDate().isBefore(from) || result.getDate().isAfter(to)) {
                        continue;
                    }
                    resultCount++;
                    // ★ selectListByRun 是**最新在前**（orderByDesc id）的，
                    //   所以这里必须用 putIfAbsent —— 用 put 会让最后写入的是**最旧**那条，
                    //   于是「先通过后失败」被算成 pass（这个 bug 实测踩到过）。
                    //   也不比较 date：同一秒内跑两次的 date 可能完全相同，比 id 更可靠。
                    lastResultByRun.putIfAbsent(run.getId(), result);
                    runCountByCase.merge(run.getCaseId(), 1, Integer::sum);
                    TestResultDO previous = lastResultByCase.get(run.getCaseId());
                    if (previous == null || previous.getDate().isBefore(result.getDate())) {
                        lastResultByCase.put(run.getCaseId(), result);
                    }
                }
            }
        }
        int fail = 0;
        for (TestResultDO result : lastResultByRun.values()) {
            if (!TestResultEnum.PASS.getResult().equals(result.getCaseResult())) {
                fail++;
            }
        }
        vo.setRunCaseCount(lastResultByRun.size());
        vo.setResultCount(resultCount);
        vo.setFailCount(fail);
        vo.setPassCount(lastResultByRun.size() - fail);

        // 按用例的明细
        List<TestReportCaseVO> caseSummaries = new ArrayList<>();
        for (Map.Entry<Long, TestResultDO> entry : lastResultByCase.entrySet()) {
            TestReportCaseVO caseVO = new TestReportCaseVO();
            caseVO.setCaseId(entry.getKey());
            CaseDO caseDO = caseMapper.selectById(entry.getKey());
            caseVO.setCaseTitle(caseDO == null ? "" : caseDO.getTitle());
            caseVO.setRunCount(runCountByCase.getOrDefault(entry.getKey(), 0));
            caseVO.setLastResult(entry.getValue().getCaseResult());
            TestResultEnum resultEnum = TestResultEnum.of(entry.getValue().getCaseResult());
            caseVO.setLastResultName(resultEnum == null ? entry.getValue().getCaseResult() : resultEnum.getName());
            caseVO.setLastRunner(entry.getValue().getLastRunner());
            caseVO.setLastRunDate(entry.getValue().getDate());
            caseSummaries.add(caseVO);
        }
        vo.setCaseSummaries(caseSummaries);

        // 涉及的需求 / 缺陷：来自这些测试单的执行结果与缺陷
        List<Long> storyIds = new ArrayList<>();
        List<Long> bugIds = new ArrayList<>();
        for (Long caseId : caseIdList) {
            CaseDO caseDO = caseMapper.selectById(caseId);
            if (caseDO != null && caseDO.getStory() != null && caseDO.getStory() > 0
                    && !storyIds.contains(caseDO.getStory())) {
                storyIds.add(caseDO.getStory());
            }
        }
        for (Long taskId : taskIds) {
            for (var bug : bugMapper.selectListByTestTask(taskId)) {
                if (!bugIds.contains(bug.getId())) {
                    bugIds.add(bug.getId());
                }
            }
        }
        vo.setStories(joinIds(storyIds));
        vo.setBugs(joinIds(bugIds));
        return vo;
    }

    private void validateReport(TestReportSaveReqVO reqVO) {
        if (reqVO.getBegin() == null || reqVO.getEnd() == null) {
            throw exception(TEST_REPORT_DATE_REQUIRED);
        }
        if (reqVO.getEnd().isBefore(reqVO.getBegin())) {
            throw exception(TEST_REPORT_DATE_INVALID);
        }
        List<Long> taskIds = parseIds(reqVO.getTasks());
        if (taskIds.isEmpty()) {
            throw exception(TEST_REPORT_TASK_REQUIRED);
        }
        // 汇总的测试单必须和报告同产品，否则数字会串产品
        for (Long taskId : taskIds) {
            TestTaskDO task = testTaskMapper.selectById(taskId);
            if (task == null || !Objects.equals(task.getProduct(), reqVO.getProduct())) {
                throw exception(TEST_REPORT_TASK_NOT_IN_PRODUCT, taskId);
            }
        }
    }

    private TestReportDO validateReportExists(Long id) {
        TestReportDO report = id == null ? null : reportMapper.selectById(id);
        if (report == null) {
            throw exception(TEST_REPORT_NOT_EXISTS, id);
        }
        return report;
    }

    private void validateSuiteNameUnique(Long product, String name, Long excludeId) {
        TestSuiteDO existed = suiteMapper.selectOne(new LambdaQueryWrapperX<TestSuiteDO>()
                .eq(TestSuiteDO::getProduct, product)
                .eq(TestSuiteDO::getName, name));
        if (existed != null && !Objects.equals(existed.getId(), excludeId)) {
            throw exception(TEST_SUITE_NAME_DUPLICATE, name);
        }
    }

    private TestSuiteRespVO convertSuite(TestSuiteDO suite) {
        TestSuiteRespVO vo = BeanUtils.toBean(suite, TestSuiteRespVO.class);
        vo.setCaseCount((long) suiteCaseMapper.selectListBySuite(suite.getId()).size());
        return vo;
    }

    private String taskNames(String tasks) {
        List<String> names = new ArrayList<>();
        for (Long taskId : parseIds(tasks)) {
            TestTaskDO task = testTaskMapper.selectById(taskId);
            names.add(task == null ? ("#" + taskId) : task.getName());
        }
        return String.join("、", names);
    }

    private List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (!StringUtils.hasText(ids)) {
            return result;
        }
        for (String item : ids.split(",")) {
            if (StringUtils.hasText(item)) {
                try {
                    result.add(Long.parseLong(item.trim()));
                } catch (NumberFormatException ignored) {
                    // 非法片段直接跳过，避免一个手写的逗号把整个接口打挂
                }
            }
        }
        return result;
    }

    private String joinIds(List<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
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

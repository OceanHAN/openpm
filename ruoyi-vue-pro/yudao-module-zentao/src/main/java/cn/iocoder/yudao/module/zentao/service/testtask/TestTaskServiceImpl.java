package cn.iocoder.yudao.module.zentao.service.testtask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestResultRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunBugReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunCaseReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestStepResultVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskStatusReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.build.BuildDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseStepDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestResultDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestRunDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestTaskDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.bug.BugMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.build.BuildMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testcase.CaseMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testcase.CaseStepMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testtask.TestResultMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testtask.TestRunMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testtask.TestTaskMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.testcase.CaseStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.testcase.CaseStepTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.testtask.TestResultEnum;
import cn.iocoder.yudao.module.zentao.enums.testtask.TestTaskStatusEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.bug.BugService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 测试单 Service 实现
 *
 * <p>对齐禅道 {@code module/testtask} 的 create / update / start / close / block / activate /
 * linkCase / unlinkCase / assignCase / createResult。
 *
 * <p>本模块的价值在于它把测试链闭上了：只有做到测试单，{@code zt_case} 上的
 * 「最近执行结果 / 执行人 / 执行时间」才会有数据 —— 这三个字段是**执行时回写的**。
 */
@Slf4j
@Service
public class TestTaskServiceImpl implements TestTaskService {

    @Resource
    private TestTaskMapper testTaskMapper;

    @Resource
    private TestRunMapper testRunMapper;

    @Resource
    private TestResultMapper testResultMapper;

    @Resource
    private CaseMapper caseMapper;

    @Resource
    private CaseStepMapper caseStepMapper;

    @Resource
    private BuildMapper buildMapper;

    @Resource
    private BugMapper bugMapper;

    @Resource
    private BugService bugService;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ================================================================
    // 测试单 CRUD
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTestTask(TestTaskSaveReqVO reqVO) {
        validateDate(reqVO);
        String account = currentAccount();
        LocalDateTime now = LocalDateTime.now();

        TestTaskDO task = new TestTaskDO();
        task.setProduct(reqVO.getProduct());
        task.setProject(reqVO.getProject() == null ? 0L : reqVO.getProject());
        task.setExecution(reqVO.getExecution() == null ? 0L : reqVO.getExecution());
        task.setBuild(reqVO.getBuild() == null ? 0L : reqVO.getBuild());
        task.setName(reqVO.getName());
        task.setType(reqVO.getType() == null ? "" : reqVO.getType());
        task.setOwner(StringUtils.hasText(reqVO.getOwner()) ? reqVO.getOwner() : account);
        task.setPri(reqVO.getPri() == null ? 3 : reqVO.getPri());
        task.setBegin(reqVO.getBegin());
        task.setEnd(reqVO.getEnd());
        task.setDesc(reqVO.getDesc() == null ? "" : reqVO.getDesc());
        task.setReport("");
        task.setStatus(TestTaskStatusEnum.WAIT.getStatus());
        task.setCreatedBy(account);
        task.setCreatedDate(now);
        testTaskMapper.insert(task);

        actionService.recordAction("testtask", task.getId(), ActionTypeEnum.CREATED, "新建测试单：" + task.getName());
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTestTask(TestTaskSaveReqVO reqVO) {
        TestTaskDO old = validateTestTaskExists(reqVO.getId());
        validateDate(reqVO);

        TestTaskDO updateObj = new TestTaskDO();
        updateObj.setId(old.getId());
        updateObj.setProduct(reqVO.getProduct());
        updateObj.setProject(reqVO.getProject());
        updateObj.setExecution(reqVO.getExecution());
        updateObj.setBuild(reqVO.getBuild());
        updateObj.setName(reqVO.getName());
        updateObj.setType(reqVO.getType());
        updateObj.setOwner(reqVO.getOwner());
        updateObj.setPri(reqVO.getPri());
        updateObj.setBegin(reqVO.getBegin());
        updateObj.setEnd(reqVO.getEnd());
        updateObj.setDesc(reqVO.getDesc());
        testTaskMapper.updateById(updateObj);

        actionService.recordActionWithChanges("testtask", old.getId(), ActionTypeEnum.EDITED,
                "修改测试单：" + reqVO.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTestTask(Long id) {
        TestTaskDO task = validateTestTaskExists(id);
        // 排进来的用例与执行历史一起物理清理：
        // zt_testrun 没有 deleted 列，zt_testresult 留着会指向一条不存在的 run
        for (TestRunDO run : testRunMapper.selectListByTask(id)) {
            testResultMapper.deleteByRunPhysical(run.getId());
        }
        testRunMapper.deleteByTaskPhysical(id);
        testTaskMapper.deleteById(id);
        actionService.recordAction("testtask", id, ActionTypeEnum.DELETED, "删除测试单：" + task.getName());
    }

    @Override
    public TestTaskDO validateTestTaskExists(Long id) {
        TestTaskDO task = id == null ? null : testTaskMapper.selectById(id);
        if (task == null) {
            throw exception(TEST_TASK_NOT_EXISTS, id);
        }
        return task;
    }

    @Override
    public TestTaskRespVO getTestTask(Long id) {
        return convert(validateTestTaskExists(id));
    }

    @Override
    public PageResult<TestTaskRespVO> getTestTaskPage(TestTaskPageReqVO reqVO) {
        PageResult<TestTaskDO> page = testTaskMapper.selectPage(reqVO);
        List<TestTaskRespVO> list = new ArrayList<>(page.getList().size());
        for (TestTaskDO task : page.getList()) {
            list.add(convert(task));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<TestTaskDO> getTestTaskListByProduct(Long product) {
        return testTaskMapper.selectListByProduct(product);
    }

    @Override
    public List<TestTaskDO> getTestTaskListByExecution(Long execution) {
        return testTaskMapper.selectListByExecution(execution);
    }

    // ================================================================
    // 状态流转
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startTestTask(Long id) {
        TestTaskDO old = validateTestTaskExists(id);
        // 禅道 isClickable：只有 wait / blocked 能开始
        if (!TestTaskStatusEnum.WAIT.getStatus().equals(old.getStatus())
                && !TestTaskStatusEnum.BLOCKED.getStatus().equals(old.getStatus())) {
            throw exception(TEST_TASK_STATUS_ILLEGAL, statusName(old.getStatus()));
        }
        TestTaskDO updateObj = new TestTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(TestTaskStatusEnum.DOING.getStatus());
        updateObj.setRealBegan(LocalDate.now());
        testTaskMapper.updateById(updateObj);
        actionService.recordActionWithChanges("testtask", id, ActionTypeEnum.EDITED,
                "开始测试单：" + old.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void blockTestTask(Long id, String comment) {
        TestTaskDO old = validateTestTaskExists(id);
        if (!TestTaskStatusEnum.DOING.getStatus().equals(old.getStatus())
                && !TestTaskStatusEnum.WAIT.getStatus().equals(old.getStatus())) {
            throw exception(TEST_TASK_STATUS_ILLEGAL, statusName(old.getStatus()));
        }
        TestTaskDO updateObj = new TestTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(TestTaskStatusEnum.BLOCKED.getStatus());
        testTaskMapper.updateById(updateObj);
        actionService.recordActionWithChanges("testtask", id, ActionTypeEnum.EDITED,
                "阻塞测试单：" + comment, old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateTestTask(Long id, String comment) {
        TestTaskDO old = validateTestTaskExists(id);
        if (!TestTaskStatusEnum.BLOCKED.getStatus().equals(old.getStatus())
                && !TestTaskStatusEnum.DONE.getStatus().equals(old.getStatus())) {
            throw exception(TEST_TASK_STATUS_ILLEGAL, statusName(old.getStatus()));
        }
        TestTaskDO updateObj = new TestTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(TestTaskStatusEnum.DOING.getStatus());
        // 激活要把「完成时间」清掉 —— 必须用原生 UPDATE，updateById 会跳过 null（第 19 条坑）
        testTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TestTaskDO>()
                .eq(TestTaskDO::getId, id)
                .set(TestTaskDO::getStatus, TestTaskStatusEnum.DOING.getStatus())
                .set(TestTaskDO::getRealFinishedDate, null)
                .set(TestTaskDO::getReport, null));
        actionService.recordAction("testtask", id, ActionTypeEnum.ACTIVATED,
                "激活测试单：" + (comment == null ? "" : comment));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeTestTask(Long id, TestTaskStatusReqVO reqVO) {
        TestTaskDO old = validateTestTaskExists(id);
        if (!TestTaskStatusEnum.DOING.getStatus().equals(old.getStatus())
                && !TestTaskStatusEnum.BLOCKED.getStatus().equals(old.getStatus())) {
            throw exception(TEST_TASK_STATUS_ILLEGAL, statusName(old.getStatus()));
        }
        if (reqVO == null || reqVO.getRealFinishedDate() == null) {
            throw exception(TEST_TASK_FINISHED_DATE_REQUIRED);
        }
        // 禅道的两条校验：完成时间不能早于计划开始、不能晚于明天
        if (old.getBegin() != null && reqVO.getRealFinishedDate().toLocalDate().isBefore(old.getBegin())) {
            throw exception(TEST_TASK_FINISHED_DATE_LESS, old.getBegin());
        }
        if (reqVO.getRealFinishedDate().toLocalDate().isAfter(LocalDate.now().plusDays(1))) {
            throw exception(TEST_TASK_FINISHED_DATE_MORE);
        }

        TestTaskDO updateObj = new TestTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(TestTaskStatusEnum.DONE.getStatus());
        updateObj.setRealFinishedDate(reqVO.getRealFinishedDate());
        updateObj.setReport(reqVO.getReport());
        testTaskMapper.updateById(updateObj);
        actionService.recordActionWithChanges("testtask", id, ActionTypeEnum.CLOSED,
                "关闭测试单：" + (reqVO.getComment() == null ? "" : reqVO.getComment()), old, updateObj);
    }

    // ================================================================
    // 用例编排
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int linkCase(TestRunLinkReqVO reqVO) {
        TestTaskDO task = validateTestTaskExists(reqVO.getTaskId());
        if (reqVO.getCaseIds() == null || reqVO.getCaseIds().isEmpty()) {
            throw exception(TEST_RUN_CASE_EMPTY);
        }
        int linked = 0;
        for (Long caseId : reqVO.getCaseIds()) {
            CaseDO caseDO = caseMapper.selectById(caseId);
            if (caseDO == null) {
                throw exception(CASE_NOT_EXISTS, caseId);
            }
            // 禅道把用例排进测试单时不校验产品；本实现显式拦截，
            // 否则会出现「产品 A 的测试单里跑产品 B 的用例」这种数据
            if (!Objects.equals(caseDO.getProduct(), task.getProduct())) {
                throw exception(TEST_RUN_CASE_NOT_IN_PRODUCT, caseId);
            }
            TestRunDO existed = testRunMapper.selectByTaskAndCase(task.getId(), caseId);
            if (existed != null) {
                // 【有意偏离禅道】禅道用 REPLACE INTO，会把这条用例之前的执行结果清空；
                // 这里只更新「排进来时的用例版本」和指派，结果原样保留。
                testRunMapper.updateCaseVersionAndAssignee(task.getId(), caseId, caseDO.getVersion(),
                        reqVO.getAssignedTo() == null ? existed.getAssignedTo() : reqVO.getAssignedTo());
                linked++;
                continue;
            }
            TestRunDO run = new TestRunDO();
            run.setTask(task.getId());
            run.setCaseId(caseId);
            run.setCaseVersion(caseDO.getVersion());
            run.setVersion(1);
            run.setAssignedTo(reqVO.getAssignedTo() == null ? "" : reqVO.getAssignedTo());
            run.setLastRunResult("");
            run.setStatus("normal");
            testRunMapper.insert(run);
            linked++;
        }
        actionService.recordAction("testtask", task.getId(), ActionTypeEnum.EDITED,
                "关联用例 " + linked + " 条到测试单：" + task.getName());
        return linked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkCase(Long runId) {
        TestRunDO run = validateRunExists(runId);
        testResultMapper.deleteByRunPhysical(runId);
        testRunMapper.deleteByIdPhysical(runId);
        actionService.recordAction("testtask", run.getTask(), ActionTypeEnum.EDITED,
                "从测试单移除用例 #" + run.getCaseId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignCase(Long runId, String assignedTo) {
        validateRunExists(runId);
        testRunMapper.updateAssignee(runId, assignedTo == null ? "" : assignedTo);
    }

    @Override
    public List<TestRunRespVO> getRunList(Long taskId) {
        validateTestTaskExists(taskId);
        List<TestRunDO> runs = testRunMapper.selectListByTask(taskId);
        List<TestRunRespVO> result = new ArrayList<>(runs.size());
        for (TestRunDO run : runs) {
            result.add(convertRun(run));
        }
        return result;
    }

    @Override
    public List<TestRunRespVO> getLinkableList(Long taskId, String title, Long module) {
        TestTaskDO task = validateTestTaskExists(taskId);
        // 已经排进来的用例编号
        List<Long> linkedIds = testRunMapper.selectListByTask(taskId).stream().map(TestRunDO::getCaseId).toList();
        LambdaQueryWrapperX<CaseDO> wrapper = new LambdaQueryWrapperX<CaseDO>()
                .eq(CaseDO::getProduct, task.getProduct())
                .likeIfPresent(CaseDO::getTitle, title)
                .eqIfPresent(CaseDO::getModule, module);
        // 排除已经排进本测试单的用例。LambdaQueryWrapperX 没有 notInIfPresent，
        // 所以自己判空 —— 也不能把空集合交给 notIn（语义会变成「排除 0 个」倒是安全，
        // 但多拼一个 IN () 没必要）。
        if (!linkedIds.isEmpty()) {
            wrapper.notIn(CaseDO::getId, linkedIds);
        }
        wrapper.orderByDesc(CaseDO::getId);
        List<CaseDO> cases = caseMapper.selectList(wrapper);
        List<TestRunRespVO> result = new ArrayList<>(cases.size());
        for (CaseDO caseDO : cases) {
            TestRunRespVO vo = new TestRunRespVO();
            vo.setTask(taskId);
            vo.setCaseId(caseDO.getId());
            vo.setCaseTitle(caseDO.getTitle());
            vo.setCaseType(caseDO.getType());
            vo.setCasePri(caseDO.getPri());
            vo.setLatestCaseVersion(caseDO.getVersion());
            vo.setStepCount(caseStepMapper.selectListByCaseAndVersion(caseDO.getId(), caseDO.getVersion()).size());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<TestRunRespVO> getRunListByCase(Long caseId) {
        List<TestRunDO> runs = testRunMapper.selectListByCase(caseId);
        List<TestRunRespVO> result = new ArrayList<>(runs.size());
        for (TestRunDO run : runs) {
            result.add(convertRun(run));
        }
        return result;
    }

    // ================================================================
    // 执行（本模块的核心）
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String runCase(TestRunCaseReqVO reqVO) {
        TestRunDO run = validateRunExists(reqVO.getRunId());
        CaseDO caseDO = caseMapper.selectById(run.getCaseId());
        if (caseDO == null) {
            throw exception(CASE_NOT_EXISTS, run.getCaseId());
        }
        // 拿当前版本的步骤，校验每个提交的步骤 id 都属于这个版本
        List<CaseStepDO> steps = caseStepMapper.selectListByCaseAndVersion(run.getCaseId(), caseDO.getVersion());
        List<String> stepResults = new ArrayList<>(steps.size());
        for (TestStepResultVO stepResult : reqVO.getStepResults()) {
            CaseStepDO matched = steps.stream()
                    .filter(step -> Objects.equals(step.getId(), stepResult.getId()))
                    .findFirst().orElse(null);
            if (matched == null) {
                throw exception(TEST_STEP_NOT_BELONG, stepResult.getId());
            }
            if (TestResultEnum.of(stepResult.getResult()) == null) {
                throw exception(TEST_RESULT_INVALID, stepResult.getResult());
            }
            stepResults.add("{\"id\":" + stepResult.getId()
                    + ",\"result\":\"" + stepResult.getResult() + "\""
                    + (StringUtils.hasText(stepResult.getRemark())
                        ? ",\"remark\":" + toJsonString(stepResult.getRemark()) : "")
                    + "}");
        }

        String caseResult = aggregateResult(reqVO.getStepResults());
        String account = currentAccount();
        LocalDateTime now = LocalDateTime.now();

        // ① 执行历史：一次执行一行
        TestResultDO resultDO = new TestResultDO();
        resultDO.setRun(run.getId());
        resultDO.setCaseId(run.getCaseId());
        resultDO.setVersion(caseDO.getVersion());
        resultDO.setCaseResult(caseResult);
        resultDO.setStepResults("[" + String.join(",", stepResults) + "]");
        resultDO.setLastRunner(account);
        resultDO.setDate(now);
        testResultMapper.insert(resultDO);

        // ② 回写用例：这三个字段就是「用例的最近执行情况」，只在执行时产生
        caseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CaseDO>()
                .eq(CaseDO::getId, run.getCaseId())
                .set(CaseDO::getLastRunResult, caseResult)
                .set(CaseDO::getLastRunner, account)
                .set(CaseDO::getLastRunDate, now)
                // 用例执行过就不再是「待评审」了；被阻塞时回到 blocked
                .set(CaseDO::getStatus, TestResultEnum.BLOCKED.getResult().equals(caseResult)
                        ? CaseStatusEnum.BLOCKED.getStatus()
                        : CaseStatusEnum.NORMAL.getStatus()));

        // ③ 回写 run：状态由结果推导
        testRunMapper.updateRunResult(run.getId(),
                TestResultEnum.BLOCKED.getResult().equals(caseResult) ? "blocked" : "normal",
                caseResult, account, now);

        actionService.recordAction("case", run.getCaseId(), ActionTypeEnum.EDITED,
                "执行用例（测试单 #" + run.getTask() + "）：" + resultName(caseResult));
        return caseResult;
    }

    @Override
    public List<TestResultRespVO> getResultList(Long runId) {
        validateRunExists(runId);
        List<TestResultDO> list = testResultMapper.selectListByRun(runId);
        List<TestResultRespVO> result = new ArrayList<>(list.size());
        for (TestResultDO item : list) {
            TestResultRespVO vo = BeanUtils.toBean(item, TestResultRespVO.class);
            vo.setCaseResultName(resultName(item.getCaseResult()));
            result.add(vo);
        }
        return result;
    }

    // ================================================================
    // 执行失败 → 建缺陷
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBugForRun(TestRunBugReqVO reqVO) {
        TestRunDO run = validateRunExists(reqVO.getRunId());
        TestTaskDO task = validateTestTaskExists(run.getTask());
        CaseDO caseDO = caseMapper.selectById(run.getCaseId());
        if (caseDO == null) {
            throw exception(CASE_NOT_EXISTS, run.getCaseId());
        }

        BugSaveReqVO bugReq = new BugSaveReqVO();
        // 归属：产品来自测试单，分支/模块/需求来自用例 —— 禅道也是这么串的
        bugReq.setProduct(task.getProduct());
        bugReq.setProject(task.getProject());
        bugReq.setExecution(task.getExecution());
        bugReq.setBranch(caseDO.getBranch());
        bugReq.setModule(caseDO.getModule());
        bugReq.setStory(caseDO.getStory());
        bugReq.setTitle(reqVO.getTitle());
        bugReq.setKeywords(reqVO.getKeywords());
        bugReq.setSeverity(reqVO.getSeverity() == null ? 3 : reqVO.getSeverity());
        bugReq.setPri(reqVO.getPri() == null ? 3 : reqVO.getPri());
        bugReq.setType(reqVO.getType());
        bugReq.setOs(reqVO.getOs());
        bugReq.setBrowser(reqVO.getBrowser());
        bugReq.setAssignedTo(reqVO.getAssignedTo());
        // 影响版本：默认取测试单关联的构建（测的是哪个包）
        bugReq.setOpenedBuild(StringUtils.hasText(reqVO.getOpenedBuild()) ? reqVO.getOpenedBuild()
                : (task.getBuild() != null && task.getBuild() > 0 ? String.valueOf(task.getBuild()) : ""));
        // 复现步骤：不填就按「用例步骤 + 失败的那一步」自动生成（禅道也是预填一份给用户改）
        bugReq.setSteps(StringUtils.hasText(reqVO.getSteps()) ? reqVO.getSteps()
                : buildStepsForBug(caseDO, reqVO.getStepId()));
        // ★ 溯源三件套：来源用例 + **来源用例的版本（冻结）** + 来源测试单
        bugReq.setCaseId(caseDO.getId());
        bugReq.setCaseVersion(caseDO.getVersion());
        bugReq.setTesttask(task.getId());

        Long bugId = bugService.createBug(bugReq);

        // 在用例的时间线上留一笔：这条用例跑出过哪个缺陷
        actionService.recordAction("case", caseDO.getId(), ActionTypeEnum.EDITED,
                "用例执行失败建缺陷 #" + bugId + "：" + reqVO.getTitle());
        return bugId;
    }

    @Override
    public List<BugDO> getBugListByCase(Long caseId) {
        return bugMapper.selectListByCase(caseId);
    }

    @Override
    public List<BugDO> getBugListByTask(Long taskId) {
        validateTestTaskExists(taskId);
        return bugMapper.selectListByTestTask(taskId);
    }

    /**
     * 用用例的步骤拼一份「复现步骤」给缺陷用。
     *
     * <p>禅道是把用例步骤整段带过去让人改；这里做得更细一点：
     * 如果指定了失败的那个步骤，就在那一步后面标一个「← 这一步失败」，
     * 让人一眼看出卡在哪。
     */
    private String buildStepsForBug(CaseDO caseDO, Long failedStepId) {
        List<CaseStepDO> steps = caseStepMapper.selectListByCaseAndVersion(caseDO.getId(), caseDO.getVersion());
        if (steps.isEmpty()) {
            return "【复现步骤】\n1. 见用例 #" + caseDO.getId() + " " + caseDO.getTitle();
        }
        StringBuilder sb = new StringBuilder("【复现步骤】来源用例 #")
                .append(caseDO.getId()).append(" ").append(caseDO.getTitle())
                .append("（v").append(caseDO.getVersion()).append("）\n");
        int index = 1;
        for (CaseStepDO step : steps) {
            if (CaseStepTypeEnum.GROUP.getType().equals(step.getType())) {
                sb.append(index++).append(". ").append(step.getDesc()).append("（步骤组）\n");
                continue;
            }
            sb.append(index++).append(". ").append(step.getDesc());
            if (StringUtils.hasText(step.getExpect())) {
                sb.append("\n   预期：").append(step.getExpect());
            }
            if (Objects.equals(step.getId(), failedStepId)) {
                sb.append("\n   ← 这一步失败");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 由步骤结果算出用例级结果 —— 禅道 {@code createResult} 里的那段循环。
     *
     * <p>规则：默认 {@code pass}；逐个看步骤结果，{@code n/a} 和 {@code pass} 跳过；
     * 第一个「有效结果」（非 pass 且非 n/a）直接成为用例结果，遇到 {@code fail} 立即结束。
     * 所以是 **fail 优先、其次按出现顺序**，不是多数派。
     */
    private String aggregateResult(List<TestStepResultVO> stepResults) {
        String caseResult = TestResultEnum.PASS.getResult();
        for (TestStepResultVO stepResult : stepResults) {
            if (!TestResultEnum.isEffective(stepResult.getResult())) {
                continue;
            }
            if (TestResultEnum.of(stepResult.getResult()) != null) {
                caseResult = stepResult.getResult();
            }
            if (TestResultEnum.FAIL.getResult().equals(stepResult.getResult())) {
                break;
            }
        }
        return caseResult;
    }

    // ================================================================
    // 转换与工具
    // ================================================================

    private TestRunDO validateRunExists(Long runId) {
        TestRunDO run = runId == null ? null : testRunMapper.selectById(runId);
        if (run == null) {
            throw exception(TEST_RUN_NOT_EXISTS, runId);
        }
        return run;
    }

    private TestRunRespVO convertRun(TestRunDO run) {
        TestRunRespVO vo = BeanUtils.toBean(run, TestRunRespVO.class);
        CaseDO caseDO = caseMapper.selectById(run.getCaseId());
        if (caseDO != null) {
            vo.setCaseTitle(caseDO.getTitle());
            vo.setCaseType(caseDO.getType());
            vo.setCasePri(caseDO.getPri());
            vo.setCaseModule(caseDO.getModule());
            vo.setLatestCaseVersion(caseDO.getVersion());
            // 排进来之后用例又改过步骤 → 提示「用例已变更」（与 projectstory 的「版本已变更」同思路）
            vo.setCaseChanged(!Objects.equals(run.getCaseVersion(), caseDO.getVersion()));
            vo.setStepCount(caseStepMapper.selectListByCaseAndVersion(caseDO.getId(), caseDO.getVersion()).size());
        }
        vo.setLastRunResultName(StringUtils.hasText(run.getLastRunResult())
                ? resultName(run.getLastRunResult()) : "未执行");
        return vo;
    }

    private TestTaskRespVO convert(TestTaskDO task) {
        TestTaskRespVO vo = BeanUtils.toBean(task, TestTaskRespVO.class);
        TestTaskStatusEnum statusEnum = TestTaskStatusEnum.of(task.getStatus());
        vo.setStatusName(statusEnum == null ? task.getStatus() : statusEnum.getName());
        if (task.getBuild() != null && task.getBuild() > 0) {
            BuildDO build = buildMapper.selectById(task.getBuild());
            vo.setBuildName(build == null ? "" : build.getName());
        }
        // 统计：用例数 / 通过 / 失败 / 阻塞 / 未执行
        List<TestRunDO> runs = testRunMapper.selectListByTask(task.getId());
        long pass = 0;
        long fail = 0;
        long blocked = 0;
        long unexecuted = 0;
        for (TestRunDO run : runs) {
            if (!StringUtils.hasText(run.getLastRunResult())) {
                unexecuted++;
            } else if (TestResultEnum.PASS.getResult().equals(run.getLastRunResult())) {
                pass++;
            } else if (TestResultEnum.FAIL.getResult().equals(run.getLastRunResult())) {
                fail++;
            } else if (TestResultEnum.BLOCKED.getResult().equals(run.getLastRunResult())) {
                blocked++;
            }
        }
        vo.setCaseCount((long) runs.size());
        vo.setRunCount((long) runs.size() - unexecuted);
        vo.setPassCount(pass);
        vo.setFailCount(fail);
        vo.setBlockedCount(blocked);
        vo.setUnexecutedCount(unexecuted);
        return vo;
    }

    private void validateDate(TestTaskSaveReqVO reqVO) {
        if (reqVO.getBegin() != null && reqVO.getEnd() != null && reqVO.getEnd().isBefore(reqVO.getBegin())) {
            throw exception(TEST_TASK_DATE_INVALID);
        }
    }

    private String statusName(String status) {
        TestTaskStatusEnum statusEnum = TestTaskStatusEnum.of(status);
        return statusEnum == null ? status : statusEnum.getName();
    }

    private String resultName(String result) {
        TestResultEnum resultEnum = TestResultEnum.of(result);
        return resultEnum == null ? result : resultEnum.getName();
    }

    /**
     * 极简的 JSON 字符串转义：只处理引号、反斜杠与控制字符，避免 remark 里的引号破坏 JSON。
     */
    private String toJsonString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append("\"").toString();
    }

    /**
     * 取当前登录用户的账号，与其它 Service 保持一致口径。
     */
    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

}

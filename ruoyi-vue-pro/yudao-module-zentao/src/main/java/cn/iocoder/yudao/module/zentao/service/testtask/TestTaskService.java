package cn.iocoder.yudao.module.zentao.service.testtask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestResultRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunBugReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunCaseReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskStatusReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestTaskDO;

import java.util.List;

/**
 * 测试单 Service 接口
 *
 * <h3>禅道语义（module/testtask）</h3>
 * <ul>
 *   <li>测试单 {@code zt_testtask} + 排进来的用例 {@code zt_testrun}（UNIQUE(task,case)）
 *       + 执行历史 {@code zt_testresult}</li>
 *   <li>执行一次用例会**写三处**：插一条结果历史、回写用例、回写 run</li>
 *   <li>用例级结果由步骤结果算出来（fail 优先，不是多数派）</li>
 * </ul>
 */
public interface TestTaskService {

    // ==================== 测试单 ====================

    Long createTestTask(TestTaskSaveReqVO reqVO);

    void updateTestTask(TestTaskSaveReqVO reqVO);

    void deleteTestTask(Long id);

    TestTaskDO validateTestTaskExists(Long id);

    TestTaskRespVO getTestTask(Long id);

    PageResult<TestTaskRespVO> getTestTaskPage(TestTaskPageReqVO reqVO);

    List<TestTaskDO> getTestTaskListByProduct(Long product);

    List<TestTaskDO> getTestTaskListByExecution(Long execution);

    // ==================== 状态流转 ====================

    void startTestTask(Long id);

    void blockTestTask(Long id, String comment);

    void activateTestTask(Long id, String comment);

    void closeTestTask(Long id, TestTaskStatusReqVO reqVO);

    // ==================== 用例编排 ====================

    /**
     * 把用例排进测试单。已排过的只更新用例版本与指派，**保留执行结果**。
     *
     * @return 实际生效的用例数
     */
    int linkCase(TestRunLinkReqVO reqVO);

    void unlinkCase(Long runId);

    void assignCase(Long runId, String assignedTo);

    /**
     * 测试单里的用例执行列表
     */
    List<TestRunRespVO> getRunList(Long taskId);

    /**
     * 还能排进这个测试单的用例（同产品、且还没排过）
     */
    List<TestRunRespVO> getLinkableList(Long taskId, String title, Long module);

    /**
     * 某用例被哪些测试单排过
     */
    List<TestRunRespVO> getRunListByCase(Long caseId);

    // ==================== 执行 ====================

    /**
     * 执行一条用例。
     *
     * <p>写三处：插 {@code zt_testresult} 历史、回写 {@code zt_case} 的最近结果、回写 {@code zt_testrun}。
     *
     * @return 用例级结果（pass/fail/blocked/n-a）
     */
    String runCase(TestRunCaseReqVO reqVO);

    /**
     * 某条 run 的执行历史（最新在前）
     */
    List<TestResultRespVO> getResultList(Long runId);

    // ==================== 执行失败 → 建缺陷 ====================

    /**
     * 从一条用例的执行结果建缺陷（禅道的 testcase/createBug）。
     *
     * <p>会把「来源用例 + 用例版本 + 测试单」写进缺陷，
     * 于是缺陷能反查到「是哪条用例哪一版跑出来的」。
     *
     * @return 缺陷编号
     */
    Long createBugForRun(TestRunBugReqVO reqVO);

    /**
     * 某条用例跑出来的缺陷
     */
    List<BugDO> getBugListByCase(Long caseId);

    /**
     * 某个测试单跑出来的缺陷
     */
    List<BugDO> getBugListByTask(Long taskId);

}

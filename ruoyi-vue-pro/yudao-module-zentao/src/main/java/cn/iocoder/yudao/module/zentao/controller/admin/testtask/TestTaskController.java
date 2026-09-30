package cn.iocoder.yudao.module.zentao.controller.admin.testtask;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestResultRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunBugReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunCaseReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestRunRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskStatusReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestTaskDO;
import cn.iocoder.yudao.module.zentao.enums.testtask.TestResultEnum;
import cn.iocoder.yudao.module.zentao.enums.testtask.TestTaskStatusEnum;
import cn.iocoder.yudao.module.zentao.service.testtask.TestTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 测试单 Controller
 *
 * <p>测试单是测试链闭环的地方：用例排进测试单 → 执行 → 结果回写用例与 run。
 * 所以 {@code zt_case} 上的「最近执行结果」只有在用过本模块之后才会有值。
 */
@Tag(name = "管理后台 - 禅道测试单")
@RestController
@RequestMapping("/zentao/testtask")
@Validated
public class TestTaskController {

    @Resource
    private TestTaskService testTaskService;

    // ==================== 测试单 CRUD ====================

    @PostMapping("/create")
    @Operation(summary = "新建测试单", description = "结束日期不能早于开始日期")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:create')")
    public CommonResult<Long> createTestTask(@Valid @RequestBody TestTaskSaveReqVO reqVO) {
        return success(testTaskService.createTestTask(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改测试单")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<Boolean> updateTestTask(@Valid @RequestBody TestTaskSaveReqVO reqVO) {
        testTaskService.updateTestTask(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除测试单", description = "排进来的用例与执行历史一起物理清理")
    @Parameter(name = "id", description = "测试单编号", required = true, example = "94102")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:delete')")
    public CommonResult<Boolean> deleteTestTask(@RequestParam("id") Long id) {
        testTaskService.deleteTestTask(id);
        return success(true);
    }

    // ==================== 查询 ====================

    @GetMapping("/get")
    @Operation(summary = "获得测试单详情", description = "带上用例数/通过/失败/阻塞/未执行的统计")
    @Parameter(name = "id", description = "测试单编号", required = true, example = "94101")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<TestTaskRespVO> getTestTask(@RequestParam("id") Long id) {
        return success(testTaskService.getTestTask(id));
    }

    @GetMapping("/page")
    @Operation(summary = "测试单分页查询", description = "type 是逗号列表，用 FIND_IN_SET 匹配")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<PageResult<TestTaskRespVO>> getTestTaskPage(@Valid TestTaskPageReqVO reqVO) {
        return success(testTaskService.getTestTaskPage(reqVO));
    }

    @GetMapping("/list-by-product")
    @Operation(summary = "获得某产品的测试单")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<TestTaskRespVO>> getListByProduct(@RequestParam("product") Long product) {
        List<TestTaskRespVO> result = new ArrayList<>();
        for (TestTaskDO task : testTaskService.getTestTaskListByProduct(product)) {
            result.add(testTaskService.getTestTask(task.getId()));
        }
        return success(result);
    }

    @GetMapping("/list-by-execution")
    @Operation(summary = "获得某执行的测试单")
    @Parameter(name = "execution", description = "执行编号", required = true, example = "90001")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<TestTaskRespVO>> getListByExecution(@RequestParam("execution") Long execution) {
        List<TestTaskRespVO> result = new ArrayList<>();
        for (TestTaskDO task : testTaskService.getTestTaskListByExecution(execution)) {
            result.add(testTaskService.getTestTask(task.getId()));
        }
        return success(result);
    }

    @GetMapping("/simple-list")
    @Operation(summary = "测试单下拉列表（不带动辄几十条统计的明细，只给 id + 名称）")
    @Parameter(name = "product", description = "产品编号", example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleList(
            @RequestParam(value = "product", required = false) Long product) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<TestTaskDO> tasks = product == null ? List.of() : testTaskService.getTestTaskListByProduct(product);
        for (TestTaskDO task : tasks) {
            result.add(Map.of("id", task.getId(), "name", task.getName(), "status", task.getStatus()));
        }
        return success(result);
    }

    // ==================== 状态流转 ====================

    @PutMapping("/start")
    @Operation(summary = "开始测试单", description = "只有「未开始」「被阻塞」能开始；会写入实际开始日期")
    @Parameter(name = "id", description = "测试单编号", required = true, example = "94102")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<Boolean> startTestTask(@RequestParam("id") Long id) {
        testTaskService.startTestTask(id);
        return success(true);
    }

    @PutMapping("/block")
    @Operation(summary = "阻塞测试单")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<Boolean> blockTestTask(@RequestBody(required = false) TestTaskStatusReqVO reqVO,
                                               @RequestParam("id") Long id) {
        testTaskService.blockTestTask(id, reqVO == null ? null : reqVO.getComment());
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活测试单", description = "被阻塞/已关闭 → 进行中，并清空完成时间与测试总结")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<Boolean> activateTestTask(@RequestBody(required = false) TestTaskStatusReqVO reqVO,
                                                  @RequestParam("id") Long id) {
        testTaskService.activateTestTask(id, reqVO == null ? null : reqVO.getComment());
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭测试单",
            description = "必须填写实际完成时间；不能早于计划开始日期、不能晚于明天（禅道原规则）")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<Boolean> closeTestTask(@Valid @RequestBody TestTaskStatusReqVO reqVO,
                                               @RequestParam("id") Long id) {
        testTaskService.closeTestTask(id, reqVO);
        return success(true);
    }

    // ==================== 用例编排 ====================

    @PostMapping("/link-case")
    @Operation(summary = "把用例排进测试单",
            description = "已排过的只更新用例版本与指派，**保留执行结果**（禅道用 REPLACE 会清空，见 README 说明）")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<Integer> linkCase(@Valid @RequestBody TestRunLinkReqVO reqVO) {
        return success(testTaskService.linkCase(reqVO));
    }

    @DeleteMapping("/unlink-case")
    @Operation(summary = "把用例从测试单移出", description = "连带清理它的执行历史")
    @Parameter(name = "runId", description = "执行记录编号", required = true, example = "94153")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<Boolean> unlinkCase(@RequestParam("runId") Long runId) {
        testTaskService.unlinkCase(runId);
        return success(true);
    }

    @PutMapping("/assign-case")
    @Operation(summary = "指派用例执行人")
    @Parameter(name = "runId", description = "执行记录编号", required = true, example = "94153")
    @Parameter(name = "assignedTo", description = "执行人账号", required = true, example = "admin")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<Boolean> assignCase(@RequestParam("runId") Long runId,
                                            @RequestParam("assignedTo") String assignedTo) {
        testTaskService.assignCase(runId, assignedTo);
        return success(true);
    }

    @GetMapping("/run-list")
    @Operation(summary = "测试单里的用例执行列表",
            description = "带上用例标题、指派、最近结果；caseChanged=true 表示排进来之后用例又改过")
    @Parameter(name = "taskId", description = "测试单编号", required = true, example = "94101")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<TestRunRespVO>> getRunList(@RequestParam("taskId") Long taskId) {
        return success(testTaskService.getRunList(taskId));
    }

    @GetMapping("/linkable-list")
    @Operation(summary = "还能排进这个测试单的用例（同产品、且还没排过）")
    @Parameter(name = "taskId", description = "测试单编号", required = true, example = "94101")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<TestRunRespVO>> getLinkableList(
            @RequestParam("taskId") Long taskId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "module", required = false) Long module) {
        return success(testTaskService.getLinkableList(taskId, title, module));
    }

    @GetMapping("/run-list-by-case")
    @Operation(summary = "某用例被哪些测试单排过")
    @Parameter(name = "caseId", description = "用例编号", required = true, example = "93101")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<TestRunRespVO>> getRunListByCase(@RequestParam("caseId") Long caseId) {
        return success(testTaskService.getRunListByCase(caseId));
    }

    // ==================== 执行 ====================

    @PostMapping("/run-case")
    @Operation(summary = "执行用例",
            description = "提交每个步骤的结果；用例级结果由步骤结果算出（默认 pass，遇到非 pass/n-a 以它为准，"
                    + "遇到 fail 直接结束）。会同时写：执行历史、用例的最近结果、run 的状态")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<String> runCase(@Valid @RequestBody TestRunCaseReqVO reqVO) {
        return success(testTaskService.runCase(reqVO));
    }

    @GetMapping("/result-list")
    @Operation(summary = "某条执行记录的历史结果（最新在前）")
    @Parameter(name = "runId", description = "执行记录编号", required = true, example = "94151")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<TestResultRespVO>> getResultList(@RequestParam("runId") Long runId) {
        return success(testTaskService.getResultList(runId));
    }

    // ==================== 执行失败 → 建缺陷 ====================

    @PostMapping("/create-bug")
    @Operation(summary = "从用例执行结果建缺陷",
            description = "会把「来源用例 + 来源用例版本（冻结）+ 来源测试单」写进缺陷；"
                    + "复现步骤不填就按用例步骤自动生成，并标出失败的那一步")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:update')")
    public CommonResult<Long> createBugForRun(@Valid @RequestBody TestRunBugReqVO reqVO) {
        return success(testTaskService.createBugForRun(reqVO));
    }

    @GetMapping("/bug-list-by-case")
    @Operation(summary = "某条用例跑出来的缺陷")
    @Parameter(name = "caseId", description = "用例编号", required = true, example = "93102")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<BugRespVO>> getBugListByCase(@RequestParam("caseId") Long caseId) {
        List<BugRespVO> result = new ArrayList<>();
        for (var bug : testTaskService.getBugListByCase(caseId)) {
            result.add(BeanUtils.toBean(bug, BugRespVO.class));
        }
        return success(result);
    }

    @GetMapping("/bug-list-by-task")
    @Operation(summary = "某个测试单跑出来的缺陷")
    @Parameter(name = "taskId", description = "测试单编号", required = true, example = "94101")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<BugRespVO>> getBugListByTask(@RequestParam("taskId") Long taskId) {
        List<BugRespVO> result = new ArrayList<>();
        for (var bug : testTaskService.getBugListByTask(taskId)) {
            result.add(BeanUtils.toBean(bug, BugRespVO.class));
        }
        return success(result);
    }

    // ==================== 枚举 ====================

    @GetMapping("/status-list")
    @Operation(summary = "获得测试单状态列表")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<Map<String, String>>> getStatusList() {
        List<Map<String, String>> result = new ArrayList<>();
        for (TestTaskStatusEnum item : TestTaskStatusEnum.values()) {
            result.add(Map.of("value", item.getStatus(), "label", item.getName()));
        }
        return success(result);
    }

    @GetMapping("/result-enum-list")
    @Operation(summary = "获得执行结果列表（含「未执行」占位）")
    @PreAuthorize("@ss.hasPermission('zentao:testtask:query')")
    public CommonResult<List<Map<String, String>>> getResultEnumList() {
        List<Map<String, String>> result = new ArrayList<>();
        result.add(Map.of("value", "", "label", "未执行"));
        for (TestResultEnum item : TestResultEnum.values()) {
            result.add(Map.of("value", item.getResult(), "label", item.getName()));
        }
        return success(result);
    }

}

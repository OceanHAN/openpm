package cn.iocoder.yudao.module.zentao.controller.admin.testcase;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseReviewReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseSpecRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseStepVO;
import cn.iocoder.yudao.module.zentao.enums.testcase.CaseStageEnum;
import cn.iocoder.yudao.module.zentao.enums.testcase.CaseStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.testcase.CaseTypeEnum;
import cn.iocoder.yudao.module.zentao.service.testcase.CaseService;
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
 * 测试用例 Controller
 *
 * <p>用例是「头部 + 版本快照 + 步骤」三张表，读详情时三张拼一起。
 * 版本规则的判据是**步骤**：只有步骤变了才 version+1，并把状态打回「待评审」。
 */
@Tag(name = "管理后台 - 禅道测试用例")
@RestController
@RequestMapping("/zentao/testcase")
@Validated
public class CaseController {

    @Resource
    private CaseService caseService;

    // ==================== CRUD ====================

    @PostMapping("/create")
    @Operation(summary = "新建测试用例",
            description = "关联需求时会记录需求当前版本（冻结）；steps 的 parent 是「本次提交数组里父步骤组的 0 基下标」")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:create')")
    public CommonResult<Long> createCase(@Valid @RequestBody CaseSaveReqVO reqVO) {
        return success(caseService.createCase(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改测试用例",
            description = "**只有步骤变化才产生新版本**；步骤一变状态会被打回「待评审」。"
                    + "只改标题/前置条件/优先级/状态时不升版本，当前版本的快照原地改写")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:update')")
    public CommonResult<Boolean> updateCase(@Valid @RequestBody CaseSaveReqVO reqVO) {
        caseService.updateCase(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除测试用例", description = "步骤与版本快照一起物理清理")
    @Parameter(name = "id", description = "用例编号", required = true, example = "93101")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:delete')")
    public CommonResult<Boolean> deleteCase(@RequestParam("id") Long id) {
        caseService.deleteCase(id);
        return success(true);
    }

    // ==================== 查询 ====================

    @GetMapping("/get")
    @Operation(summary = "获得用例详情（默认当前版本，可指定历史版本）",
            description = "返回时会叠加该版本的快照标题/前置条件，并带上层级编号算好的步骤")
    @Parameter(name = "id", description = "用例编号", required = true, example = "93101")
    @Parameter(name = "version", description = "版本号；不传或传 0 表示当前版本", example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:query')")
    public CommonResult<CaseRespVO> getCase(@RequestParam("id") Long id,
                                            @RequestParam(value = "version", required = false) Integer version) {
        return success(caseService.getCase(id, version));
    }

    @GetMapping("/page")
    @Operation(summary = "用例分页查询",
            description = "module 会展开成「自己+子孙」；needConfirm=true 只看关联需求已升版的用例")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:query')")
    public CommonResult<PageResult<CaseRespVO>> getCasePage(@Valid CasePageReqVO reqVO) {
        return success(caseService.getCasePage(reqVO));
    }

    @GetMapping("/step-list")
    @Operation(summary = "获得某版本的用例步骤（带 1. / 1.1 层级编号）")
    @Parameter(name = "id", description = "用例编号", required = true, example = "93101")
    @Parameter(name = "version", description = "版本号；不传或传 0 表示当前版本", example = "2")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:query')")
    public CommonResult<List<CaseStepVO>> getStepList(@RequestParam("id") Long id,
                                                      @RequestParam(value = "version", required = false) Integer version) {
        return success(caseService.getStepList(id, version));
    }

    @GetMapping("/spec-list")
    @Operation(summary = "获得用例的版本历史", description = "最新在前，每条带该版本的步骤数")
    @Parameter(name = "id", description = "用例编号", required = true, example = "93101")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:query')")
    public CommonResult<List<CaseSpecRespVO>> getSpecList(@RequestParam("id") Long id) {
        return success(caseService.getSpecList(id));
    }

    @GetMapping("/list-by-story")
    @Operation(summary = "获得某需求关联的用例")
    @Parameter(name = "story", description = "需求编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:query')")
    public CommonResult<List<CaseRespVO>> getListByStory(@RequestParam("story") Long story) {
        return success(caseService.getCaseListByStory(story));
    }

    // ==================== 评审 / 需求变更确认 ====================

    @PutMapping("/review")
    @Operation(summary = "评审用例", description = "只有「待评审」（wait）的用例能评审；结果 normal/blocked/investigate")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:update')")
    public CommonResult<Boolean> reviewCase(@Valid @RequestBody CaseReviewReqVO reqVO) {
        caseService.reviewCase(reqVO);
        return success(true);
    }

    @PutMapping("/confirm-story-change")
    @Operation(summary = "确认需求变更",
            description = "把用例冻结的 storyVersion 追平到需求当前版本（禅道的 confirmStoryChange）")
    @Parameter(name = "id", description = "用例编号", required = true, example = "93103")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:update')")
    public CommonResult<Boolean> confirmStoryChange(@RequestParam("id") Long id) {
        caseService.confirmStoryChange(id);
        return success(true);
    }

    // ==================== 枚举 ====================

    @GetMapping("/type-list")
    @Operation(summary = "获得用例类型列表")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:query')")
    public CommonResult<List<Map<String, String>>> getTypeList() {
        List<Map<String, String>> result = new ArrayList<>();
        for (CaseTypeEnum item : CaseTypeEnum.values()) {
            result.add(Map.of("value", item.getType(), "label", item.getName()));
        }
        return success(result);
    }

    @GetMapping("/stage-list")
    @Operation(summary = "获得测试环节列表", description = "用例的 stage 是逗号列表，可以多选")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:query')")
    public CommonResult<List<Map<String, String>>> getStageList() {
        List<Map<String, String>> result = new ArrayList<>();
        for (CaseStageEnum item : CaseStageEnum.values()) {
            result.add(Map.of("value", item.getStage(), "label", item.getName()));
        }
        return success(result);
    }

    @GetMapping("/status-list")
    @Operation(summary = "获得用例状态列表")
    @PreAuthorize("@ss.hasPermission('zentao:testcase:query')")
    public CommonResult<List<Map<String, String>>> getStatusList() {
        List<Map<String, String>> result = new ArrayList<>();
        for (CaseStatusEnum item : CaseStatusEnum.values()) {
            result.add(Map.of("value", item.getStatus(), "label", item.getName()));
        }
        return success(result);
    }

}

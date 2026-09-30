package cn.iocoder.yudao.module.zentao.controller.admin.program;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.service.program.ProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 项目集 Controller
 *
 * 对应禅道 {@code module/program/}。项目集、项目、执行是同一张表（zt_project）的三种 type，
 * 本控制器的每个接口都只能读写 {@code type='program'} 的行。
 */
@Tag(name = "管理后台 - 项目集")
@RestController
@RequestMapping("/zentao/program")
@Validated
public class ProgramController {

    @Resource
    private ProgramService programService;

    @PostMapping("/create")
    @Operation(summary = "创建项目集", description = "同级下名称唯一；path/grade 按逗号格式自动算出")
    @PreAuthorize("@ss.hasPermission('zentao:program:create')")
    public CommonResult<Long> createProgram(@Valid @RequestBody ProgramSaveReqVO createReqVO) {
        return success(programService.createProgram(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改项目集", description = "换上级项目集会重算整棵子树的 path/grade")
    @PreAuthorize("@ss.hasPermission('zentao:program:update')")
    public CommonResult<Boolean> updateProgram(@Valid @RequestBody ProgramSaveReqVO updateReqVO) {
        programService.updateProgram(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目集",
            description = "下级还有项目集/项目/产品时拒绝删除（静默级联删除比报错危险得多）")
    @Parameter(name = "id", description = "项目集编号", required = true, example = "9001")
    @PreAuthorize("@ss.hasPermission('zentao:program:delete')")
    public CommonResult<Boolean> deleteProgram(@RequestParam("id") Long id) {
        programService.deleteProgram(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除项目集")
    @Parameter(name = "ids", description = "项目集编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:program:delete')")
    public CommonResult<Boolean> deleteProgramList(@RequestParam("ids") List<Long> ids) {
        programService.deleteProgramList(ids);
        return success(true);
    }

    // ==================== 状态流转 ====================

    @PutMapping("/start")
    @Operation(summary = "开始项目集", description = "未开始/已挂起 → 进行中")
    @Parameter(name = "id", description = "项目集编号", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:program:update')")
    public CommonResult<Boolean> startProgram(@RequestParam("id") Long id) {
        programService.startProgram(id);
        return success(true);
    }

    @PutMapping("/suspend")
    @Operation(summary = "挂起项目集")
    @Parameter(name = "id", description = "项目集编号", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:program:update')")
    public CommonResult<Boolean> suspendProgram(@RequestParam("id") Long id) {
        programService.suspendProgram(id);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活项目集", description = "已挂起 → 进行中")
    @Parameter(name = "id", description = "项目集编号", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:program:update')")
    public CommonResult<Boolean> activateProgram(@RequestParam("id") Long id) {
        programService.activateProgram(id);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭项目集")
    @Parameter(name = "id", description = "项目集编号", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:program:update')")
    public CommonResult<Boolean> closeProgram(@RequestParam("id") Long id,
                                              @RequestParam(value = "reason", required = false) String reason) {
        programService.closeProgram(id, reason);
        return success(true);
    }

    // ==================== 读 ====================

    @GetMapping("/get")
    @Operation(summary = "获得项目集", description = "附带下级项目集数/项目数/产品数")
    @Parameter(name = "id", description = "项目集编号", required = true, example = "9001")
    @PreAuthorize("@ss.hasPermission('zentao:program:query')")
    public CommonResult<ProgramRespVO> getProgram(@RequestParam("id") Long id) {
        return success(programService.fillStats(programService.getProgram(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目集分页")
    @PreAuthorize("@ss.hasPermission('zentao:program:query')")
    public CommonResult<PageResult<ProgramRespVO>> getProgramPage(@Valid ProgramPageReqVO pageReqVO) {
        PageResult<ProjectDO> page = programService.getProgramPage(pageReqVO);
        return success(new PageResult<>(programService.fillStatsList(page.getList()), page.getTotal()));
    }

    @GetMapping("/list")
    @Operation(summary = "获得全部项目集", description = "按 order 排序，供树形展示/下拉使用")
    @PreAuthorize("@ss.hasPermission('zentao:program:query')")
    public CommonResult<List<ProgramRespVO>> getProgramList() {
        return success(programService.fillStatsList(programService.getProgramList()));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得未关闭的项目集（下拉）")
    @PreAuthorize("@ss.hasPermission('zentao:program:query')")
    public CommonResult<List<ProgramRespVO>> getProgramSimpleList() {
        return success(BeanUtils.toBean(programService.getProgramSimpleList(), ProgramRespVO.class));
    }

    @GetMapping("/list-by-parent")
    @Operation(summary = "获得下级项目集")
    @Parameter(name = "parent", description = "上级项目集编号，0 表示顶级", required = true, example = "0")
    @PreAuthorize("@ss.hasPermission('zentao:program:query')")
    public CommonResult<List<ProgramRespVO>> getProgramListByParent(@RequestParam("parent") Long parent) {
        return success(programService.fillStatsList(programService.getProgramListByParent(parent)));
    }

    @GetMapping("/project-list")
    @Operation(summary = "获得项目集下的项目", description = "项目靠 parent 指向项目集")
    @Parameter(name = "programId", description = "项目集编号", required = true, example = "9001")
    @PreAuthorize("@ss.hasPermission('zentao:program:query')")
    public CommonResult<List<ProjectRespVO>> getProjectList(@RequestParam("programId") Long programId) {
        return success(BeanUtils.toBean(programService.getProjectList(programId), ProjectRespVO.class));
    }

    @GetMapping("/product-list")
    @Operation(summary = "获得项目集下的产品", description = "产品靠 zt_product.program 指向项目集")
    @Parameter(name = "programId", description = "项目集编号", required = true, example = "9001")
    @PreAuthorize("@ss.hasPermission('zentao:program:query')")
    public CommonResult<List<ProductRespVO>> getProductList(@RequestParam("programId") Long programId) {
        return success(BeanUtils.toBean(programService.getProductList(programId), ProductRespVO.class));
    }

}

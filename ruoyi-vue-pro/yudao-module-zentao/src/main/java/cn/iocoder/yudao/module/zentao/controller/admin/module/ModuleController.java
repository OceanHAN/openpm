package cn.iocoder.yudao.module.zentao.controller.admin.module;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleOrderReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleTypeRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.module.ModuleDO;
import cn.iocoder.yudao.module.zentao.enums.module.ModuleTypeEnum;
import cn.iocoder.yudao.module.zentao.service.module.ModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 模块树 Controller
 *
 * <p>禅道的模块树是**通用树**：同一个接口用 {@code root + type + branch} 定位一棵树，
 * 需求模块、缺陷模块、任务模块、用例模块、产品线全都走这套接口。
 */
@Tag(name = "管理后台 - 禅道模块树")
@RestController
@RequestMapping("/zentao/module")
@Validated
public class ModuleController {

    @Resource
    private ModuleService moduleService;

    @PostMapping("/create")
    @Operation(summary = "创建模块")
    @PreAuthorize("@ss.hasPermission('zentao:module:create')")
    public CommonResult<Long> createModule(@Valid @RequestBody ModuleSaveReqVO createReqVO) {
        return success(moduleService.createModule(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改模块", description = "改上级模块（移动）会递归重算子孙的 path/grade")
    @PreAuthorize("@ss.hasPermission('zentao:module:update')")
    public CommonResult<Boolean> updateModule(@Valid @RequestBody ModuleSaveReqVO updateReqVO) {
        moduleService.updateModule(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-order")
    @Operation(summary = "批量更新模块排序")
    @PreAuthorize("@ss.hasPermission('zentao:module:update')")
    public CommonResult<Boolean> updateOrder(@Valid @RequestBody ModuleOrderReqVO reqVO) {
        moduleService.updateOrder(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模块",
            description = "连带删除所有子模块；原本挂在被删模块上的需求/任务/缺陷会改挂到上级模块")
    @Parameter(name = "id", description = "模块编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('zentao:module:delete')")
    public CommonResult<Boolean> deleteModule(@RequestParam("id") Long id) {
        moduleService.deleteModule(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得模块")
    @Parameter(name = "id", description = "模块编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('zentao:module:query')")
    public CommonResult<ModuleRespVO> getModule(@RequestParam("id") Long id) {
        return success(toRespVO(moduleService.getModule(id)));
    }

    @GetMapping("/list")
    @Operation(summary = "获得模块列表（平铺）", description = "按 root + type 定位一棵树，branch 可选")
    @Parameter(name = "root", description = "根对象：产品 id / 执行 id", required = true, example = "1")
    @Parameter(name = "type", description = "树类型：story/task/bug/case/caselib/doc/api/line", required = true, example = "story")
    @Parameter(name = "branch", description = "分支/平台，不传表示不限")
    @PreAuthorize("@ss.hasPermission('zentao:module:query')")
    public CommonResult<List<ModuleRespVO>> getModuleList(@RequestParam("root") Long root,
                                                         @RequestParam("type") String type,
                                                         @RequestParam(value = "branch", required = false) Long branch) {
        return success(BeanUtils.toBean(moduleService.getModuleList(root, type, branch), ModuleRespVO.class, this::fillRespVO));
    }

    @GetMapping("/tree")
    @Operation(summary = "获得模块树（嵌套）", description = "供前端树形控件直接使用")
    @Parameter(name = "root", description = "根对象", required = true, example = "1")
    @Parameter(name = "type", description = "树类型", required = true, example = "story")
    @Parameter(name = "branch", description = "分支/平台，不传表示不限")
    @PreAuthorize("@ss.hasPermission('zentao:module:query')")
    public CommonResult<List<ModuleRespVO>> getModuleTree(@RequestParam("root") Long root,
                                                          @RequestParam("type") String type,
                                                          @RequestParam(value = "branch", required = false) Long branch) {
        return success(moduleService.getModuleTree(root, type, branch));
    }

    @GetMapping("/type-list")
    @Operation(summary = "获得模块树类型列表", description = "供前端下拉展示")
    @PreAuthorize("@ss.hasPermission('zentao:module:query')")
    public CommonResult<List<ModuleTypeRespVO>> getTypeList() {
        List<ModuleTypeRespVO> list = Arrays.stream(ModuleTypeEnum.values())
                .map(item -> new ModuleTypeRespVO(item.getType(), item.getName()))
                .toList();
        return success(list);
    }

    private ModuleRespVO toRespVO(ModuleDO module) {
        ModuleRespVO vo = BeanUtils.toBean(module, ModuleRespVO.class);
        fillRespVO(vo);
        return vo;
    }

    private void fillRespVO(ModuleRespVO vo) {
        if (vo != null) {
            vo.setTypeName(ModuleTypeEnum.nameOf(vo.getType()));
        }
    }

}

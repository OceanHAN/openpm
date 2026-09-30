package cn.iocoder.yudao.module.zentao.controller.admin.doc;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocLibRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocLibSaveReqVO;
import cn.iocoder.yudao.module.zentao.enums.doc.DocLibTypeEnum;
import cn.iocoder.yudao.module.zentao.service.doc.DocService;
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
 * 文档库 Controller
 *
 * <p>文档库决定「文档挂在哪个对象上」：
 * <ul>
 *   <li>{@code type=product/project/execution}：跟随对象的主库（{@code main=1}），不能删</li>
 *   <li>{@code type=custom}：团队空间下的自定义库</li>
 * </ul>
 */
@Tag(name = "管理后台 - 禅道文档库")
@RestController
@RequestMapping("/zentao/doc/lib")
@Validated
public class DocLibController {

    @Resource
    private DocService docService;

    @PostMapping("/create")
    @Operation(summary = "新建文档库",
            description = "product/project/execution 类型必须给对应对象编号；custom 类型必须挂在团队空间下")
    @PreAuthorize("@ss.hasPermission('zentao:doc:create')")
    public CommonResult<Long> createLib(@Valid @RequestBody DocLibSaveReqVO reqVO) {
        return success(docService.createLib(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改文档库")
    @PreAuthorize("@ss.hasPermission('zentao:doc:update')")
    public CommonResult<Boolean> updateLib(@Valid @RequestBody DocLibSaveReqVO reqVO) {
        docService.updateLib(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文档库",
            description = "主库（跟随产品/项目/执行）不允许删除；库内还有文档时也会拒绝（与禅道不同，见 README 说明）")
    @Parameter(name = "id", description = "文档库编号", required = true, example = "91006")
    @PreAuthorize("@ss.hasPermission('zentao:doc:delete')")
    public CommonResult<Boolean> deleteLib(@RequestParam("id") Long id) {
        docService.deleteLib(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得文档库")
    @Parameter(name = "id", description = "文档库编号", required = true, example = "91001")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<DocLibRespVO> getLib(@RequestParam("id") Long id) {
        DocLibRespVO vo = BeanUtils.toBean(docService.getLib(id), DocLibRespVO.class);
        DocLibTypeEnum typeEnum = DocLibTypeEnum.of(vo.getType());
        vo.setTypeName(typeEnum == null ? vo.getType() : typeEnum.getName());
        vo.setMain(Boolean.TRUE.equals(vo.getMain()));
        return success(vo);
    }

    @GetMapping("/list")
    @Operation(summary = "获得文档库列表",
            description = "给了 type 就查某个对象（产品/项目/执行）下的库；只给 parent 就查自定义空间下的库")
    @Parameter(name = "type", description = "库类型", example = "product")
    @Parameter(name = "objectID", description = "对象编号（type 对应的产品/项目/执行 id）", example = "1")
    @Parameter(name = "parent", description = "自定义空间编号（查空间下的库时用）", example = "91005")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<List<DocLibRespVO>> getLibList(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "objectID", required = false) Long objectID,
            @RequestParam(value = "parent", required = false) Long parent) {
        if (!org.springframework.util.StringUtils.hasText(type)) {
            // 查自定义空间列表（parent=0 时返回全部空间）
            List<DocLibRespVO> result = new ArrayList<>();
            for (var lib : docService.getLibListByParent(parent)) {
                DocLibRespVO vo = BeanUtils.toBean(lib, DocLibRespVO.class);
                DocLibTypeEnum typeEnum = DocLibTypeEnum.of(lib.getType());
                vo.setTypeName(typeEnum == null ? lib.getType() : typeEnum.getName());
                vo.setMain(lib.getMain() != null && lib.getMain() == 1);
                result.add(vo);
            }
            return success(result);
        }
        return success(docService.getLibRespList(type, objectID, parent));
    }

    @GetMapping("/type-list")
    @Operation(summary = "获得文档库类型列表")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<List<Map<String, String>>> getTypeList() {
        List<Map<String, String>> result = new ArrayList<>();
        for (DocLibTypeEnum item : DocLibTypeEnum.values()) {
            result.add(Map.of("value", item.getType(), "label", item.getName()));
        }
        return success(result);
    }

}

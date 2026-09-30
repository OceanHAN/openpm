package cn.iocoder.yudao.module.zentao.controller.admin.doc;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocContentRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocMoveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocDO;
import cn.iocoder.yudao.module.zentao.enums.doc.DocStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.doc.DocTypeEnum;
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
 * 文档 Controller
 *
 * <p>一张表两种对象：{@code type=chapter} 是章节（树的中间节点），其余是文档。
 * 所以「章节树」和「文档列表」是两个接口，各查各的。
 */
@Tag(name = "管理后台 - 禅道文档")
@RestController
@RequestMapping("/zentao/doc")
@Validated
public class DocController {

    @Resource
    private DocService docService;

    // ==================== 文档 CRUD ====================

    @PostMapping("/create")
    @Operation(summary = "新建文档/章节",
            description = "type=chapter 建章节（不需要正文）；type=url 必须给合法链接；type=attachment 必须给附件编号")
    @PreAuthorize("@ss.hasPermission('zentao:doc:create')")
    public CommonResult<Long> createDoc(@Valid @RequestBody DocSaveReqVO reqVO) {
        return success(docService.createDoc(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改文档/章节",
            description = "正文/标题变化才会产生新版本；只改库/章节/权限等基础信息时版本号不变")
    @PreAuthorize("@ss.hasPermission('zentao:doc:update')")
    public CommonResult<Boolean> updateDoc(@Valid @RequestBody DocSaveReqVO reqVO) {
        docService.updateDoc(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文档/章节", description = "章节下还有子节点时拒绝删除")
    @Parameter(name = "id", description = "文档编号", required = true, example = "91013")
    @PreAuthorize("@ss.hasPermission('zentao:doc:delete')")
    public CommonResult<Boolean> deleteDoc(@RequestParam("id") Long id) {
        docService.deleteDoc(id);
        return success(true);
    }

    // ==================== 查询 ====================

    @GetMapping("/get")
    @Operation(summary = "获得文档详情（默认当前版本，可指定历史版本）")
    @Parameter(name = "id", description = "文档编号", required = true, example = "91013")
    @Parameter(name = "version", description = "版本号；不传或传 0 表示当前版本", example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<DocRespVO> getDoc(@RequestParam("id") Long id,
                                          @RequestParam(value = "version", required = false) Integer version) {
        return success(docService.getDoc(id, version));
    }

    @GetMapping("/view")
    @Operation(summary = "浏览文档", description = "返回详情并把浏览次数 +1（仅已发布的文档计数）")
    @Parameter(name = "id", description = "文档编号", required = true, example = "91013")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<DocRespVO> viewDoc(@RequestParam("id") Long id) {
        return success(docService.viewDoc(id));
    }

    @GetMapping("/page")
    @Operation(summary = "文档分页查询",
            description = "spaceType + spaceObjectID 可以一次查出某个空间下所有库的文档；excludeChapter=true 排除章节")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<PageResult<DocRespVO>> getDocPage(@Valid DocPageReqVO reqVO) {
        PageResult<DocDO> page = docService.getDocPage(reqVO);
        return success(new PageResult<>(convertList(page.getList()), page.getTotal()));
    }

    @GetMapping("/chapter-tree")
    @Operation(summary = "获得某库的章节树", description = "只含 type=chapter 的行，节点上带直属文档数")
    @Parameter(name = "lib", description = "文档库编号", required = true, example = "91001")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<List<DocRespVO>> getChapterTree(@RequestParam("lib") Long lib) {
        return success(docService.getChapterTree(lib));
    }

    @GetMapping("/content-list")
    @Operation(summary = "获得文档的版本历史", description = "最新在前；version=0 的草稿排在最后")
    @Parameter(name = "id", description = "文档编号", required = true, example = "91013")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<List<DocContentRespVO>> getContentList(@RequestParam("id") Long id) {
        return success(docService.getContentList(id));
    }

    @GetMapping("/list-by-lib")
    @Operation(summary = "获得某库下的文档列表（不含章节）")
    @Parameter(name = "lib", description = "文档库编号", required = true, example = "91001")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<List<DocRespVO>> getListByLib(@RequestParam("lib") Long lib) {
        DocPageReqVO reqVO = new DocPageReqVO();
        reqVO.setLib(lib);
        reqVO.setExcludeChapter(true);
        reqVO.setPageSize(100);
        PageResult<DocDO> page = docService.getDocPage(reqVO);
        return success(convertList(page.getList()));
    }

    // ==================== 结构 / 状态 ====================

    @PutMapping("/move")
    @Operation(summary = "移动文档/章节", description = "可跨库；不能移动到自己的子节点下；子树 path/grade 会一起重算")
    @PreAuthorize("@ss.hasPermission('zentao:doc:update')")
    public CommonResult<Boolean> moveDoc(@Valid @RequestBody DocMoveReqVO reqVO) {
        docService.moveDoc(reqVO);
        return success(true);
    }

    @PutMapping("/publish")
    @Operation(summary = "发布草稿", description = "把 version=0 的草稿行升成 v1，文档状态转为已发布")
    @Parameter(name = "id", description = "文档编号", required = true, example = "91015")
    @PreAuthorize("@ss.hasPermission('zentao:doc:update')")
    public CommonResult<Boolean> publishDoc(@RequestParam("id") Long id) {
        docService.publishDoc(id);
        return success(true);
    }

    // ==================== 枚举 ====================

    @GetMapping("/type-list")
    @Operation(summary = "获得文档类型列表", description = "chapter 是章节，不是文档")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<List<Map<String, Object>>> getTypeList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DocTypeEnum item : DocTypeEnum.values()) {
            result.add(Map.of("value", item.getType(), "label", item.getName(),
                    "chapter", DocTypeEnum.isChapter(item.getType())));
        }
        return success(result);
    }

    @GetMapping("/status-list")
    @Operation(summary = "获得文档状态列表")
    @PreAuthorize("@ss.hasPermission('zentao:doc:query')")
    public CommonResult<List<Map<String, String>>> getStatusList() {
        List<Map<String, String>> result = new ArrayList<>();
        for (DocStatusEnum item : DocStatusEnum.values()) {
            result.add(Map.of("value", item.getStatus(), "label", item.getName()));
        }
        return success(result);
    }

    // ==================== 内部 ====================

    private List<DocRespVO> convertList(List<DocDO> list) {
        // 转成 VO 的逻辑收敛在服务层：只有它拿得到 mapper，
        // 而列表里需要 libName / parentTitle，在控制器里补就变成 N+1
        return docService.getDocRespList(list);
    }

}

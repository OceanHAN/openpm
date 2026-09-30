package cn.iocoder.yudao.module.zentao.controller.admin.file;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zentao.controller.admin.file.vo.FileBindReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.file.vo.FileRenameReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.file.vo.FileRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.file.ZentaoFileDO;
import cn.iocoder.yudao.module.zentao.service.file.ZentaoFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 附件 Controller
 *
 * <p>类名带 Zentao 前缀是**必须的**：yudao 的 infra 模块已经有
 * {@code cn.iocoder.yudao.module.infra.controller.admin.file.FileController}，
 * Spring 组件扫描默认按「短类名首字母小写」命名 bean，两个 fileController 会直接撞车
 * 启动失败（ConflictingBeanDefinitionException）。Service / Mapper 同理。
 *
 * <p>附件是所有业务模块的公共能力：需求、任务、Bug、文档等都用同一套接口。
 * 接口按禅道 {@code module/file} 的语义组织：
 * <ul>
 *   <li>上传时可带 {@code gid}（对象还没保存），保存对象后调 {@code /bind-by-gid} 补绑</li>
 *   <li>{@code /download} 累加下载次数并返回真实访问地址（字节由 yudao 文件服务托管）</li>
 *   <li>{@code objectType} 取值沿用禅道：story / task / bug / doc / project / execution …</li>
 * </ul>
 */
@Tag(name = "管理后台 - 禅道附件")
@RestController
@RequestMapping("/zentao/file")
@Validated
public class ZentaoFileController {

    // 字段名同样避开 infra 的同名 bean，理由见 ZentaoFileServiceImpl
    @Resource
    private ZentaoFileService zentaoFileService;

    // ==================== 上传与绑定 ====================

    @PostMapping("/upload")
    @Operation(summary = "上传附件",
            description = "objectType/objectID 可留空表示「先上传后绑定」，此时请带上 gid")
    @PreAuthorize("@ss.hasPermission('zentao:file:create')")
    public CommonResult<FileRespVO> upload(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "objectType", required = false) String objectType,
                                           @RequestParam(value = "objectID", required = false) Long objectID,
                                           @RequestParam(value = "gid", required = false) String gid) {
        ZentaoFileDO fileDO = zentaoFileService.upload(file, objectType, objectID, gid);
        return success(convert(fileDO));
    }

    @PostMapping("/bind-by-gid")
    @Operation(summary = "按 gid 绑定附件到对象",
            description = "新建对象的场景：上传时对象 id 还不存在，保存成功后调用本接口补绑")
    @PreAuthorize("@ss.hasPermission('zentao:file:create')")
    public CommonResult<Integer> bindByGid(@Valid @RequestBody FileBindReqVO reqVO) {
        return success(zentaoFileService.bindByGid(reqVO.getGid(), reqVO.getObjectType(), reqVO.getObjectID()));
    }

    // ==================== 查询 ====================

    @GetMapping("/list")
    @Operation(summary = "获得某对象的附件列表")
    @Parameter(name = "objectType", description = "对象类型", required = true, example = "story")
    @Parameter(name = "objectID", description = "对象编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:file:query')")
    public CommonResult<List<FileRespVO>> getListByObject(@RequestParam("objectType") String objectType,
                                                          @RequestParam("objectID") Long objectID) {
        return success(convertList(zentaoFileService.getListByObject(objectType, objectID)));
    }

    @GetMapping("/list-by-gid")
    @Operation(summary = "获得某临时分组（gid）下的附件列表")
    @Parameter(name = "gid", description = "临时分组 id", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:file:query')")
    public CommonResult<List<FileRespVO>> getListByGid(@RequestParam("gid") String gid) {
        return success(convertList(zentaoFileService.getListByGid(gid)));
    }

    @GetMapping("/get")
    @Operation(summary = "获得附件")
    @Parameter(name = "id", description = "附件编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:file:query')")
    public CommonResult<FileRespVO> getFile(@RequestParam("id") Long id) {
        return success(convert(zentaoFileService.getFile(id)));
    }

    @GetMapping("/count")
    @Operation(summary = "获得某对象的附件数量")
    @Parameter(name = "objectType", description = "对象类型", required = true, example = "story")
    @Parameter(name = "objectID", description = "对象编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:file:query')")
    public CommonResult<Long> getCount(@RequestParam("objectType") String objectType,
                                       @RequestParam("objectID") Long objectID) {
        return success(zentaoFileService.countByObject(objectType, objectID));
    }

    // ==================== 下载 / 修改 / 删除 ====================

    @GetMapping("/download")
    @Operation(summary = "下载附件",
            description = "返回可访问的文件地址，并把下载次数 +1（禅道 file->download 的行为）")
    @Parameter(name = "id", description = "附件编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:file:query')")
    public CommonResult<String> download(@RequestParam("id") Long id) {
        return success(zentaoFileService.download(id));
    }

    @PutMapping("/rename")
    @Operation(summary = "重命名附件")
    @PreAuthorize("@ss.hasPermission('zentao:file:update')")
    public CommonResult<Boolean> rename(@Valid @RequestBody FileRenameReqVO reqVO) {
        zentaoFileService.rename(reqVO.getId(), reqVO.getTitle());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除附件")
    @Parameter(name = "id", description = "附件编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:file:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        zentaoFileService.delete(id);
        return success(true);
    }

    @DeleteMapping("/delete-by-object")
    @Operation(summary = "删除某对象下的全部附件",
            description = "对象被删除时清理附件，对应禅道 file->deleteByObject")
    @PreAuthorize("@ss.hasPermission('zentao:file:delete')")
    public CommonResult<Integer> deleteByObject(@RequestParam("objectType") String objectType,
                                                @RequestParam("objectID") Long objectID) {
        return success(zentaoFileService.deleteByObject(objectType, objectID));
    }

    // ==================== 转换 ====================

    private List<FileRespVO> convertList(List<ZentaoFileDO> list) {
        return list.stream().map(this::convert).toList();
    }

    private FileRespVO convert(ZentaoFileDO fileDO) {
        FileRespVO vo = new FileRespVO();
        vo.setId(fileDO.getId());
        vo.setTitle(fileDO.getTitle());
        vo.setExtension(fileDO.getExtension());
        vo.setSize(fileDO.getSize());
        vo.setSizeText(sizeText(fileDO.getSize()));
        vo.setObjectType(fileDO.getObjectType());
        vo.setObjectID(fileDO.getObjectID());
        vo.setGid(fileDO.getGid());
        vo.setUrl(fileDO.getPathname());
        vo.setAddedBy(fileDO.getAddedBy());
        vo.setAddedDate(fileDO.getAddedDate());
        vo.setDownloads(fileDO.getDownloads());
        vo.setImage(isImage(fileDO.getExtension()));
        return vo;
    }

    /**
     * 人类可读的大小文案：前端列表直接显示，省得每处都写一遍
     */
    private String sizeText(Long size) {
        if (size == null || size <= 0) {
            return "0 B";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        }
        return String.format("%.1f MB", size / 1024.0 / 1024.0);
    }

    private boolean isImage(String extension) {
        if (extension == null) {
            return false;
        }
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> true;
            default -> false;
        };
    }

}

package cn.iocoder.yudao.module.zentao.controller.admin.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 附件 Response VO")
@Data
public class FileRespVO {

    @Schema(description = "附件编号", example = "1")
    private Long id;

    @Schema(description = "文件名", example = "需求说明书.docx")
    private String title;

    @Schema(description = "扩展名", example = "docx")
    private String extension;

    @Schema(description = "字节大小", example = "20480")
    private Long size;

    @Schema(description = "大小文案", example = "20.0 KB")
    private String sizeText;

    @Schema(description = "所属对象类型", example = "story")
    private String objectType;

    @Schema(description = "所属对象编号", example = "1")
    private Long objectID;

    @Schema(description = "临时分组 id（为空表示已绑定对象）", example = "")
    private String gid;

    @Schema(description = "访问地址（由 yudao 文件服务提供）",
            example = "http://127.0.0.1:48080/admin-api/infra/file/4/get/20260913/x.docx")
    private String url;

    @Schema(description = "上传人", example = "admin")
    private String addedBy;

    @Schema(description = "上传时间")
    private LocalDateTime addedDate;

    @Schema(description = "下载次数", example = "0")
    private Integer downloads;

    @Schema(description = "是否图片（前端可直接预览）", example = "false")
    private Boolean image;

}

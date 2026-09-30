package cn.iocoder.yudao.module.zentao.controller.admin.doc.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 文档版本 Response VO")
@Data
public class DocContentRespVO {

    @Schema(description = "版本记录编号", example = "91022")
    private Long id;

    @Schema(description = "文档编号", example = "91013")
    private Long doc;

    @Schema(description = "版本号（0 为草稿）", example = "2")
    private Integer version;

    @Schema(description = "该版本标题", example = "产品需求说明书")
    private String title;

    @Schema(description = "该版本摘要", example = "补充验收标准")
    private String digest;

    @Schema(description = "该版本正文")
    private String content;

    @Schema(description = "Markdown 原始内容")
    private String rawContent;

    @Schema(description = "附件编号，逗号列表", example = "")
    private String files;

    @Schema(description = "内容类型", example = "html")
    private String type;

    @Schema(description = "创建人", example = "admin")
    private String addedBy;

    @Schema(description = "创建时间")
    private LocalDateTime addedDate;

    @Schema(description = "修改人", example = "admin")
    private String editedBy;

    @Schema(description = "修改时间")
    private LocalDateTime editedDate;

    @Schema(description = "是否当前版本", example = "true")
    private Boolean current;

    @Schema(description = "是否草稿位（version=0）", example = "false")
    private Boolean draft;

}

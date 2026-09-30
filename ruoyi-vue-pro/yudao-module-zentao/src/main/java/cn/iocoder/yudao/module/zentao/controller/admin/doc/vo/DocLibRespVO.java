package cn.iocoder.yudao.module.zentao.controller.admin.doc.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 文档库 Response VO")
@Data
public class DocLibRespVO {

    @Schema(description = "文档库编号", example = "91001")
    private Long id;

    @Schema(description = "库类型", example = "product")
    private String type;

    @Schema(description = "库类型文案", example = "产品文档库")
    private String typeName;

    @Schema(description = "父空间", example = "91005")
    private Long parent;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "库名称", example = "产品文档库")
    private String name;

    @Schema(description = "权限", example = "open")
    private String acl;

    @Schema(description = "私有库可见角色", example = "")
    private String groups;

    @Schema(description = "私有库可见用户", example = "")
    private String users;

    @Schema(description = "是否内置主库", example = "true")
    private Boolean main;

    @Schema(description = "库描述", example = "产品线对外文档")
    private String desc;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "库内文档数（不含章节）", example = "3")
    private Long docCount;

    @Schema(description = "创建人", example = "admin")
    private String addedBy;

    @Schema(description = "创建时间")
    private LocalDateTime addedDate;

}

package cn.iocoder.yudao.module.zentao.controller.admin.doc.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 文档新增/修改 Request VO")
@Data
public class DocSaveReqVO {

    @Schema(description = "文档编号（修改时必填）", example = "91013")
    private Long id;

    @Schema(description = "所属文档库", requiredMode = Schema.RequiredMode.REQUIRED, example = "91001")
    @NotNull(message = "所属文档库不能为空")
    private Long lib;

    @Schema(description = "上级章节（可为空）", example = "91011")
    private Long parent;

    @Schema(description = "所属模块（通用树 zt_module，type=doc）", example = "0")
    private Long module;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "产品需求说明书")
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 255, message = "文档标题长度不能超过 255 个字符")
    private String title;

    @Schema(description = "关键词", example = "需求,说明书")
    private String keywords;

    @Schema(description = "类型：chapter/html/markdown/text/url/word/ppt/excel/attachment",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "html")
    @NotBlank(message = "文档类型不能为空")
    private String type;

    @Schema(description = "状态：normal 已发布 / draft 草稿（缺省按已发布）", example = "normal")
    private String status;

    @Schema(description = "正文（text/html/markdown/url 用；草稿与正式版都用这个字段传）",
            example = "<h1>标题</h1><p>正文</p>")
    private String content;

    @Schema(description = "Markdown 原始内容（编辑器重新编辑时用）", example = "# 标题")
    private String rawContent;

    @Schema(description = "附件编号，逗号列表（attachment 类型必填）", example = "91031")
    private String files;

    @Schema(description = "权限：open 公开 / private 私有", example = "open")
    private String acl;

    @Schema(description = "私有文档可见角色", example = "")
    private String groups;

    @Schema(description = "私有文档可见用户", example = "")
    private String users;

}

package cn.iocoder.yudao.module.zentao.controller.admin.doc.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Schema(description = "管理后台 - 文档 Response VO")
@Data
public class DocRespVO {

    @Schema(description = "文档编号", example = "91013")
    private Long id;

    @Schema(description = "所属文档库", example = "91001")
    private Long lib;

    @Schema(description = "文档库名称", example = "产品文档库")
    private String libName;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "所属模块", example = "0")
    private Long module;

    @Schema(description = "上级章节", example = "91011")
    private Long parent;

    @Schema(description = "上级章节标题", example = "需求文档")
    private String parentTitle;

    @Schema(description = "章节路径", example = ",91011,91013,")
    private String path;

    @Schema(description = "层级", example = "2")
    private Integer grade;

    @Schema(description = "排序", example = "91013")
    private Integer order;

    @Schema(description = "标题", example = "产品需求说明书")
    private String title;

    @Schema(description = "关键词", example = "需求,说明书")
    private String keywords;

    @Schema(description = "类型", example = "html")
    private String type;

    @Schema(description = "类型文案", example = "富文本")
    private String typeName;

    @Schema(description = "是否章节", example = "false")
    private Boolean chapter;

    @Schema(description = "状态", example = "normal")
    private String status;

    @Schema(description = "状态文案", example = "已发布")
    private String statusName;

    @Schema(description = "当前版本号（0 表示只有草稿）", example = "2")
    private Integer version;

    @Schema(description = "浏览次数", example = "12")
    private Integer views;

    @Schema(description = "收藏次数", example = "0")
    private Integer collects;

    @Schema(description = "权限", example = "open")
    private String acl;

    @Schema(description = "私有可见角色", example = "")
    private String groups;

    @Schema(description = "私有可见用户", example = "")
    private String users;

    @Schema(description = "创建人", example = "admin")
    private String addedBy;

    @Schema(description = "创建时间")
    private LocalDateTime addedDate;

    @Schema(description = "最后修改人", example = "admin")
    private String editedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime editedDate;

    @Schema(description = "该版本的正文（读详情时按 version 叠加，列表里为空）")
    private String content;

    @Schema(description = "Markdown 原始内容")
    private String rawContent;

    @Schema(description = "附件编号，逗号列表", example = "")
    private String files;

    @Schema(description = "直属文档数（章节树接口填充）", example = "2")
    private Long docCount;

    @Schema(description = "子节点（章节树接口填充）")
    private List<DocRespVO> children;

}

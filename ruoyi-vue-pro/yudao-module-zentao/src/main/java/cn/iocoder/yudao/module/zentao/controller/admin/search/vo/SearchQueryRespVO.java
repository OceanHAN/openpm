package cn.iocoder.yudao.module.zentao.controller.admin.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 保存查询 Response VO")
@Data
public class SearchQueryRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "账号", example = "admin")
    private String account;

    @Schema(description = "模块", example = "story")
    private String module;

    @Schema(description = "查询名称", example = "激活的需求")
    private String title;

    @Schema(description = "表单定义快照（JSON）")
    private String form;

    /** 对应表里的 sql 列，但内容是条件 JSON —— 特意换个名字，免得调用方以为能直接拼 SQL */
    @Schema(description = "结构化条件 JSON（对应禅道的 sql 列）",
            example = "[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"active\"}]")
    private String conditions;

    @Schema(description = "是否快捷方式", example = "1")
    private Integer shortcut;

    @Schema(description = "是否公共查询", example = "0")
    private Integer common;

}

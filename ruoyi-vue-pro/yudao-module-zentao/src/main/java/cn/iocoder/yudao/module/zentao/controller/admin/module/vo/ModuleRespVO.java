package cn.iocoder.yudao.module.zentao.controller.admin.module.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 模块 Response VO")
@Data
public class ModuleRespVO {

    @Schema(description = "模块编号", example = "10")
    private Long id;

    @Schema(description = "所属根对象", example = "1")
    private Long root;

    @Schema(description = "树类型", example = "story")
    private String type;

    @Schema(description = "类型文案", example = "需求模块")
    private String typeName;

    @Schema(description = "所属分支/平台", example = "0")
    private Long branch;

    @Schema(description = "上级模块", example = "0")
    private Long parent;

    @Schema(description = "模块名称", example = "用户中心")
    private String name;

    @Schema(description = "路径，逗号格式：,1,3,", example = ",1,3,")
    private String path;

    @Schema(description = "层级，一级为 1", example = "2")
    private Integer grade;

    @Schema(description = "排序", example = "10")
    private Integer order;

    @Schema(description = "简称")
    private String shortName;

    @Schema(description = "负责人")
    private String owner;

    @Schema(description = "直接子模块数量", example = "2")
    private Integer childCount;

    @Schema(description = "子模块。只在树接口里填充")
    private List<ModuleRespVO> children = new ArrayList<>();

}

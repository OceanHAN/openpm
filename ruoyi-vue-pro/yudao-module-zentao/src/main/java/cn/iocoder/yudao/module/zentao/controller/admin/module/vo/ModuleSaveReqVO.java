package cn.iocoder.yudao.module.zentao.controller.admin.module.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模块创建/修改 Request VO
 *
 * <p>禅道里「模块」是通用树节点，必须同时给出 {@code root}（挂在哪棵树上）
 * 与 {@code type}（哪一棵树）。{@code branch} 只有需求/缺陷/用例这三类产品视图的树才用得到。
 */
@Schema(description = "管理后台 - 模块创建/修改 Request VO")
@Data
public class ModuleSaveReqVO {

    @Schema(description = "模块编号，新建时为空", example = "10")
    private Long id;

    @Schema(description = "所属根对象：需求/缺陷/用例树传产品 id，任务树传执行 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属根对象不能为空")
    private Long root;

    @Schema(description = "树类型：story/task/bug/case/caselib/doc/api/line", requiredMode = Schema.RequiredMode.REQUIRED, example = "story")
    @NotBlank(message = "模块树类型不能为空")
    private String type;

    @Schema(description = "所属分支/平台，0 表示主干", example = "0")
    private Long branch;

    @Schema(description = "上级模块，0 表示一级模块", example = "0")
    private Long parent;

    @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户中心")
    @NotBlank(message = "模块名称不能为空")
    @Size(max = 60, message = "模块名称长度不能超过 60 个字符")
    private String name;

    @Schema(description = "简称", example = "用户")
    @Size(max = 60, message = "简称长度不能超过 60 个字符")
    private String shortName;

    @Schema(description = "负责人", example = "admin")
    private String owner;

    @Schema(description = "排序。不传时自动取同级最大值 + 10", example = "10")
    private Integer order;

}

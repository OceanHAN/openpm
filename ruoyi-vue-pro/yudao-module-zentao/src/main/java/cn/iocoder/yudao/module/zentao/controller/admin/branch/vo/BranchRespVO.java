package cn.iocoder.yudao.module.zentao.controller.admin.branch.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 分支 Response VO")
@Data
public class BranchRespVO {

    @Schema(description = "分支编号。0 表示虚拟主干，不落库", example = "1")
    private Long id;

    @Schema(description = "所属产品", example = "2")
    private Long product;

    @Schema(description = "产品名称。方便列表直接展示（禅道列表也是这么拼的）", example = "数据治理平台")
    private String productName;

    @Schema(description = "分支/平台文案。产品类型为 platform 时是「平台」，否则是「分支」", example = "平台")
    private String branchLabel;

    @Schema(description = "分支/平台名称", example = "企业版")
    private String name;

    @Schema(description = "是否默认分支", example = "1")
    private Integer defaultFlag;

    @Schema(description = "状态：active/closed", example = "active")
    private String status;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "关闭时间")
    private LocalDateTime closedDate;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "是否是虚拟主干（id=0）", example = "false")
    private Boolean mainBranch;

}

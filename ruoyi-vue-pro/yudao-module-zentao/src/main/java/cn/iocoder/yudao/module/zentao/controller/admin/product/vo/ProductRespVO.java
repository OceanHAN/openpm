package cn.iocoder.yudao.module.zentao.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 产品 Response VO")
@Data
public class ProductRespVO {

    @Schema(description = "产品编号", example = "1")
    private Long id;

    @Schema(description = "所属项目集", example = "0")
    private Long program;

    @Schema(description = "所属产品线", example = "0")
    private Long line;

    @Schema(description = "产品名称", example = "禅道研发管理平台")
    private String name;

    @Schema(description = "产品代号", example = "ZENTAO")
    private String code;

    @Schema(description = "类型", example = "normal")
    private String type;

    @Schema(description = "状态", example = "normal")
    private String status;

    @Schema(description = "产品描述")
    private String desc;

    @Schema(description = "产品经理", example = "admin")
    private String PO;

    @Schema(description = "测试负责人", example = "admin")
    private String QD;

    @Schema(description = "研发负责人", example = "admin")
    private String RD;

    @Schema(description = "访问控制", example = "open")
    private String acl;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "关闭时间")
    private LocalDateTime closedDate;

    @Schema(description = "排序", example = "0")
    private Integer order;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 统计信息。只在该产品的详情/列表需要时填充，为 null 表示未计算。
     */
    @Schema(description = "关联统计（实时统计，非冗余存储）")
    private ProductStats stats;

    @Schema(description = "产品关联的业务对象统计")
    @Data
    public static class ProductStats {

        @Schema(description = "需求总数", example = "12")
        private Long totalStories;
        @Schema(description = "激活状态需求数", example = "8")
        private Long activeStories;
        @Schema(description = "已关闭需求数", example = "4")
        private Long closedStories;

        @Schema(description = "缺陷总数", example = "5")
        private Long totalBugs;
        @Schema(description = "未解决缺陷数", example = "3")
        private Long unresolvedBugs;
        @Schema(description = "已关闭缺陷数", example = "2")
        private Long closedBugs;

    }

}

package cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维度 Response VO（对齐 {@code zt_dimension} 的列）。
 *
 * <p>注意时间字段是 {@code LocalDateTime}：yudao 的 Jackson 把它序列化成**数字时间戳**，
 * 前端必须用 {@code @/utils/formatTime} 的 {@code formatDate} 格式化（坑位 #51）。
 */
@Schema(description = "管理后台 - 维度 Response VO")
@Data
public class DimensionRespVO {

    @Schema(description = "维度编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "维度名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "宏观管理维度")
    private String name;

    @Schema(description = "维度代号", requiredMode = Schema.RequiredMode.REQUIRED, example = "macro")
    private String code;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "访问控制：open 公开 / private 仅创建者与白名单", example = "open")
    private String acl;

    @Schema(description = "白名单账号逗号串（acl=private 时生效）", example = ",admin,")
    private String whitelist;

    @Schema(description = "创建人账号", example = "system")
    private String createdBy;

    @Schema(description = "创建时间（禅道 createdDate；序列化成时间戳，前端用 formatDate）")
    private LocalDateTime createdDate;

    @Schema(description = "最后修改人账号")
    private String editedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime editedDate;

}

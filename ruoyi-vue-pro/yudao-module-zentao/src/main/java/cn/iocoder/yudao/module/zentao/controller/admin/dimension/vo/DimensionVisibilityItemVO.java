package cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 可见性诊断的**单行**：某个维度对某个账号为什么可见 / 为什么不可见。
 *
 * <p>{@code reason} 是命中的那一句判据（与 {@code module/bi/model.php:41-65} 的分支一一对应）：
 * <pre>
 *   admin       超管直通（禅道 $app->user->admin → array_keys(全部对象)）
 *   open        acl = 'open'
 *   creator     createdBy = 账号
 *   whitelist   FIND_IN_SET(账号, whitelist)
 *   none        三条都不命中 → 不可见
 * </pre>
 */
@Schema(description = "管理后台 - 维度可见性诊断行")
@Data
public class DimensionVisibilityItemVO {

    @Schema(description = "维度编号", example = "1")
    private Long id;

    @Schema(description = "维度名称", example = "宏观管理维度")
    private String name;

    @Schema(description = "访问控制", example = "open")
    private String acl;

    @Schema(description = "创建人账号", example = "system")
    private String createdBy;

    @Schema(description = "白名单逗号串")
    private String whitelist;

    @Schema(description = "是否可见", example = "true")
    private Boolean visible;

    @Schema(description = "命中的判据：admin/open/creator/whitelist/none", example = "open")
    private String reason;

}

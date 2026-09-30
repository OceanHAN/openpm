package cn.iocoder.yudao.module.zentao.controller.admin.company.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 「谁是超管」的口径对照。
 *
 * <p>禅道：{@code zt_company.admins} 是逗号串，{@code strpos(admins, ",{$account},") !== false} 即超管；
 * yudao：超管是 {@code super_admin} 角色，而且 {@code PermissionServiceImpl} 对它**硬编码放行、不查权限表**
 * （这是「组织权限」那一轮踩过的坑，见 README 坑位 #24）。
 * 迁移时这两套口径必须人工对齐，这个接口就是把差异摆出来。
 */
@Schema(description = "管理后台 - 超管口径对照 Response VO")
@Data
public class CompanyAdminsRespVO {

    @Schema(description = "禅道口径：zt_company.admins 拆出来的账号")
    private List<String> zentaoAdmins;

    @Schema(description = "yudao 口径：拥有 super_admin 角色的用户账号")
    private List<String> yudaoSuperAdmins;

    @Schema(description = "两边都有（已对齐）")
    private List<String> matched;

    @Schema(description = "只在禅道 admins 里（yudao 侧要补角色）")
    private List<String> onlyInZentao;

    @Schema(description = "只在 yudao 超管角色里（禅道侧要补 admins）")
    private List<String> onlyInYudao;

    @Schema(description = "说明")
    private String note;

}

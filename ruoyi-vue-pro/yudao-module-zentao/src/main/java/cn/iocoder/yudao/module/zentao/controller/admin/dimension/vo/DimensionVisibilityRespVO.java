package cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 可见性口径自检（禅道 {@code biModel::getViewableObject('dimension')}）。
 *
 * <p>这是**只读诊断**接口，不是切换维度用的：它把「某个账号能看到哪些维度、每一行是因为哪一句判据」
 * 摆出来，专门用来核对第 ① 条照抄规则。做法参照 {@code company} 的 {@code /admins} 口径对照
 * （README 3.44②）：**迁移评审要能看见口径差异，而不是只相信接口没报错**。
 *
 * <p>为什么需要它：admin 是超管、走的是「直通全部」那条短路，所以用 admin 调任何接口都**验证不了**
 * {@code acl/createdBy/whitelist} 这三句；把判据单独做成一个只读视图，才能用任意账号（含非超管）
 * 复算一遍。
 */
@Schema(description = "管理后台 - 维度可见性口径自检")
@Data
public class DimensionVisibilityRespVO {

    @Schema(description = "被检查的账号（不传 = 当前登录账号）", example = "admin")
    private String account;

    @Schema(description = "该账号是不是超级管理员（yudao 的 super_admin 角色；禅道原本看 zt_company.admins）",
            example = "true")
    private Boolean superAdmin;

    @Schema(description = "禅道判据原文（照抄自 module/bi/model.php:41-65）")
    private String rule;

    @Schema(description = "逐行诊断（含不可见的那些）")
    private List<DimensionVisibilityItemVO> dimensions;

    @Schema(description = "可见维度编号")
    private List<Long> viewableIds;

    @Schema(description = "口径说明")
    private String note;

}

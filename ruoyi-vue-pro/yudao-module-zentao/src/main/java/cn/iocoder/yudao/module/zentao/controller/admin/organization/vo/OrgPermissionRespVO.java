package cn.iocoder.yudao.module.zentao.controller.admin.organization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 禅道视角的权限条目
 *
 * <p>yudao 的权限串是 {@code zentao:story:create} 这种「前缀:模块:方法」三段式，
 * 正好可以拆成禅道 {@code zt_grouppriv(group, module, method)} 的两级结构，
 * 所以这个 VO 就是「一个模块 + 它下面的方法列表」。
 */
@Schema(description = "管理后台 - 禅道视角的权限条目（模块 + 方法）")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrgPermissionRespVO {

    @Schema(description = "禅道模块名。对应 zt_grouppriv.module", example = "story")
    private String module;

    @Schema(description = "模块中文名（按禅道模块名映射）", example = "需求")
    private String moduleName;

    @Schema(description = "方法列表。对应 zt_grouppriv.method", example = "[\"query\",\"create\"]")
    private List<String> methods;

    @Schema(description = "权限条数", example = "5")
    private Integer count;

}

package cn.iocoder.yudao.module.zentao.controller.admin.organization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 禅道 → yudao 的组织权限字段映射（给迁移评审看的对照表）
 */
@Schema(description = "管理后台 - 禅道组织权限到 yudao 的映射")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrgMappingRespVO {

    @Schema(description = "禅道对象", example = "zt_user")
    private String zentaoTable;

    @Schema(description = "yudao 对应对象", example = "system_users")
    private String yudaoTable;

    @Schema(description = "迁移策略", example = "不迁移，直接复用")
    private String strategy;

    @Schema(description = "字段对照：禅道字段 → yudao 字段", example = "[\"account → username\",\"realname → nickname\"]")
    private List<String> fieldMapping;

    @Schema(description = "差异与注意事项")
    private List<String> notes;

}

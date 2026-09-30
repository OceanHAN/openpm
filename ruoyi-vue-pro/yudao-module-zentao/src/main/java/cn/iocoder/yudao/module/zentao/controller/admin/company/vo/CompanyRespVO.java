package cn.iocoder.yudao.module.zentao.controller.admin.company.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 公司 Response VO")
@Data
public class CompanyRespVO {

    @Schema(description = "编号（1 是默认公司）", example = "1")
    private Long id;

    @Schema(description = "公司名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "禅道软件")
    private String name;

    @Schema(description = "联系电话", example = "0532-86893366")
    private String phone;

    @Schema(description = "传真")
    private String fax;

    @Schema(description = "通讯地址")
    private String address;

    @Schema(description = "邮政编码")
    private String zipcode;

    @Schema(description = "官网", example = "https://www.zentao.net")
    private String website;

    @Schema(description = "内网地址")
    private String backyard;

    @Schema(description = "是否允许匿名登录（yudao 侧无此机制，仅存放）", example = "0")
    private Integer guest;

    @Schema(description = "管理员账号逗号串（禅道判超管的依据）", example = ",admin,")
    private String admins;

}

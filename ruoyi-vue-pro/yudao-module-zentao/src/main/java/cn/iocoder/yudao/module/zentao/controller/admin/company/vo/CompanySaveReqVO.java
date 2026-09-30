package cn.iocoder.yudao.module.zentao.controller.admin.company.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 创建/修改公司。
 *
 * <p>字段与禅道 {@code module/company/config/form.php} 的 {@code form->edit} 一一对应：
 * {@code name/phone/fax/address/zipcode/website/backyard/guest} —— **没有 admins**，
 * 因为禅道的公司编辑表单里也没有它（admins 由安装程序与「设为管理员」维护）。
 */
@Schema(description = "管理后台 - 公司创建/修改 Request VO")
@Data
public class CompanySaveReqVO {

    @Schema(description = "编号（新建不传）", example = "2")
    private Long id;

    @Schema(description = "公司名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "甲方公司")
    @NotEmpty(message = "公司名称不能为空")
    private String name;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "传真")
    private String fax;

    @Schema(description = "通讯地址")
    private String address;

    @Schema(description = "邮政编码")
    private String zipcode;

    @Schema(description = "官网（只填 http:// 会被清空，禅道行为）")
    private String website;

    @Schema(description = "内网地址（只填 http:// 会被清空，禅道行为）")
    private String backyard;

    @Schema(description = "是否允许匿名登录：1 允许", example = "0")
    private Integer guest;

}

package cn.iocoder.yudao.module.zentao.controller.admin.company.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 外部公司下拉项。
 *
 * <p>字段名（{@code text/value/keys}）是禅道 {@code company::ajaxGetOutsideCompany()} 的原样输出，
 * 前端 zui 的 select 直接吃这三个键；这里保持一致，前端就不用做二次转换。
 */
@Schema(description = "管理后台 - 外部公司下拉项")
@Data
public class CompanyOptionVO {

    @Schema(description = "显示文本", example = "甲方公司")
    private String text;

    @Schema(description = "值（公司编号）", example = "2")
    private Long value;

    @Schema(description = "搜索用（禅道用名字当 keys）", example = "甲方公司")
    private String keys;

}

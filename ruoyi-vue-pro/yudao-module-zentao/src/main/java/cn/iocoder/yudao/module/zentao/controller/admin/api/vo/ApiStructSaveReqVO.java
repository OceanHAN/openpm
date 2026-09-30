package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 创建/修改数据结构。
 *
 * <h3>必填只有 {@code name}，虽然禅道 config 里写的是 {@code name,params}</h3>
 * {@code module/api/config.php:11} 写的是
 * {@code $config->api->struct->requiredFields = 'name,params'} —— 但 {@code zt_apistruct}
 * **根本没有 params 列**，模板 {@code ui/createstruct.html.php} 里也没有这个输入框。
 * 这是禅道的一处配置残留，所以本实现只把 {@code name} 当必填。
 *
 * <p>字段表就是 {@code form->createStruct}/{@code form->editStruct}：
 * {@code name / type / attribute / desc}。注意**没有 version** ——
 * 禅道编辑结构时无条件 {@code version = 旧版本 + 1}（{@code control.php:544}），
 * 不像接口那样先比较字段。
 */
@Schema(description = "管理后台 - 接口数据结构创建/修改 Request VO")
@Data
public class ApiStructSaveReqVO {

    @Schema(description = "结构编号（新建不传）", example = "92731")
    private Long id;

    @Schema(description = "所属接口库（新建必填）", example = "92751")
    private Long lib;

    @Schema(description = "结构名", requiredMode = Schema.RequiredMode.REQUIRED, example = "user")
    @NotEmpty(message = "结构名不能为空")
    private String name;

    @Schema(description = "结构类型：formData/json/array/object（缺省 formData）", example = "json")
    private String type;

    @Schema(description = "字段树 JSON。形状：[{field,paramsType,required,desc,structType,sub,key,children:[]}]")
    private String attribute;

    @Schema(description = "结构说明")
    private String desc;

}

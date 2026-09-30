package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 发布接口库。
 *
 * <p>字段表就是禅道 {@code form->createRelease}：{@code version}（必填）、{@code desc}
 * （{@code config/form.php:3-5}）。{@code lib} 由路径/参数给，
 * {@code addedBy}/{@code addedDate} 由服务端补。
 *
 * <p>{@code version} 是**字符串**（禅道 {@code zt_api_lib_release.version} 是 varchar），
 * 同一库内唯一 —— 禅道在 {@code control.php:441} 用
 * {@code getRelease($libID,'byVersion',$version)} 做冲突检查。
 */
@Schema(description = "管理后台 - 接口库发布 Request VO")
@Data
public class ApiReleaseSaveReqVO {

    @Schema(description = "所属接口库", requiredMode = Schema.RequiredMode.REQUIRED, example = "92751")
    private Long lib;

    @Schema(description = "版本号（字符串，同库内唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "v1.1")
    @NotEmpty(message = "版本号不能为空")
    private String version;

    @Schema(description = "版本说明", example = "补充了需求列表接口")
    private String desc;

}

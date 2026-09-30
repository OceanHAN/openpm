package cn.iocoder.yudao.module.zentao.controller.admin.score.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 计分请求。
 *
 * <p>对应禅道 {@code score::create($module, $method, $param, $account, $time)} ——
 * 它是被**其它模块**调的（任务完成、缺陷解决、执行关闭、登录……），这里开一个显式入口，
 * 既方便别的已迁模块调用，也方便联调与测试。
 */
@Schema(description = "管理后台 - 计分 Request VO")
@Data
public class ScoreCreateReqVO {

    @Schema(description = "模块", requiredMode = Schema.RequiredMode.REQUIRED, example = "task")
    @NotEmpty(message = "模块不能为空")
    private String module;

    @Schema(description = "动作", requiredMode = Schema.RequiredMode.REQUIRED, example = "finish")
    @NotEmpty(message = "动作不能为空")
    private String method;

    @Schema(description = "对象编号（部分规则需要，如任务完成/缺陷解决）", example = "1")
    private Long param;

    @Schema(description = "给谁计分（不传=当前登录账号）", example = "admin")
    private String account;

    @Schema(description = "计分时间（不传=现在；用于补录或测试时间窗）")
    private String time;

}

package cn.iocoder.yudao.module.zentao.controller.admin.score.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 积分规则 Response VO")
@Data
public class ScoreRuleRespVO {

    @Schema(description = "模块", example = "user")
    private String module;

    @Schema(description = "模块中文名", example = "用户")
    private String moduleName;

    @Schema(description = "动作", example = "login")
    private String method;

    @Schema(description = "动作中文名", example = "登录")
    private String methodName;

    @Schema(description = "最多计几次（0 = 不限）", example = "3")
    private String times;

    @Schema(description = "时间窗口小时数（0 = 不限；禅道配 24，实现上按当天数）", example = "24")
    private String hour;

    @Schema(description = "分值", example = "1")
    private Integer score;

    @Schema(description = "扩展说明（严重程度/优先级/密码强度等加成）")
    private String desc;

}

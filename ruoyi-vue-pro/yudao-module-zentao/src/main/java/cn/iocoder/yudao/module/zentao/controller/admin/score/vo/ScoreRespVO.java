package cn.iocoder.yudao.module.zentao.controller.admin.score.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 积分流水 Response VO")
@Data
public class ScoreRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "账号", example = "admin")
    private String account;

    @Schema(description = "模块", example = "task")
    private String module;

    @Schema(description = "模块中文名", example = "任务")
    private String moduleName;

    @Schema(description = "动作", example = "finish")
    private String method;

    @Schema(description = "动作中文名", example = "完成任务")
    private String methodName;

    @Schema(description = "描述", example = "完成任务ID:1")
    private String desc;

    @Schema(description = "计分前总分", example = "10")
    private Integer before;

    @Schema(description = "本次得分", example = "3")
    private Integer score;

    @Schema(description = "计分后总分", example = "13")
    private Integer after;

    @Schema(description = "计分时间")
    private LocalDateTime time;

}

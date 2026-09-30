package cn.iocoder.yudao.module.zentao.controller.admin.score.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 积分总览 Response VO")
@Data
public class ScoreTotalRespVO {

    @Schema(description = "账号", example = "admin")
    private String account;

    @Schema(description = "总积分（= SUM(zt_score.score)，禅道冗余在 zt_user.score）", example = "128")
    private Integer total;

    @Schema(description = "昨日新增积分", example = "5")
    private Integer yesterday;

    @Schema(description = "提示语（禅道 getNotice 的那句话，昨日为 0 时为空）", example = "昨天增加了积分：5，总积分：128")
    private String tip;

    @Schema(description = "流水条数", example = "42")
    private Integer count;

    @Schema(description = "积分功能开关（禅道 system.common.global.scoreStatus；本项目用配置项 zentao.score.enabled）", example = "true")
    private Boolean enabled;

}

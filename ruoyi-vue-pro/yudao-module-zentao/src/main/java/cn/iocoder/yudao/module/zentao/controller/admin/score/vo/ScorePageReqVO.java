package cn.iocoder.yudao.module.zentao.controller.admin.score.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 积分流水分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ScorePageReqVO extends PageParam {

    @Schema(description = "账号（不传=当前登录账号）", example = "admin")
    private String account;

    @Schema(description = "模块", example = "task")
    private String module;

    @Schema(description = "动作", example = "finish")
    private String method;

    @Schema(description = "计分时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] time;

}

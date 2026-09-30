package cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - Webhook 发送日志分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebhookLogPageReqVO extends PageParam {

    @Schema(description = "对象类型（默认 webhook）", example = "webhook")
    private String objectType;

    @Schema(description = "对象编号（webhook 编号）", example = "92200")
    private Long objectID;

    @Schema(description = "动作编号（zt_action.id）", example = "1")
    private Integer action;

    @Schema(description = "请求地址（模糊）")
    private String url;

    @Schema(description = "发送时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] date;

}

package cn.iocoder.yudao.module.zentao.controller.admin.entry.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 应用接入调用日志分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EntryLogPageReqVO extends PageParam {

    @Schema(description = "对象类型", example = "entry")
    private String objectType;

    @Schema(description = "对象编号（应用编号）", example = "1")
    private Long objectID;

    @Schema(description = "请求地址", example = "/api.php")
    private String url;

    @Schema(description = "请求时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] date;

}

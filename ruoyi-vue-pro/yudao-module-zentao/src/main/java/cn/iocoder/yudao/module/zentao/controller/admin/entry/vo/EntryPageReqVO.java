package cn.iocoder.yudao.module.zentao.controller.admin.entry.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 应用接入分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EntryPageReqVO extends PageParam {

    @Schema(description = "应用名称", example = "OA")
    private String name;

    @Schema(description = "应用代号", example = "oa")
    private String code;

    @Schema(description = "绑定账号", example = "admin")
    private String account;

    @Schema(description = "是否免密登录", example = "0")
    private Integer freePasswd;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createdDate;

}

package cn.iocoder.yudao.module.zentao.controller.admin.effort.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "管理后台 - 工时分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EffortPageReqVO extends PageParam {

    @Schema(description = "任务编号", example = "1")
    private Long taskId;

    @Schema(description = "账号", example = "admin")
    private String account;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "工作日期区间，[开始, 结束]")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate[] date;

}

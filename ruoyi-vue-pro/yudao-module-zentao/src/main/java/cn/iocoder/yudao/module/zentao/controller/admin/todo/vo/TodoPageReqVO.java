package cn.iocoder.yudao.module.zentao.controller.admin.todo.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "管理后台 - 待办分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TodoPageReqVO extends PageParam {

    @Schema(description = "归属账号", example = "admin")
    private String account;

    @Schema(description = "指派人", example = "admin")
    private String assignedTo;

    @Schema(description = "状态：wait/doing/done/closed", example = "wait")
    private String status;

    @Schema(description = "类型：custom/bug/task/story/testtask", example = "custom")
    private String type;

    @Schema(description = "优先级 1~4", example = "1")
    private Integer pri;

    @Schema(description = "是否私有：0/1", example = "0")
    private Integer privateFlag;

    @Schema(description = "名称，模糊匹配", example = "评审")
    private String name;

    @Schema(description = "日期区间，[开始, 结束]")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate[] date;

    // ==================== 「我的地盘」专用 ====================

    @Schema(description = "浏览范围：today/tomorrow/thisweek/before/future/all", example = "today")
    private String browseType;

    @Schema(description = "是否只看我指派给别人的", example = "false")
    private Boolean assignedToOther;

}

package cn.iocoder.yudao.module.zentao.controller.admin.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "管理后台 - 待办创建/修改 Request VO")
@Data
public class TodoSaveReqVO {

    @Schema(description = "待办编号，修改时必填", example = "97101")
    private Long id;

    @Schema(description = "归属账号，不填则取当前登录用户", example = "admin")
    private String account;

    @Schema(description = "指派人，不填则取归属账号", example = "admin")
    private String assignedTo;

    @Schema(description = "哪天做，不填默认今天", example = "2026-09-14")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @Schema(description = "开始时间 HHMM", example = "0900")
    private String begin;

    @Schema(description = "结束时间 HHMM", example = "1000")
    private String end;

    @Schema(description = "类型：custom/cycle/bug/task/story/testtask", example = "custom")
    private String type;

    @Schema(description = "关联对象编号（type 不是 custom/cycle 时必填）", example = "0")
    private Long objectID;

    @Schema(description = "优先级 1~4", example = "3")
    private Integer pri;

    @Schema(description = "待办名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "评审需求变更")
    @NotBlank(message = "待办名称不能为空")
    private String name;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "是否私有：1 只有自己能看到内容", example = "0")
    private Integer privateFlag;

    @Schema(description = "是否周期待办：0/1。本实现只存字段，不自动生成下一次", example = "0")
    private Integer cycle;

    @Schema(description = "周期配置（禅道原字段）", example = "")
    private String config;

}

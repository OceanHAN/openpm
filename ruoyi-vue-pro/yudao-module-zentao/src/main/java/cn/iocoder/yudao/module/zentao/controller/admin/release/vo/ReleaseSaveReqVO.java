package cn.iocoder.yudao.module.zentao.controller.admin.release.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 发布创建/修改 Request VO")
@Data
public class ReleaseSaveReqVO {

    @Schema(description = "发布编号，新建时为空", example = "1")
    private Long id;

    @Schema(description = "所属产品", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属产品不能为空")
    private Long product;

    @Schema(description = "分支/平台。多分支产品必填，可多选", example = "[0]")
    private List<Long> branches;

    @Schema(description = "发布版本号。禅道里**全局唯一**", requiredMode = Schema.RequiredMode.REQUIRED, example = "V1.0")
    @NotBlank(message = "发布版本号不能为空")
    @Size(max = 255, message = "发布版本号长度不能超过 255 个字符")
    private String name;

    @Schema(description = "包含的构建。选了之后会把构建里的需求/Bug 同步进来", example = "[1,2]")
    private List<Long> builds;

    @Schema(description = "涉及的项目编号列表（可手工指定；不填时从构建推导）", example = "[1]")
    private List<Long> projects;

    @Schema(description = "是否从构建同步需求与 Bug。默认 true", example = "true")
    private Boolean syncFromBuilds;

    @Schema(description = "是否里程碑", example = "false")
    private Boolean marker;

    @Schema(description = "计划发布日期。状态为「已发布」时可以不填", example = "2026-02-28")
    private LocalDate date;

    @Schema(description = "实际发布日期。状态为「已发布」时必填", example = "2026-03-01 10:00:00")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    // 必须显式指定反序列化器：yudao 全局给 LocalDateTime 注册的是「按 Long 时间戳解析」的实现，
    // 而它**不认 @JsonFormat**（只有序列化器认）。不写这一行的话，
    // 传 "2099-01-01 10:00:00" 这种字符串会被 getValueAsLong() 静默解析成 1970-01-01，
    // 于是「发布日期不能晚于今天」的校验永远不会触发。
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime releasedDate;

    @Schema(description = "状态：wait/normal/fail/terminate", example = "wait")
    private String status;

    @Schema(description = "本次完成的需求编号（不选构建时手工指定）", example = "[1,2]")
    private List<Long> stories;

    @Schema(description = "本次解决的 Bug 编号", example = "[1]")
    private List<Long> bugs;

    @Schema(description = "遗留的 Bug 编号", example = "[]")
    private List<Long> leftBugs;

    @Schema(description = "包含的子发布编号", example = "[]")
    private List<Long> releases;

    @Schema(description = "描述")
    private String desc;

}

package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 测试单状态流转 Request VO")
@Data
public class TestTaskStatusReqVO {

    @Schema(description = "关闭时必填：实际完成时间", example = "2026-03-10 18:00:00")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    // ★ 必须显式指定反序列化器，理由与 ReleaseSaveReqVO 完全一样（README 第 21 条坑）：
    //   yudao 全局给 LocalDateTime 注册的是「按 Long 时间戳解析」的实现，它不认 @JsonFormat，
    //   于是 "2099-01-01 10:00:00" 会被静默解析成 1970-01-01 ——
    //   「完成时间不能晚于明天」永远不触发，反而触发「不能早于计划开始日期」。
    //   同样的坑在每个「时间字符串进、业务规则判断」的字段上都会重现一次。
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime realFinishedDate;

    @Schema(description = "关闭时的测试总结", example = "全部通过")
    private String report;

    @Schema(description = "备注", example = "依赖环境未就绪")
    private String comment;

}

package cn.iocoder.yudao.module.zentao.controller.admin.holiday.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 节假日 Response VO")
@Data
public class HolidayRespVO {

    private Long id;
    private String name;
    private String type;
    private String desc;
    private String year;
    private LocalDate begin;
    private LocalDate end;

}

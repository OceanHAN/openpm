package cn.iocoder.yudao.module.zentao.controller.admin.entry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 应用接入调用日志 Response VO")
@Data
public class EntryLogRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "对象类型", example = "entry")
    private String objectType;

    @Schema(description = "对象编号", example = "1")
    private Long objectID;

    @Schema(description = "请求时间")
    private LocalDateTime date;

    @Schema(description = "请求地址", example = "/index.php?m=user&f=apilogin")
    private String url;

    @Schema(description = "校验结果")
    private String result;

}

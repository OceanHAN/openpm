package cn.iocoder.yudao.module.zentao.controller.admin.entry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 应用接入签名 Response VO")
@Data
public class EntrySignRespVO {

    @Schema(description = "应用代号", example = "oa")
    private String code;

    @Schema(description = "时间戳（原样回显，可省略）", example = "1700000000")
    private String time;

    @Schema(description = "签名", example = "d41d8cd98f00b204e9800998ecf8427e")
    private String token;

    @Schema(description = "签名方式：time = md5(code+key+time)，query = md5(md5(query)+key)", example = "time")
    private String mode;

}

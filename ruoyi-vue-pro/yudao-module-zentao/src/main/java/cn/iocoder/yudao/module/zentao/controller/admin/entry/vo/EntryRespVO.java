package cn.iocoder.yudao.module.zentao.controller.admin.entry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 应用接入 Response VO")
@Data
public class EntryRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "应用名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "OA")
    private String name;

    @Schema(description = "绑定账号", example = "admin")
    private String account;

    @Schema(description = "应用代号", requiredMode = Schema.RequiredMode.REQUIRED, example = "oa")
    private String code;

    @Schema(description = "密钥", example = "c0f0…32位")
    private String key;

    @Schema(description = "是否免密登录：1 开启", example = "0")
    private Integer freePasswd;

    @Schema(description = "允许的来源 IP", example = "*")
    private String ip;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "最近一次调用的请求时间戳（防重放用）", example = "1700000000")
    private Integer calledTime;

    @Schema(description = "创建人账号", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "最后编辑人账号", example = "admin")
    private String editedBy;

    @Schema(description = "最后编辑时间")
    private LocalDateTime editedDate;

}

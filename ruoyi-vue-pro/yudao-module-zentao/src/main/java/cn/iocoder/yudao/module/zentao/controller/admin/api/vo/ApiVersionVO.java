package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本链上的一格（接口版本 / 结构版本共用）。
 *
 * <p>禅道的版本回溯是「拿 snap 里的 version 回查 spec 表」，
 * 所以这一格只需要「第几版、谁写的、什么时候」，内容由详情接口按 version 返回。
 */
@Schema(description = "管理后台 - 版本链条目")
@Data
public class ApiVersionVO {

    @Schema(description = "版本号", example = "2")
    private Integer version;

    @Schema(description = "该版本的写者", example = "admin")
    private String addedBy;

    @Schema(description = "该版本的写入时间", example = "2026-04-10 14:30:00")
    private LocalDateTime addedDate;

    @Schema(description = "是不是当前版本", example = "true")
    private Boolean current;

}

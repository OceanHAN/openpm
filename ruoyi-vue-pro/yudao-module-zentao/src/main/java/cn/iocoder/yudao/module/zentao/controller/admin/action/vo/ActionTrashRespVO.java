package cn.iocoder.yudao.module.zentao.controller.admin.action.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 回收站记录 Response VO")
@Data
public class ActionTrashRespVO {

    @Schema(description = "删除动作的日志编号（还原/隐藏都用它）", example = "1024")
    private Long actionId;

    @Schema(description = "对象类型", example = "story")
    private String objectType;

    @Schema(description = "对象类型名", example = "需求")
    private String objectTypeName;

    @Schema(description = "对象编号", example = "1024")
    private Long objectID;

    @Schema(description = "对象名称（去对应的业务表里查出来的）", example = "支持需求批量导入")
    private String objectName;

    @Schema(description = "删除人", example = "admin")
    private String deletedBy;

    @Schema(description = "删除时间")
    private LocalDateTime deletedDate;

    @Schema(description = "删除时填的备注")
    private String comment;

    @Schema(description = "能不能还原：对象已不存在 / 类型不在白名单里都不能", example = "true")
    private Boolean canUndelete;

    @Schema(description = "不能还原的原因", example = "对象类型「effort」不支持回收站还原")
    private String reason;

}

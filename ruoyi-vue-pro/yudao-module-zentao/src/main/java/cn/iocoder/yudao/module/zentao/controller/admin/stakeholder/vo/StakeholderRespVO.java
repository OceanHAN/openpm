package cn.iocoder.yudao.module.zentao.controller.admin.stakeholder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 干系人 Response VO")
@Data
public class StakeholderRespVO {

    @Schema(description = "干系人编号", example = "99101")
    private Long id;

    @Schema(description = "对象类型", example = "project")
    private String objectType;

    @Schema(description = "对象编号", example = "1")
    private Long objectID;

    @Schema(description = "账号", example = "admin")
    private String user;

    @Schema(description = "姓名", example = "管理员")
    private String realname;

    @Schema(description = "类型：inside / outside", example = "inside")
    private String type;

    @Schema(description = "类型名称", example = "内部")
    private String typeName;

    @Schema(description = "是否关键干系人", example = "1")
    private Integer key;

    @Schema(description = "来源", example = "team")
    private String from;

    @Schema(description = "来源名称", example = "团队成员")
    private String fromName;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

}

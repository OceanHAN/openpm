package cn.iocoder.yudao.module.zentao.controller.admin.team.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 团队成员 Request VO")
@Data
public class TeamMemberSaveReqVO {

    @Schema(description = "成员记录编号（修改时必填）", example = "98101")
    private Long id;

    @Schema(description = "所属对象编号（项目或执行）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属对象编号不能为空")
    private Long root;

    @Schema(description = "类型：project/execution", requiredMode = Schema.RequiredMode.REQUIRED, example = "project")
    @NotBlank(message = "团队成员类型不能为空")
    private String type;

    @Schema(description = "成员账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    @NotBlank(message = "成员账号不能为空")
    private String account;

    @Schema(description = "角色", example = "研发")
    private String role;

    @Schema(description = "岗位", example = "")
    private String position;

    @Schema(description = "是否受限访问：yes/no", example = "no")
    private String limited;

    @Schema(description = "加入日期，不填默认今天", example = "2026-03-01")
    private LocalDate join;

    @Schema(description = "可用天数", example = "20")
    private Integer days;

    @Schema(description = "每天投入小时数（默认 7.0）", example = "7")
    private BigDecimal hours;

}

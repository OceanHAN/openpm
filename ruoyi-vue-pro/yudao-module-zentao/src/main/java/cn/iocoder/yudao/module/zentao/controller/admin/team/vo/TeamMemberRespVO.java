package cn.iocoder.yudao.module.zentao.controller.admin.team.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 团队成员 Response VO")
@Data
public class TeamMemberRespVO {

    @Schema(description = "成员记录编号", example = "98101")
    private Long id;

    @Schema(description = "所属对象编号", example = "1")
    private Long root;

    @Schema(description = "类型：project/execution", example = "project")
    private String type;

    @Schema(description = "账号", example = "admin")
    private String account;

    @Schema(description = "姓名", example = "管理员")
    private String realname;

    @Schema(description = "角色", example = "项目经理")
    private String role;

    @Schema(description = "岗位")
    private String position;

    @Schema(description = "是否受限访问：yes/no", example = "no")
    private String limited;

    @Schema(description = "加入日期", example = "2026-01-01")
    private LocalDate join;

    @Schema(description = "可用天数", example = "20")
    private Integer days;

    @Schema(description = "每天投入小时数", example = "7")
    private BigDecimal hours;

    @Schema(description = "可用工时 = 天数 × 每天小时数", example = "140")
    private BigDecimal totalHours;

    @Schema(description = "排序", example = "0")
    private Integer order;

}

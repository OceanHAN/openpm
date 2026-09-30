package cn.iocoder.yudao.module.zentao.controller.admin.my.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 「我的团队」一行 = 我在某个项目/执行里的成员关系（zt_team 的一行）。
 *
 * <p>字段类型刻意与 {@code TeamDO} 保持一致（days 是 Integer、limited 是 yes/no 字符串）：
 * 类型不一致时 BeanUtils 会静默跳过这个字段，页面上就显示空白，很难查。
 */
@Schema(description = "管理后台 - 我的团队（我参与的项目/执行） Response VO")
@Data
public class MyTeamRespVO {

    @Schema(description = "团队成员记录编号", example = "98101")
    private Long id;

    @Schema(description = "对象编号（项目或执行）", example = "1")
    private Long root;

    @Schema(description = "对象类型：project 项目 / execution 执行", example = "project")
    private String type;

    @Schema(description = "对象名称（由 root 回查 zt_project 补上）", example = "禅道迁移一期")
    private String rootName;

    @Schema(description = "对象状态（项目/执行的状态）", example = "doing")
    private String rootStatus;

    @Schema(description = "账号", example = "admin")
    private String account;

    @Schema(description = "姓名", example = "管理员")
    private String realname;

    @Schema(description = "我在这个团队里的角色", example = "研发")
    private String role;

    @Schema(description = "岗位")
    private String position;

    @Schema(description = "加入日期")
    private LocalDate join;

    @Schema(description = "可用天数", example = "5")
    private Integer days;

    @Schema(description = "每天小时数", example = "7.00")
    private BigDecimal hours;

    @Schema(description = "可用工时 = 天数 × 每天小时数", example = "35.00")
    private BigDecimal totalHours;

    @Schema(description = "受限访问：yes/no（受限用户只能看到自己的任务）", example = "no")
    private String limited;

}

package cn.iocoder.yudao.module.zentao.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 报表页面的「可选年份 / 部门 / 人员」。
 *
 * <p>禅道 {@code reportZen::assignAnnualBaseData} 会把这三样一起查出来：
 * 年份从「最早一条动作」算到今年、部门来自部门树、人员来自部门下的用户。
 */
@Schema(description = "管理后台 - 报表筛选项 Response VO")
@Data
public class ReportOptionRespVO {

    @Schema(description = "可选年份（升序），最早一年来自 zt_action 的第一条动作")
    private List<String> years = new ArrayList<>();

    @Schema(description = "默认选中的年份", example = "2026")
    private String current;

    @Schema(description = "部门列表（可选）")
    private List<DeptOption> depts = new ArrayList<>();

    @Schema(description = "人员列表（可选）")
    private List<UserOption> users = new ArrayList<>();

    @Schema(description = "部门")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeptOption {
        private Long id;
        private String name;
    }

    @Schema(description = "人员")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserOption {
        private String account;
        private String name;
        private Long deptId;
    }

}

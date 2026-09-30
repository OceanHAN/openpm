package cn.iocoder.yudao.module.zentao.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 产出统计一项（禅道 {@code report::getOutput4API}）。
 *
 * <p>回答的是「这一年里，各条产线各产出了多少东西」：
 * 产品/计划/发布/执行/需求/任务/缺陷/用例，每类下面再按动作（创建/编辑/关闭…）拆开。
 */
@Schema(description = "管理后台 - 产出统计 Response VO")
@Data
public class ReportOutputRespVO {

    @Schema(description = "对象类型", example = "task")
    private String objectType;

    @Schema(description = "对象类型中文名", example = "任务")
    private String objectTypeName;

    @Schema(description = "该类下的动作合计", example = "128")
    private Integer total = 0;

    @Schema(description = "动作明细")
    private List<ActionItem> actions = new ArrayList<>();

    @Schema(description = "动作明细")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionItem {
        @Schema(description = "动作代码：create/edit/close/finish/...", example = "create")
        private String code;

        @Schema(description = "动作中文名", example = "创建")
        private String name;

        @Schema(description = "条数", example = "42")
        private Integer total;
    }

}

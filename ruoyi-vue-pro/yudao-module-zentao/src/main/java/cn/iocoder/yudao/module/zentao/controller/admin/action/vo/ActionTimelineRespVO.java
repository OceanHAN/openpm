package cn.iocoder.yudao.module.zentao.controller.admin.action.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Schema(description = "管理后台 - 操作日志时间线 Response VO")
@Data
public class ActionTimelineRespVO {

    @Schema(description = "日志编号", example = "1")
    private Long id;

    @Schema(description = "对象类型", example = "story")
    private String objectType;

    @Schema(description = "对象编号", example = "1024")
    private Long objectID;

    @Schema(description = "操作人账号", example = "admin")
    private String actor;

    @Schema(description = "动作", example = "changed")
    private String action;

    @Schema(description = "动作名称", example = "变更")
    private String actionName;

    @Schema(description = "操作时间")
    private LocalDateTime date;

    @Schema(description = "备注")
    private String comment;

    /**
     * 动作渲染文本（谁 + 动作 + 字段变化），如「admin 编辑：状态 激活 → 已关闭」。
     * 对应禅道 {@code action/model.php#renderAction} + {@code renderChanges}：
     * 前端可以直接展示这一行，也可以用下面的 histories 自己渲染。
     */
    @Schema(description = "动作渲染文本", example = "admin 编辑：状态 激活 → 已关闭")
    private String renderedDesc;

    @Schema(description = "字段级变更明细")
    private List<HistoryItem> histories;

    @Schema(description = "字段变更明细")
    @Data
    public static class HistoryItem {

        @Schema(description = "字段名", example = "spec")
        private String field;

        @Schema(description = "旧值")
        private String oldValue;

        @Schema(description = "新值")
        private String newValue;

        @Schema(description = "长文本差异（统一 diff 风格纯文本）")
        private String diff;

    }

}

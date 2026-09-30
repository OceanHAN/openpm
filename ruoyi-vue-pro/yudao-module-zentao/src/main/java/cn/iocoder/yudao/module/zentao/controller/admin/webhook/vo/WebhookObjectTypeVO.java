package cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 禅道 {@code config/webhook.php} 的 {@code objectTypes} 白名单项。
 *
 * <p>前端「新建/编辑 webhook」的对象类型与动作下拉直接吃这个接口 ——
 * 白名单只有一份实现（在 {@code WebhookTypeConfig}），前端不再抄一遍，避免两边跑偏。
 */
@Schema(description = "管理后台 - Webhook 对象类型白名单 Response VO")
@Data
public class WebhookObjectTypeVO {

    @Schema(description = "对象类型", example = "story")
    private String type;

    @Schema(description = "中文名（禅道 $lang->action->objectTypes）", example = "需求")
    private String name;

    @Schema(description = "该对象类型**有实际发送语义**的动作（禅道白名单里的完整列表）",
            example = "[\"opened\",\"edited\",\"closed\"]")
    private List<String> actionTypes;

    @Schema(description = "本系统 zt_action 里真实出现过的动作（用来告诉使用者哪些动作有数据）",
            example = "[\"opened\",\"edited\"]")
    private List<String> observedActionTypes;

    @Schema(description = "是否在 needAssignTypes 里（决定是否 @ 指派人）", example = "true")
    private Boolean needAssign;

}

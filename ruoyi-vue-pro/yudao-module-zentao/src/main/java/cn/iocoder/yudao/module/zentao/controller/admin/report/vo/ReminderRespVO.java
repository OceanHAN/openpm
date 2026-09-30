package cn.iocoder.yudao.module.zentao.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 每日提醒（禅道 {@code reportZen::getReminder} + {@code view/dailyreminder.html.php}）。
 *
 * <p>一个人一条：把他「快到期的、没做完的」东西凑在一起。禅道在这里直接发邮件；
 * 本实现只产出数据（发信交给 yudao 的通知/站内信，见 README 3.38 的说明）。
 */
@Schema(description = "管理后台 - 每日提醒 Response VO")
@Data
public class ReminderRespVO {

    @Schema(description = "账号", example = "admin")
    private String account;

    @Schema(description = "姓名", example = "管理员")
    private String realname;

    @Schema(description = "需要提醒的条数合计", example = "7")
    private Integer total;

    @Schema(description = "快到期的缺陷")
    private List<Map<String, Object>> bugs = new ArrayList<>();

    @Schema(description = "快到期的任务")
    private List<Map<String, Object>> tasks = new ArrayList<>();

    @Schema(description = "未完成的待办")
    private List<Map<String, Object>> todos = new ArrayList<>();

    @Schema(description = "未完成的测试单")
    private List<Map<String, Object>> testTasks = new ArrayList<>();

    @Schema(description = "快到期的看板卡片")
    private List<Map<String, Object>> cards = new ArrayList<>();

}

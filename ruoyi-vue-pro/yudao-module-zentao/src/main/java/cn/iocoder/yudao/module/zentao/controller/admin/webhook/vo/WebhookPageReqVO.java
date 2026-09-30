package cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - Webhook 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebhookPageReqVO extends PageParam {

    @Schema(description = "名称（模糊）", example = "发布")
    private String name;

    @Schema(description = "类型", example = "default")
    private String type;

    @Schema(description = "请求地址（精确）", example = "http://127.0.0.1:48080/admin-api/zentao/webhook/mock-receive")
    private String url;

}

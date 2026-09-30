package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 创建/修改接口。
 *
 * <h3>字段就是禅道 {@code config/form.php} 的 {@code form->create} + {@code form->edit}</h3>
 * <pre>
 *   create 必填：title, path       （config.php:14）
 *   edit   必填：lib, title, path  （config.php:18）
 *   可写字段：module/title/protocol/method/path/requestType/status/owner/
 *            params/paramsExample/response/responseExample/desc（+ edit 的 editedDate）
 * </pre>
 * <b>两个字段刻意不在里面</b>：
 * <ul>
 *   <li>{@code responseType}：禅道 create/edit 表单里都没有它，所以更新接口时它**不会被改写**；
 *       本实现同样不接收它（列照存，见 {@link ApiRespVO#getResponseType()}）。</li>
 *   <li>{@code commonParams}：同上，禅道表单里没有。</li>
 * </ul>
 * 也就是说 {@code update} 只写 form->edit 声明的字段 —— 传别的字段进来不会生效。
 *
 * <p>{@code title} 的空值校验刻意**不放 @NotEmpty**，而是交给 Service 抛
 * {@code API_TITLE_REQUIRED}（预置业务错误码），这样「接口名称为空」既能被前端表单拦住，
 * 也能被接口调用方拿到明确的业务码。
 */
@Schema(description = "管理后台 - 接口创建/修改 Request VO")
@Data
public class ApiSaveReqVO {

    @Schema(description = "接口编号（新建不传）", example = "92711")
    private Long id;

    @Schema(description = "所属接口库（新建必填）", example = "92751")
    private Long lib;

    @Schema(description = "所属目录（zt_module type=api）", example = "92703")
    private Long module;

    @Schema(description = "接口名称，(lib,module) 内唯一", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "获取当前登录用户")
    private String title;

    @Schema(description = "请求路径，(lib,module,method) 内唯一", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "/api.php/v1/user")
    @NotEmpty(message = "请求路径不能为空")
    private String path;

    @Schema(description = "协议：HTTP/HTTPS/WS/WSS", example = "HTTP")
    private String protocol;

    @Schema(description = "请求方式：GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD", example = "GET")
    private String method;

    @Schema(description = "请求格式：application/json 等", example = "application/json")
    private String requestType;

    @Schema(description = "开发状态：done/doing/hidden", example = "done")
    private String status;

    @Schema(description = "负责人账号", example = "admin")
    private String owner;

    @Schema(description = "接口说明")
    private String desc;

    @Schema(description = "请求参数树 JSON：{header:[],params:[],paramsType,query:[]}")
    private String params;

    @Schema(description = "请求示例")
    private String paramsExample;

    @Schema(description = "响应示例")
    private String responseExample;

    @Schema(description = "响应字段树 JSON（数组）")
    private String response;

    @Schema(description = "提交前读到的最新修改时间。**同时是乐观锁**：库里已被别人改过时拒绝本次修改",
            example = "2026-04-10 14:30:00")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    // yudao 全局给 LocalDateTime 注册的是「按 Long 时间戳解析」的反序列化器，它不认 @JsonFormat，
    // 所以字符串会被静默读成 1970 —— 那样乐观锁永远比不相等，每次都误报「已被别人修改」（README 坑位 #21/#30）
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime editedDate;

}

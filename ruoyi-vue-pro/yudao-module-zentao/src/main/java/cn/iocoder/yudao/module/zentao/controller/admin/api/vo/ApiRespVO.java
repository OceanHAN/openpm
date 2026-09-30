package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口（当前值 + 展示用的关联名称）。
 *
 * <p>{@code params}/{@code response}/{@code paramsExample}/{@code responseExample}
 * 一律按**原始字符串**返回（就是禅道库里存的样子）：
 * 它们的形状是接口文档自己的 JSON，前端用安全解析渲染成字段树即可。
 * 服务端不做反序列化，避免「一条脏 JSON 让整个分页 500」（README 第 34 条坑的思路）。
 */
@Schema(description = "管理后台 - 接口 Response VO")
@Data
public class ApiRespVO {

    @Schema(description = "接口编号", example = "92711")
    private Long id;

    @Schema(description = "所属产品（禅道原列是 varchar，实际恒为 0）", example = "0")
    private Long product;

    @Schema(description = "所属接口库", example = "92751")
    private Long lib;

    @Schema(description = "所属接口库名称（从 zt_doclib 补）", example = "禅道接口库")
    private String libName;

    @Schema(description = "所属目录", example = "92703")
    private Long module;

    @Schema(description = "目录名称（从 zt_module 补）", example = "用户登录")
    private String moduleName;

    @Schema(description = "接口名称", example = "获取当前登录用户")
    private String title;

    @Schema(description = "请求路径", example = "/api.php/v1/user")
    private String path;

    @Schema(description = "协议", example = "HTTP")
    private String protocol;

    @Schema(description = "请求方式", example = "GET")
    private String method;

    @Schema(description = "请求格式", example = "application/json")
    private String requestType;

    @Schema(description = "响应格式（禅道 create/edit 表单里没有这一项，属于只读列）")
    private String responseType;

    @Schema(description = "开发状态", example = "done")
    private String status;

    @Schema(description = "开发状态文案：done=开发完成 / doing=开发中 / 其它原样", example = "开发完成")
    private String statusName;

    @Schema(description = "负责人账号", example = "admin")
    private String owner;

    @Schema(description = "接口说明")
    private String desc;

    @Schema(description = "当前版本号", example = "2")
    private Integer version;

    @Schema(description = "请求参数树 JSON（原样字符串）")
    private String params;

    @Schema(description = "请求示例")
    private String paramsExample;

    @Schema(description = "响应示例")
    private String responseExample;

    @Schema(description = "响应字段树 JSON（原样字符串）")
    private String response;

    @Schema(description = "公共参数（禅道表单里没有它，恒为空）")
    private String commonParams;

    @Schema(description = "创建人", example = "admin")
    private String addedBy;

    @Schema(description = "创建时间", example = "2026-04-01 09:00:00")
    private LocalDateTime addedDate;

    @Schema(description = "最后修改人", example = "admin")
    private String editedBy;

    @Schema(description = "最后修改时间（同时是乐观锁的值）", example = "2026-04-10 14:30:00")
    private LocalDateTime editedDate;

    @Schema(description = "共有几个版本（zt_apispec 的行数）", example = "2")
    private Integer versionCount;

}

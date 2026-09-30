package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据结构（可复用结构）。
 *
 * <p>{@code attribute} 是**原始 JSON 字符串**（字段树），由前端安全解析渲染。
 * {@code versionList} 只在详情里填 —— 禅道的结构版本表只按 {@code name} 关联，
 * 所以版本链是「这个名字的所有版本」。
 */
@Schema(description = "管理后台 - 接口数据结构 Response VO")
@Data
public class ApiStructRespVO {

    @Schema(description = "结构编号", example = "92731")
    private Long id;

    @Schema(description = "所属接口库", example = "92751")
    private Long lib;

    @Schema(description = "所属接口库名称", example = "禅道接口库")
    private String libName;

    @Schema(description = "结构名", example = "user")
    private String name;

    @Schema(description = "结构类型：formData/json/array/object", example = "json")
    private String type;

    @Schema(description = "结构说明", example = "禅道用户对象")
    private String desc;

    @Schema(description = "当前版本号", example = "2")
    private Integer version;

    @Schema(description = "字段树 JSON（原样字符串）")
    private String attribute;

    @Schema(description = "创建人", example = "admin")
    private String addedBy;

    @Schema(description = "创建人姓名（禅道 struct 列表 join zt_user 取 realname）", example = "管理员")
    private String addedName;

    @Schema(description = "创建时间", example = "2026-04-01 09:05:00")
    private LocalDateTime addedDate;

    @Schema(description = "最后修改人", example = "admin")
    private String editedBy;

    @Schema(description = "最后修改时间", example = "2026-04-10 14:35:00")
    private LocalDateTime editedDate;

    @Schema(description = "共有几个版本（zt_apistruct_spec 里同名的行数）", example = "2")
    private Integer versionCount;

    @Schema(description = "版本链（新版本在前），仅详情返回")
    private List<ApiVersionVO> versionList;

}

package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 接口分页查询。
 *
 * <p>字段对应禅道 {@code $config->api->search}（{@code module/api/config.php:44}）：
 * title / id / lib / path / method / status / version / addedBy / editedBy / addedDate / editedDate。
 * 本实现取其中真正在用的几个，外加两个禅道链路上的：
 * <ul>
 *   <li>{@code module}：目录过滤。禅道 {@code getListByModuleID} 会把目录展开成
 *       「自己 + 全部子孙」（{@code FIND_IN_SET(id, path)}），本实现复用 module 模块的
 *       {@code getSelfAndDescendantIds}</li>
 *   <li>{@code releaseID}：按发布版本浏览（禅道 {@code getListByModuleID($libID,$moduleID,$releaseID)}），
 *       走 snap 里的 (id, version) 回查 spec</li>
 * </ul>
 */
@Schema(description = "管理后台 - 接口分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiPageReqVO extends PageParam {

    @Schema(description = "所属接口库（zt_doclib 里 type=api 的记录）", example = "92751")
    private Long lib;

    @Schema(description = "目录编号（会连带查出子目录下的接口）", example = "92701")
    private Long module;

    @Schema(description = "接口名称（模糊）", example = "用户")
    private String title;

    @Schema(description = "请求路径（模糊）", example = "/api.php/v1")
    private String path;

    @Schema(description = "请求方式：GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD", example = "GET")
    private String method;

    @Schema(description = "开发状态：done/doing/hidden", example = "done")
    private String status;

    @Schema(description = "负责人账号", example = "admin")
    private String owner;

    @Schema(description = "发布版本编号（按冻结版本浏览时传）", example = "92761")
    private Long releaseID;

}

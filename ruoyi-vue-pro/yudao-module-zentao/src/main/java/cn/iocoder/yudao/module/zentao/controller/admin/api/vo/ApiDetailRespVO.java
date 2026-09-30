package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 接口详情（当前值 + 版本链 + 正在看的版本）。
 *
 * <p>对应禅道 {@code api::getByID($id, $version, $releaseID)}（{@code model.php:312}）：
 * <ul>
 *   <li>不传 version/release → 返回**当前值**（读 zt_api 主表）</li>
 *   <li>传 version → 内容来自 zt_apispec 的那一版（历史回溯）</li>
 *   <li>传 releaseID → 先从发布快照 snap 里查出该接口被冻结的 version，再走上面那条</li>
 * </ul>
 * {@code viewingVersion} 就是最终生效的版本号（0 表示读的是主表当前值）；
 * {@code versionList} 是完整的版本链，供前端直接列出并点击回看。
 */
@Schema(description = "管理后台 - 接口详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiDetailRespVO extends ApiRespVO {

    @Schema(description = "本次返回内容是第几版；0 表示主表当前值", example = "2")
    private Integer viewingVersion;

    @Schema(description = "按发布版本浏览时：发布版本编号", example = "92761")
    private Long releaseID;

    @Schema(description = "按发布版本浏览时：该发布的版本号字符串", example = "v1.0")
    private String releaseVersion;

    @Schema(description = "版本链（新版本在前）")
    private List<ApiVersionVO> versionList;

}

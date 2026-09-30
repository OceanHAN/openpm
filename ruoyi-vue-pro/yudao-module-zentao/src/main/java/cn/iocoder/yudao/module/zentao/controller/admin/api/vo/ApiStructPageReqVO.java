package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据结构分页。
 *
 * <p>禅道 {@code api::struct($libID, $releaseID)} 只按库过滤（{@code model.php:475 getStructByQuery}），
 * 传了 releaseID 时才改用发布快照里的 {@code (name, version)} 组合（{@code getStructListByRelease}）。
 * 本实现保留 {@code name} 模糊查询作为补充。
 */
@Schema(description = "管理后台 - 接口数据结构分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiStructPageReqVO extends PageParam {

    @Schema(description = "所属接口库", example = "92751")
    private Long lib;

    @Schema(description = "结构名（模糊）", example = "user")
    private String name;

    @Schema(description = "发布版本编号（按冻结版本浏览结构时传）", example = "92761")
    private Long releaseID;

}

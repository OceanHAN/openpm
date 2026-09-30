package cn.iocoder.yudao.module.zentao.controller.admin.build.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 构建关联需求/Bug Request VO
 *
 * <p>关联 Bug 时还可以同时指定「解决者」（禅道 {@code linkBug($buildID, $bugIdList, $resolvedList)}）：
 * 构建会把还没解决的 Bug 直接置为已解决，解决人取自这里。
 */
@Schema(description = "管理后台 - 构建关联需求/Bug Request VO")
@Data
public class BuildLinkReqVO {

    @Schema(description = "构建编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long build;

    @Schema(description = "要关联的需求/Bug 编号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2]")
    @NotEmpty(message = "请至少选择一条数据")
    private List<Long> ids;

    @Schema(description = "Bug 编号 → 解决者账号。关联 Bug 时会用它写入 resolvedBy，不传则为空", example = "{\"1\":\"admin\"}")
    private Map<Long, String> resolvedBy;

}

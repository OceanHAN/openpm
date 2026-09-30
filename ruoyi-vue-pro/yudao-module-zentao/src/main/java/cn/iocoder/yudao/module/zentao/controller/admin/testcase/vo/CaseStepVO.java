package cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 用例步骤")
@Data
public class CaseStepVO {

    @Schema(description = "步骤编号（新增时为空）", example = "93151")
    private Long id;

    @Schema(description = "所属步骤组编号（新增时用**本次提交的数组下标**指向同批里的组；不传或 -1 表示顶层）",
            example = "0")
    private Long parent;

    @Schema(description = "类型：step 步骤 / group 步骤组", example = "step")
    private String type;

    @Schema(description = "步骤描述", example = "打开登录页面")
    private String desc;

    @Schema(description = "预期结果（步骤组不需要）", example = "页面正常加载")
    private String expect;

    @Schema(description = "层级编号，如 1.2.1（由后端算出，不落库）", example = "1.1")
    private String name;

    @Schema(description = "层级 1-3（由后端算出）", example = "2")
    private Integer grade;

}

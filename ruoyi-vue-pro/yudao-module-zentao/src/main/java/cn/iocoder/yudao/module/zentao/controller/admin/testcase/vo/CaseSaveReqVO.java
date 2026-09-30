package cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 测试用例新增/修改 Request VO")
@Data
public class CaseSaveReqVO {

    @Schema(description = "用例编号（修改时必填）", example = "93101")
    private Long id;

    @Schema(description = "所属产品。与 lib 二选一：产品用例传 product，用例库用例传 lib（product 置 0）",
            example = "1")
    private Long product;

    @Schema(description = "所属用例库。与 product 二选一（用例库用例：lib>0、product=0）", example = "95301")
    private Long lib;

    @Schema(description = "所属分支/平台（0=主干）", example = "0")
    private Long branch;

    @Schema(description = "所属模块（通用树 zt_module，type=case）", example = "93301")
    private Long module;

    @Schema(description = "关联需求", example = "1")
    private Long story;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "正常登录-用户名密码正确")
    @NotBlank(message = "用例标题不能为空")
    @Size(max = 255, message = "标题长度不能超过 255 个字符")
    private String title;

    @Schema(description = "前置条件", example = "已注册用户 admin/admin123")
    private String precondition;

    @Schema(description = "关键词", example = "登录,冒烟")
    private String keywords;

    @Schema(description = "优先级 1-4", example = "1")
    private Integer pri;

    @Schema(description = "类型：unit/interface/feature/install/config/performance/security/other",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "feature")
    @NotBlank(message = "用例类型不能为空")
    private String type;

    @Schema(description = "测试环节，**逗号列表**（可多选）", example = "smoke,feature")
    private String stage;

    @Schema(description = "状态：wait/normal/blocked/investigate（新建缺省 wait 或 normal）", example = "normal")
    private String status;

    @Schema(description = "步骤列表。**只有步骤变化才会产生新版本**", example = "[]")
    private List<CaseStepVO> steps;

}

package cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 用例版本 Response VO")
@Data
public class CaseSpecRespVO {

    @Schema(description = "版本记录编号", example = "93202")
    private Long id;

    @Schema(description = "用例编号", example = "93101")
    private Long caseId;

    @Schema(description = "版本号", example = "2")
    private Integer version;

    @Schema(description = "该版本标题", example = "正常登录-用户名密码正确（补充验证码）")
    private String title;

    @Schema(description = "该版本前置条件")
    private String precondition;

    @Schema(description = "附件编号，逗号列表", example = "")
    private String files;

    @Schema(description = "该版本的步骤数", example = "4")
    private Integer stepCount;

    @Schema(description = "是否当前版本", example = "true")
    private Boolean current;

}

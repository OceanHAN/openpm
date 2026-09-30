package cn.iocoder.yudao.module.zentao.controller.admin.repo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 代码库新增/修改 Request VO")
@Data
public class RepoSaveReqVO {

    @Schema(description = "编号（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "代码库名称（唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "zentao")
    @NotBlank(message = "代码库名称不能为空")
    private String name;

    @Schema(description = "关联产品，逗号分隔", example = "1,2")
    private String product;

    @Schema(description = "源码管理类型（本实现只支持 git）", example = "git")
    private String scmType;

    @Schema(description = "本地仓库路径（服务器上可访问的 git 仓库）", example = "/tmp/demo-repo")
    @NotBlank(message = "仓库路径不能为空")
    private String path;

    @Schema(description = "默认分支", example = "master")
    private String defaultBranch;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "权限：open/private", example = "open")
    private String acl;

    @Schema(description = "状态：active/closed", example = "active")
    private String status;

}

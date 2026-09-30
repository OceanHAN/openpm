package cn.iocoder.yudao.module.zentao.controller.admin.repo.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 提交记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RepoCommitPageReqVO extends PageParam {

    @Schema(description = "代码库编号，不传=全部", example = "1")
    private Long repo;

    @Schema(description = "提交者（模糊）", example = "admin")
    private String committer;

    @Schema(description = "提交说明关键词（模糊）", example = "登录")
    private String keywords;

    @Schema(description = "只看某个对象关联的提交：对象类型 story/bug/task", example = "task")
    private String objectType;

    @Schema(description = "只看某个对象关联的提交：对象编号", example = "1")
    private Long objectID;

}

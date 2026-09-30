package cn.iocoder.yudao.module.zentao.controller.admin.repo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 提交记录 Response VO")
@Data
public class RepoCommitRespVO {

    @Schema(description = "提交记录编号", example = "1")
    private Long id;

    @Schema(description = "代码库编号", example = "1")
    private Long repo;

    @Schema(description = "代码库名称", example = "zentao")
    private String repoName;

    @Schema(description = "commit sha", example = "a1b2c3d")
    private String revision;

    @Schema(description = "第几次提交（自增序号）", example = "1")
    private Integer commit;

    @Schema(description = "提交说明")
    private String comment;

    @Schema(description = "提交者", example = "admin")
    private String committer;

    @Schema(description = "提交时间")
    private LocalDateTime time;

    @Schema(description = "改动的文件数", example = "3")
    private Integer fileCount;

    @Schema(description = "改动的文件（详情接口才返回）")
    private List<Map<String, Object>> files = new ArrayList<>();

    @Schema(description = "关联的对象（从提交说明里的 Story #1 / Task #2 / Bug #3 解析）")
    private List<Map<String, Object>> linkedObjects = new ArrayList<>();

}

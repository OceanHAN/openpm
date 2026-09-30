package cn.iocoder.yudao.module.zentao.controller.admin.repo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 代码库 Response VO")
@Data
public class RepoRespVO {

    private Long id;
    private String name;
    private String product;
    private String scmType;
    private String path;
    private String defaultBranch;
    private String desc;
    private String acl;
    private String status;
    private Integer synced;
    private String lastSyncRevision;
    private LocalDateTime lastSyncDate;
    private Integer lastSyncCount;
    private LocalDateTime createdDate;
    private LocalDateTime editedDate;

}

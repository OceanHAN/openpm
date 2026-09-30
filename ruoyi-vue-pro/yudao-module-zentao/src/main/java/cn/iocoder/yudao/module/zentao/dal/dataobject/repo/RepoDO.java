package cn.iocoder.yudao.module.zentao.dal.dataobject.repo;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码库（禅道 {@code zt_repo}，v21 起改名为 {@code ops_repo}）。
 *
 * <h3>本实现只支持「本地 Git 仓库」这一种来源</h3>
 * 禅道的代码库可以来自 GitLab / Gitea / Gitea / SVN / 本地 Git，前几种走
 * {@code module/provider} + {@code module/gitlab} 一整套「代码服务商」体系（本实现未迁移）。
 * 这里把「本地路径 + git 命令」这条最简单的链路做完整 —— 「提交 → 需求/任务/缺陷」
 * 的关联才是这个模块真正的价值，接服务商 API 属于接入层。
 *
 * <p>保留字/驼峰：{@code desc} 是 MySQL 关键字（坑位 #10），{@code scmType} 是驼峰列（坑位 #1）。
 */
@TableName("zt_repo")
@Data
public class RepoDO extends BaseDO {

    @TableId
    private Long id;

    /** 代码库名称（唯一） */
    private String name;

    /** 关联产品，逗号分隔 */
    private String product;

    /** 源码管理类型，本实现只支持 git。列名是驼峰 scmType */
    @TableField("scmType")
    private String scmType;

    /** 本地仓库路径 */
    private String path;

    /** 默认分支 */
    @TableField("defaultBranch")
    private String defaultBranch;

    /** 代码库描述。列名 desc 是 MySQL 关键字 */
    @TableField("`desc`")
    private String desc;

    /** 权限：open 公开 / private 私有 */
    private String acl;

    /** 状态：active 正常 / closed 关闭 */
    private String status;

    /** 是否已同步过 */
    private Integer synced;

    /** 最近同步到的 revision */
    @TableField("lastSyncRevision")
    private String lastSyncRevision;

    /** 最近同步时间 */
    @TableField("lastSyncDate")
    private LocalDateTime lastSyncDate;

    /** 最近一次同步进来的提交数 */
    @TableField("lastSyncCount")
    private Integer lastSyncCount;

}

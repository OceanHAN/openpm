package cn.iocoder.yudao.module.zentao.dal.dataobject.bug;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.zentao.enums.bug.BugStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 缺陷 DO
 *
 * 对应禅道 {@code zt_bug}。生命周期是 active → resolved → closed，随时可重新激活。
 *
 * 禅道对「解决」有两个强制联动：
 * <ul>
 *   <li>{@code resolution=duplicate} 时必须指定 {@code duplicateBug}，且该缺陷要存在</li>
 *   <li>{@code resolution=fixed} 时必须指定 {@code resolvedBuild}</li>
 * </ul>
 * 这两条在 Service 层实现。
 */
@TableName("zt_bug")
@KeySequence("zt_bug_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class BugDO extends BaseDO {

    @TableId
    private Long id;

    // ==================== 归属 ====================

    private Long product;
    private Long project;
    private Long execution;

    private Long module;

    /**
     * 所属分支/平台。产品类型为 platform 时是平台，为 branch 时是分支
     */
    private Long branch;

    private Long plan;
    /**
     * 关联需求
     */
    private Long story;
    /**
     * 关联任务
     */
    private Long task;

    /**
     * 来源用例。**case 是 MySQL 保留字**，必须加反引号
     */
    @TableField("`case`")
    private Long caseId;

    /**
     * 来源用例的版本（冻结值）。驼峰列名
     */
    @TableField("caseVersion")
    private Integer caseVersion;

    /**
     * 来源测试单
     */
    private Long testtask;

    // ==================== 内容 ====================

    private String title;
    private String keywords;
    /**
     * 严重程度 1~4
     */
    private Integer severity;
    private Integer pri;
    private String type;
    private String os;
    private String browser;
    /**
     * 重现步骤
     */
    private String steps;

    // ==================== 状态机 ====================

    /**
     * 状态
     *
     * 枚举 {@link BugStatusEnum}
     */
    private String status;
    private Integer confirmed;
    @TableField("activatedCount")
    private Integer activatedCount;
    @TableField("activatedDate")
    private LocalDateTime activatedDate;

    // ==================== 生命周期 ====================

    @TableField("openedBy")
    private String openedBy;
    @TableField("openedDate")
    private LocalDateTime openedDate;
    @TableField("openedBuild")
    private String openedBuild;
    @TableField("assignedTo")
    private String assignedTo;
    @TableField("assignedDate")
    private LocalDateTime assignedDate;
    private LocalDate deadline;
    @TableField("resolvedBy")
    private String resolvedBy;
    /**
     * 解决方案
     *
     * 枚举 {@link cn.iocoder.yudao.module.zentao.enums.bug.BugResolutionEnum}
     */
    private String resolution;
    @TableField("resolvedBuild")
    private String resolvedBuild;
    @TableField("resolvedDate")
    private LocalDateTime resolvedDate;
    @TableField("closedBy")
    private String closedBy;
    @TableField("closedDate")
    private LocalDateTime closedDate;
    @TableField("duplicateBug")
    private Long duplicateBug;
    @TableField("lastEditedBy")
    private String lastEditedBy;
    @TableField("lastEditedDate")
    private LocalDateTime lastEditedDate;

}

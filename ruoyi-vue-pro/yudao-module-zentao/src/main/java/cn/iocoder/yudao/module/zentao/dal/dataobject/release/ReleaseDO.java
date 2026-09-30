package cn.iocoder.yudao.module.zentao.dal.dataobject.release;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 产品发布 DO
 *
 * <p>对应禅道 {@code zt_release}：对外交付版本，引用构建与计划，维护三份清单
 * （stories 完成的需求 / bugs 解决的 Bug / leftBugs 遗留的 Bug）。
 *
 * <h3>几个必须注意的点</h3>
 * <ol>
 *   <li>{@code build}/{@code branch}/{@code project} 是<b>前后都带逗号的</b>逗号列表（{@code ',1,2,'}），
 *       和 {@code story.plan}（{@code '1,2'}）的写法不同 —— 禅道自己在同一套表里混用了两种编码</li>
 *   <li>{@code shadow} 是创建发布时**自动生成的影子构建**编号（同名同分支同日期的一条 zt_build）</li>
 *   <li>发布名 **全局唯一**（禅道按 `system` 查重，system 默认为 0，等价于跨产品唯一）</li>
 * </ol>
 *
 * <p>驼峰列名（{@code releasedDate}/{@code leftBugs}/{@code subStatus}/{@code createdBy}/{@code createdDate}）
 * 要显式声明；{@code desc} 与 {@code system} 是保留字（system 是 MySQL 8 保留字）。
 */
@TableName("zt_release")
@KeySequence("zt_release_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ReleaseDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 所属项目，逗号列表（',1,'）
     */
    private String project;

    /**
     * 所属产品
     */
    private Long product;

    /**
     * 分支/平台，逗号列表（',0,'）
     */
    private String branch;

    /**
     * 影子构建编号（创建发布时自动插入的 zt_build）
     */
    private Long shadow;

    /**
     * 包含的构建，逗号列表（',1,2,'）
     */
    private String build;

    /**
     * 发布版本号，全局唯一
     */
    private String name;

    /**
     * 所属系统（禅道原字段，未启用时恒为 0）。
     */
    @TableField("`system`")
    private Long system;

    /**
     * 被包含的子发布，逗号列表
     */
    private String releases;

    /**
     * 是否里程碑
     */
    private Integer marker;

    /**
     * 计划发布日期
     */
    @TableField("`date`")
    private LocalDate date;

    /**
     * 实际发布日期。驼峰列名
     */
    @TableField("releasedDate")
    private LocalDateTime releasedDate;

    /**
     * 本次完成的需求，逗号列表
     */
    private String stories;

    /**
     * 本次解决的 Bug，逗号列表
     */
    private String bugs;

    /**
     * 遗留的 Bug，逗号列表。驼峰列名
     */
    @TableField("leftBugs")
    private String leftBugs;

    /**
     * 是否发送通知（禅道原字段）
     */
    private String notify;

    /**
     * 抄送给（禅道原字段）
     */
    private String mailto;

    /**
     * 状态：wait/normal/fail/terminate
     */
    private String status;

    /**
     * 子状态（禅道原字段）。驼峰列名
     */
    @TableField("subStatus")
    private String subStatus;

    /**
     * 描述。列名 {@code desc} 是保留字
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 创建人。驼峰列名
     */
    @TableField("createdBy")
    private String createdBy;

    /**
     * 创建时间。驼峰列名
     */
    @TableField("createdDate")
    private LocalDateTime createdDate;

}

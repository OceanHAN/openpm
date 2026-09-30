package cn.iocoder.yudao.module.zentao.dal.dataobject.build;

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
 * 构建 DO
 *
 * <p>对应禅道 {@code zt_build}：一次打包记录，属于某个执行、引用某个产品，
 * 并记录本次完成的需求（{@code stories}）与解决的 Bug（{@code bugs}）——两者都是**逗号列表**。
 *
 * <h3>三个要点</h3>
 * <ol>
 *   <li><b>集成构建</b>：{@code builds} 是子构建编号的逗号列表，集成构建的 {@code execution} 为 0，
 *       {@code branch} 由子构建的分支并集算出；读取时 stories/bugs 要把子构建的并进来
 *       （禅道 {@code joinChildBuilds()}）</li>
 *   <li><b>stories / bugs 是多值文本</b>，查询要 {@code FIND_IN_SET}</li>
 *   <li>缺陷的 {@code resolvedBuild} 存的是**构建编号**，所以构建是「Bug 在哪个版本修好」的锚点</li>
 * </ol>
 *
 * <p>驼峰列名（{@code scmPath}/{@code filePath}/{@code artifactRepoID}/{@code createdBy}/{@code createdDate}）
 * 必须显式声明，否则 MyBatis-Plus 会转成下划线；{@code desc} 是保留字。
 */
@TableName("zt_build")
@KeySequence("zt_build_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class BuildDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 所属项目
     */
    private Long project;

    /**
     * 所属产品
     */
    private Long product;

    /**
     * 分支/平台，逗号列表；0 表示主干
     */
    private String branch;

    /**
     * 所属执行。集成构建固定为 0
     */
    private Long execution;

    /**
     * 集成构建包含的子构建编号，逗号列表
     */
    private String builds;

    /**
     * 构建名称
     */
    private String name;

    /**
     * 所属系统（禅道原字段）。
     *
     * <p>列名 {@code system} 是 <b>MySQL 8 的保留字</b>，不加反引号直接报 1064。
     * 注意 JSqlParser 4.5 是能解析 {@code system} 的 —— 也就是说「MySQL 保留字」
     * 和「JSqlParser 保留字」是两份不同的清单，两个都要过一遍。
     */
    @TableField("`system`")
    private Long system;

    /**
     * 源代码地址。驼峰列名
     */
    @TableField("scmPath")
    private String scmPath;

    /**
     * 下载地址。驼峰列名
     */
    @TableField("filePath")
    private String filePath;

    /**
     * 打包日期
     */
    private LocalDate date;

    /**
     * 本次完成的需求，逗号列表
     */
    private String stories;

    /**
     * 本次解决的 Bug，逗号列表
     */
    private String bugs;

    /**
     * 制品库编号（禅道原字段）。驼峰列名
     */
    @TableField("artifactRepoID")
    private Long artifactRepoID;

    /**
     * 构建者
     */
    private String builder;

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

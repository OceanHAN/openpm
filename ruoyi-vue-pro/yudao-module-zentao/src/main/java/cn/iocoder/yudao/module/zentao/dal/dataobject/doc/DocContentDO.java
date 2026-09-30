package cn.iocoder.yudao.module.zentao.dal.dataobject.doc;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文档版本内容 DO
 *
 * <p>对应禅道 {@code zt_doccontent}。这是本项目**第二个「追加式版本链」**
 * （第一个是 {@code zt_storyspec}），规则几乎一样：
 * <ul>
 *   <li>{@code (doc, version)} 唯一，一个版本一条快照</li>
 *   <li>编辑内容 → 追加 {@code version+1} 的新快照；只改基础信息 → 版本不动</li>
 * </ul>
 *
 * <p><b>唯一的差异</b>：doc 多了一个 {@code version=0} 的**草稿位**。
 * 草稿期间反复保存都改 version=0 那一行，发布时才升到 1。
 * 所以同一张表上「UPDATE 还是 INSERT」取决于目标版本是否已存在，
 * 这正是禅道 {@code saveDocContent} 的分支逻辑 —— 也是这个模块最容易写错的地方。
 *
 * <p>{@code rawContent} / {@code fromVersion} 是驼峰列名。
 */
@TableName("zt_doccontent")
@KeySequence("zt_doccontent_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class DocContentDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 文档编号
     */
    private Long doc;

    /**
     * 该版本的标题
     */
    private String title;

    /**
     * 该版本的摘要
     */
    private String digest;

    /**
     * 该版本的正文
     */
    private String content;

    /**
     * Markdown 原始内容。驼峰列名
     */
    @TableField("rawContent")
    private String rawContent;

    /**
     * 附件编号，逗号列表（zt_file.id）
     */
    private String files;

    /**
     * 内容类型：html / markdown / text
     */
    private String type;

    /**
     * 该版本创建人。驼峰列名
     */
    @TableField("addedBy")
    private String addedBy;

    /**
     * 该版本创建时间。驼峰列名
     */
    @TableField("addedDate")
    private LocalDateTime addedDate;

    /**
     * 该版本修改人。驼峰列名
     */
    @TableField("editedBy")
    private String editedBy;

    /**
     * 该版本修改时间。驼峰列名
     */
    @TableField("editedDate")
    private LocalDateTime editedDate;

    /**
     * 版本号，0 为草稿
     */
    private Integer version;

    /**
     * 衍生自哪个版本。驼峰列名
     */
    @TableField("fromVersion")
    private Integer fromVersion;

}

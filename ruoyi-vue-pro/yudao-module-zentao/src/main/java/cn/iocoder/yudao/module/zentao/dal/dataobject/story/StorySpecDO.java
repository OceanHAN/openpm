package cn.iocoder.yudao.module.zentao.dal.dataobject.story;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 需求版本快照 DO
 *
 * 对应禅道 {@code zt_storyspec}。这是需求变更历史的核心：
 * {@code zt_story} 只保存"当前是第几版"，而每一版的标题/描述/验收标准
 * 都按 {@code (story, version)} 存在本表，形成追加式的版本链。
 *
 * 两条写路径（与禅道 model.php 一致）：
 * <ul>
 *   <li>普通编辑：{@code UPDATE ... WHERE story=? AND version=当前版}，原地改当前版</li>
 *   <li>正式变更：{@code INSERT} 一行 {@code version+1} 的新快照，追加历史</li>
 * </ul>
 *
 * 表上有 {@code UNIQUE KEY (story, version)}，保证同一版本不会出现两条快照——
 * 这是整个版本机制正确性的基石。
 */
@TableName("zt_storyspec")
@KeySequence("zt_storyspec_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StorySpecDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 需求编号
     */
    private Long story;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 该版本的需求标题
     *
     * 注意：标题在主表和本表都有。禅道读取时以本表为准
     * （见 model.php getById），主表的标题用于列表展示与检索。
     */
    private String title;

    /**
     * 该版本的需求描述
     */
    private String spec;

    /**
     * 该版本的验收标准
     */
    private String verify;

    /**
     * 附件编号，逗号分隔
     */
    private String files;

    /**
     * 关联文档
     */
    private String docs;

    /**
     * 关联文档版本
     */
    @TableField("docVersions")
    private String docVersions;

}

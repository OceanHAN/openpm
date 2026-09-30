package cn.iocoder.yudao.module.zentao.dal.dataobject.story;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 需求评审 DO
 *
 * 对应禅道 {@code zt_storyreview}。评审是**绑定到版本**的：
 * 同一需求的不同版本可以有完全不同的评审人与结果。
 *
 * 一个评审人一行，表决就是更新自己那一行的 {@code result} 与 {@code reviewDate}。
 * 只有当前版本的评审人**全部**提交后，才会触发需求状态流转。
 */
@TableName("zt_storyreview")
@KeySequence("zt_storyreview_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StoryReviewDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 需求编号
     */
    private Long story;

    /**
     * 被评审的版本号
     */
    private Integer version;

    /**
     * 评审人账号
     */
    private String reviewer;

    /**
     * 评审结果
     *
     * 枚举 {@link cn.iocoder.yudao.module.zentao.enums.story.StoryReviewResultEnum}
     * 空串表示尚未表决
     */
    private String result;

    /**
     * 评审时间
     */
    @TableField("reviewDate")
    private LocalDateTime reviewDate;

}

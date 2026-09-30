package cn.iocoder.yudao.module.zentao.dal.mysql.story;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryReviewDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 需求评审 Mapper
 */
@Mapper
public interface StoryReviewMapper extends BaseMapperX<StoryReviewDO> {

    /**
     * 取某个需求某个版本的全部评审记录
     */
    default List<StoryReviewDO> selectListByStoryAndVersion(Long story, Integer version) {
        return selectList(new LambdaQueryWrapperX<StoryReviewDO>()
                .eq(StoryReviewDO::getStory, story)
                .eq(StoryReviewDO::getVersion, version)
                .orderByAsc(StoryReviewDO::getId));
    }

    /**
     * 取某个评审人在某需求某版本上的记录
     */
    default StoryReviewDO selectByStoryVersionReviewer(Long story, Integer version, String reviewer) {
        return selectOne(new LambdaQueryWrapperX<StoryReviewDO>()
                .eq(StoryReviewDO::getStory, story)
                .eq(StoryReviewDO::getVersion, version)
                .eq(StoryReviewDO::getReviewer, reviewer));
    }

    /**
     * 物理删除某个需求某个版本的评审记录。
     *
     * 【为什么必须物理删除】BaseDO.deleted 带 @TableLogic，普通 deleteById 是逻辑删除，
     * 行仍然占着 (story, version, reviewer) 这个唯一键。评审结果为 revert 时需求会
     * 回滚到 version-1，之后再变更又会写到同一个 version，逻辑删除会让唯一键冲突。
     * 禅道在 setStatusByReviewResult 里也是直接 DELETE，这里保持一致。
     */
    @Delete("DELETE FROM zt_storyreview WHERE story = #{story} AND version = #{version}")
    int physicalDeleteByStoryAndVersion(@Param("story") Long story, @Param("version") Integer version);

    /**
     * 物理删除某需求某个版本中不在指定评审人列表里的记录。
     * 用于重新提交评审时移除已被剔除的评审人（对应禅道 doUpdateReviewer）。
     */
    @Delete("DELETE FROM zt_storyreview WHERE story = #{story} AND version = #{version} "
            + "AND reviewer NOT IN (${reviewerList})")
    int physicalDeleteNotInReviewers(@Param("story") Long story, @Param("version") Integer version,
                                     @Param("reviewerList") String reviewerList);

}

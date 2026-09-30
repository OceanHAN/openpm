package cn.iocoder.yudao.module.zentao.dal.mysql.story;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StorySpecDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 需求版本快照 Mapper
 */
@Mapper
public interface StorySpecMapper extends BaseMapperX<StorySpecDO> {

    /**
     * 按 (需求, 版本) 取快照 —— 对应禅道 getById 里的
     * {@code WHERE story=? AND version=?}
     */
    default StorySpecDO selectByStoryAndVersion(Long story, Integer version) {
        return selectOne(new LambdaQueryWrapperX<StorySpecDO>()
                .eq(StorySpecDO::getStory, story)
                .eq(StorySpecDO::getVersion, version));
    }

    /**
     * 取某个需求的全部版本历史，版本号倒序（最新的在前）
     */
    default List<StorySpecDO> selectListByStory(Long story) {
        return selectList(new LambdaQueryWrapperX<StorySpecDO>()
                .eq(StorySpecDO::getStory, story)
                .orderByDesc(StorySpecDO::getVersion));
    }

    /**
     * 物理删除某个需求某个版本的快照。
     *
     * 【为什么必须物理删除】BaseDO.deleted 带 @TableLogic，而表上有唯一键
     * (story, version)。评审结果为 revert 时需求回滚到 version-1，之后再次变更
     * 又会写入同一个 version，若采用逻辑删除就会撞唯一键。
     * 禅道 setStatusByReviewResult 里同样是直接 DELETE，这里保持一致。
     */
    @Delete("DELETE FROM zt_storyspec WHERE story = #{story} AND version = #{version}")
    int physicalDeleteByStoryAndVersion(@Param("story") Long story, @Param("version") Integer version);

}

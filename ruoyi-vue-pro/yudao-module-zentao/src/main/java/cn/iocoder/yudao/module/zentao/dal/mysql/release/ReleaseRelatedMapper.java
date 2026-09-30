package cn.iocoder.yudao.module.zentao.dal.mysql.release;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.release.ReleaseRelatedDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 发布关联对象 Mapper
 *
 * <p>关系是纯结构数据，删除即物理删除（{@code zt_releaserelated} 没有 deleted 列，
 * 也没有继承 BaseDO），所以这里没有 {@code @TableLogic} 的坑。
 */
@Mapper
public interface ReleaseRelatedMapper extends BaseMapperX<ReleaseRelatedDO> {

    /**
     * 某个发布下的全部关联
     */
    default List<ReleaseRelatedDO> selectListByRelease(Long releaseId) {
        return selectList(new LambdaQueryWrapperX<ReleaseRelatedDO>()
                .eq(ReleaseRelatedDO::getRelease, releaseId)
                .orderByAsc(ReleaseRelatedDO::getId));
    }

    /**
     * 某个发布下、某一类对象的关联
     */
    default List<ReleaseRelatedDO> selectListByRelease(Long releaseId, String objectType) {
        return selectList(new LambdaQueryWrapperX<ReleaseRelatedDO>()
                .eq(ReleaseRelatedDO::getRelease, releaseId)
                .eq(ReleaseRelatedDO::getObjectType, objectType)
                .orderByAsc(ReleaseRelatedDO::getId));
    }

    /**
     * 删除某个发布下、某一类对象的关联（对应禅道 updateRelated 的先删后插）
     */
    default void deleteByRelease(Long releaseId, String objectType) {
        delete(new LambdaQueryWrapperX<ReleaseRelatedDO>()
                .eq(ReleaseRelatedDO::getRelease, releaseId)
                .eq(ReleaseRelatedDO::getObjectType, objectType));
    }

    /**
     * 删除某个发布的全部关联
     */
    default void deleteByRelease(Long releaseId) {
        delete(new LambdaQueryWrapperX<ReleaseRelatedDO>()
                .eq(ReleaseRelatedDO::getRelease, releaseId));
    }

}

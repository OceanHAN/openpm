package cn.iocoder.yudao.module.zentao.dal.mysql.stakeholder;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.stakeholder.StakeholderDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 干系人 Mapper
 */
@Mapper
public interface StakeholderMapper extends BaseMapperX<StakeholderDO> {

    /**
     * 某个对象下的干系人：关键干系人排前面，其余按加入顺序
     */
    default List<StakeholderDO> selectListByObject(String objectType, Long objectID) {
        return selectList(new LambdaQueryWrapperX<StakeholderDO>()
                .eq(StakeholderDO::getObjectType, objectType)
                .eq(StakeholderDO::getObjectID, objectID)
                .orderByDesc(StakeholderDO::getKey)
                .orderByAsc(StakeholderDO::getId));
    }

    /**
     * 同一个人是否已经在这个对象下（禅道的唯一性校验口径）
     */
    default StakeholderDO selectByObjectAndUser(String objectType, Long objectID, String user) {
        return selectOne(new LambdaQueryWrapperX<StakeholderDO>()
                .eq(StakeholderDO::getObjectType, objectType)
                .eq(StakeholderDO::getObjectID, objectID)
                .eq(StakeholderDO::getUser, user)
                .last("LIMIT 1"));
    }

    /**
     * 批量取多个对象的干系人（列表页一次查完，避免 N+1）
     */
    default List<StakeholderDO> selectListByObjects(String objectType, Collection<Long> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<StakeholderDO>()
                .eq(StakeholderDO::getObjectType, objectType)
                .in(StakeholderDO::getObjectID, objectIds)
                .orderByAsc(StakeholderDO::getObjectID)
                .orderByDesc(StakeholderDO::getKey)
                .orderByAsc(StakeholderDO::getId));
    }

    /**
     * 我参与的（作为干系人）：「我的地盘」/项目列表用得上
     */
    default List<StakeholderDO> selectListByUser(String objectType, String user) {
        return selectList(new LambdaQueryWrapperX<StakeholderDO>()
                .eqIfPresent(StakeholderDO::getObjectType, objectType)
                .eq(StakeholderDO::getUser, user)
                .orderByDesc(StakeholderDO::getId));
    }

}

package cn.iocoder.yudao.module.zentao.dal.mysql.action;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.action.HistoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 操作日志字段变更明细 Mapper
 */
@Mapper
public interface HistoryMapper extends BaseMapperX<HistoryDO> {

    /**
     * 按操作日志编号取变更明细
     */
    default List<HistoryDO> selectListByAction(Long actionId) {
        return selectList(new LambdaQueryWrapperX<HistoryDO>()
                .eq(HistoryDO::getAction, actionId)
                .orderByAsc(HistoryDO::getId));
    }

    /**
     * 批量取多个操作日志的变更明细，供时间线一次性加载，避免 N+1 查询
     */
    default List<HistoryDO> selectListByActions(Collection<Long> actionIds) {
        if (actionIds == null || actionIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<HistoryDO>()
                .in(HistoryDO::getAction, actionIds)
                .orderByAsc(HistoryDO::getId));
    }

}

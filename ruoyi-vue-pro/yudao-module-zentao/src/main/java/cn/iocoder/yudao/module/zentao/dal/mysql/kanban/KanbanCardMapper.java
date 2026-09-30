package cn.iocoder.yudao.module.zentao.dal.mysql.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.KanbanCardDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * KanbanCardMapper Mapper
 */
@Mapper
public interface KanbanCardMapper extends BaseMapperX<KanbanCardDO> {


    default PageResult<KanbanCardDO> selectPage(Long kanban, String name, Integer archived, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<KanbanCardDO>()
                .eqIfPresent(KanbanCardDO::getKanban, kanban)
                .likeIfPresent(KanbanCardDO::getName, name)
                .eqIfPresent(KanbanCardDO::getArchived, archived)
                .orderByAsc(KanbanCardDO::getOrder)
                .orderByDesc(KanbanCardDO::getId));
    }

    default List<KanbanCardDO> selectListByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<KanbanCardDO>()
                .in(KanbanCardDO::getId, ids)
                .orderByAsc(KanbanCardDO::getId));
    }

    default List<KanbanCardDO> selectListByKanban(Long kanban) {
        return selectList(new LambdaQueryWrapperX<KanbanCardDO>()
                .eq(KanbanCardDO::getKanban, kanban)
                .orderByAsc(KanbanCardDO::getOrder)
                .orderByAsc(KanbanCardDO::getId));
    }

    default Long countByKanban(Long kanban) {
        return selectCount(new LambdaQueryWrapperX<KanbanCardDO>()
                .eq(KanbanCardDO::getKanban, kanban));
    }

}

package cn.iocoder.yudao.module.zentao.dal.mysql.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.KanbanDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * KanbanMapper Mapper
 */
@Mapper
public interface KanbanMapper extends BaseMapperX<KanbanDO> {


    default PageResult<KanbanDO> selectPage(Long space, String name, String status, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<KanbanDO>()
                .eqIfPresent(KanbanDO::getSpace, space)
                .likeIfPresent(KanbanDO::getName, name)
                .eqIfPresent(KanbanDO::getStatus, status)
                .orderByDesc(KanbanDO::getOrder)
                .orderByDesc(KanbanDO::getId));
    }

    default List<KanbanDO> selectListBySpace(Long space) {
        return selectList(new LambdaQueryWrapperX<KanbanDO>()
                .eq(KanbanDO::getSpace, space)
                .orderByDesc(KanbanDO::getOrder)
                .orderByDesc(KanbanDO::getId));
    }

    default Long countBySpace(Long space) {
        return selectCount(new LambdaQueryWrapperX<KanbanDO>()
                .eq(KanbanDO::getSpace, space));
    }

    /** 同一空间下看板名唯一（禅道 create 里由 autoCheck + 表单校验保证，这里显式判重） */
    default KanbanDO selectBySpaceAndName(Long space, String name) {
        return selectOne(new LambdaQueryWrapperX<KanbanDO>()
                .eq(KanbanDO::getSpace, space)
                .eq(KanbanDO::getName, name));
    }

}

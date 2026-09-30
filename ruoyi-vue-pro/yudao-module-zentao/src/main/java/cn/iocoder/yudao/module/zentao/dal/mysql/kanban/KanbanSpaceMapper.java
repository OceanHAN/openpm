package cn.iocoder.yudao.module.zentao.dal.mysql.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.KanbanSpaceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * KanbanSpaceMapper Mapper
 */
@Mapper
public interface KanbanSpaceMapper extends BaseMapperX<KanbanSpaceDO> {


    default PageResult<KanbanSpaceDO> selectPage(String name, String type, String status, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<KanbanSpaceDO>()
                .likeIfPresent(KanbanSpaceDO::getName, name)
                .eqIfPresent(KanbanSpaceDO::getType, type)
                .eqIfPresent(KanbanSpaceDO::getStatus, status)
                .orderByDesc(KanbanSpaceDO::getOrder)
                .orderByDesc(KanbanSpaceDO::getId));
    }

    default List<KanbanSpaceDO> selectListByType(String type) {
        return selectList(new LambdaQueryWrapperX<KanbanSpaceDO>()
                .eqIfPresent(KanbanSpaceDO::getType, type)
                .orderByDesc(KanbanSpaceDO::getOrder)
                .orderByDesc(KanbanSpaceDO::getId));
    }

    default KanbanSpaceDO selectByName(String name) {
        return selectOne(KanbanSpaceDO::getName, name);
    }

}

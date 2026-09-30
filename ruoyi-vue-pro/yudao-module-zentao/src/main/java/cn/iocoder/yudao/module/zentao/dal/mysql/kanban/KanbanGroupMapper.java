package cn.iocoder.yudao.module.zentao.dal.mysql.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.KanbanGroupDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * KanbanGroupMapper Mapper
 */
@Mapper
public interface KanbanGroupMapper extends BaseMapperX<KanbanGroupDO> {


    default KanbanGroupDO selectByRegion(Long region) {
        return selectOne(KanbanGroupDO::getRegion, region);
    }

    default List<KanbanGroupDO> selectListByKanban(Long kanban) {
        return selectList(new LambdaQueryWrapperX<KanbanGroupDO>()
                .eq(KanbanGroupDO::getKanban, kanban)
                .orderByAsc(KanbanGroupDO::getOrder));
    }

}

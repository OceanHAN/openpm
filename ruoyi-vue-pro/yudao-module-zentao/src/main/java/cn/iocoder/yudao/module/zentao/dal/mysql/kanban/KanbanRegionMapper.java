package cn.iocoder.yudao.module.zentao.dal.mysql.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.KanbanRegionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * KanbanRegionMapper Mapper
 */
@Mapper
public interface KanbanRegionMapper extends BaseMapperX<KanbanRegionDO> {


    default List<KanbanRegionDO> selectListByKanban(Long kanban) {
        return selectList(new LambdaQueryWrapperX<KanbanRegionDO>()
                .eq(KanbanRegionDO::getKanban, kanban)
                .orderByAsc(KanbanRegionDO::getOrder)
                .orderByAsc(KanbanRegionDO::getId));
    }

    /** 区域名在同一个 (kanban, space) 下唯一（禅道 createRegion 的 unique 校验） */
    default KanbanRegionDO selectByKanbanAndName(Long kanban, Long space, String name) {
        return selectOne(new LambdaQueryWrapperX<KanbanRegionDO>()
                .eq(KanbanRegionDO::getKanban, kanban)
                .eq(KanbanRegionDO::getSpace, space)
                .eq(KanbanRegionDO::getName, name));
    }

    default Long selectMaxOrder(Long kanban) {
        List<KanbanRegionDO> list = selectListByKanban(kanban);
        long max = 0;
        for (KanbanRegionDO region : list) {
            max = Math.max(max, region.getOrder() == null ? 0 : region.getOrder());
        }
        return max;
    }

}

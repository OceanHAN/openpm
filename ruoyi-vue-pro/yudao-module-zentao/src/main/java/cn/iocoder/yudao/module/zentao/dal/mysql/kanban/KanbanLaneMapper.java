package cn.iocoder.yudao.module.zentao.dal.mysql.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.KanbanLaneDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * KanbanLaneMapper Mapper
 */
@Mapper
public interface KanbanLaneMapper extends BaseMapperX<KanbanLaneDO> {


    default List<KanbanLaneDO> selectListByGroup(Long group) {
        return selectList(new LambdaQueryWrapperX<KanbanLaneDO>()
                .eq(KanbanLaneDO::getGroupId, group)
                .orderByAsc(KanbanLaneDO::getOrder)
                .orderByAsc(KanbanLaneDO::getId));
    }

    default List<KanbanLaneDO> selectListByRegion(Long region) {
        return selectList(new LambdaQueryWrapperX<KanbanLaneDO>()
                .eq(KanbanLaneDO::getRegion, region)
                .orderByAsc(KanbanLaneDO::getOrder)
                .orderByAsc(KanbanLaneDO::getId));
    }

    /**
     * 泳道按 order 插入时，把后面的泳道整体后移一位
     */
    @Update("UPDATE zt_kanbanlane SET `order` = `order` + 1 WHERE `group` = #{group} AND `order` >= #{order} AND deleted = 0")
    int shiftOrder(@Param("group") Long group, @Param("order") Integer order);

    default Long selectMaxOrder(Long group) {
        long max = 0;
        for (KanbanLaneDO lane : selectListByGroup(group)) {
            max = Math.max(max, lane.getOrder() == null ? 0 : lane.getOrder());
        }
        return max;
    }

}

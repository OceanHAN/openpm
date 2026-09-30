package cn.iocoder.yudao.module.zentao.dal.mysql.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.KanbanColumnDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * KanbanColumnMapper Mapper
 */
@Mapper
public interface KanbanColumnMapper extends BaseMapperX<KanbanColumnDO> {


    default List<KanbanColumnDO> selectListByGroup(Long group) {
        return selectList(new LambdaQueryWrapperX<KanbanColumnDO>()
                .eq(KanbanColumnDO::getGroupId, group)
                .orderByAsc(KanbanColumnDO::getOrder)
                .orderByAsc(KanbanColumnDO::getId));
    }

    default List<KanbanColumnDO> selectListByRegion(Long region) {
        return selectList(new LambdaQueryWrapperX<KanbanColumnDO>()
                .eq(KanbanColumnDO::getRegion, region)
                .orderByAsc(KanbanColumnDO::getOrder)
                .orderByAsc(KanbanColumnDO::getId));
    }

    /** 子列的 WIP 之和（禅道 createColumn 里校验「子列之和 ≤ 父列限额」用） */
    default Long sumChildLimit(Long parent) {
        long sum = 0;
        for (KanbanColumnDO column : selectList(new LambdaQueryWrapperX<KanbanColumnDO>()
                .eq(KanbanColumnDO::getParent, parent))) {
            sum += column.getLimit() == null ? 0 : column.getLimit();
        }
        return sum;
    }

    /**
     * 新列插到 order 位置时，把后面的列整体后移一位（禅道 createColumn 里的 `order` = `order` + 1）
     */
    @Update("UPDATE zt_kanbancolumn SET `order` = `order` + 1 WHERE `group` = #{group} AND `order` >= #{order} AND deleted = 0")
    int shiftOrder(@Param("group") Long group, @Param("order") Integer order);

    default Long selectMaxOrder(Long group) {
        long max = 0;
        for (KanbanColumnDO column : selectListByGroup(group)) {
            max = Math.max(max, column.getOrder() == null ? 0 : column.getOrder());
        }
        return max;
    }

}

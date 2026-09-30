package cn.iocoder.yudao.module.zentao.dal.mysql.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.KanbanCellDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * KanbanCellMapper Mapper
 */
@Mapper
public interface KanbanCellMapper extends BaseMapperX<KanbanCellDO> {


    default KanbanCellDO selectByLaneAndColumn(Long lane, Long column) {
        return selectOne(new LambdaQueryWrapperX<KanbanCellDO>()
                .eq(KanbanCellDO::getLane, lane)
                .eq(KanbanCellDO::getColumn, column));
    }

    default List<KanbanCellDO> selectListByKanban(Long kanban) {
        return selectList(new LambdaQueryWrapperX<KanbanCellDO>()
                .eq(KanbanCellDO::getKanban, kanban)
                .orderByAsc(KanbanCellDO::getId));
    }

    /** 更新格子的 cards 逗号列表 */
    @Update("UPDATE zt_kanbancell SET cards = #{cards} WHERE id = #{id}")
    int updateCards(@Param("id") Long id, @Param("cards") String cards);

    /** 删泳道时物理清理它的格子（格子没有 deleted 列） */
    @Delete("DELETE FROM zt_kanbancell WHERE lane = #{lane}")
    int deleteByLane(@Param("lane") Long lane);

    /** 删列时物理清理它的格子（`column` 是保留字） */
    @Delete("DELETE FROM zt_kanbancell WHERE `column` = #{column}")
    int deleteByColumn(@Param("column") Long column);

    /** 删区域/看板时整块清理 */
    @Delete("DELETE FROM zt_kanbancell WHERE kanban = #{kanban}")
    int deleteByKanban(@Param("kanban") Long kanban);

    /**
     * 「这张卡片在哪些格子里」—— 卡片换泳道时要按**泳道类型**把同区域的所有格子都摘一遍
     * （禅道 moveCard 里的 fromCells 查询）
     */
}

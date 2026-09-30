package cn.iocoder.yudao.module.zentao.dal.mysql.team;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.team.TeamDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 团队成员 Mapper
 *
 * 注意本表**没有 deleted 列**：成员的增删都是物理操作（`@Delete` 原生 SQL）。
 */
@Mapper
public interface TeamMapper extends BaseMapperX<TeamDO> {

    default List<TeamDO> selectListByRoot(Long root, String type) {
        return selectList(new LambdaQueryWrapperX<TeamDO>()
                .eq(TeamDO::getRoot, root)
                .eq(TeamDO::getType, type)
                .orderByAsc(TeamDO::getOrder)
                .orderByAsc(TeamDO::getId));
    }

    default TeamDO selectByRootAndAccount(Long root, String type, String account) {
        return selectOne(new LambdaQueryWrapperX<TeamDO>()
                .eq(TeamDO::getRoot, root)
                .eq(TeamDO::getType, type)
                .eq(TeamDO::getAccount, account)
                .last("LIMIT 1"));
    }

    default List<TeamDO> selectListByAccount(String account) {
        return selectList(new LambdaQueryWrapperX<TeamDO>()
                .eq(TeamDO::getAccount, account)
                .orderByDesc(TeamDO::getId));
    }

    /**
     * 批量统计各对象的成员数（项目列表的「团队人数」就靠它）：
     * {@code SELECT root, COUNT(1) FROM zt_team WHERE root IN (...) AND type=? GROUP BY root}
     */
    @Select("<script>SELECT root AS root, COUNT(1) AS cnt FROM zt_team " +
            "WHERE type = #{type} AND root IN " +
            "<foreach collection='roots' item='r' open='(' separator=',' close=')'>#{r}</foreach> " +
            "GROUP BY root</script>")
    List<Map<String, Object>> countByRoots(@Param("roots") List<Long> roots, @Param("type") String type);

    /** 物理删除某个对象下的全部成员（禅道 updateTeamMembers 的第一步） */
    @Delete("DELETE FROM zt_team WHERE root = #{root} AND type = #{type}")
    int deleteByRoot(@Param("root") Long root, @Param("type") String type);

}

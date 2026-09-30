package cn.iocoder.yudao.module.zentao.dal.mysql.repo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.repo.RelationDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RelationMapper extends BaseMapperX<RelationDO> {

    /** 某条提交关联的全部对象 */
    default List<RelationDO> selectListByRevision(Long revisionId) {
        return selectList(new LambdaQueryWrapperX<RelationDO>()
                .eq(RelationDO::getAType, "revision")
                .eq(RelationDO::getAId, revisionId)
                .eq(RelationDO::getRelation, "commit")
                .orderByAsc(RelationDO::getBType));
    }

    /** 重同步某条提交时先清掉它的旧关联 */
    @Delete("DELETE FROM zt_relation WHERE AType = 'revision' AND AID = #{revisionId} AND relation = 'commit'")
    int deleteByRevision(@Param("revisionId") Long revisionId);

    /** 删提交记录（代码库删除时） */
    @Delete("DELETE FROM zt_relation WHERE AType = 'revision' AND relation = 'commit' "
            + "AND AID IN (SELECT id FROM zt_repohistory WHERE repo = #{repo})")
    int deleteByRepo(@Param("repo") Long repo);

    /** 某条提交是否已关联过某个对象（幂等用） */
    @Select("SELECT COUNT(1) FROM zt_relation WHERE AType = 'revision' AND AID = #{revisionId} "
            + "AND relation = 'commit' AND BType = #{objectType} AND BID = #{objectId}")
    Integer countRelation(@Param("revisionId") Long revisionId, @Param("objectType") String objectType,
                          @Param("objectId") Long objectId);

}

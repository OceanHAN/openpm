package cn.iocoder.yudao.module.zentao.dal.mysql.api;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiSpecDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 接口版本内容 Mapper。
 *
 * <p>本表**没有 {@code deleted} 列**，所以 {@code @TableLogic} 不生效，
 * 删除就是物理删除 —— 这正是禅道的行为（{@code model.php:162} 先
 * {@code delete from zt_apispec where doc = x and version = y} 再 insert）。
 * 用 {@code @Delete} 显式声明，别用 {@code deleteById}（那会拼出 {@code deleted} 条件）。
 */
@Mapper
public interface ApiSpecMapper extends BaseMapperX<ApiSpecDO> {

    /** 取某个接口的某个版本（禅道 {@code getByID} 的 spec 分支） */
    default ApiSpecDO selectByDocAndVersion(Long doc, Integer version) {
        return selectOne(new LambdaQueryWrapperX<ApiSpecDO>()
                .eq(ApiSpecDO::getDoc, doc)
                .eq(ApiSpecDO::getVersion, version));
    }

    /** 取某个接口的全部版本，版本号倒序（最新在前，供详情抽屉的版本链展示） */
    default List<ApiSpecDO> selectListByDoc(Long doc) {
        LambdaQueryWrapperX<ApiSpecDO> wrapper = new LambdaQueryWrapperX<ApiSpecDO>()
                .eq(ApiSpecDO::getDoc, doc);
        wrapper.orderByDesc(ApiSpecDO::getVersion);
        return selectList(wrapper);
    }

    /**
     * 取一批接口的全部版本。
     *
     * <p>按发布版本浏览时用：snap 只给 {@code (id, version)}，先在 Java 里把版本挑出来，
     * 避免在 SQL 里拼一堆 {@code (doc = x AND version = y)} 的 OR 条件。
     */
    default List<ApiSpecDO> selectListByDocs(Collection<Long> docs) {
        return selectList(new LambdaQueryWrapperX<ApiSpecDO>()
                .in(ApiSpecDO::getDoc, docs));
    }

    /** 物理删除某个 (doc, version) 的快照行 —— 禅道 update 的第一步 */
    @Delete("DELETE FROM zt_apispec WHERE doc = #{doc} AND version = #{version}")
    int deleteByDocAndVersion(@Param("doc") Long doc, @Param("version") Integer version);

    /**
     * 某接口有几个版本（详情里给「共 N 个版本」用）。
     */
    @Select("SELECT COUNT(*) FROM zt_apispec WHERE doc = #{doc}")
    int countByDoc(@Param("doc") Long doc);

    /**
     * 一批接口各有几个版本。
     *
     * <p>列表页要展示「v2 · 共 2 版」，逐行走 {@code countByDoc} 就是 N+1 次查询，
     * 这里一次分组查询拿全（与 {@code QaMapper.selectBugStatusStat} 同一个写法）。
     */
    @Select("<script>SELECT doc AS doc, COUNT(1) AS cnt FROM zt_apispec WHERE doc IN "
            + "<foreach collection='docs' item='d' open='(' separator=',' close=')'>#{d}</foreach> "
            + "GROUP BY doc</script>")
    List<Map<String, Object>> countByDocs(@Param("docs") Collection<Long> docs);

}

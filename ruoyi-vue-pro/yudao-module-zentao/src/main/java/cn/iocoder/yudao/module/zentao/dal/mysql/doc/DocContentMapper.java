package cn.iocoder.yudao.module.zentao.dal.mysql.doc;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocContentDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档版本内容 Mapper
 */
@Mapper
public interface DocContentMapper extends BaseMapperX<DocContentDO> {

    /**
     * 指定版本（version=0 即草稿）
     */
    default DocContentDO selectByDocAndVersion(Long doc, Integer version) {
        return selectOne(new LambdaQueryWrapperX<DocContentDO>()
                .eq(DocContentDO::getDoc, doc)
                .eq(DocContentDO::getVersion, version));
    }

    /**
     * 版本历史，最新在前（草稿 version=0 排在最后）
     */
    default List<DocContentDO> selectListByDoc(Long doc) {
        return selectList(new LambdaQueryWrapperX<DocContentDO>()
                .eq(DocContentDO::getDoc, doc)
                .orderByDesc(DocContentDO::getVersion));
    }

    /**
     * **物理删除**某个版本。
     *
     * <p>必须物理删：表上有 {@code UNIQUE(doc, version)}，逻辑删除后那一行还占着版本号，
     * 之后再生成同一版本号就会撞唯一键（README 第 4 条坑，需求快照 revert 时踩过一次）。
     */
    @Delete("DELETE FROM zt_doccontent WHERE doc = #{doc} AND version = #{version}")
    int deleteByDocAndVersionPhysical(@Param("doc") Long doc, @Param("version") Integer version);

    /**
     * 物理删除某文档的全部版本（删除文档时连带清理）
     */
    @Delete("DELETE FROM zt_doccontent WHERE doc = #{doc}")
    int deleteByDocPhysical(@Param("doc") Long doc);

}

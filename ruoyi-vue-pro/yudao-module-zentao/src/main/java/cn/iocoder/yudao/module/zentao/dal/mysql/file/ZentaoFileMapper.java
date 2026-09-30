package cn.iocoder.yudao.module.zentao.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.file.ZentaoFileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 附件 Mapper
 *
 * <p>名字带 Zentao 前缀是为了避开 infra 模块同名的 fileMapper bean。
 */
@Mapper
public interface ZentaoFileMapper extends BaseMapperX<ZentaoFileDO> {

    /**
     * 某个对象下的附件（新的在前）
     */
    default List<ZentaoFileDO> selectListByObject(String objectType, Long objectID) {
        return selectList(new LambdaQueryWrapperX<ZentaoFileDO>()
                .eq(ZentaoFileDO::getObjectType, objectType)
                .eq(ZentaoFileDO::getObjectID, objectID)
                .orderByDesc(ZentaoFileDO::getId));
    }

    /**
     * 某个临时分组（gid）下的附件，用于「先传附件、后绑对象」
     */
    default List<ZentaoFileDO> selectListByGid(String gid) {
        return selectList(new LambdaQueryWrapperX<ZentaoFileDO>()
                .eq(ZentaoFileDO::getGid, gid)
                .orderByAsc(ZentaoFileDO::getId));
    }

    /**
     * 下载计数 +1。
     *
     * <p>用原生 UPDATE 而不是「查出来再 updateById」：并发下载时后者会互相覆盖，
     * 而且多一次查询。{@code downloads = downloads + 1} 让数据库自己保证原子性。
     */
    @Update("UPDATE zt_file SET downloads = downloads + 1 WHERE id = #{id} AND deleted = 0")
    int increaseDownloads(@Param("id") Long id);

}

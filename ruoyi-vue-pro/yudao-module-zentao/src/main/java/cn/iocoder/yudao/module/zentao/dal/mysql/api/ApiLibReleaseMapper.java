package cn.iocoder.yudao.module.zentao.dal.mysql.api;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiLibReleaseDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 接口库发布版本 Mapper。
 *
 * <p>本表没有 {@code deleted} 列：禅道 {@code deleteRelease}（{@code model.php:80}）
 * 是**物理删除**一个发布版本，所以这里也用 {@code @Delete} 而不是逻辑删除
 * （用 {@code deleteById} 会拼出 {@code deleted} 条件，直接报 Unknown column）。
 */
@Mapper
public interface ApiLibReleaseMapper extends BaseMapperX<ApiLibReleaseDO> {

    /** 某库的发布版本列表，最新在前（禅道 releases 页默认 orderBy=id） */
    default List<ApiLibReleaseDO> selectListByLib(Long lib) {
        LambdaQueryWrapperX<ApiLibReleaseDO> wrapper = new LambdaQueryWrapperX<ApiLibReleaseDO>()
                .eqIfPresent(ApiLibReleaseDO::getLib, lib);
        wrapper.orderByDesc(ApiLibReleaseDO::getId);
        return selectList(wrapper);
    }

    /** 某库下是否已有这个版本号（禅道 control.php:441 的唯一性校验） */
    default ApiLibReleaseDO selectByLibAndVersion(Long lib, String version) {
        return selectOne(new LambdaQueryWrapperX<ApiLibReleaseDO>()
                .eq(ApiLibReleaseDO::getLib, lib)
                .eq(ApiLibReleaseDO::getVersion, version));
    }

    /** 物理删除一个发布版本（禅道 deleteRelease 原样） */
    @Delete("DELETE FROM zt_api_lib_release WHERE id = #{id}")
    int deleteByIdPhysical(@Param("id") Long id);

}

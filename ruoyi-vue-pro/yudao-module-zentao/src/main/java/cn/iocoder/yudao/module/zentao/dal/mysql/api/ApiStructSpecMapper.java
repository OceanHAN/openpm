package cn.iocoder.yudao.module.zentao.dal.mysql.api;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiStructSpecDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据结构版本内容 Mapper。
 *
 * <h3>关联键是 {@code name}，不是 id</h3>
 * 禅道 {@code getStructListByRelease}（{@code model.php:512}）的 join 是
 * {@code object.name = spec.name} —— 版本表里根本没有结构编号。
 * 所以这里查版本也一律按 {@code name}，把这份「缺陷」如实保留；
 * 顺带一条约束：同一 {@code name} 在两个库里会互相看见版本。
 *
 * <p>本表没有 {@code deleted} 列（物理删除），所以不继承 BaseDO。
 */
@Mapper
public interface ApiStructSpecMapper extends BaseMapperX<ApiStructSpecDO> {

    /** 某个结构的全部版本（按 name 关联），版本号倒序 */
    default List<ApiStructSpecDO> selectListByName(String name) {
        LambdaQueryWrapperX<ApiStructSpecDO> wrapper = new LambdaQueryWrapperX<ApiStructSpecDO>()
                .eq(ApiStructSpecDO::getName, name);
        wrapper.orderByDesc(ApiStructSpecDO::getVersion);
        return selectList(wrapper);
    }

    /** 取某个 name 的指定版本（禅道按 release 回溯结构时用） */
    default ApiStructSpecDO selectByNameAndVersion(String name, Integer version) {
        return selectOne(new LambdaQueryWrapperX<ApiStructSpecDO>()
                .eq(ApiStructSpecDO::getName, name)
                .eq(ApiStructSpecDO::getVersion, version));
    }

    /** 该结构名已有几个版本 */
    @Select("SELECT COUNT(*) FROM zt_apistruct_spec WHERE name = #{name}")
    int countByName(@Param("name") String name);

}

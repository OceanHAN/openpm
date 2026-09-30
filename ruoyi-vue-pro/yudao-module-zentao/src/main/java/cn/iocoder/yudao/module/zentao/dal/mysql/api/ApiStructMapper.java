package cn.iocoder.yudao.module.zentao.dal.mysql.api;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiStructDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 接口数据结构 Mapper。
 *
 * <p>本表**有 {@code deleted} 列**，所以继承的查询都会自动带 {@code deleted = 0}，
 * 删除也不需要额外的保护 SQL —— 真正的保护在 Service 层（被接口/结构引用时拒绝删除）。
 */
@Mapper
public interface ApiStructMapper extends BaseMapperX<ApiStructDO> {

    /** 结构分页：禅道 {@code getStructByQuery} 只按 lib 过滤 + deleted=0（{@code model.php:475}） */
    default PageResult<ApiStructDO> selectPage(ApiStructPageReqVO reqVO) {
        LambdaQueryWrapperX<ApiStructDO> wrapper = new LambdaQueryWrapperX<ApiStructDO>()
                .eqIfPresent(ApiStructDO::getLib, reqVO.getLib())
                .likeIfPresent(ApiStructDO::getName, reqVO.getName());
        // 禅道 struct 页的默认排序是 id_desc
        wrapper.orderByDesc(ApiStructDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /** 某库下的全部结构（发布时打快照、以及扫引用关系时用） */
    default List<ApiStructDO> selectListByLib(Long lib) {
        LambdaQueryWrapperX<ApiStructDO> wrapper = new LambdaQueryWrapperX<ApiStructDO>()
                .eqIfPresent(ApiStructDO::getLib, lib);
        wrapper.orderByAsc(ApiStructDO::getId);
        return selectList(wrapper);
    }

    /** 全部未删除的结构（扫「结构引用了哪个接口」时用） */
    default List<ApiStructDO> selectAllList() {
        LambdaQueryWrapperX<ApiStructDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.orderByAsc(ApiStructDO::getId);
        return selectList(wrapper);
    }

    /**
     * 按编号批量取结构行，**包含已逻辑删除的** —— 按发布版本回溯时，
     * 快照里冻结的结构可能已被删除，而禅道 {@code getStructListByRelease} 用的是
     * {@code zt_apistruct object left join zt_apistruct_spec spec}，没有 {@code deleted} 条件。
     */
    @Select("<script>SELECT * FROM zt_apistruct WHERE id IN "
            + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<ApiStructDO> selectListByIdsIgnoreDeleted(@Param("ids") Collection<Long> ids);

}

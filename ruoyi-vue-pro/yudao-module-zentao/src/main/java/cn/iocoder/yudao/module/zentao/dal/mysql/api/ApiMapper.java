package cn.iocoder.yudao.module.zentao.dal.mysql.api;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 接口文档 Mapper。
 *
 * <h3>为什么有两条 @Select 是「不带 deleted」的</h3>
 * 禅道新建/编辑接口时的唯一性检查是 {@code check('title','unique', "lib = x AND module = y")}、
 * {@code check('path','unique', "lib = x AND module = y AND method = 'GET'")}（{@code model.php:99-100/:146-147}）。
 * 这两条 SQL **不会**自动带上 {@code deleted = 0} —— 也就是说「被删掉的接口名/路径仍然占位」。
 * 与 {@code zt_company.name}、{@code zt_entry.code} 是同一个坑，所以这里用原生 SQL 明确不带过滤
 * （原生 SQL 不会被 {@code @TableLogic} 改写，这是它唯一的用处）。
 *
 * <p>注意 {@code LambdaQueryWrapperX} 只覆写了一部分方法，继承来的方法会把表达式类型退回基类，
 * 所以一律「先建 wrapper、再逐条语句调用」。
 */
@Mapper
public interface ApiMapper extends BaseMapperX<ApiDO> {

    /**
     * 接口分页（当前值链路）。
     *
     * @param moduleIds 目录过滤展开后的「自己 + 全部子孙」编号；为 null 表示不按目录过滤
     */
    default PageResult<ApiDO> selectPage(ApiPageReqVO reqVO, Collection<Long> moduleIds) {
        LambdaQueryWrapperX<ApiDO> wrapper = new LambdaQueryWrapperX<ApiDO>()
                .eqIfPresent(ApiDO::getLib, reqVO.getLib())
                .inIfPresent(ApiDO::getModule, moduleIds)
                .likeIfPresent(ApiDO::getTitle, reqVO.getTitle())
                .likeIfPresent(ApiDO::getPath, reqVO.getPath())
                .eqIfPresent(ApiDO::getMethod, reqVO.getMethod())
                .eqIfPresent(ApiDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ApiDO::getOwner, reqVO.getOwner());
        // 禅道 getListByModuleID 的分支没有 orderBy（就是插入顺序），这里显式写 id asc 保证分页稳定
        wrapper.orderByAsc(ApiDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /**
     * 某库全部接口的「编号 + 版本号」—— 发布时打快照用（{@code model.php:50}）。
     *
     * <p>只 select 两列，snap 里也只存这两列：发布冻结的是**版本号**，内容仍在 spec 表里。
     */
    default List<ApiDO> selectIdAndVersionByLib(Long lib) {
        LambdaQueryWrapperX<ApiDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.select(ApiDO::getId, ApiDO::getVersion);
        wrapper.eq(ApiDO::getLib, lib);
        wrapper.orderByAsc(ApiDO::getId);
        return selectList(wrapper);
    }

    /**
     * 按编号批量取接口行，**包含已逻辑删除的**。
     *
     * <p>用途：按发布版本浏览时，snap 里冻结的接口可能已被删除，而禅道
     * {@code getApiListByRelease}（{@code model.php:375}）是 {@code api left join apispec}，
     * 没有 {@code deleted} 条件 —— 已删除的接口在历史版本里**照样能看**。
     */
    @Select("<script>SELECT * FROM zt_api WHERE id IN "
            + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<ApiDO> selectListByIdsIgnoreDeleted(@Param("ids") Collection<Long> ids);

    /**
     * 按名称找同 (lib, module) 下的接口编号，**包含已逻辑删除的记录**（禅道 unique 口径）。
     */
    @Select("SELECT id FROM zt_api WHERE lib = #{lib} AND module = #{module} AND title = #{title} "
            + "ORDER BY id ASC LIMIT 1")
    Long selectIdByTitleIgnoreDeleted(@Param("lib") Long lib, @Param("module") Long module,
                                      @Param("title") String title);

    /**
     * 按路径找同 (lib, module, method) 下的接口编号，**包含已逻辑删除的记录**（禅道 unique 口径）。
     */
    @Select("SELECT id FROM zt_api WHERE lib = #{lib} AND module = #{module} AND method = #{method} "
            + "AND path = #{path} ORDER BY id ASC LIMIT 1")
    Long selectIdByPathIgnoreDeleted(@Param("lib") Long lib, @Param("module") Long module,
                                     @Param("method") String method, @Param("path") String path);

}

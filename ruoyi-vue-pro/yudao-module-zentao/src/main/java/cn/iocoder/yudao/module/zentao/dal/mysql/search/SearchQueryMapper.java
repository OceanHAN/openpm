package cn.iocoder.yudao.module.zentao.dal.mysql.search;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.search.vo.SearchQueryPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.search.SearchQueryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 保存的查询 Mapper。
 *
 * <p>注意 {@code LambdaQueryWrapperX} 只覆写了一部分方法，所以一律「先建 wrapper、再逐条语句调用」。
 * 这张表**没有 deleted 列**（DO 不继承 BaseDO），所以查出来的就是全部（禅道也是物理删除）。
 */
@Mapper
public interface SearchQueryMapper extends BaseMapperX<SearchQueryDO> {

    default PageResult<SearchQueryDO> selectPage(SearchQueryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SearchQueryDO>()
                .eqIfPresent(SearchQueryDO::getAccount, reqVO.getAccount())
                .eqIfPresent(SearchQueryDO::getModule, reqVO.getModule())
                .likeIfPresent(SearchQueryDO::getTitle, reqVO.getTitle())
                .eqIfPresent(SearchQueryDO::getShortcut, reqVO.getShortcut())
                .orderByDesc(SearchQueryDO::getId));
    }

    /** 某人在某模块下的查询（含公共查询）——禅道 buildQuery/ajaxGetQuery 的口径 */
    default List<SearchQueryDO> selectListByAccountAndModule(String account, String module) {
        LambdaQueryWrapperX<SearchQueryDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eq(SearchQueryDO::getModule, module);
        wrapper.and(true, inner -> inner.eq(SearchQueryDO::getAccount, account).or().eq(SearchQueryDO::getCommon, 1));
        wrapper.orderByDesc(SearchQueryDO::getShortcut);
        wrapper.orderByDesc(SearchQueryDO::getId);
        return selectList(wrapper);
    }

    /** 快捷方式（列表页页签上显示的那几个） */
    default List<SearchQueryDO> selectShortcuts(String account, String module) {
        LambdaQueryWrapperX<SearchQueryDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eq(SearchQueryDO::getModule, module);
        wrapper.eq(SearchQueryDO::getShortcut, 1);
        wrapper.and(true, inner -> inner.eq(SearchQueryDO::getAccount, account).or().eq(SearchQueryDO::getCommon, 1));
        wrapper.orderByDesc(SearchQueryDO::getId);
        return selectList(wrapper);
    }

}

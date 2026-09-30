package cn.iocoder.yudao.module.zentao.dal.mysql.search;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.search.SearchDictDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 搜索词典 Mapper（拼音首字母码表）。
 *
 * <p>这张表没有 deleted 列，DO 不继承 BaseDO。
 */
@Mapper
public interface SearchDictMapper extends BaseMapperX<SearchDictDO> {

    default List<SearchDictDO> selectListByKeys(List<Integer> keys) {
        LambdaQueryWrapperX<SearchDictDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.in(SearchDictDO::getKey, keys);
        return selectList(wrapper);
    }

}

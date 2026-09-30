package cn.iocoder.yudao.module.zentao.dal.mysql.bi;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bi.DataViewDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * BI 数据视图 Mapper
 */
@Mapper
public interface DataViewMapper extends BaseMapperX<DataViewDO> {

    default PageResult<DataViewDO> selectPage(String name, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<DataViewDO>()
                .likeIfPresent(DataViewDO::getName, name)
                .orderByDesc(DataViewDO::getId));
    }

    default List<DataViewDO> selectAllList() {
        return selectList(new LambdaQueryWrapperX<DataViewDO>().orderByDesc(DataViewDO::getId));
    }

    default DataViewDO selectByCode(String code) {
        return selectOne(DataViewDO::getCode, code);
    }

}

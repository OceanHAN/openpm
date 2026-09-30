package cn.iocoder.yudao.module.zentao.dal.mysql.bi;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bi.ChartDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * BI 图表 Mapper
 */
@Mapper
public interface ChartMapper extends BaseMapperX<ChartDO> {

    default PageResult<ChartDO> selectPage(String name, String type, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ChartDO>()
                .likeIfPresent(ChartDO::getName, name)
                .eqIfPresent(ChartDO::getType, type)
                .orderByDesc(ChartDO::getId));
    }

    default List<ChartDO> selectAllList() {
        return selectList(new LambdaQueryWrapperX<ChartDO>().orderByDesc(ChartDO::getId));
    }

}

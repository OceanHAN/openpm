package cn.iocoder.yudao.module.zentao.dal.mysql.metric;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.metric.MetricDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 度量项定义 Mapper
 */
@Mapper
public interface MetricMapper extends BaseMapperX<MetricDO> {

    default PageResult<MetricDO> selectPage(MetricPageReqVO reqVO, Collection<String> codes) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MetricDO>()
                .eqIfPresent(MetricDO::getPurpose, reqVO.getPurpose())
                .eqIfPresent(MetricDO::getScope, reqVO.getScope())
                .eqIfPresent(MetricDO::getObject, reqVO.getObject())
                .eqIfPresent(MetricDO::getStage, reqVO.getStage())
                .inIfPresent(MetricDO::getCode, codes)
                .and(org.springframework.util.StringUtils.hasText(reqVO.getKeyword()), w -> w
                        .like(MetricDO::getName, reqVO.getKeyword())
                        .or().like(MetricDO::getCode, reqVO.getKeyword())
                        .or().like(MetricDO::getAlias, reqVO.getKeyword()))
                .orderByAsc(MetricDO::getOrder)
                .orderByAsc(MetricDO::getId));
    }

    default List<MetricDO> selectListByFilter(String purpose, String scope, String object) {
        return selectList(new LambdaQueryWrapperX<MetricDO>()
                .eqIfPresent(MetricDO::getPurpose, purpose)
                .eqIfPresent(MetricDO::getScope, scope)
                .eqIfPresent(MetricDO::getObject, object)
                .orderByAsc(MetricDO::getOrder)
                .orderByAsc(MetricDO::getId));
    }

    default MetricDO selectByCode(String code) {
        return selectOne(MetricDO::getCode, code);
    }

    default Long countByCodeIn(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return 0L;
        }
        return selectCount(new LambdaQueryWrapperX<MetricDO>().in(MetricDO::getCode, codes));
    }

    default List<MetricDO> selectListByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<MetricDO>()
                .in(MetricDO::getCode, codes)
                .orderByAsc(MetricDO::getOrder)
                .orderByAsc(MetricDO::getId));
    }

}

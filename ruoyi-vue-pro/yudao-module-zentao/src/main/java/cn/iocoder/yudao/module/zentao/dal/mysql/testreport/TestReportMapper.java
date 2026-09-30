package cn.iocoder.yudao.module.zentao.dal.mysql.testreport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.TestReportDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 测试报告 Mapper
 */
@Mapper
public interface TestReportMapper extends BaseMapperX<TestReportDO> {

    default PageResult<TestReportDO> selectPage(TestReportPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TestReportDO>()
                .eqIfPresent(TestReportDO::getProduct, reqVO.getProduct())
                .eqIfPresent(TestReportDO::getProject, reqVO.getProject())
                .eqIfPresent(TestReportDO::getExecution, reqVO.getExecution())
                .likeIfPresent(TestReportDO::getTitle, reqVO.getTitle())
                .orderByDesc(TestReportDO::getId));
    }

    default List<TestReportDO> selectListByProduct(Long product) {
        return selectList(new LambdaQueryWrapperX<TestReportDO>()
                .eq(TestReportDO::getProduct, product)
                .orderByDesc(TestReportDO::getId));
    }

}

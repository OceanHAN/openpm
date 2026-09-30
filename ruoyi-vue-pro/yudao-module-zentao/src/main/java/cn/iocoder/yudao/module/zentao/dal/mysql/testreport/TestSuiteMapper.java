package cn.iocoder.yudao.module.zentao.dal.mysql.testreport;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuitePageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.TestSuiteDO;
import cn.iocoder.yudao.module.zentao.enums.testreport.TestSuiteTypeEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用例集 Mapper
 */
@Mapper
public interface TestSuiteMapper extends BaseMapperX<TestSuiteDO> {

    default PageResult<TestSuiteDO> selectPage(TestSuitePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TestSuiteDO>()
                .eqIfPresent(TestSuiteDO::getProduct, reqVO.getProduct())
                .eqIfPresent(TestSuiteDO::getProject, reqVO.getProject())
                .eqIfPresent(TestSuiteDO::getType, reqVO.getType())
                .likeIfPresent(TestSuiteDO::getName, reqVO.getName())
                // ★ 用例库与用例集共用 zt_testsuite：用例库是 (product=0, type='library')。
                //   这里必须把库排除掉，否则用例库会出现在用例集列表里（共用表强制加区分条件，坑位 #12）。
                .ne(TestSuiteDO::getType, TestSuiteTypeEnum.LIBRARY.getType())
                .orderByDesc(TestSuiteDO::getId));
    }

    default List<TestSuiteDO> selectListByProduct(Long product) {
        return selectList(new LambdaQueryWrapperX<TestSuiteDO>()
                .eq(TestSuiteDO::getProduct, product)
                .ne(TestSuiteDO::getType, TestSuiteTypeEnum.LIBRARY.getType())
                .orderByAsc(TestSuiteDO::getOrder)
                .orderByDesc(TestSuiteDO::getId));
    }

    /**
     * 用例库分页：用例库 = (product=0, type='library')。顺序按禅道 caselib/getList 的 order_desc, id_desc
     */
    default PageResult<TestSuiteDO> selectLibPage(PageParam pageParam, String name) {
        return selectPage(pageParam, new LambdaQueryWrapperX<TestSuiteDO>()
                .eq(TestSuiteDO::getProduct, 0L)
                .eq(TestSuiteDO::getType, TestSuiteTypeEnum.LIBRARY.getType())
                .likeIfPresent(TestSuiteDO::getName, name)
                .orderByDesc(TestSuiteDO::getOrder)
                .orderByDesc(TestSuiteDO::getId));
    }

    /**
     * 全部用例库（下拉用）
     */
    default List<TestSuiteDO> selectLibList() {
        return selectList(new LambdaQueryWrapperX<TestSuiteDO>()
                .eq(TestSuiteDO::getProduct, 0L)
                .eq(TestSuiteDO::getType, TestSuiteTypeEnum.LIBRARY.getType())
                .orderByDesc(TestSuiteDO::getOrder)
                .orderByDesc(TestSuiteDO::getId));
    }

}

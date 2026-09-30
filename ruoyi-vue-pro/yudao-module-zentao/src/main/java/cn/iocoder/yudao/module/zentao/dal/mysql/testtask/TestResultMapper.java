package cn.iocoder.yudao.module.zentao.dal.mysql.testtask;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestResultDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 测试执行结果 Mapper
 */
@Mapper
public interface TestResultMapper extends BaseMapperX<TestResultDO> {

    /**
     * 某条执行记录的历史结果，最新在前
     */
    default List<TestResultDO> selectListByRun(Long run) {
        return selectList(new LambdaQueryWrapperX<TestResultDO>()
                .eq(TestResultDO::getRun, run)
                .orderByDesc(TestResultDO::getId));
    }

    default List<TestResultDO> selectListByCase(Long caseId) {
        return selectList(new LambdaQueryWrapperX<TestResultDO>()
                .eq(TestResultDO::getCaseId, caseId)
                .orderByDesc(TestResultDO::getId));
    }

    /**
     * 移除测试单里的用例时，连带清掉它的执行历史（否则历史会指向一条不存在的 run）
     */
    @Delete("DELETE FROM zt_testresult WHERE run = #{run}")
    int deleteByRunPhysical(@Param("run") Long run);

}

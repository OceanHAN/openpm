package cn.iocoder.yudao.module.zentao.dal.mysql.testreport;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.SuiteCaseDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用例集与用例的关联 Mapper
 *
 * <p>表上没有 {@code deleted} 列，删除一律物理删。
 */
@Mapper
public interface SuiteCaseMapper extends BaseMapperX<SuiteCaseDO> {

    default List<SuiteCaseDO> selectListBySuite(Long suite) {
        return selectList(new LambdaQueryWrapperX<SuiteCaseDO>()
                .eq(SuiteCaseDO::getSuite, suite)
                .orderByAsc(SuiteCaseDO::getId));
    }

    default SuiteCaseDO selectBySuiteAndCase(Long suite, Long caseId) {
        return selectOne(new LambdaQueryWrapperX<SuiteCaseDO>()
                .eq(SuiteCaseDO::getSuite, suite)
                .eq(SuiteCaseDO::getCaseId, caseId));
    }

    default List<SuiteCaseDO> selectListByCase(Long caseId) {
        return selectList(new LambdaQueryWrapperX<SuiteCaseDO>()
                .eq(SuiteCaseDO::getCaseId, caseId));
    }

    /**
     * 已经关联过了就只更新版本（幂等）—— 依赖 UNIQUE(suite, case)，
     * 不能用 REPLACE（那会换掉主键、丢失 create_time）
     */
    @Update("UPDATE zt_suitecase SET caseVersion = #{caseVersion}, update_time = NOW() "
            + "WHERE suite = #{suite} AND `case` = #{caseId}")
    int updateCaseVersion(@Param("suite") Long suite, @Param("caseId") Long caseId,
                          @Param("caseVersion") Integer caseVersion);

    @Delete("DELETE FROM zt_suitecase WHERE suite = #{suite} AND `case` = #{caseId}")
    int deleteBySuiteAndCase(@Param("suite") Long suite, @Param("caseId") Long caseId);

    @Delete("DELETE FROM zt_suitecase WHERE suite = #{suite}")
    int deleteBySuitePhysical(@Param("suite") Long suite);

}

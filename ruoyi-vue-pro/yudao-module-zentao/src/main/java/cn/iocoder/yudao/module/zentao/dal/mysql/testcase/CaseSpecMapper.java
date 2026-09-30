package cn.iocoder.yudao.module.zentao.dal.mysql.testcase;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseSpecDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用例版本快照 Mapper
 */
@Mapper
public interface CaseSpecMapper extends BaseMapperX<CaseSpecDO> {

    default CaseSpecDO selectByCaseAndVersion(Long caseId, Integer version) {
        return selectOne(new LambdaQueryWrapperX<CaseSpecDO>()
                .eq(CaseSpecDO::getCaseId, caseId)
                .eq(CaseSpecDO::getVersion, version));
    }

    default List<CaseSpecDO> selectListByCase(Long caseId) {
        return selectList(new LambdaQueryWrapperX<CaseSpecDO>()
                .eq(CaseSpecDO::getCaseId, caseId)
                .orderByDesc(CaseSpecDO::getVersion));
    }

    /**
     * 物理删除某个版本（表上有 UNIQUE(case, version)，逻辑删除会占住版本号）
     */
    @Delete("DELETE FROM zt_casespec WHERE `case` = #{caseId} AND version = #{version}")
    int deleteByCaseAndVersionPhysical(@Param("caseId") Long caseId, @Param("version") Integer version);

    @Delete("DELETE FROM zt_casespec WHERE `case` = #{caseId}")
    int deleteByCasePhysical(@Param("caseId") Long caseId);

}

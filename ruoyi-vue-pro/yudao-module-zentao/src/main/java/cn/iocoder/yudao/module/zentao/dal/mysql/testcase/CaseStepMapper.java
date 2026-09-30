package cn.iocoder.yudao.module.zentao.dal.mysql.testcase;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseStepDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用例步骤 Mapper
 *
 * <p>这张表没有 {@code deleted} 列，所以**删除一律走物理删除**（原生 DELETE）——
 * 也正因为如此，它不会像 zt_storyspec 那样出现「逻辑删除占着唯一键」的问题。
 */
@Mapper
public interface CaseStepMapper extends BaseMapperX<CaseStepDO> {

    /**
     * 某个用例某个版本的步骤，按 id 升序（插入顺序即展示顺序）
     */
    default List<CaseStepDO> selectListByCaseAndVersion(Long caseId, Integer version) {
        return selectList(new LambdaQueryWrapperX<CaseStepDO>()
                .eq(CaseStepDO::getCaseId, caseId)
                .eq(CaseStepDO::getVersion, version)
                .orderByAsc(CaseStepDO::getId));
    }

    /**
     * 物理删除某个用例的全部步骤（删用例时调用）
     */
    @Delete("DELETE FROM zt_casestep WHERE `case` = #{caseId}")
    int deleteByCasePhysical(@Param("caseId") Long caseId);

    /**
     * 物理删除某个用例的某个版本（版本回滚/清理时调用）
     */
    @Delete("DELETE FROM zt_casestep WHERE `case` = #{caseId} AND version = #{version}")
    int deleteByCaseAndVersionPhysical(@Param("caseId") Long caseId, @Param("version") Integer version);

}

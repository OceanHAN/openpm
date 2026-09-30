package cn.iocoder.yudao.module.zentao.dal.mysql.bug;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 缺陷 Mapper
 */
@Mapper
public interface BugMapper extends BaseMapperX<BugDO> {

    default PageResult<BugDO> selectPage(BugPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BugDO>()
                .eqIfPresent(BugDO::getProduct, reqVO.getProduct())
                .eqIfPresent(BugDO::getProject, reqVO.getProject())
                .eqIfPresent(BugDO::getExecution, reqVO.getExecution())
                .eqIfPresent(BugDO::getBranch, reqVO.getBranch())
                .eqIfPresent(BugDO::getModule, reqVO.getModule())
                .inIfPresent(BugDO::getModule, reqVO.getModuleIds())
                .eqIfPresent(BugDO::getStory, reqVO.getStory())
                .eqIfPresent(BugDO::getStatus, reqVO.getStatus())
                .eqIfPresent(BugDO::getSeverity, reqVO.getSeverity())
                .eqIfPresent(BugDO::getPri, reqVO.getPri())
                .eqIfPresent(BugDO::getType, reqVO.getType())
                .eqIfPresent(BugDO::getAssignedTo, reqVO.getAssignedTo())
                .eqIfPresent(BugDO::getResolvedBy, reqVO.getResolvedBy())
                .likeIfPresent(BugDO::getTitle, reqVO.getTitle())
                .orderByDesc(BugDO::getId));
    }

    /**
     * 某个产品下的 Bug（构建关联 Bug 的候选用）
     */
    default List<BugDO> selectListByProduct(Long product) {
        return selectList(new LambdaQueryWrapperX<BugDO>()
                .eq(BugDO::getProduct, product)
                .orderByDesc(BugDO::getId));
    }

    /**
     * 某条用例跑出来的缺陷（来源用例）
     */
    default List<BugDO> selectListByCase(Long caseId) {
        return selectList(new LambdaQueryWrapperX<BugDO>()
                .eq(BugDO::getCaseId, caseId)
                .orderByDesc(BugDO::getId));
    }

    /**
     * 某个测试单跑出来的缺陷
     */
    default List<BugDO> selectListByTestTask(Long testtask) {
        return selectList(new LambdaQueryWrapperX<BugDO>()
                .eq(BugDO::getTesttask, testtask)
                .orderByDesc(BugDO::getId));
    }

    default List<BugDO> selectListByStory(Long story) {
        return selectList(new LambdaQueryWrapperX<BugDO>()
                .eq(BugDO::getStory, story)
                .orderByDesc(BugDO::getId));
    }

}

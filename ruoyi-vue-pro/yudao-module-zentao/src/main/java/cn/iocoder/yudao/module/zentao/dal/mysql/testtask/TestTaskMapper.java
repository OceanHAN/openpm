package cn.iocoder.yudao.module.zentao.dal.mysql.testtask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 测试单 Mapper
 */
@Mapper
public interface TestTaskMapper extends BaseMapperX<TestTaskDO> {

    default PageResult<TestTaskDO> selectPage(TestTaskPageReqVO reqVO) {
        LambdaQueryWrapperX<TestTaskDO> wrapper = new LambdaQueryWrapperX<TestTaskDO>()
                .eqIfPresent(TestTaskDO::getProduct, reqVO.getProduct())
                .eqIfPresent(TestTaskDO::getProject, reqVO.getProject())
                .eqIfPresent(TestTaskDO::getExecution, reqVO.getExecution())
                .eqIfPresent(TestTaskDO::getBuild, reqVO.getBuild())
                .eqIfPresent(TestTaskDO::getOwner, reqVO.getOwner())
                .eqIfPresent(TestTaskDO::getStatus, reqVO.getStatus())
                .likeIfPresent(TestTaskDO::getName, reqVO.getName());
        // 「我参与的测试单」：负责人或创建人命中（成员表在禅道里是 zt_team，本实现的测试单没有成员列表）
        if (org.springframework.util.StringUtils.hasText(reqVO.getMember())) {
            wrapper.and(w -> w.eq(TestTaskDO::getOwner, reqVO.getMember())
                    .or().eq(TestTaskDO::getCreatedBy, reqVO.getMember()));
        }
        // type 是逗号列表，只能 FIND_IN_SET
        if (reqVO.getType() != null && !reqVO.getType().isEmpty()) {
            wrapper.apply("FIND_IN_SET({0}, type)", reqVO.getType());
        }
        wrapper.orderByDesc(TestTaskDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /** 我负责的测试单（未删除，最新在前）——「我的日历」用 */
    default List<TestTaskDO> selectListByOwner(String owner) {
        return selectList(new LambdaQueryWrapperX<TestTaskDO>()
                .eq(TestTaskDO::getOwner, owner)
                .orderByDesc(TestTaskDO::getId));
    }

    default List<TestTaskDO> selectListByProduct(Long product) {
        return selectList(new LambdaQueryWrapperX<TestTaskDO>()
                .eq(TestTaskDO::getProduct, product)
                .orderByDesc(TestTaskDO::getId));
    }

    default List<TestTaskDO> selectListByExecution(Long execution) {
        return selectList(new LambdaQueryWrapperX<TestTaskDO>()
                .eq(TestTaskDO::getExecution, execution)
                .orderByDesc(TestTaskDO::getId));
    }

}

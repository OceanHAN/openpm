package cn.iocoder.yudao.module.zentao.dal.mysql.todo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.todo.TodoDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 待办 Mapper
 */
@Mapper
public interface TodoMapper extends BaseMapperX<TodoDO> {

    /**
     * 管理视角的分页：按归属账号/指派人/状态/类型/优先级/日期区间过滤
     */
    default PageResult<TodoDO> selectPage(TodoPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TodoDO>()
                .eqIfPresent(TodoDO::getAccount, reqVO.getAccount())
                .eqIfPresent(TodoDO::getAssignedTo, reqVO.getAssignedTo())
                .eqIfPresent(TodoDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TodoDO::getType, reqVO.getType())
                .eqIfPresent(TodoDO::getPri, reqVO.getPri())
                .eqIfPresent(TodoDO::getPrivateFlag, reqVO.getPrivateFlag())
                .likeIfPresent(TodoDO::getName, reqVO.getName())
                .betweenIfPresent(TodoDO::getDate, reqVO.getDate())
                // 禅道默认排序：date, status, begin
                .orderByAsc(TodoDO::getDate)
                .orderByAsc(TodoDO::getStatus)
                .orderByAsc(TodoDO::getBegin)
                .orderByDesc(TodoDO::getId));
    }

    /**
     * 「我的待办」：禅道的条件是
     * {@code assignedTo = 我 OR finishedBy = 我 OR closedBy = 我}
     * （module/todo/tao.php#getListBy），不是按 account 查。
     *
     * @param account   当前账号
     * @param status    状态；undone 表示「未完成」（不含 done/closed）
     * @param beginDate 起始日期，可空
     * @param endDate   结束日期，可空
     * @param cycle     是否只要周期待办
     */
    default List<TodoDO> selectMyList(String account, String status, LocalDate beginDate, LocalDate endDate,
                                      boolean cycle,
                                      boolean assignedToOther) {
        LambdaQueryWrapperX<TodoDO> wrapper = new LambdaQueryWrapperX<>();
        if (assignedToOther) {
            // 我指派给别人的：account = 我 且 assignedTo 不是我（也不能是空的）
            wrapper.eq(TodoDO::getAccount, account)
                    .isNotNull(TodoDO::getAssignedTo)
                    .ne(TodoDO::getAssignedTo, "")
                    .ne(TodoDO::getAssignedTo, account);
        } else {
            wrapper.and(w -> w.eq(TodoDO::getAssignedTo, account)
                    .or().eq(TodoDO::getFinishedBy, account)
                    .or().eq(TodoDO::getClosedBy, account));
        }
        wrapper.geIfPresent(TodoDO::getDate, beginDate)
                .leIfPresent(TodoDO::getDate, endDate)
                .eq(TodoDO::getCycle, cycle ? 1 : 0);
        if ("undone".equals(status)) {
            wrapper.notIn(TodoDO::getStatus, "done", "closed");
        } else if (status != null && !"all".equals(status)) {
            wrapper.eq(TodoDO::getStatus, status);
        }
        wrapper.orderByAsc(TodoDO::getDate)
                .orderByAsc(TodoDO::getStatus)
                .orderByAsc(TodoDO::getBegin)
                .orderByDesc(TodoDO::getId);
        return selectList(wrapper);
    }

    /**
     * 某人某天的待办（禅道「导入到今天」时用来判断哪些还没完成）
     */
    default List<TodoDO> selectListByAccount(String account, String status) {
        LambdaQueryWrapperX<TodoDO> wrapper = new LambdaQueryWrapperX<TodoDO>()
                .eq(TodoDO::getAccount, account);
        // undone 是聚合状态，不能直接当 status 值去等值匹配（那样一条都查不到）
        if ("undone".equals(status)) {
            wrapper.notIn(TodoDO::getStatus, "done", "closed");
        } else {
            wrapper.eqIfPresent(TodoDO::getStatus, status);
        }
        return selectList(wrapper.orderByAsc(TodoDO::getDate).orderByAsc(TodoDO::getId));
    }

}

package cn.iocoder.yudao.module.zentao.dal.mysql.action;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.action.ActionDO;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

import java.util.List;

/**
 * 操作日志 Mapper
 */
@Mapper
public interface ActionMapper extends BaseMapperX<ActionDO> {

    /**
     * 某人的最近动态（「我的地盘」的那条时间线），最新在前
     */
    default List<ActionDO> selectListByActor(String actor, int limit) {
        return selectList(new LambdaQueryWrapperX<ActionDO>()
                .eq(ActionDO::getActor, actor)
                .orderByDesc(ActionDO::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    /**
     * 按对象取操作时间线，最新在前
     *
     * @param objectType 对象类型，如 story
     * @param objectID   对象编号
     */
    default List<ActionDO> selectListByObject(String objectType, Long objectID) {
        return selectList(new LambdaQueryWrapperX<ActionDO>()
                .eq(ActionDO::getObjectType, objectType)
                .eq(ActionDO::getObjectID, objectID)
                .orderByDesc(ActionDO::getId));
    }

    /**
     * 回收站：所有「删除」动作，且没有被隐藏（{@code extra <> 'beHidden'}）。
     *
     * <p>禅道用 {@code extra='canUndelete'} 标记「可还原的删除」，本实现把所有逻辑删除都当成可还原的
     * （是否真的能还原由 ActionObjectMap 白名单 + 对象当前状态决定），{@code extra} 只用来记「已隐藏」。
     */
    default PageResult<ActionDO> selectTrashPage(String objectType, String actor, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ActionDO>()
                .eq(ActionDO::getAction, ActionTypeEnum.DELETED.getAction())
                .eqIfPresent(ActionDO::getObjectType, objectType)
                .eqIfPresent(ActionDO::getActor, actor)
                .and(w -> w.isNull(ActionDO::getExtra).or().ne(ActionDO::getExtra, HIDDEN_FLAG))
                .orderByDesc(ActionDO::getId));
    }

    /**
     * 动态（feed）：按人 / 周期 / 产品 / 项目 / 执行过滤，最新在前
     */
    default List<ActionDO> selectDynamic(String actor, LocalDateTime begin, String product, Long project,
                                         Long execution, int limit) {
        return selectList(new LambdaQueryWrapperX<ActionDO>()
                .eqIfPresent(ActionDO::getActor, actor)
                .geIfPresent(ActionDO::getDate, begin)
                .likeIfPresent(ActionDO::getProduct, product)
                .eqIfPresent(ActionDO::getProject, project)
                .eqIfPresent(ActionDO::getExecution, execution)
                .orderByDesc(ActionDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    /** 回收站里隐藏一条（只改 extra，对象仍然是删除状态） */
    @Update("UPDATE zt_action SET extra = '" + HIDDEN_FLAG + "' WHERE id = #{id}")
    int hideAction(@Param("id") Long id);

    /** 隐藏全部可还原的删除记录 */
    @Update("UPDATE zt_action SET extra = '" + HIDDEN_FLAG + "' "
            + "WHERE action = 'deleted' AND (extra IS NULL OR extra <> '" + HIDDEN_FLAG + "')")
    int hideAllActions();

    /** 改备注内容（只有备注动作才允许改，权限在 Service 里校验） */
    @Update("UPDATE zt_action SET comment = #{comment} WHERE id = #{id}")
    int updateComment(@Param("id") Long id, @Param("comment") String comment);

    /** 回收站里的「已隐藏」标记值 */
    String HIDDEN_FLAG = "beHidden";

}

package cn.iocoder.yudao.module.zentao.dal.mysql.score;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.score.vo.ScorePageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.score.ScoreDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分流水 Mapper。
 *
 * <p>注意 {@code LambdaQueryWrapperX} 只覆写了一部分方法，继承来的方法会把表达式类型退回
 * {@code LambdaQueryWrapper}，所以这里一律「先建 wrapper、再逐条语句调用」（坑位 #1 的变体）。
 */
@Mapper
public interface ScoreMapper extends BaseMapperX<ScoreDO> {

    /** 某人积分明细：禅道 {@code getListByAccount} 的排序是 {@code time_desc, id_desc} */
    default PageResult<ScoreDO> selectPage(ScorePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ScoreDO>()
                .eqIfPresent(ScoreDO::getAccount, reqVO.getAccount())
                .eqIfPresent(ScoreDO::getModule, reqVO.getModule())
                .eqIfPresent(ScoreDO::getMethod, reqVO.getMethod())
                .betweenIfPresent(ScoreDO::getTime, reqVO.getTime())
                .orderByDesc(ScoreDO::getTime)
                .orderByDesc(ScoreDO::getId));
    }

    /** 总分 = SUM(score)（禅道冗余在 zt_user.score 上，本实现不迁 zt_user，所以按流水求和） */
    @Select("SELECT COALESCE(SUM(`score`), 0) FROM zt_score WHERE account = #{account}")
    Integer selectTotalByAccount(@Param("account") String account);

    /**
     * 同一动作在**这一天**里已经计过几次（禅道 saveScore 的 hour 分支）。
     *
     * <p>禅道配的 hour 都是 24（或 0），实现上直接按「当天 00:00:00 ~ 23:59:59」数条数 ——
     * 也就是「这个小时内」实际退化成了「这一天」，本实现照抄这个口径。
     */
    @Select("SELECT COUNT(*) FROM zt_score WHERE account = #{account} AND module = #{module} AND method = #{method} "
            + "AND `time` BETWEEN #{begin} AND #{end}")
    int countByDay(@Param("account") String account, @Param("module") String module,
                   @Param("method") String method, @Param("begin") LocalDateTime begin,
                   @Param("end") LocalDateTime end);

    /** 同一动作累计计过几次（禅道 saveScore 的 times 分支：hour 为空时按全量数） */
    @Select("SELECT COUNT(*) FROM zt_score WHERE account = #{account} AND module = #{module} AND method = #{method}")
    int countAll(@Param("account") String account, @Param("module") String module, @Param("method") String method);

    /** 某人某天（含）之后的流水，用于「昨日积分」那类统计 */
    @Select("SELECT COALESCE(SUM(`score`), 0) FROM zt_score WHERE account = #{account} "
            + "AND `time` BETWEEN #{begin} AND #{end}")
    Integer sumBetween(@Param("account") String account, @Param("begin") LocalDateTime begin,
                       @Param("end") LocalDateTime end);

    /** 取某时间点之前的最新一条（用来算总分快照；没有就返回空） */
    @Select("SELECT COALESCE(SUM(`score`), 0) FROM zt_score WHERE account = #{account} AND id < #{beforeId}")
    Integer sumBeforeId(@Param("account") String account, @Param("beforeId") Long beforeId);

    default List<ScoreDO> selectListByAccount(String account) {
        LambdaQueryWrapperX<ScoreDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eq(ScoreDO::getAccount, account);
        wrapper.orderByDesc(ScoreDO::getTime);
        wrapper.orderByDesc(ScoreDO::getId);
        return selectList(wrapper);
    }

}

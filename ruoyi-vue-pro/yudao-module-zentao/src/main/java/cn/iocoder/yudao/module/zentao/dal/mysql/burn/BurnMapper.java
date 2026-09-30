package cn.iocoder.yudao.module.zentao.dal.mysql.burn;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.burn.BurnDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 燃尽图快照 Mapper。
 *
 * <p>本表没有 {@code deleted} 列（禅道原样），所以不能用 {@code BaseMapperX.deleteById} 那套逻辑删除，
 * 当天那一行用 {@code REPLACE} 覆盖（唯一键 {@code execution + date + task}，禅道也是这么写的）。
 *
 * <p>另外几个「汇总任务工时」的查询是跨表的聚合，直接用原生 SQL（与报表模块同一做法）。
 */
@Mapper
public interface BurnMapper extends BaseMapperX<BurnDO> {

    /** 某个执行某天的快照（禅道 getBurnByExecution） */
    default BurnDO selectByExecutionAndDate(Long execution, LocalDate date) {
        return selectOne(new LambdaQueryWrapperX<BurnDO>()
                .eq(BurnDO::getExecution, execution)
                .eq(BurnDO::getDate, date)
                .last("LIMIT 1"));
    }

    /** 某个执行某段时间的快照（按日期升序，画图用） */
    default List<BurnDO> selectListByRange(Long execution, LocalDate begin, LocalDate end) {
        return selectList(new LambdaQueryWrapperX<BurnDO>()
                .eq(BurnDO::getExecution, execution)
                .ge(BurnDO::getDate, begin)
                .le(BurnDO::getDate, end)
                .orderByAsc(BurnDO::getDate));
    }

    /**
     * 覆盖当天快照（禅道 {@code $this->dao->replace(TABLE_BURN)}）。
     * 用 REPLACE 而不是 update：唯一键冲突时删旧行插新行，语义就是「这一天以最新汇总为准」。
     */
    @Insert("REPLACE INTO zt_burn (execution, product, task, `date`, estimate, `left`, consumed, storyPoint) "
            + "VALUES (#{execution}, 0, 0, #{date}, #{estimate}, #{left}, #{consumed}, #{storyPoint})")
    int replaceBurn(@Param("execution") Long execution, @Param("date") LocalDate date,
                    @Param("estimate") BigDecimal estimate, @Param("left") BigDecimal left,
                    @Param("consumed") BigDecimal consumed, @Param("storyPoint") BigDecimal storyPoint);

    /**
     * 执行下任务的工时汇总（禅道 {@code executionTao::fetchBurnData} 的第一条）。
     *
     * <p>禅道还过滤了 {@code isParent = 0}（父子任务里只算叶子），本实现的 {@code zt_task}
     * **没有 isParent 列**（父子任务还没做），所以这条条件省掉；其余一致：不含已删除、不含已取消。
     */
    @Select("SELECT COALESCE(SUM(estimate), 0) AS estimate, COALESCE(SUM(`left`), 0) AS `left`, "
            + "COALESCE(SUM(consumed), 0) AS consumed FROM zt_task "
            + "WHERE deleted = 0 AND execution = #{execution} AND status <> 'cancel'")
    Map<String, Object> selectTaskSum(@Param("execution") Long execution);

    /** 已关闭任务「还挂着的剩余工时」——燃尽图里要把它从剩余里扣掉（禅道 closedLefts） */
    @Select("SELECT COALESCE(SUM(`left`), 0) FROM zt_task "
            + "WHERE deleted = 0 AND execution = #{execution} AND status = 'closed'")
    BigDecimal selectClosedLeft(@Param("execution") Long execution);

    /** 已完成/已关闭任务的预计工时——从「原计划」里扣掉（禅道 finishedEstimates） */
    @Select("SELECT COALESCE(SUM(estimate), 0) FROM zt_task "
            + "WHERE deleted = 0 AND execution = #{execution} AND status IN ('done', 'closed')")
    BigDecimal selectFinishedEstimate(@Param("execution") Long execution);

    /**
     * 需求规模合计（禅道 storyPoints）：本执行关联的、**还没做完**的需求的预计规模。
     * 口径与禅道一致：未关闭 + 阶段是 wait/planned/projected/developing + 不是父需求。
     */
    @Select("SELECT COALESCE(SUM(s.estimate), 0) FROM zt_projectstory ps "
            + "JOIN zt_story s ON s.id = ps.story AND s.deleted = 0 "
            + "WHERE ps.project = #{execution} AND s.status <> 'closed' "
            + "AND s.stage IN ('wait', 'planned', 'projected', 'developing') AND s.isParent = 0")
    BigDecimal selectStoryPoint(@Param("execution") Long execution);

}

package cn.iocoder.yudao.module.zentao.dal.mysql.testtask;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testtask.TestRunDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 测试单用例执行 Mapper
 */
@Mapper
public interface TestRunMapper extends BaseMapperX<TestRunDO> {

    default TestRunDO selectByTaskAndCase(Long task, Long caseId) {
        return selectOne(new LambdaQueryWrapperX<TestRunDO>()
                .eq(TestRunDO::getTask, task)
                .eq(TestRunDO::getCaseId, caseId));
    }

    default List<TestRunDO> selectListByTask(Long task) {
        return selectList(new LambdaQueryWrapperX<TestRunDO>()
                .eq(TestRunDO::getTask, task)
                .orderByAsc(TestRunDO::getId));
    }

    /**
     * 某用例被哪些测试单排过（禅道的 getRelatedTestTasks）
     */
    default List<TestRunDO> selectListByCase(Long caseId) {
        return selectList(new LambdaQueryWrapperX<TestRunDO>()
                .eq(TestRunDO::getCaseId, caseId)
                .orderByDesc(TestRunDO::getId));
    }

    /**
     * 把用例排进测试单（存在就更新版本/指派，**保留已有执行结果**）。
     *
     * <p>禅道这里用的是 {@code REPLACE INTO}，而表上有 {@code UNIQUE(task, case)} ——
     * REPLACE 是「先删后插」，于是「把同一个用例再排一次」会把它之前的执行结果清空。
     * 本实现改成 UPDATE，只动 caseVersion/assignedTo，结果字段原样保留（有意偏离，见 SQL 注释）。
     */
    @Update("UPDATE zt_testrun SET caseVersion = #{caseVersion}, assignedTo = #{assignedTo}, "
            + "update_time = NOW() WHERE task = #{task} AND `case` = #{caseId}")
    int updateCaseVersionAndAssignee(@Param("task") Long task, @Param("caseId") Long caseId,
                                     @Param("caseVersion") Integer caseVersion,
                                     @Param("assignedTo") String assignedTo);

    /**
     * 回写执行结果。
     *
     * <p>用原生 UPDATE 而不是 updateById：这些字段里 {@code lastRunDate} 之类
     * 可能出现 null（清空），而 updateById 会跳过 null（README 第 19 条坑）。
     */
    @Update("UPDATE zt_testrun SET status = #{status}, lastRunResult = #{result}, "
            + "lastRunner = #{runner}, lastRunDate = #{date}, update_time = NOW() WHERE id = #{id}")
    int updateRunResult(@Param("id") Long id, @Param("status") String status,
                        @Param("result") String result, @Param("runner") String runner,
                        @Param("date") LocalDateTime date);

    /**
     * 指派执行人
     */
    @Update("UPDATE zt_testrun SET assignedTo = #{assignedTo}, update_time = NOW() WHERE id = #{id}")
    int updateAssignee(@Param("id") Long id, @Param("assignedTo") String assignedTo);

    /**
     * 移除测试单里的用例。这张表没有 deleted 列，只能物理删。
     */
    @Delete("DELETE FROM zt_testrun WHERE id = #{id}")
    int deleteByIdPhysical(@Param("id") Long id);

    @Delete("DELETE FROM zt_testrun WHERE task = #{task}")
    int deleteByTaskPhysical(@Param("task") Long task);

}

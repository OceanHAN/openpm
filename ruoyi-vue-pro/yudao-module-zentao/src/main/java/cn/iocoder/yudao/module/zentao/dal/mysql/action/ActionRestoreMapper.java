package cn.iocoder.yudao.module.zentao.dal.mysql.action;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

/**
 * 回收站专用 Mapper：按「对象类型」去对应的表里查名称、判断删除状态、还原。
 *
 * <p>表名与列名来自 {@code ActionObjectMap} 的白名单（写死在代码里，不接受外部输入），
 * 所以这里用 {@code ${}} 是安全的 —— 这是继 BI 的 {@code BiQueryMapper} 之后第二处，
 * 同样在测试里有「非法对象类型被拒绝」的断言。
 */
@Mapper
public interface ActionRestoreMapper {

    /** 查对象的名字与删除状态（表名/列名都来自白名单） */
    @Select("SELECT id, `${nameColumn}` AS objectName, deleted AS deletedFlag "
            + "FROM `${table}` WHERE id = #{id}")
    Map<String, Object> selectObject(@Param("table") String table,
                                     @Param("nameColumn") String nameColumn,
                                     @Param("id") Long id);

    /** 还原（逻辑删除置回 0） */
    @Update("UPDATE `${table}` SET deleted = b'0' WHERE id = #{id}")
    int restoreObject(@Param("table") String table, @Param("id") Long id);

}

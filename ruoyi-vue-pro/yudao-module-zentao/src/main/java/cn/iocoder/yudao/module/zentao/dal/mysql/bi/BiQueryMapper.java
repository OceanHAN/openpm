package cn.iocoder.yudao.module.zentao.dal.mysql.bi;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * BI 动态查询 Mapper。
 *
 * <p>数据视图的 SQL 是**运行时才知道**的，所以这里只能用 {@code ${}} 做字符串替换 ——
 * 这是全项目唯一一处。安全性由 {@link cn.iocoder.yudao.module.zentao.service.bi.SqlGuard}
 * 保证：单语句、必须 SELECT/WITH、关键字黑名单、只允许 {@code zt_*} 表、执行时再包一层 LIMIT。
 * 拼接前所有字段名都过 {@code validateIdentifier}（只允许字母/数字/下划线）。
 */
@Mapper
public interface BiQueryMapper {

    @Select("${sql}")
    List<Map<String, Object>> selectMaps(@Param("sql") String sql);

}

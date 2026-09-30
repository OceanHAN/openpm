package cn.iocoder.yudao.module.zentao.dal.dataobject.testreport;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用例集里的用例 DO
 *
 * <p>对应禅道 {@code zt_suitecase}。**这张表不继承 BaseDO**：禅道原表没有 {@code deleted} 列，
 * 移除用例就是物理删。
 *
 * <p><b>本实现比禅道多了一个唯一键 {@code (suite, case)}</b>：
 * 禅道用 {@code REPLACE INTO} 但没有唯一索引，同一个用例会被重复插进去。
 * 保留字：{@code case}。
 */
@TableName("zt_suitecase")
@Data
public class SuiteCaseDO {

    @TableId
    private Long id;

    private Long suite;

    private Long product;

    /**
     * 用例编号。case 是 MySQL 保留字
     */
    @TableField("`case`")
    private Long caseId;

    /**
     * 排进来时的用例版本。驼峰列名
     */
    @TableField("caseVersion")
    private Integer caseVersion;

    /**
     * 版本（禅道原字段）
     */
    private Integer version;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    private String creator;

    private String updater;

}

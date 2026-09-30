package cn.iocoder.yudao.module.zentao.dal.dataobject.testreport;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 测试用例集 DO
 *
 * <p>对应禅道 {@code zt_testsuite}。用例集是「可复用的用例集合」：
 * 把一批用例打包，排进测试单时一次选完，不用一条条勾。
 *
 * <p>它不产生任何执行数据，只是对既有用例的组织方式。
 * 保留字：{@code desc}、{@code order}。
 */
@TableName("zt_testsuite")
@KeySequence("zt_testsuite_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TestSuiteDO extends BaseDO {

    @TableId
    private Long id;

    private Long project;

    private Long product;

    private String name;

    /**
     * 描述。desc 是 MySQL 保留字
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 类型：public 公共 / private 私有
     */
    private String type;

    /**
     * 排序。order 是 MySQL 保留字
     */
    @TableField("`order`")
    private Integer order;

    /**
     * 创建人。驼峰列名
     */
    @TableField("addedBy")
    private String addedBy;

    /**
     * 创建时间。驼峰列名
     */
    @TableField("addedDate")
    private LocalDateTime addedDate;

    /**
     * 最后修改人。驼峰列名
     */
    @TableField("lastEditedBy")
    private String lastEditedBy;

    /**
     * 最后修改时间。驼峰列名
     */
    @TableField("lastEditedDate")
    private LocalDateTime lastEditedDate;

}

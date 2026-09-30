package cn.iocoder.yudao.module.zentao.dal.dataobject.api;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据结构版本内容（禅道 {@code zt_apistruct_spec}）。
 *
 * <h3>这张表暴露了禅道的一处真实设计缺陷</h3>
 * 它**既没有 struct id，也没有 lib**，只有 {@code name}；禅道读取时按
 * {@code object.name = spec.name} 关联（{@code model.php:512 getStructListByRelease}）。
 * 于是「两个库里有同名结构」会串版本 —— 建表时刻意**不加** {@code (name,version)} 唯一键，
 * 因为那会让第二个库建同名结构直接失败（照抄事实，但把风险写在这里）。
 *
 * <p>更新结构时禅道是**无条件追加** {@code version+1} 的新行
 * （{@code control.php:544} 直接 {@code $formData->version = $struct->version + 1}，
 * 不像接口那样先比较字段），所以 {@code user} 结构的 v1/v2 两条行都在，
 * 而 {@code zt_apistruct} 头部只留 v2 的 attribute。
 *
 * <p><b>没有 {@code deleted} 列</b> → 不继承 BaseDO（继承会拼出 {@code deleted = 0} 直接报错）。
 * {@code desc} 是保留字；驼峰列名要显式声明。
 */
@TableName("zt_apistruct_spec")
@Data
public class ApiStructSpecDO {

    @TableId
    private Long id;

    /** 结构名 —— 禅道唯一的关联键（注意不是结构编号） */
    private String name;

    /** 结构类型 */
    private String type;

    /** 结构说明。列名 desc 是保留字 */
    @TableField("`desc`")
    private String desc;

    /** 字段树 JSON */
    private String attribute;

    /** 版本号 */
    private Integer version;

    /** 该版本的写者（驼峰列名） */
    @TableField("addedBy")
    private String addedBy;

    /** 该版本的写入时间（驼峰列名） */
    @TableField("addedDate")
    private LocalDateTime addedDate;

}

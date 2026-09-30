package cn.iocoder.yudao.module.zentao.dal.dataobject.api;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 可复用数据结构（禅道 {@code zt_apistruct}）。
 *
 * <h3>{@code attribute} 是可嵌套的字段树</h3>
 * 形如：
 * <pre>
 * [{ "field":"id", "paramsType":"int", "required":"", "desc":"用户编号",
 *    "structType":"json", "sub":1, "key":"kwoq04srulxevt93a3h", "children":[] }]
 * </pre>
 * 来自 {@code module/api/js/common.ui.js} 的 {@code processRow()/buildNestedParams()}：
 * {@code children} 是子字段，{@code paramsType} 既可以是内置类型
 * （object/array/string/date/datetime/boolean/int/long/float/double/decimal，以及自定义的 file/ref），
 * 也可以是**同库另一个结构的编号** —— 这就是「结构引用结构」，
 * 见 {@code control.php:1031 getTypeOptions()} 把结构名/编号塞进类型下拉。
 *
 * <p>删除结构时的「被引用保护」就是扫这个字段树（{@link cn.iocoder.yudao.module.zentao.service.api.ApiService}）；
 * 禅道本身**没有任何**删除前引用检查（{@code control.php:570 deleteStruct} 直接删），
 * 这一层保护是本实现有意加的，属于加固，见模块说明。
 *
 * <p>结构版本表 {@code zt_apistruct_spec} 只按 {@code name} 关联（禅道的真实设计缺陷），
 * 所以本表 {@code name} 是那套关联的唯一键。
 *
 * <p>{@code desc} 是保留字；驼峰列名要显式声明。本表有 {@code deleted} 列 → 继承 BaseDO。
 */
@TableName("zt_apistruct")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiStructDO extends BaseDO {

    @TableId
    private Long id;

    /** 所属接口库 */
    private Long lib;

    /** 结构名（{@code zt_apistruct_spec} 就按它关联版本） */
    private String name;

    /** 结构类型：formData / json / array / object（禅道 {$lang->struct->typeOptions}） */
    private String type;

    /** 结构说明（保留字） */
    @TableField("`desc`")
    private String desc;

    /** 当前版本号；编辑结构时无条件 +1（禅道 {@code control.php:544}） */
    private Integer version;

    /** 字段树 JSON */
    private String attribute;

    /** 创建人（驼峰列名） */
    @TableField("addedBy")
    private String addedBy;

    /** 创建时间（驼峰列名） */
    @TableField("addedDate")
    private LocalDateTime addedDate;

    /** 最后修改人（驼峰列名） */
    @TableField("editedBy")
    private String editedBy;

    /** 最后修改时间（驼峰列名） */
    @TableField("editedDate")
    private LocalDateTime editedDate;

}

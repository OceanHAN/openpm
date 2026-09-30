package cn.iocoder.yudao.module.zentao.dal.dataobject.search;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 搜索词典（禅道 {@code zt_searchdict}）。
 *
 * <p>两个小整数/字符列：{@code key} 是一个数字编码，{@code value} 是一位 ASCII 字符。
 * 禅道用它把中文按 Unicode 编码映射成拼音首字母，从而实现「按拼音搜中文」——
 * 表里是**码表**，不是业务数据。
 *
 * <p>本表没有 {@code deleted}/审计列（禅道原样），所以不继承 BaseDO。
 * 保留字：{@code key} 与 {@code value} 都要加反引号（后者在 JSqlParser 里是关键字，坑位 #11）。
 */
@TableName("zt_searchdict")
@Data
public class SearchDictDO {

    @TableId
    private Long id;

    /** 编码（对应汉字的 Unicode 高位） */
    @TableField("`key`")
    private Integer key;

    /** 拼音首字母（一位 ASCII） */
    @TableField("`value`")
    private String value;

}

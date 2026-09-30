package cn.iocoder.yudao.module.zentao.dal.dataobject.product;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 产品 DO
 *
 * 对应禅道 {@code zt_product}。产品是禅道的根对象：需求、计划、发布、缺陷都挂在产品下。
 *
 * <h3>与禅道的有意偏离</h3>
 * 禅道 zt_product 有 20 多个预计算计数器（draftStories / activeStories / totalStories /
 * unresolvedBugs / totalBugs ...），用于列表页快速展示。我们是**读时实时统计**而不是冗余存储，
 * 因为冗余字段需要靠业务代码在每个写路径维护，一旦漏改就会永久漂移；
 * 实时统计只多一次带索引的聚合查询。见 {@code ProductService#getProductStats}。
 *
 * 注意 MySQL 保留字：{@code desc} 和 {@code order} 必须加反引号；
 * {@code PO}/{@code QD}/{@code RD} 是禅道的原始大写列名。
 */
@TableName("zt_product")
@KeySequence("zt_product_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 所属项目集
     */
    private Long program;

    /**
     * 所属产品线
     */
    private Long line;

    /**
     * 产品名称
     */
    private String name;

    /**
     * 产品代号
     */
    private String code;

    /**
     * 类型：normal 正常 / branch 多分支 / platform 多平台
     */
    private String type;

    /**
     * 状态：normal 正常 / closed 结束
     */
    private String status;

    /**
     * 产品描述。列名 desc 是 MySQL 保留字
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 产品经理。禅道原始列名为大写 PO
     */
    @TableField("`PO`")
    private String PO;

    /**
     * 测试负责人
     */
    @TableField("`QD`")
    private String QD;

    /**
     * 研发负责人
     */
    @TableField("`RD`")
    private String RD;

    /**
     * 访问控制：open 公开 / private 私有
     */
    private String acl;

    /**
     * 创建人
     */
    @TableField("createdBy")
    private String createdBy;

    /**
     * 创建时间
     */
    @TableField("createdDate")
    private LocalDateTime createdDate;

    /**
     * 关闭时间
     */
    @TableField("closedDate")
    private LocalDateTime closedDate;

    /**
     * 排序。列名 order 是 MySQL 保留字
     */
    @TableField("`order`")
    private Integer order;

    /**
     * 视图
     */
    private String vision;

}

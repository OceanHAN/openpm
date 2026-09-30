package cn.iocoder.yudao.module.zentao.dal.dataobject.stage;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 阶段模板 DO
 *
 * <p>对应禅道 {@code zt_stage}。**注意它描述的是「流程模板」，不是某个项目里的阶段**：
 * <pre>
 *   zt_stage                        → 模板：workflowGroup=1 下有 需求/设计/开发/测试/发布
 *   zt_project (type='stage')       → 实例：某个瀑布项目按模板生成的实际阶段
 * </pre>
 * 项目通过 {@code zt_project.workflowGroup} 记住自己用的是哪一套模板。
 *
 * <p>{@code percent} 在禅道表里是 varchar（可能是空串），本实现保持列类型一致，
 * Java 里按字符串存取、比较时转 BigDecimal（见 {@code StageService#getTotalPercent}）。
 */
@TableName("zt_stage")
@KeySequence("zt_stage_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StageDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 所属流程模板组。一个瀑布流程一套阶段
     */
    @TableField("workflowGroup")
    private Long workflowGroup;

    /**
     * 阶段名称
     */
    private String name;

    /**
     * 工作量占比（%）。列类型是 varchar，空串表示未设置
     */
    private String percent;

    /**
     * 阶段类型。枚举 {@link cn.iocoder.yudao.module.zentao.enums.stage.StageTypeEnum}
     */
    private String type;

    /**
     * 适用的项目流程类型：waterfall / waterfallplus / ipd。驼峰列名
     */
    @TableField("projectType")
    private String projectType;

    /**
     * 创建人。驼峰列名
     */
    @TableField("createdBy")
    private String createdBy;

    /**
     * 创建时间。驼峰列名
     */
    @TableField("createdDate")
    private LocalDateTime createdDate;

    /**
     * 最后修改人。驼峰列名
     */
    @TableField("editedBy")
    private String editedBy;

    /**
     * 最后修改时间。驼峰列名
     */
    @TableField("editedDate")
    private LocalDateTime editedDate;

    /**
     * 排序。列名 {@code order} 是保留字
     */
    @TableField("`order`")
    private Integer order;

}

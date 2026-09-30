package cn.iocoder.yudao.module.zentao.dal.dataobject.project;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.zentao.enums.project.ProjectModelEnum;
import cn.iocoder.yudao.module.zentao.enums.project.ProjectStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目 DO
 *
 * 对应禅道 {@code zt_project}。项目是主干链的中间层：
 * <pre>
 *   产品(product)     管需求池
 *   项目(project)     管交付范围与周期        ← 本表
 *   执行(execution)   项目下的迭代/阶段
 *   任务(task)        挂在执行上
 * </pre>
 *
 * <h3>两个级联开关</h3>
 * <ul>
 *   <li>{@code multiple=0}（单执行项目）：关闭项目时连带关闭它唯一的执行</li>
 *   <li>{@code hasProduct=0}（未关联产品）：关闭项目时连带关闭自动创建的产品</li>
 * </ul>
 * 这两条是禅道 {@code project::close()} 的真实行为，在 Service 层实现。
 *
 * <h3>工时</h3>
 * 与任务同构：{@code estimate / left / consumed}。理想情况下应由项目下所有任务汇总而来，
 * 本实现先支持手工登记，汇总逻辑留待任务-项目联动时补。
 *
 * 注意 MySQL 保留字：{@code desc}、{@code left}、{@code order}。
 */
@TableName("zt_project")
@KeySequence("zt_project_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDO extends BaseDO {

    @TableId
    private Long id;

    // ==================== 层级与模型 ====================

    /**
     * 所属项目。
     *
     * 【执行专用】执行（迭代/阶段/看板）与项目共用本表，由 {@code type} 区分：
     * {@code type='project'} 时本字段为 0；执行时本字段指向所属项目编号。
     * 层级字段的含义按角色区分（三种角色共用本表，见 ExecutionTypeEnum）：
     * <ul>
     *   <li>type='program'：parent = 上级项目集</li>
     *   <li>type='project'：parent = **所属项目集**（不是「父项目」，项目之间是平级的）</li>
     *   <li>type='sprint/stage/kanban'：parent=0，归属看 project 列</li>
     * </ul>
     * path 是逗号格式且包含自己（,9001,1,），grade 从 1 开始。
     */
    private Long project;

    private Long parent;
    private String path;
    private Integer grade;
    @TableField("isTpl")
    private Integer isTpl;
    /**
     * 模型
     *
     * 枚举 {@link ProjectModelEnum}
     */
    private String model;
    private String type;
    private String category;
    private String lifetime;

    // ==================== 内容 ====================

    private String name;
    private String code;
    /**
     * 项目描述。列名 desc 是 MySQL 保留字
     */
    @TableField("`desc`")
    private String desc;
    /**
     * 交付物。列名 output 是 JSqlParser 的保留字（T-SQL OUTPUT 子句），
     * 不加反引号会让 MyBatis-Plus 的 SQL 解析器直接失败
     */
    @TableField("`output`")
    private String output;

    // ==================== 产品与执行关联 ====================

    /**
     * 是否关联产品。为 0 时关闭项目会连带关闭自动创建的产品
     */
    @TableField("hasProduct")
    private Integer hasProduct;
    /**
     * 是否多执行。为 0 时关闭项目会连带关闭其执行
     */
    private Integer multiple;
    @TableField("storyType")
    private String storyType;

    // ==================== 预算 ====================

    private BigDecimal budget;
    @TableField("budgetUnit")
    private String budgetUnit;

    // ==================== 周期 ====================

    /**
     * 计划开始。列名 begin 是 SQL 块关键字，需反引号
     */
    @TableField("`begin`")
    private LocalDate begin;
    /**
     * 计划结束。列名 end 是 SQL 块关键字，需反引号
     */
    @TableField("`end`")
    private LocalDate end;
    @TableField("realBegan")
    private LocalDate realBegan;
    @TableField("realEnd")
    private LocalDate realEnd;
    private Integer days;

    // ==================== 阶段（瀑布流程） ====================

    /**
     * 阶段工作量占比。瀑布项目按模板生成阶段时，从阶段模板复制过来（可按项目调整）
     */
    private BigDecimal percent;

    /**
     * 使用的流程模板编号（瀑布项目 &gt; 0；scrum 项目为 0）。驼峰列名
     */
    @TableField("workflowGroup")
    private Long workflowGroup;

    /**
     * 阶段属性（禅道原字段）
     */
    private String attribute;

    // ==================== 状态机 ====================

    /**
     * 状态
     *
     * 枚举 {@link ProjectStatusEnum}
     */
    private String status;
    private Integer pri;
    private Integer milestone;

    // ==================== 工时 ====================

    private BigDecimal estimate;
    /**
     * 剩余工时。列名 left 是 MySQL 保留字
     */
    @TableField("`left`")
    private BigDecimal left;
    private BigDecimal consumed;
    private BigDecimal progress;

    // ==================== 角色与团队 ====================

    @TableField("`PO`")
    private String PO;
    @TableField("`PM`")
    private String PM;
    @TableField("`QD`")
    private String QD;
    @TableField("`RD`")
    private String RD;
    private String team;
    @TableField("teamCount")
    private Integer teamCount;
    private String acl;
    private String whitelist;

    // ==================== 生命周期 ====================

    @TableField("openedBy")
    private String openedBy;
    @TableField("openedDate")
    private LocalDateTime openedDate;
    @TableField("lastEditedBy")
    private String lastEditedBy;
    @TableField("lastEditedDate")
    private LocalDateTime lastEditedDate;
    @TableField("closedBy")
    private String closedBy;
    @TableField("closedDate")
    private LocalDateTime closedDate;
    @TableField("closedReason")
    private String closedReason;
    @TableField("canceledBy")
    private String canceledBy;
    @TableField("canceledDate")
    private LocalDateTime canceledDate;
    @TableField("suspendedDate")
    private LocalDateTime suspendedDate;
    @TableField("activatedDate")
    private LocalDateTime activatedDate;
    /**
     * 排序。列名 order 是 MySQL 保留字，需反引号
     */
    @TableField("`order`")
    private Integer order;

}

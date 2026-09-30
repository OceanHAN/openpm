package cn.iocoder.yudao.module.zentao.dal.dataobject.story;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.zentao.enums.story.StoryCategoryEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryStageEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求 DO
 *
 * 表名与字段名刻意对齐禅道的 {@code zt_story}，这样：
 * 1. 迁移期可以直接从禅道库导数据，字段一一对应，无需中间映射层；
 * 2. 禅道已有的业务规则、报表、习惯叫法可以原样沿用；
 * 3. 出问题时可以左右对照 PHP 实现，降低理解成本。
 *
 * 注意：因为禅道用驼峰列名（openedBy / assignedTo ...），而 MyBatis-Plus 默认会把
 * Java 字段的驼峰转成下划线，所以这些字段必须用 {@link TableField} 显式声明列名，
 * 否则会报 "Unknown column 'opened_by'"。
 *
 * 说明：禅道把「需求描述/验收标准」拆在 {@code zt_storyspec} 表（按 version 存多份历史），
 * 本切片为控制范围先内联到主表，后续做需求变更历史时再拆表。
 *
 * @see <a href="https://github.com/easysoft/zentaopms">禅道源码</a>
 */
@TableName("zt_story")
@KeySequence("zt_story_seq") // Oracle、PostgreSQL、Kingbase、DB2、H2 等数据库的主键自增；MySQL 可不写
@Data
@EqualsAndHashCode(callSuper = true)
public class StoryDO extends BaseDO {

    /**
     * 需求编号
     */
    @TableId
    private Long id;

    // ==================== 归属 ====================

    /**
     * 所属产品
     */
    private Long product;
    /**
     * 所属模块
     */
    private Long module;
    /**
     * 所属计划，多个用逗号分隔（禅道原字段为 text）
     */
    private String plan;
    /**
     * 所属分支
     */
    private Long branch;

    // ==================== 内容 ====================

    /**
     * 需求标题
     */
    private String title;
    /**
     * 关键词
     */
    private String keywords;

    // 注意：需求描述(spec)与验收标准(verify)不在本表。
    // 禅道把它们放在 zt_storyspec，按 (story, version) 保存每一版，
    // 以支持需求变更历史。读取时由 StoryService 按当前版本叠加进来（见下方 @TableField(exist = false)）。
    // 见 StorySpecDO。

    /**
     * 需求描述（非持久化字段，读取时从 zt_storyspec 按版本叠加）
     */
    @TableField(exist = false)
    private String spec;

    /**
     * 验收标准（非持久化字段，读取时从 zt_storyspec 按版本叠加）
     */
    @TableField(exist = false)
    private String verify;

    /**
     * 需求类型
     */
    private String type;
    /**
     * 需求分类
     *
     * 枚举 {@link StoryCategoryEnum}
     */
    private String category;
    /**
     * 优先级，1~4，值越小优先级越高
     */
    private Integer pri;
    /**
     * 预计工时
     */
    private BigDecimal estimate;

    // ==================== 状态机 ====================

    /**
     * 状态
     *
     * 枚举 {@link StoryStatusEnum}
     */
    private String status;
    /**
     * 研发阶段
     *
     * 枚举 {@link StoryStageEnum}
     */
    private String stage;
    /**
     * 版本号。禅道每次变更需求会 +1，用于追溯历史版本
     */
    private Integer version;

    // ==================== 来源 ====================

    /**
     * 需求来源
     */
    /**
     * 父需求（0 = 一级需求）
     */
    private Long parent;

    /**
     * 分解时父需求的版本（冻结）。驼峰列名
     */
    @TableField("parentVersion")
    private Integer parentVersion;

    /**
     * 顶层祖先（便于一次捞出整棵树）。root 是 MySQL 8 保留字
     */
    @TableField("`root`")
    private Long root;

    /**
     * 树路径，逗号包起来且包含自己
     */
    private String path;

    /**
     * 层级，从 1 开始
     */
    private Integer grade;

    /**
     * 是否已分解（有子需求）。驼峰列名
     */
    @TableField("isParent")
    private Integer isParent;
    /**
     * 来源备注
     */
    @TableField("sourceNote")
    private String sourceNote;
    /**
     * 由哪个 Bug 转化而来
     */
    @TableField("fromBug")
    private Long fromBug;

    // ==================== 生命周期（字段命名对齐禅道） ====================

    /**
     * 创建人
     */
    @TableField("openedBy")
    private String openedBy;
    /**
     * 创建时间
     */
    @TableField("openedDate")
    private LocalDateTime openedDate;
    /**
     * 指派给
     */
    @TableField("assignedTo")
    private String assignedTo;
    /**
     * 指派时间
     */
    @TableField("assignedDate")
    private LocalDateTime assignedDate;
    /**
     * 关闭人
     */
    @TableField("closedBy")
    private String closedBy;
    /**
     * 关闭时间
     */
    @TableField("closedDate")
    private LocalDateTime closedDate;
    /**
     * 关闭原因：done(已完成) / duplicate(重复) / postponed(延期) / willnotdo(不做) / bydesign(设计如此)
     */
    @TableField("closedReason")
    private String closedReason;
    /**
     * 重复需求指向的需求编号，closedReason=duplicate 时必填
     */
    @TableField("duplicateStory")
    private Long duplicateStory;
    /**
     * 激活时间
     */
    @TableField("activatedDate")
    private LocalDateTime activatedDate;
    /**
     * 最后修改人
     */
    @TableField("lastEditedBy")
    private String lastEditedBy;
    /**
     * 最后修改时间
     */
    @TableField("lastEditedDate")
    private LocalDateTime lastEditedDate;
    /**
     * 已评审人，逗号分隔
     */
    @TableField("reviewedBy")
    private String reviewedBy;
    /**
     * 最后评审时间
     */
    @TableField("reviewedDate")
    private LocalDateTime reviewedDate;

}

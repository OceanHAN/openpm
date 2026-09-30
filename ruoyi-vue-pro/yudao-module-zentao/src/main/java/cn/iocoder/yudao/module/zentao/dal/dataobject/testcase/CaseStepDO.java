package cn.iocoder.yudao.module.zentao.dal.dataobject.testcase;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试用例步骤 DO
 *
 * <p>对应禅道 {@code zt_casestep}。这是本模块的「一对多子结构」：
 * 一个 {@code (case, version)} 下有 N 条步骤。
 *
 * <p>两个细节：
 * <ol>
 *   <li>{@code type=group} 是**步骤组**（只有描述、没有预期结果），
 *       {@code parent} 指向所属的组；最多三级。前端看到的
 *       {@code 1. / 1.1 / 1.1.1} 编号是**算出来的**，不落库</li>
 *   <li>这张表**不继承 BaseDO**：禅道原表没有 {@code deleted} 列。
 *       步骤是纯粹的子数据，删版本/删用例时整批物理删除 ——
 *       也正因为没有逻辑删除，它不会像 zt_storyspec 那样撞唯一键</li>
 * </ol>
 *
 * <p>保留字：{@code case}、{@code desc} 都要加反引号。
 */
@TableName("zt_casestep")
@Data
public class CaseStepDO {

    @TableId
    private Long id;

    /**
     * 所属步骤组（0 = 顶层）
     */
    private Long parent;

    /**
     * 用例编号。case 是 MySQL 保留字
     */
    @TableField("`case`")
    private Long caseId;

    /**
     * 所属用例版本
     */
    private Integer version;

    /**
     * step 步骤 / group 步骤组
     */
    private String type;

    /**
     * 步骤描述。desc 是 MySQL 保留字
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 预期结果
     */
    private String expect;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    private String creator;

    private String updater;

}

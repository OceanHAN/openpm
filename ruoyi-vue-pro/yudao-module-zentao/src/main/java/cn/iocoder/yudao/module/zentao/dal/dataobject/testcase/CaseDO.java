package cn.iocoder.yudao.module.zentao.dal.dataobject.testcase;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 测试用例 DO
 *
 * <p>对应禅道 {@code zt_case}。这是**第三条版本规则**，而且判据和前面两条都不一样：
 * <ul>
 *   <li>{@code zt_storyspec}（3.2）：正式变更才 version+1</li>
 *   <li>{@code zt_doccontent}（3.17）：正文变了才 version+1</li>
 *   <li>{@code zt_casespec}/{@code zt_casestep}（本模块）：<b>只有「步骤」变了才 version+1</b>，
 *       并且会把 {@code status} 打回 {@code wait}（待评审）</li>
 * </ul>
 * 所以「改标题一定要升版本」这种直觉在用例上是错的 —— 标题、前置条件、优先级、状态
 * 都是原地改，只有 {@code zt_casestep} 的内容变化才产生新版本。
 *
 * <p>{@code storyVersion} 是**冻结的需求版本**：关联需求时记下当时的版本，
 * 需求后来升版不会自动同步，而是让用例进入「待确认」（见 README 3.13 的同类思路）。
 *
 * <p>保留字：{@code order}。
 */
@TableName("zt_case")
@KeySequence("zt_case_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CaseDO extends BaseDO {

    @TableId
    private Long id;

    private Long product;

    /**
     * 所属用例库（0 = 不属于任何用例库）。
     *
     * <p>禅道里用例的归属是**二选一**：要么属于产品（{@code product>0, lib=0}），
     * 要么属于用例库（{@code product=0, lib>0}）。用例库本身是
     * {@code zt_testsuite} 里 {@code (product=0, type='library')} 的行。
     */
    private Long lib;

    /**
     * 分支/平台，0 = 主干
     */
    private Long branch;

    /**
     * 所属模块（通用树 zt_module，type=case）
     */
    private Long module;

    /**
     * 关联需求
     */
    private Long story;

    /**
     * 关联需求在关联那一刻的版本（冻结）。驼峰列名
     */
    @TableField("storyVersion")
    private Integer storyVersion;

    /**
     * 标题（按当前版本同步；读历史版本时用 zt_casespec 覆盖）
     */
    private String title;

    /**
     * 前置条件。驼峰列名
     */
    @TableField("precondition")
    private String precondition;

    private String keywords;

    /**
     * 优先级 1-4
     */
    private Integer pri;

    /**
     * 类型：unit/interface/feature/install/config/performance/security/other
     */
    private String type;

    /**
     * 适用测试环节，**逗号列表**（禅道可多选），过滤要用 FIND_IN_SET
     */
    private String stage;

    /**
     * 状态：wait 待评审 / normal 正常 / blocked 被阻塞 / investigate 研究中
     */
    private String status;

    /**
     * 最近执行结果。驼峰列名（由 testtask 回写）
     */
    @TableField("lastRunResult")
    private String lastRunResult;

    /**
     * 最近执行人。驼峰列名
     */
    @TableField("lastRunner")
    private String lastRunner;

    /**
     * 最近执行时间。驼峰列名
     */
    @TableField("lastRunDate")
    private LocalDateTime lastRunDate;

    /**
     * 当前版本号（只有步骤变了才 +1）
     */
    private Integer version;

    /**
     * 排序。order 是 MySQL 保留字
     */
    @TableField("`order`")
    private Integer order;

    /**
     * 排序（禅道原字段）
     */
    private Integer sort;

    /**
     * 创建人。驼峰列名
     */
    @TableField("openedBy")
    private String openedBy;

    /**
     * 创建时间。驼峰列名
     */
    @TableField("openedDate")
    private LocalDateTime openedDate;

    /**
     * 评审人。驼峰列名
     */
    @TableField("reviewedBy")
    private String reviewedBy;

    /**
     * 评审时间。驼峰列名
     */
    @TableField("reviewedDate")
    private LocalDate reviewedDate;

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

    /**
     * 来源缺陷。驼峰列名，0 = 非缺陷转来
     */
    @TableField("fromBug")
    private Long fromBug;

    /**
     * 来源产品用例编号（驼峰列名）。库内用例从产品用例导入时记下来源，
     * 0 = 在库里手工新建。禅道用它做「已导入」判重（{@code testcase/getCanImportCases}）。
     */
    @TableField("fromCaseID")
    private Long fromCaseID;

    /**
     * 导入时来源用例的版本（驼峰列名）。来源用例升版后比它大 → 库里这条「源用例已更新」。
     */
    @TableField("fromCaseVersion")
    private Integer fromCaseVersion;

}

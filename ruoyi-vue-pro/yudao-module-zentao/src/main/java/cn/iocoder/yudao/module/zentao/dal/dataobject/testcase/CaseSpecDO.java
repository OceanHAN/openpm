package cn.iocoder.yudao.module.zentao.dal.dataobject.testcase;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试用例版本快照 DO
 *
 * <p>对应禅道 {@code zt_casespec}，与 {@code zt_storyspec} 同构：
 * 头部（zt_case）只存「当前是第几版」，每一版的标题与前置条件按
 * {@code (case, version)} 存在这里。
 *
 * <p><b>注意步骤不在这张表里</b>：步骤单独一张 {@code zt_casestep}，也按 version 分组。
 * 于是「一个版本」的内容实际由两张表拼出来 —— 这正是本模块比 story/doc 麻烦的地方。
 *
 * <p>{@code case} 是 MySQL 保留字，必须加反引号。
 */
@TableName("zt_casespec")
@KeySequence("zt_casespec_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CaseSpecDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 用例编号。case 是 MySQL 保留字
     */
    @TableField("`case`")
    private Long caseId;

    private Integer version;

    private String title;

    /**
     * 前置条件。驼峰列名
     */
    @TableField("precondition")
    private String precondition;

    /**
     * 附件编号，逗号列表
     */
    private String files;

}

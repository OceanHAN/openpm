package cn.iocoder.yudao.module.zentao.dal.dataobject.doc;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文档库 DO
 *
 * <p>对应禅道 {@code zt_doclib}。文档库是「文档挂在哪个对象上」的答案：
 * <ul>
 *   <li>{@code type=product/project/execution}：跟着产品/项目/执行走的**主库**（{@code main=1}），
 *       不允许删除；建库时会把对象的编号抄到 {@code product}/{@code project}/{@code execution}</li>
 *   <li>{@code type=custom}：自定义库，必须挂在某个「团队空间」下（{@code parent} 指向空间）</li>
 * </ul>
 *
 * <p>{@code desc} / {@code order} 都是 MySQL 保留字，必须显式加反引号。
 */
@TableName("zt_doclib")
@KeySequence("zt_doclib_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class DocLibDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 库类型：product / project / execution / custom
     */
    private String type;

    /**
     * 父空间（custom 库挂在团队空间下）
     */
    private Long parent;

    private Long product;

    private Long project;

    private Long execution;

    /**
     * 库名称
     */
    private String name;

    /**
     * API 库的 baseUrl（保留字段）。驼峰列名 —— 不声明的话 MyBatis-Plus 会拼成 base_url
     */
    @TableField("baseUrl")
    private String baseUrl;

    /**
     * 权限：open 公开 / private 私有
     */
    private String acl;

    /**
     * 私有库可见角色，逗号列表。**groups 是 MySQL 8 的保留字**（GROUPS 用于窗口函数），
     * 不加反引号会直接语法错 —— 而且它藏在 order 后面，
     * 「拼 SELECT 试跑」的预检方式只会报出第一个保留字，很容易漏（见脚本注释）。
     */
    @TableField("`groups`")
    private String groups;

    /**
     * 私有库可见用户，逗号列表
     */
    private String users;

    /**
     * 是否内置主库（主库不允许删除）
     */
    private Integer main;

    /**
     * 库描述。desc 是 MySQL 保留字
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 排序。order 是 MySQL 保留字
     */
    @TableField("`order`")
    private Integer order;

    /**
     * 创建人。驼峰列名
     */
    @TableField("addedBy")
    private String addedBy;

    /**
     * 创建时间。驼峰列名
     */
    @TableField("addedDate")
    private LocalDateTime addedDate;

    /**
     * 是否归档
     */
    private Integer archived;

    /**
     * 库内默认排序。驼峰列名
     */
    @TableField("orderBy")
    private String orderBy;

}

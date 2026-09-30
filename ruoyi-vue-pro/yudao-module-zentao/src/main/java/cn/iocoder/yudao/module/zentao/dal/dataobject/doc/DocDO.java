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
 * 文档 DO
 *
 * <p>对应禅道 {@code zt_doc}。一张表两种对象：
 * <ul>
 *   <li>{@code type='chapter'}：**章节**，树的中间节点，没有正文（不写 zt_doccontent）</li>
 *   <li>其它类型（html/markdown/url/word/ppt/excel/attachment）：真正的文档，有版本内容</li>
 * </ul>
 * 所以「章节树」和「文档列表」查的是同一张表，靠 {@code type} 区分 —— 和
 * {@code zt_project} 用 type 区分项目/执行是同一类设计（README 第 12 条坑）。
 *
 * <p>{@code parent}/{@code path}/{@code grade} 组成章节树，规则与 {@code zt_module} 一致：
 * path 用逗号包起来、**包含自己**（一级 {@code ,1,}、二级 {@code ,1,2,}），
 * 这样前缀匹配就能一次取出整棵子树。
 *
 * <p>保留字：{@code order}（MySQL）、{@code from}（MySQL）都要加反引号。
 */
@TableName("zt_doc")
@KeySequence("zt_doc_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class DocDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 所属文档库
     */
    private Long lib;

    private Long product;

    private Long project;

    private Long execution;

    /**
     * 所属模块（通用树 zt_module，type=doc）
     */
    private Long module;

    /**
     * 标题（章节名或文档名）
     */
    private String title;

    /**
     * 关键词
     */
    private String keywords;

    /**
     * 类型：chapter / html / markdown / url / word / ppt / excel / attachment
     */
    private String type;

    /**
     * 状态：normal 已发布 / draft 草稿
     */
    private String status;

    /**
     * 父章节
     */
    private Long parent;

    /**
     * 章节树路径，逗号包起来且包含自己
     */
    private String path;

    /**
     * 层级，从 1 开始
     */
    private Integer grade;

    /**
     * 排序。order 是 MySQL 保留字
     */
    @TableField("`order`")
    private Integer order;

    /**
     * 浏览次数
     */
    private Integer views;

    /**
     * 收藏次数
     */
    private Integer collects;

    /**
     * 草稿内容（status=draft 时正文存这里）
     */
    private String draft;

    /**
     * 当前版本号；0 表示只有草稿、还没发布
     */
    private Integer version;

    /**
     * 复制来源文档。from 是 MySQL 保留字
     */
    @TableField("`from`")
    private Long from;

    /**
     * 复制来源版本。驼峰列名
     */
    @TableField("fromVersion")
    private Integer fromVersion;

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
     * 指派给。驼峰列名
     */
    @TableField("assignedTo")
    private String assignedTo;

    /**
     * 指派时间。驼峰列名
     */
    @TableField("assignedDate")
    private LocalDateTime assignedDate;

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
     * 权限：open 公开 / private 私有
     */
    private String acl;

    /**
     * 私有文档可见角色，逗号列表。**groups 是 MySQL 8 的保留字**，必须加反引号
     */
    @TableField("`groups`")
    private String groups;

    /**
     * 私有文档可见用户，逗号列表
     */
    private String users;

    /**
     * 视野（禅道原字段，保留）
     */
    private String vision;

}

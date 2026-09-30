package cn.iocoder.yudao.module.zentao.dal.dataobject.search;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 保存的查询（禅道 {@code zt_userquery}）。
 *
 * <h3>它是「保存搜索条件」的落地表</h3>
 * 禅道每个列表页的「搜索」都能把当前条件存下来、下次一键用，还能设为「快捷方式」放在页签上。
 * 一条记录 = 谁的（account）、哪个模块的（module）、叫什么名字（title）、
 * 表单长什么样（form，字段定义快照）、条件是什么（sql）、是不是快捷方式（shortcut）、是不是公共查询（common）。
 *
 * <h3>⚠️ 一处**必须**有意偏离：sql 列不存 SQL</h3>
 * 禅道把条件序列化成一段 **SQL 片段**（`t1.status = 'active'` 这种），存进 `sql` 列，
 * 列表页直接拼进 WHERE。Java 侧照搬等于把 SQL 注入的口子开到数据层，而且换个 ORM 就失效。
 * 本实现的做法是：
 * <pre>
 *   form —— 原样存「字段定义快照」（JSON），前端拿它回填搜索表单；
 *   sql  —— 列名保留（保持表结构与禅道一致），但存的是**结构化的条件 JSON**：
 *           [{"field":"status","op":"eq","value":"active"}, …]
 *           由调用方把它翻译成对应模块的查询 VO（本项目每个模块的查询条件都是强类型 VO）。
 * </pre>
 * 这条写进 README —— 迁移「保存查询」时最容易直接抄 SQL 串，抄完就是两个坑（注入 + 方言）。
 *
 * <p><b>没有 {@code deleted} 列</b>（禅道原样：保存的查询是物理删除的），所以**不继承 BaseDO** ——
 * 继承会让所有查询都带上 {@code deleted = 0}，直接报「Unknown column 'deleted'」。
 *
 * <p>保留字：{@code sql} 在 MySQL 里不是关键字，但 JSqlParser 会当关键字（坑位 #11/#20 那一类），
 * 所以照样加反引号。
 */
@TableName("zt_userquery")
@Data
public class SearchQueryDO {

    @TableId
    private Long id;

    /** 账号（谁的查询） */
    private String account;

    /** 模块（story/task/bug/…；禅道存的是模块名，前端按它回填对应页面的搜索表单） */
    private String module;

    /** 查询名称 */
    private String title;

    /** 表单定义快照（JSON） */
    private String form;

    /** **结构化条件 JSON**（不是 SQL —— 见类注释） */
    @TableField("`sql`")
    private String sql;

    /** 是否快捷方式：1 是（会显示在列表页页签上） */
    private Integer shortcut;

    /** 是否公共查询：1 是（所有人可见） */
    private Integer common;

}

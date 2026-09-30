package cn.iocoder.yudao.module.zentao.dal.dataobject.entry;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用接入（禅道 {@code zt_entry}）。
 *
 * <h3>它是干什么的</h3>
 * 第三方应用（OA、门户、移动端）带着 {@code code + token} 调禅道的接口，甚至**免密跳到某个账号**。
 * 校验链在 {@code common::checkEntry} 里，顺序是：
 * <pre>
 *   code 缺失 / token 缺失 → 404 EMPTY_ENTRY / 401
 *   查 code → 无此应用 → 404 EMPTY_ENTRY
 *   key 为空 → 401 EMPTY_KEY
 *   IP 不在白名单 → 403 IP_DENIED
 *   token 不对 → 401 INVALID_TOKEN
 *   非免密且未绑定账号 → 403 ACCOUNT_UNBOUND
 *   账号不存在/停用 → 406 INVALID_ACCOUNT
 *   （免密）直接建立登录态；否则记录 session 与调用日志
 * </pre>
 *
 * <p>保留字：{@code key} 是 MySQL 关键字、{@code desc} 也是（坑位 #10）。
 */
@TableName("zt_entry")
@Data
public class EntryDO extends BaseDO {

    @TableId
    private Long id;

    /** 应用名称 */
    private String name;

    /** 绑定的账号（免密登录用；freePasswd=1 时可为空） */
    private String account;

    /** 应用代号：字母或数字的组合，唯一 */
    private String code;

    /** 密钥（32 位） */
    @TableField("`key`")
    private String key;

    /** 是否免密：1 表示可以直接免密进入绑定账号 */
    @TableField("freePasswd")
    private Integer freePasswd;

    /** 允许的来源 IP：* / 精确 IP / 逗号列表 / a-b 区间 / 192.168.1.* 通配 */
    private String ip;

    /** 描述。列名 desc 是关键字 */
    @TableField("`desc`")
    private String desc;

    /** 最近一次调用的请求时间戳，用于防重放 */
    @TableField("calledTime")
    private Integer calledTime;

    /** 创建人 / 创建时间（禅道原样保留） */
    @TableField("createdBy")
    private String createdBy;

    @TableField("createdDate")
    private LocalDateTime createdDate;

    @TableField("editedBy")
    private String editedBy;

    @TableField("editedDate")
    private LocalDateTime editedDate;

}

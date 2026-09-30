package cn.iocoder.yudao.module.zentao.dal.dataobject.api;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口版本内容（禅道 {@code zt_apispec}）。
 *
 * <p>相当于 {@code zt_storyspec} / {@code zt_doccontent}：{@code (doc, version)} 一条，
 * 保存那一版的全部内容。禅道的两条写路径（{@code model.php}）：
 * <pre>
 *   create  → INSERT (doc, 1)
 *   update  → 先 DELETE (doc, version) 再 INSERT（version 可能不变，也可能 +1）
 * </pre>
 * 第二条等价于「原地重写当前版本」，所以历史版本的 spec 行是**只增不改**的。
 *
 * <p><b>本表没有 {@code deleted} 列</b>（禅道原样），所以**不继承 BaseDO** ——
 * 继承会让每条查询都带上 {@code deleted = 0}，直接报 {@code Unknown column 'deleted'}。
 * {@code create_time}/{@code creator} 等框架列只在建表时留了默认值，不映射到 Java 字段。
 *
 * <p>注意本表**没有 {@code lib}、没有 {@code product}、没有 {@code commonParams}、
 * 没有 {@code editedBy}/{@code editedDate}**：禅道 {@code getApiSpecByData()}（{@code model.php:1027}）
 * 就只写这些列。也就是说「哪一版属于哪个库」只能从 {@code zt_api} 反查，spec 自己没有库信息。
 *
 * <p>{@code desc} 是保留字；驼峰列名（{@code requestType} 等）都要显式声明。
 */
@TableName("zt_apispec")
@Data
public class ApiSpecDO {

    @TableId
    private Long id;

    /** 接口编号（{@code zt_api.id}）—— 列名是 doc 而不是 api，照抄禅道 */
    private Long doc;

    /** 当时的目录编号 */
    private Long module;

    /** 当时的接口名称 */
    private String title;

    /** 当时的请求路径 */
    private String path;

    /** 当时的协议 */
    private String protocol;

    /** 当时的请求方式 */
    private String method;

    /** 当时的请求格式（驼峰列名） */
    @TableField("requestType")
    private String requestType;

    /** 当时的响应格式（驼峰列名） */
    @TableField("responseType")
    private String responseType;

    /** 当时的开发状态 */
    private String status;

    /** 当时的负责人 */
    private String owner;

    /** 当时的接口说明（保留字） */
    @TableField("`desc`")
    private String desc;

    /** 版本号 */
    private Integer version;

    /** 当时的请求参数树 JSON */
    private String params;

    /** 当时的请求示例（驼峰列名） */
    @TableField("paramsExample")
    private String paramsExample;

    /** 当时的响应示例（驼峰列名） */
    @TableField("responseExample")
    private String responseExample;

    /** 当时的响应字段树 JSON */
    private String response;

    /** 该版本的写者（驼峰列名） */
    @TableField("addedBy")
    private String addedBy;

    /** 该版本的写入时间（驼峰列名） */
    @TableField("addedDate")
    private LocalDateTime addedDate;

}

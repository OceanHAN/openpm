package cn.iocoder.yudao.module.zentao.dal.dataobject.api;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 接口文档（禅道 {@code zt_api}）。
 *
 * <h3>它是「接口文档库」的头部表，不是「对外 REST 接口」</h3>
 * 禅道 {@code module/api} 是一套接口文档管理：按产品/项目/独立空间挂载接口库
 * （库是 {@code zt_doclib} 里 {@code type='api'} 的记录），库下是目录树（{@code zt_module}，
 * {@code type='api'}），目录下才是接口，接口旁边挂着可复用的数据结构，最后可以发布版本。
 *
 * <h3>头部 + 追加式快照：本项目第三条版本链</h3>
 * <pre>
 *   zt_api       头部：当前值 + 当前版本号，编辑时原地 UPDATE
 *   zt_apispec   快照：(doc, version) 一条，见 {@link ApiSpecDO}
 * </pre>
 * 与需求/文档两条版本链的差别在于**版本号的推进条件**：禅道 {@code model.php:139-140} 是
 * 「先做 {@code common::createChanges()}，真有变更才 {@code version = old + 1}」，
 * 而且对同一个 {@code (doc, version)} 是**先 DELETE 再 INSERT**（{@code model.php:162-163}）——
 * 等价于「原地重写当前版本的快照」，历史版本的 spec 行永久保留。
 *
 * <h3>两条必须照抄的唯一性（都在应用层，不建数据库唯一键）</h3>
 * <ul>
 *   <li>{@code title} 在 {@code (lib, module)} 内唯一（{@code model.php:99}）</li>
 *   <li>{@code path} 在 {@code (lib, module, method)} 内唯一（{@code model.php:100}）</li>
 * </ul>
 * 禅道的 unique 检查**不过滤已删除行**（与 {@code zt_company.name}、{@code zt_entry.code} 同一个坑），
 * 所以查询也不带 {@code deleted} 条件；建数据库唯一键反而会和逻辑删除打架（README 第 4 条坑）。
 *
 * <p><b>{@code desc} 是 MySQL 保留字</b>，必须显式加反引号。
 * {@code requestType}/{@code responseType}/{@code paramsExample}/{@code responseExample}/
 * {@code commonParams}/{@code addedBy}/{@code addedDate}/{@code editedBy}/{@code editedDate}
 * 都是驼峰列名，不加 {@code @TableField} 会被 MyBatis-Plus 拼成下划线（README 第 1 条坑）。
 */
@TableName("zt_api")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 所属产品。禅道原列是 {@code varchar(255)}（实际恒为 0），本实现按 id 归一为 bigint。
     */
    private Long product;

    /** 所属接口库（{@code zt_doclib} 里 {@code type='api'} 的记录） */
    private Long lib;

    /** 所属目录（{@code zt_module}，{@code type='api'}，{@code root = lib}） */
    private Long module;

    /** 接口名称，(lib, module) 内唯一 */
    private String title;

    /** 请求路径，(lib, module, method) 内唯一 */
    private String path;

    /** 协议：HTTP / HTTPS / WS / WSS */
    private String protocol;

    /** 请求方式：GET / POST / PUT / DELETE / PATCH / OPTIONS / HEAD */
    private String method;

    /** 请求格式（驼峰列名） */
    @TableField("requestType")
    private String requestType;

    /**
     * 响应格式（驼峰列名）。
     * <p><b>禅道的 create/edit 表单里都没有这一项</b>（{@code config/form.php}），
     * 所以它是只读的历史列：更新接口时不会被改写，本实现同样不动它。
     */
    @TableField("responseType")
    private String responseType;

    /** 开发状态：{@code done} 开发完成 / {@code doing} 开发中 / {@code hidden} 不显示 */
    private String status;

    /** 负责人账号 */
    private String owner;

    /** 接口说明。列名 {@code desc} 是 MySQL 保留字 */
    @TableField("`desc`")
    private String desc;

    /** 当前版本号；只有在编辑表单字段**真有变更**时才 +1 */
    private Integer version;

    /** 请求参数树 JSON：{@code {header:[],params:[],paramsType:"formData|json|array|object",query:[]}} */
    private String params;

    /** 请求示例（驼峰列名） */
    @TableField("paramsExample")
    private String paramsExample;

    /** 响应示例（驼峰列名） */
    @TableField("responseExample")
    private String responseExample;

    /** 响应字段树 JSON（形状与 {@code params.params} 相同） */
    private String response;

    /** 公共参数（驼峰列名）。禅道表单里没有它，恒为空 */
    @TableField("commonParams")
    private String commonParams;

    /** 创建人（驼峰列名） */
    @TableField("addedBy")
    private String addedBy;

    /** 创建时间（驼峰列名） */
    @TableField("addedDate")
    private LocalDateTime addedDate;

    /** 最后修改人（驼峰列名） */
    @TableField("editedBy")
    private String editedBy;

    /**
     * 最后修改时间（驼峰列名）。
     *
     * <p>它同时是**乐观锁**：禅道 {@code model.php:130-135} 发现「提交里的 editedDate 与库里不一致」
     * 就报 {@code lang->error->editedByOther}（别人已经改过）。本实现照抄这条。
     */
    @TableField("editedDate")
    private LocalDateTime editedDate;

}

package cn.iocoder.yudao.module.zentao.dal.dataobject.api;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口库发布版本（禅道 {@code zt_api_lib_release}）。
 *
 * <h3>{@code snap} 是快照，不是引用 —— 这是本模块最容易被做错的一处</h3>
 * 禅道 {@code model.php:39-70 publishLib()} 发布时把三样东西序列化进 {@code snap}：
 * <pre>
 *   { "modules": [ zt_module 整行… ],            // 目录树的形状
 *     "apis":    [ {"id":92711,"version":2} … ], // 只存编号 + 当时的版本号
 *     "structs": [ {"id":92731,"version":2} … ] }
 * </pre>
 * 读某个发布版本时，拿 snap 里的 {@code version} **回查** {@code zt_apispec}
 * （{@code model.php:312 getByID}、{@code :362 getApiListByRelease}）——
 * 也就是说：**冻结的是「版本号」，内容仍在 spec 表里**，所以删掉接口/结构的当前行
 * 不会破坏历史版本的可读性（{@code getApiListByRelease} 甚至不过滤 {@code deleted}）。
 *
 * <h3>其它照抄点</h3>
 * <ul>
 *   <li>{@code version} 是 **varchar**（禅道原样），同一 {@code lib} 内唯一 ——
 *       由 {@code control.php:441} 在应用层校验（{@code getRelease($libID,'byVersion',$version)}）</li>
 *   <li>删除发布是**物理删除**（{@code model.php:80 deleteRelease} 直接 {@code delete}）</li>
 * </ul>
 *
 * <p><b>没有 {@code deleted} 列</b> → 不继承 BaseDO。{@code desc} 是保留字。
 */
@TableName("zt_api_lib_release")
@Data
public class ApiLibReleaseDO {

    @TableId
    private Long id;

    /** 所属接口库 */
    private Long lib;

    /** 版本说明（保留字） */
    @TableField("`desc`")
    private String desc;

    /** 版本号：字符串，(lib, version) 内唯一 */
    private String version;

    /** 快照 JSON，形状见类注释 */
    private String snap;

    /** 发布人（驼峰列名） */
    @TableField("addedBy")
    private String addedBy;

    /** 发布时间（驼峰列名） */
    @TableField("addedDate")
    private LocalDateTime addedDate;

}

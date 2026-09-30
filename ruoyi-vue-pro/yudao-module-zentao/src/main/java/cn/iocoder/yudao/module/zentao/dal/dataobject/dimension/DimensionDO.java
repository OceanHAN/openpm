package cn.iocoder.yudao.module.zentao.dal.dataobject.dimension;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维度（禅道 {@code zt_dimension}）。
 *
 * <h3>它是 BI 的「1.5 级导航」</h3>
 * 把大屏（screen）/ 透视表（pivot）/ 图表（chart）归类到「宏观 / 效能 / 质量」三个维度下，
 * 提供切换维度的下拉菜单，并记住用户上次所在的维度。开源版**只有只读能力 + 3 行预置数据**，
 * 没有维度 CRUD / 管理界面（全库只有 {@code upgrade/model.php:6971} 直接 INSERT 这张表），
 * 所以本实现也不提供写接口 —— 见 README 的「有意偏离」。
 *
 * <h3>两个字段的语义不能凭直觉猜</h3>
 * <ol>
 *   <li><b>{@code acl} / {@code whitelist}</b>：可见性判定**不在 dimension 模块里**，而在
 *       {@code biModel::getViewableObject('dimension')}（{@code module/bi/model.php:14-66}）：
 *       {@code acl='open'} 或 {@code createdBy=自己} 或 {@code FIND_IN_SET(自己, whitelist)}，
 *       超管（{@code zt_company.admins}，本项目映射为 yudao 的 {@code super_admin} 角色）直通全部。</li>
 *   <li><b>{@code createdBy} / {@code editedBy}</b>：存的是**账号**（不是用户 id），与禅道其它表一致。
 *       MP 会把驼峰转下划线，所以必须显式 {@code @TableField("createdBy")}（坑位 #1）。</li>
 * </ol>
 *
 * <p>保留字：{@code desc} 是 MySQL 关键字，加反引号（坑位 #10 的名单里就有它）。
 * 逻辑删除列 {@code deleted} 由 {@link BaseDO} 提供（zt_dimension 是 {@code tinyint unsigned}）。
 */
@TableName("zt_dimension")
@Data
public class DimensionDO extends BaseDO {

    @TableId
    private Long id;

    /** 维度名称（1.5 级导航下拉的显示文本） */
    private String name;

    /** 维度代号（macro / efficiency / quality） */
    private String code;

    /** 描述。列名 desc 是 MySQL 关键字 */
    @TableField("`desc`")
    private String desc;

    /** 访问控制：open 公开 / private 仅创建者与白名单 */
    private String acl;

    /** 白名单账号逗号串（acl=private 时用 FIND_IN_SET 命中，禅道是 explode + in_array） */
    private String whitelist;

    /** 创建人账号（可见性判据之一：createdBy = 自己） */
    @TableField("createdBy")
    private String createdBy;

    /** 创建时间（禅道列，与框架的 create_time 不是一回事） */
    @TableField("createdDate")
    private LocalDateTime createdDate;

    /** 最后修改人账号 */
    @TableField("editedBy")
    private String editedBy;

    /** 最后修改时间 */
    @TableField("editedDate")
    private LocalDateTime editedDate;

}

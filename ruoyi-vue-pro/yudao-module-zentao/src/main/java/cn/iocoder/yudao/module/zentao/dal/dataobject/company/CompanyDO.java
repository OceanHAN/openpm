package cn.iocoder.yudao.module.zentao.dal.dataobject.company;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 公司（禅道 {@code zt_company}）。
 *
 * <h3>这个模块在禅道里叫「组织视图」</h3>
 * 它不止是「公司信息」那一页，而是 {@code company} 模块的三个入口：公司信息（view/edit）、
 * 组织成员（browse，按部门/内部外部看 {@code zt_user}）、组织动态（dynamic，全公司的 action feed）。
 * 后两个在本项目里分别由 {@code organization} 与 {@code action} 两个模块承担，
 * 所以这里只落 {@code zt_company} 这张表 + 两条禅道特有的口径：
 *
 * <ol>
 *   <li><b>{@code admins} 是「谁是超管」的口径</b>：存的是逗号串（安装时写的是 {@code ,admin,}），
 *       禅道用 {@code strpos(admins, ",{$account},") !== false} 判超管；
 *       yudao 里超管是 {@code super_admin} 角色（而且是硬编码放行、不查权限表），
 *       两边口径必须人工对齐 —— 本模块给的就是这个对照视图（{@code /admins}）。</li>
 *   <li><b>外部公司（{@code id != 1}）</b>：{@code getOutsideCompanies()} 就是 {@code id != 1}。
 *       它服务的是「外部干系人」：禅道加外部人员时会往 {@code zt_user} 里建一条 type=outside 的记录，
 *       并把 {@code zt_user.company} 指向这里；公司不存在还能顺手新建一条（insert-on-the-fly）。</li>
 * </ol>
 *
 * <p>{@code guest}（匿名登录）在 yudao 里没有对应机制（认证统一走 OAuth2），字段照存但不生效，见 README。
 */
@TableName("zt_company")
@Data
public class CompanyDO extends BaseDO {

    @TableId
    private Long id;

    /** 公司名称（唯一，禅道 update 时做 unique 校验且**不过滤已删除**） */
    private String name;

    private String phone;

    private String fax;

    private String address;

    private String zipcode;

    /** 官网。表单预填 http://，禅道在保存时把「正好等于 http://」的值清空 */
    private String website;

    /** 内网地址。同上，http:// 会被清空 */
    private String backyard;

    /** 是否允许匿名登录：1 允许。yudao 侧无此机制，仅存放 */
    private Integer guest;

    /** 管理员账号逗号串（如 {@code ,admin,}）：禅道判超管的唯一依据 */
    private String admins;

}

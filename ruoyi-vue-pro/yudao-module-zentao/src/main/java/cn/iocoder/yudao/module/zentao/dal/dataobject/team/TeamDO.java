package cn.iocoder.yudao.module.zentao.dal.dataobject.team;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目/执行团队成员 DO（禅道 {@code zt_team}）
 *
 * <h3>这张表回答「谁在这个项目里」</h3>
 * 之前的实现是从 {@code zt_project.team} 这个逗号字符串**推导**团队人数的，
 * 但禅道是从本表统计的（{@code module/project/tao.php#fetchMemberCountByIdList}）。
 * 字符串只装得下账号，装不下「什么角色、从哪天开始、每天投入几小时」——
 * 而这些正是工时统计、访问控制、负载分配的基础。
 *
 * <h3>两个关键点</h3>
 * <ol>
 *   <li><b>不继承 BaseDO</b>：禅道原表没有 {@code deleted} 列，成员是「在/不在」的关系数据，
 *       变更是**物理增删**（{@code updateTeamMembers} 先 delete 再 insert）。
 *       如果用逻辑删除，重新添加同一个人会撞 {@code UNIQUE(root,type,account)}（坑位 #4 同款）；</li>
 *   <li><b>{@code days * hours} 是这个人在这个项目里的可用工时</b>
 *       （禅道 {@code project/model.php:556} 就是这么算的），{@code hours} 默认 7.0。</li>
 * </ol>
 *
 * <p>保留字：{@code join}、{@code order} 都要加反引号。
 */
@TableName("zt_team")
@Data
public class TeamDO {

    @TableId
    private Long id;

    /** 所属对象编号（项目编号或执行编号） */
    private Long root;

    /** 类型，见 TeamTypeEnum */
    private String type;

    /** 成员账号 */
    private String account;

    /** 在本项目/执行里的角色 */
    private String role;

    /** 岗位 */
    private String position;

    /** 是否受限访问：yes/no */
    private String limited;

    /** 加入日期。列名 join 是 MySQL 关键字 */
    @TableField("`join`")
    private LocalDate join;

    /** 可用天数 */
    private Integer days;

    /** 每天投入小时数（禅道默认 7.0） */
    private BigDecimal hours;

    private BigDecimal estimate;
    private BigDecimal consumed;

    /** 剩余工时。列名 left 是 MySQL 保留字 */
    @TableField("`left`")
    private BigDecimal left;

    /** 排序。列名 order 是 MySQL 关键字 */
    @TableField("`order`")
    private Integer order;

}

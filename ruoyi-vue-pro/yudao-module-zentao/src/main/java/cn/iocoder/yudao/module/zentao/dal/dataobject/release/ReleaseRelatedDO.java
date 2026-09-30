package cn.iocoder.yudao.module.zentao.dal.dataobject.release;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 发布关联对象 DO
 *
 * <p>对应禅道 {@code zt_releaserelated}：一份**泛化的关系表**，
 * 用 {@code (release, objectType, objectID)} 记录发布关联了哪些对象。
 * {@code objectType} 取值：{@code project / build / branch / release / story / bug / leftBug}。
 *
 * <p>为什么禅道在已经有 {@code build/stories/bugs} 这些逗号列的情况下还要这张表？
 * 因为它要支持「按关联对象反查发布」（比如查某个项目发布了哪些版本、某个 Bug 出现在哪些发布里），
 * 用逗号列做反查只能全表 LIKE，而关系表可以直接走索引。
 *
 * <p>这张表不继承 BaseDO —— 禅道原表也没有 creator/deleted 这些字段，关系是纯粹的结构数据，
 * 删除时直接物理删除（见 {@code ReleaseService#deleteRelease}）。
 */
@TableName("zt_releaserelated")
@Data
public class ReleaseRelatedDO {

    @TableId
    private Long id;

    /**
     * 发布编号。
     *
     * <p>列名 {@code release} 是 <b>MySQL 8 的保留字</b>（和 {@code system} 一样：
     * JSqlParser 能解析、MySQL 直接报 1064），必须加反引号。
     */
    @TableField("`release`")
    private Long release;

    /**
     * 关联对象编号。
     *
     * <p>禅道列名是驼峰的 {@code objectID}；不显式声明的话 MyBatis-Plus 会转成
     * {@code object_i_d}，直接报 Unknown column。
     */
    @TableField("objectID")
    private Long objectID;

    /**
     * 关联对象类型：project/build/branch/release/story/bug/leftBug。驼峰列名
     */
    @TableField("objectType")
    private String objectType;

}

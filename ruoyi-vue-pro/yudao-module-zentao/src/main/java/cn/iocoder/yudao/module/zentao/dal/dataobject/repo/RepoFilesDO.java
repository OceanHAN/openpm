package cn.iocoder.yudao.module.zentao.dal.dataobject.repo;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 提交里改动的文件（禅道 {@code zt_repofiles}）。
 *
 * <p>一条 commit 可以有 N 行：{@code action} 是 git 的 {@code --name-status} 首字母
 * （A 新增 / M 修改 / D 删除 / R 重命名），重命名时 {@code oldPath} 记旧路径。
 *
 * <p>本表也没有 {@code deleted} 列（与提交记录一样是事实数据）。
 */
@TableName("zt_repofiles")
@Data
public class RepoFilesDO {

    @TableId
    private Long id;

    /** 代码库编号 */
    private Long repo;

    /** commit sha */
    private String revision;

    /** 文件路径 */
    private String path;

    /** 重命名前的路径 */
    @TableField("oldPath")
    private String oldPath;

    /** 类型：file/dir */
    private String type;

    /** 动作：A/M/D/R */
    private String action;

}

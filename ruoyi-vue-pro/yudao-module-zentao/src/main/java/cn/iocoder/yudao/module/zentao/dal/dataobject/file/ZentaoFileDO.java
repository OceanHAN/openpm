package cn.iocoder.yudao.module.zentao.dal.dataobject.file;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 附件 DO
 *
 * <p>类名带 Zentao 前缀是**必须的**：yudao 的 infra 模块已经有
 * {@code cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO}，而 application.yaml 里
 * {@code mybatis-plus.type-aliases-package} 是按 {@code module.*.dal.dataobject} 通配扫描的，
 * 两个 FileDO 会注册成同一个别名直接把 SqlSessionFactory 打挂（The alias 'FileDO' is already mapped）。
 *
 * <p>对应禅道 {@code zt_file}。每一行是一个附件，靠 {@code (objectType, objectID)}
 * 挂在业务对象上，所以附件是**所有业务模块的公共能力**。
 *
 * <h3>两个要点</h3>
 * <ol>
 *   <li><b>gid 两阶段绑定</b>：新建对象时对象 id 还不存在，禅道先给这批上传一个临时 gid，
 *       等对象保存后再按 gid 一次性绑定（{@code updateObjectID}）</li>
 *   <li><b>存储不在这里</b>：字节交给 yudao 的文件服务，{@code pathname} 存它返回的 URL</li>
 * </ol>
 *
 * <p>驼峰列名（objectType/objectID/addedBy/addedDate）必须显式声明。
 */
@TableName("zt_file")
@KeySequence("zt_file_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ZentaoFileDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 存储路径 / 可访问 URL（yudao 文件服务返回）
     */
    private String pathname;

    /**
     * 文件名（展示用）
     */
    private String title;

    /**
     * 扩展名
     */
    private String extension;

    /**
     * 字节大小
     */
    private Long size;

    /**
     * 所属对象类型：story/task/bug/doc…。驼峰列名
     */
    @TableField("objectType")
    private String objectType;

    /**
     * 所属对象编号。驼峰列名
     */
    @TableField("objectID")
    private Long objectID;

    /**
     * 临时分组 id，用于「先传附件、后绑对象」
     */
    private String gid;

    /**
     * 上传人。驼峰列名
     */
    @TableField("addedBy")
    private String addedBy;

    /**
     * 上传时间。驼峰列名
     */
    @TableField("addedDate")
    private LocalDateTime addedDate;

    /**
     * 下载次数
     */
    private Integer downloads;

    /**
     * 扩展字段（禅道原字段）
     */
    private String extra;

}

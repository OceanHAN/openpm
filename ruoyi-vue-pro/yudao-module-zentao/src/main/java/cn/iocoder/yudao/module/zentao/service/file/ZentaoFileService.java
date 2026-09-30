package cn.iocoder.yudao.module.zentao.service.file;

import cn.iocoder.yudao.module.zentao.dal.dataobject.file.ZentaoFileDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 附件 Service 接口
 *
 * <p>名字带 Zentao 前缀是为了避开 infra 模块同名的 fileService bean，详见 Controller 的说明。
 *
 * <h3>禅道语义（module/file/model.php）</h3>
 * <ul>
 *   <li>附件靠 {@code (objectType, objectID)} 挂在业务对象上 —— 是所有模块的公共能力</li>
 *   <li><b>gid 两阶段绑定</b>：新建对象时先上传（带临时 gid），对象保存后再
 *       {@code bindByGid} 一次性挂上去</li>
 *   <li>{@code downloads} 下载计数累加</li>
 *   <li>存储不迁移：字节交给 yudao 的文件服务（{@code FileApi}），
 *       {@code zt_file.pathname} 存它返回的 URL</li>
 * </ul>
 */
public interface ZentaoFileService {

    /**
     * 上传附件
     *
     * @param objectType 对象类型；传空表示暂不绑定（配合 gid 两阶段上传）
     * @param objectID   对象编号；0 表示暂不绑定
     * @param gid        临时分组 id，可空
     */
    ZentaoFileDO upload(MultipartFile file, String objectType, Long objectID, String gid);

    /**
     * 按 gid 把附件绑定到对象（禅道 updateObjectID）
     *
     * @return 绑定的附件数量
     */
    int bindByGid(String gid, String objectType, Long objectID);

    /**
     * 某个对象下的附件列表
     */
    List<ZentaoFileDO> getListByObject(String objectType, Long objectID);

    /**
     * 某个 gid 下的附件列表
     */
    List<ZentaoFileDO> getListByGid(String gid);

    /**
     * 获得附件
     */
    ZentaoFileDO getFile(Long id);

    /**
     * 校验附件存在
     */
    ZentaoFileDO validateFileExists(Long id);

    /**
     * 下载：返回访问 URL，并把下载计数 +1
     */
    String download(Long id);

    /**
     * 重命名
     */
    void rename(Long id, String title);

    /**
     * 删除附件（连带删掉存储里的字节）
     */
    void delete(Long id);

    /**
     * 删除某个对象下的全部附件
     */
    int deleteByObject(String objectType, Long objectID);

    /**
     * 某个对象下的附件数量
     */
    Long countByObject(String objectType, Long objectID);

}

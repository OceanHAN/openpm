package cn.iocoder.yudao.module.zentao.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.file.ZentaoFileDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.file.ZentaoFileMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 附件 Service 实现
 *
 * <p>名字带 Zentao 前缀是为了避开 infra 模块同名的 fileServiceImpl bean。
 *
 * <p>对齐禅道 {@code module/file/model.php} 的 saveUpload / updateObjectID / download / delete，
 * 但**存储不自己实现**：字节走 yudao 的 {@link FileApi}（存储后端由 infra_file_config 决定），
 * 本表只保留禅道侧的元数据。
 */
@Slf4j
@Service
public class ZentaoFileServiceImpl implements ZentaoFileService {

    private static final String OBJECT_TYPE_FILE = "file";

    /**
     * 单文件大小上限：50MB（禅道默认也在同一量级）
     */
    private static final long MAX_SIZE = 50L * 1024 * 1024;

    // 字段名必须带 zentao：@Resource 是**先按名字**注入的，叫 fileMapper 会命中
    // infra 模块的 FileMapper（同名 bean），然后类型不匹配直接启动失败。
    @Resource
    private ZentaoFileMapper zentaoFileMapper;

    @Resource
    private FileApi fileApi;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 上传 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ZentaoFileDO upload(MultipartFile file, String objectType, Long objectID, String gid) {
        if (file == null || file.isEmpty()) {
            throw exception(FILE_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw exception(FILE_SIZE_EXCEEDED, MAX_SIZE / 1024 / 1024);
        }

        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "unnamed";
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.warn("[upload][读取上传内容失败({})]", originalName, e);
            throw exception(FILE_READ_FAILED);
        }

        // 字节交给 yudao 的文件服务；directory 固定 zentao，便于在存储侧归类
        String url = fileApi.createFile(content, originalName, "zentao", file.getContentType());

        ZentaoFileDO fileDO = new ZentaoFileDO();
        fileDO.setPathname(url);
        fileDO.setTitle(originalName);
        fileDO.setExtension(extensionOf(originalName));
        fileDO.setSize(file.getSize());
        fileDO.setObjectType(StringUtils.hasText(objectType) ? objectType : "");
        fileDO.setObjectID(objectID == null ? 0L : objectID);
        fileDO.setGid(StringUtils.hasText(gid) ? gid : "");
        fileDO.setDownloads(0);
        fileDO.setExtra("");
        fileDO.setAddedBy(currentAccount());
        fileDO.setAddedDate(LocalDateTime.now());
        zentaoFileMapper.insert(fileDO);

        actionService.recordAction(OBJECT_TYPE_FILE, fileDO.getId(), ActionTypeEnum.CREATED,
                "上传附件：" + originalName + (StringUtils.hasText(objectType)
                        ? "（" + objectType + " #" + objectID + "）" : "（待绑定 gid=" + gid + "）"));
        return fileDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bindByGid(String gid, String objectType, Long objectID) {
        List<ZentaoFileDO> files = zentaoFileMapper.selectListByGid(gid);
        if (files.isEmpty()) {
            throw exception(FILE_GID_NOT_EXISTS, gid);
        }
        int count = 0;
        for (ZentaoFileDO item : files) {
            ZentaoFileDO updateObj = new ZentaoFileDO();
            updateObj.setId(item.getId());
            updateObj.setObjectType(objectType);
            updateObj.setObjectID(objectID);
            updateObj.setGid("");
            zentaoFileMapper.updateById(updateObj);
            count++;
        }
        actionService.recordAction(OBJECT_TYPE_FILE, objectID, ActionTypeEnum.CREATED,
                "绑定 " + count + " 个附件到 " + objectType + " #" + objectID);
        return count;
    }

    // ==================== 查询 ====================

    @Override
    public List<ZentaoFileDO> getListByObject(String objectType, Long objectID) {
        return zentaoFileMapper.selectListByObject(objectType, objectID);
    }

    @Override
    public List<ZentaoFileDO> getListByGid(String gid) {
        return zentaoFileMapper.selectListByGid(gid);
    }

    @Override
    public ZentaoFileDO getFile(Long id) {
        return validateFileExists(id);
    }

    @Override
    public ZentaoFileDO validateFileExists(Long id) {
        ZentaoFileDO fileDO = id == null ? null : zentaoFileMapper.selectById(id);
        if (fileDO == null) {
            throw exception(FILE_NOT_EXISTS, id);
        }
        return fileDO;
    }

    @Override
    public Long countByObject(String objectType, Long objectID) {
        return (long) zentaoFileMapper.selectListByObject(objectType, objectID).size();
    }

    // ==================== 下载 / 重命名 / 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String download(Long id) {
        ZentaoFileDO fileDO = validateFileExists(id);
        // 计数交给数据库自增，避免并发下载互相覆盖
        zentaoFileMapper.increaseDownloads(id);
        actionService.recordAction(OBJECT_TYPE_FILE, id, ActionTypeEnum.EDITED,
                "下载附件：" + fileDO.getTitle());
        return fileDO.getPathname();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String title) {
        ZentaoFileDO old = validateFileExists(id);
        ZentaoFileDO updateObj = new ZentaoFileDO();
        updateObj.setId(id);
        updateObj.setTitle(title);
        updateObj.setExtension(extensionOf(title));
        zentaoFileMapper.updateById(updateObj);
        actionService.recordAction(OBJECT_TYPE_FILE, id, ActionTypeEnum.EDITED,
                "附件重命名：" + old.getTitle() + " → " + title);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ZentaoFileDO fileDO = validateFileExists(id);
        // 只删元数据：存储里的字节由 yudao 的文件服务管理，
        // 这里不做物理删除（多个业务对象可能引用同一 URL，且 yudao 侧没有按 URL 删除的 API）
        zentaoFileMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_FILE, id, ActionTypeEnum.DELETED,
                "删除附件：" + fileDO.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByObject(String objectType, Long objectID) {
        List<ZentaoFileDO> files = zentaoFileMapper.selectListByObject(objectType, objectID);
        files.forEach(item -> zentaoFileMapper.deleteById(item.getId()));
        return files.size();
    }

    // ==================== 内部 ====================

    private String extensionOf(String name) {
        if (!StringUtils.hasText(name) || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }

    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

}

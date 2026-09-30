package cn.iocoder.yudao.module.zentao.dal.mysql.doc;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocLibDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文档库 Mapper
 */
@Mapper
public interface DocLibMapper extends BaseMapperX<DocLibDO> {

    /**
     * 某个对象（产品/项目/执行）下的文档库
     */
    default List<DocLibDO> selectListByObject(String type, Long objectID) {
        // 注意 orderByAsc 没有被 LambdaQueryWrapperX 覆写（只有 orderByDesc 覆写了），
        // 链式调用会退化成基类型 LambdaQueryWrapper，所以排序必须单独一行写。
        LambdaQueryWrapperX<DocLibDO> wrapper = new LambdaQueryWrapperX<DocLibDO>()
                .eq(DocLibDO::getType, type);
        wrapper.orderByAsc(DocLibDO::getOrder).orderByAsc(DocLibDO::getId);
        if ("product".equals(type)) {
            wrapper.eq(DocLibDO::getProduct, objectID);
        } else if ("project".equals(type)) {
            wrapper.eq(DocLibDO::getProject, objectID);
        } else if ("execution".equals(type)) {
            wrapper.eq(DocLibDO::getExecution, objectID);
        }
        return selectList(wrapper);
    }

    /**
     * 自定义空间（type=custom 且 parent=0）下的库
     */
    default List<DocLibDO> selectListByParent(Long parent) {
        LambdaQueryWrapperX<DocLibDO> wrapper = new LambdaQueryWrapperX<DocLibDO>()
                .eq(DocLibDO::getParent, parent);
        wrapper.orderByAsc(DocLibDO::getOrder).orderByAsc(DocLibDO::getId);
        return selectList(wrapper);
    }

    /**
     * 同空间下是否已有同名库（名称唯一性校验）
     */
    default DocLibDO selectByTypeAndObjectAndName(String type, Long objectID, Long parent, String name) {
        LambdaQueryWrapperX<DocLibDO> wrapper = new LambdaQueryWrapperX<DocLibDO>()
                .eq(DocLibDO::getType, type)
                .eq(DocLibDO::getName, name);
        if ("product".equals(type)) {
            wrapper.eq(DocLibDO::getProduct, objectID);
        } else if ("project".equals(type)) {
            wrapper.eq(DocLibDO::getProject, objectID);
        } else if ("execution".equals(type)) {
            wrapper.eq(DocLibDO::getExecution, objectID);
        } else {
            wrapper.eq(DocLibDO::getParent, parent == null ? 0L : parent);
        }
        return selectOne(wrapper);
    }

}

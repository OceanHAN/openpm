package cn.iocoder.yudao.module.zentao.controller.admin.doc.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 文档分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DocPageReqVO extends PageParam {

    @Schema(description = "文档库编号", example = "91001")
    private Long lib;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "所属模块（精确匹配；章节树是文档的主导航，不用它做子树展开）", example = "1")
    private Long module;

    @Schema(description = "上级章节（章节树里点某个章节时用它列出章节下的文档）", example = "91011")
    private Long parent;

    @Schema(description = "标题（模糊）", example = "需求")
    private String title;

    @Schema(description = "关键词（模糊）", example = "架构")
    private String keywords;

    @Schema(description = "类型", example = "html")
    private String type;

    @Schema(description = "状态：normal/draft", example = "normal")
    private String status;

    @Schema(description = "是否排除章节（文档列表用；默认 false 表示章节也返回）", example = "true")
    private Boolean excludeChapter;

    @Schema(description = "空间类型：product/project/execution/custom。给了它就能一次查出该空间下所有库的文档",
            example = "product")
    private String spaceType;

    @Schema(description = "空间对象编号（spaceType 对应的产品/项目/执行 id）", example = "1")
    private Long spaceObjectID;

    @Schema(description = "成员（创建人/指派给/最后修改人命中该账号）")
    private String member;

}

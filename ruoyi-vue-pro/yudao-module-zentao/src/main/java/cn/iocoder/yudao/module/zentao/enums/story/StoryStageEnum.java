package cn.iocoder.yudao.module.zentao.enums.story;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 需求研发阶段枚举
 *
 * 取值对齐禅道 {@code $lang->story->stageList}。
 * 禅道用这个字段驱动「需求在研发流程中走到哪一步」，与 {@link StoryStatusEnum}
 * 是两个正交的维度：status 表示需求的审批状态，stage 表示研发进度。
 */
@Getter
@AllArgsConstructor
public enum StoryStageEnum {

    WAIT("wait", "未开始"),
    PLANNED("planned", "已计划"),
    PROJECTED("projected", "研发立项"),
    DESIGNING("designing", "设计中"),
    DESIGNED("designed", "设计完毕"),
    DEVELOPING("developing", "研发中"),
    DEVELOPED("developed", "研发完毕"),
    TESTING("testing", "测试中"),
    TESTED("tested", "测试完毕"),
    VERIFIED("verified", "已验收"),
    /** 已发布。需求被关联到「已发布」状态的发布时会流转到这一阶段（禅道 release::setStoriesStage） */
    RELEASED("released", "已发布"),
    CLOSED("closed", "已关闭");

    private final String stage;
    private final String name;

}

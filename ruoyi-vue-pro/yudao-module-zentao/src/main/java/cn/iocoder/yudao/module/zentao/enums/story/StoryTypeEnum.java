package cn.iocoder.yudao.module.zentao.enums.story;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 需求分层类型枚举（业务需求 ER / 用户需求 UR / 研发需求 SR）
 *
 * 禅道里 "需求" 是一个统一概念，靠 {@code zt_story.type} 区分三个层次：
 * <pre>
 *   module/epic/lang/zh-cn.php        $lang->ERCommon = '业务需求'   type = epic
 *   module/requirement/lang/zh-cn.php $lang->URCommon = '用户需求'   type = requirement
 *   module/story/lang/zh-cn.php       $lang->SRCommon = '研发需求'   type = story
 * </pre>
 *
 * 两个薄壳模块（{@code module/epic}、{@code module/requirement}）的 control.php 里
 * 全部 action 都只是把 {@code storyType} 换成 epic/requirement 后转调 story 的
 * model，{@code module/epic/model.php} 与 {@code module/requirement/model.php}
 * 都只有 15 行且只是 {@code class epicModel extends model}，
 * 所以本实现不为它们建表、也不建独立 Service，
 * 而是在 story 上做类型分层（与禅道同一套数据模型，PHP 侧数据可原样迁移）。
 *
 * <h3>父子类型规则</h3>
 * 禅道 {@code module/story/model.php} 的 {@code getEpicParents / getRequirementParents /
 * getStoryParents} 决定了候选父需求的类型：
 * <pre>
 *   getEpicParents()        父只能是 epic
 *   getRequirementParents() 父可以是 epic + requirement
 *   getStoryParents()       父可以是 epic + requirement（enableER 关闭时只有 requirement）
 * </pre>
 * 归纳成一条规则就是 <b>父需求的层级不能低于子需求</b>（{@code parentLevel <= childLevel}），
 * 即 1 级业务需求可以作为任何需求的父、2 级用户需求不能作为业务需求的父。
 * 另外研发需求自身的分解（同类型子需求）仍然合法，见 {@link #isParentTypeAllowed}。
 *
 * 与禅道的差异（有意为之，写在 README「需求分层」一节）：
 * 禅道用 {@code $config->enableER} / {@code $config->URAndSR} 两个开关决定是否展示
 * 业务需求/用户需求（开源版默认关闭），本实现不引入开关、三层始终可用，
 * 因为开关属于界面裁剪，不属于数据模型；历史数据里只有 story 也不受影响。
 */
@Getter
@AllArgsConstructor
public enum StoryTypeEnum {

    /** 业务需求 ER，层级 1 */
    EPIC("epic", "业务需求", 1),

    /** 用户需求 UR，层级 2 */
    REQUIREMENT("requirement", "用户需求", 2),

    /** 研发需求 SR，层级 3（{@code zt_story.type} 的默认值） */
    STORY("story", "研发需求", 3);

    /**
     * 类型值，对应禅道数据库 {@code zt_story.type} 的存储值
     */
    private final String type;

    /**
     * 类型名，对应禅道 {@code $lang->ERCommon/URCommon/SRCommon}
     */
    private final String name;

    /**
     * 需求层级，1 最粗、3 最细；父子合法性判断就靠它
     */
    private final Integer level;

    public static StoryTypeEnum of(String type) {
        for (StoryTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String type) {
        return of(type) != null;
    }

    /**
     * 取类型名，未知类型返回原值，避免日志里出现 null
     */
    public static String nameOf(String type) {
        StoryTypeEnum item = of(type);
        return item != null ? item.name : String.valueOf(type);
    }

    /**
     * 取层级，未知类型按最细的研发需求处理（兼容历史脏数据，不让老数据直接不可用）
     */
    public static Integer levelOf(String type) {
        StoryTypeEnum item = of(type);
        return item != null ? item.level : STORY.level;
    }

    /**
     * 父需求类型是否允许挂这种类型的子需求
     *
     * 规则：父的层级 <= 子的层级。于是
     * <pre>
     *   epic        可以挂 epic(同层分解) / requirement / story
     *   requirement 可以挂 requirement(同层分解) / story
     *   story       只能挂 story（研发需求自己的分解）
     * </pre>
     *
     * @param childType  子需求类型
     * @param parentType 父需求类型
     */
    public static boolean isParentTypeAllowed(String childType, String parentType) {
        return levelOf(parentType) <= levelOf(childType);
    }

    /**
     * 某类型的父需求候选类型（对齐禅道 getXxxParents 的查询条件）
     */
    public static List<String> parentTypesOf(String childType) {
        List<String> result = new ArrayList<>();
        for (StoryTypeEnum item : values()) {
            if (isParentTypeAllowed(childType, item.type)) {
                result.add(item.type);
            }
        }
        return result;
    }

    /**
     * 分解（批量建子需求）时子需求的类型 —— 禅道里
     * 「业务需求」的分解按钮跳的是 {@code requirement/batchCreate}，
     * 「用户需求」的分解按钮跳的是 {@code story/batchCreate}，
     * 「研发需求」分解出来的还是研发需求（子需求）。
     */
    public static String childTypeOf(String parentType) {
        StoryTypeEnum item = of(parentType);
        if (item == null) {
            return STORY.type;
        }
        // 层级 +1，已经是最后一层就保持 story
        int nextLevel = item.level + 1;
        for (StoryTypeEnum candidate : values()) {
            if (candidate.level == nextLevel) {
                return candidate.type;
            }
        }
        return STORY.type;
    }

}

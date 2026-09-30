package cn.iocoder.yudao.module.zentao.enums.module;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 模块树类型枚举
 *
 * <p>{@code zt_module} 是一张通用树表，用 {@code (root, type, branch)} 定位一棵树。
 * 禅道的 {@code type} 取值来自各模块的模块维护入口（{@code module/tree/control.php}
 * 里的 {@code browseXxx} 方法 + {@code config/zentao.php} 的 objectTables 映射）。
 *
 * <p>这里只列出**本项目会用到或即将用到**的类型。禅道还有 deliverable / dashboard /
 * host / practice / trainSkill 等十几种，属于特定业务视图，需要时在这里追加即可
 * （追加后 {@link #isValid} 自动放行，不需要改 Service）。
 */
@Getter
@AllArgsConstructor
public enum ModuleTypeEnum {

    /** 需求模块。root = 产品 id */
    STORY("story", "需求模块"),
    /** 任务模块。root = 执行 id */
    TASK("task", "任务模块"),
    /** 缺陷模块。root = 产品 id */
    BUG("bug", "缺陷模块"),
    /** 用例模块。root = 产品 id */
    CASE("case", "用例模块"),
    /** 用例库模块。root = 用例库 id */
    CASELIB("caselib", "用例库模块"),
    /** 文档库分类。root = 文档库 id */
    DOC("doc", "文档分类"),
    /** 接口目录。root = 接口库 id */
    API("api", "接口目录"),
    /**
     * 产品线。这是一个特例：{@code objectTables['productline'] = TABLE_MODULE}，
     * 也就是说产品线也存在这张表里，root 恒为 0
     */
    LINE("line", "产品线");

    private final String type;
    private final String name;

    /**
     * 需要「挂到 branch 上」的类型。禅道只有需求/缺陷/用例这类**产品视图**的模块树带分支，
     * 任务模块树是按执行走的，不带分支
     */
    private static final List<String> BRANCH_AWARE_TYPES =
            Arrays.asList(STORY.getType(), BUG.getType(), CASE.getType());

    public static ModuleTypeEnum of(String type) {
        for (ModuleTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String type) {
        return of(type) != null;
    }

    public static boolean isBranchAware(String type) {
        return BRANCH_AWARE_TYPES.contains(type);
    }

    /**
     * 取类型名，未知类型返回原值，避免报错信息里出现 null
     */
    public static String nameOf(String type) {
        ModuleTypeEnum item = of(type);
        return item != null ? item.getName() : String.valueOf(type);
    }

}

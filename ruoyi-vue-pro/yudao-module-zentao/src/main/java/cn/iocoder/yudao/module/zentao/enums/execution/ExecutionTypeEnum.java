package cn.iocoder.yudao.module.zentao.enums.execution;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 执行类型枚举
 *
 * 取值对齐禅道 {@code $lang->execution->typeList}。
 *
 * <h3>关键：执行与项目共用 zt_project 表</h3>
 * 禅道 config/zentaopms.php 里有**三个**常量指向同一张表：
 * <pre>
 *   define('TABLE_PROGRAM',   '`zt_project`');   // 项目集
 *   define('TABLE_PROJECT',   '`zt_project`');   // 项目
 *   define('TABLE_EXECUTION', '`zt_project`');   // 执行
 * </pre>
 * 也就是说项目集、项目、执行是同一张表里的三种 type：
 * <pre>
 *   type = 'program'                     → 项目集（{@link #PROGRAM}）
 *   type = 'project'                     → 项目（{@link #PROJECT}）
 *   type IN ('sprint','stage','kanban')  → 执行
 * </pre>
 * 所以这里额外定义 {@link #PROJECT}，并在 {@link #EXECUTION_TYPES} 里列出属于执行的类型，
 * 供查询时过滤。**任何查执行的地方都必须带上这个 type 条件**，否则会把项目也查出来。
 */
@Getter
@AllArgsConstructor
public enum ExecutionTypeEnum {

    /** 项目集。也不是执行，同样存在这张表里；项目靠 parent 指向所属项目集 */
    PROGRAM("program", "项目集"),
    /** 项目。不是执行，但存在同一张表里，用于查询过滤 */
    PROJECT("project", "项目"),
    /** 阶段。瀑布模型的执行 */
    STAGE("stage", "阶段"),
    /** 迭代。Scrum 模型的执行 */
    SPRINT("sprint", "迭代"),
    /** 看板。看板模型的执行 */
    KANBAN("kanban", "看板");

    private final String type;
    private final String name;

    /**
     * 属于「执行」的类型集合。查询执行时用它做 IN 过滤
     */
    public static final List<String> EXECUTION_TYPES =
            Arrays.asList(STAGE.getType(), SPRINT.getType(), KANBAN.getType());

    /**
     * 类型是否属于「执行」
     */
    public static boolean isExecution(String type) {
        return EXECUTION_TYPES.contains(type);
    }

    public static ExecutionTypeEnum of(String type) {
        for (ExecutionTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

}

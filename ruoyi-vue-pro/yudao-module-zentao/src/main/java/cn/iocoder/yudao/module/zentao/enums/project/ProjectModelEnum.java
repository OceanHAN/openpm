package cn.iocoder.yudao.module.zentao.enums.project;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 项目管理模型枚举
 *
 * 取值对齐禅道 {@code $lang->project->modelList}。
 * 模型决定项目用哪套流程：Scrum 有迭代与需求池，瀑布有阶段与里程碑，看板只有卡片流转。
 */
@Getter
@AllArgsConstructor
public enum ProjectModelEnum {

    SCRUM("scrum", "Scrum"),
    WATERFALL("waterfall", "瀑布"),
    KANBAN("kanban", "看板"),
    AGILE_PLUS("agileplus", "融合敏捷"),
    WATERFALL_PLUS("waterfallplus", "融合瀑布"),
    IPD("ipd", "IPD");

    private final String model;
    private final String name;

    public static ProjectModelEnum of(String model) {
        for (ProjectModelEnum item : values()) {
            if (item.model.equals(model)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String model) {
        return of(model) != null;
    }

}

package cn.iocoder.yudao.module.zentao.enums.todo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 待办类型枚举
 *
 * 取值对齐禅道 {@code $lang->todo->typeList}：
 * <pre>
 *   custom   自定义（不挂对象，就是随手记的一件事）
 *   cycle    周期待办（本实现只存字段，不自动生成下一次）
 *   bug      关联缺陷
 *   task     关联任务
 *   story    关联需求
 *   testtask 关联测试单
 * </pre>
 * 除 custom/cycle 外，其余类型必须带 objectID —— 它们本质上是「某个对象的快捷入口」，
 * 禅道 create 时还会拿对象的 name/title 回填待办名称（module/todo/zen.php#prepareCreateData）。
 */
@Getter
@AllArgsConstructor
public enum TodoTypeEnum {

    CUSTOM("custom", "自定义"),
    CYCLE("cycle", "周期"),
    BUG("bug", "Bug"),
    TASK("task", "任务"),
    STORY("story", "需求"),
    TESTTASK("testtask", "测试单");

    private final String type;
    private final String name;

    public static TodoTypeEnum of(String type) {
        for (TodoTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

    /** 是否需要挂一个业务对象 */
    public static boolean needObject(String type) {
        return type != null && !CUSTOM.getType().equals(type) && !CYCLE.getType().equals(type);
    }

}

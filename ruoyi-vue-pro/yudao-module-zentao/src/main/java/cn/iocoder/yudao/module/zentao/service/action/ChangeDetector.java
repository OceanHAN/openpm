package cn.iocoder.yudao.module.zentao.service.action;

import cn.iocoder.yudao.module.zentao.dal.dataobject.action.HistoryDO;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 字段级变更检测
 *
 * 对应禅道 {@code common::createChanges()}。做法是逐字段对比新旧对象，
 * 把有差异的字段抽成 {@link HistoryDO} 列表，供 {@code zt_history} 落库。
 *
 * <h3>为什么要维护黑名单</h3>
 * 禅道在这里有一份「不记录」的字段清单。原因是像 {@code lastEditedDate}、
 * {@code assignedDate} 这类字段每次保存都会变，如果照实记录，日志会被
 * 「最后修改时间从 A 变成 B」这种噪声淹没，真正的业务变更反而看不见。
 * 这里保持同样的策略。
 */
public final class ChangeDetector {

    /**
     * 完全不记录的字段。对应禅道 createChanges 里的
     * {@code in_array($check, array('lastediteddate', 'lasteditedby', 'assigneddate', ...))}
     */
    private static final Set<String> IGNORED_FIELDS = new LinkedHashSet<>(Arrays.asList(
            // 禅道原有的黑名单
            "lastediteddate", "lasteditedby", "assigneddate", "editedby", "editeddate",
            "editingdate", "uid", "prevstatus", "prevassignedto",
            // yudao BaseDO 的审计字段，同样属于噪声
            "createtime", "updatetime", "creator", "updater", "deleted"
    ));

    /**
     * 新值为空时跳过的日期字段。对应禅道里
     * {@code in_array($check, array('finisheddate', 'canceleddate', ...)) && $value == ''}
     */
    private static final Set<String> EMPTY_SKIPPED_FIELDS = new LinkedHashSet<>(Arrays.asList(
            "finisheddate", "canceleddate", "hangupeddate", "lastcheckeddate",
            "activateddate", "closeddate", "actualcloseddate"
    ));

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ChangeDetector() {}

    /**
     * 对比新旧对象，返回发生变化的字段列表
     *
     * @param oldObj 变更前的对象
     * @param newObj 用于更新的对象。约定：只对「显式设置过（非 null）」的字段做比较，
     *               这与 MyBatis-Plus updateById 只更新非 null 字段的行为一致，
     *               也避免把未参与本次更新的字段误报成「从有值变成 null」
     * @return 变更明细列表；无变化时返回空列表
     */
    public static List<HistoryDO> detect(Object oldObj, Object newObj) {
        if (oldObj == null || newObj == null) {
            return List.of();
        }
        List<HistoryDO> changes = new ArrayList<>();
        for (Field field : collectFields(newObj.getClass())) {
            String name = field.getName();
            String lower = name.toLowerCase();
            if (IGNORED_FIELDS.contains(lower)) {
                continue;
            }

            Object newValue = readField(field, newObj);
            Object oldValue = readField(field, oldObj);

            // 只比较本次显式赋值的字段
            if (newValue == null) {
                continue;
            }
            // 集合、数组等复杂类型不逐字段记录（禅道同样跳过 object/array）
            if (isComplex(newValue) || isComplex(oldValue)) {
                continue;
            }

            String newText = toText(newValue);
            String oldText = toText(oldValue);
            if (EMPTY_SKIPPED_FIELDS.contains(lower) && newText.isEmpty()) {
                continue;
            }
            if (newText.equals(oldText)) {
                continue;
            }

            HistoryDO history = new HistoryDO();
            history.setField(name);
            history.setOld(oldText);
            history.setNew_(newText);
            history.setOldValue(oldText);
            history.setNewValue(newText);
            history.setDiff(LineDiff.needDiff(name) ? LineDiff.diff(oldText, newText) : "");
            changes.add(history);
        }
        return changes;
    }

    /**
     * 收集类及其父类（不含 Object）的全部实例字段
     */
    private static List<Field> collectFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static Object readField(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isComplex(Object value) {
        return value != null && (value instanceof Iterable || value instanceof Object[]);
    }

    private static String toText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMATTER);
        }
        return String.valueOf(value);
    }

}

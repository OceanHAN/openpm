package cn.iocoder.yudao.module.zentao.service.action;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.ACTION_UNDELETE_UNSUPPORTED;

/**
 * 「对象类型 → 表 + 名称列」映射 —— 回收站要用它把 {@code zt_action} 里的
 * {@code (objectType, objectID)} 还原成「哪张表的哪一行」。
 *
 * <p>禅道对应 {@code module/action/tao.php#getUndeleteParamsByObjectType}，是一张更大的表
 * （覆盖 artifact / space / projectchange 等）。本实现只登记**已经迁移、且删除是逻辑删除**的对象类型：
 * 表名与列名都写死在这里，**不接受任何外部输入**，所以拼 SQL 是安全的。
 *
 * <p>没有登记的类型在回收站里会显示 {@code canUndelete=false}，点还原会报
 * 「对象类型不支持回收站还原」——不猜、不误删。
 */
@Component
public class ActionObjectMap {

    /** 表 + 名称列 */
    public record Target(String table, String nameColumn) {}

    private static final Map<String, Target> TARGETS = new LinkedHashMap<>();

    static {
        TARGETS.put("story", new Target("zt_story", "title"));
        TARGETS.put("task", new Target("zt_task", "name"));
        TARGETS.put("bug", new Target("zt_bug", "title"));
        TARGETS.put("case", new Target("zt_case", "title"));
        TARGETS.put("doc", new Target("zt_doc", "title"));
        TARGETS.put("module", new Target("zt_module", "name"));
        TARGETS.put("product", new Target("zt_product", "name"));
        TARGETS.put("project", new Target("zt_project", "name"));
        TARGETS.put("execution", new Target("zt_project", "name"));
        TARGETS.put("program", new Target("zt_project", "name"));
        TARGETS.put("productplan", new Target("zt_productplan", "title"));
        TARGETS.put("build", new Target("zt_build", "name"));
        TARGETS.put("release", new Target("zt_release", "name"));
        TARGETS.put("branch", new Target("zt_branch", "name"));
        TARGETS.put("testtask", new Target("zt_testtask", "name"));
        TARGETS.put("testsuite", new Target("zt_testsuite", "name"));
        TARGETS.put("testreport", new Target("zt_testreport", "title"));
        TARGETS.put("todo", new Target("zt_todo", "name"));
        TARGETS.put("file", new Target("zt_file", "title"));
        TARGETS.put("kanban", new Target("zt_kanban", "name"));
        TARGETS.put("chart", new Target("zt_chart", "name"));
        TARGETS.put("dataview", new Target("zt_dataview", "name"));
        TARGETS.put("caselib", new Target("zt_testsuite", "name"));
        TARGETS.put("metric", new Target("zt_metric", "name"));
    }

    /** 对象类型 → 中文名（禅道 $lang->action->objectTypes；回收站列表与动态里都要显示） */
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("story", "需求");
        NAMES.put("task", "任务");
        NAMES.put("bug", "缺陷");
        NAMES.put("case", "用例");
        NAMES.put("doc", "文档");
        NAMES.put("module", "模块");
        NAMES.put("product", "产品");
        NAMES.put("project", "项目");
        NAMES.put("execution", "执行");
        NAMES.put("program", "项目集");
        NAMES.put("productplan", "计划");
        NAMES.put("build", "构建");
        NAMES.put("release", "发布");
        NAMES.put("branch", "分支/平台");
        NAMES.put("testtask", "测试单");
        NAMES.put("testsuite", "用例集");
        NAMES.put("testreport", "测试报告");
        NAMES.put("caselib", "用例库");
        NAMES.put("todo", "待办");
        NAMES.put("file", "附件");
        NAMES.put("effort", "工时");
        NAMES.put("team", "团队成员");
        NAMES.put("stakeholder", "干系人");
        NAMES.put("workestimation", "工作量估算");
        NAMES.put("kanban", "看板");
        NAMES.put("kanbanSpace", "看板空间");
        NAMES.put("kanbanRegion", "看板区域");
        NAMES.put("kanbanLane", "看板泳道");
        NAMES.put("kanbanColumn", "看板列");
        NAMES.put("kanbanCard", "看板卡片");
        NAMES.put("metric", "度量项");
        NAMES.put("dataview", "数据视图");
        NAMES.put("chart", "图表");
        NAMES.put("action", "操作日志");
    }

    /** 取中文名，没登记就原样返回（不为了显示去猜） */
    public String displayName(String objectType) {
        if (!StringUtils.hasText(objectType)) {
            return "-";
        }
        return NAMES.getOrDefault(objectType, objectType);
    }

    /** 取映射；不支持的类型返回 null（调用方决定是报错还是标灰） */
    public Target find(String objectType) {
        return StringUtils.hasText(objectType) ? TARGETS.get(objectType) : null;
    }

    /** 取映射，取不到直接报错 */
    public Target require(String objectType) {
        Target target = find(objectType);
        if (target == null) {
            throw exception(ACTION_UNDELETE_UNSUPPORTED, objectType);
        }
        return target;
    }

}

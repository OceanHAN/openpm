package cn.iocoder.yudao.module.zentao.controller.admin.todo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoAssignReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.todo.TodoDO;
import cn.iocoder.yudao.module.zentao.enums.todo.TodoStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.todo.TodoTypeEnum;
import cn.iocoder.yudao.module.zentao.service.todo.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 待办 Controller
 *
 * 对应禅道 {@code module/todo/}。待办是**个人**清单（zt_todo），和项目里的任务（zt_task）不是一回事。
 */
@Tag(name = "管理后台 - 待办")
@RestController
@RequestMapping("/zentao/todo")
@Validated
public class TodoController {

    @Resource
    private TodoService todoService;

    @PostMapping("/create")
    @Operation(summary = "创建待办",
            description = "不填日期默认今天；type 不是 custom/cycle 时必须带 objectID（关联对象的快捷入口）")
    @PreAuthorize("@ss.hasPermission('zentao:todo:create')")
    public CommonResult<Long> createTodo(@Valid @RequestBody TodoSaveReqVO createReqVO) {
        return success(todoService.createTodo(createReqVO));
    }

    @PostMapping("/batch-create")
    @Operation(summary = "批量创建待办", description = "禅道 batchCreate：一次给一批名称，其余字段共用")
    @PreAuthorize("@ss.hasPermission('zentao:todo:create')")
    public CommonResult<List<Long>> batchCreate(@Valid @RequestBody TodoSaveReqVO baseReqVO,
                                               @RequestParam("names") List<String> names) {
        return success(todoService.batchCreate(baseReqVO, names));
    }

    @PutMapping("/update")
    @Operation(summary = "修改待办")
    @PreAuthorize("@ss.hasPermission('zentao:todo:update')")
    public CommonResult<Boolean> updateTodo(@Valid @RequestBody TodoSaveReqVO updateReqVO) {
        todoService.updateTodo(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除待办")
    @Parameter(name = "id", description = "待办编号", required = true, example = "97101")
    @PreAuthorize("@ss.hasPermission('zentao:todo:delete')")
    public CommonResult<Boolean> deleteTodo(@RequestParam("id") Long id) {
        todoService.deleteTodo(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除待办")
    @PreAuthorize("@ss.hasPermission('zentao:todo:delete')")
    public CommonResult<Boolean> deleteTodoList(@RequestParam("ids") List<Long> ids) {
        todoService.deleteTodoList(ids);
        return success(true);
    }

    // ==================== 状态流转 ====================

    @PutMapping("/start")
    @Operation(summary = "开始待办", description = "未开始 → 进行中")
    @Parameter(name = "id", description = "待办编号", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:todo:update')")
    public CommonResult<Boolean> startTodo(@RequestParam("id") Long id) {
        todoService.startTodo(id);
        return success(true);
    }

    @PutMapping("/finish")
    @Operation(summary = "完成待办", description = "写完成人与完成时间")
    @Parameter(name = "id", description = "待办编号", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:todo:update')")
    public CommonResult<Boolean> finishTodo(@RequestParam("id") Long id) {
        todoService.finishTodo(id);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭待办",
            description = "禅道会把指派人置成伪用户 closed —— 关闭的待办在「我指派给别人的」列表里也不再出现")
    @Parameter(name = "id", description = "待办编号", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:todo:update')")
    public CommonResult<Boolean> closeTodo(@RequestParam("id") Long id) {
        todoService.closeTodo(id);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活待办", description = "退回未开始；指派人若是伪用户 closed 则还原成完成人")
    @Parameter(name = "id", description = "待办编号", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:todo:update')")
    public CommonResult<Boolean> activateTodo(@RequestParam("id") Long id) {
        todoService.activateTodo(id);
        return success(true);
    }

    @PutMapping("/assign")
    @Operation(summary = "指派待办")
    @PreAuthorize("@ss.hasPermission('zentao:todo:update')")
    public CommonResult<Boolean> assignTodo(@Valid @RequestBody TodoAssignReqVO reqVO) {
        todoService.assignTodo(reqVO);
        return success(true);
    }

    @PutMapping("/import-to-today")
    @Operation(summary = "把之前没完成的待办挪到今天", description = "禅道 import2Today")
    @Parameter(name = "all", description = "true 时连今天的一起重排", example = "false")
    @PreAuthorize("@ss.hasPermission('zentao:todo:update')")
    public CommonResult<Integer> importToToday(@RequestParam(value = "account", required = false) String account,
                                               @RequestParam(value = "all", defaultValue = "false") Boolean all) {
        return success(todoService.importToToday(account, Boolean.TRUE.equals(all)));
    }

    // ==================== 读 ====================

    @GetMapping("/get")
    @Operation(summary = "获得待办")
    @Parameter(name = "id", description = "待办编号", required = true, example = "97101")
    @PreAuthorize("@ss.hasPermission('zentao:todo:query')")
    public CommonResult<TodoRespVO> getTodo(@RequestParam("id") Long id) {
        return success(convert(todoService.getTodo(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得待办分页", description = "管理视角：按归属账号/指派人/状态/类型/优先级/日期区间过滤")
    @PreAuthorize("@ss.hasPermission('zentao:todo:query')")
    public CommonResult<PageResult<TodoRespVO>> getTodoPage(@Valid TodoPageReqVO pageReqVO) {
        PageResult<TodoDO> page = todoService.getTodoPage(pageReqVO);
        List<TodoRespVO> list = new ArrayList<>();
        page.getList().forEach(todo -> list.add(convert(todo)));
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/my-list")
    @Operation(summary = "获得我的待办",
            description = "禅道口径：assignedTo = 我 或 finishedBy = 我 或 closedBy = 我；"
                    + "browseType 支持 today/tomorrow/thisweek/before/future/all/cycle")
    @PreAuthorize("@ss.hasPermission('zentao:todo:query')")
    public CommonResult<List<TodoRespVO>> getMyTodoList(@Valid TodoPageReqVO reqVO) {
        List<TodoRespVO> list = new ArrayList<>();
        todoService.getMyTodoList(reqVO, reqVO.getAccount()).forEach(todo -> list.add(convert(todo)));
        return success(list);
    }

    @GetMapping("/type-list")
    @Operation(summary = "待办类型枚举")
    @PreAuthorize("@ss.hasPermission('zentao:todo:query')")
    public CommonResult<List<Map<String, String>>> getTypeList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (TodoTypeEnum item : TodoTypeEnum.values()) {
            list.add(enumItem(item.getType(), item.getName()));
        }
        return success(list);
    }

    @GetMapping("/status-list")
    @Operation(summary = "待办状态枚举")
    @PreAuthorize("@ss.hasPermission('zentao:todo:query')")
    public CommonResult<List<Map<String, String>>> getStatusList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (TodoStatusEnum item : TodoStatusEnum.values()) {
            list.add(enumItem(item.getStatus(), item.getName()));
        }
        // 禅道列表页额外给一个「未完成」的聚合状态
        list.add(enumItem("undone", "未完成"));
        return success(list);
    }

    @GetMapping("/pri-list")
    @Operation(summary = "待办优先级枚举")
    @PreAuthorize("@ss.hasPermission('zentao:todo:query')")
    public CommonResult<List<Map<String, Object>>> getPriList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int pri = 1; pri <= 4; pri++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", pri);
            item.put("label", String.valueOf(pri));
            item.put("name", pri == 1 ? "最高" : pri == 2 ? "较高" : pri == 3 ? "普通" : "最低");
            list.add(item);
        }
        return success(list);
    }

    // ==================== 内部 ====================

    private TodoRespVO convert(TodoDO todo) {
        TodoRespVO vo = BeanUtils.toBean(todo, TodoRespVO.class);
        if (vo == null) {
            return null;
        }
        TodoTypeEnum type = TodoTypeEnum.of(todo.getType());
        vo.setTypeName(type == null ? todo.getType() : type.getName());
        // 「已过期」= 未完成 && 日期早于今天（前端据此标红）
        vo.setOverdue(todo.getDate() != null
                && todo.getDate().isBefore(LocalDate.now())
                && !Arrays.asList("done", "closed").contains(todo.getStatus()));
        return vo;
    }

    private Map<String, String> enumItem(String value, String label) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("value", value);
        item.put("label", label);
        return item;
    }

}

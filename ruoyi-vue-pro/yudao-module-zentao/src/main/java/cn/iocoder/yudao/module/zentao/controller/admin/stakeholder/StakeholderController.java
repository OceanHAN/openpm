package cn.iocoder.yudao.module.zentao.controller.admin.stakeholder;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.stakeholder.vo.StakeholderRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.stakeholder.vo.StakeholderSaveReqVO;
import cn.iocoder.yudao.module.zentao.enums.stakeholder.StakeholderFromEnum;
import cn.iocoder.yudao.module.zentao.enums.stakeholder.StakeholderTypeEnum;
import cn.iocoder.yudao.module.zentao.service.stakeholder.StakeholderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 干系人 Controller
 *
 * 干系人挂在**项目集（program）或项目（project）**下，和团队成员（zt_team）不是一回事。
 */
@Tag(name = "管理后台 - 干系人")
@RestController
@RequestMapping("/zentao/stakeholder")
@Validated
public class StakeholderController {

    @Resource
    private StakeholderService stakeholderService;

    @PostMapping("/create")
    @Operation(summary = "添加干系人",
            description = "type 由 from 推导：from=outside → outside，其余 → inside；同一个人在同一个对象下不能重复添加")
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:update')")
    public CommonResult<Long> createStakeholder(@Valid @RequestBody StakeholderSaveReqVO createReqVO) {
        return success(stakeholderService.createStakeholder(createReqVO));
    }

    @PostMapping("/batch-create")
    @Operation(summary = "批量添加干系人", description = "已经加过的直接跳过（禅道 batchCreate 的行为）")
    @Parameter(name = "objectType", description = "program / project", required = true)
    @Parameter(name = "objectID", description = "对象编号", required = true)
    @Parameter(name = "from", description = "来源：team / company / outside", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:update')")
    public CommonResult<List<Long>> batchCreate(@RequestParam("objectType") String objectType,
                                                @RequestParam("objectID") Long objectID,
                                                @RequestParam("from") String from,
                                                @RequestBody List<String> users) {
        return success(stakeholderService.batchCreate(objectType, objectID, from, users));
    }

    @PutMapping("/update")
    @Operation(summary = "修改干系人", description = "主要改「来源」与「是否关键干系人」")
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:update')")
    public CommonResult<Boolean> updateStakeholder(@Valid @RequestBody StakeholderSaveReqVO updateReqVO) {
        stakeholderService.updateStakeholder(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "移除干系人（按记录编号）")
    @Parameter(name = "id", description = "干系人编号", required = true, example = "99101")
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:update')")
    public CommonResult<Boolean> deleteStakeholder(@RequestParam("id") Long id) {
        stakeholderService.deleteStakeholder(id);
        return success(true);
    }

    @DeleteMapping("/delete-by-user")
    @Operation(summary = "移除干系人（按对象+账号）", description = "禅道 delete(userID) 的口径")
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:update')")
    public CommonResult<Boolean> deleteByUser(@RequestParam("objectType") String objectType,
                                              @RequestParam("objectID") Long objectID,
                                              @RequestParam("user") String user) {
        stakeholderService.deleteByObjectAndUser(objectType, objectID, user);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得干系人")
    @Parameter(name = "id", description = "干系人编号", required = true, example = "99101")
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:query')")
    public CommonResult<StakeholderRespVO> getStakeholder(@RequestParam("id") Long id) {
        return success(stakeholderService.getStakeholderVO(id));
    }

    @GetMapping("/list")
    @Operation(summary = "某个对象的干系人列表", description = "关键干系人排前面；内部人员回填姓名")
    @Parameter(name = "objectType", description = "program / project", required = true)
    @Parameter(name = "objectID", description = "对象编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:query')")
    public CommonResult<List<StakeholderRespVO>> getStakeholderList(
            @RequestParam("objectType") String objectType,
            @RequestParam("objectID") Long objectID) {
        return success(stakeholderService.getStakeholderList(objectType, objectID));
    }

    @GetMapping("/list-by-user")
    @Operation(summary = "我作为干系人参与的对象编号", description = "「我的地盘」用得上")
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:query')")
    public CommonResult<List<Long>> getObjectIdsByUser(
            @RequestParam(value = "objectType", required = false) String objectType,
            @RequestParam("user") String user) {
        return success(stakeholderService.getObjectIdsByUser(objectType, user));
    }

    @GetMapping("/type-list")
    @Operation(summary = "干系人类型枚举")
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:query')")
    public CommonResult<List<Map<String, String>>> getTypeList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (StakeholderTypeEnum item : StakeholderTypeEnum.values()) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("value", item.getType());
            map.put("label", item.getName());
            list.add(map);
        }
        return success(list);
    }

    @GetMapping("/from-list")
    @Operation(summary = "干系人来源枚举", description = "team 团队成员 / company 公司同事 / outside 外部人员")
    @PreAuthorize("@ss.hasPermission('zentao:stakeholder:query')")
    public CommonResult<List<Map<String, String>>> getFromList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (StakeholderFromEnum item : StakeholderFromEnum.values()) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("value", item.getFrom());
            map.put("label", item.getName());
            list.add(map);
        }
        return success(list);
    }

}

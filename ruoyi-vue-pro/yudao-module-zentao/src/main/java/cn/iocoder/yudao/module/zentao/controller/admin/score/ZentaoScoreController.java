package cn.iocoder.yudao.module.zentao.controller.admin.score;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.score.vo.ScoreCreateReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.score.vo.ScorePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.score.vo.ScoreRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.score.vo.ScoreRuleRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.score.vo.ScoreTotalRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.score.ScoreDO;
import cn.iocoder.yudao.module.zentao.service.score.ScoreRules;
import cn.iocoder.yudao.module.zentao.service.score.ScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 积分（禅道 {@code module/score}）。
 *
 * <p>禅道只有两个 action：{@code ajax}（被前端 JS 调用去计分）与 {@code rule}（积分规则页）。
 * 计分本身是**被别的模块调**的，所以本实现把它开成显式入口 {@code POST /zentao/score/create}，
 * 既给已迁模块用，也方便联调与测试。
 *
 * <p>「我的积分」入口在禅道属于 {@code my} 模块（`$lang->my->scoreRule`），本实现放在积分页里
 * （明细 + 规则 + 总览），不重复做一套「我的」。
 */
@Tag(name = "管理后台 - 禅道积分")
@RestController
@RequestMapping("/zentao/score")
@Validated
public class ZentaoScoreController {

    @Resource
    private ScoreService scoreService;

    @GetMapping("/page")
    @Operation(summary = "积分流水分页",
            description = "不传 account 就是「我的积分」（与禅道 getListByAccount 一致，排序 time desc, id desc）")
    @PreAuthorize("@ss.hasPermission('zentao:score:query')")
    public CommonResult<PageResult<ScoreRespVO>> getPage(@Valid ScorePageReqVO pageReqVO) {
        PageResult<ScoreDO> page = scoreService.getPage(pageReqVO);
        PageResult<ScoreRespVO> result = BeanUtils.toBean(page, ScoreRespVO.class);
        result.getList().forEach(vo -> {
            vo.setModuleName(ScoreRules.moduleName(vo.getModule()));
            vo.setMethodName(ScoreRules.methodName(vo.getModule(), vo.getMethod()));
        });
        return success(result);
    }

    @GetMapping("/total")
    @Operation(summary = "积分总览", description = "总积分（SUM(流水)）+ 昨日新增 + 禅道 getNotice 的那句提示")
    @Parameter(name = "account", description = "账号（不传=当前登录账号）", example = "admin")
    @PreAuthorize("@ss.hasPermission('zentao:score:query')")
    public CommonResult<ScoreTotalRespVO> getTotal(@RequestParam(value = "account", required = false) String account) {
        return success(scoreService.getTotal(account));
    }

    @GetMapping("/rule")
    @Operation(summary = "积分规则", description = "禅道 score::rule 的规则表：分值 / 次数上限 / 时间窗 / 扩展加成说明")
    @PreAuthorize("@ss.hasPermission('zentao:score:query')")
    public CommonResult<List<ScoreRuleRespVO>> getRules() {
        return success(scoreService.getRules());
    }

    @PostMapping("/create")
    @Operation(summary = "计分",
            description = "对应禅道 score::create(module, method, param, account, time)："
                    + "按规则表计分，命中次数/时间窗上限就跳过；写流水并落 before/after 快照。"
                    + "规则不存在或功能关闭时**明确报错**（禅道内部调用是静默跳过）")
    @PreAuthorize("@ss.hasPermission('zentao:score:create')")
    public CommonResult<ScoreRespVO> create(@Valid @RequestBody ScoreCreateReqVO reqVO) {
        ScoreDO score = scoreService.create(reqVO.getModule(), reqVO.getMethod(), reqVO.getParam(),
                reqVO.getAccount(), reqVO.getTime());
        ScoreRespVO vo = BeanUtils.toBean(score, ScoreRespVO.class);
        if (vo != null) {
            vo.setModuleName(ScoreRules.moduleName(vo.getModule()));
            vo.setMethodName(ScoreRules.methodName(vo.getModule(), vo.getMethod()));
        }
        return success(vo);
    }

}

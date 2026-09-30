package cn.iocoder.yudao.module.zentao.service.company;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.company.vo.CompanyAdminsRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.company.vo.CompanyOptionVO;
import cn.iocoder.yudao.module.zentao.controller.admin.company.vo.CompanyPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgUserRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.zentao.service.organization.OrganizationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 公司信息（禅道 {@code module/company}，界面上叫「组织视图」）。
 *
 * <h3>这个模块只做两件事，其余两件交给已有模块</h3>
 * <pre>
 *   公司信息 view/edit  →  本模块（zt_company 一张表 + 两条禅道口径：admins / 外部公司）
 *   组织成员 browse     →  organization 模块（zt_user 视角的用户列表 + 部门树，不重复实现）
 *   组织动态 dynamic    →  action 模块（全公司 feed，前端直接调 /zentao/action/dynamic）
 * </pre>
 *
 * <h3>三处照抄的禅道行为</h3>
 * <ol>
 *   <li><b>{@code http://} 归一</b>：表单里官网/内网预填 {@code http://}，禅道保存时用
 *       {@code setIF($post->website == 'http://', 'website', '')} 把它清空 —— 否则公司信息里会
 *       永远残留一个没有域名的 http://。这条很容易被当成「没用的细节」跳过，但它是禅道故意的。</li>
 *   <li><b>名称唯一且不过滤已删除</b>：{@code batchCheck('name','unique', "id != x")}，
 *       与 {@code entry.code} 同一个坑（坑位 #52 那段）。</li>
 *   <li><b>默认公司 id = 1</b>：{@code getFirst()} 取 id 最小的一条当「本公司」；
 *       {@code getOutsideCompanies()} 就是 {@code id != 1}（服务外部干系人的公司下拉）。</li>
 * </ol>
 *
 * <p><b>有意不做 delete</b>：禅道 {@code module/company} 没有删除 action（多公司是付费版能力，
 * 开源版里 id != 1 的公司由「加外部干系人时顺手新建」产生）。本实现同样不提供删除，
 * 免得误删掉被外部人员引用的公司。
 */
@Service
@Slf4j
public class CompanyService {

    @Resource
    private CompanyMapper companyMapper;

    @Resource
    private OrganizationService organizationService;

    // ==================== 查 ====================

    public PageResult<CompanyDO> getPage(CompanyPageReqVO reqVO) {
        return companyMapper.selectPage(reqVO);
    }

    public CompanyDO getCompany(Long id) {
        CompanyDO company = companyMapper.selectById(id);
        if (company == null) {
            throw exception(COMPANY_NOT_EXISTS);
        }
        return company;
    }

    /** 禅道 {@code getFirst()}：本公司（id 最小的一条） */
    public CompanyDO getFirstCompany() {
        CompanyDO company = companyMapper.selectFirst();
        if (company == null) {
            throw exception(COMPANY_DEFAULT_NOT_EXISTS);
        }
        return company;
    }

    public List<CompanyDO> getList() {
        return companyMapper.selectList();
    }

    /** 外部公司下拉（禅道 {@code ajaxGetOutsideCompany} 的 {text,value,keys} 结构） */
    public List<CompanyOptionVO> getOutsideOptions() {
        List<CompanyOptionVO> options = new ArrayList<>();
        for (CompanyDO company : companyMapper.selectOutsideList()) {
            CompanyOptionVO option = new CompanyOptionVO();
            option.setText(company.getName());
            option.setValue(company.getId());
            option.setKeys(company.getName());
            options.add(option);
        }
        return options;
    }

    // ==================== 写 ====================

    public Long create(CompanyDO company) {
        validateName(company.getName(), null);
        fillDefaults(company);
        companyMapper.insert(company);
        return company.getId();
    }

    public void update(CompanyDO company) {
        getCompany(company.getId());
        validateName(company.getName(), company.getId());
        fillDefaults(company);
        companyMapper.updateById(company);
    }

    /** 必填 + 唯一（不过滤已删除，禅道口径） */
    private void validateName(String name, Long excludeId) {
        if (StrUtil.isBlank(name)) {
            throw exception(COMPANY_NAME_REQUIRED);
        }
        Long existsId = companyMapper.selectIdByNameIgnoreDeleted(name);
        if (existsId != null && !existsId.equals(excludeId)) {
            throw exception(COMPANY_NAME_DUPLICATE, name);
        }
    }

    /**
     * 默认值与归一化：{@code website/backyard} 只填了 {@code http://} 就当没填；
     * {@code guest} 缺省 0（不允许匿名登录）。
     */
    private void fillDefaults(CompanyDO company) {
        company.setWebsite(blankIfOnlyScheme(company.getWebsite()));
        company.setBackyard(blankIfOnlyScheme(company.getBackyard()));
        if (company.getGuest() == null) {
            company.setGuest(0);
        }
    }

    private String blankIfOnlyScheme(String value) {
        return "http://".equals(StrUtil.trimToEmpty(value)) ? "" : value;
    }

    // ==================== 超管口径对照 ====================

    /**
     * 禅道用 {@code zt_company.admins} 判超管，yudao 用 {@code super_admin} 角色 ——
     * 这个接口把两边的差异摆出来（组织成员列表复用 organization 模块，不重复查库）。
     */
    public CompanyAdminsRespVO getAdminsMapping() {
        CompanyDO company = getFirstCompany();

        List<String> zentaoAdmins = new ArrayList<>();
        for (String account : StrUtil.split(StrUtil.blankToDefault(company.getAdmins(), ""), ',')) {
            if (StrUtil.isNotBlank(account)) {
                zentaoAdmins.add(account.trim());
            }
        }

        List<String> yudaoSuperAdmins = new ArrayList<>();
        for (OrgUserRespVO user : organizationService.getUserList(null, null, null)) {
            String roleCodes = StrUtil.blankToDefault(user.getRoleCodes(), "");
            if (StrUtil.split(roleCodes, ',').stream().map(String::trim).anyMatch("super_admin"::equals)) {
                yudaoSuperAdmins.add(user.getAccount());
            }
        }

        CompanyAdminsRespVO resp = new CompanyAdminsRespVO();
        resp.setZentaoAdmins(zentaoAdmins);
        resp.setYudaoSuperAdmins(yudaoSuperAdmins);
        resp.setMatched(intersect(zentaoAdmins, yudaoSuperAdmins));
        resp.setOnlyInZentao(diff(zentaoAdmins, yudaoSuperAdmins));
        resp.setOnlyInYudao(diff(yudaoSuperAdmins, zentaoAdmins));
        resp.setNote("禅道判超管看 zt_company.admins 这个逗号串（strpos(admins, \",account,\")）；"
                + "yudao 看 super_admin 角色，而且超管是硬编码放行、不查权限表。两套口径要人工对齐："
                + "只在禅道侧的账号在 yudao 里没有超管角色，只在 yudao 侧的账号在禅道里不算超管。");
        return resp;
    }

    private List<String> intersect(List<String> a, List<String> b) {
        LinkedHashSet<String> set = new LinkedHashSet<>(a);
        set.retainAll(new LinkedHashSet<>(b));
        return new ArrayList<>(set);
    }

    private List<String> diff(List<String> a, List<String> b) {
        LinkedHashSet<String> set = new LinkedHashSet<>(a);
        set.removeAll(new LinkedHashSet<>(b));
        return new ArrayList<>(set);
    }

}

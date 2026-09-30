package cn.iocoder.yudao.module.zentao.enums.testreport;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code zt_testsuite.type} 的取值 —— 「用例集」与「用例库」是同一张表的两类记录。
 *
 * <p>禅道的用例集模块只认 public/private（{@code module/testsuite/model.php} 的查询条件），
 * 用例库模块只认 library + {@code product=0}（{@code module/caselib/model.php:142}）：
 * <pre>
 *   用例集 testsuite：product = &lt;所属产品&gt;，type in ('public','private')
 *   用例库 caselib  ：product = 0，           type = 'library'
 * </pre>
 *
 * 所以两边查询都必须带上这个区分条件，否则就是互相串数据
 * （这正是 README 坑位 #12「共用表要把 type 过滤当成强制项」）。
 */
@Getter
@AllArgsConstructor
public enum TestSuiteTypeEnum {

    /** 公共用例集（产品下所有人可见） */
    PUBLIC("public", "公共"),
    /** 私有用例集（只有创建者可见） */
    PRIVATE("private", "私有"),
    /** 用例库（product 恒为 0） */
    LIBRARY("library", "用例库");

    private final String type;

    private final String name;

    public static TestSuiteTypeEnum of(String type) {
        for (TestSuiteTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

}

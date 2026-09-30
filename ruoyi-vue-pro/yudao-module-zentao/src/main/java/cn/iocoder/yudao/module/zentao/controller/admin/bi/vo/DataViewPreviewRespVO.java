package cn.iocoder.yudao.module.zentao.controller.admin.bi.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 数据视图预览 Response VO")
@Data
public class DataViewPreviewRespVO {

    @Schema(description = "列名")
    private List<String> columns = new ArrayList<>();

    @Schema(description = "数据行")
    private List<Map<String, Object>> rows = new ArrayList<>();

    @Schema(description = "行数", example = "20")
    private Integer total;

    @Schema(description = "实际执行的 SQL（包了 LIMIT，便于排查）")
    private String executedSql;

}

package cn.iocoder.yudao.module.zentao.framework.web;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.FILE_EMPTY;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.FILE_SIZE_EXCEEDED;

/**
 * 附件上传的异常兜底
 *
 * <p>为什么要单独兜：multipart 的解析发生在进入 Controller **之前**（DispatcherServlet 的
 * checkMultipart），所以「文件太大」「请求不是 multipart」这两种情况根本走不到业务代码里，
 * 也就用不上 {@code ServiceException} 那套错误码，默认会抛成 500「系统异常」。
 * 对上传这种高频操作来说，用户看到 500 是没法自救的 —— 这里把它们翻译成和业务校验一致的中文提示。
 *
 * <p>{@code basePackages} 只圈禅道模块：别把 infra 自己的文件上传也改了行为。
 */
@Slf4j
@RestControllerAdvice(basePackages = "cn.iocoder.yudao.module.zentao")
public class ZentaoFileExceptionHandler {

    /**
     * 超过 spring.servlet.multipart.max-file-size（64MB）时 Spring 直接抛这个。
     * 提示统一按业务上限 50MB 来说，避免暴露两个不一致的数字。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public CommonResult<?> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("[handleMaxUploadSize][附件超过服务端上限]", ex);
        return CommonResult.error(FILE_SIZE_EXCEEDED, 50);
    }

    /**
     * 请求不是 multipart（比如前端忘了带文件字段）、或 multipart 体被截断。
     */
    @ExceptionHandler(MultipartException.class)
    public CommonResult<?> handleMultipart(MultipartException ex) {
        log.warn("[handleMultipart][multipart 请求解析失败：{}]", ex.getMessage());
        return CommonResult.error(FILE_EMPTY);
    }

}

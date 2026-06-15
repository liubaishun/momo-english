package com.momo.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(StackOverflowError.class)
    public String handleStackOverflowError(StackOverflowError e) {
        // 记录完整堆栈到日志文件
        log.error("发生栈溢出错误，请检查是否有无限递归或循环引用", e);

        // 返回友好提示
        return "服务器内部错误：栈溢出（可能是数据循环引用或无限递归）";
    }
}

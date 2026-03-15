package com.jelly.cinema.common.exception;

import com.jelly.cinema.common.exception.BusinessException;
import com.jelly.cinema.common.result.R;
import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e) {
        return R.fail(500, "服务器异常: " + e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusinessException(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<?> handleNotLoginException(NotLoginException e) {
        return R.fail(401, "未登录或登录已过期");
    }
}

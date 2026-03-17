package com.jelly.cinema.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.jelly.cinema.common.result.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        return R.fail(400, extractBindMessage(e.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    public R<?> handleBindException(BindException e) {
        return R.fail(400, extractBindMessage(e.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<?> handleConstraintViolationException(ConstraintViolationException e) {
        return R.fail(400, extractConstraintMessage(e.getConstraintViolations()));
    }

    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusinessException(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<?> handleNotLoginException(NotLoginException e) {
        return R.fail(401, "未登录或登录已过期");
    }

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e) {
        return R.fail(500, "服务器异常: " + e.getMessage());
    }

    private String extractBindMessage(Iterable<FieldError> fieldErrors) {
        for (FieldError fieldError : fieldErrors) {
            if (fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()) {
                return fieldError.getDefaultMessage();
            }
        }
        return "请求参数校验失败";
    }

    private String extractConstraintMessage(Set<ConstraintViolation<?>> violations) {
        if (violations == null || violations.isEmpty()) {
            return "请求参数校验失败";
        }
        for (ConstraintViolation<?> violation : violations) {
            if (violation.getMessage() != null && !violation.getMessage().isBlank()) {
                return violation.getMessage();
            }
        }
        return "请求参数校验失败";
    }
}

package com.jelly.cinema.common.result;

import java.io.Serializable;

public class R<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer code;
    private String msg;
    private T data;

    public R() {}

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public static <T> R<T> ok() {
        return restResult(null, 200, "success");
    }

    public static <T> R<T> ok(T data) {
        return restResult(data, 200, "success");
    }

    public static <T> R<T> ok(T data, String msg) {
        return restResult(data, 200, msg);
    }

    public static <T> R<T> fail() {
        return restResult(null, 500, "error");
    }

    public static <T> R<T> fail(String msg) {
        return restResult(null, 500, msg);
    }

    public static <T> R<T> fail(Integer code, String msg) {
        return restResult(null, code, msg);
    }

    private static <T> R<T> restResult(T data, Integer code, String msg) {
        R<T> apiResult = new R<>();
        apiResult.setCode(code);
        apiResult.setData(data);
        apiResult.setMsg(msg);
        return apiResult;
    }
}

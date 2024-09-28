package com.examforge.common.web;

import lombok.Data;

/** 统一响应体：{code, message, data, traceId}（与 docs/05 接口文档一致） */
@Data
public class Result<T> {
    public static final int OK = 0;
    public static final int UNAUTHORIZED = 40100;
    public static final int FORBIDDEN = 40301;
    public static final int NOT_FOUND = 40400;
    public static final int BAD_REQUEST = 42200;
    public static final int TOO_MANY = 42900;
    public static final int SYSTEM = 50010;

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> Result<T> ok(T data) { return build(OK, "ok", data); }
    public static <T> Result<T> fail(int code, String message) { return build(code, message, null); }

    private static <T> Result<T> build(int code, String message, T data) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.data = data;
        return r;
    }
}

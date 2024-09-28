package com.examforge.common.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理：统一转 Result，禁止堆栈外泄 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public static class BizException extends RuntimeException {
        public final int code;
        public BizException(int code, String message) { super(message); this.code = code; }
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> biz(BizException e) {
        int http = e.code == Result.UNAUTHORIZED ? 401 : e.code == Result.FORBIDDEN ? 403
                : e.code == Result.NOT_FOUND ? 404 : e.code == Result.BAD_REQUEST ? 422 : 400;
        return ResponseEntity.status(http).body(Result.fail(e.code, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.status(422).body(Result.fail(Result.BAD_REQUEST, "参数校验失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> any(Exception e) {
        log.error("[system-error]", e);
        return ResponseEntity.status(500).body(Result.fail(Result.SYSTEM, "系统错误"));
    }

    @SuppressWarnings("unused")
    private static HttpStatus unused = HttpStatus.OK;
}

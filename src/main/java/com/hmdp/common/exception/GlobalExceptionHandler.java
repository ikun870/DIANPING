package com.hmdp.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hmdp.dto.Result;

@RestControllerAdvice // 代替原来的 @ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义限流异常
     * @param e 限流异常
     * @return 限流异常信息
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Result> handleRateLimitException(RateLimitException e) {
        Result result = Result.fail(e.getMessage());
        return new ResponseEntity<>(result, HttpStatus.TOO_MANY_REQUESTS);
    }

  

}
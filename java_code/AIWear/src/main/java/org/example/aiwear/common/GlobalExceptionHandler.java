package org.example.aiwear.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数错误";
        return Result.clientError(message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<String> handleMissingServletRequestParameter(MissingServletRequestParameterException e) {
        return Result.clientError("缺少请求参数: " + e.getParameterName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<String> handleMissingServletRequestPart(MissingServletRequestPartException e) {
        return Result.clientError("缺少请求参数: " + e.getRequestPartName());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public Result<String> handleMissingRequestHeader(MissingRequestHeaderException e) {
        return Result.clientError("缺少请求头令牌");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return Result.clientError("请求体格式错误");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<String> handleConstraintViolation(ConstraintViolationException e) {
        return Result.clientError(e.getMessage());
    }
}

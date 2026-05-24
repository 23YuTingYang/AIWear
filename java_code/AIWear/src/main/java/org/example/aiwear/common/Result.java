package org.example.aiwear.common;


import lombok.Data;
import java.io.Serializable;

/**
 * 封装统一响应结果类
 **/
@Data
public class Result<T> implements Serializable {
    // 状态码
    private Integer code;
    // 提示信息
    private String message;
    // 数据
    private T data;


    //成功响应
    public static <T> Result<T> success(String message ,  T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    //客户端失败响应
    public static <T> Result<T> clientError(String message) {
        Result<T> result = new Result<>();
        result.setCode(400);
        result.setMessage(message);
        return result;
    }

    //服务器失败响应
    public static <T> Result<T> serverError(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }
}

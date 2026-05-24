package org.example.aiwear.service;


import jakarta.validation.Valid;
import org.example.aiwear.dto.request.AuthRequest;
import org.example.aiwear.dto.request.SendVerificationCodeRequest;
import org.example.aiwear.dto.response.AuthResponse;
import org.springframework.validation.annotation.Validated;

/**
 * 用户模块服务接口
 */
@Validated
public interface UserService {
    //发送验证码
    boolean sendVerificationCode(@Valid SendVerificationCodeRequest request);

    //认证注册/登录
    AuthResponse auth(@Valid AuthRequest request);

    //用户登出
    boolean logout(String authorization);
}

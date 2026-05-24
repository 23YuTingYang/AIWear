package org.example.aiwear.controller;


import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aiwear.common.Result;
import org.example.aiwear.dto.request.AuthRequest;
import org.example.aiwear.dto.response.AuthResponse;
import org.example.aiwear.dto.response.SendVerificationCodeResponse;
import org.example.aiwear.dto.request.SendVerificationCodeRequest;
import org.example.aiwear.log.ApiLog;
import org.example.aiwear.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


//用户模块的控制器
@Slf4j
@RestController
@RequestMapping("/api/user")
public class    UserController {

    @Autowired
    private UserService userService;

    //发送验证码
    @ApiLog
    @PostMapping("/send-code")
    public Result<SendVerificationCodeResponse> SendVerificationCode(@RequestBody @Valid SendVerificationCodeRequest request){
        boolean success = userService.sendVerificationCode(request);
        if (success){
            return Result.success("发送验证码成功" ,
                    SendVerificationCodeResponse.builder()
                            .sendTo("***")
                            .expireTime(300)
                            .build());
        }else{
            return Result.serverError("发送验证码失败,请稍后重试");
        }
    }

    //统一认证接口
    @ApiLog
    @PostMapping("/auth")
    public Result<AuthResponse> auth(@RequestBody @Valid AuthRequest request){
        return Result.success("认证成功" , userService.auth(request));
    }

    //用户登出接口
    @ApiLog
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value="Authorization") String authorization){
        boolean success = userService.logout(authorization);
        if (success){
            return Result.success("登出成功" , "登出成功");
        }else{
            return Result.serverError("登出失败.请稍后重试");
        }
    }
}

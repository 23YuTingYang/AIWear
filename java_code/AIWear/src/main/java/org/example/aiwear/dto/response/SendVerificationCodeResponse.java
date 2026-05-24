package org.example.aiwear.dto.response;


import lombok.Builder;
import lombok.Data;

/**
 * 发送验证码响应实体类
 */
@Data
@Builder
public class SendVerificationCodeResponse {
    // 接收验证码的邮箱
    private String sendTo;

    // 验证码有效期(过期时间（秒）)
    private Integer expireTime;
}




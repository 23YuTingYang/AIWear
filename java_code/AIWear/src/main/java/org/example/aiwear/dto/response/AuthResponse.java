package org.example.aiwear.dto.response;


//认证接口返回实体类

import lombok.Data;

import java.io.Serializable;


@Data
public class AuthResponse implements Serializable {
    private Long userId;

    private String username;

    private String email;

    private String token;
}

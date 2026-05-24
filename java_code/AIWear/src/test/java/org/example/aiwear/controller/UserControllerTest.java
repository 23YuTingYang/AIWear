package org.example.aiwear.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiwear.dto.request.AuthRequest;
import org.example.aiwear.dto.request.SendVerificationCodeRequest;
import org.example.aiwear.dto.response.AuthResponse;
import org.example.aiwear.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class   UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    //JSON 序列化工具，把请求对象转成 JSON 字符串。
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

// 测试说明：验证发送验证码成功时，接口返回成功结构
    @Test
    void sendCode_shouldReturnSuccessWhenServiceReturnsTrue() throws Exception {
        when(userService.sendVerificationCode(any(SendVerificationCodeRequest.class))).thenReturn(true);

        SendVerificationCodeRequest req = new SendVerificationCodeRequest();
        req.setEmail("a@test.com");

        mockMvc.perform(post("/api/user/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))  // 把请求对象转为 JSON
                .andExpect(status().isOk())    //断言 HTTP 状态码为 200。
                .andExpect(jsonPath("$.code").value(200))   //断言业务码 code=200
                .andExpect(jsonPath("$.data.sendTo").value("***"))  //断言返回数据里 sendTo 为 ***
                .andExpect(jsonPath("$.data.expireTime").value(300));  //断言返回验证码过期时间 300。
    }

// 测试说明：验证发送验证码失败时，接口返回服务端错误结构。
    @Test
    void sendCode_shouldReturnServerErrorWhenServiceReturnsFalse() throws Exception {
        when(userService.sendVerificationCode(any(SendVerificationCodeRequest.class))).thenReturn(false);

        SendVerificationCodeRequest req = new SendVerificationCodeRequest();
        req.setEmail("a@test.com");

        mockMvc.perform(post("/api/user/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

// 测试说明：验证邮箱格式非法时，参数校验返回 400 Bad Request。
    @Test
    void sendCode_shouldReturnBadRequestWhenEmailInvalid() throws Exception {
        SendVerificationCodeRequest req = new SendVerificationCodeRequest();
        req.setEmail("bad-email");

        mockMvc.perform(post("/api/user/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

// 测试说明：验证认证成功时，接口返回成功结构和 token 数据。。
    @Test
    void auth_shouldReturnSuccessWithData() throws Exception {
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUserId(1L);
        authResponse.setUsername("jack");
        authResponse.setToken("token-x");
        when(userService.auth(any(AuthRequest.class))).thenReturn(authResponse);

        AuthRequest req = new AuthRequest();
        req.setAccount("jack");
        req.setPassword("123456");

        mockMvc.perform(post("/api/user/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("token-x"));
    }

// 测试说明：验证账号为空时，参数校验返回 400 Bad Request。
    @Test
    void auth_shouldReturnBadRequestWhenAccountBlank() throws Exception {
        AuthRequest req = new AuthRequest();
        req.setAccount("");

        mockMvc.perform(post("/api/user/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

// 测试说明：验证登出成功时，接口返回成功结构。
    @Test
    void logout_shouldReturnSuccessWhenServiceReturnsTrue() throws Exception {
        when(userService.logout(eq("Bearer token"))).thenReturn(true);

        mockMvc.perform(post("/api/user/logout").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

// 测试说明：验证登出失败时，接口返回服务端错误结构。
    @Test
    void logout_shouldReturnServerErrorWhenServiceReturnsFalse() throws Exception {
        when(userService.logout(eq("Bearer t"))).thenReturn(false);

        mockMvc.perform(post("/api/user/logout").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
}

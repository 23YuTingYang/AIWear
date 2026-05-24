package org.example.aiwear.service.impl;

import org.example.aiwear.dto.request.AuthRequest;
import org.example.aiwear.dto.request.SendVerificationCodeRequest;
import org.example.aiwear.dto.response.AuthResponse;
import org.example.aiwear.entity.User;
import org.example.aiwear.mapper.UserMapper;
import org.example.aiwear.util.EmailService;
import org.example.aiwear.util.JWTUtil;
import org.example.aiwear.util.VerificationCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = UserServiceImpl.class)
class UserServiceImplTest {

    @MockBean
    private EmailService emailService;
    @MockBean
    private VerificationCodeService verificationCodeService;
    @MockBean
    private UserMapper userMapper;
    @MockBean
    private JWTUtil jwtUtil;

    @Autowired
    private UserServiceImpl userService;

    // 测试说明：验证验证码未过期时，重复发送会抛出异常。
    @Test
    void sendVerificationCode_shouldThrowWhenCodeStillValid() {
        SendVerificationCodeRequest req = new SendVerificationCodeRequest();
        req.setEmail("a@test.com");
        when(verificationCodeService.hasKey("a@test.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.sendVerificationCode(req));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证发送验证码成功时会生成并保存验证码。
    @Test
    void sendVerificationCode_shouldSaveAndSendMail() {
        SendVerificationCodeRequest req = new SendVerificationCodeRequest();
        req.setEmail("a@test.com");

        when(verificationCodeService.hasKey("a@test.com")).thenReturn(false);
        when(verificationCodeService.generateCode()).thenReturn("123456");
        when(emailService.sendVerificationCodeEmail("a@test.com", "123456")).thenReturn(true);

        boolean success = userService.sendVerificationCode(req);

        assertThat(success).isTrue();
        verify(verificationCodeService).saveCode("a@test.com", "123456");
    }

    // 测试说明：验证邮箱登录缺少验证码时会抛出异常。
    @Test
    void auth_email_shouldThrowWhenCodeIsBlank() {
        AuthRequest req = new AuthRequest();
        req.setAccount("a@test.com");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.auth(req));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证邮箱登录验证码错误时会抛出异常。
    @Test
    void auth_email_shouldThrowWhenCodeInvalid() {
        AuthRequest req = new AuthRequest();
        req.setAccount("a@test.com");
        req.setVerificationCode("1234");

        when(userMapper.selectOne(any())).thenReturn(null);
        when(verificationCodeService.verifyCode("a@test.com", "1234")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.auth(req));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证邮箱新用户会完成注册并返回 token。
    @Test
    void auth_email_shouldRegisterUserAndReturnToken() {
        AuthRequest req = new AuthRequest();
        req.setAccount("a@test.com");
        req.setVerificationCode("1234");

        when(userMapper.selectOne(any())).thenReturn(null);
        when(verificationCodeService.verifyCode("a@test.com", "1234")).thenReturn(true);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(11L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        when(jwtUtil.generateToken(any(User.class))).thenReturn("token-1");

        AuthResponse response = userService.auth(req);

        assertThat(response.getToken()).isEqualTo("token-1");
        assertThat(response.getUserId()).isEqualTo(11L);
    }

    // 测试说明：验证用户名密码方式下新用户会自动注册。
    @Test
    void auth_username_shouldRegisterWhenUserNotExists() {
        AuthRequest req = new AuthRequest();
        req.setAccount("jack");
        req.setPassword("abc123");

        when(userMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(22L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        when(jwtUtil.generateToken(any(User.class))).thenReturn("token-2");

        AuthResponse response = userService.auth(req);

        verify(userMapper).insert(any(User.class));
        assertThat(response.getToken()).isEqualTo("token-2");
    }

    // 测试说明：验证用户名密码不匹配时会抛出异常。
    @Test
    void auth_username_shouldThrowWhenPasswordMismatch() {
        AuthRequest req = new AuthRequest();
        req.setAccount("jack");
        req.setPassword("wrong");

        User existing = new User();
        existing.setUsername("jack");
        existing.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("another"));
        when(userMapper.selectOne(any())).thenReturn(existing);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.auth(req));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证用户名密码匹配时会返回 token。
    @Test
    void auth_username_shouldReturnTokenWhenPasswordMatches() {
        AuthRequest req = new AuthRequest();
        req.setAccount("jack");
        req.setPassword("secret");

        User existing = new User();
        existing.setId(7L);
        existing.setUsername("jack");
        existing.setPasswordHash(new BCryptPasswordEncoder().encode("secret"));
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(jwtUtil.generateToken(existing)).thenReturn("token-3");

        AuthResponse response = userService.auth(req);

        assertThat(response.getToken()).isEqualTo("token-3");
        assertThat(response.getUsername()).isEqualTo("jack");
    }

    // 测试说明：验证登出时会解析并移除 token。
    @Test
    void logout_shouldParseAndRemoveToken() {
        when(jwtUtil.parseToken("Bearer x")).thenReturn("token-x");
        when(jwtUtil.removeToken("token-x")).thenReturn(true);

        boolean success = userService.logout("Bearer x");

        assertThat(success).isTrue();
        verify(jwtUtil).parseToken("Bearer x");
        verify(jwtUtil).removeToken("token-x");
    }
}

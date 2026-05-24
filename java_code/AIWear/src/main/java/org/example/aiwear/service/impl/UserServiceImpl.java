package org.example.aiwear.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aiwear.dto.request.AuthRequest;
import org.example.aiwear.dto.request.SendVerificationCodeRequest;
import org.example.aiwear.dto.response.AuthResponse;
import org.example.aiwear.entity.User;
import org.example.aiwear.mapper.UserMapper;
import org.example.aiwear.service.UserService;
import org.example.aiwear.util.EmailService;
import org.example.aiwear.util.JWTUtil;
import org.example.aiwear.util.VerificationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户实现类
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    //邮件服务
    @Autowired
    private EmailService emailService;

    //验证码服务
    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JWTUtil jwtUtil;

    //密码加密 /解密 ,校验的对象
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    //发送验证码
    @Override
    public boolean sendVerificationCode(SendVerificationCodeRequest request) {
        // 获取邮箱
        String email = request.getEmail();
        // 验证码是否已经发送 或 存在
        if(verificationCodeService.hasKey(email)){
            throw new IllegalArgumentException("验证码还没过期，请勿重复发送");
        }
        // 生成验证码
        String verificationCode = verificationCodeService.generateCode();
        // 保存验证码到 Redis
        verificationCodeService.saveCode(email, verificationCode);

        // 发送邮件
        return emailService.sendVerificationCodeEmail(email, verificationCode);
    }

    //认证
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse auth(AuthRequest request) {
        // 1. 获取⽤⼾名
        String account = request.getAccount();
        //2.判断当前认证⽅式
        boolean isEmail = account.contains("@");
        // 3. 查询⽤⼾是否存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, account); //构建查询条件
        User user = userMapper.selectOne(queryWrapper);
        //4.邮箱验证码方式
        if(isEmail){
            String code = request.getVerificationCode();
            if(code == null || code.isEmpty()){
                throw new IllegalArgumentException("验证码不能为空");
            }
            //5. 验证验证码
            if(!verificationCodeService.verifyCode(account, code)){
                throw new IllegalArgumentException("验证码不存在或已过期");
            }
            //6.处理新老用户
            if(user == null){
                //新用户
                user = new User();
                user.setUsername(account);
                user.setEmail(account);
                //用户加入数据库
                userMapper.insert(user);
                log.info("新用户注册成功：邮箱{}", user.getEmail());
            }
        }else{
            //7.用户名密码方式
            if(user == null){
                //新用户
                user = new User();
                user.setUsername(account);
                if(request.getPassword() == null || request.getPassword().isEmpty()){
                    throw new IllegalArgumentException("密码不能为空");
                }
                //加密密码
                user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                userMapper.insert(user);
                log.info("新用户注册成功：用户名{}", user.getUsername());
            }else{
                //老用户
                //校验密码
                if(request.getPassword() == null || request.getPassword().isEmpty()){
                    throw new IllegalArgumentException("密码不能为空");
                }
                if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())){
                    throw new IllegalArgumentException("用户名或密码错误");
                }
            }
        }
        // 新老用户都要返回令牌
        return createResponse(user);
    }

    //返回认证的响应
    private AuthResponse createResponse(User user) {
        AuthResponse response = new AuthResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setToken(jwtUtil.generateToken(user));
        return response;
    }


    @Override
    public boolean logout(String authorization) {
        // 解析令牌
        String token = jwtUtil.parseToken(authorization);
        // 删除令牌
        return jwtUtil.removeToken(token);
    }
}

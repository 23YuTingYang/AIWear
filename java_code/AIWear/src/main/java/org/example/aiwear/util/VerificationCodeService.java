package org.example.aiwear.util;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务类
 */
@Slf4j
@Service
public class VerificationCodeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //redis 前缀
    private static final String VERIFICATION_CODE_PREFIX = "verification:code:";

    //过期时间
    private static final int EXPIRE_TIME = 5;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    //生成6位的随机验证码
    public String generateCode(){
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        //将整数验证码转换成字符串返回
        return String.valueOf(code);
    }

    //保存验证码
    //email : 邮箱
    //code : 验证码
    public void saveCode(String email, String code){
        String key = VERIFICATION_CODE_PREFIX + email;
        //过期时间 5分钟
        stringRedisTemplate.opsForValue().set(key, code, EXPIRE_TIME, TimeUnit.MINUTES);
    }

    //验证码是不是存在
    public boolean hasKey(String email){
        String key = VERIFICATION_CODE_PREFIX + email;
        return stringRedisTemplate.hasKey(key);
    }

    //校验验证码
    // 校验验证码
    public boolean verifyCode(String email, String code) {
        String key = VERIFICATION_CODE_PREFIX + email;

        //判断验证码是否存在
        if (!stringRedisTemplate.hasKey(key)) {
            log.warn("验证码不存在或已过期 邮箱:{}", email);
            return false;
        }

        // 从 Redis 取出保存的验证码
        String storedCode = stringRedisTemplate.opsForValue().get(key);

        // 验证码不匹配，不删除，允许用户重新输入
        if (!code.equals(storedCode)) {
            log.warn("验证码错误 邮箱:{}", email);
            return false;
        }

        // 验证码匹配后删除，防止重复使用
        stringRedisTemplate.delete(key);
        return true;
    }

}

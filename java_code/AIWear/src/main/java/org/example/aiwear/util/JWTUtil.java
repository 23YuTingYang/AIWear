package org.example.aiwear.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.example.aiwear.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JWT工具类
 * 作用：
 * 1. 根据用户信息生成 JWT 令牌
 * 2. 将生成后的令牌缓存到 Redis 中，避免同一个用户重复生成
 */


@Slf4j
@Component
public class JWTUtil {

    // Redis 操作对象，用来存取用户令牌
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // JWT 的签名密钥，从 application.yml 中的 jwt.secret 读取
    @Value("${jwt.secret}")
    private String secret;

    // JWT 的过期时间，从 application.yml 中的 jwt.expiration 读取，当前代码按“小时”处理
    @Value("${jwt.expiration}")
    private Long expiration;

    // Redis 中保存用户令牌的键前缀，完整键示例：jwt:user:token:1
    private static final String USER_TOKEN_KEY_PREFIX = "jwt:user:token:";

    /**
     * 根据用户信息生成 JWT 令牌。
     * 如果 Redis 中已经存在该用户的令牌，则直接返回旧令牌；
     * 如果不存在，则创建新的令牌，并写入 Redis。
     *
     * @param user 当前登录成功的用户
     * @return JWT 令牌字符串
     */
    public String generateToken(User user) {
        // 每个用户对应一个固定的 Redis 键
        String userKey = USER_TOKEN_KEY_PREFIX + user.getId();

        // 1. 判断用户是否已经生成过令牌，存在则直接复用
        if(stringRedisTemplate.hasKey(userKey)){
            return stringRedisTemplate.opsForValue().get(userKey);
        }

        // 2. 创建声明，声明是 JWT 中保存的业务数据
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());

        // 3. 设置签发时间和过期时间
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration * 1000 * 60 * 60);

        // 4. 根据配置中的 secret 生成 HMAC 签名密钥
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        // 5. 生成 JWT 令牌
        String token = Jwts.builder()
                .setClaims(claims)    // 把用户信息放进令牌
                .setIssuedAt(now)     //设置签发时间
                .setExpiration(expirationDate)  //设置过期时间
                .signWith(key, SignatureAlgorithm.HS256)  //用密钥和 HS256 算法签名
                .compact();      // 生成最终令牌字符串

        // 6. 将令牌保存到 Redis，过期时间单位为小时
        stringRedisTemplate.opsForValue().set(
                userKey,
                token,
                expiration,
                TimeUnit.HOURS
        );
        return token;
    }

    //请求头中获取 后面还有一个空格
    private static final String BEARER_PREFIX = "Bearer ";

    // 截取令牌
    public String parseToken(String authorization) {
        // 检查请求头中是否有令牌
        if (authorization == null || authorization.isBlank()) {
            throw new IllegalArgumentException("缺少请求头令牌");
        }

        // 检查请求头令牌格式
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("请求头令牌格式错误");
        }

        return authorization.substring(BEARER_PREFIX.length());
    }

    // 从令牌中解析出声明，上面的方法已经定义过 Map<String, Object> claims。
    // 生成令牌和解析令牌必须使用同一个密钥。
    private Claims getClaims(String token) {
        // 根据配置中的 secret 生成 HMAC 签名密钥
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parserBuilder()
                .setSigningKey(key)  // 设置解析令牌时使用的签名密钥。
                .build()   // 构建解析器对象。
                .parseClaimsJws(token) // 解析并校验令牌。
                                        // 1. 校验令牌格式是否正确
                                        // 2. 校验签名是否正确
                                        // 3. 校验令牌是否过期
                                        // 4. 解析令牌里的声明
                .getBody();   // 取出令牌里的负载，也就是声明。
    }

    // 获取令牌中的用户ID
    public Long getUserId(String token){
        Claims claims = getClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj == null) {
            throw new RuntimeException("token中缺少userId");
        }
        Long userId = Long.valueOf(userIdObj.toString());  //把字符串转化为 Long
        return userId;
    }

    // 删除令牌
    public boolean removeToken(String token){
        if(token == null || token.isBlank()){
            return true;
        }
        Claims claims = getClaims(token);

        Object userIdObj = claims.get("userId");
        if (userIdObj == null) {
            throw new RuntimeException("token中缺少userId");
        }
        Long userId = Long.valueOf(userIdObj.toString());  //把字符串转化为 Long

        String userKey = USER_TOKEN_KEY_PREFIX + userId;

        if(stringRedisTemplate.hasKey(userKey)) {
            return stringRedisTemplate.delete(userKey);
        }
        return false;
    }

}

package com.aurora.service.impl;

import com.aurora.model.dto.UserDetailsDTO;
import com.aurora.service.RedisService;
import com.aurora.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static com.aurora.constant.AuthConstant.*;
import static com.aurora.constant.RedisConstant.LOGIN_USER;


@Service
public class TokenServiceImpl implements TokenService {

    @Autowired
    private RedisService redisService;

    @Override
    public String createToken(UserDetailsDTO userDetailsDTO) {
        refreshToken(userDetailsDTO);
        String userId = userDetailsDTO.getId().toString();
        return createToken(userId);
    }

    @Override
    public String createToken(String subject) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        SecretKey secretKey = generalKey();
        return Jwts.builder().setId(getUuid()).setSubject(subject)
                .setIssuer("huaweimian")
                .signWith(signatureAlgorithm, secretKey).compact();
    }

    @Override
    public void refreshToken(UserDetailsDTO userDetailsDTO) {
        LocalDateTime currentTime = LocalDateTime.now();
        userDetailsDTO.setExpireTime(currentTime.plusSeconds(EXPIRE_TIME));
        String userId = userDetailsDTO.getId().toString();
        redisService.hSet(LOGIN_USER, userId, userDetailsDTO, EXPIRE_TIME);
    }

    @Override
    public void renewToken(UserDetailsDTO userDetailsDTO) {
        LocalDateTime expireTime = userDetailsDTO.getExpireTime();
        LocalDateTime currentTime = LocalDateTime.now();
        if (Duration.between(currentTime, expireTime).toMinutes() <= TWENTY_MINUTES) {
            refreshToken(userDetailsDTO);
        }
    }

    @Override
    public Claims parseToken(String token) {
        SecretKey secretKey = generalKey();
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
    }

    /**
     * 获取登录用户信息
     * @param request 请求参数
     * @return UserDetailsDTO
     */
    @Override
    public UserDetailsDTO getUserDetailDTO(HttpServletRequest request) {
        // 如果 request 里的 Authorization 不为空则赋值给 token，为空则 将 "" 赋给token；并且会将 `Bearer` 替换成 ""
        String token = Optional.ofNullable(request.getHeader(TOKEN_HEADER)).orElse("").replaceFirst(TOKEN_PREFIX, "");

        // token 不为空，解析 token 获取userId，并将从 redis 查询用户信息 `UserDetailsDTO` 返回
        if (StringUtils.hasText(token) && !token.equals("null")) {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            return (UserDetailsDTO) redisService.hGet(LOGIN_USER, userId);
        }

        // token 为空
        return null;
    }

    @Override
    public void delLoginUser(Integer userId) {
        redisService.hDel(LOGIN_USER, String.valueOf(userId));
    }

    public String getUuid() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    public SecretKey generalKey() {
        byte[] encodedKey = Base64.getDecoder().decode(SECRET);
        return new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES");
    }

}

package com.aurora.handler;

import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AccessDecisionManagerImpl implements AccessDecisionManager {

    /**
     * 管理器
     * 决策请求能不能通过，即判断用户是否有权限
     * @param authentication 认证令牌，表示认证请求的时候 ：用户提供的身份信息和认证信息，认证通过则有角色信息
     * @param o
     * @param collection 该请求需要的角色信息，里面有由 FilterInvocationSecurityMetadataSourceImpl 所查询到的 roles集合 的权限信息
     * @throws AccessDeniedException
     * @throws InsufficientAuthenticationException
     */
    @Override
    public void decide(Authentication authentication, Object o, Collection<ConfigAttribute> collection) throws AccessDeniedException, InsufficientAuthenticationException {
        // 获取用户所拥有的角色集合
        List<String> permissionList = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // 判断 用户是否有该请求所需要的角色信息
        for (ConfigAttribute item : collection) {
            if (permissionList.contains(item.getAttribute())) {
                return;
            }
        }
        throw new AccessDeniedException("权限不足");
    }

    @Override
    public boolean supports(ConfigAttribute configAttribute) {
        return true;
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return true;
    }
}

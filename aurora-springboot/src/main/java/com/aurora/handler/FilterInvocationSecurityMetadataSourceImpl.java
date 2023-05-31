package com.aurora.handler;

import com.aurora.model.dto.ResourceRoleDTO;
import com.aurora.mapper.RoleMapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;

@Component
public class FilterInvocationSecurityMetadataSourceImpl implements FilterInvocationSecurityMetadataSource {


    @Autowired
    private RoleMapper roleMapper;

    private static List<ResourceRoleDTO> resourceRoleList;

    /**
     * 结合 t_resource、 t_role_resource、 t_role 三张表查询 resource 和 role 相关信息
     */
    @PostConstruct
    private void loadResourceRoleList() {
        resourceRoleList = roleMapper.listResourceRoles();
    }

    public void clearDataSource() {
        resourceRoleList = null;
    }

    /**
     * 向security的注入所访问路径所需要的角色权限
     * getAttributes（）是获取受保护的对象，并且可以自定义 attribute
     * @param object 三个属性：过滤链（chain）、request、response
     * @return Collection<ConfigAttribute>
     * @throws IllegalArgumentException
     */
    @Override
    public Collection<ConfigAttribute> getAttributes(Object object) throws IllegalArgumentException {
        // 如果 `resourceRoleList` 为空，则从数据库查询
        if (CollectionUtils.isEmpty(resourceRoleList)) {
            this.loadResourceRoleList();
        }
        // FilterInvocation 可以安全拿到 security 里的 request 和其他信息
        FilterInvocation fi = (FilterInvocation) object;
        String method = fi.getRequest().getMethod();
        String url = fi.getRequest().getRequestURI();

        // AntPathMatcher 是可以对路径进行动态匹配的一个工具类
        AntPathMatcher antPathMatcher = new AntPathMatcher();

        // 判断 resourceRoleList 里是否由与请求的 url 和 method 匹配的数据
        for (ResourceRoleDTO resourceRoleDTO : resourceRoleList) {
            if (antPathMatcher.match(resourceRoleDTO.getUrl(), url) && resourceRoleDTO.getRequestMethod().equals(method)) {
                // 找出匹配数据并封装角色名返回集合
                List<String> roleList = resourceRoleDTO.getRoleList();
                if (CollectionUtils.isEmpty(roleList)) {
                    return SecurityConfig.createList("disable");
                }
                // 用 security 的方法创造数组返回
                return SecurityConfig.createList(roleList.toArray(new String[]{}));
            }
        }
        return null;
    }

    @Override
    public Collection<ConfigAttribute> getAllConfigAttributes() {
        return null;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return FilterInvocation.class.isAssignableFrom(clazz);
    }
}

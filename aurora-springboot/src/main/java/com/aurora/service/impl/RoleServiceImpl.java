package com.aurora.service.impl;

import com.aurora.constant.CommonConstant;
import com.aurora.model.dto.RoleDTO;
import com.aurora.model.dto.UserRoleDTO;
import com.aurora.entity.Role;
import com.aurora.entity.RoleMenu;
import com.aurora.entity.RoleResource;
import com.aurora.entity.UserRole;
import com.aurora.exception.BizException;
import com.aurora.handler.FilterInvocationSecurityMetadataSourceImpl;
import com.aurora.mapper.RoleMapper;
import com.aurora.mapper.UserRoleMapper;
import com.aurora.service.RoleMenuService;
import com.aurora.service.RoleResourceService;
import com.aurora.service.RoleService;
import com.aurora.util.BeanCopyUtil;
import com.aurora.util.PageUtil;
import com.aurora.model.vo.ConditionVO;
import com.aurora.model.dto.PageResultDTO;
import com.aurora.model.vo.RoleVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RoleResourceService roleResourceService;

    @Autowired
    private RoleMenuService roleMenuService;

    @Autowired
    private FilterInvocationSecurityMetadataSourceImpl filterInvocationSecurityMetadataSource;

    /**
     * 查询所有用户信息
     * @return List<UserRoleDTO>
     */
    @Override
    public List<UserRoleDTO> listUserRoles() {
        List<Role> roleList = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .select(Role::getId, Role::getRoleName));
        return BeanCopyUtil.copyList(roleList, UserRoleDTO.class);
    }

    /**
     * 根据关键字查询所有相关信息，返回条件条目
     * @param conditionVO 查询条件
     * @return 查询结果页
     */
    @SneakyThrows
    @Override
    public PageResultDTO<RoleDTO> listRoles(ConditionVO conditionVO) {
        // 对role名称 `keywords` 的模糊查询，查询出role集合
        LambdaQueryWrapper<Role> queryWrapper = new LambdaQueryWrapper<Role>()
                .like(StringUtils.isNotBlank(conditionVO.getKeywords()), Role::getRoleName, conditionVO.getKeywords());
        // CompletableFuture能够主动设置计算的结果值（主动终结计算过程，即completable），从而在某些场景下主动结束阻塞等待。
        CompletableFuture<Integer> asyncCount = CompletableFuture.supplyAsync(() -> roleMapper.selectCount(queryWrapper));

        // 查询出满足condition和页面需求的数据
        List<RoleDTO> roleDTOs = roleMapper.listRoles(PageUtil.getLimitCurrent(), PageUtil.getSize(), conditionVO);

        // 此时asyncCount.get()则是当前页码数据条数
        return new PageResultDTO<>(roleDTOs, asyncCount.get());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveOrUpdateRole(RoleVO roleVO) {
        Role roleCheck = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .select(Role::getId)
                .eq(Role::getRoleName, roleVO.getRoleName()));
        if (Objects.nonNull(roleCheck) && !(roleCheck.getId().equals(roleVO.getId()))) {
            throw new BizException("该角色存在");
        }
        Role role = Role.builder()
                .id(roleVO.getId())
                .roleName(roleVO.getRoleName())
                .isDisable(CommonConstant.FALSE)
                .build();
        this.saveOrUpdate(role);
        if (Objects.nonNull(roleVO.getResourceIds())) {
            if (Objects.nonNull(roleVO.getId())) {
                roleResourceService.remove(new LambdaQueryWrapper<RoleResource>()
                        .eq(RoleResource::getRoleId, roleVO.getId()));
            }
            List<RoleResource> roleResourceList = roleVO.getResourceIds().stream()
                    .map(resourceId -> RoleResource.builder()
                            .roleId(role.getId())
                            .resourceId(resourceId)
                            .build())
                    .collect(Collectors.toList());
            roleResourceService.saveBatch(roleResourceList);
            filterInvocationSecurityMetadataSource.clearDataSource();
        }
        if (Objects.nonNull(roleVO.getMenuIds())) {
            if (Objects.nonNull(roleVO.getId())) {
                roleMenuService.remove(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleVO.getId()));
            }
            List<RoleMenu> roleMenuList = roleVO.getMenuIds().stream()
                    .map(menuId -> RoleMenu.builder()
                            .roleId(role.getId())
                            .menuId(menuId)
                            .build())
                    .collect(Collectors.toList());
            roleMenuService.saveBatch(roleMenuList);
        }
    }

    @Override
    public void deleteRoles(List<Integer> roleIdList) {
        Integer count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .in(UserRole::getRoleId, roleIdList));
        if (count > 0) {
            throw new BizException("该角色下存在用户");
        }
        roleMapper.deleteBatchIds(roleIdList);
    }

}

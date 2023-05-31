package com.aurora.service.impl;

import com.aurora.model.dto.LabelOptionDTO;
import com.aurora.model.dto.MenuDTO;
import com.aurora.model.dto.UserMenuDTO;
import com.aurora.entity.Menu;
import com.aurora.entity.RoleMenu;
import com.aurora.exception.BizException;
import com.aurora.mapper.MenuMapper;
import com.aurora.mapper.RoleMenuMapper;
import com.aurora.service.MenuService;
import com.aurora.util.BeanCopyUtil;
import com.aurora.util.UserUtil;
import com.aurora.model.vo.ConditionVO;
import com.aurora.model.vo.IsHiddenVO;
import com.aurora.model.vo.MenuVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.aurora.constant.CommonConstant.COMPONENT;
import static com.aurora.constant.CommonConstant.TRUE;

@Service
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private RoleMenuMapper roleMenuMapper;

    /**
     * 查询菜单列表信息
     * @param conditionVO 查询条件
     * @return List<MenuDTO> 菜单的树状结构
     */
    @Override
    public List<MenuDTO> listMenus(ConditionVO conditionVO) {
        // 有查询条件查询相关内容，没有则查询所有
        List<Menu> menus = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .like(StringUtils.isNotBlank(conditionVO.getKeywords()), Menu::getName, conditionVO.getKeywords()));

        // 将菜单列表过滤掉 parentId 不为空的部分；并按按照orderNum排序
        List<Menu> catalogs = listCatalogs(menus);
        // 过滤掉列表中 parentId 为空的数据，并将剩下的数据按 parentId 分组，以 map 形式返回
        Map<Integer, List<Menu>> childrenMap = getMenuMap(menus);
        // 将 menu 转换为 menuDTO 的列表，并按 MenuDTO 的 OrderNum 排序
        List<MenuDTO> menuDTOs = catalogs.stream().map(item -> {
            MenuDTO menuDTO = BeanCopyUtil.copyObject(item, MenuDTO.class);
            List<MenuDTO> list = BeanCopyUtil.copyList(childrenMap.get(item.getId()), MenuDTO.class).stream()
                    .sorted(Comparator.comparing(MenuDTO::getOrderNum))
                    .collect(Collectors.toList());
            menuDTO.setChildren(list);
            childrenMap.remove(item.getId());
            return menuDTO;
        }).sorted(Comparator.comparing(MenuDTO::getOrderNum)).collect(Collectors.toList());

        // 在将 childrenMap 处理完之后，如果 map 里面还有数据，则将剩余数据转换为 MenuDTO 并按照 OrderNum 排序，存进 `menuDTOs`
        if (CollectionUtils.isNotEmpty(childrenMap)) {
            List<Menu> childrenList = new ArrayList<>();
            childrenMap.values().forEach(childrenList::addAll);
            List<MenuDTO> childrenDTOList = childrenList.stream()
                    .map(item -> BeanCopyUtil.copyObject(item, MenuDTO.class))
                    .sorted(Comparator.comparing(MenuDTO::getOrderNum))
                    .collect(Collectors.toList());
            menuDTOs.addAll(childrenDTOList);
        }
        return menuDTOs;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveOrUpdateMenu(MenuVO menuVO) {
        Menu menu = BeanCopyUtil.copyObject(menuVO, Menu.class);
        this.saveOrUpdate(menu);
    }

    @Override
    public void updateMenuIsHidden(IsHiddenVO isHiddenVO) {
        Menu menu = BeanCopyUtil.copyObject(isHiddenVO, Menu.class);
        menuMapper.updateById(menu);
    }

    @Override
    public void deleteMenu(Integer menuId) {
        Integer count = roleMenuMapper.selectCount(new LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getMenuId, menuId));
        if (count > 0) {
            throw new BizException("菜单下有角色关联");
        }
        List<Integer> menuIds = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                        .select(Menu::getId)
                        .eq(Menu::getParentId, menuId))
                .stream()
                .map(Menu::getId)
                .collect(Collectors.toList());
        menuIds.add(menuId);
        menuMapper.deleteBatchIds(menuIds);
    }

    /**
     * 查看角色菜单选项，以两层菜单结构返回
     * @return List<LabelOptionDTO> ： 树结构的菜单列表
     */
    @Override
    public List<LabelOptionDTO> listMenuOptions() {
        // 所有菜单
        List<Menu> menus = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .select(Menu::getId, Menu::getName, Menu::getParentId, Menu::getOrderNum));
        // 所有第一层菜单
        List<Menu> catalogs = listCatalogs(menus);
        // 将所有子菜单分组
        Map<Integer, List<Menu>> childrenMap = getMenuMap(menus);

        return catalogs.stream().map(item -> {
            List<LabelOptionDTO> list = new ArrayList<>();
            List<Menu> children = childrenMap.get(item.getId());
            if (CollectionUtils.isNotEmpty(children)) {
                // 将每一层的子菜单排序并也建成`LabelOptionDTO`格式
                list = children.stream()
                        .sorted(Comparator.comparing(Menu::getOrderNum))
                        .map(menu -> LabelOptionDTO.builder()
                                .id(menu.getId())
                                .label(menu.getName())
                                .build())
                        .collect(Collectors.toList());
            }
            //对第一层菜单建成`LabelOptionDTO`形式并返回
            return LabelOptionDTO.builder()
                    .id(item.getId())
                    .label(item.getName())
                    .children(list)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 查询主页面各模块的权限
     * @return List<UserMenuDTO>
     */
    @Override
    public List<UserMenuDTO> listUserMenus() {
        List<Menu> menus = menuMapper.listMenusByUserInfoId(UserUtil.getUserDetailsDTO().getUserInfoId());
        List<Menu> catalogs = listCatalogs(menus);
        Map<Integer, List<Menu>> childrenMap = getMenuMap(menus);
        return convertUserMenuList(catalogs, childrenMap);
    }

    /**
     * 将菜单列表过滤掉parentId不为空的部分；并按按照orderNum排序
     * @param menus 菜单列表
     * @return 父母菜单
     */
    private List<Menu> listCatalogs(List<Menu> menus) {
        return menus.stream()
                .filter(item -> Objects.isNull(item.getParentId()))
                .sorted(Comparator.comparing(Menu::getOrderNum))
                .collect(Collectors.toList());
    }

    /**
     * 过滤掉列表中 parentId 为空的数据，并将剩下的数据按 parentId 分组，以 map 形式返回
     * @param menus 菜单列表
     * @return Map<Integer, List<Menu>>
     */
    private Map<Integer, List<Menu>> getMenuMap(List<Menu> menus) {
        return menus.stream()
                .filter(item -> Objects.nonNull(item.getParentId()))
                .collect(Collectors.groupingBy(Menu::getParentId));
    }

    /**
     * 将参数整合为树型结构，树型菜单
     * @param catalogList 父母菜单目录
     * @param childrenMap 子菜单目录
     * @return List<UserMenuDTO>
     */
    private List<UserMenuDTO> convertUserMenuList(List<Menu> catalogList, Map<Integer, List<Menu>> childrenMap) {
        return catalogList.stream().map(item -> {
            UserMenuDTO userMenuDTO = new UserMenuDTO();
            List<UserMenuDTO> list = new ArrayList<>();
            List<Menu> children = childrenMap.get(item.getId());
            if (CollectionUtils.isNotEmpty(children)) {
                userMenuDTO = BeanCopyUtil.copyObject(item, UserMenuDTO.class);
                list = children.stream()
                        .sorted(Comparator.comparing(Menu::getOrderNum))
                        .map(menu -> {
                            UserMenuDTO dto = BeanCopyUtil.copyObject(menu, UserMenuDTO.class);
                            dto.setHidden(menu.getIsHidden().equals(TRUE));
                            return dto;
                        })
                        .collect(Collectors.toList());
            } else {
                userMenuDTO.setPath(item.getPath());
                userMenuDTO.setComponent(COMPONENT);
                list.add(UserMenuDTO.builder()
                        .path("")
                        .name(item.getName())
                        .icon(item.getIcon())
                        .component(item.getComponent())
                        .build());
            }
            userMenuDTO.setHidden(item.getIsHidden().equals(TRUE));
            userMenuDTO.setChildren(list);
            return userMenuDTO;
        }).collect(Collectors.toList());
    }

}

package com.aurora.service.impl;

import com.aurora.model.dto.LabelOptionDTO;
import com.aurora.model.dto.ResourceDTO;
import com.aurora.entity.Resource;
import com.aurora.entity.RoleResource;
import com.aurora.exception.BizException;
import com.aurora.handler.FilterInvocationSecurityMetadataSourceImpl;
import com.aurora.mapper.ResourceMapper;
import com.aurora.mapper.RoleResourceMapper;
import com.aurora.service.ResourceService;
import com.aurora.util.BeanCopyUtil;
import com.aurora.model.vo.ConditionVO;
import com.aurora.model.vo.ResourceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.aurora.constant.CommonConstant.FALSE;

@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceMapper, Resource> implements ResourceService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private RoleResourceMapper roleResourceMapper;


    @Autowired
    private FilterInvocationSecurityMetadataSourceImpl filterInvocationSecurityMetadataSource;

    @SuppressWarnings("all")
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void importSwagger() {
        this.remove(null);
        roleResourceMapper.delete(null);
        List<Resource> resources = new ArrayList<>();
        Map<String, Object> data = restTemplate.getForObject("http://localhost:8080/v2/api-docs", Map.class);
        List<Map<String, String>> tagList = (List<Map<String, String>>) data.get("tags");
        tagList.forEach(item -> {
            Resource resource = Resource.builder()
                    .resourceName(item.get("name"))
                    .isAnonymous(FALSE)
                    .createTime(LocalDateTime.now())
                    .build();
            resources.add(resource);
        });
        this.saveBatch(resources);
        Map<String, Integer> permissionMap = resources.stream()
                .collect(Collectors.toMap(Resource::getResourceName, Resource::getId));
        resources.clear();
        Map<String, Map<String, Map<String, Object>>> path = (Map<String, Map<String, Map<String, Object>>>) data.get("paths");
        path.forEach((url, value) -> value.forEach((requestMethod, info) -> {
            String permissionName = info.get("summary").toString();
            List<String> tag = (List<String>) info.get("tags");
            Integer parentId = permissionMap.get(tag.get(0));
            Resource resource = Resource.builder()
                    .resourceName(permissionName)
                    .url(url.replaceAll("\\{[^}]*\\}", "*"))
                    .parentId(parentId)
                    .requestMethod(requestMethod.toUpperCase())
                    .isAnonymous(FALSE)
                    .createTime(LocalDateTime.now())
                    .build();
            resources.add(resource);
        }));
        this.saveBatch(resources);
    }

    @Override
    public void saveOrUpdateResource(ResourceVO resourceVO) {
        Resource resource = BeanCopyUtil.copyObject(resourceVO, Resource.class);
        this.saveOrUpdate(resource);
        filterInvocationSecurityMetadataSource.clearDataSource();
    }

    @Override
    public void deleteResource(Integer resourceId) {
        Integer count = roleResourceMapper.selectCount(new LambdaQueryWrapper<RoleResource>()
                .eq(RoleResource::getResourceId, resourceId));
        if (count > 0) {
            throw new BizException("该资源下存在角色");
        }
        List<Integer> resourceIds = resourceMapper.selectList(new LambdaQueryWrapper<Resource>()
                        .select(Resource::getId).
                        eq(Resource::getParentId, resourceId))
                .stream()
                .map(Resource::getId)
                .collect(Collectors.toList());
        resourceIds.add(resourceId);
        resourceMapper.deleteBatchIds(resourceIds);
    }

    /**
     * 查看接口资源选项，以两层菜单结构返回
     * @param conditionVO 查询条件
     * @return List<ResourceDTO> 两层的树状结构
     */
    @Override
    public List<ResourceDTO> listResources(ConditionVO conditionVO) {
        // 如果查询条件为空，则查询所有，不为空则根据条件查询
        List<Resource> resources = resourceMapper.selectList(new LambdaQueryWrapper<Resource>()
                .like(StringUtils.isNotBlank(conditionVO.getKeywords()), Resource::getResourceName, conditionVO.getKeywords()));
        // 将资源列表过滤掉 parentId 不为空的部分,剩下则都是 父母资源
        List<Resource> parents = listResourceModule(resources);
        // 将所有子资源分组，按 ParentId 进行分组
        Map<Integer, List<Resource>> childrenMap = listResourceChildren(resources);
        // 将父母资源和资源进行树状的整合
        List<ResourceDTO> resourceDTOs = parents.stream().map(item -> {
            ResourceDTO resourceDTO = BeanCopyUtil.copyObject(item, ResourceDTO.class);
            List<ResourceDTO> child = BeanCopyUtil.copyList(childrenMap.get(item.getId()), ResourceDTO.class);
            resourceDTO.setChildren(child);
            childrenMap.remove(item.getId());
            return resourceDTO;
        }).collect(Collectors.toList());
        // 如果 childrenMap 不为空，则再将 Resource 转换为 ResourceDTO，并添加到 `resourceDTOs`
        if (CollectionUtils.isNotEmpty(childrenMap)) {
            List<Resource> childrenList = new ArrayList<>();
            childrenMap.values().forEach(childrenList::addAll);
            List<ResourceDTO> childrenDTOs = childrenList.stream()
                    .map(item -> BeanCopyUtil.copyObject(item, ResourceDTO.class))
                    .collect(Collectors.toList());
            resourceDTOs.addAll(childrenDTOs);
        }
        return resourceDTOs;
    }

    /**
     * 查看角色资源选项，以两层菜单结构返回
     * @return List<LabelOptionDTO> ： 树结构的资源列表
     */
    @Override
    public List<LabelOptionDTO> listResourceOption() {
        // 条件查询
        List<Resource> resources = resourceMapper.selectList(new LambdaQueryWrapper<Resource>()
                .select(Resource::getId, Resource::getResourceName, Resource::getParentId)
                .eq(Resource::getIsAnonymous, FALSE));
        // 所有第一层资源
        List<Resource> parents = listResourceModule(resources);
        // 将所有子资源分组
        Map<Integer, List<Resource>> childrenMap = listResourceChildren(resources);

        return parents.stream().map(item -> {
            List<LabelOptionDTO> list = new ArrayList<>();
            List<Resource> children = childrenMap.get(item.getId());
            if (CollectionUtils.isNotEmpty(children)) {
                // 将每一层的子资源排序并也建成`LabelOptionDTO`格式
                list = children.stream()
                        .map(resource -> LabelOptionDTO.builder()
                                .id(resource.getId())
                                .label(resource.getResourceName())
                                .build())
                        .collect(Collectors.toList());
            }
            //对第一层资源建成`LabelOptionDTO`形式并返回
            return LabelOptionDTO.builder()
                    .id(item.getId())
                    .label(item.getResourceName())
                    .children(list)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 将资源列表过滤掉parentId不为空的部分
     * @param resourceList
     * @return
     */
    private List<Resource> listResourceModule(List<Resource> resourceList) {
        return resourceList.stream()
                .filter(item -> Objects.isNull(item.getParentId()))
                .collect(Collectors.toList());
    }

    /**
     * 过滤掉列表中 parentId 为空的数据，并将剩下的数据按 parentId 分组，以 map 形式返回
     * @param resourceList 资源列表
     * @return Map<Integer, List<Resource>>
     */
    private Map<Integer, List<Resource>> listResourceChildren(List<Resource> resourceList) {
        return resourceList.stream()
                .filter(item -> Objects.nonNull(item.getParentId()))
                .collect(Collectors.groupingBy(Resource::getParentId));
    }

}

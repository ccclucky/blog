package com.aurora.service.impl;

import com.aurora.model.dto.PageResultDTO;
import com.aurora.model.dto.UserDetailsDTO;
import com.aurora.model.dto.UserInfoDTO;
import com.aurora.model.dto.UserOnlineDTO;
import com.aurora.entity.UserAuth;
import com.aurora.entity.UserInfo;
import com.aurora.entity.UserRole;
import com.aurora.enums.FilePathEnum;
import com.aurora.exception.BizException;
import com.aurora.mapper.UserAuthMapper;
import com.aurora.mapper.UserInfoMapper;
import com.aurora.service.RedisService;
import com.aurora.service.TokenService;
import com.aurora.service.UserInfoService;
import com.aurora.service.UserRoleService;
import com.aurora.strategy.context.UploadStrategyContext;
import com.aurora.util.BeanCopyUtil;
import com.aurora.util.UserUtil;
import com.aurora.model.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

import static com.aurora.constant.RedisConstant.USER_CODE_KEY;
import static com.aurora.util.PageUtil.getLimitCurrent;
import static com.aurora.util.PageUtil.getSize;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private UploadStrategyContext uploadStrategyContext;

    @Value("upload.minio.bucketName")
    private String bucketName;


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserInfo(UserInfoVO userInfoVO) {
        UserInfo userInfo = UserInfo.builder()
                .id(UserUtil.getUserDetailsDTO().getUserInfoId())
                .nickname(userInfoVO.getNickname())
                .intro(userInfoVO.getIntro())
                .website(userInfoVO.getWebsite())
                .build();
        userInfoMapper.updateById(userInfo);
    }

    @Override
    public String updateUserAvatar(MultipartFile file) {
        String avatar = uploadStrategyContext.executeUploadStrategy(file, FilePathEnum.AVATAR.getPath());
        UserInfo userInfo = UserInfo.builder()
                .id(UserUtil.getUserDetailsDTO().getUserInfoId())
                .avatar(avatar)
                .build();
        userInfoMapper.updateById(userInfo);
        return avatar;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveUserEmail(EmailVO emailVO) {
        if (Objects.isNull(redisService.get(USER_CODE_KEY + emailVO.getEmail()))) {
            throw new BizException("验证码错误");
        }
        if (!emailVO.getCode().equals(redisService.get(USER_CODE_KEY + emailVO.getEmail()).toString())) {
            throw new BizException("验证码错误！");
        }
        UserInfo userInfo = UserInfo.builder()
                .id(UserUtil.getUserDetailsDTO().getUserInfoId())
                .email(emailVO.getEmail())
                .build();
        userInfoMapper.updateById(userInfo);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserSubscribe(SubscribeVO subscribeVO) {
        UserInfo temp = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getId, subscribeVO.getUserId()));
        if (StringUtils.isEmpty(temp.getEmail())) {
            throw new BizException("邮箱未绑定！");
        }
        UserInfo userInfo = UserInfo.builder()
                .id(subscribeVO.getUserId())
                .isSubscribe(subscribeVO.getIsSubscribe())
                .build();
        userInfoMapper.updateById(userInfo);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserRole(UserRoleVO userRoleVO) {
        UserInfo userInfo = UserInfo.builder()
                .id(userRoleVO.getUserInfoId())
                .nickname(userRoleVO.getNickname())
                .build();
        userInfoMapper.updateById(userInfo);
        userRoleService.remove(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userRoleVO.getUserInfoId()));
        List<UserRole> userRoleList = userRoleVO.getRoleIds().stream()
                .map(roleId -> UserRole.builder()
                        .roleId(roleId)
                        .userId(userRoleVO.getUserInfoId())
                        .build())
                .collect(Collectors.toList());
        userRoleService.saveBatch(userRoleList);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserDisable(UserDisableVO userDisableVO) {
        UserInfo userInfo = UserInfo.builder()
                .id(userDisableVO.getId())
                .isDisable(userDisableVO.getIsDisable())
                .build();
        userInfoMapper.updateById(userInfo);
    }

    /**
     * 查询在线用户
     * @param conditionVO 页码、条数、模糊查询条件
     * @return PageResultDTO<UserOnlineDTO>
     */
    @Override
    public PageResultDTO<UserOnlineDTO> listOnlineUsers(ConditionVO conditionVO) {
        // 从 redis 查询所有 login_user 并用 Map 封装
        Map<String, Object> userMaps = redisService.hGetAll("login_user");
        // 将 userMaps 转化为 List<UserOnlineDTO>
        Collection<Object> values = userMaps.values();
        ArrayList<UserDetailsDTO> userDetailsDTOs = new ArrayList<>();
        for (Object value : values) {
            userDetailsDTOs.add((UserDetailsDTO) value);
        }
        List<UserOnlineDTO> userOnlineDTOs = BeanCopyUtil.copyList(userDetailsDTOs, UserOnlineDTO.class);

        // 如果查询条件有 keywords，将 userOnlineDTOs 的不符合 keywords 的数据过滤掉，如果没有，则不用过滤，然后根据登录时间逆序排序
        List<UserOnlineDTO> onlineUsers = userOnlineDTOs.stream()
                .filter(item -> StringUtils.isBlank(conditionVO.getKeywords()) || item.getNickname().contains(conditionVO.getKeywords()))
                .sorted(Comparator.comparing(UserOnlineDTO::getLastLoginTime).reversed())
                .collect(Collectors.toList());
        // 当前页首条数据的索引
        int fromIndex = getLimitCurrent().intValue();
        // 当前页应该显示的条数
        int size = getSize().intValue();
        // 当前页最后一条数据的索引
        int toIndex = onlineUsers.size() - fromIndex > size ? fromIndex + size : onlineUsers.size();
        // 截取当前页需要显示的数据
        List<UserOnlineDTO> userOnlineList = onlineUsers.subList(fromIndex, toIndex);
        return new PageResultDTO<>(userOnlineList, onlineUsers.size());
    }

    @Override
    public void removeOnlineUser(Integer userInfoId) {
        Integer userId = userAuthMapper.selectOne(new LambdaQueryWrapper<UserAuth>().eq(UserAuth::getUserInfoId, userInfoId)).getId();
        tokenService.delLoginUser(userId);
    }

    @Override
    public UserInfoDTO getUserInfoById(Integer id) {
        UserInfo userInfo = userInfoMapper.selectById(id);
        return BeanCopyUtil.copyObject(userInfo, UserInfoDTO.class);
    }

}

package com.web.project.user.service;

import com.web.project.auth.dto.ResetPasswordDTO;
import com.web.project.user.dto.RebindEmailDTO;
import com.web.project.user.dto.RebindPhoneDTO;
import com.web.project.user.dto.UpdateProfileDTO;
import com.web.project.user.vo.UserInfoVO;

/**
 * 普通用户个人资料业务接口。
 */
public interface UserProfileService {

    /**
     * 查询当前用户资料。
     */
    UserInfoVO getProfile(Long userId);

    /**
     * 修改昵称和头像。
     */
    void updateProfile(Long userId, UpdateProfileDTO updateProfileDTO);

    /**
     * 登录态修改密码。
     */
    void changePassword(Long userId, ResetPasswordDTO resetPasswordDTO);

    /**
     * 换绑手机号。
     */
    void rebindPhone(Long userId, RebindPhoneDTO rebindPhoneDTO);

    /**
     * 换绑邮箱。
     */
    void rebindEmail(Long userId, RebindEmailDTO rebindEmailDTO);
}

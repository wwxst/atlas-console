package com.web.project.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料参数。
 *
 * 只允许修改昵称和头像，联系方式必须通过换绑接口修改。
 *
 * @param nickname 昵称
 * @param avatar   头像地址，可为空
 */
public record UpdateProfileDTO(

        @NotBlank(message = "请输入昵称")
        @Size(max = 50, message = "昵称不能超过50个字符")
        String nickname,

        @Size(max = 255, message = "头像地址不能超过255个字符")
        String avatar
) {
}

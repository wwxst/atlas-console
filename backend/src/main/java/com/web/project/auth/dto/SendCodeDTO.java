package com.web.project.auth.dto;

import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发送验证码请求参数。
 *
 * @param target     手机号或邮箱
 * @param targetType 联系方式类型
 * @param scene      验证码场景
 */
public record SendCodeDTO(

        @NotBlank(message = "请输入手机号或邮箱")
        @Size(max = 100, message = "手机号或邮箱格式不正确")
        String target,

        @NotNull(message = "请选择联系方式类型")
        TargetType targetType,

        @NotNull(message = "请选择验证码场景")
        VerificationScene scene
) {
    @Override
    public String toString() {
        return "SendCodeDTO[target=redacted, targetType=" + targetType + ", scene=" + scene + "]";
    }
}

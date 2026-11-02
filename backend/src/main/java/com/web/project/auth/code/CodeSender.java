package com.web.project.auth.code;

import com.web.project.auth.model.TargetType;

/**
 * 验证码发送接口。
 *
 * 生产默认通过已配置的 SMTP 或短信服务商发送；仅显式设置 AUTH_CODE_SENDER=dev 时使用模拟实现。
 */
public interface CodeSender {

    /**
     * 发送验证码。
     *
     * @param targetType 联系方式类型
     * @param target     规范化后的联系方式
     * @param code       6 位数字验证码
     */
    void send(TargetType targetType, String target, String code);
}

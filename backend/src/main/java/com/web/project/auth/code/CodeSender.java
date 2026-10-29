package com.web.project.auth.code;

import com.web.project.auth.model.TargetType;

/**
 * 验证码发送接口。
 *
 * 当前由开发环境实现模拟发送；真实短信和邮件供应商尚未接入。
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

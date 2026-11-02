package com.web.project.auth.code;

import com.web.project.auth.model.TargetType;
import com.web.project.auth.provider.EmailCodeSender;
import com.web.project.auth.provider.SmsCodeSender;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 根据联系方式类型将验证码发送到真实 SMTP 或短信服务商。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.auth", name = "code-sender", havingValue = "provider", matchIfMissing = true)
public class ConfiguredCodeSender implements CodeSender {

    private final SmsCodeSender smsCodeSender;
    private final EmailCodeSender emailCodeSender;

    @Override
    public void send(TargetType targetType, String target, String code) {
        if (targetType == TargetType.PHONE) {
            smsCodeSender.send(target, code);
        } else {
            emailCodeSender.send(target, code);
        }
    }
}

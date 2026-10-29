package com.web.project.auth.code;

import com.web.project.auth.model.TargetType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 开发环境验证码发送实现。
 *
 * 不接入真实短信或邮件供应商，也不在日志中打印验证码或完整联系方式。
 */
@Slf4j
@Component
public class DevCodeSender implements CodeSender {

    @Override
    public void send(TargetType targetType, String target, String code) {
        log.info("开发环境模拟验证码发送，渠道={}", targetType);
    }
}

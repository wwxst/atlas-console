package com.web.project.channel.service.impl;

import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.entity.SysConfig;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.channel.model.AuthChannel;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 渠道配置业务实现类。
 *
 * 直接读取 MySQL，不增加配置缓存。
 */
@Service
@RequiredArgsConstructor
public class AuthChannelServiceImpl implements AuthChannelService {

    private static final String PHONE_ENABLED = "auth.phone.enabled";
    private static final String PHONE_CODE_ENABLED = "auth.phone.code.enabled";
    private static final String EMAIL_ENABLED = "auth.email.enabled";
    private static final String EMAIL_CODE_ENABLED = "auth.email.code.enabled";

    private final SysConfigMapper sysConfigMapper;

    @Override
    public AuthChannelConfig getChannels() {
        Map<String, String> values = loadValues(List.of(
                PHONE_ENABLED,
                PHONE_CODE_ENABLED,
                EMAIL_ENABLED,
                EMAIL_CODE_ENABLED
        ));

        return new AuthChannelConfig(
                requireBoolean(values, PHONE_ENABLED),
                requireBoolean(values, PHONE_CODE_ENABLED),
                requireBoolean(values, EMAIL_ENABLED),
                requireBoolean(values, EMAIL_CODE_ENABLED)
        );
    }

    @Override
    @Transactional
    public void updateChannel(AuthChannel channel, AuthChannelUpdateDTO dto) {
        /*
         * 在同一事务中锁定两个渠道总开关，再校验和更新。
         * 并发修改共用这组行锁，避免各自关闭一个渠道后两个都关闭。
         */
        Map<String, String> locked = sysConfigMapper
                .selectMasterSwitchesForUpdate()
                .stream()
                .collect(Collectors.toMap(
                        SysConfig::getConfigKey,
                        SysConfig::getConfigValue
                ));

        boolean phoneEnabled = requireBoolean(locked, PHONE_ENABLED);
        boolean emailEnabled = requireBoolean(locked, EMAIL_ENABLED);

        if (channel == AuthChannel.PHONE) {
            phoneEnabled = dto.enabled();
        } else {
            emailEnabled = dto.enabled();
        }

        // 手机和邮箱总开关不能同时关闭
        if (!phoneEnabled && !emailEnabled) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_ALL_CHANNELS);
        }

        if (channel == AuthChannel.PHONE) {
            updateValue(PHONE_ENABLED, dto.enabled());
            updateValue(PHONE_CODE_ENABLED, dto.codeEnabled());
        } else {
            updateValue(EMAIL_ENABLED, dto.enabled());
            updateValue(EMAIL_CODE_ENABLED, dto.codeEnabled());
        }
    }

    private Map<String, String> loadValues(List<String> keys) {
        return sysConfigMapper.selectByKeys(keys)
                .stream()
                .collect(Collectors.toMap(
                        SysConfig::getConfigKey,
                        SysConfig::getConfigValue
                ));
    }

    private boolean requireBoolean(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value != null) {
            if ("true".equalsIgnoreCase(value.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(value.trim())) {
                return false;
            }
        }
        throw new IllegalStateException("认证渠道配置缺失或格式错误：" + key);
    }

    private void updateValue(String key, Boolean value) {
        if (sysConfigMapper.updateValue(key, value.toString()) != 1) {
            throw new IllegalStateException("认证渠道配置未写入预期记录：" + key);
        }
    }
}

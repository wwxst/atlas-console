package com.web.project.channel.service.impl;

import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.entity.SysConfig;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.channel.model.AuthChannel;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthChannelServiceImplTest {

    private final SysConfigMapper sysConfigMapper = mock(SysConfigMapper.class);
    private final AuthChannelServiceImpl service = new AuthChannelServiceImpl(sysConfigMapper);

    private SysConfig config(String key, String value) {
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }

    @Test
    void getChannelsParsesStoredValues() {
        when(sysConfigMapper.selectByKeys(any())).thenReturn(List.of(
                config("auth.phone.enabled", "true"),
                config("auth.phone.code.enabled", "false"),
                config("auth.email.enabled", "true"),
                config("auth.email.code.enabled", "true")
        ));

        AuthChannelConfig channels = service.getChannels();

        assertTrue(channels.phoneEnabled());
        assertFalse(channels.phoneCodeEnabled());
        assertTrue(channels.emailEnabled());
        assertTrue(channels.emailCodeEnabled());
    }

    @Test
    void cannotDisableBothChannels() {
        // 手机号已关闭，再关闭邮箱会导致两个总开关同时关闭
        when(sysConfigMapper.selectMasterSwitchesForUpdate()).thenReturn(List.of(
                config("auth.phone.enabled", "false"),
                config("auth.email.enabled", "true")
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateChannel(
                        AuthChannel.EMAIL,
                        new AuthChannelUpdateDTO(false, true)
                )
        );

        assertEquals(ErrorCode.CANNOT_DISABLE_ALL_CHANNELS, exception.getErrorCode());
        verify(sysConfigMapper, never()).updateValue(any(), any());
    }

    @Test
    void disablingOneChannelIsAllowedWhenOtherStaysEnabled() {
        when(sysConfigMapper.selectMasterSwitchesForUpdate()).thenReturn(List.of(
                config("auth.phone.enabled", "true"),
                config("auth.email.enabled", "true")
        ));
        when(sysConfigMapper.updateValue(anyString(), anyString())).thenReturn(1);

        service.updateChannel(
                AuthChannel.PHONE,
                new AuthChannelUpdateDTO(false, false)
        );

        verify(sysConfigMapper).updateValue("auth.phone.enabled", "false");
        verify(sysConfigMapper).updateValue("auth.phone.code.enabled", "false");
        verify(sysConfigMapper, never()).updateValue(eq("auth.email.enabled"), any());
    }

    @Test
    void enablingChannelPersistsBothSwitches() {
        when(sysConfigMapper.selectMasterSwitchesForUpdate()).thenReturn(List.of(
                config("auth.phone.enabled", "true"),
                config("auth.email.enabled", "false")
        ));
        when(sysConfigMapper.updateValue(anyString(), anyString())).thenReturn(1);

        service.updateChannel(
                AuthChannel.EMAIL,
                new AuthChannelUpdateDTO(true, true)
        );

        verify(sysConfigMapper).updateValue("auth.email.enabled", "true");
        verify(sysConfigMapper).updateValue("auth.email.code.enabled", "true");
    }

    @Test
    void missingConfigurationIsNotTreatedAsDisabledChannel() {
        when(sysConfigMapper.selectByKeys(any())).thenReturn(List.of());
        assertThrows(IllegalStateException.class, service::getChannels);
    }

    @Test
    void malformedConfigurationIsNotTreatedAsFalse() {
        when(sysConfigMapper.selectByKeys(any())).thenReturn(List.of(
                config("auth.phone.enabled", "invalid"),
                config("auth.phone.code.enabled", "true"),
                config("auth.email.enabled", "true"),
                config("auth.email.code.enabled", "true")
        ));
        assertThrows(IllegalStateException.class, service::getChannels);
    }

    @Test
    void updateDoesNotSucceedWhenConfigurationRowIsMissing() {
        when(sysConfigMapper.selectMasterSwitchesForUpdate()).thenReturn(List.of(
                config("auth.phone.enabled", "true"),
                config("auth.email.enabled", "true")
        ));
        when(sysConfigMapper.updateValue("auth.phone.enabled", "false")).thenReturn(1);
        when(sysConfigMapper.updateValue("auth.phone.code.enabled", "false")).thenReturn(0);
        assertThrows(IllegalStateException.class,
                () -> service.updateChannel(AuthChannel.PHONE, new AuthChannelUpdateDTO(false, false)));
    }
}

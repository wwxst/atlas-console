package com.web.project.channel.service;

import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.model.AuthChannel;
import com.web.project.channel.model.AuthChannelConfig;

/**
 * 渠道配置业务接口。
 */
public interface AuthChannelService {

    /**
     * 查询渠道配置。
     */
    AuthChannelConfig getChannels();

    /**
     * 修改指定渠道的总开关和验证码开关。
     *
     * @param channel 目标渠道
     * @param dto     修改参数
     */
    void updateChannel(AuthChannel channel, AuthChannelUpdateDTO dto);
}

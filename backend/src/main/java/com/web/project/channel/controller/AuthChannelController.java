package com.web.project.channel.controller;

import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.model.AuthChannel;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 管理端渠道配置接口。
 */
@RestController
@RequestMapping("/api/sys-user/auth-channels")
@RequiredArgsConstructor
public class AuthChannelController {

    private final AuthChannelService authChannelService;

    /**
     * 查询渠道配置。
     */
    @GetMapping
    public Result<AuthChannelConfig> getChannels() {
        return Result.success(authChannelService.getChannels());
    }

    /**
     * 修改 phone 或 email 渠道配置。
     */
    @PutMapping("/{channel}")
    public Result<Void> updateChannel(
            @PathVariable String channel,
            @Valid @RequestBody AuthChannelUpdateDTO updateDTO
    ) {
        authChannelService.updateChannel(
                parseChannel(channel),
                updateDTO
        );

        return Result.success();
    }

    private AuthChannel parseChannel(String channel) {
        return switch (channel.toLowerCase(Locale.ROOT)) {
            case "phone" -> AuthChannel.PHONE;
            case "email" -> AuthChannel.EMAIL;
            default -> throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "渠道类型不正确"
            );
        };
    }
}

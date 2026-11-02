package com.web.project.channel.controller;

import com.web.project.channel.dto.AliyunSmsSettingsUpdateDTO;
import com.web.project.channel.dto.EmailLoginSettingsUpdateDTO;
import com.web.project.channel.dto.LoginSettingsUpdateDTO;
import com.web.project.channel.dto.TencentSmsSettingsUpdateDTO;
import com.web.project.channel.service.LoginProviderSettingsService;
import com.web.project.channel.vo.AliyunSmsSettingsVO;
import com.web.project.channel.vo.EmailLoginSettingsVO;
import com.web.project.channel.vo.LoginSettingsVO;
import com.web.project.channel.vo.TencentSmsSettingsVO;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端登录方式、短信服务商和 SMTP 配置接口。 */
@RestController
@RequestMapping("/api/sys-user")
@RequiredArgsConstructor
public class LoginProviderSettingsController {

    private final LoginProviderSettingsService service;

    @GetMapping("/login-settings")
    public Result<LoginSettingsVO> loginSettings() {
        return Result.success(service.getLoginSettings());
    }

    @PutMapping("/login-settings")
    public Result<LoginSettingsVO> updateLoginSettings(@Valid @RequestBody LoginSettingsUpdateDTO dto) {
        return Result.success(service.updateLoginSettings(dto));
    }

    @GetMapping("/login-providers/sms/aliyun")
    public Result<AliyunSmsSettingsVO> aliyunSmsSettings() {
        return Result.success(service.getAliyunSmsSettings());
    }

    @PutMapping("/login-providers/sms/aliyun")
    public Result<AliyunSmsSettingsVO> updateAliyunSmsSettings(
            @Valid @RequestBody AliyunSmsSettingsUpdateDTO dto) {
        return Result.success(service.updateAliyunSmsSettings(dto));
    }

    @GetMapping("/login-providers/sms/tencent")
    public Result<TencentSmsSettingsVO> tencentSmsSettings() {
        return Result.success(service.getTencentSmsSettings());
    }

    @PutMapping("/login-providers/sms/tencent")
    public Result<TencentSmsSettingsVO> updateTencentSmsSettings(
            @Valid @RequestBody TencentSmsSettingsUpdateDTO dto) {
        return Result.success(service.updateTencentSmsSettings(dto));
    }

    @GetMapping("/login-providers/email")
    public Result<EmailLoginSettingsVO> emailLoginSettings() {
        return Result.success(service.getEmailLoginSettings());
    }

    @PutMapping("/login-providers/email")
    public Result<EmailLoginSettingsVO> updateEmailLoginSettings(
            @Valid @RequestBody EmailLoginSettingsUpdateDTO dto) {
        return Result.success(service.updateEmailLoginSettings(dto));
    }
}

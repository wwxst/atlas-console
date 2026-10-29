package com.web.project.common;

import com.web.project.auth.dto.RefreshTokenDTO;
import com.web.project.auth.dto.ResetPasswordDTO;
import com.web.project.auth.dto.SendCodeDTO;
import com.web.project.auth.dto.SysUserLoginDTO;
import com.web.project.auth.dto.UserLoginDTO;
import com.web.project.auth.dto.UserRegisterDTO;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import com.web.project.config.properties.JwtProperties;
import com.web.project.user.dto.RebindEmailDTO;
import com.web.project.user.dto.RebindPhoneDTO;
import com.web.project.user.dto.UpdateUserAccountDTO;
import com.web.project.user.vo.UserAccountListVO;
import com.web.project.user.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataLoggingTest {

    @Test
    void printingSensitiveObjectsAndResponseContainersDoesNotExposeTheirValues() {
        String phone = "13800138000";
        String email = "private@example.com";
        String password = "private-password";
        String code = "654321";
        String access = "private-access-token";
        String refresh = "private-refresh-token";
        String secret = "private-signing-key";
        TokenPairVO pair = new TokenPairVO(access, refresh);
        UserInfoVO profile = new UserInfoVO(1L, "123456", phone, email, null, null, 1, null);
        UserAccountListVO user = new UserAccountListVO(1L, "123456", phone, email, null, null, 1, null, null);

        List<Object> objects = List.of(
                new SysUserLoginDTO("admin", password),
                new UserLoginDTO(phone, TargetType.PHONE, password, code),
                new UserRegisterDTO(email, TargetType.EMAIL, code, password),
                new ResetPasswordDTO(phone, TargetType.PHONE, code, password),
                new SendCodeDTO(email, TargetType.EMAIL, VerificationScene.REGISTER),
                new RefreshTokenDTO(refresh),
                new RebindPhoneDTO(email, TargetType.EMAIL, code, phone, code),
                new RebindEmailDTO(phone, TargetType.PHONE, code, email, code),
                new UpdateUserAccountDTO("昵称", phone, email),
                new AccessTokenVO(access),
                pair,
                profile,
                user,
                new JwtProperties("web-project", secret, Duration.ofMinutes(30), Duration.ofDays(30)),
                Result.success(pair),
                Result.success(profile),
                new PageResult<>(1L, 1, 10, List.of(user))
        );
        for (Object object : objects) {
            String rendered = object.toString();
            for (String sensitive : List.of(phone, email, password, code, access, refresh, secret)) {
                assertFalse(rendered.contains(sensitive), object.getClass().getSimpleName());
            }
        }
    }

    @Test
    void redactingObjectPrintingPreservesTokenJsonContract() {
        JsonMapper mapper = JsonMapper.builder().build();
        TokenPairVO pair = new TokenPairVO("access-value", "refresh-value");
        String json = mapper.writeValueAsString(pair);
        assertEquals(pair, mapper.readValue(json, TokenPairVO.class));
        assertTrue(json.contains("access-value"));
        assertTrue(json.contains("refresh-value"));
    }
}

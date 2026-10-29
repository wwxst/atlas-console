package com.web.project.user.service.impl;

import com.web.project.auth.dto.ResetPasswordDTO;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import com.web.project.auth.service.VerificationCodeService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.user.dto.RebindEmailDTO;
import com.web.project.user.dto.RebindPhoneDTO;
import com.web.project.user.dto.UpdateProfileDTO;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import com.web.project.user.vo.UserInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserProfileServiceImplTest {

    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);

    private final UserProfileServiceImpl service = new UserProfileServiceImpl(
            userAccountMapper,
            passwordEncoder,
            verificationCodeService
    );

    private UserAccount account;

    @BeforeEach
    void setUp() {
        account = new UserAccount();
        account.setId(5L);
        account.setUserNo("123456");
        account.setPhone("13800138000");
        account.setEmail("user@example.com");
        account.setNickname("昵称");
        account.setStatus(1);
        account.setPasswordVersion(2L);
    }

    @Test
    void getProfileReturnsCurrentAccount() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);

        UserInfoVO profile = service.getProfile(5L);

        assertEquals(5L, profile.id());
        assertEquals("123456", profile.userNo());
        assertEquals("13800138000", profile.phone());
        assertEquals("user@example.com", profile.email());
    }

    @Test
    void getProfileRejectsDisabledAccount() {
        account.setStatus(0);
        when(userAccountMapper.selectById(5L)).thenReturn(account);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProfile(5L)
        );

        assertEquals(ErrorCode.USER_DISABLED, exception.getErrorCode());
    }

    @Test
    void updateProfilePersistsNicknameAndAvatar() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);
        when(userAccountMapper.updateProfile(5L, "新昵称", "https://avatar")).thenReturn(1);

        service.updateProfile(5L, new UpdateProfileDTO("新昵称", "https://avatar"));

        verify(userAccountMapper).updateProfile(5L, "新昵称", "https://avatar");
    }

    @Test
    void changePasswordRejectsTargetNotBelongingToAccount() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.changePassword(5L, new ResetPasswordDTO(
                        "13900139000", TargetType.PHONE, "123456", "newPassword1"
                ))
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void changePasswordVerifiesCodeAndUpdatesPassword() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);
        when(passwordEncoder.encode("newPassword1")).thenReturn("$new");
        when(userAccountMapper.updatePassword(5L, "$new")).thenReturn(1);

        service.changePassword(5L, new ResetPasswordDTO(
                "13800138000", TargetType.PHONE, "123456", "newPassword1"
        ));

        verify(verificationCodeService).verify(
                VerificationScene.RESET_PASSWORD, TargetType.PHONE, "13800138000", "123456"
        );
        verify(userAccountMapper).updatePassword(5L, "$new");
    }

    @Test
    void invalidPasswordChangeDoesNotConsumeCode() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);

        assertThrows(
                BusinessException.class,
                () -> service.changePassword(5L, new ResetPasswordDTO(
                        "13800138000", TargetType.PHONE, "123456", "short"
                ))
        );

        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void rebindPhoneRejectsOldTargetNotBelongingToAccount() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.rebindPhone(5L, new RebindPhoneDTO(
                        "other@example.com", TargetType.EMAIL, "111111",
                        "13900139000", "222222"
                ))
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void rebindPhoneVerifiesBothCodesThenUpdates() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);
        when(userAccountMapper.updatePhone(5L, "13900139000")).thenReturn(1);

        service.rebindPhone(5L, new RebindPhoneDTO(
                "13800138000", TargetType.PHONE, "111111",
                "13900139000", "222222"
        ));

        verify(verificationCodeService).verify(
                VerificationScene.REBIND_OLD, TargetType.PHONE, "13800138000", "111111"
        );
        verify(verificationCodeService).verify(
                VerificationScene.REBIND_NEW, TargetType.PHONE, "13900139000", "222222"
        );
        verify(userAccountMapper).updatePhone(5L, "13900139000");
    }

    @Test
    void rebindPhoneMapsUniqueConflictToAlreadyBound() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);
        when(userAccountMapper.updatePhone(anyLong(), anyString()))
                .thenThrow(new DuplicateKeyException(
                        "Duplicate entry '13900139000' for key 'user_account.uk_user_account_phone'"
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.rebindPhone(5L, new RebindPhoneDTO(
                        "13800138000", TargetType.PHONE, "111111",
                        "13900139000", "222222"
                ))
        );

        assertEquals(ErrorCode.TARGET_ALREADY_BOUND, exception.getErrorCode());
    }

    @Test
    void rebindEmailVerifiesBothCodesThenUpdates() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);
        when(userAccountMapper.updateEmail(5L, "new@example.com")).thenReturn(1);

        service.rebindEmail(5L, new RebindEmailDTO(
                "user@example.com", TargetType.EMAIL, "111111",
                "new@example.com", "222222"
        ));

        verify(verificationCodeService).verify(
                VerificationScene.REBIND_OLD, TargetType.EMAIL, "user@example.com", "111111"
        );
        verify(verificationCodeService).verify(
                VerificationScene.REBIND_NEW, TargetType.EMAIL, "new@example.com", "222222"
        );
        verify(userAccountMapper).updateEmail(5L, "new@example.com");
    }

    @Test
    void profileUpdateDoesNotSucceedWhenAccountDisappears() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);
        when(userAccountMapper.updateProfile(anyLong(), anyString(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(5L, new UpdateProfileDTO("新昵称", null)));
        assertEquals(ErrorCode.LOGIN_STATUS_INVALID, exception.getErrorCode());
    }

    @Test
    void passwordChangeDoesNotSucceedWhenUpdateMissesAccount() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);
        when(passwordEncoder.encode("newPassword1")).thenReturn("$new");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changePassword(5L, new ResetPasswordDTO(
                        "13800138000", TargetType.PHONE, "123456", "newPassword1"
                )));
        assertEquals(ErrorCode.LOGIN_STATUS_INVALID, exception.getErrorCode());
    }

    @Test
    void rebindDoesNotSucceedWhenUpdateMissesAccount() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.rebindPhone(5L, new RebindPhoneDTO(
                        "13800138000", TargetType.PHONE, "111111", "13900139000", "222222"
                )));
        assertEquals(ErrorCode.LOGIN_STATUS_INVALID, exception.getErrorCode());
    }

    @Test
    void contactConflictRetainsItsCauseWithoutExposingItAsBusinessMessage() {
        when(userAccountMapper.selectById(5L)).thenReturn(account);
        DuplicateKeyException cause = new DuplicateKeyException("sensitive database details");
        when(userAccountMapper.updateEmail(5L, "new@example.com")).thenThrow(cause);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.rebindEmail(5L, new RebindEmailDTO(
                        "user@example.com", TargetType.EMAIL, "111111", "new@example.com", "222222"
                )));
        assertSame(cause, exception.getCause());
        assertEquals(ErrorCode.TARGET_ALREADY_BOUND.getDefaultMessage(), exception.getMessage());
    }
}

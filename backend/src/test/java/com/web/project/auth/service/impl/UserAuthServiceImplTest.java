package com.web.project.auth.service.impl;

import com.web.project.auth.dto.ResetPasswordDTO;
import com.web.project.auth.dto.UserLoginDTO;
import com.web.project.auth.dto.UserRegisterDTO;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import com.web.project.auth.protection.LoginProtectionMetrics;
import com.web.project.auth.protection.LoginProtectionProperties;
import com.web.project.auth.protection.PasswordVerificationGuard;
import com.web.project.auth.service.AuthSessionService;
import com.web.project.auth.service.VerificationCodeService;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.id.SnowflakeIdGenerator;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAuthServiceImplTest {

    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthSessionService authSessionService = mock(AuthSessionService.class);
    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
    private final AuthChannelService authChannelService = mock(AuthChannelService.class);
    private final SnowflakeIdGenerator snowflakeIdGenerator = mock(SnowflakeIdGenerator.class);
    private final PasswordVerificationGuard passwordVerificationGuard = new PasswordVerificationGuard(
            new LoginProtectionProperties(8, Duration.ofSeconds(60), 60, 20),
            new LoginProtectionMetrics()
    );

    private final UserAuthServiceImpl service = new UserAuthServiceImpl(
            userAccountMapper,
            passwordEncoder,
            authSessionService,
            verificationCodeService,
            authChannelService,
            snowflakeIdGenerator,
            passwordVerificationGuard
    );

    @BeforeEach
    void enableAllChannels() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(true, true, true, true));
    }

    private UserAccount account(Long id, Long passwordVersion, String password, int status) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setPasswordVersion(passwordVersion);
        account.setPassword(password);
        account.setStatus(status);
        return account;
    }

    private void insertSucceeds() {
        when(userAccountMapper.insert(any(UserAccount.class))).thenReturn(1);
    }

    @Test
    void registerWithPhoneCreatesAccountAndSession() {
        when(userAccountMapper.selectByPhone("13800138000")).thenReturn(null);
        when(snowflakeIdGenerator.nextId()).thenReturn(11L);
        insertSucceeds();
        when(passwordEncoder.encode("password123")).thenReturn("$hash");
        when(authSessionService.createSession(AccountType.USER, 11L, 0L))
                .thenReturn(new TokenPairVO("access", "refresh"));

        TokenPairVO pair = service.register(new UserRegisterDTO(
                "13800138000", TargetType.PHONE, "123456", "password123"
        ));

        assertEquals("access", pair.accessToken());
        verify(verificationCodeService).verify(
                VerificationScene.REGISTER, TargetType.PHONE, "13800138000", "123456"
        );
        verify(userAccountMapper).insert(argThat(account -> account.getId().equals(11L)));
    }

    @Test
    void registerWithEmailNormalizesTarget() {
        when(userAccountMapper.selectByEmail("user@example.com")).thenReturn(null);
        when(snowflakeIdGenerator.nextId()).thenReturn(12L);
        insertSucceeds();
        when(authSessionService.createSession(AccountType.USER, 12L, 0L))
                .thenReturn(new TokenPairVO("access", "refresh"));

        service.register(new UserRegisterDTO(
                " User@Example.com ", TargetType.EMAIL, "123456", null
        ));

        verify(userAccountMapper).selectByEmail("user@example.com");
    }

    @Test
    void registerRejectsAlreadyRegisteredTarget() {
        when(userAccountMapper.selectByPhone("13800138000"))
                .thenReturn(account(1L, 0L, null, 1));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register(new UserRegisterDTO(
                        "13800138000", TargetType.PHONE, "123456", null
                ))
        );

        assertEquals(ErrorCode.TARGET_ALREADY_REGISTERED, exception.getErrorCode());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void invalidRegistrationPasswordDoesNotConsumeCode() {
        when(userAccountMapper.selectByPhone("13800138000")).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> service.register(new UserRegisterDTO(
                        "13800138000", TargetType.PHONE, "123456", "short"
                ))
        );

        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void registerRetriesWhenUserNoConflicts() {
        when(userAccountMapper.selectByPhone(any())).thenReturn(null);
        when(snowflakeIdGenerator.nextId()).thenReturn(13L);
        when(authSessionService.createSession(eq(AccountType.USER), eq(13L), eq(0L)))
                .thenReturn(new TokenPairVO("access", "refresh"));

        when(userAccountMapper.insert(any(UserAccount.class))).thenReturn(0, 0, 1);

        service.register(new UserRegisterDTO(
                "13800138000", TargetType.PHONE, "123456", null
        ));

        verify(userAccountMapper, times(3)).insert(any(UserAccount.class));
    }

    @Test
    void registerFailsAfterUserNoAttemptsExhausted() {
        when(userAccountMapper.selectByPhone(any())).thenReturn(null);
        when(userAccountMapper.insert(any(UserAccount.class))).thenReturn(0, 0, 0, 0, 0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register(new UserRegisterDTO(
                        "13800138000", TargetType.PHONE, "123456", null
                ))
        );

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        verify(userAccountMapper, times(5)).insert(any(UserAccount.class));
    }

    @Test
    void registerMapsContactConflictToAlreadyRegistered() {
        when(userAccountMapper.selectByPhone(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("insert failed", new java.sql.SQLException(
                "ERROR: duplicate key value violates unique constraint \"uk_user_account_phone\"", "23505")))
                .when(userAccountMapper).insert(any(UserAccount.class));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register(new UserRegisterDTO(
                        "13800138000", TargetType.PHONE, "123456", null
                ))
        );

        assertEquals(ErrorCode.TARGET_ALREADY_REGISTERED, exception.getErrorCode());
    }

    @Test
    void contactValueContainingUserNoIndexDoesNotTriggerNumberRetries() {
        DuplicateKeyException duplicate = new DuplicateKeyException("insert failed",
                new java.sql.SQLException(
                        "ERROR: duplicate key value violates unique constraint \"uk_user_account_email\"",
                        "23505"));
        doThrow(duplicate).when(userAccountMapper).insert(any(UserAccount.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new UserRegisterDTO(
                        "uk_user_account_user_no@example.com", TargetType.EMAIL, "123456", null)));

        assertEquals(ErrorCode.TARGET_ALREADY_REGISTERED, exception.getErrorCode());
        assertSame(duplicate, exception.getCause());
        verify(userAccountMapper).insert(any(UserAccount.class));
    }

    @Test
    void loginRequiresExactlyOneCredential() {
        BusinessException both = assertThrows(
                BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, "password123", "123456"
                ))
        );
        assertEquals(ErrorCode.BAD_REQUEST, both.getErrorCode());

        BusinessException none = assertThrows(
                BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, null, null
                ))
        );
        assertEquals(ErrorCode.BAD_REQUEST, none.getErrorCode());
    }

    @Test
    void loginRejectsPasswordBeyondBcryptByteLimitBeforeCheckingCredentials() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, "中".repeat(24) + "x", null)));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(userAccountMapper, passwordEncoder, authSessionService);
    }

    @Test
    void shortIncorrectLoginPasswordRemainsCredentialFailure() {
        when(userAccountMapper.selectByPhone("13800138000"))
                .thenReturn(account(3L, 0L, "$hash", 1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, "short", null)));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        verify(passwordEncoder).matches("short", "$hash");
    }

    @Test
    void loginWithPasswordSucceeds() {
        when(userAccountMapper.selectByPhone("13800138000"))
                .thenReturn(account(3L, 0L, "$hash", 1));
        when(passwordEncoder.matches("password123", "$hash")).thenReturn(true);
        when(authSessionService.createSession(AccountType.USER, 3L, 0L))
                .thenReturn(new TokenPairVO("access", "refresh"));

        TokenPairVO pair = service.login(new UserLoginDTO(
                "13800138000", TargetType.PHONE, "password123", null
        ));

        assertEquals("access", pair.accessToken());
    }

    @Test
    void loginWithWrongPasswordFails() {
        when(userAccountMapper.selectByPhone("13800138000"))
                .thenReturn(account(3L, 0L, "$hash", 1));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, "password123", null
                ))
        );

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void accountWithoutPasswordCanOnlyLoginWithCode() {
        when(userAccountMapper.selectByPhone("13800138000"))
                .thenReturn(account(3L, 0L, null, 1));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, "password123", null
                ))
        );
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());

        when(authSessionService.createSession(AccountType.USER, 3L, 0L))
                .thenReturn(new TokenPairVO("access", "refresh"));

        TokenPairVO pair = service.login(new UserLoginDTO(
                "13800138000", TargetType.PHONE, null, "123456"
        ));

        assertEquals("access", pair.accessToken());
        verify(verificationCodeService).verify(
                VerificationScene.LOGIN, TargetType.PHONE, "13800138000", "123456"
        );
    }

    @Test
    void disabledAccountRejectsLogin() {
        when(userAccountMapper.selectByPhone("13800138000"))
                .thenReturn(account(3L, 0L, "$hash", 0));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, "password123", null
                ))
        );

        assertEquals(ErrorCode.USER_DISABLED, exception.getErrorCode());
    }

    @Test
    void disabledChannelRejectsLogin() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(false, false, true, true));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, "password123", null
                ))
        );

        assertEquals(ErrorCode.AUTH_CHANNEL_DISABLED, exception.getErrorCode());
    }

    @Test
    void disabledChannelRejectsRegistrationBeforeConsumingCode() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(false, false, true, true));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new UserRegisterDTO(
                        "13800138000", TargetType.PHONE, "123456", null)));

        assertEquals(ErrorCode.AUTH_CHANNEL_DISABLED, exception.getErrorCode());
        verifyNoInteractions(userAccountMapper, verificationCodeService, authSessionService);
    }

    @Test
    void unknownAccountRejectsLoginWithoutCreatingSession() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, "password123", null)));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        verifyNoInteractions(passwordEncoder, verificationCodeService, authSessionService);
    }

    @Test
    void disabledCodeFunctionRejectsCodeLoginButAllowsPasswordLogin() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(true, false, true, true));
        when(userAccountMapper.selectByPhone("13800138000"))
                .thenReturn(account(3L, 0L, "$hash", 1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new UserLoginDTO(
                        "13800138000", TargetType.PHONE, null, "123456")));

        assertEquals(ErrorCode.CODE_FUNCTION_DISABLED, exception.getErrorCode());
        verifyNoInteractions(verificationCodeService, authSessionService);

        when(passwordEncoder.matches("password123", "$hash")).thenReturn(true);
        when(authSessionService.createSession(AccountType.USER, 3L, 0L))
                .thenReturn(new TokenPairVO("access", "refresh"));
        assertEquals("access", service.login(new UserLoginDTO(
                "13800138000", TargetType.PHONE, "password123", null)).accessToken());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void disabledChannelRejectsPasswordResetBeforeConsumingCode() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(false, false, true, true));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resetPassword(new ResetPasswordDTO(
                        "13800138000", TargetType.PHONE, "123456", "newPassword1")));

        assertEquals(ErrorCode.AUTH_CHANNEL_DISABLED, exception.getErrorCode());
        verifyNoInteractions(userAccountMapper, verificationCodeService, passwordEncoder);
    }

    @Test
    void resetPasswordConsumesCodeAndUpdatesPassword() {
        when(userAccountMapper.selectByEmail("user@example.com"))
                .thenReturn(account(4L, 2L, "$old", 1));
        when(passwordEncoder.encode("newPassword1")).thenReturn("$new");
        when(userAccountMapper.updatePassword(4L, "$new")).thenReturn(1);

        service.resetPassword(new ResetPasswordDTO(
                "User@Example.com", TargetType.EMAIL, "654321", "newPassword1"
        ));

        verify(verificationCodeService).verify(
                VerificationScene.RESET_PASSWORD, TargetType.EMAIL, "user@example.com", "654321"
        );
        verify(userAccountMapper).updatePassword(4L, "$new");
    }

    @Test
    void resetPasswordRejectsUnknownTarget() {
        when(userAccountMapper.selectByEmail(any())).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.resetPassword(new ResetPasswordDTO(
                        "user@example.com", TargetType.EMAIL, "654321", "newPassword1"
                ))
        );

        assertEquals(ErrorCode.TARGET_NOT_REGISTERED, exception.getErrorCode());
    }

    @Test
    void invalidResetPasswordDoesNotConsumeCode() {
        when(userAccountMapper.selectByEmail("user@example.com"))
                .thenReturn(account(4L, 2L, "$old", 1));

        assertThrows(
                BusinessException.class,
                () -> service.resetPassword(new ResetPasswordDTO(
                        "user@example.com", TargetType.EMAIL, "654321", "short"
                ))
        );

        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void resetPasswordDoesNotSucceedWhenAccountDisappearsBeforeUpdate() {
        when(userAccountMapper.selectByEmail("user@example.com"))
                .thenReturn(account(4L, 2L, "$old", 1));
        when(passwordEncoder.encode("newPassword1")).thenReturn("$new");
        when(userAccountMapper.updatePassword(4L, "$new")).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resetPassword(new ResetPasswordDTO(
                        "user@example.com", TargetType.EMAIL, "654321", "newPassword1"
                )));
        assertEquals(ErrorCode.TARGET_NOT_REGISTERED, exception.getErrorCode());
    }

    @Test
    void registrationDoesNotCreateSessionWhenUserNoConflictsExhaustRetries() {
        when(userAccountMapper.insert(any(UserAccount.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new UserRegisterDTO(
                        "13800138000", TargetType.PHONE, "123456", null
                )));
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        verify(userAccountMapper, times(5)).insert(any(UserAccount.class));
        verifyNoInteractions(authSessionService);
    }

    @Test
    void unexpectedDatabaseConflictPropagatesWithoutChangingItsMeaning() {
        DuplicateKeyException failure = new DuplicateKeyException("unexpected PRIMARY conflict");
        when(userAccountMapper.insert(any(UserAccount.class))).thenThrow(failure);

        assertSame(failure, assertThrows(DuplicateKeyException.class,
                () -> service.register(new UserRegisterDTO(
                        "13800138000", TargetType.PHONE, "123456", null
                ))));
        verifyNoInteractions(authSessionService);
    }
}

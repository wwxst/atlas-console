package com.web.project.auth.service;

import com.web.project.auth.code.CodeSender;
import com.web.project.auth.code.VerificationCodeStore;
import com.web.project.auth.dto.SendCodeDTO;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationCodeServiceTest {

    private final VerificationCodeStore codeStore = mock(VerificationCodeStore.class);
    private final AuthChannelService authChannelService = mock(AuthChannelService.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final CodeSender codeSender = mock(CodeSender.class);

    private final VerificationCodeService service = new VerificationCodeService(
            codeStore,
            authChannelService,
            userAccountMapper,
            codeSender
    );

    @BeforeEach
    void enableAllChannels() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(true, true, true, true));
        when(codeStore.acquireSendLock(any(), any(), any())).thenReturn(true);
        when(codeStore.incrementDailyCount(any(), any())).thenReturn(1L);
    }

    private SendCodeDTO dto(VerificationScene scene, String target) {
        return new SendCodeDTO(target, TargetType.PHONE, scene);
    }

    @Test
    void disabledChannelRejectsSend() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(false, false, true, true));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.send(dto(VerificationScene.LOGIN, "13800138000"), null)
        );

        assertEquals(ErrorCode.AUTH_CHANNEL_DISABLED, exception.getErrorCode());
        verifyNoInteractions(codeStore);
    }

    @Test
    void disabledCodeFunctionRejectsSend() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(true, false, true, true));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.send(dto(VerificationScene.LOGIN, "13800138000"), null)
        );

        assertEquals(ErrorCode.CODE_FUNCTION_DISABLED, exception.getErrorCode());
        verifyNoInteractions(codeStore);
    }

    @Test
    void registerSceneRejectsRegisteredTarget() {
        when(userAccountMapper.selectByPhone("13800138000")).thenReturn(new UserAccount());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.send(dto(VerificationScene.REGISTER, "13800138000"), null)
        );

        assertEquals(ErrorCode.TARGET_ALREADY_REGISTERED, exception.getErrorCode());
        verifyNoInteractions(codeStore);
    }

    @Test
    void loginSceneRejectsUnknownTarget() {
        when(userAccountMapper.selectByPhone("13800138000")).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.send(dto(VerificationScene.LOGIN, "13800138000"), null)
        );

        assertEquals(ErrorCode.TARGET_NOT_REGISTERED, exception.getErrorCode());
    }

    @Test
    void rebindOldWithoutAccessTokenIsRejected() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.send(dto(VerificationScene.REBIND_OLD, "13800138000"), null)
        );

        assertEquals(ErrorCode.LOGIN_STATUS_INVALID, exception.getErrorCode());
    }

    @Test
    void rebindNewRejectsAlreadyBoundTarget() {
        UserAccount current = new UserAccount();
        current.setStatus(1);
        when(userAccountMapper.selectById(7L)).thenReturn(current);
        when(userAccountMapper.selectByPhone("13800138000")).thenReturn(new UserAccount());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.send(dto(VerificationScene.REBIND_NEW, "13800138000"), 7L)
        );

        assertEquals(ErrorCode.TARGET_ALREADY_BOUND, exception.getErrorCode());
    }

    @Test
    void rebindNewWithoutAccessTokenIsRejected() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.send(dto(VerificationScene.REBIND_NEW, "13800138000"), null)
        );

        assertEquals(ErrorCode.LOGIN_STATUS_INVALID, exception.getErrorCode());
        verify(userAccountMapper, never()).selectByPhone(anyString());
        verifyNoInteractions(codeStore);
    }

    @Test
    void sendLockRejectionReturnsTooFrequent() {
        when(userAccountMapper.selectByPhone(any())).thenReturn(new UserAccount());
        when(codeStore.acquireSendLock(any(), any(), any())).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.send(dto(VerificationScene.LOGIN, "13800138000"), null)
        );

        assertEquals(ErrorCode.CODE_SEND_TOO_FREQUENT, exception.getErrorCode());
        verify(codeStore, never()).incrementDailyCount(any(), any());
    }

    @Test
    void dailyLimitReleasesLockAndRejects() {
        when(userAccountMapper.selectByPhone(any())).thenReturn(new UserAccount());
        when(codeStore.incrementDailyCount(any(), any())).thenReturn(11L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.send(dto(VerificationScene.LOGIN, "13800138000"), null)
        );

        assertEquals(ErrorCode.CODE_SEND_LIMIT_EXCEEDED, exception.getErrorCode());
        verify(codeStore).releaseSendLock(
                VerificationScene.LOGIN, TargetType.PHONE, "13800138000"
        );
        verify(codeStore, never()).saveCode(any(), any(), any(), any());
    }

    @Test
    void successfulSendStoresCodeAndSends() {
        when(userAccountMapper.selectByPhone(any())).thenReturn(new UserAccount());

        service.send(dto(VerificationScene.LOGIN, "13800138000"), null);

        verify(codeStore).saveCode(
                eq(VerificationScene.LOGIN),
                eq(TargetType.PHONE),
                eq("13800138000"),
                argThat(code -> code.matches("\\d{6}"))
        );
        verify(codeSender).send(
                eq(TargetType.PHONE),
                eq("13800138000"),
                argThat(code -> code.matches("\\d{6}"))
        );
    }

    @Test
    void verifyFailsWhenCodeIsNotConsumed() {
        when(codeStore.consumeCode(any(), any(), any(), any())).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        VerificationScene.LOGIN, TargetType.PHONE, "13800138000", "123456"
                )
        );

        assertEquals(ErrorCode.VERIFICATION_CODE_INVALID, exception.getErrorCode());
    }

    @Test
    void disabledChannelRejectsPreviouslyIssuedCode() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(false, false, true, true));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        VerificationScene.REGISTER, TargetType.PHONE, "13800138000", "123456"
                )
        );

        assertEquals(ErrorCode.AUTH_CHANNEL_DISABLED, exception.getErrorCode());
        verify(codeStore, never()).consumeCode(any(), any(), any(), any());
    }

    @Test
    void disabledCodeFunctionRejectsPreviouslyIssuedCode() {
        when(authChannelService.getChannels())
                .thenReturn(new AuthChannelConfig(true, false, true, true));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        VerificationScene.LOGIN, TargetType.PHONE, "13800138000", "123456"
                )
        );

        assertEquals(ErrorCode.CODE_FUNCTION_DISABLED, exception.getErrorCode());
        verify(codeStore, never()).consumeCode(any(), any(), any(), any());
    }

    @Test
    void disabledUserCannotSendRebindCode() {
        UserAccount current = new UserAccount();
        current.setStatus(0);
        when(userAccountMapper.selectById(7L)).thenReturn(current);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.send(dto(VerificationScene.REBIND_NEW, "13900139000"), 7L));
        assertEquals(ErrorCode.USER_DISABLED, exception.getErrorCode());
        verifyNoInteractions(codeStore, codeSender);
    }
}

package com.web.project.user.service.impl;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.user.dto.UpdateUserAccountDTO;
import com.web.project.user.dto.UserAccountQueryDTO;
import com.web.project.user.entity.UserAccount;
import com.web.project.auth.presence.AuthPresenceStore;
import com.web.project.auth.model.AccountType;
import com.web.project.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

class UserAccountServiceImplTest {
    private final UserAccountMapper mapper = mock(UserAccountMapper.class);
    private final AuthPresenceStore presenceStore = mock(AuthPresenceStore.class);
    private final UserAccountServiceImpl service = new UserAccountServiceImpl(mapper, presenceStore);

    @Test
    void missingStatusTargetIsNotReportedAsSuccess() {
        var failure = assertThrows(BusinessException.class, () -> service.updateStatus(5L, 0));
        assertEquals(ErrorCode.BAD_REQUEST, failure.getErrorCode());
        assertEquals("用户不存在", failure.getMessage());
    }

    @Test
    void statusWriteFailurePropagates() {
        var failure = new IllegalStateException("database unavailable");
        when(mapper.updateStatus(5L, 0)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.updateStatus(5L, 0)));
    }

    @Test
    void updateReturnsDisabledAccountWithNormalizedContacts() {
        UserAccount stored = new UserAccount();
        stored.setId(5L);
        stored.setUserNo("123456");
        stored.setNickname("新昵称");
        stored.setEmail("new@example.com");
        stored.setStatus(0);
        when(mapper.updateAccount(5L, "新昵称", null, "new@example.com")).thenReturn(1);
        when(mapper.selectById(5L)).thenReturn(stored);

        var result = service.updateUser(5L, new UpdateUserAccountDTO(" 新昵称 ", " ", " NEW@EXAMPLE.COM "));

        assertEquals(5L, result.id());
        assertEquals("123456", result.userNo());
        assertEquals(0, result.status());
        assertEquals("新昵称", result.nickname());
        assertNull(result.phone());
        assertEquals("new@example.com", result.email());
        verify(mapper).updateAccount(5L, "新昵称", null, "new@example.com");
        verify(mapper).selectById(5L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void listReturnsExistingAvatarField() {
        UserAccount stored = new UserAccount();
        stored.setId(5L);
        stored.setUserNo("123456");
        stored.setAvatar("https://files.example/avatar.png");
        stored.setStatus(1);
        when(mapper.countByCondition(null, null)).thenReturn(1L);
        when(mapper.selectPageByCondition(null, null, 0L, 10, null)).thenReturn(List.of(stored));
        when(presenceStore.onlineIds(AccountType.USER, List.of(5L))).thenReturn(java.util.Set.of(5L));

        var result = service.getUserPage(new UserAccountQueryDTO());

        assertEquals("https://files.example/avatar.png", result.records().getFirst().avatar());
        assertTrue(result.records().getFirst().online());
    }

    @Test
    void duplicateContactUsesExistingBusinessErrorAndRetainsCause() {
        var cause = new DuplicateKeyException("duplicate contact");
        when(mapper.updateAccount(5L, "昵称", "13800138000", null)).thenThrow(cause);

        var failure = assertThrows(BusinessException.class,
                () -> service.updateUser(5L, new UpdateUserAccountDTO("昵称", "13800138000", null)));

        assertEquals(ErrorCode.TARGET_ALREADY_BOUND, failure.getErrorCode());
        assertSame(cause, failure.getCause());
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void missingAccountIsBusinessError() {
        var failure = assertThrows(BusinessException.class,
                () -> service.updateUser(5L, new UpdateUserAccountDTO("昵称", null, "new@example.com")));
        assertEquals(ErrorCode.BAD_REQUEST, failure.getErrorCode());
        assertEquals("用户不存在", failure.getMessage());
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void invalidContactIsRejectedBeforeWrite() {
        for (var input : new UpdateUserAccountDTO[]{
                new UpdateUserAccountDTO("昵称", "123", null),
                new UpdateUserAccountDTO("昵称", null, "broken")
        }) {
            assertEquals(ErrorCode.BAD_REQUEST,
                    assertThrows(BusinessException.class, () -> service.updateUser(5L, input)).getErrorCode());
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void unexpectedDatabaseFailureIsNotConvertedToContactConflict() {
        var cause = new IllegalStateException("database unavailable");
        when(mapper.updateAccount(5L, "昵称", null, "new@example.com")).thenThrow(cause);
        assertSame(cause, assertThrows(IllegalStateException.class,
                () -> service.updateUser(5L, new UpdateUserAccountDTO("昵称", null, "new@example.com"))));
    }

}

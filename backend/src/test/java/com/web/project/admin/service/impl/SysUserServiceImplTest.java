package com.web.project.admin.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.web.project.admin.dto.CreateSysUserDTO;
import com.web.project.admin.dto.ResetSysUserPasswordDTO;
import com.web.project.admin.dto.UpdateSysUserDTO;
import com.web.project.admin.entity.SysUser;
import com.web.project.auth.presence.AuthPresenceStore;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.storage.service.StorageService;
import com.web.project.storage.entity.StorageFile;
import com.web.project.storage.mapper.StorageFileMapper;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SysUserServiceImplTest {

    private final SysUserMapper mapper = mock(SysUserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final StorageService storage = mock(StorageService.class);
    private final StorageFileMapper files = mock(StorageFileMapper.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final AuthPresenceStore presenceStore = mock(AuthPresenceStore.class);
    private final SysUserServiceImpl service = new SysUserServiceImpl(mapper, presenceStore, passwordEncoder, storage, files, transactions);
    private boolean transactionRunning;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void executeReferenceTransactions() {
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            transactionRunning = true;
            try {
                return ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(new SimpleTransactionStatus());
            } finally {
                transactionRunning = false;
            }
        });
    }

    @Test
    void updateStatusDelegatesAndAcceptsOneUpdatedRow() {
        when(mapper.updateStatus(8L, 0)).thenReturn(1);

        service.updateStatus(8L, 0);

        verify(mapper).updateStatus(8L, 0);
    }

    @Test
    void updateStatusRejectsMissingUser() {
        when(mapper.updateStatus(99L, 1)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateStatus(99L, 1)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void updateStatusRejectsDisablingSuperAdmin() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateStatus(1L, 0)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("超级管理员不允许停用", exception.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void createHashesPasswordAndReturnsSafeUser() {
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(7L);
            return 1;
        }).when(mapper).insert(any(SysUser.class));
        SysUser stored = user(7L, "operator", "运营人员", 1);
        stored.setPassword("hashed-password");
        when(mapper.selectById(7L)).thenReturn(stored);

        var result = service.create(new CreateSysUserDTO("operator", "password123", "运营人员", 1));

        assertEquals(7L, result.id());
        assertEquals("operator", result.username());
        verify(passwordEncoder).encode("password123");
        verify(mapper).insert(any(SysUser.class));
    }

    @Test
    void createRejectsUnexpectedInsertCount() {
        when(mapper.insert(any(SysUser.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(new CreateSysUserDTO("operator", "password123", "运营人员", 1))
        );

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
    }

    @Test
    void createRejectsPasswordOutsidePolicyBeforeWriting() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(new CreateSysUserDTO("operator", "short", "运营人员", 1))
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(mapper, passwordEncoder);
    }

    @Test
    void updateAndResetPasswordWriteRequestedAccount() {
        when(mapper.update(any(SysUser.class))).thenReturn(1);
        when(mapper.selectById(8L)).thenReturn(user(8L, "edited", "编辑后", 0));
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(mapper.updatePassword(8L, "new-hash")).thenReturn(1);

        var result = service.update(8L, new UpdateSysUserDTO(" edited ", " 编辑后 "));
        service.resetPassword(8L, new ResetSysUserPasswordDTO("newpassword"));

        assertEquals("edited", result.username());
        verify(mapper).update(any(SysUser.class));
        verify(mapper).updatePassword(8L, "new-hash");
    }

    @Test
    void deleteRejectsMissingAccount() {
        when(mapper.deleteById(88L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.delete(88L));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void deleteAcceptsOneDeletedRow() {
        when(mapper.selectByIdForUpdate(8L)).thenReturn(user(8L, "operator", "用户", 1));
        when(mapper.deleteById(8L)).thenReturn(1);

        service.delete(8L);

        verify(mapper).deleteById(8L);
    }

    @Test
    void deleteRejectsSuperAdmin() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("超级管理员不允许删除", exception.getMessage());
        verifyNoInteractions(mapper, storage);
    }

    @Test
    void avatarReplacementCommitsReferenceBeforeDeletingOldFile() {
        SysUser current = user(8L, "operator", "用户", 1);
        current.setAvatarFileId(11L);
        SysUser updated = user(8L, "operator", "用户", 1);
        updated.setAvatarFileId(12L);
        updated.setUpdatedAt(java.time.LocalDateTime.of(2026, 10, 6, 12, 0));
        when(mapper.selectById(8L)).thenReturn(current, updated);
        when(mapper.selectByIdForUpdate(8L)).thenReturn(current);
        StorageFile uploaded = new StorageFile();
        uploaded.setId(12L);
        var file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});
        when(storage.storeAvatar(file)).thenAnswer(invocation -> {
            assertEquals(false, transactionRunning);
            return uploaded;
        });
        when(files.selectByIdForUpdate(12L)).thenReturn(uploaded);
        when(mapper.updateAvatar(8L, 12L)).thenAnswer(invocation -> {
            assertEquals(true, transactionRunning);
            return 1;
        });
        doAnswer(invocation -> {
            assertEquals(false, transactionRunning);
            return null;
        }).when(storage).delete(11L);

        var result = service.updateAvatar(8L, file);

        assertEquals("/files/12", result.avatar());
        assertEquals(updated.getUpdatedAt(), result.updatedAt());
        var ordered = inOrder(storage, mapper);
        ordered.verify(storage).storeAvatar(file);
        ordered.verify(mapper).selectByIdForUpdate(8L);
        ordered.verify(mapper).updateAvatar(8L, 12L);
        ordered.verify(storage).delete(11L);
    }

    @Test
    void failedReferenceWriteCleansOnlyNewFileAndPreservesOriginalFailure() {
        when(mapper.selectById(8L)).thenReturn(user(8L, "operator", "用户", 1));
        when(mapper.selectByIdForUpdate(8L)).thenReturn(user(8L, "operator", "用户", 1));
        StorageFile uploaded = new StorageFile();
        uploaded.setId(12L);
        var file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});
        when(storage.storeAvatar(file)).thenReturn(uploaded);
        when(files.selectByIdForUpdate(12L)).thenReturn(uploaded);
        var original = new IllegalStateException("database write failed");
        when(mapper.updateAvatar(8L, 12L)).thenThrow(original);
        var cleanup = new IllegalStateException("physical delete failed");
        doThrow(cleanup).when(storage).delete(12L);

        var actual = assertThrows(IllegalStateException.class, () -> service.updateAvatar(8L, file));

        assertEquals(original, actual);
        assertEquals(cleanup, actual.getSuppressed()[0]);
        verify(storage).delete(12L);
    }

    @Test
    void avatarUpdateSucceedsWhenOldFileCleanupFailsAfterCommit() {
        SysUser current = user(8L, "operator", "用户", 1);
        current.setAvatarFileId(11L);
        SysUser updated = user(8L, "operator", "用户", 1);
        updated.setAvatarFileId(12L);
        when(mapper.selectById(8L)).thenReturn(current, updated);
        when(mapper.selectByIdForUpdate(8L)).thenReturn(current);
        StorageFile uploaded = new StorageFile();
        uploaded.setId(12L);
        var file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});
        when(storage.storeAvatar(file)).thenReturn(uploaded);
        when(files.selectByIdForUpdate(12L)).thenReturn(uploaded);
        when(mapper.updateAvatar(8L, 12L)).thenReturn(1);
        doThrow(new IllegalStateException("sensitive-cleanup-value",
                new IllegalArgumentException("sensitive-cause-value"))).when(storage).delete(11L);
        Logger logger = (Logger) LoggerFactory.getLogger(SysUserServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var result = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.updateAvatar(8L, file));

            assertEquals("/files/12", result.avatar());
            verify(storage).delete(11L);
            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.getFirst();
            assertNull(event.getThrowableProxy(), "Raw exception messages must not reach the log formatter");
            assertTrue(event.getFormattedMessage().contains("IllegalStateException"));
            assertTrue(event.getFormattedMessage().contains("IllegalArgumentException"));
            assertTrue(event.getFormattedMessage().contains("SysUserServiceImplTest"));
            assertFalse(event.getFormattedMessage().contains("sensitive-cleanup-value"));
            assertFalse(event.getFormattedMessage().contains("sensitive-cause-value"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private SysUser user(Long id, String username, String nickname, Integer status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setStatus(status);
        return user;
    }
}

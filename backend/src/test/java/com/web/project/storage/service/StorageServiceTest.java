package com.web.project.storage.service;

import com.web.project.common.exception.BusinessException;
import com.web.project.storage.config.FileProperties;
import com.web.project.storage.entity.StorageFile;
import com.web.project.storage.entity.StorageSpace;
import com.web.project.storage.mapper.StorageFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageServiceTest {
    @TempDir Path directory;
    StorageSettingsService settings;
    StorageFileMapper mapper;
    OssStorageClient oss;
    StorageService service;

    @BeforeEach void setUp() {
        settings = mock(StorageSettingsService.class);
        mapper = mock(StorageFileMapper.class);
        oss = mock(OssStorageClient.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var local = new StorageSettingsService.Snapshot("local", "", "", null);
        when(settings.snapshot()).thenReturn(local);
        when(settings.lockConfig()).thenReturn(local);
        service = new StorageService(settings, mapper, oss, new FileProperties(directory.toString()), transactionManager);
    }

    @Test void actualImageDeterminesTypeAndMetadataFailureCleansPhysicalFile() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        when(mapper.insert(any())).thenAnswer(call -> { StorageFile file = call.getArgument(0); file.setId(9L); return 1; });
        StorageFile file = service.storeAvatar(new MockMultipartFile("file", "evil.html", "text/html", bytes.toByteArray()));
        assertEquals("image/png", file.getContentType());
        assertTrue(Files.exists(directory.resolve(file.getStorageKey())));
        assertEquals("/files/9", StorageService.url(file.getId()));
        RuntimeException databaseFailure = new IllegalStateException("database failed");
        doThrow(databaseFailure).when(mapper).insert(any());
        assertSame(databaseFailure, assertThrows(IllegalStateException.class,
                () -> service.storeAvatar(new MockMultipartFile("file", bytes.toByteArray()))));
        try (var paths = Files.walk(directory)) { assertEquals(1, paths.filter(Files::isRegularFile).count()); }
    }

    @Test void referencedFileCannotBeDeletedAndPhysicalFailureRetainsMetadata() throws Exception {
        StorageFile file = localFile();
        when(mapper.selectById(1L)).thenReturn(file);
        when(mapper.countAvatarReferences(1L)).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.delete(1L));
        verify(mapper, never()).delete(any());
        when(mapper.countAvatarReferences(1L)).thenReturn(0L);
        Files.createDirectories(directory.resolve(file.getStorageKey()));
        Files.writeString(directory.resolve(file.getStorageKey()).resolve("child"), "occupied");
        BusinessException failure = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertNotNull(failure.getCause());
        verify(mapper, never()).delete(any());
    }

    @Test void ossDeleteUsesRecordedSpaceAndFailureRetainsMetadata() {
        StorageFile file = localFile(); file.setProvider("aliyun"); file.setSpaceId(7L);
        StorageSpace space = new StorageSpace(); space.setId(7L); space.setBucketName("old-bucket"); space.setRegionId("oss-cn-hangzhou");
        when(mapper.selectById(1L)).thenReturn(file);
        when(settings.requireSpace(7L)).thenReturn(space);
        var credentials = new OssStorageClient.Credentials("id", "secret");
        when(settings.credentials(any())).thenReturn(credentials);
        BusinessException failure = new BusinessException(com.web.project.common.error.ErrorCode.INTERNAL_SERVER_ERROR, new IllegalStateException("remote failed"));
        doThrow(failure).when(oss).delete(space, credentials, file.getStorageKey());
        assertSame(failure, assertThrows(BusinessException.class, () -> service.delete(1L)));
        verify(mapper, never()).delete(any());
    }

    @Test void nullDeleteAndUrlAreEmptyOperations() {
        service.delete(null);
        assertNull(StorageService.url(null));
        verifyNoInteractions(mapper, oss);
    }

    @Test void ossUploadRecordsCapturedDestinationAndCompensatesMetadataFailure() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var config = new StorageSettingsService.Snapshot("aliyun", "id", "encrypted", 7L);
        when(settings.snapshot()).thenReturn(config);
        StorageSpace space = new StorageSpace(); space.setId(7L); space.setBucketName("old-bucket"); space.setRegionId("oss-cn-hangzhou");
        when(settings.requireSpace(7L)).thenReturn(space);
        var credentials = new OssStorageClient.Credentials("id", "secret");
        when(settings.credentials(config)).thenReturn(credentials);
        when(mapper.insert(any())).thenAnswer(call -> { StorageFile file = call.getArgument(0); file.setId(10L); return 1; });
        StorageFile file = service.storeAvatar(new MockMultipartFile("file", bytes.toByteArray()));
        assertEquals("aliyun", file.getProvider()); assertEquals(7L, file.getSpaceId());
        verify(oss).put(eq(space), eq(credentials), eq(file.getStorageKey()), any(), eq("image/png"));
        RuntimeException original = new IllegalStateException("metadata failed");
        doThrow(original).when(mapper).insert(any());
        doThrow(new BusinessException(com.web.project.common.error.ErrorCode.INTERNAL_SERVER_ERROR)).when(oss).delete(eq(space), eq(credentials), anyString());
        assertSame(original, assertThrows(IllegalStateException.class,
                () -> service.storeAvatar(new MockMultipartFile("file", bytes.toByteArray()))));
        assertEquals(1, original.getSuppressed().length);
    }

    @Test void historicalLocalAndOssFilesIgnoreCurrentGlobalProvider() throws Exception {
        StorageFile local = localFile(); local.setContentType("image/png");
        Files.write(directory.resolve(local.getStorageKey()), new byte[]{1, 2});
        when(mapper.selectById(1L)).thenReturn(local);
        assertNotNull(service.content(1L).resource());
        StorageFile remote = localFile(); remote.setProvider("aliyun"); remote.setSpaceId(7L);
        when(mapper.selectById(2L)).thenReturn(remote);
        StorageSpace space = new StorageSpace(); space.setId(7L); space.setBucketName("old-bucket"); space.setRegionId("oss-cn-hangzhou");
        when(settings.requireSpace(7L)).thenReturn(space);
        assertEquals("https://old-bucket.oss-cn-hangzhou.aliyuncs.com/avatar.png", service.content(2L).redirect().toString());
        space.setAccessDomain("https://cdn.example.com");
        assertEquals("https://cdn.example.com/avatar.png", service.content(2L).redirect().toString());
        verify(settings, never()).snapshot();
    }

    private StorageFile localFile() {
        StorageFile file = new StorageFile(); file.setId(1L); file.setProvider("local"); file.setStorageKey("avatar.png"); return file;
    }
}

package com.web.project.persistence;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.storage.config.FileProperties;
import com.web.project.storage.dto.StorageCredentialsDTO;
import com.web.project.storage.entity.StorageFile;
import com.web.project.storage.entity.StorageSpace;
import com.web.project.storage.mapper.StorageConfigMapper;
import com.web.project.storage.mapper.StorageFileMapper;
import com.web.project.storage.mapper.StorageSpaceMapper;
import com.web.project.storage.service.OssStorageClient;
import com.web.project.storage.service.StorageService;
import com.web.project.storage.service.StorageSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 仅连接显式指定的可丢弃 MySQL 库；OSS 是唯一被替换的外部边界。 */
@EnabledIfEnvironmentVariable(named = "ATLAS_MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
@SpringBootTest(properties = {
        "spring.datasource.url=${ATLAS_MYSQL_TEST_URL}",
        "spring.flyway.enabled=true",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.storage.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class StorageIntegrationTest {
    @Autowired private StorageSettingsService settings;
    @Autowired private StorageConfigMapper configs;
    @Autowired private StorageSpaceMapper spaces;
    @Autowired private StorageFileMapper files;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private OssStorageClient oss;
    @TempDir private Path uploadDirectory;

    private final Map<String, String> originalConfig = new LinkedHashMap<>();
    private final List<Long> createdSpaces = new ArrayList<>();
    private final List<Long> createdFiles = new ArrayList<>();
    private StorageService storage;

    @BeforeEach
    void setUp() {
        configs.selectStorageConfig().forEach(row -> originalConfig.put(row.getConfigKey(), row.getConfigValue()));
        configs.updateValue("storage.type", "local");
        configs.updateValue("storage.aliyun.default-space-id", "");
        when(oss.listBuckets(any())).thenReturn(List.of());
        settings.updateCredentials(new StorageCredentialsDTO("integration-key", "integration-secret"));
        storage = new StorageService(settings, files, oss, new FileProperties(uploadDirectory.toString()), transactionManager);
    }

    @AfterEach
    void restoreOwnData() {
        originalConfig.forEach(configs::updateValue);
        createdFiles.forEach(files::delete);
        createdSpaces.forEach(spaces::delete);
    }

    @Test
    void defaultAndReferencedSpacesCannotBeDeletedAndDeletingBindingDoesNotContactOss() {
        StorageSpace first = createSpace();
        StorageSpace second = createSpace();
        settings.setDefault(first.getId());
        clearInvocations(oss);
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(BusinessException.class, () -> settings.deleteSpace(first.getId())).getErrorCode());
        assertNotNull(spaces.selectById(first.getId()));
        verifyNoInteractions(oss);

        settings.setDefault(second.getId());
        StorageFile file = createFile(first.getId());
        clearInvocations(oss);
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(BusinessException.class, () -> settings.deleteSpace(first.getId())).getErrorCode());
        assertNotNull(spaces.selectById(first.getId()));
        assertEquals(1, files.delete(file.getId()));
        settings.deleteSpace(first.getId());
        assertNull(spaces.selectById(first.getId()));
        assertNotNull(spaces.selectById(second.getId()));
        verifyNoInteractions(oss);
    }

    @Test
    void concurrentDefaultSelectionLeavesOneDefaultSpace() throws Exception {
        StorageSpace first = createSpace();
        StorageSpace second = createSpace();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstAttempt = executor.submit(() -> selectDefault(first, ready, start));
            var secondAttempt = executor.submit(() -> selectDefault(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertDoesNotThrow(() -> firstAttempt.get(5, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> secondAttempt.get(5, TimeUnit.SECONDS));
        }

        Long selected = settings.snapshot().defaultSpaceId();
        assertTrue(Objects.equals(selected, first.getId()) || Objects.equals(selected, second.getId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_config WHERE config_key = 'storage.aliyun.default-space-id' "
                        + "AND config_value <> ''", Integer.class));
    }

    private Boolean selectDefault(StorageSpace space, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            settings.setDefault(space.getId());
        } catch (BusinessException expectedRace) {
            assertEquals(ErrorCode.BAD_REQUEST, expectedRace.getErrorCode());
        }
        return true;
    }

    @Test
    void failedSecondCredentialWriteRollsBackAccessKeyId() {
        configs.updateValue("storage.aliyun.access-key-id", "previous-key");
        configs.updateValue("storage.aliyun.access-key-secret", "");
        jdbc.execute("""
                ALTER TABLE sys_config ADD CONSTRAINT chk_storage_credential_fault
                CHECK (config_key <> 'storage.aliyun.access-key-secret' OR config_value = '')
                """);
        try {
            assertThrows(DataAccessException.class,
                    () -> settings.updateCredentials(new StorageCredentialsDTO("replacement-key", "replacement-secret")));
            var after = settings.snapshot();
            assertEquals("previous-key", after.accessKeyId());
            assertEquals("", after.encryptedSecret());
            assertFalse(after.configured());
        } finally {
            jdbc.execute("ALTER TABLE sys_config DROP CHECK chk_storage_credential_fault");
        }
    }

    @Test
    void inFlightOssPutDoesNotLockConfigAndDeletedDestinationCleansUploadedObject() throws Exception {
        StorageSpace destination = createSpace();
        StorageSpace replacement = createSpace();
        settings.setDefault(destination.getId());
        settings.updateType("aliyun");
        CountDownLatch putStarted = new CountDownLatch(1);
        CountDownLatch releasePut = new CountDownLatch(1);
        AtomicReference<String> uploadedKey = new AtomicReference<>();
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            uploadedKey.set(call.getArgument(2));
            putStarted.countDown();
            assertTrue(releasePut.await(15, TimeUnit.SECONDS), "Test did not release the OSS upload");
            return null;
        }).when(oss).put(any(), any(), anyString(), any(byte[].class), anyString());
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(oss).delete(any(), any(), anyString());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var upload = executor.submit(() -> storage.storeAvatar(png()));
            try {
                assertTrue(putStarted.await(5, TimeUnit.SECONDS), "Upload did not reach OSS");
                var change = executor.submit(() -> {
                    settings.setDefault(replacement.getId());
                    settings.deleteSpace(destination.getId());
                });
                change.get(5, TimeUnit.SECONDS);
                assertEquals(1, releasePut.getCount(), "OSS upload must still be waiting");
                assertNull(spaces.selectById(destination.getId()));
            } finally {
                releasePut.countDown();
            }
            ExecutionException failure = assertThrows(ExecutionException.class, () -> upload.get(5, TimeUnit.SECONDS));
            BusinessException cause = assertInstanceOf(BusinessException.class, failure.getCause());
            assertEquals(ErrorCode.BAD_REQUEST, cause.getErrorCode());
        }
        assertNotNull(uploadedKey.get());
        verify(oss).delete(argThat(space -> destination.getId().equals(space.getId())), any(), eq(uploadedKey.get()));
        assertEquals(0, files.countBySpaceId(destination.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM storage_file WHERE storage_key = ?",
                Integer.class, uploadedKey.get()));
        assertEquals(replacement.getId(), settings.snapshot().defaultSpaceId());
    }

    @Test
    void failedPhysicalOssDeleteRetainsRealMetadataForRetry() {
        StorageSpace destination = createSpace();
        StorageFile file = createFile(destination.getId());
        BusinessException remoteFailure = new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                new IllegalStateException("simulated remote failure"));
        doThrow(remoteFailure).when(oss).delete(any(), any(), eq(file.getStorageKey()));

        assertSame(remoteFailure, assertThrows(BusinessException.class, () -> storage.delete(file.getId())));
        StorageFile retained = files.selectById(file.getId());
        assertNotNull(retained);
        assertEquals(destination.getId(), retained.getSpaceId());
        assertEquals(file.getStorageKey(), retained.getStorageKey());
    }

    private StorageSpace createSpace() {
        StorageSpace space = new StorageSpace();
        space.setBucketName("atlas-it-" + UUID.randomUUID());
        space.setRegionId("oss-cn-hangzhou");
        assertEquals(1, spaces.insert(space));
        createdSpaces.add(space.getId());
        return space;
    }

    private StorageFile createFile(Long spaceId) {
        StorageFile file = new StorageFile();
        file.setProvider("aliyun");
        file.setSpaceId(spaceId);
        file.setStorageKey("avatars/integration-" + UUID.randomUUID() + ".png");
        file.setContentType("image/png");
        file.setSizeBytes(1L);
        assertEquals(1, files.insert(file));
        createdFiles.add(file.getId());
        return file;
    }

    private MockMultipartFile png() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes));
        return new MockMultipartFile("file", "avatar.png", "image/png", bytes.toByteArray());
    }
}

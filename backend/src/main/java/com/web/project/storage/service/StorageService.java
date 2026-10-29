package com.web.project.storage.service;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.storage.config.FileProperties;
import com.web.project.storage.entity.StorageFile;
import com.web.project.storage.entity.StorageSpace;
import com.web.project.storage.mapper.StorageFileMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class StorageService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final StorageSettingsService settings;
    private final StorageFileMapper fileMapper;
    private final OssStorageClient oss;
    private final Path root;
    private final TransactionTemplate transaction;

    public StorageService(StorageSettingsService settings, StorageFileMapper fileMapper, OssStorageClient oss,
                          FileProperties properties, PlatformTransactionManager transactionManager) {
        this.settings = settings;
        this.fileMapper = fileMapper;
        this.oss = oss;
        this.root = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public static String url(Long id) { return id == null ? null : "/files/" + id; }

    public StorageFile storeAvatar(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() > MAX_BYTES) {
            throw StoragePolicy.bad("头像文件不能为空或超过 2MB");
        }
        byte[] bytes;
        try (InputStream input = upload.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
        catch (IOException exception) { throw ioFailure(exception); }
        AvatarImage image = AvatarImage.inspect(bytes);
        StorageSettingsService.Snapshot config = settings.snapshot();
        StorageSpace destination = "aliyun".equals(config.storageType()) ? settings.requireSpace(config.defaultSpaceId()) : null;
        var credentials = destination == null ? null : settings.credentials(config);
        if (destination != null) { oss.validateSpace(destination, credentials); }
        StorageFile file = new StorageFile();
        file.setProvider(config.storageType());
        file.setSpaceId(destination == null ? null : destination.getId());
        file.setStorageKey("avatars/" + UUID.randomUUID() + "." + image.extension());
        file.setContentType(image.contentType());
        file.setSizeBytes(bytes.length);
        try {
            if (destination == null) {
                Path path = localPath(file);
                Files.createDirectories(path.getParent());
                Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
            } else { oss.put(destination, credentials, file.getStorageKey(), bytes, file.getContentType()); }
        } catch (IOException exception) {
            BusinessException failure = ioFailure(exception);
            cleanup(file, destination, credentials, failure);
            throw failure;
        } catch (RuntimeException exception) {
            cleanup(file, destination, credentials, exception);
            throw exception;
        }
        try {
            return transaction.execute(status -> {
                settings.lockConfig();
                // 切换只影响下一次上传；当前目的地必须仍然存在。
                if (destination != null) {
                    StorageSpace current = settings.requireSpace(destination.getId());
                    if (!destination.getBucketName().equals(current.getBucketName()) || !destination.getRegionId().equals(current.getRegionId())) {
                        throw StoragePolicy.bad("上传期间存储目的地发生变化，请重试");
                    }
                }
                if (fileMapper.insert(file) != 1 || file.getId() == null) { throw new IllegalStateException("文件元数据未写入预期记录"); }
                return file;
            });
        } catch (RuntimeException exception) {
            cleanup(file, destination, credentials, exception);
            throw exception;
        }
    }

    /** 先保留元数据完成物理删除；I/O 失败时记录仍可供排查和重试。 */
    public void delete(Long id) {
        if (id == null) { return; }
        StorageFile file = fileMapper.selectById(id);
        if (file == null) { return; }
        if (fileMapper.countAvatarReferences(id) > 0) { throw StoragePolicy.bad("文件仍被头像引用，不能删除"); }
        StorageSpace destination = "aliyun".equals(file.getProvider()) ? settings.requireSpace(file.getSpaceId()) : null;
        var credentials = destination == null ? null : settings.credentials(settings.snapshot());
        physicalDelete(file, destination, credentials);
        transaction.executeWithoutResult(status -> {
            settings.lockConfig();
            StorageFile locked = fileMapper.selectByIdForUpdate(id);
            if (locked == null) { return; }
            if (fileMapper.countAvatarReferences(id) > 0) { throw StoragePolicy.bad("文件仍被头像引用，不能删除"); }
            if (fileMapper.delete(id) != 1) { throw new IllegalStateException("文件元数据未删除预期记录"); }
        });
    }

    private void cleanup(StorageFile file, StorageSpace destination, OssStorageClient.Credentials credentials, RuntimeException original) {
        try { physicalDelete(file, destination, credentials); }
        catch (RuntimeException cleanupFailure) { original.addSuppressed(cleanupFailure); }
    }

    private void physicalDelete(StorageFile file, StorageSpace destination, OssStorageClient.Credentials credentials) {
        if (destination != null) { oss.delete(destination, credentials, file.getStorageKey()); return; }
        try { Files.deleteIfExists(localPath(file)); }
        catch (IOException exception) { throw ioFailure(exception); }
    }

    public record FileContent(StorageFile file, Resource resource, URI redirect) {}

    public FileContent content(Long id) {
        StorageFile file = fileMapper.selectById(id);
        if (file == null) { return null; }
        if ("local".equals(file.getProvider())) {
            Path path = localPath(file);
            if (!Files.isRegularFile(path)) { return null; }
            return new FileContent(file, new FileSystemResource(path), null);
        }
        StorageSpace space = settings.requireSpace(file.getSpaceId());
        String origin = space.getAccessDomain() == null ? "https://" + StoragePolicy.bucket(space.getBucketName())
                + "." + StoragePolicy.region(space.getRegionId()) + ".aliyuncs.com" : StoragePolicy.domain(space.getAccessDomain());
        return new FileContent(file, null, URI.create(origin + "/" + file.getStorageKey()));
    }

    private Path localPath(StorageFile file) {
        Path path = root.resolve(file.getStorageKey()).normalize();
        if (!path.startsWith(root)) { throw new IllegalStateException("文件存储路径超出上传目录"); }
        return path;
    }

    private BusinessException ioFailure(IOException exception) {
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "本地存储操作失败，请检查上传目录", exception);
    }
}

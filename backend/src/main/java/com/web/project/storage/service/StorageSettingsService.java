package com.web.project.storage.service;

import com.web.project.channel.entity.SysConfig;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.PageResult;
import com.web.project.storage.dto.BindStorageSpaceDTO;
import com.web.project.storage.dto.StorageCredentialsDTO;
import com.web.project.storage.entity.StorageSpace;
import com.web.project.storage.mapper.StorageConfigMapper;
import com.web.project.storage.mapper.StorageFileMapper;
import com.web.project.storage.mapper.StorageSpaceMapper;
import com.web.project.storage.vo.StorageSpaceVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class StorageSettingsService {
    private static final String TYPE = "storage.type";
    private static final String KEY_ID = "storage.aliyun.access-key-id";
    private static final String KEY_SECRET = "storage.aliyun.access-key-secret";
    private static final String DEFAULT_ID = "storage.aliyun.default-space-id";
    private final StorageConfigMapper configMapper;
    private final StorageSpaceMapper spaceMapper;
    private final StorageFileMapper fileMapper;
    private final CredentialCipher cipher;
    private final OssStorageClient oss;
    private final TransactionTemplate transaction;

    public StorageSettingsService(StorageConfigMapper configMapper, StorageSpaceMapper spaceMapper,
                                  StorageFileMapper fileMapper, CredentialCipher cipher, OssStorageClient oss,
                                  PlatformTransactionManager transactionManager) {
        this.configMapper = configMapper;
        this.spaceMapper = spaceMapper;
        this.fileMapper = fileMapper;
        this.cipher = cipher;
        this.oss = oss;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public record Snapshot(String storageType, String accessKeyId, String encryptedSecret, Long defaultSpaceId) {
        public boolean configured() { return !accessKeyId.isBlank() && !encryptedSecret.isBlank(); }
        @Override public String toString() { return "StorageSnapshot[storageType=" + storageType + ", credentials=redacted]"; }
    }

    public Snapshot snapshot() { return decode(configMapper.selectStorageConfig()); }
    public Snapshot lockConfig() { return decode(configMapper.selectStorageConfigForUpdate()); }

    private Snapshot decode(List<SysConfig> rows) {
        Map<String, String> values = rows.stream().collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
        if (!values.keySet().containsAll(List.of(TYPE, KEY_ID, KEY_SECRET, DEFAULT_ID))
                || !("local".equals(values.get(TYPE)) || "aliyun".equals(values.get(TYPE)))) {
            throw new IllegalStateException("存储配置缺失或格式错误");
        }
        String defaultValue = values.get(DEFAULT_ID);
        Long defaultId = defaultValue.isEmpty() ? null : Long.valueOf(defaultValue);
        return new Snapshot(values.get(TYPE), values.get(KEY_ID), values.get(KEY_SECRET), defaultId);
    }

    public OssStorageClient.Credentials credentials(Snapshot snapshot) {
        if (!snapshot.configured()) { throw StoragePolicy.bad("请先配置阿里云凭据"); }
        return new OssStorageClient.Credentials(snapshot.accessKeyId(), cipher.decrypt(snapshot.encryptedSecret()));
    }

    public Snapshot updateType(String type) {
        if (!"local".equals(type) && !"aliyun".equals(type)) { throw StoragePolicy.bad("存储方式只能是 local 或 aliyun"); }
        Snapshot before = snapshot();
        StorageSpace validatedSpace = "aliyun".equals(type) ? requireSpace(before.defaultSpaceId()) : null;
        if ("aliyun".equals(type)) {
            oss.validateSpace(validatedSpace, credentials(before));
        }
        return transaction.execute(status -> {
            requireUnchanged(before, lockConfig());
            if (validatedSpace != null) { requireSameDestination(validatedSpace, requireSpace(before.defaultSpaceId())); }
            write(TYPE, type);
            return snapshot();
        });
    }

    public void updateCredentials(StorageCredentialsDTO dto) {
        String encrypted = cipher.encrypt(dto.accessKeySecret());
        Snapshot before = snapshot();
        List<StorageSpace> existingSpaces = selectAllSpaces();
        var supplied = new OssStorageClient.Credentials(dto.accessKeyId(), dto.accessKeySecret());
        oss.listBuckets(supplied);
        for (StorageSpace existingSpace : existingSpaces) {
            oss.validateSpace(existingSpace, supplied);
        }
        transaction.executeWithoutResult(status -> {
            requireUnchanged(before, lockConfig());
            requireUnchangedSpaces(existingSpaces);
            write(KEY_ID, dto.accessKeyId());
            write(KEY_SECRET, encrypted);
        });
    }

    public PageResult<StorageSpaceVO> spaces(int page, int pageSize) {
        Snapshot config = snapshot();
        return new PageResult<>(spaceMapper.count(), page, pageSize,
                spaceMapper.selectPage((long) (page - 1) * pageSize, pageSize).stream()
                        .map(space -> StorageSpaceVO.of(space, config.defaultSpaceId())).toList());
    }

    public StorageSpaceVO bind(BindStorageSpaceDTO dto) {
        // Fail before contacting OSS or persisting candidate credentials.
        if (!"public-read".equals(dto.accessPermission())) {
            throw StoragePolicy.bad("头像存储空间禁止公共读写权限，仅允许 public-read");
        }
        StorageSpace space = new StorageSpace();
        space.setBucketName(StoragePolicy.bucket(dto.bucketName()));
        space.setRegionId(StoragePolicy.region(dto.regionId()));
        space.setAccessDomain(StoragePolicy.domain(dto.accessDomain()));
        Snapshot before = snapshot();
        boolean hasCandidateCredentials = dto.accessKeyId() != null || dto.accessKeySecret() != null;
        if (hasCandidateCredentials && (dto.accessKeyId() == null || dto.accessKeySecret() == null)) {
            throw StoragePolicy.bad("新凭据必须同时提供 AccessKeyId 和 AccessKeySecret");
        }
        String encryptedSecret = null;
        OssStorageClient.Credentials bindingCredentials;
        List<StorageSpace> existingSpaces = List.of();
        if (hasCandidateCredentials) {
            bindingCredentials = new OssStorageClient.Credentials(dto.accessKeyId(), dto.accessKeySecret());
            oss.listBuckets(bindingCredentials);
            existingSpaces = selectAllSpaces();
            for (StorageSpace existingSpace : existingSpaces) {
                oss.validateSpace(existingSpace, bindingCredentials);
            }
            encryptedSecret = cipher.encrypt(dto.accessKeySecret());
        } else {
            bindingCredentials = credentials(before);
        }
        String actualPermission = oss.validateSpace(space, bindingCredentials);
        if (!"public-read".equals(actualPermission)) {
            throw StoragePolicy.bad("Bucket 实际权限必须是公共读（public-read），禁止匿名写入");
        }
        if (!dto.accessPermission().equals(actualPermission)) {
            throw StoragePolicy.bad("Bucket 实际读写权限与选择的权限不一致");
        }
        final String candidateSecret = encryptedSecret;
        final List<StorageSpace> validatedExistingSpaces = existingSpaces;
        try {
            return transaction.execute(status -> {
                requireUnchanged(before, lockConfig());
                if (hasCandidateCredentials) {
                    requireUnchangedSpaces(validatedExistingSpaces);
                    write(KEY_ID, dto.accessKeyId());
                    write(KEY_SECRET, candidateSecret);
                }
                requireWrite(spaceMapper.insert(space));
                return StorageSpaceVO.of(requireSpace(space.getId()), before.defaultSpaceId());
            });
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该 Bucket 已绑定", exception);
        }
    }

    public int sync() {
        Snapshot before = snapshot();
        var credentials = credentials(before);
        List<OssStorageClient.BucketInfo> buckets = oss.listBuckets(credentials);
        // Reject a sync containing an unsafe Bucket before persisting ANY changes.
        // The OSS SDK checks actual server-side ACL, not a caller-supplied label.
        for (var bucket : buckets) {
            StorageSpace candidate = new StorageSpace();
            candidate.setBucketName(StoragePolicy.bucket(bucket.bucketName()));
            candidate.setRegionId(StoragePolicy.region(bucket.regionId()));
            if (!"public-read".equals(oss.validateSpace(candidate, credentials))) {
                throw StoragePolicy.bad("同步失败：Bucket 实际权限必须是公共读（public-read）");
            }
        }
        return transaction.execute(status -> {
            requireUnchanged(before, lockConfig());
            int synced = 0;
            for (var bucket : buckets) {
                StorageSpace existing = spaceMapper.selectByBucketName(bucket.bucketName());
                if (existing != null) {
                    // 文件依赖原地域；仅无引用空间可以同步真实地域。
                    if (!existing.getRegionId().equals(bucket.regionId())) {
                        if (fileMapper.countBySpaceId(existing.getId()) > 0) {
                            throw StoragePolicy.bad("已绑定 Bucket 的地域与云端不一致，已有文件引用，不能更改地域");
                        }
                        requireWrite(spaceMapper.updateRegion(existing.getId(), bucket.regionId()));
                        synced++;
                    }
                    continue;
                }
                StorageSpace space = new StorageSpace();
                space.setBucketName(StoragePolicy.bucket(bucket.bucketName()));
                space.setRegionId(StoragePolicy.region(bucket.regionId()));
                requireWrite(spaceMapper.insert(space));
                synced++;
            }
            return synced;
        });
    }

    public StorageSpaceVO setDefault(Long id) {
        Snapshot before = snapshot();
        StorageSpace space = requireSpace(id);
        oss.validateSpace(space, credentials(before));
        return transaction.execute(status -> {
            requireUnchanged(before, lockConfig());
            StorageSpace current = requireSpace(id);
            requireSameDestination(space, current);
            write(DEFAULT_ID, id.toString());
            return StorageSpaceVO.of(current, id);
        });
    }

    public StorageSpaceVO updateDomain(Long id, String domain) {
        String normalized = StoragePolicy.domain(domain);
        return transaction.execute(status -> {
            Snapshot config = lockConfig();
            requireSpace(id);
            requireWrite(spaceMapper.updateDomain(id, normalized));
            return StorageSpaceVO.of(requireSpace(id), config.defaultSpaceId());
        });
    }

    public void deleteSpace(Long id) {
        transaction.executeWithoutResult(status -> {
            Snapshot config = lockConfig();
            requireSpace(id);
            if (Objects.equals(id, config.defaultSpaceId())) { throw StoragePolicy.bad("默认空间不能删除"); }
            if (fileMapper.countBySpaceId(id) > 0) { throw StoragePolicy.bad("空间仍有文件引用，不能删除"); }
            requireWrite(spaceMapper.delete(id));
        });
    }

    public StorageSpace requireSpace(Long id) {
        StorageSpace space = id == null ? null : spaceMapper.selectById(id);
        if (space == null) { throw StoragePolicy.bad("存储空间不存在或尚未选择默认空间"); }
        return space;
    }

    private void requireUnchanged(Snapshot expected, Snapshot actual) {
        if (!expected.equals(actual)) { throw StoragePolicy.bad("存储配置已更新，请刷新后重试"); }
    }

    private List<StorageSpace> selectAllSpaces() {
        long total = spaceMapper.count();
        List<StorageSpace> spaces = new java.util.ArrayList<>();
        for (long offset = 0; offset < total; offset += 1000) {
            spaces.addAll(spaceMapper.selectPage(offset, 1000));
        }
        return List.copyOf(spaces);
    }

    private void requireUnchangedSpaces(List<StorageSpace> expected) {
        List<SpaceDestination> expectedDestinations = expected.stream().map(SpaceDestination::of).toList();
        List<SpaceDestination> currentDestinations = selectAllSpaces().stream().map(SpaceDestination::of).toList();
        if (!expectedDestinations.equals(currentDestinations)) {
            throw StoragePolicy.bad("存储空间列表已更新，请刷新后重试");
        }
    }

    private record SpaceDestination(Long id, String bucketName, String regionId) {
        private static SpaceDestination of(StorageSpace space) {
            return new SpaceDestination(space.getId(), space.getBucketName(), space.getRegionId());
        }
    }

    private void requireSameDestination(StorageSpace expected, StorageSpace actual) {
        if (!expected.getBucketName().equals(actual.getBucketName()) || !expected.getRegionId().equals(actual.getRegionId())) {
            throw StoragePolicy.bad("空间目的地已更新，请刷新后重试");
        }
    }

    private void write(String key, String value) { requireWrite(configMapper.updateValue(key, value)); }
    private void requireWrite(int rows) {
        if (rows != 1) { throw new IllegalStateException("存储数据未写入预期记录"); }
    }
}

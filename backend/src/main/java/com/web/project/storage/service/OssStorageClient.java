package com.web.project.storage.service;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ListBucketsRequest;
import com.aliyun.oss.model.BucketList;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.storage.entity.StorageSpace;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

/** SDK 只接收官方标准地域 Endpoint，访问域名仅用于公开文件跳转。 */
@Component
public class OssStorageClient {
    public record Credentials(String accessKeyId, String accessKeySecret) {
        @Override public String toString() { return "Credentials[redacted]"; }
    }
    public record BucketInfo(String bucketName, String regionId) {}

    public List<BucketInfo> listBuckets(Credentials credentials) {
        return invoke("oss-cn-hangzhou", credentials, client -> {
            List<BucketInfo> buckets = new ArrayList<>();
            ListBucketsRequest request = new ListBucketsRequest().withMaxKeys(1000);
            BucketList page;
            do {
                page = client.listBuckets(request);
                for (var bucket : page.getBucketList()) {
                    if (StoragePolicy.supportedRegion(bucket.getLocation())) {
                        buckets.add(new BucketInfo(bucket.getName(), StoragePolicy.region(bucket.getLocation())));
                    }
                }
                request.setMarker(page.getNextMarker());
            } while (page.isTruncated());
            return buckets;
        });
    }

    public String validateSpace(StorageSpace space, Credentials credentials) {
        return invoke(space.getRegionId(), credentials, client -> {
            String actualRegion = StoragePolicy.region(client.getBucketLocation(space.getBucketName()));
            if (!space.getRegionId().equals(actualRegion)) { throw StoragePolicy.bad("Bucket 实际地域与选择的地域不一致"); }
            CannedAccessControlList permission = client.getBucketAcl(space.getBucketName()).getCannedACL();
            if (permission != CannedAccessControlList.PublicRead) {
                throw StoragePolicy.bad("头像空间仅允许公共读（PublicRead），禁止公共读写或私有 Bucket");
            }
            return permission.toString();
        });
    }

    public void put(StorageSpace space, Credentials credentials, String key, byte[] bytes, String contentType) {
        invoke(space.getRegionId(), credentials, client -> {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType);
            client.putObject(space.getBucketName(), key, new ByteArrayInputStream(bytes), metadata);
            return null;
        });
    }

    public void delete(StorageSpace space, Credentials credentials, String key) {
        invoke(space.getRegionId(), credentials, client -> { client.deleteObject(space.getBucketName(), key); return null; });
    }

    private <T> T invoke(String region, Credentials credentials, Function<OSS, T> operation) {
        String endpoint = StoragePolicy.endpoint(region);
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setConnectionTimeout(5_000);
        configuration.setSocketTimeout(10_000);
        configuration.setRequestTimeout(15_000);
        configuration.setRequestTimeoutEnabled(true);
        configuration.setMaxErrorRetry(0);
        OSS client = null;
        try {
            client = new OSSClientBuilder().build(endpoint, credentials.accessKeyId(), credentials.accessKeySecret(), configuration);
            return operation.apply(client);
        }
        catch (BusinessException exception) { throw exception; }
        catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "阿里云存储操作失败，请检查凭据、权限和空间配置", exception);
        } finally { if (client != null) { client.shutdown(); } }
    }
}

package com.web.project.storage.service;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.*;
import com.web.project.common.exception.BusinessException;
import com.web.project.storage.entity.StorageSpace;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OssStorageClientTest {
    private final OssStorageClient service = new OssStorageClient();
    private final OssStorageClient.Credentials credentials = new OssStorageClient.Credentials("id", "secret");

    @Test void bucketSyncReadsEveryPageAndClosesSdkClient() {
        OSS client = mock(OSS.class);
        Bucket first = bucket("first-bucket", "oss-cn-hangzhou");
        Bucket second = bucket("second-bucket", "oss-cn-shanghai");
        BucketList page1 = new BucketList(); page1.setBucketList(List.of(first)); page1.setTruncated(true); page1.setNextMarker("first-bucket");
        BucketList page2 = new BucketList(); page2.setBucketList(List.of(second)); page2.setTruncated(false);
        when(client.listBuckets(any(ListBucketsRequest.class))).thenReturn(page1, page2);
        try (var builders = mockConstruction(OSSClientBuilder.class, (builder, context) ->
                when(builder.build(anyString(), anyString(), anyString(), any(ClientBuilderConfiguration.class))).thenReturn(client))) {
            assertEquals(2, service.listBuckets(credentials).size());
            verify(client, times(2)).listBuckets(any(ListBucketsRequest.class));
            verify(client).shutdown();
        }
    }

    @Test void publicReadIsAcceptedAndClientIsAlwaysClosed() {
        for (var acl : List.of(CannedAccessControlList.PublicRead)) {
            OSS client = mock(OSS.class);
            when(client.getBucketLocation("avatar-bucket")).thenReturn("oss-cn-hangzhou");
            AccessControlList access = new AccessControlList(); access.setCannedACL(acl);
            when(client.getBucketAcl("avatar-bucket")).thenReturn(access);
            try (var builders = mockConstruction(OSSClientBuilder.class, (builder, context) ->
                    when(builder.build(anyString(), anyString(), anyString(), any(ClientBuilderConfiguration.class))).thenReturn(client))) {
                StorageSpace space = new StorageSpace(); space.setBucketName("avatar-bucket"); space.setRegionId("oss-cn-hangzhou");
                assertEquals(acl.toString(), service.validateSpace(space, credentials));
                verify(client).shutdown();
            }
        }
    }

    @Test void publicReadWriteBucketIsRejectedAndClientIsClosed() {
        OSS client = mock(OSS.class);
        when(client.getBucketLocation("avatar-bucket")).thenReturn("oss-cn-hangzhou");
        AccessControlList access = new AccessControlList();
        access.setCannedACL(CannedAccessControlList.PublicReadWrite);
        when(client.getBucketAcl("avatar-bucket")).thenReturn(access);
        try (var builders = mockConstruction(OSSClientBuilder.class, (builder, context) ->
                when(builder.build(anyString(), anyString(), anyString(), any(ClientBuilderConfiguration.class))).thenReturn(client))) {
            StorageSpace space = new StorageSpace(); space.setBucketName("avatar-bucket"); space.setRegionId("oss-cn-hangzhou");
            BusinessException denied = assertThrows(BusinessException.class, () -> service.validateSpace(space, credentials));
            assertTrue(denied.getMessage().contains("公共读"));
            verify(client).shutdown();
        }
    }

    @Test void privateBucketIsRejectedAndClientIsClosed() {
        OSS client = mock(OSS.class);
        when(client.getBucketLocation("avatar-bucket")).thenReturn("oss-cn-hangzhou");
        AccessControlList access = new AccessControlList(); access.setCannedACL(CannedAccessControlList.Private);
        when(client.getBucketAcl("avatar-bucket")).thenReturn(access);
        try (var builders = mockConstruction(OSSClientBuilder.class, (builder, context) ->
                when(builder.build(anyString(), anyString(), anyString(), any(ClientBuilderConfiguration.class))).thenReturn(client))) {
            StorageSpace space = new StorageSpace(); space.setBucketName("avatar-bucket"); space.setRegionId("oss-cn-hangzhou");
            assertThrows(BusinessException.class, () -> service.validateSpace(space, credentials));
            verify(client).shutdown();
        }
    }

    @Test void providerFailureKeepsCauseWithoutLeakingMessage() {
        OSS client = mock(OSS.class);
        RuntimeException cause = new RuntimeException("provider secret details");
        doThrow(cause).when(client).deleteObject("avatar-bucket", "avatar.png");
        try (var builders = mockConstruction(OSSClientBuilder.class, (builder, context) ->
                when(builder.build(anyString(), anyString(), anyString(), any(ClientBuilderConfiguration.class))).thenReturn(client))) {
            StorageSpace space = new StorageSpace(); space.setBucketName("avatar-bucket"); space.setRegionId("oss-cn-hangzhou");
            BusinessException failure = assertThrows(BusinessException.class, () -> service.delete(space, credentials, "avatar.png"));
            assertSame(cause, failure.getCause()); assertFalse(failure.getMessage().contains("secret details"));
            verify(client).shutdown();
        }
    }

    private Bucket bucket(String name, String location) { Bucket bucket = new Bucket(name); bucket.setLocation(location); return bucket; }
}

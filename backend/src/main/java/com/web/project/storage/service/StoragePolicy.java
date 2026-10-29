package com.web.project.storage.service;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public final class StoragePolicy {
    private static final Set<String> REGIONS = Set.of(
            "cn-hangzhou", "cn-shanghai", "cn-wuhan-lr", "cn-qingdao", "cn-beijing", "cn-zhangjiakou",
            "cn-huhehaote", "cn-wulanchabu", "cn-shenzhen", "cn-heyuan", "cn-guangzhou", "cn-chengdu",
            "cn-zhongwei", "cn-hongkong", "ap-northeast-1", "ap-northeast-2", "ap-southeast-1",
            "ap-southeast-3", "ap-southeast-5", "ap-southeast-6", "ap-southeast-7", "ap-southeast-8",
            "sa-east-1", "eu-central-1", "eu-west-1", "us-west-1", "us-east-1", "na-south-1",
            "eu-west-2", "me-east-1");

    private StoragePolicy() {}

    public static boolean supportedRegion(String value) {
        return value != null && REGIONS.contains(value.startsWith("oss-") ? value.substring(4) : value);
    }

    public static String region(String value) {
        String normalized = value != null && value.startsWith("oss-") ? value.substring(4) : value;
        if (!REGIONS.contains(normalized == null ? "" : normalized)) { throw bad("请选择支持的阿里云地域"); }
        return "oss-" + normalized;
    }

    public static String endpoint(String value) { return "https://" + region(value) + ".aliyuncs.com"; }

    public static String bucket(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")) { throw bad("Bucket 名称格式不正确"); }
        return value;
    }

    public static String domain(String value) {
        if (value == null || value.isBlank()) { return null; }
        try {
            URI uri = new URI(value.trim());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                    || (uri.getPort() != -1 && uri.getPort() != 443) || value.length() > 255) {
                throw bad("访问域名必须是 HTTPS 域名，不包含路径、凭据、查询或片段");
            }
            return "https://" + uri.getHost().toLowerCase(java.util.Locale.ROOT);
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "访问域名格式不正确", exception);
        }
    }

    public static BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
}

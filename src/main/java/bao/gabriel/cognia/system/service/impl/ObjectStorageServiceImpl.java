package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.dto.storage.AdminStorageSettingResponse;
import bao.gabriel.cognia.system.dto.storage.PresignedUrlResponse;
import bao.gabriel.cognia.system.dto.storage.StorageConnectionTestResponse;
import bao.gabriel.cognia.system.dto.storage.StoredObjectResponse;
import bao.gabriel.cognia.system.dto.storage.UpdateStorageSettingRequest;
import bao.gabriel.cognia.system.entity.StorageSetting;
import bao.gabriel.cognia.system.mapper.StorageSettingMapper;
import bao.gabriel.cognia.system.service.ObjectStorageService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListMultipartUploadsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.MultipartUpload;
import com.amazonaws.services.s3.model.MultipartUploadListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.SetBucketLifecycleConfigurationRequest;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Database-backed S3-compatible storage. Every operation resolves the current
 * configuration from {@code sys_storage_setting}; the security credential is
 * decrypted only in memory when an S3 client is required.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectStorageServiceImpl implements ObjectStorageService {

    private static final long CACHE_TTL_MS = 10_000L;
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String DEFAULT_REGION = "us-east-1";
    private static final int DEFAULT_MAX_FILE_SIZE_MB = 100;
    private static final int DEFAULT_PRESIGNED_EXPIRY_SECONDS = 3600;
    private static final int DEFAULT_TEMP_RETENTION_DAYS = 7;
    private static final String RULE_TEMP_EXPIRATION = "cognia-tmp-expiration";

    private final StorageSettingMapper storageSettingMapper;
    private final SecurityCryptoService cryptoService;
    private final SiteSettingService siteSettingService;

    private final Object settingLock = new Object();
    private final Object clientLock = new Object();

    private volatile StorageSetting cachedSetting;
    private volatile long cachedAt;
    private volatile AmazonS3 cachedClient;
    private volatile String cachedClientSignature;

    @Override
    public AdminStorageSettingResponse getAdminSettings() {
        return toResponse(getSetting());
    }

    @Override
    @Transactional
    public AdminStorageSettingResponse updateSettings(UpdateStorageSettingRequest request, Long operatorId) {
        // Work on a detached copy: getSetting() is cached in memory and a
        // failed validation must not leave unsaved values in that cache.
        StorageSetting setting = storageSettingMapper.selectById(StorageSetting.SINGLETON_ID);
        if (setting == null) {
            setting = createDefaultSetting();
        }

        if (request.endpoint() != null) {
            setting.setEndpoint(normalizeHttpUrl(request.endpoint(), "S3 Endpoint"));
        }
        if (request.publicBaseUrl() != null) {
            setting.setPublicBaseUrl(normalizeHttpUrl(request.publicBaseUrl(), "公共访问地址"));
        }
        if (request.region() != null) {
            String region = trimToNull(request.region());
            setting.setRegion(region == null ? DEFAULT_REGION : normalizeRegion(region));
        }
        if (request.bucket() != null) {
            setting.setBucket(normalizeBucket(request.bucket()));
        }
        if (request.accessKey() != null) {
            setting.setAccessKey(trimToNull(request.accessKey()));
        }
        if (request.objectPrefix() != null) {
            setting.setObjectPrefix(normalizePath(request.objectPrefix(), "对象键前缀"));
        }
        if (request.maxFileSizeMb() != null) {
            setting.setMaxFileSizeMb(request.maxFileSizeMb());
        }
        if (request.presignedUrlExpirySeconds() != null) {
            setting.setPresignedUrlExpirySeconds(request.presignedUrlExpirySeconds());
        }
        if (request.tempRetentionDays() != null) {
            setting.setTempRetentionDays(request.tempRetentionDays());
        }
        if (request.pathStyleAccess() != null) {
            setting.setPathStyleAccess(request.pathStyleAccess());
        }
        if (request.enabled() != null) {
            setting.setEnabled(request.enabled());
        }
        if (Boolean.TRUE.equals(request.clearSecretKey())) {
            setting.setSecretKey(null);
        } else if (StringUtils.hasText(request.secretKey())) {
            setting.setSecretKey(cryptoService.encrypt(request.secretKey()));
        }

        validate(setting);

        LocalDateTime now = LocalDateTime.now();
        setting.setUpdatedBy(operatorId);
        setting.setUpdatedAt(now);
        if (setting.getRegion() == null) {
            setting.setRegion(DEFAULT_REGION);
        }
        if (setting.getMaxFileSizeMb() == null) {
            setting.setMaxFileSizeMb(DEFAULT_MAX_FILE_SIZE_MB);
        }
        if (setting.getPresignedUrlExpirySeconds() == null) {
            setting.setPresignedUrlExpirySeconds(DEFAULT_PRESIGNED_EXPIRY_SECONDS);
        }
        if (setting.getTempRetentionDays() == null) {
            setting.setTempRetentionDays(DEFAULT_TEMP_RETENTION_DAYS);
        }

        if (storageSettingMapper.selectById(StorageSetting.SINGLETON_ID) == null) {
            setting.setCreateTime(now);
            storageSettingMapper.insert(setting);
        } else {
            storageSettingMapper.updateById(setting);
        }

        synchronized (settingLock) {
            cachedSetting = setting;
            cachedAt = System.currentTimeMillis();
        }
        // Keep the bucket lifecycle in sync as soon as the admin saves the
        // retention value; the scheduled task remains a safety net.
        if (isConfigured(setting)) {
            try {
                syncTempLifecycle(setting.tempRetentionDays());
            } catch (Exception ex) {
                log.warn("Unable to sync tmp/ lifecycle rule after saving storage settings: {}", ex.getMessage());
            }
        }
        return toResponse(setting);
    }

    @Override
    public StorageConnectionTestResponse testConnection() {
        StorageSetting setting = getSetting();
        String testedAt = siteSettingService.formatDateTime(LocalDateTime.now());
        if (!isConfigured(setting)) {
            return new StorageConnectionTestResponse(false, false, false, false, false, 0L,
                    setting.getEndpoint(), setting.getRegion(), setting.getBucket(),
                    "请先填写 Bucket、AccessKey 与 SecretKey", testedAt);
        }

        long started = System.currentTimeMillis();
        boolean bucketExists = false;
        boolean canWrite = false;
        boolean canRead = false;
        boolean canDelete = false;
        String failure = null;
        String bucketProbeFailure = null;
        String key = testObjectKey(setting);
        byte[] payload = "Cognia object storage connectivity test".getBytes(StandardCharsets.UTF_8);

        try {
            AmazonS3 client = client(setting);
            try {
                bucketExists = client.doesBucketExistV2(setting.getBucket());
            } catch (Exception ex) {
                bucketProbeFailure = describeError(ex);
            }
            try {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(payload.length);
                metadata.setContentType("text/plain; charset=utf-8");
                client.putObject(setting.getBucket(), key, new ByteArrayInputStream(payload), metadata);
                canWrite = true;
                try (S3Object object = client.getObject(setting.getBucket(), key);
                     InputStream content = object.getObjectContent()) {
                    byte[] stored = content.readAllBytes();
                    canRead = Arrays.equals(payload, stored);
                    if (!canRead) {
                        failure = "读取到的内容与写入内容不一致";
                    }
                }
            } catch (Exception ex) {
                failure = describeError(ex);
            } finally {
                if (canWrite) {
                    try {
                        client.deleteObject(setting.getBucket(), key);
                        canDelete = true;
                    } catch (Exception ex) {
                        failure = "测试对象清理失败：" + describeError(ex);
                    }
                }
            }
        } catch (Exception ex) {
            failure = describeError(ex);
        }

        long latencyMs = System.currentTimeMillis() - started;
        boolean success = canWrite && canRead && canDelete;
        String message;
        if (success) {
            message = bucketProbeFailure == null
                    ? "连接正常：Bucket 可访问，写入、读取与删除测试均通过"
                    : "读写删测试通过；HeadBucket 请求被拒绝（可能缺少 Bucket 读取权限）：" + bucketProbeFailure;
        } else if (failure != null) {
            message = failure;
        } else {
            message = "连接测试未通过，请检查 Endpoint、区域与 Bucket 配置";
        }
        // A successful write proves the bucket exists even when HeadBucket is
        // denied by a least-privilege policy.
        return new StorageConnectionTestResponse(success, bucketExists || canWrite, canWrite, canRead, canDelete,
                latencyMs, setting.getEndpoint(), setting.getRegion(), setting.getBucket(), message, testedAt);
    }

    @Override
    public boolean isEnabled() {
        return getSetting().enabled();
    }

    @Override
    public boolean isConfigured() {
        return isConfigured(getSetting());
    }

    @Override
    public StoredObjectResponse upload(MultipartFile file, String directory) {
        StorageSetting setting = getSetting();
        if (!setting.enabled()) {
            throw BusinessException.badRequest("对象存储尚未启用，请先在控制台开启并保存配置");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }
        long maxBytes = (long) setting.maxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw BusinessException.badRequest("文件大小不能超过 " + setting.maxFileSizeMb() + " MB");
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        List<String> allowedTypes = siteSettingService.getAllowedUploadTypes();
        if (!allowedTypes.isEmpty()) {
            if (extension.isEmpty()) {
                throw BusinessException.badRequest("无法识别文件扩展名，允许的类型：" + String.join("、", allowedTypes));
            }
            if (!allowedTypes.contains(extension)) {
                throw BusinessException.badRequest("不支持上传 ." + extension + " 文件");
            }
        }

        String key = buildKey(setting, directory, extension);
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType() : "application/octet-stream";
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);
            try (InputStream input = file.getInputStream()) {
                client(setting).putObject(setting.getBucket(), key, input, metadata);
            }
        } catch (Exception ex) {
            String reason = describeError(ex);
            log.warn("Object upload failed for bucket {} key {}", setting.getBucket(), key, ex);
            throw BusinessException.badRequest("对象上传失败：" + reason);
        }

        String url;
        try {
            url = objectUrl(setting, key);
        } catch (Exception ex) {
            log.warn("Object uploaded but URL generation failed for key {}", key, ex);
            url = null;
        }
        return new StoredObjectResponse(key, url, file.getSize(), contentType,
                siteSettingService.formatDateTime(LocalDateTime.now()));
    }

    @Override
    public void deleteObject(String key) {
        StorageSetting setting = getSetting();
        if (!isConfigured(setting)) {
            throw BusinessException.badRequest("请先在对象存储设置中完成配置");
        }
        String objectKey = normalizeObjectKey(key);
        try {
            client(setting).deleteObject(setting.getBucket(), objectKey);
        } catch (AmazonS3Exception ex) {
            if (ex.getStatusCode() != 404) {
                throw BusinessException.badRequest("删除对象失败：" + describeError(ex));
            }
        } catch (Exception ex) {
            throw BusinessException.badRequest("删除对象失败：" + describeError(ex));
        }
    }

    @Override
    public int deletePrefix(String prefix) {
        StorageSetting setting = getSetting();
        if (!isConfigured(setting)) {
            throw BusinessException.badRequest("请先在对象存储设置中完成配置");
        }
        String normalized = normalizePrefix(prefix);
        int deleted = 0;
        String continuationToken = null;
        do {
            ListObjectsV2Request listRequest = new ListObjectsV2Request()
                    .withBucketName(setting.getBucket())
                    .withPrefix(normalized);
            if (continuationToken != null) {
                listRequest.setContinuationToken(continuationToken);
            }
            ListObjectsV2Result result = client(setting).listObjectsV2(listRequest);
            List<String> keys = result.getObjectSummaries().stream()
                    .map(summary -> summary.getKey())
                    .filter(key -> key != null && !key.endsWith("/"))
                    .toList();
            if (!keys.isEmpty()) {
                client(setting).deleteObjects(new DeleteObjectsRequest(setting.getBucket())
                        .withKeys(keys.toArray(new String[0])));
                deleted += keys.size();
            }
            continuationToken = Boolean.TRUE.equals(result.isTruncated())
                    ? result.getNextContinuationToken() : null;
        } while (continuationToken != null);
        return deleted;
    }

    @Override
    public int abortIncompleteMultipartUploads(String prefix, int olderThanHours, int limit) {
        StorageSetting setting = getSetting();
        if (!isConfigured(setting)) {
            throw BusinessException.badRequest("请先在对象存储设置中完成配置");
        }
        String normalized = normalizePrefix(prefix);
        Date cutoff = new Date(System.currentTimeMillis() - Math.max(1, olderThanHours) * 3600_000L);
        int aborted = 0;
        String keyMarker = null;
        String uploadIdMarker = null;
        do {
            ListMultipartUploadsRequest request = new ListMultipartUploadsRequest(setting.getBucket())
                    .withPrefix(normalized);
            if (keyMarker != null) {
                request.withKeyMarker(keyMarker);
            }
            if (uploadIdMarker != null) {
                request.withUploadIdMarker(uploadIdMarker);
            }
            MultipartUploadListing listing = client(setting).listMultipartUploads(request);
            for (MultipartUpload upload : listing.getMultipartUploads()) {
                if (aborted >= limit) {
                    return aborted;
                }
                if (upload.getInitiated() != null && upload.getInitiated().before(cutoff)) {
                    client(setting).abortMultipartUpload(new AbortMultipartUploadRequest(
                            setting.getBucket(), upload.getKey(), upload.getUploadId()));
                    aborted++;
                }
            }
            keyMarker = listing.getNextKeyMarker();
            uploadIdMarker = listing.getNextUploadIdMarker();
            if (!listing.isTruncated()) {
                break;
            }
        } while (keyMarker != null);
        return aborted;
    }

    @Override
    public String syncTempLifecycle(int retentionDays) {
        StorageSetting setting = getSetting();
        if (!isConfigured(setting)) {
            throw BusinessException.badRequest("请先在对象存储设置中完成配置");
        }
        int days = retentionDays < 1 ? setting.tempRetentionDays() : retentionDays;
        BucketLifecycleConfiguration configuration;
        try {
            configuration = client(setting).getBucketLifecycleConfiguration(setting.getBucket());
        } catch (AmazonS3Exception ex) {
            if (ex.getStatusCode() == 404 || "NoSuchLifecycleConfiguration".equals(ex.getErrorCode())) {
                configuration = new BucketLifecycleConfiguration();
            } else {
                throw BusinessException.badRequest("读取生命周期规则失败：" + describeError(ex));
            }
        }
        if (configuration == null) {
            configuration = new BucketLifecycleConfiguration();
        }
        List<BucketLifecycleConfiguration.Rule> rules = new ArrayList<>();
        if (configuration.getRules() != null) {
            for (BucketLifecycleConfiguration.Rule rule : configuration.getRules()) {
                if (!RULE_TEMP_EXPIRATION.equals(rule.getId())) {
                    rules.add(rule);
                }
            }
        }
        rules.add(new BucketLifecycleConfiguration.Rule()
                .withId(RULE_TEMP_EXPIRATION)
                .withPrefix("tmp/")
                .withStatus("Enabled")
                .withExpirationInDays(days));
        configuration.setRules(rules);
        try {
            client(setting).setBucketLifecycleConfiguration(
                    new SetBucketLifecycleConfigurationRequest(setting.getBucket(), configuration));
        } catch (Exception ex) {
            throw BusinessException.badRequest("保存生命周期规则失败：" + describeError(ex));
        }
        return "已同步 tmp/ 生命周期规则，临时文件将在 " + days + " 天后过期";
    }

    private String normalizePrefix(String prefix) {
        String value = trimToNull(prefix);
        if (value == null) {
            throw BusinessException.badRequest("清理前缀不能为空");
        }
        value = value.replaceAll("^/+", "");
        if (value.isEmpty() || value.contains("..") || value.contains("\\")) {
            throw BusinessException.badRequest("清理前缀格式不正确");
        }
        return value.endsWith("/") ? value : value + "/";
    }

    @Override
    public PresignedUrlResponse presignedUrl(String key, Integer expiresInSeconds) {
        StorageSetting setting = getSetting();
        if (!isConfigured(setting)) {
            throw BusinessException.badRequest("请先在对象存储设置中完成配置");
        }
        String objectKey = normalizeObjectKey(key);
        int expiry = expiresInSeconds == null
                ? setting.presignedUrlExpirySeconds() : expiresInSeconds;
        if (expiry < 60 || expiry > 604800) {
            throw BusinessException.badRequest("预签名有效期需在 60 到 604800 秒之间");
        }
        String url = presignedUrl(setting, objectKey, expiry);
        return new PresignedUrlResponse(objectKey, url, expiry,
                siteSettingService.formatDateTime(LocalDateTime.now().plusSeconds(expiry)));
    }

    // ------------------------------------------------------------------
    // Configuration access
    // ------------------------------------------------------------------

    private StorageSetting getSetting() {
        StorageSetting cached = cachedSetting;
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return cached;
        }
        synchronized (settingLock) {
            cached = cachedSetting;
            if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return cached;
            }
            StorageSetting loaded = storageSettingMapper.selectById(StorageSetting.SINGLETON_ID);
            if (loaded == null) {
                loaded = createDefaultSetting();
            }
            cachedSetting = loaded;
            cachedAt = System.currentTimeMillis();
            return loaded;
        }
    }

    private StorageSetting createDefaultSetting() {
        StorageSetting setting = new StorageSetting();
        setting.setId(StorageSetting.SINGLETON_ID);
        setting.setEnabled(false);
        setting.setRegion(DEFAULT_REGION);
        setting.setPathStyleAccess(false);
        setting.setMaxFileSizeMb(DEFAULT_MAX_FILE_SIZE_MB);
        setting.setPresignedUrlExpirySeconds(DEFAULT_PRESIGNED_EXPIRY_SECONDS);
        setting.setTempRetentionDays(DEFAULT_TEMP_RETENTION_DAYS);
        return setting;
    }

    private AdminStorageSettingResponse toResponse(StorageSetting setting) {
        return new AdminStorageSettingResponse(
                setting.enabled(),
                isConfigured(setting),
                setting.getEndpoint(),
                setting.getRegion() == null ? DEFAULT_REGION : setting.getRegion(),
                setting.getBucket(),
                setting.getAccessKey(),
                StringUtils.hasText(setting.getSecretKey()),
                setting.pathStyleAccess(),
                setting.getPublicBaseUrl(),
                setting.getObjectPrefix(),
                setting.maxFileSizeMb(),
                setting.presignedUrlExpirySeconds(),
                setting.tempRetentionDays(),
                setting.getUpdatedAt() == null ? null
                        : siteSettingService.formatDateTime(setting.getUpdatedAt())
        );
    }

    private boolean isConfigured(StorageSetting setting) {
        return StringUtils.hasText(setting.getBucket())
                && StringUtils.hasText(setting.getAccessKey())
                && StringUtils.hasText(setting.getSecretKey())
                && StringUtils.hasText(setting.getRegion());
    }

    private void validate(StorageSetting setting) {
        if (setting.enabled()) {
            if (!StringUtils.hasText(setting.getBucket())) {
                throw BusinessException.badRequest("启用对象存储时必须填写 Bucket");
            }
            if (!StringUtils.hasText(setting.getAccessKey())) {
                throw BusinessException.badRequest("启用对象存储时必须填写 AccessKey");
            }
            if (!StringUtils.hasText(setting.getSecretKey())) {
                throw BusinessException.badRequest("启用对象存储时必须填写 SecretKey");
            }
            if (!StringUtils.hasText(setting.getRegion())) {
                throw BusinessException.badRequest("启用对象存储时必须填写区域");
            }
        }
        if (setting.getBucket() != null) {
            normalizeBucket(setting.getBucket());
        }
        if (StringUtils.hasText(setting.getRegion()) && !setting.getRegion().matches("[A-Za-z0-9-]{1,64}")) {
            throw BusinessException.badRequest("区域格式不正确");
        }
        if (StringUtils.hasText(setting.getEndpoint())) {
            normalizeHttpUrl(setting.getEndpoint(), "S3 Endpoint");
        }
        if (StringUtils.hasText(setting.getPublicBaseUrl())) {
            normalizeHttpUrl(setting.getPublicBaseUrl(), "公共访问地址");
        }
        if (StringUtils.hasText(setting.getObjectPrefix())) {
            normalizePath(setting.getObjectPrefix(), "对象键前缀");
        }
        int maxFileSizeMb = setting.maxFileSizeMb();
        if (maxFileSizeMb < 1 || maxFileSizeMb > 512) {
            throw BusinessException.badRequest("单文件大小上限需在 1 到 512 MB 之间");
        }
        int expiry = setting.presignedUrlExpirySeconds();
        if (expiry < 60 || expiry > 604800) {
            throw BusinessException.badRequest("预签名有效期需在 60 到 604800 秒之间");
        }
        int tempRetentionDays = setting.tempRetentionDays();
        if (tempRetentionDays < 1 || tempRetentionDays > 365) {
            throw BusinessException.badRequest("临时文件保留天数需在 1 到 365 天之间");
        }
    }

    private String normalizeHttpUrl(String value, String label) {
        String url = trimToNull(value);
        if (url == null) {
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw BusinessException.badRequest(label + " 必须以 http:// 或 https:// 开头");
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw BusinessException.badRequest(label + " 格式不正确");
            }
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(label + " 格式不正确");
        }
        return stripTrailingSlash(url);
    }

    private String normalizeRegion(String value) {
        String region = trimToNull(value);
        if (region == null) {
            return DEFAULT_REGION;
        }
        if (!region.matches("[A-Za-z0-9-]{1,64}")) {
            throw BusinessException.badRequest("区域格式不正确");
        }
        return region;
    }

    private String normalizeBucket(String value) {
        String bucket = trimToNull(value);
        if (bucket == null) {
            return null;
        }
        if (!bucket.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,62}") || bucket.contains("..")) {
            throw BusinessException.badRequest("Bucket 名称格式不正确，需为 3-63 位字母、数字、点、下划线或短横线，且以字母或数字开头");
        }
        return bucket;
    }

    private String normalizePath(String value, String label) {
        String path = trimToNull(value);
        if (path == null) {
            return null;
        }
        path = path.replace('\\', '/').replaceAll("/{2,}", "/");
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return null;
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..") || !segment.matches("[A-Za-z0-9._-]+")) {
                throw BusinessException.badRequest(label + "只能包含字母、数字、点、下划线、短横线和斜杠");
            }
        }
        return path;
    }

    private String normalizeObjectKey(String value) {
        String key = trimToNull(value);
        if (key == null) {
            throw BusinessException.badRequest("对象 Key 不能为空");
        }
        if (key.startsWith("/") || key.contains("\\") || key.contains("..") || key.length() > 1024) {
            throw BusinessException.badRequest("对象 Key 格式不正确");
        }
        return key;
    }

    // ------------------------------------------------------------------
    // S3 client and object keys
    // ------------------------------------------------------------------

    private AmazonS3 client(StorageSetting setting) {
        String secret = cryptoService.decrypt(setting.getSecretKey());
        if (secret == null) {
            throw BusinessException.badRequest("SecretKey 无法解密，请在控制台重新填写");
        }
        String signature = signature(setting, secret);
        AmazonS3 current = cachedClient;
        if (current != null && signature.equals(cachedClientSignature)) {
            return current;
        }
        synchronized (clientLock) {
            if (cachedClient != null && signature.equals(cachedClientSignature)) {
                return cachedClient;
            }
            shutdownClient();
            AmazonS3 built = buildClient(setting, secret);
            cachedClient = built;
            cachedClientSignature = signature;
            return built;
        }
    }

    private AmazonS3 buildClient(StorageSetting setting, String secret) {
        ClientConfiguration configuration = new ClientConfiguration();
        configuration.setConnectionTimeout(5_000);
        configuration.setSocketTimeout(15_000);
        configuration.setMaxConnections(32);
        configuration.setMaxErrorRetry(1);
        configuration.setUserAgentPrefix("Cognia");

        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(setting.getAccessKey(), secret)))
                .withClientConfiguration(configuration)
                .withPathStyleAccessEnabled(setting.pathStyleAccess());

        String endpoint = trimToNull(setting.getEndpoint());
        if (endpoint != null) {
            builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, setting.getRegion()));
        } else {
            builder.withRegion(setting.getRegion());
        }
        return builder.build();
    }

    private String signature(StorageSetting setting, String secret) {
        return String.join("\u0001",
                nullToEmpty(setting.getEndpoint()),
                nullToEmpty(setting.getRegion()),
                nullToEmpty(setting.getBucket()),
                nullToEmpty(setting.getAccessKey()),
                secret,
                String.valueOf(setting.pathStyleAccess()));
    }

    private String testObjectKey(StorageSetting setting) {
        String prefix = trimToNull(setting.getObjectPrefix());
        String base = prefix == null ? "" : prefix + "/";
        return base + ".cognia-test/" + UUID.randomUUID() + ".txt";
    }

    private String buildKey(StorageSetting setting, String directory, String extension) {
        List<String> segments = new ArrayList<>();
        String prefix = normalizePath(setting.getObjectPrefix(), "对象键前缀");
        if (prefix != null) {
            segments.add(prefix);
        }
        String dir = normalizePath(directory, "上传目录");
        if (dir != null) {
            segments.add(dir);
        }
        ZoneId zoneId = siteSettingService.getZoneId();
        segments.add(LocalDate.now(zoneId).format(DATE_PATH));
        String suffix = extension.isEmpty() ? "" : "." + extension;
        segments.add(UUID.randomUUID().toString().replace("-", "") + suffix);
        return String.join("/", segments);
    }

    private String objectUrl(StorageSetting setting, String key) {
        String base = trimToNull(setting.getPublicBaseUrl());
        if (base != null) {
            return stripTrailingSlash(base) + "/" + key;
        }
        return presignedUrl(setting, key, setting.presignedUrlExpirySeconds());
    }

    private String presignedUrl(StorageSetting setting, String key, int expirySeconds) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expirySeconds * 1000L);
            return client(setting).generatePresignedUrl(setting.getBucket(), key, expiration).toString();
        } catch (Exception ex) {
            throw BusinessException.badRequest("生成预签名地址失败：" + describeError(ex));
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,16}") ? extension : "";
    }

    private String describeError(Exception ex) {
        String message;
        if (ex instanceof AmazonS3Exception s3Exception) {
            StringBuilder builder = new StringBuilder("S3 请求失败");
            if (s3Exception.getStatusCode() > 0) {
                builder.append("（HTTP ").append(s3Exception.getStatusCode()).append("）");
            }
            if (StringUtils.hasText(s3Exception.getErrorCode())) {
                builder.append("：").append(s3Exception.getErrorCode());
            }
            if (StringUtils.hasText(s3Exception.getErrorMessage())) {
                builder.append(" - ").append(s3Exception.getErrorMessage());
            } else if (StringUtils.hasText(s3Exception.getMessage())) {
                builder.append(" - ").append(s3Exception.getMessage());
            }
            message = builder.toString();
        } else if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            message = "无法访问对象存储：" + ex.getMessage();
        } else {
            message = "无法访问对象存储：" + ex.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) + "…" : message;
    }

    private void shutdownClient() {
        if (cachedClient != null) {
            try {
                cachedClient.shutdown();
            } catch (Exception ex) {
                log.debug("Failed to shutdown previous S3 client", ex);
            }
            cachedClient = null;
            cachedClientSignature = null;
        }
    }

    @PreDestroy
    public void destroy() {
        synchronized (clientLock) {
            shutdownClient();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}

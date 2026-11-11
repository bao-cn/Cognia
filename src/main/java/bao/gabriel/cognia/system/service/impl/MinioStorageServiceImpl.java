package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.storage.UploadProgressListener;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.dto.storage.StoredObjectResponse;
import bao.gabriel.cognia.system.entity.StorageSetting;
import bao.gabriel.cognia.system.mapper.StorageSettingMapper;
import bao.gabriel.cognia.system.service.MinioStorageService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO/S3 upload utility. Large files are sent with MinIO's multipart API
 * (automatic part management, no resumable checkpoints); progress is reported
 * through {@link UploadProgressListener}. All uploads share the DB-backed
 * size/type policy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageServiceImpl implements MinioStorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final long MULTIPART_THRESHOLD = 10L * 1024 * 1024;
    private static final long MULTIPART_PART_SIZE = 10L * 1024 * 1024;
    private static final String DEFAULT_REGION = "us-east-1";

    private final StorageSettingMapper storageSettingMapper;
    private final SecurityCryptoService cryptoService;
    private final SiteSettingService siteSettingService;

    @Override
    public StoredObjectResponse upload(MultipartFile file, String directory, UploadProgressListener listener) {
        StorageSetting setting = requireEnabledSetting();
        String extension = validateFile(file, setting);
        String key = buildKey(setting, directory, extension);
        return put(setting, file, key, contentType(file), listener);
    }

    @Override
    public StoredObjectResponse uploadTemp(MultipartFile file, UploadProgressListener listener) {
        StorageSetting setting = requireEnabledSetting();
        // Temporary multimodal inputs include images/audio that are not part of
        // the document whitelist, so tmp/ uploads only enforce the size limit
        // and a sanitized extension.
        String extension = validateTempFile(file, setting);
        String key = "tmp/" + UUID.randomUUID().toString().replace("-", "")
                + (extension.isEmpty() ? "" : "." + extension);
        return put(setting, file, key, contentType(file), listener);
    }

    @Override
    public StoredObjectResponse uploadAtKey(MultipartFile file, String key, UploadProgressListener listener) {
        StorageSetting setting = requireEnabledSetting();
        validateFile(file, setting);
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")) {
            throw BusinessException.badRequest("对象 Key 不合法");
        }
        return put(setting, file, key, contentType(file), listener);
    }

    @Override
    public StoredObjectResponse uploadChatImage(MultipartFile file, String key, UploadProgressListener listener) {
        StorageSetting setting = requireEnabledSetting();
        validateTempFile(file, setting);
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")) {
            throw BusinessException.badRequest("对象 Key 不合法");
        }
        return put(setting, file, key, contentType(file), listener);
    }

    @Override
    public void copyChatImage(String sourceKey, String targetKey) {
        StorageSetting setting = requireEnabledSetting();
        validateObjectKey(sourceKey);
        validateObjectKey(targetKey);
        try {
            buildClient(setting).copyObject(CopyObjectArgs.builder()
                    .bucket(setting.getBucket())
                    .object(targetKey)
                    .source(CopySource.builder().bucket(setting.getBucket()).object(sourceKey).build())
                    .build());
        } catch (Exception ex) {
            throw BusinessException.badRequest("图片归档失败：" + describe(ex));
        }
    }

    private void validateObjectKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..") || key.contains("\\")) {
            throw BusinessException.badRequest("对象 Key 不合法");
        }
    }

    @Override
    public StoredObjectResponse uploadBytesAtKey(byte[] bytes, String key, String contentType) {
        StorageSetting setting = requireEnabledSetting();
        if (bytes == null) {
            throw BusinessException.badRequest("内容不能为空");
        }
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")) {
            throw BusinessException.badRequest("对象 Key 不合法");
        }
        String type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        try (var input = new java.io.ByteArrayInputStream(bytes)) {
            buildClient(setting).putObject(PutObjectArgs.builder()
                    .bucket(setting.getBucket())
                    .object(key)
                    .stream(input, bytes.length, -1)
                    .contentType(type)
                    .build());
            return new StoredObjectResponse(key, publicUrl(setting, key), bytes.length, type,
                    siteSettingService.formatDateTime(LocalDateTime.now()));
        } catch (Exception ex) {
            throw BusinessException.badRequest("对象上传失败：" + describe(ex));
        }
    }

    @Override
    public byte[] readObject(String key) {
        StorageSetting setting = requireConfiguredSetting();
        try (var response = buildClient(setting).getObject(GetObjectArgs.builder()
                .bucket(setting.getBucket())
                .object(key)
                .build())) {
            return response.readAllBytes();
        } catch (Exception ex) {
            throw BusinessException.badRequest("读取对象失败：" + describe(ex));
        }
    }

    @Override
    public StoredObjectResponse uploadAvatar(String ownerHandle, Long ownerId, MultipartFile file) {
        StorageSetting setting = requireEnabledSetting();
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("头像文件不能为空");
        }
        String type = contentType(file);
        if (!"image/webp".equalsIgnoreCase(type)) {
            String name = file.getOriginalFilename();
            if (name != null && name.toLowerCase().endsWith(".webp")) {
                type = "image/webp";
            } else {
                throw BusinessException.badRequest("头像请先转换为 WebP 格式");
            }
        }
        long maxBytes = (long) setting.maxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw BusinessException.badRequest("头像大小不能超过 " + setting.maxFileSizeMb() + " MB");
        }
        String handle = ownerHandle == null || ownerHandle.isBlank() ? String.valueOf(ownerId) : ownerHandle.trim();
        if (!handle.matches("[A-Za-z0-9._-]{1,64}")) {
            throw BusinessException.badRequest("头像标识不合法");
        }
        String key = "avatar/" + handle + "/avatar.webp";
        StoredObjectResponse stored = put(setting, file, key, "image/webp", UploadProgressListener.NOOP);
        // Avatars are rendered by <img> tags without an Authorization header,
        // so persist a stable same-origin URL and let the public avatar
        // endpoint mint a fresh presigned URL on demand.
        return new StoredObjectResponse(stored.key(), "/api/v1/storage/avatar/" + ownerId,
                stored.size(), stored.contentType(), stored.uploadedAt());
    }

    @Override
    public String presignedUrl(String key, int expirySeconds) {
        StorageSetting setting = requireConfiguredSetting();
        try {
            MinioClient client = buildClient(setting);
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(setting.getBucket())
                    .object(key)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw BusinessException.badRequest("生成预签名地址失败：" + describe(ex));
        }
    }

    private StoredObjectResponse put(StorageSetting setting, MultipartFile file, String key,
                                     String contentType, UploadProgressListener listener) {
        long size = file.getSize();
        long partSize = size > MULTIPART_THRESHOLD ? MULTIPART_PART_SIZE : -1;
        try (InputStream raw = file.getInputStream();
             InputStream stream = listener == null
                     ? raw : new ProgressInputStream(raw, size, listener)) {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(setting.getBucket())
                    .object(key)
                    .stream(stream, size, partSize)
                    .contentType(contentType)
                    .build();
            buildClient(setting).putObject(args);
        } catch (Exception ex) {
            log.warn("MinIO upload failed for bucket {} key {}", setting.getBucket(), key, ex);
            throw BusinessException.badRequest("对象上传失败：" + describe(ex));
        }
        String url;
        try {
            String base = trimToNull(setting.getPublicBaseUrl());
            url = base == null
                    ? presignedUrl(setting, key, setting.presignedUrlExpirySeconds())
                    : stripTrailingSlash(base) + "/" + key;
        } catch (Exception ex) {
            log.warn("Object uploaded but URL generation failed for key {}", key, ex);
            url = null;
        }
        return new StoredObjectResponse(key, url, size, contentType,
                siteSettingService.formatDateTime(LocalDateTime.now()));
    }

    private String validateFile(MultipartFile file, StorageSetting setting) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }
        ensureWithinSize(file, setting);
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
        return extension;
    }

    private String validateTempFile(MultipartFile file, StorageSetting setting) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }
        ensureWithinSize(file, setting);
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        return extensionOf(originalName);
    }

    private void ensureWithinSize(MultipartFile file, StorageSetting setting) {
        long maxBytes = (long) setting.maxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw BusinessException.badRequest("文件大小不能超过 " + setting.maxFileSizeMb() + " MB");
        }
    }

    private String buildKey(StorageSetting setting, String directory, String extension) {
        List<String> segments = new ArrayList<>();
        if (StringUtils.hasText(setting.getObjectPrefix())) {
            segments.add(setting.getObjectPrefix().replaceAll("/+$", ""));
        }
        String dir = trimToNull(directory);
        if (dir != null) {
            segments.add(normalizePath(dir));
        }
        segments.add(LocalDate.now(zoneId()).format(DATE_PATH));
        segments.add(UUID.randomUUID().toString().replace("-", "")
                + (extension.isEmpty() ? "" : "." + extension));
        return String.join("/", segments);
    }

    private String normalizePath(String value) {
        String path = value.replaceAll("^/+", "").replaceAll("/+$", "");
        if (path.isEmpty()) {
            throw BusinessException.badRequest("上传目录不能为空");
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..") || !segment.matches("[A-Za-z0-9._-]+")) {
                throw BusinessException.badRequest("上传目录只能包含字母、数字、点、下划线、短横线和斜杠");
            }
        }
        return path;
    }

    private MinioClient buildClient(StorageSetting setting) {
        String secret = cryptoService.decrypt(setting.getSecretKey());
        if (secret == null) {
            throw BusinessException.badRequest("SecretKey 无法解密，请在控制台重新填写");
        }
        String endpoint = trimToNull(setting.getEndpoint());
        if (endpoint == null) {
            String region = StringUtils.hasText(setting.getRegion()) ? setting.getRegion() : DEFAULT_REGION;
            endpoint = "https://s3." + region + ".amazonaws.com";
        }
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(setting.getAccessKey(), secret)
                .region(StringUtils.hasText(setting.getRegion()) ? setting.getRegion() : DEFAULT_REGION)
                .build();
    }

    private String publicUrl(StorageSetting setting, String key) {
        try {
            String base = trimToNull(setting.getPublicBaseUrl());
            return base == null
                    ? presignedUrl(setting, key, setting.presignedUrlExpirySeconds())
                    : stripTrailingSlash(base) + "/" + key;
        } catch (Exception ex) {
            log.warn("Object uploaded but URL generation failed for key {}", key, ex);
            return null;
        }
    }

    private String presignedUrl(StorageSetting setting, String key, int expirySeconds) {
        try {
            return buildClient(setting).getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(setting.getBucket())
                    .object(key)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw BusinessException.badRequest("生成预签名地址失败：" + describe(ex));
        }
    }

    private StorageSetting requireEnabledSetting() {
        StorageSetting setting = requireConfiguredSetting();
        if (!setting.enabled()) {
            throw BusinessException.badRequest("对象存储尚未启用，请先在控制台开启并保存配置");
        }
        return setting;
    }

    private StorageSetting requireConfiguredSetting() {
        StorageSetting setting = storageSettingMapper.selectById(StorageSetting.SINGLETON_ID);
        if (setting == null || !StringUtils.hasText(setting.getBucket())
                || !StringUtils.hasText(setting.getAccessKey())
                || !StringUtils.hasText(setting.getSecretKey())) {
            throw BusinessException.badRequest("请先在对象存储设置中完成配置");
        }
        return setting;
    }

    private ZoneId zoneId() {
        try {
            return siteSettingService.getZoneId();
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    private static String contentType(MultipartFile file) {
        return StringUtils.hasText(file.getContentType())
                ? file.getContentType() : "application/octet-stream";
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,16}") ? extension : "";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String describe(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) + "…" : message;
    }

    /**
     * Counts bytes as MinIO reads the stream. Calls are throttled to roughly
     * one every 256 KiB plus a final 100% tick.
     */
    private static final class ProgressInputStream extends FilterInputStream {

        private final long total;
        private final UploadProgressListener listener;
        private long transferred;
        private long nextReport;

        private ProgressInputStream(InputStream in, long total, UploadProgressListener listener) {
            super(in);
            this.total = total;
            this.listener = listener;
            this.nextReport = 256 * 1024;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                advance(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                advance(read);
            }
            return read;
        }

        private void advance(int count) {
            transferred += count;
            if (transferred >= nextReport || transferred >= total) {
                nextReport = transferred + 256 * 1024;
                try {
                    listener.onProgress(Math.min(transferred, total), total);
                } catch (Exception ex) {
                    log.debug("Upload progress listener failed", ex);
                }
            }
        }
    }
}

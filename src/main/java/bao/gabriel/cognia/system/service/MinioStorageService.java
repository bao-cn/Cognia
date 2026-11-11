package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.storage.UploadProgressListener;
import bao.gabriel.cognia.system.dto.storage.StoredObjectResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unified upload entry point backed by the MinIO Java SDK. Every upload in the
 * application (knowledge files, avatars, temporary multimodal files) goes
 * through this service so the same size limits, key prefixes and multipart
 * behaviour apply.
 */
public interface MinioStorageService {

    StoredObjectResponse upload(MultipartFile file, String directory, UploadProgressListener listener);

    StoredObjectResponse uploadTemp(MultipartFile file, UploadProgressListener listener);

    StoredObjectResponse uploadAvatar(String ownerHandle, Long ownerId, MultipartFile file);

    StoredObjectResponse uploadAtKey(MultipartFile file, String key, UploadProgressListener listener);

    StoredObjectResponse uploadChatImage(MultipartFile file, String key, UploadProgressListener listener);

    void copyChatImage(String sourceKey, String targetKey);

    byte[] readObject(String key);

    StoredObjectResponse uploadBytesAtKey(byte[] bytes, String key, String contentType);

    String presignedUrl(String key, int expirySeconds);
}

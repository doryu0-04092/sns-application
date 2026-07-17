package com.snsapp.backend.storage;

import com.snsapp.backend.exception.InvalidImageTypeException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * ローカルディスク({@code app.storage.upload-dir}配下)に画像を保存する実装。
 * 将来AWS S3へ移行する際は、この実装クラスをS3実装に差し替えるだけでよい
 * (StorageServiceインターフェースの契約(URL文字列を返す)は変わらない)。
 */
@Service
public class LocalDiskStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskStorageService.class);

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");
    private static final Set<String> ALLOWED_TYPES = ALLOWED_CONTENT_TYPES.keySet();

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    @Value("${app.storage.base-url}")
    private String baseUrl;

    @Override
    public String store(MultipartFile file, String category) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidImageTypeException();
        }

        String extension = ALLOWED_CONTENT_TYPES.get(contentType);
        String filename = UUID.randomUUID() + extension;

        try {
            Path targetDir = Path.of(uploadDir, category);
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("画像の保存に失敗しました", ex);
        }

        return baseUrl + "/" + category + "/" + filename;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(baseUrl)) {
            return;
        }
        String relativePath = url.substring(baseUrl.length()).replaceFirst("^/", "");
        try {
            Files.deleteIfExists(Path.of(uploadDir, relativePath));
        } catch (IOException ex) {
            log.warn("Failed to delete stored file: {}", url, ex);
        }
    }
}

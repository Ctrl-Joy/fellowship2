package app.strata.storage;

import app.strata.common.config.AppProperties;
import app.strata.common.error.ApiException;
import app.strata.common.text.Tokens;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private final StorageService storage;
    private final MediaAssetRepository assets;
    private final long maxBytes;

    public record MediaDto(long id, String url, Integer width, Integer height, String contentType) {}

    public record StoredImage(String key, String url, ImageInspector.ImageInfo info, long size) {}

    public MediaService(StorageService storage, MediaAssetRepository assets, AppProperties props) {
        this.storage = storage;
        this.assets = assets;
        this.maxBytes = props.storage().maxImageBytes();
    }

    /** Validates and stores an image under {@code prefix}/ with a random, unguessable key. */
    public StoredImage storeImage(MultipartFile file, String prefix, long limitBytes) {
        if (file == null || file.isEmpty()) throw ApiException.field("file", "Choose a file to upload");
        long limit = Math.min(limitBytes, maxBytes);
        if (file.getSize() > limit) throw ApiException.field("file", "Images must be under " + (limit / 1024 / 1024) + " MB");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Upload failed");
        }
        ImageInspector.ImageInfo info = ImageInspector.inspect(bytes);
        String key = prefix + "/" + Tokens.random(18) + "." + info.extension();
        storage.put(key, bytes, info.contentType());
        return new StoredImage(key, storage.publicUrl(key), info, bytes.length);
    }

    @Transactional
    public MediaDto uploadPostImage(long ownerId, MultipartFile file) {
        StoredImage img = storeImage(file, "media", maxBytes);
        MediaAsset a = assets.save(new MediaAsset(ownerId, img.key(), img.info().contentType(), img.size(),
                img.info().width(), img.info().height()));
        return toDto(a);
    }

    public MediaDto toDto(MediaAsset a) {
        return new MediaDto(a.getId(), storage.publicUrl(a.getStorageKey()), a.getWidth(), a.getHeight(), a.getContentType());
    }
}

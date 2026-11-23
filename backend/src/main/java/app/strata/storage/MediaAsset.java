package app.strata.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "media_assets")
public class MediaAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long ownerId;
    @Column(nullable = false)
    private String storageKey;
    @Column(nullable = false)
    private String contentType;
    private long sizeBytes;
    private Integer width;
    private Integer height;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected MediaAsset() {}

    public MediaAsset(Long ownerId, String storageKey, String contentType, long sizeBytes, Integer width, Integer height) {
        this.ownerId = ownerId;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
}

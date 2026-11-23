package app.strata.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Binary storage abstraction. Metadata lives in PostgreSQL; bytes live here. The local filesystem driver
 * is used in development and single-node deployments; an S3-compatible driver plugs in behind this
 * interface (see docs/DECISIONS.md, D-007).
 */
public interface StorageService {
    void put(String key, byte[] bytes, String contentType);

    Optional<StoredObject> get(String key);

    void delete(String key);

    /** Public URL path for a stored key, relative to the API origin. */
    default String publicUrl(String key) {
        return "/api/v1/files/" + key;
    }

    boolean healthy();

    record StoredObject(InputStream stream, long size, String contentType) {}
}

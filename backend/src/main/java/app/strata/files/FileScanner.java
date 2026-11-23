package app.strata.files;

import java.nio.file.Path;

/**
 * Inspects an uploaded file after it has been streamed to storage. Implementations decide CLEAN, REJECTED or
 * UNSCANNED and, for accepted plaintext files, the MIME type the server will serve.
 *
 * <p>The default ({@link DefaultFileScanner}) does structural checks only: magic bytes vs extension, an
 * executable/script denylist and archive bomb heuristics. It is <b>not</b> an antivirus engine, and the UI never calls
 * it one. A malware engine (e.g. ClamAV over clamd) can replace it behind this interface.
 */
public interface FileScanner {

    enum Status { CLEAN, REJECTED, UNSCANNED }

    /**
     * @param name   the sanitised file name the user gave
     * @param head   the first bytes of the file (up to 8 KiB)
     * @param size   total size in bytes
     * @param local  the stored file on local disk when the storage driver has one, else null (deep checks skipped)
     * @param e2ee   true for end-to-end encrypted uploads: the server only sees ciphertext and can't inspect content
     */
    record Input(String name, byte[] head, long size, Path local, boolean e2ee) {}

    /** contentType is set when the file is accepted; reason explains a rejection (or what was checked). */
    record Result(Status status, String contentType, String reason) {
        public static Result rejected(String reason) {
            return new Result(Status.REJECTED, null, reason);
        }
    }

    Result scan(Input input);
}

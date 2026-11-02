package bao.gabriel.cognia.common.storage;

/**
 * Receives server-side upload progress. The callback is invoked on the thread
 * that reads the request body, so implementations must be cheap and
 * thread-safe.
 */
@FunctionalInterface
public interface UploadProgressListener {

    void onProgress(long bytesTransferred, long totalBytes);

    UploadProgressListener NOOP = (transferred, total) -> {
    };
}

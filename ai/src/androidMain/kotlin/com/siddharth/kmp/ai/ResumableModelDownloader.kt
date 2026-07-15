package com.siddharth.kmp.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

// ponytail: HttpURLConnection (JDK, zero extra deps) — a single-file resumable GET is exactly what it
// does well. No OkHttp/Ktor pulled into :ai just for this.
//
// Resumable-download technique (reimplemented from documented behaviour — no vendored worker):
//  - Download into a sibling `<file>.tmp`; atomically rename to the final file only on completion, so
//    a half-file is never mistaken for a ready model.
//  - Resume by sending `Range: bytes=<existing .tmp length>-`. A 206 means the server honoured it and
//    we append; a 200 means it ignored the range, so we restart the .tmp clean.
//  - `Accept-Encoding: identity` is REQUIRED — a gzip'd transfer re-encodes the bytes, which makes
//    the byte offsets meaningless and corrupts a resumed file.
//  - Optional `Authorization: Bearer <token>` for gated repos (e.g. some Hugging Face models).
//
// The caller (a WorkManager foreground worker, wifi-only) drives this and persists progress; scheduling
// and the FGS notification stay app-side — this is just the transfer engine.
class ResumableModelDownloader(
    private val bearerToken: String? = null,
    private val bufferSize: Int = 64 * 1024,
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 30_000,
    /** Injectable clock (elapsed nanos) so the progress/speed math is testable off a real wall-clock. */
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /**
     * Downloads [url] into [target], resuming from `<target>.tmp` if a partial exists, emitting a
     * [DownloadProgress] per buffer. On success the `.tmp` is renamed to [target]; on cancellation the
     * `.tmp` is left in place for the next resume.
     */
    fun download(
        url: String,
        target: File,
    ): Flow<DownloadProgress> =
        flow {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.parentFile?.mkdirs()
            val existing = if (tmp.exists()) tmp.length() else 0L

            val conn =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    // gzip would re-encode the stream and break byte-range resume — force identity.
                    setRequestProperty("Accept-Encoding", "identity")
                    if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
                    bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                }

            try {
                val code = conn.responseCode
                val resuming = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
                val startOffset = if (resuming) existing else 0L
                // Server ignored the Range (200 with a stale .tmp) → discard the partial and start clean.
                if (!resuming && existing > 0) tmp.delete()

                val remaining = conn.contentLengthLong
                val total = if (remaining >= 0) startOffset + remaining else -1L

                RandomAccessFile(tmp, "rw").use { raf ->
                    raf.seek(startOffset)
                    conn.inputStream.use { input ->
                        val buf = ByteArray(bufferSize)
                        var received = startOffset
                        val started = nanoTime()
                        while (true) {
                            currentCoroutineContext().ensureActive() // cooperative cancellation → .tmp survives
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            received += n
                            val elapsedMs = (nanoTime() - started) / 1_000_000L
                            emit(computeDownloadProgress(received, total, elapsedMs, startOffset))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }.flowOn(Dispatchers.IO)
}

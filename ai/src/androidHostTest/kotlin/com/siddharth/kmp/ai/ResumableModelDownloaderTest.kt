package com.siddharth.kmp.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.util.zip.GZIPOutputStream

/**
 * Real HTTP, no fakes: `com.sun.net.httpserver.HttpServer` (JDK, already on the host-test classpath —
 * no new test dependency) serves each fixture on localhost, so [ResumableModelDownloader] runs its
 * actual `HttpURLConnection` against actual Range/Content-Encoding response handling instead of a
 * mocked stream. Only [ResumableModelDownloader] is exercised here — [MediaPipeModelManager]'s
 * license-ack/wifi gates sit in front of it and aren't this class's concern.
 */
class ResumableModelDownloaderTest {
    private lateinit var server: HttpServer
    private lateinit var dir: File

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.start()
        dir = File.createTempFile("downloader-test", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun url(path: String): String = "http://localhost:${server.address.port}$path"

    @Test
    fun `full download writes the complete file`() =
        runBlocking {
            val body = ByteArray(50_000) { it.toByte() }
            server.createContext("/model") { exchange ->
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            val target = File(dir, "model.task")

            ResumableModelDownloader().download(url("/model"), target).toList()

            assertTrue(target.exists())
            assertTrue(body.contentEquals(target.readBytes()))
        }

    @Test
    fun `resumes a partial download via the Range header`() =
        runBlocking {
            val body = ByteArray(50_000) { it.toByte() }
            val resumedFrom = 20_000
            server.createContext("/model") { exchange ->
                val range = exchange.requestHeaders.getFirst("Range")
                assertEquals("bytes=$resumedFrom-", range) // proves the .tmp's existing length drove the request
                val slice = body.copyOfRange(resumedFrom, body.size)
                exchange.responseHeaders.add("Content-Range", "bytes $resumedFrom-${body.size - 1}/${body.size}")
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_PARTIAL, slice.size.toLong())
                exchange.responseBody.use { it.write(slice) }
            }
            val target = File(dir, "model.task")
            File(dir, "model.task.tmp").writeBytes(body.copyOfRange(0, resumedFrom))

            ResumableModelDownloader().download(url("/model"), target).toList()

            assertTrue(target.exists())
            assertTrue(body.contentEquals(target.readBytes()))
        }

    @Test
    fun `restarts clean when the server ignores the Range header`() =
        runBlocking {
            val body = ByteArray(1_000) { it.toByte() }
            server.createContext("/no-range-support") { exchange ->
                // Always answers 200 with the full body — simulates a server/CDN that doesn't
                // support byte-range requests at all, ignoring any Range header we sent.
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            val target = File(dir, "model.task")
            val staleGarbage = ByteArray(500) { 9 } // from a prior attempt against a different server
            File(dir, "model.task.tmp").writeBytes(staleGarbage)

            ResumableModelDownloader().download(url("/no-range-support"), target).toList()

            assertTrue(target.exists())
            assertTrue(body.contentEquals(target.readBytes())) // NOT the 500 garbage bytes + a short body
        }

    @Test
    fun `rejects a response gzip-encoded despite Accept-Encoding identity`() =
        runBlocking {
            val body = ByteArray(1_000) { it.toByte() }
            val gzipped = ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(body) } }.toByteArray()
            server.createContext("/gzip") { exchange ->
                exchange.responseHeaders.add("Content-Encoding", "gzip")
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, gzipped.size.toLong())
                exchange.responseBody.use { it.write(gzipped) }
            }
            val target = File(dir, "model.task")

            assertThrows(IllegalStateException::class.java) {
                runBlocking { ResumableModelDownloader().download(url("/gzip"), target).toList() }
            }
            assertFalse(target.exists())
        }
}

package eu.hxreborn.amznkiller.http

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

object HttpClient {
    private const val MAX_BYTES = 512_000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val BUFFER_SIZE = 8192
    private const val MAX_REDIRECTS = 5

    fun isHttps(url: String): Boolean =
        runCatching {
            val uri = URI(url.trim())
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }.getOrDefault(false)

    fun fetch(url: String): String {
        var target = url.trim()
        var redirects = 0

        while (redirects < MAX_REDIRECTS) {
            if (!isHttps(target)) throw IOException("Only https URLs are allowed")
            val conn =
                (URI(target).toURL().openConnection() as? HttpURLConnection)
                    ?: error("Non-HTTP URL: $target")
            try {
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.instanceFollowRedirects = false
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "amznkiller/1.0")

                val code = conn.responseCode
                if (code in 300..399) {
                    val location =
                        conn.getHeaderField("Location")
                            ?: throw IOException("HTTP $code redirect without Location header")
                    target = URI(target).resolve(location.trim()).toString()
                    redirects++
                    continue
                }

                validateResponse(conn)
                return readLimited(conn.inputStream)
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects ($MAX_REDIRECTS)")
    }

    private fun validateResponse(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")
    }

    private fun readLimited(stream: InputStream): String =
        stream.use {
            val baos = ByteArrayOutputStream()
            val chunk = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (true) {
                val bytesRead = it.read(chunk)
                if (bytesRead < 0) break
                total += bytesRead
                if (total > MAX_BYTES) throw IOException("Response exceeded $MAX_BYTES bytes")
                baos.write(chunk, 0, bytesRead)
            }
            baos.toString(Charsets.UTF_8.name())
        }
}

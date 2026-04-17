package org.pihole.android.data.lists

import java.net.HttpURLConnection
import java.net.URL

class AdlistDownloader {

    data class Result(
        val body: String,
        val etag: String?,
        val lastModified: String?,
        val notModified: Boolean,
    )

    fun download(url: String, etag: String?, lastModified: String?): Result {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        etag?.let { conn.setRequestProperty("If-None-Match", it) }
        lastModified?.let { conn.setRequestProperty("If-Modified-Since", it) }
        conn.connect()
        val code = conn.responseCode
        if (code == 304) {
            return Result(
                body = "",
                etag = etag,
                lastModified = lastModified,
                notModified = true,
            )
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        return Result(
            body = text,
            etag = conn.getHeaderField("ETag"),
            lastModified = conn.getHeaderField("Last-Modified"),
            notModified = false,
        )
    }
}

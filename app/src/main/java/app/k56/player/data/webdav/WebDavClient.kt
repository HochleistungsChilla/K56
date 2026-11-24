package app.k56.player.data.webdav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import app.k56.player.core.net.DiagnosticsInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DavEntry(
    val path: String,
    val url: String,
    val name: String,
    val isDirectory: Boolean,
    val contentType: String?,
    val size: Long?,
)

/**
 * Minimal WebDAV client: only what a music library needs, namely PROPFIND
 * listings and direct GETs. Basic auth is sent preemptively.
 */
class WebDavClient(
    private val baseUrl: String,
    private val basicAuth: String?,
) {

    private val http = OkHttpClient.Builder()
            .addInterceptor(DiagnosticsInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun list(relativePath: String): List<DavEntry> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(absoluteUrl(relativePath))
            .method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml".toMediaType()))
            .header("Depth", "1")
            .header("Accept", "application/xml, text/xml")
            .apply { if (!basicAuth.isNullOrBlank()) header("Authorization", "Basic $basicAuth") }
            .build()

        http.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw IOException("WebDAV-Zugriff verweigert (HTTP ${response.code}).")
            }
            if (!response.isSuccessful && response.code != 207) {
                throw IOException("WebDAV antwortet mit HTTP ${response.code}.")
            }
            val stream = response.body?.byteStream() ?: throw IOException("WebDAV lieferte keine Antwort.")
            parseMultiStatus(stream, relativePath)
        }
    }

    fun absoluteUrl(relativePath: String): String {
        val base = baseUrl.trimEnd('/')
        if (relativePath.isBlank()) return "$base/"
        val encoded = relativePath.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
        return "$base/$encoded"
    }

    private fun parseMultiStatus(stream: java.io.InputStream, requestedPath: String): List<DavEntry> {
        val entries = mutableListOf<DavEntry>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(stream, null)

        var inResponse = false
        var href: String? = null
        var isCollection = false
        var contentType: String? = null
        var size: Long? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "response" -> {
                        inResponse = true
                        href = null
                        isCollection = false
                        contentType = null
                        size = null
                    }

                    "href" -> if (inResponse) href = parser.nextText()
                    "collection" -> if (inResponse) isCollection = true
                    "getcontenttype" -> if (inResponse) contentType = parser.nextText()
                    "getcontentlength" -> if (inResponse) {
                        size = parser.nextText().toLongOrNull()
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == "response" && inResponse) {
                    val rawHref = href
                    if (rawHref != null) {
                        val path = relativePath(rawHref)
                        if (path != requestedPath.trim('/')) {
                            val name = path.substringAfterLast('/').ifBlank { path }
                            entries += DavEntry(
                                path = path,
                                url = absoluteUrl(path),
                                name = name,
                                isDirectory = isCollection,
                                contentType = contentType,
                                size = size,
                            )
                        }
                    }
                    inResponse = false
                }
            }
            event = parser.next()
        }
        return entries
    }

    private fun relativePath(href: String): String {
        val path = runCatching { URI(href).path }.getOrNull() ?: href
        val basePath = runCatching { URI(baseUrl).path }.getOrNull()?.trimEnd('/') ?: ""
        val relative = if (basePath.isNotEmpty() && path.startsWith(basePath)) {
            path.removePrefix(basePath)
        } else {
            path
        }
        return relative.trim('/')
    }

    private companion object {
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getcontenttype/>
    <d:getcontentlength/>
  </d:prop>
</d:propfind>"""
    }
}

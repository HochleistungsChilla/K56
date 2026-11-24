package app.k56.player.core.net

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Records one entry per request in [DiagnosticsStore].
 *
 * What is stored is deliberately narrow: method, path without query, status,
 * duration and a coarse failure class. Headers and bodies are never read, so no
 * token, cookie or credential can end up in the buffer.
 */
class DiagnosticsInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        return try {
            val response = chain.proceed(request)
            DiagnosticsStore.record(
                DiagnosticsEntry(
                    atEpochMs = System.currentTimeMillis(),
                    method = request.method,
                    path = request.url.encodedPath.take(160),
                    status = response.code,
                    durationMs = (System.nanoTime() - started) / 1_000_000,
                    errorKind = if (response.isSuccessful || response.isRedirect) {
                        null
                    } else {
                        DiagnosticsErrorKind.HTTP
                    },
                ),
            )
            response
        } catch (e: IOException) {
            DiagnosticsStore.record(
                DiagnosticsEntry(
                    atEpochMs = System.currentTimeMillis(),
                    method = request.method,
                    path = request.url.encodedPath.take(160),
                    status = null,
                    durationMs = (System.nanoTime() - started) / 1_000_000,
                    errorKind = classify(e),
                ),
            )
            throw e
        }
    }

    private fun classify(e: IOException): DiagnosticsErrorKind = when (e) {
        is SocketTimeoutException -> DiagnosticsErrorKind.TIMEOUT
        is UnknownHostException -> DiagnosticsErrorKind.DNS
        is SSLException -> DiagnosticsErrorKind.TLS
        else -> DiagnosticsErrorKind.IO
    }
}

package app.k56.player.core.net

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Wraps the player's data source so media traffic reaches [DiagnosticsStore].
 *
 * ExoPlayer does not use the Retrofit client, so without this wrapper the one
 * request that decides whether anything is audible - the audio stream - is the
 * only one missing from the debug screen. Only method, path, status/code and
 * duration are recorded; the query string (which carries `api_key`) is reduced
 * to its parameter names, and only when the request failed.
 */
@UnstableApi
class DiagnosticsDataSourceFactory(
    private val upstream: DataSource.Factory,
) : DataSource.Factory {

    override fun createDataSource(): DataSource = DiagnosticsDataSource(upstream.createDataSource())
}

@UnstableApi
private class DiagnosticsDataSource(private val delegate: DataSource) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) =
        delegate.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val started = System.nanoTime()
        return try {
            delegate.open(dataSpec).also {
                record(dataSpec, status = null, kind = null, started = started)
            }
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            record(dataSpec, status = e.responseCode, kind = DiagnosticsErrorKind.HTTP, started = started)
            throw e
        } catch (e: IOException) {
            record(dataSpec, status = null, kind = classify(e), started = started)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    override fun getUri(): Uri? = delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() = delegate.close()

    private fun record(dataSpec: DataSpec, status: Int?, kind: DiagnosticsErrorKind?, started: Long) {
        DiagnosticsStore.record(
            DiagnosticsEntry(
                atEpochMs = System.currentTimeMillis(),
                method = "GET",
                path = dataSpec.uri.encodedPath?.take(160) ?: dataSpec.uri.toString().take(160),
                status = status,
                durationMs = (System.nanoTime() - started) / 1_000_000,
                errorKind = kind,
                source = DiagnosticsSource.MEDIA,
                queryNames = if (kind == null) emptyList() else dataSpec.uri.queryParameterNames?.sorted().orEmpty(),
            ),
        )
    }

    private fun classify(e: IOException): DiagnosticsErrorKind = when (e) {
        is SocketTimeoutException -> DiagnosticsErrorKind.TIMEOUT
        is UnknownHostException -> DiagnosticsErrorKind.DNS
        is SSLException -> DiagnosticsErrorKind.TLS
        else -> DiagnosticsErrorKind.IO
    }
}

package app.k56.player.data.acquisition.clients

/**
 * One entry in a download client's queue. Deliberately provider-neutral: the
 * adapters map their own vocabulary onto these fields.
 */
data class DownloadTask(
    val id: String,
    val name: String,
    /** 0f..1f */
    val progress: Float,
    /** Provider state, unmodified (e.g. "downloading", "stalledUP"). */
    val state: String,
    val etaSeconds: Long? = null,
    val sizeBytes: Long? = null,
    /** Bytes already downloaded, when the client reports it separately. */
    val transferredBytes: Long? = null,
    val speedBytesPerSecond: Long? = null,
    val error: String? = null,
) {
    val finished: Boolean
        get() = progress >= 0.999f
}

class DownloadClientException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * What K56 needs from a download client: hand over a link, look at the queue,
 * pause/resume. Deliberately no remove-with-files — the app never deletes.
 */
interface DownloadClient {

    /** Human-readable client name for the UI. */
    val label: String

    /** Returns the client's version string; throws when the credentials are wrong. */
    suspend fun test(): String

    /** Adds a magnet link, an http(s) URL or an NZB URL. */
    suspend fun add(url: String, category: String? = null): Boolean

    suspend fun tasks(): List<DownloadTask>

    suspend fun pause(id: String)

    suspend fun resume(id: String)
}

package app.k56.player.data.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.k56.player.core.model.Album
import app.k56.player.core.model.Artist
import app.k56.player.core.model.LibraryCounts
import app.k56.player.core.model.Track

/**
 * On-device copy of the server library, so browsing and search keep working
 * without a network. [LibrarySync] writes it, the library screens read it.
 *
 * Plain SQLite from the platform: no Room, no KSP, no extra dependency.
 */
class LibraryDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "library.db", null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tracks (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                album_id TEXT,
                album_name TEXT,
                artist_name TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                index_number INTEGER,
                disc_number INTEGER,
                container TEXT,
                bitrate INTEGER,
                image_url TEXT,
                is_favorite INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE albums (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                artist TEXT,
                artist_id TEXT,
                year INTEGER,
                image_url TEXT,
                track_count INTEGER,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                overview TEXT,
                genres TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE artists (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                image_url TEXT,
                overview TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE genres (name TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE sync_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_tracks_title ON tracks(title)")
        db.execSQL("CREATE INDEX idx_tracks_album ON tracks(album_id)")
        db.execSQL("CREATE INDEX idx_albums_name ON albums(name)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf("tracks", "albums", "artists", "genres", "sync_state").forEach {
            db.execSQL("DROP TABLE IF EXISTS $it")
        }
        onCreate(db)
    }

    /** True when nothing has been imported yet. */
    fun isEmpty(): Boolean = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM tracks",
        null,
    ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 0 }

    /** Replaces the whole index in one transaction, so readers never see a half copy. */
    fun replaceAll(
        tracks: List<Track>,
        albums: List<Album>,
        artists: List<Artist>,
        genres: List<String>,
        serverCounts: LibraryCounts,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("tracks", null, null)
            db.delete("albums", null, null)
            db.delete("artists", null, null)
            db.delete("genres", null, null)

            tracks.forEach { track ->
                db.insert("tracks", null, ContentValues().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("album_id", track.albumId)
                    put("album_name", track.albumName)
                    put("artist_name", track.artistName)
                    put("duration_ms", track.durationMs)
                    put("index_number", track.indexNumber)
                    put("disc_number", track.discNumber)
                    put("container", track.container)
                    put("bitrate", track.bitrate)
                    put("image_url", track.imageUrl)
                    put("is_favorite", if (track.isFavorite) 1 else 0)
                })
            }
            albums.forEach { album ->
                db.insert("albums", null, ContentValues().apply {
                    put("id", album.id)
                    put("name", album.name)
                    put("artist", album.artist)
                    put("artist_id", album.artistId)
                    put("year", album.year)
                    put("image_url", album.imageUrl)
                    put("track_count", album.trackCount)
                    put("is_favorite", if (album.isFavorite) 1 else 0)
                    put("overview", album.overview)
                    put("genres", album.genres.joinToString("\u0001"))
                })
            }
            artists.forEach { artist ->
                db.insert("artists", null, ContentValues().apply {
                    put("id", artist.id)
                    put("name", artist.name)
                    put("image_url", artist.imageUrl)
                    put("overview", artist.overview)
                })
            }
            genres.forEach { name ->
                db.insertWithOnConflict("genres", null, ContentValues().apply {
                    put("name", name)
                }, SQLiteDatabase.CONFLICT_IGNORE)
            }

            putState(db, KEY_LAST_SYNC, System.currentTimeMillis().toString())
            putState(db, KEY_SERVER_TRACKS, serverCounts.songs.toString())
            putState(db, KEY_SERVER_ALBUMS, serverCounts.albums.toString())
            putState(db, KEY_SERVER_ARTISTS, serverCounts.artists.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Upserts changed items without touching the rest of the index. */
    fun applyDelta(tracks: List<Track>, albums: List<Album>) {
        if (tracks.isEmpty() && albums.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            tracks.forEach { track ->
                db.insertWithOnConflict("tracks", null, ContentValues().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("album_id", track.albumId)
                    put("album_name", track.albumName)
                    put("artist_name", track.artistName)
                    put("duration_ms", track.durationMs)
                    put("index_number", track.indexNumber)
                    put("disc_number", track.discNumber)
                    put("container", track.container)
                    put("bitrate", track.bitrate)
                    put("image_url", track.imageUrl)
                    put("is_favorite", if (track.isFavorite) 1 else 0)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            albums.forEach { album ->
                db.insertWithOnConflict("albums", null, ContentValues().apply {
                    put("id", album.id)
                    put("name", album.name)
                    put("artist", album.artist)
                    put("artist_id", album.artistId)
                    put("year", album.year)
                    put("image_url", album.imageUrl)
                    put("track_count", album.trackCount)
                    put("is_favorite", if (album.isFavorite) 1 else 0)
                    put("overview", album.overview)
                    put("genres", album.genres.joinToString("\u0001"))
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Streaming full sync: rows land in shadow tables first, so the visible
     * index stays consistent and memory use does not grow with library size.
     */
    fun beginStaging() {
        val db = writableDatabase
        STAGED_TABLES.forEach { table ->
            db.execSQL("CREATE TABLE IF NOT EXISTS ${table}_staging AS SELECT * FROM $table WHERE 0")
            db.delete("${table}_staging", null, null)
        }
    }

    fun appendTracks(items: List<Track>) {
        if (items.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            items.forEach { track ->
                db.insert("tracks_staging", null, ContentValues().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("album_id", track.albumId)
                    put("album_name", track.albumName)
                    put("artist_name", track.artistName)
                    put("duration_ms", track.durationMs)
                    put("index_number", track.indexNumber)
                    put("disc_number", track.discNumber)
                    put("container", track.container)
                    put("bitrate", track.bitrate)
                    put("image_url", track.imageUrl)
                    put("is_favorite", if (track.isFavorite) 1 else 0)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun appendAlbums(items: List<Album>) {
        if (items.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            items.forEach { album ->
                db.insert("albums_staging", null, ContentValues().apply {
                    put("id", album.id)
                    put("name", album.name)
                    put("artist", album.artist)
                    put("artist_id", album.artistId)
                    put("year", album.year)
                    put("image_url", album.imageUrl)
                    put("track_count", album.trackCount)
                    put("is_favorite", if (album.isFavorite) 1 else 0)
                    put("overview", album.overview)
                    put("genres", album.genres.joinToString("\u0001"))
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun appendArtists(items: List<Artist>) {
        if (items.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            items.forEach { artist ->
                db.insert("artists_staging", null, ContentValues().apply {
                    put("id", artist.id)
                    put("name", artist.name)
                    put("image_url", artist.imageUrl)
                    put("overview", artist.overview)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun appendGenres(names: List<String>) {
        if (names.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            names.forEach { name ->
                db.insertWithOnConflict(
                    "genres_staging",
                    null,
                    ContentValues().apply { put("name", name) },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Empties the visible index; called once per full sync before the first publish. */
    fun clearLive() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            STAGED_TABLES.forEach { db.delete(it, null, null) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Moves staged rows into the visible tables and clears the staging tables.
     * Called repeatedly during a sync so the library grows while it runs.
     */
    fun flushStaging() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            STAGED_TABLES.forEach { table ->
                db.execSQL("INSERT OR REPLACE INTO $table SELECT * FROM ${table}_staging")
                db.execSQL("DELETE FROM ${table}_staging")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Records the outcome of a finished sync. */
    fun finishSync(serverCounts: LibraryCounts) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            putState(db, KEY_LAST_SYNC, System.currentTimeMillis().toString())
            putState(db, KEY_SERVER_TRACKS, serverCounts.songs.toString())
            putState(db, KEY_SERVER_ALBUMS, serverCounts.albums.toString())
            putState(db, KEY_SERVER_ARTISTS, serverCounts.artists.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Publishes the staged rows in one short transaction. */
    fun commitStaging(serverCounts: LibraryCounts) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            STAGED_TABLES.forEach { table ->
                db.execSQL("DELETE FROM $table")
                db.execSQL("INSERT INTO $table SELECT * FROM ${table}_staging")
                db.execSQL("DELETE FROM ${table}_staging")
            }
            putState(db, KEY_LAST_SYNC, System.currentTimeMillis().toString())
            putState(db, KEY_SERVER_TRACKS, serverCounts.songs.toString())
            putState(db, KEY_SERVER_ALBUMS, serverCounts.albums.toString())
            putState(db, KEY_SERVER_ARTISTS, serverCounts.artists.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun abortStaging() {
        val db = writableDatabase
        STAGED_TABLES.forEach { db.delete("${it}_staging", null, null) }
    }

    fun counts(): LibraryCounts = LibraryCounts(
        albums = count("albums"),
        artists = count("artists"),
        songs = count("tracks"),
    )

    fun serverCounts(): LibraryCounts = LibraryCounts(
        albums = state(KEY_SERVER_ALBUMS)?.toIntOrNull() ?: 0,
        artists = state(KEY_SERVER_ARTISTS)?.toIntOrNull() ?: 0,
        songs = state(KEY_SERVER_TRACKS)?.toIntOrNull() ?: 0,
    )

    fun lastSync(): Long = state(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L

    fun allTracks(): List<Track> = readableDatabase
        .rawQuery("SELECT * FROM tracks ORDER BY title COLLATE NOCASE", null)
        .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toTrack()) } }

    fun allAlbums(): List<Album> = readableDatabase
        .rawQuery("SELECT * FROM albums ORDER BY name COLLATE NOCASE", null)
        .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toAlbum()) } }

    fun allArtists(): List<Artist> = readableDatabase
        .rawQuery("SELECT * FROM artists ORDER BY name COLLATE NOCASE", null)
        .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toArtist()) } }

    fun allGenres(): List<String> = readableDatabase
        .rawQuery("SELECT name FROM genres ORDER BY name COLLATE NOCASE", null)
        .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    /** Local title search, case-insensitive, at most [limit] hits. */
    fun searchTracks(query: String, limit: Int = 80): List<Track> {
        val term = "%${query.trim().replace("%", "\\%").replace("_", "\\_")}%"
        return readableDatabase.rawQuery(
            "SELECT * FROM tracks WHERE title LIKE ? ESCAPE '\\' " +
                "OR album_name LIKE ? ESCAPE '\\' OR artist_name LIKE ? ESCAPE '\\' " +
                "ORDER BY title COLLATE NOCASE LIMIT $limit",
            arrayOf(term, term, term),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toTrack()) } }
    }

    /** One album by server id, or null when it was never indexed. */
    fun albumById(id: String): Album? = readableDatabase
        .rawQuery("SELECT * FROM albums WHERE id = ?", arrayOf(id))
        .use { cursor -> if (cursor.moveToFirst()) cursor.toAlbum() else null }

    /** Tracks of one album in disc/track order. */
    fun tracksByAlbum(albumId: String): List<Track> = readableDatabase
        .rawQuery(
            "SELECT * FROM tracks WHERE album_id = ? ORDER BY disc_number, index_number",
            arrayOf(albumId),
        )
        .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toTrack()) } }

    /** One artist by server id, or null when it was never indexed. */
    fun artistById(id: String): Artist? = readableDatabase
        .rawQuery("SELECT * FROM artists WHERE id = ?", arrayOf(id))
        .use { cursor -> if (cursor.moveToFirst()) cursor.toArtist() else null }

    /** Albums headlined by this artist, oldest first. */
    fun albumsByArtist(artistId: String): List<Album> = readableDatabase
        .rawQuery(
            "SELECT * FROM albums WHERE artist_id = ? ORDER BY year, name COLLATE NOCASE",
            arrayOf(artistId),
        )
        .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toAlbum()) } }

    /** Tracks credited to this artist (by name), title-sorted. */
    fun tracksByArtist(name: String): List<Track> = readableDatabase
        .rawQuery(
            "SELECT * FROM tracks WHERE artist_name = ? ORDER BY title COLLATE NOCASE",
            arrayOf(name),
        )
        .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toTrack()) } }

    /** Albums tagged with one genre (substring match on the joined genre column). */
    fun albumsByGenre(genre: String): List<Album> {
        val term = "%${genre.replace("%", "\\%").replace("_", "\\_")}%"
        return readableDatabase.rawQuery(
            "SELECT * FROM albums WHERE genres LIKE ? ESCAPE '\\' ORDER BY name COLLATE NOCASE",
            arrayOf(term),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toAlbum()) } }
    }

    /** Tracks whose album is tagged with one genre. */
    fun tracksByGenre(genre: String): List<Track> {
        val term = "%${genre.replace("%", "\\%").replace("_", "\\_")}%"
        return readableDatabase.rawQuery(
            "SELECT t.* FROM tracks t JOIN albums a ON t.album_id = a.id " +
                "WHERE a.genres LIKE ? ESCAPE '\\' ORDER BY t.title COLLATE NOCASE",
            arrayOf(term),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toTrack()) } }
    }

    /** Local album-name/artist search, case-insensitive. */
    fun searchAlbums(query: String, limit: Int = 40): List<Album> {
        val term = "%${query.trim().replace("%", "\\%").replace("_", "\\_")}%"
        return readableDatabase.rawQuery(
            "SELECT * FROM albums WHERE name LIKE ? ESCAPE '\\' OR artist LIKE ? ESCAPE '\\' " +
                "ORDER BY name COLLATE NOCASE LIMIT $limit",
            arrayOf(term, term),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toAlbum()) } }
    }

    /** Local artist-name search, case-insensitive. */
    fun searchArtists(query: String, limit: Int = 40): List<Artist> {
        val term = "%${query.trim().replace("%", "\\%").replace("_", "\\_")}%"
        return readableDatabase.rawQuery(
            "SELECT * FROM artists WHERE name LIKE ? ESCAPE '\\' ORDER BY name COLLATE NOCASE LIMIT $limit",
            arrayOf(term),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toArtist()) } }
    }

    private fun count(table: String): Int = readableDatabase
        .rawQuery("SELECT COUNT(*) FROM $table", null)
        .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun state(key: String): String? = readableDatabase
        .rawQuery("SELECT value FROM sync_state WHERE key = ?", arrayOf(key))
        .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun putState(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict(
            "sync_state",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun android.database.Cursor.toTrack() = Track(
        id = getString(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        albumId = getStringOrNull("album_id"),
        albumName = getStringOrNull("album_name"),
        artistName = getStringOrNull("artist_name"),
        durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
        indexNumber = getIntOrNull("index_number"),
        discNumber = getIntOrNull("disc_number"),
        container = getStringOrNull("container"),
        bitrate = getIntOrNull("bitrate"),
        imageUrl = getStringOrNull("image_url"),
        isFavorite = getInt(getColumnIndexOrThrow("is_favorite")) == 1,
    )

    private fun android.database.Cursor.toAlbum() = Album(
        id = getString(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        artist = getStringOrNull("artist"),
        artistId = getStringOrNull("artist_id"),
        year = getIntOrNull("year"),
        imageUrl = getStringOrNull("image_url"),
        trackCount = getIntOrNull("track_count"),
        isFavorite = getInt(getColumnIndexOrThrow("is_favorite")) == 1,
        overview = getStringOrNull("overview"),
        genres = getStringOrNull("genres")?.takeIf { it.isNotEmpty() }?.split("\u0001").orEmpty(),
    )

    private fun android.database.Cursor.toArtist() = Artist(
        id = getString(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        imageUrl = getStringOrNull("image_url"),
        overview = getStringOrNull("overview"),
    )

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.getIntOrNull(column: String): Int? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getInt(index)
    }

    companion object {
        private const val DB_VERSION = 1
        private val STAGED_TABLES = listOf("tracks", "albums", "artists", "genres")
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_SERVER_TRACKS = "server_tracks"
        private const val KEY_SERVER_ALBUMS = "server_albums"
        private const val KEY_SERVER_ARTISTS = "server_artists"

        @Volatile
        private var instance: LibraryDb? = null

        fun get(context: Context): LibraryDb =
            instance ?: synchronized(this) {
                instance ?: LibraryDb(context).also { instance = it }
            }
    }
}

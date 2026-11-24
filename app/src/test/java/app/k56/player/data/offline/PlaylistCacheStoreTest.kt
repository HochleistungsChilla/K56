package app.k56.player.data.offline

import app.k56.player.core.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlaylistCacheStoreTest {

    private lateinit var dir: File
    private lateinit var store: PlaylistCacheStore

    @Before
    fun start() {
        dir = Files.createTempDirectory("playlist-cache").toFile()
        store = PlaylistCacheStore(dir)
    }

    @After
    fun stop() {
        dir.deleteRecursively()
    }

    private fun track(id: String, title: String = id) = Track(
        id = id,
        title = title,
        albumId = "album-1",
        albumName = "Album",
        artistName = "Artist",
        durationMs = 200_000,
        indexNumber = 2,
        discNumber = 1,
        container = "flac",
        bitrate = 900,
        imageUrl = null,
        streamKey = null,
        isFavorite = true,
    )

    @Test
    fun aSavedPlaylistComesBackWithAllFields() {
        store.save("pl-1", "Late night", listOf(track("t1", "One"), track("t2", "Two")))

        val cached = store.load("pl-1")
        assertNotNull(cached)
        assertEquals("Late night", cached!!.name)
        assertEquals(listOf("t1", "t2"), cached.tracks.map { it.id })
        assertEquals("Two", cached.tracks[1].title)
        assertEquals(200_000L, cached.tracks[1].durationMs)
        assertEquals(2, cached.tracks[1].indexNumber)
        assertEquals(1, cached.tracks[1].discNumber)
        assertEquals("flac", cached.tracks[1].container)
        assertEquals(900, cached.tracks[1].bitrate)
        assertNull(cached.tracks[1].imageUrl)
        assertTrue(cached.tracks[1].isFavorite)
        assertTrue(cached.savedAt > 0)
    }

    @Test
    fun anUnknownPlaylistIsNull() {
        assertNull(store.load("nope"))
    }

    @Test
    fun clearRemovesTheCopy() {
        store.save("pl-1", "X", listOf(track("t1")))
        store.clear("pl-1")
        assertNull(store.load("pl-1"))
    }

    @Test
    fun anEmptyTrackListIsNotCached() {
        store.save("pl-1", "X", emptyList())
        assertNull(store.load("pl-1"))
    }

    @Test
    fun savingTheSameContentKeepsTheTimestamp() {
        store.save("pl-1", "X", listOf(track("t1")))
        val first = store.load("pl-1")!!.savedAt
        Thread.sleep(5)
        store.save("pl-1", "X", listOf(track("t1")))
        assertEquals(first, store.load("pl-1")!!.savedAt)
    }

    @Test
    fun newContentUpdatesTheTimestamp() {
        store.save("pl-1", "X", listOf(track("t1")))
        val first = store.load("pl-1")!!.savedAt
        Thread.sleep(5)
        store.save("pl-1", "X", listOf(track("t1"), track("t2")))
        val second = store.load("pl-1")!!.savedAt
        assertTrue(second > first)
        assertEquals(2, store.load("pl-1")!!.tracks.size)
    }

    @Test
    fun theFileListStaysFreeOfTemporaryFiles() {
        repeat(5) { index ->
            store.save("pl-$index", "P$index", listOf(track("t$index")))
        }
        val names = dir.listFiles().orEmpty().map { it.name }
        assertTrue(names.isNotEmpty())
        assertFalse("temporary files left behind: $names", names.any { it.endsWith(".tmp") })
    }

    @Test
    fun theFileListStaysFreeOfTemporaryFilesAfterAConcurrentRewrite() {
        store.save("pl-1", "X", listOf(track("t1")))
        store.save("pl-1", "Y", listOf(track("t1"), track("t2")))
        val names = dir.listFiles().orEmpty().map { it.name }
        assertEquals(1, names.size)
        assertEquals("Y", store.load("pl-1")!!.name)
    }

    @Test
    fun aCorruptFileIsIgnoredInsteadOfThrowing() {
        dir.mkdirs()
        File(dir, "pl-1.json").writeText("{not json")
        assertNull(store.load("pl-1"))
    }

    @Test
    fun anIdWithPathCharactersCannotEscapeTheDirectory() {
        store.save("../../etc/passwd", "X", listOf(track("t1")))
        val names = dir.listFiles().orEmpty().map { it.name }
        assertEquals(1, names.size)
        assertTrue(names.single().endsWith(".json"))
        assertTrue(dir.toPath().toRealPath().toString() in File(dir, names.single()).toPath().toRealPath().toString())
    }
}

package app.resona.player.core.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {

    @Test
    fun addsHttpSchemeAndTrailingSlash() {
        assertEquals("http://192.168.1.10:8096/", ServerUrl.normalize("192.168.1.10:8096"))
    }

    @Test
    fun keepsHttpsAndPath() {
        assertEquals("https://music.example.com/jellyfin/", ServerUrl.normalize("https://music.example.com/jellyfin/"))
    }

    @Test
    fun stripsPastedWebUiPath() {
        assertEquals(
            "http://host:8096/",
            ServerUrl.normalize("http://host:8096/web/index.html"),
        )
    }

    @Test
    fun hostExtraction() {
        assertEquals("music.example.com", ServerUrl.host("https://music.example.com/jellyfin"))
    }
}

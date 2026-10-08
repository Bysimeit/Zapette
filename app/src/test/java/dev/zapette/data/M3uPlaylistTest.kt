package dev.zapette.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistTest {

    private val sample = """
        #EXTM3U x-tvg-url="https://example.com/guide.xml.gz"
        #EXTINF:-1 tvg-id="a" tvg-logo="https://example.com/a.png" group-title="News;Sports",Channel A (1080p)
        https://example.com/a.m3u8
        #EXTINF:-1 tvg-logo="" http-user-agent="Mozilla/5.0 (X11; Linux, like Gecko)" group-title="Movies",Channel, with comma
        #EXTVLCOPT:http-referrer=https://example.com/
        http://example.com/b.php?id=1
        #EXTINF:-1,No attributes
        #EXTGRP:Music
        https://example.com/c.ts
    """.trimIndent()

    @Test
    fun `reads names, logos, groups and urls`() {
        val channels = M3uPlaylist.parse(sample)
        assertEquals(3, channels.size)

        val a = channels[0]
        assertEquals("Channel A (1080p)", a.entry.name)
        assertEquals("https://example.com/a.png", a.entry.image)
        assertEquals("https://example.com/a.m3u8", a.entry.url)
        assertEquals(listOf("News", "Sports"), a.groups)
        assertEquals(Kind.LIVE, a.entry.kind)
    }

    @Test
    fun `keeps commas inside names and attributes`() {
        val b = M3uPlaylist.parse(sample)[1]
        assertEquals("Channel, with comma", b.entry.name)
        assertEquals("Mozilla/5.0 (X11; Linux, like Gecko)", b.entry.userAgent)
        assertNull(b.entry.image)
        assertEquals(listOf("Movies"), b.groups)
    }

    @Test
    fun `handles entries without attributes`() {
        val c = M3uPlaylist.parse(sample)[2]
        assertEquals("No attributes", c.entry.name)
        assertEquals(listOf("Music"), c.groups)
    }

    @Test
    fun `rejects content that is not a playlist`() {
        val error = runCatching { M3uPlaylist.parse("<html>Not found</html>") }.exceptionOrNull()
        assertTrue(error is XtreamException && error.reason == XtreamException.Reason.NOT_PLAYLIST)
    }

    @Test
    fun `plain playlist accounts have no credentials`() {
        assertTrue(Account("https://example.com/list.m3u", "", "").isPlaylist)
        assertTrue(!Account("http://example.com:8080", "user", "pass").isPlaylist)
    }
}

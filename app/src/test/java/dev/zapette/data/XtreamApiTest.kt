package dev.zapette.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamApiTest {

    @Test
    fun `adds http and strips the trailing slash`() {
        assertEquals("http://example.com:8080", XtreamApi.normalizeServer("example.com:8080/"))
    }

    @Test
    fun `keeps https and drops the default port`() {
        assertEquals("https://example.com", XtreamApi.normalizeServer("https://example.com:443"))
    }

    @Test
    fun `cleans up a pasted player_api link`() {
        assertEquals(
            "http://example.com:8080",
            XtreamApi.normalizeServer("http://example.com:8080/player_api.php?username=a&password=b"),
        )
    }

    @Test
    fun `extracts credentials from an M3U link`() {
        val account = XtreamApi.parseM3uLink(
            "http://example.com:8080/get.php?username=user&password=secret&type=m3u_plus&output=ts"
        )
        assertEquals(Account("http://example.com:8080", "user", "secret"), account)
    }

    @Test
    fun `ignores a plain address without credentials`() {
        assertNull(XtreamApi.parseM3uLink("http://example.com:8080"))
    }

    @Test
    fun `reads movie details`() {
        val detail = XtreamApi.parseMovieDetail(
            """{"info":{"movie_image":"http://x/p.jpg","genre":"Drame","plot":"Story","cast":"A, B",
            "director":"C","releasedate":"08 May 2026","duration_secs":5002,"duration":"01:23:22"}}"""
        )
        assertEquals(MovieDetail("Story", "http://x/p.jpg", "Drame", "08 May 2026", 83, "C", "A, B"), detail)
    }

    @Test
    fun `reads the runtime from duration_secs first`() {
        assertEquals(112, XtreamApi.parseMovieDetail("""{"info":{"duration_secs":6720,"duration":"00:10:00"}}""").minutes)
    }

    @Test
    fun `falls back to the HH-MM-SS duration`() {
        assertEquals(112, XtreamApi.parseMovieDetail("""{"info":{"duration":"01:52:30"}}""").minutes)
    }

    @Test
    fun `falls back to the runtime in minutes`() {
        assertEquals(95, XtreamApi.parseMovieDetail("""{"info":{"episode_run_time":"95"}}""").minutes)
    }

    @Test
    fun `has no runtime when info is empty`() {
        assertNull(XtreamApi.parseMovieDetail("""{"info":[],"movie_data":{}}""").minutes)
        assertNull(XtreamApi.parseMovieDetail("""{"info":{"duration_secs":0,"duration":"00:00:00"}}""").minutes)
    }
}

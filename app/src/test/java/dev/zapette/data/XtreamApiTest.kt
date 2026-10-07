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
}

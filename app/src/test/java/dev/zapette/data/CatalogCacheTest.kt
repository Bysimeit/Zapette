package dev.zapette.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class CatalogCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000L
    private val day = TimeUnit.HOURS.toMillis(24)

    private fun cache() = CatalogCache(folder.root.resolve("catalog"), day) { clock }

    @Test
    fun `returns what was written while fresh`() {
        val cache = cache()
        cache.write("a", "body")
        clock += day - 1
        assertEquals("body", cache.read("a"))
        assertNull(cache.read("b"))
    }

    @Test
    fun `expires 24 hours after the first write`() {
        val cache = cache()
        cache.write("a", "old")
        clock += day / 2
        cache.write("b", "newer")
        clock += day / 2
        assertTrue(cache.isExpired())
        assertNull(cache.read("a"))
        assertNull(cache.read("b"))
    }

    @Test
    fun `starts a new period after expiry`() {
        val cache = cache()
        cache.write("a", "old")
        clock += day
        cache.write("b", "fresh")
        assertFalse(cache.isExpired())
        assertNull(cache.read("a"))
        assertEquals("fresh", cache.read("b"))
    }

    @Test
    fun `clear drops everything`() {
        val cache = cache()
        cache.write("a", "body")
        cache.clear()
        assertFalse(cache.isExpired())
        assertNull(cache.read("a"))
    }

    @Test
    fun `survives a new instance`() {
        cache().write("a", "body")
        assertEquals("body", cache().read("a"))
    }
}

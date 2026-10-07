package dev.zapette.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RateLimitTest {

    private val server = MockWebServer().apply { start() }
    private val api = XtreamApi(Account(server.url("/").toString(), "user", "pass"))

    private fun tooMany() = MockResponse().setResponseCode(429).setHeader("Retry-After", "1")

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `retries after a 429`() = runBlocking {
        server.enqueue(tooMany())
        server.enqueue(MockResponse().setBody("""[{"category_id":"1","category_name":"News"}]"""))

        assertEquals(listOf(Category("1", "News")), api.categories(Kind.LIVE))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `gives up after two retries`() = runBlocking {
        repeat(3) { server.enqueue(tooMany()) }

        try {
            api.categories(Kind.LIVE)
            fail("expected a 429 error")
        } catch (e: XtreamException) {
            assertEquals(429, e.httpCode)
        }
        assertEquals(3, server.requestCount)
    }
}

package app.masroufy.device

import app.masroufy.core.PriceFeed
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryFeedCache
import app.masroufy.port.HttpFailure
import app.masroufy.usecase.FeedState
import app.masroufy.usecase.LoadOnlineFeeds
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * الجلب من النت بـ`HttpURLConnection` (نفس كود أندرويد) قدام سيرفر محلي على الكمبيوتر — من غير نت حقيقي: النص بالعربي UTF-8 بيوصل زي ما هو،
 * 404 ⇒ `HttpFailure(404)`، مفيش سيرفر ⇒ `HttpFailure(null)`، والقراية كلها لحد `LoadOnlineFeeds` ⇒ ملف أسعار. الأرقام مخترعة.
 */
class PlatformHttpTextTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/public/prices.json") { ex ->
            val body = """{"generatedAt":"2026-10-08T08:00:00Z","baseCurrency":"SAR","prices":{"GOLD_24K_GRAM":{"name":"ذهب عيار 24","unit":"جرام","pricePerUnitMinor":40000,"asOf":"2026-10-08","source":"مصدر وهمي"}}}"""
                .toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        createContext("/public/missing.json") { ex ->
            ex.sendResponseHeaders(404, -1)
            ex.close()
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}/public/"

    @AfterTest
    fun stop() = server.stop(0)

    @Test fun readsUtf8Text() = runBlocking<Unit> {
        val text = PlatformHttpText().getText(base + "prices.json")
        assertEquals(true, text.contains("ذهب عيار 24"))
    }

    @Test fun non200IsAFailureWithItsStatus() = runBlocking<Unit> {
        val e = assertFailsWith<HttpFailure> { PlatformHttpText().getText(base + "missing.json") }
        assertEquals(404, e.status)
    }

    @Test fun noServerIsAFailureWithoutStatus() = runBlocking<Unit> {
        val closed = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).address.port
        val e = assertFailsWith<HttpFailure> { PlatformHttpText().getText("http://127.0.0.1:$closed/x.json") }
        assertNull(e.status)
    }

    @Test fun feedsUseTheRealFetcher() = runBlocking<Unit> {
        val feeds = LoadOnlineFeeds(PlatformHttpText(), MemoryFeedCache(), FixedClock("2026-10-09T07:00:00.000Z"), { 5L }, baseUrl = base)
        val ready = assertIs<FeedState.Ready<PriceFeed>>(feeds.prices())
        assertEquals(40000L, ready.feed.prices.single().pricePerUnitMinor)
        assertIs<FeedState.Unavailable>(feeds.averages(), "averages.json مش على السيرفر ⇒ غير متاح بسببه")
    }
}

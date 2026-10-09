package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryFeedCache
import app.masroufy.memory.MemoryHttpText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ملفات الأسعار والمتوسطات من النت (ARCHITECTURE §6): القراية بقارئ `core` · النسخة على الجهاز و«آخر تحديث» · من غير نت ⇒ القديمة أو «غير متاح».
 * الأرقام مخترعة (المستودع عام).
 */
class LoadOnlineFeedsTest {
    private val pricesJson = """
        {"generatedAt":"2026-10-08T08:14:34.651Z","baseCurrency":"SAR","prices":{
          "GOLD_24K_GRAM":{"name":"ذهب عيار 24","unit":"جرام","pricePerUnitMinor":40000,"asOf":"2026-10-08","source":"مصدر وهمي"},
          "GOLD_24K_GRAM_EGP":{"name":"ذهب بالجنيه","unit":"جرام","currency":"EGP","pricePerUnitMinor":500000,"asOf":"2026-10-08","source":"مصدر وهمي"},
          "BROKEN":{"name":"سطر بايظ","pricePerUnitMinor":12.5,"asOf":"2026-10-08","source":"x"}
        },"failures":[{"source":"مصدر واقع","reason":"timeout"}]}
    """.trimIndent()

    private val averagesJson = """
        {"schema":1,"generatedAt":"2026-10-05T15:14:13.466Z","averages":{
          "GOLD_USD":{"kind":"computed","valueBp":900,"periodStart":"2015-12","periodEnd":"2025-12","official":true,"sourceName":"مصدر وهمي","computedOn":"2026-10-05"}
        }}
    """.trimIndent()

    private var now = 1_000_000L
    private val http = MemoryHttpText().also {
        it[FEEDS_BASE_URL + "prices.json"] = pricesJson
        it[FEEDS_BASE_URL + "averages.json"] = averagesJson
    }
    private val cache = MemoryFeedCache()
    private val feeds = LoadOnlineFeeds(http, cache, FixedClock("2026-10-09T07:00:00.000Z"), { now })

    @Test fun parsesBothFilesWithTheCoreReaders() = runBlocking<Unit> {
        val prices = assertIs<FeedState.Ready<app.masroufy.core.PriceFeed>>(feeds.prices())
        assertTrue(prices.fromNetworkNow)
        assertEquals("2026-10-09T07:00:00.000Z", prices.fetchedAtIso, "«آخر تحديث» = وقت التنزيل")
        assertEquals(listOf("GOLD_24K_GRAM", "GOLD_24K_GRAM_EGP"), prices.feed.prices.map { it.symbol })
        assertEquals(1, prices.feed.rejected.size, "السطر البايظ بيترفض لوحده")
        assertEquals(1, prices.feed.failures.size)
        val avg = assertIs<FeedState.Ready<app.masroufy.core.AveragesFeed>>(feeds.averages())
        assertEquals(900, avg.feed.entries.getValue("GOLD_USD").valueBp)
    }

    @Test fun freshCopyIsReusedAndForceRefetches() = runBlocking<Unit> {
        feeds.prices()
        now += 60_000
        val again = assertIs<FeedState.Ready<*>>(feeds.prices())
        assertEquals(false, again.fromNetworkNow)
        assertEquals(1, http.calls.size, "النسخة الحديثة ما بتتنزلش تاني")
        feeds.prices(force = true)
        assertEquals(2, http.calls.size)
        now += FEED_MAX_AGE_MILLIS
        feeds.prices()
        assertEquals(3, http.calls.size, "النسخة القديمة بتتنزل تاني لوحدها")
    }

    @Test fun offlineKeepsTheLastCopyWithItsTimeOrSaysNotAvailable() = runBlocking<Unit> {
        http.offline = true
        val none = assertIs<FeedState.Unavailable>(feeds.prices())
        assertEquals(uiText(TextKey.FEED_OFFLINE), none.reason, "ولا نسخة ⇒ «غير متاح» بسببه، مش صفر")
        http.offline = false
        feeds.prices()
        http.offline = true
        now += FEED_MAX_AGE_MILLIS + 1
        val kept = assertIs<FeedState.Ready<*>>(feeds.prices())
        assertEquals("2026-10-09T07:00:00.000Z", kept.fetchedAtIso)
        assertEquals(uiText(TextKey.FEED_OFFLINE), kept.refreshFailed)
    }

    @Test fun badFileIsRejectedAndNeverCached() = runBlocking<Unit> {
        http[FEEDS_BASE_URL + "prices.json"] = "{\"prices\": 3}"
        val bad = assertIs<FeedState.Unavailable>(feeds.prices())
        assertEquals(uiText(TextKey.FEED_NO_GENERATED_AT), bad.reason)
        assertNull(cache.read("prices.json"))
        http[FEEDS_BASE_URL + "prices.json"] = "not json"
        assertEquals(uiText(TextKey.FEED_BAD_SHAPE), assertIs<FeedState.Unavailable>(feeds.prices()).reason)
        http[FEEDS_BASE_URL + "prices.json"] = pricesJson
        http[FEEDS_BASE_URL + "averages.json"] = "{}"
        val missing = assertIs<FeedState.Unavailable>(LoadOnlineFeeds(http, cache, FixedClock("x"), { now }, baseUrl = "https://example.invalid/").prices())
        assertEquals(uiText(TextKey.FEED_HTTP_FAILED, "404"), missing.reason)
    }

    @Test fun pricesFeedSyncAssets() = runBlocking<Unit> {
        val assets = MemoryAssetRepository(
            listOf(
                app.masroufy.core.Asset(
                    id = "a1", name = "ذهب", kind = "gold", unitLabel = "جرام", currency = Currency.SAR, archived = false, feedSymbol = "GOLD_24K_GRAM",
                ),
            ),
        )
        val prices = MemoryAssetPriceRepository()
        val feed = assertIs<FeedState.Ready<app.masroufy.core.PriceFeed>>(feeds.prices()).feed
        val outcome = SyncAssetPrices(SyncAssetPricesDeps(assets, prices)).sync(feed)
        assertEquals(1, outcome.updated.size)
        assertEquals(40000L, prices.listAll().single().pricePerUnitMinor)
    }
}

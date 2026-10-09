package app.masroufy.usecase

import app.masroufy.core.AveragesFeed
import app.masroufy.core.PriceFeed
import app.masroufy.core.PriceFeedError
import app.masroufy.core.TextKey
import app.masroufy.core.parseAveragesFeed
import app.masroufy.core.parsePriceFeed
import app.masroufy.core.uiText
import app.masroufy.port.CachedFeed
import app.masroufy.port.Clock
import app.masroufy.port.FeedCachePort
import app.masroufy.port.HttpFailure
import app.masroufy.port.HttpTextPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * ملفات الأسعار والمتوسطات من مستودع الأسعار (ARCHITECTURE §6 · OVERRIDES §69.6) — بتغذّي `SyncAssetPrices` و`LoadDefaultRates`.
 * - بينزّل الملف ويقراه بنفس قارئ `core` (`parsePriceFeed` · `parseAveragesFeed`) — الشكل الغلط بيترفض، مش بيتخمّن.
 * - **النسخة على الجهاز:** آخر نسخة سليمة بتتحفظ بوقتها ⇒ من غير نت بيتعرض «آخر تحديث» بتاريخها، ولو ولا مرة نزلت ⇒ «غير متاح»
 *   بسببه (CLAUDE.md #10 — مش صفر).
 * - **مش كل فتحة:** الملفات بتتجدد مرة في اليوم (GitHub Actions)، فلو النسخة أحدث من [maxAgeMillis] ما بننزلش تاني إلا بـ`force`.
 */
enum class OnlineFeed(val fileName: String) {
    PRICES("prices.json"),
    AVERAGES("averages.json"),
}

/** نفس مكان التطبيق الحالي (ARCHITECTURE §6). */
const val FEEDS_BASE_URL = "https://raw.githubusercontent.com/mohussamarch/tagroba/main/public/"

const val FEED_MAX_AGE_MILLIS: Long = 6L * 60 * 60 * 1000

sealed interface FeedState<out T> {
    /**
     * [fetchedAtIso] = إمتى النسخة دي نزلت («آخر تحديث»). [refreshFailed] = حاولنا ننزّل دلوقتي وفشل (السبب بلغة المستخدم) —
     * المعروض لسه النسخة القديمة.
     */
    data class Ready<T>(val feed: T, val fetchedAtIso: String, val fromNetworkNow: Boolean, val refreshFailed: String? = null) : FeedState<T>

    /** ولا نسخة سليمة على الجهاز ولا من النت. */
    data class Unavailable(val reason: String) : FeedState<Nothing>
}

private sealed interface Parsed<out T> {
    data class Ok<T>(val feed: T) : Parsed<T>

    data class Bad(val reason: String) : Parsed<Nothing>
}

class LoadOnlineFeeds(
    private val http: HttpTextPort,
    private val cache: FeedCachePort,
    private val clock: Clock,
    private val nowMillis: () -> Long,
    private val baseUrl: String = FEEDS_BASE_URL,
    private val maxAgeMillis: Long = FEED_MAX_AGE_MILLIS,
) {
    suspend fun prices(force: Boolean = false): FeedState<PriceFeed> = load(OnlineFeed.PRICES, force, ::parsePriceFeed)

    suspend fun averages(force: Boolean = false): FeedState<AveragesFeed> = load(OnlineFeed.AVERAGES, force, ::parseAveragesFeed)

    private suspend fun <T> load(which: OnlineFeed, force: Boolean, parse: (Any?) -> T): FeedState<T> {
        val stored = cache.read(which.fileName)
        val cached = stored?.let { (tryParse(it.text, parse) as? Parsed.Ok<T>)?.feed }
        if (!force && stored != null && cached != null && nowMillis() - stored.fetchedAtMillis in 0 until maxAgeMillis) {
            return FeedState.Ready(cached, stored.fetchedAtIso, fromNetworkNow = false)
        }
        val failure: String = try {
            val text = http.getText(baseUrl + which.fileName)
            when (val got = tryParse(text, parse)) {
                is Parsed.Ok -> {
                    val now = CachedFeed(text, clock.nowIso(), nowMillis())
                    cache.write(which.fileName, now)
                    return FeedState.Ready(got.feed, now.fetchedAtIso, fromNetworkNow = true)
                }
                is Parsed.Bad -> got.reason
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpFailure) {
            if (e.status == null) uiText(TextKey.FEED_OFFLINE) else uiText(TextKey.FEED_HTTP_FAILED, e.status.toString())
        }
        return if (stored != null && cached != null) FeedState.Ready(cached, stored.fetchedAtIso, fromNetworkNow = false, refreshFailed = failure)
        else FeedState.Unavailable(failure)
    }

    private fun <T> tryParse(text: String, parse: (Any?) -> T): Parsed<T> = try {
        Parsed.Ok(parse(JsonText.parse(text)))
    } catch (e: PriceFeedError) {
        Parsed.Bad(e.message ?: uiText(TextKey.FEED_BAD_SHAPE))
    } catch (_: JsonText.InvalidJson) {
        Parsed.Bad(uiText(TextKey.FEED_BAD_SHAPE))
    }
}

package app.masroufy.usecase

import app.masroufy.core.DateParts
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.Merchant
import app.masroufy.core.SharedMerchantEntry
import app.masroufy.core.baselineCatalog
import app.masroufy.core.contributionFor
import app.masroufy.core.effectiveEntry
import app.masroufy.core.latestUpdate
import app.masroufy.core.planAccountMerchantSync
import app.masroufy.core.shareableName
import app.masroufy.core.sharedMerchantKey
import app.masroufy.core.toDayNumber
import app.masroufy.port.Clock
import app.masroufy.port.MerchantRepository
import app.masroufy.port.SharedMerchantCatalogPort
import app.masroufy.port.SyncCursorPort

/**
 * قاعدة التجار المشتركة — نقل `sharedMerchants.ts` (OVERRIDES §25 و§25.1).
 * `sync`: التغييرات من آخر مرة، **وكل المؤكدين مرة في اليوم**، وبيضيف للحساب المؤكد الناقص من غير ما يكتب فوق تصنيف المستخدم.
 * `contribute`: لما المستخدم يأكد تصنيف شراء، اسم المحل وتصنيفه بيتبعتوا **مش مؤكدين**.
 */

data class SharedSyncResult(val changes: Int, val added: Int, val filled: Int)

enum class ContributionResult(val wire: String) { SHARED("shared"), SKIPPED("skipped") }

data class SharedMerchantsDeps(
    val catalog: SharedMerchantCatalogPort,
    val merchants: MerchantRepository,
    /** تجار المرجع اللي جوه التطبيق — أساس القاعدة (§25.1). */
    val baseline: List<Merchant>,
    /** معرّفات شجرة التصنيفات بس — نفس المعرّف في كل الحسابات. */
    val treeCategoryIds: Set<Id>,
    val cursor: SyncCursorPort,
    /** آخر مراجعة لكل المؤكدين. */
    val confirmedCursor: SyncCursorPort,
    val clock: Clock,
)

/** كل المؤكدين بيتقروا مرة في اليوم على الأكتر (حد القراية المجانية 50 ألف في اليوم). */
private const val CONFIRMED_REFRESH_MS = 24L * 60 * 60 * 1000

private val INSTANT = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3})\d*)?)?(Z|[+-]\d{2}:\d{2})""")

/** وقت ISO بالمللي زي `Date.parse` لشكل `toISOString` والمناطق الزمنية؛ غير كده null (جافاسكربت NaN). */
private fun instantMillis(iso: String): Long? {
    val m = INSTANT.matchEntire(iso) ?: return null
    val g = m.groupValues
    val day = toDayNumber(DateParts(g[1].toInt(), g[2].toInt(), g[3].toInt())).toLong()
    val millis = g[7].ifEmpty { "0" }.padEnd(3, '0').toLong()
    val offsetMinutes = if (g[8] == "Z") 0 else (g[8].substring(1, 3).toInt() * 60 + g[8].substring(4, 6).toInt()) * (if (g[8][0] == '-') -1 else 1)
    val seconds = day * 86_400 + g[4].toInt() * 3_600 + g[5].toInt() * 60 + g[6].ifEmpty { "0" }.toInt() - offsetMinutes * 60L
    return seconds * 1_000 + millis
}

class SharedMerchants(private val deps: SharedMerchantsDeps) {
    private val baseline = baselineCatalog(deps.baseline)

    suspend fun sync(): SharedSyncResult {
        val since = deps.cursor.read()
        val now = deps.clock.nowIso()
        val last = deps.confirmedCursor.read()
        // أول مزامنة بتقرا كله أصلًا؛ بعدها كل المؤكدين مرة في اليوم
        val refresh = since != null && (last.isNullOrEmpty() || run {
            val a = instantMillis(now)
            val b = instantMillis(last)
            a != null && b != null && a - b >= CONFIRMED_REFRESH_MS
        })
        val changed = deps.catalog.listChangedSince(since)
        val confirmed = if (refresh) deps.catalog.listConfirmed() else emptyList()
        val byKey = LinkedHashMap<String, SharedMerchantEntry>()
        for (e in confirmed) byKey[sharedMerchantKey(e.normalizedName)] = e
        for (e in changed) byKey[sharedMerchantKey(e.normalizedName)] = e
        val remote = byKey.values.toList()
        var added = 0
        var filled = 0
        if (remote.isNotEmpty()) {
            val effective = remote.map { effectiveEntry(baseline[sharedMerchantKey(it.normalizedName)], it)!! }
            val plan = planAccountMerchantSync(effective, deps.merchants.listAll(), deps.treeCategoryIds)
            val writes = plan.add + plan.fill
            if (writes.isNotEmpty()) deps.merchants.saveMany(writes)
            added = plan.add.size
            filled = plan.fill.size
        }
        // المؤشرات بتتقدم بعد الكتابة بس — انقطاع في النص بيعيد نفس التغييرات (المعرّفات ثابتة فمفيش تكرار)
        val latest = latestUpdate(changed, since)
        if (latest != null && latest != since) deps.cursor.write(latest)
        if (since == null || refresh) deps.confirmedCursor.write(now)
        return SharedSyncResult(remote.size, added, filled)
    }

    suspend fun contribute(economicKind: EconomicKind, observedDirection: Direction, rawMerchantName: String?, categoryId: Id): ContributionResult {
        // الفحص الرخيص الأول: مفيش قراية من السيرفر لعملية مش هتتبعت أصلًا
        val probe = contributionFor(economicKind, observedDirection, rawMerchantName, categoryId, deps.treeCategoryIds, null)
        val normalized = shareableName(rawMerchantName)
        if (probe == null || normalized == null) return ContributionResult.SKIPPED
        val current = effectiveEntry(baseline[sharedMerchantKey(normalized)], deps.catalog.get(normalized))
        val entry = contributionFor(economicKind, observedDirection, rawMerchantName, categoryId, deps.treeCategoryIds, current)
            ?: return ContributionResult.SKIPPED
        deps.catalog.save(entry)
        return ContributionResult.SHARED
    }
}

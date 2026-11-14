package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.HoldingType
import app.masroufy.core.HoldingValue
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.IsoDate
import app.masroufy.core.PutAwayHolding
import app.masroufy.core.UserSetting
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.currentValueOf
import app.masroufy.core.holdingTypeOf
import app.masroufy.core.isRealEstate
import app.masroufy.core.purposeOf
import app.masroufy.port.Clock
import app.masroufy.port.UserSettingsStore
import app.masroufy.port.ZakatFactRepository

/**
 * «حاجاتي» من بيانات المساحة (مواصفة r05 §2.6 · §4.0 بند 4): كل أصل مش مؤرشف بقيمته النهارده — نفس منطق القيمة الموجود من غير
 * حساب جديد (المعادن والأسهم والباقي = المركز × السعر · العقار = `currentValueOf`). ⚠️ «الخطوة 0» — اتعملت هنا لأن الحزم بدأت قبلها.
 *
 * القيمة «غير متاح» (null، مش صفر) لو: مفيش سعر · مفيش شراء متسجل خالص (غير العقار) · عملة الأصل غير عملة البلد.
 * الأصل اللي اتباع كله (شراء متسجل والمملوك صفر) والمؤرشف مش «حاجة معاك» ⇒ مش في المجموع — بيروحوا [HoldingsSnapshot.putAway] (قسم مطوي تحت).
 * [LoadHoldingsDeps.settings] = مكان اختيار «تدخل الحاجات الشخصية في المجموع؟» (null أو مش محفوظ ⇒ لأ — رد المالك ٢أ).
 */
data class LoadHoldingsDeps(val assets: ManageAssets, val zakatFacts: ZakatFactRepository, val currency: Currency, val settings: UserSettingsStore? = null)

class LoadHoldings(private val deps: LoadHoldingsDeps) {
    suspend fun load(today: IsoDate): HoldingsSnapshot {
        val view = deps.assets.listPortfolio(today)
        val facts = deps.zakatFacts.listAll().associateBy { it.subjectId }
        val (shown, away) = view.rows.partition { !it.asset.archived && !soldOut(it) }
        val putAway = away.map { PutAwayHolding(it.asset.id, it.asset.name, holdingTypeOf(it.asset), it.asset.archived, soldOut(it)) }
            .sortedBy { it.name }
        val items = shown
            .map { row ->
                val asset = row.asset
                val fact = facts[asset.id]
                val jewellery = fact?.purpose == ZakatPurpose.WEAR
                val value = when {
                    asset.currency != deps.currency -> null
                    asset.isRealEstate -> currentValueOf(asset, row.position, lotsRecorded = row.lots.isNotEmpty())
                    row.lots.isEmpty() -> null
                    else -> row.position.marketValueMinor
                }
                HoldingValue(asset, holdingTypeOf(asset), purposeOf(asset, jewellery), value, row.position.heldQuantity, fact?.karat, fact?.fineness, jewellery)
            }
        val goldMissing = items.any { it.type == HoldingType.GOLD && it.valueMinor == null && it.asset.currency == deps.currency && it.quantity > 0L }
        val includePersonal = deps.settings?.let { readIncludePersonal(it) } ?: false
        return HoldingsSnapshot(deps.currency, items, goldMissing, includePersonal, putAway)
    }

    /** اتباع كله: فيه شراء متسجل والمملوك صفر. */
    private fun soldOut(row: AssetRow): Boolean = row.lots.isNotEmpty() && row.position.heldQuantity <= 0L
}

/** الاختيار المحفوظ (مش موجود أو الإعدادات وقعت ⇒ لأ — الافتراضي بتاع المالك). */
private suspend fun readIncludePersonal(settings: UserSettingsStore): Boolean =
    runCatching { settings.listAll().filterIsInstance<UserSetting.HoldingsPrefs>().firstOrNull()?.includePersonal }.getOrNull() ?: false

/** «تدخل الحاجات الشخصية في المجموع؟» (رد المالك 2026-10-16 ٢أ) — قراية وكتابة على الحساب. */
class ManageHoldingsPrefs(private val settings: UserSettingsStore, private val clock: Clock) {
    suspend fun includePersonal(): Boolean = readIncludePersonal(settings)

    suspend fun setIncludePersonal(on: Boolean) {
        settings.save(UserSetting.HoldingsPrefs(on, clock.nowIso()))
    }
}

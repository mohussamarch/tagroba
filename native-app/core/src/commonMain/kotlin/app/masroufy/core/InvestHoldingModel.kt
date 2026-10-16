package app.masroufy.core

/**
 * عقد «الاستثمار» الجديد المشترك (مواصفة النقل §4.0 — الخطوة 0): الأنواع اللي «حاجاتي» و«هتبقى معاك كام» و«الأفكار» بيتشاركوها.
 * ⚠️ الخطوة 0 ما اتعملتش قبل الحزم ⇒ الملف ده اتكتب من حزمة FUTURE **بالجزء اللي هي محتاجاه بس** وبنفس الأسامي اللي في المواصفة؛
 * المُجمِّع بيدمجه مع نسخة حزمة HOLDINGS لو كتبت نفس الملف (`HoldingType` · `holdingTypeOf` · `purposeOf` عندها).
 *
 * فلوس بالهللة (`Long`) ونسب بنقاط الأساس (`Int`) — مفيش كسر عائم (CLAUDE.md #1).
 */

/** مجموعة الحاجة في «حاجاتي» (مربع لكل مجموعة) — بالترتيب الثابت للعرض. */
enum class HoldingGroup(val wire: String) {
    METAL("metal"), PROPERTY("property"), SHARES("shares"), PERSONAL("personal"), COLLECTIBLES("collectibles"), OTHER("other");

    companion object {
        fun fromWire(wire: String?): HoldingGroup? = entries.firstOrNull { it.wire == wire }
    }
}

/** الغرض: استثمار · احتياط · شخصي (برّه الإجمالي والتوقع — سؤال المالك Q1) · تجارة. */
enum class HoldingPurpose(val wire: String) {
    INVEST("invest"), RESERVE("reserve"), PERSONAL("personal"), TRADE("trade");

    companion object {
        fun fromWire(wire: String?): HoldingPurpose? = entries.firstOrNull { it.wire == wire }
    }
}

/** بأي متوسط رسمي بتكبر في التوقع: العقار بالتضخم · الذهب · الأسهم · ولا حاجة (ثابتة بالاسم — الفضة والمقتنيات، سؤال Q4). */
enum class GrowthBucket { PROPERTY, GOLD, SHARES, NONE }

/** حاجة واحدة بقيمتها النهارده — [valueMinor] null = القيمة مش معروفة (برّه المجموع ومعدودة — CLAUDE.md #10). */
data class HoldingValue(
    val assetId: Id,
    val name: String,
    val group: HoldingGroup,
    val purpose: HoldingPurpose,
    val bucket: GrowthBucket,
    val valueMinor: Halalas?,
)

/** كل حاجات البلد الشغالة (غير المؤرشفة) بعملتها. [goldPriceMissing] ⇒ سعر الذهب النهارده مش متاح والذهب قيمته null. */
/**
 * [includePersonal] = اختيار المستخدم «تدخل الحاجات الشخصية في المجموع؟» (رد المالك 2026-10-16 ‎2a: الافتراضي **من غيرها**، والاختيار
 * محفوظ للمستخدم ومفتاحه في «حاجاتي» — حزمة HOLDINGS). بيدخل المجموع والتوقع «إنت كمان» مع بعض.
 */
data class HoldingsSnapshot(val currency: Currency, val items: List<HoldingValue>, val goldPriceMissing: Boolean, val includePersonal: Boolean = false)

/** اللي بيدخل التوقع من الحاجات (من غير الشخصي): مقسوم على المتوسطات. [unknownCount] = حاجات قيمتها مش معروفة (برّه الحساب). */
data class InvestableSplit(
    val propertyMinor: Halalas,
    val goldMinor: Halalas,
    val sharesMinor: Halalas,
    val noGrowthMinor: Halalas,
    val unknownCount: Int,
) {
    val totalMinor: Halalas get() = addMoney(addMoney(propertyMinor, goldMinor), addMoney(sharesMinor, noGrowthMinor))

    companion object {
        val EMPTY = InvestableSplit(0, 0, 0, 0, 0)
    }
}

/** التقسيم من الحاجات: الشخصي برّه (إلا لو [includePersonal] — ثابت بالاسم من غير نمو)، والقيمة المجهولة برّه ومعدودة. */
fun investableSplit(items: List<HoldingValue>, includePersonal: Boolean = false): InvestableSplit {
    var re = 0L
    var gold = 0L
    var stk = 0L
    var flat = 0L
    var unknown = 0
    for (h in items) {
        if (h.purpose == HoldingPurpose.PERSONAL && !includePersonal) continue
        val v = h.valueMinor
        if (v == null) {
            unknown++
            continue
        }
        when (h.bucket) {
            GrowthBucket.PROPERTY -> re = addMoney(re, v)
            GrowthBucket.GOLD -> gold = addMoney(gold, v)
            GrowthBucket.SHARES -> stk = addMoney(stk, v)
            GrowthBucket.NONE -> flat = addMoney(flat, v)
        }
    }
    return InvestableSplit(re, gold, stk, flat, unknown)
}

/** المتوسطات الرسمية التلاتة للبلد بمصادرها (من ملف المتوسطات — §69.6). */
data class InvestRates(
    val cpiBp: Int,
    val goldBp: Int,
    val sharesBp: Int,
    val cpi: AverageEntry,
    val gold: AverageEntry,
    val shares: AverageEntry,
)

sealed interface InvestRatesState {
    data class Ready(val rates: InvestRates) : InvestRatesState

    /** واحد أو أكتر من التلاتة مش موجود ⇒ التوقع كله «غير متاح» (زي النموذج — كله أو لا شيء). [missing] مفاتيح الملف الناقصة. */
    data class Unavailable(val missing: List<String>) : InvestRatesState
}

/**
 * «زي أملاكك»: متوسط المعدلات موزون بقيمة كل نوع = (عقار×تضخم + ذهب×ذهب + أسهم×أسهم) ÷ (عقار + ذهب + أسهم) — تقريب لتحت.
 * الثابت ([InvestableSplit.noGrowthMinor]) مش داخل الوزن (زي النموذج). المقام صفر ⇒ null.
 */
fun holdingsMixBp(split: InvestableSplit, rates: InvestRates): Int? {
    val w = addMoney(addMoney(split.propertyMinor, split.goldMinor), split.sharesMinor)
    if (w <= 0L) return null
    val num = mulDivWide(split.propertyMinor, rates.cpiBp.toLong(), 1) + mulDivWide(split.goldMinor, rates.goldBp.toLong(), 1) +
        mulDivWide(split.sharesMinor, rates.sharesBp.toLong(), 1)
    return num.floorDiv(w).toInt()
}

package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.UiKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.formatBp
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.monthYear
import app.masroufy.core.normalizeDigits
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.text.amount
import app.masroufy.ui.text.t
import app.masroufy.ui.text.trueMinus

/**
 * صياغة وقراية الخانات في الحاسبات — **عرض بس**، من غير أي حساب فلوس (الأرقام كلها جاية من حالات الاستخدام بالهللة).
 * قراية المبلغ من `core` (`tryParseMoney` — نفس قراية كل التطبيق)، والعدّ بالأرقام العربية في الجمل (`sentenceNumber`).
 */

/** «شهر واحد · شهران · ٣ أشهر · ١١ شهرًا» (المصري: «شهرين · ٣ شهور · ١١ شهر»). */
fun monthsPhrase(n: Int): String = when (n) {
    1 -> t(UiKey.CALCUI_MONTHS_ONE)
    2 -> t(UiKey.CALCUI_MONTHS_TWO)
    in 3..10 -> t(UiKey.CALCUI_MONTHS_FEW, sentenceNumber(n))
    else -> t(UiKey.CALCUI_MONTHS_MANY, sentenceNumber(n))
}

/** «سنة · سنتان · ٣ سنوات · ١١ سنة». */
fun yearsPhrase(n: Int): String = when (n) {
    1 -> t(UiKey.CALCUI_YEARS_ONE)
    2 -> t(UiKey.CALCUI_YEARS_TWO)
    in 3..10 -> t(UiKey.CALCUI_YEARS_FEW, sentenceNumber(n))
    else -> t(UiKey.CALCUI_YEARS_MANY, sentenceNumber(n))
}

/** مدة بالشهور: سنين كاملة بالسنين، وإلا بالشهور. */
fun durationPhrase(months: Int): String = if (months > 0 && months % 12 == 0) yearsPhrase(months / 12) else monthsPhrase(months)

/** «٧ أكتوبر ٢٠٢٨». */
fun fullDate(iso: IsoDate): String = t(UiKey.CALCUI_DAY_MONTH_YEAR, dayMonth(iso), sentenceNumber(parseIsoDate(iso).year))

/** «أبريل ٢٠٥٥». */
fun monthYearOf(iso: IsoDate): String = parseIsoDate(iso).let { monthYear(it.year, it.month) }

/** السن بالشهور ⇒ «٦٥ سنة» أو «٦٤ سنة و٨ أشهر». */
fun agePhrase(months: Int): String {
    val years = sentenceNumber(months / 12)
    val rest = months % 12
    return if (rest == 0) t(UiKey.CALCUI_AGE, years) else t(UiKey.CALCUI_AGE_MONTHS, years, monthsPhrase(rest))
}

/** «14.88%» بعلامة الطرح الحقيقية. */
fun percentText(bp: Int): String = trueMinus(formatBp(bp))

/** مبلغ من غير عملة («12,500.00») — لاقتراح جوه خانة. */
fun plainAmount(minor: Halalas, currency: Currency): String = trueMinus(amount(minor, currency))

/** الخانة فاضية؟ */
fun String.isBlankField(): Boolean = normalizeDigits(this).isBlank()

/** مبلغ من الخانة: فاضي ⇒ null، مش مقروء ⇒ [Bad]. */
sealed interface Parsed<out T> {
    data object Empty : Parsed<Nothing>
    data object Bad : Parsed<Nothing>
    data class Ok<T>(val value: T) : Parsed<T>
}

fun parseAmountField(text: String, currency: Currency): Parsed<Halalas> {
    if (text.isBlankField()) return Parsed.Empty
    return tryParseMoney(text, currency)?.let { Parsed.Ok(it) } ?: Parsed.Bad
}

/** عدد صحيح من الخانة (شهور · سنين) — أرقام بس، لحد ٦ خانات. */
fun parseCountField(text: String): Parsed<Int> {
    val s = normalizeDigits(text).trim()
    if (s.isEmpty()) return Parsed.Empty
    if (s.length > 6 || !s.all { it in '0'..'9' }) return Parsed.Bad
    return Parsed.Ok(s.toInt())
}

/** تاريخ من الخانة (ISO). */
fun parseDateField(text: String): Parsed<IsoDate> {
    val s = normalizeDigits(text).trim()
    if (s.isEmpty()) return Parsed.Empty
    return if (isValidIsoDate(s)) Parsed.Ok(s) else Parsed.Bad
}

val <T> Parsed<T>.orNull: T? get() = (this as? Parsed.Ok<T>)?.value

/**
 * خانات الشاشة كنصوص (زي ما المستخدم كتبها) — بتتحفظ مع الشاشة (`rememberSaveable`) فترجع زي ما سبتها لما تفتح شاشة تانية وترجع.
 */
@Stable
class FieldsState(initial: Map<String, String> = emptyMap()) {
    private val values = mutableStateMapOf<String, String>().apply { putAll(initial) }

    operator fun get(id: String): String = values[id] ?: ""

    operator fun set(id: String, value: String) {
        values[id] = value
    }

    fun snapshot(): Map<String, String> = values.toMap()

    companion object {
        val Saver: Saver<FieldsState, Any> = listSaver(
            save = { s -> s.values.entries.flatMap { listOf(it.key, it.value) } },
            restore = { l -> FieldsState(l.chunked(2).associate { it[0] to it[1] }) },
        )
    }
}

@Composable
fun rememberFields(): FieldsState = rememberSaveable(saver = FieldsState.Saver) { FieldsState() }

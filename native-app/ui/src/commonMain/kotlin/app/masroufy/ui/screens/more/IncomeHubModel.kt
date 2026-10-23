package app.masroufy.ui.screens.more

import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.LinkedEmployer
import app.masroufy.core.R06Key
import app.masroufy.core.TextRef
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t

/**
 * «مصادر الدخل» بعد الدمج (OVERRIDES §112-6 — `IncomeHub.dc.html`): **دوال نقية** (JVM). مفيش حساب فلوس هنا — المبالغ زي ما اتكتبت
 * على المصدر (`expectedMinor` بالوحدة الصغرى) وبتتصاغ بس. «جهات العمل» ما بقتش في «المزيد» — رابطها جوه نوع الشغل ([KindPageView.employersLink]).
 * سؤال «ده مرتب؟» **مش هنا**: اتنقل لـ«محتاجة مراجعة» في «العمليات» (`operations/SalaryReviewCard.kt`).
 */

/** الأنواع السبعة بالترتيب الثابت — بتظهر دايمًا حتى لو مفيش بيانات («الهيكل الثابت»). */
val HUB_KINDS: List<IncomeSourceKind> = listOf(
    IncomeSourceKind.JOB, IncomeSourceKind.PART_TIME, IncomeSourceKind.CLIENT, IncomeSourceKind.RENT,
    IncomeSourceKind.INVESTMENT, IncomeSourceKind.PENSION, IncomeSourceKind.OTHER,
)

fun hubKindLabel(kind: IncomeSourceKind): TextRef = when (kind) {
    IncomeSourceKind.JOB -> R06Key.INC_KIND_JOB
    IncomeSourceKind.PART_TIME -> R06Key.INC_KIND_PART_TIME
    IncomeSourceKind.CLIENT -> R06Key.INC_KIND_CLIENT
    IncomeSourceKind.RENT -> R06Key.INC_KIND_RENT
    IncomeSourceKind.INVESTMENT -> R06Key.INC_KIND_INVESTMENT
    IncomeSourceKind.PENSION -> R06Key.INC_KIND_PENSION
    IncomeSourceKind.OTHER -> R06Key.INC_KIND_OTHER
}

/** الشغل بمرتب: جوّاه «غيّرت شغلي» و«أماكن اشتغلت فيها» ورابط «جهات العمل». */
fun isWorkKind(kind: IncomeSourceKind): Boolean = kind == IncomeSourceKind.JOB || kind == IncomeSourceKind.PART_TIME

/** CURRENT = فيه مصدر شغال (الاسم أخضر) · PAST_ONLY = كله خلص («قبل كده: …») · EMPTY = «ضيف» بإطار خفيف. */
enum class TileState { CURRENT, PAST_ONLY, EMPTY }

data class KindTile(val kind: IncomeSourceKind, val label: String, val sub: String, val state: TileState)

/** الشاشة الرئيسية: كروت الأنواع بس — **مفيش** سؤال مرتب (اتنقل للمراجعة). */
data class IncomeHubView(val tiles: List<KindTile>)

private fun isCurrent(s: IncomeSource) = s.endedAt == null

fun incomeHub(sources: List<IncomeSource>): IncomeHubView = IncomeHubView(
    HUB_KINDS.map { k ->
        val xs = sources.filter { it.kind == k }
        val now = xs.filter(::isCurrent).sortedByDescending { it.startedAt }
        val past = xs.filterNot(::isCurrent).sortedByDescending { it.endedAt }
        when {
            now.isNotEmpty() -> KindTile(k, t(hubKindLabel(k)), now.first().name, TileState.CURRENT)
            past.isNotEmpty() -> KindTile(k, t(hubKindLabel(k)), t(R06Key.INC_BEFORE, past.first().name), TileState.PAST_ONLY)
            else -> KindTile(k, t(hubKindLabel(k)), t(R06Key.INC_ADD), TileState.EMPTY)
        }
    },
)

/** المصدر الشغال: الاسم · «يوم 28 شهريًا · منذ …» (يوم قبض **بلد المصدر** — §111-3) · المبالغ (مبلغ لكل عملة لو الجهة في البلدين — §111-2). */
data class NowRow(val id: String, val name: String, val line: String, val amounts: List<String>)

data class PastRow(val id: String, val name: String, val dates: String)

enum class KindAction { CHANGE, ADD_OTHER, ADD }

data class KindPageView(
    val kind: IncomeSourceKind,
    val title: String,
    val nowLabel: String,
    val now: List<NowRow>,
    val noneText: String,
    val actions: List<KindAction>,
    val pastLabel: String,
    val past: List<PastRow>,
    val employersLink: Boolean,
)

/**
 * المبالغ المتوقعة للمصدر: لو جهته مربوطة في البلدين ⇒ مبلغ كل بلد بعملته (من غير جمع بين عملتين)، وإلا مبلغه هو.
 * اللي ما اتكتبش ما بيظهرش (مش صفر — القاعدة 10).
 */
fun expectedAmounts(s: IncomeSource, linked: List<LinkedEmployer>): List<String> {
    val both = linked.firstOrNull { e -> e.inBothCountries && e.pays.any { it.sourceId == s.id } }
    if (both != null) return both.pays.mapNotNull { p -> p.expectedMinor?.let { amountLabel(it, p.currency) } }
    return listOfNotNull(s.expectedMinor?.let { amountLabel(it, s.currency) })
}

fun kindPage(kind: IncomeSourceKind, sources: List<IncomeSource>, linked: List<LinkedEmployer> = emptyList()): KindPageView {
    val xs = sources.filter { it.kind == kind }
    val now = xs.filter(::isCurrent).sortedByDescending { it.startedAt }
    val past = xs.filterNot(::isCurrent).sortedByDescending { it.endedAt }
    val work = isWorkKind(kind)
    return KindPageView(
        kind = kind,
        title = t(hubKindLabel(kind)),
        nowLabel = t(if (work) R06Key.INC_NOW_WORK else R06Key.INC_NOW),
        now = now.map { NowRow(it.id, it.name, t(R06Key.INC_NOW_LINE, payText(it), periodText(it)), expectedAmounts(it, linked)) },
        noneText = t(if (work) R06Key.INC_NONE_WORK else R06Key.INC_NONE),
        actions = if (work && now.isNotEmpty()) listOf(KindAction.CHANGE, KindAction.ADD_OTHER) else listOf(KindAction.ADD),
        pastLabel = t(if (work) R06Key.INC_PAST_WORK else R06Key.INC_PAST),
        past = past.map { PastRow(it.id, it.name, periodText(it)) },
        employersLink = work,
    )
}

fun kindActionLabel(a: KindAction): TextRef = when (a) {
    KindAction.CHANGE -> R06Key.INC_CHANGED
    KindAction.ADD_OTHER -> R06Key.INC_ADD_OTHER
    KindAction.ADD -> R06Key.INC_ADD
}

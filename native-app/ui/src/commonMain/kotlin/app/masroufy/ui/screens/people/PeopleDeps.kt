package app.masroufy.ui.screens.people

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.masroufy.core.Halalas
import app.masroufy.core.ProjectKind
import app.masroufy.core.ProjectSummary
import app.masroufy.usecase.EventGifts
import app.masroufy.usecase.LoadDues
import app.masroufy.usecase.LoadPeopleOverview
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.ManageEventPrep
import app.masroufy.usecase.ManageEvents
import app.masroufy.usecase.ManageOccasions
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePersonCircles
import app.masroufy.usecase.ManageProjects

/**
 * منطقة «الأشخاص» (`SCREENS.md` §2.5 — الأشخاص والأحداث والمشاريع): **حالات استخدام بس** (CLAUDE.md #4 — ممنوع مستودع هنا).
 * التنفيذ: `:wiring` → `PeopleGraph` (بنفس اعتماداتها في اختبارات `:app`). الشاشة: `val deps = LocalSpace.current.people`.
 */
interface PeopleDeps {
    /** الدواير والقايمة بالأرصدة — `forSpace(النهارده، البلد الشغالة)` (سطر لكل بلد وعملة، من غير جمع). */
    val overview: LoadPeopleOverview

    /** الأشخاص بالتزاماتهم المفتوحة · إضافة · أرشفة · دين قديم. */
    val people: ManagePeople

    /** الدايرة والصلة بيك والصلات بين الأشخاص. */
    val circles: ManagePersonCircles

    /** المناسبات (ميلادي بس) وتذكير حدثك السنوي. */
    val occasions: ManageOccasions

    /** الأحداث: القايمة · التفاصيل · عمل وتعديل وأرشفة · بادجات النقوط. */
    val events: ManageEvents

    /** ربط العمليات بالحدث بنسبة وتسجيل النقوط (عملية لكل اسم). */
    val gifts: EventGifts

    /** تجهيزات الحدث الجاي. */
    val prep: ManageEventPrep

    /** المشاريع وقواعدها. */
    val projects: ManageProjects

    /** مواعيد الديون (لو اتحددت) — `dueItems` (اللي مصدرها `DEBT` ومعرّفها = معرّف الالتزام). */
    val dues: LoadDues

    /** عمليات فترة (للربط بالحدث: «مصروفك الأخير» وعمليات النقوط الموجودة). */
    val transactions: LoadTransactionsScreen

    /** الأرقام اللي الشاشات بتعرضها ومالهاش حالة استخدام لوحدها — من `core` في `:wiring` (الشاشة ما بتحسبش). */
    val money: PeopleMoney
}

/**
 * حسابات صغيرة بتتعرض في الشاشات ومالهاش حالة استخدام — **التنفيذ في `:wiring` من دوال `core` بس** (`projectNetMinor` · `eventShareMinor` ·
 * `sumMoney` · `budgetStatus`) عشان ولا سطر حساب يبقى في `:ui` (CLAUDE.md #4).
 */
interface PeopleMoney {
    /** رقم المشروع الكبير: الشخصي «كم كلّفني؟» = صرفت − جاءك · العمل «كم كسبت منه؟» = جاءك − صرفت (ممكن سالب — §47). */
    fun projectHeadline(kind: ProjectKind, summary: ProjectSummary): Halalas

    /** نصيب الحدث من عملية بنسبة 1..100 (`eventShareMinor` — النص لفوق على الهللة). */
    fun eventShare(amountMinor: Halalas, percent: Int): Halalas

    /** مجموع مبالغ (المعاينة قبل الحفظ). */
    fun total(amounts: List<Halalas>): Halalas

    /** المصروف على بند من مبلغه المخطط بالعُشر من المية (855 = 85.5%) — لشريط البند. */
    fun usedTenthPercent(plannedMinor: Halalas, spentMinor: Halalas): Long
}

/**
 * «حاجة اتغيرت في الأشخاص» — أي كتابة من شاشة أو لوحة في المنطقة بتزوّد العدّاد، والشاشات بتحمّل تاني (`LaunchedEffect(deps, version)`).
 * على الجهاز ده بس (الشاشات نفسها)، مش بيانات.
 */
internal object PeopleChanges {
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        version++
    }
}

/** حالة تحميل شاشة: بيحمّل (هيكل) · فشل (رسالة + «أعد المحاولة») · جاهز. */
internal sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data object Failed : Load<Nothing>

    data class Ready<T>(val value: T) : Load<T>
}

/** تشغيل قراية وتحويل الفشل لـ[Load.Failed] (من غير ما الشاشة تقع ومن غير أرقام مخترعة). */
internal suspend fun <T> loadOf(block: suspend () -> T): Load<T> = runCatching { block() }.fold({ Load.Ready(it) }, { Load.Failed })

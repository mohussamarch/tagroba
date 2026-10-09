package app.masroufy.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Space
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.dues.DuesDeps
import app.masroufy.ui.screens.home.HomeDeps
import app.masroufy.ui.screens.imports.ImportsDeps
import app.masroufy.ui.screens.investment.InvestmentDeps
import app.masroufy.ui.screens.more.MoreDeps
import app.masroufy.ui.screens.onboarding.OnboardingDeps
import app.masroufy.ui.screens.operations.OperationsDeps
import app.masroufy.ui.screens.people.PeopleDeps
import app.masroufy.usecase.AddOperationDraft
import app.masroufy.usecase.AddOperationOptions
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.WithYouNow

/**
 * اللي الشاشات بتاخده للبلد الشغالة (CLAUDE.md #4 · #6): **حالات استخدام بس** (من غير مستودعات ولا فايربيز)، متقسمة **واجهة صغيرة لكل منطقة**
 * عشان كل شاشة تاخد اللي محتاجاه بس. التنفيذ في `:wiring` (`SpaceGraph` + `<Area>Graph`) — بيتبني من جديد لما البلد تتبدّل أو حساب تاني يدخل.
 * الشاشة: `val deps = LocalSpace.current.people` ثم `LaunchedEffect(deps) { … }` (المفتاح = البلد ⇒ بتحمّل تاني لما البلد تتبدّل).
 */
interface SpaceDeps {
    val space: Space
    val shell: ShellDeps
    val home: HomeDeps
    val operations: OperationsDeps
    val imports: ImportsDeps
    val people: PeopleDeps
    val dues: DuesDeps
    val investment: InvestmentDeps
    val more: MoreDeps
    val onboarding: OnboardingDeps
}

val LocalSpace = staticCompositionLocalOf<SpaceDeps> { error("SpaceDeps مش متقدّم — الشاشة لازم تبقى جوه AppShell بعد ما الحساب يجهز") }

/** اسمك ونسبة اكتمال ملفك (دايرتك والتحية). [profilePercent] = null ⇒ الحسبة لسه ما اتبنتش (OVERRIDES §76 «ناقص في كوتلن») ⇒ مفيش شريط. */
data class MeInfo(val displayName: String?, val profilePercent: Int?, val lookIndex: Int = 1)

/** بلد في لوحة التبديل: «معك الآن» بعملتها (null = غير متاح). */
data class SpaceChoice(val space: Space, val withYouNowMinor: Halalas?, val currency: Currency, val active: Boolean)

/**
 * سطر في نافذة الجرس (عرض بس — الأفعال في صفحة الإشعارات الكاملة). [unread] = لسه ما اتقراش. [tab] = التبويب اللي بياخد النقطة الحمرا منه
 * (null = مالوش نقطة) — عشان مسح الإشعار بـ«×» يشيل نقطة تبويبه (قرار المالك 2026-10-09).
 */
data class BellItem(val threadKey: String, val title: String, val subtitle: String, val tone: BellTone, val unread: Boolean, val tab: Tab? = null)

/** لون نقطة السطر بالمجموعة. */
enum class BellTone { PRIMARY, TRANSFER, EXPENSE, ALERT }

/** نافذة الجرس + النقط الحمرا على التبويبات ([dots] — العمليات من رسايل البنك والأسئلة، والأشخاص من التأخير). */
data class BellState(val items: List<BellItem>, val unread: Int, val dots: Set<Tab>)

/**
 * الهيكل نفسه (الرأس · البطاقة البطلة · لوحة «+» · الجرس · البلد) — مشترك بين التبويبات.
 */
interface ShellDeps {
    /** النهارده بتوقيت الجوال (ISO). */
    fun today(): IsoDate

    /** الساعة دلوقتي 0–23 (صباح ولا مساء). */
    fun hourNow(): Int

    suspend fun me(): MeInfo

    /** «معك الآن» للبلد الشغالة (البطاقة البطلة + `HeroBanks`) — `LoadWithYouNow`. */
    suspend fun withYouNow(): WithYouNow

    /** البلاد للتبديل (`SpaceSwitcher`) — `ManageSpaces.list` + «معك الآن» لكل بلد. */
    suspend fun spaces(): List<SpaceChoice>

    /** التبديل على الجهاز ده بس — من غير أي كتابة على البيانات (`AccountSession.switchSpace`). */
    suspend fun switchSpace(spaceId: String): Boolean

    /** نافذة الجرس — `RunAlertEngine.inbox` + المقروء على الجهاز. */
    suspend fun bell(): BellState

    /** «تعليم الكل كمقروء» — بيشيل النقط الحمرا (المقروء على الجهاز ده بس: OVERRIDES §74 — «مقروء» مالوش حالة في البيانات لسه). */
    suspend fun markAllRead()

    /** لوحة «عملية جديدة» — `QuickAddOperation`. */
    suspend fun addOptions(): AddOperationOptions

    suspend fun addOperation(draft: AddOperationDraft): AddOperationResult
}

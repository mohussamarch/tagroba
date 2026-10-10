package app.masroufy.ui.screens.more

import app.masroufy.core.AlertGroup
import app.masroufy.core.Id
import app.masroufy.core.Space
import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.usecase.ExportCsv
import app.masroufy.usecase.FullBackup
import app.masroufy.usecase.IncomeSourceSignals
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.LoadWithYouNow
import app.masroufy.usecase.ManageIncomeSources
import app.masroufy.usecase.ManageProfile
import app.masroufy.usecase.ReconcileBalance
import app.masroufy.usecase.RunAlertEngine

/**
 * منطقة «المزيد» (`SCREENS.md` §٢.٨): More · Account (+ LookSheet) · AppSettings (القفل واللغة والمحتوى الإسلامي) · NotificationSettings ·
 * Spaces · Wallets · WalletDetail (+ WalletAddSheet) · IncomeSources · IncomeSourceDetail (+ IncomeSourceEditSheet · JobChangeSheet) ·
 * Backup · RestorePreview. الملف ده بتاع المنطقة بس. Categories · Rules · Projects لسه «قيد البناء» (روابطهم في `MoreLinks.kt`).
 * تسجيل الخروج: `LocalApp.current.signIn.signOut()` (الجلسة بترجع لشاشة الدخول لوحدها).
 *
 * **حالات استخدام بس** (CLAUDE.md #4) — التنفيذ في `:wiring` → `MoreGraph`. اللي منطقه لسه ما اتبناش في كوتلن **نقطة ربط** في [MoreHooks]
 * قيمتها `null` ⇒ الشاشة بتقول «غير متاح بعد» بدل ما تزيّف (القاعدة 10 · 15) — بتتملى وقت الدمج (المحفظة الأساسية على فرع `assistant-engine`).
 */
interface MoreDeps : MoreHooks {
    /** ملفك (الاسم · الأسئلة · يوم الراتب · المحتوى الإسلامي · الإيميل · رابط كلمة السر) — نفس `ManageProfile` بتاع الهيكل. */
    val profile: ManageProfile

    /** مصادر الدخل: القايمة · الإضافة · التعديل · القفل · «غيّرت شغلي» وأسئلته · بداية الشهر المالي. */
    val incomeSources: ManageIncomeSources

    /** «ده راتب من …؟» · مقارنة قبل وبعد البداية. */
    val incomeSignals: IncomeSourceSignals

    /** إعدادات الإشعارات: قفل/فتح مجموعة (`setGroupEnabled`) — نفس محرك الهيكل. */
    val alerts: RunAlertEngine

    /** المجموعات المقفولة دلوقتي (قراية الإعداد نفسه — `RunAlertEngine` مالوش دالة قراية). */
    suspend fun disabledAlertGroups(): Set<AlertGroup>

    /** المحافظ وأرصدتها في البلد الشغالة («معك الآن» — نفس سلسلة الرصيد). */
    val wallets: LoadWithYouNow

    /** حركات الشهر المالي (آخر حركات المحفظة بتتصفّى من هنا بالمعرّف — عرض بس). */
    val transactions: LoadTransactionsScreen

    /** مطابقة الرصيد بالكشف (اختيارية). */
    val reconcile: ReconcileBalance

    /** البلاد المفتوحة بعدد محافظ كل واحدة (من «معك الآن» بتاعها). */
    suspend fun spaces(): List<SpaceCard>

    /** تصدير العمليات CSV من تاريخ لتاريخ بالظبط. */
    val exportCsv: ExportCsv

    /** النسخة الشاملة (عمل · معاينة · استرجاع بالدمج) — null لو مستودعها مش متوصل (اختبار). */
    val fullBackup: FullBackup?

    /** وقت الجهاز ISO كامل (`exportedAt` في النسخة). */
    fun nowIso(): String
}

/** كارت بلد في `Spaces`: [walletCount] = null ⇒ مش معروف (مش صفر). */
data class SpaceCard(val space: Space, val active: Boolean, val walletCount: Int?)

/** «المزيد» — بقت شاشة بتتفتح فوق التبويب من الترس (OVERRIDES §74). */
object MoreRoute : Route {
    override val name = "More"
}

object AccountRoute : Route {
    override val name = "Account"
}

object SpacesRoute : Route {
    override val name = "Spaces"
}

object NotificationSettingsRoute : Route {
    override val name = "NotificationSettings"
}

/** القفل واللغة والمحتوى الإسلامي في صفحة واحدة (KOTLIN-MAP §١ — LockSettings · LanguageSettings · IslamicContentSettings). */
object AppSettingsRoute : Route {
    override val name = "AppSettings"
}

object WalletsRoute : Route {
    override val name = "Wallets"
}

data class WalletDetailRoute(val walletId: Id) : Route {
    override val name = "WalletDetail"
}

object IncomeSourcesRoute : Route {
    override val name = "IncomeSources"
}

data class IncomeSourceDetailRoute(val sourceId: Id) : Route {
    override val name = "IncomeSourceDetail"
}

object BackupRoute : Route {
    override val name = "Backup"
}

/** معاينة الاسترجاع لملف اتختار من الجوال: اسمه ونصه (الخطة بتتحسب في الشاشة من `FullBackup.plan`). */
data class RestorePreviewRoute(val fileName: String, val text: String) : Route {
    override val name = "RestorePreview"
}

fun RouteRegistry.registerMore() {
    screen<MoreRoute> { MoreScreen() }
    screen<AccountRoute> { AccountScreen() }
    screen<AppSettingsRoute> { AppSettingsScreen() }
    screen<NotificationSettingsRoute> { NotificationSettingsScreen() }
    screen<SpacesRoute> { SpacesScreen() }
    screen<WalletsRoute> { WalletsScreen() }
    screen<WalletDetailRoute> { r -> WalletDetailScreen(r.walletId) }
    screen<IncomeSourcesRoute> { IncomeSourcesScreen() }
    screen<IncomeSourceDetailRoute> { r -> IncomeSourceDetailScreen(r.sourceId) }
    screen<BackupRoute> { BackupScreen() }
    screen<RestorePreviewRoute> { r -> RestorePreviewScreen(r.fileName, r.text) }
}

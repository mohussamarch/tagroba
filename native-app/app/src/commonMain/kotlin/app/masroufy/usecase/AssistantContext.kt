package app.masroufy.usecase

import app.masroufy.core.AssistLexicon
import app.masroufy.core.AssistPlatform
import app.masroufy.core.AssistTab
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.LexItem
import app.masroufy.core.ScreenLink
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.UserProfile
import app.masroufy.core.ZakatPrices
import app.masroufy.core.formatMoney
import app.masroufy.core.parseIsoDate
import app.masroufy.core.uiText
import app.masroufy.port.AssetRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.ProjectRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.RoscaRepository
import app.masroufy.port.SavingsGoalRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * اللي المساعد محتاجه من الشاشة كل مرة: الوقت (ISO كامل + تاريخ البلد + الساعة المحلية للتحية) · التبويب · البلد الشغالة · الجهاز ·
 * الشخص اللي شاشته مفتوحة (لـ«عليه كام؟»).
 */
data class AssistContext(
    val nowIso: String,
    val today: IsoDate,
    val hour: Int,
    val space: Space,
    val tab: AssistTab = AssistTab.HOME,
    val platform: AssistPlatform = AssistPlatform.ANDROID,
    val subjectPersonId: Id? = null,
    /** عدد البلاد غير المؤرشفة — «التحويل بين البلدين» محتاج بلد تانية. */
    val spaceCount: Int = 1,
) {
    val currency: Currency get() = space.currency
}

/**
 * حالات الاستخدام اللي الإجابات بتقرا منها — **كل رقم من هنا** (القاعدة 10: «لا رقم بلا مصدر»). أي مصدر null ⇒ الإجابة «غير متاح» مع
 * السبب، مش رقم متألف. التشغيل بيدّي الموجود للبلد الشغالة.
 */
data class AssistantSources(
    val wallets: WalletRepository,
    val txns: TransactionRepository,
    val profile: ProfileRepository? = null,
    val home: LoadHomeScreen? = null,
    val money: LoadMoneySummary? = null,
    val history: LoadHomeHistory? = null,
    val budget: LoadBudgetScreen? = null,
    val leftover: LoadLeftover? = null,
    val cash: LoadCashSummary? = null,
    val categorySpend: LoadCategorySpend? = null,
    val lastAt: FindLastAtMerchant? = null,
    val people: LoadPeopleOverview? = null,
    val personAcross: PersonAcrossSpaces? = null,
    val dues: LoadDues? = null,
    val debtTerms: DebtTermsRepository? = null,
    val incomeSources: ManageIncomeSources? = null,
    val recurring: ManageRecurring? = null,
    val sms: AutoRecordSms? = null,
    val goals: LoadGoalsOverview? = null,
    val zakat: ManageZakat? = null,
    val payZakat: PayZakat? = null,
    /** أسعار الدهب والفضة بعملة البلد (من ملف الأسعار) — null ⇒ الزكاة «غير متاح». */
    val zakatPrices: ZakatPrices? = null,
    val assets: ManageAssets? = null,
    val events: ManageEvents? = null,
    val projects: ManageProjects? = null,
    val roscas: ManageRoscas? = null,
    val installments: ManageInstallments? = null,
    val occasions: ManageOccasions? = null,
    val calendar: LoadCalendar? = null,
    /** محافظ رصيدها مش متطابق مع الكشف (من المطابقة) ⇒ «معك الآن» تقريبي. */
    val unreconciledWalletIds: Set<Id> = emptySet(),
)

/** أسامي المستخدم للفهم — من المستودعات (أي واحد null = مفيش أسامي من النوع ده). */
data class AssistLexiconSource(
    val categories: CategoryRepository? = null,
    val merchants: MerchantRepository? = null,
    val people: PersonRepository? = null,
    val wallets: WalletRepository? = null,
    val goals: SavingsGoalRepository? = null,
    val events: LifeEventRepository? = null,
    val projects: ProjectRepository? = null,
    val recurring: RecurringRepository? = null,
    val assets: AssetRepository? = null,
    val roscas: RoscaRepository? = null,
    val plans: InstallmentPlanRepository? = null,
) {
    suspend fun load(): AssistLexicon = AssistLexicon(
        categories = categories?.listAll().orEmpty(),
        merchants = merchants?.listAll().orEmpty(),
        people = people?.listAll().orEmpty().filter { !it.archived },
        wallets = wallets?.listAll().orEmpty(),
        goals = goals?.listAll().orEmpty().map { LexItem(it.id, listOf(it.name), it.archived) },
        events = events?.listAll().orEmpty().map { LexItem(it.id, listOf(it.name), it.archived) },
        projects = projects?.listAll().orEmpty().map { LexItem(it.id, listOf(it.name), it.archived) },
        recurring = recurring?.listAll().orEmpty(),
        assets = assets?.listAll().orEmpty().map { LexItem(it.id, listOf(it.name), it.archived) },
        roscas = roscas?.listAll().orEmpty().map { LexItem(it.id, listOf(it.name)) },
        plans = plans?.listAll().orEmpty().map { LexItem(it.id, listOf(it.name)) },
    )
}

/**
 * رد المساعد: النص + الروابط + **كل مبلغ اتحط في النص** ([amounts]) بالترتيب — الاختبارات بتقارنه برقم حالة الاستخدام نفسها.
 */
data class AssistReply(
    val text: String,
    val links: List<ScreenLink> = emptyList(),
    val amounts: List<Halalas> = emptyList(),
    /** المصدر قال «تقريبي» (عمليات محتاجة تأكيد · رصيد مش متطابق). */
    val approximate: Boolean = false,
)

/** بيبني نص الرد ويجمّع المبالغ اللي اتكتبت فيه. */
class ReplyBuilder(private val currency: Currency) {
    val amounts = mutableListOf<Halalas>()
    val lines = mutableListOf<String>()
    val links = mutableListOf<ScreenLink>()
    var approximate = false

    fun money(minor: Halalas, c: Currency = currency): String {
        amounts += minor
        return formatMoney(minor, c)
    }

    fun line(key: TextKey, vararg args: String) {
        lines += uiText(key, *args)
    }

    /** «تقريبي» + سببه (لو فيه نص) — مرة واحدة في الرد. */
    fun approx(key: TextKey?, vararg args: String) {
        if (approximate) return
        approximate = true
        line(key ?: TextKey.ASSIST_APPROXIMATE, *args)
    }

    fun link(l: ScreenLink) {
        if (links.none { it == l }) links += l
    }

    fun build(): AssistReply = AssistReply(lines.joinToString(" "), links.toList(), amounts.toList(), approximate)
}

/** «٢٨ أكتوبر» (والسنة لو مش السنة دي). أسامي الشهور من جدول النصوص. */
fun assistDate(date: IsoDate, today: IsoDate): String {
    val p = parseIsoDate(date)
    val month = uiText(TextKey.ASSIST_MONTHS).split('|').getOrElse(p.month - 1) { p.month.toString() }
    return if (date.take(4) == today.take(4)) uiText(TextKey.ASSIST_DATE, p.day.toString(), month)
    else uiText(TextKey.ASSIST_DATE_YEAR, p.day.toString(), month, p.year.toString())
}

/** «يوم» · «يومين» · «٣ أيام» · «١٥ يومًا». */
fun assistDays(n: Int): String = when {
    n == 1 -> uiText(TextKey.ASSIST_DAYS_ONE)
    n == 2 -> uiText(TextKey.ASSIST_DAYS_TWO)
    n in 3..10 -> uiText(TextKey.ASSIST_DAYS_FEW, n.toString())
    else -> uiText(TextKey.ASSIST_DAYS_MANY, n.toString())
}

/** «مرة واحدة» · «مرتين» · «٣ مرات» · «١٥ مرة». */
fun assistTimes(n: Int): String = when {
    n == 1 -> uiText(TextKey.ASSIST_TIMES_ONE)
    n == 2 -> uiText(TextKey.ASSIST_TIMES_TWO)
    n in 3..10 -> uiText(TextKey.ASSIST_TIMES_FEW, n.toString())
    else -> uiText(TextKey.ASSIST_TIMES_MANY, n.toString())
}

/** «أ، ب و ج». */
fun assistList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(uiText(TextKey.ASSIST_LIST_COMMA)) + uiText(TextKey.ASSIST_LIST_AND) + items.last()
}

/** «X» بعلامات الاقتباس العربي (الإنجليزي “X”). */
fun quoted(name: String): String = uiText(TextKey.ASSIST_QUOTE, name)

/** يوم الراتب من الملف (الافتراضي ٢٨ زي باقي التطبيق). */
suspend fun AssistantSources.profileOrNull(): UserProfile? = profile?.load()

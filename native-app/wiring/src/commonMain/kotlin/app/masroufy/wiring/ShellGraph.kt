package app.masroufy.wiring

import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.Space
import app.masroufy.ui.app.BellItem
import app.masroufy.ui.app.BellState
import app.masroufy.ui.app.BellTone
import app.masroufy.ui.app.MeInfo
import app.masroufy.ui.app.ShellDeps
import app.masroufy.ui.app.SpaceChoice
import app.masroufy.ui.nav.Tab
import app.masroufy.core.MainWalletSource
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.usecase.AddWalletDefault
import app.masroufy.usecase.DismissedAlert
import app.masroufy.usecase.MainSpendingWallets
import app.masroufy.usecase.ManageAlertDismissals
import app.masroufy.usecase.AddOperationDraft
import app.masroufy.usecase.AddOperationOptions
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.AddTransaction
import app.masroufy.usecase.AddTransactionDeps
import app.masroufy.usecase.AlertEngineDeps
import app.masroufy.usecase.LoadWithYouNow
import app.masroufy.usecase.ManageProfile
import app.masroufy.usecase.ManageProfileDeps
import app.masroufy.usecase.QuickAddOperation
import app.masroufy.usecase.RunAlertEngine
import app.masroufy.usecase.WithYouNow

/**
 * الهيكل لبلد واحدة (`ShellDeps`): «معك الآن» · البلاد · الجرس · لوحة «+» · اسمك. كل حاجة حالة استخدام من `:app` — مفيش حساب هنا.
 */
class ShellGraph(
    private val space: Space,
    private val repos: SpaceRepositories,
    private val env: DeviceEnv,
    private val session: SessionLinks,
) : ShellDeps {
    private val withYou = LoadWithYouNow(repos.wallets, repos.transactions, space.currency)
    val engine = RunAlertEngine(
        AlertEngineDeps(repos.alertSettings, env.interactions, env.usualHours, repos.alertReceipts, repos.alertInbox, env.clock, dismissals = repos.assistant.alertDismissals),
    )
    val addTransaction = AddTransaction(AddTransactionDeps(repos.transactions, repos.wallets, env.ids, env.clock))
    private val quickAdd = QuickAddOperation(repos.wallets, repos.categories, repos.profile, addTransaction, space.currency, env.today)
    val profile = ManageProfile(ManageProfileDeps(repos.profile, session.account, env.clock, repos.incomeSources))

    override fun today() = env.today()

    override fun hourNow() = env.hourNow()

    // نسبة «ملفك ٪» لسه مالهاش حسبة في كوتلن (OVERRIDES §76 «ناقص») ⇒ null ⇒ الدايرة من غير شريط
    override suspend fun me(): MeInfo = MeInfo(profile.load().displayName, profilePercent = null)

    override suspend fun withYouNow(): WithYouNow = withYou.load(env.today())

    override suspend fun spaces(): List<SpaceChoice> = session.spaces().map { (s, r) ->
        val total = runCatching { LoadWithYouNow(r.wallets, r.transactions, s.currency).load(env.today()).totalMinor }.getOrNull()
        SpaceChoice(s, total, s.currency, s.id == space.id)
    }

    override suspend fun switchSpace(spaceId: String): Boolean = session.switchSpace(spaceId)

    override suspend fun bell(): BellState {
        val seen = env.seenAlerts.read()
        val items = engine.inbox().map { v ->
            val unread = v.entry.openedAt == null && v.entry.threadKey !in seen && !v.muted
            BellItem(v.entry.threadKey, v.entry.title, v.group, toneOf(v.entry.kind), unread, tabOf(v.entry.kind))
        }
        val unread = items.filter { it.unread }
        val kinds = engine.inbox().filter { v -> unread.any { it.threadKey == v.entry.threadKey } }.map { it.entry.kind }
        return BellState(items, unread.size, dotsOf(kinds))
    }

    override suspend fun markAllRead() {
        env.seenAlerts.addAll(engine.inbox().map { it.entry.threadKey })
    }

    override suspend fun addOptions(): AddOperationOptions = quickAdd.options()

    override suspend fun addOperation(draft: AddOperationDraft): AddOperationResult = quickAdd.save(draft)

    /** المحفظة الأساسية للبلد (§78 ٢) — نفس الكائن للوحة «+» وتفاصيل المحفظة وقايمة المحافظ (`MoreGraph`) والمساعد بيقرا نفس المخزن. */
    val mainWallet = MainSpendingWallets(repos.assistant.settings, repos.wallets, space.id, env.clock)

    override suspend fun addWalletDefault(): AddWalletDefault = mainWallet.defaultForAdd()

    override suspend fun setMainWallet(walletId: String) {
        mainWallet.set(walletId, MainWalletSource.ADD_SHEET)
    }

    /** «×» على إشعار (رد المالك ٣): علامة على الحساب + السطر بيتشال — `bell()` بيقرا من غيره ⇒ العدّ ونقطة التبويب بيروحوا. */
    val alertDismissals = repos.assistant.alertDismissals?.let { ManageAlertDismissals(it, repos.alertInbox, env.clock) }

    // مخزن المسح مش متوصل ⇒ «غير متاح» صريح (مش مسح للجلسة بس في صمت)
    override suspend fun dismissAlert(threadKey: String): DismissedAlert =
        (alertDismissals ?: throw IllegalStateException(uiText(TextKey.ASSIST_NA))).dismiss(threadKey)

    override suspend fun undoDismiss(dismissed: DismissedAlert) {
        alertDismissals?.undo(dismissed)
    }
}

/** لون النقطة في الجرس بالمجموعة (النموذج: رسايل البنك أخضر · التحويلات أزرق · الميزانية أحمر · المواعيد كهرماني). */
internal fun toneOf(kind: AlertKind): BellTone = when (kind.group) {
    AlertGroup.BANK_SMS -> BellTone.PRIMARY
    AlertGroup.QUESTIONS -> BellTone.TRANSFER
    AlertGroup.BUDGET -> BellTone.EXPENSE
    else -> BellTone.ALERT
}

/**
 * النقط الحمرا على التبويبات (OVERRIDES §76 ٣): **العمليات** من رسايل البنك وأسئلة المراجعة · **الأشخاص** من المواعيد اللي فاتت والمناسبات.
 * اختيار Claude للربط بالأنواع (المالك يقدر يغيّره). «تعليم الكل كمقروء» بيشيلها.
 */
internal fun dotsOf(kinds: List<AlertKind>): Set<Tab> = kinds.mapNotNull(::tabOf).toSet()

/** التبويب اللي بياخد نقطة النوع ده (null = مالوش نقطة) — نفس ربط [dotsOf]. */
internal fun tabOf(k: AlertKind): Tab? = when {
    k.group == AlertGroup.BANK_SMS || k.group == AlertGroup.QUESTIONS -> Tab.OPERATIONS
    k == AlertKind.DUE_OVERDUE || k.group == AlertGroup.OCCASIONS -> Tab.PEOPLE
    else -> null
}
